package com.ultras.claims.sound;

import com.ultras.claims.UltrasClaims;
import com.ultras.claims.settings.PlayerSettings;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Quiet, configurable sounds; each player can switch groups off. */
public final class SoundService {
    private record Def(boolean enabled, Sound sound, float volume, float pitch) {
    }

    private final UltrasClaims plugin;
    private volatile Map<SoundKey, Def> defs = new EnumMap<>(SoundKey.class);
    private final Map<String, Sound> byName = new HashMap<>();

    public SoundService(UltrasClaims plugin) {
        this.plugin = plugin;
        for (Sound s : Registry.SOUNDS) {
            NamespacedKey k = Registry.SOUNDS.getKey(s);
            if (k != null) {
                byName.put(k.getKey().replace('.', '_').toUpperCase(Locale.ROOT), s);
            }
        }
    }

    public void reload() {
        Map<SoundKey, Def> fresh = new EnumMap<>(SoundKey.class);
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("sounds");
        for (SoundKey k : SoundKey.values()) {
            ConfigurationSection s = sec == null ? null : sec.getConfigurationSection(k.id());
            if (s == null) {
                continue;
            }
            String name = s.getString("sound", "").toUpperCase(Locale.ROOT).replace('.', '_');
            Sound sound = byName.get(name);
            if (sound == null && !name.isEmpty()) {
                plugin.getLogger().warning("config.yml: sounds." + k.id() + ".sound '" + name + "' is not a known sound - muted");
            }
            fresh.put(k, new Def(s.getBoolean("enabled", true) && sound != null, sound,
                    (float) Math.max(0, Math.min(1, s.getDouble("volume", 0.5))), (float) Math.max(0.5, Math.min(2, s.getDouble("pitch", 1.0)))));
        }
        defs = fresh;
    }

    public void play(Player p, SoundKey key) {
        Def d = defs.get(key);
        if (d == null || !d.enabled() || p == null || !p.isOnline()) {
            return;
        }
        PlayerSettings ps = plugin.players().get(p.getUniqueId());
        if (ps != null && !ps.sound(key.group())) {
            return;
        }
        p.playSound(p.getLocation(), d.sound(), d.volume(), d.pitch());
    }
}
