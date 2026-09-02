package de.danberg.wachwerk;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class BlockPolicyTest {
    private Context context;
    private final Map<String, Map<String,Object>> stores = new HashMap<>();
    private final Map<String,SharedPreferences> preferences = new HashMap<>();
    @Before public void setUp() {
        context=mock(Context.class);
        when(context.getPackageName()).thenReturn("de.danberg.wachwerk");
        when(context.getSharedPreferences(anyString(),anyInt())).thenAnswer(call -> {
            String name=call.getArgument(0);
            if(preferences.containsKey(name)) return preferences.get(name);
            Map<String,Object> values=new HashMap<>(); stores.put(name,values);
            SharedPreferences prefs=mock(SharedPreferences.class);
            SharedPreferences.Editor editor=mock(SharedPreferences.Editor.class);
            when(prefs.edit()).thenReturn(editor);
            when(prefs.getBoolean(anyString(),anyBoolean())).thenAnswer(i->values.getOrDefault(i.getArgument(0),i.getArgument(1)));
            when(prefs.getString(anyString(),anyString())).thenAnswer(i->values.getOrDefault(i.getArgument(0),i.getArgument(1)));
            when(prefs.getInt(anyString(),anyInt())).thenAnswer(i->values.getOrDefault(i.getArgument(0),i.getArgument(1)));
            when(prefs.getLong(anyString(),anyLong())).thenAnswer(i->values.getOrDefault(i.getArgument(0),i.getArgument(1)));
            when(prefs.getStringSet(anyString(),anySet())).thenAnswer(i->values.getOrDefault(i.getArgument(0),i.getArgument(1)));
            when(editor.putBoolean(anyString(),anyBoolean())).thenAnswer(i->{values.put(i.getArgument(0),i.getArgument(1));return editor;});
            when(editor.putString(anyString(),anyString())).thenAnswer(i->{values.put(i.getArgument(0),i.getArgument(1));return editor;});
            when(editor.putInt(anyString(),anyInt())).thenAnswer(i->{values.put(i.getArgument(0),i.getArgument(1));return editor;});
            when(editor.putLong(anyString(),anyLong())).thenAnswer(i->{values.put(i.getArgument(0),i.getArgument(1));return editor;});
            when(editor.putStringSet(anyString(),anySet())).thenAnswer(i->{values.put(i.getArgument(0),i.getArgument(1));return editor;});
            when(editor.clear()).thenAnswer(i->{values.clear();return editor;});
            when(editor.commit()).thenReturn(true);
            preferences.put(name,prefs);return prefs;
        });
    }
    private void armAll() throws Exception {
        DateTimeFormatter format=DateTimeFormatter.ofPattern("HH:mm");
        String start=LocalTime.now().plusHours(1).format(format),end=LocalTime.now().plusHours(2).format(format);
        JSONObject data=new JSONObject("{\"packages\":[\"example.app\"],\"limits\":{\"example.app\":1},\"windows\":{\"example.app\":{}},\"methods\":{\"instant\":\"nfc\",\"limits\":\"nfc\",\"windows\":\"nfc\"}}");
        data.getJSONObject("windows").getJSONObject("example.app").put("start",start).put("end",end);
        AppBlockerStore.sync(context,data.toString());
        for(String scope:new String[]{"instant","limits","windows"}) AppBlockerStore.toggleScope(context,scope);
        for(int i=0;i<5;i++) DailyUsageStore.addMillis(context,"example.app",15000);
    }
    @Test public void allThreeRulesRequireSeparateReleases() throws Exception {
        armAll(); assertEquals("direct",BlockPolicy.reason(context,"example.app"));
        BlockPolicy.release(context,"example.app","direct",5);
        assertEquals("schedule",BlockPolicy.reason(context,"example.app"));
        assertTrue(DailyUsageStore.limitReached(context,"example.app"));
        BlockPolicy.release(context,"example.app","schedule",5);
        assertEquals("limit",BlockPolicy.reason(context,"example.app"));
        BlockPolicy.release(context,"example.app","limit",5);
        assertEquals("",BlockPolicy.reason(context,"example.app"));
        assertTrue(AppBlockerStore.scopeEnabled(context,"windows"));
        assertTrue(AppBlockerStore.scopeEnabled(context,"limits"));
    }
    @Test public void limitReleaseDoesNotGrantWindowOrDirectAccess() throws Exception {
        armAll();BlockPolicy.release(context,"example.app","limit",5);
        assertFalse(DailyUsageStore.limitReached(context,"example.app"));
        assertFalse(DailyUsageStore.windowAllowedForToday(context,"example.app"));
        assertTrue(AppBlockerStore.isBlocked(context,"example.app"));
    }
    @Test public void expiredTemporaryGrantReblocksOnlyItsScope() throws Exception {
        armAll();BlockPolicy.release(context,"example.app","schedule",5);BlockPolicy.release(context,"example.app","limit",5);
        stores.get("wachwerk_daily_usage").put("override_until:example.app",System.currentTimeMillis()-1);
        assertTrue(DailyUsageStore.limitReached(context,"example.app"));
        assertTrue(DailyUsageStore.windowAllowedForToday(context,"example.app"));
    }
    @Test public void methodSelectionAndPasswordAreIndependent() throws Exception {
        AppBlockerStore.sync(context,"{\"methods\":{\"instant\":\"nfc\",\"limits\":\"qr\",\"windows\":\"password\"}}");
        AppBlockerStore.toggleScope(context,"instant");
        assertTrue(AppBlockerStore.setPassword(context,"windows-only","windows"));
        AppBlockerStore.sync(context,"{\"methods\":{\"instant\":\"qr\",\"limits\":\"password\",\"windows\":\"password\"}}");
        assertEquals("nfc",AppBlockerStore.method(context,"instant"));
        assertEquals("password",AppBlockerStore.method(context,"limits"));
        assertTrue(AppBlockerStore.setPassword(context,"limits-only","limits"));
        assertFalse(AppBlockerStore.verifyPassword(context,"windows-only","limits"));
        assertTrue(AppBlockerStore.verifyPassword(context,"windows-only","windows"));
        AppBlockerStore.toggleScope(context,"windows");
        assertFalse(AppBlockerStore.setPassword(context,"replacement","windows"));
        assertTrue(AppBlockerStore.setPassword(context,"replacement","limits"));
    }
    @Test public void legacyKeyMigratesWithoutChangingActiveRules() {
        context.getSharedPreferences("wachwerk_blocker",0).edit().putString("method","qr").putBoolean("enabled",true).putString("daily_limits","{\"example.app\":20}").apply();
        AppBlockerStore.sync(context,"{\"method\":\"qr\",\"methods\":{}}");
        for(String scope:new String[]{"instant","limits","windows"}) assertEquals("qr",AppBlockerStore.method(context,scope));
        assertTrue(AppBlockerStore.isEnabled(context));assertEquals(20,AppBlockerStore.dailyLimitMinutes(context,"example.app"));
    }

    @Test public void dailyRolloverResetsUsageAndGrantsButKeepsConfiguredLimits() throws Exception {
        armAll();DailyUsageStore.allowForMinutes(context,"example.app",5);
        stores.get("wachwerk_daily_usage").put("date","2020-01-01");
        assertEquals(0,DailyUsageStore.usedSeconds(context,"example.app"));
        assertFalse(stores.get("wachwerk_daily_usage").containsKey("override_until:example.app"));
        assertEquals(1,AppBlockerStore.dailyLimitMinutes(context,"example.app"));
    }
    @Test public void morningRuleStartsOnlyForSelectedAppsAndExpiresAutomatically() {
        NativeState.saveSettings(context,"{\"morningBlockEnabled\":true,\"morningBlockMinutes\":20,\"morningBlockPackages\":[\"example.app\"]}");
        long now=System.currentTimeMillis();
        assertFalse(MorningBlockStore.isBlocked(context,"example.app",now));
        MorningBlockStore.startForWake(context,"wake-1",now);
        assertTrue(MorningBlockStore.isBlocked(context,"example.app",now+1199999));
        assertFalse(MorningBlockStore.isBlocked(context,"weather.app",now+1));
        assertFalse(MorningBlockStore.isBlocked(context,"example.app",now+1200000));
    }
    @Test public void sameWakeCannotExtendMorningAndSettingsOnlyAffectNextWake() {
        NativeState.saveSettings(context,"{\"morningBlockEnabled\":true,\"morningBlockMinutes\":20,\"morningBlockPackages\":[\"example.app\"]}");
        long now=System.currentTimeMillis();MorningBlockStore.startForWake(context,"wake-1",now);
        MorningBlockStore.startForWake(context,"wake-1",now+100000);
        assertEquals(now+1200000,MorningBlockStore.until(context));
        NativeState.saveSettings(context,"{\"morningBlockEnabled\":false,\"morningBlockPackages\":[]}");
        assertTrue(MorningBlockStore.isBlocked(context,"example.app",now+100));
        MorningBlockStore.startForWake(context,"wake-2",now+1300000);
        assertFalse(MorningBlockStore.isBlocked(context,"example.app",now+1300000));
    }
    @Test public void morningExpiryDoesNotUnlockAnyOtherRule() throws Exception {
        armAll();
        NativeState.saveSettings(context,"{\"morningBlockEnabled\":true,\"morningBlockMinutes\":20,\"morningBlockPackages\":[\"example.app\"]}");
        MorningBlockStore.startForWake(context,"wake",System.currentTimeMillis());
        assertEquals("morning",BlockPolicy.reason(context,"example.app"));
        BlockPolicy.release(context,"example.app","morning",5);assertEquals("morning",BlockPolicy.reason(context,"example.app"));
        stores.get("wachwerk_morning_block").put("until",System.currentTimeMillis()-1);
        assertEquals("direct",BlockPolicy.reason(context,"example.app"));
        assertTrue(DailyUsageStore.limitReached(context,"example.app"));
    }
    @Test public void nfcUidComparisonRejectsMissingAndWrongKeys() {
        assertTrue(WakeKeyStore.matches("uid:AB01","uid:ab01"));
        assertFalse(WakeKeyStore.matches("",""));assertFalse(WakeKeyStore.matches("uid:01","uid:02"));
    }
}
