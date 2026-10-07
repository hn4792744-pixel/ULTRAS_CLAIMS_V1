package com.ultras.claims.claim;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

public final class ClaimMember {
    private final UUID uuid;
    private String name;
    private final Set<MemberPermission> permissions;
    private final long addedAt;

    public ClaimMember(UUID uuid, String name, Set<MemberPermission> permissions, long addedAt) {
        this.uuid = uuid;
        this.name = name;
        this.permissions = permissions.isEmpty() ? EnumSet.noneOf(MemberPermission.class) : EnumSet.copyOf(permissions);
        this.addedAt = addedAt;
    }

    public UUID uuid() {
        return uuid;
    }

    /** Display only - identity is always the UUID. */
    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    public Set<MemberPermission> permissions() {
        return permissions;
    }

    public long addedAt() {
        return addedAt;
    }

    public ClaimMember copy() {
        return new ClaimMember(uuid, name, permissions, addedAt);
    }
}
