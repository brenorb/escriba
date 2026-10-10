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
  public static final int STATUS_ID = 1001, TRANSCRIPT_ID = 1002;
  private TextView status, transcript, partial, title;
  private Button copy, share, stop, retry, overflow, downloadVoice;
  private Transcript state = new Transcript();
  private RemoteEngine remote;
  private NativeEngine nativeEngine;
  private long generation;
  private int nativeRetries;
  private final Handler reconnect = new Handler(Looper.getMainLooper());
  private Uri currentUri;
  private boolean running;
  private boolean voiceVisible, voiceDownloadable, voiceInstalling;
  private String voiceAction = "Instalar voz e continuar";
  private SharedPreferences prefs;
  private int foreground, muted, background;

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    prefs = getSharedPreferences("settings", MODE_PRIVATE);
    if (!prefs.getBoolean("compactModelMigrationDone", false)) {
      SharedPreferences.Editor migration =
          prefs.edit().putBoolean("compactModelMigrationDone", true);
      if ("parakeet-v3-int8".equals(prefs.getString("model", ""))) {
        migration.putString("model", ModelCatalog.defaultId());
      }
      migration.apply();
    }
    draw();
    if (saved != null) {
      String text = saved.getString("text", "");
      for (String s : text.split("\n\n")) state.commit(s, -1, -1);
      title.setText(saved.getString("title", "Transcrição"));
      String uri = saved.getString("uri");
      if (uri != null) currentUri = Uri.parse(uri);
      status.setText(
          saved.getBoolean("running")
              ? "Interrompido. Toque em Tentar novamente."
              : saved.getString("status", "Pronto"));
      render();
    } else intake(getIntent());
  }

  private int dp(int value) {
    return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
  }

  private TextView label(String text, int size, int color) {
    TextView v = new TextView(this);
    v.setText(text);
    v.setTextSize(size);
    v.setTextColor(color);
    return v;
  }

  private Button button(String text, Runnable action) {
    Button b = new Button(this);
    b.setText(text);
    b.setAllCaps(false);
    styleSecondary(b);
    b.setOnClickListener(v -> action.run());
    return b;
  }

  private void draw() {
    boolean dark =
        (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            == Configuration.UI_MODE_NIGHT_YES;
    setTheme(dark ? android.R.style.Theme_Material_NoActionBar : R.style.AppTheme);
    background = Color.parseColor(dark ? "#141C19" : "#F7FAF7");
    foreground = Color.parseColor(dark ? "#EDF3ED" : "#1C2923");
    muted = Color.parseColor(dark ? "#BAC8BE" : "#53685D");
    getWindow().setStatusBarColor(background);
    getWindow().setNavigationBarColor(background);
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            dark
                ? 0
                : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    LinearLayout root = new LinearLayout(this);
    boolean landscape =
        getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    root.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
    root.setBackgroundColor(background);
    root.setPadding(dp(20), dp(12), dp(20), dp(12));
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars());
          v.setPadding(dp(20) + i.left, dp(12) + i.top, dp(20) + i.right, dp(12) + i.bottom);
          return insets;
        });
    LinearLayout panel = root;
    if (landscape) {
      panel = new LinearLayout(this);
      panel.setOrientation(LinearLayout.VERTICAL);
      ScrollView sidebar = new ScrollView(this);
      sidebar.setFillViewport(true);
      sidebar.addView(panel);
      int sideWidth =
          Math.min(dp(300), (int) (getResources().getDisplayMetrics().widthPixels * 0.4f));
      LinearLayout.LayoutParams sideParams = new LinearLayout.LayoutParams(sideWidth, -1);
      sideParams.rightMargin = dp(20);
      root.addView(sidebar, sideParams);
    }
    LinearLayout header = new LinearLayout(this);
    header.setGravity(Gravity.CENTER_VERTICAL);
    TextView brand = label("escriba", 26, foreground);
    header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
    overflow = button("⋮", this::menu);
    overflow.setContentDescription("Mais opções");
    overflow.setMinWidth(dp(48));
    overflow.setMinimumWidth(dp(48));
    overflow.setPadding(dp(12), 0, dp(12), 0);
    header.addView(overflow, new LinearLayout.LayoutParams(dp(48), dp(48)));
    panel.addView(header);
    title = label("Áudio vira leitura", 25, foreground);
    title.setPadding(0, dp(24), 0, dp(8));
    title.setMaxLines(2);
    title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    panel.addView(title);
    status =
        label(
            "Compartilhe um áudio do WhatsApp, Telegram ou Signal. A transcrição começa ao chegar.",
            14,
            muted);
    status.setId(STATUS_ID);
    android.graphics.drawable.GradientDrawable statusCard =
        new android.graphics.drawable.GradientDrawable();
    statusCard.setColor(Color.parseColor(dark ? "#202B25" : "#EAF2EC"));
    statusCard.setCornerRadius(dp(18));
    status.setBackground(statusCard);
    status.setPadding(dp(16), dp(14), dp(16), dp(14));
    LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
    statusParams.bottomMargin = dp(18);
    status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    panel.addView(status, statusParams);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    LinearLayout page = new LinearLayout(this);
    page.setOrientation(LinearLayout.VERTICAL);
    page.setGravity(Gravity.CENTER_HORIZONTAL);
    transcript = label("", 20, foreground);
    transcript.setId(TRANSCRIPT_ID);
    transcript.setTextIsSelectable(true);
    transcript.setLineSpacing(dp(6), 1.1f);
    transcript.setMaxWidth(dp(680));
    page.addView(transcript, new LinearLayout.LayoutParams(-1, -2));
    partial = label("", 18, muted);
    partial.setPadding(0, dp(16), 0, dp(24));
    partial.setLineSpacing(dp(5), 1.1f);
    page.addView(partial, new LinearLayout.LayoutParams(-1, -2));
    scroll.addView(page);
    if (landscape) root.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));
    else root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout controls = new LinearLayout(this);
    controls.setGravity(Gravity.CENTER);
    copy =
        button(
            "Copiar",
            () -> {
              ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                  .setPrimaryClip(ClipData.newPlainText("Transcrição", state.copyText()));
              Toast.makeText(this, "Texto copiado", Toast.LENGTH_SHORT).show();
            });
    share =
        button(
            "Enviar",
            () ->
                startActivity(
                    Intent.createChooser(
                        new Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, state.copyText()),
                        "Enviar transcrição")));
    stop =
        button(
            "Parar",
            () -> {
              cancel();
              state.partial("");
              status.setText("Interrompido. Texto já concluído preservado.");
              render();
            });
    for (Button action : new Button[] {copy, share, stop}) {
      LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, -2, 1);
      actionParams.setMargins(dp(3), 0, dp(3), dp(8));
      controls.addView(action, actionParams);
    }
    panel.addView(controls);
    retry =
        button(
            "Escolher áudio",
            () -> {
              if (currentUri != null) begin();
              else pick();
            });
    stylePrimary(retry);
    panel.addView(retry);
    downloadVoice =
        button(
            "Instalar voz e continuar",
            () -> {
              if (nativeEngine == null) return;
              showDownloadStatus("Pedindo ao Android para instalar a voz…", false);
              nativeEngine.downloadModel();
            });
    stylePrimary(downloadVoice);
    panel.addView(downloadVoice);
    setContentView(root);
    render();
  }

  private android.graphics.drawable.GradientDrawable rounded(int color) {
    android.graphics.drawable.GradientDrawable shape =
        new android.graphics.drawable.GradientDrawable();
    shape.setColor(color);
    shape.setCornerRadius(dp(18));
    return shape;
  }

  private void styleControl(Button button, int fill, int text, int disabledFill, int disabledText) {
    android.graphics.drawable.StateListDrawable colors =
        new android.graphics.drawable.StateListDrawable();
    colors.addState(new int[] {-android.R.attr.state_enabled}, rounded(disabledFill));
    colors.addState(new int[] {}, rounded(fill));
    android.content.res.ColorStateList ink =
        new android.content.res.ColorStateList(
            new int[][] {new int[] {-android.R.attr.state_enabled}, new int[] {}},
            new int[] {disabledText, text});
    button.setBackground(
        new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(Color.parseColor("#306F9B7F")),
            colors,
            rounded(Color.WHITE)));
    button.setTextColor(ink);
    button.setMinHeight(dp(54));
    button.setPadding(dp(8), dp(8), dp(8), dp(8));
  }

  private void styleSecondary(Button button) {
    boolean dark = background == Color.parseColor("#141C19");
    styleControl(
        button,
        Color.parseColor(dark ? "#26382E" : "#E5EEE8"),
        foreground,
        Color.parseColor(dark ? "#202B25" : "#EDF1EE"),
        Color.parseColor(dark ? "#778B7E" : "#87948C"));
  }

  private void stylePrimary(Button button) {
    boolean dark = background == Color.parseColor("#141C19");
    styleControl(
        button,
        Color.parseColor("#246B4C"),
        Color.WHITE,
        Color.parseColor(dark ? "#26382E" : "#DFE6E1"),
        Color.parseColor(dark ? "#88998F" : "#6E7F75"));
    button.setPadding(dp(20), dp(8), dp(20), dp(8));
  }

  private void menu() {
    PopupMenu menu = new PopupMenu(this, overflow);
    menu.getMenu().add("Ajustes");
    for (int i = 0; i < ModelCatalog.ids().length; i++) {
      int index = i;
      menu.getMenu()
          .add("Usar " + ModelCatalog.labelAt(i))
          .setCheckable(true)
          .setChecked(
              ModelCatalog.idAt(i).equals(prefs.getString("model", ModelCatalog.defaultId())))
          .setOnMenuItemClickListener(
              item -> {
                chooseModel(ModelCatalog.idAt(index));
                return true;
              });
    }
    menu.getMenu().add("Escolher outro áudio");
    menu.getMenu().add("Limpar texto");
    menu.setOnMenuItemClickListener(
        item -> {
          switch (item.getTitle().toString()) {
            case "Ajustes":
              settings();
              break;
            case "Escolher outro áudio":
              pick();
              break;
            default:
              cancel();
              state = new Transcript();
              currentUri = null;
              title.setText("Áudio vira leitura");
              status.setText("Texto limpo.");
              render();
          }
          return true;
        });
    menu.show();
  }

  private void chooseModel(String model) {
    SharedPreferences.Editor edit = prefs.edit().putString("model", model);
    boolean serverReady = EndpointPolicy.valid(prefs.getString("endpoint", ""));
    if (serverReady) edit.putString("engine", "remote");
    edit.apply();
    String message =
        serverReady
            ? ModelCatalog.labelAt(ModelCatalog.indexOf(model))
                + " selecionado. O servidor baixa o modelo na primeira transcrição."
            : ""
                + ModelCatalog.labelAt(ModelCatalog.indexOf(model))
                + " pré-selecionado. Configure o servidor HTTPS em Ajustes uma vez.";
    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    if (serverReady && currentUri != null) begin();
    else if (!running) status.setText(message);
  }

  private void pick() {
    startActivityForResult(
        new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .putExtra(
                Intent.EXTRA_MIME_TYPES,
                new String[] {"audio/*", "video/*", "application/ogg", "application/octet-stream"}),
        11);
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == 11 && result == RESULT_OK && data != null) {
      intake(
          new Intent(Intent.ACTION_VIEW)
              .setDataAndType(data.getData(), getContentResolver().getType(data.getData())));
    }
  }

  @Override
  public void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    intake(intent);
  }

  private void intake(Intent intent) {
    if (intent == null) return;
    Uri uri = null;
    if (Intent.ACTION_SEND.equals(intent.getAction())) {
      uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
      if (uri == null && intent.getClipData() != null && intent.getClipData().getItemCount() == 1)
        uri = intent.getClipData().getItemAt(0).getUri();
    } else if (Intent.ACTION_VIEW.equals(intent.getAction())) uri = intent.getData();
    else if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
      cancel();
      status.setText("Compartilhe um áudio por vez.");
      render();
      return;
    }
    if (uri == null) {
      if (Intent.ACTION_SEND.equals(intent.getAction())
          || Intent.ACTION_VIEW.equals(intent.getAction())) {
        cancel();
        status.setText("O compartilhamento não contém um arquivo de áudio.");
        render();
      }
      return;
    }
    cancel();
    state = new Transcript();
    currentUri = null;
    render();
    if (!"content".equals(uri.getScheme()) && !"file".equals(uri.getScheme())) {
      status.setText("Abra um arquivo local de áudio.");
      return;
    }
    String name = uri.getLastPathSegment();
    try (Cursor c =
        getContentResolver()
            .query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
      if (c != null && c.moveToFirst()) {
        int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        if (idx >= 0) name = c.getString(idx);
      }
    } catch (Exception ignored) {
    }
    if (!InputPolicy.accepts(intent.getType(), name)) {
      status.setText(
          "Formato não reconhecido. Compartilhe Opus, Ogg, MP3, M4A, WAV, FLAC ou vídeo.");
      return;
    }
    currentUri = uri;
    title.setText(name == null ? "Transcrição" : name);
    begin();
  }

  private void begin() {
    begin(false);
  }

  private void begin(boolean reconnecting) {
    if (!reconnecting) nativeRetries = 0;
    if (currentUri == null) return;
    cancel();
    state = new Transcript();
    running = true;
    render();
    status.setText("Transcrevendo… o texto aparece por trechos.");
    long ticket = generation;
    String engine = prefs.getString("engine", "native"),
        language = prefs.getString("language", "pt-BR");
    if (engine.equals("remote")) {
      remote = new RemoteEngine();
      remote.start(
          getApplicationContext(),
          currentUri,
          prefs.getString("endpoint", ""),
          prefs.getString("token", ""),
          prefs.getString("model", ModelCatalog.defaultId()),
          language.split("-")[0],
          new RemoteEngine.Listener() {
            public void segment(String text, double start, double end) {
              runOnUiThread(
                  () -> {
                    if (ticket != generation) return;
                    state.commit(text, start, end);
                    render();
                  });
            }

            public void status(String text) {
              runOnUiThread(
                  () -> {
                    if (ticket == generation) status.setText(text);
                  });
            }

            public void done() {
              runOnUiThread(() -> complete(ticket));
            }

            public void error(String text) {
              runOnUiThread(() -> failed(ticket, text));
            }
          });
    } else {
      if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
          != PackageManager.PERMISSION_GRANTED) {
        running = false;
        status.setText(
            "O serviço Android exige permissão de microfone mesmo para um arquivo. O app não grava"
                + " áudio.");
        requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, 12);
        render();
        return;
      }
      nativeEngine =
          new NativeEngine(
              getApplicationContext(),
              new NativeEngine.Listener() {
                public void partial(String text) {
                  if (ticket == generation) {
                    state.partial(text);
                    render();
                  }
                }

                public void segment(String text) {
                  if (ticket == generation) {
                    state.commit(text, -1, -1);
                    render();
                  }
                }

                public void done() {
                  complete(ticket);
                }

                public void error(String text) {
                  failed(ticket, text);
                }

                public void disconnected() {
                  runOnUiThread(
                      () -> {
                        if (ticket != generation) return;
                        voiceVisible = voiceDownloadable = voiceInstalling = false;
                        running = true;
                        render();
                        if (nativeRetries++ >= 3) {
                          failed(
                              ticket,
                              "O serviço Android continua indisponível. Tente novamente ou"
                                  + " configure o servidor.");
                          return;
                        }
                        status.setText("Reconectando ao serviço de voz Android…");
                        reconnect.postDelayed(
                            () -> {
                              if (ticket == generation) begin(true);
                            },
                            750);
                      });
                }

                public void modelUnavailable(boolean canDownload) {
                  runOnUiThread(
                      () -> {
                        if (ticket != generation) return;
                        showVoiceUnavailable(canDownload);
                      });
                }

                public void downloadStatus(String text, boolean finished) {
                  runOnUiThread(
                      () -> {
                        if (ticket != generation) return;
                        showDownloadStatus(text, finished);
                      });
                }

                public void modelReady() {
                  runOnUiThread(
                      () -> {
                        if (ticket != generation) return;
                        voiceVisible = voiceDownloadable = voiceInstalling = false;
                        running = true;
                        status.setText("Voz pronta. Continuando a transcrição…");
                        render();
                      });
                }
              });
      nativeEngine.start(currentUri, language);
    }
  }

  private void showVoiceUnavailable(boolean canDownload) {
    running = false;
    voiceVisible = true;
    voiceDownloadable = canDownload;
    voiceInstalling = false;
    voiceAction = canDownload ? "Instalar voz e continuar" : "Voz indisponível neste aparelho";
    status.setText(
        canDownload
            ? "O Android pode baixar o idioma escolhido. Toque uma vez; quando terminar, a"
                + " transcrição continua sozinha."
            : "O serviço de voz deste aparelho não oferece o idioma escolhido offline. Configure o"
                + " servidor em Ajustes.");
    render();
  }

  private void showDownloadStatus(String text, boolean finished) {
    voiceVisible = true;
    voiceDownloadable = true;
    voiceInstalling = !finished;
    running = !finished;
    voiceAction = finished ? "Tentar instalar voz novamente" : "Instalando voz…";
    status.setText(text);
    render();
  }

  private void complete(long ticket) {
    if (ticket != generation) return;
    running = false;
    state.partial("");
    status.setText(
        state.copyText().isEmpty()
            ? "Concluído. Nenhuma fala reconhecida."
            : "Concluído · "
                + state.segments().size()
                + (state.segments().size() == 1 ? " trecho" : " trechos"));
    render();
  }

  private void failed(long ticket, String message) {
    if (ticket != generation) return;
    running = false;
    state.partial("");
    status.setText("Não foi possível transcrever. " + message);
    render();
  }

  private void cancel() {
    generation++;
    reconnect.removeCallbacksAndMessages(null);
    running = false;
    voiceVisible = voiceDownloadable = voiceInstalling = false;
    if (remote != null) {
      remote.cancel();
      remote = null;
    }
    if (nativeEngine != null) {
      nativeEngine.cancel();
      nativeEngine = null;
    }
  }

  private void render() {
    if (transcript == null) return;
    StringBuilder b = new StringBuilder();
    for (Transcript.Segment s : state.segments()) {
      if (b.length() > 0) b.append("\n\n");
      if (s.start() >= 0)
        b.append(time(s.start())).append(" – ").append(time(s.end())).append("\n");
      b.append(s.text());
    }
    transcript.setText(b.toString());
    partial.setText(state.provisional().isEmpty() ? "" : "Prévia · " + state.provisional());
    copy.setEnabled(!state.copyText().isEmpty());
    share.setEnabled(!state.copyText().isEmpty());
    downloadVoice.setVisibility(voiceVisible ? View.VISIBLE : View.GONE);
    downloadVoice.setEnabled(voiceDownloadable && !voiceInstalling);
    downloadVoice.setText(voiceAction);
    stop.setEnabled(running);
    retry.setEnabled(!running);
    retry.setText(currentUri == null ? "Escolher áudio" : "Tentar novamente");
  }

  private String time(double seconds) {
    int s = (int) seconds;
    return String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60);
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(request, permissions, results);
    if (request == 12) {
      if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) begin();
      else {
        status.setText(
            "Permissão negada. Configure o servidor em Ajustes para transcrever sem microfone.");
        render();
      }
    }
  }

  private EditText edit(LinearLayout box, String name, String value, boolean secret) {
    TextView caption = label(name, 14, muted);
    box.addView(caption);
    EditText field = new EditText(this);
    field.setId(View.generateViewId());
    caption.setLabelFor(field.getId());
    field.setText(value);
    field.setTextColor(foreground);
    field.setSingleLine(true);
    field.setInputType(
        secret
            ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
            : InputType.TYPE_CLASS_TEXT);
    box.addView(field);
    return field;
  }

  private void settings() {
    LinearLayout box = new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(dp(24), dp(8), dp(24), dp(8));
    box.setBackgroundColor(background);
    box.addView(
        label(
            "Android local não envia áudio. Servidor envia os trechos ao endereço escolhido, por"
                + " HTTPS.",
            14,
            muted));
    Spinner engine = new Spinner(this);
    engine.setAdapter(
        new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            new String[] {"Android local (offline)", "Servidor configurado"}));
    engine.setSelection(prefs.getString("engine", "native").equals("remote") ? 1 : 0);
    box.addView(engine);
    EditText language =
        edit(box, "Idioma (pt-BR, en-US…)", prefs.getString("language", "pt-BR"), false);
    EditText endpoint =
        edit(box, "URL completa de transcrição", prefs.getString("endpoint", ""), false);
    EditText token = edit(box, "Chave / token (opcional)", prefs.getString("token", ""), true);
    box.addView(label("Modelo do servidor · baixa na primeira transcrição", 14, muted));
    Spinner model = new Spinner(this);
    model.setAdapter(
        new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_dropdown_item, ModelCatalog.labels()));
    int selectedModel = ModelCatalog.indexOf(prefs.getString("model", ModelCatalog.defaultId()));
    model.setSelection(
        selectedModel < 0 ? ModelCatalog.indexOf(ModelCatalog.defaultId()) : selectedModel);
    box.addView(model);
    ScrollView scroll = new ScrollView(this);
    scroll.addView(box);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Ajustes")
            .setView(scroll)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar", null)
            .create();
    dialog.setOnShowListener(
        v ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    view -> {
                      String url = endpoint.getText().toString().trim(),
                          lang = language.getText().toString().trim(),
                          m = ModelCatalog.idAt(model.getSelectedItemPosition()),
                          key = token.getText().toString().trim();
                      if (engine.getSelectedItemPosition() == 1
                          && (!EndpointPolicy.valid(url)
                              || (!url.startsWith("https://")
                                  && (getApplicationInfo().flags
                                          & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)
                                      == 0))) {
                        endpoint.setError("Use HTTPS");
                        return;
                      }
                      if (!lang.matches("[a-zA-Z]{2,3}(-[a-zA-Z]{2,4})?")) {
                        language.setError("Exemplo: pt-BR");
                        return;
                      }
                      if (key.contains("\n") || key.contains("\r")) {
                        token.setError("Chave inválida");
                        return;
                      }
                      prefs
                          .edit()
                          .putString(
                              "engine", engine.getSelectedItemPosition() == 1 ? "remote" : "native")
                          .putString("language", lang)
                          .putString("endpoint", url)
                          .putString("token", key)
                          .putString("model", m)
                          .apply();
                      dialog.dismiss();
                      if (currentUri != null && !running) begin();
                    }));
    dialog.show();
  }

  @Override
  public void onConfigurationChanged(Configuration config) {
    super.onConfigurationChanged(config);
    String t = title.getText().toString(), s = status.getText().toString();
    draw();
    title.setText(t);
    status.setText(s);
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    out.putString("text", state.copyText());
    out.putString("title", title.getText().toString());
    out.putString("status", status.getText().toString());
    out.putBoolean("running", running);
    if (currentUri != null) out.putString("uri", currentUri.toString());
  }

  @Override
  protected void onDestroy() {
    cancel();
    super.onDestroy();
  }
}
