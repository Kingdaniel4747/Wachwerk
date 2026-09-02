package de.danberg.wachwerk;

import android.app.Activity;
import android.app.KeyguardManager;
import android.provider.Settings;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AlarmActivity extends Activity implements SensorEventListener, NfcAdapter.ReaderCallback {
    private UiPalette palette;
    private int MINT;
    private int ICE;
    private int INK;
    private int PANEL;
    private static final int QR_REQUEST = 5120;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Button nfcAccess;
    private boolean resumed;
    private boolean qrAccepted;
    private String session;
    private SensorManager sensorManager;
    private boolean completed;
    private ScanPulseView scanPulse;
    private TextView challengeStatus;
    private String challenge;
    private int shakes;
    private int shakeTarget;
    private int holdSeconds;
    private int snakeSeconds;
    private NfcAdapter nfcAdapter;
    private long lastShake;
    private long holdStarted;
    private final Runnable holdProgress = new Runnable() {
        @Override public void run() {
            if (holdStarted == 0) return;
            long elapsed = System.currentTimeMillis() - holdStarted;
            int seconds = Math.min(holdSeconds, (int) (elapsed / 1000));
            challengeStatus.setText("Gedrückt halten: " + seconds + " / " + holdSeconds + " Sekunden");
            if (elapsed >= holdSeconds * 1_000L) unlock("Geschafft · " + holdSeconds + " Sekunden bewusst gehalten.");
            else handler.postDelayed(this, 80L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        palette = UiPalette.from(this);
        MINT=palette.success; ICE=palette.text; INK=palette.background; PANEL=palette.panel;
        GentleWakeActivity.finishIfRunning();
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        getWindow().setStatusBarColor(INK);
        getWindow().setNavigationBarColor(INK);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        loadSession();
    }

    private void loadSession() {
        Intent active=AlarmSessionStore.current(this);
        if(active==null) { finishAndRemoveTask();return; }
        setIntent(active); session=active.getStringExtra(AlarmSessionStore.SESSION);
        completed=false;shakes=0;holdStarted=0;scanPulse=null;nfcAccess=null;
        if(sensorManager!=null)sensorManager.unregisterListener(this);
        disableReader();
        challenge=AlarmScheduler.value(active,"challenge","shake");
        shakeTarget=Math.max(3,active.getIntExtra("shakeCount",12));
        holdSeconds=Math.max(3,active.getIntExtra("holdSeconds",8));
        snakeSeconds=Math.max(3,active.getIntExtra("snakeSeconds",10));
        nfcAdapter=is("nfc")?NfcAdapter.getDefaultAdapter(this):null;
        buildInterface();
        if(resumed) { if(is("shake"))registerShakeSensor();enableReader(); }
    }

    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); loadSession(); }
    private final Runnable sessionTick=new Runnable() {
        @Override public void run() {
            if(!resumed || completed)return;
            if(!AlarmSessionStore.matches(AlarmActivity.this,session))loadSession();
            if(!isFinishing())handler.postDelayed(this,1000);
        }
    };

    private void buildInterface() {
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{palette.map(Color.rgb(31, 70, 105)), INK, palette.map(Color.rgb(4, 13, 22))});
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24), dp(42), dp(24), dp(32));
        content.setBackground(background);

        TextView pulse = label("◴", 48, INK, Typeface.BOLD);
        pulse.setBackground(rounded(palette.map(Color.rgb(255, 211, 71)), 1000, Color.TRANSPARENT, 0));
        pulse.setGravity(Gravity.CENTER);
        content.addView(pulse, new LinearLayout.LayoutParams(dp(106), dp(106)));

        TextView overline = label("WACHWERK · GUTEN MORGEN", 11, palette.map(Color.rgb(131, 164, 197)), Typeface.BOLD);
        LinearLayout.LayoutParams overlineParams = wrap(); overlineParams.topMargin = dp(23);
        content.addView(overline, overlineParams);
        TextView time = label(new SimpleDateFormat("HH:mm", Locale.GERMANY).format(new Date()), 74, Color.WHITE, Typeface.BOLD);
        time.setLetterSpacing(-0.05f); content.addView(time, wrap());
        TextView title = label(AlarmScheduler.value(getIntent(), "label", "Aufstehen"), 24, ICE, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = wrap(); titleParams.topMargin = dp(7); content.addView(title, titleParams);

        TextView subtitle = label(challengeDescription(), 14, palette.map(Color.rgb(155, 180, 202)), Typeface.NORMAL);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subParams = wrap(); subParams.topMargin = dp(6); subParams.bottomMargin = dp(22); content.addView(subtitle, subParams);

        LinearLayout taskBox = new LinearLayout(this);
        taskBox.setOrientation(LinearLayout.VERTICAL);
        taskBox.setPadding(dp(18), dp(17), dp(18), dp(17));
        taskBox.setBackground(rounded(PANEL, 22, palette.map(Color.rgb(45, 74, 99)), 1));
        taskBox.addView(label(challengeTitle(), 17, ICE, Typeface.BOLD), matchWrap());
        challengeStatus = label(initialStatus(), 13, palette.map(Color.rgb(145, 170, 193)), Typeface.NORMAL);
        LinearLayout.LayoutParams statusParams = matchWrap(); statusParams.topMargin = dp(7); taskBox.addView(challengeStatus, statusParams);

        if (is("nfc") || is("qr")) { scanPulse = new ScanPulseView(this); taskBox.addView(scanPulse, match(dp(136))); }
        if (is("qr")) {
            Button scan = button("KAMERA ÖFFNEN", palette.map(Color.rgb(41, 73, 99)), ICE);
            LinearLayout.LayoutParams params = match(dp(51)); params.topMargin = dp(14);
            scan.setOnClickListener(v -> startActivityForResult(new Intent(this, QrScannerActivity.class)
                .putExtra("expectedToken", AlarmScheduler.value(getIntent(), "qrToken", "wachwerk")), QR_REQUEST));
            taskBox.addView(scan, params);
        } else if (is("hold")) {
            Button hold = button("HIER " + holdSeconds + " SEKUNDEN HALTEN", palette.map(Color.rgb(41, 73, 99)), ICE);
            LinearLayout.LayoutParams params = match(dp(58)); params.topMargin = dp(14);
            hold.setOnTouchListener((view, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) { holdStarted = System.currentTimeMillis(); handler.post(holdProgress); view.setPressed(true); return true; }
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    if (!completed) { holdStarted = 0; handler.removeCallbacks(holdProgress); challengeStatus.setText("Zu früh losgelassen · beginne erneut."); }
                    view.setPressed(false); return true;
                }
                return true;
            });
            taskBox.addView(hold, params);
        } else if (is("snake")) {
            SnakeChallengeView snake = new SnakeChallengeView(this, snakeSeconds,
                () -> unlock("Geschafft · der Schlange " + snakeSeconds + " Sekunden gefolgt."));
            LinearLayout.LayoutParams params = match(dp(235)); params.topMargin = dp(13); taskBox.addView(snake, params);
        } else if (is("nfc") && nfcAdapter == null) {
            Button unavailable = button("NFC IST AUF DIESEM HANDY NICHT VERFÜGBAR", palette.map(Color.rgb(72, 49, 55)), palette.map(Color.rgb(255, 173, 165)));
            unavailable.setEnabled(false);
            LinearLayout.LayoutParams params = match(dp(51)); params.topMargin = dp(14); taskBox.addView(unavailable, params);
        }
        if(is("nfc") && nfcAdapter!=null) {
            nfcAccess=button("Handy für NFC entsperren",PANEL,ICE);
            nfcAccess.setOnClickListener(v -> {
                if(!nfcAdapter.isEnabled()) { startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));return; }
                KeyguardManager guard=getSystemService(KeyguardManager.class);
                guard.requestDismissKeyguard(this,new KeyguardManager.KeyguardDismissCallback() {
                    @Override public void onDismissSucceeded() { enableReader(); }
                    @Override public void onDismissCancelled() { challengeStatus.setText("Der Wecker klingelt weiter. Entsperre das Handy, um NFC zu verwenden."); }
                    @Override public void onDismissError() { challengeStatus.setText("Bitte das Handy entsperren und die Wecker-Benachrichtigung öffnen."); }
                });
            });
            LinearLayout.LayoutParams accessParams=match(dp(54));accessParams.topMargin=dp(14);
            taskBox.addView(nfcAccess,accessParams);
        }
        content.addView(taskBox, matchWrap());

        if (getIntent().getBooleanExtra("snoozeEnabled", true)) {
            int snoozeMinutes = AlarmScheduler.nextSnoozeMinutes(getIntent());
            Button snooze = button(snoozeMinutes + (snoozeMinutes == 1 ? " MINUTE SPÄTER" : " MINUTEN SPÄTER"), Color.TRANSPARENT, palette.map(Color.rgb(145, 169, 190)));
            snooze.setBackgroundColor(Color.TRANSPARENT);
            snooze.setOnClickListener(v -> { if (!completed && AlarmRingingService.dismiss(this,session,true)) { completed=true;finishAndRemoveTask(); } });
            LinearLayout.LayoutParams snoozeParams = match(dp(48)); snoozeParams.topMargin = dp(3); content.addView(snooze, snoozeParams);
        }

        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -1)); setContentView(scroll);
    }

    private void registerShakeSensor() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor accelerometer = sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        else { challenge = "hold"; buildInterface(); challengeStatus.setText("Kein Bewegungssensor · halte stattdessen das Display."); }
    }

    @Override public void onSensorChanged(SensorEvent event) {
        float force = (float) Math.sqrt(event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2]);
        long now = System.currentTimeMillis();
        if (force > 17f && now - lastShake > 240L) {
            lastShake = now; shakes++; challengeStatus.setText("Geschüttelt: " + shakes + " / " + shakeTarget);
            if (shakes >= shakeTarget) unlock("Geschafft · Du bist wirklich wach.");
        }
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void unlock(String message) {
        if (!completeChallenge(message)) return;
        handler.postDelayed(this::finishAndRemoveTask, 500L);
    }

    private boolean completeChallenge(String message) {
        if (completed || !resumed || !AlarmRingingService.dismiss(this,session,false)) return false;
        completed = true; holdStarted = 0;
        if(sensorManager!=null)sensorManager.unregisterListener(this);
        challengeStatus.setText(message); challengeStatus.setTextColor(MINT);
        if (scanPulse != null) scanPulse.success();
        else challengeStatus.performHapticFeedback(Build.VERSION.SDK_INT>=30
            ? android.view.HapticFeedbackConstants.CONFIRM : android.view.HapticFeedbackConstants.LONG_PRESS);
        return true;
    }

    @Override public void onTagDiscovered(Tag tag) {
        String scannedSession=session;
        String scanned = NfcTagActivity.tokenForTag(tag);
        String stored = AlarmScheduler.value(getIntent(), "nfcToken", "");
        String expected = stored.isEmpty() ? WakeKeyStore.token(this) : stored;
        runOnUiThread(() -> {
            if(scannedSession==null || !scannedSession.equals(session) || !is("nfc"))return;
            if (WakeKeyStore.matches(expected,scanned)) {
                if (!completeChallenge("Geschafft · Wecker aus · Tag wegnehmen")) return;
                boolean waiting = false;
                try { waiting = nfcAdapter.ignore(tag, 1500, () -> handler.postDelayed(this::finishAndRemoveTask, 180), handler); } catch (Exception ignored) {}
                if (!waiting) handler.postDelayed(this::finishAndRemoveTask, 1200);
                handler.postDelayed(() -> { if (!isFinishing()) finishAndRemoveTask(); }, 6000);
            }
            else challengeStatus.setText("Das ist nicht der für diesen Wecker angelernte NFC-Tag.");
        });
    }

    private void enableReader() {
        if (!resumed || completed || !is("nfc")) return;
        if(nfcAdapter==null) { challengeStatus.setText("Dieses Handy unterstützt NFC nicht.");return; }
        boolean enabled=nfcAdapter.isEnabled();
        boolean locked=getSystemService(KeyguardManager.class).isKeyguardLocked();
        if(nfcAccess!=null) {
            nfcAccess.setVisibility(!enabled || locked ? View.VISIBLE : View.GONE);
            nfcAccess.setText(!enabled ? "NFC einschalten" : "Handy für NFC entsperren");
        }
        if(!enabled) { challengeStatus.setText("NFC ist ausgeschaltet. Schalte es ein, der Wecker klingelt weiter.");return; }
        challengeStatus.setText(locked ? "Handy gesperrt · falls NFC nicht reagiert, zuerst hier entsperren."
            : "NFC bereit · halte die Rückseite an deinen angelernten Tag.");
        try {
            nfcAdapter.enableReaderMode(this,this,NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B
                | NfcAdapter.FLAG_READER_NFC_F | NfcAdapter.FLAG_READER_NFC_V
                | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK | NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,null);
        } catch(RuntimeException error) { challengeStatus.setText("NFC konnte nicht starten. Entsperre das Handy und öffne den Wecker erneut."); }
    }
    private void disableReader() {
        try { if(nfcAdapter!=null)nfcAdapter.disableReaderMode(this); }catch(RuntimeException ignored){}
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if(focused)enableReader();
    }
    @Override protected void onResume() {
        super.onResume();resumed=true;
        if(!AlarmSessionStore.matches(this,session))loadSession();
        if(isFinishing())return;
        AlarmRingingService.ensureRunning(this);
        if(is("shake"))registerShakeSensor();
        enableReader();handler.removeCallbacks(sessionTick);handler.post(sessionTick);
        if(qrAccepted) { qrAccepted=false;unlock("Code erkannt · Du bist aus dem Bett."); }
    }
    @Override protected void onPause() {
        resumed=false;disableReader();holdStarted=0;
        handler.removeCallbacks(holdProgress);handler.removeCallbacks(sessionTick);
        if(sensorManager!=null)sensorManager.unregisterListener(this);
        super.onPause();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == QR_REQUEST && resultCode == RESULT_OK) {
            if(resumed)unlock("Code erkannt · Du bist aus dem Bett.");else qrAccepted=true;
        }
        else if (requestCode == QR_REQUEST) challengeStatus.setText("Noch nicht erkannt · Kamera erneut öffnen.");
    }

    @Override protected void onDestroy() {
        if(sensorManager!=null)sensorManager.unregisterListener(this);
        handler.removeCallbacksAndMessages(null);super.onDestroy();
    }
    @Override public void onBackPressed() { /* Aufgabe zuerst abschließen. */ }

    private boolean is(String value) { return value.equalsIgnoreCase(challenge); }
    private String challengeTitle() { if (is("qr")) return "QR-Code im anderen Raum"; if (is("nfc")) return "Angelernten NFC-Tag scannen"; if (is("hold")) return "Display bewusst halten"; if (is("snake")) return "Dem Kopf mit dem Daumen folgen"; return "Handy kräftig schütteln"; }
    private String challengeDescription() { if (is("qr")) return "Geh zu deinem gedruckten Wachwerk-Code."; if (is("nfc")) return "Geh zum Tag und halte das Handy mit der Rückseite daran."; if (is("hold")) return "Halte ohne Unterbrechung " + holdSeconds + " Sekunden."; if (is("snake")) return "Bleib " + snakeSeconds + " Sekunden am bewegten Kopf."; return shakeTarget + " kräftige Bewegungen beenden den Wecker."; }
    private String initialStatus() { if (is("qr")) return "Die Kamera erkennt nur deinen persönlichen Code."; if (is("nfc")) return "NFC ist bereit · halte das Handy an den Tag."; if (is("hold")) return "Noch nicht gestartet."; if (is("snake")) return "Finger auf den grünen Kopf legen und folgen."; return "Geschüttelt: 0 / " + shakeTarget; }
    private TextView label(String text, int sp, int color, int style) { TextView view = new TextView(this); view.setText(text); view.setTextSize(sp); view.setTextColor(color); view.setTypeface(Typeface.create("sans", style)); return view; }
    private Button button(String text, int background, int foreground) { Button button = new Button(this); button.setText(text); button.setTextSize(13); button.setTextColor(foreground); button.setTypeface(Typeface.DEFAULT_BOLD); button.setAllCaps(false); button.setBackground(rounded(background, 16, Color.TRANSPARENT, 0)); return button; }
    private GradientDrawable rounded(int color, int radiusDp, int stroke, int strokeDp) { GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radiusDp)); if (strokeDp > 0) drawable.setStroke(dp(strokeDp), stroke); return drawable; }
    private LinearLayout.LayoutParams wrap() { return new LinearLayout.LayoutParams(-2, -2); }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams match(int height) { return new LinearLayout.LayoutParams(-1, height); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
