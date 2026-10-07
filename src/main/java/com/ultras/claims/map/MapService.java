package com.ultras.claims.map;

import com.ultras.claims.UltrasClaims;
import com.ultras.claims.claim.Claim;
import com.ultras.claims.settings.PlayerSettings;
import com.ultras.claims.util.ColorSpec;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The physical claim map: a real filled map that draws the chunks around whoever holds it, using the same colours as
 * the map menu. It is rendered per viewer and only redrawn when the viewer changes chunk or a claim changes.
 */
public final class MapService {
    private static final int CELL = 8; // pixels per chunk, 16x16 chunks on a 128x128 map

    private final UltrasClaims plugin;

    public MapService(UltrasClaims plugin) {
        this.plugin = plugin;
    }

    public void start() {
        for (PlayerSettings ps : plugin.players().all()) {
            if (ps.mapViewId() >= 0) {
                MapView v = Bukkit.getMap(ps.mapViewId());
                if (v != null) {
                    attach(v);
                }
            }
        }
    }

    public void stop() {
        // renderers belong to this plugin instance and disappear with it
    }

    public void reload() {
        // colours are read on every render
    }

    /** Re-attaches the renderer to a map view (after a restart the view comes back without it). */
    public void attach(MapView view) {
        for (PlayerSettings ps : plugin.players().all()) {
            if (ps.mapViewId() == view.getId()) {
                setup(view);
                return;
            }
        }
    }

    private void setup(MapView view) {
        for (MapRenderer r : new java.util.ArrayList<>(view.getRenderers())) {
            view.removeRenderer(r);
        }
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        view.addRenderer(new ClaimRenderer());
    }

    public boolean isClaimMap(ItemStack item) {
        return item != null && item.getType() == Material.FILLED_MAP && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(plugin.keys().claimMap, PersistentDataType.BYTE);
    }

    /** Gives the player their claim map (the same map view is reused, so maps never pile up in the world data). */
    public ItemStack create(Player p) {
        PlayerSettings ps = plugin.players().get(p.getUniqueId());
        MapView view = ps.mapViewId() >= 0 ? Bukkit.getMap(ps.mapViewId()) : null;
        if (view == null) {
            World w = p.getWorld();
            view = Bukkit.createMap(w);
            ps.mapView(view.getId(), w.getName());
            plugin.players().save(ps);
        }
        setup(view);
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(view);
        meta.displayName(plugin.messages().gui(plugin.messages().languageOf(p), "item-map-name"));
        meta.lore(plugin.messages().guiLore(plugin.messages().languageOf(p), "item-map-lore"));
        meta.getPersistentDataContainer().set(plugin.keys().claimMap, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private ColorSpec color(String id, String def) {
        return ColorSpec.parse(plugin.getConfig().getString("map.colors." + id, def), ColorSpec.of(0xAAAAAA));
    }

    private final class ClaimRenderer extends MapRenderer {
        private final Map<UUID, long[]> last = new HashMap<>();

        ClaimRenderer() {
            super(true); // contextual: every viewer gets their own picture
        }

        @Override
        public void render(MapView map, MapCanvas canvas, Player player) {
            int pcx = player.getLocation().getBlockX() >> 4;
            int pcz = player.getLocation().getBlockZ() >> 4;
            long stamp = plugin.claims().version();
            long[] prev = last.get(player.getUniqueId());
            String world = player.getWorld().getName();
            long worldHash = world.hashCode();
            if (prev != null && prev[0] == pcx && prev[1] == pcz && prev[2] == stamp && prev[3] == worldHash) {
                return;
            }
            last.put(player.getUniqueId(), new long[]{pcx, pcz, stamp, worldHash});
            Color free = color("free", "GRAY").awt();
            Color ownHead = color("own-head", "DARK_GREEN").awt();
            Color own = color("own", "GREEN").awt();
            Color member = color("member", "BLUE").awt();
            Color enemy = color("enemy", "RED").awt();
            Color line = new Color(20, 20, 20);
            int half = 128 / CELL / 2;
            for (int gx = 0; gx < 16; gx++) {
                for (int gz = 0; gz < 16; gz++) {
                    int x = pcx - half + gx;
                    int z = pcz - half + gz;
                    Claim c = plugin.claims().at(world, x, z);
                    Color col = free;
                    if (c != null) {
                        if (c.isOwner(player.getUniqueId())) {
                            col = c.headChunk().x() == x && c.headChunk().z() == z ? ownHead : own;
                        } else if (c.isMember(player.getUniqueId())) {
                            col = member;
                        } else {
                            col = enemy;
                        }
                    }
                    for (int px = 0; px < CELL; px++) {
                        for (int pz = 0; pz < CELL; pz++) {
                            boolean edge = px == 0 || pz == 0;
                            canvas.setPixelColor(gx * CELL + px, gz * CELL + pz, edge ? line : col);
                        }
                    }
                }
            }
            Color white = Color.WHITE;
            int mx = half * CELL + CELL / 2;
            for (int a = -2; a <= 2; a++) {
                canvas.setPixelColor(mx + a, mx, white);
                canvas.setPixelColor(mx, mx + a, white);
            }
        }
    }
}
