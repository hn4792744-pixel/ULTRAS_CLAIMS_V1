package com.ultras.claims.claim;

import com.ultras.claims.UltrasClaims;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

/** Creates and recognises claim head items. A head carries only its owner - never any claim data. */
public final class HeadService {
    private final UltrasClaims plugin;

    public HeadService(UltrasClaims plugin) {
        this.plugin = plugin;
    }

    public ItemStack create(UUID owner, String ownerName, boolean adminIssued, String lang) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        OfflinePlayer op = Bukkit.getOfflinePlayer(owner);
        meta.setOwningPlayer(op);
        meta.displayName(plugin.messages().gui(lang, "item-head-name"));
        List<Component> lore = plugin.messages().guiLore(lang, "item-head-lore", "owner", ownerName);
        meta.lore(lore);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(plugin.keys().headOwner, PersistentDataType.STRING, owner.toString());
        pdc.set(plugin.keys().headToken, PersistentDataType.STRING, UUID.randomUUID().toString());
        if (adminIssued) {
            pdc.set(plugin.keys().headAdmin, PersistentDataType.BYTE, (byte) 1);
        }
        meta.setMaxStackSize(1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isHead(ItemStack item) {
        return ownerOf(item) != null;
    }

    public UUID ownerOf(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD || !item.hasItemMeta()) {
            return null;
        }
        String s = item.getItemMeta().getPersistentDataContainer().get(plugin.keys().headOwner, PersistentDataType.STRING);
        if (s == null) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String tokenOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(plugin.keys().headToken, PersistentDataType.STRING);
    }

    public boolean adminIssued(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta m = item.getItemMeta();
        return m.getPersistentDataContainer().has(plugin.keys().headAdmin, PersistentDataType.BYTE);
    }

    /** Puts the item in the inventory, or drops it at the player's feet when full. */
    public void give(Player p, ItemStack item) {
        var left = p.getInventory().addItem(item);
        for (ItemStack rest : left.values()) {
            dropFor(p.getWorld(), p.getLocation(), rest, p.getUniqueId());
        }
    }

    /** Drops a head so that only its owner can pick it up and fire/lava/cactus cannot destroy it. */
    public void dropFor(org.bukkit.World world, org.bukkit.Location at, ItemStack item, UUID owner) {
        org.bukkit.entity.Item drop = world.dropItem(at, item);
        drop.setOwner(owner);
        drop.setInvulnerable(true);
    }
}
