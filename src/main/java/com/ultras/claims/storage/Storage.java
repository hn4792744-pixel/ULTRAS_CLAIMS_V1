package com.ultras.claims.storage;

import com.ultras.claims.claim.Claim;
import com.ultras.claims.claim.ClaimMember;
import com.ultras.claims.claim.ClaimState;
import com.ultras.claims.claim.MemberPermission;
import com.ultras.claims.claim.NotificationKey;
import com.ultras.claims.claim.ProtectionKey;
import com.ultras.claims.core.ChunkPos;
import com.ultras.claims.settings.PlayerSettings;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite storage. One connection, one thread: every statement runs on the storage thread, so the server thread
 * never blocks on disk. Writes are queued in order and executed in transactions with prepared statements.
 */
public final class Storage implements AutoCloseable {
    private static final int SCHEMA = 1;

    /** A deposit snapshot kept so that items are never lost if the server stops while a cabin menu is open. */
    public record CabinRow(long id, String claimId, UUID player, String data) {
    }

    @FunctionalInterface
    private interface Work {
        void run(Connection c) throws SQLException;
    }

    @FunctionalInterface
    private interface Query<T> {
        T run(Connection c) throws SQLException;
    }

    private final Logger log;
    private final Path file;
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "UltrasClaims-Storage");
        t.setDaemon(false);
        return t;
    });
    private Connection conn;

    public Storage(Path file, Logger log) {
        this.file = file;
        this.log = log;
    }

    // ------------------------------------------------------------------ lifecycle

    public void open() throws Exception {
        call(() -> {
            connect();
            migrate();
            return null;
        });
    }

    private void connect() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite driver (org.sqlite.JDBC) not found - Paper normally bundles it", e);
        }
        conn = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
        try (Statement s = conn.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=NORMAL");
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("PRAGMA busy_timeout=5000");
        }
    }

    private void migrate() throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS claims ("
                    + "id TEXT PRIMARY KEY, owner_uuid TEXT NOT NULL, owner_name TEXT NOT NULL, world TEXT NOT NULL,"
                    + "head_x INTEGER NOT NULL, head_y INTEGER NOT NULL, head_z INTEGER NOT NULL, name TEXT NOT NULL DEFAULT '',"
                    + "expansions INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, last_renewal_at INTEGER NOT NULL,"
                    + "protection_ends_at INTEGER NOT NULL, state TEXT NOT NULL DEFAULT 'ACTIVE', grace_ends_at INTEGER NOT NULL DEFAULT 0,"
                    + "archived INTEGER NOT NULL DEFAULT 0, archived_at INTEGER NOT NULL DEFAULT 0)");
            s.execute("CREATE TABLE IF NOT EXISTS claim_chunks ("
                    + "claim_id TEXT NOT NULL, world TEXT NOT NULL, x INTEGER NOT NULL, z INTEGER NOT NULL,"
                    + "PRIMARY KEY (claim_id, x, z), FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE)");
            s.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_claim_chunks_pos ON claim_chunks (world, x, z)");
            s.execute("CREATE TABLE IF NOT EXISTS claim_members ("
                    + "claim_id TEXT NOT NULL, uuid TEXT NOT NULL, name TEXT NOT NULL, added_at INTEGER NOT NULL,"
                    + "PRIMARY KEY (claim_id, uuid), FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE)");
            s.execute("CREATE INDEX IF NOT EXISTS ix_claim_members_uuid ON claim_members (uuid)");
            s.execute("CREATE TABLE IF NOT EXISTS member_permissions ("
                    + "claim_id TEXT NOT NULL, uuid TEXT NOT NULL, permission TEXT NOT NULL,"
                    + "PRIMARY KEY (claim_id, uuid, permission), FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE)");
            s.execute("CREATE TABLE IF NOT EXISTS claim_settings ("
                    + "claim_id TEXT NOT NULL, key TEXT NOT NULL, value INTEGER NOT NULL,"
                    + "PRIMARY KEY (claim_id, key), FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE)");
            s.execute("CREATE TABLE IF NOT EXISTS claim_notifications ("
                    + "claim_id TEXT NOT NULL, key TEXT NOT NULL, value INTEGER NOT NULL,"
                    + "PRIMARY KEY (claim_id, key), FOREIGN KEY (claim_id) REFERENCES claims(id) ON DELETE CASCADE)");
            s.execute("CREATE TABLE IF NOT EXISTS cabin_storage ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, claim_id TEXT NOT NULL, player_uuid TEXT NOT NULL,"
                    + "data TEXT NOT NULL, updated_at INTEGER NOT NULL, UNIQUE (claim_id, player_uuid))");
            s.execute("CREATE TABLE IF NOT EXISTS used_tokens (token TEXT PRIMARY KEY, claim_id TEXT NOT NULL, used_at INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS player_settings ("
                    + "uuid TEXT PRIMARY KEY, language TEXT NOT NULL, heads_issued INTEGER NOT NULL DEFAULT 0,"
                    + "map_view_id INTEGER NOT NULL DEFAULT -1, map_world TEXT NOT NULL DEFAULT '', settings TEXT NOT NULL DEFAULT '')");
        }
        int current = -1;
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT version FROM schema_version LIMIT 1")) {
            if (rs.next()) {
                current = rs.getInt(1);
            }
        }
        if (current < 0) {
            try (Statement s = conn.createStatement()) {
                s.execute("INSERT INTO schema_version (version) VALUES (" + SCHEMA + ")");
            }
        } else if (current > SCHEMA) {
            throw new SQLException("Database was written by a newer version of the plugin (schema " + current + ")");
        }
    }

    /** Finishes every queued write, then closes the connection. */
    @Override
    public void close() {
        exec.shutdown();
        try {
            if (!exec.awaitTermination(30, TimeUnit.SECONDS)) {
                log.severe("Storage did not finish writing within 30s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            if (conn != null) {
                conn.close();
            }
        } catch (SQLException e) {
            log.log(Level.WARNING, "closing database", e);
        }
    }

    // ------------------------------------------------------------------ plumbing

    private <T> T call(java.util.concurrent.Callable<T> task) throws Exception {
        Future<T> f = exec.submit(task);
        try {
            return f.get();
        } catch (ExecutionException e) {
            throw e.getCause() instanceof Exception ex ? ex : e;
        }
    }

    private <T> T query(Query<T> q) throws Exception {
        return call(() -> q.run(conn));
    }

    private void write(String what, Work w) {
        if (exec.isShutdown()) {
            log.severe("Write after shutdown ignored: " + what);
            return;
        }
        exec.execute(() -> {
            try {
                boolean auto = conn.getAutoCommit();
                conn.setAutoCommit(false);
                try {
                    w.run(conn);
                    conn.commit();
                } catch (SQLException | RuntimeException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(auto);
                }
            } catch (Exception e) {
                log.log(Level.SEVERE, "Database write failed: " + what, e);
            }
        });
    }

    // ------------------------------------------------------------------ claims

    public List<Claim> loadClaims() throws Exception {
        return query(c -> {
            Map<String, Claim> map = new HashMap<>();
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT * FROM claims WHERE archived = 0")) {
                while (rs.next()) {
                    Claim cl = new Claim(rs.getString("id"), UUID.fromString(rs.getString("owner_uuid")), rs.getString("owner_name"),
                            rs.getString("world"), rs.getInt("head_x"), rs.getInt("head_y"), rs.getInt("head_z"), rs.getLong("created_at"));
                    cl.name(rs.getString("name"));
                    cl.expansions(rs.getInt("expansions"));
                    cl.lastRenewalAt(rs.getLong("last_renewal_at"));
                    cl.protectionEndsAt(rs.getLong("protection_ends_at"));
                    try {
                        cl.state(ClaimState.valueOf(rs.getString("state")));
                    } catch (IllegalArgumentException e) {
                        cl.state(ClaimState.ACTIVE);
                    }
                    cl.graceEndsAt(rs.getLong("grace_ends_at"));
                    map.put(cl.id(), cl);
                }
            }
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT claim_id, x, z FROM claim_chunks")) {
                while (rs.next()) {
                    Claim cl = map.get(rs.getString(1));
                    if (cl != null) {
                        cl.chunks().add(new ChunkPos(rs.getInt(2), rs.getInt(3)));
                    }
                }
            }
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT claim_id, uuid, name, added_at FROM claim_members")) {
                while (rs.next()) {
                    Claim cl = map.get(rs.getString(1));
                    if (cl != null) {
                        UUID u = UUID.fromString(rs.getString(2));
                        cl.members().put(u, new ClaimMember(u, rs.getString(3), java.util.EnumSet.noneOf(MemberPermission.class), rs.getLong(4)));
                    }
                }
            }
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT claim_id, uuid, permission FROM member_permissions")) {
                while (rs.next()) {
                    Claim cl = map.get(rs.getString(1));
                    MemberPermission p = MemberPermission.fromId(rs.getString(3));
                    if (cl != null && p != null) {
                        ClaimMember m = cl.members().get(UUID.fromString(rs.getString(2)));
                        if (m != null) {
                            m.permissions().add(p);
                        }
                    }
                }
            }
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT claim_id, key, value FROM claim_settings")) {
                while (rs.next()) {
                    Claim cl = map.get(rs.getString(1));
                    ProtectionKey k = ProtectionKey.fromId(rs.getString(2));
                    if (cl != null && k != null) {
                        cl.settings().put(k, rs.getInt(3) != 0);
                    }
                }
            }
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT claim_id, key, value FROM claim_notifications")) {
                while (rs.next()) {
                    Claim cl = map.get(rs.getString(1));
                    NotificationKey k = NotificationKey.fromId(rs.getString(2));
                    if (cl != null && k != null && rs.getInt(3) != 0) {
                        cl.notifications().add(k);
                    }
                }
            }
            return new ArrayList<>(map.values());
        });
    }

    /** Saves a snapshot (use {@link Claim#copy()}) in one transaction. */
    public void saveClaim(Claim s) {
        write("save claim " + s.id(), c -> writeClaim(c, s));
    }

    private static void writeClaim(Connection c, Claim s) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO claims (id, owner_uuid, owner_name, world, head_x, head_y, head_z, name,"
                + " expansions, created_at, last_renewal_at, protection_ends_at, state, grace_ends_at, archived)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,0) ON CONFLICT(id) DO UPDATE SET owner_name=excluded.owner_name, name=excluded.name,"
                + " expansions=excluded.expansions, last_renewal_at=excluded.last_renewal_at, protection_ends_at=excluded.protection_ends_at,"
                + " state=excluded.state, grace_ends_at=excluded.grace_ends_at, archived=0")) {
            ps.setString(1, s.id());
            ps.setString(2, s.ownerUuid().toString());
            ps.setString(3, s.ownerName());
            ps.setString(4, s.world());
            ps.setInt(5, s.headX());
            ps.setInt(6, s.headY());
            ps.setInt(7, s.headZ());
            ps.setString(8, s.name());
            ps.setInt(9, s.expansions());
            ps.setLong(10, s.createdAt());
            ps.setLong(11, s.lastRenewalAt());
            ps.setLong(12, s.protectionEndsAt());
            ps.setString(13, s.state().name());
            ps.setLong(14, s.graceEndsAt());
            ps.executeUpdate();
        }
        for (String table : new String[]{"claim_chunks", "claim_members", "member_permissions", "claim_settings", "claim_notifications"}) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + table + " WHERE claim_id = ?")) {
                ps.setString(1, s.id());
                ps.executeUpdate();
            }
        }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO claim_chunks (claim_id, world, x, z) VALUES (?,?,?,?)")) {
            for (ChunkPos p : s.chunks()) {
                ps.setString(1, s.id());
                ps.setString(2, s.world());
                ps.setInt(3, p.x());
                ps.setInt(4, p.z());
                ps.addBatch();
            }
            ps.executeBatch();
        }
        try (PreparedStatement pm = c.prepareStatement("INSERT INTO claim_members (claim_id, uuid, name, added_at) VALUES (?,?,?,?)");
             PreparedStatement pp = c.prepareStatement("INSERT INTO member_permissions (claim_id, uuid, permission) VALUES (?,?,?)")) {
            for (ClaimMember m : s.members().values()) {
                pm.setString(1, s.id());
                pm.setString(2, m.uuid().toString());
                pm.setString(3, m.name());
                pm.setLong(4, m.addedAt());
                pm.addBatch();
                for (MemberPermission p : m.permissions()) {
                    pp.setString(1, s.id());
                    pp.setString(2, m.uuid().toString());
                    pp.setString(3, p.id());
                    pp.addBatch();
                }
            }
            pm.executeBatch();
            pp.executeBatch();
        }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO claim_settings (claim_id, key, value) VALUES (?,?,?)")) {
            for (Map.Entry<ProtectionKey, Boolean> e : s.settings().entrySet()) {
                ps.setString(1, s.id());
                ps.setString(2, e.getKey().id());
                ps.setInt(3, Boolean.TRUE.equals(e.getValue()) ? 1 : 0);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO claim_notifications (claim_id, key, value) VALUES (?,?,?)")) {
            for (NotificationKey k : NotificationKey.values()) {
                ps.setString(1, s.id());
                ps.setString(2, k.id());
                ps.setInt(3, s.notifications().contains(k) ? 1 : 0);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** Removes the claim; with archive=true the claim row is kept (marked archived) and its chunks are freed. */
    public void deleteClaim(String id, boolean archive, long now) {
        write("delete claim " + id, c -> {
            for (String table : new String[]{"claim_chunks", "member_permissions", "claim_settings", "claim_notifications"}) {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + table + " WHERE claim_id = ?")) {
                    ps.setString(1, id);
                    ps.executeUpdate();
                }
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM claim_members WHERE claim_id = ?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
            if (archive) {
                try (PreparedStatement ps = c.prepareStatement("UPDATE claims SET archived = 1, archived_at = ? WHERE id = ?")) {
                    ps.setLong(1, now);
                    ps.setString(2, id);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM claims WHERE id = ?")) {
                    ps.setString(1, id);
                    ps.executeUpdate();
                }
            }
        });
    }

    // ------------------------------------------------------------------ cabin snapshots

    /** Replaces (or, with null data, deletes) the pending-deposit snapshot of one player in one claim. */
    public void saveCabinSnapshot(String claimId, UUID player, String data) {
        write("cabin snapshot", c -> writeSnapshot(c, claimId, player, data));
    }

    /** Claim time + remaining items in one transaction: either both are stored or neither. */
    public void commitCabin(Claim snapshot, UUID player, String remainingData) {
        write("cabin commit " + snapshot.id(), c -> {
            writeClaim(c, snapshot);
            writeSnapshot(c, snapshot.id(), player, remainingData);
        });
    }

    private static void writeSnapshot(Connection c, String claimId, UUID player, String data) throws SQLException {
        if (data == null) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM cabin_storage WHERE claim_id = ? AND player_uuid = ?")) {
                ps.setString(1, claimId);
                ps.setString(2, player.toString());
                ps.executeUpdate();
            }
            return;
        }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO cabin_storage (claim_id, player_uuid, data, updated_at) VALUES (?,?,?,?)"
                + " ON CONFLICT(claim_id, player_uuid) DO UPDATE SET data = excluded.data, updated_at = excluded.updated_at")) {
            ps.setString(1, claimId);
            ps.setString(2, player.toString());
            ps.setString(3, data);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public List<CabinRow> loadCabinRows() throws Exception {
        return query(c -> {
            List<CabinRow> rows = new ArrayList<>();
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT id, claim_id, player_uuid, data FROM cabin_storage")) {
                while (rs.next()) {
                    rows.add(new CabinRow(rs.getLong(1), rs.getString(2), UUID.fromString(rs.getString(3)), rs.getString(4)));
                }
            }
            return rows;
        });
    }

    /** Remembers that a head item was placed; a second item with the same token (a duplicate) is then refused. */
    public void markTokenUsed(String token, String claimId) {
        write("used token", c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO used_tokens (token, claim_id, used_at) VALUES (?,?,?)")) {
                ps.setString(1, token);
                ps.setString(2, claimId);
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
            }
        });
    }

    public java.util.Set<String> loadUsedTokens() throws Exception {
        return query(c -> {
            java.util.Set<String> out = new java.util.HashSet<>();
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT token FROM used_tokens")) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
            return out;
        });
    }

    public void deleteCabinRow(long id) {
        write("delete cabin row", c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM cabin_storage WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
        });
    }

    // ------------------------------------------------------------------ player settings

    public Map<UUID, PlayerSettings> loadPlayerSettings(String defaultLanguage) throws Exception {
        return query(c -> {
            Map<UUID, PlayerSettings> map = new HashMap<>();
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT * FROM player_settings")) {
                while (rs.next()) {
                    UUID u = UUID.fromString(rs.getString("uuid"));
                    PlayerSettings p = new PlayerSettings(u, defaultLanguage);
                    p.language(rs.getString("language"));
                    p.headsIssued(rs.getInt("heads_issued"));
                    p.mapView(rs.getInt("map_view_id"), rs.getString("map_world"));
                    p.decode(rs.getString("settings"));
                    map.put(u, p);
                }
            }
            return map;
        });
    }

    public void savePlayerSettings(PlayerSettings p) {
        final UUID uuid = p.uuid();
        final String lang = p.language();
        final int heads = p.headsIssued();
        final int mapId = p.mapViewId();
        final String mapWorld = p.mapWorld();
        final String text = p.encode();
        write("player settings", c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO player_settings (uuid, language, heads_issued, map_view_id, map_world, settings)"
                    + " VALUES (?,?,?,?,?,?) ON CONFLICT(uuid) DO UPDATE SET language=excluded.language, heads_issued=excluded.heads_issued,"
                    + " map_view_id=excluded.map_view_id, map_world=excluded.map_world, settings=excluded.settings")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, lang);
                ps.setInt(3, heads);
                ps.setInt(4, mapId);
                ps.setString(5, mapWorld);
                ps.setString(6, text);
                ps.executeUpdate();
            }
        });
    }

    /** Runs a task on the storage thread after everything queued so far (used to hand results back safely). */
    public void afterWrites(Consumer<Void> done) {
        exec.execute(() -> done.accept(null));
    }
}
