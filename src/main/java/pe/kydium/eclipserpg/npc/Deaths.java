package pe.kydium.eclipserpg.npc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.util.Keys;

import java.util.ArrayList;
import java.util.Set;

/**
 * Muertes del mundo vivo: el cuerpo queda tendido un rato (opcional) y las criaturas de la aldea
 * vuelven a aparecer en su sitio pasado un tiempo (como los NPCs de CustomNPCs).
 */
public final class Deaths implements Listener {

    private static final Set<EntityType> NO_BODY = Set.of(EntityType.ENDER_DRAGON, EntityType.WITHER, EntityType.SLIME, EntityType.MAGMA_CUBE,
            EntityType.ARMOR_STAND, EntityType.MANNEQUIN, EntityType.PLAYER, EntityType.GHAST, EntityType.HAPPY_GHAST, EntityType.PHANTOM);

    private final EclipseRPG plugin;
    private final Combat combat;
    public static NamespacedKey RESPAWN;

    public Deaths(EclipseRPG plugin, Combat combat) {
        this.plugin = plugin;
        this.combat = combat;
        RESPAWN = Keys.k("reaparece");
    }

    /** Marca una criatura para que reaparezca en este lugar cuando muera. */
    public static void markRespawn(LivingEntity le, String kind) {
        if (RESPAWN == null) return;
        Location l = le.getLocation();
        le.getPersistentDataContainer().set(RESPAWN, PersistentDataType.STRING,
                kind + ";" + le.getType().name() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead instanceof Player || dead instanceof Mannequin || dead.getScoreboardTags().contains(Combat.CORPSE)) return;
        // reaparición
        String rs = dead.getPersistentDataContainer().get(RESPAWN, PersistentDataType.STRING);
        if (rs != null && combat.villageBack() > 0) {
            String[] a = rs.split(";");
            try {
                String kind = a[0];
                EntityType type = EntityType.valueOf(a[1]);
                Location home = new Location(dead.getWorld(), Integer.parseInt(a[2]) + 0.5, Integer.parseInt(a[3]), Integer.parseInt(a[4]) + 0.5);
                boolean invul = dead.isInvulnerable();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (home.getWorld() == null) return;
                    Entity n = home.getWorld().spawnEntity(home, type);
                    if (n instanceof LivingEntity le) {
                        le.setPersistent(true);
                        le.setRemoveWhenFarAway(false);
                        le.setInvulnerable(invul);
                        markRespawn(le, kind);
                        home.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, home.clone().add(0, 0.8, 0), 10, 0.3, 0.4, 0.3, 0);
                    }
                }, combat.villageBack() * 20L);
            } catch (RuntimeException ignored) {
                // dato viejo
            }
        }
        if (!combat.mobCorpses() || combat.corpseSeconds() <= 0 || NO_BODY.contains(dead.getType())) return;
        String mid = dead.getPersistentDataContainer().get(Keys.MOB, PersistentDataType.STRING);
        if (mid != null) {
            var d = plugin.mobs().def(mid);
            if (d != null && (d.boss || Boolean.FALSE.equals(d.corpse))) return;
        }
        body(dead);
    }

    /** Copia quieta y tendida del que murió; se quita sola. */
    private void body(LivingEntity dead) {
        try {
            var snap = dead.createSnapshot();
            if (snap == null) return;
            Location at = dead.getLocation();
            for (int i = 0; i < 6 && at.getBlockY() > at.getWorld().getMinHeight() && at.clone().subtract(0, 0.2, 0).getBlock().isPassable(); i++) at.subtract(0, 1, 0);
            // el original se va ya: si no, su animación de caída se ve encima del cuerpo (parpadeo)
            Bukkit.getScheduler().runTask(plugin, dead::remove);
            Entity c = snap.createEntity(at);
            if (!(c instanceof LivingEntity le)) {
                if (c != null) c.remove();
                return;
            }
            le.addScoreboardTag(Combat.CORPSE);
            var max = le.getAttribute(Attribute.MAX_HEALTH);
            le.setHealth(max == null ? 1 : max.getValue());
            for (NamespacedKey k : new ArrayList<>(le.getPersistentDataContainer().getKeys()))
                if (k.getNamespace().equals(Keys.k("x").getNamespace())) le.getPersistentDataContainer().remove(k);
            le.setInvulnerable(true);
            le.setSilent(true);
            le.setCollidable(false);
            le.setPersistent(false);
            le.setFireTicks(0);
            le.setCustomNameVisible(false);
            le.setGravity(false);
            le.setVelocity(new org.bukkit.util.Vector());
            for (Entity p : le.getPassengers()) p.remove();
            if (le instanceof Mob m) {
                m.setAI(false);
                m.setTarget(null);
            }
            if (le.getEquipment() != null) for (var slot : org.bukkit.inventory.EquipmentSlot.values())
                try {
                    le.getEquipment().setDropChance(slot, 0f);
                } catch (IllegalArgumentException ignored) {
                    // ranura que este mob no tiene
                }
            le.setPose(Pose.SLEEPING, true);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!le.isValid()) return;
                le.getWorld().spawnParticle(org.bukkit.Particle.POOF, le.getLocation().add(0, 0.3, 0), 8, 0.3, 0.1, 0.3, 0.01);
                le.remove();
            }, combat.corpseSeconds() * 20L);
        } catch (RuntimeException ex) {
            plugin.getLogger().fine("No se pudo dejar el cuerpo de " + dead.getType() + ": " + ex.getMessage());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTouchBody(PlayerInteractEntityEvent e) {
        if (e.getRightClicked().getScoreboardTags().contains(Combat.CORPSE)) e.setCancelled(true);
    }
}
