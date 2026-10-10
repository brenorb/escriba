package dev.brenorb.escriba;
import android.test.ActivityInstrumentationTestCase2;
import android.content.*;
import android.net.Uri;
import android.widget.TextView;
import android.widget.Button;
import android.view.View;
import android.view.ViewGroup;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
public class ShareFlowTest extends ActivityInstrumentationTestCase2<MainActivity> {
    public ShareFlowTest(){super(MainActivity.class);}
    private ServerSocket server; private Thread handler; private volatile int requests; private volatile int delayMillis, responseCode=200;
    @Override protected void setUp() throws Exception {
        super.setUp();
        server=new ServerSocket(0,8,InetAddress.getByName("127.0.0.1"));
        getInstrumentation().getTargetContext().getSharedPreferences("settings",0).edit().clear().putString("engine","remote").putString("endpoint","http://127.0.0.1:"+server.getLocalPort()+"/transcribe").commit();
        handler=new Thread(()->{while(!server.isClosed())try(Socket s=server.accept()) {
            InputStream in=s.getInputStream();ByteArrayOutputStream header=new ByteArrayOutputStream();int b;
            while((b=in.read())!=-1){header.write(b);if(header.toString().endsWith("\r\n\r\n"))break;}
            int length=0;for(String line:header.toString().split("\r\n"))if(line.toLowerCase().startsWith("content-length:"))length=Integer.parseInt(line.substring(15).trim());
            byte[] body=in.readNBytes(length);
            if(body.length!=length)throw new IOException("Truncated body");
            requests++;
            if(delayMillis>0)try{Thread.sleep(delayMillis);}catch(InterruptedException ignored){}
            String json="{\"text\":\"Trecho "+requests+" transcrito.\"}";
            byte[] data=json.getBytes(StandardCharsets.UTF_8);
            s.getOutputStream().write(("HTTP/1.1 "+responseCode+" Test\r\nContent-Type: application/json\r\nContent-Length: "+data.length+"\r\nConnection: close\r\n\r\n").getBytes());s.getOutputStream().write(data);
        }catch(IOException ignored){}},"fixture-server");handler.start();
    }
    private void fixture(String name)throws Exception{
        File f=new File(getInstrumentation().getTargetContext().getCacheDir(),name);
        try(InputStream in=getInstrumentation().getContext().getAssets().open(name);OutputStream out=new FileOutputStream(f)){in.transferTo(out);}
    }
    private MainActivity share(String name)throws Exception{
        fixture(name);
        setActivityIntent(new Intent(Intent.ACTION_SEND).setType("application/octet-stream").putExtra(Intent.EXTRA_STREAM,Uri.parse("content://dev.brenorb.escriba.fixture/"+name)));
        return getActivity();
    }
    private String text(MainActivity a,int id){final String[] s={""};getInstrumentation().runOnMainSync(()->s[0]=((TextView)a.findViewById(id)).getText().toString());return s[0];}
    private void waitDone(MainActivity a)throws Exception{
        long limit=System.currentTimeMillis()+30000;
        while(System.currentTimeMillis()<limit){if(text(a,MainActivity.STATUS_ID).startsWith("Concluído"))return;Thread.sleep(100);}
        fail("Completion not reached: "+text(a,MainActivity.STATUS_ID));
    }
    public void testOpusAutoStartsAndPublishesOrderedChunks()throws Exception{
        MainActivity a=share("voice.opus");waitDone(a);
        assertEquals(3,requests);
        String result=text(a,MainActivity.TRANSCRIPT_ID);
        assertTrue(result.contains("Trecho 1 transcrito."));assertTrue(result.contains("Trecho 3 transcrito."));
        assertTrue(result.indexOf("Trecho 1")<result.indexOf("Trecho 3"));
    }
    public void testMp3AndNewShareReplacePreviousText()throws Exception{
        MainActivity a=share("short.mp3");waitDone(a);assertEquals(1,requests);
        fixture("short.m4a");
        getInstrumentation().runOnMainSync(()->a.onNewIntent(new Intent(Intent.ACTION_SEND).setType("audio/mp4").putExtra(Intent.EXTRA_STREAM,Uri.parse("content://dev.brenorb.escriba.fixture/short.m4a"))));
        waitDone(a);assertEquals(2,requests);assertFalse(text(a,MainActivity.TRANSCRIPT_ID).contains("Trecho 1 transcrito."));
    }
    public void testCorruptAudioShowsRecoverableError()throws Exception{
        MainActivity a=share("corrupt.opus");
        long limit=System.currentTimeMillis()+15000;
        while(System.currentTimeMillis()<limit && !text(a,MainActivity.STATUS_ID).startsWith("Não foi possível"))Thread.sleep(100);
        assertTrue(text(a,MainActivity.STATUS_ID).startsWith("Não foi possível"));assertEquals(0,requests);
    }
    private void waitRequest()throws Exception{
        long limit=System.currentTimeMillis()+10000;
        while(requests==0 && System.currentTimeMillis()<limit)Thread.sleep(50);
        assertTrue("Request never started",requests>0);
    }
    private Button button(View v,String label){
        if(v instanceof Button && ((Button)v).getText().toString().equals(label))return (Button)v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Button b=button(((ViewGroup)v).getChildAt(i),label);if(b!=null)return b;}
        return null;
    }
    public void testDocumentPickerResultStartsAutomatically()throws Exception{
        fixture("short.mp3");setActivityIntent(new Intent(Intent.ACTION_MAIN));MainActivity a=getActivity();
        getInstrumentation().runOnMainSync(()->a.onActivityResult(11,android.app.Activity.RESULT_OK,new Intent().setData(Uri.parse("content://dev.brenorb.escriba.fixture/short.mp3"))));
        waitDone(a);assertEquals(1,requests);
    }
    public void testClipDataOnlyShareStartsAutomatically()throws Exception{
        fixture("short.mp3");
        Intent intent=new Intent(Intent.ACTION_SEND).setType("audio/mpeg");
        intent.setClipData(ClipData.newUri(getInstrumentation().getTargetContext().getContentResolver(),"audio",Uri.parse("content://dev.brenorb.escriba.fixture/short.mp3")));
        setActivityIntent(intent);MainActivity a=getActivity();waitDone(a);
        assertEquals(1,requests);assertTrue(text(a,MainActivity.TRANSCRIPT_ID).contains("Trecho 1"));
    }
    public void testCancelDuringSlowResponseRejectsLateText()throws Exception{
        delayMillis=1200;MainActivity a=share("short.mp3");waitRequest();
        getInstrumentation().runOnMainSync(()->{Button b=button(a.getWindow().getDecorView(),"Parar");assertNotNull(b);b.performClick();});
        Thread.sleep(1800);
        assertTrue(text(a,MainActivity.STATUS_ID).startsWith("Interrompido"));
        assertEquals("",text(a,MainActivity.TRANSCRIPT_ID));assertEquals(1,requests);
    }
    public void testNewShareDuringSlowResponseRejectsPreviousText()throws Exception{
        delayMillis=1200;MainActivity a=share("short.mp3");waitRequest();fixture("short.m4a");
        getInstrumentation().runOnMainSync(()->a.onNewIntent(new Intent(Intent.ACTION_SEND).setType("audio/mp4").putExtra(Intent.EXTRA_STREAM,Uri.parse("content://dev.brenorb.escriba.fixture/short.m4a"))));
        waitDone(a);assertEquals(2,requests);
        assertFalse(text(a,MainActivity.TRANSCRIPT_ID).contains("Trecho 1"));assertTrue(text(a,MainActivity.TRANSCRIPT_ID).contains("Trecho 2"));
    }
    public void testUnauthorizedResponseHasNoTranscript()throws Exception{
        responseCode=401;MainActivity a=share("short.mp3");
        long limit=System.currentTimeMillis()+15000;
        while(System.currentTimeMillis()<limit && !text(a,MainActivity.STATUS_ID).startsWith("Não foi possível"))Thread.sleep(50);
        assertTrue(text(a,MainActivity.STATUS_ID).contains("HTTP 401"));assertEquals("",text(a,MainActivity.TRANSCRIPT_ID));
    }
    @Override protected void tearDown()throws Exception{server.close();handler.join(2000);super.tearDown();}
}
