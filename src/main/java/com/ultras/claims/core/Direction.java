package com.ultras.claims.core;

/** The four expansion directions. */
public enum Direction {
    NORTH(0, -1, 180f),
    SOUTH(0, 1, 0f),
    EAST(1, 0, 270f),
    WEST(-1, 0, 90f);

    private final int dx;
    private final int dz;
    private final float yaw;

    Direction(int dx, int dz, float yaw) {
        this.dx = dx;
        this.dz = dz;
        this.yaw = yaw;
    }

    public int dx() {
        return dx;
    }

    public int dz() {
        return dz;
    }

    /** Minecraft yaw of an object that faces this direction. */
    public float yaw() {
        return yaw;
    }

    public Direction opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST -> WEST;
            case WEST -> EAST;
        };
    }
}
