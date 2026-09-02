package de.danberg.wachwerk;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        BedtimeReceiver.stopSession(context);
        if (!AlarmSessionStore.enqueue(context,intent)) return;
        context.getSystemService(NotificationManager.class).cancel(17000 + intent.getIntExtra("requestCode",1));
        try { AlarmRingingService.ensureRunning(context); }
        catch (RuntimeException denied) {
            android.util.Log.e("Wachwerk","Alarm service start denied",denied);
            context.getSystemService(NotificationManager.class).notify(AlarmRingingService.NOTIFICATION,
                AlarmRingingService.notification(context,true));
        }
        PowerManager power=(PowerManager)context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock lock=power.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "Wachwerk:AlarmWake");
        lock.acquire(15000L);
        try { context.startActivity(AlarmRingingService.screenIntent(context)); } catch (RuntimeException ignored) { /* Notification opens the same ringing session. */ }
        if (intent.getAction()==null || !intent.getAction().contains(".SNOOZE.")) AlarmScheduler.rescheduleRecurring(context,intent);
    }
}
