package com.ultras.claims.settings;

import com.ultras.claims.UltrasClaims;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory cache of every player's settings; every change is written through to the database. */
public final class PlayerSettingsService {
    private final UltrasClaims plugin;
    private final Map<UUID, PlayerSettings> cache = new ConcurrentHashMap<>();

    public PlayerSettingsService(UltrasClaims plugin) {
        this.plugin = plugin;
    }

    public void load() throws Exception {
        cache.clear();
        cache.putAll(plugin.storage().loadPlayerSettings(plugin.defaultLanguage()));
    }

    /** Never null: unknown players get defaults (not stored until something changes). */
    public PlayerSettings get(UUID uuid) {
        return cache.computeIfAbsent(uuid, u -> new PlayerSettings(u, plugin.defaultLanguage()));
    }

    public java.util.Collection<PlayerSettings> all() {
        return java.util.List.copyOf(cache.values());
    }

    public boolean known(UUID uuid) {
        return cache.containsKey(uuid);
    }

    public void save(PlayerSettings s) {
        plugin.storage().savePlayerSettings(s);
    }

    public void reset(UUID uuid) {
        PlayerSettings s = get(uuid);
        s.resetPreferences(plugin.defaultLanguage());
        save(s);
    }
}
