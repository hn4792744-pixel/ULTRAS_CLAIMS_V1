package com.ultras.claims.gui;

import com.ultras.claims.settings.PlayerSettings;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /claim_setting: personal preferences, stored per UUID. */
final class PlayerSettingsMenu extends Menu {
    private final PlayerSettings ps;

    PlayerSettingsMenu(GuiService gui, Player viewer) {
        super(gui, viewer);
        this.ps = plugin.players().get(viewer.getUniqueId());
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected Component title() {
        return gui.text(lang, "gui.title-player-settings");
    }

    private List<Component> state(String key, boolean on) {
        List<Component> l = new ArrayList<>(gui.lore(lang, key));
        l.add(Component.empty());
        l.add(gui.text(lang, on ? "gui.state-on" : "gui.state-off"));
        return l;
    }

    @Override
    protected void build() {
        set(10, gui.icon(ps.claimNotifications() ? "toggle-on" : "toggle-off", gui.text(lang, "gui.ps-notifications"), state("gui.ps-notifications-lore", ps.claimNotifications())), c -> {
            ps.claimNotifications(!ps.claimNotifications());
            plugin.players().save(ps);
            refresh();
        });
        set(11, gui.button("ps-sounds", lang), c -> gui.openSounds(viewer, this));
        set(12, gui.button("ps-language", lang, "language", plugin.messages().languageName(ps.language())), c -> gui.openLanguage(viewer, this));
        set(13, gui.button("ps-messages", lang), c -> gui.openMessages(viewer, this));
        set(14, gui.button("ps-entry", lang), c -> gui.openEntryExit(viewer, this));
        set(15, gui.icon(ps.claimInvitations() ? "toggle-on" : "toggle-off", gui.text(lang, "gui.ps-invitations"), state("gui.ps-invitations-lore", ps.claimInvitations())), c -> {
            ps.claimInvitations(!ps.claimInvitations());
            plugin.players().save(ps);
            refresh();
        });
        set(16, gui.button("ps-membership", lang), c -> gui.openMembership(viewer, this));
        set(21, gui.button("ps-reset", lang), c -> gui.openConfirm(viewer, gui.text(lang, "gui.title-reset"), gui.lore(lang, "gui.reset-lore"), this, () -> {
            plugin.players().reset(viewer.getUniqueId());
            plugin.messages().info(viewer, "settings-reset");
            gui.openPlayerSettings(viewer);
        }));
        closeButton(23);
    }
}
