package pe.kydium.eclipserpg.npc;

import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.quests.QuestManager;
import pe.kydium.eclipserpg.util.Keys;
import pe.kydium.eclipserpg.util.Txt;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * NPCs con forma de persona (maniquíes de Minecraft) con skins propias según su rol,
 * que caminan por la aldea. Los de la historia pasean cerca de su puesto, se detienen
 * a mirarte y muestran "?" encima cuando tienen misión; los de relleno recorren la aldea.
 */
public final class NpcEntities implements Listener {

    private enum Mark {
        NEW("<yellow><bold>?", QuestManager.State.AVAILABLE),
        READY("<green><bold>✔", QuestManager.State.READY),
        TALK("<aqua><bold>?", QuestManager.State.TALK_TARGET),
        LOCKED("<gray><bold>?", QuestManager.State.LOCKED);

        final String text;
        final QuestManager.State state;

        Mark(String text, QuestManager.State state) {
            this.text = text;
            this.state = state;
        }
    }

    /** Estado de paseo de un NPC. */
    static final class Walk {
        double homeX, homeY, homeZ;
        double radius;
        double speed;
        boolean story;
        double tx, tz;
        boolean hasTarget;
        int pause;
        float yaw;
    }

    private static final String[] NAMES = {"Rosa", "Tomás", "Lucía", "Mateo", "Carmen", "Andrés", "Elena", "Julián",
            "Marta", "Diego", "Sofía", "Ramón", "Valeria", "Hugo", "Benito", "Paula", "Óscar", "Nina", "Gael", "Abril",
            "Ernesto", "Lola", "Simón", "Clara", "Iván", "Rocío", "Bruno", "Inés"};

    private static final Map<String, String> ROLE_TITLE = Map.ofEntries(
            Map.entry("campesino", "Campesino"), Map.entry("campesina", "Campesina"), Map.entry("pescador", "Pescador"),
            Map.entry("pastora", "Pastora"), Map.entry("cantero", "Cantero"), Map.entry("monje", "Monje"),
            Map.entry("carnicero", "Carnicero"), Map.entry("arquero", "Arquero"), Map.entry("aprendiz", "Aprendiz de herrero"),
            Map.entry("curtidor", "Curtidor"), Map.entry("guardia", "Guardia del Alba"), Map.entry("erudito", "Erudito"),
            Map.entry("aldeano", "Aldeano"), Map.entry("aldeana", "Aldeana"), Map.entry("nino", "Niño"), Map.entry("nina", "Niña"));

    private static final Map<String, List<String>> CHATTER = Map.of(
            "guardia", List.of("Mantén los ojos abiertos, viajero.", "Nada pasa por esta puerta sin que lo vea.", "Dicen que de noche algo ronda los bosques."),
            "nino", List.of("¿Quieres jugar a las escondidas?", "¡Un día seré un héroe como tú!", "Mi abuela dice que no salga de noche."),
            "nina", List.of("¡Mira, encontré una flor!", "¿Es verdad que viste un monstruo?", "¡Corre, que te pillo!"),
            "monje", List.of("Que la luz del alba te guíe.", "El eclipse no es castigo, es advertencia.", "Reza antes de bajar a las criptas."),
            "carnicero", List.of("Carne fresca mañana, si los lobos no llegan antes.", "¿Tienes hambre? Vuelve más tarde."),
            "pescador", List.of("Los peces están raros desde el eclipse.", "Una vez saqué un pez con ojos rojos..."),
            "default", List.of("Buen día, viajero.", "La Abuela Tula sabe más de lo que cuenta.", "¿Has visto el cielo últimamente?",
                    "Bruno hace las mejores espadas de la región.", "Pip vende de todo… a precio de oro.", "Que el eclipse no te encuentre de noche.",
                    "Hace tiempo que no llegaba nadie nuevo a la aldea.", "Dicen que bajo la tierra duermen cosas antiguas."));

    private static final boolean TEST = Boolean.getBoolean("eclipse.prueba");
    private final EclipseRPG plugin;
    private final Map<String, UUID> npcs = new HashMap<>();
    private final Set<UUID> fillers = new HashSet<>();
    private final Map<UUID, Walk> walks = new HashMap<>();
    private final Map<String, Map<Mark, UUID>> marks = new HashMap<>();

    private final Combat combat;

    public NpcEntities(EclipseRPG plugin) {
        this.plugin = plugin;
        this.combat = new Combat(plugin, this);
    }

    public Combat combat() {
        return combat;
    }

    /** ¿Este maniquí pelea como guardián? (guardias de la aldea o NPC con "combate: guardián") */
    /** Entidad del NPC de la historia (null si no está en el mundo). */
    public Entity entityOf(String id) {
        UUID u = id == null ? null : npcs.get(id);
        return u == null ? null : Bukkit.getEntity(u);
    }

    public boolean isNpcOrFiller(Entity e) {
        return npcId(e) != null || fillerRole(e) != null;
    }

    /** Casa del NPC (donde vuelve al reaparecer). */
    Location homeOf(LivingEntity le) {
        Walk w = walks.get(le.getUniqueId());
        if (w != null) return new Location(le.getWorld(), w.homeX, w.homeY, w.homeZ, (float) w.yaw, 0);
        int[] h = le.getPersistentDataContainer().get(Keys.HOME, PersistentDataType.INTEGER_ARRAY);
        return h != null && h.length == 3 ? new Location(le.getWorld(), h[0] / 10.0, h[1] / 10.0, h[2] / 10.0) : null;
    }

    public boolean isGuard(Entity e) {
        if (!combat.guardsFight()) return false;
        if ("guardia".equals(fillerRole(e))) return true;
        String id = npcId(e);
        NpcManager.NpcDef d = id == null ? null : plugin.npcs().def(id);
        return d != null && "guardian".equals(d.combat);
    }

    private boolean isCoward(Entity e) {
        String role = fillerRole(e);
        if (role != null) return combat.fleeEnabled() && !"guardia".equals(role);
        String id = npcId(e);
        NpcManager.NpcDef d = id == null ? null : plugin.npcs().def(id);
        return d != null && "cobarde".equals(d.combat);
    }

    /** Vuelve a poner el texto de debajo del nombre (después de estar herido). */
    void restoreDescription(LivingEntity le) {
        String role = fillerRole(le);
        if (role != null) {
            describe(le, ROLE_TITLE.getOrDefault(role, "Aldeano"));
            return;
        }
        String id = npcId(le);
        NpcManager.NpcDef d = id == null ? null : plugin.npcs().def(id);
        describe(le, d == null || d.role == null ? "" : d.role);
    }

    public void load() {
        combat.load();
        npcs.clear();
        fillers.clear();
        var sec = plugin.getConfig().getConfigurationSection("npc-entidades");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                try {
                    npcs.put(id, UUID.fromString(sec.getString(id, "")));
                } catch (IllegalArgumentException ignored) {
                    // uuid inválido
                }
            }
        }
        for (String s : plugin.getConfig().getStringList("relleno-entidades")) {
            try {
                fillers.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
                // uuid inválido
            }
        }
    }

    private void saveFillers() {
        List<String> l = new ArrayList<>();
        for (UUID u : fillers) l.add(u.toString());
        plugin.getConfig().set("relleno-entidades", l);
    }

    // ---------- Crear maniquíes ----------

    private static int[] ints(UUID u) {
        long m = u.getMostSignificantBits(), l = u.getLeastSignificantBits();
        return new int[]{(int) (m >> 32), (int) m, (int) (l >> 32), (int) l};
    }

    private boolean slim(String skin) {
        return plugin.story().slim(skin);
    }

    /** Invoca un maniquí con una skin del paquete de recursos (assets/eclipse/textures/npc/&lt;skin&gt;.png). */
    private LivingEntity summon(Location at, String skin) {
        World w = at.getWorld();
        if (w == null) return null;
        UUID id = UUID.randomUUID();
        int[] a = ints(id);
        String nbt = String.format(Locale.ROOT,
                "{UUID:[I;%d,%d,%d,%d],profile:{texture:\"eclipse:npc/%s\",model:\"%s\"},immovable:1b,Invulnerable:1b,"
                        + "PersistenceRequired:1b,NoGravity:1b,Silent:1b,Rotation:[%.1ff,0f]}",
                a[0], a[1], a[2], a[3], skin, slim(skin) ? "slim" : "wide", at.getYaw());
        String cmd = String.format(Locale.ROOT, "execute in %s run summon minecraft:mannequin %.3f %.3f %.3f %s",
                w.getKey().asString(), at.getX(), at.getY(), at.getZ(), nbt);
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        } catch (Exception e) {
            plugin.getLogger().warning("No se pudo crear el NPC: " + e.getMessage());
            return null;
        }
        Entity e = Bukkit.getEntity(id);
        if (!(e instanceof LivingEntity le)) {
            plugin.getLogger().warning("El servidor no creó el maniquí (¿versión sin maniquíes?). Comando: " + cmd);
            return null;
        }
        le.setPersistent(true);
        le.setRemoveWhenFarAway(false);
        le.setInvulnerable(true);
        le.setSilent(true);
        le.setGravity(false);
        le.setCollidable(false);
        if (le instanceof Mannequin m) m.setImmovable(true);
        le.getPersistentDataContainer().set(Keys.HOME, PersistentDataType.INTEGER_ARRAY,
                new int[]{(int) Math.floor(at.getX() * 10), (int) Math.floor(at.getY() * 10), (int) Math.floor(at.getZ() * 10)});
        return le;
    }

    private static void hold(LivingEntity le, Material main, Material off) {
        EntityEquipment eq = le.getEquipment();
        if (eq == null) return;
        if (main != null) eq.setItemInMainHand(new ItemStack(main));
        if (off != null) eq.setItemInOffHand(new ItemStack(off));
    }

    private static void describe(LivingEntity le, String text) {
        if (le instanceof Mannequin m) m.setDescription(Txt.mm("<gray>" + text));
    }

    /** Crea (o recrea) los NPCs de la historia en sus puestos. */
    public void spawnAll(Map<String, Location> spots) {
        for (Map.Entry<String, Location> e : spots.entrySet()) spawn(e.getKey(), e.getValue());
        plugin.saveConfig();
    }

    public void spawn(String id, Location at) {
        NpcManager.NpcDef def = plugin.npcs().def(id);
        if (def == null || at.getWorld() == null) return;
        if (!at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            // lejos (otra ciudad): se carga ese pedazo del mundo y luego se crea
            at.getWorld().getChunkAtAsync(at).thenAccept(c -> Bukkit.getScheduler().runTask(plugin, () -> {
                spawn(id, at);
                plugin.saveConfig();
            }));
            return;
        }
        despawn(id);
        String skin = def.skin == null ? "aldeano_1" : def.skin;
        LivingEntity le = summon(safeSpot(at), skin);
        if (le == null) return;
        le.customName(Txt.mm(def.name));
        le.setCustomNameVisible(true);
        describe(le, def.role == null ? "" : def.role);
        le.getPersistentDataContainer().set(Keys.NPC, PersistentDataType.STRING, id);
        hold(le, mat(def.hand), mat(def.offhand));
        npcs.put(id, le.getUniqueId());
        plugin.getConfig().set("npc-entidades." + id, le.getUniqueId().toString());
    }

    private static Material mat(String id) {
        return id == null ? null : Material.matchMaterial(id);
    }

    /** Quita el maniquí de un NPC de la historia (si está cargado). */
    public void despawn(String id) {
        UUID old = npcs.remove(id);
        if (old != null) {
            Entity e = Bukkit.getEntity(old);
            if (e != null) e.remove();
            walks.remove(old);
        }
        removeMarks(id);
        plugin.getConfig().set("npc-entidades." + id, null);
    }

    /**
     * Pone a todos los NPCs de la historia en su lugar: primero el que fijaste con /historia colocar,
     * luego el "lugar:" de historia.txt y si no, el siguiente lugar libre de la aldea.
     */
    public int placeStoryNpcs() {
        var cfg = plugin.getConfig();
        World w = Bukkit.getWorld(cfg.getString("aldea.mundo", "world"));
        if (w == null || !cfg.contains("aldea.centro.x")) return 0;
        // NPCs que ya no están en la historia
        for (String id : new ArrayList<>(npcs.keySet())) if (plugin.npcs().def(id) == null) despawn(id);
        // aldeas construidas con versiones anteriores guardaban los puestos con el nombre del NPC
        Map<String, String> legacy = Map.of("casa", "tula", "cuartel", "ines", "herreria", "bruno", "tienda", "pip",
                "biblioteca", "odon", "mapas", "lia", "torre", "velkar", "tienda_negra", "errante");
        for (Map.Entry<String, String> e : legacy.entrySet()) {
            String old = "aldea.puestos." + e.getValue(), now = "aldea.lugares." + e.getKey();
            if (cfg.contains(old + ".x") && !cfg.contains(now + ".x")) cfg.set(now, cfg.getConfigurationSection(old).getValues(false));
        }
        Map<String, Location> slots = new java.util.LinkedHashMap<>();
        for (String slot : pe.kydium.eclipserpg.story.StoryManager.PLACES) {
            String k = "aldea.lugares." + slot;
            if (cfg.contains(k + ".x")) slots.put(slot, new Location(w, cfg.getDouble(k + ".x"), cfg.getDouble(k + ".y"),
                    cfg.getDouble(k + ".z"), (float) cfg.getDouble(k + ".yaw"), 0f));
        }
        Set<String> used = new HashSet<>();
        Map<String, Location> spots = new java.util.LinkedHashMap<>();
        List<NpcManager.NpcDef> pending = new ArrayList<>();
        for (String id : plugin.npcs().ids()) {
            NpcManager.NpcDef def = plugin.npcs().def(id);
            String k = "npc-lugares." + id;
            if (def.city != null && !cfg.contains(k + ".x")) continue;   // vive en otra ciudad que aún no existe
            if (cfg.contains(k + ".x")) {
                World cw = Bukkit.getWorld(cfg.getString(k + ".mundo", w.getName()));
                spots.put(id, new Location(cw == null ? w : cw, cfg.getDouble(k + ".x"), cfg.getDouble(k + ".y"), cfg.getDouble(k + ".z"),
                        (float) cfg.getDouble(k + ".yaw"), 0f));
            } else if (def.place != null && slots.containsKey(def.place)) {
                Location l = slots.get(def.place).clone();
                if (!used.add(def.place)) l.add(1.5, 0, 0);   // dos NPCs en el mismo lugar: uno al lado del otro
                spots.put(id, l);
            } else pending.add(def);
        }
        for (NpcManager.NpcDef def : pending) {
            String free = null;
            for (String slot : slots.keySet()) if (!used.contains(slot)) {
                free = slot;
                break;
            }
            if (free != null) {
                used.add(free);
                spots.put(def.id, slots.get(free));
            } else {
                Location c = plugin.items().campLocation();
                double a = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
                spots.put(def.id, c.clone().add(Math.cos(a) * 6, 0, Math.sin(a) * 6));
            }
        }
        spawnAll(spots);
        plugin.getConfig().set("npc-firma", signature());
        return spots.size();
    }

    /** Dónde está (o vive) un NPC aunque su pedazo del mundo no esté cargado. */
    public Location spotOf(String id) {
        Entity e = entityOf(id);
        if (e != null) return e.getLocation();
        var cfg = plugin.getConfig();
        String k = "npc-lugares." + id;
        if (!cfg.contains(k + ".x")) return null;
        World w = Bukkit.getWorld(cfg.getString(k + ".mundo", "world"));
        return w == null ? null : new Location(w, cfg.getDouble(k + ".x"), cfg.getDouble(k + ".y"), cfg.getDouble(k + ".z"));
    }

    /** Guarda la posición elegida para un NPC (comando /historia colocar). */
    public void setCustomSpot(String id, Location at) {
        String k = "npc-lugares." + id;
        var cfg = plugin.getConfig();
        cfg.set(k + ".mundo", at.getWorld().getName());
        cfg.set(k + ".x", at.getX());
        cfg.set(k + ".y", at.getY());
        cfg.set(k + ".z", at.getZ());
        cfg.set(k + ".yaw", (double) at.getYaw());
        spawn(id, at);
        plugin.saveConfig();
    }

    /** Elimina los NPCs de relleno que existan. */
    public void clearFillers() {
        for (UUID u : fillers) {
            Entity e = Bukkit.getEntity(u);
            if (e != null) e.remove();
            walks.remove(u);
        }
        fillers.clear();
        saveFillers();
    }

    /** Rol de relleno a partir del "oficio" del plano de la aldea. */
    public static String roleFor(String prof) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (prof == null) {
            String[] any = {"aldeano", "aldeana", "aldeano", "aldeana", "nino", "nina"};
            return any[r.nextInt(any.length)];
        }
        return switch (prof) {
            case "farmer" -> r.nextBoolean() ? "campesino" : "campesina";
            case "librarian", "cartographer" -> "erudito";
            case "fisherman" -> "pescador";
            case "shepherd" -> "pastora";
            case "mason" -> "cantero";
            case "cleric" -> "monje";
            case "butcher" -> "carnicero";
            case "fletcher" -> "arquero";
            case "toolsmith", "armorer", "weaponsmith" -> "aprendiz";
            case "leatherworker" -> "curtidor";
            case "guard" -> "guardia";
            default -> "aldeano";
        };
    }

    public void spawnFiller(String role, Location at) {
        List<String> opts = new ArrayList<>();
        for (String k : plugin.story().skins().keySet()) if (k.startsWith(role + "_")) opts.add(k);
        if (opts.isEmpty()) opts.add("aldeano_1");
        String skin = opts.get(ThreadLocalRandom.current().nextInt(opts.size()));
        at.setYaw(ThreadLocalRandom.current().nextFloat() * 360f - 180f);
        LivingEntity le = summon(safeSpot(at), skin);
        if (le == null) return;
        boolean kid = role.equals("nino") || role.equals("nina");
        String name = NAMES[ThreadLocalRandom.current().nextInt(NAMES.length)];
        le.customName(Txt.mm("<white>" + name));
        le.setCustomNameVisible(false);
        describe(le, ROLE_TITLE.getOrDefault(role, "Aldeano"));
        if (kid) {
            var sc = le.getAttribute(Attribute.SCALE);
            if (sc != null) sc.setBaseValue(0.62);
        }
        switch (role) {
            case "campesino", "campesina" -> hold(le, Material.IRON_HOE, null);
            case "pescador" -> hold(le, Material.FISHING_ROD, null);
            case "pastora" -> hold(le, Material.SHEARS, null);
            case "cantero" -> hold(le, Material.STONE_PICKAXE, null);
            case "carnicero" -> hold(le, Material.IRON_AXE, null);
            case "arquero" -> hold(le, Material.BOW, null);
            case "aprendiz" -> hold(le, Material.IRON_INGOT, null);
            case "curtidor" -> hold(le, Material.LEATHER, null);
            case "guardia" -> hold(le, Material.IRON_SWORD, Material.SHIELD);
            case "erudito", "monje" -> hold(le, Material.BOOK, null);
            case "nina" -> hold(le, Material.POPPY, null);
            default -> { }
        }
        le.getPersistentDataContainer().set(Keys.FILLER, PersistentDataType.STRING, role);
        fillers.add(le.getUniqueId());
        saveFillers();
    }

    private String npcId(Entity e) {
        return e.getPersistentDataContainer().get(Keys.NPC, PersistentDataType.STRING);
    }

    private String fillerRole(Entity e) {
        return e.getPersistentDataContainer().get(Keys.FILLER, PersistentDataType.STRING);
    }

    // ---------- Eventos ----------

    /** Al cargar entidades: quitar NPCs viejos o duplicados (por ejemplo, de una versión anterior de la historia). */
    @EventHandler
    public void onEntitiesLoad(org.bukkit.event.world.EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) {
            if (en.getScoreboardTags().contains(Combat.CORPSE)) {
                en.remove();
                continue;
            }
            if (en.getScoreboardTags().contains(Combat.FALLEN) && en instanceof LivingEntity fl) combat.stand(fl);
            String id = npcId(en);
            if (id != null) {
                UUID cur = npcs.get(id);
                if (cur == null || !cur.equals(en.getUniqueId())) en.remove();
                continue;
            }
            if (fillerRole(en) != null && !fillers.contains(en.getUniqueId())) en.remove();
            // marcadores "?" sueltos de una sesión anterior
            if (en instanceof TextDisplay && !en.isPersistent() && en.getVehicle() == null
                    && en.getScoreboardTags().contains("eclipse_marca")) en.remove();
        }
    }

    /** Firma de los NPCs de la historia: si cambia (editaste historia.txt), se vuelven a crear. */
    public String signature() {
        StringBuilder b = new StringBuilder();
        for (String id : plugin.npcs().ids()) {
            NpcManager.NpcDef d = plugin.npcs().def(id);
            b.append(id).append('|').append(d.name).append('|').append(d.skin).append('|').append(d.role).append('|')
                    .append(d.place).append('|').append(d.hand).append('|').append(d.offhand).append(';');
        }
        var sec = plugin.getConfig().getConfigurationSection("npc-lugares");
        if (sec != null) b.append(sec.getValues(true));
        return Integer.toHexString(b.toString().hashCode());
    }

    /** Al iniciar: si la historia cambió desde la última vez, recolocar a los NPCs. */
    public void refreshIfChanged() {
        if (!plugin.village().isGenerated()) return;
        String sig = signature();
        java.util.Set<String> expected = new java.util.HashSet<>();
        for (String id : plugin.npcs().ids()) {
            NpcManager.NpcDef d = plugin.npcs().def(id);
            if (d.city == null || plugin.getConfig().contains("npc-lugares." + id + ".x")) expected.add(id);
        }
        if (sig.equals(plugin.getConfig().getString("npc-firma")) && npcs.keySet().containsAll(expected)) return;
        int n = placeStoryNpcs();
        plugin.getConfig().set("npc-firma", sig);
        plugin.saveConfig();
        plugin.getLogger().info("Los NPCs cambiaron en historia.txt: " + n + " NPCs recolocados.");
    }

    private final Map<UUID, Long> clickCd = new HashMap<>();

    private void clicked(Player p, Entity ent) {
        String id = npcId(ent);
        String role = id == null ? fillerRole(ent) : null;
        if (id == null && role == null) return;
        if (combat.isDown(ent)) {
            p.sendActionBar(Txt.mm("<red>✚ Está herido. Vuelve en un rato."));
            return;
        }
        long now = System.currentTimeMillis();
        Long last = clickCd.get(p.getUniqueId());
        if (last != null && now - last < 400) return;
        clickCd.put(p.getUniqueId(), now);
        Walk w = walks.get(ent.getUniqueId());
        if (w != null) {
            w.hasTarget = false;
            w.pause = 100;
        }
        if (id != null) {
            plugin.npcs().open(p, id);
            return;
        }
        List<String> lines = CHATTER.getOrDefault(role, CHATTER.get("default"));
        if (ThreadLocalRandom.current().nextInt(3) == 0) lines = CHATTER.get("default");
        String line = lines.get(ThreadLocalRandom.current().nextInt(lines.size()));
        var nm = ent.customName();
        String name = nm == null ? "Aldeano" : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(nm);
        Txt.send(p, "<gray>" + name + ": <white>" + line);
        p.playSound(ent.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_AMBIENT, 0.6f, 1.2f);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEntityEvent e) {
        Entity ent = e.getRightClicked();
        if (npcId(ent) == null && fillerRole(ent) == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        clicked(e.getPlayer(), ent);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractAt(PlayerInteractAtEntityEvent e) {
        Entity ent = e.getRightClicked();
        if (npcId(ent) == null && fillerRole(ent) == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        clicked(e.getPlayer(), ent);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (npcId(e.getEntity()) == null && fillerRole(e.getEntity()) == null) return;
        e.setCancelled(true);
        if (e instanceof org.bukkit.event.entity.EntityDamageByEntityEvent by && e.getEntity() instanceof LivingEntity le && isGuard(le)) {
            Entity src = by.getDamager() instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Entity sh ? sh : by.getDamager();
            if (!(src instanceof Player)) combat.hurt(le, e.getDamage());
        } else if (e instanceof org.bukkit.event.entity.EntityDamageByEntityEvent by && e.getEntity() instanceof LivingEntity le && combat.npcsFall()) {
            Entity src = by.getDamager() instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Entity sh ? sh : by.getDamager();
            if (!(src instanceof Player) && combat.hostile(src)) combat.hurt(le, e.getDamage());
        }
    }

    // ---------- Tareas: paseo, mirar al jugador y marcadores ----------

    public void startTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::walkTick, 40L, 1L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::markTick, 40L, 10L);
        combat.startTask();
    }

    private Walk walkFor(LivingEntity le, boolean story) {
        Walk w = walks.get(le.getUniqueId());
        if (w != null) return w;
        w = new Walk();
        int[] h = le.getPersistentDataContainer().get(Keys.HOME, PersistentDataType.INTEGER_ARRAY);
        Location l = le.getLocation();
        if (h != null && h.length == 3) {
            w.homeX = h[0] / 10.0;
            w.homeY = h[1] / 10.0;
            w.homeZ = h[2] / 10.0;
        } else {
            w.homeX = l.getX();
            w.homeY = l.getY();
            w.homeZ = l.getZ();
        }
        w.story = story;
        String role = fillerRole(le);
        boolean kid = "nino".equals(role) || "nina".equals(role);
        boolean guard = "guardia".equals(role);
        w.radius = story ? plugin.getConfig().getDouble("npc-paseo.radio-historia", 3.5)
                : guard ? 4 : plugin.getConfig().getDouble("npc-paseo.radio-relleno", 13);
        w.speed = kid ? 0.14 : (story ? 0.075 : 0.09);
        w.yaw = l.getYaw();
        w.pause = ThreadLocalRandom.current().nextInt(20, 120);
        walks.put(le.getUniqueId(), w);
        return w;
    }

    private void walkTick() {
        for (Map.Entry<String, UUID> e : npcs.entrySet()) {
            if (Bukkit.getEntity(e.getValue()) instanceof LivingEntity le && le.isValid()) step(le, walkFor(le, true));
        }
        for (UUID u : fillers) {
            if (Bukkit.getEntity(u) instanceof LivingEntity le && le.isValid()) step(le, walkFor(le, false));
        }
    }

    private Player nearest(LivingEntity le, double max) {
        Player best = null;
        double bd = max * max;
        for (Player p : le.getWorld().getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            double d = p.getLocation().distanceSquared(le.getLocation());
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    private static float lerpYaw(float from, float to, float t) {
        float d = ((to - from) % 360 + 540) % 360 - 180;
        return from + d * t;
    }

    private void step(LivingEntity le, Walk w) {
        Location loc = le.getLocation();
        if (!loc.getBlock().getRelative(0, 1, 0).isPassable()) {
            // atrapado: salir al lugar libre más cercano
            Location free = safeSpot(loc);
            free.setYaw(w.yaw);
            le.teleport(free, TeleportFlag.EntityState.RETAIN_PASSENGERS);
            w.hasTarget = false;
            return;
        }
        // nadie cerca: no gastar
        if (!TEST && nearest(le, 72) == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (combat.isDown(le) && !isGuard(le)) return;
        if (isGuard(le)) {
            String sid = npcId(le);
            NpcManager.NpcDef nd = sid == null ? null : plugin.npcs().def(sid);
            double hp = nd != null && nd.combatHp > 0 ? nd.combatHp : combat.guardHp();
            double dmg = nd != null && nd.combatDamage > 0 ? nd.combatDamage : combat.guardDmg();
            if (combat.guardTick(le, w, hp, dmg)) return;
        }
        boolean panic = isCoward(le) && combat.danger(le);
        if (panic) {
            // corre a casa
            w.pause = 0;
            w.tx = w.homeX;
            w.tz = w.homeZ;
            w.hasTarget = true;
        }
        Player close = panic ? null : nearest(le, w.story ? 5 : 3);
        if (close != null) {
            // detenerse y mirar al jugador
            Location eye = le.getEyeLocation(), to = close.getEyeLocation();
            double dx = to.getX() - eye.getX(), dy = to.getY() - eye.getY(), dz = to.getZ() - eye.getZ();
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            w.yaw = lerpYaw(w.yaw, yaw, 0.3f);
            le.setRotation(w.yaw, pitch);
            w.pause = Math.max(w.pause, 30);
            return;
        }
        if (w.pause > 0) {
            w.pause--;
            if (w.pause % 40 == 0 && r.nextInt(3) == 0) {
                w.yaw = w.yaw + r.nextFloat() * 90 - 45;
                le.setRotation(w.yaw, 0);
            }
            return;
        }
        if (!w.hasTarget) {
            double ang = r.nextDouble() * Math.PI * 2, dist = r.nextDouble() * w.radius;
            w.tx = w.homeX + Math.cos(ang) * dist;
            w.tz = w.homeZ + Math.sin(ang) * dist;
            w.hasTarget = true;
        }
        double dx = w.tx - loc.getX(), dz = w.tz - loc.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < 0.25) {
            w.hasTarget = false;
            w.pause = panic ? 10 : w.story ? r.nextInt(120, 400) : r.nextInt(30, 200);
            return;
        }
        double sp = Math.min(d, panic ? Math.max(0.2, w.speed * 2.6) : w.speed);
        double nx = loc.getX() + dx / d * sp, nz = loc.getZ() + dz / d * sp;
        // mirar un poco más adelante para no meterse en paredes
        double ax = loc.getX() + dx / d * 0.45, az = loc.getZ() + dz / d * 0.45;
        double ny = surface(le.getWorld(), nx, nz, loc.getY());
        double ay = surface(le.getWorld(), ax, az, loc.getY());
        if (Double.isNaN(ny) || Double.isNaN(ay) || Math.abs(ny - loc.getY()) > 1.05 || Math.abs(ay - loc.getY()) > 1.05
                || Math.abs(ny - w.homeY) > 6) {
            w.hasTarget = false;
            w.pause = r.nextInt(10, 40);
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        w.yaw = lerpYaw(w.yaw, yaw, 0.25f);
        Location next = new Location(loc.getWorld(), nx, ny, nz, w.yaw, 0);
        le.teleport(next, TeleportFlag.EntityState.RETAIN_PASSENGERS);
    }

    /** Lugar libre más cercano para pararse (no dentro de paredes, vallas ni espantapájaros). */
    public static Location safeSpot(Location at) {
        World w = at.getWorld();
        if (w == null) return at;
        int bx = at.getBlockX(), bz = at.getBlockZ();
        for (int r = 0; r <= 6; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (double dy : new double[]{0, 1, -1, 2}) {
                        double y = surface(w, bx + dx + 0.5, bz + dz + 0.5, at.getY() + dy);
                        if (Double.isNaN(y)) continue;
                        // también libre alrededor (medio bloque) para no rozar paredes
                        return new Location(w, bx + dx + 0.5, y, bz + dz + 0.5, at.getYaw(), 0);
                    }
                }
            }
        }
        return at;
    }

    /** Altura del suelo caminable en (x,z) cerca de y; NaN si no se puede pisar. */
    static double surface(World w, double x, double z, double y) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z), by = (int) Math.floor(y + 0.01);
        if (!w.isChunkLoaded(bx >> 4, bz >> 4)) return Double.NaN;
        for (int yy : new int[]{by, by + 1, by - 1}) {
            Block below = w.getBlockAt(bx, yy - 1, bz), feet = w.getBlockAt(bx, yy, bz), head = w.getBlockAt(bx, yy + 1, bz);
            if (!feet.isPassable() || feet.isLiquid() || !head.isPassable() || head.isLiquid()) continue;
            if (below.isPassable() || below.isLiquid()) continue;
            Material bm = below.getType();
            if (bm == Material.MAGMA_BLOCK || bm == Material.CAMPFIRE || bm == Material.SOUL_CAMPFIRE || bm == Material.CACTUS) continue;
            BoundingBox bb = below.getBoundingBox();
            if (bb.getHeight() > 1.01 || bb.getHeight() <= 0) continue;
            return bb.getMaxY();
        }
        // escalón de losa dentro del bloque de los pies
        Block feet = w.getBlockAt(bx, by, bz);
        if (!feet.isPassable() && !feet.isLiquid()) {
            BoundingBox bb = feet.getBoundingBox();
            if (bb.getHeight() <= 0.55 && w.getBlockAt(bx, by + 1, bz).isPassable() && w.getBlockAt(bx, by + 2, bz).isPassable())
                return bb.getMaxY();
        }
        return Double.NaN;
    }

    private void markTick() {
        for (Map.Entry<String, UUID> e : npcs.entrySet()) {
            Entity ent = Bukkit.getEntity(e.getValue());
            if (!(ent instanceof LivingEntity v) || !v.isValid()) continue;
            Map<Mark, UUID> m = ensureMarks(e.getKey(), v);
            for (Player p : v.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(v.getLocation()) > 64 * 64) continue;
                QuestManager.State st = p.getGameMode() == GameMode.SPECTATOR || combat.isDown(v) ? QuestManager.State.NONE
                        : plugin.quests().stateFor(p, e.getKey());
                for (Mark mk : Mark.values()) {
                    Entity disp = Bukkit.getEntity(m.get(mk));
                    if (disp == null) continue;
                    boolean show = mk.state == st;
                    if (show && !p.canSee(disp)) p.showEntity(plugin, disp);
                    else if (!show && p.canSee(disp)) p.hideEntity(plugin, disp);
                }
            }
        }
    }

    private Map<Mark, UUID> ensureMarks(String id, LivingEntity v) {
        Map<Mark, UUID> m = marks.computeIfAbsent(id, k -> new HashMap<>());
        World w = v.getWorld();
        for (Mark mk : Mark.values()) {
            UUID u = m.get(mk);
            Entity e = u == null ? null : Bukkit.getEntity(u);
            if (e != null && e.isValid()) {
                if (!v.getPassengers().contains(e)) v.addPassenger(e);
                continue;
            }
            TextDisplay td = (TextDisplay) w.spawnEntity(v.getLocation().add(0, 2, 0), EntityType.TEXT_DISPLAY);
            td.text(Txt.mm(mk.text));
            td.setBillboard(Display.Billboard.CENTER);
            td.setVisibleByDefault(false);
            td.setPersistent(false);
            td.setShadowed(true);
            td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            td.addScoreboardTag("eclipse_marca");
            td.setTransformation(new Transformation(new Vector3f(0f, 0.75f, 0f), new AxisAngle4f(),
                    new Vector3f(2.2f, 2.2f, 2.2f), new AxisAngle4f()));
            v.addPassenger(td);
            m.put(mk, td.getUniqueId());
        }
        return m;
    }

    private void removeMarks(String id) {
        Map<Mark, UUID> m = marks.remove(id);
        if (m == null) return;
        for (UUID u : m.values()) {
            Entity e = Bukkit.getEntity(u);
            if (e != null) e.remove();
        }
    }

    public void cleanup() {
        for (String id : marks.keySet().toArray(new String[0])) removeMarks(id);
    }

    public Map<String, UUID> storyIds() {
        return npcs;
    }

    public Set<UUID> fillerIds() {
        return fillers;
    }

    public boolean has(String id) {
        return npcs.containsKey(id);
    }
}
