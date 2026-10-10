package dev.brenorb.escriba;

import java.util.*;

public final class CoreSpec {
  public static void main(String[] args) throws Exception {
    check(InputPolicy.accepts("audio/ogg", "note.opus"), "opus audio");
    check(InputPolicy.accepts("application/octet-stream", "voice.OPUS"), "messenger generic MIME");
    check(!InputPolicy.accepts("text/plain", "notes.txt"), "reject text");
    check(
        !InputPolicy.accepts("application/octet-stream", "payload.exe"), "reject unrelated binary");
    check(EndpointPolicy.valid("https://example.org/v1/audio/transcriptions"), "HTTPS");
    check(!EndpointPolicy.valid("http://192.168.1.20:8765/transcribe"), "LAN requires TLS");
    check(!EndpointPolicy.valid("http://evil.example/transcribe"), "reject public cleartext");
    check(!EndpointPolicy.valid("https://user:password@example.org/"), "reject URL credentials");
    check(!EndpointPolicy.valid("http://192.168.1.20.evil.example/"), "reject spoofed LAN");
    Transcript state = new Transcript();
    state.partial("Primeira hipótese");
    state.partial("Hipótese corrigida");
    check(state.copyText().isEmpty(), "partial not exported");
    state.commit("Primeira frase.", 0, 15);
    state.partial("Outra");
    state.commit("Segunda frase.", 15, 30);
    check(
        state.copyText().equals("Primeira frase.\n\nSegunda frase."),
        "segment replacement and ordering");
    byte[] wav = Pcm.wav(new byte[] {0, 0, 1, 0}, 16000, 1);
    check(wav.length == 48 && wav[0] == 'R' && wav[40] == 4, "WAV header and payload");
    Pcm.Converter converter = new Pcm.Converter(48000, 2);
    byte[] stereo = new byte[48000 * 4];
    Arrays.fill(stereo, (byte) 0);
    check(converter.convert(stereo).length == 32000, "48k stereo to 16k mono");
    Pcm.Converter uneven = new Pcm.Converter(44100, 1);
    int bytes = 0;
    for (int i = 0; i < 100; i++) bytes += uneven.convert(new byte[882]).length;
    check(bytes == 32000, "resampler phase across buffers");
    System.out.println("PASS 14 core assertions");
  }

  static void check(boolean b, String why) {
    if (!b) throw new AssertionError(why);
  }
}
