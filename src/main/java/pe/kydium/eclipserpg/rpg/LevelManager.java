package pe.kydium.eclipserpg.rpg;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.permissions.PermissionAttachment;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.data.PlayerData;
import pe.kydium.eclipserpg.util.Txt;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Niveles, XP RPG, puntos de habilidad, Soles y permisos de nivel. */
public final class LevelManager implements Listener {
    private final EclipseRPG plugin;
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    public LevelManager(EclipseRPG plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    // ---------- Configuración ----------

    public int maxLevel() {
        int m = cfg().getInt("niveles.nivel-maximo", 0);
        return m <= 0 ? Integer.MAX_VALUE : m;
    }

    public long xpNeeded(int level) {
        return Math.max(1, cfg().getLong("niveles.xp-por-nivel", 150) * level);
    }

    public int pointsPerLevel() {
        return cfg().getInt("niveles.puntos-por-nivel", 1);
    }

    public double perPoint(Stat s) {
        return cfg().getDouble("stats." + s.id + ".por-punto", s.defPerPoint);
    }

    public int maxPoints(Stat s) {
        return cfg().getInt("stats." + s.id + ".maximo", s.defMax);
    }

    /** Valor secundario de un atributo (config stats.<id>.<clave>). */
    private double extra(Stat s, String key, double def) {
        return cfg().getDouble("stats." + s.id + "." + key, def);
    }

    public String statDescription(Stat s) {
        return s.summary;
    }

    /** Lo que da un atributo con `n` puntos, una línea por efecto. */
    public java.util.List<String> statEffects(Stat s, int n) {
        double pp = perPoint(s);
        return switch (s) {
            case VIDA -> java.util.List.of("+" + Txt.dec(n * pp) + " de vida máxima");
            case MENTE -> java.util.List.of("+" + Txt.dec(n * pp) + "% de probabilidad de poderes",
                    "-" + Txt.dec(Math.min(50, n * extra(s, "recarga", 1.5))) + "% de recarga de poderes");
            case DEFENSA -> java.util.List.of("+" + Txt.dec(n * pp) + " de armadura",
                    "+" + Txt.dec(Math.min(100, n * extra(s, "empuje", 2))) + "% de firmeza (menos empuje)",
                    Txt.dec(Math.min(60, n * extra(s, "hambre", 3))) + "% de no gastar hambre");
            case FUERZA -> java.util.List.of("+" + Txt.dec(n * pp) + " de daño", "+" + Txt.dec(n * extra(s, "pesadas", 1.5)) + "% con hachas y mazas");
            case AGILIDAD -> java.util.List.of("+" + Txt.dec(n * pp) + "% de velocidad", "+" + Txt.dec(n * extra(s, "ataque", 1.5)) + "% de velocidad de ataque",
                    "+" + Txt.dec(n * extra(s, "distancia", 2)) + "% de daño con arcos y tridentes");
            case INTELIGENCIA -> java.util.List.of("+" + Txt.dec(n * pp) + "% de daño de poderes mágicos");
            case FE -> java.util.List.of("+" + Txt.dec(n * pp) + "% de curación", "+" + Txt.dec(n * extra(s, "corrompidos", 2)) + "% de daño a Corrompidos");
            case FORTUNA -> java.util.List.of("+" + Txt.dec(n * pp) + "% de Soles y botín", "+" + Txt.dec(n * extra(s, "suerte", 0.1)) + " de suerte");
        };
    }

    private int pts(Player p, Stat s) {
        return plugin.data().get(p).stat(s.id);
    }

    /** Mente: cuánto sube la probabilidad de los poderes (0.2 = +20 % relativo). */
    public double mind(Player p) {
        return pts(p, Stat.MENTE) * perPoint(Stat.MENTE) / 100.0;
    }

    /** Mente: multiplicador de las recargas de poderes (1 = normal). */
    public double cooldownMult(Player p) {
        return 1 - Math.min(0.5, pts(p, Stat.MENTE) * extra(Stat.MENTE, "recarga", 1.5) / 100.0);
    }

    /** Inteligencia: multiplicador del daño de poderes mágicos. */
    public double magic(Player p) {
        return 1 + pts(p, Stat.INTELIGENCIA) * perPoint(Stat.INTELIGENCIA) / 100.0;
    }

    /** Fe: multiplicador de curación. */
    public double healMult(Player p) {
        return 1 + pts(p, Stat.FE) * perPoint(Stat.FE) / 100.0;
    }

    /** Fe: multiplicador de daño contra Corrompidos. */
    public double holyMult(Player p) {
        return 1 + pts(p, Stat.FE) * extra(Stat.FE, "corrompidos", 2) / 100.0;
    }

    /** Fuerza: multiplicador con armas pesadas (hachas y mazas). */
    public double heavyMult(Player p) {
        return 1 + pts(p, Stat.FUERZA) * extra(Stat.FUERZA, "pesadas", 1.5) / 100.0;
    }

    /** Destreza: multiplicador de daño a distancia. */
    public double rangedMult(Player p) {
        return 1 + pts(p, Stat.AGILIDAD) * extra(Stat.AGILIDAD, "distancia", 2) / 100.0;
    }

    /** Aguante: probabilidad de no gastar hambre. */
    public double hungerSave(Player p) {
        return Math.min(0.6, pts(p, Stat.DEFENSA) * extra(Stat.DEFENSA, "hambre", 3) / 100.0);
    }

    /** Tier 1..10 según el nivel: niveles 1-5 = tier 1, 6-10 = tier 2... */
    public int tierFor(int level) {
        int perTier = Math.max(1, cfg().getInt("niveles.niveles-por-tier", 5));
        return Math.max(1, Math.min(10, 1 + (level - 1) / perTier));
    }

    public int tier(Player p) {
        return tierFor(plugin.data().get(p).level);
    }

    // ---------- XP y nivel ----------

    public void addXp(Player p, long amount) {
        if (amount <= 0) return;
        PlayerData d = plugin.data().get(p);
        int before = d.level;
        if (d.level >= maxLevel()) {
            d.xp = 0;
        } else {
            d.xp += amount;
            while (d.level < maxLevel() && d.xp >= xpNeeded(d.level)) {
                d.xp -= xpNeeded(d.level);
                d.level++;
                d.points += pointsPerLevel();
            }
            if (d.level >= maxLevel()) d.xp = 0;
        }
        d.dirty = true;
        if (d.level > before) onLevelUp(p, d.level);
    }

    public void setLevel(Player p, int level) {
        PlayerData d = plugin.data().get(p);
        int lv = Math.max(1, Math.min(maxLevel(), level));
        int spent = 0;
        for (int v : d.stats.values()) spent += v;
        d.level = lv;
        d.xp = 0;
        d.points = Math.max(0, (lv - 1) * pointsPerLevel() - spent);
        d.dirty = true;
        applyAll(p);
    }

    private void onLevelUp(Player p, int level) {
        p.showTitle(Title.title(
                Txt.mm("<gold><bold>¡Nivel " + level + "!"),
                Txt.mm("<yellow>Tienes puntos de habilidad · usa <white>/nivel"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(600))));
        Txt.sound(p, "entity.player.levelup", 1f, 1.2f);
        Txt.send(p, "<gold>Subiste al nivel <yellow>" + level + "</yellow>. Usa <white>/nivel</white> para repartir tus puntos.");
        applyAll(p);
    }

    /** Da XP RPG y Soles juntos y lo muestra en la barra de acción. */
    public void reward(Player p, long xp, long soles) {
        if (plugin.world() != null) {
            xp = Math.round(xp * plugin.world().xpMult(p));
            soles = Math.round(soles * plugin.world().moneyMult(p));
        }
        long realSoles = soles > 0 ? applyFortune(p, soles) : 0;
        if (realSoles > 0) addSoles(p, realSoles);
        addXp(p, xp);
        PlayerData d = plugin.data().get(p);
        StringBuilder sb = new StringBuilder();
        if (xp > 0) sb.append("<aqua>+").append(Txt.num(xp)).append(" XP</aqua>  ");
        if (realSoles > 0) sb.append("<yellow>+").append(Txt.num(realSoles)).append(" ☀</yellow>  ");
        if (d.level < maxLevel()) {
            sb.append("<gray>Nv ").append(d.level).append(" · ")
              .append(Txt.num(d.xp)).append("/").append(Txt.num(xpNeeded(d.level)));
        } else {
            sb.append("<gold>Nivel máximo");
        }
        p.sendActionBar(Txt.mm(sb.toString()));
    }

    public long applyFortune(Player p, long soles) {
        double bonus = plugin.data().get(p).stat(Stat.FORTUNA.id) * perPoint(Stat.FORTUNA) / 100.0;
        return Math.round(soles * (1.0 + bonus));
    }

    /** Multiplicador de probabilidad de botín por Fortuna. */
    public double lootMultiplier(Player p) {
        return 1.0 + plugin.data().get(p).stat(Stat.FORTUNA.id) * perPoint(Stat.FORTUNA) / 100.0;
    }

    // ---------- Soles ----------

    public long soles(Player p) {
        return plugin.data().get(p).soles;
    }

    public void addSoles(Player p, long amount) {
        PlayerData d = plugin.data().get(p);
        d.soles = Math.max(0, d.soles + amount);
        d.dirty = true;
    }

    public boolean takeSoles(Player p, long amount) {
        PlayerData d = plugin.data().get(p);
        if (d.soles < amount) return false;
        d.soles -= amount;
        d.dirty = true;
        return true;
    }

    // ---------- Aplicar stats / permisos ----------

    public void applyAll(Player p) {
        applyStats(p);
        updatePermissions(p);
        updateListName(p);
    }

    public void applyStats(Player p) {
        PlayerData d = plugin.data().get(p);
        set(p, Attribute.MAX_HEALTH, "vida", d.stat("vida") * perPoint(Stat.VIDA), AttributeModifier.Operation.ADD_NUMBER);
        set(p, Attribute.ARMOR, "defensa", d.stat("defensa") * perPoint(Stat.DEFENSA), AttributeModifier.Operation.ADD_NUMBER);
        set(p, Attribute.KNOCKBACK_RESISTANCE, "defensa_firmeza", Math.min(1, d.stat("defensa") * extra(Stat.DEFENSA, "empuje", 2) / 100.0), AttributeModifier.Operation.ADD_NUMBER);
        set(p, Attribute.ATTACK_DAMAGE, "fuerza", d.stat("fuerza") * perPoint(Stat.FUERZA), AttributeModifier.Operation.ADD_NUMBER);
        set(p, Attribute.MOVEMENT_SPEED, "agilidad", d.stat("agilidad") * perPoint(Stat.AGILIDAD) / 100.0, AttributeModifier.Operation.ADD_SCALAR);
        set(p, Attribute.ATTACK_SPEED, "agilidad_ataque", d.stat("agilidad") * extra(Stat.AGILIDAD, "ataque", 1.5) / 100.0, AttributeModifier.Operation.ADD_SCALAR);
        set(p, Attribute.LUCK, "fortuna", d.stat("fortuna") * extra(Stat.FORTUNA, "suerte", 0.1), AttributeModifier.Operation.ADD_NUMBER);
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null && p.getHealth() > hp.getValue()) p.setHealth(hp.getValue());
    }

    private void set(Player p, Attribute attr, String id, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        NamespacedKey key = pe.kydium.eclipserpg.util.Keys.k("stat_" + id);
        for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) if (key.equals(m.getKey())) inst.removeModifier(m);
        if (amount != 0) inst.addTransientModifier(new AttributeModifier(key, amount, op, EquipmentSlotGroup.ANY));
    }

    public void updatePermissions(Player p) {
        PermissionAttachment old = attachments.remove(p.getUniqueId());
        if (old != null) {
            try {
                p.removeAttachment(old);
            } catch (IllegalArgumentException ignored) {
                // ya no estaba
            }
        }
        PermissionAttachment att = p.addAttachment(plugin);
        int lv = plugin.data().get(p).level;
        for (int i = 1; i <= lv; i++) att.setPermission("eclipserpg.nivel." + i, true);
        att.setPermission("eclipserpg.tier." + tierFor(lv), true);
        attachments.put(p.getUniqueId(), att);
    }

    public void updateListName(Player p) {
        if (!cfg().getBoolean("niveles.mostrar-en-tab", true)) return;
        int lv = plugin.data().get(p).level;
        Component name = Txt.mm("<dark_gray>[<gold>" + lv + "</gold>]</dark_gray> ").append(p.name());
        p.playerListName(name);
    }

    // ---------- Eventos ----------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.data().get(p);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) applyAll(p);
        }, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        PermissionAttachment att = attachments.remove(e.getPlayer().getUniqueId());
        if (att != null) {
            try {
                e.getPlayer().removeAttachment(att);
            } catch (IllegalArgumentException ignored) {
                // nada
            }
        }
        plugin.data().unload(e.getPlayer().getUniqueId());
    }

    // ---------- Efectos de los atributos ----------

    /** Aguante: a veces no gasta hambre. */
    @EventHandler(ignoreCancelled = true)
    public void onHunger(org.bukkit.event.entity.FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player p) || e.getFoodLevel() >= p.getFoodLevel()) return;
        if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() < hungerSave(p)) e.setCancelled(true);
    }

    /** Fe: curas más. */
    @EventHandler(ignoreCancelled = true)
    public void onHeal(org.bukkit.event.entity.EntityRegainHealthEvent e) {
        if (e.getEntity() instanceof Player p) e.setAmount(e.getAmount() * healMult(p));
    }

    /** Fuerza (hachas y mazas), Destreza (arcos y tridentes) y Fe (contra Corrompidos). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        Player p = null;
        double mult = 1;
        if (e.getDamager() instanceof Player pl) {
            p = pl;
            String w = pl.getInventory().getItemInMainHand().getType().name();
            if (w.endsWith("_AXE") || w.equals("MACE")) mult *= heavyMult(pl);
        } else if (e.getDamager() instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Player pl) {
            p = pl;
            if (pr instanceof org.bukkit.entity.AbstractArrow) mult *= rangedMult(pl);
        }
        if (p == null || e.getEntity() == p) return;
        if (plugin.npcEntities() != null && plugin.npcEntities().combat().corrupted(e.getEntity())) mult *= holyMult(p);
        if (mult != 1) e.setDamage(e.getDamage() * mult);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                applyStats(p);
                AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
                if (hp != null) p.setHealth(hp.getValue());
            }
        }, 2L);
    }
}
