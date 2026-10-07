package com.ultras.claims.listener;

import com.ultras.claims.UltrasClaims;
import com.ultras.claims.claim.Claim;
import com.ultras.claims.claim.ClaimMember;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.MapInitializeEvent;

/** Join/quit housekeeping. */
public final class PlayerListener implements Listener {
    private final UltrasClaims plugin;

    public PlayerListener(UltrasClaims plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // keep the stored names current (players can rename)
        for (Claim c : plugin.claims().ownedBy(p.getUniqueId())) {
            if (!c.ownerName().equals(p.getName())) {
                c.ownerName(p.getName());
                plugin.claims().save(c);
            }
        }
        for (Claim c : plugin.claims().memberClaims(p.getUniqueId())) {
            ClaimMember m = c.member(p.getUniqueId());
            if (m != null && !m.name().equals(p.getName())) {
                m.name(p.getName());
                plugin.claims().save(c);
            }
        }
        plugin.cabin().restoreOnJoin(p);
        var ps = plugin.players().get(p.getUniqueId());
        if (!plugin.players().known(p.getUniqueId()) || ps.language() == null) {
            plugin.players().save(ps);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.protection().forget(e.getPlayer().getUniqueId());
        plugin.borders().hide(e.getPlayer());
    }

    @EventHandler
    public void onMapInit(MapInitializeEvent e) {
        plugin.maps().attach(e.getMap());
    }
}
