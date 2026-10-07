package com.ultras.claims;

import com.ultras.claims.core.AccessRules;
import com.ultras.claims.core.CabinMath;
import com.ultras.claims.core.ChunkPos;
import com.ultras.claims.core.Direction;
import com.ultras.claims.core.Geometry;
import com.ultras.claims.core.SmallCaps;
import com.ultras.claims.core.TimeFormat;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests of the pure logic (no server needed). */
class CoreTests {
    // ---- access priority: admin bypass > owner > member permission > visitor setting > deny
    @Test
    void unprotectedClaimAllowsEveryone() {
        assertTrue(AccessRules.allowed(false, false, false, false, false));
    }

    @Test
    void protectedClaimDeniesVisitorsByDefault() {
        assertFalse(AccessRules.allowed(true, false, false, false, false));
    }

    @Test
    void bypassOwnerMemberVisitorOrder() {
        assertTrue(AccessRules.allowed(true, true, false, false, false));   // admin bypass
        assertTrue(AccessRules.allowed(true, false, true, false, false));   // owner
        assertTrue(AccessRules.allowed(true, false, false, true, false));   // member with permission
        assertFalse(AccessRules.allowed(true, false, false, false, false)); // member without permission and visitor setting ON
        assertTrue(AccessRules.allowed(true, false, false, false, true));   // visitor setting OFF = allowed
    }

    // ---- cabin math
    private static Map<String, CabinMath.Rule> rules() {
        Map<String, CabinMath.Rule> r = new LinkedHashMap<>();
        r.put("STONE", new CabinMath.Rule(1, 3));
        r.put("COAL_ORE", new CabinMath.Rule(5, 60));
        return r;
    }

    @Test
    void cabinStepsAreWholeMinimums() {
        CabinMath.Result res = CabinMath.compute(rules(), Map.of("STONE", 10, "COAL_ORE", 12));
        assertEquals(10, (int) res.consumed().get("STONE"));
        assertEquals(10, (int) res.consumed().get("COAL_ORE")); // 2 steps of 5, 2 left over
        assertEquals(10 * 3 + 2 * 60, res.seconds());
    }

    @Test
    void cabinBelowMinimumConsumesNothing() {
        CabinMath.Result res = CabinMath.compute(rules(), Map.of("COAL_ORE", 4));
        assertTrue(res.isEmpty());
        assertEquals(0, res.seconds());
    }

    @Test
    void cabinUnknownItemsAreIgnored() {
        assertTrue(CabinMath.compute(rules(), Map.of("DIRT", 64)).isEmpty());
    }

    @Test
    void cabinCapLimitsTimeAndKeepsExtraItems() {
        CabinMath.Result res = CabinMath.compute(rules(), Map.of("STONE", 100), 30);
        assertEquals(10, (int) res.consumed().get("STONE"));
        assertEquals(30, res.seconds());
        assertTrue(CabinMath.compute(rules(), Map.of("STONE", 100), 0).isEmpty());
    }

    // ---- chunks & geometry
    @Test
    void chunkOfBlockHandlesNegatives() {
        assertEquals(new ChunkPos(0, 0), ChunkPos.ofBlock(15, 15));
        assertEquals(new ChunkPos(-1, -1), ChunkPos.ofBlock(-1, -1));
        assertEquals(new ChunkPos(-2, 1), ChunkPos.ofBlock(-17, 16));
    }

    @Test
    void chunkKeysAreUnique() {
        Set<Long> keys = new HashSet<>();
        for (int x = -50; x <= 50; x++) {
            for (int z = -50; z <= 50; z++) {
                assertTrue(keys.add(ChunkPos.key(x, z)));
            }
        }
    }

    @Test
    void stepMovesOneChunk() {
        assertEquals(new ChunkPos(3, 2), new ChunkPos(3, 3).step(Direction.NORTH));
        assertEquals(new ChunkPos(4, 3), new ChunkPos(3, 3).step(Direction.EAST));
    }

    @Test
    void plusChunkIsOnTheOuterEdgeInTheMiddle() {
        Set<ChunkPos> c = new HashSet<>();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                c.add(new ChunkPos(x, z));
            }
        }
        assertEquals(new ChunkPos(1, 0), Geometry.plusChunk(c, Direction.NORTH, n -> true));
        assertEquals(new ChunkPos(2, 1), Geometry.plusChunk(c, Direction.EAST, n -> true));
        // a blocked middle neighbour moves the plus to a free one
        ChunkPos blocked = new ChunkPos(1, -1);
        ChunkPos moved = Geometry.plusChunk(c, Direction.NORTH, n -> !n.equals(blocked));
        assertFalse(moved.equals(new ChunkPos(1, 0)));
    }

    @Test
    void connectivity() {
        Set<ChunkPos> c = new HashSet<>(Set.of(new ChunkPos(0, 0), new ChunkPos(1, 0), new ChunkPos(2, 0)));
        assertTrue(Geometry.connected(c));
        c.remove(new ChunkPos(1, 0));
        assertFalse(Geometry.connected(c));
    }

    @Test
    void perimeterOfOneChunkHasFourSides() {
        assertEquals(4, Geometry.perimeter(Set.of(new ChunkPos(0, 0))).size());
    }

    // ---- text helpers
    @Test
    void smallCapsKeepsTagsAndPlaceholders() {
        assertEquals("<success>✓ ᴄʟᴀɪᴍ ᴇxᴘᴀɴᴅᴇᴅ %max%", SmallCaps.convertTemplate("<success>✓ claim expanded %max%"));
    }

    @Test
    void timeFormat() {
        assertEquals("58m", TimeFormat.format(58 * 60, false));
        assertEquals("1h 5m", TimeFormat.format(3900, false));
        assertEquals("2d 3h", TimeFormat.format(2 * 86_400 + 3 * 3600, false));
        assertEquals("0s", TimeFormat.format(0, false));
    }

    @Test
    void timeParse() {
        assertEquals(3, TimeFormat.parse("3s"));
        assertEquals(1800, TimeFormat.parse("30m"));
        assertEquals(86_400, TimeFormat.parse("1d"));
        assertEquals(90, TimeFormat.parse("90"));
        assertEquals(-1, TimeFormat.parse("abc"));
    }
}
