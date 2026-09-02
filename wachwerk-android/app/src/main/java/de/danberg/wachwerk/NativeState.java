package de.danberg.wachwerk;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

public final class NativeState {
    private static final String PREFS = "wachwerk_native";
    private static final String KEY_SETTINGS = "settings_json";
    private static final String KEY_PENDING = "pending_wake";
    private static final String KEY_COMPLETED = "completed_one_time";

    private NativeState() {}

    public static void saveSettings(Context context, String json) {
        if (json == null || json.isBlank()) json = "{}";
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SETTINGS, json).apply();
    }

    public static JSONObject settings(Context context) {
        try {
            return new JSONObject(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SETTINGS, "{}"));
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    public static void recordWake(Context context, Intent source) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            String alarmId = AlarmScheduler.value(source, "alarmId", "alarm");
            long firedAt = source.getLongExtra("firstFiredAt", System.currentTimeMillis());
            String eventId = AlarmScheduler.value(source,AlarmSessionStore.SESSION,alarmId + "-" + firedAt);
            if(eventId.equals(prefs.getString("lastRecordedWake","")))return;
            JSONObject wake = new JSONObject()
                .put("eventId", eventId)
                .put("alarmId", alarmId)
                .put("label", AlarmScheduler.value(source, "label", "Wecker"))
                .put("plannedTime", AlarmScheduler.value(source, "time", "07:00"))
                .put("firedAt", firedAt)
                .put("snoozes", source.getIntExtra("snoozes", 0))
                .put("plannedSleep", plannedSleepForAlarm(context, alarmId));
            prefs.edit().putString(KEY_PENDING, wake.toString()).putString("lastRecordedWake",eventId).commit();
            MorningBlockStore.startForWake(context,eventId);

            String daysJson = AlarmScheduler.value(source, "daysJson", "[]");
            if (new JSONArray(daysJson).length() == 0) appendCompleted(prefs, alarmId);
            int delay = Math.max(0, settings(context).optInt("morningDelay", 60));
            MorningCheckReceiver.schedule(context, delay);
        } catch (Exception ignored) {
            // The alarm is already stopped; a storage failure must not restart it.
        }
    }

    private static void appendCompleted(SharedPreferences prefs, String alarmId) {
        try {
            JSONArray old = new JSONArray(prefs.getString(KEY_COMPLETED, "[]"));
            JSONArray updated = new JSONArray();
            boolean found = false;
            for (int i = 0; i < old.length(); i++) {
                String id = old.optString(i);
                if (alarmId.equals(id)) found = true;
                updated.put(id);
            }
            if (!found) updated.put(alarmId);
            prefs.edit().putString(KEY_COMPLETED, updated.toString()).apply();
        } catch (Exception ignored) {}
    }

    private static String plannedSleepForAlarm(Context context, String alarmId) {
        try {
            JSONArray alarms = new JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("alarms_json", "[]"));
            for (int i = 0; i < alarms.length(); i++) {
                JSONObject alarm = alarms.optJSONObject(i);
                if (alarm != null && alarmId.equals(alarm.optString("id"))) {
                    String planned = alarm.optString("plannedSleep", "");
                    if (planned.matches("\\d{2}:\\d{2}")) return planned;
                }
            }
        } catch (Exception ignored) {}
        return settings(context).optString("bedtime", "22:00");
    }

    public static String getState(Context context, boolean openMorningCheck) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONObject state = new JSONObject();
            String pending = prefs.getString(KEY_PENDING, "");
            state.put("pendingWake", pending.isBlank() ? JSONObject.NULL : new JSONObject(pending));
            state.put("completedOneTimeIds", new JSONArray(prefs.getString(KEY_COMPLETED, "[]")));
            state.put("openMorningCheck", openMorningCheck);
            state.put("morningBlock",MorningBlockStore.state(context));
            state.put("alarmRinging",AlarmSessionStore.current(context)!=null);
            return state.toString();
        } catch (Exception ignored) {
            return "{\"pendingWake\":null,\"completedOneTimeIds\":[],\"openMorningCheck\":false}";
        }
    }

    public static void completeMorningCheck(Context context, String eventId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONObject pending = new JSONObject(prefs.getString(KEY_PENDING, "{}"));
            if (eventId.equals(pending.optString("eventId"))) prefs.edit().remove(KEY_PENDING).apply();
        } catch (Exception ignored) {}
        context.getSystemService(NotificationManager.class).cancel(MorningCheckReceiver.NOTIFICATION_ID);
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent alarm = PendingIntent.getBroadcast(context, MorningCheckReceiver.REQUEST_CODE,
            new Intent(context, MorningCheckReceiver.class), PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (alarm != null) manager.cancel(alarm);
    }
}
