package de.danberg.wachwerk;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;

public class MorningCheckReceiver extends BroadcastReceiver {
    public static final int REQUEST_CODE = 31471;
    public static final int NOTIFICATION_ID = 31472;

    @Override
    public void onReceive(Context context, Intent intent) {
        MainActivity.createNotificationChannels(context);
        Intent open = new Intent(context, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("openMorningCheck", true);
        PendingIntent content = PendingIntent.getActivity(context, REQUEST_CODE, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, MainActivity.MORNING_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(Color.rgb(155, 245, 177))
            .setContentTitle("Wachwerk · Morgencheck")
            .setContentText("Wie lief dein Aufstehen heute?")
            .setStyle(new Notification.BigTextStyle().bigText("Direkt auf, zu spät oder verschlafen? Ein Tipp genügt und verbessert deine persönliche Schlafanalyse."))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(content)
            .build();
        context.getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification);
    }

    public static void schedule(Context context, int delayMinutes) {
        Intent intent = new Intent(context, MorningCheckReceiver.class);
        PendingIntent pending = PendingIntent.getBroadcast(context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        long at = System.currentTimeMillis() + Math.max(0, delayMinutes) * 60_000L;
        if (delayMinutes == 0) {
            context.sendBroadcast(intent);
            return;
        }
        try { manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending); }
        catch (SecurityException denied) { manager.set(AlarmManager.RTC_WAKEUP, at, pending); }
    }
}
