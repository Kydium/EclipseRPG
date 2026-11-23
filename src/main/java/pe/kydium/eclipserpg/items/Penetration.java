package pe.kydium.eclipserpg.items;

import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.util.Keys;

import java.util.List;

/**
 * Penetración: parte de la armadura del enemigo que tu golpe ignora. Es lo que sirve contra los jefes,
 * que tienen mucha armadura y a los que los demás poderes no les hacen efecto.
 * <ul>
 *   <li>Cada nivel de mejora del arma (+1…+10) da {@code dificultad.penetracion-por-mejora} (3 %).</li>
 *   <li>La runa / poder "penetracion" suma su valor en %.</li>
 *   <li>El poder "cazajefes" da más daño contra jefes.</li>
 * </ul>
 * Tope: {@code dificultad.penetracion-maxima} (80 %).
 */
public final class Penetration implements Listener {

    private final EclipseRPG plugin;

    public Penetration(EclipseRPG plugin) {
        this.plugin = plugin;
    }

    /** Quién atacó, con qué arma y qué mejora tenía (las flechas guardan la del arco). */
    private record Hit(Player player, List<Powers.Power> powers, int upgrade) {}

    private Hit hit(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) {
            ItemStack w = p.getInventory().getItemInMainHand();
            return new Hit(p, plugin.powers().of(w), plugin.items().upgradeLevel(w));
        }
        if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p) {
            Integer up = pr.getPersistentDataContainer().get(Keys.UPGRADE, PersistentDataType.INTEGER);
            List<Powers.Power> ps = pr instanceof org.bukkit.entity.Trident ? plugin.powers().of(p.getInventory().getItemInMainHand())
                    : Powers.deser(pr.getPersistentDataContainer().get(Powers.SHOT, PersistentDataType.STRING));
            return new Hit(p, ps, up == null ? 0 : up);
        }
        return null;
    }

    private static double sum(List<Powers.Power> ps, String type) {
        double t = 0;
        for (Powers.Power p : ps) if (p.type().equals(type)) t += p.value();
        return t;
    }

    /** Penetración total (0…tope) de un golpe. */
    public double of(Player p, List<Powers.Power> ps, int upgrade) {
        double pen = plugin.getConfig().getDouble("dificultad.penetracion-base", 0.0)
                + upgrade * plugin.getConfig().getDouble("dificultad.penetracion-por-mejora", 0.03)
                + sum(ps, "penetracion") / 100.0;
        return Math.max(0, Math.min(plugin.getConfig().getDouble("dificultad.penetracion-maxima", 0.8), pen));
    }

    /** Penetración del arma que el jugador tiene en la mano (para mostrarla en menús). */
    public double ofHand(Player p) {
        ItemStack w = p.getInventory().getItemInMainHand();
        return of(p, plugin.powers().of(w), plugin.items().upgradeLevel(w));
    }

    /** Cazajefes: multiplica el daño base contra un jefe. La llama BossRules antes del tope por golpe. */
    public void bossBonus(EntityDamageEvent ev) {
        if (!(ev instanceof EntityDamageByEntityEvent e)) return;
        Hit h = hit(e);
        if (h == null) return;
        double bonus = sum(h.powers(), "cazajefes") / 100.0;
        if (bonus > 0) e.setDamage(e.getDamage() * (1 + bonus));
    }

    /** Quita parte de la reducción por armadura. Debe llamarse después de cambiar el daño base. */
    public void apply(EntityDamageEvent ev) {
        if (!(ev instanceof EntityDamageByEntityEvent e) || !(e.getEntity() instanceof LivingEntity target)) return;
        Hit h = hit(e);
        if (h == null || target == h.player()) return;
        double pen = of(h.player(), h.powers(), h.upgrade());
        if (pen <= 0) return;
        try {
            @SuppressWarnings("deprecation")
            EntityDamageEvent.DamageModifier ARMOR = EntityDamageEvent.DamageModifier.ARMOR;
            if (!e.isApplicable(ARMOR)) return;
            double armor = e.getDamage(ARMOR);   // negativo: lo que la armadura quita
            if (armor >= 0) return;
            e.setDamage(ARMOR, armor * (1 - pen));
            if (pen >= 0.25 && plugin.mobs().isBoss(target))
                target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, target.getHeight() * 0.6, 0), 6, 0.3, 0.3, 0.3, 0.1);
        } catch (UnsupportedOperationException | IllegalArgumentException ex) {
            // si el servidor no deja tocar la armadura: se aproxima con más daño base
            e.setDamage(e.getDamage() * (1 + pen * 0.5));
        }
    }

    /** Monstruos normales (los jefes pasan por BossRules, que llama a apply() al final). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Entity t = e.getEntity();
        if (plugin.mobs().isBoss(t)) return;
        apply(e);
    }
}
