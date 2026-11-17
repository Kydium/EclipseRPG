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
        return cfg().getInt("niveles.nivel-maximo", 50);
    }

    public long xpNeeded(int level) {
        return Math.max(1, cfg().getLong("niveles.xp-por-nivel", 150) * level);
    }

    public int pointsPerLevel() {
        return cfg().getInt("niveles.puntos-por-nivel", 1);
    }

    public double perPoint(Stat s) {
        return cfg().getDouble("stats." + s.id + ".por-punto", 1.0);
    }

    public int maxPoints(Stat s) {
        return cfg().getInt("stats." + s.id + ".maximo", 20);
    }

    public String statDescription(Stat s) {
        return cfg().getString("stats." + s.id + ".descripcion", "");
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
        for (Stat s : Stat.values()) {
            Attribute attr = s.attribute();
            if (attr == null) continue;
            AttributeInstance inst = p.getAttribute(attr);
            if (inst == null) continue;
            NamespacedKey key = pe.kydium.eclipserpg.util.Keys.k("stat_" + s.id);
            for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
                if (key.equals(m.getKey())) inst.removeModifier(m);
            }
            double amount = d.stat(s.id) * perPoint(s);
            if (s == Stat.AGILIDAD) amount = amount / 100.0; // por-punto está en %
            if (amount != 0) {
                inst.addTransientModifier(new AttributeModifier(key, amount, s.operation(), EquipmentSlotGroup.ANY));
            }
        }
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null && p.getHealth() > hp.getValue()) p.setHealth(hp.getValue());
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
