package com.ultras.claims.expansion;

import com.ultras.claims.UltrasClaims;
import com.ultras.claims.claim.Claim;
import com.ultras.claims.claim.ClaimState;
import com.ultras.claims.claim.MemberPermission;
import com.ultras.claims.claim.NotificationKey;
import com.ultras.claims.core.ChunkPos;
import com.ultras.claims.core.Direction;
import com.ultras.claims.economy.ChargeResult;
import com.ultras.claims.language.MessageChannel;
import com.ultras.claims.sound.SoundKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

/**
 * Expansion: checks, payment, commit. A claim can only be expanded by one action at a time and a target chunk can only
 * be reserved once, so two players clicking the same "+" can never be charged twice or overlap.
 */
public final class ExpansionService {
    public enum Problem {
        NONE, NOT_ALLOWED, GRACE, MAX_REACHED, CHUNK_CLAIMED, OUTSIDE_BORDER, BUSY, ECONOMY_UNAVAILABLE, DISABLED_WORLD, GONE
    }

    /** What expanding in a direction would do right now. */
    public record Check(Problem problem, ChunkPos target, Claim blockedBy) {
        public boolean ok() {
            return problem == Problem.NONE;
        }
    }

    private final UltrasClaims plugin;
    private final Set<String> busyClaims = new HashSet<>();
    private final Set<String> reservedChunks = new HashSet<>();

    public ExpansionService(UltrasClaims plugin) {
        this.plugin = plugin;
    }

    public int max() {
        return plugin.getConfig().getInt("limits.max-expansion-per-claim", 100);
    }

    public boolean canExpand(Player p, Claim c) {
        return plugin.hasBypass(p) || c.isOwner(p.getUniqueId()) || c.hasPermission(p.getUniqueId(), MemberPermission.EXPAND);
    }

    /** The chunk a "+" in this direction would add, found with the same rule the border uses for its marker. */
    public ChunkPos target(Claim c, Direction d) {
        ChunkPos from = com.ultras.claims.core.Geometry.plusChunk(c.chunks(), d,
                n -> plugin.claims().at(c.world(), n.x(), n.z()) == null);
        return from.step(d);
    }

    public Check check(Player p, Claim c, Direction d) {
        return check(p, c, target(c, d));
    }

    /** The direction in which {@code target} touches the claim, or null when it is not adjacent. */
    public Direction directionTo(Claim c, ChunkPos target) {
        for (Direction d : Direction.values()) {
            if (c.chunks().contains(target.step(d.opposite()))) {
                return d;
            }
        }
        return null;
    }

    public Check check(Player p, Claim c, ChunkPos target) {
        if (plugin.claims().get(c.id()) != c) {
            return new Check(Problem.GONE, null, null);
        }
        if (directionTo(c, target) == null) {
            return new Check(Problem.GONE, target, null);
        }
        if (!canExpand(p, c)) {
            return new Check(Problem.NOT_ALLOWED, target, null);
        }
        if (c.state() == ClaimState.GRACE) {
            return new Check(Problem.GRACE, target, null);
        }
        if (c.expansions() >= max()) {
            return new Check(Problem.MAX_REACHED, target, null);
        }
        Claim other = plugin.claims().at(c.world(), target.x(), target.z());
        if (other != null) {
            return new Check(Problem.CHUNK_CLAIMED, target, other);
        }
        World w = Bukkit.getWorld(c.world());
        if (w == null) {
            return new Check(Problem.GONE, target, null);
        }
        Location center = new Location(w, target.minBlockX() + 8, 64, target.minBlockZ() + 8);
        if (!w.getWorldBorder().isInside(center)) {
            return new Check(Problem.OUTSIDE_BORDER, target, null);
        }
        if (busyClaims.contains(c.id()) || reservedChunks.contains(chunkKey(c.world(), target))) {
            return new Check(Problem.BUSY, target, null);
        }
        if (!plugin.economy().usable() && !plugin.economy().free(p)) {
            return new Check(Problem.ECONOMY_UNAVAILABLE, target, null);
        }
        return new Check(Problem.NONE, target, null);
    }

    private static String chunkKey(String world, ChunkPos p) {
        return world + ';' + p.x() + ';' + p.z();
    }

    /** Entry point for "+" clicks and menus. Opens the confirmation screen when configured. */
    public void request(Player p, Claim c, Direction d, boolean confirmed) {
        requestTarget(p, c, target(c, d), confirmed);
    }

    /** Expands towards one specific neighbouring chunk (map and wand use this). */
    public void requestTarget(Player p, Claim c, ChunkPos target, boolean confirmed) {
        Check chk = check(p, c, target);
        if (!chk.ok()) {
            explain(p, chk);
            return;
        }
        if (!confirmed && plugin.getConfig().getBoolean("expansion.require-confirmation", false)) {
            plugin.gui().openConfirmExpand(p, c, target);
            return;
        }
        execute(p, c, target);
    }

    public void explain(Player p, Check chk) {
        String key = switch (chk.problem()) {
            case NOT_ALLOWED -> "expand-not-allowed";
            case GRACE -> "expand-grace";
            case MAX_REACHED -> "expand-max";
            case CHUNK_CLAIMED -> "expand-chunk-claimed";
            case OUTSIDE_BORDER -> "expand-outside-border";
            case BUSY -> "expand-busy";
            case ECONOMY_UNAVAILABLE -> "economy-unavailable";
            case DISABLED_WORLD -> "world-disabled";
            default -> "claim-gone";
        };
        plugin.messages().error(p, key, "owner", chk.blockedBy() == null ? "" : chk.blockedBy().ownerName(), "max", max());
        plugin.sounds().play(p, SoundKey.ERROR);
    }

    private void execute(Player p, Claim c, ChunkPos target) {
        final Direction d = directionTo(c, target);
        final String rKey = chunkKey(c.world(), target);
        busyClaims.add(c.id());
        reservedChunks.add(rKey);
        plugin.economy().charge(p, result -> {
            try {
                if (result != ChargeResult.OK) {
                    plugin.messages().error(p, result == ChargeResult.INSUFFICIENT ? "economy-insufficient" : "economy-unavailable");
                    plugin.sounds().play(p, SoundKey.ERROR);
                    return;
                }
                // Paid. Re-check the facts that could have changed while a (command) payment was running.
                Claim other = plugin.claims().at(c.world(), target.x(), target.z());
                if (plugin.claims().get(c.id()) != c || other != null || c.expansions() >= max() || c.state() == ClaimState.GRACE) {
                    plugin.economy().refund(p);
                    plugin.log().log("EXPANSION_REFUNDED", "claim=" + c.id() + " player=" + p.getName() + " chunk=" + target);
                    plugin.messages().error(p, "expand-refunded");
                    plugin.sounds().play(p, SoundKey.ERROR);
                    return;
                }
                plugin.claims().addChunk(c, target);
                c.expansions(c.expansions() + 1);
                plugin.claims().save(c);
                plugin.log().log("EXPANSION", "claim=" + c.id() + " by=" + p.getName() + " dir=" + d + " chunk=" + target.x() + "," + target.z()
                        + " progress=" + c.expansions() + "/" + max() + " economy=" + plugin.economy().providerId());
                plugin.messages().send(p, "expand-success", MessageChannel.EXPANSION, "current", c.expansions(), "max", max());
                plugin.sounds().play(p, SoundKey.EXPANSION);
                plugin.notifications().notify(c, NotificationKey.CLAIM_EXPANDED, "notify-expanded", MessageChannel.EXPANSION, SoundKey.EXPANSION,
                        p.getUniqueId(), "player", p.getName(), "current", c.expansions(), "max", max());
                plugin.borders().onClaimChanged(c, p);
                plugin.holograms().refresh(c);
            } finally {
                busyClaims.remove(c.id());
                reservedChunks.remove(rKey);
            }
        });
    }
}
