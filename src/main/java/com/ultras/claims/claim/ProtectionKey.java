package com.ultras.claims.claim;

import java.util.Locale;

/**
 * Visitor protections of a claim. ON = visitors are blocked, OFF = visitors may do it.
 * Every key is an explicit switch; anything not mapped to a key is denied (default deny).
 */
public enum ProtectionKey {
    PVP(Category.COMBAT),
    BLOCK_BREAK(Category.BUILDING),
    BLOCK_PLACE(Category.BUILDING),
    BUCKET(Category.BUILDING),
    CONTAINER(Category.CONTAINERS),
    CHEST(Category.CONTAINERS),
    BARREL(Category.CONTAINERS),
    HOPPER(Category.CONTAINERS),
    DOOR(Category.REDSTONE),
    TRAPDOOR(Category.REDSTONE),
    FENCE_GATE(Category.REDSTONE),
    BUTTON(Category.REDSTONE),
    LEVER(Category.REDSTONE),
    REDSTONE(Category.REDSTONE),
    PISTONS(Category.REDSTONE),
    ITEM_PICKUP(Category.ENTITIES),
    ITEM_DROP(Category.ENTITIES),
    MOB_DAMAGE(Category.ENTITIES),
    ANIMAL_DAMAGE(Category.ENTITIES),
    VILLAGER_INTERACTION(Category.ENTITIES),
    VEHICLE(Category.ENTITIES),
    FISHING(Category.ENTITIES),
    FIRE(Category.WORLD),
    LAVA(Category.WORLD),
    WATER(Category.WORLD),
    EXPLOSIONS(Category.WORLD),
    TNT(Category.WORLD),
    CREEPER(Category.WORLD),
    ENDERMAN_GRIEF(Category.WORLD),
    CROP_TRAMPLE(Category.WORLD);

    public enum Category {
        COMBAT, BUILDING, CONTAINERS, REDSTONE, ENTITIES, WORLD
    }

    private final Category category;

    ProtectionKey(Category category) {
        this.category = category;
    }

    public Category category() {
        return category;
    }

    /** Key used in config.yml, messages and the database, e.g. block-break. */
    public String id() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static ProtectionKey fromId(String id) {
        for (ProtectionKey k : values()) {
            if (k.id().equalsIgnoreCase(id) || k.name().equalsIgnoreCase(id)) {
                return k;
            }
        }
        return null;
    }
}
