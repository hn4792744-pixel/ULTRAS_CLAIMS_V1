package com.ultras.claims.gui;

import com.ultras.claims.UltrasClaims;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.function.Consumer;

/**
 * Routes menu clicks and closes every item-theft route: every click, drag, shift-click, number key, double click and
 * creative pick on a menu is cancelled before anything can move. Only deliberate handlers (cabin deposit) act.
 */
public final class MenuListener implements Listener {
    private final UltrasClaims plugin;

    public MenuListener(UltrasClaims plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu menu)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || !p.equals(menu.viewer())) {
            return;
        }
        if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR || e.getClick() == ClickType.DOUBLE_CLICK) {
            return;
        }
        if (e.getClickedInventory() == null) {
            return;
        }
        if (e.getClickedInventory().equals(e.getView().getTopInventory())) {
            if (e.getCursor() != null && !e.getCursor().getType().isAir()) {
                menu.clickWithCursor(e);
            } else {
                menu.click(e);
            }
        } else {
            menu.clickBottom(e);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCreative(InventoryCreativeEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof Menu m) {
            m.onClose();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.gui().forget(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (!plugin.gui().hasPrompt(p.getUniqueId())) {
            return;
        }
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Consumer<String> cb = plugin.gui().takePrompt(p.getUniqueId());
        if (cb == null) {
            return;
        }
        boolean cancel = text.equalsIgnoreCase("cancel") || text.equals("إلغاء") || text.equals("الغاء");
        Bukkit.getScheduler().runTask(plugin, () -> cb.accept(cancel ? null : text.length() > 32 ? text.substring(0, 32) : text));
    }
}
