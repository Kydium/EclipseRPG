package pe.kydium.eclipserpg.mobs;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.SlimeSplitEvent;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.util.Txt;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Reglas de los jefes para que sean difíciles de verdad:
 * - no les afectan pociones ni efectos (veneno, lentitud, congelar, levitar…), ni el fuego, la lava, caídas o asfixia;
 * - los poderes de las armas no hacen nada contra ellos (eso lo revisa Powers);
 * - ningún golpe les quita más de cierto % de su vida (nada de matarlos de 3 golpes);
 * - desvían parte de las flechas y si los atacan de lejos sin acercarse, saltan hacia el tirador;
 * - no rompen bloques con explosiones ni se dividen (cubo de magma).
 */
public final class BossRules implements Listener {

    private final EclipseRPG plugin;
    private final Map<UUID, Long> lastClose = new HashMap<>();

    public BossRules(EclipseRPG plugin) {
        this.plugin = plugin;
    }

    private boolean boss(Entity e) {
        return e != null && plugin.mobs().isBoss(e);
    }

    /** Ningún efecto que no venga del propio jefe (sus furias y auras se ponen con cause PLUGIN). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent e) {
        if (!boss(e.getEntity())) return;
        if (e.getAction() != EntityPotionEffectEvent.Action.ADDED && e.getAction() != EntityPotionEffectEvent.Action.CHANGED) return;
        if (e.getCause() == EntityPotionEffectEvent.Cause.PLUGIN) return;   // los poderes de armas ni siquiera lo intentan (Powers)
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(EntityCombustEvent e) {
        if (boss(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity le) || !boss(le)) return;
        switch (e.getCause()) {
            case FALL, SUFFOCATION, DROWNING, CONTACT, FREEZE, FIRE, FIRE_TICK, LAVA, POISON, WITHER, MAGIC, HOT_FLOOR,
                 CRAMMING, LIGHTNING, FLY_INTO_WALL, BLOCK_EXPLOSION, ENTITY_EXPLOSION, DRAGON_BREATH, CAMPFIRE, STARVATION -> {
                e.setCancelled(true);
                le.setFireTicks(0);
                le.setFreezeTicks(0);
                return;
            }
            default -> { }
        }
        if (plugin.bossAbilities() != null && plugin.bossAbilities().shielded(le.getUniqueId())) {
            e.setCancelled(true);
            le.getWorld().spawnParticle(Particle.ENCHANTED_HIT, le.getLocation().add(0, le.getHeight() / 2, 0), 12, 0.5, 0.6, 0.5, 0.2);
            if (e instanceof EntityDamageByEntityEvent ed && attacker(ed) instanceof Player p) {
                p.sendActionBar(Txt.mm("<aqua>🛡 Su escudo lo protege: <white>mata primero a sus esbirros"));
                Txt.sound(p, "item.shield.block", 1f, 0.7f);
            }
            return;
        }
        // tope por golpe: % de su vida total (ya contando la que pasa de 1024)
        var max = le.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) {
            double cap = max.getValue() * plugin.getConfig().getDouble("dificultad.jefes.tope-por-golpe", 0.05);
            if (e.getDamage() > cap) e.setDamage(cap);
        }
    }

    /** Flechas desviadas y castigo a quien ataca de lejos sin acercarse. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity bossE) || !boss(bossE)) return;
        Entity src = attacker(e);
        if (!(src instanceof Player p)) return;
        double dist = p.getLocation().distance(bossE.getLocation());
        if (dist < 5) lastClose.put(bossE.getUniqueId(), System.currentTimeMillis());
        if (!(e.getDamager() instanceof Projectile)) return;
        if (ThreadLocalRandom.current().nextDouble() < plugin.getConfig().getDouble("dificultad.jefes.desviar-flechas", 0.3)) {
            e.setCancelled(true);
            bossE.getWorld().spawnParticle(Particle.CRIT, e.getDamager().getLocation(), 10, 0.2, 0.2, 0.2, 0.2);
            Txt.sound(p, "item.shield.block", 0.8f, 1.4f);
            p.sendActionBar(Txt.mm("<gray>Desvía la flecha…"));
            return;
        }
        long since = System.currentTimeMillis() - lastClose.getOrDefault(bossE.getUniqueId(), 0L);
        if (dist > 10 && since > 6000 && ThreadLocalRandom.current().nextDouble() < 0.5) {
            lastClose.put(bossE.getUniqueId(), System.currentTimeMillis());
            Location to = plugin.mobs().spawnSpot(p.getLocation(), 2);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!bossE.isValid() || !p.isOnline() || p.getGameMode() == GameMode.SPECTATOR) return;
                bossE.getWorld().spawnParticle(Particle.LARGE_SMOKE, bossE.getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.05);
                bossE.teleport(to);
                bossE.getWorld().spawnParticle(Particle.LARGE_SMOKE, to.clone().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.05);
                Txt.sound(p, "entity.enderman.teleport", 1f, 0.5f);
                p.sendActionBar(Txt.mm("<red>⚠ ¡No puedes esconderte de lejos!"));
                if (bossE instanceof org.bukkit.entity.Mob m) m.setTarget(p);
            });
        }
    }

    private static Entity attacker(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Entity sh) return sh;
        return e.getDamager();
    }

    /** Sus explosiones (calaveras del Wither, bolas de fuego…) no rompen la mazmorra. */
    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        Entity src = e.getEntity();
        if (src instanceof Projectile pr && pr.getShooter() instanceof Entity sh) src = sh;
        if (boss(src)) e.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlock(EntityChangeBlockEvent e) {
        if (boss(e.getEntity())) e.setCancelled(true);
    }

    /** Los clones falsos no sueltan nada. */
    @EventHandler
    public void onCloneDeath(org.bukkit.event.entity.EntityDeathEvent e) {
        if (!e.getEntity().getScoreboardTags().contains("eclipse_clon")) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSplit(SlimeSplitEvent e) {
        if (boss(e.getEntity())) e.setCancelled(true);
    }
}
