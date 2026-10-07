package com.ultras.claims.border;

import com.ultras.claims.UltrasClaims;
import com.ultras.claims.claim.Claim;
import com.ultras.claims.core.ChunkPos;
import com.ultras.claims.core.Direction;
import com.ultras.claims.core.Geometry;
import com.ultras.claims.expansion.ExpansionService;
import com.ultras.claims.util.ColorSpec;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Virtual claim borders: client-side-only Display entities (nothing in the world changes) shown to one player.
 * A "+" per direction (clickable) and optionally a full wall. One shared task runs only while someone is looking.
 */
public final class BorderService {
    public static final String TAG = "ultras_claims_display";

    /** One clickable "+" : two bars (BlockDisplays) and an Interaction hitbox. */
    static final class PlusUnit {
        final String claimId;
        final Direction dir;
        final boolean blocked;
        /** The claim chunk that carries this "+" and the free chunk it would add. */
        final ChunkPos source;
        final ChunkPos target;
        BlockDisplay plate;
        BlockDisplay vertical;
        BlockDisplay horizontal;
        Interaction hit;
        double x;
        double y;
        double z;

        PlusUnit(String claimId, Direction dir, boolean blocked, ChunkPos source, ChunkPos target) {
            this.claimId = claimId;
            this.dir = dir;
            this.blocked = blocked;
            this.source = source;
            this.target = target;
        }
    }

    static final class Beam {
        final BlockDisplay display;
        final double baseOffset;

        Beam(BlockDisplay d, double baseOffset) {
            this.display = d;
            this.baseOffset = baseOffset;
        }
    }

    static final class ClaimView {
        final String claimId;
        final List<PlusUnit> pluses = new ArrayList<>();
        final List<Beam> beams = new ArrayList<>();
        double beamY = Double.NaN;

        ClaimView(String claimId) {
            this.claimId = claimId;
        }
    }

    static final class Session {
        final UUID viewer;
        final String world;
        final List<ClaimView> views = new ArrayList<>();
        final List<Entity> entities = new ArrayList<>();
        long expiresAt;
        int entityBudget;

        Session(UUID viewer, String world) {
            this.viewer = viewer;
            this.world = world;
        }
    }

    private final UltrasClaims plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, PlusUnit> byEntity = new HashMap<>();
    private final Map<UUID, Long> clickCooldown = new HashMap<>();
    private BukkitTask ticker;
    private int tick;

    public BorderService(UltrasClaims plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ config

    private boolean enabled() {
        return plugin.getConfig().getBoolean("border.enabled", true);
    }

    private ColorSpec color(String path, String def) {
        return ColorSpec.parse(plugin.getConfig().getString(path, def), ColorSpec.of(0xFFFFFF));
    }

    // ------------------------------------------------------------------ public API

    public boolean has(Player p) {
        return sessions.containsKey(p.getUniqueId());
    }

    public void show(Player viewer, Claim claim) {
        showArea(viewer, List.of(claim));
    }

    /** Shows one or many claims (the wand shows everything nearby). Replaces the player's current display. */
    public void showArea(Player viewer, Collection<Claim> claims) {
        if (!enabled()) {
            return;
        }
        hide(viewer);
        if (claims.isEmpty()) {
            return;
        }
        Session s = new Session(viewer.getUniqueId(), viewer.getWorld().getName());
        s.expiresAt = System.currentTimeMillis() + plugin.getConfig().getLong("border.display-time", 30) * 1000L;
        s.entityBudget = plugin.getConfig().getInt("border.max-entities", 480);
        sessions.put(viewer.getUniqueId(), s);
        for (Claim c : claims) {
            if (c.world().equals(s.world)) {
                build(viewer, s, c);
            }
        }
        startTicker();
    }

    public void hide(Player viewer) {
        remove(viewer.getUniqueId());
    }

    public void hideAll() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            remove(id);
        }
        stopTicker();
    }

    /** A claim grew (or changed): redraw it for everyone who is looking and restart their timer. */
    public void onClaimChanged(Claim c, Player actor) {
        long expire = System.currentTimeMillis() + plugin.getConfig().getLong("border.display-time", 30) * 1000L;
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Session s = sessions.get(id);
            Player viewer = Bukkit.getPlayer(id);
            if (s == null || viewer == null || s.views.stream().noneMatch(v -> v.claimId.equals(c.id()))) {
                continue;
            }
            List<Claim> claims = new ArrayList<>();
            for (ClaimView v : s.views) {
                Claim cl = plugin.claims().get(v.claimId);
                if (cl != null) {
                    claims.add(cl);
                }
            }
            showArea(viewer, claims);
            Session fresh = sessions.get(id);
            if (fresh != null) {
                fresh.expiresAt = expire;
            }
        }
    }

    public void onClaimRemoved(Claim c) {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Session s = sessions.get(id);
            if (s != null && s.views.stream().anyMatch(v -> v.claimId.equals(c.id()))) {
                remove(id);
            }
        }
    }

    /** Removes every display entity left behind by a previous run (e.g. after /reload). */
    public void purgeOrphans() {
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e.getScoreboardTags().contains(TAG)) {
                    e.remove();
                }
            }
        }
    }

    // ------------------------------------------------------------------ building

    private void build(Player viewer, Session s, Claim c) {
        ClaimView view = new ClaimView(c.id());
        s.views.add(view);
        ExpansionService exp = plugin.expansion();
        boolean owner = c.isOwner(viewer.getUniqueId()) || plugin.hasBypass(viewer);
        boolean showPluses = exp.canExpand(viewer, c) && (owner || !plugin.getConfig().getBoolean("border.show-only-to-owner", true)) && c.isProtected();
        boolean beams = plugin.getConfig().getBoolean("border.full-beam-enabled", false) || !showPluses;
        double yOffset = plugin.getConfig().getDouble("border.plus-offset-y", 1.2);
        double baseY = viewer.getLocation().getY();
        if (showPluses) {
            // Every outer face of every claim chunk gets its own "+" (not one per side of the whole claim).
            List<PlusUnit> candidates = new ArrayList<>();
            for (ChunkPos cp : c.chunks()) {
                for (Direction d : Direction.values()) {
                    ChunkPos n = cp.step(d);
                    if (c.chunks().contains(n)) {
                        continue; // inner face
                    }
                    ExpansionService.Check chk = exp.check(viewer, c, n);
                    if (chk.problem() == ExpansionService.Problem.CHUNK_CLAIMED) {
                        continue; // someone else's land: nothing to offer
                    }
                    boolean blocked = !chk.ok() && chk.problem() != ExpansionService.Problem.BUSY;
                    candidates.add(new PlusUnit(c.id(), d, blocked, cp, n));
                }
            }
            double vx = viewer.getLocation().getX();
            double vz = viewer.getLocation().getZ();
            candidates.sort(java.util.Comparator.comparingDouble(u -> {
                double[] p = facePosition(u);
                return (p[0] - vx) * (p[0] - vx) + (p[1] - vz) * (p[1] - vz);
            }));
            for (PlusUnit u : candidates) {
                if (spawnPlus(viewer, s, c, u, baseY + yOffset)) {
                    view.pluses.add(u);
                }
            }
        }
        if (beams) {
            buildBeams(viewer, s, c, view, baseY);
        }
    }

    private boolean spawnPlus(Player viewer, Session s, Claim c, PlusUnit u, double centerY) {
        if (s.entityBudget < 4) {
            return false;
        }
        World w = viewer.getWorld();
        double[] pos = facePosition(u);
        double x = pos[0];
        double z = pos[1];
        if (!w.isChunkLoaded((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4)) {
            return false;
        }
        u.x = x;
        u.y = centerY;
        u.z = z;
        ColorSpec col = u.blocked ? ColorSpec.of(0x777777) : color("border.plus-color", "GREEN");
        double size = Math.max(0.5, plugin.getConfig().getDouble("border.plus-size", 2.6));
        float yaw = (u.dir == Direction.EAST || u.dir == Direction.WEST) ? 90f : 0f;
        Location at = new Location(w, x, centerY, z, yaw, 0f);
        boolean glow = plugin.getConfig().getBoolean("border.glow", true) && !u.blocked;
        u.plate = spawn(viewer, s, at, BlockDisplay.class, d -> stylePlate(d, size));
        u.vertical = spawn(viewer, s, at, BlockDisplay.class, d -> styleBar(d, col, glow, size, true, 1f));
        u.horizontal = spawn(viewer, s, at, BlockDisplay.class, d -> styleBar(d, col, glow, size, false, 1f));
        Location hitAt = new Location(w, x, centerY - size / 2.0, z);
        u.hit = spawn(viewer, s, hitAt, Interaction.class, i -> {
            i.setInteractionWidth((float) size);
            i.setInteractionHeight((float) size);
            i.setResponsive(true);
        });
        byEntity.put(u.hit.getUniqueId(), u);
        s.entityBudget -= 4;
        return true;
    }

    /** Centre of the outer face of a claim chunk that a "+" sits on (x, z). */
    private static double[] facePosition(PlusUnit u) {
        ChunkPos pc = u.source;
        return switch (u.dir) {
            case NORTH -> new double[]{pc.minBlockX() + 8, pc.minBlockZ() - 0.5};
            case SOUTH -> new double[]{pc.minBlockX() + 8, pc.minBlockZ() + 16.5};
            case WEST -> new double[]{pc.minBlockX() - 0.5, pc.minBlockZ() + 8};
            default -> new double[]{pc.minBlockX() + 16.5, pc.minBlockZ() + 8};
        };
    }

    /** A thin dark plate behind the "+", so it stays readable against any background. */
    private void stylePlate(BlockDisplay d, double size) {
        d.setBlock(org.bukkit.Material.BLACK_CONCRETE.createBlockData());
        d.setBrightness(new Display.Brightness(6, 6));
        d.setViewRange(1.5f);
        float side = (float) (size * 1.12);
        d.setTransformation(new Transformation(new Vector3f(-side / 2f, -side / 2f, -0.11f), new AxisAngle4f(), new Vector3f(side, side, 0.05f), new AxisAngle4f()));
    }

    private void styleBar(BlockDisplay d, ColorSpec col, boolean glow, double size, boolean vertical, float factor) {
        d.setBlock(col.concrete().createBlockData());
        d.setBrightness(new Display.Brightness(15, 15));
        d.setViewRange(1.5f);
        if (glow) {
            d.setGlowing(true);
            d.setGlowColorOverride(col.bukkit());
        }
        d.setTeleportDuration(4);
        d.setTransformation(barTransform(size, vertical, factor));
    }

    private Transformation barTransform(double size, boolean vertical, float factor) {
        float thick = (float) (size * 0.30);
        float len = (float) size;
        float depth = 0.12f;
        float sx = (vertical ? thick : len) * factor;
        float sy = (vertical ? len : thick) * factor;
        float sz = depth;
        return new Transformation(new Vector3f(-sx / 2f, -sy / 2f, -sz / 2f), new AxisAngle4f(), new Vector3f(sx, sy, sz), new AxisAngle4f());
    }

    private void buildBeams(Player viewer, Session s, Claim c, ClaimView view, double baseY) {
        World w = viewer.getWorld();
        double height = Math.max(2, plugin.getConfig().getDouble("border.beam-height", 8));
        ColorSpec col = color("border.beam-color", "WHITE");
        boolean glow = plugin.getConfig().getBoolean("border.glow", true);
        double y0 = baseY - height / 2.0;
        view.beamY = baseY;
        for (Geometry.Segment seg : Geometry.perimeter(c.chunks())) {
            if (s.entityBudget < 1) {
                break;
            }
            boolean alongX = seg.side() == Direction.NORTH || seg.side() == Direction.SOUTH;
            double x = alongX ? seg.from() : seg.fixed() - 0.03;
            double z = alongX ? seg.fixed() - 0.03 : seg.from();
            if (!w.isChunkLoaded((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4)) {
                continue;
            }
            Location at = new Location(w, x, y0, z);
            final float lenF = seg.length();
            BlockDisplay d = spawn(viewer, s, at, BlockDisplay.class, e -> {
                e.setBlock(col.glass().createBlockData());
                e.setBrightness(new Display.Brightness(15, 15));
                e.setViewRange(1.0f);
                if (glow) {
                    e.setGlowing(true);
                    e.setGlowColorOverride(col.bukkit());
                }
                e.setTeleportDuration(6);
                e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                        new Vector3f(alongX ? lenF : 0.06f, (float) height, alongX ? 0.06f : lenF), new AxisAngle4f()));
            });
            view.beams.add(new Beam(d, 0));
            s.entityBudget--;
        }
    }

    private <T extends Entity> T spawn(Player viewer, Session s, Location at, Class<T> type, Consumer<T> init) {
        T e = at.getWorld().spawn(at, type, ent -> {
            ent.setVisibleByDefault(false);
            ent.setPersistent(false);
            ent.addScoreboardTag(TAG);
            init.accept(ent);
        });
        viewer.showEntity(plugin, e);
        s.entities.add(e);
        return e;
    }

    // ------------------------------------------------------------------ removal

    private void remove(UUID viewer) {
        Session s = sessions.remove(viewer);
        if (s == null) {
            return;
        }
        for (Entity e : s.entities) {
            byEntity.remove(e.getUniqueId());
            if (e.isValid() || !e.isDead()) {
                e.remove();
            }
        }
        s.entities.clear();
        if (sessions.isEmpty()) {
            stopTicker();
        }
    }

    // ------------------------------------------------------------------ ticking

    private void startTicker() {
        if (ticker != null) {
            return;
        }
        tick = 0;
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::run, 5L, 5L);
    }

    private void stopTicker() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    private void run() {
        tick++;
        long now = System.currentTimeMillis();
        boolean follow = plugin.getConfig().getBoolean("border.height-follow-player", true);
        boolean animate = plugin.getConfig().getBoolean("plus.animation.enabled", true);
        boolean pulse = plugin.getConfig().getBoolean("plus.animation.pulse", true) || plugin.getConfig().getBoolean("border.pulse", false);
        double speed = Math.max(0.2, plugin.getConfig().getDouble("plus.animation.speed", 1.0));
        double yOffset = plugin.getConfig().getDouble("border.plus-offset-y", 1.2);
        double size = Math.max(0.5, plugin.getConfig().getDouble("border.plus-size", 2.6));
        int cycle = Math.max(1, (int) Math.round(4 / speed));
        float factor = (tick / cycle) % 2 == 0 ? 1f : 1.1f;
        for (Iterator<Map.Entry<UUID, Session>> it = sessions.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Session> en = it.next();
            Session s = en.getValue();
            Player viewer = Bukkit.getPlayer(en.getKey());
            if (viewer == null || !viewer.isOnline() || now >= s.expiresAt || !viewer.getWorld().getName().equals(s.world)) {
                it.remove();
                dispose(s);
                continue;
            }
            double vy = viewer.getLocation().getY();
            for (ClaimView v : s.views) {
                for (PlusUnit u : v.pluses) {
                    if (follow && Math.abs((vy + yOffset) - u.y) > 0.75) {
                        move(u, vy + yOffset, size);
                    }
                    if (animate && pulse && !u.blocked && tick % cycle == 0) {
                        u.vertical.setInterpolationDelay(0);
                        u.vertical.setInterpolationDuration(Math.max(2, cycle * 5));
                        u.vertical.setTransformation(barTransform(size, true, factor));
                        u.horizontal.setInterpolationDelay(0);
                        u.horizontal.setInterpolationDuration(Math.max(2, cycle * 5));
                        u.horizontal.setTransformation(barTransform(size, false, factor));
                    }
                }
                if (follow && !v.beams.isEmpty() && Math.abs(vy - v.beamY) > 2.0) {
                    double h = Math.max(2, plugin.getConfig().getDouble("border.beam-height", 8));
                    for (Beam b : v.beams) {
                        Location l = b.display.getLocation();
                        l.setY(vy - h / 2.0);
                        b.display.teleport(l);
                    }
                    v.beamY = vy;
                }
            }
        }
        if (sessions.isEmpty()) {
            stopTicker();
        }
    }

    private void move(PlusUnit u, double newY, double size) {
        u.y = newY;
        for (BlockDisplay d : new BlockDisplay[]{u.plate, u.vertical, u.horizontal}) {
            Location l = d.getLocation();
            l.setY(newY);
            d.teleport(l);
        }
        Location h = u.hit.getLocation();
        h.setY(newY - size / 2.0);
        u.hit.teleport(h);
    }

    private void dispose(Session s) {
        for (Entity e : s.entities) {
            byEntity.remove(e.getUniqueId());
            e.remove();
        }
        s.entities.clear();
    }

    // ------------------------------------------------------------------ clicks

    /** A "+" was clicked (either mouse button). Debounced per player. */
    public void click(Player p, UUID entityId, boolean left) {
        PlusUnit u = byEntity.get(entityId);
        Session s = sessions.get(p.getUniqueId());
        if (u == null || s == null || findEntity(s, entityId) == null) {
            return;
        }
        if (left && !plugin.getConfig().getBoolean("border.click-left", true)) {
            return;
        }
        if (!left && !plugin.getConfig().getBoolean("border.click-right", true)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = clickCooldown.get(p.getUniqueId());
        if (last != null && now - last < plugin.getConfig().getLong("expansion.click-cooldown-ms", 400)) {
            return;
        }
        clickCooldown.put(p.getUniqueId(), now);
        Claim c = plugin.claims().get(u.claimId);
        if (c == null) {
            return;
        }
        if (u.blocked) {
            ExpansionService.Check chk = plugin.expansion().check(p, c, u.target);
            if (!chk.ok()) {
                plugin.expansion().explain(p, chk);
            }
            return;
        }
        plugin.expansion().requestTarget(p, c, u.target, false);
    }

    private Entity findEntity(Session s, UUID id) {
        for (Entity e : s.entities) {
            if (e.getUniqueId().equals(id)) {
                return e;
            }
        }
        return null;
    }

    public void forget(UUID player) {
        clickCooldown.remove(player);
    }

    public boolean isPlusEntity(UUID id) {
        return byEntity.containsKey(id);
    }
}
