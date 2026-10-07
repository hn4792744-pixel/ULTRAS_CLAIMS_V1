package com.ultras.claims.protection;

import com.ultras.claims.UltrasClaims;
import com.ultras.claims.claim.Claim;
import com.ultras.claims.claim.MemberPermission;
import com.ultras.claims.claim.ProtectionKey;
import com.ultras.claims.core.AccessRules;
import com.ultras.claims.sound.SoundKey;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Every protection decision goes through here: priority admin bypass > owner > member permission > visitor setting > deny. */
public final class ProtectionService {
    private final UltrasClaims plugin;
    private final Map<UUID, Long> lastDeny = new HashMap<>();

    public ProtectionService(UltrasClaims plugin) {
        this.plugin = plugin;
    }

    /** May this player do something that is governed by (key, perm) inside this claim? */
    public boolean allowed(Player p, Claim c, ProtectionKey key, MemberPermission perm) {
        if (c == null) {
            return true;
        }
        UUID id = p.getUniqueId();
        return AccessRules.allowed(c.isProtected(), plugin.hasBypass(p), c.isOwner(id),
                perm != null && c.hasPermission(id, perm), c.visitorsAllowed(key));
    }

    public boolean allowedAt(Player p, Block b, ProtectionKey key, MemberPermission perm) {
        return allowed(p, plugin.claims().at(b), key, perm);
    }

    public boolean allowedAt(Player p, Location l, ProtectionKey key, MemberPermission perm) {
        return allowed(p, plugin.claims().at(l), key, perm);
    }

    /**
     * Environment rule (explosions, fire, fluids, pistons ...): true when the claim at this location is protected and the
     * switch is ON, i.e. the action must be blocked.
     */
    public boolean blocksEnvironment(Claim c, ProtectionKey key) {
        return c != null && c.isProtected() && !c.visitorsAllowed(key);
    }

    /** Short, throttled refusal text + quiet error sound. */
    public void deny(Player p, Claim c) {
        long now = System.currentTimeMillis();
        Long last = lastDeny.get(p.getUniqueId());
        if (last != null && now - last < 1500) {
            return;
        }
        lastDeny.put(p.getUniqueId(), now);
        plugin.messages().error(p, "protect-denied", "owner", c == null ? "" : c.displayName());
        plugin.sounds().play(p, SoundKey.ERROR);
    }

    /** Convenience for listeners: returns true (and tells the player) when the action must be cancelled. */
    public boolean denies(Player p, Claim c, ProtectionKey key, MemberPermission perm) {
        if (allowed(p, c, key, perm)) {
            return false;
        }
        deny(p, c);
        return true;
    }

    public void forget(UUID id) {
        lastDeny.remove(id);
    }
}
