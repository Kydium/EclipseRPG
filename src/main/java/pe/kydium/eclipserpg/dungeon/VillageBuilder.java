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
import pe.kydium.eclipserpg.npc.NpcEntities;
import pe.kydium.eclipserpg.util.AsyncChunks;
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

    /** Radio de la aldea en bloques (para protegerla). */
    public int radius() {
        return radius;
    }
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

    /**
     * Busca un lugar plano en un bioma bueno, en espiral desde el spawn, SIN congelar el servidor:
     * cada candidato carga sus chunks en segundo plano y se revisa un candidato por tick.
     */
    public void findSiteAsync(World w, java.util.function.Consumer<Location> done) {
        Location spawn = w.getSpawnLocation();
        // "spawn" (lo normal): la aldea va donde aparecen los jugadores; solo se aparta un poco si ahí hay agua o un barranco
        boolean nearSpawn = !plugin.getConfig().getString("aldea.ubicacion", "spawn").equalsIgnoreCase("buscar");
        int maxR = nearSpawn ? plugin.getConfig().getInt("aldea.radio-spawn", 160) : plugin.getConfig().getInt("aldea.busqueda-max", 1500);
        int maxDiff = plugin.getConfig().getInt("aldea.desnivel-max", 7) + (nearSpawn ? 5 : 0);
        int stepR = nearSpawn ? 24 : 96;
        List<int[]> cands = new ArrayList<>();
        for (int r = 0; r <= maxR; r += stepR) {
            int steps = r == 0 ? 1 : nearSpawn ? Math.max(8, r / 12) : Math.max(8, r / 48);
            for (int i = 0; i < steps; i++) {
                double a = 2 * Math.PI * i / steps;
                cands.add(new int[]{spawn.getBlockX() + (int) (Math.cos(a) * r), spawn.getBlockZ() + (int) (Math.sin(a) * r)});
            }
        }
        new Object() {
            int idx;
            Location best;
            int bestDiff = Integer.MAX_VALUE;

            void next() {
                if (idx >= cands.size()) {
                    if (nearSpawn && best == null) {
                        // alrededor del spawn todo es agua o montaña: buscar más lejos
                        plugin.getConfig().set("aldea.ubicacion", "buscar");
                        findSiteAsync(w, site -> {
                            plugin.getConfig().set("aldea.ubicacion", "spawn");
                            done.accept(site);
                        });
                        return;
                    }
                    done.accept(best);
                    return;
                }
                if (idx % 10 == 0) progress("<gray>Buscando un buen lugar para la aldea… <white>" + (100 * idx / cands.size()) + "%");
                int[] c = cands.get(idx++);
                AsyncChunks.load(w, AsyncChunks.around(new int[][]{c})).whenComplete((v, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    Block top = w.getHighestBlockAt(c[0], c[1], HeightMap.MOTION_BLOCKING_NO_LEAVES);
                    if (ex != null || top.isLiquid() || (!nearSpawn && !goodBiome(w, c[0], top.getY(), c[1]))) {
                        next();
                        return;
                    }
                    int[][] grid = new int[25][];
                    int k = 0;
                    for (int dx = -40; dx <= 40; dx += 20)
                        for (int dz = -40; dz <= 40; dz += 20) grid[k++] = new int[]{c[0] + dx, c[1] + dz};
                    AsyncChunks.load(w, AsyncChunks.around(grid)).whenComplete((v2, ex2) -> Bukkit.getScheduler().runTask(plugin, () -> {
                        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
                        boolean water = false;
                        List<Integer> hs = new ArrayList<>();
                        for (int[] g : grid) {
                            Block b = w.getHighestBlockAt(g[0], g[1], HeightMap.MOTION_BLOCKING_NO_LEAVES);
                            if (b.isLiquid() || b.getType() == Material.ICE) water = true;
                            min = Math.min(min, b.getY());
                            max = Math.max(max, b.getY());
                            hs.add(b.getY());
                        }
                        if (ex2 == null && !water) {
                            hs.sort(Integer::compare);
                            Location here = new Location(w, c[0], hs.get(hs.size() / 2), c[1]);
                            int diff = max - min;
                            if (diff <= maxDiff) {
                                done.accept(here);
                                return;
                            }
                            if (nearSpawn && diff > maxDiff * 3) {
                                next();
                                return;
                            }
                            if (diff < bestDiff) {
                                bestDiff = diff;
                                best = here;
                            }
                        }
                        next();
                    }));
                }));
            }
        }.next();
    }

    private void progress(String msg) {
        for (Player p : Bukkit.getOnlinePlayers()) p.sendActionBar(Txt.mm(msg));
    }

    // ---------- Construcción ----------

    public boolean isBusy() {
        return busy;
    }

    public boolean isGenerated() {
        return plugin.getConfig().getBoolean("aldea.generada", false);
    }

    /** Se llama al iniciar el servidor: genera la aldea si todavía no existe. */
    public void autoGenerate() {
        if (isGenerated() || !plugin.getConfig().getBoolean("aldea.auto", true)) return;
        World w = Bukkit.getWorlds().get(0);
        if (busy) return;
        busy = true;
        plugin.getLogger().info("Buscando un buen lugar para la aldea (sin detener el servidor)…");
        findSiteAsync(w, site -> {
            busy = false;
            if (site == null) {
                plugin.getLogger().warning("No se encontró lugar para la aldea. Usa /eclipse construiraldea donde quieras.");
                return;
            }
            build(site, null);
        });
    }

    /** Construye la aldea con el centro de la plaza en "center" (y = nivel del suelo). */
    public void build(Location center, Player who) {
        build(center, who, null, null);
    }

    /**
     * tipo: chica | mediana | grande | auto | clasica (la de siempre, con plano fijo).
     * estilo: llanura, taiga, nieve, desierto, sabana, cerezo, pantano, selva, oscuro o auto (según el bioma).
     */
    public void build(Location center, Player who, String tipo, String estilo) {
        String mode = (tipo != null ? tipo : plugin.getConfig().getString("aldea.tipo", "auto")).toLowerCase(java.util.Locale.ROOT);
        if (!mode.equals("clasica")) {
            buildProcedural(center, who, mode, estilo != null ? estilo : plugin.getConfig().getString("aldea.estilo", "auto"));
            return;
        }
        buildClassic(center, who);
    }

    private java.io.File markerFile() {
        return new java.io.File(plugin.getDataFolder(), "datos-aldea.json");
    }

    private void buildProcedural(Location center, Player who, String mode, String estilo) {
        if (busy) {
            if (who != null) Txt.send(who, "<red>Ya se está construyendo la aldea.");
            return;
        }
        busy = true;
        World w = center.getWorld();
        Random rnd = new Random(w.getSeed() ^ (center.getBlockX() * 341873128712L) ^ (center.getBlockZ() * 132897987541L) ^ System.nanoTime());
        pe.kydium.eclipserpg.gen.VillageGen.Size size = switch (mode) {
            case "chica", "pequena", "pequeña" -> pe.kydium.eclipserpg.gen.VillageGen.Size.CHICA;
            case "mediana" -> pe.kydium.eclipserpg.gen.VillageGen.Size.MEDIANA;
            case "grande" -> pe.kydium.eclipserpg.gen.VillageGen.Size.GRANDE;
            case "enorme", "gigante", "ciudad" -> pe.kydium.eclipserpg.gen.VillageGen.Size.ENORME;
            default -> {
                if (Boolean.getBoolean("eclipse.prueba")) yield pe.kydium.eclipserpg.gen.VillageGen.Size.MEDIANA;
                int roll = rnd.nextInt(100);
                yield roll < 30 ? pe.kydium.eclipserpg.gen.VillageGen.Size.CHICA : roll < 75 ? pe.kydium.eclipserpg.gen.VillageGen.Size.MEDIANA : pe.kydium.eclipserpg.gen.VillageGen.Size.GRANDE;
            }
        };
        int cx = center.getBlockX(), cz = center.getBlockZ();
        radius = pe.kydium.eclipserpg.gen.VillageGen.radiusFor(size) + 4;
        int R0 = radius + 8;
        progress("<gray>Preparando el terreno de la aldea…");
        java.util.Set<Long> area = AsyncChunks.area(cx - R0, cz - R0, cx + R0, cz + R0);
        AsyncChunks.load(w, area).whenComplete((v, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
            for (long k : area) w.addPluginChunkTicket((int) (k >> 32), (int) k, plugin);
            var terrain = new pe.kydium.eclipserpg.structure.WorldTerrain(w);
            String styleId = estilo == null || estilo.equalsIgnoreCase("auto") ? pe.kydium.eclipserpg.gen.Style.forBiome(terrain.biome(cx, cz)) : estilo.toLowerCase(java.util.Locale.ROOT);
            pe.kydium.eclipserpg.gen.Build b;
            try {
                var vg = new pe.kydium.eclipserpg.gen.VillageGen(terrain, rnd, pe.kydium.eclipserpg.gen.Style.get(styleId), size, cx, cz);
                String tr = plugin.getConfig().getString("aldea.trazado", "auto").toUpperCase(java.util.Locale.ROOT).replace("Í", "I");
                for (var l : pe.kydium.eclipserpg.gen.VillageGen.Layout.values()) if (l.name().equals(tr)) vg.layout(l);
                b = vg.generate();
            } catch (RuntimeException e) {
                busy = false;
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "No se pudo generar la aldea", e);
                if (who != null) Txt.send(who, "<red>No se pudo generar la aldea: " + e.getMessage());
                return;
            }
            int gy = terrain.ground(cx, cz);
            markers.clear();
            for (var m : b.markers) {
                JsonObject o = new JsonObject();
                o.addProperty("t", m.type());
                o.addProperty("x", m.x() - cx);
                o.addProperty("y", m.y() - gy);
                o.addProperty("z", m.z() - cz);
                switch (m.type()) {
                    case "npc" -> {
                        o.addProperty("id", m.arg());
                        o.addProperty("yaw", m.yaw());
                    }
                    case "villager" -> {
                        if (m.arg() == null || m.arg().isEmpty()) o.add("prof", com.google.gson.JsonNull.INSTANCE);
                        else o.addProperty("prof", m.arg());
                    }
                    case "animal" -> o.addProperty("type", m.arg());
                    default -> { }
                }
                markers.add(o);
            }
            try {
                com.google.gson.JsonObject saved = new com.google.gson.JsonObject();
                saved.addProperty("radio", radius);
                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                markers.forEach(arr::add);
                saved.add("marcas", arr);
                java.nio.file.Files.writeString(markerFile().toPath(), saved.toString(), StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                plugin.getLogger().warning("No se pudo guardar datos-aldea.json: " + e.getMessage());
            }
            plugin.getLogger().info("Construyendo aldea " + size.name().toLowerCase(java.util.Locale.ROOT) + " (" + styleId + ") en " + cx + " " + gy + " "
                    + cz + ": " + b.blocks.size() + " bloques");
            if (who != null) Txt.send(who, "<yellow>Construyendo " + plugin.villageName() + " <gray>(" + size.name().toLowerCase(java.util.Locale.ROOT) + ", estilo " + styleId + ")…");
            int perTick = plugin.getConfig().getInt("aldea.bloques-por-tick", 8000);
            pe.kydium.eclipserpg.structure.BuildPlacer.place(plugin, w, b, perTick,
                    pct -> { if (pct % 10 == 0) progress("<gray>Construyendo la aldea… <white>" + pct + "%"); },
                    containers -> {
                        for (int[] c : containers) {
                            if (w.getBlockAt(c[0], c[1], c[2]).getState() instanceof org.bukkit.block.Container ct && ct.getInventory().isEmpty())
                                plugin.structures().fillTable(ct.getInventory(), pe.kydium.eclipserpg.structure.StructureManager.LOOT_VILLAGE, 2, 1);
                        }
                        finish(w, cx, gy, cz, who);
                        if (!Boolean.getBoolean("eclipse.prueba")) Bukkit.getScheduler().runTaskLater(plugin, () -> w.removePluginChunkTickets(plugin), 200L);
                    });
        }));
    }

    private void buildClassic(Location center, Player who) {
        ops.clear();
        markers.clear();
        if (markerFile().exists() && !markerFile().delete()) plugin.getLogger().warning("No se pudo borrar datos-aldea.json");
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
        int cx0 = center.getBlockX(), cz0 = center.getBlockZ(), R0 = radius + 2;
        progress("<gray>Preparando el terreno de la aldea…");
        java.util.Set<Long> area = AsyncChunks.area(cx0 - R0, cz0 - R0, cx0 + R0, cz0 + R0);
        AsyncChunks.load(w, area).whenComplete((v, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
            for (long k : area) w.addPluginChunkTicket((int) (k >> 32), (int) k, plugin);
            place(center, who);
        }));
    }

    private void place(Location center, Player who) {
        World w = center.getWorld();
        int cx = center.getBlockX(), cz = center.getBlockZ(), gy = center.getBlockY();
        int R = radius + 2;
        if (Boolean.getBoolean("eclipse.prueba")) {
            // en la prueba no hay jugadores: mantener la aldea cargada
            for (int x = (cx - R) >> 4; x <= (cx + R) >> 4; x++)
                for (int z = (cz - R) >> 4; z <= (cz + R) >> 4; z++) w.addPluginChunkTicket(x, z, plugin);
        }
        List<int[]> prep = new ArrayList<>();
        plugin.getLogger().info("Construyendo la aldea en " + cx + " " + gy + " " + cz);
        if (who != null) Txt.send(who, "<yellow>Construyendo " + plugin.villageName() + "… dame unos segundos.");

        // fase 1: preparar terreno (rellenar debajo y despejar encima)
        for (int x = cx - R; x <= cx + R; x++)
            for (int z = cz - R; z <= cz + R; z++) prep.add(new int[]{x, z});

        BlockData dirt = Material.DIRT.createBlockData();
        BlockData air = Material.AIR.createBlockData();
        final int[] i = {0};
        final int[] j = {0};
        List<int[]> joins = new ArrayList<>();
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
            if (i[0] < prep.size()) {
                if (i[0] % 2000 < 200) progress("<gray>Construyendo la aldea… <white>" + (40 * i[0] / prep.size()) + "%");
                return;
            }
            // fase 2: el plano
            while (j[0] < ops.size() && budget > 0) {
                Op o = ops.get(j[0]++);
                Block b = w.getBlockAt(cx + o.x(), gy + o.y(), cz + o.z());
                boolean fluid = FLUIDS.contains(o.data().getMaterial().getKey().getKey());
                b.setBlockData(o.data(), fluid);
                if (Connect.needs(o.data())) joins.add(new int[]{cx + o.x(), gy + o.y(), cz + o.z()});
                budget--;
            }
            if (j[0] < ops.size() && j[0] % 4000 < perTick) progress("<gray>Construyendo la aldea… <white>" + (40 + 60 * j[0] / ops.size()) + "%");
            if (j[0] < ops.size()) return;
            task.cancel();
            Connect.fix(w, joins);
            finish(w, cx, gy, cz, who);
            if (!Boolean.getBoolean("eclipse.prueba")) {
                // soltar los chunks un poco después (los NPCs ya quedaron guardados)
                Bukkit.getScheduler().runTaskLater(plugin, () -> w.removePluginChunkTickets(plugin), 200L);
            }
        }, 1L, 1L);
    }

    private void finish(World w, int cx, int gy, int cz, Player who) {
        Map<String, Location> npcSpots = new HashMap<>();
        Location campSpot = new Location(w, cx + 0.5, gy + 1, cz + 0.5);
        boolean invul = plugin.getConfig().getBoolean("aldea.aldeanos-invencibles", true);
        plugin.npcEntities().clearFillers();
        for (JsonObject m : markers) {
            String t = m.get("t").getAsString();
            Location at = new Location(w, cx + m.get("x").getAsInt() + 0.5, gy + m.get("y").getAsInt(), cz + m.get("z").getAsInt() + 0.5);
            switch (t) {
                case "center" -> campSpot = at;
                case "npc" -> {
                    at.setYaw(m.get("yaw").getAsFloat());
                    npcSpots.put(m.get("id").getAsString(), at);   // lugar de la aldea (casa, herreria, plaza_norte…)
                }
                case "villager" -> {
                    String prof = m.has("prof") && !m.get("prof").isJsonNull() ? m.get("prof").getAsString() : null;
                    plugin.npcEntities().spawnFiller(NpcEntities.roleFor(prof), at);
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
                        pe.kydium.eclipserpg.npc.Deaths.markRespawn(le, "aldea");
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
        cfg.set("aldea.puestos", null);
        for (Map.Entry<String, Location> e : npcSpots.entrySet()) {
            String k = "aldea.lugares." + e.getKey();
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
        if (plugin.getConfig().getBoolean("aldea.mover-spawn", true)) {
            // los jugadores nuevos (y los que mueren sin cama) aparecen en la plaza
            Location sp = campSpot.clone();
            sp.setYaw(180f);
            w.setSpawnLocation(sp);
            for (String rule : List.of("spawnRadius", "respawn_radius", "spawn_radius")) {
                try {
                    @SuppressWarnings("unchecked")
                    org.bukkit.GameRule<Integer> gr = (org.bukkit.GameRule<Integer>) (Object) org.bukkit.GameRule.getByName(rule);
                    if (gr != null) w.setGameRule(gr, 0);
                } catch (RuntimeException ignored) {
                    // regla con otro nombre en esta versión
                }
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld() != w || p.getLocation().distanceSquared(sp) < 40 * 40) continue;
                var d = plugin.data().get(p);
                if (d.questIndex == 0 && !d.questAccepted) {
                    p.teleportAsync(sp);
                    Txt.send(p, "<green>Te llevé a " + plugin.villageName() + "<green>: tu aventura empieza aquí.");
                }
            }
        }
        busy = false;
        plugin.npcEntities().placeStoryNpcs();
        plugin.saveConfig();
        plugin.getLogger().info("¡Aldea lista en " + cx + " " + gy + " " + cz + "!");
        for (Player p : Bukkit.getOnlinePlayers()) p.sendActionBar(Txt.mm("<green>✔ " + plugin.villageName() + " está lista"));
        if (who != null) Txt.send(who, "<green>✔ " + plugin.villageName() + " está lista, con sus NPCs y aldeanos.");
        for (Player p : Bukkit.getOnlinePlayers()) giveCompassIfNew(p);
    }

    /** Vuelve a poner a los NPCs de relleno (caminantes) de una aldea ya construida. */
    public int respawnFillers() {
        if (markerFile().exists()) {
            try {
                JsonObject saved = JsonParser.parseString(java.nio.file.Files.readString(markerFile().toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
                markers.clear();
                for (var e : saved.getAsJsonArray("marcas")) markers.add(e.getAsJsonObject());
                radius = saved.get("radio").getAsInt();
            } catch (Exception e) {
                plugin.getLogger().warning("datos-aldea.json dañado: " + e.getMessage());
                return 0;
            }
        } else if (!loadBlueprint()) return 0;
        var cfg = plugin.getConfig();
        World w = Bukkit.getWorld(cfg.getString("aldea.mundo", "world"));
        if (w == null || !cfg.contains("aldea.centro.x")) return 0;
        int cx = cfg.getInt("aldea.centro.x"), gy = cfg.getInt("aldea.centro.y"), cz = cfg.getInt("aldea.centro.z");
        plugin.npcEntities().clearFillers();
        // unir vidrios/vallas de una aldea construida con una versión anterior
        List<int[]> joins = new ArrayList<>();
        for (int x = cx - radius; x <= cx + radius; x++)
            for (int z = cz - radius; z <= cz + radius; z++)
                for (int y = gy - 2; y <= gy + 28; y++)
                    if (Connect.needs(w.getBlockAt(x, y, z).getBlockData())) joins.add(new int[]{x, y, z});
        Connect.fix(w, joins);
        // quitar aldeanos viejos de versiones anteriores dentro de la aldea
        for (Entity e : w.getNearbyEntities(new Location(w, cx, gy, cz), radius + 4, 40, radius + 4)) {
            if (e instanceof Villager v && v.getPersistentDataContainer().isEmpty()) v.remove();
        }
        int n = 0;
        for (JsonObject m : markers) {
            if (!"villager".equals(m.get("t").getAsString())) continue;
            Location at = new Location(w, cx + m.get("x").getAsInt() + 0.5, gy + m.get("y").getAsInt(), cz + m.get("z").getAsInt() + 0.5);
            String prof = m.has("prof") && !m.get("prof").isJsonNull() ? m.get("prof").getAsString() : null;
            plugin.npcEntities().spawnFiller(NpcEntities.roleFor(prof), at);
            n++;
        }
        plugin.saveConfig();
        return n;
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
            meta.displayName(Txt.item("<gold>Brújula: " + plugin.villageName()));
            meta.lore(Txt.lore(List.of("<gray>Apunta a la aldea donde empieza", "<gray>tu historia.",
                    "<gray>X " + c.getBlockX() + " · Z " + c.getBlockZ())));
            meta.setEnchantmentGlintOverride(true);
            it.setItemMeta(meta);
        }
        plugin.items().give(p, it);
    }

    private void giveCompassIfNew(Player p) {
        PlayerData d = plugin.data().get(p);
        String flag = "brujula_" + plugin.getConfig().getString("mundo-uid", "aldea");
        if (d.flags.contains(flag) || center() == null) return;
        d.flags.add(flag);
        d.dirty = true;
        giveCompass(p);
        Location c = center();
        var first = plugin.quests().byNumber(1);
        String who = first == null ? "" : "<gray> y habla con " + plugin.npcs().name(first.npc) + "<gray>.";
        Txt.send(p, "<gold>🧭 Recibiste una brújula. <gray>Síguela hasta <white>" + plugin.villageName() + " <gray>(X " + c.getBlockX()
                + ", Z " + c.getBlockZ() + ")" + who);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) giveCompassIfNew(p);
        }, 40L);
    }
}
