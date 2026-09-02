package de.danberg.wachwerk;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

/** An alarm uses its saved key; newly created alarms reuse the one-time setup. */
public final class WakeKeyStore {
    static boolean matches(String expected, String scanned) {
        if(expected==null || expected.isEmpty() || scanned==null || scanned.isEmpty())return false;
        return expected.startsWith("uid:") && scanned.startsWith("uid:") ? expected.equalsIgnoreCase(scanned) : expected.equals(scanned);
    }
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("wachwerk_wake_keys", Context.MODE_PRIVATE); }
    public static String token(Context c) { return prefs(c).getString("nfc", AppBlockerStore.token(c)); }
    public static void setToken(Context c, String token) { if (token != null && !token.isEmpty()) prefs(c).edit().putString("nfc", token).apply(); }
    public static void verifyQr(Context c) { prefs(c).edit().putBoolean("qrVerified", true).apply(); }
    public static String state(Context c) {
        try { return new JSONObject().put("nfcToken", token(c)).put("qrVerified", prefs(c).getBoolean("qrVerified", false)).toString(); }
        catch (Exception ignored) { return "{}"; }
    }
}
