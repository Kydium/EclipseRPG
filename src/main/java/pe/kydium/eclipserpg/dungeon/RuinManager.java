package pe.kydium.eclipserpg.dungeon;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.util.Txt;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Ruinas corruptas pequeñas que aparecen al explorar terreno nuevo. */
public final class RuinManager implements Listener {

    private record Ruin(String world, int x, int y, int z, int chx, int chy, int chz) {}

    private final EclipseRPG plugin;
    private final List<Ruin> pending = new ArrayList<>();
    private final File file;
    private int created = 0;

    public RuinManager(EclipseRPG plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "datos-ruinas.yml");
    }

    private YamlConfiguration cfg() {
        return plugin.story().yaml("mazmorras");
    }

    public void load() {
        pending.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String s : y.getStringList("pendientes")) {
            String[] p = s.split(";");
            if (p.length == 4) pending.add(new Ruin(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]),
                    Integer.parseInt(p[1]) + 1, Integer.parseInt(p[2]), Integer.parseInt(p[3])));
            else if (p.length == 7) pending.add(new Ruin(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]),
                    Integer.parseInt(p[4]), Integer.parseInt(p[5]), Integer.parseInt(p[6])));
        }
        created = y.getInt("creadas", 0);
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> out = new ArrayList<>();
        for (Ruin r : pending) out.add(r.world() + ";" + r.x() + ";" + r.y() + ";" + r.z() + ";" + r.chx() + ";" + r.chy() + ";" + r.chz());
        y.set("pendientes", out);
        y.set("creadas", created);
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar datos-ruinas.yml");
        }
    }

    private double chance;
    private int minSpawnDist;
    private int radius;
    private boolean active;

    public void reloadSettings() {
        YamlConfiguration c = cfg();
        active = c.getBoolean("ruinas.activas", true);
        chance = c.getDouble("ruinas.probabilidad", 0.004);
        minSpawnDist = c.getInt("ruinas.distancia-min-spawn", 150);
        radius = c.getInt("ruinas.radio-activacion", 8);
    }

    @EventHandler
    public void onChunk(ChunkLoadEvent e) {
        if (!active || !e.isNewChunk()) return;
        World w = e.getWorld();
        if (w.getEnvironment() != World.Environment.NORMAL) return;
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;
        int x = (e.getChunk().getX() << 4) + 8;
        int z = (e.getChunk().getZ() << 4) + 8;
        Location spawn = w.getSpawnLocation();
        if (Math.hypot(x - spawn.getX(), z - spawn.getZ()) < minSpawnDist) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> build(w, x, z), 2L);
    }

    private final java.util.Map<String, org.bukkit.block.data.BlockData> cache = new java.util.HashMap<>();

    private org.bukkit.block.data.BlockData data(String state) {
        return cache.computeIfAbsent(state, k -> {
            try {
                return Bukkit.createBlockData("minecraft:" + k);
            } catch (IllegalArgumentException e) {
                try {
                    return Bukkit.createBlockData("minecraft:" + (k.contains("[") ? k.substring(0, k.indexOf('[')) : k));
                } catch (IllegalArgumentException e2) {
                    return Material.STONE_BRICKS.createBlockData();
                }
            }
        });
    }

    /** Construye una ruina aquí (también lo usa la prueba automática). Devuelve el cofre o null. */
    public Block buildAt(World w, int cx, int cz) {
        return build(w, cx, cz);
    }

    private Block build(World w, int cx, int cz) {
        if (!w.isChunkLoaded(cx >> 4, cz >> 4)) return null;
        Block top = w.getHighestBlockAt(cx, cz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        Material ground = top.getType();
        if (top.isLiquid() || ground == Material.ICE || !ground.isSolid()) return null;
        boolean desert = ground == Material.SAND || ground == Material.RED_SAND || ground == Material.SANDSTONE;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        RuinBuilder.Variant v = RuinBuilder.Variant.values()[r.nextInt(RuinBuilder.Variant.values().length)];
        RuinBuilder.Result res = new RuinBuilder(r.nextLong(), desert).build(v);
        int y = top.getY();
        java.util.List<int[]> joins = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, String> e : res.blocks.entrySet()) {
            String[] p = e.getKey().split(",");
            int x = cx + Integer.parseInt(p[0]), yy = y + Integer.parseInt(p[1]), z = cz + Integer.parseInt(p[2]);
            org.bukkit.block.data.BlockData bd = data(e.getValue());
            w.getBlockAt(x, yy, z).setBlockData(bd, false);
            if (Connect.needs(bd)) joins.add(new int[]{x, yy, z});
            // rellenar debajo del suelo de la ruina para que no quede flotando
            if (Integer.parseInt(p[1]) == 0) {
                for (int dy = 1; dy <= 4; dy++) {
                    Block below = w.getBlockAt(x, yy - dy, z);
                    if (below.getType().isSolid()) break;
                    below.setType(desert ? Material.SANDSTONE : Material.COBBLESTONE, false);
                }
            }
        }
        Connect.fix(w, joins);
        // el cofre ya trae botín (según la distancia al spawn); al despertar la ruina salen sus guardianes
        Block chestBlock = w.getBlockAt(cx + res.chestX, y + res.chestY, cz + res.chestZ);
        int tier = (int) Math.max(1, Math.min(10, 1 + Math.hypot(cx - w.getSpawnLocation().getX(), cz - w.getSpawnLocation().getZ()) / 400));
        if (chestBlock.getState() instanceof Chest c) plugin.dungeons().fillLoot(c.getBlockInventory(), tier, 2);
        pending.add(new Ruin(w.getName(), cx + res.altarX, y + res.altarY, cz + res.altarZ,
                cx + res.chestX, y + res.chestY, cz + res.chestZ));
        created++;
        if (created % 5 == 0) save();
        return chestBlock;
    }

    public void startTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 60L, 20L);
    }

    private void tick() {
        if (pending.isEmpty()) return;
        List<Ruin> done = new ArrayList<>();
        for (Ruin ru : pending) {
            World w = Bukkit.getWorld(ru.world());
            if (w == null || !w.isChunkLoaded(ru.x() >> 4, ru.z() >> 4)) continue;
            Location at = new Location(w, ru.x() + 0.5, ru.y(), ru.z() + 0.5);
            Player near = null;
            for (Player p : w.getPlayers()) {
                if (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE) continue;
                if (p.getLocation().distanceSquared(at) <= radius * radius) {
                    near = p;
                    break;
                }
            }
            if (near == null) continue;
            activate(ru, w, at, near);
            done.add(ru);
        }
        if (!done.isEmpty()) {
            pending.removeAll(done);
            save();
        }
    }

    private void activate(Ruin ru, World w, Location at, Player p) {
        int tier = plugin.levels().tier(p);
        w.getBlockAt(ru.x(), ru.y(), ru.z()).setType(Material.OBSIDIAN, false);
        w.strikeLightningEffect(at);
        for (Player o : w.getPlayers()) {
            if (o.getLocation().distanceSquared(at) < 40 * 40) {
                Txt.send(o, "<dark_purple>¡Una ruina corrupta despierta! <gray>Derrota a sus guardianes.");
                Txt.sound(o, "entity.elder_guardian.curse", 0.8f, 1.2f);
            }
        }
        List<String> pool = plugin.getConfig().getStringList("grieta.mobs-por-tier." + tier);
        int count = 3 + tier / 3;
        for (int i = 0; i < count && !pool.isEmpty(); i++) {
            String id = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            LivingEntity le = plugin.mobs().spawn(id, plugin.mobs().spawnSpot(at, 4));
            if (le != null) le.setRemoveWhenFarAway(true);
        }
        Block chest = w.getBlockAt(ru.chx(), ru.chy(), ru.chz());
        if (chest.getState() instanceof Chest c) plugin.dungeons().fillLoot(c.getBlockInventory(), tier, 1); // premio extra
    }
}
