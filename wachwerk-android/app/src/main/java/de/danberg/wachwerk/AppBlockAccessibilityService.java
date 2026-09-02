package de.danberg.wachwerk;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

public class AppBlockAccessibilityService extends AccessibilityService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String currentPackage = "";
    private String lastBlockedPackage = "";
    private long lastUsageTick;
    private long lastBlockLaunch;
    private final Runnable usageTick = new Runnable() {
        @Override public void run() {
            accrueUsage();
            checkAndBlock(currentPackage);
            handler.postDelayed(this, 5_000L);
        }
    };

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        lastUsageTick = SystemClock.elapsedRealtime();
        handler.removeCallbacks(usageTick);
        handler.postDelayed(usageTick, 5_000L);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName() == null) return;
        accrueUsage();
        String packageName = event.getPackageName().toString();
        if (packageName.equals(getPackageName()) || packageName.equals("com.android.systemui")) {
            currentPackage = "";
            return;
        }
        currentPackage = packageName;
        lastUsageTick = SystemClock.elapsedRealtime();
        checkAndBlock(packageName);
    }

    private void accrueUsage() {
        long now = SystemClock.elapsedRealtime();
        if (lastUsageTick <= 0L) { lastUsageTick = now; return; }
        long elapsed = Math.min(10_000L, Math.max(0L, now - lastUsageTick));
        lastUsageTick = now;
        if (currentPackage.isEmpty() || AppBlockerStore.dailyLimitMinutes(this, currentPackage) <= 0) return;
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        android.app.KeyguardManager guard=getSystemService(android.app.KeyguardManager.class);
        if (power != null && power.isInteractive() && (guard==null || !guard.isKeyguardLocked())) {
            if(!DailyUsageStore.hasUsageAccess(this))DailyUsageStore.addMillis(this, currentPackage, elapsed);
            AppLimitNotifier.check(this, currentPackage);
        }
    }

    private void checkAndBlock(String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
        PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
        android.app.KeyguardManager guard=getSystemService(android.app.KeyguardManager.class);
        if((power!=null && !power.isInteractive()) || (guard!=null && guard.isKeyguardLocked()))return;
        final String blockReason = BlockPolicy.reason(this, packageName);
        if (blockReason.isEmpty()) {
            if (!packageName.equals(lastBlockedPackage)) lastBlockedPackage = "";
            return;
        }
        long now = System.currentTimeMillis();
        if (packageName.equals(lastBlockedPackage) && now - lastBlockLaunch < 1_800L) return;
        lastBlockedPackage = packageName;
        lastBlockLaunch = now;
        currentPackage = "";
        performGlobalAction(GLOBAL_ACTION_HOME);
        handler.postDelayed(() -> {
            Intent block = new Intent(this, BlockScreenActivity.class)
                .putExtra("blockedPackage", packageName)
                .putExtra("blockReason", blockReason)
                .putExtra("limitMinutes", AppBlockerStore.dailyLimitMinutes(this, packageName))
                .putExtra("usedSeconds", DailyUsageStore.usedSeconds(this, packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(block);
        }, 170L);
    }

    @Override public void onInterrupt() {}

    @Override public void onDestroy() {
        handler.removeCallbacks(usageTick);
        accrueUsage();
        super.onDestroy();
    }
}
