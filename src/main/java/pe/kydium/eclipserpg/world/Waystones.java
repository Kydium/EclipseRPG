package pe.kydium.eclipserpg.world;

import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.quests.DialogUI;
import pe.kydium.eclipserpg.structure.StructureManager;
import pe.kydium.eclipserpg.util.Keys;
import pe.kydium.eclipserpg.util.Txt;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Piedras de viaje: una en la aldea y una en cada ciudad de la historia. Al acercarte a una la descubres;
 * con clic derecho en cualquier piedra viajas a otra que ya conozcas (unos segundos quieto, cuesta unos Soles).
 */
public final class Waystones implements Listener {

    public static final String VILLAGE = "aldea";

    public record Stone(String id, String world, int x, int y, int z) {
        Location loc(World w) {
            return new Location(w, x + 0.5, y, z + 0.5);
        }

        boolean is(Block b) {
            return b.getWorld().getName().equals(world) && b.getX() == x && b.getY() == y && b.getZ() == z;
        }
    }

    private final EclipseRPG plugin;
    private final File file;
    private final Map<String, Stone> stones = new LinkedHashMap<>();
    private final Map<UUID, Long> hurt = new HashMap<>();
    private final Map<UUID, Integer> channel = new HashMap<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private final Map<String, Long> failed = new HashMap<>();

    public Waystones(EclipseRPG plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "datos-viajes.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        var sec = y.getConfigurationSection("piedras");
        if (sec != null) for (String id : sec.getKeys(false)) {
            var s = sec.getConfigurationSection(id);
            if (s != null) stones.put(id, new Stone(id, s.getString("mundo", "world"), s.getInt("x"), s.getInt("y"), s.getInt("z")));
        }
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("viajes.activo", true);
    }

    public List<Stone> all() {
        return new ArrayList<>(stones.values());
    }

    public String name(String id) {
        return id.equals(VILLAGE) ? plugin.villageName() : plugin.cities().name(id).replaceAll("<[^>]*>", "");
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Stone s : stones.values()) {
            String k = "piedras." + s.id();
            y.set(k + ".mundo", s.world());
            y.set(k + ".x", s.x());
            y.set(k + ".y", s.y());
            y.set(k + ".z", s.z());
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudieron guardar las piedras de viaje: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- construir

    /** Pone las piedras que falten (aldea ya generada y ciudades construidas). */
    public void ensureAll() {
        if (!enabled()) return;
        repair();
        Location v = plugin.village() == null ? null : plugin.village().center();
        if (v != null && !stones.containsKey(VILLAGE)) place(VILLAGE, v, plugin.village().radius());
        for (String c : plugin.cities().ids()) {
            if (stones.containsKey(c)) continue;
            StructureManager.Inst i = plugin.cities().instance(c);
            World w = i == null ? null : Bukkit.getWorld(i.world);
            if (w == null) continue;
            List<Location> extra = new ArrayList<>();
            for (float[] pl : i.places.values()) extra.add(new Location(w, pl[0], pl[1], pl[2]));
            place(c, new Location(w, i.x + 0.5, i.y + 1, i.z + 0.5), Math.max(8, Math.min(i.rx, i.rz)), extra);
        }
    }

    private void place(String id, Location center, int radius) {
        place(id, center, radius, List.of());
    }

    private void place(String id, Location center, int radius, List<Location> extra) {
        Long f = failed.get(id);
        if (f != null && System.currentTimeMillis() - f < 300_000L) return;
        World w = center.getWorld();
        if (!w.isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4)) return;   // cuando alguien pase cerca
        int rad = Math.max(10, Math.min(30, radius));
        int[] at = findSpot(w, center, rad, true, true);
        for (int k = 0; at == null && k < extra.size(); k++) at = findSpot(w, extra.get(k), 8, true, false);
        if (at == null) at = findSpot(w, center, rad, false, false);
        for (int k = 0; at == null && k < extra.size(); k++) at = findSpot(w, extra.get(k), 10, false, false);
        if (at == null) {
            failed.put(id, System.currentTimeMillis());
            plugin.getLogger().warning("No encontré un sitio plano para la piedra de viaje de " + name(id) + "; reintento en 5 minutos");
            return;
        }
        build(w, at[0], at[1], at[2]);
        Stone s = new Stone(id, w.getName(), at[0], at[1], at[2]);
        stones.put(id, s);
        save();
        label(s, w);
        plugin.getLogger().info("Piedra de viaje de " + name(id) + " en " + at[0] + " " + at[1] + " " + at[2]);
    }

    /**
     * Si una piedra ya no está (el servidor se cerró sin "stop" antes de guardar el mundo, o la quitaron),
     * la vuelve a poner en su sitio, con su letrero.
     */
    private void repair() {
        for (Stone s : stones.values()) {
            World w = Bukkit.getWorld(s.world());
            if (w == null || !w.isChunkLoaded(s.x() >> 4, s.z() >> 4)) continue;
            if (w.getBlockAt(s.x(), s.y(), s.z()).getType() != Material.LODESTONE) {
                build(w, s.x(), s.y(), s.z());
                label(s, w);
                plugin.getLogger().info("Piedra de viaje de " + name(s.id()) + " repuesta en " + s.x() + " " + s.y() + " " + s.z());
                continue;
            }
            boolean hasLabel = false;
            for (Entity e : w.getNearbyEntities(s.loc(w).add(0, 1.6, 0), 1, 2, 1))
                if (e instanceof TextDisplay && e.getPersistentDataContainer().has(Keys.k("piedra_viaje"))) hasLabel = true;
            if (!hasLabel) label(s, w);
        }
    }

    private static void build(World w, int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                w.getBlockAt(x + dx, y - 1, z + dz).setType(dx == 0 && dz == 0 ? Material.CRYING_OBSIDIAN : Material.POLISHED_BLACKSTONE_BRICKS, false);
                for (int dy = 0; dy <= 2; dy++) if (dx != 0 || dz != 0 || dy > 0) w.getBlockAt(x + dx, y + dy, z + dz).setType(Material.AIR, false);
            }
        w.getBlockAt(x, y, z).setType(Material.LODESTONE, false);
        for (int[] c : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}})
            w.getBlockAt(x + c[0], y, z + c[1]).setType(Material.SOUL_LANTERN, false);
    }

    private void label(Stone s, World w) {
        Location l = s.loc(w).add(0, 1.6, 0);
        for (Entity e : w.getNearbyEntities(l, 1, 2, 1))
            if (e instanceof TextDisplay && e.getPersistentDataContainer().has(Keys.k("piedra_viaje"))) e.remove();
        w.spawn(l, TextDisplay.class, t -> {
            t.text(Txt.mm("<aqua>✦ Piedra de viaje ✦\n<white>" + name(s.id()) + "\n<gray>Clic derecho para viajar"));
            t.setBillboard(Display.Billboard.CENTER);
            t.setPersistent(true);
            t.getPersistentDataContainer().set(Keys.k("piedra_viaje"), PersistentDataType.STRING, s.id());
        });
    }

    /** Busca un trozo plano de suelo (no techo) cerca del centro, con aire encima. */
    private int[] findSpot(World w, Location c, int radius, boolean strict, boolean nearY) {
        int cx = c.getBlockX(), cz = c.getBlockZ(), cy = c.getBlockY();
        for (int r = strict && nearY ? 3 : 2; r <= radius; r++)
            for (int t = 0; t < 8 * r; t++) {
                double ang = t * (Math.PI * 2 / (8 * r));
                int x = cx + (int) Math.round(Math.cos(ang) * r), z = cz + (int) Math.round(Math.sin(ang) * r);
                if (!w.isChunkLoaded((x - 2) >> 4, (z - 2) >> 4) || !w.isChunkLoaded((x + 2) >> 4, (z + 2) >> 4)
                        || !w.isChunkLoaded((x - 2) >> 4, (z + 2) >> 4) || !w.isChunkLoaded((x + 2) >> 4, (z - 2) >> 4)) continue;
                int g = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                if (Math.abs(g + 1 - cy) > (nearY ? 4 : 8)) continue;
                if (ok(w, x, g, z, strict)) return new int[]{x, g + 1, z};
            }
        return null;
    }

    private static boolean ok(World w, int x, int g, int z, boolean strict) {
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                int h = w.getHighestBlockYAt(x + dx, z + dz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                boolean inner = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                if (!strict && !inner) {
                    // modo flexible: alrededor basta con que no haya pared justo al lado
                    if (h > g + 2) return false;
                    continue;
                }
                if (inner ? h != g : Math.abs(h - g) > 1) return false;
                Material gm = w.getBlockAt(x + dx, g, z + dz).getType();
                if (inner && !(strict ? ground(gm) : floor(gm))) return false;
                for (int dy = 1; dy <= 3; dy++) if (!w.getBlockAt(x + dx, g + dy, z + dz).isPassable()) return false;
            }
        return true;
    }

    /** Cualquier piso firme que no sea techo, hojas ni agua. */
    private static boolean floor(Material m) {
        String n = m.name();
        return m.isSolid() && m.isOccluding() && !n.endsWith("LEAVES") && !n.endsWith("_LOG") && !n.endsWith("_WOOD") && !n.contains("GLASS")
                && !n.endsWith("WOOL") && m != Material.HAY_BLOCK;
    }

    private static boolean ground(Material m) {
        String n = m.name();
        return m == Material.GRASS_BLOCK || m == Material.DIRT || m == Material.DIRT_PATH || m == Material.COARSE_DIRT || m == Material.PODZOL
                || m == Material.GRAVEL || m == Material.SAND || m == Material.RED_SAND || m == Material.SANDSTONE || m == Material.SNOW_BLOCK
                || m == Material.STONE || m == Material.COBBLESTONE || m == Material.MOSSY_COBBLESTONE || m == Material.SMOOTH_STONE
                || m == Material.PACKED_MUD || m == Material.MUD_BRICKS || m == Material.ROOTED_DIRT || m == Material.MOSS_BLOCK
                || n.endsWith("STONE_BRICKS") || n.startsWith("POLISHED_") && !n.endsWith("SLAB") && !n.endsWith("STAIRS") && !n.endsWith("WALL")
                || n.endsWith("TERRACOTTA") || n.endsWith("CONCRETE");
    }

    // ---------------------------------------------------------------- descubrir

    private Set<String> known(Player p) {
        String raw = p.getPersistentDataContainer().get(Keys.k("viajes"), PersistentDataType.STRING);
        Set<String> s = new LinkedHashSet<>();
        s.add(VILLAGE);
        if (raw != null) for (String k : raw.split(",")) if (!k.isBlank()) s.add(k);
        return s;
    }

    private void learn(Player p, String id) {
        Set<String> s = known(p);
        if (!s.add(id)) return;
        p.getPersistentDataContainer().set(Keys.k("viajes"), PersistentDataType.STRING, String.join(",", s));
        Txt.send(p, "<aqua>✦ Descubriste la piedra de viaje de <white>" + name(id) + "<aqua>. Ya puedes viajar aquí desde cualquier otra piedra.");
        Txt.sound(p, "block.amethyst_block.chime", 1f, 1.2f);
    }

    public void startTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::ensureAll, 100L, 600L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!enabled()) return;
            for (Stone s : stones.values()) {
                World w = Bukkit.getWorld(s.world());
                if (w == null || !w.isChunkLoaded(s.x() >> 4, s.z() >> 4)) continue;
                Location l = s.loc(w);
                boolean near = false;
                for (Player p : w.getPlayers()) {
                    double d = p.getLocation().distanceSquared(l);
                    if (d < 32 * 32) near = true;
                    if (d < 7 * 7) learn(p, s.id());
                }
                if (near) {
                    w.spawnParticle(Particle.PORTAL, l.clone().add(0, 1.2, 0), 25, 0.4, 0.6, 0.4, 0.4);
                    w.spawnParticle(Particle.END_ROD, l.clone().add(0, 1.1, 0), 2, 0.3, 0.4, 0.3, 0.01);
                }
            }
        }, 40L, 20L);
    }

    // ---------------------------------------------------------------- usar

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) return;
        if (e.getClickedBlock().getType() != Material.LODESTONE) return;
        Stone s = at(e.getClickedBlock());
        if (s == null) return;
        e.setCancelled(true);
        learn(e.getPlayer(), s.id());
        open(e.getPlayer(), s);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        for (Stone s : stones.values()) {
            if (!b.getWorld().getName().equals(s.world())) continue;
            if (Math.abs(b.getX() - s.x()) <= 1 && Math.abs(b.getZ() - s.z()) <= 1 && b.getY() >= s.y() - 1 && b.getY() <= s.y()) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) hurt.put(p.getUniqueId(), System.currentTimeMillis());
    }

    private Stone at(Block b) {
        for (Stone s : stones.values()) if (s.is(b)) return s;
        return null;
    }

    public long cost(Stone from, Stone to) {
        if (!from.world().equals(to.world())) return plugin.getConfig().getLong("viajes.costo-minimo", 5) * 4;
        double d = Math.hypot(from.x() - to.x(), from.z() - to.z());
        long c = Math.round(d / 100.0 * plugin.getConfig().getDouble("viajes.costo-por-100-bloques", 3));
        return Math.max(plugin.getConfig().getLong("viajes.costo-minimo", 5), c);
    }

    public void open(Player p, Stone from) {
        Set<String> k = known(p);
        List<String> body = new ArrayList<>();
        body.add("<gray>Estás en <white>" + name(from.id()) + "<gray>. Elige a dónde viajar.");
        body.add("<dark_gray>Las piedras se descubren al acercarte a ellas.");
        List<DialogUI.Button> b = new ArrayList<>();
        for (Stone to : stones.values()) {
            if (to.id().equals(from.id())) continue;
            if (!k.contains(to.id())) {
                b.add(new DialogUI.Button("<dark_gray>🔒 " + name(to.id()), "Ve una vez hasta allí para descubrir su piedra", null));
                continue;
            }
            long c = cost(from, to);
            int dist = (int) Math.hypot(from.x() - to.x(), from.z() - to.z());
            b.add(new DialogUI.Button("<aqua>✦ <white>" + name(to.id()) + " <gray>(" + c + " ☀)", dist + " bloques de aquí", () -> start(p, from, to, c)));
        }
        if (b.isEmpty()) body.add("<yellow>Todavía no hay otras piedras. Cada ciudad nueva que se abra tendrá la suya.");
        b.add(new DialogUI.Button("<gray>Cerrar", null, null));
        new DialogUI(plugin).show(p, "<aqua>✦ Piedra de viaje", body, b, true);
    }

    private void start(Player p, Stone from, Stone to, long cost) {
        UUID u = p.getUniqueId();
        if (channel.containsKey(u)) return;
        Long cd = cooldown.get(u);
        int cdSec = plugin.getConfig().getInt("viajes.espera-segundos", 20);
        if (cd != null && System.currentTimeMillis() - cd < cdSec * 1000L) {
            Txt.send(p, "<gray>La piedra todavía brilla débil… espera <white>" + (cdSec - (System.currentTimeMillis() - cd) / 1000) + " s<gray>.");
            return;
        }
        if (plugin.levels().soles(p) < cost) {
            Txt.send(p, "<red>Te faltan Soles: el viaje cuesta <yellow>" + cost + " ☀<red>.");
            return;
        }
        World w = Bukkit.getWorld(to.world());
        if (w == null) return;
        Location startAt = p.getLocation();
        int secs = Math.max(0, plugin.getConfig().getInt("viajes.segundos-quieto", 3));
        long startedAt = System.currentTimeMillis();
        int[] left = {secs};
        int task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Integer id = channel.get(u);
            if (!p.isOnline() || p.getWorld() != startAt.getWorld() || p.getLocation().distanceSquared(startAt) > 1.5 * 1.5
                    || hurt.getOrDefault(u, 0L) > startedAt) {
                if (id != null) Bukkit.getScheduler().cancelTask(id);
                channel.remove(u);
                if (p.isOnline()) p.sendActionBar(Txt.mm("<red>Viaje cancelado (te moviste o te hirieron)"));
                return;
            }
            if (left[0] > 0) {
                p.sendActionBar(Txt.mm("<aqua>✦ Viajando a <white>" + name(to.id()) + "<aqua> en " + left[0] + "…"));
                p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 60, 0.5, 1, 0.5, 0.6);
                Txt.sound(p, "block.portal.trigger", 0.25f, 1.5f + 0.2f * left[0]);
                left[0]--;
                return;
            }
            if (id != null) Bukkit.getScheduler().cancelTask(id);
            channel.remove(u);
            if (!plugin.levels().takeSoles(p, cost)) {
                Txt.send(p, "<red>Te faltan Soles para el viaje.");
                return;
            }
            cooldown.put(u, System.currentTimeMillis());
            Location dest = to.loc(w).add(1, 0, 0);   // al lado de la piedra, sobre el piso que puso la piedra
            dest.setYaw(90f);
            p.teleportAsync(dest).thenRun(() -> {
                Txt.sound(p, "entity.enderman.teleport", 1f, 1f);
                p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, p.getLocation().add(0, 1, 0), 80, 0.4, 1, 0.4, 0.05);
                p.sendActionBar(Txt.mm("<aqua>✦ <white>" + name(to.id()) + (cost > 0 ? " <gray>(-" + cost + " ☀)" : "")));
            });
        }, 0L, 20L).getTaskId();
        channel.put(u, task);
    }
}
