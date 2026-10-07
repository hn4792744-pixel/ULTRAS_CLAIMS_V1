package com.ultras.claims.gui;

import com.ultras.claims.claim.Claim;
import com.ultras.claims.claim.ClaimState;
import com.ultras.claims.core.TimeFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;

/** Read-only summary of a claim. */
final class InfoMenu extends Menu {
    InfoMenu(GuiService gui, Player viewer, Claim claim) {
        super(gui, viewer);
        this.claim = claim;
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected Component title() {
        return gui.text(lang, "gui.title-info", "id", claim.id());
    }

    @Override
    public boolean live() {
        return true;
    }

    static String date(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(millis));
    }

    @Override
    protected void build() {
        boolean ar = lang.equals("ar");
        String remaining = !plugin.cabin().enabled() ? "∞" : TimeFormat.format(plugin.cabin().remainingSeconds(claim), ar);
        String state = plugin.messages().plain(lang, claim.state() == ClaimState.GRACE ? "gui.claim-state-grace" : "gui.claim-state-active");
        set(13, gui.icon("info", gui.text(lang, "gui.info-name", "id", claim.id()), plugin.messages().guiLore(lang, "gui.info-lore",
                "id", claim.id(), "owner", claim.ownerName(), "name", claim.displayName(), "chunks", claim.chunks().size(),
                "expansions", claim.expansions(), "max", plugin.expansion().max(), "members", claim.members().size(),
                "maxmembers", plugin.getConfig().getInt("limits.max-members-per-claim", 4), "state", state, "remaining", remaining,
                "created", date(claim.createdAt()), "renewed", date(claim.lastRenewalAt()), "world", claim.world(),
                "x", claim.headX(), "y", claim.headY(), "z", claim.headZ())));
        backButton(22);
    }
}
