package de.danberg.wachwerk;

import android.content.Context;

/** One reason, one authentication, one release. Other scopes are never cleared as a side effect. */
public final class BlockPolicy {
    private BlockPolicy() {}
    public static String scope(String reason) {
        return "limit".equals(reason) ? "limits" : "schedule".equals(reason) ? "windows" : "instant";
    }
    private static boolean keyReady(Context context, String scope) {
        return !"password".equals(AppBlockerStore.method(context, scope)) || AppBlockerStore.hasPassword(context, scope);
    }
    public static String reason(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) return "";
        if (MorningBlockStore.isBlocked(context,packageName)) return "morning";
        if (AppBlockerStore.isBlocked(context, packageName) && keyReady(context, "instant")) return "direct";
        if (FocusTimerScheduler.isPackageBlocked(context, packageName)) return "focus";
        if (AppBlockerStore.isOutsideAllowedWindow(context, packageName)
                && !DailyUsageStore.windowAllowedForToday(context, packageName) && keyReady(context, "windows")) return "schedule";
        if (DailyUsageStore.limitReached(context, packageName) && keyReady(context, "limits")) return "limit";
        return "";
    }
    public static void release(Context context, String packageName, String reason, int minutes) {
        int duration = Math.max(1, Math.min(1440, minutes));
        switch (reason) {
            case "limit": DailyUsageStore.allowForMinutes(context, packageName, duration); break;
            case "schedule": DailyUsageStore.allowWindowForMinutes(context, packageName, duration); break;
            case "focus": FocusTimerScheduler.allowPackageForSession(context, packageName); break;
            case "direct": AppBlockerStore.setEnabled(context, false); break;
            default: break;
        }
    }
}
