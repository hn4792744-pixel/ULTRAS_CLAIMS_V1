package com.ultras.claims.listener;

import com.ultras.claims.UltrasClaims;
import com.ultras.claims.claim.Claim;
import com.ultras.claims.claim.ClaimState;
import com.ultras.claims.claim.NotificationKey;
import com.ultras.claims.language.MessageChannel;
import com.ultras.claims.settings.PlayerSettings;
import com.ultras.claims.sound.SoundKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Claim entry / exit. Driven only by a change of chunk (a cheap integer comparison per move event), never by a timer
 * that scans players.
 */
public final class EntryListener implements Listener {
    private final UltrasClaims plugin;
    private final Map<UUID, String> current = new HashMap<>();

    public EntryListener(UltrasClaims plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null || ((from.getBlockX() >> 4) == (to.getBlockX() >> 4) && (from.getBlockZ() >> 4) == (to.getBlockZ() >> 4)
                && from.getWorld() == to.getWorld())) {
            return;
        }
        update(e.getPlayer(), to, true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        update(e.getPlayer(), e.getTo(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        update(e.getPlayer(), e.getRespawnLocation(), false);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> update(e.getPlayer(), e.getPlayer().getLocation(), true));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        String id = current.remove(p.getUniqueId());
        if (id != null) {
            Claim c = plugin.claims().get(id);
            if (c != null) {
                visitorLeft(p, c);
            }
        }
    }

    private void update(Player p, Location to, boolean announce) {
        Claim now = to == null ? null : plugin.claims().at(to);
        String newId = now == null ? null : now.id();
        String oldId = current.get(p.getUniqueId());
        if (java.util.Objects.equals(oldId, newId)) {
            return;
        }
        if (newId == null) {
            current.remove(p.getUniqueId());
        } else {
            current.put(p.getUniqueId(), newId);
        }
        if (oldId != null) {
            Claim old = plugin.claims().get(oldId);
            if (old != null) {
                if (announce && newId == null) {
                    plugin.messages().positioned(p, "exit", MessageChannel.EXIT, "owner", old.ownerName(), "id", old.id());
                    plugin.sounds().play(p, SoundKey.EXIT);
                }
                visitorLeft(p, old);
            }
        }
        if (now != null && announce) {
            boolean own = now.isOwner(p.getUniqueId());
            String key = own ? "entry-own" : now.isMember(p.getUniqueId()) ? "entry-member" : "entry-visitor";
            if (now.state() == ClaimState.GRACE && own) {
                key = "entry-grace";
            }
            plugin.messages().positioned(p, key, MessageChannel.ENTRY, "owner", now.ownerName(), "id", now.id());
            plugin.sounds().play(p, SoundKey.ENTER);
            visitorEntered(p, now);
        }
    }

    private boolean isVisitor(Player p, Claim c) {
        return !c.isOwner(p.getUniqueId()) && !c.isMember(p.getUniqueId());
    }

    private void visitorEntered(Player p, Claim c) {
        if (!isVisitor(p, c)) {
            return;
        }
        alert(c, p, NotificationKey.VISITOR_ENTERED, "notify-visitor-entered", false);
    }

    private void visitorLeft(Player p, Claim c) {
        if (!isVisitor(p, c)) {
            return;
        }
        alert(c, p, NotificationKey.VISITOR_LEFT, "notify-visitor-left", true);
    }

    private void alert(Claim c, Player visitor, NotificationKey key, String message, boolean leaving) {
        if (!c.notifies(key)) {
            return;
        }
        for (UUID id : plugin.notifications().audience(c)) {
            Player target = Bukkit.getPlayer(id);
            if (target == null || target.equals(visitor)) {
                continue;
            }
            PlayerSettings ps = plugin.players().get(id);
            boolean wants = c.isOwner(id) ? ps.ownerEntryAlerts() : ps.memberEntryAlerts();
            if (!ps.claimNotifications() || !wants || (leaving && !ps.notifyOnLeave())) {
                continue;
            }
            plugin.messages().send(target, message, MessageChannel.CLAIM, "player", visitor.getName(), "id", c.id());
            plugin.sounds().play(target, SoundKey.ENTER);
        }
    }
}
