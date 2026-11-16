package pe.kydium.eclipserpg.dungeon;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.util.Txt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Construye la Aldea del Alba alrededor del campamento: plaza con pozo, 8 casas
 * (una por NPC), caminos, granjas, muralla con puertas y aldeanos de relleno.
 */
public final class VillageBuilder {

    private record Op(int x, int y, int z, Material m) {}

    private static final String[] NPC_ORDER = {"tula", "bruno", "ines", "pip", "lia", "odon", "velkar", "errante"};
    private static final String[] NAMES = {"Rosa", "Tomás", "Lucía", "Mateo", "Carmen", "Andrés", "Elena", "Julián",
            "Marta", "Diego", "Sofía", "Ramón", "Valeria", "Hugo", "Inés la Joven", "Benito", "Paula", "Óscar", "Nina", "Gael"};

    private final EclipseRPG plugin;
    private final List<Op> ops = new ArrayList<>();
    private final Random rnd = new Random();
    private final Map<String, Location> npcSpots = new LinkedHashMap<>();

    public VillageBuilder(EclipseRPG plugin) {
        this.plugin = plugin;
    }

    private void set(int x, int y, int z, Material m) {
        ops.add(new Op(x, y, z, m));
    }

    public void build(Player p) {
        Location c = plugin.items().campLocation();
        World w = c.getWorld();
        int cx = c.getBlockX(), cz = c.getBlockZ();
        int gy = c.getBlockY() - 1; // nivel del suelo
        int R = 30;

        // 1) Aplanar y limpiar
        for (int x = cx - R; x <= cx + R; x++) {
            for (int z = cz - R; z <= cz + R; z++) {
                for (int y = gy - 3; y < gy; y++) set(x, y, z, Material.DIRT);
                set(x, gy, z, Material.GRASS_BLOCK);
                for (int y = gy + 1; y <= gy + 14; y++) set(x, y, z, Material.AIR);
            }
        }
        // 2) Caminos en cruz
        for (int d = -R; d <= R; d++) {
            for (int o = -1; o <= 1; o++) {
                set(cx + d, gy, cz + o, Material.DIRT_PATH);
                set(cx + o, gy, cz + d, Material.DIRT_PATH);
            }
        }
        // 3) Plaza y pozo
        for (int x = cx - 6; x <= cx + 6; x++)
            for (int z = cz - 6; z <= cz + 6; z++)
                set(x, gy, z, rnd.nextDouble() < 0.25 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS);
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int z = cz - 2; z <= cz + 2; z++) {
                boolean rim = Math.abs(x - cx) == 2 || Math.abs(z - cz) == 2;
                if (rim) set(x, gy + 1, z, Material.COBBLESTONE_WALL);
                else {
                    set(x, gy, z, Material.WATER);
                    set(x, gy - 1, z, Material.WATER);
                }
            }
        }
        for (int[] pc : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            for (int y = gy + 2; y <= gy + 4; y++) set(cx + pc[0], y, cz + pc[1], Material.OAK_FENCE);
        }
        for (int x = cx - 2; x <= cx + 2; x++)
            for (int z = cz - 2; z <= cz + 2; z++) set(x, gy + 5, z, Material.OAK_SLAB);
        set(cx, gy + 4, cz, Material.LANTERN);

        // 4) Ocho casas alrededor de la plaza, con la puerta mirando al centro
        int[][] houses = {{-14, -8}, {14, -8}, {-14, 8}, {14, 8}, {-8, -15}, {8, -15}, {-8, 15}, {8, 15}};
        for (int i = 0; i < houses.length; i++) {
            int hx = cx + houses[i][0], hz = cz + houses[i][1];
            // la puerta da hacia el lado más cercano al centro
            boolean alongX = Math.abs(houses[i][0]) > Math.abs(houses[i][1]);
            int doorDx = alongX ? -Integer.signum(houses[i][0]) : 0;
            int doorDz = alongX ? 0 : -Integer.signum(houses[i][1]);
            house(hx, gy, hz, doorDx, doorDz, NPC_ORDER[i]);
            Location spot = new Location(w, hx + doorDx * 5 + 0.5, gy + 1, hz + doorDz * 5 + 0.5);
            spot.setDirection(new org.bukkit.util.Vector(-doorDx, 0, -doorDz).multiply(-1));
            npcSpots.put(NPC_ORDER[i], spot);
            // camino de la puerta al camino principal
            for (int s = 4; s <= 8; s++) set(hx + doorDx * s, gy, hz + doorDz * s, Material.DIRT_PATH);
        }

        // 5) Granjas
        farm(cx + 19, gy, cz - 22);
        farm(cx - 25, gy, cz + 18);

        // 6) Muralla con puertas en los caminos
        for (int d = -R; d <= R; d++) {
            for (int[] side : new int[][]{{d, -R}, {d, R}, {-R, d}, {R, d}}) {
                int x = cx + side[0], z = cz + side[1];
                boolean gate = Math.abs(side[0]) <= 1 || Math.abs(side[1]) <= 1;
                if (gate) continue;
                set(x, gy + 1, z, Material.COBBLESTONE_WALL);
                set(x, gy + 2, z, Material.COBBLESTONE_WALL);
                if (Math.floorMod(d, 6) == 0) set(x, gy + 3, z, Material.LANTERN);
            }
        }
        // faroles en los caminos
        for (int d = 10; d <= R - 2; d += 8) {
            for (int[] lp : new int[][]{{d, 2}, {-d, -2}, {2, -d}, {-2, d}}) {
                set(cx + lp[0], gy + 1, cz + lp[1], Material.OAK_FENCE);
                set(cx + lp[0], gy + 2, cz + lp[1], Material.OAK_FENCE);
                set(cx + lp[0], gy + 3, cz + lp[1], Material.LANTERN);
            }
        }

        Txt.send(p, "<yellow>Construyendo la Aldea del Alba… (" + ops.size() + " bloques)");
        final int[] idx = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int end = Math.min(ops.size(), idx[0] + 8000);
            for (int i = idx[0]; i < end; i++) {
                Op o = ops.get(i);
                Block b = w.getBlockAt(o.x(), o.y(), o.z());
                b.setType(o.m(), o.m() == Material.WATER);
            }
            idx[0] = end;
            if (idx[0] >= ops.size()) {
                task.cancel();
                done(p, w, c, gy);
            }
        }, 1L, 1L);
    }

    private void done(Player p, World w, Location c, int gy) {
        // guardar los puestos de cada NPC para /eclipse crearnpcs
        for (Map.Entry<String, Location> e : npcSpots.entrySet()) {
            Location l = e.getValue();
            String k = "aldea.puestos." + e.getKey();
            plugin.getConfig().set(k + ".x", l.getX());
            plugin.getConfig().set(k + ".y", l.getY());
            plugin.getConfig().set(k + ".z", l.getZ());
            plugin.getConfig().set(k + ".yaw", l.getYaw());
        }
        plugin.getConfig().set("aldea.mundo", w.getName());
        plugin.saveConfig();
        int count = plugin.getConfig().getInt("aldea.aldeanos-relleno", 12);
        spawnVillagers(w, c, gy, count);
        Txt.send(p, "<green>¡La Aldea del Alba está lista! <gray>Ahora usa <white>/eclipse crearnpcs</white> para poner a los NPCs en sus casas.");
    }

    public void spawnVillagers(World w, Location c, int gy, int count) {
        Villager.Profession[] profs = {Villager.Profession.FARMER, Villager.Profession.FISHERMAN, Villager.Profession.SHEPHERD,
                Villager.Profession.FLETCHER, Villager.Profession.LIBRARIAN, Villager.Profession.MASON,
                Villager.Profession.BUTCHER, Villager.Profession.NONE, Villager.Profession.NONE};
        boolean invulnerable = plugin.getConfig().getBoolean("aldea.aldeanos-invencibles", true);
        for (int i = 0; i < count; i++) {
            Location at = new Location(w, c.getBlockX() + rnd.nextInt(-20, 21) + 0.5, gy + 1, c.getBlockZ() + rnd.nextInt(-20, 21) + 0.5);
            if (!at.getBlock().isPassable()) at = c.clone().add(rnd.nextInt(-5, 6), 0, rnd.nextInt(-5, 6));
            if (!(w.spawnEntity(at, EntityType.VILLAGER) instanceof Villager v)) continue;
            v.customName(Txt.mm("<gray>" + NAMES[rnd.nextInt(NAMES.length)]));
            v.setCustomNameVisible(false);
            v.setProfession(profs[rnd.nextInt(profs.length)]);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            v.setInvulnerable(invulnerable);
        }
    }

    private static final Map<String, Material[]> INTERIOR = Map.of(
            "tula", new Material[]{Material.COMPOSTER, Material.FLOWER_POT, Material.CAMPFIRE},
            "bruno", new Material[]{Material.ANVIL, Material.BLAST_FURNACE, Material.SMITHING_TABLE},
            "ines", new Material[]{Material.GRINDSTONE, Material.TARGET, Material.CHEST},
            "pip", new Material[]{Material.BARREL, Material.BARREL, Material.CHEST},
            "lia", new Material[]{Material.CARTOGRAPHY_TABLE, Material.LECTERN, Material.BARREL},
            "odon", new Material[]{Material.BREWING_STAND, Material.CAULDRON, Material.BOOKSHELF},
            "velkar", new Material[]{Material.ENCHANTING_TABLE, Material.BOOKSHELF, Material.BOOKSHELF},
            "errante", new Material[]{Material.SOUL_CAMPFIRE, Material.CRYING_OBSIDIAN, Material.LODESTONE});

    private void house(int hx, int gy, int hz, int ddx, int ddz, String npc) {
        boolean dark = npc.equals("velkar") || npc.equals("errante");
        Material wall = dark ? Material.DEEPSLATE_BRICKS : Material.OAK_PLANKS;
        Material corner = dark ? Material.POLISHED_BLACKSTONE : Material.OAK_LOG;
        Material base = Material.COBBLESTONE;
        Material roof = dark ? Material.PURPLE_TERRACOTTA : Material.DARK_OAK_PLANKS;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int x = hx + dx, z = hz + dz;
                boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                boolean cornerP = Math.abs(dx) == 3 && Math.abs(dz) == 3;
                set(x, gy, z, edge ? base : Material.SPRUCE_PLANKS);
                for (int y = gy + 1; y <= gy + 4; y++) {
                    if (!edge) continue;
                    Material m = cornerP ? corner : (y == gy + 1 ? base : wall);
                    boolean window = y == gy + 2 && !cornerP && (dx == 0 || dz == 0);
                    set(x, y, z, window ? Material.GLASS_PANE : m);
                }
            }
        }
        // puerta (hueco de 2 de alto)
        int doorX = hx + ddx * 3, doorZ = hz + ddz * 3;
        set(doorX, gy + 1, doorZ, Material.AIR);
        set(doorX, gy + 2, doorZ, Material.AIR);
        set(doorX + ddx, gy + 3, doorZ + ddz, Material.LANTERN);
        // techo escalonado
        for (int k = 0; k <= 3; k++) {
            int s = 4 - k;
            for (int dx = -s; dx <= s; dx++)
                for (int dz = -s; dz <= s; dz++)
                    if (Math.abs(dx) == s || Math.abs(dz) == s || k == 3) set(hx + dx, gy + 5 + k, hz + dz, roof);
        }
        // interior
        Material[] inside = INTERIOR.getOrDefault(npc, new Material[]{Material.CRAFTING_TABLE});
        int[][] spots = {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}};
        int placed = 0;
        for (int[] s : spots) {
            if (placed >= inside.length) break;
            int x = hx + s[0], z = hz + s[1];
            // no tapar la entrada
            if (Math.abs((x - doorX)) + Math.abs((z - doorZ)) <= 2) continue;
            set(x, gy + 1, z, inside[placed++]);
        }
        set(hx, gy + 4, hz, Material.LANTERN);
    }

    private void farm(int fx, int gy, int fz) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 2;
                if (edge) {
                    set(fx + dx, gy, fz + dz, Material.OAK_LOG);
                } else if (dz == 0) {
                    set(fx + dx, gy, fz + dz, Material.WATER);
                } else {
                    set(fx + dx, gy, fz + dz, Material.FARMLAND);
                    set(fx + dx, gy + 1, fz + dz, Material.WHEAT);
                }
            }
        }
    }
}
