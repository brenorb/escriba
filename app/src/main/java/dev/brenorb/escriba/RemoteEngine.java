package dev.brenorb.escriba;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

public final class RemoteEngine {
  public interface Listener {
    void segment(String text, double start, double end);

    void done();

    void error(String message);
  }

  private volatile boolean cancelled;
  private volatile HttpURLConnection connection;
  private Thread worker;

  public void cancel() {
    cancelled = true;
    HttpURLConnection c = connection;
    if (c != null) c.disconnect();
    if (worker != null) worker.interrupt();
  }

  public void start(
      Context context,
      Uri uri,
      String endpoint,
      String token,
      String model,
      String language,
      Listener listener) {
    worker =
        new Thread(
            () -> {
              try {
                if (!EndpointPolicy.valid(endpoint)
                    || (!endpoint.startsWith("https://")
                        && (context.getApplicationInfo().flags
                                & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)
                            == 0)) throw new IOException("Configure um endereço HTTPS em Ajustes.");
                ByteArrayOutputStream chunk = new ByteArrayOutputStream(480000);
                long[] offset = {0};
                AudioDecoder.decode(
                    context,
                    uri,
                    pcm -> {
                      int at = 0;
                      while (at < pcm.length && !cancelled) {
                        int n = Math.min(480000 - chunk.size(), pcm.length - at);
                        chunk.write(pcm, at, n);
                        at += n;
                        if (chunk.size() == 480000) {
                          send(
                              chunk.toByteArray(),
                              offset[0],
                              endpoint,
                              token,
                              model,
                              language,
                              listener);
                          offset[0] += chunk.size();
                          chunk.reset();
                        }
                      }
                    },
                    () -> cancelled);
                if (chunk.size() > 0)
                  send(chunk.toByteArray(), offset[0], endpoint, token, model, language, listener);
                if (!cancelled) listener.done();
              } catch (Exception e) {
                if (!cancelled)
                  listener.error(
                      e.getMessage() == null ? "Falha ao transcrever o áudio." : e.getMessage());
              }
            },
            "escriba-remote");
    worker.start();
  }

  private void send(
      byte[] pcm,
      long offset,
      String endpoint,
      String token,
      String model,
      String language,
      Listener listener)
      throws Exception {
    if (cancelled) throw new InterruptedException();
    String boundary = "EscribaAudioBoundary";
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    field(body, boundary, "model", model);
    field(body, boundary, "language", language);
    body.write(
        ("--"
                + boundary
                + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"part.wav\"\r\n"
                + "Content-Type: audio/wav\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8));
    body.write(Pcm.wav(pcm, 16000, 1));
    body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
    connection = c;
    try {
      c.setInstanceFollowRedirects(false);
      c.setConnectTimeout(10000);
      c.setReadTimeout(120000);
      c.setRequestMethod("POST");
      c.setDoOutput(true);
      c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
      if (!token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token);
      c.setFixedLengthStreamingMode(body.size());
      try (OutputStream out = c.getOutputStream()) {
        body.writeTo(out);
      }
      int status = c.getResponseCode();
      if (status != 200)
        throw new IOException(
            "Servidor respondeu HTTP " + status + ". Verifique endereço, chave e modelo.");
      ByteArrayOutputStream response = new ByteArrayOutputStream();
      try (InputStream in = c.getInputStream()) {
        byte[] b = new byte[4096];
        int n;
        while ((n = in.read(b)) != -1) {
          if (response.size() + n > 1024 * 1024) throw new IOException("Resposta grande demais.");
          response.write(b, 0, n);
        }
      }
      JSONObject json = new JSONObject(response.toString(StandardCharsets.UTF_8.name()));
      if (!json.has("text") || !(json.get("text") instanceof String))
        throw new IOException("Resposta sem campo text.");
      if (!cancelled)
        listener.segment(json.getString("text"), offset / 32000.0, (offset + pcm.length) / 32000.0);
    } finally {
      c.disconnect();
      if (connection == c) connection = null;
    }
  }

  private static void field(OutputStream out, String boundary, String name, String value)
      throws IOException {
    if (value.contains("\r") || value.contains("\n"))
      throw new IOException("Configuração inválida.");
    out.write(
        ("--"
                + boundary
                + "\r\nContent-Disposition: form-data; name=\""
                + name
                + "\"\r\n\r\n"
                + value
                + "\r\n")
            .getBytes(StandardCharsets.UTF_8));
  }
}
