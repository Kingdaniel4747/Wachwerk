package de.danberg.wachwerk;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;

public class TodoReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent source) {
        MainActivity.createNotificationChannels(context);
        String todoId = source.getStringExtra("todoId");
        String text = source.getStringExtra("text");
        if (todoId == null) todoId = "todo";
        if (text == null || text.isBlank()) text = "Du hast noch eine offene Aufgabe.";
        Intent open = new Intent(context, MainActivity.class)
            .putExtra("openTodos", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(context, 53000 + Math.abs(todoId.hashCode() % 10000), open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, MainActivity.TODO_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(Color.rgb(155, 245, 177))
            .setContentTitle("Wachwerk · Aufgabe fällig")
            .setContentText(text)
            .setStyle(new Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setPriority(Notification.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(content)
            .build();
        context.getSystemService(NotificationManager.class).notify(54000 + Math.abs(todoId.hashCode() % 10000), notification);
    }
}
