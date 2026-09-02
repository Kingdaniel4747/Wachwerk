package de.danberg.wachwerk;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.PowerManager;

import java.util.Calendar;

public class BedtimeReceiver extends BroadcastReceiver {
    private static final String PREFS = "wachwerk_bedtime";
    private static final String ACTION_START = "de.danberg.wachwerk.BEDTIME_START";
    private static final String ACTION_REPEAT = "de.danberg.wachwerk.BEDTIME_REPEAT";
    private static final int REQUEST_START = 27001;
    private static final int REQUEST_REPEAT = 27002;

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean("enabled", false)) return;
        MainActivity.createNotificationChannels(context);

        if (ACTION_START.equals(intent.getAction())) {
            long now = System.currentTimeMillis();
            boolean screenOn = isScreenOn(context);
            long nextReminderAt = now + nextCadenceMinutes(prefs, 0) * 60_000L;
            prefs.edit().putLong("started", now).putInt("count", 0)
                .putLong("screenOffSince", screenOn ? 0L : now)
                .putLong("nextReminderAt", nextReminderAt).apply();
            if (screenOn) postReminder(context, 0); else cancelVisibleReminders(context);
            scheduleDailyStart(context, prefs);
            scheduleNextCheck(context, prefs);
            return;
        }

        if (ACTION_REPEAT.equals(intent.getAction())) {
            long started = prefs.getLong("started", 0L);
            long now = System.currentTimeMillis();
            if (started == 0L) return;
            if (now - started > 16L * 60L * 60L * 1000L) { stopSession(context); return; }

            if (!isScreenOn(context)) {
                long screenOffSince = prefs.getLong("screenOffSince", 0L);
                if (screenOffSince == 0L) {
                    screenOffSince = now;
                    prefs.edit().putLong("screenOffSince", screenOffSince).apply();
                }
                cancelVisibleReminders(context);
                long confirmationMillis = Math.max(1, prefs.getInt("sleepDetectMinutes", 60)) * 60_000L;
                if (now - screenOffSince >= confirmationMillis) { stopSession(context); return; }
                scheduleNextCheck(context, prefs);
                return;
            }

            prefs.edit().putLong("screenOffSince", 0L).apply();
            long nextReminderAt = prefs.getLong("nextReminderAt", now);
            if (now + 1_000L >= nextReminderAt) {
                int count = prefs.getInt("count", 0) + 1;
                long following = now + nextCadenceMinutes(prefs, count) * 60_000L;
                prefs.edit().putInt("count", count).putLong("nextReminderAt", following).apply();
                postReminder(context, count);
            }
            scheduleNextCheck(context, prefs);
        }
    }

    public static void syncPlan(Context context, String time, int interval, int minimumInterval, int sleepDetectMinutes, String mode, String message, boolean enabled) {
        interval = Math.max(1, Math.min(1440, interval));
        minimumInterval = Math.max(1, Math.min(interval, minimumInterval));
        sleepDetectMinutes = Math.max(5, Math.min(720, sleepDetectMinutes));
        if (time == null || !time.matches("\\d{2}:\\d{2}")) time = "22:00";
        if (!"urgent".equals(mode)) mode = "fixed";
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (message == null || message.isBlank()) message = "Zeit, das Handy wegzulegen und schlafen zu gehen.";
        String signature = time + "|" + interval + "|" + minimumInterval + "|" + sleepDetectMinutes + "|" + mode + "|" + message + "|" + enabled;
        if (signature.equals(prefs.getString("signature", ""))) return;

        prefs.edit().putString("signature", signature).putString("time", time)
            .putInt("interval", interval).putInt("minimumInterval", minimumInterval).putInt("sleepDetectMinutes", sleepDetectMinutes)
            .putString("mode", mode).putString("message", message).putBoolean("enabled", enabled)
            .putLong("started", 0L).putLong("screenOffSince", 0L).putLong("nextReminderAt", 0L).putInt("count", 0).apply();
        cancel(context);
        if (enabled) scheduleDailyStart(context, prefs);
    }

    public static void restorePlan(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean("enabled", false)) return;
        scheduleDailyStart(context, prefs);
        long started = prefs.getLong("started", 0L);
        if (started > 0L && System.currentTimeMillis() - started < 16L * 60L * 60L * 1000L) {
            scheduleNextCheck(context, prefs);
        }
    }

    private static void scheduleDailyStart(Context context, SharedPreferences prefs) {
        String[] parts = prefs.getString("time", "22:00").split(":");
        int hour = parse(parts, 0, 22);
        int minute = parse(parts, 1, 0);
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, hour);
        target.set(Calendar.MINUTE, minute);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);
        if (target.getTimeInMillis() <= System.currentTimeMillis() + 1500L) target.add(Calendar.DAY_OF_YEAR, 1);
        schedule(context, ACTION_START, REQUEST_START, target.getTimeInMillis());
    }

    private static int nextCadenceMinutes(SharedPreferences prefs, int count) {
        int base = prefs.getInt("interval", 5);
        int minutes = base;
        if ("urgent".equals(prefs.getString("mode", "fixed"))) {
            int minimum = Math.max(1, prefs.getInt("minimumInterval", 1));
            minutes = Math.max(minimum, (int) Math.floor(base / Math.pow(2d, Math.max(0, count))));
        }
        return Math.max(1, minutes);
    }

    private static void scheduleNextCheck(Context context, SharedPreferences prefs) {
        long now = System.currentTimeMillis();
        long nextReminder = prefs.getLong("nextReminderAt", now + 5L * 60_000L);
        long triggerAt = Math.min(nextReminder, now + 5L * 60_000L);
        long screenOffSince = prefs.getLong("screenOffSince", 0L);
        if (screenOffSince > 0L) {
            long sleepConfirmedAt = screenOffSince + Math.max(5, prefs.getInt("sleepDetectMinutes", 60)) * 60_000L;
            triggerAt = Math.min(triggerAt, sleepConfirmedAt);
        }
        schedule(context, ACTION_REPEAT, REQUEST_REPEAT, Math.max(now + 5_000L, triggerAt));
    }

    public static void stopSession(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putLong("started", 0L).putLong("screenOffSince", 0L).putLong("nextReminderAt", 0L).putInt("count", 0).apply();
        cancelRepeat(context);
        cancelVisibleReminders(context);
    }

    private static void schedule(Context context, String action, int requestCode, long triggerAt) {
        Intent intent = new Intent(context, BedtimeReceiver.class).setAction(action);
        PendingIntent pending = PendingIntent.getBroadcast(context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        try {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
        } catch (SecurityException denied) {
            manager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending);
        }
    }

    private static void cancel(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        for (int code : new int[]{REQUEST_START, REQUEST_REPEAT}) {
            String action = code == REQUEST_START ? ACTION_START : ACTION_REPEAT;
            PendingIntent pending = PendingIntent.getBroadcast(context, code,
                new Intent(context, BedtimeReceiver.class).setAction(action),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pending != null) manager.cancel(pending);
        }
    }

    private static void cancelRepeat(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pending = PendingIntent.getBroadcast(context, REQUEST_REPEAT,
            new Intent(context, BedtimeReceiver.class).setAction(ACTION_REPEAT),
            PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pending != null) manager.cancel(pending);
    }

    private static void postReminder(Context context, int count) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String custom = prefs.getString("message", "Zeit, das Handy wegzulegen und schlafen zu gehen.");
        String message = count == 0 ? custom : custom + (count > 2 ? " Jetzt wirklich." : " Wachwerk erinnert dich erneut.");
        Intent open = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent content = PendingIntent.getActivity(context, 28000, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, MainActivity.BEDTIME_CHANNEL)
            .setSmallIcon(de.danberg.wachwerk.R.drawable.ic_notification)
            .setColor(Color.rgb(155, 245, 177))
            .setContentTitle("Wachwerk · Einschlaf-Coach")
            .setContentText(message)
            .setStyle(new Notification.BigTextStyle().bigText(message))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setPriority(Notification.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(content)
            .build();
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        int notificationId = 8800 + (count % 2);
        manager.cancel(notificationId == 8800 ? 8801 : 8800);
        manager.notify(notificationId, notification);
    }

    private static boolean isScreenOn(Context context) {
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return power != null && power.isInteractive();
    }

    private static void cancelVisibleReminders(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.cancel(8800); manager.cancel(8801);
    }

    private static int parse(String[] parts, int index, int fallback) {
        try { return Integer.parseInt(parts[index]); }
        catch (Exception ignored) { return fallback; }
    }
}
