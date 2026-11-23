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
                if (e.getTickDuration() > 1000) {
                    slowTicks++;
                    plugin.getLogger().info(String.format(java.util.Locale.ROOT, "[PRUEBA] tick lento: %.0f ms (etapa %d, segundo %d)", e.getTickDuration(), stage, ticks));
                }
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
        long roots = st.doc().missions.stream().filter(m -> m.root == null).count();
        check(roots >= 70 && st.doc().missions.size() >= 130, "la historia tiene " + roots + " misiones en " + st.doc().missions.size() + " etapas");
        check(st.doc().npcs.size() == 25 && plugin.npcs().ids().size() == 25, "la historia tiene 25 NPCs (" + st.doc().npcs.size() + ")");
        check(st.doc().cities.size() == 5 && plugin.cities().ids().size() == 5 && plugin.structures().def("ciudad_hornoviejo") != null,
                "la historia tiene 5 ciudades registradas como estructuras");
        check(plugin.quests().total() == st.doc().missions.size() && plugin.quests().missionCount() == roots,
                "el motor cargó todas las etapas (" + plugin.quests().total() + ")");
        int multi = 0, autos = 0, talks = 0;
        for (int i = 1; i <= plugin.quests().total(); i++) {
            var q = plugin.quests().byNumber(i);
            if (q.stages > 1 && q.stage == 1) multi++;
            if (q.auto) autos++;
            talks += q.talks.size();
        }
        check(multi >= 40 && autos >= 20 && talks >= 15, "misiones con etapas, etapas automáticas y conversaciones (" + multi + "/" + autos + "/" + talks + ")");
        // ETAPA y CIUDAD en una historia pequeña
        var rsc = new pe.kydium.eclipserpg.story.StoryParser(st.dictionary(), st.catalog()).parse(List.of(
                "HISTORIA: Etapas", "CIUDAD puerto", "  nombre: Puerto Lejano", "  tamaño: chica", "  distancia: 500-700", "  dirección: sur",
                "NPC a", "  nombre: Ana", "  skin: anciana", "NPC b", "  nombre: Beto", "  skin: herrero", "  ciudad: puerto",
                "MISIÓN Larga", "  npc: a", "  dice: Hola", "  objetivo: llega a la ciudad Puerto Lejano", "  conversa: b | Te esperaba",
                "  ETAPA Segunda", "    npc: b", "    dice: Sigue", "    objetivo: habla con Ana", "    conversa: a | ¡Volviste!",
                "  ETAPA Tercera", "    objetivo: entrega 3 pan"));
        for (var pr : rsc.problems()) plugin.getLogger().info("[PRUEBA] etapas: " + pr);
        var ms = rsc.doc().missions;
        check(rsc.errors() == 0 && ms.size() == 3 && ms.get(1).root == ms.get(0) && ms.get(2).stage == 3 && ms.get(0).stages == 3
                        && "a".equals(ms.get(2).npc) && "puerto".equals(rsc.doc().npcs.get("b").city) && ms.get(1).talks.containsKey("a")
                        && "ESTRUCTURA".equals(ms.get(0).objectives.get(0).type) && ms.get(0).objectives.get(0).targets.contains("ciudad_puerto")
                        && "sur".equals(rsc.doc().cities.get("puerto").direction),
                "ETAPA, CONVERSA y CIUDAD se leen bien");
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

    /** Paquete de texturas extra: modelos por nombre (textura:) y aspectos de armadura (aspecto:). */
    private void checkExtraPack() {
        var rp = plugin.resourcePack();
        if (rp == null) return;
        java.io.File dir = rp.extras().folder();
        dir.mkdirs();
        java.io.File zf = new java.io.File(dir, "zz-prueba.zip");
        try (var z = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(zf))) {
            z.putNextEntry(new java.util.zip.ZipEntry("pack.mcmeta"));
            z.write(("{\"pack\":{\"pack_format\":16,\"description\":\"x\"},\"overlays\":{\"entries\":[{\"directory\":\"capa\",\"formats\":[70,500],"
                    + "\"min_format\":[70,0],\"max_format\":[500,0]},{\"directory\":\"vieja\",\"formats\":[10,20],\"min_format\":[10,0],\"max_format\":[20,0]}]}}").getBytes());
            z.putNextEntry(new java.util.zip.ZipEntry("capa/assets/minecraft/items/netherite_sword.json"));
            z.write(("{\"model\":{\"type\":\"minecraft:select\",\"property\":\"minecraft:component\",\"component\":\"minecraft:custom_name\","
                    + "\"cases\":[{\"when\":\"Superboss Slayer\",\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/cit/superboss_slayer\"}}],"
                    + "\"fallback\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/netherite_sword\"}}}").getBytes());
            z.putNextEntry(new java.util.zip.ZipEntry("vieja/assets/minecraft/items/diamond_axe.json"));
            z.write(("{\"model\":{\"type\":\"minecraft:select\",\"property\":\"minecraft:component\",\"component\":\"minecraft:custom_name\","
                    + "\"cases\":[{\"when\":\"Hacha Vieja De Prueba\",\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/x\"}}]}}").getBytes());
            for (String n : new String[]{"champions_armor_1", "champions_armor_2"}) {
                z.putNextEntry(new java.util.zip.ZipEntry("capa/assets/minecraft/equipment/icons/minecraft_dungeons/" + n + ".json"));
                z.write("{\"layers\":{}}".getBytes());
            }
        } catch (Exception e) {
            fail("no se pudo crear el paquete extra de prueba: " + e);
            return;
        }
        rp.extras().scan();
        rp.rebuild();
        var sword = plugin.items().create("cazadora_de_reyes", 1);
        var legs = plugin.items().create("campeon_grebas", 1);
        var chest = plugin.items().create("campeon_pechera", 1);
        var axe = plugin.items().create("rompeyelmos", 1);
        check(rp.extras().packs().size() >= 1 && sword != null && sword.getItemMeta().hasItemModel()
                        && sword.getItemMeta().getItemModel().toString().equals("eclipse:cazadora_de_reyes"),
                "un arma con «textura:» usa el modelo del paquete extra (" + (sword == null ? "?" : sword.getItemMeta().getItemModel()) + ")");
        check(rp.extras().model("Hacha Vieja De Prueba", "diamond_axe") == null && rp.extras().model("superboss slayer", "netherite_sword") != null,
                "las capas del paquete que no son para esta versión se ignoran");
        check(legs != null && legs.getItemMeta().hasEquippable() && chest != null
                        && "minecraft:icons/minecraft_dungeons/champions_armor_2".equals(String.valueOf(legs.getItemMeta().getEquippable().getModel()))
                        && "minecraft:icons/minecraft_dungeons/champions_armor_1".equals(String.valueOf(chest.getItemMeta().getEquippable().getModel())),
                "las armaduras con «aspecto:» se ven distintas puestas (grebas _2, peto _1)");
        boolean inZip = false;
        try (var z = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(rp.bytes()))) {
            java.util.zip.ZipEntry en;
            while ((en = z.getNextEntry()) != null) if (en.getName().equals("assets/eclipse/items/cazadora_de_reyes.json")) inZip = true;
        } catch (Exception ignored) {
            // se reporta abajo
        }
        check(inZip, "el paquete del plugin trae el modelo de los objetos con textura");
        zf.delete();
        rp.extras().scan();
        rp.rebuild();
    }

    /** Penetración: atraviesa la armadura (también de jefes) y se gana con mejoras y runas. */
    private void checkPenetration() {
        check("penetracion".equals(pe.kydium.eclipserpg.items.Powers.normType("penetración")) && "cazajefes".equals(pe.kydium.eclipserpg.items.Powers.normType("cazajefes"))
                && "perforar".equals(pe.kydium.eclipserpg.items.Powers.normType("perforacion")), "existen los poderes Penetración y Cazajefes");
        var pen = plugin.penetration();
        double p10 = pen.of(null, List.of(), 10), pr = pen.of(null, List.of(new pe.kydium.eclipserpg.items.Powers.Power("penetracion", 20, 1)), 5),
                cap = pen.of(null, List.of(new pe.kydium.eclipserpg.items.Powers.Power("penetracion", 500, 1)), 10);
        check(Math.abs(p10 - 0.30) < 1e-6 && Math.abs(pr - 0.35) < 1e-6 && Math.abs(cap - 0.8) < 1e-6,
                "la penetración suma mejora y runas, con tope (" + p10 + " / " + pr + " / " + cap + ")");
        var rune = pe.kydium.eclipserpg.items.Powers.rune("penetracion:15");
        check(pe.kydium.eclipserpg.items.Powers.isRune(rune), "se crea la Runa de Penetración");
        var e = new pe.kydium.eclipserpg.shop.ShopManager.Entry();
        e.item = "runa:penetracion:10";
        var d = new pe.kydium.eclipserpg.shop.ShopManager.Entry();
        d.item = "runa:espinas:15";
        var t = new pe.kydium.eclipserpg.shop.ShopManager.Entry();
        t.item = "runa:vetas:16";
        check("runas-ataque".equals(pe.kydium.eclipserpg.shop.ShopMenu.categoryOf(plugin, e)) && "runas-defensa".equals(pe.kydium.eclipserpg.shop.ShopMenu.categoryOf(plugin, d))
                && "runas-herramienta".equals(pe.kydium.eclipserpg.shop.ShopMenu.categoryOf(plugin, t)), "la tienda de runas se ordena en ataque, defensa y herramienta");
        // un jefe con armadura recibe más daño si el golpe tiene penetración
        var w = plugin.getServer().getWorlds().get(0);
        var at = w.getSpawnLocation().clone().add(6, 1, 6);
        var boss = plugin.mobs().spawn("rondador_podrido", at);
        if (boss != null) {
            var armor = boss.getAttribute(org.bukkit.attribute.Attribute.ARMOR);
            double a = armor == null ? 0 : armor.getValue();
            check(a > 0 && a <= 10 * plugin.getConfig().getDouble("dificultad.jefes.armadura", 0.75) + 0.01, "los jefes tienen menos armadura (" + a + ")");
            boss.remove();
        }
    }

    private void checkPack() {
        checkExtraPack();
        checkPenetration();
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
            int skins = 0, mobTex = 0;
            boolean golem = false;
            try (var z = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(resp.body()))) {
                java.util.zip.ZipEntry en;
                while ((en = z.getNextEntry()) != null) {
                    if (en.getName().startsWith("assets/eclipse/textures/npc/") && en.getName().endsWith(".png")) skins++;
                    if (en.getName().startsWith("assets/minecraft/textures/entity/")) mobTex++;
                    if (en.getName().equals("assets/minecraft/textures/entity/iron_golem/iron_golem_crackiness_low.png")) golem = true;
                }
            }
            check(mobTex >= 50 && golem, "el paquete trae las texturas de Corrompidos (" + mobTex + ")");
            var gd = plugin.mobs().def("golem_corrompido");
            check(gd != null && "enemigo".equals(gd.role) && gd.startHealth < 1 && plugin.mobs().def("lobo_corrompido") != null
                    && plugin.mobs().def("golem_eclipse") != null, "existen los gólems y lobos corrompidos");
            var g = plugin.mobs().spawn("golem_corrompido", plugin.getServer().getWorlds().get(0).getSpawnLocation().clone().add(3, 1, 3));
            check(g instanceof org.bukkit.entity.IronGolem ig && !ig.isPlayerCreated() && g.getHealth() < pe.kydium.eclipserpg.mobs.MobManager.maxHealth(g) * 0.8
                    && plugin.npcEntities().combat().hostile(g), "el gólem corrompido nace agrietado y es hostil");
            if (g != null) g.remove();
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
            if (ticks > 1400) {
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
                    checkCitiesBegin();
                    checkV71();
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
                    if ((generatedDone < 6 || !structureDone || cityInst == null) && genWait++ < 120) return;
                    checkWorldEnd();
                    checkCitiesEnd();
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
        check(alive == 9, "los 9 NPCs de la aldea existen (" + alive + ")");
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
        int rooms = plugin.dungeons().roomCount(1), floors = plugin.dungeons().floorCount(1);
        plugin.getLogger().info("[PRUEBA] mazmorra: " + rooms + " salas en " + floors + " niveles de suelo, " + (b.x2() - b.x1()) + "x" + (b.z2() - b.z1()) + " bloques");
        check(rooms >= 25, "la mazmorra es enorme (" + rooms + " salas)");
        // ¿se puede llegar caminando desde la entrada hasta la sala del jefe?
        Box boss = plugin.dungeons().bossBox(1);
        DEBUG_ROOMS = plugin.dungeons().roomBoxes(1);
        check(boss != null && walkable(w, b, e, boss), "se puede llegar caminando de la entrada a la sala del jefe");
    }

    /** Búsqueda por anchura sobre lugares donde cabe un jugador: pasos de 1 arriba, caídas de hasta 3. */
    private static boolean walkable(World w, Box b, int[] e, Box goal) {
        int sx = b.x2() - b.x1() + 3, sy = b.y2() - b.y1() + 3, sz = b.z2() - b.z1() + 3;
        int ox = b.x1() - 1, oy = b.y1() - 1, oz = b.z1() - 1;
        java.util.BitSet seen = new java.util.BitSet(sx * sy * sz);
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        int ey = e[1];
        while (ey > oy && !stand(w, e[0], ey, e[2])) ey--;
        q.add(new int[]{e[0], ey, e[2]});
        while (!q.isEmpty()) {
            int[] c = q.poll();
            int idx = ((c[0] - ox) * sy + (c[1] - oy)) * sz + (c[2] - oz);
            if (c[0] < ox || c[1] < oy || c[2] < oz || c[0] >= ox + sx || c[1] >= oy + sy || c[2] >= oz + sz || seen.get(idx)) continue;
            seen.set(idx);
            if (goal.contains(c[0], c[1], c[2]) && !DEBUG_WALK) return true;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = c[0] + d[0], nz = c[2] + d[1];
                for (int dy = 1; dy >= -3; dy--) {
                    int ny = c[1] + dy;
                    if (dy == 1 && !w.getBlockAt(c[0], c[1] + 2, c[2]).isPassable()) continue;   // sin espacio para saltar
                    if (stand(w, nx, ny, nz)) {
                        q.add(new int[]{nx, ny, nz});
                        break;
                    }
                    if (!w.getBlockAt(nx, ny, nz).isPassable() || !w.getBlockAt(nx, ny + 1, nz).isPassable()) break;
                }
            }
        }
        if (DEBUG_WALK) for (Box r : DEBUG_ROOMS) {
            boolean hit = false;
            for (int x = r.x1(); x <= r.x2() && !hit; x++)
                for (int z = r.z1(); z <= r.z2() && !hit; z++)
                    for (int y = r.y1(); y <= r.y1() + 2 && !hit; y++) {
                        int idx = ((x - ox) * sy + (y - oy)) * sz + (z - oz);
                        if (seen.get(idx)) hit = true;
                    }
            Bukkit.getLogger().info("[PRUEBA] sala " + r.x1() + "," + r.y1() + "," + r.z1() + " a " + r.x2() + "," + r.y2() + "," + r.z2() + (hit ? " alcanzada" : " NO ALCANZADA"));
        }
        if (DEBUG_WALK) try (java.io.PrintWriter pw = new java.io.PrintWriter("debug-mazmorra.txt")) {
            java.util.TreeSet<Integer> ys = new java.util.TreeSet<>();
            for (Box r : DEBUG_ROOMS) ys.add(r.y1());
            for (int y : ys.descendingSet()) {
                pw.println("Y " + y + " x0=" + b.x1() + " z0=" + b.z1());
                for (int z = b.z1(); z <= b.z2(); z++) {
                    StringBuilder sb = new StringBuilder();
                    for (int x = b.x1(); x <= b.x2(); x++) {
                        boolean reached = false;
                        for (int yy = y - 1; yy <= y + 1 && !reached; yy++) {
                            int idx = ((x - ox) * sy + (yy - oy)) * sz + (z - oz);
                            if (yy >= oy && seen.get(idx)) reached = true;
                        }
                        Block f = w.getBlockAt(x, y, z), bl = w.getBlockAt(x, y - 1, z);
                        char ch = reached ? '+' : f.getType() == Material.WATER || bl.getType() == Material.WATER ? '~'
                                : bl.getType() == Material.LAVA || f.getType() == Material.LAVA ? 'L'
                                : f.getType().name().endsWith("STAIRS") ? 'S' : f.isPassable() ? (bl.getType().isSolid() ? 'o' : ' ') : '#';
                        sb.append(ch);
                    }
                    pw.println(sb);
                }
            }
        } catch (java.io.IOException ignored) {
        }
        return false;
    }

    static boolean DEBUG_WALK = Boolean.getBoolean("eclipse.debugwalk");   // -Declipse.debugwalk=true: dice qué salas no se alcanzan
    static List<Box> DEBUG_ROOMS = new ArrayList<>();

    private static boolean stand(World w, int x, int y, int z) {
        Block feet = w.getBlockAt(x, y, z), head = w.getBlockAt(x, y + 1, z), below = w.getBlockAt(x, y - 1, z);
        return (feet.isPassable() || feet.isLiquid()) && (head.isPassable() || head.isLiquid()) && feet.getType() != Material.LAVA
                && (below.getType().isSolid() || below.isLiquid() && below.getType() == Material.WATER || feet.getType().name().endsWith("STAIRS"));
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
            check(rj.get("errores").getAsInt() == 0 && rj.get("misiones").getAsInt() == plugin.story().doc().missions.size(), "el editor revisa la historia (0 errores, " + rj.get("misiones").getAsInt() + " misiones)");
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
        int stuckM = 0, totalM = 0;
        for (var i : generated) { int[] r = plugin.structures().testWave(i); stuckM += r[0]; totalM += r[1]; }
        check(totalM > 0 && stuckM == 0, "los monstruos de las estructuras aparecen en espacio libre (" + stuckM + " atrapados de " + totalM + ")");
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
        var boss = new pe.kydium.eclipserpg.quests.Quest();
        boss.id = "jefe";
        boss.boss = true;
        var bo = new pe.kydium.eclipserpg.quests.Quest.Objective();
        bo.type = pe.kydium.eclipserpg.quests.Quest.Type.MOB;
        bo.text = "Derrota al Rey de Ceniza";
        boss.objectives.add(bo);
        var give = new pe.kydium.eclipserpg.quests.Quest();
        give.id = "entrega";
        var go = new pe.kydium.eclipserpg.quests.Quest.Objective();
        go.type = pe.kydium.eclipserpg.quests.Quest.Type.ENTREGAR;
        go.text = "Entrega 20 panes";
        give.objectives.add(go);
        String br = boss == null ? "" : boss.autoReply(), gr = give == null ? "" : give.autoReply();
        plugin.getLogger().info("[PRUEBA] respuesta automática de jefe: " + br + " | de entrega: " + gr);
        check(br.contains("Derrotaré") && gr.contains("Te traeré"), "si no tiene respuesta, el jugador contesta según el objetivo");
        // misiones variadas: resistir, tesoro, acertijo y tiempo
        int surv = 0, tre = 0, rid = 0, timed = 0;
        for (int i = 1; i <= 400; i++) {
            var q = plugin.quests().byNumber(i);
            if (q == null) break;
            for (var o : q.objectives) {
                if (o.type == pe.kydium.eclipserpg.quests.Quest.Type.SOBREVIVIR) surv++;
                if (o.type == pe.kydium.eclipserpg.quests.Quest.Type.TESORO) tre++;
                if (o.type == pe.kydium.eclipserpg.quests.Quest.Type.RESPONDER && q.riddle != null && q.answer != null && !q.wrong.isEmpty()) rid++;
            }
            if (q.timeLimit > 0) timed++;
        }
        check(surv >= 5 && tre >= 3 && rid >= 5 && timed >= 4, "hay misiones de resistir, tesoro, acertijo y con tiempo (" + surv + "/" + tre + "/" + rid + "/" + timed + ")");
        var rs = new pe.kydium.eclipserpg.story.StoryParser(plugin.story().dictionary(), plugin.story().catalog()).parse(List.of(
                "HISTORIA: Prueba", "NPC a", "  nombre: Ana", "MISIÓN Uno", "  npc: a", "  objetivo: resiste 2 minutos",
                "  oleadas: zombi_corrompido 3 cada 15", "  tiempo: 4 min", "  acertijo: ¿Qué es?", "  correcta: Esto", "  incorrecta: Aquello / Lo otro"));
        for (var pr : rs.problems()) plugin.getLogger().info("[PRUEBA] misión variada: " + pr);
        var mm = rs.doc().missions.isEmpty() ? null : rs.doc().missions.get(0);
        check(rs.errors() == 0 && mm != null && mm.timeLimit == 240 && mm.waveEvery == 15 && mm.waves.size() == 1 && mm.wrong.size() == 2
                && mm.objectives.get(0).amount == 120, "se leen tiempo, oleadas, acertijo y «resiste N minutos»");
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
        // los diálogos de un paquete nuevo se cargan recién al reiniciar: en un mundo de prueba recién creado solo se revisa el archivo
        java.io.File menu = new java.io.File(pe.kydium.eclipserpg.menu.HotkeyMenu.levelDir(), "datapacks/eclipse_atajos/data/eclipse/dialog/menu.json");
        boolean dlg = menu.exists();
        if (pe.kydium.eclipserpg.menu.HotkeyMenu.presentAtStartup) try {
            var reg = io.papermc.paper.registry.RegistryAccess.registryAccess().getRegistry(io.papermc.paper.registry.RegistryKey.DIALOG);
            dlg = reg.get(net.kyori.adventure.key.Key.key("eclipse", "menu")) != null;
        } catch (Throwable t) {
            plugin.getLogger().info("[PRUEBA] registro de diálogos: " + t);
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
        start.remove(npc.getUniqueId()); // vuelve a su casa al levantarse: no cuenta para la prueba de caminar
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
        check(r.errors() == 0 && plugin.quests().total() == plugin.story().doc().missions.size(), "/historia recargar funciona");
        int alive = 0;
        for (UUID u : plugin.npcEntities().storyIds().values()) if (Bukkit.getEntity(u) instanceof Mannequin m && m.isValid()) alive++;
        check(alive == 9, "después de recargar siguen los 9 NPCs (" + alive + ")");
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

    // ---------------------------------------------------------------- ciudades y peligro

    private pe.kydium.eclipserpg.structure.StructureManager.Inst cityInst;

    private void checkCitiesBegin() {
        check(!plugin.cities().isBuilt("hornoviejo"), "Hornoviejo no existe hasta que la historia lleva allí");
        plugin.structures().build("ciudad_hornoviejo", null, inst -> {
            cityInst = inst;
            if (inst != null) plugin.cities().placeNpcs("hornoviejo", inst);
        });
    }

    private void checkV71() {
        // íconos dibujados en vez de emojis
        pe.kydium.eclipserpg.util.Icons.enabled = true;
        String t = pe.kydium.eclipserpg.util.Icons.apply("<gold>☀ Soles 📖 ⚔");
        check(t.contains("eclipse:iconos") && !t.contains("📖") && !t.contains("☀"), "los emojis se cambian por íconos dibujados");
        String fj = pe.kydium.eclipserpg.util.Icons.fontJson();
        check(new com.google.gson.JsonParser().parse(fj).getAsJsonObject().getAsJsonArray("providers").size() == pe.kydium.eclipserpg.util.Icons.NAMES.size(),
                "la letra de íconos es JSON válido (" + pe.kydium.eclipserpg.util.Icons.NAMES.size() + " íconos)");
        int missing = 0;
        for (String n : pe.kydium.eclipserpg.util.Icons.NAMES) if (plugin.getResource("motor/hud/iconos/" + n + ".png") == null) missing++;
        check(missing == 0 && plugin.getResource("motor/hud/caja.png") != null && plugin.getResource("motor/hud/barra_vacia.png") != null,
                "están los dibujos de íconos, el cuadro del panel y la barra invisible");
        // los Corrompidos no se pegan entre ellos
        World w = Bukkit.getWorlds().get(0);
        Location c = plugin.village().center().clone().add(60, 0, 60);
        c.setY(w.getHighestBlockYAt(c) + 1);
        LivingEntity a = (LivingEntity) w.spawnEntity(c, org.bukkit.entity.EntityType.ZOMBIE);
        LivingEntity b = (LivingEntity) w.spawnEntity(c.clone().add(1, 0, 0), org.bukkit.entity.EntityType.SKELETON);
        LivingEntity g = plugin.mobs().spawn("golem_corrompido", c.clone().add(-1, 0, 0));
        plugin.mobs().corrupt(a, 2);
        plugin.mobs().corrupt(b, 2);
        double ha = a.getHealth(), hb = b.getHealth();
        a.damage(5, b);
        b.damage(5, a);
        boolean golemOk = true;
        if (g != null) {
            double hg = g.getHealth();
            g.damage(5, a);
            a.damage(5, g);
            golemOk = g.getHealth() == hg;
        }
        check(a.getHealth() == ha && b.getHealth() == hb && golemOk, "los Corrompidos y el gólem corrompido no se hacen daño entre ellos");
        a.remove();
        b.remove();
        if (g != null) g.remove();
        check(plugin.items().create("esencia_corrupta", 1) != null && plugin.items().create("corazon_corrupto", 1) != null,
                "existen la Esencia y el Corazón Corrupto (premios por matar Corrompidos)");
        check(plugin.shops().ids().contains("trueque") && plugin.npcs().def("sabina") != null, "Sabina tiene su tienda de trueque");
        int effects = 0;
        for (var st : pe.kydium.eclipserpg.rpg.Stat.values()) if (!plugin.levels().statEffects(st, 3).isEmpty()) effects++;
        check(pe.kydium.eclipserpg.rpg.Stat.values().length == 8 && effects == 8, "hay 8 atributos estilo Elden Ring y todos explican su efecto (" + effects + ")");
        check(plugin.levels().perPoint(pe.kydium.eclipserpg.rpg.Stat.INTELIGENCIA) > 0 && plugin.levels().maxPoints(pe.kydium.eclipserpg.rpg.Stat.FE) > 0,
                "los atributos nuevos tienen valores aunque el config.yml sea viejo");
        {
            World tw = Bukkit.getWorlds().get(0);
            Location tl = plugin.village().center().clone().add(30, 0, 30);
            tl.setY(tw.getHighestBlockYAt(tl) + 1);
            var z = (org.bukkit.entity.Zombie) tw.spawnEntity(tl, org.bukkit.entity.EntityType.ZOMBIE);
            z.damage(4);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                var n = z.customName();
                String txt = n == null ? "" : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(n);
                check(txt.contains("❤") && txt.contains("/20") && z.isCustomNameVisible(), "al golpear un monstruo se ve su vida (" + txt + ")");
                z.setHealth(0);
            }, 2L);
        }
        {
            World tw = Bukkit.getWorlds().get(0);
            Location base = plugin.village().center().clone().add(60, 0, -60);
            base.setY(tw.getHighestBlockYAt(base) + 2);
            java.util.Set<org.bukkit.entity.EntityType> types = new java.util.HashSet<>();
            int bosses = 0, capped = 0, spawned = 0;
            java.util.Set<org.bukkit.entity.EntityType> common = new java.util.HashSet<>();
            for (String id : plugin.mobs().ids()) {
                var d = plugin.mobs().def(id);
                if (d != null && !d.boss) common.add(d.type);
            }
            boolean clash = false;
            for (String id : plugin.mobs().ids()) {
                var d = plugin.mobs().def(id);
                if (d == null || !d.boss) continue;
                bosses++;
                types.add(d.type);
                if (common.contains(d.type)) clash = true;
                var le = plugin.mobs().spawn(id, base.clone().add(bosses * 6, 0, 0));
                if (le == null) continue;
                spawned++;
                double before = le.getHealth();
                le.damage(100000);
                if (!le.isDead() && le.getHealth() >= before - le.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() * 0.051) capped++;
                le.remove();
            }
            check(bosses == 10 && types.size() == 10 && !clash, "los 10 jefes son de criaturas distintas y ningún mob común se les parece (" + types.size() + ")");
            check(spawned == 10 && capped == 10, "ningún golpe le quita a un jefe más del 5 % de su vida (" + capped + "/" + spawned + ")");
        }
        {
            // tiendas v7.7: vender lo que sea, armaduras del día, categorías, mejoras hasta +10
            var sm = plugin.shops();
            var vals = sm.values();
            double dia = vals.value(new org.bukkit.inventory.ItemStack(Material.DIAMOND, 2)), dirt = vals.value(new org.bukkit.inventory.ItemStack(Material.DIRT, 64));
            var bread = vals.value(new org.bukkit.inventory.ItemStack(Material.BREAD, 8));
            check(dia >= 30 && dirt < 3 && bread < 20 * 0.41, "el mercader paga por cualquier cosa, sin dar más de lo que cuesta comprarla (2 diamantes " + (int) dia + ", 64 tierra " + String.format(java.util.Locale.ROOT, "%.1f", dirt) + ", 8 panes " + String.format(java.util.Locale.ROOT, "%.1f", bread) + ")");
            var solar = plugin.items().create("fragmento_solar_1", 1);
            var bag = plugin.items().create("mochila_pequena", 1);
            check(vals.refuse(solar) != null && vals.refuse(bag) != null, "no compra objetos de la historia ni mochilas");
            var gen = sm.shop("general");
            var daily = sm.dailyArmor(gen);
            var again = sm.dailyArmor(gen);
            boolean same = daily.size() == again.size();
            for (int i = 0; same && i < daily.size(); i++) same = sm.build(daily.get(i)).isSimilar(sm.build(again.get(i)));
            java.util.Set<String> names = new java.util.HashSet<>();
            for (var de : daily) { var bi = sm.build(de); names.add(bi.getItemMeta().hasDisplayName() ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(bi.getItemMeta().displayName()) : bi.getType().name()); }
            check(daily.size() == 8 && same && names.size() == 8, "Pip trae 8 novedades hoy (armaduras y curiosidades), iguales para todos (" + names + ")");
            int built = 0, total = 0, potions = 0, runes = 0;
            for (String sid : List.of("herreria", "alquimia", "trueque")) {
                for (var de : sm.dailyArmor(sm.shop(sid))) {
                    total++;
                    var it2 = sm.build(de);
                    if (it2 != null) built++;
                    if (it2 != null && it2.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta pm && pm.hasCustomEffects()) potions++;
                    if (it2 != null && !pe.kydium.eclipserpg.items.Powers.runesOf(it2.getItemMeta()).isEmpty()) runes++;
                }
            }
            check(total == 14 && built == 14 && potions >= 3, "Bruno, Fray Odón y Sabina también traen novedades cada día (" + built + "/" + total + ", " + potions + " elixires, " + runes + " con poderes)");
            java.util.Set<String> cats = new java.util.TreeSet<>();
            for (var e2 : sm.visibleEntries(gen)) cats.add(pe.kydium.eclipserpg.shop.ShopMenu.categoryOf(plugin, e2));
            check(cats.size() >= 4 && cats.contains("novedades") && cats.contains("comida"), "la tienda de Pip está ordenada por categorías " + cats);
            var sword = new org.bukkit.inventory.ItemStack(Material.DIAMOND_SWORD);
            for (int i = 0; i < 10; i++) plugin.items().upgrade(sword, 10);
            double extra = 0;
            for (var m : sword.getItemMeta().getAttributeModifiers(org.bukkit.attribute.Attribute.ATTACK_DAMAGE))
                if (m.getKey().getKey().equals("mejora")) extra += m.getAmount();
            var chest = new org.bukkit.inventory.ItemStack(Material.IRON_CHESTPLATE);
            for (int i = 0; i < 9; i++) plugin.items().upgrade(chest, 10);
            double armor = 0, hp = 0;
            for (var m : chest.getItemMeta().getAttributeModifiers(org.bukkit.attribute.Attribute.ARMOR)) armor += m.getAmount();
            var hpm = chest.getItemMeta().getAttributeModifiers(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (hpm != null) for (var m : hpm) hp += m.getAmount();
            check(plugin.items().upgradeLevel(sword) == 10 && extra == 13.5 && armor >= 6 + 4.5 && hp == 2,
                    "las mejoras llegan a +10 en armas y armaduras (+" + extra + " daño, armadura " + armor + ", +" + hp + " vida)");
            check(plugin.items().create("fragmento_mejora_superior", 1) != null && plugin.items().create("fragmento_mejora_ancestral", 1) != null
                    && "fragmento_mejora_ancestral".equals(pe.kydium.eclipserpg.items.ItemManager.fragmentFor(9)), "existen los fragmentos Superior y Ancestral");
        }
        check(pe.kydium.eclipserpg.menu.Menu.isFiller(pe.kydium.eclipserpg.menu.Menu.filler())
                && !pe.kydium.eclipserpg.menu.Menu.isFiller(new org.bukkit.inventory.ItemStack(Material.BLACK_STAINED_GLASS_PANE)),
                "los cristales de relleno de los menús se reconocen (y se quitan si llegan al inventario)");
        {
            var sw = new org.bukkit.inventory.ItemStack(Material.IRON_SWORD);
            int r0 = plugin.powers().maxRunes(sw);
            for (int i = 0; i < 10; i++) plugin.items().upgrade(sw, 10);
            check(r0 == 5 && plugin.powers().maxRunes(sw) == 7, "se pueden poner varias runas: 5 por objeto y hasta 7 a +10 (" + r0 + " → " + plugin.powers().maxRunes(sw) + ")");
        }
        var vida = Bukkit.getScoreboardManager().getMainScoreboard().getObjective("eclipse_vida");
        check(vida != null && vida.getDisplaySlot() == org.bukkit.scoreboard.DisplaySlot.BELOW_NAME, "la vida se muestra debajo del nombre de los jugadores");
    }

    private void checkCitiesEnd() {
        var inst = cityInst;
        check(inst != null, "se construyó la ciudad Hornoviejo");
        if (inst == null) return;
        Location c = plugin.village().center();
        double dist = Math.hypot(inst.x - c.getX(), inst.z - c.getZ());
        check(dist >= 500 && inst.x > c.getX(), "Hornoviejo está lejos, al este de la aldea (" + (int) dist + " bloques)");
        check(inst.places.size() >= 5, "la ciudad guardó lugares para NPCs (" + inst.places.size() + ")");
        int ok = 0;
        for (String id : List.of("greta", "tobias", "ilse")) {
            Location l = plugin.npcEntities().spotOf(id);
            if (l != null && Math.hypot(l.getX() - inst.x, l.getZ() - inst.z) < Math.max(inst.rx, inst.rz) + 8) ok++;
        }
        check(ok == 3, "los 3 NPCs de Hornoviejo viven en la ciudad (" + ok + "/3)");
        World w = Bukkit.getWorld(inst.world);
        check(w != null && plugin.cities().inside(new Location(w, inst.x, inst.y + 2, inst.z), 0), "la ciudad está protegida como la aldea");
        // piedras de viaje: aldea y Hornoviejo
        if (w != null) {
            w.getChunkAt(inst.x >> 4, inst.z >> 4);
            w.getChunkAt(c.getBlockX() >> 4, c.getBlockZ() >> 4);
        }
        plugin.waystones().ensureAll();
        var stones = plugin.waystones().all();
        int lode = 0;
        for (var st : stones) {
            World sw = Bukkit.getWorld(st.world());
            if (sw != null && sw.getBlockAt(st.x(), st.y(), st.z()).getType() == org.bukkit.Material.LODESTONE) lode++;
            plugin.getLogger().info("[PRUEBA] piedra de viaje " + st.id() + " en " + st.x() + " " + st.y() + " " + st.z());
        }
        boolean both = stones.stream().anyMatch(st -> st.id().equals("aldea")) && stones.stream().anyMatch(st -> st.id().equals("hornoviejo"));
        check(both && lode == stones.size(), "hay piedras de viaje en la aldea y en Hornoviejo (" + lode + "/" + stones.size() + ")");
        if (both) {
            var a1 = stones.stream().filter(st -> st.id().equals("aldea")).findFirst().get();
            var a2 = stones.stream().filter(st -> st.id().equals("hornoviejo")).findFirst().get();
            long cost = plugin.waystones().cost(a1, a2);
            check(cost >= 5 && cost < 200, "viajar de la aldea a Hornoviejo cuesta algo razonable (" + cost + " ☀)");
        }
        if (both) {
            var a1 = stones.stream().filter(st -> st.id().equals("aldea")).findFirst().get();
            World sw = Bukkit.getWorld(a1.world());
            sw.getBlockAt(a1.x(), a1.y(), a1.z()).setType(org.bukkit.Material.AIR);
            plugin.waystones().ensureAll();
            check(sw.getBlockAt(a1.x(), a1.y(), a1.z()).getType() == org.bukkit.Material.LODESTONE, "si la piedra de viaje desaparece, se vuelve a poner sola");
        }
        var golem = w == null ? null : plugin.world().spawnMob("iron_golem", new Location(w, inst.x + 0.5, w.getMinHeight() + 6, inst.z + 0.5));
        check(golem == null || golem.getLocation().getBlock().isPassable() && golem.getLocation().clone().add(0, 2, 0).getBlock().isPassable(),
                "un gólem que sale en un hueco bajo se mueve a donde quepa o no aparece");
        if (golem != null) golem.remove();
        int traders = w == null ? 0 : plugin.minimap().traders(w).size();
        check(traders >= 5, "el mapa conoce a los comerciantes (" + traders + ")");
        int th = plugin.structures().threat(inst, plugin.structures().def("fortaleza"));
        check(th >= 2, "el peligro sube con la distancia (" + th + " ☠ a " + (int) dist + " bloques)");
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
