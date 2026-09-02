package de.danberg.wachwerk;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;

import java.time.LocalDate;

public final class AppLimitNotifier {
    private static final String PREFS = "wachwerk_limit_notifications";
    private AppLimitNotifier() {}

    public static void check(Context context, String packageName) {
        if (!AppBlockerStore.limitRemindersEnabled(context)) return;
        int limit = AppBlockerStore.dailyLimitMinutes(context, packageName);
        if (limit <= 0) return;
        long usedMinutes = DailyUsageStore.usedSeconds(context, packageName) / 60L;
        int interval = AppBlockerStore.limitReminderMinutes(context);
        long bucket = usedMinutes / interval;
        if (bucket <= 0 || usedMinutes >= limit) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = LocalDate.now() + ":" + packageName;
        if (prefs.getLong(key, 0L) >= bucket) return;
        prefs.edit().putLong(key, bucket).apply();
        MainActivity.createNotificationChannels(context);
        String label = appLabel(context, packageName);
        long remaining = Math.max(0L, limit - usedMinutes);
        Intent open = new Intent(context, MainActivity.class).putExtra("openBlocker", true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(context, 61800 + Math.abs(packageName.hashCode() % 1000), open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, MainActivity.LIMIT_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setColor(Color.rgb(255, 211, 71))
            .setContentTitle(label + " · noch " + remaining + " Minuten")
            .setContentText(usedMinutes + " von " + limit + " Minuten heute genutzt")
            .setCategory(Notification.CATEGORY_REMINDER).setPriority(Notification.PRIORITY_DEFAULT)
            .setAutoCancel(true).setContentIntent(content).build();
        context.getSystemService(NotificationManager.class).notify(61800 + Math.abs(packageName.hashCode() % 1000), notification);
    }

    private static String appLabel(Context context, String packageName) {
        try { return String.valueOf(context.getPackageManager().getApplicationLabel(context.getPackageManager().getApplicationInfo(packageName, 0))); }
        catch (Exception ignored) { return packageName; }
    }
}
