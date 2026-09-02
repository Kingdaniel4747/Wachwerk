package de.danberg.wachwerk;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;

public class GentleWakeActivity extends Activity {
    private static GentleWakeActivity running;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long started;
    private long duration;
    private View root;
    private int requestCode;
    private final Runnable brighten = new Runnable() {
        @Override public void run() {
            float progress = Math.min(1f, (System.currentTimeMillis() - started) / (float) duration);
            float eased = (float) Math.pow(progress, .62d);
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.screenBrightness = .04f + eased * .96f;
            getWindow().setAttributes(params);
            updateVisual(eased, progress);
            if (progress < 1f) handler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        running = this;
        requestCode = getIntent().getIntExtra("requestCode", 1);
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 26) {
            KeyguardManager keyguard = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            keyguard.requestDismissKeyguard(this, null);
        }
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        duration = Math.max(5, getIntent().getIntExtra("gentleMinutes", 15)) * 60_000L;
        started = System.currentTimeMillis();
        buildUi();
        handler.post(brighten);
    }

    private void buildUi() {
        root = new View(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
            new int[]{Color.rgb(20,12,5), Color.rgb(44,27,10), Color.rgb(72,48,15)}));
        setContentView(root);
    }

    private void updateVisual(float eased, float progress) {
        if (root == null) return;
        int lower = blend(Color.rgb(20, 12, 5), Color.rgb(255, 253, 244), eased);
        int middle = blend(Color.rgb(44, 27, 10), Color.rgb(255, 248, 218), eased);
        int upper = blend(Color.rgb(72, 48, 15), Color.rgb(255, 255, 248), eased);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{lower, middle, upper}));
    }

    private int blend(int from, int to, float amount) {
        amount = Math.max(0f, Math.min(1f, amount));
        return Color.rgb(
            Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * amount),
            Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * amount),
            Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount));
    }

    static void finishIfRunning() {
        if (running != null) running.runOnUiThread(() -> running.finishAndRemoveTask());
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(brighten);
        getSystemService(NotificationManager.class).cancel(17000 + requestCode);
        if (running == this) running = null;
        super.onDestroy();
    }
}
