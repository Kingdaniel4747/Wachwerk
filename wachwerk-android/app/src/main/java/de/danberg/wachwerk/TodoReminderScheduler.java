package de.danberg.wachwerk;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

public final class TodoReminderScheduler {
    private static final String PREFS = "wachwerk_native";
    private static final String KEY_TODOS = "todos_json";
    private static final String KEY_CODES = "todo_codes";

    private TodoReminderScheduler() {}

    public static void sync(Context context, String json) {
        if (json == null || json.isBlank()) json = "[]";
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (json.equals(preferences.getString(KEY_TODOS, ""))) return;
        cancel(context, preferences.getStringSet(KEY_CODES, new HashSet<>()));
        preferences.edit().putString(KEY_TODOS, json).apply();
        scheduleAll(context, json);
    }

    public static void restore(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        scheduleAll(context, preferences.getString(KEY_TODOS, "[]"));
    }

    private static void scheduleAll(Context context, String json) {
        Set<String> codes = new HashSet<>();
        try {
            JSONArray todos = new JSONArray(json);
            for (int index = 0; index < todos.length(); index++) {
                JSONObject todo = todos.optJSONObject(index);
                if (todo == null || todo.optBoolean("done", false)) continue;
                String reminderAt = todo.optString("reminderAt", "");
                if (reminderAt.isBlank()) continue;
                long at = LocalDateTime.parse(reminderAt).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                if (at <= System.currentTimeMillis() + 1_500L) continue;
                String id = todo.optString("id", String.valueOf(index));
                int code = 52000 + Math.abs(id.hashCode() % 10000);
                Intent intent = new Intent(context, TodoReminderReceiver.class)
                    .setAction("de.danberg.wachwerk.TODO." + code)
                    .putExtra("todoId", id)
                    .putExtra("text", todo.optString("text", "Offene Aufgabe"));
                PendingIntent pending = PendingIntent.getBroadcast(context, code, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                try { manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending); }
                catch (SecurityException denied) { manager.set(AlarmManager.RTC_WAKEUP, at, pending); }
                codes.add(String.valueOf(code));
            }
        } catch (Exception ignored) {
            // Invalid local reminder data must not affect the remaining tasks.
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY_CODES, codes).apply();
    }

    private static void cancel(Context context, Set<String> codes) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        for (String raw : codes) {
            try {
                int code = Integer.parseInt(raw);
                PendingIntent pending = PendingIntent.getBroadcast(context, code,
                    new Intent(context, TodoReminderReceiver.class).setAction("de.danberg.wachwerk.TODO." + code),
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (pending != null) manager.cancel(pending);
                context.getSystemService(android.app.NotificationManager.class).cancel(54000 + Math.max(0, code - 52000));
            } catch (Exception ignored) {}
        }
    }
}
