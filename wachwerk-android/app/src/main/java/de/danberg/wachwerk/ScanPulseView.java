package de.danberg.wachwerk;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.View;

/** Lightweight scan feedback: one canvas, no image assets or background work. */
public final class ScanPulseView extends View {
    private final UiPalette palette;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean success;
    public ScanPulseView(Context context) { super(context); palette=UiPalette.from(context); setContentDescription("Scanner bereit"); }
    public void success() { success = true; setContentDescription("Erfolgreich erkannt"); performHapticFeedback(HapticFeedbackConstants.CONFIRM); invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx=getWidth()/2f, cy=getHeight()/2f, radius=Math.min(cx,cy)*.68f;
        double phase=SystemClock.uptimeMillis()/900.0;
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2*getResources().getDisplayMetrics().density);
        paint.setColor((palette.accent & 0x00ffffff) | 0x37000000); canvas.drawCircle(cx,cy,radius,paint);
        paint.setStyle(Paint.Style.FILL); paint.setColor((palette.accent & 0x00ffffff) | 0x1e000000);
        canvas.drawCircle(cx,cy,radius*(float)(.37+.12*Math.sin(phase)),paint);
        paint.setColor(palette.map(Color.rgb(155,245,177)));
        if(success) { paint.setTextSize(radius*.8f); paint.setTextAlign(Paint.Align.CENTER); canvas.drawText("✓",cx,cy+radius*.28f,paint); }
        else { canvas.drawCircle(cx,cy,radius*.12f,paint); canvas.drawCircle(cx+(float)Math.cos(phase)*radius,cy+(float)Math.sin(phase)*radius,radius*.045f,paint); }
        if(!success && isShown()) postInvalidateDelayed(33);
    }
}
