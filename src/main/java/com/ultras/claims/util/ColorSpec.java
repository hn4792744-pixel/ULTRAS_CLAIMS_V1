package com.ultras.claims.util;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Material;

import java.util.Locale;
import java.util.Map;

/** A color from config: a name (GREEN, DARK_GREEN, ...) or #rrggbb. Maps to display blocks, glass panes and map colors. */
public final class ColorSpec {
    private static final Map<String, Integer> NAMES = Map.ofEntries(
            Map.entry("WHITE", 0xFFFFFF), Map.entry("GREEN", 0x55FF55), Map.entry("DARK_GREEN", 0x00AA00),
            Map.entry("BLUE", 0x5555FF), Map.entry("DARK_BLUE", 0x0000AA), Map.entry("RED", 0xFF5555),
            Map.entry("DARK_RED", 0xAA0000), Map.entry("GOLD", 0xFFAA00), Map.entry("YELLOW", 0xFFFF55),
            Map.entry("PURPLE", 0xAA00AA), Map.entry("LIGHT_PURPLE", 0xFF55FF), Map.entry("AQUA", 0x55FFFF),
            Map.entry("CYAN", 0x00AAAA), Map.entry("GRAY", 0xAAAAAA), Map.entry("DARK_GRAY", 0x555555),
            Map.entry("BLACK", 0x000000), Map.entry("ORANGE", 0xFFAA00), Map.entry("PINK", 0xFF8FC7));

    /** dye name, rgb. */
    private static final Object[][] DYES = {
            {"WHITE", 0xF9FFFE}, {"ORANGE", 0xF9801D}, {"MAGENTA", 0xC74EBD}, {"LIGHT_BLUE", 0x3AB3DA},
            {"YELLOW", 0xFED83D}, {"LIME", 0x80C71F}, {"PINK", 0xF38BAA}, {"GRAY", 0x474F52},
            {"LIGHT_GRAY", 0x9D9D97}, {"CYAN", 0x169C9C}, {"PURPLE", 0x8932B8}, {"BLUE", 0x3C44AA},
            {"BROWN", 0x835432}, {"GREEN", 0x5E7C16}, {"RED", 0xB02E26}, {"BLACK", 0x1D1D21}};

    private final int rgb;

    private ColorSpec(int rgb) {
        this.rgb = rgb & 0xFFFFFF;
    }

    public static ColorSpec parse(String text, ColorSpec fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        String t = text.trim();
        if (t.startsWith("#") && t.length() == 7) {
            try {
                return new ColorSpec(Integer.parseInt(t.substring(1), 16));
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        Integer v = NAMES.get(t.toUpperCase(Locale.ROOT).replace(' ', '_'));
        return v == null ? fallback : new ColorSpec(v);
    }

    public static ColorSpec of(int rgb) {
        return new ColorSpec(rgb);
    }

    public int rgb() {
        return rgb;
    }

    public Color bukkit() {
        return Color.fromRGB(rgb);
    }

    public TextColor text() {
        return TextColor.color(rgb);
    }

    public java.awt.Color awt() {
        return new java.awt.Color(rgb);
    }

    private String nearestDye() {
        String best = "WHITE";
        long bestD = Long.MAX_VALUE;
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        for (Object[] d : DYES) {
            int c = (Integer) d[1];
            long dr = r - ((c >> 16) & 255), dg = g - ((c >> 8) & 255), db = b - (c & 255);
            long dist = dr * dr * 3 + dg * dg * 4 + db * db * 2;
            if (dist < bestD) {
                bestD = dist;
                best = (String) d[0];
            }
        }
        return best;
    }

    public Material concrete() {
        return Material.valueOf(nearestDye() + "_CONCRETE");
    }

    public Material pane() {
        return Material.valueOf(nearestDye() + "_STAINED_GLASS_PANE");
    }

    public Material glass() {
        return Material.valueOf(nearestDye() + "_STAINED_GLASS");
    }
}
