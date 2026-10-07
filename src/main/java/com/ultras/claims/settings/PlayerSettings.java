package com.ultras.claims.settings;

import com.ultras.claims.language.MessageChannel;
import com.ultras.claims.sound.PlayerSoundGroup;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** Per-player preferences, stored per UUID. Nothing here affects other players. */
public final class PlayerSettings {
    private final UUID uuid;
    private String language;
    private boolean claimNotifications = true;
    private boolean claimInvitations = true;
    private boolean ownerEntryAlerts = true;
    private boolean memberEntryAlerts = true;
    private boolean notifyOnLeave = false;
    private EntryPosition entryPosition = EntryPosition.ACTIONBAR;
    private final EnumMap<PlayerSoundGroup, Boolean> sounds = new EnumMap<>(PlayerSoundGroup.class);
    private final EnumMap<MessageChannel, Boolean> messages = new EnumMap<>(MessageChannel.class);
    private int headsIssued;
    private int mapViewId = -1;
    private String mapWorld = "";

    public PlayerSettings(UUID uuid, String defaultLanguage) {
        this.uuid = uuid;
        this.language = defaultLanguage;
        resetPreferences(defaultLanguage);
        this.language = defaultLanguage;
    }

    /** Back to defaults (language included). Counters such as issued heads are kept. */
    public void resetPreferences(String defaultLanguage) {
        language = defaultLanguage;
        claimNotifications = true;
        claimInvitations = true;
        ownerEntryAlerts = true;
        memberEntryAlerts = true;
        notifyOnLeave = false;
        entryPosition = EntryPosition.ACTIONBAR;
        for (PlayerSoundGroup g : PlayerSoundGroup.values()) {
            sounds.put(g, Boolean.TRUE);
        }
        for (MessageChannel c : MessageChannel.values()) {
            messages.put(c, Boolean.TRUE);
        }
    }

    public UUID uuid() {
        return uuid;
    }

    public String language() {
        return language;
    }

    public void language(String l) {
        this.language = l;
    }

    public boolean claimNotifications() {
        return claimNotifications;
    }

    public void claimNotifications(boolean v) {
        this.claimNotifications = v;
    }

    public boolean claimInvitations() {
        return claimInvitations;
    }

    public void claimInvitations(boolean v) {
        this.claimInvitations = v;
    }

    public boolean ownerEntryAlerts() {
        return ownerEntryAlerts;
    }

    public void ownerEntryAlerts(boolean v) {
        this.ownerEntryAlerts = v;
    }

    public boolean memberEntryAlerts() {
        return memberEntryAlerts;
    }

    public void memberEntryAlerts(boolean v) {
        this.memberEntryAlerts = v;
    }

    public boolean notifyOnLeave() {
        return notifyOnLeave;
    }

    public void notifyOnLeave(boolean v) {
        this.notifyOnLeave = v;
    }

    public EntryPosition entryPosition() {
        return entryPosition;
    }

    public void entryPosition(EntryPosition p) {
        this.entryPosition = p;
    }

    public boolean sound(PlayerSoundGroup g) {
        return Boolean.TRUE.equals(sounds.get(g));
    }

    public void sound(PlayerSoundGroup g, boolean v) {
        sounds.put(g, v);
    }

    public boolean allSounds() {
        for (Boolean b : sounds.values()) {
            if (!Boolean.TRUE.equals(b)) {
                return false;
            }
        }
        return true;
    }

    public void allSounds(boolean v) {
        for (PlayerSoundGroup g : PlayerSoundGroup.values()) {
            sounds.put(g, v);
        }
    }

    public boolean message(MessageChannel c) {
        return c == MessageChannel.ALWAYS || Boolean.TRUE.equals(messages.get(c));
    }

    public void message(MessageChannel c, boolean v) {
        messages.put(c, v);
    }

    public int headsIssued() {
        return headsIssued;
    }

    public void headsIssued(int n) {
        this.headsIssued = Math.max(0, n);
    }

    public int mapViewId() {
        return mapViewId;
    }

    public String mapWorld() {
        return mapWorld;
    }

    public void mapView(int id, String world) {
        this.mapViewId = id;
        this.mapWorld = world == null ? "" : world;
    }

    // ------------------------------------------------------------------ storage text

    /** key=value;key=value of everything except language, counters and the map link. */
    public String encode() {
        StringBuilder sb = new StringBuilder();
        put(sb, "notif", claimNotifications);
        put(sb, "invite", claimInvitations);
        put(sb, "ownerAlert", ownerEntryAlerts);
        put(sb, "memberAlert", memberEntryAlerts);
        put(sb, "leaveAlert", notifyOnLeave);
        sb.append("pos=").append(entryPosition.name()).append(';');
        for (Map.Entry<PlayerSoundGroup, Boolean> e : sounds.entrySet()) {
            put(sb, "snd." + e.getKey().name(), e.getValue());
        }
        for (Map.Entry<MessageChannel, Boolean> e : messages.entrySet()) {
            put(sb, "msg." + e.getKey().name(), e.getValue());
        }
        return sb.toString();
    }

    public void decode(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        for (String part : text.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String k = part.substring(0, eq);
            String v = part.substring(eq + 1);
            boolean b = v.equals("1");
            try {
                switch (k) {
                    case "notif" -> claimNotifications = b;
                    case "invite" -> claimInvitations = b;
                    case "ownerAlert" -> ownerEntryAlerts = b;
                    case "memberAlert" -> memberEntryAlerts = b;
                    case "leaveAlert" -> notifyOnLeave = b;
                    case "pos" -> entryPosition = EntryPosition.valueOf(v);
                    default -> {
                        if (k.startsWith("snd.")) {
                            sounds.put(PlayerSoundGroup.valueOf(k.substring(4)), b);
                        } else if (k.startsWith("msg.")) {
                            messages.put(MessageChannel.valueOf(k.substring(4)), b);
                        }
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // unknown key from a newer/older version: skip
            }
        }
    }

    private static void put(StringBuilder sb, String key, boolean v) {
        sb.append(key).append('=').append(v ? '1' : '0').append(';');
    }
}
