package com.ultras.claims.border;

import com.ultras.claims.UltrasClaims;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Left and right clicks on the "+" and clean-up of border displays. */
public final class PlusListener implements Listener {
    private final UltrasClaims plugin;

    public PlusListener(UltrasClaims plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClick(PlayerInteractEntityEvent e) {
        Entity target = e.getRightClicked();
        if (target instanceof Interaction && plugin.borders().isPlusEntity(target.getUniqueId())) {
            e.setCancelled(true);
            if (e.getHand() == EquipmentSlot.HAND) {
                plugin.borders().click(e.getPlayer(), target.getUniqueId(), false);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLeftClick(PrePlayerAttackEntityEvent e) {
        Entity target = e.getAttacked();
        if (target instanceof Interaction && plugin.borders().isPlusEntity(target.getUniqueId())) {
            e.setCancelled(true);
            plugin.borders().click(e.getPlayer(), target.getUniqueId(), true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAttackFallback(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Interaction && plugin.borders().isPlusEntity(e.getEntity().getUniqueId())) {
            e.setCancelled(true);
            if (e.getDamager() instanceof Player p) {
                plugin.borders().click(p, e.getEntity().getUniqueId(), true);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.borders().hide(e.getPlayer());
        plugin.borders().forget(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        plugin.borders().hide(e.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        plugin.borders().hide(e.getEntity());
    }
}
