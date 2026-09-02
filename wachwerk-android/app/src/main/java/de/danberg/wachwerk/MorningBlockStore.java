package de.danberg.wachwerk;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;

/** Independent timed rule; a wake snapshots the configuration for this morning. */
final class MorningBlockStore {
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("wachwerk_morning_block",0); }
    static void startForWake(Context c,String eventId) { startForWake(c,eventId,System.currentTimeMillis()); }
    static synchronized void startForWake(Context c,String eventId,long now) {
        SharedPreferences p=prefs(c);
        if(eventId.equals(p.getString("wake","")))return;
        JSONObject settings=NativeState.settings(c);
        if(!settings.optBoolean("morningBlockEnabled",false))return;
        JSONArray apps=settings.optJSONArray("morningBlockPackages");
        Set<String> selected=new HashSet<>();
        if(apps!=null)for(int i=0;i<apps.length();i++) {
            String pkg=apps.optString(i);
            if(!pkg.isEmpty() && !pkg.equals(c.getPackageName()))selected.add(pkg);
        }
        if(selected.isEmpty())return;
        int minutes=Math.max(1,Math.min(1440,settings.optInt("morningBlockMinutes",20)));
        p.edit().putString("wake",eventId).putLong("until",now+minutes*60000L)
            .putStringSet("packages",selected).commit();
    }
    static long until(Context c) { return prefs(c).getLong("until",0L); }
    static boolean isBlocked(Context c,String pkg) { return isBlocked(c,pkg,System.currentTimeMillis()); }
    static boolean isBlocked(Context c,String pkg,long now) {
        return until(c)>now && prefs(c).getStringSet("packages",new HashSet<>()).contains(pkg);
    }
    static JSONObject state(Context c) {
        JSONObject json=new JSONObject();
        try { json.put("until",until(c)).put("packages",new JSONArray(prefs(c).getStringSet("packages",new HashSet<>()))); }
        catch(Exception ignored){}
        return json;
    }
}
