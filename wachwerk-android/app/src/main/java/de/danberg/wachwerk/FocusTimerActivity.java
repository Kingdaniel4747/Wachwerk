package de.danberg.wachwerk;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FocusTimerActivity extends Activity {
    private Ringtone ringtone;
    private Vibrator vibrator;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        getWindow().setStatusBarColor(Color.rgb(6, 19, 31));
        getWindow().setNavigationBarColor(Color.rgb(6, 19, 31));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        buildUi(); startSound();
    }

    private void buildUi() {
        boolean workFinished = "work".equals(FocusTimerScheduler.phase(this));
        int round = FocusTimerScheduler.round(this), rounds = FocusTimerScheduler.rounds(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(40), dp(28), dp(40));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(38, 48, 24), Color.rgb(10, 29, 39), Color.rgb(5, 14, 22)}));
        TextView icon = text(workFinished ? "✓" : "☕", 70, Color.rgb(255, 211, 71), Typeface.BOLD); icon.setGravity(Gravity.CENTER); root.addView(icon);
        TextView title = text(workFinished ? "Fokuszeit geschafft" : "Pause beendet", 29, Color.WHITE, Typeface.BOLD); title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = matchWrap(); titleParams.topMargin = dp(18); root.addView(title, titleParams);
        TextView info = text("Durchgang " + round + " von " + rounds, 15, Color.rgb(194, 211, 226), Typeface.NORMAL); info.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams infoParams = matchWrap(); infoParams.topMargin = dp(9); root.addView(info, infoParams);
        String primaryText = workFinished ? "PAUSE STARTEN" : (round < rounds ? "NÄCHSTE FOKUSPHASE" : "SESSION ABSCHLIESSEN");
        Button primary = button(primaryText, Color.rgb(155, 245, 177), Color.rgb(6, 19, 31));
        primary.setOnClickListener(v -> { stopSound(); FocusTimerScheduler.advance(this); returnToApp(); });
        LinearLayout.LayoutParams primaryParams = match(dp(58)); primaryParams.topMargin = dp(34); root.addView(primary, primaryParams);
        Button stop = button("SESSION BEENDEN", Color.rgb(28, 54, 72), Color.rgb(201, 220, 248));
        stop.setOnClickListener(v -> { stopSound(); FocusTimerScheduler.cancel(this); returnToApp(); });
        LinearLayout.LayoutParams stopParams = match(dp(54)); stopParams.topMargin = dp(11); root.addView(stop, stopParams);
        setContentView(root);
    }

    private void startSound() {
        try {
            ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
            ringtone.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
            if (Build.VERSION.SDK_INT >= 28) ringtone.setLooping(true);
            ringtone.play();
        } catch (Exception ignored) {}
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        long[] pattern = {0, 500, 250, 500, 800};
        if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
        else vibrator.vibrate(pattern, 0);
    }

    private void stopSound() {
        try { if (ringtone != null && ringtone.isPlaying()) ringtone.stop(); } catch (Exception ignored) {}
        if (vibrator != null) vibrator.cancel();
        NotificationManager notifications = getSystemService(NotificationManager.class);
        // onDestroy also runs after advance(): never remove the next phase's live timer.
        try {
            boolean stillRinging = new org.json.JSONObject(FocusTimerScheduler.stateJson(this)).optBoolean("ringing");
            if (notifications != null && stillRinging) notifications.cancel(9292);
        } catch (Exception ignored) {}
    }

    private void returnToApp() {
        startActivity(new Intent(this, MainActivity.class).putExtra("openAlarms", true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    @Override protected void onDestroy() { stopSound(); super.onDestroy(); }
    @Override public void onBackPressed() { moveTaskToBack(true); }
    private TextView text(String value, int size, int color, int style) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setTypeface(Typeface.create("sans", style)); return view; }
    private Button button(String value, int background, int color) { Button button = new Button(this); button.setText(value); button.setAllCaps(false); button.setTextColor(color); button.setTextSize(14); button.setTypeface(Typeface.DEFAULT_BOLD); GradientDrawable shape = new GradientDrawable(); shape.setColor(background); shape.setCornerRadius(dp(17)); button.setBackground(shape); return button; }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams match(int height) { return new LinearLayout.LayoutParams(-1, height); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
