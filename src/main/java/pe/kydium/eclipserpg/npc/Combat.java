package pe.kydium.eclipserpg.npc;

import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.mobs.MobManager;
import pe.kydium.eclipserpg.util.Keys;
import pe.kydium.eclipserpg.util.Reg;
import pe.kydium.eclipserpg.util.Txt;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Roles de combate. Guardián: defiende, ataca a los hostiles y nunca a los jugadores.
 * Enemigo: ataca a jugadores y guardianes. Neutral: solo responde si lo atacan. Pacífico: nunca ataca.
 * Los guardias de la aldea (maniquíes) pelean, pueden caer heridos y se recuperan; los aldeanos huyen a casa.
 */
public final class Combat implements Listener {

    static final class G {
        double hp = -1;
        int cd, scan;
        UUID target;
        long downUntil;
        boolean panic;
    }

    private final EclipseRPG plugin;
    private final NpcEntities owner;
    private final Map<UUID, G> st = new HashMap<>();
    private final Map<EntityType, String> vanillaRoles = new HashMap<>();
    private double guardHp = 40, guardDmg = 6, vision = 14, leash = 24, speed = 0.24;
    private int recover = 45;
    private boolean flee = true, guardsFight = true;

    Combat(EclipseRPG plugin, NpcEntities owner) {
        this.plugin = plugin;
        this.owner = owner;
    }

    public void load() {
        vanillaRoles.clear();
        ConfigurationSection r = plugin.story().yaml("mundo").getConfigurationSection("roles");
        ConfigurationSection g = r == null ? null : r.getConfigurationSection("guardias");
        guardsFight = g == null || g.getBoolean("pelean", true);
        guardHp = g == null ? 40 : Math.max(1, g.getDouble("vida", 40));
        guardDmg = g == null ? 6 : Math.max(0, g.getDouble("danio", 6));
        vision = g == null ? 14 : Math.max(4, Math.min(32, g.getDouble("vision", 14)));
        leash = g == null ? 24 : Math.max(vision, g.getDouble("persecucion", 24));
        recover = g == null ? 45 : Math.max(5, g.getInt("recuperacion", 45));
        flee = r == null || r.getBoolean("aldeanos-huyen", true);
        ConfigurationSection m = r == null ? null : r.getConfigurationSection("mobs");
        if (m != null) for (String k : m.getKeys(false)) {
            EntityType t = Reg.entity(k);
            String role = normRole(m.getString(k));
            if (t != null && role != null) vanillaRoles.put(t, role);
        }
        st.clear();
    }

    public static String normRole(String s) {
        if (s == null) return null;
        String k = s.trim().toLowerCase(Locale.ROOT).replace("á", "a").replace("í", "i");
        return switch (k) {
            case "guardian", "guardia", "aliado", "defensor" -> "guardian";
            case "enemigo", "hostil" -> "enemigo";
            case "neutral", "neutro" -> "neutral";
            case "pacifico", "amistoso" -> "pacifico";
            case "cobarde", "huye" -> "cobarde";
            default -> null;
        };
    }

    public boolean fleeEnabled() {
        return flee;
    }

    public boolean guardsFight() {
        return guardsFight;
    }

    public double guardHp() {
        return guardHp;
    }

    public double guardDmg() {
        return guardDmg;
    }

    /** Rol puesto a mano (en el enemigo de la historia o en mundo.yml > roles > mobs); null si no tiene. */
    public String explicitRole(Entity e) {
        String id = e.getPersistentDataContainer().get(Keys.MOB, PersistentDataType.STRING);
        if (id != null) {
            MobManager.MobDef d = plugin.mobs().def(id);
            if (d != null && d.role != null) return d.role;
        }
        return vanillaRoles.get(e.getType());
    }

    public boolean hostile(Entity e) {
        if (!(e instanceof LivingEntity le) || e instanceof Player || e instanceof Mannequin || !le.isValid() || le.isDead()) return false;
        String role = explicitRole(e);
        if (role != null) return role.equals("enemigo");
        return e instanceof Enemy;
    }

    private G g(LivingEntity le) {
        return st.computeIfAbsent(le.getUniqueId(), k -> new G());
    }

    private LivingEntity find(LivingEntity le, NpcEntities.Walk w) {
        LivingEntity best = null;
        double bd = vision * vision;
        Location loc = le.getLocation();
        for (Entity e : le.getNearbyEntities(vision, 6, vision)) {
            if (!hostile(e)) continue;
            Location el = e.getLocation();
            double hx = el.getX() - w.homeX, hz = el.getZ() - w.homeZ;
            if (hx * hx + hz * hz > leash * leash) continue;
            double d = el.distanceSquared(loc);
            if (d < bd && le.hasLineOfSight(e)) {
                bd = d;
                best = (LivingEntity) e;
            }
        }
        return best;
    }

    /** Un tick de un guardia. Devuelve true si está peleando o herido (no debe pasear). */
    boolean guardTick(LivingEntity le, NpcEntities.Walk w, double maxHp, double dmg) {
        G g = g(le);
        if (g.hp < 0) {
            g.hp = maxHp;
            le.setInvulnerable(false);
        }
        if (g.downUntil > 0) {
            if (System.currentTimeMillis() < g.downUntil) return true;
            g.downUntil = 0;
            g.hp = maxHp;
            le.setInvulnerable(false);
            owner.restoreDescription(le);
        }
        if (g.cd > 0) g.cd--;
        Location loc = le.getLocation();
        LivingEntity t = g.target != null && Bukkit.getEntity(g.target) instanceof LivingEntity l && l.isValid() && !l.isDead() ? l : null;
        if (t != null) {
            Location tl = t.getLocation();
            double hx = tl.getX() - w.homeX, hz = tl.getZ() - w.homeZ;
            if (t.getWorld() != loc.getWorld() || hx * hx + hz * hz > leash * leash || !hostile(t)) t = null;
        }
        if (t == null) {
            g.target = null;
            if (++g.scan % 10 != 0) return false;
            t = find(le, w);
            if (t == null) return false;
            g.target = t.getUniqueId();
        }
        Location tl = t.getLocation();
        double dx = tl.getX() - loc.getX(), dz = tl.getZ() - loc.getZ(), dy = tl.getY() - loc.getY();
        double d = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        w.yaw = yaw;
        boolean moved = false;
        if (d > 1.9) {
            double step = Math.min(speed, d - 1.6);
            double nx = loc.getX() + dx / d * step, nz = loc.getZ() + dz / d * step;
            double ny = NpcEntities.surface(le.getWorld(), nx, nz, loc.getY());
            if (!Double.isNaN(ny) && Math.abs(ny - loc.getY()) <= 1.05) {
                le.teleport(new Location(loc.getWorld(), nx, ny, nz, yaw, 0), TeleportFlag.EntityState.RETAIN_PASSENGERS);
                moved = true;
            }
        }
        if (!moved) le.setRotation(yaw, (float) -Math.toDegrees(Math.atan2(dy, Math.max(0.1, d))));
        if (d <= 2.9 && Math.abs(dy) < 3 && g.cd == 0) {
            g.cd = 16;
            le.swingMainHand();
            t.damage(dmg, le);
            le.getWorld().playSound(loc, org.bukkit.Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1f);
        }
        w.hasTarget = false;
        w.pause = 30;
        return true;
    }

    /** ¿Hay un hostil cerca de este aldeano? (se revisa dos veces por segundo) */
    boolean danger(LivingEntity le) {
        G g = g(le);
        if (++g.scan % 10 == 0) {
            g.panic = false;
            for (Entity e : le.getNearbyEntities(9, 4, 9)) {
                if (hostile(e)) {
                    g.panic = true;
                    break;
                }
            }
        }
        return g.panic;
    }

    /** Un enemigo golpeó a un guardia. */
    void hurt(LivingEntity guard, double dmg) {
        G g = g(guard);
        if (g.downUntil > 0 || g.hp < 0) return;
        g.hp -= dmg;
        guard.playHurtAnimation(0);
        guard.getWorld().playSound(guard.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_HURT, 0.8f, 0.9f);
        if (g.hp > 0) return;
        g.downUntil = System.currentTimeMillis() + recover * 1000L;
        g.target = null;
        guard.setInvulnerable(true);
        if (guard instanceof Mannequin m) m.setDescription(Txt.mm("<red>✚ Herido · se está recuperando"));
    }

    public boolean isDown(Entity e) {
        G g = st.get(e.getUniqueId());
        return g != null && g.downUntil > 0;
    }

    public void startTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::aggroTick, 60L, 20L);
    }

    /** Cada segundo: los enemigos se fijan en los guardianes cercanos y los mobs guardianes buscan enemigos. */
    private void aggroTick() {
        Set<UUID> seen = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            for (Entity e : p.getNearbyEntities(28, 12, 28)) {
                if (!(e instanceof Mob m) || !seen.add(e.getUniqueId())) continue;
                LivingEntity cur = m.getTarget();
                if (cur != null && cur.isValid() && !cur.isDead()) continue;
                String role = explicitRole(m);
                if ("guardian".equals(role)) {
                    LivingEntity t = nearest(m, 16, true);
                    if (t != null) m.setTarget(t);
                } else if (hostile(m) && m.getType() != EntityType.CREEPER) {
                    LivingEntity t = nearest(m, 12, false);
                    if (t != null) m.setTarget(t);
                    else if ("enemigo".equals(role) && !(m instanceof Enemy)) {
                        Player near = null;
                        double bd = 16 * 16;
                        for (Player q : m.getWorld().getPlayers()) {
                            if (q.getGameMode() != GameMode.SURVIVAL && q.getGameMode() != GameMode.ADVENTURE) continue;
                            double d = q.getLocation().distanceSquared(m.getLocation());
                            if (d < bd) {
                                bd = d;
                                near = q;
                            }
                        }
                        if (near != null) m.setTarget(near);
                    }
                }
            }
        }
    }

    /** hostiles = true: el hostil más cercano; false: el guardián (maniquí o mob) más cercano. */
    private LivingEntity nearest(Mob from, double radius, boolean hostiles) {
        LivingEntity best = null;
        double bd = radius * radius;
        for (Entity e : from.getNearbyEntities(radius, 5, radius)) {
            boolean ok = hostiles ? hostile(e)
                    : (e instanceof Mannequin && guardsFight && owner.isGuard(e) && !isDown(e) && st.containsKey(e.getUniqueId()))
                    || (e instanceof Mob && "guardian".equals(explicitRole(e)));
            if (!ok || !(e instanceof LivingEntity le) || le.isDead()) continue;
            double d = e.getLocation().distanceSquared(from.getLocation());
            if (d < bd && from.hasLineOfSight(e)) {
                bd = d;
                best = le;
            }
        }
        return best;
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        LivingEntity target = e.getTarget();
        if (target == null) return;
        String role = explicitRole(e.getEntity());
        if (role == null) return;
        EntityTargetEvent.TargetReason why = e.getReason();
        switch (role) {
            case "pacifico" -> e.setCancelled(true);
            case "neutral" -> {
                boolean provoked = why == EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY
                        || why == EntityTargetEvent.TargetReason.TARGET_ATTACKED_NEARBY_ENTITY
                        || why == EntityTargetEvent.TargetReason.TARGET_ATTACKED_OWNER
                        || why == EntityTargetEvent.TargetReason.OWNER_ATTACKED_TARGET;
                if (!provoked) e.setCancelled(true);
            }
            case "guardian" -> {
                if (!hostile(target)) e.setCancelled(true);
            }
            default -> { }
        }
    }
}
