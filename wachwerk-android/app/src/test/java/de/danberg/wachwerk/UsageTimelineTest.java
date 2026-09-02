package de.danberg.wachwerk;
import org.junit.Test;
import java.time.*;
import static org.junit.Assert.*;
public class UsageTimelineTest {
    @Test public void midnightClipsYesterdayAndStartsFresh() {
        UsageTimeline t = new UsageTimeline(100000, 160000);
        t.event(40000, 1, "app", "a"); t.event(115000, 2, "app", "a");
        assertEquals(15L, (long)t.finish().get("app"));
    }
    @Test public void screenOffAndLockNeverCountAsUsage() {
        UsageTimeline t = new UsageTimeline(0, 200000);
        t.event(0,1,"app","a"); t.event(10000,16,null,null); t.event(10001,17,null,null);
        t.event(100000,15,null,null); t.event(120000,18,null,null); t.event(125000,2,"app","a");
        assertEquals(15L, (long)t.finish().get("app"));
    }
    @Test public void switchingAppsClosesMissingPauseAndStalePauseIsIgnored() {
        UsageTimeline t = new UsageTimeline(0, 90000);
        t.event(0,1,"app","a");t.event(10000,1,"other","b");t.event(20000,2,"app","a");
        assertEquals(10L,(long)t.finish().get("app"));
    }
    @Test public void activityTransitionsAndDuplicatesDoNotDoubleCount() {
        UsageTimeline t = new UsageTimeline(0, 60000);
        t.event(0,1,"app","a");t.event(10000,1,"app","b");t.event(11000,2,"app","a");
        t.event(20000,1,"app","b");t.event(30000,2,"app","b");
        assertEquals(30L,(long)t.finish().get("app"));
    }
    @Test public void rebootClosesSessionAndDoesNotInventMoreUsage() {
        UsageTimeline t = new UsageTimeline(0,60000);
        t.event(0,1,"app","a");t.event(5000,26,null,null);t.event(20000,27,null,null);
        assertEquals(5L,(long)t.finish().get("app"));
    }
    @Test public void localDayHandlesSummerTimeChange() {
        ZoneId zone=ZoneId.of("Europe/Berlin"); LocalDate day=LocalDate.of(2026,3,29);
        long start=day.atStartOfDay(zone).toInstant().toEpochMilli(),end=day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        UsageTimeline t=new UsageTimeline(start,end);t.event(start-60000,1,"app","a");
        assertEquals(23*3600L,(long)t.finish().get("app"));
    }
}
