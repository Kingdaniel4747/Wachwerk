package de.danberg.wachwerk;

import android.content.Context;

/** Same named palettes as the offline UI; read locally, no network resources. */
public final class UiPalette {
    public final int background, panel, field, line, text, muted, accent, success, gold, danger;
    private final boolean original;
    private UiPalette(String name) {
        original = !"solar".equals(name) && !"dusk".equals(name);
        if ("solar".equals(name)) {
            background=0xff002638; panel=0xff003049; field=0xff123f51; line=0xff48606a;
            text=0xffeae2b7; muted=0xffb9beb0; accent=0xfff77f00; success=0xffa5cda7; gold=0xfffcbf49; danger=0xffd62828;
        } else if ("dusk".equals(name)) {
            background=0xff191629; panel=0xff29223e; field=0xff342b49; line=0xff5a4d6b;
            text=0xffe6def5; muted=0xffb8acc8; accent=0xffc2b2f1; success=0xff90cdb7; gold=0xffedd9bc; danger=0xffeaa6ad;
        } else {
            background=0xff06131f; panel=0xff102331; field=0xff183348; line=0xff345063;
            text=0xffc9dcf8; muted=0xff9eb3c5; accent=0xff9bf5b1; success=0xff9bf5b1; gold=0xffffd347; danger=0xffff857a;
        }
    }
    public static UiPalette from(Context context) { return new UiPalette(NativeState.settings(context).optString("palette", "classic")); }
    public int map(int color) {
        if (original || color == 0xffffffff || (color >>> 24) != 255) return color;
        int r=(color>>16)&255, g=(color>>8)&255, b=color&255;
        int light=Math.max(r,Math.max(g,b));
        if (g > r*1.2 && g > b*1.12) return light > 110 ? success : panel;
        if (r > b*1.45 && g > b*1.2) return light > 120 ? gold : panel;
        if (r > g*1.3 && r > b*1.2) return light > 120 ? danger : panel;
        return light < 40 ? background : light < 78 ? panel : light < 115 ? field : light < 175 ? muted : text;
    }
}
