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

    private record Ruin(String world, int x, int y, int z) {}

    private final EclipseRPG plugin;
    private final List<Ruin> pending = new ArrayList<>();
    private final File file;
    private int created = 0;

    public RuinManager(EclipseRPG plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "datos-ruinas.yml");
    }

    private YamlConfiguration cfg() {
        return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "mazmorras.yml"));
    }

    public void load() {
        pending.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String s : y.getStringList("pendientes")) {
            String[] p = s.split(";");
            if (p.length == 4) pending.add(new Ruin(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])));
        }
        created = y.getInt("creadas", 0);
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> out = new ArrayList<>();
        for (Ruin r : pending) out.add(r.world() + ";" + r.x() + ";" + r.y() + ";" + r.z());
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

    private void build(World w, int cx, int cz) {
        if (!w.isChunkLoaded(cx >> 4, cz >> 4)) return;
        Block top = w.getHighestBlockAt(cx, cz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        Material ground = top.getType();
        if (top.isLiquid() || ground == Material.ICE || !ground.isSolid()) return;
        boolean desert = ground == Material.SAND || ground == Material.RED_SAND || ground == Material.SANDSTONE;
        Material[] floor = desert
                ? new Material[]{Material.SANDSTONE, Material.CUT_SANDSTONE, Material.SMOOTH_SANDSTONE}
                : new Material[]{Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS, Material.STONE_BRICKS, Material.MOSSY_COBBLESTONE};
        Material pillar = desert ? Material.CHISELED_SANDSTONE : Material.CHISELED_STONE_BRICKS;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int y = top.getY();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int x = cx + dx, z = cz + dz;
                // base sólida hasta el suelo
                for (int yy = y; yy >= y - 3; yy--) {
                    Block b = w.getBlockAt(x, yy, z);
                    if (b.getType().isSolid() && yy < y) break;
                    b.setType(floor[r.nextInt(floor.length)], false);
                }
                for (int yy = y + 1; yy <= y + 5; yy++) w.getBlockAt(x, yy, z).setType(Material.AIR, false);
                boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
                if (corner) {
                    int h = 3 + r.nextInt(3);
                    for (int yy = y + 1; yy <= y + h; yy++) w.getBlockAt(x, yy, z).setType(pillar, false);
                } else if (edge && r.nextDouble() < 0.6) {
                    int h = 1 + r.nextInt(3);
                    for (int yy = y + 1; yy <= y + h; yy++) w.getBlockAt(x, yy, z).setType(floor[r.nextInt(floor.length)], false);
                } else if (!edge && r.nextDouble() < 0.06) {
                    w.getBlockAt(x, y + 1, z).setType(Material.COBWEB, false);
                }
            }
        }
        w.getBlockAt(cx, y + 1, cz).setType(Material.CRYING_OBSIDIAN, false);
        w.getBlockAt(cx, y + 2, cz).setType(Material.SOUL_LANTERN, false);
        w.getBlockAt(cx + 1, y + 1, cz).setType(Material.CHEST, false);
        pending.add(new Ruin(w.getName(), cx, y + 1, cz));
        created++;
        if (created % 5 == 0) save();
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
        Block chest = w.getBlockAt(ru.x() + 1, ru.y(), ru.z());
        if (chest.getState() instanceof Chest c) plugin.dungeons().fillLoot(c.getBlockInventory(), tier, 1);
    }
}
