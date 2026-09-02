package de.danberg.wachwerk;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/** Durable FIFO. Only an acknowledged challenge/snooze removes its exact occurrence. */
final class AlarmSessionStore {
    static final String SESSION = "ringSession";
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("wachwerk_ringing", 0); }
    private static RingingLedger ledger(Context c) { return new RingingLedger(prefs(c).getString("ledger","{}")); }
    static synchronized Intent current(Context c) {
        try { String payload=ledger(c).payload();return payload.isEmpty()?null:Intent.parseUri(payload,Intent.URI_INTENT_SCHEME); }
        catch(Exception e) { return null; }
    }
    static synchronized boolean enqueue(Context c,Intent source) {
        String key=AlarmScheduler.value(source,"alarmId","alarm")+":"+source.getLongExtra("scheduledAt",System.currentTimeMillis());
        Intent copy=new Intent(source).putExtra(SESSION,key);
        if(!copy.hasExtra("firstFiredAt"))copy.putExtra("firstFiredAt",System.currentTimeMillis());
        RingingLedger ledger=ledger(c);
        if(!ledger.enqueue(key,copy.toUri(Intent.URI_INTENT_SCHEME)))return false;
        return prefs(c).edit().putString("ledger",ledger.serialize()).commit();
    }
    static synchronized boolean matches(Context c,String session) { return session!=null && !session.isEmpty() && session.equals(ledger(c).id()); }
    static synchronized boolean remove(Context c,String session) {
        RingingLedger ledger=ledger(c);
        return ledger.acknowledge(session) && prefs(c).edit().putString("ledger",ledger.serialize()).commit();
    }
}
