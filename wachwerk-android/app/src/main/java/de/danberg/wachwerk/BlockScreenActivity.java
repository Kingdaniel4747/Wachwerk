package de.danberg.wachwerk;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class BlockScreenActivity extends Activity implements NfcAdapter.ReaderCallback {
    private static final int QR_REQUEST = 5510;
    private NfcAdapter adapter;
    private TextView status;
    private String method;
    private String blockReason;
    private UiPalette palette;
    private EditText passwordField;
    private String blockedPackage;
    private boolean limitMode;
    private boolean scheduleMode;
    private boolean focusMode;
    private boolean nfcBusy;
    private int durationMinutes = 5;
    private boolean scanning;
    private boolean completed;
    private ScanPulseView pulse;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        palette = UiPalette.from(this);
        getWindow().setStatusBarColor(palette.background);
        getWindow().setNavigationBarColor(palette.background);
        blockedPackage = getIntent().getStringExtra("blockedPackage");
        selectReason(BlockPolicy.reason(this, blockedPackage));
        if (savedInstanceState != null) durationMinutes = savedInstanceState.getInt("durationMinutes", 5);
        adapter = NfcAdapter.getDefaultAdapter(this);
        buildUi();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);setIntent(intent);
        blockedPackage=intent.getStringExtra("blockedPackage");completed=false;scanning=false;nfcBusy=false;
        selectReason(BlockPolicy.reason(this,blockedPackage));buildUi();
    }
    private final Runnable checkExpiry=new Runnable() {
        @Override public void run() {
            if(isFinishing() || completed)return;
            String current=BlockPolicy.reason(BlockScreenActivity.this,blockedPackage);
            if(!current.equals(blockReason)) {
                scanning=false;
                if(adapter!=null)adapter.disableReaderMode(BlockScreenActivity.this);
                if(current.isEmpty()) { returnToBlockedApp();return; }
                selectReason(current);buildUi();
            }
            if("morning".equals(current)) {
                long seconds=Math.max(0,(MorningBlockStore.until(BlockScreenActivity.this)-System.currentTimeMillis()+999)/1000);
                status.setText(String.format(java.util.Locale.GERMANY,"%02d:%02d",seconds/60,seconds%60));
            }
            handler.postDelayed(this,1000);
        }
    };
    private void buildMorningUi() {
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28),dp(36),dp(28),dp(36));root.setBackgroundColor(palette.background);
        TextView symbol=text("☀",64,palette.success,Typeface.BOLD);root.addView(symbol);
        TextView title=text("Erst einmal wach werden",26,palette.text,Typeface.BOLD);title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams p=matchWrap();p.topMargin=dp(24);root.addView(title,p);
        TextView info=text(appLabel(blockedPackage)+" wartet noch. Deine Morgensperre endet automatisch; andere Sperren bleiben unverändert.",16,palette.muted,Typeface.NORMAL);
        info.setGravity(Gravity.CENTER);root.addView(info,p);
        status=text("",58,palette.text,Typeface.BOLD);status.setGravity(Gravity.CENTER);root.addView(status,p);
        Button home=smallButton("Wachwerk öffnen");home.setOnClickListener(v->returnToBlocker());root.addView(home,p);
        android.widget.ScrollView scroll=new android.widget.ScrollView(this);scroll.setFillViewport(true);scroll.addView(root);setContentView(scroll);
    }

    private void selectReason(String reason) {
        blockReason = reason;
        limitMode = "limit".equals(reason); scheduleMode = "schedule".equals(reason); focusMode = "focus".equals(reason);
        method = AppBlockerStore.method(this, BlockPolicy.scope(reason));
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putInt("durationMinutes", durationMinutes); }

    private void buildUi() {
        if ("morning".equals(blockReason)) { buildMorningUi(); return; }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(34), dp(28), dp(34));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{palette.panel, palette.background, palette.background}));
        TextView icon = text(limitMode ? "⌛" : focusMode ? "◎" : scheduleMode ? "◷" : "◈", 68, palette.map(Color.rgb(255, 211, 71)), Typeface.BOLD); icon.setGravity(Gravity.CENTER); root.addView(icon, matchWrap());
        String titleText = limitMode ? "Tageslimit erreicht" : focusMode ? "Während Fokus gesperrt" : scheduleMode ? "Außerhalb deiner Nutzungszeit" : "Diese App ist gerade gesperrt";
        TextView title = text(titleText, 27, Color.WHITE, Typeface.BOLD);
        title.setGravity(Gravity.CENTER); LinearLayout.LayoutParams titleParams = matchWrap(); titleParams.topMargin = dp(22); root.addView(title, titleParams);
        String actionResult = limitMode || scheduleMode ? "Diese Regel pausiert für deine ausgewählte Dauer. Weitere aktive Sperren werden separat freigegeben." : focusMode ? "Gibt die Fokus-Sperre frei. Andere Regeln bleiben aktiv." : "Hebt die Direkt-Sperre auf. Tageslimits und Uhrzeiten bleiben unverändert.";
        String instruction = "qr".equals(method) ? "Scanne deinen ausgedruckten Wachwerk-QR-Code. " + actionResult
            : "password".equals(method) ? "Gib dein Wachwerk-Passwort ein. " + actionResult
            : "Halte den angelernten NFC-Tag an die Rückseite des Handys. " + actionResult;
        String reason = limitMode ? "Dein tägliches Zeitlimit für " + appLabel(blockedPackage) + " ist aufgebraucht. "
            : focusMode ? appLabel(blockedPackage) + " gehört zu deiner Fokus-Sperre. "
            : scheduleMode ? appLabel(blockedPackage) + " ist aktuell nicht in deinem erlaubten Zeitfenster. " : "";
        TextView info = text(reason + instruction,
            14, palette.map(Color.rgb(194, 211, 226)), Typeface.NORMAL);
        info.setGravity(Gravity.CENTER); LinearLayout.LayoutParams infoParams = matchWrap(); infoParams.topMargin = dp(12); root.addView(info, infoParams);
        status = text(limitMode ? Math.max(0, DailyUsageStore.usedSeconds(this, blockedPackage) / 60L) + " von " + AppBlockerStore.dailyLimitMinutes(this, blockedPackage) + " Minuten heute genutzt"
            : "qr".equals(method) ? "Kamera bereit · starte den Scanner."
            : "password".equals(method) ? "Passwort bereit."
            : adapter == null ? "NFC ist auf diesem Handy nicht verfügbar." : "NFC bereit · warte auf den Tag …", 13, palette.map(Color.rgb(155, 245, 177)), Typeface.BOLD);
        status.setGravity(Gravity.CENTER); LinearLayout.LayoutParams statusParams = matchWrap(); statusParams.topMargin = dp(25); root.addView(status, statusParams);
        if (limitMode || scheduleMode) addUnlockDuration(root);
        if ("nfc".equals(method)) {
            Button scan = smallButton("Jetzt scannen"); scan.setTextSize(16); scan.setOnClickListener(v -> beginScan());
            LinearLayout.LayoutParams params = compact(dp(48)); params.topMargin = dp(24); root.addView(scan, params);
        }
        if ("qr".equals(method)) {
            Button scan = new Button(this); scan.setText("Jetzt scannen"); scan.setAllCaps(false); scan.setTypeface(Typeface.DEFAULT_BOLD); scan.setTextColor(palette.map(Color.rgb(6,19,31))); scan.setBackground(rounded(palette.map(Color.rgb(155,245,177)), 15));
            scan.setOnClickListener(v -> beginScan());
            LinearLayout.LayoutParams scanParams = compact(dp(48)); scanParams.topMargin = dp(20); root.addView(scan, scanParams);
        } else if ("password".equals(method)) {
            EditText password = new EditText(this); passwordField = password;
            password.setHint("Passwort"); password.setTextColor(Color.WHITE); password.setHintTextColor(palette.map(Color.rgb(120, 145, 166)));
            password.setSingleLine(true); password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            password.setPadding(dp(15), 0, dp(15), 0); password.setBackground(rounded(palette.map(Color.rgb(17, 40, 57)), 13));
            LinearLayout.LayoutParams passwordParams = match(dp(52)); passwordParams.topMargin = dp(20); root.addView(password, passwordParams);
            Button verify = new Button(this); verify.setText("Mit Passwort freigeben"); verify.setAllCaps(false); verify.setTypeface(Typeface.DEFAULT_BOLD); verify.setTextColor(palette.map(Color.rgb(6,19,31))); verify.setBackground(rounded(palette.map(Color.rgb(155,245,177)), 15));
            verify.setOnClickListener(v -> {
                if (AppBlockerStore.verifyPassword(this, password.getText().toString(), BlockPolicy.scope(blockReason))) unlock();
                else { password.setText(""); status.setText("Passwort falsch · versuche es erneut."); }
            });
            LinearLayout.LayoutParams verifyParams = compact(dp(48)); verifyParams.topMargin = dp(10); root.addView(verify, verifyParams);
        }
        Button open = new Button(this); open.setText("Wachwerk öffnen"); open.setAllCaps(false); open.setTypeface(Typeface.DEFAULT_BOLD); open.setTextColor(palette.map(Color.rgb(201,220,248))); open.setBackground(rounded(palette.map(Color.rgb(24,51,72)), 15));
        open.setOnClickListener(v -> returnToBlocker());
        LinearLayout.LayoutParams buttonParams = compact(dp(48)); buttonParams.topMargin = dp(48); root.addView(open, buttonParams);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.setFillViewport(true); scroll.addView(root); setContentView(scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        if (completed) return;
        String current = BlockPolicy.reason(this, blockedPackage);
        if (!current.equals(blockReason)) {
            scanning = false;
            if (current.isEmpty()) { returnToBlockedApp(); return; }
            selectReason(current); buildUi();
        }
        enableReader();handler.removeCallbacks(checkExpiry);handler.post(checkExpiry);
    }

    private void enableReader() {
        if (scanning && !completed && "nfc".equals(method) && adapter != null && adapter.isEnabled()) adapter.enableReaderMode(this, this,
            NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B | NfcAdapter.FLAG_READER_NFC_F | NfcAdapter.FLAG_READER_NFC_V, null);
    }

    private void beginScan() {
        scanning = true;
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28),dp(36),dp(28),dp(28)); root.setBackgroundColor(palette.map(Color.rgb(6,19,31)));
        pulse = new ScanPulseView(this); root.addView(pulse, new LinearLayout.LayoutParams(dp(200),dp(200)));
        status = text("nfc".equals(method) ? "NFC-Tag jetzt an die Rückseite halten" : "Deinen QR-Code jetzt scannen",22,Color.WHITE,Typeface.BOLD);
        status.setGravity(Gravity.CENTER); LinearLayout.LayoutParams p=matchWrap();p.topMargin=dp(24);root.addView(status,p);
        if ("nfc".equals(method) && (adapter == null || !adapter.isEnabled())) {
            status.setText(adapter == null ? "Dieses Handy unterstützt kein NFC" : "Bitte NFC in den Schnelleinstellungen einschalten");
        }
        if ("qr".equals(method)) {
            Button retry=smallButton("Kamera öffnen");retry.setOnClickListener(v->openQr());LinearLayout.LayoutParams rp=match(dp(54));rp.topMargin=dp(22);root.addView(retry,rp);
        }
        Button back=smallButton("Zurück");back.setOnClickListener(v->{if(completed)return;scanning=false;if(adapter!=null)adapter.disableReaderMode(this);buildUi();});
        LinearLayout.LayoutParams bp=match(dp(52));bp.topMargin=dp(40);root.addView(back,bp);
        android.widget.ScrollView scroll=new android.widget.ScrollView(this);scroll.setFillViewport(true);scroll.addView(root);setContentView(scroll);enableReader();if("qr".equals(method))openQr();
    }
    private void openQr() { startActivityForResult(new Intent(this,QrScannerActivity.class).putExtra("expectedToken",AppBlockerStore.qrToken(this)),QR_REQUEST); }

    @Override protected void onPause() {
        handler.removeCallbacks(checkExpiry);
        if (adapter != null) adapter.disableReaderMode(this);
        super.onPause();
    }

    @Override public void onTagDiscovered(Tag tag) {
        if (nfcBusy || completed || !scanning) return;
        String scanned = NfcTagActivity.tokenForTag(tag);
        String expected = AppBlockerStore.token(this);
        runOnUiThread(() -> {
            if (completed) return;
            if (expected.isEmpty()) {
                AppBlockerStore.setToken(this, scanned);
                unlockAfterTagRemoved(tag);
            } else if (expected.equals(scanned)) {
                unlockAfterTagRemoved(tag);
            } else status.setText("Falscher Tag · bitte den angelernten Wachwerk-Tag verwenden.");
        });
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == QR_REQUEST && resultCode == RESULT_OK) unlock();
        else if (requestCode == QR_REQUEST) status.setText("QR-Code noch nicht erkannt · erneut versuchen.");
    }

    private void unlock() { completeUnlock(null); }
    private void unlockAfterTagRemoved(Tag tag) { completeUnlock(tag); }

    private void completeUnlock(Tag tag) {
        if (completed || "morning".equals(blockReason)) return;
        completed = true; nfcBusy = true;
        if (pulse != null) pulse.success();
        BlockPolicy.release(this, blockedPackage, blockReason, selectedMinutes());
        status.setText(tag == null ? "Bestätigt · diese Sperre ist freigegeben" : "Bestätigt · Tag jetzt wegnehmen");
        Toast.makeText(this, limitMode || scheduleMode ? unlockLabel(selectedMinutes()) : "Diese Sperre ist aufgehoben", Toast.LENGTH_SHORT).show();
        if (tag == null) { handler.postDelayed(this::continueOrReturn, 550L); return; }
        boolean waiting = false;
        try { if (adapter != null) waiting = adapter.ignore(tag, 1500, () -> handler.postDelayed(this::continueOrReturn, 140L), handler); }
        catch (Exception ignored) {}
        if (!waiting) handler.postDelayed(this::continueOrReturn, 1200L);
        handler.postDelayed(this::continueOrReturn, 6000L);
    }

    private void continueOrReturn() {
        if (isFinishing() || !completed) return;
        handler.removeCallbacksAndMessages(null);
        if (adapter != null) adapter.disableReaderMode(this);
        String remaining = BlockPolicy.reason(this, blockedPackage);
        if (remaining.isEmpty()) { returnToBlockedApp(); return; }
        // Do not consume the same tag twice: show the next rule and require a new scan.
        completed = false; scanning = false; nfcBusy = false; pulse = null;
        selectReason(remaining); buildUi();
        status.setText("Eine weitere Sperre ist noch aktiv · separat bestätigen");
    }

    private void addUnlockDuration(LinearLayout root) {
        TextView caption=text("Dauer auswählen",14,palette.muted,Typeface.NORMAL);
        caption.setGravity(Gravity.CENTER);LinearLayout.LayoutParams cp=matchWrap();cp.topMargin=dp(24);root.addView(caption,cp);
        Button duration=smallButton(durationMinutes+" Min.");duration.setTextSize(20);
        duration.setOnClickListener(v->{
            android.app.Dialog dialog=new android.app.Dialog(this);dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
            LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setGravity(Gravity.CENTER_HORIZONTAL);content.setPadding(dp(24),dp(20),dp(24),dp(28));content.setBackground(rounded(palette.map(Color.rgb(16,35,49)),28));
            TextView label=text("Freigabe in Minuten",23,Color.WHITE,Typeface.BOLD);content.addView(label);
            final int[] draft={durationMinutes};NumberDialView dial=new NumberDialView(this,durationMinutes,value->draft[0]=value);content.addView(dial,new LinearLayout.LayoutParams(dp(245),dp(245)));
            Button save=smallButton("Übernehmen");save.setTextSize(16);
            save.setOnClickListener(w->{
                durationMinutes=draft[0];duration.setText(durationMinutes+" Min.");dialog.dismiss();
                if ("password".equals(method)) { passwordField.requestFocus(); ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(passwordField,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT); }
                else beginScan();
            });
            LinearLayout.LayoutParams p=match(dp(54));p.topMargin=dp(18);content.addView(save,p);
            android.widget.ScrollView scroll=new android.widget.ScrollView(this);scroll.addView(content);
            dialog.setContentView(scroll);dialog.show();if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);dialog.getWindow().setLayout(-1,Math.min(dp(420),getResources().getDisplayMetrics().heightPixels-dp(48)));dialog.getWindow().setGravity(Gravity.BOTTOM);}
        });
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(140),dp(46));p.gravity=Gravity.CENTER_HORIZONTAL;p.topMargin=dp(10);root.addView(duration,p);
    }

    private int selectedMinutes() { return Math.max(1,Math.min(1440,durationMinutes)); }

    private String unlockLabel(int minutes) { return minutes == 0 ? "Für heute freigegeben" : "Für " + minutes + " Minuten freigegeben"; }

    private void returnToBlockedApp() {
        if (isFinishing()) return;
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(blockedPackage);
            if (launch != null) { launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP); startActivity(launch); finish(); return; }
        } catch (Exception ignored) {}
        returnToBlocker();
    }

    private void returnToBlocker() {
        Intent intent = new Intent(this, MainActivity.class)
            .putExtra("openBlocker", true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private String appLabel(String packageName) {
        if (packageName == null || packageName.isEmpty()) return "diese App";
        try { return String.valueOf(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(packageName, 0))); }
        catch (Exception ignored) { return packageName; }
    }

    @Override public void onBackPressed() { if(scanning && !completed){scanning=false;if(adapter!=null)adapter.disableReaderMode(this);buildUi();}else moveTaskToBack(true); }
    @Override protected void onDestroy() {handler.removeCallbacksAndMessages(null);super.onDestroy();}
    private TextView text(String value, int size, int color, int style) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setTypeface(Typeface.create("sans", style)); return view; }
    private GradientDrawable rounded(int color, int radius) { GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable; }
    private Button smallButton(String value) { Button button = new Button(this); button.setText(value); button.setAllCaps(false); button.setTextSize(15); button.setTypeface(Typeface.DEFAULT_BOLD); button.setTextColor(palette.map(Color.rgb(205,222,238))); button.setBackground(rounded(palette.map(Color.rgb(24,51,72)), 12)); return button; }
    private LinearLayout.LayoutParams compact(int height) { return new LinearLayout.LayoutParams(Math.min(dp(280),getResources().getDisplayMetrics().widthPixels-dp(56)),height); }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams match(int height) { return new LinearLayout.LayoutParams(-1, height); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
