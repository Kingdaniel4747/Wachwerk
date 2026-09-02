package de.danberg.wachwerk;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class AlarmScheduler {
    private static final String PREFS = "wachwerk_native";
    private static final String KEY_ALARMS = "alarms_json";
    private static final String KEY_CODES = "alarm_codes";

    private AlarmScheduler() {}

    public static void syncAlarms(Context context, String json) {
        if (json == null || json.isBlank()) json = "[]";
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (json.equals(prefs.getString(KEY_ALARMS, ""))) return;
        cancelCodes(context, prefs.getStringSet(KEY_CODES, new HashSet<>()));
        prefs.edit().putString(KEY_ALARMS, json).apply();
        scheduleAll(context, json);
    }

    public static void restoreAll(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        scheduleAll(context, prefs.getString(KEY_ALARMS, "[]"));
        BedtimeReceiver.restorePlan(context);
    }

    public static boolean canScheduleExact(Context context) {
        if (Build.VERSION.SDK_INT < 31) return true;
        return ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms();
    }

    private static void scheduleAll(Context context, String json) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> codes = new HashSet<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject alarm = array.optJSONObject(i);
                if (alarm == null || !alarm.optBoolean("enabled", false)) continue;
                String id = alarm.optString("id", String.valueOf(i));
                int code = requestCodeFor(id);
                JSONArray days = alarm.optJSONArray("days");
                String daysJson = days == null ? "[]" : days.toString();
                boolean scheduled = scheduleOne(context, code, id,
                    alarm.optString("time", "07:00"), alarm.optString("date", ""),
                    alarm.optString("label", "Wecker"), daysJson,
                    alarm.optString("challenge", "shake"), alarm.optString("sound", "Systemstandard"),
                    alarm.optBoolean("gentleWake", false), alarm.optInt("gentleMinutes", 15),
                    alarm.optString("qrToken", "wachwerk"), alarm.optString("nfcToken", ""),
                    Math.max(3, alarm.optInt("shakeCount", 12)),
                    Math.max(3, alarm.optInt("holdSeconds", 8)), Math.max(3, alarm.optInt("snakeSeconds", 10)),
                    alarm.optBoolean("snoozeEnabled", true), Math.max(1, alarm.optInt("snoozeMinutes", 10)),
                    alarm.optBoolean("snoozeAggressive", false), Math.max(1, alarm.optInt("snoozeMinimumMinutes", 1)));
                if (scheduled) codes.add(String.valueOf(code));
            }
        } catch (Exception ignored) {
            // Malformed local data must not crash the app.
        }
        prefs.edit().putStringSet(KEY_CODES, codes).apply();
    }

    static boolean scheduleOne(Context context, int requestCode, String alarmId, String time, String date,
                               String label, String daysJson, String challenge, String sound,
                               boolean gentleWake, int gentleMinutes, String qrToken, String nfcToken, int shakeCount,
                               int holdSeconds, int snakeSeconds, boolean snoozeEnabled, int snoozeMinutes,
                               boolean snoozeAggressive, int snoozeMinimumMinutes) {
        long triggerAt = nextTrigger(time, date, daysJson);
        if (triggerAt <= 0) return false;
        Intent intent = baseIntent(new Intent(context, AlarmReceiver.class), requestCode, alarmId, time,
            date, label, daysJson, challenge, sound, gentleWake, gentleMinutes, qrToken, nfcToken, shakeCount,
            holdSeconds, snakeSeconds, snoozeEnabled, snoozeMinutes, snoozeAggressive, snoozeMinimumMinutes)
            .setAction("de.danberg.wachwerk.ALARM." + requestCode).putExtra("scheduledAt",triggerAt);
        PendingIntent pending = PendingIntent.getBroadcast(context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        try {
            Intent show = new Intent(context, MainActivity.class);
            PendingIntent showPending = PendingIntent.getActivity(context, requestCode + 50000, show,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            manager.setAlarmClock(new AlarmManager.AlarmClockInfo(triggerAt, showPending), pending);
        } catch (SecurityException denied) {
            manager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending);
        }
        if (gentleWake) scheduleGentleWake(context, requestCode, intent, triggerAt, gentleMinutes);
        return true;
    }

    private static Intent baseIntent(Intent intent, int requestCode, String alarmId, String time, String date,
                                     String label, String daysJson, String challenge, String sound,
                                     boolean gentleWake, int gentleMinutes, String qrToken, String nfcToken, int shakeCount,
                                     int holdSeconds, int snakeSeconds, boolean snoozeEnabled, int snoozeMinutes,
                                     boolean snoozeAggressive, int snoozeMinimumMinutes) {
        return intent.putExtra("requestCode", requestCode).putExtra("alarmId", alarmId)
            .putExtra("time", time).putExtra("date", date).putExtra("label", label)
            .putExtra("daysJson", daysJson).putExtra("challenge", challenge).putExtra("sound", sound)
            .putExtra("gentleWake", gentleWake).putExtra("gentleMinutes", gentleMinutes)
            .putExtra("qrToken", qrToken).putExtra("nfcToken", nfcToken)
            .putExtra("shakeCount", Math.max(3, shakeCount)).putExtra("holdSeconds", Math.max(3, holdSeconds))
            .putExtra("snakeSeconds", Math.max(3, snakeSeconds)).putExtra("snoozeEnabled", snoozeEnabled)
            .putExtra("snoozeMinutes", Math.max(1, snoozeMinutes)).putExtra("snoozeAggressive", snoozeAggressive)
            .putExtra("snoozeMinimumMinutes", Math.max(1, Math.min(snoozeMinutes, snoozeMinimumMinutes)))
            .putExtra("snoozes", 0);
    }

    private static void scheduleGentleWake(Context context, int requestCode, Intent source, long alarmAt, int minutes) {
        long at = alarmAt - Math.max(5, minutes) * 60_000L;
        if (at <= System.currentTimeMillis() + 1500L) return;
        Intent intent = new Intent(context, GentleWakeReceiver.class)
            .setAction("de.danberg.wachwerk.GENTLE." + requestCode)
            .putExtras(source).putExtra("gentleMinutes", Math.max(5, minutes));
        PendingIntent pending = PendingIntent.getBroadcast(context, requestCode + 40000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        setExact(context, pending, at);
    }

    static void rescheduleRecurring(Context context, Intent fired) {
        String daysJson = value(fired, "daysJson", "[]");
        try { if (new JSONArray(daysJson).length() == 0) return; }
        catch (Exception ignored) { return; }
        scheduleOne(context, fired.getIntExtra("requestCode", 1), value(fired, "alarmId", "alarm"),
            value(fired, "time", "07:00"), value(fired, "date", ""), value(fired, "label", "Wecker"),
            daysJson, value(fired, "challenge", "shake"), value(fired, "sound", "Systemstandard"),
            fired.getBooleanExtra("gentleWake", false), fired.getIntExtra("gentleMinutes", 15),
            value(fired, "qrToken", "wachwerk"), value(fired, "nfcToken", ""),
            fired.getIntExtra("shakeCount", 12), fired.getIntExtra("holdSeconds", 8),
            fired.getIntExtra("snakeSeconds", 10), fired.getBooleanExtra("snoozeEnabled", true),
            fired.getIntExtra("snoozeMinutes", 10), fired.getBooleanExtra("snoozeAggressive", false),
            fired.getIntExtra("snoozeMinimumMinutes", 1));
    }

    static int nextSnoozeMinutes(Intent source) {
        int start = Math.max(1, source.getIntExtra("snoozeMinutes", 10));
        if (!source.getBooleanExtra("snoozeAggressive", false)) return start;
        int minimum = Math.max(1, Math.min(start, source.getIntExtra("snoozeMinimumMinutes", 1)));
        int snoozes = Math.max(0, source.getIntExtra("snoozes", 0));
        return Math.max(minimum, (int) Math.floor(start / Math.pow(2d, snoozes)));
    }

    static void scheduleSnooze(Context context, Intent source) {
        int minutes = nextSnoozeMinutes(source);
        int originalCode = source.getIntExtra("requestCode", 1);
        int requestCode = originalCode + 30000;
        long triggerAt = System.currentTimeMillis() + minutes * 60_000L;
        Intent intent = new Intent(context, AlarmReceiver.class)
            .setAction("de.danberg.wachwerk.SNOOZE." + requestCode)
            .putExtras(source)
            .putExtra("requestCode", requestCode)
            .putExtra("label", value(source, "label", "Wecker"))
            .putExtra("gentleWake", false)
            .putExtra("snoozes", source.getIntExtra("snoozes", 0) + 1).putExtra("scheduledAt",triggerAt);
        intent.removeExtra(AlarmSessionStore.SESSION);
        PendingIntent pending = PendingIntent.getBroadcast(context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        setExact(context, pending, triggerAt);
    }

    private static void setExact(Context context, PendingIntent pending, long at) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        try { manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending); }
        catch (SecurityException denied) { manager.set(AlarmManager.RTC_WAKEUP, at, pending); }
    }

    private static long nextTrigger(String time, String date, String daysJson) {
        String[] parts = time.split(":");
        int hour = parts.length > 0 ? safeInt(parts[0], 7) : 7;
        int minute = parts.length > 1 ? safeInt(parts[1], 0) : 0;
        Calendar now = Calendar.getInstance();
        try {
            JSONArray days = new JSONArray(daysJson);
            if (days.length() == 0) {
                String[] dateParts = date.split("-");
                if (dateParts.length != 3) return -1;
                Calendar candidate = Calendar.getInstance();
                candidate.set(safeInt(dateParts[0], now.get(Calendar.YEAR)), safeInt(dateParts[1], 1) - 1,
                    safeInt(dateParts[2], now.get(Calendar.DAY_OF_MONTH)), hour, minute, 0);
                candidate.set(Calendar.MILLISECOND, 0);
                return candidate.getTimeInMillis() > now.getTimeInMillis() + 1500L ? candidate.getTimeInMillis() : -1;
            }
            for (int offset = 0; offset < 8; offset++) {
                Calendar candidate = (Calendar) now.clone();
                candidate.add(Calendar.DAY_OF_YEAR, offset);
                candidate.set(Calendar.HOUR_OF_DAY, hour); candidate.set(Calendar.MINUTE, minute);
                candidate.set(Calendar.SECOND, 0); candidate.set(Calendar.MILLISECOND, 0);
                if (candidate.getTimeInMillis() <= now.getTimeInMillis() + 1500L) continue;
                int jsDay = candidate.get(Calendar.DAY_OF_WEEK) - 1;
                for (int i = 0; i < days.length(); i++) if (days.optInt(i, -1) == jsDay) return candidate.getTimeInMillis();
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private static void cancelCodes(Context context, Set<String> codes) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        for (String raw : codes) {
            int code = safeInt(raw, 0);
            PendingIntent alarm = PendingIntent.getBroadcast(context, code,
                new Intent(context, AlarmReceiver.class).setAction("de.danberg.wachwerk.ALARM." + code),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (alarm != null) manager.cancel(alarm);
            PendingIntent gentle = PendingIntent.getBroadcast(context, code + 40000,
                new Intent(context, GentleWakeReceiver.class).setAction("de.danberg.wachwerk.GENTLE." + code),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (gentle != null) manager.cancel(gentle);
        }
    }

    private static int requestCodeFor(String id) { return 1000 + Math.abs(id.toLowerCase(Locale.ROOT).hashCode() % 20000); }
    private static int safeInt(String value, int fallback) { try { return Integer.parseInt(value); } catch (Exception ignored) { return fallback; } }
    static String value(Intent intent, String key, String fallback) { String result = intent.getStringExtra(key); return result == null ? fallback : result; }
}
