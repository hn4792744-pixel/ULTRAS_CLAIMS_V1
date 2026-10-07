package com.ultras.claims.gui;

import com.ultras.claims.claim.Claim;
import com.ultras.claims.core.ChunkPos;
import com.ultras.claims.util.ColorSpec;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Chunk map around the player: colour-coded cells, zoom, navigation, chunk settings and "expand here". */
final class MapMenu extends Menu {
    private static final int COLS = 9;
    private static final int ROWS = 5;
    private static final int[] ZOOMS = {1, 2, 4};

    private final String world;
    private final ChunkPos here;
    private int cx;
    private int cz;
    private int zoomIdx;
    private ChunkPos selected;

    MapMenu(GuiService gui, Player viewer) {
        super(gui, viewer);
        this.world = viewer.getWorld().getName();
        this.here = ChunkPos.ofBlock(viewer.getLocation().getBlockX(), viewer.getLocation().getBlockZ());
        this.cx = here.x();
        this.cz = here.z();
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected Component title() {
        return gui.text(lang, "gui.title-map", "x", cx, "z", cz);
    }

    private ColorSpec color(String id, String def) {
        return ColorSpec.parse(plugin.getConfig().getString("map.colors." + id, def), ColorSpec.of(0xAAAAAA));
    }

    /** What a cell is, by priority. */
    private enum Kind { OWN_HEAD, OWN, MEMBER, ENEMY, FREE }

    @Override
    protected void build() {
        int zoom = ZOOMS[zoomIdx];
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int x0 = cx + (c - COLS / 2) * zoom;
                int z0 = cz + (r - ROWS / 2) * zoom;
                set(r * 9 + c, cell(x0, z0, zoom), click -> clicked(x0, z0, zoom));
            }
        }
        backButton(45);
        set(46, gui.button("left", lang), c -> move(-1, 0));
        set(47, gui.button("up", lang), c -> move(0, -1));
        set(48, gui.button("center", lang), c -> {
            cx = here.x();
            cz = here.z();
            refresh();
        });
        set(49, gui.button("down", lang), c -> move(0, 1));
        set(50, gui.button("right", lang), c -> move(1, 0));
        if (zoomIdx < ZOOMS.length - 1) {
            set(51, gui.button("zoom-out", lang, "zoom", ZOOMS[zoomIdx + 1]), c -> {
                zoomIdx++;
                refresh();
            });
        }
        if (zoomIdx > 0) {
            set(52, gui.button("zoom-in", lang, "zoom", ZOOMS[zoomIdx - 1]), c -> {
                zoomIdx--;
                refresh();
            });
        }
        if (selected != null && zoom == 1) {
            Claim target = expandableFor(selected);
            if (target != null) {
                set(53, gui.button("expand-here", lang, "x", selected.x(), "z", selected.z()), c -> plugin.expansion().requestTarget(viewer, target, selected, false));
            } else {
                set(53, gui.button("expand-unavailable", lang, "x", selected.x(), "z", selected.z()));
            }
        }
    }

    private void move(int dx, int dz) {
        int step = ZOOMS[zoomIdx] * 3;
        cx += dx * step;
        cz += dz * step;
        refresh();
    }

    private Claim expandableFor(ChunkPos target) {
        if (plugin.claims().at(world, target.x(), target.z()) != null) {
            return null;
        }
        for (Claim c : plugin.claims().ownedBy(viewer.getUniqueId())) {
            if (c.world().equals(world) && plugin.expansion().directionTo(c, target) != null) {
                return c;
            }
        }
        for (Claim c : plugin.claims().memberClaims(viewer.getUniqueId())) {
            if (c.world().equals(world) && plugin.expansion().canExpand(viewer, c) && plugin.expansion().directionTo(c, target) != null) {
                return c;
            }
        }
        return null;
    }

    private Kind kindOf(int x, int z) {
        Claim c = plugin.claims().at(world, x, z);
        if (c == null) {
            return Kind.FREE;
        }
        if (c.isOwner(viewer.getUniqueId())) {
            return c.headChunk().equals(new ChunkPos(x, z)) ? Kind.OWN_HEAD : Kind.OWN;
        }
        return c.isMember(viewer.getUniqueId()) ? Kind.MEMBER : Kind.ENEMY;
    }

    private ItemStack cell(int x0, int z0, int zoom) {
        Kind best = Kind.FREE;
        boolean hasHere = false;
        boolean hasSelected = false;
        Claim sample = null;
        for (int dx = 0; dx < zoom; dx++) {
            for (int dz = 0; dz < zoom; dz++) {
                Kind k = kindOf(x0 + dx, z0 + dz);
                if (k.ordinal() < best.ordinal()) {
                    best = k;
                }
                if (k != Kind.FREE && sample == null) {
                    sample = plugin.claims().at(world, x0 + dx, z0 + dz);
                }
                hasHere |= here.x() == x0 + dx && here.z() == z0 + dz;
                hasSelected |= selected != null && selected.x() == x0 + dx && selected.z() == z0 + dz;
            }
        }
        String id = switch (best) {
            case OWN_HEAD -> "own-head";
            case OWN -> "own";
            case MEMBER -> "member";
            case ENEMY -> "enemy";
            case FREE -> "free";
        };
        ColorSpec col = hasSelected ? color("selected", "WHITE") : switch (best) {
            case OWN_HEAD -> color("own-head", "DARK_GREEN");
            case OWN -> color("own", "GREEN");
            case MEMBER -> color("member", "BLUE");
            case ENEMY -> color("enemy", "RED");
            case FREE -> color("free", "GRAY");
        };
        List<Component> lore = new ArrayList<>();
        lore.add(gui.text(lang, "gui.map-coords", "x", x0, "z", z0));
        if (sample != null) {
            lore.add(gui.text(lang, "gui.map-owner", "owner", sample.ownerName(), "id", sample.id()));
        }
        if (zoom == 1 && (best == Kind.OWN_HEAD || best == Kind.OWN || best == Kind.MEMBER)) {
            lore.add(gui.text(lang, "gui.map-click-settings"));
        } else if (zoom == 1 && best == Kind.FREE) {
            lore.add(gui.text(lang, "gui.map-click-select"));
        } else if (zoom > 1) {
            lore.add(gui.text(lang, "gui.map-click-zoom"));
        }
        Component name = gui.text(lang, "gui.map-" + id);
        if (hasHere) {
            name = name.append(gui.text(lang, "gui.map-here"));
        }
        ItemStack it = new ItemStack(col.pane());
        ItemMeta m = it.getItemMeta();
        m.displayName(name);
        m.lore(lore);
        if (hasHere) {
            m.setEnchantmentGlintOverride(true);
        }
        m.addItemFlags(org.bukkit.inventory.ItemFlag.values());
        it.setItemMeta(m);
        return it;
    }

    private void clicked(int x0, int z0, int zoom) {
        if (zoom > 1) {
            cx = x0 + zoom / 2;
            cz = z0 + zoom / 2;
            zoomIdx--;
            refresh();
            return;
        }
        Claim c = plugin.claims().at(world, x0, z0);
        if (c == null) {
            selected = new ChunkPos(x0, z0);
            refresh();
        } else if (c.isOwner(viewer.getUniqueId()) || c.isMember(viewer.getUniqueId()) || plugin.hasBypass(viewer)) {
            gui.openSettings(viewer, c, x0 + ", " + z0, this);
        } else {
            plugin.messages().info(viewer, "map-enemy-claim", "owner", c.ownerName());
        }
    }
}
