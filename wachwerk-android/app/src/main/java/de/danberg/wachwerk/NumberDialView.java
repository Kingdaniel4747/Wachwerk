package de.danberg.wachwerk;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import java.util.function.IntConsumer;

public final class NumberDialView extends View {
    private final UiPalette palette;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int value;
    private double lastAngle, remainder;
    private final IntConsumer changed;
    public NumberDialView(Context context, int initial, IntConsumer changed) { super(context); palette=UiPalette.from(context); value=initial; this.changed=changed; setFocusable(true); setContentDescription("Minuten durch Drehen wählen"); }
    public void step(int amount) { value=Math.max(1,Math.min(1440,value+amount)); changed.accept(value); invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(cx,cy)*.9f;
        paint.setColor(palette.map(Color.rgb(12,33,48))); canvas.drawCircle(cx,cy,r,paint);
        paint.setStrokeWidth(2*getResources().getDisplayMetrics().density);
        for(int i=0;i<60;i++) { double a=Math.toRadians(i*6+value*6-90); float inner=r-(i%5==0?20:10)*getResources().getDisplayMetrics().density; paint.setColor(i%5==0?palette.map(Color.rgb(168,195,216)):palette.map(Color.rgb(58,85,106))); canvas.drawLine(cx+(float)Math.cos(a)*inner,cy+(float)Math.sin(a)*inner,cx+(float)Math.cos(a)*(r-5),cy+(float)Math.sin(a)*(r-5),paint); }
        paint.setColor(palette.map(Color.rgb(155,245,177))); canvas.drawCircle(cx,cy-r,5*getResources().getDisplayMetrics().density,paint);
        paint.setColor(Color.WHITE); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(48*getResources().getDisplayMetrics().scaledDensity); canvas.drawText(String.valueOf(value),cx,cy+8*getResources().getDisplayMetrics().density,paint);
        paint.setColor(palette.map(Color.rgb(170,194,210))); paint.setTextSize(14*getResources().getDisplayMetrics().scaledDensity); canvas.drawText("Minuten",cx,cy+36*getResources().getDisplayMetrics().density,paint);
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        double a=Math.toDegrees(Math.atan2(e.getY()-getHeight()/2f,e.getX()-getWidth()/2f));
        if(e.getAction()==MotionEvent.ACTION_DOWN){lastAngle=a;remainder=0;getParent().requestDisallowInterceptTouchEvent(true);return true;}
        if(e.getAction()==MotionEvent.ACTION_MOVE){double delta=a-lastAngle;if(delta>180)delta-=360;if(delta< -180)delta+=360;lastAngle=a;remainder+=delta;int steps=(int)(remainder/6);if(steps!=0){remainder-=steps*6;step(steps);}return true;}
        if(e.getAction()==MotionEvent.ACTION_UP){performClick();return true;}return true;
    }
    @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info); info.setClassName("android.widget.SeekBar");
        info.setRangeInfo(android.view.accessibility.AccessibilityNodeInfo.RangeInfo.obtain(android.view.accessibility.AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,1,1440,value));
        info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
    }
    @Override public boolean performAccessibilityAction(int action, android.os.Bundle arguments) {
        if(action==android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD){step(1);return true;}
        if(action==android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){step(-1);return true;}
        return super.performAccessibilityAction(action,arguments);
    }
    @Override public boolean onKeyDown(int key, android.view.KeyEvent event) {
        if(key==android.view.KeyEvent.KEYCODE_DPAD_UP || key==android.view.KeyEvent.KEYCODE_DPAD_RIGHT){step(1);return true;}
        if(key==android.view.KeyEvent.KEYCODE_DPAD_DOWN || key==android.view.KeyEvent.KEYCODE_DPAD_LEFT){step(-1);return true;}
        return super.onKeyDown(key,event);
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
