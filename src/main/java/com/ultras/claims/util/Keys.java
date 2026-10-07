package com.ultras.claims.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Persistent-data keys. */
public final class Keys {
    public final NamespacedKey headOwner;
    public final NamespacedKey headAdmin;
    public final NamespacedKey headToken;
    public final NamespacedKey blockClaimId;
    public final NamespacedKey displayTag;
    public final NamespacedKey plusDirection;
    public final NamespacedKey wand;
    public final NamespacedKey claimMap;

    public Keys(Plugin p) {
        headOwner = new NamespacedKey(p, "head_owner");
        headAdmin = new NamespacedKey(p, "head_admin");
        headToken = new NamespacedKey(p, "head_token");
        blockClaimId = new NamespacedKey(p, "claim_id");
        displayTag = new NamespacedKey(p, "display");
        plusDirection = new NamespacedKey(p, "plus_dir");
        wand = new NamespacedKey(p, "wand");
        claimMap = new NamespacedKey(p, "claim_map");
    }
}
