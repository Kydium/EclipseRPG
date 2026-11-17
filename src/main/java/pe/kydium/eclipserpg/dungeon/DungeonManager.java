package pe.kydium.eclipserpg.dungeon;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.Dispenser;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.persistence.PersistentDataType;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.quests.Quest;
import pe.kydium.eclipserpg.util.Keys;
import pe.kydium.eclipserpg.util.Txt;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Mazmorras de cada acto: construcción, oleadas por sala, sala del jefe y brújula. */
public final class DungeonManager implements Listener {

    public static final class Room {
        Box box;
        boolean boss;
        boolean active;
        long clearedAt;
        final Set<UUID> mobs = new HashSet<>();
    }

    public static final class Dungeon {
        int act;
        String world;
        Box bounds;
        int ex, ey, ez;
        final List<Room> rooms = new ArrayList<>();
        final List<Box> doors = new ArrayList<>();
        boolean built;
        UUID boss;
        long bossReadyAt;
        long emptySince;
        boolean doorsClosed;
        boolean spawning;
        final Set<UUID> inside = new HashSet<>();
    }

    private static final String[] AMBIENT = {"ambient.cave", "entity.warden.heartbeat", "ambient.soul_sand_valley.mood",
            "ambient.basalt_deltas.mood", "entity.zombie.ambient", "block.chain.step", "entity.skeleton.ambient", "ambient.crimson_forest.mood"};
    private static final String[] WHISPERS = {"…no deberías estar aquí…", "…el eclipse te ve…", "…nadie sale igual que entró…",
            "…los huesos recuerdan…", "…más abajo… más oscuro…", "…tu luz se apaga…"};

    private final EclipseRPG plugin;
    private final Map<Integer, Dungeon> dungeons = new HashMap<>();
    private final Set<Integer> building = new HashSet<>();
    private final Map<Integer, List<UUID>> waiting = new HashMap<>();
    private YamlConfiguration cfg = new YamlConfiguration();
    private final File dataFile;

    public DungeonManager(EclipseRPG plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "datos-mazmorras.yml");
    }

    // ---------- Configuración y datos ----------

    public void load() {
        cfg = plugin.story().yaml("mazmorras");
        dungeons.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
        for (String k : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(k);
            if (s == null) continue;
            Dungeon d = new Dungeon();
            d.act = Integer.parseInt(k);
            d.world = s.getString("mundo");
            d.bounds = Box.fromList(s.getIntegerList("limites"));
            d.ex = s.getInt("entrada.x");
            d.ey = s.getInt("entrada.y");
            d.ez = s.getInt("entrada.z");
            d.built = s.getBoolean("construida", true);
            for (Map<?, ?> m : s.getMapList("salas")) {
                Room r = new Room();
                @SuppressWarnings("unchecked") List<Integer> box = (List<Integer>) m.get("caja");
                r.box = Box.fromList(box);
                r.boss = Boolean.parseBoolean(String.valueOf(m.get("jefe")));
                d.rooms.add(r);
            }
            for (Object o : s.getList("puertas", List.of())) {
                if (o instanceof List<?> l) {
                    List<Integer> li = new ArrayList<>();
                    for (Object v : l) li.add(Integer.parseInt(v.toString()));
                    d.doors.add(Box.fromList(li));
                }
            }
            dungeons.put(d.act, d);
        }
        plugin.getLogger().info("Mazmorras construidas: " + dungeons.size());
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Dungeon d : dungeons.values()) {
            String k = String.valueOf(d.act);
            y.set(k + ".mundo", d.world);
            y.set(k + ".limites", d.bounds.toList());
            y.set(k + ".entrada.x", d.ex);
            y.set(k + ".entrada.y", d.ey);
            y.set(k + ".entrada.z", d.ez);
            y.set(k + ".construida", d.built);
            List<Map<String, Object>> rooms = new ArrayList<>();
            for (Room r : d.rooms) {
                Map<String, Object> m = new HashMap<>();
                m.put("caja", r.box.toList());
                m.put("jefe", r.boss);
                rooms.add(m);
            }
            y.set(k + ".salas", rooms);
            List<List<Integer>> doors = new ArrayList<>();
            for (Box b : d.doors) doors.add(b.toList());
            y.set(k + ".puertas", doors);
        }
        try {
            y.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar datos-mazmorras.yml: " + e.getMessage());
        }
    }

    public ConfigurationSection themeFor(int act) {
        return theme(act);
    }

    private ConfigurationSection theme(int act) {
        return cfg.getConfigurationSection("mazmorras." + act);
    }

    public String name(int act) {
        ConfigurationSection t = theme(act);
        return t == null ? "Mazmorra " + act : t.getString("nombre", "Mazmorra " + act);
    }

    public String bossOf(int act) {
        ConfigurationSection t = theme(act);
        return t == null ? null : t.getString("jefe");
    }

    private List<String> mats(ConfigurationSection t, String key) {
        List<String> out = new ArrayList<>();
        for (String s : t.getStringList(key)) {
            if (s.equals("chain")) s = "iron_chain"; // nombre antiguo
            Material m = Material.matchMaterial(s.contains("[") ? s.substring(0, s.indexOf('[')) : s);
            if (m != null && m.isBlock()) out.add(s.toLowerCase());
        }
        return out;
    }

    private final Map<String, BlockData> dataCache = new HashMap<>();

    private BlockData data(String state) {
        return dataCache.computeIfAbsent(state, k -> {
            if (k.equals("chain") || k.startsWith("chain[")) k = "iron_" + k; // nombre antiguo
            String full = k.contains(":") ? k : "minecraft:" + k;
            try {
                return Bukkit.createBlockData(full);
            } catch (IllegalArgumentException e) {
                try {
                    return Bukkit.createBlockData(full.contains("[") ? full.substring(0, full.indexOf('[')) : full);
                } catch (IllegalArgumentException e2) {
                    plugin.getLogger().warning("Bloque desconocido en mazmorra: " + k);
                    return Material.STONE_BRICKS.createBlockData();
                }
            }
        });
    }

    // ---------- Construcción ----------

    /** Asegura que la mazmorra exista y le da la brújula al jugador (cuando esté lista). */
    public void ensure(int act, Player p) {
        Dungeon d = dungeons.get(act);
        if (d != null && d.built) {
            giveCompass(p, d);
            return;
        }
        waiting.computeIfAbsent(act, k -> new ArrayList<>()).add(p.getUniqueId());
        if (building.contains(act)) {
            Txt.send(p, "<gray>La mazmorra se está formando… espera unos segundos.");
            return;
        }
        build(act, p);
    }

    /** Construye la mazmorra sin jugador (prueba automática). */
    public void buildForTest(int act) {
        build(act, null);
    }

    public boolean isBuilt(int act) {
        Dungeon d = dungeons.get(act);
        return d != null && d.built;
    }

    public int[] entrance(int act) {
        Dungeon d = dungeons.get(act);
        return d == null ? null : new int[]{d.ex, d.ey, d.ez};
    }

    public Box bounds(int act) {
        Dungeon d = dungeons.get(act);
        return d == null ? null : d.bounds;
    }

    private static void msg(Player p, String m) {
        if (p != null) Txt.send(p, m);
    }

    private void build(int act, Player p) {
        ConfigurationSection t = theme(act);
        if (t == null) {
            msg(p, "<red>No hay mazmorra configurada para el acto " + act);
            waiting.remove(act);
            return;
        }
        Location camp = plugin.items().campLocation();
        World w = camp.getWorld();
        building.add(act);
        msg(p, "<gray>Buscando dónde está " + name(act) + "<gray>…");
        searchSite(act, p, w, camp, new Random(w.getSeed() ^ (act * 7919L)), 0);
    }

    /** Prueba lugares uno por uno, cargando sus chunks en segundo plano (no congela el servidor). */
    private void searchSite(int act, Player p, World w, Location camp, Random r, int tries) {
        ConfigurationSection g = cfg.getConfigurationSection("general");
        ConfigurationSection own = theme(act);
        int min = g == null ? 250 : g.getInt("distancia-min", 250);
        int max = g == null ? 450 : g.getInt("distancia-max", 450);
        if (own != null && own.getInt("distancia-min", 0) > 0) min = own.getInt("distancia-min");
        if (own != null && own.getInt("distancia-max", 0) > min) max = own.getInt("distancia-max");
        max = Math.max(max, min + 20);
        int depth = g == null ? 18 : g.getInt("profundidad", 18);
        if (tries >= 80) {
            msg(p, "<red>No encontré un buen lugar para la mazmorra. Intenta de nuevo más tarde.");
            building.remove(act);
            waiting.remove(act);
            return;
        }
        double ang = r.nextDouble() * Math.PI * 2;
        double dist = min + r.nextDouble() * (max - min);
        int x = camp.getBlockX() + (int) (Math.cos(ang) * dist);
        int z = camp.getBlockZ() + (int) (Math.sin(ang) * dist);
        int[][] pts = {{x, z}, {x + 6, z}, {x - 6, z}, {x, z + 6}, {x, z - 6}, {x + 12, z}, {x + 18, z}};
        pe.kydium.eclipserpg.util.AsyncChunks.load(w, pe.kydium.eclipserpg.util.AsyncChunks.around(pts)).whenComplete((v, ex) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Block top = w.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                    Material m = top.getType();
                    boolean ok = ex == null && !top.isLiquid() && m != Material.ICE && m != Material.PACKED_ICE && m != Material.BLUE_ICE
                            && m != Material.SNOW_BLOCK && m != Material.POWDER_SNOW && top.getRelative(0, 1, 0).getType() != Material.SNOW;
                    if (ok) {
                        String biome = w.getBiome(x, top.getY(), z).getKey().getKey();
                        for (String bad : cfg.getStringList("general.biomas-prohibidos")) if (biome.contains(bad)) ok = false;
                    }
                    if (ok) {
                        int hMin = top.getY(), hMax = top.getY();
                        for (int i = 1; i < pts.length; i++) {
                            Block b2 = w.getHighestBlockAt(pts[i][0], pts[i][1], HeightMap.MOTION_BLOCKING_NO_LEAVES);
                            if (b2.isLiquid()) ok = false;
                            hMin = Math.min(hMin, b2.getY());
                            hMax = Math.max(hMax, b2.getY());
                        }
                        if (hMax - hMin > 4 || top.getY() - depth < w.getMinHeight() + 6) ok = false;
                    }
                    if (!ok) {
                        searchSite(act, p, w, camp, r, tries + 1);
                        return;
                    }
                    buildAt(act, p, w, top.getLocation(), top.getY());
                }));
    }

    private void buildAt(int act, Player p, World w, Location spot, int surface) {
        ConfigurationSection t = theme(act);
        ConfigurationSection g = cfg.getConfigurationSection("general");
        int depth = g == null ? 18 : g.getInt("profundidad", 18);
        DungeonBuilder.Theme th = new DungeonBuilder.Theme();
        th.floor = mats(t, "suelo");
        th.wall = mats(t, "pared");
        th.ceiling = mats(t, "techo");
        th.accent = mats(t, "acento");
        th.decor = mats(t, "decoracion");
        th.light = t.getString("luz", "glowstone");
        th.stairs = t.getString("escalera", "stone_brick_stairs");
        th.slab = t.getString("losa", "stone_brick_slab");
        th.wallBlock = t.getString("muro", "cobblestone_wall");
        th.hazard = t.getString("peligro", "lava");
        th.dimLight = t.getString("luz-tenue", "soul_lantern");
        th.blood = t.getString("sangre", "nether_wart_block");
        th.treasure = t.getString("tesoro", "gold_block");
        int floorY = surface - depth;
        DungeonBuilder builder = new DungeonBuilder(w.getSeed() + act, th, spot.getBlockX(), floorY, spot.getBlockZ(), surface);
        ConfigurationSection ownT = theme(act);
        int rMin = g == null ? 7 : g.getInt("salas-min", 7), rMax = g == null ? 10 : g.getInt("salas-max", 10);
        if (ownT != null && ownT.getInt("salas-min", 0) > 0) rMin = Math.max(3, Math.min(16, ownT.getInt("salas-min")));
        if (ownT != null && ownT.getInt("salas-max", 0) > 0) rMax = Math.min(18, ownT.getInt("salas-max"));
        rMax = Math.max(rMax, rMin);
        DungeonBuilder.Result res = builder.build(rMin, rMax);

        Dungeon d = new Dungeon();
        d.act = act;
        d.world = w.getName();
        d.bounds = res.bounds;
        d.ex = res.entranceX;
        d.ey = res.entranceY;
        d.ez = res.entranceZ;
        d.built = false;
        for (DungeonBuilder.RoomPlan rp : res.rooms) {
            if (rp.start) continue;
            Room room = new Room();
            room.box = rp.interior;
            room.boss = rp.boss;
            d.rooms.add(room);
        }
        d.doors.addAll(res.bossDoors);
        dungeons.put(act, d);
        building.add(act);
        msg(p, "<light_purple>La tierra tiembla… " + name(act) + "<light_purple> está emergiendo.");
        java.util.Set<Long> area = pe.kydium.eclipserpg.util.AsyncChunks.area(res.bounds.x1() - 2, res.bounds.z1() - 2,
                res.bounds.x2() + 2, res.bounds.z2() + 2);
        pe.kydium.eclipserpg.util.AsyncChunks.load(w, area).whenComplete((v, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
            for (long k : area) w.addPluginChunkTicket((int) (k >> 32), (int) k, plugin);
            place(act, w, res, area);
        }));
    }

    private void place(int act, World w, DungeonBuilder.Result res, java.util.Set<Long> area) {
        ConfigurationSection g = cfg.getConfigurationSection("general");

        int perTick = g == null ? 6000 : g.getInt("bloques-por-tick", 6000);
        List<DungeonBuilder.Op> ops = res.ops;
        List<DungeonBuilder.Op> specials = new ArrayList<>();
        List<int[]> joins = new ArrayList<>();
        final int[] idx = {0};
        final int finalAct = act;
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int end = Math.min(ops.size(), idx[0] + perTick);
            for (int i = idx[0]; i < end; i++) {
                DungeonBuilder.Op op = ops.get(i);
                Block b = w.getBlockAt(op.x(), op.y(), op.z());
                boolean fluid = op.block().equals("lava") || op.block().equals("water");
                BlockData bd = data(op.block());
                b.setBlockData(bd, fluid);
                if (Connect.needs(bd)) joins.add(new int[]{op.x(), op.y(), op.z()});
                if (op.special() != DungeonBuilder.Special.NONE) specials.add(op);
            }
            idx[0] = end;
            if (idx[0] >= ops.size()) {
                task.cancel();
                Connect.fix(w, joins);
                if (!Boolean.getBoolean("eclipse.prueba"))
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        for (long k : area) w.removePluginChunkTicket((int) (k >> 32), (int) k, plugin);
                    }, 100L);
                finish(finalAct, w, specials);
            }
        }, 1L, 1L);
    }

    private void finish(int act, World w, List<DungeonBuilder.Op> specials) {
        Dungeon d = dungeons.get(act);
        for (DungeonBuilder.Op op : specials) {
            Block b = w.getBlockAt(op.x(), op.y(), op.z());
            switch (op.special()) {
                case CHEST -> {
                    if (b.getState() instanceof Chest chest) fillLoot(chest.getBlockInventory(), act, 1);
                }
                case SPAWNER -> {
                    if (b.getState() instanceof CreatureSpawner sp) {
                        sp.setSpawnedType(spawnerType(act));
                        sp.setDelay(200);
                        sp.setMaxNearbyEntities(4);
                        sp.update(true, false);
                    }
                }
                case TRAP_DISPENSER -> {
                    if (b.getState() instanceof Dispenser disp) disp.getInventory().addItem(new ItemStack(Material.ARROW, 64));
                }
                default -> { }
            }
        }
        d.built = true;
        building.remove(act);
        save();
        for (UUID u : waiting.getOrDefault(act, List.of())) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) giveCompass(p, d);
        }
        waiting.remove(act);
        plugin.getLogger().info("Mazmorra del acto " + act + " construida en " + d.ex + " " + d.ey + " " + d.ez);
    }

    private EntityType spawnerType(int act) {
        ConfigurationSection th = theme(act);
        String own = th == null ? null : th.getString("generador");
        if (own != null && !own.isBlank()) {
            EntityType et = pe.kydium.eclipserpg.util.Reg.entity(own.trim().toLowerCase(java.util.Locale.ROOT));
            if (et != null && et.isAlive()) return et;
        }
        return switch (act) {
            case 1 -> EntityType.ZOMBIE;
            case 2, 8 -> EntityType.SKELETON;
            case 3 -> EntityType.VINDICATOR;
            case 4 -> EntityType.DROWNED;
            case 5 -> EntityType.HUSK;
            case 6 -> EntityType.BLAZE;
            case 7 -> EntityType.BOGGED;
            case 9 -> EntityType.PIGLIN_BRUTE;
            default -> EntityType.ENDERMAN;
        };
    }

    /** Llena un cofre con el botín del acto. rolls = cuántas veces se tira la tabla. */
    public void fillLoot(Inventory inv, int act, int rolls) {
        ConfigurationSection t = theme(act) != null ? theme(act) : theme(Math.max(1, Math.min(10, act)));
        if (t == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        // nunca un cofre vacío: si salió nada, se vuelve a sortear
        for (int i = 0; i < rolls || (inv.isEmpty() && i < rolls + 6); i++) {
            for (String line : t.getStringList("botin")) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 3) continue;
                double chance;
                try {
                    chance = Double.parseDouble(parts[2]);
                } catch (NumberFormatException e) {
                    continue;
                }
                if (r.nextDouble() >= chance) continue;
                String spec = parts[0].contains(":") ? parts[0] : "material:" + parts[0];
                int amount = Integer.parseInt(parts[1]);
                ItemStack it = plugin.shops().buildSpec(spec, 1);
                if (it == null) continue;
                it.setAmount(Math.max(1, Math.min(it.getMaxStackSize(), 1 + r.nextInt(Math.max(1, amount)))));
                int slot = r.nextInt(inv.getSize());
                if (inv.getItem(slot) == null) inv.setItem(slot, it);
                else inv.addItem(it);
            }
        }
    }

    public void giveCompass(Player p, Dungeon d) {
        World w = Bukkit.getWorld(d.world);
        if (w == null) return;
        Location target = new Location(w, d.ex, d.ey, d.ez);
        ItemStack it = new ItemStack(Material.COMPASS);
        if (it.getItemMeta() instanceof CompassMeta meta) {
            meta.setLodestone(target);
            meta.setLodestoneTracked(false);
            meta.displayName(Txt.item("<light_purple>Brújula: " + name(d.act)));
            meta.lore(Txt.lore(List.of("<gray>Apunta a la entrada de la mazmorra.",
                    "<gray>X " + d.ex + " · Y " + d.ey + " · Z " + d.ez)));
            it.setItemMeta(meta);
        }
        plugin.items().give(p, it);
        Txt.send(p, "<light_purple>" + name(d.act) + " <gray>está en <white>X " + d.ex + ", Z " + d.ez
                + "<gray>. Sigue la brújula.");
    }

    /** Entrada de la mazmorra de ese acto (null si todavía no existe). */
    public Location entrance(int act) {
        Dungeon d = dungeons.get(act);
        World w = d == null ? null : Bukkit.getWorld(d.world);
        return w == null ? null : new Location(w, d.ex + 0.5, d.ey, d.ez + 0.5);
    }

    public Dungeon get(int act) {
        return dungeons.get(act);
    }

    // ---------- Funcionamiento ----------

    public void startTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 10L);
    }

    private long cooldownMs() {
        return cfg.getLong("general.reaparicion-minutos", 15) * 60_000L;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Dungeon d : dungeons.values()) {
            if (!d.built) continue;
            World w = Bukkit.getWorld(d.world);
            if (w == null) continue;
            List<Player> inside = new ArrayList<>();
            for (Player p : w.getPlayers()) {
                if (p.getGameMode() == GameMode.SPECTATOR) continue;
                if (d.bounds.contains(p.getLocation())) inside.add(p);
            }
            for (Player p : inside) plugin.quests().addProgress(p, Quest.Type.MAZMORRA, String.valueOf(d.act), 1, false);
            // ambiente: título al entrar, sonidos y susurros
            Set<UUID> now2 = new HashSet<>();
            for (Player p : inside) {
                now2.add(p.getUniqueId());
                if (!d.inside.contains(p.getUniqueId())) {
                    p.showTitle(Title.title(Txt.mm(name(d.act)), Txt.mm("<dark_gray><italic>" + WHISPERS[ThreadLocalRandom.current().nextInt(WHISPERS.length)]),
                            Title.Times.times(Duration.ofMillis(800), Duration.ofSeconds(3), Duration.ofMillis(1200))));
                    Txt.sound(p, "ambient.cave", 1f, 0.6f);
                } else if (ThreadLocalRandom.current().nextDouble() < 0.02) {
                    Txt.sound(p, AMBIENT[ThreadLocalRandom.current().nextInt(AMBIENT.length)], 0.8f, 0.5f + ThreadLocalRandom.current().nextFloat() * 0.4f);
                    if (ThreadLocalRandom.current().nextDouble() < 0.3) {
                        p.sendActionBar(Txt.mm("<dark_gray><italic>" + WHISPERS[ThreadLocalRandom.current().nextInt(WHISPERS.length)]));
                    }
                }
            }
            d.inside.clear();
            d.inside.addAll(now2);

            for (Room r : d.rooms) {
                r.mobs.removeIf(u -> {
                    Entity e = Bukkit.getEntity(u);
                    return e == null || e.isDead() || !e.isValid();
                });
                List<Player> inRoom = new ArrayList<>();
                for (Player p : inside) if (r.box.contains(p.getLocation())) inRoom.add(p);
                if (r.boss) {
                    tickBoss(d, r, w, inRoom, inside, now);
                    continue;
                }
                if (r.active && r.mobs.isEmpty()) {
                    r.active = false;
                    r.clearedAt = now;
                    for (Player p : inRoom) p.sendActionBar(Txt.mm("<green>✔ Sala despejada"));
                } else if (!r.active && !inRoom.isEmpty() && now - r.clearedAt > cooldownMs()) {
                    r.active = true;
                    int count = cfg.getInt("general.enemigos-por-sala", 3) + d.act / 3 + (inRoom.size() - 1);
                    spawnWave(d, r, w, count);
                    for (Player p : inRoom) {
                        p.sendActionBar(Txt.mm("<red>⚠ ¡Emboscada de Corrompidos!"));
                        Txt.sound(p, "entity.illusioner.cast_spell", 1f, 0.7f);
                    }
                }
            }
        }
    }

    private void tickBoss(Dungeon d, Room r, World w, List<Player> inRoom, List<Player> inside, long now) {
        if (d.spawning) return;
        Entity boss = d.boss == null ? null : Bukkit.getEntity(d.boss);
        boolean alive = boss != null && !boss.isDead() && boss.isValid();
        if (alive) {
            if (inside.isEmpty()) {
                if (d.emptySince == 0) d.emptySince = now;
                if (now - d.emptySince > 30_000) {
                    boss.remove();
                    d.boss = null;
                    setDoors(d, w, false);
                }
            } else {
                d.emptySince = 0;
            }
            return;
        }
        if (d.doorsClosed && d.boss != null) {
            // el jefe murió
            d.boss = null;
            setDoors(d, w, false);
            d.bossReadyAt = now + 60_000;
            Location c = new Location(w, r.box.centerX(), r.box.y1(), r.box.centerZ());
            Block chest = c.getBlock();
            chest.setType(Material.CHEST, false);
            if (chest.getState() instanceof Chest ch) fillLoot(ch.getBlockInventory(), d.act, 2);
            for (Player p : inside) {
                p.showTitle(Title.title(Txt.mm("<gold><bold>¡Mazmorra conquistada!"), Txt.mm("<gray>El cofre del jefe apareció en el centro"),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(600))));
            }
            return;
        }
        if (inRoom.isEmpty() || now < d.bossReadyAt || d.boss != null) return;
        String bossId = bossOf(d.act);
        if (bossId == null || plugin.mobs().def(bossId) == null) return;
        // limpiar el cofre anterior del centro
        Block center = w.getBlockAt(r.box.centerX(), r.box.y1(), r.box.centerZ());
        if (center.getType() == Material.CHEST) center.setType(Material.AIR, false);
        setDoors(d, w, true);
        d.spawning = true;
        for (Player p : inside) {
            p.showTitle(Title.title(Txt.mm("<dark_red><bold>¡Las puertas se cierran!"), Txt.mm(plugin.mobs().def(bossId).name),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
            Txt.sound(p, "block.iron_door.close", 1f, 0.6f);
            Txt.sound(p, "entity.wither.spawn", 0.6f, 0.9f);
        }
        Location spawn = new Location(w, r.box.centerX() + 0.5, r.box.y1(), r.box.centerZ() + 0.5);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            w.strikeLightningEffect(spawn);
            LivingEntity le = plugin.mobs().spawn(bossId, spawn);
            d.spawning = false;
            if (le == null) {
                d.boss = null;
                setDoors(d, w, false);
                return;
            }
            le.getPersistentDataContainer().set(Keys.DUNGEON, PersistentDataType.INTEGER, d.act);
            d.boss = le.getUniqueId();
        }, 60L);
    }

    private void setDoors(Dungeon d, World w, boolean closed) {
        d.doorsClosed = closed;
        for (Box b : d.doors) {
            for (int x = b.x1(); x <= b.x2(); x++)
                for (int y = b.y1(); y <= b.y2(); y++)
                    for (int z = b.z1(); z <= b.z2(); z++) {
                        Block bl = w.getBlockAt(x, y, z);
                        if (closed && bl.getType().isAir()) bl.setType(Material.IRON_BARS, true);
                        else if (!closed && bl.getType() == Material.IRON_BARS) bl.setType(Material.AIR, true);
                    }
        }
    }

    private void spawnWave(Dungeon d, Room r, World w, int count) {
        ConfigurationSection th = theme(d.act);
        List<String> pool = th == null ? List.of() : th.getStringList("enemigos");
        if (pool.isEmpty()) pool = plugin.getConfig().getStringList("grieta.mobs-por-tier." + d.act);
        if (pool.isEmpty()) return;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            int x = rnd.nextInt(r.box.x1() + 1, r.box.x2());
            int z = rnd.nextInt(r.box.z1() + 1, r.box.z2());
            Location at = new Location(w, x + 0.5, r.box.y1(), z + 0.5);
            if (!at.getBlock().isPassable()) continue;
            LivingEntity le = plugin.world().spawnMob(pool.get(rnd.nextInt(pool.size())).trim().toLowerCase(java.util.Locale.ROOT), at);
            if (le == null) continue;
            le.getPersistentDataContainer().set(Keys.DUNGEON, PersistentDataType.INTEGER, d.act);
            r.mobs.add(le.getUniqueId());
        }
    }

    /** ¿Esta ubicación está dentro de alguna mazmorra? Devuelve el acto o 0. */
    public int actAt(Location l) {
        for (Dungeon d : dungeons.values()) {
            if (d.built && l.getWorld() != null && l.getWorld().getName().equals(d.world) && d.bounds.contains(l)) return d.act;
        }
        return 0;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawner(CreatureSpawnEvent e) {
        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER) return;
        int act = actAt(e.getLocation());
        if (act > 0) plugin.mobs().corrupt(e.getEntity(), act);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        Integer act = e.getEntity().getPersistentDataContainer().get(Keys.DUNGEON, PersistentDataType.INTEGER);
        if (act == null) return;
        e.getDrops().removeIf(it -> it.getType() == Material.ROTTEN_FLESH && ThreadLocalRandom.current().nextBoolean());
    }

    /** Para /eclipse mazmorra: lista de mazmorras construidas. */
    public List<String> describe() {
        List<String> out = new ArrayList<>();
        for (int act = 1; act <= 10; act++) {
            Dungeon d = dungeons.get(act);
            out.add((d == null ? "<gray>" : "<white>") + act + ". " + name(act) + (d == null ? " <gray>(sin construir)"
                    : " <gray>X " + d.ex + " Y " + d.ey + " Z " + d.ez));
        }
        return out;
    }
}
