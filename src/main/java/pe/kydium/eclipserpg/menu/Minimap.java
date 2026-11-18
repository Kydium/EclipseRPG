package pe.kydium.eclipserpg.menu;

import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.util.Keys;
import pe.kydium.eclipserpg.util.Txt;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mapa del aventurero: un mapa que va centrado en el jugador (como un minimapa en la otra mano),
 * con la misión (X roja), la aldea (estandarte verde) y otros jugadores. Clic derecho: acercar / alejar.
 */
public final class Minimap extends MapRenderer implements Listener {

    private static final int[] ZOOMS = {1, 2, 4};

    private final EclipseRPG plugin;
    private final NamespacedKey KEY;
    private MapView view;
    private final Map<UUID, int[]> last = new HashMap<>();   // x, z, zoom, tiempo/500
    private final Map<UUID, Integer> zoom = new HashMap<>();
    private final Map<Integer, Byte> colorCache = new HashMap<>();

    public Minimap(EclipseRPG plugin) {
        super(true); // cada jugador ve su propio mapa
        this.plugin = plugin;
        this.KEY = Keys.k("minimapa");
    }

    public void init() {
        File f = new File(plugin.getDataFolder(), "datos-minimapa.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        int id = y.getInt("mapa", -1);
        view = id >= 0 ? Bukkit.getMap(id) : null;
        if (view == null) {
            view = Bukkit.createMap(Bukkit.getWorlds().get(0));
            y.set("mapa", view.getId());
            try {
                y.save(f);
            } catch (IOException e) {
                plugin.getLogger().warning("No se pudo guardar el minimapa: " + e.getMessage());
            }
        }
        for (MapRenderer r : List.copyOf(view.getRenderers())) view.removeRenderer(r);
        view.addRenderer(this);
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        view.setLocked(false);
    }

    public ItemStack item() {
        ItemStack it = new ItemStack(Material.FILLED_MAP);
        if (it.getItemMeta() instanceof MapMeta meta) {
            meta.setMapView(view);
            meta.displayName(Txt.item("<gold>🗺 Mapa del aventurero"));
            meta.lore(Txt.lore(List.of("<gray>Llévalo en la otra mano (tecla F):", "<gray>va centrado en ti, como un minimapa.",
                    "<red>✖ <gray>tu misión · <green>⚑ <gray>la aldea", "<yellow>Clic derecho: acercar / alejar")));
            meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
            meta.setMapColor(org.bukkit.Color.fromRGB(0xD8A93A));
            it.setItemMeta(meta);
        }
        return it;
    }

    private boolean isMinimap(ItemStack it) {
        return it != null && it.getType() == Material.FILLED_MAP && it.getItemMeta() instanceof MapMeta m
                && (m.getPersistentDataContainer().has(KEY) || m.hasMapView() && m.getMapView() != null && view != null && m.getMapView().getId() == view.getId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isMinimap(e.getItem())) return;
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null && e.getClickedBlock().getType().isInteractable()) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        int z = (zoom.getOrDefault(p.getUniqueId(), 0) + 1) % ZOOMS.length;
        zoom.put(p.getUniqueId(), z);
        last.remove(p.getUniqueId());
        p.sendActionBar(Txt.mm("<gold>🗺 Zoom: <white>" + (128 * ZOOMS[z]) + " bloques"));
        Txt.sound(p, "item.book.page_turn", 0.8f, 1.2f);
    }

    // ---------------------------------------------------------------- dibujo

    @Override
    public void render(MapView map, MapCanvas canvas, Player p) {
        Location l = p.getLocation();
        int zi = zoom.getOrDefault(p.getUniqueId(), 0);
        int s = ZOOMS[zi];
        int cx = l.getBlockX(), cz = l.getBlockZ();
        long now = System.currentTimeMillis() / 400;
        int[] prev = last.get(p.getUniqueId());
        boolean redraw = prev == null || prev[2] != zi || Math.abs(prev[0] - cx) >= s || Math.abs(prev[1] - cz) >= s || now - prev[3] >= 10;
        if (redraw && (prev == null || now != prev[3])) {
            last.put(p.getUniqueId(), new int[]{cx, cz, zi, (int) now});
            terrain(canvas, p.getWorld(), cx, cz, s);
        }
        cursors(canvas, p, cx, cz, s);
    }

    private void terrain(MapCanvas canvas, World w, int cx, int cz, int s) {
        int[] heights = new int[128];
        for (int px = 0; px < 128; px++) {
            int bx = cx + (px - 64) * s;
            heights[px] = Integer.MIN_VALUE;
            int bzTop = cz - 64 * s - s;
            if (w.isChunkLoaded(bx >> 4, bzTop >> 4)) heights[px] = w.getHighestBlockYAt(bx, bzTop, HeightMap.WORLD_SURFACE);
        }
        for (int py = 0; py < 128; py++) {
            int bz = cz + (py - 64) * s;
            for (int px = 0; px < 128; px++) {
                int bx = cx + (px - 64) * s;
                if (!w.isChunkLoaded(bx >> 4, bz >> 4)) {
                    canvas.setPixel(px, py, (byte) 0);
                    heights[px] = Integer.MIN_VALUE;
                    continue;
                }
                int y = w.getHighestBlockYAt(bx, bz, HeightMap.WORLD_SURFACE);
                Block b = w.getBlockAt(bx, y, bz);
                org.bukkit.Color c;
                try {
                    c = b.getBlockData().getMapColor();
                } catch (RuntimeException | LinkageError e) {
                    c = org.bukkit.Color.GRAY;
                }
                int north = heights[px];
                double shade = b.isLiquid() ? (((px + py) & 1) == 0 ? 0.92 : 0.86) : north == Integer.MIN_VALUE ? 0.86 : y > north ? 1.0 : y < north ? 0.71 : 0.86;
                heights[px] = y;
                int rgb = ((int) (c.getRed() * shade) << 16) | ((int) (c.getGreen() * shade) << 8) | (int) (c.getBlue() * shade);
                canvas.setPixel(px, py, color(rgb));
            }
        }
    }

    @SuppressWarnings("deprecation")
    private byte color(int rgb) {
        return colorCache.computeIfAbsent(rgb, k -> MapPalette.matchColor(new java.awt.Color(k)));
    }

    private void cursors(MapCanvas canvas, Player p, int cx, int cz, int s) {
        MapCursorCollection cur = new MapCursorCollection();
        // tú, en el centro, mirando hacia donde miras
        cur.addCursor(new MapCursor((byte) 0, (byte) 0, dir(p.getLocation().getYaw()), MapCursor.Type.PLAYER, true));
        // aldea
        Location v = plugin.village().center();
        if (v != null && v.getWorld() == p.getWorld()) add(cur, v, cx, cz, s, MapCursor.Type.BANNER_LIME, "Aldea");
        // misión
        Location q = plugin.hud() == null ? null : plugin.hud().target(p);
        if (q != null && q.getWorld() == p.getWorld()) add(cur, q, cx, cz, s, MapCursor.Type.RED_X, "Misión");
        // otros jugadores
        for (Player o : p.getWorld().getPlayers()) {
            if (o == p || o.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            Location ol = o.getLocation();
            int mx = (ol.getBlockX() - cx) * 2 / s, mz = (ol.getBlockZ() - cz) * 2 / s;
            if (Math.abs(mx) > 126 || Math.abs(mz) > 126) continue;
            cur.addCursor(new MapCursor((byte) mx, (byte) mz, dir(ol.getYaw()), MapCursor.Type.BLUE_MARKER, true));
        }
        canvas.setCursors(cur);
    }

    private static void add(MapCursorCollection cur, Location at, int cx, int cz, int s, MapCursor.Type type, String label) {
        int mx = (at.getBlockX() - cx) * 2 / s, mz = (at.getBlockZ() - cz) * 2 / s;
        boolean inside = Math.abs(mx) <= 126 && Math.abs(mz) <= 126;
        if (!inside) {
            // fuera del mapa: se queda en el borde, en su dirección
            double k = 126.0 / Math.max(Math.abs(mx), Math.abs(mz));
            mx = (int) (mx * k);
            mz = (int) (mz * k);
        }
        MapCursor c = new MapCursor((byte) mx, (byte) mz, (byte) 8, type, true);
        if (inside) c.caption(net.kyori.adventure.text.Component.text(label));
        cur.addCursor(c);
    }

    private static byte dir(float yaw) {
        float y = (yaw % 360 + 360) % 360;
        return (byte) (Math.round(y / 22.5f) & 15);
    }
}
