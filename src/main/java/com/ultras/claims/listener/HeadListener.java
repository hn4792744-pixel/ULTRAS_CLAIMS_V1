package com.ultras.claims.listener;

import com.ultras.claims.UltrasClaims;
import com.ultras.claims.claim.Claim;
import com.ultras.claims.claim.MemberPermission;
import com.ultras.claims.claim.NotificationKey;
import com.ultras.claims.language.MessageChannel;
import com.ultras.claims.sound.SoundKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** Placing, using and breaking claim heads. */
public final class HeadListener implements Listener {
    private final UltrasClaims plugin;

    public HeadListener(UltrasClaims plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ placing

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        ItemStack item = e.getItemInHand();
        UUID headOwner = plugin.heads().ownerOf(item);
        if (headOwner == null) {
            return;
        }
        Player p = e.getPlayer();
        Block block = e.getBlockPlaced();
        String token = plugin.heads().tokenOf(item);
        if (!plugin.hasBypass(p) && !headOwner.equals(p.getUniqueId())) {
            refuse(e, p, "head-not-yours");
            return;
        }
        if (token == null || plugin.tokenUsed(token)) {
            // A copy of a head that was already placed once: never creates a claim, and the copy is removed.
            plugin.log().log("HEAD_DUPLICATE", "player=" + p.getName() + " token=" + token);
            refuse(e, p, "head-invalid");
            e.getItemInHand().setAmount(0);
            return;
        }
        if (plugin.worldDisabled(block.getWorld().getName())) {
            refuse(e, p, "world-disabled");
            return;
        }
        Claim existing = plugin.claims().at(block);
        if (existing != null) {
            if (isStale(existing)) {
                plugin.log().log("STALE_CLAIM_REMOVED", "claim=" + existing.id() + " (head block missing) while placing a new head");
                plugin.cabin().removeClaimFully(existing, false);
            } else {
                refuse(e, p, "head-chunk-claimed", "owner", existing.ownerName());
                return;
            }
        }
        int max = plugin.getConfig().getInt("limits.max-claims-owned", 2);
        if (plugin.claims().ownedCount(headOwner) >= max) {
            refuse(e, p, "head-limit-owned", "max", max);
            return;
        }
        String ownerName = headOwner.equals(p.getUniqueId()) ? p.getName() : nameOf(headOwner);
        Claim c = plugin.claims().create(headOwner, ownerName, block.getLocation());
        plugin.useToken(token, c.id());
        plugin.log().log("CLAIM_CREATED", "claim=" + c.id() + " owner=" + ownerName + " by=" + p.getName() + " at=" + c.world() + ","
                + c.headX() + "," + c.headY() + "," + c.headZ());
        plugin.messages().send(p, "claim-created", MessageChannel.CLAIM, "id", c.id());
        plugin.sounds().play(p, SoundKey.SUCCESS);
        plugin.holograms().refresh(c);
        plugin.borders().show(p, c);
    }

    private void refuse(BlockPlaceEvent e, Player p, String key, Object... ph) {
        e.setCancelled(true);
        plugin.messages().error(p, key, ph);
        plugin.sounds().play(p, SoundKey.ERROR);
    }

    private String nameOf(UUID id) {
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? id.toString().substring(0, 8) : n;
    }

    /** A claim whose head block no longer exists (removed by an external tool). Only judged when its chunk is loaded. */
    private boolean isStale(Claim c) {
        World w = Bukkit.getWorld(c.world());
        if (w == null || !w.isChunkLoaded(c.headX() >> 4, c.headZ() >> 4)) {
            return false;
        }
        Material m = w.getBlockAt(c.headX(), c.headY(), c.headZ()).getType();
        return m != Material.PLAYER_HEAD && m != Material.PLAYER_WALL_HEAD;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        if (e.isNewChunk() || !plugin.getConfig().getBoolean("head.verify-on-chunk-load", true)) {
            return;
        }
        String world = e.getWorld().getName();
        Claim c = plugin.claims().at(world, e.getChunk().getX(), e.getChunk().getZ());
        if (c != null && c.headChunk().x() == e.getChunk().getX() && c.headChunk().z() == e.getChunk().getZ() && isStale(c)) {
            plugin.log().log("STALE_CLAIM_REMOVED", "claim=" + c.id() + " (head block missing at chunk load)");
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (plugin.claims().get(c.id()) == c) {
                    plugin.cabin().removeClaimFully(c, false);
                }
            });
        }
    }

    // ------------------------------------------------------------------ using

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null || (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.LEFT_CLICK_BLOCK)) {
            return;
        }
        Material m = b.getType();
        if (m != Material.PLAYER_HEAD && m != Material.PLAYER_WALL_HEAD) {
            return;
        }
        Claim c = plugin.claims().byHead(b);
        if (c == null) {
            return;
        }
        Player p = e.getPlayer();
        boolean left = e.getAction() == Action.LEFT_CLICK_BLOCK;
        if (left && p.isSneaking() && plugin.getConfig().getBoolean("head.break-requires-sneak", true)) {
            return; // sneak + left click = break attempt, decided in onBreak
        }
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.OFF_HAND) {
            return;
        }
        plugin.gui().openClaim(p, c);
    }

    // ------------------------------------------------------------------ breaking

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Claim c = plugin.claims().byHead(b);
        if (c == null) {
            return;
        }
        e.setCancelled(true);
        Player p = e.getPlayer();
        boolean allowed = plugin.hasBypass(p) || c.isOwner(p.getUniqueId()) || c.hasPermission(p.getUniqueId(), MemberPermission.BREAK_HEAD);
        if (!allowed) {
            plugin.messages().error(p, "head-break-denied");
            plugin.sounds().play(p, SoundKey.ERROR);
            return;
        }
        if (plugin.getConfig().getBoolean("head.break-requires-sneak", true) && !p.isSneaking()) {
            plugin.messages().error(p, "head-break-sneak");
            return;
        }
        plugin.log().log("HEAD_BROKEN", "claim=" + c.id() + " owner=" + c.ownerName() + " by=" + p.getName());
        plugin.notifications().notify(c, NotificationKey.HEAD_BROKEN, "notify-head-broken", MessageChannel.WARNING, SoundKey.WARNING, p.getUniqueId(),
                "player", p.getName(), "id", c.id());
        // Remove everything first, then hand the head back as a brand-new item (a re-placed head starts from scratch).
        plugin.cabin().removeClaimFully(c, false);
        b.setType(Material.AIR, false);
        ItemStack head = plugin.heads().create(c.ownerUuid(), c.ownerName(), false, plugin.messages().languageOf(c.ownerUuid()));
        plugin.heads().dropFor(b.getWorld(), new Location(b.getWorld(), b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5), head, c.ownerUuid());
        plugin.messages().send(p, "head-broken", MessageChannel.CLAIM, "id", c.id());
        plugin.sounds().play(p, SoundKey.SUCCESS);
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent e) {
        for (ItemStack it : e.getInventory().getContents()) {
            if (plugin.heads().isHead(it)) {
                e.setResult(null);
                return;
            }
        }
    }
}
