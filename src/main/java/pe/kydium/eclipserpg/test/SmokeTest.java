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
                while ((en = z.getNextEntry()) != null) if (en.getName().endsWith(".png")) skins++;
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
                    if (editorDone) finish();
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
        check(fill >= 25, "hay aldeanos de relleno (" + fill + ")");
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
