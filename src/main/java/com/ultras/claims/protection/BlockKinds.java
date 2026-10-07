package com.ultras.claims.protection;

import com.ultras.claims.claim.MemberPermission;
import com.ultras.claims.claim.ProtectionKey;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Container;

/** Classifies a right-clicked block into the protection switch and member permission that govern it. */
public final class BlockKinds {
    public record Kind(ProtectionKey key, MemberPermission perm) {
    }

    private BlockKinds() {
    }

    /** Null when the block has no special use (a plain block). */
    public static Kind of(Block b) {
        Material m = b.getType();
        if (m == Material.ENDER_CHEST) {
            return null; // personal inventory, nothing to steal
        }
        if (Tag.DOORS.isTagged(m)) {
            return new Kind(ProtectionKey.DOOR, MemberPermission.DOORS);
        }
        if (Tag.TRAPDOORS.isTagged(m)) {
            return new Kind(ProtectionKey.TRAPDOOR, MemberPermission.DOORS);
        }
        if (Tag.FENCE_GATES.isTagged(m)) {
            return new Kind(ProtectionKey.FENCE_GATE, MemberPermission.DOORS);
        }
        if (Tag.BUTTONS.isTagged(m)) {
            return new Kind(ProtectionKey.BUTTON, MemberPermission.BUTTONS);
        }
        if (m == Material.LEVER) {
            return new Kind(ProtectionKey.LEVER, MemberPermission.LEVERS);
        }
        if (Tag.PRESSURE_PLATES.isTagged(m) || m == Material.REPEATER || m == Material.COMPARATOR || m == Material.DAYLIGHT_DETECTOR
                || m == Material.NOTE_BLOCK || m == Material.TRIPWIRE || m == Material.TRIPWIRE_HOOK || m == Material.TARGET
                || m == Material.REDSTONE_WIRE || m == Material.BELL) {
            return new Kind(ProtectionKey.REDSTONE, MemberPermission.REDSTONE);
        }
        if (m == Material.CHEST || m == Material.TRAPPED_CHEST) {
            return new Kind(ProtectionKey.CHEST, MemberPermission.CONTAINERS);
        }
        if (m == Material.BARREL) {
            return new Kind(ProtectionKey.BARREL, MemberPermission.CONTAINERS);
        }
        if (m == Material.HOPPER || m == Material.DROPPER || m == Material.DISPENSER) {
            return new Kind(ProtectionKey.HOPPER, MemberPermission.CONTAINERS);
        }
        if (isContainer(b)) {
            return new Kind(ProtectionKey.CONTAINER, MemberPermission.CONTAINERS);
        }
        if (m.isInteractable()) {
            // beds, crafting tables, anvils, jukeboxes ... : governed by "block place" for visitors and "use claim" for members
            return new Kind(ProtectionKey.BLOCK_PLACE, MemberPermission.USE_CLAIM);
        }
        return null;
    }

    private static boolean isContainer(Block b) {
        try {
            return b.getState(false) instanceof Container;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean isFireStarter(Material m) {
        return m == Material.FLINT_AND_STEEL || m == Material.FIRE_CHARGE;
    }

    /** Tools that change blocks without breaking them (tilling, stripping, flattening, brushing, shearing). */
    public static boolean isBlockTool(Material m) {
        String n = m.name();
        return n.endsWith("_HOE") || n.endsWith("_AXE") || n.endsWith("_SHOVEL") || m == Material.SHEARS || m == Material.BRUSH;
    }

    public static boolean isVehicleItem(Material m) {
        String n = m.name();
        return n.endsWith("_BOAT") || n.endsWith("_RAFT") || n.endsWith("MINECART");
    }

    public static boolean placesEntity(Material m) {
        String n = m.name();
        return n.endsWith("_SPAWN_EGG") || m == Material.ARMOR_STAND || m == Material.END_CRYSTAL || m == Material.ITEM_FRAME
                || m == Material.GLOW_ITEM_FRAME || m == Material.PAINTING || m == Material.BONE_MEAL;
    }
}
