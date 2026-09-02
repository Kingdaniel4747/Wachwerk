package de.danberg.wachwerk;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.util.Base64;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {
    public static final String LOCAL_APP = "file:///android_asset/site/index.html";
    public static final String ALARM_CHANNEL = "wachwerk_alarm";
    public static final String BEDTIME_CHANNEL = "wachwerk_bedtime";
    public static final String MORNING_CHANNEL = "wachwerk_morning_check";
    public static final String GENTLE_CHANNEL = "wachwerk_gentle_wake";
    public static final String TODO_CHANNEL = "wachwerk_todo";
    public static final String FOCUS_CHANNEL = "wachwerk_focus_timer";
    public static final String FOCUS_PROGRESS_CHANNEL = "wachwerk_focus_progress";
    public static final String LIMIT_CHANNEL = "wachwerk_app_limits";
    private static final int CUSTOM_SOUND_REQUEST = 4302;
    private static final int CAMERA_PERMISSION_REQUEST = 4303;
    private static final int NFC_ENROLL_REQUEST = 4401;
    private static final int NFC_BLOCKER_SCAN_REQUEST = 4402;
    private static final int QR_BLOCKER_SCAN_REQUEST = 4403;

    private WebView webView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean firstMainFrameError;
    private boolean exactPromptShown;
    private boolean fullScreenPromptShown;
    private boolean pageReady;
    private String pendingNfcPurpose = "alarm";
    private String pendingBlockerScope = "instant";
    private static final int QR_VERIFY_REQUEST = 4404;
    private Ringtone previewRingtone;
    private MediaPlayer previewPlayer;
    private volatile String installedAppsCache = "[]";
    private volatile boolean installedAppsLoaded;
    private volatile boolean installedAppsLoading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) { pendingNfcPurpose = savedInstanceState.getString("nfcPurpose","alarm"); pendingBlockerScope = savedInstanceState.getString("blockerScope","instant"); }
        getWindow().setStatusBarColor(UiPalette.from(MainActivity.this).background);
        getWindow().setNavigationBarColor(UiPalette.from(MainActivity.this).background);
        createNotificationChannels(this);
        preloadInstalledApps();
        updateOrientationFlags();
        buildWebView();
        requestNotificationPermission();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("nfcPurpose", pendingNfcPurpose); state.putString("blockerScope", pendingBlockerScope); super.onSaveInstanceState(state);
    }

    private void buildWebView() {
        webView = new WebView(this);
        webView.setBackgroundColor(UiPalette.from(MainActivity.this).background);
        android.webkit.WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setCacheMode(android.webkit.WebSettings.LOAD_NO_CACHE);
        webSettings.setBlockNetworkLoads(true);
        webSettings.setBlockNetworkImage(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(false);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(false);
        webSettings.setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webSettings.setBuiltInZoomControls(false);
        webSettings.setDisplayZoomControls(false);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setUserAgentString(webSettings.getUserAgentString() + " WachwerkAndroid/1.13-Offline");

        webView.addJavascriptInterface(new WachwerkBridge(), "WachwerkAndroid");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url.startsWith("file:///android_asset/")) {
                    firstMainFrameError = false;
                    pageReady = true;
                    dispatchNativeState();
                    dispatchBlockerState();
                    dispatchFocusState();
                    dispatchPermissionState();
                    dispatchInstalledApps();
                    dispatchRequestedScreen();
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame() && !firstMainFrameError) {
                    firstMainFrameError = true;
                    view.loadUrl("file:///android_asset/offline.html");
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !"file".equals(request.getUrl().getScheme());
            }
        });
        setContentView(webView);
        webView.loadUrl(LOCAL_APP);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 4101);
        }
    }

    private void updateOrientationFlags() {
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (landscape) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateOrientationFlags();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
        AlarmScheduler.restoreAll(this);
        TodoReminderScheduler.restore(this);
        FocusTimerScheduler.restore(this);
        dispatchNativeState();
        dispatchBlockerState();
        dispatchFocusState();
        dispatchPermissionState();
        dispatchRequestedScreen();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        dispatchNativeState();
        dispatchBlockerState();
        dispatchFocusState();
        dispatchPermissionState();
        dispatchRequestedScreen();
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    private void dispatchNativeState() {
        if (!pageReady || webView == null) return;
        String state = NativeState.getState(this, getIntent().getBooleanExtra("openMorningCheck", false));
        getIntent().removeExtra("openMorningCheck");
        String script = "window.dispatchEvent(new CustomEvent('wachwerk-native-state',{detail:" + state + "}));";
        handler.post(() -> webView.evaluateJavascript(script, null));
    }

    private void dispatchBlockerState() {
        if (!pageReady || webView == null) return;
        String script = "window.dispatchEvent(new CustomEvent('wachwerk-blocker-state',{detail:" + AppBlockerStore.stateJson(this) + "}));"
            + "window.dispatchEvent(new CustomEvent('wachwerk-accessibility-state',{detail:{enabled:" + isBlockerAccessibilityEnabled()
            + ",usageEnabled:" + DailyUsageStore.hasUsageAccess(this) + "}}));";
        handler.post(() -> webView.evaluateJavascript(script, null));
    }

    private void dispatchFocusState() {
        if (!pageReady || webView == null) return;
        String script = "window.dispatchEvent(new CustomEvent('wachwerk-focus-state',{detail:"
            + FocusTimerScheduler.stateJson(this) + "}));";
        handler.post(() -> webView.evaluateJavascript(script, null));
    }

    private void dispatchPermissionState() {
        if (!pageReady || webView == null) return;
        String script = "window.dispatchEvent(new CustomEvent('wachwerk-permission-state',{detail:"
            + permissionStateJson() + "}));";
        handler.post(() -> webView.evaluateJavascript(script, null));
    }

    private String permissionStateJson() {
        try {
            NotificationManager manager = getSystemService(NotificationManager.class);
            boolean notifications = Build.VERSION.SDK_INT < 24 || manager.areNotificationsEnabled();
            boolean exact = AlarmScheduler.canScheduleExact(this);
            boolean fullScreen = Build.VERSION.SDK_INT < 34 || manager.canUseFullScreenIntent();
            boolean camera = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
            boolean dnd = manager.isNotificationPolicyAccessGranted();
            return new JSONObject().put("notifications", notifications).put("exact", exact)
                .put("fullScreen", fullScreen).put("camera", camera).put("dnd", dnd)
                .put("liveSupported", FocusTimerScheduler.supportsLiveUpdates()).put("liveEnabled", FocusTimerScheduler.canPostLiveUpdates(this)).toString();
        } catch (Exception ignored) {
            return "{\"notifications\":false,\"exact\":false,\"fullScreen\":false,\"camera\":false,\"dnd\":false}";
        }
    }

    private void dispatchRequestedScreen() {
        if (!pageReady || webView == null) return;
        String requested = null;
        if (getIntent().getBooleanExtra("openBlocker", false)) requested = "blocker";
        else if (getIntent().getBooleanExtra("openTodos", false)) requested = "todos";
        else if (getIntent().getBooleanExtra("openAlarms", false)) requested = "alarms";
        if (requested == null) return;
        getIntent().removeExtra("openBlocker"); getIntent().removeExtra("openTodos"); getIntent().removeExtra("openAlarms");
        String screen = requested;
        handler.post(() -> webView.evaluateJavascript(
            "window.dispatchEvent(new CustomEvent('wachwerk-open-screen',{detail:{screen:'" + screen + "'}}));", null));
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel alarms = new NotificationChannel(ALARM_CHANNEL, "Wecker", NotificationManager.IMPORTANCE_HIGH);
        alarms.setDescription("Vollbild-Wecker von Wachwerk");
        alarms.enableVibration(true);
        alarms.setBypassDnd(true);
        alarms.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(alarms);

        NotificationChannel bedtime = new NotificationChannel(BEDTIME_CHANNEL, "Einschlaf-Coach", NotificationManager.IMPORTANCE_HIGH);
        bedtime.setDescription("Hartnäckige Erinnerungen zum Schlafengehen");
        bedtime.enableVibration(true);
        manager.createNotificationChannel(bedtime);

        NotificationChannel morning = new NotificationChannel(MORNING_CHANNEL, "Morgencheck", NotificationManager.IMPORTANCE_DEFAULT);
        morning.setDescription("Frage nach dem letzten Aufstehen");
        manager.createNotificationChannel(morning);

        NotificationChannel gentle = new NotificationChannel(GENTLE_CHANNEL, "Sanftes Licht", NotificationManager.IMPORTANCE_HIGH);
        gentle.setDescription("Weckt den Bildschirm vor dem eigentlichen Alarm und hellt ihn langsam auf");
        gentle.enableVibration(false);
        gentle.setSound(null, null);
        gentle.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(gentle);

        NotificationChannel todos = new NotificationChannel(TODO_CHANNEL, "To-do-Erinnerungen", NotificationManager.IMPORTANCE_HIGH);
        todos.setDescription("Erinnerungen an selbst festgelegte Aufgaben");
        todos.enableVibration(true);
        manager.createNotificationChannel(todos);

        NotificationChannel focus = new NotificationChannel(FOCUS_CHANNEL, "Fokus-Timer", NotificationManager.IMPORTANCE_HIGH);
        focus.setDescription("Klingelt am Ende einer Fokus- oder Pausenphase");
        focus.enableVibration(true);
        focus.setBypassDnd(true);
        focus.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(focus);

        NotificationChannel focusProgress = new NotificationChannel(FOCUS_PROGRESS_CHANNEL, "Laufende Fokus-Session", NotificationManager.IMPORTANCE_LOW);
        focusProgress.setDescription("Zeigt die verbleibende Fokus- oder Pausenzeit dauerhaft an");
        focusProgress.setSound(null, null);
        focusProgress.enableVibration(false);
        manager.createNotificationChannel(focusProgress);

        NotificationChannel limits = new NotificationChannel(LIMIT_CHANNEL, "App-Zeit verbleibend", NotificationManager.IMPORTANCE_DEFAULT);
        limits.setDescription("Frei einstellbare Hinweise zur verbleibenden App-Zeit");
        manager.createNotificationChannel(limits);
    }

    public final class WachwerkBridge {
        @JavascriptInterface
        public void syncAlarms(String json) {
            AlarmScheduler.syncAlarms(getApplicationContext(), json);
            if (json != null && json.contains("\"enabled\":true") && !AlarmScheduler.canScheduleExact(MainActivity.this)) {
                handler.post(MainActivity.this::promptExactAlarmPermission);
            }
            if (json != null && json.contains("\"enabled\":true")) handler.post(MainActivity.this::promptFullScreenPermission);
        }

        @JavascriptInterface
        public void syncBedtime(boolean enabled, String time, int intervalMinutes, int minimumIntervalMinutes, int sleepDetectMinutes, String mode, String message) {
            BedtimeReceiver.syncPlan(getApplicationContext(), time, intervalMinutes, minimumIntervalMinutes, sleepDetectMinutes, mode, message, enabled);
            if (enabled && !AlarmScheduler.canScheduleExact(MainActivity.this)) handler.post(MainActivity.this::promptExactAlarmPermission);
        }

        @JavascriptInterface
        public void syncTodos(String json) {
            TodoReminderScheduler.sync(getApplicationContext(), json);
            if (json != null && json.matches("(?s).*\\\"reminderAt\\\":\\\"[^\\\"]+.*") && !AlarmScheduler.canScheduleExact(MainActivity.this)) {
                handler.post(MainActivity.this::promptExactAlarmPermission);
            }
        }

        @JavascriptInterface
        public void syncSettings(String json) {
            try { JSONObject data = new JSONObject(json); if (WakeKeyStore.token(getApplicationContext()).isEmpty()) WakeKeyStore.setToken(getApplicationContext(), data.optString("alarmNfcToken")); } catch (Exception ignored) {}
            NativeState.saveSettings(getApplicationContext(), json);
            handler.post(() -> { int color = UiPalette.from(MainActivity.this).background; getWindow().setStatusBarColor(color); getWindow().setNavigationBarColor(color); webView.setBackgroundColor(color); });
        }

        @JavascriptInterface
        public String getNativeState() {
            return NativeState.getState(getApplicationContext(), getIntent().getBooleanExtra("openMorningCheck", false));
        }

        @JavascriptInterface
        public void openRingingAlarm() {
            handler.post(() -> { if(AlarmSessionStore.current(MainActivity.this)!=null)
                startActivity(AlarmRingingService.screenIntent(MainActivity.this)); });
        }

        @JavascriptInterface
        public String getAlarmPermissionState() { return MainActivity.this.permissionStateJson(); }

        @JavascriptInterface
        public void completeMorningCheck(String eventId, String state) {
            NativeState.completeMorningCheck(getApplicationContext(), eventId);
        }

        @JavascriptInterface
        public String getQrMatrix(String content) {
            try {
                BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 29, 29);
                StringBuilder bits = new StringBuilder(matrix.getWidth() * matrix.getHeight());
                for (int y = 0; y < matrix.getHeight(); y++) for (int x = 0; x < matrix.getWidth(); x++) bits.append(matrix.get(x, y) ? '1' : '0');
                return new JSONObject().put("size", matrix.getWidth()).put("bits", bits.toString()).toString();
            } catch (Exception error) {
                return "{}";
            }
        }

        @JavascriptInterface
        public void enterStandby() {
            handler.post(() -> setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
        }

        @JavascriptInterface
        public void exitStandby() {
            handler.post(() -> setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED));
        }

        @JavascriptInterface
        public void printCurrentPage() {
            handler.post(() -> {
                PrintManager manager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                manager.print("Wachwerk QR-Code", webView.createPrintDocumentAdapter("Wachwerk QR-Code"),
                    new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build());
            });
        }

        @JavascriptInterface
        public void chooseAlarmSound() {
            handler.post(() -> {
                try {
                    Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("audio/*");
                    startActivityForResult(picker, CUSTOM_SOUND_REQUEST);
                } catch (Exception error) {
                    Toast.makeText(MainActivity.this, "Auf diesem Gerät wurde keine Audio-Auswahl gefunden.", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void previewAlarmSound(String sound) {
            handler.post(() -> MainActivity.this.previewAlarmSound(sound));
        }

        @JavascriptInterface
        public void stopAlarmSoundPreview() {
            handler.post(MainActivity.this::stopSoundPreview);
        }

        @JavascriptInterface
        public String getWakeKeyState() { return WakeKeyStore.state(getApplicationContext()); }

        @JavascriptInterface
        public void verifyAlarmQr() {
            handler.post(() -> startActivityForResult(new Intent(MainActivity.this, QrScannerActivity.class)
                .putExtra("expectedToken", "wachwerk-personal-code"), QR_VERIFY_REQUEST));
        }

        @JavascriptInterface
        public boolean hasNfc() { return NfcAdapter.getDefaultAdapter(MainActivity.this) != null; }

        @JavascriptInterface
        public void enrollNfcTag(String purpose) {
            handler.post(() -> {
                NfcAdapter adapter = NfcAdapter.getDefaultAdapter(MainActivity.this);
                if (adapter == null) { Toast.makeText(MainActivity.this, "Dieses Handy unterstützt kein NFC.", Toast.LENGTH_LONG).show(); return; }
                pendingNfcPurpose = "blocker".equals(purpose) ? "blocker" : "alarm";
                startActivityForResult(new Intent(MainActivity.this, NfcTagActivity.class).putExtra("mode", "enroll"), NFC_ENROLL_REQUEST);
            });
        }

        @JavascriptInterface
        public void scanBlockerTag(String token, String scope) {
            if (!"nfc".equals(AppBlockerStore.method(MainActivity.this, scope))) return;
            pendingBlockerScope = AppBlockerStore.normalizeScope(scope);
            handler.post(() -> {
                NfcAdapter adapter = NfcAdapter.getDefaultAdapter(MainActivity.this);
                if (adapter == null) { Toast.makeText(MainActivity.this, "Dieses Handy unterstützt kein NFC.", Toast.LENGTH_LONG).show(); return; }
                String expectedToken = AppBlockerStore.token(MainActivity.this);
                if (expectedToken.isEmpty()) expectedToken = WakeKeyStore.token(MainActivity.this);
                boolean firstUse = expectedToken.isEmpty();
                startActivityForResult(new Intent(MainActivity.this, NfcTagActivity.class)
                    .putExtra("mode", firstUse ? "enroll" : "scan").putExtra("expectedToken", expectedToken), NFC_BLOCKER_SCAN_REQUEST);
            });
        }

        @JavascriptInterface
        public void scanBlockerQr(String token, String scope) {
            if (!"qr".equals(AppBlockerStore.method(MainActivity.this, scope))) return;
            pendingBlockerScope = AppBlockerStore.normalizeScope(scope);
            handler.post(() -> startActivityForResult(new Intent(MainActivity.this, QrScannerActivity.class)
                .putExtra("expectedToken", AppBlockerStore.qrToken(MainActivity.this)), QR_BLOCKER_SCAN_REQUEST));
        }

        @JavascriptInterface
        public void requestCameraPermission() {
            handler.post(MainActivity.this::requestCameraPermissionFromSettings);
        }

        @JavascriptInterface
        public void openExactAlarmSettings() {
            handler.post(() -> startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:" + getPackageName()))));
        }

        @JavascriptInterface
        public void openFullScreenSettings() {
            handler.post(() -> startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.parse("package:" + getPackageName()))));
        }

        @JavascriptInterface
        public void openLiveNotificationSettings() {
            handler.post(() -> {
                Intent intent = new Intent("android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS").putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                try {
                    try { intent.setAction((String) Settings.class.getField("ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS").get(null)); } catch (ReflectiveOperationException ignored) {}
                    startActivity(intent);
                } catch (Exception unsupported) {
                    startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
                }
            });
        }

        @JavascriptInterface
        public void openNotificationSettings() {
            handler.post(() -> startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())));
        }

        @JavascriptInterface
        public void openNotificationPolicySettings() {
            handler.post(() -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)));
        }

        @JavascriptInterface
        public String getInstalledApps() { if (!installedAppsLoaded) preloadInstalledApps(); return installedAppsCache; }

        @JavascriptInterface
        public String getBlockerState() { return AppBlockerStore.stateJson(getApplicationContext()); }

        @JavascriptInterface
        public String getAppUsage() { return DailyUsageStore.stateJson(getApplicationContext()); }

        @JavascriptInterface
        public String getFocusTimerState() { return FocusTimerScheduler.stateJson(getApplicationContext()); }

        @JavascriptInterface
        public void startFocusTimer(int workMinutes, int breakMinutes, int rounds, String packagesJson, boolean silenceNotifications) {
            FocusTimerScheduler.start(getApplicationContext(), workMinutes, breakMinutes, rounds, packagesJson, silenceNotifications);
            dispatchFocusState();
            if (!AlarmScheduler.canScheduleExact(MainActivity.this)) handler.post(MainActivity.this::promptExactAlarmPermission);
            handler.post(MainActivity.this::promptFullScreenPermission);
        }

        @JavascriptInterface
        public void cancelFocusTimer() {
            FocusTimerScheduler.cancel(getApplicationContext());
            dispatchFocusState();
        }

        @JavascriptInterface
        public void syncAppBlocker(String json) { AppBlockerStore.sync(getApplicationContext(), json); }

        @JavascriptInterface
        public boolean setBlockerPassword(String password, String scope) {
            boolean saved = AppBlockerStore.setPassword(getApplicationContext(), password, scope);
            dispatchBlockerState();
            return saved;
        }

        @JavascriptInterface
        public boolean toggleBlockerPassword(String password, String scope) {
            if (!"password".equals(AppBlockerStore.method(MainActivity.this, scope)) || !AppBlockerStore.verifyPassword(getApplicationContext(), password, scope)) return false;
            AppBlockerStore.toggleScope(getApplicationContext(), scope);
            dispatchBlockerState();
            return true;
        }

        @JavascriptInterface
        public boolean isAccessibilityEnabled() { return MainActivity.this.isBlockerAccessibilityEnabled(); }

        @JavascriptInterface
        public boolean hasUsageAccess() { return DailyUsageStore.hasUsageAccess(MainActivity.this); }

        @JavascriptInterface
        public void openAccessibilitySettings() {
            handler.post(() -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        }

        @JavascriptInterface
        public void openUsageAccessSettings() {
            handler.post(() -> {
                try {
                    startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
                } catch (Exception ignored) {
                    startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                }
            });
        }

        @JavascriptInterface
        public void toast(String message) {
            handler.post(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
        }
    }

    private void previewAlarmSound(String sound) {
        stopSoundPreview();
        try {
            if (sound != null && sound.startsWith("custom:")) {
                String fileName = sound.substring(7);
                if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) throw new IllegalArgumentException("Ungültiger Dateiname");
                File file = new File(new File(getFilesDir(), "alarm_sounds"), fileName);
                previewPlayer = new MediaPlayer();
                previewPlayer.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
                previewPlayer.setDataSource(file.getAbsolutePath());
                previewPlayer.setOnCompletionListener(player -> stopSoundPreview());
                previewPlayer.prepare(); previewPlayer.start();
            } else {
                int type = "Sanft".equals(sound) ? RingtoneManager.TYPE_NOTIFICATION : "Klar".equals(sound) ? RingtoneManager.TYPE_RINGTONE : RingtoneManager.TYPE_ALARM;
                previewRingtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(type));
                previewRingtone.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                previewRingtone.play();
            }
            dispatchSoundPreviewState(true);
            handler.postDelayed(this::stopSoundPreview, 8_000L);
        } catch (Exception error) {
            stopSoundPreview();
            Toast.makeText(this, "Alarmton konnte nicht abgespielt werden.", Toast.LENGTH_LONG).show();
        }
    }

    private void stopSoundPreview() {
        try { if (previewRingtone != null && previewRingtone.isPlaying()) previewRingtone.stop(); } catch (Exception ignored) {}
        previewRingtone = null;
        try { if (previewPlayer != null) { if (previewPlayer.isPlaying()) previewPlayer.stop(); previewPlayer.release(); } } catch (Exception ignored) {}
        previewPlayer = null;
        dispatchSoundPreviewState(false);
    }

    private void dispatchSoundPreviewState(boolean playing) {
        if (!pageReady || webView == null) return;
        String script = "window.dispatchEvent(new CustomEvent('wachwerk-sound-preview',{detail:{playing:" + playing + "}}));";
        handler.post(() -> webView.evaluateJavascript(script, null));
    }

    private void requestCameraPermissionFromSettings() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Kamera-Berechtigung ist aktiv.", Toast.LENGTH_SHORT).show();
            return;
        }
        requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
    }

    private String installedAppsJson() {
        JSONArray result = new JSONArray();
        try {
            Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> resolved = new ArrayList<>(getPackageManager().queryIntentActivities(launcher, PackageManager.MATCH_ALL));
            resolved.sort(Comparator.comparing(item -> String.valueOf(item.loadLabel(getPackageManager())), String.CASE_INSENSITIVE_ORDER));
            for (ResolveInfo item : resolved) {
                String packageName = item.activityInfo.packageName;
                if (packageName.equals(getPackageName())) continue;
                JSONObject app = new JSONObject().put("packageName", packageName)
                    .put("label", String.valueOf(item.loadLabel(getPackageManager())))
                    .put("icon", iconData(item.loadIcon(getPackageManager())));
                boolean exists = false;
                for (int i = 0; i < result.length(); i++) if (packageName.equals(result.optJSONObject(i).optString("packageName"))) { exists = true; break; }
                if (!exists) result.put(app);
            }
        } catch (Exception ignored) {}
        return result.toString();
    }

    private String iconData(Drawable drawable) {
        if (drawable == null) return "";
        try {
            int size = 96;
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, size, size);
            drawable.draw(canvas);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 92, output);
            bitmap.recycle();
            return "data:image/png;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
        } catch (Exception ignored) { return ""; }
    }

    private void preloadInstalledApps() {
        if (installedAppsLoaded || installedAppsLoading) return;
        installedAppsLoading = true;
        new Thread(() -> {
            installedAppsCache = installedAppsJson();
            installedAppsLoaded = true;
            installedAppsLoading = false;
            handler.post(this::dispatchInstalledApps);
        }, "Wachwerk-App-Liste").start();
    }

    private void dispatchInstalledApps() {
        if (!pageReady || webView == null || !installedAppsLoaded) return;
        String script = "window.dispatchEvent(new CustomEvent('wachwerk-installed-apps',{detail:" + installedAppsCache + "}));";
        handler.post(() -> webView.evaluateJavascript(script, null));
    }

    private boolean isBlockerAccessibilityEnabled() {
        String expected = new ComponentName(this, AppBlockAccessibilityService.class).flattenToString();
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        while (splitter.hasNext()) if (expected.equalsIgnoreCase(splitter.next())) return true;
        return false;
    }

    private void promptExactAlarmPermission() {
        if (exactPromptShown || isFinishing() || Build.VERSION.SDK_INT < 31) return;
        exactPromptShown = true;
        new AlertDialog.Builder(this)
            .setTitle("Wecker zuverlässig erlauben")
            .setMessage("Android braucht einmal die Erlaubnis „Alarme & Erinnerungen“, damit Wachwerk sekundengenau klingeln darf – auch bei gesperrtem Bildschirm.")
            .setPositiveButton("Erlauben", (dialog, which) -> startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()))))
            .setNegativeButton("Später", null)
            .show();
    }

    private void promptFullScreenPermission() {
        if (fullScreenPromptShown || isFinishing() || Build.VERSION.SDK_INT < 34) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager.canUseFullScreenIntent()) return;
        fullScreenPromptShown = true;
        new AlertDialog.Builder(this)
            .setTitle("Wecker auf dem Sperrbildschirm")
            .setMessage("Erlaube Wachwerk einmal Vollbild-Benachrichtigungen. Nur so können Wecker und sanftes Licht den ausgeschalteten Bildschirm zuverlässig einschalten.")
            .setPositiveButton("Erlauben", (dialog, which) -> startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + getPackageName()))))
            .setNegativeButton("Später", null)
            .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == QR_VERIFY_REQUEST) {
            if (resultCode == RESULT_OK) {
                WakeKeyStore.verifyQr(this);
                if (webView != null) webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('wachwerk-qr-verified'));", null);
                Toast.makeText(this, "QR-Code geprüft · für alle QR-Wecker bereit", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (requestCode == NFC_ENROLL_REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                try {
                    String token = data.getStringExtra("token");
                    WakeKeyStore.setToken(this, token);
                    if ("blocker".equals(pendingNfcPurpose)) AppBlockerStore.setToken(this, token);
                    JSONObject detail = new JSONObject().put("purpose", pendingNfcPurpose)
                        .put("token", token).put("label", data.getStringExtra("label"));
                    String script = "window.dispatchEvent(new CustomEvent('wachwerk-nfc-enrolled',{detail:" + detail + "}));";
                    handler.postDelayed(() -> {
                        if (webView != null) webView.evaluateJavascript(script, null);
                        if ("blocker".equals(pendingNfcPurpose)) dispatchBlockerState();
                    }, 350L);
                } catch (Exception ignored) {}
            }
            return;
        }
        if (requestCode == NFC_BLOCKER_SCAN_REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                String token = data.getStringExtra("token");
                if (AppBlockerStore.token(this).isEmpty() && token != null && !token.isEmpty()) AppBlockerStore.setToken(this, token);
                if (WakeKeyStore.token(this).isEmpty()) WakeKeyStore.setToken(this, token);
                boolean enabled = AppBlockerStore.toggleScope(this, pendingBlockerScope);
                Toast.makeText(this, enabled ? "Sperre aktiviert" : "Sperre aufgehoben", Toast.LENGTH_SHORT).show();
                dispatchBlockerState();
            }
            return;
        }
        if (requestCode == QR_BLOCKER_SCAN_REQUEST) {
            if (resultCode == RESULT_OK) {
                boolean enabled = AppBlockerStore.toggleScope(this, pendingBlockerScope);
                Toast.makeText(this, enabled ? "Sperre aktiviert" : "Sperre aufgehoben", Toast.LENGTH_SHORT).show();
                dispatchBlockerState();
            }
            return;
        }
        if (requestCode != CUSTOM_SOUND_REQUEST || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            String displayName = queryDisplayName(uri);
            String safeName = displayName.replaceAll("[^A-Za-z0-9ÄÖÜäöüß._ -]", "_");
            if (safeName.trim().isEmpty()) safeName = "Eigener-Alarmton";
            String storedName = System.currentTimeMillis() + "-" + safeName;
            File directory = new File(getFilesDir(), "alarm_sounds");
            if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("Audio-Ordner konnte nicht erstellt werden");
            File destination = new File(directory, storedName);
            long total = 0;
            try (InputStream input = getContentResolver().openInputStream(uri); FileOutputStream output = new FileOutputStream(destination)) {
                if (input == null) throw new IllegalStateException("Audiodatei konnte nicht gelesen werden");
                byte[] buffer = new byte[16_384];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    total += count;
                    if (total > 30L * 1024L * 1024L) throw new IllegalArgumentException("Audiodatei ist größer als 30 MB");
                    output.write(buffer, 0, count);
                }
            } catch (Exception error) {
                destination.delete();
                throw error;
            }
            JSONObject detail = new JSONObject().put("id", "custom:" + storedName).put("name", displayName);
            String script = "window.dispatchEvent(new CustomEvent('wachwerk-custom-sound',{detail:" + detail + "}));";
            if (webView != null) webView.evaluateJavascript(script, null);
        } catch (Exception error) {
            Toast.makeText(this, "Alarmton konnte nicht gespeichert werden: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != CAMERA_PERMISSION_REQUEST) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Kamera-Berechtigung ist aktiv.", Toast.LENGTH_SHORT).show();
        } else {
            new AlertDialog.Builder(this)
                .setTitle("Kamera für QR-Code erlauben")
                .setMessage("Die Berechtigung wurde abgelehnt. Öffne die Android-App-Einstellungen und aktiviere dort Kamera für Wachwerk.")
                .setPositiveButton("App-Einstellungen", (dialog, which) -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))))
                .setNegativeButton("Abbrechen", null).show();
        }
        dispatchPermissionState();
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0 && cursor.getString(index) != null) return cursor.getString(index);
            }
        }
        return "Eigener-Alarmton";
    }

    @Override protected void onDestroy() {
        stopSoundPreview();
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
