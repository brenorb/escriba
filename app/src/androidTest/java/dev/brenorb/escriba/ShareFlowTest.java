package dev.brenorb.escriba;
import android.test.ActivityInstrumentationTestCase2;
import android.content.*;
import android.net.Uri;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ScrollView;
import android.view.View;
import android.view.ViewGroup;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
public class ShareFlowTest extends ActivityInstrumentationTestCase2<MainActivity> {
    public ShareFlowTest(){super(MainActivity.class);}
    private ServerSocket server; private Thread handler; private volatile int requests; private volatile int delayMillis, responseCode=200, textRepeats=1, busyResponses; private final java.util.List<String> receivedModels=java.util.Collections.synchronizedList(new java.util.ArrayList<>());
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
            String multipart=new String(body,StandardCharsets.UTF_8);
            int modelStart=multipart.indexOf("name=\"model\"");
            if(modelStart>=0){modelStart=multipart.indexOf("\r\n\r\n",modelStart)+4;receivedModels.add(multipart.substring(modelStart,multipart.indexOf("\r\n",modelStart)));}
            requests++;
            if(delayMillis>0)try{Thread.sleep(delayMillis);}catch(InterruptedException ignored){}
            String json="{\"text\":\""+("Trecho "+requests+" transcrito. ").repeat(textRepeats)+"\"}";
            byte[] data=json.getBytes(StandardCharsets.UTF_8);
            s.getOutputStream().write(("HTTP/1.1 "+(requests<=busyResponses?503:responseCode)+" Test\r\nContent-Type: application/json\r\nContent-Length: "+data.length+"\r\nConnection: close\r\n\r\n").getBytes());s.getOutputStream().write(data);
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
        while(System.currentTimeMillis()<limit){String status=text(a,MainActivity.STATUS_ID);if(status.startsWith("Concluído"))return;if(status.startsWith("Não foi possível"))fail("Unexpected terminal error: "+status);Thread.sleep(100);}
        fail("Completion not reached: "+text(a,MainActivity.STATUS_ID));
    }
    public void testOpusAutoStartsAndPublishesOrderedChunks()throws Exception{
        textRepeats=100;delayMillis=300;MainActivity a=share("voice.opus");waitDone(a);
        Thread.sleep(500);
        assertEquals(3,requests);
        String result=text(a,MainActivity.TRANSCRIPT_ID);
        assertTrue(result.contains("Trecho 1 transcrito."));assertTrue(result.contains("Trecho 3 transcrito."));
        assertTrue(result.indexOf("Trecho 1")<result.indexOf("Trecho 3"));
        getInstrumentation().waitForIdleSync();
        getInstrumentation().runOnMainSync(()->assertEquals("Appending transcript must preserve the reader position",0,scroll(a.getWindow().getDecorView()).getScrollY()));
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
    private ScrollView scroll(View v){
        if(v instanceof ScrollView)return (ScrollView)v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){ScrollView s=scroll(((ViewGroup)v).getChildAt(i));if(s!=null)return s;}
        return null;
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
    public void testBusyServerRetriesWithoutUserInteraction()throws Exception{
        busyResponses=1;MainActivity a=share("short.mp3");waitDone(a);
        assertEquals("Retry exactly the busy request",2,requests);
        assertTrue(text(a,MainActivity.TRANSCRIPT_ID).contains("Trecho 2"));
        assertFalse(text(a,MainActivity.TRANSCRIPT_ID).contains("Trecho 1"));
    }
    public void testCancelWhileBackendBusyStopsRetries()throws Exception{
        responseCode=503;MainActivity a=share("short.mp3");waitRequest();
        getInstrumentation().runOnMainSync(()->button(a.getWindow().getDecorView(),"Parar").performClick());
        Thread.sleep(1800);
        assertTrue(text(a,MainActivity.STATUS_ID).startsWith("Interrompido"));
        assertEquals("No retry after cancellation",1,requests);assertEquals("",text(a,MainActivity.TRANSCRIPT_ID));
    }
    public void testCopyExportsCommittedTextWithoutTimestamps()throws Exception{
        MainActivity a=share("short.mp3");waitDone(a);
        String[] copied={""};
        getInstrumentation().runOnMainSync(()->{
            button(a.getWindow().getDecorView(),"Copiar").performClick();
            android.content.ClipboardManager manager=(android.content.ClipboardManager)a.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            copied[0]=manager.getPrimaryClip().getItemAt(0).getText().toString();
        });
        assertEquals("Trecho 1 transcrito.",copied[0]);
    }
    private android.view.accessibility.AccessibilityNodeInfo node(android.view.accessibility.AccessibilityNodeInfo root,String text){
        if(root==null)return null;
        if(text.equalsIgnoreCase(root.getText()==null?"":root.getText().toString()))return root;
        for(int i=0;i<root.getChildCount();i++){android.view.accessibility.AccessibilityNodeInfo n=node(root.getChild(i),text);if(n!=null)return n;}
        return null;
    }
    private android.view.accessibility.AccessibilityNodeInfo waitNode(String text)throws Exception{
        long limit=System.currentTimeMillis()+3000;
        while(System.currentTimeMillis()<limit){android.view.accessibility.AccessibilityNodeInfo n=node(getInstrumentation().getUiAutomation().getRootInActiveWindow(),text);if(n!=null){android.graphics.Rect bounds=new android.graphics.Rect();n.getBoundsInScreen(bounds);if(bounds.width()>0&&bounds.height()>0)return n;}Thread.sleep(50);}
        fail("Visible UI text not found: "+text);return null;
    }
    private void clickNode(android.view.accessibility.AccessibilityNodeInfo target){
        android.graphics.Rect bounds=new android.graphics.Rect();target.getBoundsInScreen(bounds);
        assertTrue(bounds.width()>0&&bounds.height()>0);long now=android.os.SystemClock.uptimeMillis();
        getInstrumentation().sendPointerSync(android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,bounds.exactCenterX(),bounds.exactCenterY(),0));
        getInstrumentation().sendPointerSync(android.view.MotionEvent.obtain(now,now+20,android.view.MotionEvent.ACTION_UP,bounds.exactCenterX(),bounds.exactCenterY(),0));
    }
    private void nodesOfClass(android.view.accessibility.AccessibilityNodeInfo root,String type,java.util.List<android.view.accessibility.AccessibilityNodeInfo> found){
        if(root==null)return;
        if(type.contentEquals(root.getClassName()))found.add(root);
        for(int i=0;i<root.getChildCount();i++)nodesOfClass(root.getChild(i),type,found);
    }
    public void testSettingsMenuIsReachable()throws Exception{
        getInstrumentation().getTargetContext().getSharedPreferences("settings",0).edit().putString("engine","native").putString("endpoint","").commit();
        setActivityIntent(new Intent(Intent.ACTION_MAIN));MainActivity a=getActivity();
        getInstrumentation().runOnMainSync(()->button(a.getWindow().getDecorView(),"⋮").performClick());
        getInstrumentation().waitForIdleSync();
        android.view.accessibility.AccessibilityNodeInfo settings=waitNode("Ajustes");
        android.graphics.Rect bounds=new android.graphics.Rect();settings.getBoundsInScreen(bounds);
        assertTrue("Settings menu item must be visible on screen",bounds.height()>0);
        clickNode(settings);
        getInstrumentation().waitForIdleSync();
        assertNotNull(waitNode("URL completa de transcrição"));assertNotNull(waitNode("Salvar"));
        java.util.List<android.view.accessibility.AccessibilityNodeInfo> spinners=new java.util.ArrayList<>();
        nodesOfClass(getInstrumentation().getUiAutomation().getRootInActiveWindow(),"android.widget.Spinner",spinners);
        assertEquals(2,spinners.size());clickNode(spinners.get(0));
        clickNode(waitNode("Servidor configurado"));getInstrumentation().waitForIdleSync();waitNode("URL completa de transcrição");
        java.util.List<android.view.accessibility.AccessibilityNodeInfo> fields=new java.util.ArrayList<>();
        nodesOfClass(getInstrumentation().getUiAutomation().getRootInActiveWindow(),"android.widget.EditText",fields);
        assertEquals("Language, URL, secret",3,fields.size());
        android.os.Bundle arguments=new android.os.Bundle();arguments.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"https://example.org/transcribe");
        assertTrue(fields.get(1).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,arguments));
        clickNode(waitNode("Salvar"));getInstrumentation().waitForIdleSync();
        assertEquals("remote",a.getSharedPreferences("settings",0).getString("engine",""));
        assertEquals("https://example.org/transcribe",a.getSharedPreferences("settings",0).getString("endpoint",""));
    }
    private double luminance(int color){
        double[] channels={android.graphics.Color.red(color)/255.0,android.graphics.Color.green(color)/255.0,android.graphics.Color.blue(color)/255.0};
        for(int i=0;i<3;i++)channels[i]=channels[i]<=0.03928?channels[i]/12.92:Math.pow((channels[i]+0.055)/1.055,2.4);
        return channels[0]*0.2126+channels[1]*0.7152+channels[2]*0.0722;
    }
    public void testNativeControlsHaveReadableDarkThemeContrast()throws Exception{
        android.app.UiModeManager mode=(android.app.UiModeManager)getInstrumentation().getTargetContext().getSystemService(android.content.Context.UI_MODE_SERVICE);
        mode.setApplicationNightMode(android.app.UiModeManager.MODE_NIGHT_YES);
        try{
            setActivityIntent(new Intent(Intent.ACTION_MAIN));MainActivity a=getActivity();
            getInstrumentation().waitForIdleSync();
            android.util.TypedValue value=new android.util.TypedValue();assertTrue(a.getTheme().resolveAttribute(android.R.attr.textColorPrimary,value,true));
            int color=value.resourceId!=0?a.getResources().getColorStateList(value.resourceId,a.getTheme()).getDefaultColor():value.data;
            double light=luminance(color),background=luminance(android.graphics.Color.parseColor("#141C19"));
            double ratio=(Math.max(light,background)+0.05)/(Math.min(light,background)+0.05);
            assertTrue("Native spinner text contrast must be >=4.5, actual="+ratio,ratio>=4.5);
        }finally{mode.setApplicationNightMode(android.app.UiModeManager.MODE_NIGHT_NO);}
    }
    private void waitRequests(int expected)throws Exception{
        long limit=System.currentTimeMillis()+5000;while(requests<expected&&System.currentTimeMillis()<limit)Thread.sleep(50);
        assertEquals("Expected automatic inference request",expected,requests);
    }
    private void selectModel(MainActivity activity,String id)throws Exception{
        getInstrumentation().runOnMainSync(()->button(activity.getWindow().getDecorView(),"⋮").performClick());
        waitNode("Usar "+ModelCatalog.labelAt(ModelCatalog.indexOf(id)));Thread.sleep(200);
        clickNode(waitNode("Usar "+ModelCatalog.labelAt(ModelCatalog.indexOf(id))));
        getInstrumentation().waitForIdleSync();
        long limit=System.currentTimeMillis()+1500;
        while(!id.equals(activity.getSharedPreferences("settings",0).getString("model",""))&&System.currentTimeMillis()<limit)Thread.sleep(20);
        assertEquals("Chosen menu model must persist",id,activity.getSharedPreferences("settings",0).getString("model",""));
    }
    public void testModelSelectionRetranscribesCurrentAudioAutomatically()throws Exception{
        MainActivity a=share("short.mp3");waitDone(a);
        assertEquals("parakeet-redux",receivedModels.get(0));
        selectModel(a,"parakeet-v3-int8");waitRequests(2);waitDone(a);
        assertEquals("parakeet-v3-int8",receivedModels.get(1));
    }
    public void testModelSelectionCancelsInFlightInference()throws Exception{
        delayMillis=2500;MainActivity a=share("short.mp3");waitRequest();
        selectModel(a,"parakeet-v3-int8");waitDone(a);
        assertEquals(2,requests);assertEquals("parakeet-v3-int8",receivedModels.get(1));
        assertFalse(text(a,MainActivity.TRANSCRIPT_ID).contains("Trecho 1"));assertTrue(text(a,MainActivity.TRANSCRIPT_ID).contains("Trecho 2"));
    }
    public void testReduxMigrationOccursOnlyOnce()throws Exception{
        android.content.SharedPreferences settings=getInstrumentation().getTargetContext().getSharedPreferences("settings",0);
        settings.edit().putString("model","parakeet-v3-int8").remove("compactModelMigrationDone").commit();
        setActivityIntent(new Intent(Intent.ACTION_MAIN));MainActivity a=getActivity();
        assertEquals("parakeet-redux",settings.getString("model",""));assertTrue(settings.getBoolean("compactModelMigrationDone",false));
        selectModel(a,"parakeet-v3-int8");
        assertEquals("parakeet-v3-int8",settings.getString("model",""));
        android.app.Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(MainActivity.class.getName(),null,false);
        android.app.Activity recreated=null;
        try{
            getInstrumentation().runOnMainSync(()->a.recreate());recreated=monitor.waitForActivityWithTimeout(3000);
            assertNotNull("Recreation must actually launch the next activity",recreated);getInstrumentation().waitForIdleSync();
            assertEquals("Explicit selection must survive later creations","parakeet-v3-int8",settings.getString("model",""));
        }finally{if(recreated!=null){android.app.Activity activity=recreated;getInstrumentation().runOnMainSync(activity::finish);}getInstrumentation().removeMonitor(monitor);}
    }
    private NativeEngine.Listener nativeUi(MainActivity activity)throws Exception{
        java.lang.reflect.Field field=MainActivity.class.getDeclaredField("nativeEngine");field.setAccessible(true);
        NativeEngine engine=(NativeEngine)field.get(activity);assertNotNull("Native engine must exist",engine);
        java.lang.reflect.Field listener=NativeEngine.class.getDeclaredField("listener");listener.setAccessible(true);
        return (NativeEngine.Listener)listener.get(engine);
    }
    private MainActivity nativeShare()throws Exception{
        getInstrumentation().getTargetContext().getSharedPreferences("settings",0).edit().putString("engine","native").commit();
        shell("pm grant dev.brenorb.escriba android.permission.RECORD_AUDIO");MainActivity activity=share("short.mp3");Thread.sleep(500);return activity;
    }
    public void testVoiceInstallStateSurvivesRedraw()throws Exception{
        MainActivity a=nativeShare();NativeEngine.Listener listener=nativeUi(a);
        int[] visible={-1};boolean[] enabled={false};
        getInstrumentation().runOnMainSync(()->{
            listener.modelUnavailable(true);
            a.onConfigurationChanged(new android.content.res.Configuration(a.getResources().getConfiguration()));
            Button install=button(a.getWindow().getDecorView(),"Instalar voz e continuar");
            if(install!=null){visible[0]=install.getVisibility();enabled[0]=install.isEnabled();}
        });
        assertEquals("Keep one-click installation reachable after redraw",View.VISIBLE,visible[0]);assertTrue(enabled[0]);
    }
    public void testVoiceDownloadCanBeStopped()throws Exception{
        MainActivity a=nativeShare();NativeEngine.Listener listener=nativeUi(a);
        boolean[] cancellable={false};
        getInstrumentation().runOnMainSync(()->{
            listener.modelUnavailable(true);listener.downloadStatus("Download em andamento",false);
            Button stop=button(a.getWindow().getDecorView(),"Parar");cancellable[0]=stop.isEnabled();
            stop.performClick();listener.modelReady();
        });
        assertTrue("Download must have an enabled cancel action",cancellable[0]);
        assertTrue(text(a,MainActivity.STATUS_ID).startsWith("Interrompido"));assertEquals(0,requests);
    }
    private void shell(String command)throws Exception{
        try(java.io.InputStream input=new android.os.ParcelFileDescriptor.AutoCloseInputStream(getInstrumentation().getUiAutomation().executeShellCommand(command))){input.readAllBytes();}
    }
    public void testLandscapeLeavesRoomForReading()throws Exception{
        MainActivity original=share("short.mp3");waitDone(original);
        android.app.Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(MainActivity.class.getName(),null,false);
        MainActivity current=original;
        try{
            shell("settings put system font_scale 1.5");
            android.app.Activity replacement=monitor.waitForActivityWithTimeout(2500);
            if(replacement!=null)current=(MainActivity)replacement;
            assertFalse("Measure the active Activity rather than a destroyed pre-font-scale instance",current.isDestroyed());
            MainActivity active=current;
            getInstrumentation().runOnMainSync(()->active.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            int[] dimensions=new int[3];long limit=System.currentTimeMillis()+5000;
            do{
                getInstrumentation().waitForIdleSync();
                getInstrumentation().runOnMainSync(()->{
                    View view=active.findViewById(MainActivity.TRANSCRIPT_ID);
                    while(view!=null&&!(view instanceof android.widget.ScrollView))view=view.getParent() instanceof View?(View)view.getParent():null;
                    if(view!=null)dimensions[0]=view.getHeight();dimensions[1]=active.getWindow().getDecorView().getHeight();dimensions[2]=active.getWindow().getDecorView().getWidth();
                });
                if(dimensions[2]>dimensions[1]&&active.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE)break;
                Thread.sleep(50);
            }while(System.currentTimeMillis()<limit);
            android.util.Log.i("EscribaAdversarial","font-scale recreation originalDestroyed="+original.isDestroyed()+" activeChanged="+(active!=original)+" oldDecor="+original.getWindow().getDecorView().getWidth()+"x"+original.getWindow().getDecorView().getHeight()+" actual="+dimensions[2]+"x"+dimensions[1]+" reading="+dimensions[0]);
            assertEquals("Actual landscape configuration required",android.content.res.Configuration.ORIENTATION_LANDSCAPE,active.getResources().getConfiguration().orientation);
            assertTrue("Actual landscape layout width must exceed height",dimensions[2]>dimensions[1]);
            assertTrue("Font scale must actually apply",active.getResources().getConfiguration().fontScale>=1.45f);
            assertTrue("Reading pane must occupy half landscape viewport; reading="+dimensions[0]+" viewport="+dimensions[1],dimensions[0]>=dimensions[1]/2);
        }finally{
            MainActivity active=current;
            getInstrumentation().runOnMainSync(()->active.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            shell("wm user-rotation lock 0");shell("settings put system font_scale 1.0");
            android.app.Activity reset=monitor.waitForActivityWithTimeout(2500);
            if(reset!=null)getInstrumentation().runOnMainSync(reset::finish);
            if(active!=original)getInstrumentation().runOnMainSync(active::finish);
            getInstrumentation().removeMonitor(monitor);getInstrumentation().waitForIdleSync();Thread.sleep(300);
        }
    }
    public void testNativeReconnectCanBeCancelledBeforeDelayedRetry()throws Exception{
        MainActivity a=nativeShare();NativeEngine.Listener listener=nativeUi(a);
        boolean[] enabled={false};
        getInstrumentation().runOnMainSync(()->{listener.modelUnavailable(true);listener.disconnected();Button stop=button(a.getWindow().getDecorView(),"Parar");enabled[0]=stop.isEnabled();stop.performClick();});
        assertTrue("Reconnect must expose an enabled Stop even after missing-model state",enabled[0]);Thread.sleep(1100);getInstrumentation().waitForIdleSync();
        assertTrue("Delayed reconnect must not restart after Stop",text(a,MainActivity.STATUS_ID).startsWith("Interrompido"));assertEquals(0,requests);
    }
    public void testVoiceDownloadWatchdogReleasesPendingState()throws Exception{
        int[] finished={0};boolean[] waiting={true};
        getInstrumentation().runOnMainSync(()->{
            try{
                NativeEngine engine=new NativeEngine(getInstrumentation().getTargetContext(),new NativeEngine.Listener(){
                    public void partial(String text){}public void segment(String text){}public void done(){}public void error(String text){}
                    public void downloadStatus(String text,boolean complete){if(complete)finished[0]++;}
                });
                java.lang.reflect.Field requested=NativeEngine.class.getDeclaredField("downloadRequested");requested.setAccessible(true);requested.setBoolean(engine,true);
                java.lang.reflect.Field timer=NativeEngine.class.getDeclaredField("downloadTimeout");timer.setAccessible(true);((Runnable)timer.get(engine)).run();
                waiting[0]=requested.getBoolean(engine);((Runnable)timer.get(engine)).run();
            }catch(Exception failure){throw new RuntimeException(failure);}
        });
        assertFalse("A silent provider must not keep installation pending forever",waiting[0]);assertEquals("Finish once, even if timer repeats",1,finished[0]);
    }
    public void testNativeEarlyCompletionIsRejected()throws Exception{
        int[] calls=new int[3];
        getInstrumentation().runOnMainSync(()->{
            NativeEngine engine=new NativeEngine(getInstrumentation().getTargetContext(),new NativeEngine.Listener(){
                public void partial(String text){}
                public void segment(String text){calls[0]++;}
                public void done(){calls[1]++;}
                public void error(String text){calls[2]++;}
            });
            android.os.Bundle result=new android.os.Bundle();
            result.putStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION,new java.util.ArrayList<>(java.util.Arrays.asList("Truncated result")));
            engine.onResults(result);
        });
        assertEquals("Do not commit an unconsumed audio source",0,calls[0]);
        assertEquals("Do not mark truncated source complete",0,calls[1]);
        assertEquals("Explain provider failure",1,calls[2]);
    }
    @Override protected void tearDown()throws Exception{server.close();handler.join(2000);super.tearDown();}
}
