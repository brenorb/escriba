package dev.brenorb.escriba;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.Locale;

public class MainActivity extends Activity {
    public static final int STATUS_ID=1001, TRANSCRIPT_ID=1002;
    private TextView status,transcript,partial,title;
    private Button copy,share,stop,retry;
    private Transcript state=new Transcript();
    private RemoteEngine remote;private NativeEngine nativeEngine;
    private long generation;private Uri currentUri;private boolean running;
    private SharedPreferences prefs;private int foreground,muted,background;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);prefs=getSharedPreferences("settings",MODE_PRIVATE);draw();
        if(saved!=null){
            String text=saved.getString("text","");for(String s:text.split("\n\n"))state.commit(s,-1,-1);
            title.setText(saved.getString("title","Transcrição"));
            String uri=saved.getString("uri");if(uri!=null)currentUri=Uri.parse(uri);
            status.setText(saved.getBoolean("running")?"Interrompido. Toque em Tentar novamente.":saved.getString("status","Pronto"));render();
        }else intake(getIntent());
    }
    private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
    private TextView label(String text,int size,int color){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);return v;}
    private Button button(String text,Runnable action){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setOnClickListener(v->action.run());return b;}
    private void draw(){
        boolean dark=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        background=Color.parseColor(dark?"#141C19":"#F7FAF7");foreground=Color.parseColor(dark?"#EDF3ED":"#1C2923");muted=Color.parseColor(dark?"#BAC8BE":"#53685D");
        getWindow().setStatusBarColor(background);getWindow().setNavigationBarColor(background);
        getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(background);
        root.setPadding(dp(20),dp(12),dp(20),dp(12));
        root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(dp(20)+i.left,dp(12)+i.top,dp(20)+i.right,dp(12)+i.bottom);return insets;});
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=label("escriba",26,foreground);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        Button overflow=button("⋮",this::menu);overflow.setContentDescription("Mais opções");header.addView(overflow);root.addView(header);
        title=label("Áudio vira leitura",25,foreground);title.setPadding(0,dp(24),0,dp(8));title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);root.addView(title);
        status=label("Compartilhe um áudio do WhatsApp, Telegram ou Signal. A transcrição começa ao chegar.",14,muted);status.setId(STATUS_ID);status.setPadding(0,0,0,dp(20));status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(status);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setGravity(Gravity.CENTER_HORIZONTAL);
        transcript=label("",20,foreground);transcript.setId(TRANSCRIPT_ID);transcript.setTextIsSelectable(true);transcript.setLineSpacing(dp(6),1.1f);transcript.setMaxWidth(dp(680));
        page.addView(transcript,new LinearLayout.LayoutParams(-1,-2));
        partial=label("",18,muted);partial.setPadding(0,dp(16),0,dp(24));partial.setLineSpacing(dp(5),1.1f);page.addView(partial,new LinearLayout.LayoutParams(-1,-2));
        scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);
        copy=button("Copiar",()->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Transcrição",state.copyText()));Toast.makeText(this,"Texto copiado",Toast.LENGTH_SHORT).show();});
        share=button("Enviar",()->startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,state.copyText()),"Enviar transcrição")));
        stop=button("Parar",()->{cancel();state.partial("");status.setText("Interrompido. Texto já concluído preservado.");render();});
        controls.addView(copy,new LinearLayout.LayoutParams(0,-2,1));controls.addView(share,new LinearLayout.LayoutParams(0,-2,1));controls.addView(stop,new LinearLayout.LayoutParams(0,-2,1));root.addView(controls);
        retry=button("Escolher áudio",()->{if(currentUri!=null)begin();else pick();});root.addView(retry);
        setContentView(root);render();
    }
    private void menu(){PopupMenu menu=new PopupMenu(this,findViewById(android.R.id.content));menu.getMenu().add("Ajustes");menu.getMenu().add("Escolher outro áudio");menu.getMenu().add("Limpar texto");menu.setOnMenuItemClickListener(item->{switch(item.getTitle().toString()){case "Ajustes":settings();break;case "Escolher outro áudio":pick();break;default:cancel();state=new Transcript();currentUri=null;title.setText("Áudio vira leitura");status.setText("Texto limpo.");render();}return true;});menu.show();}
    private void pick(){startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"audio/*","video/*","application/ogg","application/octet-stream"}),11);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==11&&result==RESULT_OK&&data!=null){intake(new Intent(Intent.ACTION_VIEW).setData(data.getData()).setType(getContentResolver().getType(data.getData())));}}
    @Override public void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);intake(intent);}
    private void intake(Intent intent){
        if(intent==null)return;
        Uri uri=null;
        if(Intent.ACTION_SEND.equals(intent.getAction())){uri=intent.getParcelableExtra(Intent.EXTRA_STREAM,Uri.class);if(uri==null&&intent.getClipData()!=null&&intent.getClipData().getItemCount()==1)uri=intent.getClipData().getItemAt(0).getUri();}
        else if(Intent.ACTION_VIEW.equals(intent.getAction()))uri=intent.getData();
        else if(Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())){cancel();status.setText("Compartilhe um áudio por vez.");return;}
        if(uri==null){if(Intent.ACTION_SEND.equals(intent.getAction())||Intent.ACTION_VIEW.equals(intent.getAction())){cancel();status.setText("O compartilhamento não contém um arquivo de áudio.");}return;}
        cancel();state=new Transcript();currentUri=null;render();
        if(!"content".equals(uri.getScheme())&&!"file".equals(uri.getScheme())){status.setText("Abra um arquivo local de áudio.");return;}
        String name=uri.getLastPathSegment();
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()){int idx=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(idx>=0)name=c.getString(idx);}}
        catch(Exception ignored){}
        if(!InputPolicy.accepts(intent.getType(),name)){status.setText("Formato não reconhecido. Compartilhe Opus, Ogg, MP3, M4A, WAV, FLAC ou vídeo.");return;}
        currentUri=uri;title.setText(name==null?"Transcrição":name);begin();
    }
    private void begin(){
        if(currentUri==null)return;cancel();state=new Transcript();running=true;render();status.setText("Transcrevendo… o texto aparece por trechos.");
        long ticket=generation;String engine=prefs.getString("engine","native"),language=prefs.getString("language","pt-BR");
        if(engine.equals("remote")){
            remote=new RemoteEngine();remote.start(getApplicationContext(),currentUri,prefs.getString("endpoint",""),prefs.getString("token",""),prefs.getString("model","parakeet-v3-int8"),language.split("-")[0],new RemoteEngine.Listener(){
                public void segment(String text,double start,double end){runOnUiThread(()->{if(ticket!=generation)return;state.commit(text,start,end);render();});}
                public void done(){runOnUiThread(()->complete(ticket));}
                public void error(String text){runOnUiThread(()->failed(ticket,text));}
            });
        }else{
            if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){running=false;status.setText("O serviço Android exige permissão de microfone mesmo para um arquivo. O app não grava áudio.");requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},12);render();return;}
            nativeEngine=new NativeEngine(getApplicationContext(),new NativeEngine.Listener(){
                public void partial(String text){if(ticket==generation){state.partial(text);render();}}
                public void segment(String text){if(ticket==generation){state.commit(text,-1,-1);render();}}
                public void done(){complete(ticket);}public void error(String text){failed(ticket,text);}
            });nativeEngine.start(currentUri,language);
        }
    }
    private void complete(long ticket){if(ticket!=generation)return;running=false;state.partial("");status.setText(state.copyText().isEmpty()?"Concluído. Nenhuma fala reconhecida.":"Concluído · "+state.segments().size()+" trechos");render();}
    private void failed(long ticket,String message){if(ticket!=generation)return;running=false;state.partial("");status.setText("Não foi possível transcrever. "+message);render();}
    private void cancel(){generation++;running=false;if(remote!=null){remote.cancel();remote=null;}if(nativeEngine!=null){nativeEngine.cancel();nativeEngine=null;}}
    private void render(){if(transcript==null)return;StringBuilder b=new StringBuilder();for(Transcript.Segment s:state.segments()){if(b.length()>0)b.append("\n\n");if(s.start()>=0)b.append(time(s.start())).append(" – ").append(time(s.end())).append("\n");b.append(s.text());}transcript.setText(b.toString());partial.setText(state.provisional().isEmpty()?"":"Prévia · "+state.provisional());copy.setEnabled(!state.copyText().isEmpty());share.setEnabled(!state.copyText().isEmpty());stop.setEnabled(running);retry.setEnabled(!running);retry.setText(currentUri==null?"Escolher áudio":"Tentar novamente");}
    private String time(double seconds){int s=(int)seconds;return String.format(Locale.ROOT,"%02d:%02d",s/60,s%60);}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==12){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)begin();else{status.setText("Permissão negada. Configure o servidor em Ajustes para transcrever sem microfone.");render();}}}
    private EditText edit(LinearLayout box,String name,String value,boolean secret){box.addView(label(name,14,muted));EditText field=new EditText(this);field.setText(value);field.setTextColor(foreground);field.setSingleLine(true);field.setInputType(secret?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_CLASS_TEXT);box.addView(field);return field;}
    private void settings(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(24),dp(8),dp(24),dp(8));box.setBackgroundColor(background);
        box.addView(label("Android local não envia áudio. Servidor envia os trechos ao endereço escolhido, por HTTPS.",14,muted));
        Spinner engine=new Spinner(this);engine.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Android local (offline)","Servidor configurado"}));engine.setSelection(prefs.getString("engine","native").equals("remote")?1:0);box.addView(engine);
        EditText language=edit(box,"Idioma (pt-BR, en-US…)",prefs.getString("language","pt-BR"),false);
        EditText endpoint=edit(box,"URL completa de transcrição",prefs.getString("endpoint",""),false);
        EditText token=edit(box,"Chave / token (opcional)",prefs.getString("token",""),true);
        EditText model=edit(box,"Modelo do endpoint",prefs.getString("model","parakeet-v3-int8"),false);
        ScrollView scroll=new ScrollView(this);scroll.addView(box);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Ajustes").setView(scroll).setNegativeButton("Cancelar",null).setPositiveButton("Salvar",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(view->{
            String url=endpoint.getText().toString().trim(),lang=language.getText().toString().trim(),m=model.getText().toString().trim(),key=token.getText().toString().trim();
            if(engine.getSelectedItemPosition()==1&&(!EndpointPolicy.valid(url)||(!url.startsWith("https://")&&(getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)==0))){endpoint.setError("Use HTTPS");return;}
            if(!lang.matches("[a-zA-Z]{2,3}(-[a-zA-Z]{2,4})?")){language.setError("Exemplo: pt-BR");return;}
            if(m.isEmpty()||m.contains("\n")||m.contains("\r")){model.setError("Informe um modelo válido");return;}
            if(key.contains("\n")||key.contains("\r")){token.setError("Chave inválida");return;}
            prefs.edit().putString("engine",engine.getSelectedItemPosition()==1?"remote":"native").putString("language",lang).putString("endpoint",url).putString("token",key).putString("model",m).apply();dialog.dismiss();
            if(currentUri!=null&&!running)begin();
        }));dialog.show();
    }
    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);String t=title.getText().toString(),s=status.getText().toString();draw();title.setText(t);status.setText(s);}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("text",state.copyText());out.putString("title",title.getText().toString());out.putString("status",status.getText().toString());out.putBoolean("running",running);if(currentUri!=null)out.putString("uri",currentUri.toString());}
    @Override protected void onDestroy(){cancel();super.onDestroy();}
}
