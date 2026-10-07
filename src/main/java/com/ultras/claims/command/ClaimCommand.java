package com.ultras.claims.command;

import com.ultras.claims.UltrasClaims;
import com.ultras.claims.claim.Claim;
import com.ultras.claims.claim.MemberPermission;
import com.ultras.claims.core.TimeFormat;
import com.ultras.claims.language.MessageChannel;
import com.ultras.claims.sound.SoundKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** /claim and its sub-commands. Admin sub-commands are neither executable nor suggested without permission. */
public final class ClaimCommand implements CommandExecutor, TabCompleter {
    private final UltrasClaims plugin;

    public ClaimCommand(UltrasClaims plugin) {
        this.plugin = plugin;
    }

    private boolean admin(CommandSender s) {
        return s.hasPermission("ultrasclaims.admin");
    }

    private String lang(CommandSender s) {
        return s instanceof Player p ? plugin.messages().languageOf(p) : plugin.defaultLanguage();
    }

    private void say(CommandSender s, String key, Object... ph) {
        if (s instanceof Player p) {
            plugin.messages().send(p, key, MessageChannel.ALWAYS, ph);
        } else {
            plugin.messages().send(s, lang(s), key, ph);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p) {
                giveHead(p);
            } else {
                say(sender, "players-only");
            }
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> list(sender, args);
            case "tp" -> tp(sender, args);
            case "info" -> info(sender, args);
            case "wand" -> wand(sender);
            case "map" -> map(sender);
            case "reload" -> {
                if (!admin(sender)) {
                    return denied(sender);
                }
                plugin.reloadAll();
                say(sender, "reloaded");
            }
            case "admin" -> {
                if (!admin(sender)) {
                    return denied(sender);
                }
                adminCommand(sender, args);
            }
            default -> say(sender, "usage", "usage", "/claim [list|tp|info|wand|map" + (admin(sender) ? "|admin|reload" : "") + "]");
        }
        return true;
    }

    private boolean denied(CommandSender s) {
        say(s, "no-permission");
        return true;
    }

    // ------------------------------------------------------------------ player commands

    private void giveHead(Player p) {
        var ps = plugin.players().get(p.getUniqueId());
        int max = plugin.getConfig().getInt("limits.max-heads", 2);
        if (max <= 0) {
            plugin.messages().error(p, "head-cannot-obtain");
            plugin.sounds().play(p, SoundKey.ERROR);
            return;
        }
        if (ps.headsIssued() >= max) {
            plugin.messages().error(p, "head-limit", "max", max);
            plugin.sounds().play(p, SoundKey.ERROR);
            return;
        }
        ps.headsIssued(ps.headsIssued() + 1);
        plugin.players().save(ps);
        plugin.heads().give(p, plugin.heads().create(p.getUniqueId(), p.getName(), false, plugin.messages().languageOf(p)));
        plugin.log().log("HEAD_ISSUED", "player=" + p.getName() + " count=" + ps.headsIssued() + "/" + max);
        plugin.messages().send(p, "head-given", MessageChannel.ALWAYS, "count", ps.headsIssued(), "max", max);
        plugin.sounds().play(p, SoundKey.SUCCESS);
    }

    private void list(CommandSender sender, String[] args) {
        boolean all = args.length > 1 && args[1].equalsIgnoreCase("all") && sender.hasPermission("ultrasclaims.admin.list");
        List<Claim> claims;
        if (all) {
            claims = new ArrayList<>(plugin.claims().all());
        } else if (sender instanceof Player p) {
            claims = new ArrayList<>(plugin.claims().ownedBy(p.getUniqueId()));
            claims.addAll(plugin.claims().memberClaims(p.getUniqueId()));
        } else {
            say(sender, "players-only");
            return;
        }
        if (claims.isEmpty()) {
            say(sender, "list-empty");
            return;
        }
        say(sender, "list-header", "count", claims.size());
        int shown = 0;
        for (Claim c : claims) {
            if (shown++ >= 50) {
                say(sender, "list-more", "count", claims.size() - 50);
                break;
            }
            boolean ar = lang(sender).equals("ar");
            String time = plugin.cabin().enabled() ? TimeFormat.format(plugin.cabin().remainingSeconds(c), ar) : "∞";
            say(sender, "list-entry", "id", c.id(), "owner", c.ownerName(), "chunks", c.chunks().size(), "world", c.world(),
                    "x", c.headX(), "y", c.headY(), "z", c.headZ(), "time", time);
        }
    }

    private void tp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            say(sender, "players-only");
            return;
        }
        if (args.length < 2) {
            say(sender, "usage", "usage", "/claim tp <id>");
            return;
        }
        Claim c = plugin.claims().get(args[1]);
        if (c == null) {
            say(sender, "claim-not-found", "id", args[1].toUpperCase(Locale.ROOT));
            return;
        }
        boolean allowed = p.hasPermission("ultrasclaims.admin.tp") || c.isOwner(p.getUniqueId())
                || c.hasPermission(p.getUniqueId(), MemberPermission.TELEPORT);
        if (!allowed) {
            say(sender, "tp-denied");
            return;
        }
        World w = Bukkit.getWorld(c.world());
        if (w == null) {
            say(sender, "claim-gone");
            return;
        }
        Location to = new Location(w, c.headX() + 0.5, c.headY() + 1, c.headZ() + 0.5, p.getLocation().getYaw(), p.getLocation().getPitch());
        p.teleportAsync(to).thenAccept(ok -> {
            if (ok) {
                plugin.messages().send(p, "tp-done", MessageChannel.ALWAYS, "id", c.id());
            }
        });
    }

    private void info(CommandSender sender, String[] args) {
        Claim c;
        if (args.length > 1) {
            c = plugin.claims().get(args[1]);
        } else if (sender instanceof Player p) {
            c = plugin.claims().at(p.getLocation());
        } else {
            say(sender, "usage", "usage", "/claim info <id>");
            return;
        }
        if (c == null) {
            say(sender, "claim-not-found-here");
            return;
        }
        if (sender instanceof Player p && !(plugin.hasBypass(p) || c.isOwner(p.getUniqueId()) || c.isMember(p.getUniqueId()))) {
            say(sender, "info-visitor", "owner", c.ownerName());
            return;
        }
        boolean ar = lang(sender).equals("ar");
        String time = plugin.cabin().enabled() ? TimeFormat.format(plugin.cabin().remainingSeconds(c), ar) : "∞";
        say(sender, "info-lines", "id", c.id(), "owner", c.ownerName(), "chunks", c.chunks().size(), "expansions", c.expansions(),
                "max", plugin.expansion().max(), "members", c.members().size(),
                "maxmembers", plugin.getConfig().getInt("limits.max-members-per-claim", 4), "time", time,
                "state", plugin.messages().plain(lang(sender), "gui.claim-state-" + c.state().name().toLowerCase(Locale.ROOT)));
    }

    private void wand(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            say(sender, "players-only");
            return;
        }
        ItemStack wand = new ItemStack(Material.STICK);
        ItemMeta m = wand.getItemMeta();
        String l = plugin.messages().languageOf(p);
        m.displayName(plugin.messages().gui(l, "item-wand-name"));
        m.lore(plugin.messages().guiLore(l, "item-wand-lore"));
        m.getPersistentDataContainer().set(plugin.keys().wand, PersistentDataType.BYTE, (byte) 1);
        wand.setItemMeta(m);
        plugin.heads().give(p, wand);
        say(sender, "wand-given");
    }

    private void map(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            say(sender, "players-only");
            return;
        }
        plugin.heads().give(p, plugin.maps().create(p));
        say(sender, "map-given");
    }

    // ------------------------------------------------------------------ admin

    private void adminCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            say(sender, "usage", "usage", "/claim admin <add|remove|reset|renew|list>");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.length < 3) {
                    say(sender, "usage", "usage", "/claim admin add <player>");
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    say(sender, "player-offline", "player", args[2]);
                    return;
                }
                plugin.heads().give(target, plugin.heads().create(target.getUniqueId(), target.getName(), true, plugin.messages().languageOf(target)));
                plugin.log().log("HEAD_ADMIN_ADDED", "admin=" + sender.getName() + " player=" + target.getName());
                say(sender, "admin-head-given", "player", target.getName());
                plugin.messages().send(target, "head-given-admin", MessageChannel.ALWAYS);
            }
            case "remove" -> {
                Claim c = args.length < 3 ? null : plugin.claims().get(args[2]);
                if (c == null) {
                    say(sender, "claim-not-found", "id", args.length < 3 ? "?" : args[2].toUpperCase(Locale.ROOT));
                    return;
                }
                plugin.log().log("CLAIM_REMOVED_ADMIN", "claim=" + c.id() + " owner=" + c.ownerName() + " admin=" + sender.getName());
                plugin.cabin().removeClaimFully(c, true);
                say(sender, "admin-removed", "id", c.id());
            }
            case "reset" -> {
                if (args.length < 3) {
                    say(sender, "usage", "usage", "/claim admin reset <player>");
                    return;
                }
                OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(args[2]);
                if (op == null) {
                    say(sender, "player-offline", "player", args[2]);
                    return;
                }
                var ps = plugin.players().get(op.getUniqueId());
                ps.headsIssued(0);
                plugin.players().save(ps);
                say(sender, "admin-reset", "player", String.valueOf(op.getName()));
            }
            case "renew" -> {
                Claim c = args.length < 3 ? null : plugin.claims().get(args[2]);
                if (c == null || !(sender instanceof Player p)) {
                    say(sender, c == null ? "claim-not-found" : "players-only", "id", args.length < 3 ? "?" : args[2].toUpperCase(Locale.ROOT));
                    return;
                }
                if (!plugin.cabin().enabled() || !plugin.getConfig().getBoolean("renew.enabled", true)) {
                    say(sender, "cabin-disabled");
                    return;
                }
                long minutes = plugin.getConfig().getLong("renew.manual-time", 60);
                if (args.length > 3) {
                    try {
                        minutes = Math.max(1, Long.parseLong(args[3]));
                    } catch (NumberFormatException ignored) {
                        say(sender, "usage", "usage", "/claim admin renew <id> [minutes]");
                        return;
                    }
                }
                plugin.cabin().renewManual(p, c, minutes * 60);
            }
            case "list" -> list(sender, new String[]{"list", "all"});
            default -> say(sender, "usage", "usage", "/claim admin <add|remove|reset|renew|list>");
        }
    }

    // ------------------------------------------------------------------ tab completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("list", "tp", "info", "wand", "map"));
            if (admin(sender)) {
                out.add("admin");
                out.add("reload");
            }
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "tp", "info" -> out.addAll(visibleIds(sender));
                case "list" -> {
                    if (sender.hasPermission("ultrasclaims.admin.list")) {
                        out.add("all");
                    }
                }
                case "admin" -> {
                    if (admin(sender)) {
                        out.addAll(List.of("add", "remove", "reset", "renew", "list"));
                    }
                }
                default -> {
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && admin(sender)) {
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "add", "reset" -> Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                case "remove", "renew" -> plugin.claims().all().forEach(c -> out.add(c.id()));
                default -> {
                }
            }
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().collect(Collectors.toList());
    }

    private List<String> visibleIds(CommandSender sender) {
        List<String> ids = new ArrayList<>();
        if (sender instanceof Player p) {
            if (p.hasPermission("ultrasclaims.admin.tp")) {
                plugin.claims().all().forEach(c -> ids.add(c.id()));
            } else {
                plugin.claims().ownedBy(p.getUniqueId()).forEach(c -> ids.add(c.id()));
                plugin.claims().memberClaims(p.getUniqueId()).forEach(c -> ids.add(c.id()));
            }
        }
        return ids;
    }
}
