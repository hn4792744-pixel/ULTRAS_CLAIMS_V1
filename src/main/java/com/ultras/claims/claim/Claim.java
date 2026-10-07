package com.ultras.claims.claim;

import com.ultras.claims.core.ChunkPos;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One claim. Mutated on the server thread only; the database layer receives {@link #copy()} snapshots.
 */
public final class Claim {
    private final String id;
    private final UUID ownerUuid;
    private String ownerName;
    private final String world;
    private final int headX;
    private final int headY;
    private final int headZ;
    private final Set<ChunkPos> chunks = new LinkedHashSet<>();
    private final Map<UUID, ClaimMember> members = new LinkedHashMap<>();
    private final EnumMap<ProtectionKey, Boolean> settings = new EnumMap<>(ProtectionKey.class);
    private final EnumSet<NotificationKey> notifications = EnumSet.noneOf(NotificationKey.class);
    private int expansions;
    private String name = "";
    private final long createdAt;
    private long lastRenewalAt;
    private long protectionEndsAt;
    private ClaimState state = ClaimState.ACTIVE;
    private long graceEndsAt;

    // runtime only (never saved)
    private int warnBucket = Integer.MAX_VALUE;

    public Claim(String id, UUID ownerUuid, String ownerName, String world, int headX, int headY, int headZ, long createdAt) {
        this.id = id;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.world = world;
        this.headX = headX;
        this.headY = headY;
        this.headZ = headZ;
        this.createdAt = createdAt;
        this.lastRenewalAt = createdAt;
        for (ProtectionKey k : ProtectionKey.values()) {
            settings.put(k, Boolean.TRUE);
        }
    }

    public String id() {
        return id;
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }

    public String ownerName() {
        return ownerName;
    }

    public void ownerName(String n) {
        this.ownerName = n;
    }

    public String world() {
        return world;
    }

    public int headX() {
        return headX;
    }

    public int headY() {
        return headY;
    }

    public int headZ() {
        return headZ;
    }

    public ChunkPos headChunk() {
        return ChunkPos.ofBlock(headX, headZ);
    }

    public Set<ChunkPos> chunks() {
        return chunks;
    }

    public Map<UUID, ClaimMember> members() {
        return members;
    }

    public EnumMap<ProtectionKey, Boolean> settings() {
        return settings;
    }

    public EnumSet<NotificationKey> notifications() {
        return notifications;
    }

    public int expansions() {
        return expansions;
    }

    public void expansions(int n) {
        this.expansions = n;
    }

    public String name() {
        return name;
    }

    public void name(String n) {
        this.name = n == null ? "" : n;
    }

    public long createdAt() {
        return createdAt;
    }

    public long lastRenewalAt() {
        return lastRenewalAt;
    }

    public void lastRenewalAt(long t) {
        this.lastRenewalAt = t;
    }

    public long protectionEndsAt() {
        return protectionEndsAt;
    }

    public void protectionEndsAt(long t) {
        this.protectionEndsAt = t;
    }

    public ClaimState state() {
        return state;
    }

    public void state(ClaimState s) {
        this.state = s;
    }

    public long graceEndsAt() {
        return graceEndsAt;
    }

    public void graceEndsAt(long t) {
        this.graceEndsAt = t;
    }

    public int warnBucket() {
        return warnBucket;
    }

    public void warnBucket(int b) {
        this.warnBucket = b;
    }

    // ------------------------------------------------------------------ queries

    public boolean isProtected() {
        return state == ClaimState.ACTIVE;
    }

    public boolean isOwner(UUID uuid) {
        return ownerUuid.equals(uuid);
    }

    public ClaimMember member(UUID uuid) {
        return members.get(uuid);
    }

    public boolean isMember(UUID uuid) {
        return members.containsKey(uuid);
    }

    public boolean hasPermission(UUID uuid, MemberPermission p) {
        ClaimMember m = members.get(uuid);
        return m != null && m.permissions().contains(p);
    }

    /** ON means blocked; a visitor may act only when the switch is OFF. */
    public boolean visitorsAllowed(ProtectionKey k) {
        return !Boolean.TRUE.equals(settings.get(k));
    }

    public boolean notifies(NotificationKey k) {
        return notifications.contains(k);
    }

    public String displayName() {
        return name.isEmpty() ? ownerName : name;
    }

    public Claim copy() {
        Claim c = new Claim(id, ownerUuid, ownerName, world, headX, headY, headZ, createdAt);
        c.chunks.addAll(chunks);
        for (ClaimMember m : members.values()) {
            c.members.put(m.uuid(), m.copy());
        }
        c.settings.putAll(settings);
        c.notifications.addAll(notifications);
        c.expansions = expansions;
        c.name = name;
        c.lastRenewalAt = lastRenewalAt;
        c.protectionEndsAt = protectionEndsAt;
        c.state = state;
        c.graceEndsAt = graceEndsAt;
        return c;
    }
}
