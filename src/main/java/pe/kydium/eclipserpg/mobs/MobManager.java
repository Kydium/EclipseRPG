package pe.kydium.eclipserpg.mobs;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.data.PlayerData;
import pe.kydium.eclipserpg.util.Keys;
import pe.kydium.eclipserpg.util.Reg;
import pe.kydium.eclipserpg.util.Txt;

import java.io.File;
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

/** Corrompidos, jefes, corrupción natural de mobs, barras de jefe y recompensas. */
public final class MobManager implements Listener {

    public record Prize(String item, int amount, boolean once) {}

    public record Drop(String item, int amount, double chance) {}

    /** Habilidad de jefe. tipo: ONDA, SALTO, TELEPORTE, INVOCAR, FLECHAS, FUEGO, RAYO, ATRAER, EFECTO, EMBESTIDA, CLONES, METEOROS, ESCUDO, CADENAS */
    public record Ability(String type, int every, double power, double radius, String extra, int amount) {}

    public static final class MobDef {
        public String id;
        public EntityType type;
        public String name;
        public double health;
        public double damageExtra;   // se suma al daño base del mob
        public double damage;        // si > 0, reemplaza el daño base
        public double speed;         // % extra de velocidad (0.2 = +20%)
        public double scale = 1.0;
        public boolean boss;
        public String barColor = "PURPLE";
        public long xp;
        public long soles;
        public final Map<String, String> equipment = new LinkedHashMap<>();
        public final List<String> effects = new ArrayList<>();
        public final List<Prize> prizes = new ArrayList<>();
        public final List<Drop> drops = new ArrayList<>();
        public final List<String> minions = new ArrayList<>();
        public int minionCount = 3;
        public double regen;          // vida que recupera por segundo
        public Boolean corpse;        // cuerpo: false = desaparece al morir (null = lo de mundo.yml)
        public String role;           // enemigo | neutral | pacifico | guardian (null = lo normal del mob)
        public double armor;
        public double toughness;
        public double knockbackResist;
        public boolean enrage;
        public String aura;
        public double startHealth = 1.0;                 // vida-inicial: 0.7 = empieza herido (gólems agrietados)
        public final List<Ability> abilities = new ArrayList<>();
    }

    public record Tier(double health, double damage, long xp, long soles) {}

    public static final class VanillaBoss {
        public long xp;
        public long soles;
        public double radius = 64;
        public final List<Prize> prizes = new ArrayList<>();
    }

    private static final Set<EntityType> NEVER_CORRUPT = Set.of(
            EntityType.WITHER, EntityType.ENDER_DRAGON, EntityType.WARDEN, EntityType.ELDER_GUARDIAN);

    private static final double MAX_HP = 1024;

    private final EclipseRPG plugin;
    private final Map<String, MobDef> defs = new LinkedHashMap<>();
    private final Map<EntityType, VanillaBoss> vanillaBosses = new HashMap<>();
    private final List<Tier> tiers = new ArrayList<>();
    private final Map<String, String> names = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();

    /** ¿Es un jefe? (de mobs.yml con jefe: true, o con barra: Guardián de Grieta…) */
    public boolean isBoss(Entity e) {
        if (bars.containsKey(e.getUniqueId())) return true;
        if (!(e instanceof LivingEntity le)) return false;
        String id = le.getPersistentDataContainer().get(Keys.MOB, PersistentDataType.STRING);
        MobDef d = id == null ? null : defs.get(id);
        return d != null && d.boss;
    }

    /** ¿Tiene barra de jefe arriba? (esos no muestran la vida en el nombre) */
    public boolean hasBar(Entity e) {
        return bars.containsKey(e.getUniqueId());
    }
    private final Map<UUID, Set<UUID>> barViewers = new HashMap<>();

    public MobManager(EclipseRPG plugin) {
        this.plugin = plugin;
    }

    // ---------- Carga ----------

    public void load() {
        defs.clear();
        vanillaBosses.clear();
        tiers.clear();
        names.clear();

        ConfigurationSection cor = plugin.getConfig().getConfigurationSection("corrupcion");
        if (cor != null) {
            for (Map<?, ?> m : cor.getMapList("tiers")) {
                tiers.add(new Tier(num(m.get("vida"), 30), num(m.get("danio"), 2),
                        (long) num(m.get("xp"), 10), (long) num(m.get("soles"), 2)));
            }
            ConfigurationSection n = cor.getConfigurationSection("nombres");
            if (n != null) for (String k : n.getKeys(false)) names.put(k.toUpperCase(Locale.ROOT), n.getString(k));
        }
        if (tiers.isEmpty()) tiers.add(new Tier(30, 2, 10, 2));

        YamlConfiguration y = plugin.story().yaml("mobs");
        ConfigurationSection root = y.getConfigurationSection("mobs");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                if (s == null) continue;
                EntityType type = Reg.entity(s.getString("tipo", "zombie"));
                if (type == null || !type.isAlive()) {
                    plugin.getLogger().warning("mobs.yml: tipo inválido en " + id);
                    continue;
                }
                MobDef d = new MobDef();
                d.id = id;
                d.type = type;
                d.name = s.getString("nombre", id);
                d.health = s.getDouble("vida", 20);
                d.damageExtra = s.getDouble("danio-extra", 0);
                d.damage = s.getDouble("danio", 0);
                d.speed = s.getDouble("velocidad", 0);
                d.scale = s.getDouble("escala", 1.0);
                d.boss = s.getBoolean("jefe", false);
                d.barColor = s.getString("color-barra", d.boss ? "RED" : "PURPLE");
                d.xp = s.getLong("xp", 10);
                d.soles = s.getLong("soles", 2);
                ConfigurationSection eq = s.getConfigurationSection("equipo");
                if (eq != null) for (String k : eq.getKeys(false)) d.equipment.put(k, eq.getString(k));
                d.effects.addAll(s.getStringList("efectos"));
                d.prizes.addAll(readPrizes(s.getMapList("premios")));
                for (Map<?, ?> m : s.getMapList("botin")) {
                    Object it = m.get("item");
                    if (it == null) continue;
                    d.drops.add(new Drop(it.toString(), (int) num(m.get("cantidad"), 1), num(m.get("probabilidad"), 1)));
                }
                d.minions.addAll(s.getStringList("esbirros"));
                d.minionCount = s.getInt("cantidad-esbirros", 3);
                d.regen = s.getDouble("regeneracion", 0);
                d.role = pe.kydium.eclipserpg.npc.Combat.normRole(s.getString("rol"));
                if (s.contains("cuerpo")) d.corpse = s.getBoolean("cuerpo");
                d.armor = s.getDouble("armadura", 0);
                d.toughness = s.getDouble("dureza", 0);
                d.knockbackResist = s.getDouble("resistencia-empuje", d.boss ? 0.8 : 0);
                d.enrage = s.getBoolean("furia", d.boss);
                d.aura = s.getString("aura");
                d.startHealth = Math.max(0.05, Math.min(1.0, s.getDouble("vida-inicial", 1.0)));
                for (Map<?, ?> m : s.getMapList("habilidades")) {
                    Object t = m.get("tipo");
                    if (t == null) continue;
                    d.abilities.add(new Ability(t.toString().toUpperCase(Locale.ROOT),
                            (int) Math.max(1, num(m.get("cada"), 10)), num(m.get("potencia"), 6),
                            num(m.get("radio"), 6), m.get("extra") == null ? null : m.get("extra").toString(),
                            (int) num(m.get("cantidad"), 1)));
                }
                defs.put(id, d);
            }
        }

        ConfigurationSection vb = y.getConfigurationSection("jefes-vanilla");
        if (vb != null) {
            for (String k : vb.getKeys(false)) {
                EntityType t = Reg.entity(k);
                ConfigurationSection s = vb.getConfigurationSection(k);
                if (t == null || s == null) continue;
                VanillaBoss b = new VanillaBoss();
                b.xp = s.getLong("xp", 0);
                b.soles = s.getLong("soles", 0);
                b.radius = s.getDouble("radio", 64);
                b.prizes.addAll(readPrizes(s.getMapList("premios")));
                vanillaBosses.put(t, b);
            }
        }
        plugin.getLogger().info("Mobs cargados: " + defs.size() + " · tiers: " + tiers.size());
    }

    private List<Prize> readPrizes(List<Map<?, ?>> list) {
        List<Prize> out = new ArrayList<>();
        for (Map<?, ?> m : list) {
            Object it = m.get("item");
            if (it == null) continue;
            Object once = m.get("una-vez");
            out.add(new Prize(it.toString(), (int) num(m.get("cantidad"), 1), once == null || Boolean.parseBoolean(once.toString())));
        }
        return out;
    }

    private static double num(Object o, double def) {
        if (o == null) return def;
        try {
            return Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public Set<String> ids() {
        return defs.keySet();
    }

    public MobDef def(String id) {
        return defs.get(id);
    }

    public Tier tier(int t) {
        int i = Math.max(1, Math.min(tiers.size(), t)) - 1;
        return tiers.get(i);
    }

    // ---------- Aparición ----------

    public LivingEntity spawn(String id, Location loc) {
        return spawn(id, loc, 1.0, null, false);
    }

    /** Hace aparecer un mob de mobs.yml. hpMult y nameOverride sirven para variantes (Guardián de Grieta). */
    public LivingEntity spawn(String id, Location loc, double hpMult, String nameOverride, boolean forceBar) {
        MobDef d = defs.get(id);
        if (d == null || loc.getWorld() == null) return null;
        Entity ent = loc.getWorld().spawnEntity(loc, d.type);
        if (!(ent instanceof LivingEntity le)) {
            ent.remove();
            return null;
        }
        if (le instanceof org.bukkit.entity.Slime sl) sl.setSize(d.boss ? 4 : Math.max(1, sl.getSize()));
        String name = nameOverride != null ? nameOverride : d.name;
        le.customName(Txt.mm(name));
        // el nombre solo se ve al mirarlo de cerca (como en Minecraft); los jefes lo muestran siempre
        le.setCustomNameVisible(d.boss || forceBar || plugin.getConfig().getBoolean("dificultad.nombres-siempre-visibles", false));

        boolean bossLike = d.boss || forceBar;
        double hpDiff = plugin.getConfig().getDouble(bossLike ? "dificultad.vida-jefes" : "dificultad.vida-corrompidos", 1.0);
        double dmgDiff = plugin.getConfig().getDouble(bossLike ? "dificultad.danio-jefes" : "dificultad.danio-corrompidos", 1.0);
        double totalHp = d.health * hpMult * hpDiff;
        if (bossLike) {
            int players = 0;
            for (Player p : loc.getWorld().getPlayers()) {
                if (p.getGameMode() != GameMode.SPECTATOR && p.getLocation().distanceSquared(loc) < 48 * 48) players++;
            }
            double perPlayer = plugin.getConfig().getDouble("dificultad.vida-extra-por-jugador", 0.5);
            totalHp *= 1.0 + perPlayer * Math.max(0, players - 1);
        }
        // Minecraft limita la vida a 1024: el resto se simula reduciendo el daño recibido
        double realHp = Math.min(totalHp, MAX_HP);
        setBase(le, Attribute.MAX_HEALTH, realHp);
        le.setHealth(Math.min(realHp, maxHealth(le)));
        if (d.startHealth < 1.0) le.setHealth(Math.max(1, maxHealth(le) * d.startHealth));
        if (le instanceof org.bukkit.entity.IronGolem golem) golem.setPlayerCreated(false);
        if (le instanceof org.bukkit.entity.Wolf wolf && "enemigo".equals(d.role)) wolf.setAngry(true);
        if (totalHp > MAX_HP) le.getPersistentDataContainer().set(Keys.DMG_DIV, PersistentDataType.DOUBLE, totalHp / MAX_HP);
        AttributeInstance dmg = le.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmg != null) {
            if (d.damage > 0) dmg.setBaseValue(d.damage * dmgDiff);
            else dmg.setBaseValue((dmg.getBaseValue() + d.damageExtra) * dmgDiff);
        }
        if (d.armor > 0) setBase(le, Attribute.ARMOR, d.armor);
        if (d.toughness > 0) setBase(le, Attribute.ARMOR_TOUGHNESS, d.toughness);
        if (d.knockbackResist > 0) setBase(le, Attribute.KNOCKBACK_RESISTANCE, Math.min(1, d.knockbackResist));
        AttributeInstance spd = le.getAttribute(Attribute.MOVEMENT_SPEED);
        if (spd != null && d.speed != 0) spd.setBaseValue(spd.getBaseValue() * (1.0 + d.speed));
        if (d.scale != 1.0) setBase(le, Attribute.SCALE, d.scale);
        AttributeInstance follow = le.getAttribute(Attribute.FOLLOW_RANGE);
        if (follow != null) follow.setBaseValue(Math.max(follow.getBaseValue(), 40));

        EntityEquipment eq = le.getEquipment();
        if (eq != null) {
            for (Map.Entry<String, String> e : d.equipment.entrySet()) {
                ItemStack it = resolveItem(e.getValue());
                if (it == null) continue;
                switch (e.getKey().toLowerCase(Locale.ROOT)) {
                    case "mano" -> { eq.setItemInMainHand(it); eq.setItemInMainHandDropChance(0f); }
                    case "mano2" -> { eq.setItemInOffHand(it); eq.setItemInOffHandDropChance(0f); }
                    case "casco" -> { eq.setHelmet(it); eq.setHelmetDropChance(0f); }
                    case "pecho" -> { eq.setChestplate(it); eq.setChestplateDropChance(0f); }
                    case "piernas" -> { eq.setLeggings(it); eq.setLeggingsDropChance(0f); }
                    case "pies" -> { eq.setBoots(it); eq.setBootsDropChance(0f); }
                    default -> { }
                }
            }
        }
        for (String eff : d.effects) {
            String[] parts = eff.split(":");
            PotionEffectType type = Reg.effect(parts[0]);
            if (type == null) continue;
            int amp = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
            le.addPotionEffect(new PotionEffect(type, PotionEffect.INFINITE_DURATION, amp, false, false));
        }
        preventConversion(le);
        le.setRemoveWhenFarAway(false);
        if (le instanceof org.bukkit.entity.Wither wi) {
            wi.setInvulnerableTicks(0);
            var vb = wi.getBossBar();
            if (vb != null) { vb.setVisible(false); vb.removeAll(); }
        }
        if (le instanceof org.bukkit.entity.EnderDragon) le.remove();
        if (le instanceof Mob mob) mob.setCanPickupItems(false);

        PersistentDataContainer pdc = le.getPersistentDataContainer();
        pdc.set(Keys.MOB, PersistentDataType.STRING, d.id);
        if (hpMult != 1.0) pdc.set(Keys.BOSS_HP, PersistentDataType.DOUBLE, hpMult);
        if (d.boss || forceBar) createBar(le, name, d.barColor);
        return le;
    }

    /** Convierte un mob normal en Corrompido del tier indicado. */
    public void corrupt(LivingEntity le, int tierNum) {
        Tier t = tier(tierNum);
        String typeKey = le.getType().getKey().getKey().toUpperCase(Locale.ROOT);
        String base = names.getOrDefault(typeKey, Txt.pretty(typeKey));
        String adj = base.endsWith("a") ? "Corrompida" : "Corrompido";
        le.customName(Txt.mm("<dark_purple>☠ " + base + " " + adj + " <gray>[T" + tierNum + "]"));
        le.setCustomNameVisible(false);
        double hp = Math.min(MAX_HP, t.health() * plugin.getConfig().getDouble("dificultad.vida-corrompidos", 1.0));
        setBase(le, Attribute.MAX_HEALTH, hp);
        le.setHealth(Math.min(hp, maxHealth(le)));
        AttributeInstance dmg = le.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue((dmg.getBaseValue() + t.damage()) * plugin.getConfig().getDouble("dificultad.danio-corrompidos", 1.0));
        setBase(le, Attribute.ARMOR, Math.min(20, tierNum * 1.5));
        preventConversion(le);
        le.getPersistentDataContainer().set(Keys.MOB, PersistentDataType.STRING, "natural");
        le.getPersistentDataContainer().set(Keys.TIER, PersistentDataType.INTEGER, tierNum);
    }

    private void preventConversion(LivingEntity le) {
        if (le instanceof PiglinAbstract pa) pa.setImmuneToZombification(true);
        if (le instanceof Hoglin h) h.setImmuneToZombification(true);
    }

    private static void setBase(LivingEntity le, Attribute a, double v) {
        AttributeInstance inst = le.getAttribute(a);
        if (inst != null) inst.setBaseValue(v);
    }

    public static double maxHealth(LivingEntity le) {
        AttributeInstance inst = le.getAttribute(Attribute.MAX_HEALTH);
        return inst == null ? 20 : inst.getValue();
    }

    private ItemStack resolveItem(String spec) {
        if (spec == null) return null;
        if (spec.startsWith("custom:")) return plugin.items().create(spec.substring(7), 1);
        Material m = Material.matchMaterial(spec);
        return m == null ? null : new ItemStack(m);
    }

    /** Busca un lugar libre cerca del jugador para que aparezca un mob. */
    public Location spawnSpot(Location center, int radius) {
        World w = center.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int tries = 0; tries < 30; tries++) {
            int dx = r.nextInt(-radius, radius + 1);
            int dz = r.nextInt(-radius, radius + 1);
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2 && tries < 20) continue;
            int bx = center.getBlockX() + dx;
            int bz = center.getBlockZ() + dz;
            for (int dy = 6; dy >= -8; dy--) {
                int by = center.getBlockY() + dy;
                if (by <= w.getMinHeight() || by + 1 >= w.getMaxHeight()) continue;
                Block feet = w.getBlockAt(bx, by, bz);
                Block head = feet.getRelative(0, 1, 0);
                Block ground = feet.getRelative(0, -1, 0);
                if (feet.isPassable() && head.isPassable() && ground.getType().isSolid() && !feet.isLiquid() && !head.isLiquid()) {
                    return new Location(w, bx + 0.5, by, bz + 0.5);
                }
            }
        }
        // nada libre cerca: encima de lo más alto (nunca dentro de un bloque)
        int top = w.getHighestBlockYAt(center.getBlockX(), center.getBlockZ());
        return new Location(w, center.getBlockX() + 0.5, top + 1, center.getBlockZ() + 0.5);
    }

    // ---------- Barras de jefe ----------

    private void createBar(LivingEntity le, String name, String color) {
        BossBar.Color c;
        try {
            c = BossBar.Color.valueOf(color.toUpperCase(Locale.ROOT));
            if (c == BossBar.Color.WHITE) c = BossBar.Color.PINK;   // las blancas son invisibles (panel de misión)
        } catch (IllegalArgumentException e) {
            c = BossBar.Color.RED;
        }
        bars.put(le.getUniqueId(), BossBar.bossBar(Txt.mm(name), 1f, c, BossBar.Overlay.NOTCHED_10));
        barViewers.put(le.getUniqueId(), new HashSet<>());
    }

    public void startBarTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickBars, 20L, 10L);
    }

    private void tickBars() {
        for (UUID id : new ArrayList<>(bars.keySet())) {
            BossBar bar = bars.get(id);
            Entity ent = Bukkit.getEntity(id);
            Set<UUID> viewers = barViewers.get(id);
            if (!(ent instanceof LivingEntity le) || le.isDead() || !le.isValid()) {
                removeBar(id);
                continue;
            }
            bar.progress((float) Math.max(0, Math.min(1, le.getHealth() / maxHealth(le))));
            for (Player p : le.getWorld().getPlayers()) {
                boolean near = p.getLocation().distanceSquared(le.getLocation()) < 48 * 48;
                if (near && viewers.add(p.getUniqueId())) p.showBossBar(bar);
                else if (!near && viewers.remove(p.getUniqueId())) p.hideBossBar(bar);
            }
            viewers.removeIf(u -> {
                Player p = Bukkit.getPlayer(u);
                if (p == null) return true;
                if (p.getWorld() != le.getWorld()) {
                    p.hideBossBar(bar);
                    return true;
                }
                return false;
            });
        }
    }

    private void removeBar(UUID id) {
        BossBar bar = bars.remove(id);
        Set<UUID> viewers = barViewers.remove(id);
        if (bar == null || viewers == null) return;
        for (UUID u : viewers) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) p.hideBossBar(bar);
        }
    }

    /** Regeneración de vida de los mobs que la tienen (regeneracion: vida por segundo). */
    public void startRegenTask() {
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            boolean any = false;
            for (MobDef d : defs.values()) if (d.regen > 0) any = true;
            if (!any) return;
            for (org.bukkit.World w : org.bukkit.Bukkit.getWorlds()) {
                for (LivingEntity le : w.getLivingEntities()) {
                    String id = le.getPersistentDataContainer().get(Keys.MOB, PersistentDataType.STRING);
                    if (id == null) continue;
                    MobDef d = defs.get(id);
                    if (d == null || d.regen <= 0 || le.isDead()) continue;
                    Double div = le.getPersistentDataContainer().get(Keys.DMG_DIV, PersistentDataType.DOUBLE);
                    double heal = d.regen / (div == null ? 1 : div);
                    le.setHealth(Math.min(maxHealth(le), le.getHealth() + heal));
                }
            }
        }, 40L, 20L);
    }

    public void cleanup() {
        for (UUID id : new ArrayList<>(bars.keySet())) removeBar(id);
    }

    // ---------- Eventos ----------

    @EventHandler(ignoreCancelled = true)
    public void onNaturalSpawn(CreatureSpawnEvent e) {
        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        LivingEntity le = e.getEntity();
        if (!(le instanceof Enemy) || NEVER_CORRUPT.contains(le.getType())) return;
        double chance = plugin.getConfig().getDouble("corrupcion.probabilidad", 0.12);
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;
        double radius = plugin.getConfig().getDouble("corrupcion.radio-jugador", 64);
        Player nearest = null;
        double best = radius * radius;
        for (Player p : le.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(le.getLocation());
            if (d < best) {
                best = d;
                nearest = p;
            }
        }
        if (nearest == null) return;
        corrupt(le, plugin.levels().tier(nearest));
    }

    /** Reduce el daño a jefes con más de 1024 de vida y activa la furia. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBossHit(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity le)) return;
        PersistentDataContainer pdc = le.getPersistentDataContainer();
        Double div = pdc.get(Keys.DMG_DIV, PersistentDataType.DOUBLE);
        if (div != null && div > 1) e.setDamage(e.getDamage() / div);
        String id = pdc.get(Keys.MOB, PersistentDataType.STRING);
        MobDef d = id == null ? null : defs.get(id);
        if (d == null || !d.enrage || pdc.has(Keys.ENRAGED, PersistentDataType.BYTE)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (le.isDead() || le.getHealth() > maxHealth(le) * 0.3) return;
            if (le.getPersistentDataContainer().has(Keys.ENRAGED, PersistentDataType.BYTE)) return;
            le.getPersistentDataContainer().set(Keys.ENRAGED, PersistentDataType.BYTE, (byte) 1);
            PotionEffectType str = Reg.effect("strength");
            PotionEffectType spd = Reg.effect("speed");
            if (str != null) le.addPotionEffect(new PotionEffect(str, PotionEffect.INFINITE_DURATION, 1, false, true));
            if (spd != null) le.addPotionEffect(new PotionEffect(spd, PotionEffect.INFINITE_DURATION, 0, false, true));
            le.setGlowing(true);
            for (Player p : le.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(le.getLocation()) < 48 * 48) {
                    Txt.send(p, "<dark_red><bold>¡" + d.name + "<dark_red><bold> entra en FURIA!");
                    Txt.sound(p, "entity.ravager.roar", 1f, 0.7f);
                }
            }
        });
    }

    /** Jefes vivos con barra (para las habilidades). */
    public java.util.Collection<UUID> activeBosses() {
        return new ArrayList<>(bars.keySet());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity le)) return;
        String id = le.getPersistentDataContainer().get(Keys.MOB, PersistentDataType.STRING);
        if (id == null) return;
        MobDef d = defs.get(id);
        if (d == null || d.minions.isEmpty()) return;
        if (le.getPersistentDataContainer().has(Keys.PHASE, PersistentDataType.BYTE)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (le.isDead() || le.getHealth() > maxHealth(le) / 2) return;
            if (le.getPersistentDataContainer().has(Keys.PHASE, PersistentDataType.BYTE)) return;
            le.getPersistentDataContainer().set(Keys.PHASE, PersistentDataType.BYTE, (byte) 1);
            for (Player p : le.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(le.getLocation()) < 48 * 48) {
                    Txt.send(p, "<red>¡" + d.name + "<red> invoca refuerzos!");
                    Txt.sound(p, "entity.evoker.prepare_summon", 1f, 0.8f);
                }
            }
            for (int i = 0; i < d.minionCount; i++) {
                String m = d.minions.get(ThreadLocalRandom.current().nextInt(d.minions.size()));
                spawn(m, spawnSpot(le.getLocation(), 4));
            }
        });
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        LivingEntity le = e.getEntity();
        removeBar(le.getUniqueId());
        String id = le.getPersistentDataContainer().get(Keys.MOB, PersistentDataType.STRING);
        Player killer = le.getKiller();

        VanillaBoss vb = vanillaBosses.get(le.getType());
        if (id == null && vb != null) {
            for (Player p : participants(le, vb.radius, killer)) {
                plugin.levels().reward(p, vb.xp, vb.soles);
                givePrizes(p, vb.prizes, le.getType().getKey().getKey());
            }
            return;
        }
        if (id == null) return;

        if (id.equals("natural")) {
            if (killer == null) return;
            Integer t = le.getPersistentDataContainer().get(Keys.TIER, PersistentDataType.INTEGER);
            int tn = t == null ? 1 : t;
            Tier tier = tier(tn);
            corruptReward(e, killer, tn, tier.xp(), tier.soles());
            return;
        }

        MobDef d = defs.get(id);
        if (d == null) return;
        Double mult = le.getPersistentDataContainer().get(Keys.BOSS_HP, PersistentDataType.DOUBLE);
        double m = mult == null ? 1.0 : mult;
        if (d.boss) {
            for (Player p : participants(le, 32, killer)) {
                plugin.levels().reward(p, d.xp, d.soles);
                givePrizes(p, d.prizes, d.id);
                ItemStack hearts = plugin.items().create("corazon_corrupto", plugin.getConfig().getInt("corrupcion.recompensas.corazones-jefe", 2));
                if (hearts != null) for (ItemStack left : p.getInventory().addItem(hearts).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
                Txt.send(p, "<gold>¡Derrotaste a " + d.name + "<gold>!");
                Txt.sound(p, "ui.toast.challenge_complete", 1f, 1f);
            }
        } else if (killer != null) {
            Integer t = le.getPersistentDataContainer().get(Keys.TIER, PersistentDataType.INTEGER);
            corruptReward(e, killer, t == null ? Math.max(1, plugin.levels().tier(killer)) : t, Math.round(d.xp * m), Math.round(d.soles * m));
        }
        if (killer != null) {
            double luck = plugin.levels().lootMultiplier(killer);
            for (Drop dr : d.drops) {
                if (ThreadLocalRandom.current().nextDouble() < dr.chance() * luck) {
                    ItemStack it = resolveItem(dr.item().contains(":") ? dr.item() : "custom:" + dr.item());
                    if (it != null) {
                        it.setAmount(dr.amount());
                        e.getDrops().add(it);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- premio por Corrompido

    private record Streak(int count, long until) {}

    private final Map<UUID, Streak> streaks = new HashMap<>();

    /** "Racha x7 · +60 % de soles" mientras dure (para el panel), o null. */
    public String streakText(Player p) {
        Streak s = streaks.get(p.getUniqueId());
        if (s == null || s.count() < 3 || System.currentTimeMillis() > s.until()) return null;
        return "Racha x" + s.count() + " · +" + streakBonus(s.count()) + "% soles";
    }

    private int streakBonus(int n) {
        var c = plugin.getConfig();
        int per = c.getInt("corrupcion.recompensas.racha-por-muerte", 10), max = c.getInt("corrupcion.recompensas.racha-maxima", 100);
        return n < 3 ? 0 : Math.min(max, (n - 2) * per);
    }

    /**
     * Matar un Corrompido vale la pena: soles y XP (más si es élite o llevas racha), Esencia Corrupta para vender,
     * a veces un Fragmento de Mejora o una esmeralda, y un "+soles" que flota donde cayó.
     */
    private void corruptReward(EntityDeathEvent e, Player killer, int tierNum, long xp, long soles) {
        var c = plugin.getConfig();
        LivingEntity le = e.getEntity();
        boolean elite = le.getScoreboardTags().contains("eclipse_elite");
        long now = System.currentTimeMillis();
        Streak old = streaks.get(killer.getUniqueId());
        int n = old != null && now <= old.until() ? old.count() + 1 : 1;
        streaks.put(killer.getUniqueId(), new Streak(n, now + c.getLong("corrupcion.recompensas.racha-segundos", 12) * 1000L));
        double mult = c.getDouble("corrupcion.recompensas.multiplicador-soles", 2.0) * (1 + streakBonus(n) / 100.0) * (elite ? 3 : 1);
        long gain = Math.max(1, Math.round(soles * mult));
        long gxp = Math.round(xp * c.getDouble("corrupcion.recompensas.multiplicador-xp", 1.5) * (elite ? 2 : 1));
        plugin.levels().reward(killer, gxp, gain);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double luck = plugin.levels().lootMultiplier(killer);
        // Esencia Corrupta: se vende en las tiendas (Pip, el mercado del Errante, Zahir y Varo)
        double pe = c.getDouble("corrupcion.recompensas.prob-esencia", 0.35) + 0.03 * tierNum;
        if (elite || r.nextDouble() < pe * luck) {
            ItemStack es = plugin.items().create("esencia_corrupta", elite ? 2 + r.nextInt(2) : 1);
            if (es != null) e.getDrops().add(es);
        }
        // Corazón Corrupto: raro; Sabina la Coleccionista lo cambia por objetos únicos
        double ph = c.getDouble("corrupcion.recompensas.prob-corazon", 0.03) + 0.005 * tierNum + (elite ? 0.35 : 0);
        if (r.nextDouble() < ph * luck) dropHeart(e, killer, 1);
        double pf = c.getDouble("corrupcion.prob-fragmento", 0.01) + 0.003 * tierNum + (elite ? 0.10 : 0);
        maybeDropFragment(e, killer, pf);
        if (r.nextDouble() < (0.02 + 0.01 * tierNum + (elite ? 0.25 : 0)) * luck) e.getDrops().add(new ItemStack(Material.EMERALD, 1 + (elite ? r.nextInt(3) : 0)));
        if (n >= 3 && (n == 3 || n % 5 == 0)) Txt.sound(killer, "entity.experience_orb.pickup", 0.8f, Math.min(2f, 0.8f + n * 0.05f));
        floatText(le.getLocation().add(0, le.getHeight() + 0.3, 0),
                "<yellow>+" + gain + " ☀" + (elite ? " <gold>★ Élite" : "") + (n >= 3 ? " <light_purple>x" + n : ""));
    }

    private void dropHeart(EntityDeathEvent e, Player killer, int n) {
        ItemStack h = plugin.items().create("corazon_corrupto", n);
        if (h == null) return;
        e.getDrops().add(h);
        Txt.sound(killer, "block.amethyst_block.resonate", 1f, 0.6f);
        killer.showTitle(net.kyori.adventure.title.Title.title(Txt.mm(""), Txt.mm("<dark_purple>♥ ¡Un Corazón Corrupto!"),
                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(100), java.time.Duration.ofMillis(1500), java.time.Duration.ofMillis(400))));
        var key = Keys.k("aviso_corazon");
        if (!killer.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            killer.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            Txt.send(killer, "<light_purple>💎 Sabina la Coleccionista, junto a la fuente de la aldea, cambia Corazones y Esencias Corruptas por objetos raros.");
        }
    }

    /** Texto que sube y desaparece (lo ven todos los que están cerca). */
    private void floatText(Location at, String mini) {
        if (!plugin.getConfig().getBoolean("corrupcion.recompensas.texto-flotante", true) || at.getWorld() == null) return;
        org.bukkit.entity.TextDisplay td = at.getWorld().spawn(at, org.bukkit.entity.TextDisplay.class, t -> {
            t.text(Txt.mm(mini));
            t.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            t.setPersistent(false);
            t.setShadowed(true);
            t.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
            t.setTeleportDuration(3);
            t.addScoreboardTag("eclipse_flotante");
        });
        final int[] k = {0};
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!td.isValid() || ++k[0] > 12) {
                td.remove();
                task.cancel();
                return;
            }
            td.teleport(td.getLocation().add(0, 0.12, 0));
        }, 2L, 2L);
    }

    private void maybeDropFragment(EntityDeathEvent e, Player killer, double chance) {
        if (ThreadLocalRandom.current().nextDouble() < chance * plugin.levels().lootMultiplier(killer)) {
            // los Corrompidos de tier alto a veces sueltan fragmentos mejores
            int tier = plugin.levels().tier(killer);
            double r = ThreadLocalRandom.current().nextDouble();
            String id = tier >= 8 && r < 0.08 ? "fragmento_mejora_ancestral" : tier >= 5 && r < 0.3 ? "fragmento_mejora_superior" : "fragmento_mejora";
            ItemStack frag = plugin.items().create(id, 1);
            if (frag == null) frag = plugin.items().create("fragmento_mejora", 1);
            if (frag != null) e.getDrops().add(frag);
        }
    }

    private List<Player> participants(LivingEntity le, double radius, Player killer) {
        List<Player> out = new ArrayList<>();
        for (Player p : le.getWorld().getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            if (p.getLocation().distanceSquared(le.getLocation()) <= radius * radius) out.add(p);
        }
        if (killer != null && !out.contains(killer)) out.add(killer);
        return out;
    }

    /** Entrega premios; los marcados "una-vez" solo se dan la primera vez a cada jugador. */
    private void givePrizes(Player p, List<Prize> prizes, String source) {
        PlayerData data = plugin.data().get(p);
        long compensation = plugin.getConfig().getLong("jefes.soles-si-repite", 200);
        boolean compensated = false;
        for (Prize pr : prizes) {
            String key = source + ":" + pr.item();
            if (pr.once() && data.rewards.contains(key)) {
                if (!compensated && compensation > 0) {
                    plugin.levels().addSoles(p, compensation);
                    Txt.send(p, "<gray>Ya tenías el premio de este jefe: recibes <yellow>" + compensation + " ☀</yellow>.");
                    compensated = true;
                }
                continue;
            }
            ItemStack it = plugin.items().create(pr.item(), pr.amount());
            if (it == null) {
                Material mat = Material.matchMaterial(pr.item());
                if (mat != null) it = new ItemStack(mat, pr.amount());
            }
            if (it == null) continue;
            plugin.items().give(p, it);
            if (pr.once()) data.rewards.add(key);
            data.dirty = true;
        }
    }
}
