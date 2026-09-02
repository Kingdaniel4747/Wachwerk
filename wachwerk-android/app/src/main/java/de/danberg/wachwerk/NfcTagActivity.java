package de.danberg.wachwerk;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

public class NfcTagActivity extends Activity implements NfcAdapter.ReaderCallback {
    private UiPalette palette;
    private static final String MIME = "application/vnd.de.danberg.wachwerk";
    private NfcAdapter adapter;
    private TextView status;
    private boolean enroll;
    private String expected;
    private volatile boolean busy;
    private ScanPulseView pulse;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        palette = UiPalette.from(this);
        enroll = "enroll".equals(getIntent().getStringExtra("mode"));
        expected = getIntent().getStringExtra("expectedToken");
        adapter = NfcAdapter.getDefaultAdapter(this);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(28), dp(62), dp(28), dp(32));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{palette.map(Color.rgb(26, 62, 88)), palette.map(Color.rgb(6, 19, 31)), palette.map(Color.rgb(3, 11, 18))}));
        pulse = new ScanPulseView(this); root.addView(pulse, new LinearLayout.LayoutParams(dp(176), dp(176)));
        TextView title = text(enroll ? "NFC-Tag anlernen" : "NFC-Tag scannen", 27, Color.WHITE, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = wrap(); titleParams.topMargin = dp(24); root.addView(title, titleParams);
        TextView info = text(enroll
            ? "Halte den Tag ruhig an die Rückseite des Handys. Wachwerk speichert nur seine feste Tag-ID lokal und beschreibt den Tag nicht."
            : "Halte den angelernten Tag an die Rückseite des Handys.", 14, palette.map(Color.rgb(190, 211, 229)), Typeface.NORMAL);
        info.setGravity(Gravity.CENTER); LinearLayout.LayoutParams infoParams = matchWrap(); infoParams.topMargin = dp(12); root.addView(info, infoParams);
        status = text(adapter == null ? "Dieses Handy unterstützt kein NFC." : adapter.isEnabled() ? "Bereit · warte auf den Tag …" : "NFC ist ausgeschaltet.", 13, palette.map(Color.rgb(255, 211, 71)), Typeface.BOLD);
        status.setGravity(Gravity.CENTER); LinearLayout.LayoutParams statusParams = matchWrap(); statusParams.topMargin = dp(28); root.addView(status, statusParams);
        if (adapter != null && !adapter.isEnabled()) {
            Button open = button("NFC in Android einschalten"); open.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NFC_SETTINGS)));
            LinearLayout.LayoutParams params = match(dp(52)); params.topMargin = dp(18); root.addView(open, params);
        }
        Button cancel = button("Abbrechen"); cancel.setBackgroundColor(Color.TRANSPARENT); cancel.setTextColor(palette.map(Color.rgb(148, 171, 191))); cancel.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams cancelParams = match(dp(52)); cancelParams.topMargin = dp(12); root.addView(cancel, cancelParams);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(true); scroll.addView(root); setContentView(scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        if (adapter != null && adapter.isEnabled()) adapter.enableReaderMode(this, this,
            NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B | NfcAdapter.FLAG_READER_NFC_F | NfcAdapter.FLAG_READER_NFC_V, null);
    }

    @Override protected void onPause() {
        if (adapter != null) adapter.disableReaderMode(this);
        super.onPause();
    }

    @Override public void onTagDiscovered(Tag tag) {
        if (busy) return;
        busy = true;
        try {
            String token;
            String label;
            if (enroll) {
                token = uidToken(tag);
                boolean cleaned = removeLegacyWachwerkRecord(tag);
                label = cleaned ? "NFC-Tag angelernt und alten Wachwerk-Eintrag entfernt" : "NFC-Tag erfolgreich angelernt";
            } else {
                token = tokenForTag(tag);
                if (expected == null || expected.isEmpty() || !expected.equals(token)) {
                    runOnUiThread(() -> { status.setText("Dieser NFC-Tag passt nicht. Bitte den angelernten Tag verwenden."); busy = false; });
                    return;
                }
                label = "NFC-Tag erkannt";
            }
            Intent result = new Intent().putExtra("token", token).putExtra("label", label);
            completeAfterTagRemoved(tag, result, label);
        } catch (Exception error) {
            runOnUiThread(() -> { status.setText("Tag konnte nicht gelesen werden. Bitte erneut ruhig anhalten."); busy = false; });
        }
    }

    private void completeAfterTagRemoved(Tag tag, Intent result, String label) {
        runOnUiThread(() -> {
            pulse.success();
            setResult(RESULT_OK, result);
            status.setText(label + " · Tag jetzt vom Handy nehmen");
            status.setTextColor(palette.map(Color.rgb(155, 245, 177)));
        });
        boolean waitingForRemoval = false;
        try {
            if (adapter != null) waitingForRemoval = adapter.ignore(tag, 1_500,
                () -> handler.postDelayed(this::finish, 140L), handler);
        } catch (Exception ignored) {}
        if (!waitingForRemoval) handler.postDelayed(this::finish, 1_200L);
        handler.postDelayed(() -> { if (!isFinishing()) finish(); }, 6_000L);
    }

    private static boolean removeLegacyWachwerkRecord(Tag tag) {
        try {
            Ndef ndef = Ndef.get(tag);
            if (ndef == null) return false;
            ndef.connect();
            NdefMessage current = ndef.getNdefMessage();
            if (current == null || !ndef.isWritable()) { ndef.close(); return false; }
            List<NdefRecord> kept = new ArrayList<>();
            boolean removed = false;
            for (NdefRecord record : current.getRecords()) {
                String type = new String(record.getType(), StandardCharsets.US_ASCII);
                if (record.getTnf() == NdefRecord.TNF_MIME_MEDIA && MIME.equals(type)) removed = true;
                else kept.add(record);
            }
            if (removed) {
                if (kept.isEmpty()) kept.add(new NdefRecord(NdefRecord.TNF_EMPTY, new byte[0], new byte[0], new byte[0]));
                ndef.writeNdefMessage(new NdefMessage(kept.toArray(new NdefRecord[0])));
            }
            ndef.close();
            return removed;
        } catch (Exception ignored) {}
        return false;
    }

    public static String tokenForTag(Tag tag) {
        return uidToken(tag);
    }

    private static String uidToken(Tag tag) {
        StringBuilder value = new StringBuilder("uid:");
        byte[] id = tag.getId();
        if (id != null) for (byte item : id) value.append(String.format(Locale.ROOT, "%02X", item & 0xff));
        return value.toString();
    }

    private TextView text(String value, int size, int color, int style) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setTypeface(Typeface.create("sans", style)); return view; }
    private Button button(String value) { Button button = new Button(this); button.setText(value); button.setAllCaps(false); button.setTypeface(Typeface.DEFAULT_BOLD); button.setTextColor(palette.map(Color.rgb(6,19,31))); button.setBackground(rounded(palette.map(Color.rgb(155,245,177)), 15)); return button; }
    private GradientDrawable rounded(int color, int radius) { GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable; }
    private LinearLayout.LayoutParams wrap() { return new LinearLayout.LayoutParams(-2, -2); }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams match(int height) { return new LinearLayout.LayoutParams(-1, height); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
