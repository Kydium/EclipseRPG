package pe.kydium.eclipserpg.test;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.dungeon.Box;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Prueba automática (solo con -Declipse.prueba=true): genera la aldea, NPCs, una ruina y la
 * mazmorra del acto 1 en un servidor real, revisa que todo esté bien y apaga el servidor.
 * La compilación de GitHub la ejecuta antes de publicar.
 */
public final class SmokeTest {

    private final EclipseRPG plugin;
    private final List<String> fails = new ArrayList<>();
    private int ticks;
    private int stage;
    private int wait;
    private final Map<UUID, Location> start = new HashMap<>();
    private BukkitTask task;

    public SmokeTest(EclipseRPG plugin) {
        this.plugin = plugin;
    }

    public static boolean enabled() {
        return Boolean.getBoolean("eclipse.prueba");
    }

    private void ok(String m) {
        plugin.getLogger().info("[PRUEBA] OK: " + m);
    }

    private void fail(String m) {
        fails.add(m);
        plugin.getLogger().severe("[PRUEBA] FALLO: " + m);
    }

    private void check(boolean cond, String m) {
        if (cond) ok(m);
        else fail(m);
    }

    private double maxTickMs;
    private int slowTicks;

    public void start() {
        plugin.getLogger().info("[PRUEBA] Iniciando prueba automática…");
        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onTick(com.destroystokyo.paper.event.server.ServerTickEndEvent e) {
                if (ticks < 3) return; // el arranque no cuenta
                maxTickMs = Math.max(maxTickMs, e.getTickDuration());
                if (e.getTickDuration() > 1000) slowTicks++;
            }
        }, plugin);
        checkStory();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    // ---------------------------------------------------------------- historias

    private void checkStory() {
        var st = plugin.story();
        for (var pr : st.problems()) plugin.getLogger().info("[PRUEBA] historia: " + pr);
        check(st.errorCount() == 0, "la historia de ejemplo no tiene errores (" + st.errorCount() + ")");
        check(st.doc().missions.size() == 110, "la historia tiene 110 misiones (" + st.doc().missions.size() + ")");
        check(st.doc().npcs.size() == 8 && plugin.npcs().ids().size() == 8, "la historia tiene 8 NPCs");
        check(plugin.quests().total() == 110, "el motor cargó 110 misiones (" + plugin.quests().total() + ")");
        // la plantilla de /historia nueva
        try (var in = plugin.getResource("motor/plantilla.txt")) {
            var lines = java.util.Arrays.asList(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\\R"));
            var r = new pe.kydium.eclipserpg.story.StoryParser(st.dictionary(), st.catalog()).parse(lines);
            for (var pr : r.problems()) plugin.getLogger().info("[PRUEBA] plantilla: " + pr);
            check(r.problems().isEmpty() && r.doc().missions.size() == 3, "la plantilla de historia nueva está perfecta");
        } catch (Exception e) {
            fail("no se pudo leer la plantilla: " + e);
        }
        // una historia con errores a propósito
        List<String> bad = List.of("HISTORIA: Mala", "NPC x", "  skin: noexiste", "  lugar: lunar", "MISIÓN Uno", "  npc: nadie",
                "  objetivo: bailar 3 veces", "  objetivo: entrega 5 pizzas", "  premio: 3 hierroo", "  colorr: rojo");
        var rb = new pe.kydium.eclipserpg.story.StoryParser(st.dictionary(), st.catalog()).parse(bad);
        for (var pr : rb.problems()) plugin.getLogger().info("[PRUEBA] historia mala: " + pr);
        check(rb.errors() >= 7, "una historia con errores se detecta (" + rb.errors() + " errores)");
        // armas estilo RPG: rareza y estadísticas en español
        var ci = new pe.kydium.eclipserpg.items.ItemManager.CustomItem();
        ci.id = "prueba";
        ci.name = "Hoja";
        ci.rarity = "legendario";
        ci.rpgStyle = true;
        ci.lore.add("<gray>Forjada con <gold>luz");
        ci.attributes.add(new pe.kydium.eclipserpg.items.ItemManager.AttrDef("attack_damage", 11, null, "mainhand"));
        ci.attributes.add(new pe.kydium.eclipserpg.items.ItemManager.AttrDef("movement_speed", 0.02, null, "mainhand"));
        var lore = String.join("|", plugin.items().loreLines(ci, 2));
        check(plugin.items().displayName(ci, 0).startsWith("<gold>") && lore.contains("LEGENDARIO") && lore.contains("Daño: <white>14")
                && lore.contains("+0.02") && lore.contains("Mejorada +2"), "armas estilo RPG: rareza, estadísticas y mejora (" + lore + ")");
        // diccionario: todo lo que nombra existe en esta versión de Minecraft
        checkDictionary();
    }

    private void checkDictionary() {
        java.util.List<String> bad = new java.util.ArrayList<>();
        String sec = "objetos";
        try (var in = plugin.getResource("motor/diccionario.txt")) {
            for (String l : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\\R")) {
                l = l.trim();
                if (l.isEmpty() || l.startsWith("#")) continue;
                if (l.startsWith("[")) {
                    sec = l.substring(1, l.length() - 1);
                    continue;
                }
                int eq = l.indexOf('=');
                if (eq < 0) continue;
                for (String id : l.substring(eq + 1).split(",")) {
                    id = id.trim();
                    if (id.contains("*")) continue;
                    boolean ok = switch (sec) {
                        case "objetos" -> Material.matchMaterial(id) != null;
                        case "mobs" -> org.bukkit.Registry.ENTITY_TYPE.get(org.bukkit.NamespacedKey.minecraft(id)) != null;
                        case "pociones" -> org.bukkit.Registry.POTION.get(org.bukkit.NamespacedKey.minecraft(id)) != null;
                        case "encantamientos" -> io.papermc.paper.registry.RegistryAccess.registryAccess()
                                .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT).get(org.bukkit.NamespacedKey.minecraft(id)) != null;
                        default -> true;
                    };
                    if (!ok) bad.add(sec + ": " + id);
                }
            }
        } catch (Exception e) {
            fail("no se pudo leer el diccionario: " + e);
        }
        for (String b : bad) plugin.getLogger().severe("[PRUEBA] diccionario inválido: " + b);
        check(bad.isEmpty(), "todo el diccionario existe en Minecraft " + Bukkit.getMinecraftVersion() + " (" + bad.size() + " malos)");
    }

    private void checkPack() {
        var rp = plugin.resourcePack();
        check(rp != null && rp.bytes() != null && rp.bytes().length > 10000, "el paquete de skins se armó");
        if (rp == null || rp.bytes() == null) return;
        try {
            var client = java.net.http.HttpClient.newBuilder().proxy(java.net.ProxySelector.of(null)).build();
            var req = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + rp.port() + "/eclipse-"
                    + rp.sha1().substring(0, 12) + ".zip")).timeout(java.time.Duration.ofSeconds(10)).build();
            var resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            String sha = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-1").digest(resp.body()));
            check(resp.statusCode() == 200 && sha.equals(rp.sha1()), "el servidor entrega el paquete de skins por HTTP ("
                    + resp.statusCode() + ", " + resp.body().length / 1024 + " KB)");
            int skins = 0;
            try (var z = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(resp.body()))) {
                java.util.zip.ZipEntry en;
                while ((en = z.getNextEntry()) != null) if (en.getName().startsWith("assets/eclipse/textures/npc/") && en.getName().endsWith(".png")) skins++;
            }
            check(skins == plugin.story().skins().size(), "el paquete trae todas las skins (" + skins + ")");
        } catch (Exception e) {
            fail("no se pudo descargar el paquete de skins: " + e);
        }
        // cada NPC usa una skin que existe
        for (String id : plugin.npcs().ids()) {
            String skin = plugin.npcs().def(id).skin;
            if (!plugin.story().skins().containsKey(skin)) fail("el NPC " + id + " usa la skin inexistente " + skin);
        }
    }

    private void tick() {
        ticks++;
        try {
            if (ticks > 1100) {
                fail("tiempo agotado en la etapa " + stage);
                finish();
                return;
            }
            if (wait > 0) {
                wait--;
                return;
            }
            switch (stage) {
                case 0 -> { // esperar la aldea
                    if (plugin.village().isGenerated() && !plugin.village().isBusy()) {
                        stage = 1;
                        wait = 5;
                    }
                }
                case 1 -> {
                    checkVillage();
                    stage = 2;
                    wait = 15;
                }
                case 2 -> {
                    checkPack();
                    checkReload();
                    checkBlockNames();
                    checkWalking();
                    checkRuin();
                    checkWorldBegin();
                    checkStructuresBegin();
                    checkGeneratorsBegin();
                    checkPowers();
                    checkDeaths();
                    checkReplies();
                    checkBagsAndBlocks();
                    plugin.dungeons().buildForTest(1);
                    stage = 3;
                }
                case 3 -> {
                    if (plugin.dungeons().isBuilt(1)) {
                        checkDungeon();
                        stage = 4;
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::checkEditor);
                    }
                }
                case 4 -> {
                    if (editorDone) {
                        stage = 5;
                        wait = 8;
                    }
                }
                case 5 -> {
                    if ((generatedDone < 6 || !structureDone) && genWait++ < 80) return;
                    checkWorldEnd();
                    checkStructuresEnd();
                    checkGeneratorsEnd();
                    finish();
                }
                default -> { }
            }
        } catch (Throwable t) {
            fail("excepción: " + t);
            t.printStackTrace();
            finish();
        }
    }

    private void checkVillage() {
        Location c = plugin.village().center();
        check(c != null, "la aldea tiene centro guardado");
        if (c == null) return;
        World w = c.getWorld();
        int cx = c.getBlockX(), cy = c.getBlockY() - 1, cz = c.getBlockZ();
        int built = 0, panes = 0, joined = 0;
        for (int x = cx - 44; x <= cx + 44; x++)
            for (int z = cz - 44; z <= cz + 44; z++)
                for (int y = cy + 1; y <= cy + 14; y++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (b.getType().isAir()) continue;
                    built++;
                    if (b.getType() == Material.GLASS_PANE && b.getBlockData() instanceof MultipleFacing mf) {
                        panes++;
                        if (!mf.getFaces().isEmpty()) joined++;
                    }
                }
        plugin.getLogger().info("[PRUEBA] aldea en " + cx + " " + cy + " " + cz + ": " + built + " bloques, " + panes + " vidrios");
        check(built > 8000, "la aldea tiene bloques construidos (" + built + ")");
        check(panes > 0 && joined >= panes * 0.9, "los vidrios están unidos (" + joined + "/" + panes + ")");
        Block below = w.getBlockAt(cx, cy, cz);
        check(!below.isLiquid() && below.getType() != Material.ICE, "la aldea no está sobre agua/hielo (" + below.getType() + ")");

        Map<String, UUID> story = plugin.npcEntities().storyIds();
        int alive = 0;
        for (Map.Entry<String, UUID> e : story.entrySet()) {
            Entity en = Bukkit.getEntity(e.getValue());
            if (en instanceof Mannequin m && m.isValid()) {
                alive++;
                if (m.customName() == null) fail("el NPC " + e.getKey() + " no tiene nombre");
            } else fail("el NPC " + e.getKey() + " no existe como maniquí");
        }
        check(alive == 8, "los 8 NPCs de la historia existen (" + alive + ")");
        int fill = 0;
        for (UUID u : plugin.npcEntities().fillerIds()) {
            Entity en = Bukkit.getEntity(u);
            if (en instanceof Mannequin && en.isValid()) {
                fill++;
                start.put(u, en.getLocation());
            }
        }
        check(fill >= 12, "hay aldeanos de relleno (" + fill + ")");
        check(plugin.getConfig().contains("campamento.x"), "el campamento quedó guardado");
    }

    private void checkWalking() {
        int moved = 0, stuck = 0;
        for (Map.Entry<UUID, Location> e : start.entrySet()) {
            Entity en = Bukkit.getEntity(e.getKey());
            if (en == null) continue;
            Location now = en.getLocation();
            if (now.distance(e.getValue()) > 0.5) moved++;
            Block feet = now.getBlock(), head = feet.getRelative(0, 1, 0);
            boolean inside = !head.isPassable() || (!feet.isPassable() && feet.getBoundingBox().getMaxY() > now.getY() + 0.05);
            if (inside) {
                stuck++;
                plugin.getLogger().info("[PRUEBA] dentro de bloque: " + feet.getType() + "/" + head.getType() + " en " + now.getBlockX() + " " + now.getY() + " " + now.getBlockZ());
            }
            if (now.getY() < e.getValue().getY() - 3) fail("un aldeano cayó (" + now + ")");
        }
        check(moved >= start.size() / 3, "los aldeanos caminan (" + moved + "/" + start.size() + ")");
        check(stuck == 0, "ningún aldeano dentro de un bloque (" + stuck + ")");
    }

    private void checkRuin() {
        Location c = plugin.village().center();
        World w = c.getWorld();
        int x = c.getBlockX() + 200, z = c.getBlockZ() + 200;
        w.getChunkAt(x >> 4, z >> 4).load(true);
        Block chest = plugin.ruins().buildAt(w, x, z);
        check(chest != null, "se construyó una ruina");
        if (chest != null && chest.getState() instanceof Chest ch) {
            int items = count(ch.getBlockInventory().getContents());
            check(items > 0, "el cofre de la ruina tiene botín (" + items + " objetos)");
        } else if (chest != null) fail("la ruina no tiene cofre en " + chest.getLocation());
    }

    private void checkDungeon() {
        Box b = plugin.dungeons().bounds(1);
        int[] e = plugin.dungeons().entrance(1);
        check(b != null && e != null, "la mazmorra del acto 1 tiene límites y entrada");
        if (b == null) return;
        World w = plugin.village().center().getWorld();
        int chests = 0, full = 0, spawners = 0, solid = 0;
        for (int x = b.x1(); x <= b.x2(); x++)
            for (int z = b.z1(); z <= b.z2(); z++)
                for (int y = b.y1(); y <= b.y2(); y++) {
                    Block bl = w.getBlockAt(x, y, z);
                    if (!bl.getType().isAir()) solid++;
                    if (bl.getType() == Material.CHEST && bl.getState() instanceof Chest ch) {
                        chests++;
                        if (count(ch.getBlockInventory().getContents()) > 0) full++;
                    }
                    if (bl.getType() == Material.SPAWNER) spawners++;
                }
        plugin.getLogger().info("[PRUEBA] mazmorra: " + solid + " bloques, " + chests + " cofres, " + spawners + " generadores");
        check(solid > 3000, "la mazmorra tiene bloques (" + solid + ")");
        check(chests > 0 && full == chests, "todos los cofres de la mazmorra tienen botín (" + full + "/" + chests + ")");
        check(spawners > 0, "la mazmorra tiene generadores (" + spawners + ")");
        Block gate = w.getBlockAt(e[0], e[1], e[2]);
        check(gate.isPassable(), "la entrada de la mazmorra está libre (" + gate.getType() + ")");
    }

    /** Revisa que todos los bloques de los 10 temas y de las ruinas existan en esta versión. */
    private void checkBlockNames() {
        java.util.Set<String> bad = new java.util.TreeSet<>();
        java.util.Set<String> keys = java.util.Set.of("suelo", "pared", "techo", "acento", "decoracion", "luz", "escalera",
                "losa", "muro", "peligro", "luz-tenue", "sangre", "tesoro");
        for (int act = 1; act <= 10; act++) {
            var t = plugin.dungeons().themeFor(act);
            if (t == null) {
                fail("no hay tema para el acto " + act);
                continue;
            }
            for (String k : keys) {
                List<String> vals = t.isList(k) ? t.getStringList(k) : (t.isString(k) ? List.of(t.getString(k)) : List.of());
                for (String v : vals) if (!valid(v)) bad.add("acto " + act + " " + k + "=" + v);
            }
            for (String line : t.getStringList("botin")) {
                String spec = line.trim().split("\\s+")[0];
                String sp = spec.contains(":") ? spec : "material:" + spec;
                if (plugin.shops().buildSpec(sp, 1) == null) bad.add("acto " + act + " botín " + spec);
            }
        }
        for (boolean desert : new boolean[]{false, true})
            for (pe.kydium.eclipserpg.dungeon.RuinBuilder.Variant v : pe.kydium.eclipserpg.dungeon.RuinBuilder.Variant.values())
                for (String st : new pe.kydium.eclipserpg.dungeon.RuinBuilder(1, desert).build(v).blocks.values())
                    if (!valid(st)) bad.add("ruina " + v + " " + st);
        int missing = 0;
        for (int seed = 0; seed < 200; seed++)
            for (boolean desert : new boolean[]{false, true})
                for (var v : pe.kydium.eclipserpg.dungeon.RuinBuilder.Variant.values()) {
                    var rr = new pe.kydium.eclipserpg.dungeon.RuinBuilder(seed, desert).build(v);
                    if (!rr.blocks.getOrDefault(rr.chestX + "," + rr.chestY + "," + rr.chestZ, "").startsWith("chest")) missing++;
                }
        check(missing == 0, "las ruinas siempre tienen su cofre (1200 ruinas de prueba, " + missing + " sin cofre)");
        for (String b : bad) plugin.getLogger().severe("[PRUEBA] bloque/objeto inválido: " + b);
        check(bad.isEmpty(), "todos los bloques y botines de mazmorras y ruinas existen (" + bad.size() + " malos)");
    }

    private static boolean valid(String state) {
        try {
            Bukkit.createBlockData(state.contains(":") ? state : "minecraft:" + state);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static int count(ItemStack[] items) {
        int n = 0;
        for (ItemStack it : items) if (it != null && !it.getType().isAir()) n++;
        return n;
    }

    private volatile boolean editorDone;

    /** Prueba la API del editor visual (desde otro hilo, como lo haría el navegador). */
    private void checkEditor() {
        try {
            var http = java.net.http.HttpClient.newBuilder().proxy(java.net.ProxySelector.of(null)).build();
            String base = "http://127.0.0.1:" + plugin.resourcePack().port();
            String tok = plugin.editor().token();
            java.util.function.BiFunction<String, String, java.net.http.HttpResponse<String>> get = (path, t) -> {
                try {
                    var b = java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + path)).timeout(java.time.Duration.ofSeconds(40));
                    if (t != null) b.header("X-Token", t);
                    return http.send(b.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
            var page = get.apply("/editor", null);
            check(page.statusCode() == 200 && page.body().contains("Editor de historias"), "el servidor entrega el editor visual");
            check(get.apply("/api/catalogo", "malo").statusCode() == 401, "el editor rechaza a quien no tiene el código");
            var cat = com.google.gson.JsonParser.parseString(get.apply("/api/catalogo", tok).body()).getAsJsonObject();
            check(cat.getAsJsonArray("skins").size() >= 67 && cat.getAsJsonArray("materiales").size() > 500
                    && cat.getAsJsonArray("entidades").size() > 30, "el editor recibe el catálogo (skins, materiales, criaturas)");
            String text = com.google.gson.JsonParser.parseString(get.apply("/api/texto", tok).body()).getAsJsonObject().get("texto").getAsString();
            var rev = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/api/revisar")).header("X-Token", tok)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(text)).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            var rj = com.google.gson.JsonParser.parseString(rev.body()).getAsJsonObject();
            check(rj.get("errores").getAsInt() == 0 && rj.get("misiones").getAsInt() == 110, "el editor revisa la historia (0 errores, 110 misiones)");
            var bad = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/api/revisar")).header("X-Token", tok)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString("HISTORIA: x\nMISIÓN y\n  objetivo: bailar\n")).build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            check(com.google.gson.JsonParser.parseString(bad.body()).getAsJsonObject().get("errores").getAsInt() > 0, "el editor detecta errores");
            // jefes: leer y volver a guardar sin cambios
            int before = plugin.mobs().ids().size();
            var mobs = com.google.gson.JsonParser.parseString(get.apply("/api/yaml?archivo=mobs", tok).body()).getAsJsonObject().get("datos");
            var put = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/api/yaml?archivo=mobs")).header("X-Token", tok)
                    .PUT(java.net.http.HttpRequest.BodyPublishers.ofString(mobs.toString())).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            var apply = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/api/aplicar")).header("X-Token", tok)
                    .POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            var aj = com.google.gson.JsonParser.parseString(apply.body()).getAsJsonObject();
            int after = Bukkit.getScheduler().callSyncMethod(plugin, () -> plugin.mobs().ids().size()).get();
            check(put.statusCode() == 200 && aj.get("aplicado").getAsBoolean() && after == before,
                    "guardar jefes desde el editor y aplicar funciona (" + before + " → " + after + ")");
            // todo en un texto, leer bloques AVANZADO y empezar desde cero
            String full = com.google.gson.JsonParser.parseString(get.apply("/api/texto-completo?origen=ejemplo", tok).body()).getAsJsonObject().get("texto").getAsString();
            check(full.contains("\nAVANZADO tiendas\n") && full.contains("HISTORIA:") && full.contains("legendaria:"), "el texto completo trae la historia y todo lo avanzado");
            var yl = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/api/yaml-leer")).header("X-Token", tok)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"tiendas\":\"tiendas:\\n  x:\\n    filas: 3\\n\",\"mobs\":\"mobs: [\"}")).build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            var ylj = com.google.gson.JsonParser.parseString(yl.body()).getAsJsonObject();
            check(ylj.getAsJsonObject("datos").getAsJsonObject("tiendas").getAsJsonObject("tiendas").has("x") && ylj.getAsJsonObject("errores").has("mobs"),
                    "los bloques AVANZADO se leen y avisan si tienen errores");
            var vac = com.google.gson.JsonParser.parseString(get.apply("/api/vacio", tok).body()).getAsJsonObject().getAsJsonObject("avanzado");
            check(vac.getAsJsonObject("mobs").getAsJsonObject("mobs").size() == 0 && !vac.getAsJsonObject("mazmorras").toString().contains("\"jefe\""),
                    "«empezar desde cero» deja todo vacío");
            String empty = "HISTORIA: Mi nueva historia\nDESCRIPCION: x\nALDEA: Mi aldea\nMONEDA: monedas\n\nNPC guia\n  nombre: Guía\n  color: dorado\n  oficio: Guía de la aldea\n  lugar: plaza_norte\n  charla: Hola\n\nACTO 1: El comienzo\n\nMISIÓN Primeros pasos\n  npc: guia\n  dice: Hola\n  objetivo: tala 8 troncos\n  premio: 50 xp\n";
            var er = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/api/revisar")).header("X-Token", tok)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(empty)).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            check(com.google.gson.JsonParser.parseString(er.body()).getAsJsonObject().get("errores").getAsInt() == 0, "la historia en blanco no tiene errores");
            // skin desde un link
            var sk = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/api/skin?nombre=prueba_link&url="
                    + java.net.URLEncoder.encode("https://raw.githubusercontent.com/Kydium/EclipseRPG/main/src/main/resources/skins/pirata.png",
                    java.nio.charset.StandardCharsets.UTF_8))).header("X-Token", tok).POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            boolean has = Bukkit.getScheduler().callSyncMethod(plugin, () -> plugin.story().skins().containsKey("prueba_link")).get();
            check(sk.statusCode() == 200 && has, "subir una skin desde un link funciona (" + sk.statusCode() + ")");
        } catch (Exception e) {
            fail("la API del editor falló: " + e);
            e.printStackTrace();
        } finally {
            editorDone = true;
        }
    }

    // ---------------------------------------------------------------- mundo vivo

    private int guardHits;
    private final List<org.bukkit.entity.LivingEntity> husks = new ArrayList<>();

    private void checkWorldBegin() {
        var wm = plugin.world();
        check(wm.ruleIds().size() >= 8 && wm.eventIds().size() >= 3 && wm.spawnRuleCount() >= 4,
                "mundo vivo cargado (" + wm.ruleIds().size() + " reglas, " + wm.eventIds().size() + " eventos, " + wm.spawnRuleCount() + " apariciones)");
        check(wm.start("luna_sangre") && wm.activeEvents().contains("luna_sangre"), "un evento del mundo empieza");
        check(wm.stop("luna_sangre", true) && wm.activeEvents().isEmpty(), "un evento del mundo termina");
        check(wm.start("tormenta_eclipse"), "empieza la tormenta");
        check(!wm.start("no_existe"), "un evento que no existe no rompe nada");
        // historia con campos nuevos
        var st = plugin.story();
        List<String> lines = List.of("HISTORIA: Roles", "NPC cabo", "  nombre: Cabo", "  combate: guardián", "  vida: 60", "  daño: 8",
                "MISIÓN Noche roja", "  npc: cabo", "  evento: luna_sangre", "  clima: tormenta", "  objetivo: derrota a 3 zombis");
        var r = new pe.kydium.eclipserpg.story.StoryParser(st.dictionary(), st.catalog()).parse(lines);
        for (var pr : r.problems()) plugin.getLogger().info("[PRUEBA] roles: " + pr);
        check(r.errors() == 0 && "guardian".equals(r.doc().npcs.get("cabo").combat) && "luna_sangre".equals(r.doc().missions.get(0).event),
                "la historia entiende combate, evento y clima");
        // guardias contra hostiles
        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
            public void onHit(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
                if (e.getDamager() instanceof Mannequin && !e.isCancelled()) guardHits++;
            }
        }, plugin);
        int guards = 0;
        for (UUID u : plugin.npcEntities().fillerIds()) {
            Entity g = Bukkit.getEntity(u);
            if (g == null || !plugin.npcEntities().isGuard(g)) continue;
            guards++;
            if (guards > 3) continue;
            Location at = plugin.mobs().spawnSpot(g.getLocation(), 3);
            if (g.getWorld().spawnEntity(at, org.bukkit.entity.EntityType.HUSK) instanceof org.bukkit.entity.LivingEntity h) {
                h.setAI(false);
                h.setPersistent(true);
                husks.add(h);
            }
        }
        check(guards > 0 && !husks.isEmpty(), "hay guardias en la aldea (" + guards + ")");
    }

    private void checkWorldEnd() {
        var w = Bukkit.getWorlds().get(0);
        check(w.hasStorm() && plugin.world().activeEvents().contains("tormenta_eclipse"), "el evento mantiene la tormenta");
        plugin.world().stop("tormenta_eclipse", false);
        check(!w.hasStorm(), "al terminar el evento vuelve el buen tiempo");
        int dead = 0;
        for (var h : husks) {
            if (h.isDead() || !h.isValid()) dead++;
            else h.remove();
        }
        check(guardHits > 0, "los guardias atacan a los hostiles (" + guardHits + " golpes, " + dead + "/" + husks.size() + " vencidos)");
    }

    // ---------------------------------------------------------------- estructuras

    private pe.kydium.eclipserpg.structure.StructureManager.Inst builtStructure;
    private boolean structureDone;

    private void checkStructuresBegin() {
        var sm = plugin.structures();
        int bad = 0, blocks = 0;
        for (String n : sm.planNames()) {
            var pl = sm.plan(n);
            if (pl == null) bad += 1000;
            else {
                bad += pl.bad;
                blocks += pl.ops.size();
            }
        }
        check(sm.ids().size() >= 3 && sm.planNames().size() >= 3 && bad == 0,
                "estructuras cargadas (" + sm.ids().size() + " definidas, " + sm.planNames().size() + " planos, " + blocks + " bloques, " + bad + " malos)");
        var st = plugin.story();
        List<String> lines = List.of("HISTORIA: Torres", "NPC cabo", "  nombre: Cabo", "MISIÓN La torre", "  npc: cabo", "  estructura: torre_vigia",
                "  objetivo: encuentra la estructura torre_vigia", "MISIÓN Otra", "  npc: cabo", "  objetivo: encuentra la estructura castillo_volador");
        var r = new pe.kydium.eclipserpg.story.StoryParser(st.dictionary(), st.catalog()).parse(lines);
        for (var pr : r.problems()) plugin.getLogger().info("[PRUEBA] estructuras: " + pr);
        var m0 = r.doc().missions.get(0);
        check(r.errors() == 1 && "torre_vigia".equals(m0.structure) && m0.objectives.size() == 1 && "ESTRUCTURA".equals(m0.objectives.get(0).type),
                "la historia entiende «estructura:» y «encuentra la estructura …» (" + r.errors() + " error esperado)");
        sm.buildForTest("torre_vigia", inst -> {
            builtStructure = inst;
            structureDone = true;
        });
    }

    private void checkStructuresEnd() {
        check(structureDone && builtStructure != null, "una estructura se construye sola en un buen lugar");
        if (builtStructure == null) return;
        var i = builtStructure;
        World w = Bukkit.getWorld(i.world);
        Block base = w.getBlockAt(i.x, i.y, i.z);
        int loot = 0, chests = 0;
        for (int x = i.x - i.rx; x <= i.x + i.rx; x++) for (int y = i.y; y <= i.y + i.h; y++) for (int z = i.z - i.rz; z <= i.z + i.rz; z++) {
            if (w.getBlockAt(x, y, z).getState() instanceof org.bukkit.block.Container c) {
                chests++;
                for (ItemStack it : c.getInventory().getContents()) if (it != null) loot++;
            }
        }
        check(base.getType() == Material.STONE_BRICKS && chests >= 2 && loot > 0,
                "la torre tiene suelo, cofres y botín (" + base.getType() + ", " + chests + " cofres, " + loot + " objetos) en " + i.x + " " + i.y + " " + i.z);
        check(i.enemySpots.size() == 4 && i.entrance != null && plugin.structures().count("torre_vigia") >= 1, "la torre guardó sus marcas de enemigos y entrada");
        // guardar un plano desde el mundo y volver a leerlo
        String msg = plugin.structures().savePlan("prueba_copia", w, new int[]{i.x - i.rx, i.y, i.z - i.rz}, new int[]{i.x + i.rx, i.y + i.h, i.z + i.rz});
        var copy = plugin.structures().plan("prueba_copia");
        check(copy != null && copy.ops.size() > 300 && copy.bad == 0 && plugin.structures().def("prueba_copia") != null,
                "/estructura guardar crea el plano y lo registra (" + (copy == null ? msg : copy.ops.size() + " bloques") + ")");
    }

    // ---------------------------------------------------------------- generador procedural

    private final List<pe.kydium.eclipserpg.structure.StructureManager.Inst> generated = new ArrayList<>();
    private int generatedDone, genWait;

    private void checkGeneratorsBegin() {
        // 1) todos los bloques de todas las estructuras del catálogo existen en esta versión de Minecraft
        pe.kydium.eclipserpg.gen.Terrain flat = new pe.kydium.eclipserpg.gen.Terrain() {
            public int ground(int x, int z) {
                return 64 + (int) Math.round(Math.sin(x / 9.0) * 3 + Math.cos(z / 11.0) * 3);
            }

            public boolean water(int x, int z) {
                return x > 40 && z > 40;
            }

            public String surface(int x, int z) {
                return "grass_block";
            }
        };
        java.util.Set<String> bad = new java.util.TreeSet<>();
        int total = 0;
        for (String id : pe.kydium.eclipserpg.gen.Catalog.ALL.keySet()) {
            for (String st : pe.kydium.eclipserpg.gen.Style.ids()) {
                if (!id.startsWith("aldea") && !st.equals("llanura") && !st.equals("desierto") && !st.equals("cerezo")) continue;
                try {
                    var b = pe.kydium.eclipserpg.gen.Catalog.generate(id, flat, new java.util.Random(id.hashCode() + st.hashCode()), pe.kydium.eclipserpg.gen.Style.get(st), 0, 0);
                    total += b.blocks.size();
                    for (String s : new java.util.HashSet<>(b.blocks.values())) {
                        try {
                            Bukkit.createBlockData(s.contains(":") ? s : "minecraft:" + s);
                        } catch (IllegalArgumentException e) {
                            bad.add(s);
                        }
                    }
                } catch (RuntimeException e) {
                    fail("el generador " + id + " (" + st + ") falló: " + e);
                }
            }
        }
        if (!bad.isEmpty()) plugin.getLogger().info("[PRUEBA] bloques que no existen: " + bad);
        check(bad.isEmpty(), "todos los bloques del catálogo existen (" + total + " bloques generados, " + bad.size() + " malos)");
        // 2) construir dos generadas de verdad junto a la aldea
        var sm = plugin.structures();
        Location c = plugin.village().center();
        int k = 0;
        for (String id : List.of("torre_mago", "fortaleza", "base_inicial", "galeon_volador", "piramide", "forja_legendaria")) {
            var d = sm.catalogDef(id, null);
            int x = c.getBlockX() - 260 + k * 90, z = c.getBlockZ() - 260;
            k++;
            sm.placeGenerated(d, c.getWorld(), x, z, null, inst -> {
                if (inst != null) generated.add(inst);
                generatedDone++;
            });
        }
    }

    private void checkGeneratorsEnd() {
        check(generatedDone == 6 && generated.size() == 6, "se construyeron 6 estructuras generadas (" + generated.size() + "/" + generatedDone + ")");
        int emptyChests = 0, totalChests = 0, rich = 0, spawners = 0, typed = 0, legendary = 0;
        for (var i : generated) {
            World w = Bukkit.getWorld(i.world);
            for (int x = i.x - i.rx - 2; x <= i.x + i.rx + 2; x++)
                for (int z = i.z - i.rz - 2; z <= i.z + i.rz + 2; z++)
                    for (int y = i.y - 10; y <= i.y + i.h + 4; y++) {
                        Block b = w.getBlockAt(x, y, z);
                        if (b.getState() instanceof org.bukkit.block.Container ct && (b.getType() == Material.CHEST || b.getType() == Material.BARREL)) {
                            if (i.id.equals("base_inicial")) continue;
                            totalChests++;
                            if (ct.getInventory().isEmpty()) emptyChests++;
                            for (ItemStack it : ct.getInventory().getContents())
                                if (it != null && (it.getType() == Material.DIAMOND || it.getType().name().startsWith("NETHERITE") || it.getType() == Material.ENCHANTED_GOLDEN_APPLE
                                        || it.getType() == Material.TOTEM_OF_UNDYING || it.getType() == Material.ENCHANTED_BOOK)) rich++;
                            for (ItemStack it : ct.getInventory().getContents()) {
                                String cid = it == null ? null : plugin.items().idOf(it);
                                var cd = cid == null ? null : plugin.items().def(cid);
                                if (i.id.equals("forja_legendaria") && cd != null && !cd.powers.isEmpty()) legendary++;
                            }
                        }
                        if (b.getState() instanceof org.bukkit.block.CreatureSpawner sp) {
                            spawners++;
                            if (sp.getSpawnedType() != null) typed++;
                        }
                    }
        }
        check(totalChests > 5 && emptyChests == 0, "ningún cofre de las estructuras está vacío (" + emptyChests + " vacíos de " + totalChests + ")");
        check(legendary > 0, "la forja legendaria trae un arma con poderes en el altar (" + legendary + ")");
        check(rich > 0, "hay botín valioso en los tesoros escondidos (" + rich + " objetos)");
        check(spawners > 0 && typed == spawners, "los generadores de monstruos tienen su criatura (" + typed + "/" + spawners + ")");
        for (var i : generated) {
            World w = Bukkit.getWorld(i.world);
            int solid = 0, chests = 0;
            for (int x = i.x - i.rx; x <= i.x + i.rx; x += 1)
                for (int z = i.z - i.rz; z <= i.z + i.rz; z += 1)
                    for (int y = i.y; y <= i.y + i.h; y++) {
                        Block b = w.getBlockAt(x, y, z);
                        if (!b.getType().isAir()) solid++;
                        if (b.getType() == Material.CHEST) chests++;
                    }
            check(solid > 500, i.id + " tiene bloques (" + solid + ", " + chests + " cofres) en " + i.x + " " + i.y + " " + i.z);
        }
    }

    private void checkReplies() {
        var q1 = plugin.quests().byNumber(1);
        check(q1 != null && q1.replies.size() == 2 && q1.replies.get(0).contains("madera"), "las misiones tienen respuestas propias del jugador");
        pe.kydium.eclipserpg.quests.Quest boss = null, give = null;
        for (int i = 1; i <= 200; i++) {
            var q = plugin.quests().byNumber(i);
            if (q == null) break;
            if (q.replies.isEmpty() && q.boss && boss == null) boss = q;
            if (q.replies.isEmpty() && !q.boss && give == null && !q.objectives.isEmpty() && q.objectives.get(0).type == pe.kydium.eclipserpg.quests.Quest.Type.ENTREGAR) give = q;
        }
        String br = boss == null ? "" : boss.autoReply(), gr = give == null ? "" : give.autoReply();
        plugin.getLogger().info("[PRUEBA] respuesta automática de jefe: " + br + " | de entrega: " + gr);
        check(br.contains("Derrotaré") && gr.contains("Te traeré"), "si no tiene respuesta, el jugador contesta según el objetivo");
        String lbl = plugin.shops().buttonLabel("runas");
        check(lbl.contains("Runas de forja") && !plugin.shops().buttonLabel("herreria").contains("Tienda"), "los botones de tienda dicen lo que venden (" + lbl + ")");
    }

    private void checkBagsAndBlocks() {
        ItemStack bag = plugin.items().create("mochila_grande", 1);
        check(bag != null && plugin.backpacks().size(bag) == 54 && bag.getMaxStackSize() == 1, "las mochilas se crean con sus huecos");
        ItemStack big = plugin.items().create("mochila_del_viajero", 1);
        check(big != null && plugin.backpacks().size(big) == 200, "mochila de 200 huecos");
        var sb = plugin.specialBlocks();
        check(sb.defs().size() >= 6 && sb.item("mena_hierro_eterna", 1) != null, "los bloques especiales cargan (" + sb.defs().size() + ")");
        ItemStack fromShop = plugin.shops().buildSpec("bloque:generador_de_hierro", 1);
        check(fromShop != null && fromShop.getType() == Material.BLAST_FURNACE, "los bloques especiales se venden en tiendas");
        org.bukkit.Location at = plugin.items().campLocation().clone().add(6, 0, 6);
        int before = sb.placedCount();
        sb.put(at.getBlock(), "mena_hierro_eterna");
        check(sb.placedCount() == before + 1 && at.getBlock().getType() == Material.IRON_ORE, "un bloque especial se coloca");
        try {
            for (var pk : Bukkit.getDatapackManager().getPacks())
                plugin.getLogger().info("[PRUEBA] datapack " + pk.getName() + " activo=" + pk.isEnabled() + " compat=" + pk.getCompatibility());
        } catch (Throwable t) {
            plugin.getLogger().info("[PRUEBA] datapacks: " + t);
        }
        check(plugin.hotkeys().datapackActive(), "el paquete de la tecla G está activo");
        boolean dlg = false;
        try {
            var reg = io.papermc.paper.registry.RegistryAccess.registryAccess().getRegistry(io.papermc.paper.registry.RegistryKey.DIALOG);
            dlg = reg.get(net.kyori.adventure.key.Key.key("eclipse", "menu")) != null;
        } catch (Throwable t) {
            plugin.getLogger().info("[PRUEBA] registro de diálogos: " + t);
        }
        if (!dlg) try {
            var reg = io.papermc.paper.registry.RegistryAccess.registryAccess().getRegistry(io.papermc.paper.registry.RegistryKey.DIALOG);
            StringBuilder ks = new StringBuilder();
            reg.keyStream().limit(20).forEach(k -> ks.append(k.asString()).append(' '));
            plugin.getLogger().info("[PRUEBA] diálogos: " + ks);
            ClassLoader cl = Bukkit.getServer().getClass().getClassLoader();
            for (String r : new String[]{"data/minecraft/dialog/server_links.json", "data/minecraft/dialogs/server_links.json", "data/minecraft/tags/dialog/quick_actions.json", "data/minecraft/tags/dialog/pause_screen_additions.json"})
                plugin.getLogger().info("[PRUEBA] recurso " + r + " = " + (cl.getResource(r) != null));
            java.io.File dp = new java.io.File(pe.kydium.eclipserpg.menu.HotkeyMenu.levelDir(), "datapacks/eclipse_atajos");
            try (var w = java.nio.file.Files.walk(dp.toPath())) { w.forEach(f -> plugin.getLogger().info("[PRUEBA] archivo " + f)); }
        } catch (Throwable t) {
            plugin.getLogger().info("[PRUEBA] diag: " + t);
        }
        if (!dlg) try {
            for (String ln : java.nio.file.Files.readAllLines(new java.io.File("logs/latest.log").toPath()))
                if (ln.toLowerCase().contains("dialog") || ln.contains("eclipse:menu") || ln.contains("eclipse_atajos"))
                    plugin.getLogger().info("[PRUEBA] log: " + (ln.length() > 400 ? ln.substring(0, 400) : ln));
        } catch (Exception ignored) {
            // sin log
        }
        check(dlg, "el menú de la tecla G (diálogo eclipse:menu) está cargado");
    }

    private void checkDeaths() {
        var combat = plugin.npcEntities().combat();
        org.bukkit.entity.LivingEntity npc = null;
        for (java.util.UUID u : plugin.npcEntities().fillerIds())
            if (Bukkit.getEntity(u) instanceof org.bukkit.entity.LivingEntity le && le.isValid() && !plugin.npcEntities().isGuard(le)) {
                npc = le;
                break;
            }
        if (npc == null) {
            check(false, "hay un aldeano para probar las caídas");
            return;
        }
        combat.hurt(npc, 999);
        check(combat.isDown(npc) && npc.getScoreboardTags().contains(pe.kydium.eclipserpg.npc.Combat.FALLEN), "un NPC derribado cae al suelo");
        combat.stand(npc);
        check(!combat.isDown(npc) && !npc.isInvisible() && !npc.getScoreboardTags().contains(pe.kydium.eclipserpg.npc.Combat.FALLEN), "el NPC vuelve a levantarse en su casa");
        org.bukkit.Location at = npc.getLocation().add(3, 0, 3);
        var cow = at.getWorld().spawn(at, org.bukkit.entity.Cow.class);
        cow.setHealth(0);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int bodies = 0;
            for (var e : at.getWorld().getNearbyEntities(at, 6, 6, 6))
                if (e.getScoreboardTags().contains(pe.kydium.eclipserpg.npc.Combat.CORPSE)) bodies++;
            check(bodies > 0, "al morir queda el cuerpo tendido (" + bodies + ")");
        }, 3L);
    }

    private void checkPowers() {
        check(plugin.levels().maxLevel() == Integer.MAX_VALUE, "el nivel no tiene límite");
        var cv = plugin.items().def("colmillo_vampirico");
        check(cv != null && cv.powers.size() == 2 && cv.powers.get(0).type().equals("robo_de_vida"), "las armas especiales cargan sus poderes");
        int withPowers = 0;
        for (String id : plugin.items().ids()) if (!plugin.items().def(id).powers.isEmpty()) withPowers++;
        check(withPowers >= 8, "hay " + withPowers + " armas con poderes");
        ItemStack sp = plugin.shops().buildSpec("especial:legendario", 1);
        String sid = sp == null ? null : plugin.items().idOf(sp);
        check(sid != null && plugin.items().ofRarity("legendario").contains(sid), "el botín «especial:legendario» da un arma especial (" + sid + ")");
        ItemStack rune = plugin.shops().buildSpec("runa:robo_de_vida:5", 1);
        check(rune != null && rune.getItemMeta().getPersistentDataContainer().has(pe.kydium.eclipserpg.items.Powers.RUNE), "las runas se crean desde la tienda");
        ItemStack rnd = plugin.shops().buildSpec("runa:azar", 1);
        check(rnd != null && rnd.getItemMeta().getPersistentDataContainer().has(pe.kydium.eclipserpg.items.Powers.RUNE), "las runas al azar funcionan");
        var pt = plugin.items().def("pico_titan");
        check(pt != null && pt.powers.size() == 5, "las herramientas especiales cargan sus poderes");
        ItemStack ing = pe.kydium.eclipserpg.items.ToolPowers.smelt(Material.RAW_IRON);
        check(ing != null && ing.getType() == Material.IRON_INGOT, "el poder fundir convierte hierro crudo en lingote");
        check(plugin.shops().ids().contains("herramientas_magicas"), "existe la tienda de herramientas mágicas");
        check(pe.kydium.eclipserpg.items.Powers.TYPES.size() >= 45, "hay " + pe.kydium.eclipserpg.items.Powers.TYPES.size() + " poderes");
        java.util.List<String> lore = plugin.items().loreLines(cv, 0);
        check(lore.stream().anyMatch(l -> l.contains("Robo de vida")), "la descripción muestra los poderes");
        // forjar a mano una runa en una espada normal (como lo hace el evento)
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        var meta = sword.getItemMeta();
        meta.getPersistentDataContainer().set(pe.kydium.eclipserpg.items.Powers.RUNES, org.bukkit.persistence.PersistentDataType.STRING, "rayo:4.0:0.1;fuego:3.0:0.25");
        check(pe.kydium.eclipserpg.items.Powers.runesOf(meta).size() == 2 && pe.kydium.eclipserpg.items.Powers.runeLore(meta).size() >= 3, "las runas forjadas se leen y se muestran");
        check(plugin.shops().ids().contains("legendaria") && plugin.shops().ids().contains("runas"), "existen las tiendas legendaria y runas");
    }

    private void checkReload() {
        var before = new java.util.HashMap<>(plugin.npcEntities().storyIds());
        var r = plugin.reloadStory(false);
        check(r.errors() == 0 && plugin.quests().total() == 110, "/historia recargar funciona");
        int alive = 0;
        for (UUID u : plugin.npcEntities().storyIds().values()) if (Bukkit.getEntity(u) instanceof Mannequin m && m.isValid()) alive++;
        check(alive == 8, "después de recargar siguen los 8 NPCs (" + alive + ")");
        int dupes = 0;
        for (UUID u : before.values()) if (Bukkit.getEntity(u) != null && !plugin.npcEntities().storyIds().containsValue(u)) dupes++;
        check(dupes == 0, "recargar no deja NPCs duplicados (" + dupes + ")");
        // colocar un NPC a mano
        String id = plugin.npcs().ids().iterator().next();
        Location to = plugin.village().center().clone().add(2.5, 0, 2.5);
        plugin.npcEntities().setCustomSpot(id, to);
        Entity en = Bukkit.getEntity(plugin.npcEntities().storyIds().get(id));
        check(en != null && en.getLocation().distance(to) < 3, "/historia colocar mueve al NPC");
        plugin.getConfig().set("npc-lugares." + id, null);
        plugin.npcEntities().placeStoryNpcs();
    }

    private void finish() {
        if (task != null) task.cancel();
        plugin.getLogger().info(String.format(java.util.Locale.ROOT, "[PRUEBA] tick más lento: %.0f ms, ticks de más de 1 s: %d", maxTickMs, slowTicks));
        check(maxTickMs < 5000, String.format(java.util.Locale.ROOT, "el servidor nunca se congeló (tick más lento %.0f ms)", maxTickMs));
        if (fails.isEmpty()) plugin.getLogger().info("[PRUEBA] RESULTADO: TODO BIEN");
        else plugin.getLogger().severe("[PRUEBA] RESULTADO: " + fails.size() + " FALLOS");
        Bukkit.getScheduler().runTaskLater(plugin, Bukkit::shutdown, 40L);
    }
}
