package de.danberg.wachwerk;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;

import org.json.JSONObject;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class DailyUsageStore {
    private static final String PREFS = "wachwerk_daily_usage";
    private static final String DATE = "date";
    private static final String PREFIX = "ms:";
    private static final String OVERRIDE_PREFIX = "override:";
    private static final String WINDOW_OVERRIDE_PREFIX = "window_override:";
    private static final String OVERRIDE_UNTIL_PREFIX = "override_until:";
    private static final String WINDOW_OVERRIDE_UNTIL_PREFIX = "window_override_until:";

    private DailyUsageStore() {}

    public static synchronized void addMillis(Context context, String packageName, long millis) {
        if (packageName == null || packageName.isEmpty() || millis <= 0) return;
        SharedPreferences preferences = today(context);
        String key = PREFIX + packageName;
        preferences.edit().putLong(key, preferences.getLong(key, 0L) + Math.min(millis, 15_000L)).apply();
    }

    public static synchronized long usedSeconds(Context context, String packageName) {
        long accessibilitySeconds = today(context).getLong(PREFIX + packageName, 0L) / 1_000L;
        if (!hasUsageAccess(context)) return accessibilitySeconds;
        Set<String> requested = new HashSet<>();
        requested.add(packageName);
        Map<String, Long> system = usageSeconds(context, requested);
        return system == null ? accessibilitySeconds : system.getOrDefault(packageName, 0L);
    }

    public static boolean limitReached(Context context, String packageName) {
        int minutes = AppBlockerStore.dailyLimitMinutes(context, packageName);
        return minutes > 0 && !limitAllowed(context, packageName)
            && usedSeconds(context, packageName) >= minutes * 60L;
    }

    private static boolean limitAllowed(Context context, String packageName) {
        SharedPreferences preferences = today(context);
        return preferences.getBoolean(OVERRIDE_PREFIX + packageName, false)
            || preferences.getLong(OVERRIDE_UNTIL_PREFIX + packageName, 0L) > System.currentTimeMillis();
    }

    public static void allowForToday(Context context, String packageName) {
        if (packageName != null && !packageName.isEmpty()) today(context).edit().putBoolean(OVERRIDE_PREFIX + packageName, true).apply();
    }

    public static void allowForMinutes(Context context, String packageName, int minutes) {
        if (packageName != null && !packageName.isEmpty()) today(context).edit()
            .putLong(OVERRIDE_UNTIL_PREFIX + packageName, System.currentTimeMillis() + Math.max(1, minutes) * 60_000L).apply();
    }

    public static void allowWindowForToday(Context context, String packageName) {
        if (packageName != null && !packageName.isEmpty()) today(context).edit().putBoolean(WINDOW_OVERRIDE_PREFIX + packageName, true).apply();
    }

    public static void allowWindowForMinutes(Context context, String packageName, int minutes) {
        if (packageName != null && !packageName.isEmpty()) today(context).edit()
            .putLong(WINDOW_OVERRIDE_UNTIL_PREFIX + packageName, System.currentTimeMillis() + Math.max(1, minutes) * 60_000L).apply();
    }

    public static boolean windowAllowedForToday(Context context, String packageName) {
        SharedPreferences preferences = today(context);
        return preferences.getBoolean(WINDOW_OVERRIDE_PREFIX + packageName, false)
            || preferences.getLong(WINDOW_OVERRIDE_UNTIL_PREFIX + packageName, 0L) > System.currentTimeMillis();
    }

    public static String stateJson(Context context) {
        JSONObject result = new JSONObject();
        try {
            JSONObject limits = new JSONObject(AppBlockerStore.limitsJson(context));
            Set<String> packages = new HashSet<>();
            Iterator<String> keys = limits.keys();
            while (keys.hasNext()) packages.add(keys.next());
            Map<String, Long> systemUsage = hasUsageAccess(context) ? usageSeconds(context, packages) : null;
            SharedPreferences stored = today(context);
            for (String packageName : packages) result.put(packageName,
                systemUsage == null ? stored.getLong(PREFIX + packageName, 0L) / 1_000L : systemUsage.getOrDefault(packageName, 0L));
        } catch (Exception ignored) {}
        return result.toString();
    }

    public static boolean hasUsageAccess(Context context) {
        AppOpsManager operations = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (operations == null) return false;
        return operations.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    private static long cachedAt, cachedDay;
    private static Map<String, Long> cachedUsage;
    private static synchronized Map<String, Long> usageSeconds(Context context, Set<String> requestedPackages) {
        long now = System.currentTimeMillis();
        long start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        if (cachedUsage != null && cachedDay == start && now >= cachedAt && now - cachedAt < 1000L) return cachedUsage;
        UsageStatsManager manager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (manager == null) return null;
        try {
            // Look back one day to recover a foreground session crossing midnight.
            UsageEvents events = manager.queryEvents(start - 86_400_000L, now);
            if (events == null) return null; // e.g. credential storage unavailable after a reboot
            UsageTimeline timeline = new UsageTimeline(start, now);
            UsageEvents.Event event = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                String instance = event.getClassName();
                timeline.event(event.getTimeStamp(), event.getEventType(), event.getPackageName(), instance);
            }
            cachedUsage = timeline.finish(); cachedAt = now; cachedDay = start;
            return cachedUsage;
        } catch (SecurityException | IllegalStateException unavailable) { return null; }
    }

    private static SharedPreferences today(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String today = LocalDate.now().toString();
        if (!today.equals(preferences.getString(DATE, ""))) {
            preferences.edit().clear().putString(DATE, today).commit();
        }
        return preferences;
    }
}
