package com.ultras.claims.gui;

import com.ultras.claims.claim.Claim;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

/** Claims the viewer is a member of; click to leave. */
final class MembershipMenu extends PagedMenu<Claim> {
    MembershipMenu(GuiService gui, Player viewer) {
        super(gui, viewer);
    }

    @Override
    protected Component menuTitle() {
        return gui.text(lang, "gui.title-membership", "count", plugin.claims().memberOfCount(viewer.getUniqueId()),
                "max", plugin.getConfig().getInt("limits.max-claims-member-of", 4));
    }

    @Override
    protected List<Claim> all() {
        return plugin.claims().memberClaims(viewer.getUniqueId());
    }

    @Override
    protected boolean matchesSearch(Claim c, String q) {
        return c.ownerName().toLowerCase(Locale.ROOT).contains(q) || c.id().toLowerCase(Locale.ROOT).contains(q);
    }

    @Override
    protected ItemStack render(Claim c) {
        return gui.icon("membership", gui.text(lang, "gui.membership-claim", "id", c.id(), "owner", c.ownerName()),
                plugin.messages().guiLore(lang, "gui.membership-claim-lore", "chunks", c.chunks().size(), "world", c.world()));
    }

    @Override
    protected void onClick(Claim c, ClickType click) {
        gui.openConfirm(viewer, gui.text(lang, "gui.title-leave", "id", c.id()), gui.lore(lang, "gui.leave-lore", "owner", c.ownerName()), this, () -> {
            if (plugin.members().remove(viewer, c, viewer.getUniqueId())) {
                plugin.messages().info(viewer, "member-left", "owner", c.ownerName());
            }
            new MembershipMenu(gui, viewer).back(back).open();
        });
    }
}
