package de.danberg.wachwerk;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.widget.RemoteViews;

import org.json.JSONObject;
import org.json.JSONArray;

import java.util.HashSet;
import java.util.Set;

public final class FocusTimerScheduler {
    private static final String PREFS = "wachwerk_focus_timer";
    private static final String ACTION = "de.danberg.wachwerk.FOCUS_TIMER";
    private static final int REQUEST_CODE = 29201;

    private FocusTimerScheduler() {}

    public static void start(Context context, int workMinutes, int breakMinutes, int rounds, String packagesJson, boolean silenceNotifications) {
        workMinutes = Math.max(1, Math.min(1_440, workMinutes));
        breakMinutes = Math.max(1, Math.min(1_440, breakMinutes));
        rounds = Math.max(1, Math.min(99, rounds));
        SharedPreferences prefs = prefs(context);
        Set<String> packages = new HashSet<>();
        try { JSONArray array = new JSONArray(packagesJson == null ? "[]" : packagesJson); for (int i = 0; i < array.length(); i++) if (!array.optString(i).isEmpty()) packages.add(array.optString(i)); }
        catch (Exception ignored) {}
        prefs.edit().putBoolean("active", true).putBoolean("ringing", false)
            .putString("phase", "work").putInt("workMinutes", workMinutes)
            .putInt("breakMinutes", breakMinutes).putInt("rounds", rounds)
            .putInt("round", 1).putStringSet("blockedPackages", packages)
            .putStringSet("allowedPackages", new HashSet<>()).putBoolean("silenceNotifications", silenceNotifications).apply();
        schedulePhase(context, workMinutes);
    }

    public static void advance(Context context) {
        SharedPreferences prefs = prefs(context);
        if (!prefs.getBoolean("active", false)) return;
        String phase = prefs.getString("phase", "work");
        int round = Math.max(1, prefs.getInt("round", 1));
        int rounds = Math.max(1, prefs.getInt("rounds", 1));
        if ("work".equals(phase)) {
            prefs.edit().putString("phase", "break").putBoolean("ringing", false).apply();
            schedulePhase(context, Math.max(1, prefs.getInt("breakMinutes", 5)));
        } else if (round < rounds) {
            prefs.edit().putString("phase", "work").putInt("round", round + 1).putBoolean("ringing", false).apply();
            schedulePhase(context, Math.max(1, prefs.getInt("workMinutes", 45)));
        } else {
            cancel(context);
        }
    }

    public static void markRinging(Context context) {
        prefs(context).edit().putBoolean("ringing", true).putLong("endAt", System.currentTimeMillis()).apply();
        restoreDnd(context);
    }

    public static void cancel(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pending = pending(context, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pending != null) manager.cancel(pending);
        restoreDnd(context);
        prefs(context).edit().putBoolean("active", false).putBoolean("ringing", false).putLong("endAt", 0L).apply();
        android.app.NotificationManager notifications = context.getSystemService(android.app.NotificationManager.class);
        if (notifications != null) notifications.cancel(9292);
    }

    public static void restore(Context context) {
        SharedPreferences prefs = prefs(context);
        if (!prefs.getBoolean("active", false) || prefs.getBoolean("ringing", false)) return;
        long endAt = prefs.getLong("endAt", 0L);
        if (endAt <= 0L) { cancel(context); return; }
        scheduleAt(context, Math.max(System.currentTimeMillis() + 1_500L, endAt));
        applyProtection(context);
        showOngoing(context, endAt);
    }

    public static String stateJson(Context context) {
        SharedPreferences prefs = prefs(context);
        try {
            return new JSONObject().put("active", prefs.getBoolean("active", false))
                .put("ringing", prefs.getBoolean("ringing", false))
                .put("phase", prefs.getString("phase", "work"))
                .put("workMinutes", prefs.getInt("workMinutes", 45))
                .put("breakMinutes", prefs.getInt("breakMinutes", 5))
                .put("rounds", prefs.getInt("rounds", 1))
                .put("round", prefs.getInt("round", 1))
                .put("silenceNotifications", prefs.getBoolean("silenceNotifications", false))
                .put("endAt", prefs.getLong("endAt", 0L)).toString();
        } catch (Exception ignored) {
            return "{\"active\":false,\"ringing\":false,\"phase\":\"work\",\"workMinutes\":45,\"breakMinutes\":5,\"rounds\":1,\"round\":1,\"endAt\":0}";
        }
    }

    public static String phase(Context context) { return prefs(context).getString("phase", "work"); }
    public static int round(Context context) { return Math.max(1, prefs(context).getInt("round", 1)); }
    public static int rounds(Context context) { return Math.max(1, prefs(context).getInt("rounds", 1)); }
    public static boolean isPackageBlocked(Context context, String packageName) {
        SharedPreferences prefs = prefs(context);
        return prefs.getBoolean("active", false) && !prefs.getBoolean("ringing", false)
            && "work".equals(prefs.getString("phase", "work"))
            && prefs.getStringSet("blockedPackages", new HashSet<>()).contains(packageName)
            && !prefs.getStringSet("allowedPackages", new HashSet<>()).contains(packageName);
    }
    public static void allowPackageForSession(Context context, String packageName) {
        SharedPreferences prefs = prefs(context);
        Set<String> allowed = new HashSet<>(prefs.getStringSet("allowedPackages", new HashSet<>()));
        allowed.add(packageName); prefs.edit().putStringSet("allowedPackages", allowed).apply();
    }

    private static void schedulePhase(Context context, int minutes) {
        long endAt = System.currentTimeMillis() + Math.max(1, minutes) * 60_000L;
        prefs(context).edit().putLong("endAt", endAt).apply();
        scheduleAt(context, endAt);
        applyProtection(context);
        showOngoing(context, endAt);
    }

    private static void scheduleAt(Context context, long endAt) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pending = pending(context, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try { manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pending); }
        catch (SecurityException denied) { manager.set(AlarmManager.RTC_WAKEUP, endAt, pending); }
    }

    private static PendingIntent pending(Context context, int flags) {
        return PendingIntent.getBroadcast(context, REQUEST_CODE,
            new Intent(context, FocusTimerReceiver.class).setAction(ACTION), flags);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean supportsLiveUpdates() {
        try { Notification.Builder.class.getMethod("setRequestPromotedOngoing", boolean.class); return true; }
        catch (ReflectiveOperationException ignored) { return false; }
    }
    public static boolean canPostLiveUpdates(Context context) {
        if (!supportsLiveUpdates()) return false;
        try { return (boolean) NotificationManager.class.getMethod("canPostPromotedNotifications").invoke(context.getSystemService(NotificationManager.class)); }
        catch (ReflectiveOperationException ignored) { return false; }
    }

    private static void showOngoing(Context context, long endAt) {
        MainActivity.createNotificationChannels(context);
        SharedPreferences prefs = prefs(context);
        String phase = prefs.getString("phase", "work");
        Intent open = new Intent(context, MainActivity.class).putExtra("openAlarms", true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(context, 29203, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = "work".equals(phase) ? "Fokus läuft" : "Pause läuft";
        String round = "Runde " + prefs.getInt("round", 1) + " von " + prefs.getInt("rounds", 1);
        long elapsedEnd = SystemClock.elapsedRealtime() + Math.max(0L, endAt - System.currentTimeMillis());
        RemoteViews compact = focusViews(context, R.layout.focus_notification_compact, title, round, elapsedEnd);
        RemoteViews expanded = focusViews(context, R.layout.focus_notification_expanded, title, round, elapsedEnd);
        Notification.Builder builder = new Notification.Builder(context, MainActivity.FOCUS_PROGRESS_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(title)
            .setContentText(round).setColor(UiPalette.from(context).accent)
            .setWhen(endAt).setUsesChronometer(true).setChronometerCountDown(true)
            .setCategory(Notification.CATEGORY_PROGRESS).setOngoing(true).setOnlyAlertOnce(true)
            .setSound(null).setContentIntent(content).setShowWhen(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC);
        // Public Android Live Update extras. Custom RemoteViews disqualify promotion.
        if (supportsLiveUpdates()) {
            android.os.Bundle extras = new android.os.Bundle();
            extras.putBoolean("android.requestPromotedOngoing", true);
            builder.addExtras(extras).setStyle(new Notification.BigTextStyle().bigText(round + " · Timer läuft"));
        } else {
            builder.setCustomContentView(compact).setCustomBigContentView(expanded)
                .setStyle(new Notification.DecoratedCustomViewStyle());
        }
        PendingIntent cancel = PendingIntent.getBroadcast(context, 29204, new Intent(context, FocusTimerReceiver.class).setAction("de.danberg.wachwerk.CANCEL_FOCUS"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        builder.addAction(new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_notification), "Beenden", cancel).build());
        context.getSystemService(NotificationManager.class).notify(9292, builder.build());
    }

    private static RemoteViews focusViews(Context context, int layout, String title, String round, long elapsedEnd) {
        RemoteViews views = new RemoteViews(context.getPackageName(), layout);
        UiPalette palette = UiPalette.from(context);
        views.setInt(R.id.focus_notification_root, "setBackgroundColor", palette.panel);
        views.setTextColor(R.id.focus_notification_title, palette.text);
        views.setTextColor(R.id.focus_notification_round, palette.muted);
        views.setTextViewText(R.id.focus_notification_title, title);
        views.setTextViewText(R.id.focus_notification_round, round);
        views.setChronometer(R.id.focus_notification_timer, elapsedEnd, null, true);
        if (Build.VERSION.SDK_INT >= 24) views.setChronometerCountDown(R.id.focus_notification_timer, true);
        return views;
    }

    private static void applyProtection(Context context) {
        SharedPreferences prefs = prefs(context);
        boolean silence = prefs.getBoolean("active", false) && "work".equals(prefs.getString("phase", "work"))
            && prefs.getBoolean("silenceNotifications", false);
        if (!silence) { restoreDnd(context); return; }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || !manager.isNotificationPolicyAccessGranted()) return;
        if (!prefs.contains("previousFilter")) prefs.edit().putInt("previousFilter", manager.getCurrentInterruptionFilter()).apply();
        try { manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE); } catch (Exception ignored) {}
    }

    private static void restoreDnd(Context context) {
        SharedPreferences prefs = prefs(context);
        if (!prefs.contains("previousFilter")) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        int previous = prefs.getInt("previousFilter", NotificationManager.INTERRUPTION_FILTER_ALL);
        if (manager != null && manager.isNotificationPolicyAccessGranted()) {
            try { manager.setInterruptionFilter(previous); } catch (Exception ignored) {}
        }
        prefs.edit().remove("previousFilter").apply();
    }
}
