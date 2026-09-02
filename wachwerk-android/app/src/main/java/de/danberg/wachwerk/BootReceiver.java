package de.danberg.wachwerk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        AlarmScheduler.restoreAll(context);
        TodoReminderScheduler.restore(context);
        FocusTimerScheduler.restore(context);
    }
}
