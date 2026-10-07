package com.ultras.claims.core;

/** Compact duration text such as "5h 32m" (or Arabic units). */
public final class TimeFormat {
    private TimeFormat() {
    }

    public static String format(long seconds, boolean arabic) {
        if (seconds <= 0) {
            return arabic ? "0 ث" : "0s";
        }
        long d = seconds / 86_400;
        long h = (seconds % 86_400) / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        String ud = arabic ? "ي" : "d";
        String uh = arabic ? "س" : "h";
        String um = arabic ? "د" : "m";
        String us = arabic ? "ث" : "s";
        StringBuilder sb = new StringBuilder();
        if (d > 0) {
            sb.append(d).append(ud).append(' ');
            if (h > 0) {
                sb.append(h).append(uh);
            }
        } else if (h > 0) {
            sb.append(h).append(uh).append(' ');
            if (m > 0) {
                sb.append(m).append(um);
            }
        } else if (m > 0) {
            sb.append(m).append(um);
            if (m < 5 && s > 0) {
                sb.append(' ').append(s).append(us);
            }
        } else {
            sb.append(s).append(us);
        }
        return sb.toString().trim();
    }

    /** Parses "3600", "1h", "30m", "45s", "2d" into seconds; returns -1 for invalid input. */
    public static long parse(String text) {
        if (text == null || text.isBlank()) {
            return -1;
        }
        String t = text.trim().toLowerCase();
        char last = t.charAt(t.length() - 1);
        long mult = 1;
        if (Character.isLetter(last)) {
            mult = switch (last) {
                case 's' -> 1;
                case 'm' -> 60;
                case 'h' -> 3600;
                case 'd' -> 86_400;
                default -> -1;
            };
            t = t.substring(0, t.length() - 1);
        }
        if (mult < 0) {
            return -1;
        }
        try {
            return Long.parseLong(t.trim()) * mult;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
