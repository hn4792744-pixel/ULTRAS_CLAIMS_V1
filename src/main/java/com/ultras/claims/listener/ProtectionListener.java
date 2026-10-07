package com.ultras.claims.listener;

import com.ultras.claims.UltrasClaims;
import com.ultras.claims.claim.Claim;
import com.ultras.claims.claim.MemberPermission;
import com.ultras.claims.claim.ProtectionKey;
import com.ultras.claims.protection.BlockKinds;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.entity.Wither;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;
import java.util.List;

/** Every protection rule in one place. All decisions go through ProtectionService (admin > owner > member > visitor > deny). */
public final class ProtectionListener implements Listener {
    private final UltrasClaims plugin;

    public ProtectionListener(UltrasClaims plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ helpers

    private Claim at(Block b) {
        return plugin.claims().at(b);
    }

    private boolean block(Player p, Cancellable e, Claim c, ProtectionKey key, MemberPermission perm) {
        if (plugin.protection().denies(p, c, key, perm)) {
            e.setCancelled(true);
            return true;
        }
        return false;
    }

    private boolean blockSilently(Player p, Cancellable e, Claim c, ProtectionKey key, MemberPermission perm) {
        if (!plugin.protection().allowed(p, c, key, perm)) {
            e.setCancelled(true);
            return true;
        }
        return false;
    }

    private Player shooter(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }

    private boolean isHead(Block b) {
        Material m = b.getType();
        return (m == Material.PLAYER_HEAD || m == Material.PLAYER_WALL_HEAD) && plugin.claims().byHead(b) != null;
    }

    private boolean envBlocks(Block b, ProtectionKey... keys) {
        Claim c = at(b);
        if (c == null || !c.isProtected()) {
            return false;
        }
        for (ProtectionKey k : keys) {
            if (!c.visitorsAllowed(k)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ building

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (isHead(e.getBlock())) {
            return; // HeadListener
        }
        block(e.getPlayer(), e, at(e.getBlock()), ProtectionKey.BLOCK_BREAK, MemberPermission.BREAK);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (plugin.heads().isHead(e.getItemInHand())) {
            return; // HeadListener decides about heads
        }
        block(e.getPlayer(), e, at(e.getBlock()), ProtectionKey.BLOCK_PLACE, MemberPermission.BUILD);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        block(e.getPlayer(), e, at(e.getBlock()), ProtectionKey.BUCKET, MemberPermission.BUILD);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        block(e.getPlayer(), e, at(e.getBlock()), ProtectionKey.BUCKET, MemberPermission.BUILD);
    }

    // ------------------------------------------------------------------ interaction

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Block b = e.getClickedBlock();
        if (b == null) {
            return;
        }
        if (e.getAction() == Action.PHYSICAL) {
            Claim c = at(b);
            if (c == null) {
                return;
            }
            if (b.getType() == Material.FARMLAND) {
                blockSilently(p, e, c, ProtectionKey.CROP_TRAMPLE, MemberPermission.BREAK);
            } else {
                BlockKinds.Kind k = BlockKinds.of(b);
                if (k != null) {
                    blockSilently(p, e, c, k.key(), k.perm());
                }
            }
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || isHead(b)) {
            return;
        }
        ItemStack hand = e.getItem();
        boolean holding = hand != null && !hand.getType().isAir();
        Claim clicked = at(b);
        BlockKinds.Kind kind = BlockKinds.of(b);
        if (kind != null && clicked != null && !(p.isSneaking() && holding)) {
            if (block(p, e, clicked, kind.key(), kind.perm())) {
                return;
            }
        }
        if (!holding) {
            return;
        }
        Claim target = at(b.getRelative(e.getBlockFace()));
        Material m = hand.getType();
        if (BlockKinds.isFireStarter(m)) {
            block(p, e, target, ProtectionKey.FIRE, MemberPermission.BUILD);
        } else if (BlockKinds.isVehicleItem(m)) {
            block(p, e, target, ProtectionKey.VEHICLE, MemberPermission.USE_CLAIM);
        } else if (BlockKinds.placesEntity(m)) {
            block(p, e, target, ProtectionKey.BLOCK_PLACE, MemberPermission.BUILD);
        } else if (BlockKinds.isBlockTool(m) || m == Material.BONE_MEAL) {
            block(p, e, clicked, ProtectionKey.BLOCK_PLACE, MemberPermission.BUILD);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Entity t = e.getRightClicked();
        if (t instanceof Interaction || t instanceof Player) {
            return;
        }
        Claim c = plugin.claims().at(t.getLocation());
        Player p = e.getPlayer();
        if (t instanceof Villager || t instanceof WanderingTrader) {
            block(p, e, c, ProtectionKey.VILLAGER_INTERACTION, MemberPermission.VILLAGERS);
        } else if (t instanceof StorageMinecart || t instanceof HopperMinecart) {
            block(p, e, c, ProtectionKey.CONTAINER, MemberPermission.CONTAINERS);
        } else if (t instanceof Vehicle || t instanceof AbstractHorse) {
            block(p, e, c, ProtectionKey.VEHICLE, t instanceof AbstractHorse ? MemberPermission.ANIMALS : MemberPermission.USE_CLAIM);
        } else if (t instanceof Animals) {
            block(p, e, c, ProtectionKey.ANIMAL_DAMAGE, MemberPermission.ANIMALS);
        } else if (t instanceof Hanging || t instanceof ArmorStand) {
            block(p, e, c, ProtectionKey.BLOCK_PLACE, MemberPermission.BUILD);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        block(e.getPlayer(), e, plugin.claims().at(e.getRightClicked().getLocation()), ProtectionKey.BLOCK_PLACE, MemberPermission.BUILD);
    }

    // ------------------------------------------------------------------ combat

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Entity victim = e.getEntity();
        if (victim instanceof Interaction) {
            return;
        }
        Player p = shooter(e.getDamager());
        if (p == null) {
            return;
        }
        Claim c = plugin.claims().at(victim.getLocation());
        if (c == null) {
            return;
        }
        boolean cancelled;
        if (victim instanceof Player other) {
            if (other.equals(p)) {
                return;
            }
            cancelled = deny(p, e, c, ProtectionKey.PVP, null);
        } else if (victim instanceof Villager || victim instanceof WanderingTrader) {
            cancelled = deny(p, e, c, ProtectionKey.VILLAGER_INTERACTION, MemberPermission.VILLAGERS);
        } else if (victim instanceof Animals) {
            cancelled = deny(p, e, c, ProtectionKey.ANIMAL_DAMAGE, MemberPermission.ANIMALS);
        } else if (victim instanceof Hanging || victim instanceof ArmorStand || victim instanceof EnderCrystal) {
            cancelled = deny(p, e, c, ProtectionKey.BLOCK_BREAK, MemberPermission.BREAK);
        } else if (victim instanceof Monster || victim instanceof org.bukkit.entity.LivingEntity) {
            cancelled = deny(p, e, c, ProtectionKey.MOB_DAMAGE, MemberPermission.ANIMALS);
        } else {
            cancelled = false;
        }
        if (cancelled && e.getDamager() instanceof Projectile pr) {
            pr.remove();
        }
    }

    private boolean deny(Player p, Cancellable e, Claim c, ProtectionKey key, MemberPermission perm) {
        return block(p, e, c, key, perm);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreakByEntity(HangingBreakByEntityEvent e) {
        Player p = shooter(e.getRemover());
        if (p != null) {
            block(p, e, plugin.claims().at(e.getEntity().getLocation()), ProtectionKey.BLOCK_BREAK, MemberPermission.BREAK);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent e) {
        if (e.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION
                && plugin.protection().blocksEnvironment(plugin.claims().at(e.getEntity().getLocation()), ProtectionKey.EXPLOSIONS)) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ items, vehicles, fishing, teleport

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p) {
            if (plugin.heads().isHead(e.getItem().getItemStack())) {
                return;
            }
            blockSilently(p, e, plugin.claims().at(e.getItem().getLocation()), ProtectionKey.ITEM_PICKUP, MemberPermission.PICKUP);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        block(e.getPlayer(), e, plugin.claims().at(e.getPlayer().getLocation()), ProtectionKey.ITEM_DROP, MemberPermission.DROP);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent e) {
        if (e.getEntered() instanceof Player p) {
            block(p, e, plugin.claims().at(e.getVehicle().getLocation()), ProtectionKey.VEHICLE, MemberPermission.USE_CLAIM);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent e) {
        Player p = e.getAttacker() == null ? null : shooter(e.getAttacker());
        if (p != null) {
            block(p, e, plugin.claims().at(e.getVehicle().getLocation()), ProtectionKey.VEHICLE, MemberPermission.BREAK);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        PlayerFishEvent.State s = e.getState();
        if (s == PlayerFishEvent.State.FISHING || s == PlayerFishEvent.State.CAUGHT_ENTITY) {
            Claim c = plugin.claims().at(e.getHook().getLocation());
            block(e.getPlayer(), e, c, ProtectionKey.FISHING, MemberPermission.USE_CLAIM);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        PlayerTeleportEvent.TeleportCause cause = e.getCause();
        if ((cause != PlayerTeleportEvent.TeleportCause.ENDER_PEARL && cause != PlayerTeleportEvent.TeleportCause.CONSUMABLE_EFFECT)
                || !plugin.getConfig().getBoolean("protection.block-teleport-into-claims", true) || e.getTo() == null) {
            return;
        }
        Player p = e.getPlayer();
        Claim c = plugin.claims().at(e.getTo());
        if (c != null && c.isProtected() && !plugin.hasBypass(p) && !c.isOwner(p.getUniqueId()) && !c.hasPermission(p.getUniqueId(), MemberPermission.TELEPORT)) {
            e.setCancelled(true);
            plugin.protection().deny(p, c);
        }
    }

    // ------------------------------------------------------------------ explosions, fire, fluids, growth

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        ProtectionKey specific = e.getEntity() instanceof Creeper ? ProtectionKey.CREEPER
                : e.getEntity() instanceof TNTPrimed || e.getEntity() instanceof ExplosiveMinecart ? ProtectionKey.TNT : null;
        filterExplosion(e.blockList(), specific);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        filterExplosion(e.blockList(), null);
    }

    private void filterExplosion(List<Block> blocks, ProtectionKey specific) {
        Iterator<Block> it = blocks.iterator();
        while (it.hasNext()) {
            Block b = it.next();
            if (isHead(b) || (specific != null ? envBlocks(b, ProtectionKey.EXPLOSIONS, specific) : envBlocks(b, ProtectionKey.EXPLOSIONS))) {
                it.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        Player p = e.getPlayer();
        if (p == null && e.getIgnitingEntity() != null) {
            p = shooter(e.getIgnitingEntity());
        }
        Claim c = at(e.getBlock());
        if (p != null) {
            block(p, e, c, ProtectionKey.FIRE, MemberPermission.BUILD);
        } else if (plugin.protection().blocksEnvironment(c, ProtectionKey.FIRE)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (isHead(e.getBlock()) || envBlocks(e.getBlock(), ProtectionKey.FIRE)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        Block from = e.getBlock();
        Block to = e.getToBlock();
        if ((from.getX() >> 4) == (to.getX() >> 4) && (from.getZ() >> 4) == (to.getZ() >> 4)) {
            return;
        }
        Claim target = at(to);
        if (target == null || !target.isProtected()) {
            return;
        }
        Claim source = at(from);
        if (source != null && source.ownerUuid().equals(target.ownerUuid())) {
            return;
        }
        Material m = from.getType();
        ProtectionKey key = m == Material.LAVA ? ProtectionKey.LAVA : m == Material.WATER ? ProtectionKey.WATER : null;
        if (key != null && !target.visitorsAllowed(key)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (pistonBlocked(e.getBlock(), e.getBlocks(), e.getDirection())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (pistonBlocked(e.getBlock(), e.getBlocks(), e.getDirection().getOppositeFace())) {
            e.setCancelled(true);
        }
    }

    private boolean pistonBlocked(Block piston, List<Block> moved, BlockFace dir) {
        Claim home = at(piston);
        // the piston arm itself also reaches one block further than the pushed blocks
        List<Block> touched = new java.util.ArrayList<>();
        for (Block b : moved) {
            if (isHead(b)) {
                return true;
            }
            touched.add(b);
            touched.add(b.getRelative(dir));
        }
        touched.add(piston.getRelative(dir));
        for (Block b : touched) {
            Claim c = at(b);
            if (c == null || c == home || !c.isProtected() || c.visitorsAllowed(ProtectionKey.PISTONS)) {
                continue;
            }
            if (home != null && home.ownerUuid().equals(c.ownerUuid())) {
                continue;
            }
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent e) {
        Material m = e.getBlock().getType();
        if ((m == Material.PLAYER_HEAD || m == Material.PLAYER_WALL_HEAD) && plugin.claims().byHead(e.getBlock()) != null) {
            e.setCancelled(true); // a head never pops off because its support moved
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        Block b = e.getBlock();
        if (isHead(b)) {
            e.setCancelled(true);
            return;
        }
        Entity en = e.getEntity();
        if (en instanceof Enderman) {
            if (envBlocks(b, ProtectionKey.ENDERMAN_GRIEF)) {
                e.setCancelled(true);
            }
        } else if (en instanceof Wither) {
            if (envBlocks(b, ProtectionKey.EXPLOSIONS)) {
                e.setCancelled(true);
            }
        } else if (b.getType() == Material.FARMLAND && !(en instanceof Player)) {
            if (envBlocks(b, ProtectionKey.CROP_TRAMPLE)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent e) {
        Claim home = at(e.getLocation().getBlock());
        Player p = e.getPlayer();
        e.getBlocks().removeIf(state -> {
            Claim c = plugin.claims().at(state.getBlock());
            if (c == null || c == home || !c.isProtected()) {
                return false;
            }
            if (p != null && plugin.protection().allowed(p, c, ProtectionKey.BLOCK_PLACE, MemberPermission.BUILD)) {
                return false;
            }
            return !c.visitorsAllowed(ProtectionKey.BLOCK_PLACE) && (home == null || !home.ownerUuid().equals(c.ownerUuid()));
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent e) {
        if (plugin.heads().isHead(e.getItem())) {
            e.setCancelled(true);
            return;
        }
        if (!(e.getBlock().getBlockData() instanceof org.bukkit.block.data.Directional d)) {
            return;
        }
        Block target = e.getBlock().getRelative(d.getFacing());
        Claim c = at(target);
        Claim home = at(e.getBlock());
        if (c != null && c != home && c.isProtected() && !c.visitorsAllowed(ProtectionKey.BLOCK_PLACE)
                && (home == null || !home.ownerUuid().equals(c.ownerUuid()))) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ hoppers

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent e) {
        if (plugin.claims().all().isEmpty()) {
            return;
        }
        var src = e.getSource().getLocation();
        var dst = e.getDestination().getLocation();
        if (src == null || dst == null) {
            return;
        }
        Claim a = plugin.claims().at(src);
        Claim b = plugin.claims().at(dst);
        if (a == b) {
            return;
        }
        if (a != null && b != null && a.ownerUuid().equals(b.ownerUuid())) {
            return;
        }
        var il = e.getInitiator().getLocation();
        Claim init = il == null ? null : plugin.claims().at(il);
        if ((a != init && plugin.protection().blocksEnvironment(a, ProtectionKey.HOPPER))
                || (b != init && plugin.protection().blocksEnvironment(b, ProtectionKey.HOPPER))) {
            e.setCancelled(true);
        }
    }
}
