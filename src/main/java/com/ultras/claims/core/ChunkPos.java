package com.ultras.claims.core;

/** Chunk coordinates (without world). */
public record ChunkPos(int x, int z) {
    public static ChunkPos ofBlock(int blockX, int blockZ) {
        return new ChunkPos(blockX >> 4, blockZ >> 4);
    }

    public long key() {
        return key(x, z);
    }

    public static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public ChunkPos step(Direction d) {
        return new ChunkPos(x + d.dx(), z + d.dz());
    }

    public int minBlockX() {
        return x << 4;
    }

    public int minBlockZ() {
        return z << 4;
    }
}
