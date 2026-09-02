package de.danberg.wachwerk;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class AppBlockerStoreTest {
    private Context context;
    private Map<String,Object> values;
    @Before public void setUp() {
        context=mock(Context.class); SharedPreferences prefs=mock(SharedPreferences.class);
        SharedPreferences.Editor editor=mock(SharedPreferences.Editor.class); values=new HashMap<>();
        when(context.getPackageName()).thenReturn("de.danberg.wachwerk");
        when(context.getSharedPreferences(anyString(),anyInt())).thenReturn(prefs);
        when(prefs.edit()).thenReturn(editor);
        when(prefs.getBoolean(anyString(),anyBoolean())).thenAnswer(i->values.getOrDefault(i.getArgument(0),i.getArgument(1)));
        when(prefs.getString(anyString(),anyString())).thenAnswer(i->values.getOrDefault(i.getArgument(0),i.getArgument(1)));
        when(prefs.getInt(anyString(),anyInt())).thenAnswer(i->values.getOrDefault(i.getArgument(0),i.getArgument(1)));
        when(prefs.getStringSet(anyString(),anySet())).thenAnswer(i->values.getOrDefault(i.getArgument(0),i.getArgument(1)));
        when(editor.putBoolean(anyString(),anyBoolean())).thenAnswer(i->{values.put(i.getArgument(0),i.getArgument(1));return editor;});
        when(editor.putString(anyString(),anyString())).thenAnswer(i->{values.put(i.getArgument(0),i.getArgument(1));return editor;});
        when(editor.putInt(anyString(),anyInt())).thenAnswer(i->{values.put(i.getArgument(0),i.getArgument(1));return editor;});
        when(editor.putStringSet(anyString(),anySet())).thenAnswer(i->{values.put(i.getArgument(0),i.getArgument(1));return editor;});
    }
    private void sync(int limit,String start) {
        AppBlockerStore.sync(context,"{\"enabled\":false,\"method\":\"nfc\",\"packages\":[\"example.app\"],\"limits\":{\"example.app\":"+limit+"},\"windows\":{\"example.app\":{\"start\":\""+start+"\",\"end\":\"18:00\"}}}");
    }
    @Test public void freshRulesNeedExplicitActivation() {
        sync(45,"12:00");
        assertFalse(AppBlockerStore.scopeEnabled(context,"limits"));
        assertFalse(AppBlockerStore.scopeEnabled(context,"windows"));
        assertEquals(0,AppBlockerStore.dailyLimitMinutes(context,"example.app"));
        AppBlockerStore.toggleScope(context,"limits");
        assertEquals(45,AppBlockerStore.dailyLimitMinutes(context,"example.app"));
        assertFalse(AppBlockerStore.scopeEnabled(context,"windows"));
    }
    @Test public void lockedRulesSurviveStaleUiSync() throws Exception {
        sync(45,"12:00");AppBlockerStore.toggleScope(context,"limits");AppBlockerStore.toggleScope(context,"windows");AppBlockerStore.toggle(context);
        sync(120,"00:00");
        assertTrue(AppBlockerStore.isEnabled(context));
        assertEquals(45,AppBlockerStore.dailyLimitMinutes(context,"example.app"));
        assertEquals("12:00",new JSONObject(AppBlockerStore.windowsJson(context)).getJSONObject("example.app").getString("start"));
    }
    @Test public void unlockedRulesCanBeEditedAndArmedAgain() {
        sync(45,"12:00");AppBlockerStore.toggleScope(context,"limits");AppBlockerStore.toggleScope(context,"limits");sync(63,"12:00");AppBlockerStore.toggleScope(context,"limits");
        assertEquals(63,AppBlockerStore.dailyLimitMinutes(context,"example.app"));
    }
    @Test public void upgradePreservesPreviouslyActiveLimits() {
        values.put("daily_limits","{\"example.app\":30}");
        assertTrue(AppBlockerStore.scopeEnabled(context,"limits"));
        sync(99,"12:00");assertEquals(30,AppBlockerStore.dailyLimitMinutes(context,"example.app"));
    }
    @Test public void passwordCannotChangeWhileItsOwnScopeIsLocked() {
        assertTrue(AppBlockerStore.setPassword(context,"test-password"));
        sync(45,"12:00");AppBlockerStore.toggleScope(context,"instant");
        assertFalse(AppBlockerStore.setPassword(context,"replacement"));
        assertTrue(AppBlockerStore.verifyPassword(context,"test-password"));
        assertFalse(AppBlockerStore.verifyPassword(context,"wrong"));
    }
    @Test public void staleUiCannotEraseEnrolledNfcKey() {
        AppBlockerStore.setToken(context,"uid:A1B2");sync(45,"12:00");assertEquals("uid:A1B2",AppBlockerStore.token(context));
    }
}
