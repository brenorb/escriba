package dev.brenorb.escriba;

import android.content.*;
import android.media.AudioFormat;
import android.net.Uri;
import android.os.*;
import android.speech.*;
import java.io.OutputStream;
import java.util.ArrayList;

public final class NativeEngine implements RecognitionListener {
  public interface Listener {
    void partial(String text);

    void segment(String text);

    void done();

    void error(String text);

    default void modelUnavailable(boolean canDownload) {}

    default void downloadStatus(String text, boolean finished) {}

    default void modelReady() {}
  }

  private final Context context;
  private final Listener listener;
  private final Handler main = new Handler(Looper.getMainLooper());
  private SpeechRecognizer recognizer;
  private ParcelFileDescriptor[] pipe;
  private volatile OutputStream output;
  private volatile boolean cancelled;
  private volatile boolean sourceComplete;
  private int segments;
  private Intent request;
  private String requestedLanguage;
  private boolean downloadRequested;
  private int downloadChecks;
  private final Runnable checkDownload = this::checkDownload;
  private final Runnable timeout =
      () -> fail("O serviço Android não respondeu. Tente outro idioma ou configure o servidor.");

  public NativeEngine(Context context, Listener listener) {
    this.context = context;
    this.listener = listener;
  }

  public void start(Uri uri, String language) {
    try {
      requestedLanguage = language;
      if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
        fail("Reconhecimento local indisponível neste aparelho. Configure um servidor em Ajustes.");
        return;
      }
      recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
      recognizer.setRecognitionListener(this);
      pipe = ParcelFileDescriptor.createPipe();
      request =
          new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
              .putExtra(
                  RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
              .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
              .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
              .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
              .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
              .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
              .putExtra(
                  RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
              .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16000)
              .putExtra(
                  RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE);
      armTimeout();
      recognizer.checkRecognitionSupport(
          request,
          context.getMainExecutor(),
          new RecognitionSupportCallback() {
            public void onSupportResult(RecognitionSupport support) {
              if (cancelled) return;
              if (!VoiceModelPolicy.installed(support.getInstalledOnDeviceLanguages(), language)) {
                main.removeCallbacks(timeout);
                boolean canDownload =
                    VoiceModelPolicy.downloadable(
                        support.getSupportedOnDeviceLanguages(),
                        support.getPendingOnDeviceLanguages(),
                        language);
                listener.modelUnavailable(canDownload);
                return;
              }
              try {
                recognizer.startListening(request);
                feed(uri);
              } catch (Exception e) {
                fail("O serviço Android não aceita este arquivo. Use o servidor em Ajustes.");
              }
            }

            public void onError(int error) {
              if (!cancelled)
                fail(
                    "O serviço Android não confirmou suporte a arquivos ("
                        + error
                        + "). Use o servidor em Ajustes.");
            }
          });
    } catch (Exception e) {
      fail("Não foi possível iniciar o reconhecimento local. Use o servidor em Ajustes.");
    }
  }

  public void downloadModel() {
    if (cancelled || recognizer == null || request == null) return;
    downloadRequested = true;
    downloadChecks = 0;
    listener.downloadStatus("Pedindo ao Android para instalar a voz…", false);
    try {
      if (Build.VERSION.SDK_INT >= 34) {
        recognizer.triggerModelDownload(
            request,
            context.getMainExecutor(),
            new ModelDownloadListener() {
              public void onProgress(int completedPercent) {
                if (!cancelled)
                  listener.downloadStatus("Baixando voz… " + completedPercent + "%", false);
              }

              public void onScheduled() {
                if (!cancelled)
                  listener.downloadStatus("Download agendado pelo Android. Aguardando…", false);
              }

              public void onSuccess() {
                if (!cancelled) finishModelDownload();
              }

              public void onError(int error) {
                if (!cancelled) {
                  downloadRequested = false;
                  main.removeCallbacks(checkDownload);
                  listener.downloadStatus(
                      "O Android não conseguiu baixar a voz ("
                          + error
                          + "). Toque para tentar de novo.",
                      true);
                }
              }
            });
      } else {
        recognizer.triggerModelDownload(request);
      }
      main.postDelayed(checkDownload, 3000);
    } catch (Exception error) {
      downloadRequested = false;
      listener.downloadStatus(
          "O serviço Android recusou o download. Use o servidor ou tente novamente.", true);
    }
  }

  private void checkDownload() {
    if (cancelled || !downloadRequested || recognizer == null) return;
    if (downloadChecks++ >= 60) {
      downloadRequested = false;
      listener.downloadStatus(
          "O Android ainda não concluiu o download. Conecte-se ao Wi-Fi e tente de novo.", true);
      return;
    }
    try {
      recognizer.checkRecognitionSupport(
          request,
          context.getMainExecutor(),
          new RecognitionSupportCallback() {
            public void onSupportResult(RecognitionSupport support) {
              if (cancelled || !downloadRequested) return;
              if (VoiceModelPolicy.installed(
                  support.getInstalledOnDeviceLanguages(), requestedLanguage)) {
                finishModelDownload();
              } else if (VoiceModelPolicy.downloadable(
                  support.getSupportedOnDeviceLanguages(),
                  support.getPendingOnDeviceLanguages(),
                  requestedLanguage)) {
                main.postDelayed(checkDownload, 5000);
              } else {
                downloadRequested = false;
                listener.downloadStatus(
                    "O serviço Android não oferece esse pacote de voz. Configure o servidor em"
                        + " Ajustes.",
                    true);
              }
            }

            public void onError(int error) {
              if (!cancelled && downloadRequested) main.postDelayed(checkDownload, 5000);
            }
          });
    } catch (Exception error) {
      downloadRequested = false;
      listener.downloadStatus("Não foi possível acompanhar o download da voz.", true);
    }
  }

  private void finishModelDownload() {
    if (!downloadRequested || cancelled) return;
    downloadRequested = false;
    main.removeCallbacks(checkDownload);
    listener.modelReady();
  }

  private void feed(Uri uri) {
    final ParcelFileDescriptor write = pipe[1];
    new Thread(
            () -> {
              try (OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(write)) {
                output = out;
                AudioDecoder.decode(
                    context,
                    uri,
                    pcm -> {
                      if (!cancelled) out.write(pcm);
                    },
                    () -> cancelled);
                sourceComplete = !cancelled;
              } catch (Exception e) {
                main.post(
                    () -> {
                      if (!cancelled) fail("Falha ao decodificar áudio para o serviço Android.");
                    });
              } finally {
                output = null;
              }
            },
            "escriba-native")
        .start();
  }

  public void cancel() {
    cancelled = true;
    main.removeCallbacks(timeout);
    main.removeCallbacks(checkDownload);
    try {
      if (output != null) output.close();
    } catch (Exception ignored) {
    }
    if (pipe != null)
      for (ParcelFileDescriptor p : pipe)
        try {
          p.close();
        } catch (Exception ignored) {
        }
    if (recognizer != null) {
      recognizer.cancel();
      recognizer.destroy();
      recognizer = null;
    }
  }

  private void fail(String message) {
    if (cancelled) return;
    cancel();
    listener.error(message);
  }

  private void finish() {
    if (cancelled) return;
    cancel();
    listener.done();
  }

  private void armTimeout() {
    main.removeCallbacks(timeout);
    main.postDelayed(timeout, 90000);
  }

  private String text(Bundle b) {
    ArrayList<String> results = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
    return results == null || results.isEmpty() ? "" : results.get(0);
  }

  public void onPartialResults(Bundle b) {
    if (!cancelled) {
      armTimeout();
      listener.partial(text(b));
    }
  }

  public void onSegmentResults(Bundle b) {
    if (!cancelled) {
      armTimeout();
      segments++;
      listener.segment(text(b));
    }
  }

  public void onEndOfSegmentedSession() {
    if (!sourceComplete) {
      fail("O serviço Android encerrou antes de consumir o arquivo inteiro. Use o servidor.");
      return;
    }
    finish();
  }

  public void onResults(Bundle b) {
    if (!cancelled && !sourceComplete) {
      fail("O serviço Android encerrou antes de consumir o arquivo inteiro. Use o servidor.");
      return;
    }
    if (!cancelled) {
      if (segments == 0) listener.segment(text(b));
      finish();
    }
  }

  public void onError(int code) {
    if (code == SpeechRecognizer.ERROR_NO_MATCH) {
      fail(
          "O Android não encontrou fala reconhecível neste áudio. Confira se há voz clara em"
              + " português.");
    } else if (code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
      fail("O Android não detectou fala neste áudio. Confira o volume e tente novamente.");
    } else if (code == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED
        || code == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) {
      fail(
          "O serviço Android não tem esse idioma pronto. Use “Instalar voz e continuar” ou o"
              + " servidor.");
    } else if (code == SpeechRecognizer.ERROR_AUDIO) {
      fail("O Android não conseguiu ler o áudio. Tente outro formato ou use o servidor.");
    } else {
      fail("O serviço Android falhou (código " + code + "). Tente novamente ou use o servidor.");
    }
  }

  public void onReadyForSpeech(Bundle b) {
    armTimeout();
  }

  public void onBeginningOfSpeech() {
    armTimeout();
  }

  public void onRmsChanged(float rms) {}

  public void onBufferReceived(byte[] b) {}

  public void onEndOfSpeech() {}

  public void onEvent(int type, Bundle b) {}
}
