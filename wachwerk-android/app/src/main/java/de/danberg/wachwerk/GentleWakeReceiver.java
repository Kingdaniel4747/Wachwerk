package de.danberg.wachwerk;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.PowerManager;

public class GentleWakeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        MainActivity.createNotificationChannels(context);
        int requestCode = intent.getIntExtra("requestCode", 1);
        Intent activity = new Intent(context, GentleWakeActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtras(intent);
        PendingIntent fullScreenPending = PendingIntent.getActivity(context, 17000 + requestCode, activity,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, MainActivity.GENTLE_CHANNEL)
            .setSmallIcon(de.danberg.wachwerk.R.drawable.ic_notification)
            .setColor(Color.rgb(255, 188, 90))
            .setContentTitle("Sanftes Licht startet")
            .setContentText("Der Bildschirm wird bis zum Wecker langsam heller.")
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_MAX)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPending, true)
            .setContentIntent(fullScreenPending)
            .build();
        context.getSystemService(NotificationManager.class).notify(17000 + requestCode, notification);

        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock lock = power.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE,
            "Wachwerk:GentleWake");
        lock.acquire(15_000L);
        try { context.startActivity(activity); } catch (Exception ignored) { /* Full-screen notification opens it if direct launch is restricted. */ }
    }
}
