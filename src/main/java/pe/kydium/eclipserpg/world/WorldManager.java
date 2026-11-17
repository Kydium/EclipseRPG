package pe.kydium.eclipserpg.world;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.data.PlayerData;
import pe.kydium.eclipserpg.quests.Quest;
import pe.kydium.eclipserpg.util.Keys;
import pe.kydium.eclipserpg.util.Reg;
import pe.kydium.eclipserpg.util.Txt;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * "Mundo vivo": reglas de ambiente (clima, hora, luna, bioma… que afectan al jugador),
 * apariciones naturales a medida y eventos del mundo (Luna de Sangre, tormentas…).
 * Todo se lee de avanzado/mundo.yml de la historia y se edita desde el editor web.
 */
public final class WorldManager implements Listener {

    // ------------------------------------------------------------------ Condiciones

    private static final Map<String, List<String>> BIOME_GROUPS = Map.of(
            "frio", List.of("snow", "ice", "frozen", "grove", "peaks", "taiga"),
            "calido", List.of("desert", "badlands", "savanna"),
            "desierto", List.of("desert", "badlands"),
            "selva", List.of("jungle"),
            "oceano", List.of("ocean", "beach", "river"),
            "montana", List.of("peaks", "slopes", "hills", "grove", "meadow"),
            "pantano", List.of("swamp"),
            "bosque", List.of("forest", "taiga", "grove"),
            "cuevas", List.of("caves", "dark"));

    /** Cuándo se cumple una regla. Todo lo que se deja vacío no se revisa. */
    public static final class Cond {
        final Set<String> weather = new HashSet<>();
        final Set<String> moment = new HashSet<>();
        final Set<String> moon = new HashSet<>();
        final List<String> biomes = new ArrayList<>();
        final Set<String> dimension = new HashSet<>();
        Integer minY, maxY, minLevel, maxLevel, minAct, maxAct, maxLight;
        String sky;      // abierto | techo
        Boolean inWater;
        String zone;     // aldea | fuera
        String event;
        String armor;    // ninguna | alguna | cuero | metal
        String has, hasNot;

        public boolean isEmpty() {
            return weather.isEmpty() && moment.isEmpty() && moon.isEmpty() && biomes.isEmpty() && dimension.isEmpty()
                    && minY == null && maxY == null && minLevel == null && maxLevel == null && minAct == null && maxAct == null
                    && maxLight == null && sky == null && inWater == null && zone == null && event == null && armor == null
                    && has == null && hasNot == null;
        }
    }

    private static List<String> strings(ConfigurationSection s, String key) {
        if (s == null || !s.contains(key)) return List.of();
        List<String> out = new ArrayList<>();
        if (s.isList(key)) {
            for (Object o : s.getList(key, List.of())) if (o != null) out.add(o.toString());
        } else {
            for (String p : s.getString(key, "").split(",")) if (!p.isBlank()) out.add(p);
        }
        out.replaceAll(x -> x.trim().toLowerCase(Locale.ROOT).replace(' ', '_'));
        return out;
    }

    private static Integer intOrNull(ConfigurationSection s, String key) {
        return s.contains(key) && !s.getString(key, "").isBlank() ? (Integer) s.getInt(key) : null;
    }

    private static String strOrNull(ConfigurationSection s, String key) {
        String v = s.getString(key);
        return v == null || v.isBlank() ? null : v.trim().toLowerCase(Locale.ROOT);
    }

    static Cond cond(ConfigurationSection s) {
        Cond c = new Cond();
        if (s == null) return c;
        c.weather.addAll(strings(s, "clima"));
        c.moment.addAll(strings(s, "momento"));
        c.moon.addAll(strings(s, "luna"));
        c.biomes.addAll(strings(s, "bioma"));
        c.dimension.addAll(strings(s, "dimension"));
        c.minY = intOrNull(s, "altura-min");
        c.maxY = intOrNull(s, "altura-max");
        c.minLevel = intOrNull(s, "nivel-min");
        c.maxLevel = intOrNull(s, "nivel-max");
        c.minAct = intOrNull(s, "acto-min");
        c.maxAct = intOrNull(s, "acto-max");
        c.maxLight = intOrNull(s, "luz-max");
        c.sky = strOrNull(s, "cielo");
        if (s.contains("en-agua") && !s.getString("en-agua", "").isBlank()) c.inWater = s.getBoolean("en-agua");
        c.zone = strOrNull(s, "zona");
        c.event = strOrNull(s, "evento");
        c.armor = strOrNull(s, "armadura");
        c.has = strOrNull(s, "tiene");
        c.hasNot = strOrNull(s, "no-tiene");
        return c;
    }

    public static String weatherOf(World w) {
        if (w.getEnvironment() != World.Environment.NORMAL) return "despejado";
        return w.isThundering() && w.hasStorm() ? "tormenta" : w.hasStorm() ? "lluvia" : "despejado";
    }

    public static String momentOf(World w) {
        long t = w.getTime();
        if (t >= 23000 || t < 1000) return "amanecer";
        if (t < 12000) return "dia";
        if (t < 13000) return "atardecer";
        return "noche";
    }

    public static boolean isNight(World w) {
        long t = w.getTime();
        return t >= 13000 && t < 23000;
    }

    /** llena, menguante, nueva o creciente. */
    public static String moonOf(World w) {
        int phase = (int) ((w.getFullTime() / 24000L) % 8L);
        return phase == 0 ? "llena" : phase < 4 ? "menguante" : phase == 4 ? "nueva" : "creciente";
    }

    private boolean biomeMatches(List<String> wanted, String biome) {
        for (String b : wanted) {
            List<String> group = BIOME_GROUPS.get(b);
            if (group != null) {
                for (String g : group) if (biome.contains(g)) return true;
            } else if (biome.equals(b) || biome.contains(b)) {
                return true;
            }
        }
        return false;
    }

    private static boolean metal(Material m) {
        String n = m.name();
        return n.startsWith("IRON_") || n.startsWith("GOLDEN_") || n.startsWith("DIAMOND_") || n.startsWith("NETHERITE_") || n.startsWith("CHAINMAIL_") || n.startsWith("COPPER_");
    }

    private boolean hasItem(Player p, String spec) {
        String s = spec.startsWith("material:") ? spec.substring(9) : spec;
        if (s.startsWith("custom:")) return plugin.items().count(p, s.substring(7)) > 0;
        if (plugin.items().def(s) != null) return plugin.items().count(p, s) > 0;
        Material m = Material.matchMaterial(s);
        return m != null && p.getInventory().contains(m);
    }

    /** ¿Se cumple la condición en ese lugar (y para ese jugador, si hay)? */
    public boolean test(Cond c, Location loc, Player p) {
        if (c.isEmpty()) return true;
        World w = loc.getWorld();
        if (w == null) return false;
        if (!c.dimension.isEmpty()) {
            String dim = switch (w.getEnvironment()) {
                case NETHER -> "nether";
                case THE_END -> "end";
                default -> "overworld";
            };
            if (!c.dimension.contains(dim)) return false;
        }
        if (!c.weather.isEmpty() && !c.weather.contains(weatherOf(w))) return false;
        if (!c.moment.isEmpty()) {
            String m = momentOf(w);
            boolean ok = c.moment.contains(m) || (m.equals("amanecer") && c.moment.contains("dia")) || (m.equals("atardecer") && c.moment.contains("dia"));
            if (!ok) return false;
        }
        if (!c.moon.isEmpty() && !c.moon.contains(moonOf(w))) return false;
        if (c.minY != null && loc.getY() < c.minY) return false;
        if (c.maxY != null && loc.getY() > c.maxY) return false;
        if (c.event != null && !active.containsKey(c.event)) return false;
        if (!c.biomes.isEmpty()) {
            String biome = w.getBiome(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()).getKey().getKey();
            if (!biomeMatches(c.biomes, biome)) return false;
        }
        if (c.sky != null) {
            boolean open = w.getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ()) <= loc.getBlockY();
            if (open != c.sky.startsWith("abierto")) return false;
        }
        if (c.maxLight != null && loc.getBlock().getLightLevel() > c.maxLight) return false;
        if (c.inWater != null) {
            boolean wet = loc.getBlock().getType() == Material.WATER || (p != null && p.isInWater());
            if (wet != c.inWater) return false;
        }
        if (c.zone != null) {
            Location v = plugin.village().center();
            boolean in = v != null && v.getWorld() == w && v.distanceSquared(loc) < 70 * 70;
            if (in != c.zone.equals("aldea")) return false;
        }
        if (p == null) return true;
        PlayerData d = plugin.data().get(p);
        if (c.minLevel != null && d.level < c.minLevel) return false;
        if (c.maxLevel != null && d.level > c.maxLevel) return false;
        if (c.minAct != null || c.maxAct != null) {
            Quest q = plugin.quests().current(p);
            int act = q == null ? 99 : q.act;
            if (c.minAct != null && act < c.minAct) return false;
            if (c.maxAct != null && act > c.maxAct) return false;
        }
        if (c.armor != null) {
            int pieces = 0, leather = 0, metal = 0;
            for (ItemStack it : p.getInventory().getArmorContents()) {
                if (it == null || it.getType().isAir()) continue;
                pieces++;
                if (it.getType().name().startsWith("LEATHER_")) leather++;
                if (metal(it.getType())) metal++;
            }
            boolean ok = switch (c.armor) {
                case "ninguna" -> pieces == 0;
                case "alguna" -> pieces > 0;
                case "cuero" -> leather > 0;
                case "sin_cuero", "sin-cuero" -> leather == 0;
                case "metal" -> metal > 0;
                default -> true;
            };
            if (!ok) return false;
        }
        if (c.has != null && !hasItem(p, c.has)) return false;
        return c.hasNot == null || !hasItem(p, c.hasNot);
    }

    // ------------------------------------------------------------------ Efectos

    /** Lo que le pasa al jugador mientras se cumple una regla o dura un evento. */
    public static final class Fx {
        final List<PotionEffect> potions = new ArrayList<>();
        double damage, heal;
        int hunger;
        int every = 4;
        int fire;
        boolean freeze;
        double maxHealth, speed;
        double dmgOut = 1, dmgIn = 1, xp = 1, money = 1, spawns = 1;
        String message;
    }

    static Fx fx(ConfigurationSection s) {
        Fx f = new Fx();
        if (s == null) return f;
        for (Map<?, ?> m : s.getMapList("efectos")) {
            Object e = m.get("efecto");
            if (e == null) continue;
            PotionEffectType t = Reg.effect(e.toString().trim().replace(' ', '_'));
            if (t == null) continue;
            int lvl = 1;
            try {
                lvl = Math.max(1, (int) Double.parseDouble(String.valueOf(m.get("nivel"))));
            } catch (NumberFormatException ignored) {
                // nivel 1
            }
            // dura un poco más que el ciclo para que no parpadee; visión nocturna necesita más
            f.potions.add(new PotionEffect(t, t.equals(PotionEffectType.NIGHT_VISION) ? 320 : 100, lvl - 1, true, false, true));
        }
        f.damage = s.getDouble("danio", 0);
        f.heal = s.getDouble("curar", 0);
        f.hunger = s.getInt("hambre", 0);
        f.every = Math.max(1, s.getInt("cada", 4));
        f.fire = s.getInt("fuego", 0);
        f.freeze = s.getBoolean("congelar", false);
        f.maxHealth = s.getDouble("vida-maxima", 0);
        f.speed = s.getDouble("velocidad", 0);
        f.dmgOut = mult(s, "danio-hecho");
        f.dmgIn = mult(s, "danio-recibido");
        f.xp = mult(s, "xp");
        f.money = mult(s, "dinero");
        f.spawns = mult(s, "apariciones");
        String msg = s.getString("mensaje");
        f.message = msg == null || msg.isBlank() ? null : msg;
        return f;
    }

    private static double mult(ConfigurationSection s, String key) {
        double v = s.getDouble(key, 1);
        return v <= 0 ? 1 : v;
    }

    // ------------------------------------------------------------------ Definiciones

    public static final class Rule {
        public String id, name;
        Cond when;
        Fx fx;
    }

    public static final class SpawnRule {
        String mob;
        final Set<String> replaces = new HashSet<>();
        double chance;
        int min = 1, max = 1;
        Cond when;
    }

    public static final class EventDef {
        public String id, name;
        String barColor = "RED";
        String starts = "noche";     // noche | dia | cualquiera | manual
        double chance;
        int everyDays;
        int seconds;                 // 0 = hasta que cambie el día/noche
        String weather, time;
        String title, subtitle, sound, endMessage;
        boolean noSleep;
        Cond when;                   // para empezar
        Fx fx;
        final List<String> waveMobs = new ArrayList<>();
        int waveEvery = 30, waveAmount = 2;
        long rewardXp, rewardMoney;
    }

    private static final class Active {
        EventDef def;
        long endsAt;       // ms; 0 = hasta el cambio de día/noche
        long startedAt;
        boolean startedAtNight;
        BossBar bar;
        int waveClock;
        boolean changedWeather;
    }

    /** Estado calculado de cada jugador (se refresca cada 2 segundos). */
    private static final class State {
        final Set<String> rules = new HashSet<>();
        double dmgOut = 1, dmgIn = 1, xp = 1, money = 1, spawns = 1;
        int clock;
    }

    private final EclipseRPG plugin;
    private final Map<String, Rule> rules = new LinkedHashMap<>();
    private final List<SpawnRule> spawnRules = new ArrayList<>();
    private final Map<String, EventDef> events = new LinkedHashMap<>();
    private final Map<String, Active> active = new LinkedHashMap<>();
    private final Map<UUID, State> states = new HashMap<>();
    private final Set<EntityType> blocked = new HashSet<>();
    private double spawnMult = 1;
    private double dayMinutes = 10, nightMinutes = 10;
    private String sleep = "si";
    private double timeDebt;
    private long lastFull = Long.MIN_VALUE;
    private boolean wasNight;
    private long lastHour = -1;
    private boolean spawning;
    private final NamespacedKey hpKey = Keys.k("mundo_vida"), speedKey = Keys.k("mundo_velocidad");

    public WorldManager(EclipseRPG plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ Carga

    public void load() {
        rules.clear();
        spawnRules.clear();
        events.clear();
        blocked.clear();
        YamlConfiguration y = plugin.story().yaml("mundo");
        ConfigurationSection t = y.getConfigurationSection("tiempo");
        dayMinutes = t == null ? 10 : Math.max(0.5, t.getDouble("minutos-dia", 10));
        nightMinutes = t == null ? 10 : Math.max(0.5, t.getDouble("minutos-noche", 10));
        sleep = t == null ? "si" : t.getString("dormir", "si").toLowerCase(Locale.ROOT);

        ConfigurationSection rs = y.getConfigurationSection("reglas");
        if (rs != null) for (String id : rs.getKeys(false)) {
            ConfigurationSection s = rs.getConfigurationSection(id);
            if (s == null || !s.getBoolean("activa", true)) continue;
            Rule r = new Rule();
            r.id = id;
            r.name = s.getString("nombre", id);
            r.when = cond(s.getConfigurationSection("cuando"));
            r.fx = fx(s);
            rules.put(id, r);
        }

        ConfigurationSection ap = y.getConfigurationSection("apariciones");
        spawnMult = ap == null ? 1 : Math.max(0, ap.getDouble("multiplicador", 1));
        if (ap != null) {
            for (String b : strings(ap, "bloquear")) {
                EntityType et = Reg.entity(b);
                if (et != null) blocked.add(et);
            }
            for (Map<?, ?> m : ap.getMapList("reglas")) {
                YamlConfiguration tmp = new YamlConfiguration();
                ConfigurationSection s = tmp.createSection("r", m);
                String mob = s.getString("mob", "").trim().toLowerCase(Locale.ROOT).replace(' ', '_');
                if (mob.isEmpty()) continue;
                if (plugin.mobs().def(mob) == null && Reg.entity(mob) == null) {
                    plugin.getLogger().warning("mundo.yml: en apariciones no existe el mob '" + mob + "'");
                    continue;
                }
                SpawnRule r = new SpawnRule();
                r.mob = mob;
                r.replaces.addAll(strings(s, "reemplaza"));
                r.chance = s.getDouble("probabilidad", 0.1);
                String g = s.getString("grupo", "1");
                try {
                    String[] p = g.split("-");
                    r.min = Math.max(1, Integer.parseInt(p[0].trim()));
                    r.max = Math.max(r.min, p.length > 1 ? Integer.parseInt(p[1].trim()) : r.min);
                } catch (NumberFormatException e) {
                    r.min = r.max = 1;
                }
                r.when = cond(s.getConfigurationSection("cuando"));
                spawnRules.add(r);
            }
        }

        ConfigurationSection ev = y.getConfigurationSection("eventos");
        if (ev != null) for (String id : ev.getKeys(false)) {
            ConfigurationSection s = ev.getConfigurationSection(id);
            if (s == null) continue;
            EventDef e = new EventDef();
            e.id = id.toLowerCase(Locale.ROOT);
            e.name = s.getString("nombre", id);
            e.barColor = s.getString("color-barra", "RED");
            e.starts = s.getString("empieza", "noche").toLowerCase(Locale.ROOT);
            e.chance = s.getDouble("probabilidad", 0);
            e.everyDays = s.getInt("cada-dias", 0);
            e.seconds = s.getInt("duracion", 0);
            e.weather = strOrNull(s, "clima");
            e.time = strOrNull(s, "hora");
            e.title = s.getString("anuncio");
            e.subtitle = s.getString("subtitulo", "");
            e.sound = s.getString("sonido");
            e.endMessage = s.getString("mensaje-fin");
            e.noSleep = s.getBoolean("no-dormir", false);
            e.when = cond(s.getConfigurationSection("cuando"));
            e.fx = fx(s);
            ConfigurationSection o = s.getConfigurationSection("oleadas");
            if (o != null) {
                for (String mob : strings(o, "mobs")) {
                    if (plugin.mobs().def(mob) != null || Reg.entity(mob) != null) e.waveMobs.add(mob);
                    else plugin.getLogger().warning("mundo.yml: el evento " + id + " usa un mob que no existe: " + mob);
                }
                e.waveEvery = Math.max(5, o.getInt("cada", 30));
                e.waveAmount = Math.max(1, o.getInt("cantidad", 2));
            }
            e.rewardXp = s.getLong("premio-xp", 0);
            e.rewardMoney = s.getLong("premio-dinero", 0);
            events.put(e.id, e);
        }
        // los eventos en curso siguen (con sus datos nuevos) si todavía existen
        for (Active act : new ArrayList<>(active.values())) {
            EventDef fresh = events.get(act.def.id);
            if (fresh == null) stop(act.def.id, false);
            else act.def = fresh;
        }
        plugin.getLogger().info("Mundo vivo: " + rules.size() + " reglas de ambiente, " + spawnRules.size()
                + " apariciones, " + events.size() + " eventos");
    }

    public Set<String> eventIds() {
        return events.keySet();
    }

    public Set<String> ruleIds() {
        return rules.keySet();
    }

    public Set<String> activeEvents() {
        return active.keySet();
    }

    public int spawnRuleCount() {
        return spawnRules.size();
    }

    // ------------------------------------------------------------------ Tareas

    public void startTasks() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::timeTick, 20L, 1L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::cycle, 40L, 20L);
    }

    private World main() {
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    /** Alarga o acorta el día y la noche (10 minutos cada uno es lo normal). */
    private void timeTick() {
        World w = main();
        if (w == null) return;
        long t = w.getTime();
        double minutes = (t >= 12000) ? nightMinutes : dayMinutes;
        if (Math.abs(minutes - 10) < 0.01) return;
        long full = w.getFullTime();
        boolean frozen = full == lastFull;   // el ciclo de día está apagado (gamerule): no tocar
        lastFull = full;
        if (frozen) return;
        // el juego avanza 1 por tick; queremos 10/minutos por tick
        timeDebt += 10.0 / minutes - 1.0;
        long whole = (long) timeDebt;
        if (whole != 0) {
            timeDebt -= whole;
            w.setFullTime(full + whole);
            lastFull = full + whole;
        }
    }

    /** Cada segundo: eventos; cada 2 segundos: reglas de ambiente de cada jugador. */
    private void cycle() {
        World w = main();
        if (w == null) return;
        boolean night = isNight(w);
        if (night != wasNight) {
            wasNight = night;
            for (Active a : new ArrayList<>(active.values())) {
                if (a.endsAt == 0 && a.startedAtNight != night && System.currentTimeMillis() - a.startedAt > 5000) stop(a.def.id, true);
            }
            if (!Bukkit.getOnlinePlayers().isEmpty()) roll(night ? "noche" : "dia", w);
        }
        long hour = w.getFullTime() / 1000L;
        if (hour != lastHour) {
            lastHour = hour;
            if (!Bukkit.getOnlinePlayers().isEmpty()) roll("cualquiera", w);
        }
        long now = System.currentTimeMillis();
        for (Active a : new ArrayList<>(active.values())) {
            if (a.endsAt > 0 && now >= a.endsAt) {
                stop(a.def.id, true);
                continue;
            }
            if (a.bar != null && a.endsAt > 0) {
                a.bar.progress((float) Math.max(0, Math.min(1, (a.endsAt - now) / (double) (a.endsAt - a.startedAt))));
            }
            if (a.def.weather != null) keepWeather(w, a.def.weather);
            for (Player p : Bukkit.getOnlinePlayers()) if (a.bar != null) p.showBossBar(a.bar);
            if (!a.def.waveMobs.isEmpty() && ++a.waveClock >= a.def.waveEvery) {
                a.waveClock = 0;
                wave(a.def);
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            State st = states.computeIfAbsent(p.getUniqueId(), k -> new State());
            st.clock++;
            if (st.clock % 2 == 0) applyRules(p, st);
        }
    }

    private void roll(String moment, World w) {
        long day = w.getFullTime() / 24000L;
        for (EventDef e : events.values()) {
            if (!e.starts.equals(moment) || active.containsKey(e.id)) continue;
            Location ref = w.getSpawnLocation();
            if (!test(e.when, ref, null)) continue;
            boolean go = e.everyDays > 0 && !moment.equals("cualquiera") && day % e.everyDays == e.everyDays - 1;
            if (!go && e.chance > 0 && ThreadLocalRandom.current().nextDouble() < e.chance) go = true;
            if (go) start(e.id);
        }
    }

    private void keepWeather(World w, String weather) {
        switch (weather) {
            case "tormenta" -> {
                if (!w.hasStorm()) w.setStorm(true);
                if (!w.isThundering()) w.setThundering(true);
                w.setWeatherDuration(600);
                w.setThunderDuration(600);
            }
            case "lluvia" -> {
                if (!w.hasStorm()) w.setStorm(true);
                if (w.isThundering()) w.setThundering(false);
                w.setWeatherDuration(600);
            }
            case "despejado" -> {
                if (w.hasStorm()) w.setStorm(false);
                if (w.isThundering()) w.setThundering(false);
            }
            default -> { }
        }
    }

    /** Cambia el clima del mundo principal (despejado, lluvia o tormenta) durante unos segundos. */
    public boolean setWeather(String weather, int seconds) {
        World w = main();
        if (w == null) return false;
        String k = weather == null ? "" : weather.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("tormenta", "lluvia", "despejado").contains(k)) return false;
        keepWeather(w, k);
        int ticks = Math.max(20, seconds * 20);
        if (k.equals("despejado")) w.setClearWeatherDuration(ticks);
        else {
            w.setWeatherDuration(ticks);
            if (k.equals("tormenta")) w.setThunderDuration(ticks);
        }
        return true;
    }

    private void wave(EventDef e) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE) continue;
            if (p.getWorld() != main()) continue;
            int near = 0;
            for (var ent : p.getNearbyEntities(32, 16, 32)) if (ent instanceof Enemy) near++;
            if (near >= plugin.getConfig().getInt("mundo.maximo-enemigos-cerca", 30)) continue;
            for (int i = 0; i < e.waveAmount; i++) {
                spawnMob(e.waveMobs.get(r.nextInt(e.waveMobs.size())), plugin.mobs().spawnSpot(p.getLocation(), 14));
            }
        }
    }

    private LivingEntity spawnMob(String mob, Location at) {
        if (plugin.mobs().def(mob) != null) return plugin.mobs().spawn(mob, at);
        EntityType t = Reg.entity(mob);
        if (t == null || at.getWorld() == null) return null;
        spawning = true;
        try {
            return at.getWorld().spawnEntity(at, t) instanceof LivingEntity le ? le : null;
        } finally {
            spawning = false;
        }
    }

    // ------------------------------------------------------------------ Eventos del mundo

    public boolean start(String id) {
        EventDef e = events.get(id == null ? "" : id.toLowerCase(Locale.ROOT));
        World w = main();
        if (e == null || w == null) return false;
        if (active.containsKey(e.id)) return true;
        Active a = new Active();
        a.def = e;
        a.startedAt = System.currentTimeMillis();
        if (e.time != null) {
            long target = switch (e.time) {
                case "dia", "mediodia" -> 6000;
                case "amanecer" -> 23500;
                case "atardecer" -> 12500;
                case "medianoche" -> 18000;
                case "noche" -> 13500;
                default -> -1;
            };
            if (target >= 0) {
                long full = w.getFullTime();
                long add = ((target - full % 24000L) + 24000L) % 24000L;
                w.setFullTime(full + add);
                wasNight = isNight(w);
            }
        }
        a.startedAtNight = isNight(w);
        a.endsAt = e.seconds > 0 ? a.startedAt + e.seconds * 1000L : 0;
        if (e.weather != null) {
            keepWeather(w, e.weather);
            a.changedWeather = true;
        }
        BossBar.Color c;
        try {
            c = BossBar.Color.valueOf(e.barColor.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            c = BossBar.Color.RED;
        }
        a.bar = BossBar.bossBar(Txt.mm(e.name), 1f, c, BossBar.Overlay.PROGRESS);
        active.put(e.id, a);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(a.bar);
            String title = e.title == null || e.title.isBlank() ? e.name : e.title;
            p.showTitle(Title.title(Txt.mm(title), Txt.mm(e.subtitle == null ? "" : e.subtitle),
                    Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(3), Duration.ofMillis(800))));
            Txt.sound(p, e.sound == null || e.sound.isBlank() ? "entity.wither.spawn" : e.sound, 0.7f, 0.8f);
        }
        plugin.getLogger().info("Evento del mundo iniciado: " + e.id);
        return true;
    }

    public boolean stop(String id, boolean reward) {
        Active a = active.remove(id == null ? "" : id.toLowerCase(Locale.ROOT));
        if (a == null) return false;
        World w = main();
        if (a.changedWeather && w != null && !"despejado".equals(a.def.weather)) {
            boolean other = false;
            for (Active o : active.values()) if (o.def.weather != null) other = true;
            if (!other) {
                w.setStorm(false);
                w.setThundering(false);
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (a.bar != null) p.hideBossBar(a.bar);
            if (!reward) continue;
            if (a.def.endMessage != null && !a.def.endMessage.isBlank()) Txt.send(p, a.def.endMessage);
            if (a.def.rewardXp > 0 || a.def.rewardMoney > 0) plugin.levels().reward(p, a.def.rewardXp, a.def.rewardMoney);
        }
        return true;
    }

    public String eventName(String id) {
        EventDef e = events.get(id);
        return e == null ? id : e.name;
    }

    // ------------------------------------------------------------------ Reglas sobre el jugador

    private void applyRules(Player p, State st) {
        boolean skip = p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR || p.isDead();
        List<Fx> on = new ArrayList<>();
        Set<String> now = new HashSet<>();
        if (!skip) {
            Location loc = p.getLocation();
            for (Rule r : rules.values()) {
                if (!test(r.when, loc, p)) continue;
                now.add(r.id);
                on.add(r.fx);
                if (!st.rules.contains(r.id) && r.fx.message != null) p.sendActionBar(Txt.mm(r.fx.message));
            }
            for (Active a : active.values()) {
                if (p.getWorld() != main()) continue;
                on.add(a.def.fx);
                now.add("evento:" + a.def.id);
                if (!st.rules.contains("evento:" + a.def.id) && a.def.fx.message != null) p.sendActionBar(Txt.mm(a.def.fx.message));
            }
        }
        st.rules.clear();
        st.rules.addAll(now);
        double hp = 0, speed = 0;
        st.dmgOut = st.dmgIn = st.xp = st.money = st.spawns = 1;
        int seconds = st.clock;
        for (Fx f : on) {
            for (PotionEffect pe : f.potions) p.addPotionEffect(pe);
            hp += f.maxHealth;
            speed += f.speed;
            st.dmgOut *= f.dmgOut;
            st.dmgIn *= f.dmgIn;
            st.xp *= f.xp;
            st.money *= f.money;
            st.spawns *= f.spawns;
            if (f.freeze) p.setFreezeTicks(Math.min(p.getMaxFreezeTicks() + 40, p.getFreezeTicks() + 60));
            if (f.fire > 0 && p.getFireTicks() < 20) p.setFireTicks(f.fire * 20);
            boolean pulse = seconds % Math.max(2, f.every - f.every % 2) == 0;
            if (!pulse) continue;
            if (f.damage > 0 && p.getHealth() > 0) p.damage(f.damage);
            if (f.heal > 0 && !p.isDead()) {
                AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
                p.setHealth(Math.min(max == null ? 20 : max.getValue(), p.getHealth() + f.heal));
            }
            if (f.hunger != 0) p.setFoodLevel(Math.max(0, Math.min(20, p.getFoodLevel() + f.hunger)));
        }
        modifier(p, Attribute.MAX_HEALTH, hpKey, hp, AttributeModifier.Operation.ADD_NUMBER);
        modifier(p, Attribute.MOVEMENT_SPEED, speedKey, speed, AttributeModifier.Operation.ADD_SCALAR);
    }

    private void modifier(Player p, Attribute attr, NamespacedKey key, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        AttributeModifier old = null;
        for (AttributeModifier m : inst.getModifiers()) if (key.equals(m.getKey())) old = m;
        if (old != null && Math.abs(old.getAmount() - amount) < 1e-6) return;
        if (old != null) inst.removeModifier(old);
        if (attr == Attribute.MAX_HEALTH && amount < 0) amount = Math.max(amount, -(inst.getValue() - 2));
        if (Math.abs(amount) > 1e-6) inst.addTransientModifier(new AttributeModifier(key, amount, op, EquipmentSlotGroup.ANY));
        if (attr == Attribute.MAX_HEALTH && !p.isDead() && p.getHealth() > inst.getValue()) p.setHealth(inst.getValue());
    }

    private State state(Player p) {
        return states.get(p.getUniqueId());
    }

    public double xpMult(Player p) {
        State s = state(p);
        return s == null ? 1 : s.xp;
    }

    public double moneyMult(Player p) {
        State s = state(p);
        return s == null ? 1 : s.money;
    }

    /** Reglas de ambiente que le afectan ahora (para pruebas y para /evento). */
    public Set<String> rulesOn(Player p) {
        State s = state(p);
        return s == null ? Set.of() : Set.copyOf(s.rules);
    }

    // ------------------------------------------------------------------ Listeners

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        states.remove(e.getPlayer().getUniqueId());
        for (Active a : active.values()) if (a.bar != null) e.getPlayer().hideBossBar(a.bar);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBed(PlayerBedEnterEvent e) {
        boolean block = sleep.equals("no");
        String why = "<red>No puedes dormir en este mundo.";
        for (Active a : active.values()) {
            if (a.def.noSleep) {
                block = true;
                why = "<red>No puedes dormir durante <reset>" + a.def.name;
            }
        }
        if (!block) return;
        e.setCancelled(true);
        e.getPlayer().sendActionBar(Txt.mm(why));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        double m = 1;
        if (e.getEntity() instanceof Player victim) {
            State s = state(victim);
            if (s != null) m *= s.dmgIn;
        }
        if (e instanceof EntityDamageByEntityEvent by) {
            Player attacker = by.getDamager() instanceof Player pl ? pl
                    : by.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player pl2 ? pl2 : null;
            if (attacker != null) {
                State s = state(attacker);
                if (s != null) m *= s.dmgOut;
            }
        }
        if (Math.abs(m - 1) > 1e-6) e.setDamage(e.getDamage() * m);
    }

    private Player nearest(Location loc, double max) {
        Player best = null;
        double bd = max * max;
        for (Player p : loc.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(loc);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    /** Apariciones naturales: bloquear, multiplicar y reemplazar por mobs de la historia. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (spawning || e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        LivingEntity le = e.getEntity();
        if (blocked.contains(le.getType())) {
            e.setCancelled(true);
            return;
        }
        if (!(le instanceof Enemy)) return;
        Location loc = e.getLocation();
        Player near = nearest(loc, 96);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String type = le.getType().getKey().getKey();
        for (SpawnRule rule : spawnRules) {
            if (!rule.replaces.isEmpty() && !rule.replaces.contains(type) && !rule.replaces.contains("cualquiera")) continue;
            if (r.nextDouble() >= rule.chance || !test(rule.when, loc, near)) continue;
            e.setCancelled(true);
            int n = r.nextInt(rule.min, rule.max + 1);
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (int i = 0; i < n; i++) spawnMob(rule.mob, i == 0 ? loc : plugin.mobs().spawnSpot(loc, 3));
            });
            return;
        }
        double m = spawnMult;
        State s = near == null ? null : state(near);
        if (s != null) m *= s.spawns;
        if (m < 1) {
            if (r.nextDouble() >= m) e.setCancelled(true);
            return;
        }
        double extra = Math.min(4, m - 1);
        int copies = (int) extra + (r.nextDouble() < extra - (int) extra ? 1 : 0);
        if (copies <= 0) return;
        int around = 0;
        for (var ent : le.getWorld().getNearbyEntities(loc, 40, 20, 40)) if (ent instanceof Enemy) around++;
        if (around >= plugin.getConfig().getInt("mundo.maximo-enemigos-cerca", 30)) return;
        EntityType et = le.getType();
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (int i = 0; i < copies; i++) {
                Location at = plugin.mobs().spawnSpot(loc, 4);
                // razón NATURAL para que también puedan salir Corrompidos; "spawning" evita que se multipliquen sin fin
                spawning = true;
                try {
                    at.getWorld().spawnEntity(at, et, CreatureSpawnEvent.SpawnReason.NATURAL);
                } finally {
                    spawning = false;
                }
            }
        });
    }

    /** Resumen legible del estado del mundo (comando /evento). */
    public String describe() {
        World w = main();
        if (w == null) return "sin mundo";
        return "clima " + weatherOf(w) + ", " + momentOf(w) + ", luna " + moonOf(w) + ", día " + (w.getFullTime() / 24000L + 1);
    }
}
