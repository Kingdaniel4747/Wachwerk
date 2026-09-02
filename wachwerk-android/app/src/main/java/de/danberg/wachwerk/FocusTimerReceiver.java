package de.danberg.wachwerk;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.PowerManager;

public class FocusTimerReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if ("de.danberg.wachwerk.CANCEL_FOCUS".equals(intent.getAction())) { FocusTimerScheduler.cancel(context); return; }
        try { if (!new org.json.JSONObject(FocusTimerScheduler.stateJson(context)).optBoolean("active")) return; } catch (Exception ignored) { return; }
        MainActivity.createNotificationChannels(context);
        FocusTimerScheduler.markRinging(context);
        String phase = FocusTimerScheduler.phase(context);
        String title = "work".equals(phase) ? "Fokuszeit geschafft" : "Pause beendet";
        String text = "work".equals(phase) ? "Zeit für deine Pause." : "Bereit für die nächste Fokusphase?";
        Intent activity = new Intent(context, FocusTimerActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent open = PendingIntent.getActivity(context, 29202, activity,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, MainActivity.FOCUS_CHANNEL)
            .setSmallIcon(de.danberg.wachwerk.R.drawable.ic_notification)
            .setColor(Color.rgb(255, 211, 71)).setContentTitle(title).setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM).setPriority(Notification.PRIORITY_MAX)
            .setVisibility(Notification.VISIBILITY_PUBLIC).setOngoing(true).setAutoCancel(false)
            .setFullScreenIntent(open, true).setContentIntent(open).build();
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.notify(9292, notification);
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock lock = power.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE,
            "Wachwerk:FocusTimerWake");
        lock.acquire(12_000L);
        try { context.startActivity(activity); } catch (Exception ignored) {}
    }
}
