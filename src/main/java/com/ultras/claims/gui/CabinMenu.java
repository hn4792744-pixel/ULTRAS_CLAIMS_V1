package com.ultras.claims.gui;

import com.ultras.claims.claim.Claim;
import com.ultras.claims.claim.ClaimState;
import com.ultras.claims.core.CabinMath;
import com.ultras.claims.core.TimeFormat;
import com.ultras.claims.sound.SoundKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The cabin. Items are handed in by clicking them in the player's own inventory (or dropping the cursor stack on the
 * menu); they become a server-side deposit, never a real item inside the menu. Click a deposit to take it back.
 */
final class CabinMenu extends Menu {
    private static final int[] DEPOSIT_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    CabinMenu(GuiService gui, Player viewer, Claim claim) {
        super(gui, viewer);
        this.claim = claim;
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected Component title() {
        return gui.text(lang, "gui.title-cabin", "id", claim.id());
    }

    @Override
    public boolean live() {
        return true;
    }

    @Override
    protected void build() {
        boolean ar = lang.equals("ar");
        var cabin = plugin.cabin();
        List<Component> lore = new ArrayList<>();
        String state = plugin.messages().plain(lang, claim.state() == ClaimState.GRACE ? "gui.claim-state-grace" : "gui.claim-state-active");
        lore.addAll(plugin.messages().guiLore(lang, "gui.cabin-info-lore", "remaining", TimeFormat.format(cabin.remainingSeconds(claim), ar),
                "max", TimeFormat.format(cabin.maxSeconds(), ar), "state", state));
        int shown = 0;
        for (Map.Entry<String, CabinMath.Rule> e : cabin.rules().entrySet()) {
            if (shown++ >= 14) {
                lore.add(gui.text(lang, "gui.cabin-more", "count", cabin.rules().size() - 14));
                break;
            }
            lore.add(gui.text(lang, "gui.cabin-rule", "item", pretty(e.getKey()), "minimum", e.getValue().minimum(),
                    "time", TimeFormat.format(e.getValue().seconds(), ar)));
        }
        set(4, gui.icon("cabin", gui.text(lang, "gui.cabin-info"), lore));

        Map<String, Integer> dep = cabin.depositsOf(claim, viewer.getUniqueId());
        int i = 0;
        for (Map.Entry<String, Integer> e : dep.entrySet()) {
            if (i >= DEPOSIT_SLOTS.length) {
                break;
            }
            Material m = Material.matchMaterial(e.getKey());
            if (m == null) {
                continue;
            }
            ItemStack it = new ItemStack(m, Math.max(1, Math.min(64, e.getValue())));
            ItemMeta meta = it.getItemMeta();
            meta.displayName(gui.text(lang, "gui.cabin-deposit", "item", pretty(e.getKey()), "amount", e.getValue()));
            meta.lore(gui.lore(lang, "gui.cabin-deposit-lore"));
            it.setItemMeta(meta);
            String mat = e.getKey();
            set(DEPOSIT_SLOTS[i++], it, c -> {
                int n = cabin.withdraw(viewer, claim, mat);
                if (n > 0) {
                    plugin.messages().info(viewer, "cabin-withdrawn", "amount", n, "item", pretty(mat));
                }
                refresh();
            });
        }
        if (dep.isEmpty()) {
            set(22, gui.icon("deposit", gui.text(lang, "gui.cabin-empty"), gui.lore(lang, "gui.cabin-empty-lore")));
        }
        set(40, gui.icon("clock", gui.text(lang, "gui.cabin-time", "remaining", TimeFormat.format(cabin.remainingSeconds(claim), ar)), gui.lore(lang, "gui.cabin-time-lore")));
        if (cabin.requireConfirmation() && !dep.isEmpty()) {
            set(42, gui.button("confirm", lang), c -> {
                cabin.process(claim, viewer.getUniqueId(), true);
                refresh();
            });
        }
        if (plugin.hasBypass(viewer) && plugin.getConfig().getBoolean("renew.enabled", true)) {
            long minutes = plugin.getConfig().getLong("renew.manual-time", 60);
            set(38, gui.button("renew", lang, "time", TimeFormat.format(minutes * 60, ar)), c -> {
                cabin.renewManual(viewer, claim, minutes * 60);
                refresh();
            });
        }
        backButton(49);
    }

    static String pretty(String material) {
        String s = material.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override
    public void clickBottom(InventoryClickEvent e) {
        deposit(e.getCurrentItem(), () -> e.setCurrentItem(null));
    }

    @Override
    public void clickWithCursor(InventoryClickEvent e) {
        deposit(e.getCursor(), () -> viewer.setItemOnCursor(null));
    }

    private void deposit(ItemStack stack, Runnable remove) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        var cabin = plugin.cabin();
        if (!cabin.accepts(stack)) {
            plugin.messages().error(viewer, "cabin-not-accepted");
            plugin.sounds().play(viewer, SoundKey.ERROR);
            return;
        }
        Material m = stack.getType();
        int amount = stack.getAmount();
        if (!cabin.credit(viewer, claim, m, amount)) {
            plugin.messages().error(viewer, "gui-no-permission");
            return;
        }
        remove.run();
        plugin.sounds().play(viewer, SoundKey.SUCCESS);
        plugin.messages().info(viewer, "cabin-deposited", "amount", amount, "item", pretty(m.name()), "delay", plugin.getConfig().getLong("cabin.process-delay-seconds", 10));
        refresh();
    }
}
