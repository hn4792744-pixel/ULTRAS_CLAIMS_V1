package com.ultras.claims.gui;

import com.ultras.claims.claim.Claim;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** Claim management: Border, Members, Settings, Cabin, Map, Notifications, Info, Close. */
final class ClaimMenu extends Menu {
    ClaimMenu(GuiService gui, Player viewer, Claim claim) {
        super(gui, viewer);
        this.claim = claim;
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected Component title() {
        return gui.text(lang, "gui.title-claim", "id", claim.id());
    }

    private boolean configure() {
        return gui.canConfigure(viewer, claim);
    }

    @Override
    protected void build() {
        set(gui.slot("main", "border", 10), gui.button("border", lang), c -> {
            viewer.closeInventory();
            plugin.borders().show(viewer, claim);
            plugin.messages().info(viewer, "border-shown", "seconds", plugin.getConfig().getLong("border.display-time", 30));
        });
        set(gui.slot("main", "members", 11), gui.button("members", lang), c -> gui.openMembers(viewer, claim, this));
        set(gui.slot("main", "settings", 12), gui.button("settings", lang), c -> {
            if (!configure()) {
                plugin.messages().error(viewer, "gui-no-permission");
                return;
            }
            gui.openSettings(viewer, claim, null, this);
        });
        if (plugin.cabin().enabled()) {
            set(gui.slot("main", "cabin", 13), gui.button("cabin", lang), c -> {
                if (!plugin.cabin().canUse(viewer, claim)) {
                    plugin.messages().error(viewer, "gui-no-permission");
                    return;
                }
                gui.openCabin(viewer, claim, this);
            });
        } else {
            set(gui.slot("main", "cabin", 13), gui.button("cabin-disabled", lang), c -> plugin.messages().error(viewer, "cabin-disabled"));
        }
        set(gui.slot("main", "map", 14), gui.button("map", lang), c -> gui.openMap(viewer, this));
        set(gui.slot("main", "notifications", 15), gui.button("notifications", lang), c -> {
            if (!configure()) {
                plugin.messages().error(viewer, "gui-no-permission");
                return;
            }
            gui.openNotifications(viewer, claim, this);
        });
        set(gui.slot("main", "info", 16), gui.button("info", lang), c -> gui.infoMenu(viewer, claim, this).open());
        closeButton(gui.slot("main", "close", 22));
    }
}
