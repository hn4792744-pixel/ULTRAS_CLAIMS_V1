package com.ultras.claims.gui;

import com.ultras.claims.claim.Claim;
import com.ultras.claims.member.MemberService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Pick a player (online or known offline) to add as a member. */
final class PlayerPickerMenu extends PagedMenu<OfflinePlayer> {
    private final List<OfflinePlayer> candidates = new ArrayList<>();

    PlayerPickerMenu(GuiService gui, Player viewer, Claim claim) {
        super(gui, viewer);
        this.claim = claim;
        Set<UUID> seen = new HashSet<>();
        seen.add(claim.ownerUuid());
        seen.addAll(claim.members().keySet());
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (seen.add(p.getUniqueId())) {
                candidates.add(p);
            }
        }
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() != null && seen.add(op.getUniqueId())) {
                candidates.add(op);
            }
        }
        candidates.sort(Comparator.comparing((OfflinePlayer o) -> !o.isOnline()).thenComparing(o -> o.getName().toLowerCase(Locale.ROOT)));
    }

    @Override
    protected Component menuTitle() {
        return gui.text(lang, "gui.title-picker");
    }

    @Override
    protected List<OfflinePlayer> all() {
        return candidates;
    }

    @Override
    protected List<String> filterIds() {
        return List.of("all", "online", "offline");
    }

    @Override
    protected boolean matchesFilter(OfflinePlayer o, String f) {
        return f.equals("all") || (f.equals("online") == o.isOnline());
    }

    @Override
    protected boolean matchesSearch(OfflinePlayer o, String q) {
        return o.getName() != null && o.getName().toLowerCase(Locale.ROOT).contains(q);
    }

    @Override
    protected ItemStack render(OfflinePlayer o) {
        return gui.head(o, Component.text(String.valueOf(o.getName())).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE),
                gui.lore(lang, "gui.picker-lore", "status", plugin.messages().plain(lang, o.isOnline() ? "gui.online" : "gui.offline")));
    }

    @Override
    protected void onClick(OfflinePlayer o, ClickType click) {
        String name = String.valueOf(o.getName());
        MemberService.AddResult r = plugin.members().add(viewer, claim, o.getUniqueId(), name);
        String key = switch (r) {
            case OK -> "member-added";
            case NOT_ALLOWED -> "gui-no-permission";
            case FULL -> "member-full";
            case TARGET_FULL -> "member-target-full";
            case DISABLED_INVITES -> "member-invites-disabled";
            case ALREADY -> "member-already";
            case SELF -> "member-self";
            case OWNER -> "member-owner";
            case UNKNOWN_PLAYER -> "member-unknown";
        };
        if (r == MemberService.AddResult.OK) {
            plugin.messages().info(viewer, key, "player", name, "max", plugin.getConfig().getInt("limits.max-members-per-claim", 4));
            if (back != null) {
                Menu prev = back.get();
                if (prev instanceof MembersMenu) {
                    new MembersMenu(gui, viewer, claim).back(prev.back).open();
                    return;
                }
                prev.open();
            }
        } else {
            plugin.messages().error(viewer, key, "player", name, "max", plugin.getConfig().getInt("limits.max-members-per-claim", 4));
            plugin.sounds().play(viewer, com.ultras.claims.sound.SoundKey.ERROR);
        }
    }
}
