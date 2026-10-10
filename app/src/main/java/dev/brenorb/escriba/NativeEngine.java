package dev.brenorb.escriba;
import android.content.*;
import android.media.AudioFormat;
import android.net.Uri;
import android.os.*;
import android.speech.*;
import java.io.OutputStream;
import java.util.ArrayList;
public final class NativeEngine implements RecognitionListener {
    public interface Listener {void partial(String text);void segment(String text);void done();void error(String text);}
    private final Context context; private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer; private ParcelFileDescriptor[] pipe;private volatile OutputStream output;
    private volatile boolean cancelled; private int segments;
    private final Runnable timeout=()->fail("O serviço Android não respondeu. Tente outro idioma ou configure o servidor.");
    public NativeEngine(Context context,Listener listener){this.context=context;this.listener=listener;}
    public void start(Uri uri,String language) {
        try {
            if(!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {fail("Reconhecimento local indisponível neste aparelho. Configure um servidor em Ajustes.");return;}
            recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(context);recognizer.setRecognitionListener(this);
            pipe=ParcelFileDescriptor.createPipe();
            Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE,language)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE,pipe[0])
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT,1)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,AudioFormat.ENCODING_PCM_16BIT)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE,16000)
                .putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION,RecognizerIntent.EXTRA_AUDIO_SOURCE);
            armTimeout();
            recognizer.checkRecognitionSupport(intent,context.getMainExecutor(),new RecognitionSupportCallback(){
                public void onSupportResult(RecognitionSupport support){
                    if(cancelled)return;
                    if(!support.getInstalledOnDeviceLanguages().contains(language)){
                        fail("Idioma local não instalado. Baixe o pacote de voz do Android ou use o servidor.");return;
                    }
                    try{recognizer.startListening(intent);feed(uri);}catch(Exception e){fail("O serviço Android não aceita este arquivo. Use o servidor em Ajustes.");}
                }
                public void onError(int error){if(!cancelled)fail("O serviço Android não confirmou suporte a arquivos ("+error+"). Use o servidor em Ajustes.");}
            });
        }catch(Exception e){fail("Não foi possível iniciar o reconhecimento local. Use o servidor em Ajustes.");}
    }
    private void feed(Uri uri){
        final ParcelFileDescriptor write=pipe[1];
        new Thread(()->{
            try(OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(write)){
                output=out;AudioDecoder.decode(context,uri,pcm->{if(!cancelled)out.write(pcm);},()->cancelled);
            }catch(Exception e){main.post(()->{if(!cancelled)fail("Falha ao decodificar áudio para o serviço Android.");});}
            finally{output=null;}
        },"escriba-native").start();
    }
    public void cancel(){
        cancelled=true;main.removeCallbacks(timeout);
        try{if(output!=null)output.close();}catch(Exception ignored){}
        if(pipe!=null)for(ParcelFileDescriptor p:pipe)try{p.close();}catch(Exception ignored){}
        if(recognizer!=null){recognizer.cancel();recognizer.destroy();recognizer=null;}
    }
    private void fail(String message){if(cancelled)return;cancel();listener.error(message);}
    private void finish(){if(cancelled)return;cancel();listener.done();}
    private void armTimeout(){main.removeCallbacks(timeout);main.postDelayed(timeout,90000);}
    private String text(Bundle b){ArrayList<String> results=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);return results==null||results.isEmpty()?"":results.get(0);}
    public void onPartialResults(Bundle b){if(!cancelled){armTimeout();listener.partial(text(b));}}
    public void onSegmentResults(Bundle b){if(!cancelled){armTimeout();segments++;listener.segment(text(b));}}
    public void onEndOfSegmentedSession(){finish();}
    public void onResults(Bundle b){if(!cancelled){if(segments==0)listener.segment(text(b));finish();}}
    public void onError(int code){fail("Falha do reconhecimento local ("+code+"). Verifique o idioma instalado ou configure o servidor.");}
    public void onReadyForSpeech(Bundle b){armTimeout();}public void onBeginningOfSpeech(){armTimeout();}
    public void onRmsChanged(float rms){}public void onBufferReceived(byte[] b){}public void onEndOfSpeech(){}public void onEvent(int type,Bundle b){}
}
