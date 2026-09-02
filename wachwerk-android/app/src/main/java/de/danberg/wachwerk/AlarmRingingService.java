package de.danberg.wachwerk;

import android.app.*;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.content.pm.ServiceInfo;
import java.io.File;

/** Sound belongs to the service, never the activity or its task. */
public class AlarmRingingService extends Service {
    private static final String CHANNEL = "wachwerk_ringing_v2";
    static final int NOTIFICATION = 8910;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private Ringtone fallback;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private String playingSession = "";
    private final Runnable maintain = new Runnable() {
        @Override public void run() {
            if (AlarmSessionStore.current(AlarmRingingService.this) == null) { refresh(); return; }
            if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire(120_000L);
            if (fallback != null && !fallback.isPlaying()) fallback.play();
            handler.postDelayed(this, 1000);
        }
    };
    static Intent screenIntent(Context c) {
        Intent active = AlarmSessionStore.current(c);
        Intent screen = new Intent(c,AlarmActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if(active != null) screen.putExtras(active);
        return screen;
    }
    static void ensureRunning(Context c) { c.startForegroundService(new Intent(c,AlarmRingingService.class)); }
    static synchronized boolean dismiss(Context c, String session, boolean snooze) {
        if (!AlarmSessionStore.matches(c,session)) return false;
        Intent source = AlarmSessionStore.current(c);
        if (snooze) {
            if (!source.getBooleanExtra("snoozeEnabled",true)) return false;
            AlarmScheduler.scheduleSnooze(c, source);
        }
        if (!AlarmSessionStore.remove(c,session)) return false;
        if (!snooze) NativeState.recordWake(c,source);
        c.startService(new Intent(c,AlarmRingingService.class));
        return true;
    }
    static Notification notification(Context c, boolean fallbackSound) {
        NotificationManager manager = c.getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL,"Klingelnder Wecker",NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(null,null); channel.enableVibration(false); channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(channel);
        Intent source = AlarmSessionStore.current(c);
        PendingIntent open = PendingIntent.getActivity(c,NOTIFICATION,screenIntent(c),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        MainActivity.createNotificationChannels(c);
        return new Notification.Builder(c,fallbackSound ? MainActivity.ALARM_CHANNEL : CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(source == null ? "Wecker" : AlarmScheduler.value(source,"label","Wecker"))
            .setContentText("Wecker klingelt · antippen und Aufwachaufgabe abschließen")
            .setCategory(Notification.CATEGORY_ALARM).setPriority(Notification.PRIORITY_MAX)
            .setOngoing(true).setAutoCancel(false).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PUBLIC)
            .setFullScreenIntent(open,true).setContentIntent(open).build();
    }
    @Override public void onCreate() {
        super.onCreate();
        PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
        wakeLock=power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Wachwerk:Ringing");
        wakeLock.setReferenceCounted(false);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        // Even an acknowledgement racing startup must satisfy the foreground deadline.
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION,notification(this,false),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(NOTIFICATION,notification(this,false));
        refresh();
        return AlarmSessionStore.current(this) == null ? START_NOT_STICKY : START_STICKY;
    }
    private void refresh() {
        Intent source=AlarmSessionStore.current(this);
        if(source==null) { stopAudio(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return; }
        String session=source.getStringExtra(AlarmSessionStore.SESSION);
        if (!session.equals(playingSession)) {
            stopAudio(); playingSession=session; play(source);
            getSystemService(NotificationManager.class).notify(NOTIFICATION,notification(this,false));
        }
        handler.removeCallbacks(maintain);handler.post(maintain);
    }
    private void play(Intent source) {
        String sound=AlarmScheduler.value(source,"sound","Systemstandard");
        AudioAttributes audio=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        int type=sound.equals("Sanft")?RingtoneManager.TYPE_NOTIFICATION:sound.equals("Klar")?RingtoneManager.TYPE_RINGTONE:RingtoneManager.TYPE_ALARM;
        Uri standard=RingtoneManager.getDefaultUri(type);
        for (int attempt=0;attempt<2;attempt++) {
            try {
                player=new MediaPlayer();player.setAudioAttributes(audio);
                if(attempt==0 && sound.startsWith("custom:")) {
                    String name=sound.substring(7);
                    if(name.contains("..") || name.contains("/") || name.contains("\\")) throw new IllegalArgumentException();
                    player.setDataSource(new File(new File(getFilesDir(),"alarm_sounds"),name).getAbsolutePath());
                } else player.setDataSource(this,attempt==0?standard:RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
                player.setLooping(true);player.prepare();player.start();break;
            } catch(Exception error) { if(player!=null)player.release();player=null; }
        }
        if(player==null) {
            fallback=RingtoneManager.getRingtone(this,standard);
            if(fallback!=null) { fallback.setAudioAttributes(audio); if(Build.VERSION.SDK_INT>=28)fallback.setLooping(true);fallback.play(); }
        }
        vibrator=(Vibrator)getSystemService(VIBRATOR_SERVICE);
        if(vibrator!=null)vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0,650,250,650,600},0));
    }
    private void stopAudio() {
        handler.removeCallbacks(maintain);
        if(player!=null) { try{player.stop();}catch(Exception ignored){} player.release();player=null; }
        if(fallback!=null) { fallback.stop(); fallback=null; }
        if(vibrator!=null)vibrator.cancel();
        if(wakeLock!=null && wakeLock.isHeld())wakeLock.release();
        playingSession="";
    }
    // Home, Back, rotation and task removal deliberately have no dismissal action.
    @Override public void onTaskRemoved(Intent rootIntent) { super.onTaskRemoved(rootIntent); }
    @Override public void onDestroy() { stopAudio();super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
