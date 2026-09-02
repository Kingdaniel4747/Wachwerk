package de.danberg.wachwerk;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class AppBlockerStore {
    private static final String PREFS = "wachwerk_blocker";
    private static final String ENABLED = "enabled";
    private static final String PACKAGES = "packages";
    private static final String TOKEN = "nfc_token";
    private static final String METHOD = "method";
    private static final String QR_TOKEN = "qr_token";
    private static final String LIMITS = "daily_limits";
    private static final String WINDOWS = "allowed_windows";
    private static final String LIMIT_REMINDERS = "limit_reminders";
    private static final String LIMIT_REMINDER_MINUTES = "limit_reminder_minutes";
    private static final String PASSWORD_HASH = "password_hash";

    private AppBlockerStore() {}

    public static void sync(Context context, String json) {
        try {
            JSONObject data = new JSONObject(json == null ? "{}" : json);
            JSONArray packages = data.optJSONArray("packages");
            Set<String> values = new HashSet<>();
            if (packages != null) for (int i = 0; i < packages.length(); i++) {
                String value = packages.optString(i, "");
                if (!value.isEmpty() && !value.equals(context.getPackageName())) values.add(value);
            }
            JSONObject incomingLimits = data.optJSONObject("limits");
            JSONObject cleanLimits = new JSONObject();
            if (incomingLimits != null) {
                java.util.Iterator<String> keys = incomingLimits.keys();
                while (keys.hasNext()) {
                    String packageName = keys.next();
                    int minutes = incomingLimits.optInt(packageName, 0);
                    if (!packageName.equals(context.getPackageName()) && minutes > 0 && minutes <= 1_440) cleanLimits.put(packageName, minutes);
                }
            }
            JSONObject incomingWindows = data.optJSONObject("windows");
            JSONObject cleanWindows = new JSONObject();
            if (incomingWindows != null) {
                java.util.Iterator<String> keys = incomingWindows.keys();
                while (keys.hasNext()) {
                    String packageName = keys.next();
                    JSONObject window = incomingWindows.optJSONObject(packageName);
                    if (window == null || packageName.equals(context.getPackageName())) continue;
                    String start = window.optString("start", "12:00"), end = window.optString("end", "18:00");
                    if (validTime(start) && validTime(end)) cleanWindows.put(packageName,
                        new JSONObject().put("start", start).put("end", end));
                }
            }
            boolean limitsActive = scopeEnabled(context, "limits"), windowsActive = scopeEnabled(context, "windows");
            SharedPreferences.Editor editor = prefs(context).edit();
            JSONObject methods = data.optJSONObject("methods");
            // Freeze only the active scope; another scope can still select its own key.
            // The old single method remains the migration fallback for existing installs.
            for (String scope : new String[]{"instant", "limits", "windows"}) {
                String selected = methods == null ? data.optString("method", method(context, scope))
                    : methods.optString(scope, method(context, scope));
                editor.putString(METHOD + "." + scope, scopeEnabled(context, scope) ? method(context, scope) : normalizeMethod(selected));
            }
            editor
                .putBoolean("limitsEnabled", limitsActive).putBoolean("windowsEnabled", windowsActive)
                .putBoolean(ENABLED, isEnabled(context))
                .putStringSet(PACKAGES, isEnabled(context) ? packages(context) : values)
                .putString(TOKEN, token(context))
                .putString(QR_TOKEN, data.optString("qrToken", "wachwerk-personal-code"))
                .putString(LIMITS, limitsActive ? limitsJson(context) : cleanLimits.toString())
                .putString(WINDOWS, windowsActive ? windowsJson(context) : cleanWindows.toString())
                .putBoolean(LIMIT_REMINDERS, data.optBoolean("limitReminderEnabled", false))
                .putInt(LIMIT_REMINDER_MINUTES, Math.max(1, Math.min(1_440, data.optInt("limitReminderMinutes", 10))))
                .apply();
        } catch (Exception ignored) {}
    }

    public static boolean isEnabled(Context context) { return prefs(context).getBoolean(ENABLED, false); }
    public static void setEnabled(Context context, boolean enabled) { prefs(context).edit().putBoolean(ENABLED, enabled).apply(); }
    public static boolean toggle(Context context) { boolean value = !isEnabled(context); setEnabled(context, value); return value; }
    public static boolean scopeEnabled(Context context, String scope) {
        if ("limits".equals(scope)) return prefs(context).getBoolean("limitsEnabled", !limitsJson(context).equals("{}"));
        if ("windows".equals(scope)) return prefs(context).getBoolean("windowsEnabled", !windowsJson(context).equals("{}"));
        return isEnabled(context);
    }
    public static boolean anyEnabled(Context context) { return isEnabled(context) || scopeEnabled(context,"limits") || scopeEnabled(context,"windows"); }
    public static boolean toggleScope(Context context, String scope) {
        if (!"limits".equals(scope) && !"windows".equals(scope)) return toggle(context);
        boolean enabled = !scopeEnabled(context, scope);
        prefs(context).edit().putBoolean(scope + "Enabled", enabled).apply();
        return enabled;
    }
    public static String token(Context context) { return prefs(context).getString(TOKEN, ""); }
    public static void setToken(Context context, String token) { prefs(context).edit().putString(TOKEN, token == null ? "" : token).apply(); }
    public static String method(Context context) { return method(context, "instant"); }
    public static String method(Context context, String scope) {
        return normalizeMethod(prefs(context).getString(METHOD + "." + normalizeScope(scope), prefs(context).getString(METHOD, "nfc")));
    }
    public static String normalizeScope(String scope) { return "limits".equals(scope) ? "limits" : "windows".equals(scope) ? "windows" : "instant"; }
    public static String qrToken(Context context) { return prefs(context).getString(QR_TOKEN, "wachwerk-personal-code"); }
    public static Set<String> packages(Context context) { return new HashSet<>(prefs(context).getStringSet(PACKAGES, new HashSet<>())); }
    public static boolean isBlocked(Context context, String packageName) { return isEnabled(context) && packages(context).contains(packageName); }
    public static int dailyLimitMinutes(Context context, String packageName) {
        if (!scopeEnabled(context, "limits")) return 0;
        try { return new JSONObject(prefs(context).getString(LIMITS, "{}")).optInt(packageName, 0); }
        catch (Exception ignored) { return 0; }
    }
    public static String limitsJson(Context context) { return prefs(context).getString(LIMITS, "{}"); }
    public static String windowsJson(Context context) { return prefs(context).getString(WINDOWS, "{}"); }
    public static boolean limitRemindersEnabled(Context context) { return prefs(context).getBoolean(LIMIT_REMINDERS, false); }
    public static int limitReminderMinutes(Context context) { return Math.max(1, prefs(context).getInt(LIMIT_REMINDER_MINUTES, 10)); }
    public static boolean hasAllowedWindow(Context context, String packageName) {
        try { return new JSONObject(windowsJson(context)).has(packageName); } catch (Exception ignored) { return false; }
    }
    public static boolean isOutsideAllowedWindow(Context context, String packageName) {
        if (!scopeEnabled(context, "windows")) return false;
        try {
            JSONObject window = new JSONObject(windowsJson(context)).optJSONObject(packageName);
            if (window == null) return false;
            int start = minutes(window.optString("start", "12:00"));
            int end = minutes(window.optString("end", "18:00"));
            java.util.Calendar now = java.util.Calendar.getInstance();
            int current = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE);
            boolean allowed = start == end || (start < end ? current >= start && current < end : current >= start || current < end);
            return !allowed;
        } catch (Exception ignored) { return false; }
    }
    public static boolean hasPassword(Context context) { return hasPassword(context, "instant"); }
    public static boolean hasPassword(Context context, String scope) { return !passwordHash(context, scope).isEmpty(); }
    public static boolean setPassword(Context context, String password) { return setPassword(context, password, "instant"); }
    public static boolean setPassword(Context context, String password, String scope) {
        if (scopeEnabled(context, scope) || password == null || password.length() < 4) return false;
        prefs(context).edit().putString(PASSWORD_HASH + "." + normalizeScope(scope), hash(password)).apply();
        return true;
    }
    public static boolean verifyPassword(Context context, String password) { return verifyPassword(context, password, "instant"); }
    public static boolean verifyPassword(Context context, String password, String scope) {
        String expected = passwordHash(context, scope);
        return !expected.isEmpty() && expected.equals(hash(password == null ? "" : password));
    }
    private static String passwordHash(Context context, String scope) {
        return prefs(context).getString(PASSWORD_HASH + "." + normalizeScope(scope), prefs(context).getString(PASSWORD_HASH, ""));
    }

    public static String stateJson(Context context) {
        try {
            JSONObject state = new JSONObject().put("enabled", isEnabled(context)).put("limitsEnabled", scopeEnabled(context,"limits")).put("windowsEnabled", scopeEnabled(context,"windows")).put("nfcToken", token(context))
                .put("method", method(context)).put("qrToken", qrToken(context)).put("hasPassword", hasPassword(context))
                .put("limits", new JSONObject(limitsJson(context))).put("windows", new JSONObject(windowsJson(context)))
                .put("limitReminderEnabled", limitRemindersEnabled(context)).put("limitReminderMinutes", limitReminderMinutes(context));
            JSONObject methods = new JSONObject(), passwords = new JSONObject();
            for (String scope : new String[]{"instant", "limits", "windows"}) {
                methods.put(scope, method(context, scope));
                passwords.put(scope, hasPassword(context, scope));
            }
            state.put("methods", methods).put("hasPasswords", passwords);
            JSONArray packages = new JSONArray();
            for (String value : packages(context)) packages.put(value);
            return state.put("packages", packages).toString();
        } catch (Exception ignored) { return "{\"enabled\":false,\"packages\":[],\"nfcToken\":\"\",\"method\":\"nfc\",\"qrToken\":\"wachwerk-personal-code\",\"hasPassword\":false,\"limits\":{},\"windows\":{},\"limitReminderEnabled\":false,\"limitReminderMinutes\":10}"; }
    }

    private static SharedPreferences prefs(Context context) { return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    private static String normalizeMethod(String value) { return "qr".equals(value) ? "qr" : "password".equals(value) ? "password" : "nfc"; }
    private static boolean validTime(String value) { return value != null && value.matches("(?:[01]\\d|2[0-3]):[0-5]\\d"); }
    private static int minutes(String value) { String[] parts = value.split(":"); return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]); }
    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format(java.util.Locale.ROOT, "%02x", item));
            return result.toString();
        } catch (Exception ignored) { return ""; }
    }
}
