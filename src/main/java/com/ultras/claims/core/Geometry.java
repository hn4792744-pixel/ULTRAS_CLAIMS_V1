package com.ultras.claims.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Pure chunk-set geometry used by borders, expansion and the map. */
public final class Geometry {
    private Geometry() {
    }

    /** minX, maxX, minZ, maxZ in chunk coordinates. */
    public static int[] bounds(Set<ChunkPos> chunks) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (ChunkPos c : chunks) {
            minX = Math.min(minX, c.x());
            maxX = Math.max(maxX, c.x());
            minZ = Math.min(minZ, c.z());
            maxZ = Math.max(maxZ, c.z());
        }
        return new int[]{minX, maxX, minZ, maxZ};
    }

    /**
     * The claim chunk whose outer edge carries the "+" for a direction: among the chunks on the extreme side,
     * the median one, preferring chunks whose neighbour in that direction is free.
     */
    public static ChunkPos plusChunk(Set<ChunkPos> chunks, Direction d, Predicate<ChunkPos> neighbourFree) {
        int[] b = bounds(chunks);
        List<ChunkPos> side = new ArrayList<>();
        for (ChunkPos c : chunks) {
            boolean extreme = switch (d) {
                case NORTH -> c.z() == b[2];
                case SOUTH -> c.z() == b[3];
                case WEST -> c.x() == b[0];
                case EAST -> c.x() == b[1];
            };
            if (extreme) {
                side.add(c);
            }
        }
        List<ChunkPos> free = new ArrayList<>();
        for (ChunkPos c : side) {
            if (neighbourFree.test(c.step(d))) {
                free.add(c);
            }
        }
        List<ChunkPos> pool = free.isEmpty() ? side : free;
        Comparator<ChunkPos> cmp = (d == Direction.NORTH || d == Direction.SOUTH)
                ? Comparator.comparingInt(ChunkPos::x) : Comparator.comparingInt(ChunkPos::z);
        pool.sort(cmp);
        return pool.get((pool.size() - 1) / 2);
    }

    /** A straight outer border line in block coordinates. For NORTH/SOUTH sides {@code fixed} is z and from/to are x. */
    public record Segment(Direction side, int fixed, int from, int to) {
        public int length() {
            return to - from;
        }
    }

    /** Outer boundary of a chunk set, with collinear neighbours merged into long segments. */
    public static List<Segment> perimeter(Set<ChunkPos> chunks) {
        Map<String, List<int[]>> groups = new HashMap<>();
        for (ChunkPos c : chunks) {
            for (Direction d : Direction.values()) {
                if (chunks.contains(c.step(d))) {
                    continue;
                }
                int fixed;
                int from;
                switch (d) {
                    case NORTH -> {
                        fixed = c.minBlockZ();
                        from = c.minBlockX();
                    }
                    case SOUTH -> {
                        fixed = c.minBlockZ() + 16;
                        from = c.minBlockX();
                    }
                    case WEST -> {
                        fixed = c.minBlockX();
                        from = c.minBlockZ();
                    }
                    default -> {
                        fixed = c.minBlockX() + 16;
                        from = c.minBlockZ();
                    }
                }
                groups.computeIfAbsent(d.name() + ":" + fixed, k -> new ArrayList<>()).add(new int[]{from, from + 16});
            }
        }
        List<Segment> out = new ArrayList<>();
        for (Map.Entry<String, List<int[]>> e : groups.entrySet()) {
            String[] parts = e.getKey().split(":");
            Direction side = Direction.valueOf(parts[0]);
            int fixed = Integer.parseInt(parts[1]);
            List<int[]> list = e.getValue();
            list.sort(Comparator.comparingInt(a -> a[0]));
            int curFrom = list.get(0)[0];
            int curTo = list.get(0)[1];
            for (int i = 1; i < list.size(); i++) {
                int[] r = list.get(i);
                if (r[0] == curTo) {
                    curTo = r[1];
                } else {
                    out.add(new Segment(side, fixed, curFrom, curTo));
                    curFrom = r[0];
                    curTo = r[1];
                }
            }
            out.add(new Segment(side, fixed, curFrom, curTo));
        }
        return out;
    }

    /** True when every chunk can be reached from every other through shared edges. */
    public static boolean connected(Set<ChunkPos> chunks) {
        if (chunks.isEmpty()) {
            return true;
        }
        java.util.ArrayDeque<ChunkPos> queue = new java.util.ArrayDeque<>();
        Set<ChunkPos> seen = new java.util.HashSet<>();
        ChunkPos first = chunks.iterator().next();
        queue.add(first);
        seen.add(first);
        while (!queue.isEmpty()) {
            ChunkPos c = queue.poll();
            for (Direction d : Direction.values()) {
                ChunkPos n = c.step(d);
                if (chunks.contains(n) && seen.add(n)) {
                    queue.add(n);
                }
            }
        }
        return seen.size() == chunks.size();
    }
}
