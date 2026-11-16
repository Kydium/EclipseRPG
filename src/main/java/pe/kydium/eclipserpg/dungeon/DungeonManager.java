package pe.kydium.eclipserpg.dungeon;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.Dispenser;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
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
    }

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
        cfg = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "mazmorras.yml"));
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

    private List<Material> mats(ConfigurationSection t, String key) {
        List<Material> out = new ArrayList<>();
        for (String s : t.getStringList(key)) {
            Material m = Material.matchMaterial(s);
            if (m != null && m.isBlock()) out.add(m);
        }
        return out;
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

    private void build(int act, Player p) {
        ConfigurationSection t = theme(act);
        if (t == null) {
            Txt.send(p, "<red>No hay mazmorra configurada para el acto " + act);
            return;
        }
        Location camp = plugin.items().campLocation();
        World w = camp.getWorld();
        ConfigurationSection g = cfg.getConfigurationSection("general");
        int min = g == null ? 250 : g.getInt("distancia-min", 250);
        int max = g == null ? 450 : g.getInt("distancia-max", 450);
        int depth = g == null ? 18 : g.getInt("profundidad", 18);
        Random r = new Random(w.getSeed() ^ (act * 7919L));
        Location spot = null;
        int surface = 0;
        for (int tries = 0; tries < 16 && spot == null; tries++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double dist = min + r.nextDouble() * (max - min);
            int x = camp.getBlockX() + (int) (Math.cos(ang) * dist);
            int z = camp.getBlockZ() + (int) (Math.sin(ang) * dist);
            Block top = w.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Material m = top.getType();
            if (top.isLiquid() || m == Material.ICE || m == Material.PACKED_ICE || m == Material.BLUE_ICE) continue;
            if (top.getY() - depth < w.getMinHeight() + 6) continue;
            spot = top.getLocation();
            surface = top.getY();
        }
        if (spot == null) {
            Txt.send(p, "<red>No encontré un buen lugar para la mazmorra. Intenta de nuevo más tarde.");
            waiting.remove(act);
            return;
        }
        DungeonBuilder.Theme th = new DungeonBuilder.Theme();
        th.floor = mats(t, "suelo");
        th.wall = mats(t, "pared");
        th.ceiling = mats(t, "techo");
        th.accent = mats(t, "acento");
        th.decor = mats(t, "decoracion");
        Material light = Material.matchMaterial(t.getString("luz", "glowstone"));
        if (light != null) th.light = light;
        int floorY = surface - depth;
        DungeonBuilder builder = new DungeonBuilder(w.getSeed() + act, th, spot.getBlockX(), floorY, spot.getBlockZ(), surface);
        DungeonBuilder.Result res = builder.build(g == null ? 7 : g.getInt("salas-min", 7), g == null ? 10 : g.getInt("salas-max", 10));

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
        Txt.send(p, "<light_purple>La tierra tiembla… " + name(act) + "<light_purple> está emergiendo.");

        int perTick = g == null ? 6000 : g.getInt("bloques-por-tick", 6000);
        List<DungeonBuilder.Op> ops = res.ops;
        List<DungeonBuilder.Op> specials = new ArrayList<>();
        final int[] idx = {0};
        final int finalAct = act;
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int end = Math.min(ops.size(), idx[0] + perTick);
            for (int i = idx[0]; i < end; i++) {
                DungeonBuilder.Op op = ops.get(i);
                Block b = w.getBlockAt(op.x(), op.y(), op.z());
                b.setType(op.mat(), false);
                if (op.special() != DungeonBuilder.Special.NONE) specials.add(op);
            }
            idx[0] = end;
            if (idx[0] >= ops.size()) {
                task.cancel();
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
                    BlockData data = b.getBlockData();
                    if (data instanceof Directional dir) {
                        dir.setFacing(BlockFace.UP);
                        b.setBlockData(dir, false);
                    }
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
        ConfigurationSection t = theme(Math.max(1, Math.min(10, act)));
        if (t == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < rolls; i++) {
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
        List<String> pool = plugin.getConfig().getStringList("grieta.mobs-por-tier." + d.act);
        if (pool.isEmpty()) return;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            int x = rnd.nextInt(r.box.x1() + 1, r.box.x2());
            int z = rnd.nextInt(r.box.z1() + 1, r.box.z2());
            Location at = new Location(w, x + 0.5, r.box.y1(), z + 0.5);
            if (!at.getBlock().isPassable()) continue;
            LivingEntity le = plugin.mobs().spawn(pool.get(rnd.nextInt(pool.size())), at);
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
