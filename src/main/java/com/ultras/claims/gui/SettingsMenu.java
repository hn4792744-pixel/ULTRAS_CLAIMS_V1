package com.ultras.claims.gui;

import com.ultras.claims.claim.Claim;
import com.ultras.claims.claim.ProtectionKey;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Visitor protections. ON = visitors are blocked. Also used (with a chunk label) from the map. */
final class SettingsMenu extends PagedMenu<ProtectionKey> {
    private final String chunkLabel;

    SettingsMenu(GuiService gui, Player viewer, Claim claim, String chunkLabel) {
        super(gui, viewer);
        this.claim = claim;
        this.chunkLabel = chunkLabel;
    }

    @Override
    protected Component menuTitle() {
        return chunkLabel == null ? gui.text(lang, "gui.title-settings", "id", claim.id())
                : gui.text(lang, "gui.title-chunk-settings", "chunk", chunkLabel);
    }

    @Override
    protected List<ProtectionKey> all() {
        return Arrays.asList(ProtectionKey.values());
    }

    @Override
    protected List<String> filterIds() {
        List<String> ids = new ArrayList<>();
        ids.add("all");
        for (ProtectionKey.Category c : ProtectionKey.Category.values()) {
            ids.add(c.name().toLowerCase(Locale.ROOT));
        }
        return ids;
    }

    @Override
    protected boolean matchesFilter(ProtectionKey k, String f) {
        return f.equals("all") || k.category().name().equalsIgnoreCase(f);
    }

    @Override
    protected boolean matchesSearch(ProtectionKey k, String q) {
        return plugin.messages().plain(lang, "gui.flag." + k.id()).toLowerCase(Locale.ROOT).contains(q) || k.id().contains(q);
    }

    @Override
    protected ItemStack render(ProtectionKey k) {
        boolean on = !claim.visitorsAllowed(k);
        List<Component> lore = new ArrayList<>(gui.lore(lang, "gui.flag." + k.id() + "-lore"));
        lore.add(Component.empty());
        lore.add(gui.text(lang, on ? "gui.flag-blocked" : "gui.flag-allowed"));
        if (gui.canConfigure(viewer, claim)) {
            lore.add(gui.text(lang, "gui.click-to-toggle"));
        }
        return gui.icon(on ? "toggle-on" : "toggle-off", gui.text(lang, "gui.flag." + k.id()), lore);
    }

    @Override
    protected void onClick(ProtectionKey k, ClickType click) {
        if (!gui.canConfigure(viewer, claim)) {
            plugin.messages().error(viewer, "gui-no-permission");
            return;
        }
        boolean on = !claim.visitorsAllowed(k);
        claim.settings().put(k, !on);
        plugin.claims().save(claim);
        plugin.log().log("SETTING_CHANGED", "claim=" + claim.id() + " by=" + viewer.getName() + " " + k.id() + "=" + !on);
        plugin.sounds().play(viewer, com.ultras.claims.sound.SoundKey.TOGGLE);
        refresh();
    }
}
