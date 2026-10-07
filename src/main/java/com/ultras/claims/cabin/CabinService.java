package com.ultras.claims.cabin;

import com.ultras.claims.UltrasClaims;
import com.ultras.claims.claim.Claim;
import com.ultras.claims.claim.ClaimState;
import com.ultras.claims.claim.MemberPermission;
import com.ultras.claims.claim.NotificationKey;
import com.ultras.claims.core.CabinMath;
import com.ultras.claims.core.TimeFormat;
import com.ultras.claims.language.MessageChannel;
import com.ultras.claims.sound.SoundKey;
import com.ultras.claims.storage.Storage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The cabin: players hand in items (kept server-side as virtual deposits, never as real inventory contents, so no
 * item can be duplicated or stolen through a menu) and the claim's protection time grows according to config rules.
 * It also owns the expiry timeline: ACTIVE -> warnings -> GRACE (only the old owner can renew) -> head breaks.
 */
public final class CabinService {
    /** One player's items handed in for one claim. */
    public record Orphan(String claimId, UUID player, Map<String, Integer> items) {
    }

    private static final long FOREVER_SECONDS = 100L * 365 * 86_400;

    private final UltrasClaims plugin;
    private volatile Map<String, CabinMath.Rule> rules = Map.of();
    private final Map<String, Map<String, Integer>> deposits = new HashMap<>();
    private final Map<String, BukkitTask> timers = new HashMap<>();
    private final Map<UUID, List<Orphan>> orphans = new HashMap<>();
    private BukkitTask ticker;

    public CabinService(UltrasClaims plugin) {
        this.plugin = plugin;
        loadRules();
    }

    // ------------------------------------------------------------------ config

    private void loadRules() {
        Map<String, CabinMath.Rule> fresh = new LinkedHashMap<>();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("cabin.items");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                Material m = Material.matchMaterial(key);
                ConfigurationSection r = sec.getConfigurationSection(key);
                if (m == null || !m.isItem() || r == null) {
                    plugin.getLogger().warning("config.yml: cabin.items." + key + " is not a valid item rule - ignored");
                    continue;
                }
                long seconds = seconds(r.get("time"), 0);
                fresh.put(m.name(), new CabinMath.Rule(r.getInt("minimum", 1), seconds));
            }
        }
        rules = fresh;
    }

    private static long seconds(Object v, long def) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v != null) {
            long p = TimeFormat.parse(String.valueOf(v));
            if (p >= 0) {
                return p;
            }
        }
        return def;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("cabin.enabled", true);
    }

    public boolean requireConfirmation() {
        return plugin.getConfig().getBoolean("cabin.require-confirmation", false);
    }

    public Map<String, CabinMath.Rule> rules() {
        return rules;
    }

    public long startSeconds() {
        if (!enabled()) {
            return FOREVER_SECONDS;
        }
        return seconds(plugin.getConfig().get("cabin.initial-time"), 86_400);
    }

    public long maxSeconds() {
        return seconds(plugin.getConfig().get("cabin.max-time"), 30 * 86_400L);
    }

    public long graceSeconds() {
        return Math.max(0, plugin.getConfig().getLong("expiry.grace-minutes", 30)) * 60;
    }

    private long processDelayTicks() {
        return Math.max(1, plugin.getConfig().getLong("cabin.process-delay-seconds", 10)) * 20;
    }

    private long warnStartSeconds() {
        return Math.max(0, plugin.getConfig().getLong("cabin.warning.start-minutes", 60)) * 60;
    }

    private long warnStepSeconds() {
        return Math.max(1, plugin.getConfig().getLong("cabin.warning.interval-minutes", 10)) * 60;
    }

    // ------------------------------------------------------------------ lifecycle

    public void start() throws Exception {
        for (Storage.CabinRow row : plugin.storage().loadCabinRows()) {
            Map<String, Integer> items = decode(row.data());
            if (items.isEmpty()) {
                plugin.storage().saveCabinSnapshot(row.claimId(), row.player(), null);
                continue;
            }
            if (plugin.claims().get(row.claimId()) != null) {
                deposits.put(key(row.claimId(), row.player()), items);
            } else {
                orphans.computeIfAbsent(row.player(), u -> new ArrayList<>()).add(new Orphan(row.claimId(), row.player(), items));
            }
        }
        reschedule();
    }

    /** Called once the claims are loaded: schedules the processing of restored deposits and starts the expiry clock. */
    private void reschedule() {
        if (ticker != null) {
            ticker.cancel();
        }
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 100L);
        if (!requireConfirmation()) {
            for (String k : new ArrayList<>(deposits.keySet())) {
                scheduleProcess(k);
            }
        }
    }

    public void stop() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        timers.values().forEach(BukkitTask::cancel);
        timers.clear();
    }

    public void reload() {
        loadRules();
        stop();
        if (enabled()) {
            long now = System.currentTimeMillis();
            long lifted = now + startSeconds() * 1000L;
            for (Claim c : plugin.claims().all()) {
                if (c.protectionEndsAt() > now + FOREVER_SECONDS * 500L) { // was created while the cabin was disabled
                    c.protectionEndsAt(lifted);
                    plugin.claims().save(c);
                }
            }
        }
        reschedule();
    }

    private static String key(String claimId, UUID player) {
        return claimId + ':' + player;
    }

    // ------------------------------------------------------------------ queries

    public long remainingSeconds(Claim c) {
        if (!enabled()) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, (c.protectionEndsAt() - System.currentTimeMillis()) / 1000L);
    }

    public Map<String, Integer> depositsOf(Claim c, UUID player) {
        Map<String, Integer> m = deposits.get(key(c.id(), player));
        return m == null ? Map.of() : Map.copyOf(m);
    }

    public boolean canUse(Player p, Claim c) {
        if (!enabled()) {
            return false;
        }
        if (plugin.hasBypass(p) || c.isOwner(p.getUniqueId())) {
            return true;
        }
        return c.state() == ClaimState.ACTIVE && c.hasPermission(p.getUniqueId(), MemberPermission.USE_CLAIM);
    }

    /** Is this exact stack something the cabin takes? Items with names/enchants/lore are never taken (they would be lost). */
    public boolean accepts(ItemStack s) {
        return s != null && !s.getType().isAir() && !s.hasItemMeta() && rules.containsKey(s.getType().name());
    }

    // ------------------------------------------------------------------ deposits

    /**
     * Credits items that the caller has ALREADY removed from the player's inventory in the same tick.
     * Returns false (and gives nothing) when the cabin cannot take them; the caller then keeps the items.
     */
    public boolean credit(Player p, Claim c, Material m, int amount) {
        if (amount <= 0 || !canUse(p, c) || !rules.containsKey(m.name())) {
            return false;
        }
        String k = key(c.id(), p.getUniqueId());
        deposits.computeIfAbsent(k, x -> new LinkedHashMap<>()).merge(m.name(), amount, Integer::sum);
        persist(c, p.getUniqueId());
        plugin.log().log("CABIN_DEPOSIT", "claim=" + c.id() + " player=" + p.getName() + " item=" + m.name() + " x" + amount);
        if (!requireConfirmation()) {
            scheduleProcess(k);
        }
        return true;
    }

    /** Gives one material back to the player. */
    public int withdraw(Player p, Claim c, String material) {
        String k = key(c.id(), p.getUniqueId());
        Map<String, Integer> dep = deposits.get(k);
        if (dep == null) {
            return 0;
        }
        Integer n = dep.remove(material);
        if (n == null || n <= 0) {
            return 0;
        }
        Material m = Material.matchMaterial(material);
        if (m != null) {
            giveBack(p, m, n);
        }
        if (dep.isEmpty()) {
            deposits.remove(k);
            cancelTimer(k);
        }
        persist(c, p.getUniqueId());
        plugin.log().log("CABIN_WITHDRAW", "claim=" + c.id() + " player=" + p.getName() + " item=" + material + " x" + n);
        return n;
    }

    private void giveBack(Player p, Material m, int n) {
        int max = m.getMaxStackSize();
        while (n > 0) {
            int part = Math.min(max, n);
            n -= part;
            for (ItemStack rest : p.getInventory().addItem(new ItemStack(m, part)).values()) {
                p.getWorld().dropItem(p.getLocation(), rest);
            }
        }
    }

    private void persist(Claim c, UUID player) {
        Map<String, Integer> dep = deposits.get(key(c.id(), player));
        plugin.storage().saveCabinSnapshot(c.id(), player, dep == null || dep.isEmpty() ? null : encode(dep));
    }

    private void scheduleProcess(String k) {
        cancelTimer(k);
        timers.put(k, Bukkit.getScheduler().runTaskLater(plugin, () -> {
            timers.remove(k);
            int i = k.indexOf(':');
            Claim c = plugin.claims().get(k.substring(0, i));
            if (c != null) {
                process(c, UUID.fromString(k.substring(i + 1)), false);
            }
        }, processDelayTicks()));
    }

    private void cancelTimer(String k) {
        BukkitTask t = timers.remove(k);
        if (t != null) {
            t.cancel();
        }
    }

    /** Confirm button (or the timer): verify, take the minimum amounts, add the time, log, refresh. */
    public void process(Claim c, UUID player, boolean manual) {
        Player online = Bukkit.getPlayer(player);
        if (!enabled() || plugin.claims().get(c.id()) != c) {
            return;
        }
        String k = key(c.id(), player);
        Map<String, Integer> dep = deposits.get(k);
        if (dep == null || dep.isEmpty()) {
            return;
        }
        boolean owner = c.isOwner(player);
        if (c.state() == ClaimState.GRACE && !owner && (online == null || !plugin.hasBypass(online))) {
            if (online != null) {
                plugin.messages().error(online, "cabin-grace-owner-only");
            }
            return;
        }
        long remaining = remainingSeconds(c);
        long cap = c.state() == ClaimState.GRACE ? maxSeconds() : maxSeconds() - remaining;
        if (cap <= 0) {
            if (online != null) {
                plugin.messages().error(online, "cabin-full", "max", TimeFormat.format(maxSeconds(), plugin.messages().arabic(online)));
            }
            return;
        }
        CabinMath.Result r = CabinMath.compute(rules, dep, cap);
        if (r.isEmpty() || r.seconds() <= 0) {
            if (manual && online != null) {
                plugin.messages().error(online, "cabin-nothing");
            }
            return;
        }
        for (Map.Entry<String, Integer> e : r.consumed().entrySet()) {
            dep.merge(e.getKey(), -e.getValue(), Integer::sum);
        }
        dep.values().removeIf(v -> v <= 0);
        boolean fromGrace = c.state() == ClaimState.GRACE;
        applyTime(c, r.seconds(), fromGrace);
        String left = dep.isEmpty() ? null : encode(dep);
        if (dep.isEmpty()) {
            deposits.remove(k);
        }
        plugin.storage().commitCabin(c.copy(), player, left);
        String who = online != null ? online.getName() : String.valueOf(player);
        plugin.log().log("CABIN_PROCESSED", "claim=" + c.id() + " player=" + who + " consumed=" + encode(r.consumed()) + " +" + r.seconds() + "s");
        afterTimeChange(c, online, r.seconds(), fromGrace);
    }

    /** Shared by the cabin and manual renewal: only time and state change here. */
    private void applyTime(Claim c, long seconds, boolean fromGrace) {
        long now = System.currentTimeMillis();
        long base = fromGrace ? now : Math.max(now, c.protectionEndsAt());
        c.protectionEndsAt(Math.min(base + seconds * 1000L, now + maxSeconds() * 1000L));
        c.lastRenewalAt(now);
        c.state(ClaimState.ACTIVE);
        c.graceEndsAt(0);
        if (remainingSeconds(c) > warnStartSeconds()) {
            c.warnBucket(Integer.MAX_VALUE);
        }
    }

    private void afterTimeChange(Claim c, Player actor, long added, boolean fromGrace) {
        if (actor != null) {
            boolean ar = plugin.messages().arabic(actor);
            plugin.messages().send(actor, "cabin-processed", MessageChannel.CLAIM, "time", TimeFormat.format(added, ar),
                    "remaining", TimeFormat.format(remainingSeconds(c), ar));
            plugin.sounds().play(actor, SoundKey.RENEWAL);
        }
        UUID exclude = actor == null ? null : actor.getUniqueId();
        plugin.notifications().notify(c, NotificationKey.CABIN_RENEWED, "notify-cabin-renewed", MessageChannel.CLAIM, SoundKey.RENEWAL, exclude,
                "time", TimeFormat.format(remainingSeconds(c), false));
        if (fromGrace) {
            plugin.notifications().notify(c, NotificationKey.CLAIM_RENEWED, "notify-claim-renewed", MessageChannel.CLAIM, SoundKey.RENEWAL, exclude);
        }
        plugin.holograms().refresh(c);
        plugin.gui().refreshCabin(c);
    }

    /** Admin renewal (command / cabin menu): adds a fixed amount without items. */
    public void renewManual(Player actor, Claim c, long seconds) {
        boolean fromGrace = c.state() == ClaimState.GRACE;
        applyTime(c, seconds, fromGrace);
        plugin.claims().save(c);
        plugin.log().log("CLAIM_RENEWED_MANUAL", "claim=" + c.id() + " by=" + actor.getName() + " +" + seconds + "s");
        afterTimeChange(c, actor, seconds, fromGrace);
    }

    // ------------------------------------------------------------------ expiry clock

    private void tick() {
        if (!enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Claim c : new ArrayList<>(plugin.claims().all())) {
            if (c.state() == ClaimState.ACTIVE) {
                long rem = Math.max(0, (c.protectionEndsAt() - now) / 1000L);
                if (rem <= 0) {
                    enterGrace(c, now);
                } else if (rem <= warnStartSeconds()) {
                    int bucket = (int) (rem / warnStepSeconds());
                    if (bucket < c.warnBucket()) {
                        c.warnBucket(bucket);
                        plugin.notifications().notify(c, NotificationKey.CABIN_WARNING, "notify-cabin-warning", MessageChannel.WARNING, SoundKey.WARNING, null,
                                "time", TimeFormat.format(rem, false), "id", c.id());
                    }
                }
            } else if (now >= c.graceEndsAt()) {
                finalBreak(c);
            }
        }
    }

    private void enterGrace(Claim c, long now) {
        c.state(ClaimState.GRACE);
        c.graceEndsAt(now + graceSeconds() * 1000L);
        plugin.claims().save(c);
        plugin.log().log("CLAIM_EXPIRED", "claim=" + c.id() + " owner=" + c.ownerName() + " grace-ends=" + c.graceEndsAt());
        long minutes = Math.max(1, graceSeconds() / 60);
        plugin.notifications().notify(c, NotificationKey.CABIN_EMPTY, "notify-cabin-empty", MessageChannel.WARNING, SoundKey.WARNING, null);
        plugin.notifications().notify(c, NotificationKey.CLAIM_EXPIRED, "notify-claim-expired", MessageChannel.WARNING, SoundKey.WARNING, null,
                "minutes", minutes, "id", c.id());
        plugin.holograms().refresh(c);
        plugin.gui().refreshCabin(c);
    }

    /** Grace is over: the claim disappears, the head block breaks and drops as a fresh item for the old owner. */
    private void finalBreak(Claim c) {
        plugin.log().log("CLAIM_REMOVED_EXPIRY", "claim=" + c.id() + " owner=" + c.ownerName() + " chunks=" + c.chunks().size());
        plugin.notifications().notify(c, NotificationKey.HEAD_BROKEN, "notify-head-expired", MessageChannel.WARNING, SoundKey.WARNING, null, "id", c.id());
        removeClaimFully(c, true);
    }

    /**
     * Removes a claim and everything attached to it. {@code breakHead}: remove the head block in the world and drop a
     * brand-new head for the owner (expiry). Owner/admin breaking the head handles the block and the item itself.
     */
    public void removeClaimFully(Claim c, boolean breakHead) {
        onClaimGone(c);
        plugin.borders().onClaimRemoved(c);
        plugin.holograms().remove(c);
        plugin.gui().closeFor(c);
        plugin.claims().remove(c);
        if (!breakHead) {
            return;
        }
        org.bukkit.World w = Bukkit.getWorld(c.world());
        if (w == null) {
            return;
        }
        w.getChunkAtAsync(c.headX() >> 4, c.headZ() >> 4).thenAccept(chunk -> Bukkit.getScheduler().runTask(plugin, () -> {
            var b = w.getBlockAt(c.headX(), c.headY(), c.headZ());
            if (b.getType() == Material.PLAYER_HEAD || b.getType() == Material.PLAYER_WALL_HEAD) {
                b.setType(Material.AIR, false);
            }
            ItemStack head = plugin.heads().create(c.ownerUuid(), c.ownerName(), false, plugin.messages().languageOf(c.ownerUuid()));
            plugin.heads().dropFor(w, new Location(w, c.headX() + 0.5, c.headY() + 0.5, c.headZ() + 0.5), head, c.ownerUuid());
        }));
    }

    // ------------------------------------------------------------------ leftovers

    /** The claim is gone: hand every deposited item back (now, or when the depositor next joins). */
    private void onClaimGone(Claim c) {
        String prefix = c.id() + ':';
        for (String k : new ArrayList<>(deposits.keySet())) {
            if (!k.startsWith(prefix)) {
                continue;
            }
            UUID player = UUID.fromString(k.substring(prefix.length()));
            Map<String, Integer> items = deposits.remove(k);
            cancelTimer(k);
            Player online = Bukkit.getPlayer(player);
            if (online != null) {
                returnItems(online, items);
                plugin.storage().saveCabinSnapshot(c.id(), player, null);
            } else if (items != null && !items.isEmpty()) {
                orphans.computeIfAbsent(player, u -> new ArrayList<>()).add(new Orphan(c.id(), player, items));
            }
        }
    }

    private void returnItems(Player p, Map<String, Integer> items) {
        for (Map.Entry<String, Integer> e : items.entrySet()) {
            Material m = Material.matchMaterial(e.getKey());
            if (m != null) {
                giveBack(p, m, e.getValue());
            }
        }
        plugin.messages().send(p, "cabin-items-returned", MessageChannel.ALWAYS);
    }

    public void restoreOnJoin(Player p) {
        List<Orphan> list = orphans.remove(p.getUniqueId());
        if (list == null) {
            return;
        }
        for (Orphan o : list) {
            returnItems(p, o.items());
            plugin.storage().saveCabinSnapshot(o.claimId(), o.player(), null);
            plugin.log().log("CABIN_RETURNED", "claim=" + o.claimId() + " player=" + p.getName() + " items=" + encode(o.items()));
        }
    }

    // ------------------------------------------------------------------ text encoding

    static String encode(Map<String, Integer> m) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : m.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) {
                if (!sb.isEmpty()) {
                    sb.append(';');
                }
                sb.append(e.getKey()).append('=').append(e.getValue());
            }
        }
        return sb.toString();
    }

    static Map<String, Integer> decode(String s) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (s == null || s.isBlank()) {
            return out;
        }
        for (String part : s.split(";")) {
            int i = part.indexOf('=');
            if (i <= 0) {
                continue;
            }
            try {
                int n = Integer.parseInt(part.substring(i + 1));
                if (n > 0) {
                    out.put(part.substring(0, i).toUpperCase(Locale.ROOT), n);
                }
            } catch (NumberFormatException ignored) {
                // a damaged entry is skipped; the rest is kept
            }
        }
        return out;
    }
}
