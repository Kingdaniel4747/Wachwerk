package de.danberg.wachwerk;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

public class SnakeChallengeView extends View {
    public interface Listener { void onComplete(); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Listener listener;
    private final long requiredMillis;
    private long progressMillis;
    private long lastFrame;
    private boolean touching;
    private float touchX, touchY;
    private boolean completed;

    public SnakeChallengeView(Context context, int seconds, Listener listener) { super(context); this.listener = listener; this.requiredMillis = Math.max(3, seconds) * 1_000L; paint.setStrokeCap(Paint.Cap.ROUND); setBackgroundColor(Color.rgb(8, 26, 40)); }

    private float targetX(long time) { return getWidth() * (.5f + .30f * (float) Math.sin(time / 1250.0)); }
    private float targetY(long time) { return getHeight() * (.5f + .24f * (float) Math.sin(time / 930.0 + 1.2)); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = System.currentTimeMillis();
        if (lastFrame == 0L) lastFrame = now;
        long frameMillis = Math.min(50L, Math.max(0L, now - lastFrame));
        lastFrame = now;
        float x = targetX(now), y = targetY(now);
        Path tail = new Path();
        for (int i = 18; i >= 0; i--) {
            long at = now - i * 35L;
            float px = targetX(at), py = targetY(at);
            if (i == 18) tail.moveTo(px, py); else tail.lineTo(px, py);
        }
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(11)); paint.setColor(Color.rgb(55, 124, 91)); canvas.drawPath(tail, paint);
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.rgb(155, 245, 177)); canvas.drawCircle(x, y, dp(34), paint);
        paint.setColor(Color.rgb(6, 19, 31)); canvas.drawCircle(x - dp(10), y - dp(7), dp(4), paint); canvas.drawCircle(x + dp(10), y - dp(7), dp(4), paint);

        float distance = (float) Math.hypot(touchX - x, touchY - y);
        if (touching && distance <= dp(78)) {
            progressMillis = Math.min(requiredMillis, progressMillis + frameMillis);
            float progress = Math.min(1f, progressMillis / (float) requiredMillis);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(5)); paint.setColor(Color.rgb(255, 211, 71));
            canvas.drawArc(dp(8), dp(8), getWidth() - dp(8), getHeight() - dp(8), -90, progress * 360, false, paint);
            if (progress >= 1f && !completed) { completed = true; listener.onComplete(); }
        } else if (touching) {
            // A short slip should not erase everything; progress drains slowly while the user catches up.
            progressMillis = Math.max(0L, progressMillis - frameMillis / 2L);
        }
        if (!completed) postInvalidateDelayed(16L);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        touchX = event.getX(); touchY = event.getY();
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
            touching = true;
            getParent().requestDisallowInterceptTouchEvent(true);
        } else {
            touching = false;
            getParent().requestDisallowInterceptTouchEvent(false);
        }
        invalidate(); return true;
    }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}
