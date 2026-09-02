package de.danberg.wachwerk;
import org.junit.Test;
import static org.junit.Assert.*;
public class RingingLedgerTest {
    @Test public void rotationTaskRemovalAndProcessReloadKeepAlarmActive() {
        RingingLedger a=new RingingLedger("{}");a.enqueue("alarm:1","sound and challenge");
        RingingLedger reloaded=new RingingLedger(a.serialize());
        assertEquals("alarm:1",reloaded.id());assertEquals("sound and challenge",reloaded.payload());
    }
    @Test public void simultaneousAlarmsAreQueuedAndOnlyMatchingChallengeAcknowledgesHead() {
        RingingLedger a=new RingingLedger("{}");a.enqueue("a:1","first");a.enqueue("b:1","second");
        assertFalse(a.acknowledge("b:1"));assertFalse(a.acknowledge("stale"));
        assertTrue(a.acknowledge("a:1"));assertEquals("b:1",a.id());
        assertFalse(a.acknowledge("a:1"));assertTrue(a.acknowledge("b:1"));assertEquals("",a.id());
    }
    @Test public void duplicateBroadcastOrOldNotificationCannotReviveCompletedAlarm() {
        RingingLedger a=new RingingLedger("{}");assertTrue(a.enqueue("a:1","first"));
        assertFalse(a.enqueue("a:1","duplicate"));a.acknowledge("a:1");
        RingingLedger b=new RingingLedger(a.serialize());assertFalse(b.enqueue("a:1","duplicate"));
        assertTrue(b.enqueue("a:2","tomorrow or snooze"));assertEquals("a:2",b.id());
    }
}
