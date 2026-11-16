package pe.kydium.eclipserpg.dungeon;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.data.PlayerData;
import pe.kydium.eclipserpg.util.Txt;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * La Aldea del Alba: se genera sola al iniciar el mundo en un lugar plano de un bioma
 * agradable, a partir del plano aldea.txt.gz. Coloca NPCs, aldeanos y animales,
 * y da a cada jugador una brújula que apunta a ella.
 */
public final class VillageBuilder implements Listener {

    private record Op(int x, int y, int z, BlockData data) {}

    private static final String[] NAMES = {"Rosa", "Tomás", "Lucía", "Mateo", "Carmen", "Andrés", "Elena", "Julián",
            "Marta", "Diego", "Sofía", "Ramón", "Valeria", "Hugo", "Benito", "Paula", "Óscar", "Nina", "Gael", "Abril",
            "Ernesto", "Lola", "Simón", "Clara"};
    private static final Set<String> FLUIDS = Set.of("water", "lava");

    private final EclipseRPG plugin;
    private final List<Op> ops = new ArrayList<>();
    private final List<JsonObject> markers = new ArrayList<>();
    private int radius = 44;
    private boolean busy;

    public VillageBuilder(EclipseRPG plugin) {
        this.plugin = plugin;
    }

    // ---------- Plano ----------

    private boolean loadBlueprint() {
        if (!ops.isEmpty()) return true;
        int bad = 0;
        try (InputStream in = plugin.getResource("aldea.txt.gz")) {
            if (in == null) return false;
            BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8));
            Map<String, BlockData> cache = new HashMap<>();
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) continue;
                if (line.startsWith("#")) {
                    JsonObject o = JsonParser.parseString(line.substring(1)).getAsJsonObject();
                    if ("radius".equals(o.get("t").getAsString())) radius = o.get("r").getAsInt();
                    else markers.add(o);
                    continue;
                }
                String[] p = line.split(" ", 4);
                String state = p[3].equals("air") ? "minecraft:air" : p[3];
                BlockData bd = cache.get(state);
                if (bd == null) {
                    try {
                        bd = Bukkit.createBlockData(state);
                    } catch (IllegalArgumentException e) {
                        try {
                            bd = Bukkit.createBlockData(state.contains("[") ? state.substring(0, state.indexOf('[')) : state);
                        } catch (IllegalArgumentException e2) {
                            bad++;
                            continue;
                        }
                    }
                    cache.put(state, bd);
                }
                ops.add(new Op(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), bd));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("No se pudo leer el plano de la aldea: " + e.getMessage());
            return false;
        }
        if (bad > 0) plugin.getLogger().warning("Plano de la aldea: " + bad + " bloques no reconocidos (se omiten)");
        return true;
    }

    // ---------- Buscar sitio ----------

    private boolean goodBiome(World w, int x, int y, int z) {
        String key = w.getBiome(x, y, z).getKey().getKey();
        return plugin.getConfig().getStringList("aldea.biomas").contains(key);
    }

    /** Busca un lugar plano en un bioma bueno, en espiral desde el spawn. */
    public Location findSite(World w) {
        Location spawn = w.getSpawnLocation();
        int maxR = plugin.getConfig().getInt("aldea.busqueda-max", 1500);
        Location best = null;
        int bestDiff = Integer.MAX_VALUE;
        for (int r = 0; r <= maxR; r += 96) {
            int steps = r == 0 ? 1 : Math.max(8, r / 48);
            for (int i = 0; i < steps; i++) {
                double a = 2 * Math.PI * i / steps;
                int x = spawn.getBlockX() + (int) (Math.cos(a) * r);
                int z = spawn.getBlockZ() + (int) (Math.sin(a) * r);
                Block top = w.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                if (top.isLiquid() || !goodBiome(w, x, top.getY(), z)) continue;
                int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
                boolean water = false;
                List<Integer> hs = new ArrayList<>();
                for (int dx = -40; dx <= 40; dx += 20) {
                    for (int dz = -40; dz <= 40; dz += 20) {
                        Block b = w.getHighestBlockAt(x + dx, z + dz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                        if (b.isLiquid() || b.getType() == Material.ICE) water = true;
                        min = Math.min(min, b.getY());
                        max = Math.max(max, b.getY());
                        hs.add(b.getY());
                    }
                }
                if (water) continue;
                int diff = max - min;
                hs.sort(Integer::compare);
                int gy = hs.get(hs.size() / 2);
                if (diff <= plugin.getConfig().getInt("aldea.desnivel-max", 7)) {
                    return new Location(w, x, gy, z);
                }
                if (diff < bestDiff) {
                    bestDiff = diff;
                    best = new Location(w, x, gy, z);
                }
            }
        }
        return best;
    }

    // ---------- Construcción ----------

    public boolean isGenerated() {
        return plugin.getConfig().getBoolean("aldea.generada", false);
    }

    /** Se llama al iniciar el servidor: genera la aldea si todavía no existe. */
    public void autoGenerate() {
        if (isGenerated() || !plugin.getConfig().getBoolean("aldea.auto", true)) return;
        World w = Bukkit.getWorlds().get(0);
        plugin.getLogger().info("Buscando un buen lugar para la Aldea del Alba…");
        Location site = findSite(w);
        if (site == null) {
            plugin.getLogger().warning("No se encontró lugar para la aldea. Usa /eclipse construiraldea donde quieras.");
            return;
        }
        build(site, null);
    }

    /** Construye la aldea con el centro de la plaza en "center" (y = nivel del suelo). */
    public void build(Location center, Player who) {
        if (busy) {
            if (who != null) Txt.send(who, "<red>Ya se está construyendo la aldea.");
            return;
        }
        if (!loadBlueprint()) {
            if (who != null) Txt.send(who, "<red>No se encontró el plano de la aldea.");
            return;
        }
        busy = true;
        World w = center.getWorld();
        int cx = center.getBlockX(), cz = center.getBlockZ(), gy = center.getBlockY();
        int R = radius + 2;
        List<int[]> prep = new ArrayList<>();
        plugin.getLogger().info("Construyendo la Aldea del Alba en " + cx + " " + gy + " " + cz);
        if (who != null) Txt.send(who, "<yellow>Construyendo la Aldea del Alba… dame unos segundos.");

        // fase 1: preparar terreno (rellenar debajo y despejar encima)
        for (int x = cx - R; x <= cx + R; x++)
            for (int z = cz - R; z <= cz + R; z++) prep.add(new int[]{x, z});

        BlockData dirt = Material.DIRT.createBlockData();
        BlockData air = Material.AIR.createBlockData();
        final int[] i = {0};
        final int[] j = {0};
        int perTick = plugin.getConfig().getInt("aldea.bloques-por-tick", 8000);
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int budget = perTick;
            while (i[0] < prep.size() && budget > 0) {
                int[] c = prep.get(i[0]++);
                for (int y = gy - 6; y < gy; y++) {
                    Block b = w.getBlockAt(c[0], y, c[1]);
                    if (b.isPassable() || b.isLiquid() || b.getType().name().endsWith("LEAVES")) b.setBlockData(dirt, false);
                }
                for (int y = gy + 1; y <= gy + 30; y++) {
                    Block b = w.getBlockAt(c[0], y, c[1]);
                    if (!b.getType().isAir()) b.setBlockData(air, false);
                }
                budget -= 40;
            }
            if (i[0] < prep.size()) return;
            // fase 2: el plano
            while (j[0] < ops.size() && budget > 0) {
                Op o = ops.get(j[0]++);
                Block b = w.getBlockAt(cx + o.x(), gy + o.y(), cz + o.z());
                boolean fluid = FLUIDS.contains(o.data().getMaterial().getKey().getKey());
                b.setBlockData(o.data(), fluid);
                budget--;
            }
            if (j[0] < ops.size()) return;
            task.cancel();
            finish(w, cx, gy, cz, who);
        }, 1L, 1L);
    }

    private void finish(World w, int cx, int gy, int cz, Player who) {
        Random rnd = new Random();
        Map<String, Location> npcSpots = new HashMap<>();
        Location campSpot = new Location(w, cx + 0.5, gy + 1, cz + 0.5);
        boolean invul = plugin.getConfig().getBoolean("aldea.aldeanos-invencibles", true);
        for (JsonObject m : markers) {
            String t = m.get("t").getAsString();
            Location at = new Location(w, cx + m.get("x").getAsInt() + 0.5, gy + m.get("y").getAsInt(), cz + m.get("z").getAsInt() + 0.5);
            switch (t) {
                case "center" -> campSpot = at;
                case "npc" -> {
                    at.setYaw(m.get("yaw").getAsFloat());
                    npcSpots.put(m.get("id").getAsString(), at);
                }
                case "villager" -> {
                    if (!(w.spawnEntity(at, EntityType.VILLAGER) instanceof Villager v)) continue;
                    v.customName(Txt.mm("<gray>" + NAMES[rnd.nextInt(NAMES.length)]));
                    v.setCustomNameVisible(false);
                    Villager.Profession prof = profession(m.has("prof") && !m.get("prof").isJsonNull() ? m.get("prof").getAsString() : null);
                    if (prof != null) v.setProfession(prof);
                    v.setVillagerLevel(1 + rnd.nextInt(3));
                    v.setPersistent(true);
                    v.setRemoveWhenFarAway(false);
                    v.setInvulnerable(invul);
                    if (rnd.nextDouble() < 0.2) v.setBaby();
                }
                case "animal" -> {
                    EntityType type = switch (m.get("type").getAsString()) {
                        case "cow" -> EntityType.COW;
                        case "sheep" -> EntityType.SHEEP;
                        case "pig" -> EntityType.PIG;
                        case "chicken" -> EntityType.CHICKEN;
                        case "cat" -> EntityType.CAT;
                        default -> null;
                    };
                    if (type == null) continue;
                    Entity e = w.spawnEntity(at, type);
                    if (e instanceof LivingEntity le) {
                        le.setRemoveWhenFarAway(false);
                        le.setPersistent(true);
                    }
                    if (e instanceof Cat cat) cat.setInvulnerable(invul);
                }
                default -> { }
            }
        }
        var cfg = plugin.getConfig();
        cfg.set("campamento.mundo", w.getName());
        cfg.set("campamento.x", campSpot.getX());
        cfg.set("campamento.y", campSpot.getY());
        cfg.set("campamento.z", campSpot.getZ());
        cfg.set("campamento.yaw", 180.0);
        cfg.set("campamento.pitch", 0.0);
        for (Map.Entry<String, Location> e : npcSpots.entrySet()) {
            String k = "aldea.puestos." + e.getKey();
            cfg.set(k + ".x", e.getValue().getX());
            cfg.set(k + ".y", e.getValue().getY());
            cfg.set(k + ".z", e.getValue().getZ());
            cfg.set(k + ".yaw", (double) e.getValue().getYaw());
        }
        cfg.set("aldea.mundo", w.getName());
        cfg.set("aldea.centro.x", cx);
        cfg.set("aldea.centro.y", gy);
        cfg.set("aldea.centro.z", cz);
        cfg.set("aldea.generada", true);
        plugin.saveConfig();
        busy = false;
        plugin.npcEntities().spawnAll(npcSpots);
        plugin.getLogger().info("¡Aldea del Alba lista en " + cx + " " + gy + " " + cz + "!");
        if (who != null) Txt.send(who, "<green>¡La Aldea del Alba está lista con sus NPCs y aldeanos!");
        for (Player p : Bukkit.getOnlinePlayers()) giveCompassIfNew(p);
    }

    private Villager.Profession profession(String id) {
        if (id == null) return null;
        return switch (id) {
            case "farmer" -> Villager.Profession.FARMER;
            case "librarian" -> Villager.Profession.LIBRARIAN;
            case "fisherman" -> Villager.Profession.FISHERMAN;
            case "shepherd" -> Villager.Profession.SHEPHERD;
            case "mason" -> Villager.Profession.MASON;
            case "cleric" -> Villager.Profession.CLERIC;
            case "butcher" -> Villager.Profession.BUTCHER;
            case "fletcher" -> Villager.Profession.FLETCHER;
            case "toolsmith" -> Villager.Profession.TOOLSMITH;
            case "leatherworker" -> Villager.Profession.LEATHERWORKER;
            case "armorer" -> Villager.Profession.ARMORER;
            case "weaponsmith" -> Villager.Profession.WEAPONSMITH;
            case "cartographer" -> Villager.Profession.CARTOGRAPHER;
            case "nitwit" -> Villager.Profession.NITWIT;
            default -> null;
        };
    }

    // ---------- Brújula ----------

    public Location center() {
        var cfg = plugin.getConfig();
        World w = Bukkit.getWorld(cfg.getString("aldea.mundo", "world"));
        if (w == null || !cfg.contains("aldea.centro.x")) return null;
        return new Location(w, cfg.getInt("aldea.centro.x") + 0.5, cfg.getInt("aldea.centro.y") + 1, cfg.getInt("aldea.centro.z") + 0.5);
    }

    public void giveCompass(Player p) {
        Location c = center();
        if (c == null) return;
        ItemStack it = new ItemStack(Material.COMPASS);
        if (it.getItemMeta() instanceof CompassMeta meta) {
            meta.setLodestone(c);
            meta.setLodestoneTracked(false);
            meta.displayName(Txt.item("<gold>Brújula de la Aldea del Alba"));
            meta.lore(Txt.lore(List.of("<gray>Apunta a la aldea donde empieza", "<gray>tu historia.",
                    "<gray>X " + c.getBlockX() + " · Z " + c.getBlockZ())));
            meta.setEnchantmentGlintOverride(true);
            it.setItemMeta(meta);
        }
        plugin.items().give(p, it);
    }

    private void giveCompassIfNew(Player p) {
        PlayerData d = plugin.data().get(p);
        if (d.flags.contains("brujula_aldea") || center() == null) return;
        d.flags.add("brujula_aldea");
        d.dirty = true;
        giveCompass(p);
        Location c = center();
        Txt.send(p, "<gold>Recibiste la Brújula de la Aldea del Alba. <gray>Síguela hasta <white>X " + c.getBlockX() + ", Z " + c.getBlockZ()
                + "<gray> y habla con la Abuela Tula.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) giveCompassIfNew(p);
        }, 40L);
    }
}
