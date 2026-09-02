package de.danberg.wachwerk;

import java.util.HashMap;
import java.util.Map;

/** Bounds foreground sessions to a local calendar day, never aggregate buckets. */
final class UsageTimeline {
    private final long start, end;
    private final Map<String, Long> totals = new HashMap<>();
    private String foreground = "", activity = "";
    private boolean interactive = true, locked;
    private long opened = -1;
    UsageTimeline(long start, long end) { this.start = start; this.end = end; }
    void event(long time, int type, String pkg, String instance) {
        if (time > end) return;
        // Android event values are stable; keeping this reducer Android-free makes it testable.
        if (type == 1) { // ACTIVITY_RESUMED
            close(time);
            foreground = pkg == null ? "" : pkg;
            activity = instance == null ? "" : instance;
            open(time);
        } else if (type == 2 || type == 23) { // PAUSED / STOPPED
            if (foreground.equals(pkg) && (activity.isEmpty() || activity.equals(instance))) {
                close(time); foreground = ""; activity = "";
            }
        } else if (type == 15 || type == 18) { // SCREEN_INTERACTIVE / KEYGUARD_HIDDEN
            close(time);
            if (type == 15) interactive = true; else locked = false;
            open(time);
        } else if (type == 16 || type == 17) { // SCREEN_NON_INTERACTIVE / KEYGUARD_SHOWN
            close(time);
            if (type == 16) interactive = false; else locked = true;
        } else if (type == 26 || type == 27) { // DEVICE_SHUTDOWN / STARTUP
            close(time); foreground = ""; activity = "";
        }
    }
    private void open(long time) {
        if (interactive && !locked && !foreground.isEmpty()) opened = Math.max(start, time);
    }
    private void close(long time) {
        if (opened >= 0) {
            long duration = Math.max(0, Math.min(end, time) - opened);
            totals.put(foreground, totals.getOrDefault(foreground, 0L) + duration);
        }
        opened = -1;
    }
    Map<String, Long> finish() {
        close(end);
        Map<String, Long> seconds = new HashMap<>();
        totals.forEach((pkg, ms) -> seconds.put(pkg, ms / 1000L));
        return seconds;
    }
}
