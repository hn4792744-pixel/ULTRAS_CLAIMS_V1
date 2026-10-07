package com.ultras.claims.claim;

import java.util.Locale;

/** What a member of a claim may do. */
public enum MemberPermission {
    USE_CLAIM(false),
    BUILD(false),
    BREAK(false),
    CONTAINERS(false),
    DOORS(false),
    BUTTONS(false),
    LEVERS(false),
    REDSTONE(false),
    ANIMALS(false),
    VILLAGERS(false),
    PICKUP(false),
    DROP(false),
    TELEPORT(false),
    EXPAND(false),
    MANAGE_MEMBERS(true),
    BREAK_HEAD(true);

    private final boolean sensitive;

    MemberPermission(boolean sensitive) {
        this.sensitive = sensitive;
    }

    /** Sensitive permissions are always OFF unless the owner turns them on explicitly. */
    public boolean sensitive() {
        return sensitive;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static MemberPermission fromId(String id) {
        for (MemberPermission p : values()) {
            if (p.id().equalsIgnoreCase(id) || p.name().equalsIgnoreCase(id)) {
                return p;
            }
        }
        return null;
    }
}
