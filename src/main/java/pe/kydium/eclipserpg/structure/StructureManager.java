package pe.kydium.eclipserpg.structure;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.data.PlayerData;
import pe.kydium.eclipserpg.dungeon.Connect;
import pe.kydium.eclipserpg.quests.Quest;
import pe.kydium.eclipserpg.util.AsyncChunks;
import pe.kydium.eclipserpg.util.Txt;
import pe.kydium.eclipserpg.world.WorldManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Estructuras propias: se construyen en el juego y se guardan con /estructura guardar (un "plano"),
 * y avanzado/estructuras.yml dice dónde aparecen (al azar, al aceptar una misión o a mano),
 * qué enemigos y jefe tienen y qué botín hay en sus cofres.
 */
public final class StructureManager implements Listener {

    public record Op(int x, int y, int z, BlockData data) {}

    public record Marker(String kind, String arg, int x, int y, int z) {}

    public static final class Plan {
        public String name;
        public final List<Op> ops = new ArrayList<>();
        public final List<Marker> markers = new ArrayList<>();
        public int minX, maxX, maxY, minZ, maxZ;
        public int bad;
    }

    public record Spawn(String mob, int amount) {}

    public static final class Def {
        public String id, name, plan;
        public String gen;                       // estructura generada (sale distinta cada vez)
        public String style = "auto";
        public String category = "estructura";
        public String appears = "manual";      // azar | mision | manual
        public double chance = 0.002;
        public int max = 4;
        public int distMin = 150, distMax = 450;
        public final List<String> biomes = new ArrayList<>();
        public int sink;                         // negativo = enterrada
        public int slope = 5;
        public boolean clear = true;
        public final List<Spawn> enemies = new ArrayList<>();
        public String boss;
        public int respawn;                      // minutos; 0 = solo la primera vez
        public final List<String> loot = new ArrayList<>();
        public int rolls = 2;
        public String title, subtitle;
        public long xp, money;
    }

    public static final class Inst {
        public String id, world;
        public int x, y, z;
        public int rx, rz, h;
        public final List<int[]> enemySpots = new ArrayList<>();
        public int[] bossSpot, entrance;
        long activatedAt;
        final Set<UUID> mobs = new HashSet<>();

        boolean contains(Location l, int pad) {
            return l.getX() >= x - rx - pad && l.getX() <= x + rx + 1 + pad && l.getZ() >= z - rz - pad && l.getZ() <= z + rz + 1 + pad
                    && l.getY() >= y - 4 && l.getY() <= y + h + 6;
        }
    }

    private final EclipseRPG plugin;
    private final Map<String, Def> defs = new LinkedHashMap<>();
    private final Map<String, Plan> plans = new HashMap<>();
    private final List<Inst> instances = new ArrayList<>();
    private final Set<String> building = new HashSet<>();
    private final Map<UUID, int[][]> selection = new HashMap<>();

    public StructureManager(EclipseRPG plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ archivos

    private File planFolder() {
        return new File(plugin.story().folder(), "estructuras");
    }

    private File dataFile() {
        return new File(plugin.getDataFolder(), "datos-estructuras.yml");
    }

    public static String slug(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", "");
    }

    public void load() {
        defs.clear();
        plans.clear();
        YamlConfiguration y = plugin.story().yaml("estructuras");
        ConfigurationSection root = y.getConfigurationSection("estructuras");
        if (root != null) for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            Def d = new Def();
            d.id = slug(id);
            d.name = s.getString("nombre", id);
            String planRaw = s.getString("plano", id);
            String genRaw = s.getString("generador");
            if (genRaw == null && planRaw != null && planRaw.startsWith("gen:")) genRaw = planRaw.substring(4);
            d.plan = slug(planRaw);
            if (genRaw != null && !genRaw.isBlank()) {
                d.gen = slug(genRaw);
                var ce = pe.kydium.eclipserpg.gen.Catalog.ALL.get(d.gen);
                if (ce == null) {
                    plugin.getLogger().warning("estructuras.yml: '" + id + "' usa el generador '" + d.gen + "', que no existe. Generadores: "
                            + pe.kydium.eclipserpg.gen.Catalog.ALL.keySet());
                    d.gen = null;
                } else d.category = ce.category();
            }
            d.style = s.getString("estilo", "auto");
            d.appears = s.getString("aparece", "manual").toLowerCase(Locale.ROOT);
            d.chance = s.getDouble("probabilidad", 0.002);
            d.max = Math.max(1, s.getInt("maximo", 4));
            d.distMin = Math.max(0, s.getInt("distancia-min", 150));
            d.distMax = Math.max(d.distMin + 10, s.getInt("distancia-max", 450));
            for (String b : s.getStringList("biomas")) d.biomes.add(b.trim().toLowerCase(Locale.ROOT));
            d.sink = s.getInt("hundir", 0);
            d.slope = Math.max(1, s.getInt("desnivel-max", 5));
            d.clear = s.getBoolean("despejar", true);
            for (Map<?, ?> m : s.getMapList("enemigos")) {
                Object mob = m.get("mob");
                if (mob == null || mob.toString().isBlank()) continue;
                int n = 1;
                try {
                    n = Math.max(1, (int) Double.parseDouble(String.valueOf(m.get("cantidad"))));
                } catch (NumberFormatException ignored) {
                    // 1
                }
                d.enemies.add(new Spawn(slug(mob.toString()), n));
            }
            String boss = s.getString("jefe");
            d.boss = boss == null || boss.isBlank() ? null : slug(boss);
            d.respawn = Math.max(0, s.getInt("reaparicion", 0));
            d.loot.addAll(s.getStringList("botin"));
            d.rolls = Math.max(1, s.getInt("botin-tiradas", 2));
            d.title = s.getString("anuncio");
            d.subtitle = s.getString("subtitulo", "");
            d.xp = s.getLong("premio-xp", 0);
            d.money = s.getLong("premio-dinero", 0);
            defs.put(d.id, d);
            if (d.gen == null && plan(d.plan) == null) plugin.getLogger().warning("estructuras.yml: '" + id + "' usa el plano '" + d.plan
                    + "', que no existe. Constrúyelo en el juego y guárdalo con /estructura guardar " + d.plan);
        }
        loadInstances();
        plugin.getLogger().info("Estructuras: " + defs.size() + " definidas, " + planNames().size() + " planos, " + instances.size() + " en el mundo");
    }

    private void loadInstances() {
        instances.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile());
        for (Map<?, ?> m : y.getMapList("estructuras")) {
            try {
                Inst i = new Inst();
                i.id = String.valueOf(m.get("id"));
                i.world = String.valueOf(m.get("mundo"));
                i.x = ((Number) m.get("x")).intValue();
                i.y = ((Number) m.get("y")).intValue();
                i.z = ((Number) m.get("z")).intValue();
                i.rx = ((Number) m.get("rx")).intValue();
                i.rz = ((Number) m.get("rz")).intValue();
                i.h = ((Number) m.get("h")).intValue();
                i.activatedAt = m.get("activada") instanceof Number n ? n.longValue() : 0;
                if (m.get("enemigos") instanceof List<?> l) for (Object o : l) i.enemySpots.add(ints(o));
                if (m.get("jefe") != null) i.bossSpot = ints(m.get("jefe"));
                if (m.get("entrada") != null) i.entrance = ints(m.get("entrada"));
                instances.add(i);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("datos-estructuras.yml: entrada dañada, se ignora");
            }
        }
    }

    private static int[] ints(Object o) {
        List<?> l = (List<?>) o;
        return new int[]{((Number) l.get(0)).intValue(), ((Number) l.get(1)).intValue(), ((Number) l.get(2)).intValue()};
    }

    private static List<Integer> list(int[] a) {
        return List.of(a[0], a[1], a[2]);
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Inst i : instances) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i.id);
            m.put("mundo", i.world);
            m.put("x", i.x);
            m.put("y", i.y);
            m.put("z", i.z);
            m.put("rx", i.rx);
            m.put("rz", i.rz);
            m.put("h", i.h);
            m.put("activada", i.activatedAt);
            List<List<Integer>> es = new ArrayList<>();
            for (int[] e : i.enemySpots) es.add(list(e));
            m.put("enemigos", es);
            if (i.bossSpot != null) m.put("jefe", list(i.bossSpot));
            if (i.entrance != null) m.put("entrada", list(i.entrance));
            out.add(m);
        }
        y.set("estructuras", out);
        try {
            y.save(dataFile());
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar datos-estructuras.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ planos

    /** Nombres de todos los planos: los de la historia y los que trae el plugin. */
    public Set<String> planNames() {
        Set<String> out = new TreeSet<>();
        File[] fs = planFolder().listFiles((d, n) -> n.endsWith(".txt.gz"));
        if (fs != null) for (File f : fs) out.add(f.getName().substring(0, f.getName().length() - 7));
        try (InputStream in = plugin.getResource("estructuras/lista.txt")) {
            if (in != null) for (String l : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) if (!l.isBlank()) out.add(l.trim());
        } catch (IOException ignored) {
            // sin planos incluidos
        }
        return out;
    }

    public Plan plan(String name) {
        if (name == null || name.isEmpty()) return null;
        Plan cached = plans.get(name);
        if (cached != null) return cached;
        File f = new File(planFolder(), name + ".txt.gz");
        try (InputStream raw = f.exists() ? Files.newInputStream(f.toPath()) : plugin.getResource("estructuras/" + name + ".txt.gz")) {
            if (raw == null) return null;
            Plan p = new Plan();
            p.name = name;
            Map<String, BlockData> cache = new HashMap<>();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new GZIPInputStream(raw), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] t = line.split(" ", line.startsWith("@") ? 6 : 4);
                    try {
                        if (line.startsWith("@")) {
                            // @tipo x y z [arg]
                            p.markers.add(new Marker(t[0].substring(1), t.length > 4 ? t[4].trim() : "",
                                    Integer.parseInt(t[1]), Integer.parseInt(t[2]), Integer.parseInt(t[3])));
                            continue;
                        }
                        int x = Integer.parseInt(t[0]), yy = Integer.parseInt(t[1]), z = Integer.parseInt(t[2]);
                        BlockData bd = cache.computeIfAbsent(t[3], Bukkit::createBlockData);
                        p.ops.add(new Op(x, yy, z, bd));
                        p.minX = Math.min(p.minX, x);
                        p.maxX = Math.max(p.maxX, x);
                        p.minZ = Math.min(p.minZ, z);
                        p.maxZ = Math.max(p.maxZ, z);
                        p.maxY = Math.max(p.maxY, yy);
                    } catch (RuntimeException e) {
                        p.bad++;
                    }
                }
            }
            if (p.bad > 0) plugin.getLogger().warning("Plano " + name + ": " + p.bad + " bloques no reconocidos (se omiten)");
            plans.put(name, p);
            return p;
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo leer el plano " + name + ": " + e.getMessage());
            return null;
        }
    }

    private static String signLine(org.bukkit.block.Sign s, int i) {
        return PlainTextComponentSerializer.plainText().serialize(s.getSide(org.bukkit.block.sign.Side.FRONT).line(i)).trim();
    }

    /**
     * Guarda como plano todo lo que hay entre dos esquinas. Los carteles que digan [jefe], [enemigo],
     * [entrada] o [npc] (con el id en la segunda línea) se convierten en marcas y no se guardan como bloque.
     * Devuelve un texto para el jugador.
     */
    public String savePlan(String rawName, World w, int[] a, int[] b) {
        String name = slug(rawName);
        if (name.isEmpty()) return "<red>Ponle un nombre corto, sin espacios: /estructura guardar torre_mago";
        int x1 = Math.min(a[0], b[0]), x2 = Math.max(a[0], b[0]), y1 = Math.min(a[1], b[1]), y2 = Math.max(a[1], b[1]),
                z1 = Math.min(a[2], b[2]), z2 = Math.max(a[2], b[2]);
        long vol = (long) (x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1);
        if (vol > 600_000) return "<red>Es demasiado grande (" + vol + " bloques). El máximo es 600 000: por ejemplo 80×90×80.";
        int ox = (x1 + x2) / 2, oz = (z1 + z2) / 2;
        File f = new File(planFolder(), name + ".txt.gz");
        int blocks = 0, marks = 0;
        try {
            Files.createDirectories(planFolder().toPath());
            try (Writer out = new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(f.toPath())), StandardCharsets.UTF_8)) {
                out.write("# plano de Aventura Maker · " + (x2 - x1 + 1) + "x" + (y2 - y1 + 1) + "x" + (z2 - z1 + 1) + "\n");
                for (int y = y1; y <= y2; y++) for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
                    Block bl = w.getBlockAt(x, y, z);
                    Material m = bl.getType();
                    if (m.isAir() || m == Material.STRUCTURE_VOID) continue;
                    if (bl.getState() instanceof org.bukkit.block.Sign sign) {
                        String tag = signLine(sign, 0).toLowerCase(Locale.ROOT);
                        if (tag.startsWith("[") && tag.endsWith("]")) {
                            String kind = tag.substring(1, tag.length() - 1).trim();
                            if (Set.of("jefe", "enemigo", "entrada", "npc").contains(kind)) {
                                out.write("@" + kind + " " + (x - ox) + " " + (y - y1) + " " + (z - oz) + " " + slug(signLine(sign, 1)) + "\n");
                                marks++;
                                continue;
                            }
                        }
                    }
                    out.write((x - ox) + " " + (y - y1) + " " + (z - oz) + " " + bl.getBlockData().getAsString() + "\n");
                    blocks++;
                }
            }
        } catch (IOException e) {
            return "<red>No se pudo guardar el plano: " + e.getMessage();
        }
        if (blocks == 0) {
            if (!f.delete()) plugin.getLogger().warning("No se pudo borrar el plano vacío " + f);
            return "<red>No hay bloques entre las dos esquinas.";
        }
        plans.remove(name);
        boolean created = false;
        if (!defs.containsKey(name)) {
            created = true;
            File yf = new File(plugin.story().folder(), "avanzado/estructuras.yml");
            YamlConfiguration y = yf.exists() ? YamlConfiguration.loadConfiguration(yf) : plugin.story().yaml("estructuras");
            y.set("estructuras." + name + ".nombre", "<yellow>" + Txt.pretty(name.replace('_', ' ')));
            y.set("estructuras." + name + ".plano", name);
            y.set("estructuras." + name + ".aparece", "manual");
            try {
                Files.createDirectories(yf.getParentFile().toPath());
                y.save(yf);
            } catch (IOException e) {
                plugin.getLogger().warning("No se pudo actualizar estructuras.yml: " + e.getMessage());
            }
            load();
        }
        return "<green>Plano «" + name + "» guardado: <white>" + blocks + " bloques" + (marks > 0 ? ", " + marks + " marcas" : "")
                + "<green>." + (created ? " <gray>Ya aparece en el editor (Estructuras) para decir dónde sale, enemigos y botín." : "")
                + " <gray>Pruébalo con /estructura colocar " + name;
    }

    // ------------------------------------------------------------------ consultas

    public Set<String> ids() {
        return defs.keySet();
    }

    public Def def(String id) {
        return defs.get(slug(id));
    }

    public List<Inst> instances() {
        return instances;
    }

    public int count(String id) {
        int n = 0;
        for (Inst i : instances) if (i.id.equals(id)) n++;
        return n;
    }

    public Inst nearest(String id, Location from) {
        Inst best = null;
        double bd = Double.MAX_VALUE;
        for (Inst i : instances) {
            if (id != null && !i.id.equals(id)) continue;
            if (from.getWorld() == null || !from.getWorld().getName().equals(i.world)) continue;
            double d = Math.pow(i.x - from.getX(), 2) + Math.pow(i.z - from.getZ(), 2);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    public String name(String id) {
        Def d = defs.get(id);
        return d == null ? id : d.name;
    }

    public void setSelection(Player p, int which, Block b) {
        int[][] s = selection.computeIfAbsent(p.getUniqueId(), k -> new int[2][]);
        s[which] = new int[]{b.getX(), b.getY(), b.getZ()};
    }

    public int[][] selection(Player p) {
        return selection.get(p.getUniqueId());
    }

    // ------------------------------------------------------------------ colocar

    private static final Set<Material> BAD_GROUND = Set.of(Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE, Material.POWDER_SNOW);

    /** Altura del suelo (sin hojas ni árboles) o Integer.MIN_VALUE si es agua o hielo. */
    private int ground(World w, int x, int z) {
        Block top = w.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        // bajar por troncos
        int guard = 0;
        while (guard++ < 30 && (top.getType().name().endsWith("_LOG") || top.getType().name().endsWith("_STEM") || !top.getType().isSolid())
                && top.getY() > w.getMinHeight() + 2 && !top.isLiquid()) top = top.getRelative(0, -1, 0);
        if (top.isLiquid() || BAD_GROUND.contains(top.getType())) return Integer.MIN_VALUE;
        return top.getY();
    }

    /** ¿Sirve este lugar? Devuelve la altura del suelo en el centro o Integer.MIN_VALUE. */
    private int siteOk(Def d, Plan p, World w, int x, int z) {
        if ("aerea".equals(d.category)) {
            // en el cielo: solo que no quede encima de la aldea ni de otra estructura
            int rr = Math.max(-p.minX, p.maxX);
            Location v = plugin.village().center();
            if (v != null && v.getWorld() == w && Math.hypot(v.getX() - x, v.getZ() - z) < 120 + rr) return Integer.MIN_VALUE;
            for (Inst o : instances) if (o.world.equals(w.getName()) && Math.abs(o.x - x) < o.rx + rr + 30 && Math.abs(o.z - z) < o.rz + rr + 30) return Integer.MIN_VALUE;
            return ground(w, x, z) == Integer.MIN_VALUE ? w.getHighestBlockYAt(x, z) : ground(w, x, z);
        }
        int rx = Math.max(-p.minX, p.maxX), rz = Math.max(-p.minZ, p.maxZ);
        int[][] pts = {{x, z}, {x - rx, z - rz}, {x + rx, z - rz}, {x - rx, z + rz}, {x + rx, z + rz}, {x - rx, z}, {x + rx, z}, {x, z - rz}, {x, z + rz}};
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, center = 0;
        for (int i = 0; i < pts.length; i++) {
            int g = ground(w, pts[i][0], pts[i][1]);
            if (g == Integer.MIN_VALUE) return Integer.MIN_VALUE;
            if (i == 0) center = g;
            min = Math.min(min, g);
            max = Math.max(max, g);
        }
        if (max - min > (d.gen != null ? Math.max(d.slope, 14) : d.slope)) return Integer.MIN_VALUE;
        if (!d.biomes.isEmpty()) {
            String biome = w.getBiome(x, center, z).getKey().getKey();
            if (!WorldManager.biomeIn(d.biomes, biome)) return Integer.MIN_VALUE;
        }
        Location v = plugin.village().center();
        if (v != null && v.getWorld() == w && Math.hypot(v.getX() - x, v.getZ() - z) < 90 + Math.max(rx, rz)) return Integer.MIN_VALUE;
        for (Inst o : instances) {
            if (o.world.equals(w.getName()) && Math.abs(o.x - x) < o.rx + rx + 24 && Math.abs(o.z - z) < o.rz + rz + 24) return Integer.MIN_VALUE;
        }
        return center;
    }

    /** El plano, o un "contorno" del tamaño del generador si es generada. null si no hay ninguno. */
    public Plan shape(Def d) {
        if (d.gen != null) {
            var ce = pe.kydium.eclipserpg.gen.Catalog.ALL.get(d.gen);
            if (ce == null) return null;
            Plan p = new Plan();
            p.name = "gen:" + d.gen;
            p.minX = -ce.radius();
            p.maxX = ce.radius();
            p.minZ = -ce.radius();
            p.maxZ = ce.radius();
            p.maxY = 24;
            return p;
        }
        return plan(d.plan);
    }

    private static final List<String> LOOT_STRUCTURE = List.of("BREAD 4 0.7", "COOKED_BEEF 3 0.4", "IRON_INGOT 3 0.5", "GOLD_INGOT 2 0.3",
            "EMERALD 3 0.3", "ARROW 12 0.4", "EXPERIENCE_BOTTLE 3 0.3", "DIAMOND 1 0.06", "pocion:healing 1 0.25", "libro:mending:1 1 0.03");
    /** Tesoros escondidos: lo más valioso del juego. */
    public static final List<String> LOOT_TREASURE = List.of("DIAMOND 8 0.9", "NETHERITE_SCRAP 3 0.4", "NETHERITE_INGOT 1 0.1",
            "ENCHANTED_GOLDEN_APPLE 2 0.3", "TOTEM_OF_UNDYING 1 0.25", "ELYTRA 1 0.06", "EMERALD_BLOCK 3 0.45", "GOLD_BLOCK 3 0.5",
            "DIAMOND_BLOCK 1 0.2", "HEART_OF_THE_SEA 1 0.1", "TRIDENT 1 0.06", "libro:mending:1 1 0.35", "libro:sharpness:5 1 0.3",
            "libro:protection:4 1 0.3", "libro:efficiency:5 1 0.25", "libro:fortune:3 1 0.25", "libro:looting:3 1 0.2", "libro:unbreaking:3 1 0.3",
            "custom:fragmento_mejora 3 0.5", "EXPERIENCE_BOTTLE 16 0.7", "SHULKER_SHELL 2 0.12", "NETHER_STAR 1 0.03");
    /** Lo de los cofres comunes de las casas de una aldea. */
    public static final List<String> LOOT_VILLAGE = List.of("BREAD 6 0.8", "APPLE 4 0.5", "COAL 8 0.5", "TORCH 8 0.4", "WHEAT 10 0.4",
            "IRON_INGOT 2 0.25", "EMERALD 2 0.2", "STRING 4 0.3", "LEATHER 3 0.3", "BOOK 2 0.2", "COOKED_PORKCHOP 3 0.3");
    private static final List<String> LOOT_DUNGEON = List.of("GOLD_INGOT 4 0.6", "DIAMOND 2 0.25", "EMERALD 5 0.5", "IRON_INGOT 6 0.6",
            "GOLDEN_APPLE 1 0.25", "ENCHANTED_GOLDEN_APPLE 1 0.02", "EXPERIENCE_BOTTLE 6 0.5", "pocion:strength 1 0.3", "pocion:healing 2 0.4",
            "libro:sharpness:3 1 0.15", "libro:protection:3 1 0.15", "NAME_TAG 1 0.1", "DIAMOND_SWORD 1 0.06");

    /** Construye una estructura generada centrada en (x, z): cada vez sale distinta y se adapta al terreno. */
    public void placeGenerated(Def d, World w, int x, int z, Player who, Consumer<Inst> done) {
        var ce = pe.kydium.eclipserpg.gen.Catalog.ALL.get(d.gen);
        if (ce == null) {
            if (done != null) done.accept(null);
            return;
        }
        int R = ce.radius() + 10;
        Set<Long> area = AsyncChunks.area(x - R, z - R, x + R, z + R);
        AsyncChunks.load(w, area).whenComplete((v, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
            for (long k : area) w.addPluginChunkTicket((int) (k >> 32), (int) k, plugin);
            WorldTerrain t = new WorldTerrain(w);
            String st = d.style == null || d.style.equalsIgnoreCase("auto") ? pe.kydium.eclipserpg.gen.Style.forBiome(t.biome(x, z)) : d.style;
            java.util.Random rnd = new java.util.Random(w.getSeed() ^ ((long) x * 341873128712L) ^ ((long) z * 132897987541L) ^ d.id.hashCode());
            pe.kydium.eclipserpg.gen.Build b;
            try {
                b = pe.kydium.eclipserpg.gen.Catalog.generate(d.gen, t, rnd, pe.kydium.eclipserpg.gen.Style.get(st), x, z);
            } catch (RuntimeException e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "No se pudo generar " + d.gen, e);
                for (long k : area) w.removePluginChunkTicket((int) (k >> 32), (int) k, plugin);
                if (done != null) done.accept(null);
                return;
            }
            BuildPlacer.place(plugin, w, b, Math.max(500, plugin.getConfig().getInt("estructuras.bloques-por-tick", 6000)), null, containers -> {
                boolean base = "base".equals(d.category);
                boolean rich = "mazmorra".equals(d.category) || "aerea".equals(d.category);
                List<String> table = !d.loot.isEmpty() ? d.loot : base ? List.of() : "aldea".equals(d.category) ? LOOT_VILLAGE : rich ? LOOT_DUNGEON : LOOT_STRUCTURE;
                // primero los tesoros escondidos
                for (var mk : b.markers) {
                    if (mk.type().equals("tesoro") && w.getBlockAt(mk.x(), mk.y(), mk.z()).getState() instanceof Container cont)
                        fillTable(cont.getInventory(), LOOT_TREASURE, 3, 4);
                    if (mk.type().equals("spawner") && w.getBlockAt(mk.x(), mk.y(), mk.z()).getState() instanceof org.bukkit.block.CreatureSpawner sp) {
                        org.bukkit.entity.EntityType et = pe.kydium.eclipserpg.util.Reg.entity(mk.arg());
                        if (et != null && et.isAlive()) {
                            sp.setSpawnedType(et);
                            sp.setRequiredPlayerRange(20);
                            sp.update(true, false);
                        }
                    }
                }
                for (int[] c : containers) {
                    if (table.isEmpty()) break;
                    if (w.getBlockAt(c[0], c[1], c[2]).getState() instanceof Container cont && cont.getInventory().isEmpty())
                        fillTable(cont.getInventory(), table, Math.max(2, d.rolls), "aldea".equals(d.category) ? 1 : 2);
                }
                Inst inst = new Inst();
                inst.id = d.id;
                inst.world = w.getName();
                inst.x = x;
                inst.z = z;
                inst.y = b.minY == Integer.MAX_VALUE ? t.ground(x, z) : b.minY;
                inst.rx = Math.max(Math.abs(b.minX - x), Math.abs(b.maxX - x));
                inst.rz = Math.max(Math.abs(b.minZ - z), Math.abs(b.maxZ - z));
                inst.h = Math.max(6, b.maxY - inst.y);
                for (var mk : b.markers) {
                    int[] at = {mk.x(), mk.y(), mk.z()};
                    switch (mk.type()) {
                        case "enemigo" -> inst.enemySpots.add(at);
                        case "jefe" -> inst.bossSpot = at;
                        case "entrada" -> inst.entrance = at;
                        case "villager" -> {
                            if ("aldea".equals(d.category))
                                plugin.npcEntities().spawnFiller(pe.kydium.eclipserpg.npc.NpcEntities.roleFor(mk.arg() == null || mk.arg().isEmpty() ? null : mk.arg()),
                                        new Location(w, at[0] + 0.5, at[1], at[2] + 0.5));
                        }
                        case "animal" -> {
                            org.bukkit.entity.EntityType et = pe.kydium.eclipserpg.util.Reg.entity(mk.arg());
                            if (et != null) {
                                var ent = w.spawnEntity(new Location(w, at[0] + 0.5, at[1], at[2] + 0.5), et);
                                ent.setPersistent(true);
                            }
                        }
                        default -> { }
                    }
                }
                instances.add(inst);
                save();
                if (!Boolean.getBoolean("eclipse.prueba")) Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    for (long k : area) w.removePluginChunkTicket((int) (k >> 32), (int) k, plugin);
                }, 100L);
                plugin.getLogger().info("Estructura " + d.id + " (" + d.gen + ", " + st + ") construida en " + x + " " + inst.y + " " + z + ": " + b.blocks.size() + " bloques");
                if (done != null) done.accept(inst);
            });
        }));
    }

    /** Una definición de paso para construir algo del catálogo con /estructura generar. */
    public Def catalogDef(String gen, String style) {
        var ce = pe.kydium.eclipserpg.gen.Catalog.ALL.get(slug(gen));
        if (ce == null) return null;
        Def d = defs.values().stream().filter(x -> ce.id().equals(x.gen)).findFirst().orElse(null);
        if (d != null && style == null) return d;
        Def n = new Def();
        n.id = d != null ? d.id : ce.id();
        n.name = d != null ? d.name : "<yellow>" + ce.name();
        n.gen = ce.id();
        n.category = ce.category();
        n.style = style == null ? "auto" : style;
        if (d != null) {
            n.enemies.addAll(d.enemies);
            n.boss = d.boss;
            n.loot.addAll(d.loot);
            n.title = d.title;
            n.subtitle = d.subtitle;
            n.respawn = d.respawn;
        }
        return n;
    }

    private Set<Long> chunksFor(Plan p, int x, int z) {
        return AsyncChunks.area(x + p.minX - 2, z + p.minZ - 2, x + p.maxX + 2, z + p.maxZ + 2);
    }

    /** Coloca la estructura con su suelo en la altura y (el piso del plano reemplaza al suelo). */
    public void placeAt(Def d, World w, int x, int y, int z, Player who, Consumer<Inst> done) {
        if (d.gen != null) {
            placeGenerated(d, w, x, z, who, done);
            return;
        }
        Plan p = plan(d.plan);
        if (p == null || p.ops.isEmpty()) {
            if (who != null) Txt.send(who, "<red>No existe el plano «" + d.plan + "». Constrúyelo y guárdalo con /estructura guardar " + d.plan);
            if (done != null) done.accept(null);
            return;
        }
        Set<Long> area = chunksFor(p, x, z);
        AsyncChunks.load(w, area).whenComplete((v, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
            for (long k : area) w.addPluginChunkTicket((int) (k >> 32), (int) k, plugin);
            int by = y + d.sink;
            List<int[]> joins = new ArrayList<>();
            List<int[]> containers = new ArrayList<>();
            BlockData air = Material.AIR.createBlockData();
            int perTick = Math.max(500, plugin.getConfig().getInt("estructuras.bloques-por-tick", 6000));
            final int[] i = {0};
            final int[] c = {0};
            int cols = (p.maxX - p.minX + 1) * (p.maxZ - p.minZ + 1);
            int wx = p.maxX - p.minX + 1;
            Bukkit.getScheduler().runTaskTimer(plugin, task -> {
                int budget = perTick;
                // 1) despejar encima (árboles, colinas)
                while (d.clear && d.sink >= 0 && c[0] < cols && budget > 0) {
                    int cx = x + p.minX + c[0] % wx, cz = z + p.minZ + c[0] / wx;
                    c[0]++;
                    for (int yy = by + 1; yy <= by + p.maxY + 3; yy++) {
                        Block b = w.getBlockAt(cx, yy, cz);
                        if (!b.getType().isAir()) b.setBlockData(air, false);
                    }
                    budget -= p.maxY + 3;
                }
                if (d.clear && d.sink >= 0 && c[0] < cols) return;
                // 2) el plano
                while (i[0] < p.ops.size() && budget > 0) {
                    Op o = p.ops.get(i[0]++);
                    Block b = w.getBlockAt(x + o.x(), by + o.y(), z + o.z());
                    b.setBlockData(o.data(), false);
                    if (Connect.needs(o.data())) joins.add(new int[]{b.getX(), b.getY(), b.getZ()});
                    Material m = o.data().getMaterial();
                    if (m == Material.CHEST || m == Material.BARREL || m == Material.TRAPPED_CHEST) containers.add(new int[]{b.getX(), b.getY(), b.getZ()});
                    if (o.y() == 0 && o.data().getMaterial().isOccluding()) {
                        // cimientos: que no quede flotando en una ladera
                        for (int dy = 1; dy <= 8; dy++) {
                            Block under = w.getBlockAt(b.getX(), by - dy, b.getZ());
                            if (under.getType().isSolid()) break;
                            under.setBlockData(o.data(), false);
                        }
                    }
                    budget--;
                }
                if (i[0] < p.ops.size()) return;
                task.cancel();
                Connect.fix(w, joins);
                for (int[] ct : containers) {
                    if (w.getBlockAt(ct[0], ct[1], ct[2]).getState() instanceof Container cont)
                        fillTable(cont.getInventory(), d.loot.isEmpty() ? LOOT_STRUCTURE : d.loot, d.rolls, 2);
                }
                Inst inst = new Inst();
                inst.id = d.id;
                inst.world = w.getName();
                inst.x = x;
                inst.y = by;
                inst.z = z;
                inst.rx = Math.max(-p.minX, p.maxX);
                inst.rz = Math.max(-p.minZ, p.maxZ);
                inst.h = p.maxY;
                for (Marker mk : p.markers) {
                    int[] at = {x + mk.x(), by + mk.y(), z + mk.z()};
                    switch (mk.kind()) {
                        case "enemigo" -> inst.enemySpots.add(at);
                        case "jefe" -> inst.bossSpot = at;
                        case "entrada" -> inst.entrance = at;
                        case "npc" -> {
                            if (plugin.npcs().def(mk.arg()) != null)
                                plugin.npcEntities().setCustomSpot(mk.arg(), new Location(w, at[0] + 0.5, at[1], at[2] + 0.5));
                        }
                        default -> { }
                    }
                }
                instances.add(inst);
                save();
                if (!Boolean.getBoolean("eclipse.prueba")) Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    for (long k : area) w.removePluginChunkTicket((int) (k >> 32), (int) k, plugin);
                }, 100L);
                plugin.getLogger().info("Estructura " + d.id + " construida en " + x + " " + by + " " + z);
                if (done != null) done.accept(inst);
            }, 1L, 1L);
        }));
    }

    /** Llena un cofre con una tabla; si por mala suerte queda vacío, le pone al menos "min" objetos. */
    public void fillTable(Inventory inv, List<String> table, int rolls, int min) {
        Def tmp = new Def();
        tmp.loot.addAll(table);
        tmp.rolls = Math.max(1, rolls);
        fillLoot(inv, tmp);
        int have = 0;
        for (ItemStack it : inv.getContents()) if (it != null) have++;
        java.util.Random r = ThreadLocalRandom.current();
        for (int guard = 0; have < min && guard < 40 && !table.isEmpty(); guard++) {
            String[] parts = table.get(r.nextInt(table.size())).trim().split("\\s+");
            String spec = parts[0].contains(":") ? parts[0] : "material:" + parts[0];
            ItemStack it = plugin.shops().buildSpec(spec, 1);
            if (it == null || it.getType() == Material.ENCHANTED_BOOK && !spec.startsWith("libro")) continue;
            int amount = 1;
            try {
                amount = parts.length > 1 ? Math.max(1, Integer.parseInt(parts[1])) : 1;
            } catch (NumberFormatException ignored) {
                // 1
            }
            it.setAmount(Math.max(1, Math.min(it.getMaxStackSize(), 1 + r.nextInt(amount))));
            int slot = r.nextInt(inv.getSize());
            if (inv.getItem(slot) == null) inv.setItem(slot, it);
            else inv.addItem(it);
            have++;
        }
    }

    private void fillLoot(Inventory inv, Def d) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int roll = 0; roll < d.rolls; roll++) {
            for (String line : d.loot) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 1 || parts[0].isEmpty()) continue;
                double chance = 1;
                int amount = 1;
                try {
                    if (parts.length > 1) amount = Integer.parseInt(parts[1]);
                    if (parts.length > 2) chance = Double.parseDouble(parts[2]);
                } catch (NumberFormatException e) {
                    continue;
                }
                if (r.nextDouble() >= chance) continue;
                String spec = parts[0].contains(":") ? parts[0] : "material:" + parts[0];
                ItemStack it = plugin.shops().buildSpec(spec, 1);
                if (it == null) continue;
                it.setAmount(Math.max(1, Math.min(it.getMaxStackSize(), 1 + r.nextInt(Math.max(1, amount)))));
                int slot = r.nextInt(inv.getSize());
                if (inv.getItem(slot) == null) inv.setItem(slot, it);
                else inv.addItem(it);
            }
        }
    }

    /** Busca un buen lugar a cierta distancia de la aldea (sin congelar el servidor) y construye. */
    private void search(Def d, Plan p, World w, Location from, Random r, int tries, Player who, Consumer<Inst> done) {
        if (tries >= 60) {
            building.remove(d.id);
            if (who != null) Txt.send(who, "<red>No encontré un buen lugar para " + d.name + "<red>. Puedes ponerla a mano con /estructura colocar " + d.id);
            if (done != null) done.accept(null);
            return;
        }
        double ang = r.nextDouble() * Math.PI * 2, dist = d.distMin + r.nextDouble() * (d.distMax - d.distMin);
        int x = from.getBlockX() + (int) (Math.cos(ang) * dist), z = from.getBlockZ() + (int) (Math.sin(ang) * dist);
        AsyncChunks.load(w, chunksFor(p, x, z)).whenComplete((v, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
            int g = ex == null ? siteOk(d, p, w, x, z) : Integer.MIN_VALUE;
            if (g == Integer.MIN_VALUE) {
                search(d, p, w, from, r, tries + 1, who, done);
                return;
            }
            placeAt(d, w, x, g, z, who, inst -> {
                building.remove(d.id);
                if (done != null) done.accept(inst);
            });
        }));
    }

    /** Para misiones: si la estructura no existe todavía la construye; al terminar le da la brújula al jugador. */
    public boolean ensure(String id, Player p) {
        Def d = def(id);
        if (d == null) return false;
        Inst have = nearest(d.id, p.getLocation());
        if (have != null) {
            giveCompass(p, have);
            return true;
        }
        Plan plan = shape(d);
        if (plan == null) {
            Txt.send(p, "<red>Falta el plano «" + d.plan + "» de " + d.name);
            return true;
        }
        if (!building.add(d.id)) {
            Txt.send(p, "<gray>" + d.name + " <gray>se está formando… espera unos segundos.");
            return true;
        }
        Txt.send(p, "<gray>Buscando dónde está " + d.name + "<gray>…");
        Location from = plugin.items().campLocation();
        UUID who = p.getUniqueId();
        search(d, plan, from.getWorld(), from, new Random(from.getWorld().getSeed() ^ (d.id.hashCode() * 7919L)), 0, p, inst -> {
            Player pl = Bukkit.getPlayer(who);
            if (inst != null && pl != null) giveCompass(pl, inst);
        });
        return true;
    }

    /** Construye sin jugador (prueba automática). */
    public void buildForTest(String id, Consumer<Inst> done) {
        Def d = def(id);
        Plan plan = d == null ? null : shape(d);
        if (plan == null) {
            done.accept(null);
            return;
        }
        Location from = plugin.items().campLocation();
        search(d, plan, from.getWorld(), from, new Random(42), 0, null, done);
    }

    public void giveCompass(Player p, Inst i) {
        World w = Bukkit.getWorld(i.world);
        if (w == null) return;
        int[] e = i.entrance != null ? i.entrance : new int[]{i.x, i.y + 1, i.z};
        ItemStack it = new ItemStack(Material.COMPASS);
        if (it.getItemMeta() instanceof CompassMeta meta) {
            meta.setLodestone(new Location(w, e[0], e[1], e[2]));
            meta.setLodestoneTracked(false);
            meta.displayName(Txt.item("<aqua>Brújula: <reset>" + name(i.id)));
            meta.lore(Txt.lore(List.of("<gray>X " + e[0] + " · Y " + e[1] + " · Z " + e[2])));
            it.setItemMeta(meta);
        }
        plugin.items().give(p, it);
        Txt.send(p, name(i.id) + " <gray>está en <white>X " + e[0] + ", Z " + e[2] + "<gray>. Sigue la brújula.");
    }

    // ------------------------------------------------------------------ aparición al azar

    @EventHandler
    public void onChunk(ChunkLoadEvent e) {
        if (!e.isNewChunk() || defs.isEmpty()) return;
        World w = e.getWorld();
        if (w.getEnvironment() != World.Environment.NORMAL) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Def d : defs.values()) {
            if (!d.appears.equals("azar") || r.nextDouble() >= d.chance) continue;
            if (count(d.id) >= d.max || building.contains(d.id)) continue;
            Plan p = shape(d);
            if (p == null) continue;
            int x = (e.getChunk().getX() << 4) + 8, z = (e.getChunk().getZ() << 4) + 8;
            Location spawn = w.getSpawnLocation();
            if (Math.hypot(x - spawn.getX(), z - spawn.getZ()) < d.distMin) continue;
            building.add(d.id);
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    AsyncChunks.load(w, chunksFor(p, x, z)).whenComplete((v, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
                        int g = ex == null ? siteOk(d, p, w, x, z) : Integer.MIN_VALUE;
                        if (g == Integer.MIN_VALUE) {
                            building.remove(d.id);
                            return;
                        }
                        placeAt(d, w, x, g, z, null, inst -> building.remove(d.id));
                    })), 2L);
            return;
        }
    }

    // ------------------------------------------------------------------ jugadores dentro

    public void startTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 80L, 20L);
    }

    private void tick() {
        if (instances.isEmpty()) return;
        long now = System.currentTimeMillis();
        boolean dirty = false;
        for (Inst i : instances) {
            World w = Bukkit.getWorld(i.world);
            if (w == null || !w.isChunkLoaded(i.x >> 4, i.z >> 4)) continue;
            Def d = defs.get(i.id);
            List<Player> inside = new ArrayList<>();
            for (Player p : w.getPlayers()) {
                if (p.getGameMode() == GameMode.SPECTATOR) continue;
                if (i.contains(p.getLocation(), 3)) inside.add(p);
            }
            if (inside.isEmpty()) continue;
            String flag = "est:" + i.id + ":" + i.x + ":" + i.z;
            for (Player p : inside) {
                plugin.quests().addProgress(p, Quest.Type.ESTRUCTURA, i.id, 1, false);
                PlayerData pd = plugin.data().get(p);
                if (!pd.flags.add(flag)) continue;
                pd.dirty = true;
                String title = d == null ? i.id : d.title == null || d.title.isBlank() ? d.name : d.title;
                p.showTitle(Title.title(Txt.mm(title), Txt.mm(d == null || d.subtitle == null ? "" : d.subtitle),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(600))));
                Txt.sound(p, "ui.toast.challenge_complete", 0.6f, 1.2f);
                if (d != null && (d.xp > 0 || d.money > 0)) plugin.levels().reward(p, d.xp, d.money);
            }
            if (d == null) continue;
            boolean creativeOnly = true;
            for (Player p : inside) if (p.getGameMode() != GameMode.CREATIVE) creativeOnly = false;
            if (creativeOnly) continue;
            i.mobs.removeIf(u -> !(Bukkit.getEntity(u) instanceof LivingEntity le) || le.isDead());
            boolean again = d.respawn > 0 && i.activatedAt > 0 && now - i.activatedAt > d.respawn * 60_000L && i.mobs.isEmpty();
            if (i.activatedAt != 0 && !again) continue;
            i.activatedAt = now;
            dirty = true;
            int k = 0;
            for (Spawn s : d.enemies) {
                for (int n = 0; n < s.amount(); n++) {
                    Location at;
                    if (!i.enemySpots.isEmpty()) {
                        int[] sp = i.enemySpots.get(k++ % i.enemySpots.size());
                        at = new Location(w, sp[0] + 0.5, sp[1], sp[2] + 0.5);
                    } else {
                        at = plugin.mobs().spawnSpot(new Location(w, i.x + 0.5, i.y + 1, i.z + 0.5), Math.max(2, Math.min(i.rx, i.rz)));
                    }
                    LivingEntity le = plugin.world().spawnMob(s.mob(), at);
                    if (le != null) {
                        le.setPersistent(true);
                        i.mobs.add(le.getUniqueId());
                    }
                }
            }
            if (d.boss != null) {
                int[] sp = i.bossSpot != null ? i.bossSpot : new int[]{i.x, i.y + 1, i.z};
                LivingEntity le = plugin.world().spawnMob(d.boss, new Location(w, sp[0] + 0.5, sp[1], sp[2] + 0.5));
                if (le != null) i.mobs.add(le.getUniqueId());
            }
        }
        if (dirty) save();
    }
}
