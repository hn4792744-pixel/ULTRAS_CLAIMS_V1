package com.ultras.claims.gui;

import com.ultras.claims.claim.Claim;
import com.ultras.claims.claim.ClaimMember;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Members of this one claim only. Left click: permissions. Right click: remove. */
final class MembersMenu extends PagedMenu<ClaimMember> {
    MembersMenu(GuiService gui, Player viewer, Claim claim) {
        super(gui, viewer);
        this.claim = claim;
    }

    @Override
    protected Component menuTitle() {
        return gui.text(lang, "gui.title-members", "count", claim.members().size(), "max", max());
    }

    private int max() {
        return plugin.getConfig().getInt("limits.max-members-per-claim", 4);
    }

    @Override
    protected List<ClaimMember> all() {
        return new ArrayList<>(claim.members().values());
    }

    @Override
    protected List<String> filterIds() {
        return List.of("all", "online", "offline");
    }

    @Override
    protected boolean matchesFilter(ClaimMember m, String f) {
        boolean online = Bukkit.getPlayer(m.uuid()) != null;
        return f.equals("all") || (f.equals("online") == online);
    }

    @Override
    protected boolean matchesSearch(ClaimMember m, String q) {
        return m.name().toLowerCase(Locale.ROOT).contains(q);
    }

    @Override
    protected ItemStack render(ClaimMember m) {
        boolean online = Bukkit.getPlayer(m.uuid()) != null;
        return gui.head(Bukkit.getOfflinePlayer(m.uuid()), Component.text(m.name()).decorationIfAbsent(net.kyori.adventure.text.format.TextDecoration.ITALIC,
                net.kyori.adventure.text.format.TextDecoration.State.FALSE),
                plugin.messages().guiLore(lang, "gui.member-lore", "status", plugin.messages().plain(lang, online ? "gui.online" : "gui.offline"),
                        "permissions", m.permissions().size(), "since", InfoMenu.date(m.addedAt())));
    }

    @Override
    protected void onClick(ClaimMember m, ClickType click) {
        if (click.isRightClick()) {
            if (!plugin.members().canManage(viewer, claim)) {
                plugin.messages().error(viewer, "gui-no-permission");
                return;
            }
            gui.openConfirm(viewer, gui.text(lang, "gui.title-remove-member", "player", m.name()), gui.lore(lang, "gui.remove-member-lore", "player", m.name()), this, () -> {
                if (plugin.members().remove(viewer, claim, m.uuid())) {
                    plugin.messages().info(viewer, "member-removed", "player", m.name());
                    new MembersMenu(gui, viewer, claim).back(back).open();
                } else {
                    plugin.messages().error(viewer, "gui-no-permission");
                }
            });
        } else {
            gui.openMemberPermissions(viewer, claim, m.uuid(), this);
        }
    }

    @Override
    protected void footer() {
        if (plugin.members().canManage(viewer, claim)) {
            extra(50, gui.button("add-member", lang), c -> gui.openPicker(viewer, claim, this));
        }
    }
}
