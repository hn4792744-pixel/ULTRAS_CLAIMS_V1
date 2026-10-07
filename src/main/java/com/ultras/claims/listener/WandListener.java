package com.ultras.claims.listener;

import com.ultras.claims.UltrasClaims;
import com.ultras.claims.claim.Claim;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** The claim wand shows the borders of every nearby claim; the claim map item opens the map menu on use. */
public final class WandListener implements Listener {
    private final UltrasClaims plugin;

    public WandListener(UltrasClaims plugin) {
        this.plugin = plugin;
    }

    public boolean isWand(ItemStack item) {
        return item != null && item.getType() == Material.STICK && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(plugin.keys().wand, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        if (isWand(item)) {
            e.setCancelled(true);
            int radius = plugin.getConfig().getInt("wand.radius-chunks", 3);
            int cx = p.getLocation().getBlockX() >> 4;
            int cz = p.getLocation().getBlockZ() >> 4;
            List<Claim> near = new ArrayList<>();
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    Claim c = plugin.claims().at(p.getWorld().getName(), cx + x, cz + z);
                    if (c != null && !near.contains(c)) {
                        near.add(c);
                    }
                }
            }
            if (near.isEmpty()) {
                plugin.messages().info(p, "wand-none");
                return;
            }
            plugin.borders().showArea(p, near);
            plugin.messages().info(p, "wand-shown", "count", near.size());
        } else if (plugin.maps().isClaimMap(item)) {
            // the map itself keeps drawing; sneaking opens the chunk map menu
            if (p.isSneaking()) {
                e.setCancelled(true);
                plugin.gui().openMap(p, null);
            }
        }
    }
}
