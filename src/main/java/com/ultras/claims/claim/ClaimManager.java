package com.ultras.claims.claim;

import com.ultras.claims.UltrasClaims;
import com.ultras.claims.core.ChunkPos;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns every claim in memory with O(1) lookups by chunk, head block, owner and membership.
 * Server thread only. Every change is queued to storage immediately (write-through).
 */
public final class ClaimManager {
    private static final String ID_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final UltrasClaims plugin;
    private final Map<String, Claim> byId = new HashMap<>();
    private final Map<String, Map<Long, Claim>> byChunk = new HashMap<>();
    private final Map<String, Claim> byHead = new HashMap<>();
    private final Map<UUID, Set<String>> owned = new HashMap<>();
    private final Map<UUID, Set<String>> memberOf = new HashMap<>();
    private final AtomicLong version = new AtomicLong();
    private final SecureRandom random = new SecureRandom();

    public ClaimManager(UltrasClaims plugin) {
        this.plugin = plugin;
    }

    public void load() throws Exception {
        byId.clear();
        byChunk.clear();
        byHead.clear();
        owned.clear();
        memberOf.clear();
        for (Claim c : plugin.storage().loadClaims()) {
            if (c.chunks().isEmpty()) {
                plugin.getLogger().warning("Claim " + c.id() + " has no chunks and was skipped");
                continue;
            }
            index(c);
        }
        version.incrementAndGet();
    }

    // ------------------------------------------------------------------ lookups

    public long version() {
        return version.get();
    }

    public Claim get(String id) {
        return id == null ? null : byId.get(id.toUpperCase());
    }

    public Collection<Claim> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public Claim at(String world, int chunkX, int chunkZ) {
        Map<Long, Claim> m = byChunk.get(world);
        return m == null ? null : m.get(ChunkPos.key(chunkX, chunkZ));
    }

    public Claim at(Location l) {
        if (l == null || l.getWorld() == null) {
            return null;
        }
        return at(l.getWorld().getName(), l.getBlockX() >> 4, l.getBlockZ() >> 4);
    }

    public Claim at(Block b) {
        return at(b.getWorld().getName(), b.getX() >> 4, b.getZ() >> 4);
    }

    public Claim byHead(String world, int x, int y, int z) {
        return byHead.get(headKey(world, x, y, z));
    }

    public Claim byHead(Block b) {
        return byHead(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
    }

    public int ownedCount(UUID u) {
        return owned.getOrDefault(u, Set.of()).size();
    }

    public int memberOfCount(UUID u) {
        return memberOf.getOrDefault(u, Set.of()).size();
    }

    public List<Claim> ownedBy(UUID u) {
        return resolve(owned.get(u));
    }

    public List<Claim> memberClaims(UUID u) {
        return resolve(memberOf.get(u));
    }

    private List<Claim> resolve(Set<String> ids) {
        List<Claim> out = new ArrayList<>();
        if (ids != null) {
            for (String id : ids) {
                Claim c = byId.get(id);
                if (c != null) {
                    out.add(c);
                }
            }
        }
        out.sort((a, b) -> Long.compare(a.createdAt(), b.createdAt()));
        return out;
    }

    private static String headKey(String w, int x, int y, int z) {
        return w + ';' + x + ';' + y + ';' + z;
    }

    // ------------------------------------------------------------------ changes

    /** Creates a fresh claim. Nothing from earlier claims (expansions, settings) is carried over. */
    public Claim create(UUID owner, String ownerName, Location head) {
        String id;
        do {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(ID_ALPHABET.charAt(random.nextInt(ID_ALPHABET.length())));
            }
            id = sb.toString();
        } while (byId.containsKey(id));
        long now = System.currentTimeMillis();
        Claim c = new Claim(id, owner, ownerName, head.getWorld().getName(), head.getBlockX(), head.getBlockY(), head.getBlockZ(), now);
        c.chunks().add(ChunkPos.ofBlock(head.getBlockX(), head.getBlockZ()));
        applyDefaults(c);
        c.protectionEndsAt(now + plugin.cabin().startSeconds() * 1000L);
        c.warnBucket(Integer.MAX_VALUE);
        index(c);
        save(c);
        version.incrementAndGet();
        return c;
    }

    private void applyDefaults(Claim c) {
        ConfigurationSection def = plugin.getConfig().getConfigurationSection("protection.defaults");
        for (ProtectionKey k : ProtectionKey.values()) {
            c.settings().put(k, def == null || def.getBoolean(k.id(), true));
        }
        EnumSet<NotificationKey> defNotif = defaultNotifications();
        c.notifications().clear();
        c.notifications().addAll(defNotif);
    }

    public EnumSet<NotificationKey> defaultNotifications() {
        EnumSet<NotificationKey> set = EnumSet.noneOf(NotificationKey.class);
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("notifications.defaults");
        for (NotificationKey k : NotificationKey.values()) {
            if (sec == null ? k != NotificationKey.VISITOR_LEFT : sec.getBoolean(k.id(), true)) {
                set.add(k);
            }
        }
        return set;
    }

    public EnumSet<MemberPermission> defaultPermissions() {
        EnumSet<MemberPermission> set = EnumSet.noneOf(MemberPermission.class);
        for (String s : plugin.getConfig().getStringList("members.default-permissions")) {
            MemberPermission p = MemberPermission.fromId(s);
            if (p != null) {
                set.add(p);
            }
        }
        return set;
    }

    /** Adds one chunk to a claim (caller has verified it is free). */
    public void addChunk(Claim c, ChunkPos pos) {
        c.chunks().add(pos);
        byChunk.computeIfAbsent(c.world(), w -> new HashMap<>()).put(pos.key(), c);
        version.incrementAndGet();
    }

    public void remove(Claim c) {
        deindex(c);
        plugin.storage().deleteClaim(c.id(), plugin.getConfig().getBoolean("expiry.archive", true), System.currentTimeMillis());
        version.incrementAndGet();
    }

    public void save(Claim c) {
        plugin.storage().saveClaim(c.copy());
        version.incrementAndGet();
    }

    public void memberAdded(Claim c, UUID member) {
        memberOf.computeIfAbsent(member, u -> new HashSet<>()).add(c.id());
    }

    public void memberRemoved(Claim c, UUID member) {
        Set<String> s = memberOf.get(member);
        if (s != null) {
            s.remove(c.id());
            if (s.isEmpty()) {
                memberOf.remove(member);
            }
        }
    }

    private void index(Claim c) {
        byId.put(c.id(), c);
        Map<Long, Claim> m = byChunk.computeIfAbsent(c.world(), w -> new HashMap<>());
        for (ChunkPos p : c.chunks()) {
            m.put(p.key(), c);
        }
        byHead.put(headKey(c.world(), c.headX(), c.headY(), c.headZ()), c);
        owned.computeIfAbsent(c.ownerUuid(), u -> new HashSet<>()).add(c.id());
        for (UUID member : c.members().keySet()) {
            memberOf.computeIfAbsent(member, u -> new HashSet<>()).add(c.id());
        }
    }

    private void deindex(Claim c) {
        byId.remove(c.id());
        Map<Long, Claim> m = byChunk.get(c.world());
        if (m != null) {
            for (ChunkPos p : c.chunks()) {
                m.remove(p.key(), c);
            }
        }
        byHead.remove(headKey(c.world(), c.headX(), c.headY(), c.headZ()));
        Set<String> o = owned.get(c.ownerUuid());
        if (o != null) {
            o.remove(c.id());
            if (o.isEmpty()) {
                owned.remove(c.ownerUuid());
            }
        }
        for (UUID member : new ArrayList<>(c.members().keySet())) {
            memberRemoved(c, member);
        }
    }
}
