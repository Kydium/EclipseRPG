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
        int gen;
    }

    public static final String CORPSE = "eclipse_cuerpo";
    public static final String FALLEN = "eclipse_caido";

    private final EclipseRPG plugin;
    private final NpcEntities owner;
    private final Map<UUID, G> st = new HashMap<>();
    private final Map<EntityType, String> vanillaRoles = new HashMap<>();
    private double guardHp = 40, guardDmg = 6, vision = 14, leash = 24, speed = 0.24;
    private int recover = 45;
    private boolean flee = true, guardsFight = true;
    // v6.4: los enemigos atacan a todos; muertes con cuerpo y reaparición
    private boolean attackAll = true, npcsFall = true, corpses = true, mobCorpses = true;
    private double npcHp = 20;
    private int corpseSec = 10, npcBack = 120, villageBack = 300;

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
        attackAll = r == null || !"guardianes".equalsIgnoreCase(r.getString("enemigos-atacan", "todos").trim());
        npcsFall = r == null || r.getBoolean("npcs-pueden-caer", true);
        npcHp = r == null ? 20 : Math.max(1, r.getDouble("vida-npc", 20));
        ConfigurationSection d = plugin.story().yaml("mundo").getConfigurationSection("muerte");
        corpses = d == null || d.getBoolean("cuerpos", true);
        mobCorpses = d == null || d.getBoolean("cuerpos-criaturas", true);
        corpseSec = d == null ? 10 : Math.max(0, Math.min(600, d.getInt("cuerpo-segundos", 10)));
        npcBack = d == null ? 120 : Math.max(5, d.getInt("reaparicion.npcs", 120));
        villageBack = d == null ? 300 : Math.max(0, d.getInt("reaparicion.animales-aldea", 300));
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

    public boolean npcsFall() {
        return npcsFall;
    }

    public boolean mobCorpses() {
        return corpses && mobCorpses;
    }

    public int corpseSeconds() {
        return corpseSec;
    }

    public int villageBack() {
        return villageBack;
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
        if (!(e instanceof LivingEntity le) || e instanceof Player || e instanceof Mannequin || !le.isValid() || le.isDead()
                || e.getScoreboardTags().contains(CORPSE)) return false;
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
            if (System.currentTimeMillis() < g.downUntil + 3000) return true;
            stand(le);
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

    /** Un enemigo golpeó a un NPC (guardia, aldeano o NPC de la historia). */
    public void hurt(LivingEntity npc, double dmg) {
        G g = g(npc);
        if (g.downUntil > 0) return;
        if (g.hp < 0) g.hp = owner.isGuard(npc) ? guardHp : npcHp;
        g.hp -= dmg;
        npc.playHurtAnimation(0);
        npc.getWorld().playSound(npc.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_HURT, 0.8f, 0.9f);
        if (g.hp > 0) return;
        down(npc, owner.isGuard(npc) ? recover : npcBack);
    }

    /** Cae al suelo (si hay cuerpos), luego desaparece y vuelve a su casa pasado un rato. */
    void down(LivingEntity npc, int backSeconds) {
        G g = g(npc);
        int gen = ++g.gen;
        g.downUntil = System.currentTimeMillis() + backSeconds * 1000L;
        g.target = null;
        npc.setInvulnerable(true);
        npc.addScoreboardTag(FALLEN);
        npc.getWorld().playSound(npc.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_DEATH, 0.8f, 1f);
        if (npc instanceof Mannequin m) m.setDescription(Txt.mm("<red>✚ Herido"));
        int lie = corpses ? Math.min(corpseSec, backSeconds) : 0;
        if (lie > 0) npc.setPose(org.bukkit.entity.Pose.SLEEPING, true);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (g.gen != gen || !npc.isValid()) return;
            // desaparece hasta que reaparezca
            npc.getWorld().spawnParticle(org.bukkit.Particle.POOF, npc.getLocation().add(0, 0.5, 0), 12, 0.3, 0.2, 0.3, 0.02);
            npc.setPose(org.bukkit.entity.Pose.STANDING, false);
            npc.setInvisible(true);
            npc.setCustomNameVisible(false);
            if (npc instanceof Mannequin m) m.setDescription(null);
        }, Math.max(1, lie * 20L));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (g.gen == gen && npc.isValid()) stand(npc);
        }, backSeconds * 20L);
    }

    /** Vuelve a estar de pie, en su casa y con toda la vida. */
    public void stand(LivingEntity npc) {
        G g = g(npc);
        g.gen++;
        g.downUntil = 0;
        g.hp = owner.isGuard(npc) ? guardHp : npcHp;
        npc.setInvulnerable(false);
        npc.setInvisible(false);
        npc.setPose(org.bukkit.entity.Pose.STANDING, false);
        npc.removeScoreboardTag(FALLEN);
        Location home = owner.homeOf(npc);
        if (home != null) npc.teleport(home, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        owner.restoreDescription(npc);
        npc.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, npc.getLocation().add(0, 1, 0), 14, 0.3, 0.5, 0.3, 0);
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
                    if (t == null && attackAll) t = anyone(m, 12);
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

    /** Para "los enemigos atacan a todos": el ser vivo más cercano que no sea enemigo (aldeanos, NPCs, animales, gólems, jugadores). */
    private LivingEntity anyone(Mob from, double radius) {
        LivingEntity best = null;
        double bd = radius * radius;
        for (Entity e : from.getNearbyEntities(radius, 5, radius)) {
            if (!(e instanceof LivingEntity le) || le.isDead() || !le.isValid() || hostile(e) || e.isInvulnerable() && !(e instanceof Mannequin)) continue;
            if (e instanceof org.bukkit.entity.ArmorStand || e.getScoreboardTags().contains(CORPSE) || e.getScoreboardTags().contains(FALLEN)) continue;
            if (e instanceof Player p && p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) continue;
            if (e instanceof Mannequin && (!npcsFall || !owner.isNpcOrFiller(e) || isDown(e))) continue;
            if (from instanceof org.bukkit.entity.Creeper || e.getType() == from.getType()) continue;
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
