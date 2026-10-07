package com.ultras.claims.gui;

import com.ultras.claims.UltrasClaims;
import com.ultras.claims.claim.Claim;
import com.ultras.claims.sound.SoundKey;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Base of every menu. The inventory holder is the menu itself, so a click is only ever treated as a menu click when
 * the holder is one of ours. Everything is cancelled by default - nothing can be taken out of or put into a menu.
 */
public abstract class Menu implements InventoryHolder {
    protected final UltrasClaims plugin;
    protected final GuiService gui;
    protected final Player viewer;
    protected final String lang;
    /** The claim this menu is about (null for personal menus); the menu closes when it disappears. */
    protected Claim claim;
    protected Supplier<Menu> back;
    private Inventory inv;
    private final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();

    protected Menu(GuiService gui, Player viewer) {
        this.plugin = gui.plugin();
        this.gui = gui;
        this.viewer = viewer;
        this.lang = plugin.messages().languageOf(viewer);
    }

    protected abstract int rows();

    protected abstract Component title();

    protected abstract void build();

    /** True when the content changes with time (remaining time) and should be redrawn every second. */
    public boolean live() {
        return false;
    }

    public Menu back(Supplier<Menu> previous) {
        this.back = previous;
        return this;
    }

    public Player viewer() {
        return viewer;
    }

    public Claim claim() {
        return claim;
    }

    public void open() {
        inv = Bukkit.createInventory(this, rows() * 9, title());
        refresh();
        viewer.openInventory(inv);
        plugin.sounds().play(viewer, SoundKey.GUI_OPEN);
    }

    public void refresh() {
        if (inv == null) {
            return;
        }
        inv.clear();
        actions.clear();
        ItemStack filler = gui.filler();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
        build();
    }

    protected void set(int slot, ItemStack item, Consumer<ClickType> action) {
        if (slot < 0 || slot >= inv.getSize()) {
            return;
        }
        inv.setItem(slot, item);
        if (action != null) {
            actions.put(slot, action);
        }
    }

    protected void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    protected void backButton(int slot) {
        set(slot, gui.button("back", lang), t -> {
            if (back != null) {
                back.get().open();
            } else {
                viewer.closeInventory();
            }
        });
    }

    protected void closeButton(int slot) {
        set(slot, gui.button("close", lang), t -> viewer.closeInventory());
    }

    /** Click inside the menu's own (top) inventory. */
    public void click(InventoryClickEvent e) {
        if (claim != null && plugin.claims().get(claim.id()) != claim) {
            viewer.closeInventory();
            plugin.messages().error(viewer, "claim-gone");
            return;
        }
        Consumer<ClickType> a = actions.get(e.getSlot());
        if (a != null) {
            plugin.sounds().play(viewer, SoundKey.GUI_CLICK);
            a.accept(e.getClick());
        }
    }

    /** Click in the player's own inventory while the menu is open (only the cabin uses this). */
    public void clickBottom(InventoryClickEvent e) {
    }

    /** Cursor item dropped on the menu (only the cabin uses this). */
    public void clickWithCursor(InventoryClickEvent e) {
    }

    public void onClose() {
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
