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
                    "<red>✖ <gray>tu misión · <green>⚑ <gray>la aldea",
                    "<yellow>⚑ <gray>tiendas · <aqua>⚑ <gray>contratos · <light_purple>⚑ <gray>Grieta", "<white>◎ <gray>piedra de viaje · <dark_gray>⚑ <gray>tu última muerte", "<yellow>Clic derecho: acercar / alejar")));
            meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
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
        // piedras de viaje
        if (plugin.waystones() != null && plugin.waystones().enabled())
            for (var st : plugin.waystones().all()) {
                if (!st.world().equals(p.getWorld().getName())) continue;
                add(cur, new Location(p.getWorld(), st.x(), st.y(), st.z()), cx, cz, s, MapCursor.Type.TARGET_POINT, s == 1 ? "Viaje" : null);
            }
        // comerciantes (NPCs con tienda, contratos o Grieta): solo los que caen dentro del mapa
        for (Trader t : traders(p)) {
            int mx = (t.at().getBlockX() - cx) * 2 / s, mz = (t.at().getBlockZ() - cz) * 2 / s;
            if (Math.abs(mx) > 126 || Math.abs(mz) > 126) continue;
            MapCursor c = new MapCursor((byte) mx, (byte) mz, (byte) 8, t.type(), true);
            if (s <= 2) c.caption(net.kyori.adventure.text.Component.text(s == 1 ? t.caption() : t.name()));
            cur.addCursor(c);
        }
        // tu última muerte (hasta que vuelvas a ese lugar)
        Location death = lastDeath(p);
        if (death != null && death.getWorld() == p.getWorld()) add(cur, death, cx, cz, s, MapCursor.Type.BANNER_BLACK, "Tu muerte");
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
        if (inside && label != null) c.caption(net.kyori.adventure.text.Component.text(label));
        cur.addCursor(c);
    }

    /** Dónde murió el jugador la última vez (o null si ya volvió a ese lugar). */
    public static Location lastDeath(Player p) {
        String raw = p.getPersistentDataContainer().get(Keys.k("ultima_muerte"), PersistentDataType.STRING);
        if (raw == null) return null;
        String[] a = raw.split(";");
        World w = a.length == 4 ? Bukkit.getWorld(a[0]) : null;
        if (w == null) return null;
        try {
            return new Location(w, Double.parseDouble(a[1]), Double.parseDouble(a[2]), Double.parseDouble(a[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent e) {
        Player p = e.getEntity();
        Location l = p.getLocation();
        p.getPersistentDataContainer().set(Keys.k("ultima_muerte"), PersistentDataType.STRING,
                l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) Txt.send(p, "<gray>☠ Moriste en <white>" + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ()
                    + "<gray>. Sale en tu <gold>Mapa del aventurero</gold> como una bandera negra.");
        }, 40L);
    }

    /** Al volver al lugar donde moriste, la marca se borra. */
    public void startDeathTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                Location d = lastDeath(p);
                if (d == null || d.getWorld() != p.getWorld() || p.isDead()) continue;
                if (d.distanceSquared(p.getLocation()) < 4 * 4) {
                    p.getPersistentDataContainer().remove(Keys.k("ultima_muerte"));
                    p.sendActionBar(Txt.mm("<gray>☠ Llegaste al lugar donde moriste"));
                }
            }
        }, 100L, 40L);
    }

    /** Un NPC que vende o da servicios: dónde está, su nombre y qué ofrece. */
    public record Trader(String id, String name, String what, Location at, MapCursor.Type type) {
        String caption() {
            return name + ": " + what;
        }
    }

    /** Comerciantes en el mundo del jugador (con su sitio aunque no esté cargado). */
    public List<Trader> traders(Player p) {
        return traders(p.getWorld());
    }

    public List<Trader> traders(World world) {
        List<Trader> out = new java.util.ArrayList<>();
        for (String id : plugin.npcs().ids()) {
            var d = plugin.npcs().def(id);
            if (d == null || d.services.isEmpty()) continue;
            Location at = plugin.npcEntities().spotOf(id);
            if (at == null || at.getWorld() != world) continue;
            String s0 = d.services.get(0);
            String what;
            MapCursor.Type type;
            if (s0.startsWith("tienda:")) {
                what = plugin.shops().buttonLabel(s0.substring(7)).replaceAll("<[^>]*>", "").replaceAll("^[^\\p{L}]+", "").trim();
                type = MapCursor.Type.BANNER_YELLOW;
            } else if (s0.equals("contratos")) {
                what = "Contratos";
                type = MapCursor.Type.BANNER_LIGHT_BLUE;
            } else if (s0.equals("grieta")) {
                what = "Grieta";
                type = MapCursor.Type.BANNER_PURPLE;
            } else continue;
            if (what.length() > 22) what = what.substring(0, 21) + "…";
            out.add(new Trader(id, d.plainName, what, at, type));
        }
        return out;
    }

    private static byte dir(float yaw) {
        float y = (yaw % 360 + 360) % 360;
        return (byte) (Math.round(y / 22.5f) & 15);
    }
}
