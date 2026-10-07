package com.ultras.claims.hologram;

import com.ultras.claims.UltrasClaims;
import com.ultras.claims.border.BorderService;
import com.ultras.claims.claim.Claim;
import com.ultras.claims.claim.ClaimState;
import com.ultras.claims.core.TimeFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Optional small text above a claim head (owner + remaining time). Off by default - the design stays quiet. */
public final class HologramService {
    private final UltrasClaims plugin;
    private final Map<String, UUID> displays = new HashMap<>();
    private BukkitTask task;

    public HologramService(UltrasClaims plugin) {
        this.plugin = plugin;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("hologram.enabled", false);
    }

    public void start() {
        stop();
        if (!enabled()) {
            return;
        }
        long period = Math.max(5, plugin.getConfig().getLong("hologram.update-seconds", 30)) * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> plugin.claims().all().forEach(this::refresh), 40L, period);
    }

    public void reload() {
        removeAll();
        start();
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        removeAll();
    }

    public void refresh(Claim c) {
        if (!enabled()) {
            return;
        }
        World w = Bukkit.getWorld(c.world());
        if (w == null || !w.isChunkLoaded(c.headX() >> 4, c.headZ() >> 4)) {
            return;
        }
        TextDisplay td = existing(c);
        if (td == null) {
            Location at = new Location(w, c.headX() + 0.5, c.headY() + plugin.getConfig().getDouble("hologram.height", 1.35), c.headZ() + 0.5);
            td = w.spawn(at, TextDisplay.class, d -> {
                d.setPersistent(false);
                d.addScoreboardTag(BorderService.TAG);
                d.setBillboard(Display.Billboard.CENTER);
                d.setSeeThrough(false);
                d.setShadowed(false);
                d.setBackgroundColor(org.bukkit.Color.fromARGB(0x66000000));
                d.setViewRange(0.6f);
            });
            displays.put(c.id(), td.getUniqueId());
        }
        String lang = plugin.messages().languageOf(c.ownerUuid());
        boolean ar = lang.equals("ar");
        String time = !plugin.cabin().enabled() ? "∞" : TimeFormat.format(plugin.cabin().remainingSeconds(c), ar);
        String key = c.state() == ClaimState.GRACE ? "hologram-grace" : "hologram";
        Component text = plugin.messages().render(lang, key, "owner", c.ownerName(), "time", time, "id", c.id());
        td.text(text);
    }

    public void remove(Claim c) {
        UUID id = displays.remove(c.id());
        if (id != null) {
            var e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
    }

    private TextDisplay existing(Claim c) {
        UUID id = displays.get(c.id());
        if (id == null) {
            return null;
        }
        var e = Bukkit.getEntity(id);
        if (e instanceof TextDisplay td && td.isValid()) {
            return td;
        }
        displays.remove(c.id());
        return null;
    }

    private void removeAll() {
        for (UUID id : displays.values()) {
            var e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        displays.clear();
    }
}
