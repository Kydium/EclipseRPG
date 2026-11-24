package pe.kydium.eclipserpg.mobs;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.util.Keys;
import pe.kydium.eclipserpg.util.Reg;
import pe.kydium.eclipserpg.util.Txt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Ejecuta las habilidades de los jefes cada cierto tiempo. */
public final class BossAbilities {
    private final EclipseRPG plugin;
    private final MobManager mobs;
    private final Map<UUID, int[]> cooldowns = new HashMap<>();
    /** Jefe → esbirros que sostienen su escudo (y hasta cuándo dura). */
    private final Map<UUID, java.util.Set<UUID>> shields = new HashMap<>();
    private final Map<UUID, Long> shieldUntil = new HashMap<>();
    private final java.util.Set<UUID> clones = new java.util.HashSet<>();

    /** ¿Está protegido por su escudo? (mientras vivan los esbirros que invocó) */
    public boolean shielded(UUID boss) {
        java.util.Set<UUID> ms = shields.get(boss);
        if (ms == null) return false;
        ms.removeIf(u -> {
            Entity e = Bukkit.getEntity(u);
            return e == null || e.isDead() || !e.isValid();
        });
        if (ms.isEmpty() || System.currentTimeMillis() > shieldUntil.getOrDefault(boss, 0L)) {
            shields.remove(boss);
            shieldUntil.remove(boss);
            Entity b = Bukkit.getEntity(boss);
            if (b != null) for (Player p : playersNear(b.getLocation(), 40)) {
                p.sendActionBar(Txt.mm("<yellow>✦ ¡Su escudo se rompió!"));
                Txt.sound(p, "block.glass.break", 1f, 0.6f);
            }
            return false;
        }
        return true;
    }

    public boolean isClone(Entity e) {
        return clones.contains(e.getUniqueId());
    }

    public BossAbilities(EclipseRPG plugin, MobManager mobs) {
        this.plugin = plugin;
        this.mobs = mobs;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    private int auraClock;

    /** Aura de los mobs comunes (no jefes) que la tengan: cada 2 s, solo cerca de jugadores. */
    private void commonAuras() {
        if (++auraClock % 2 != 0) return;
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (Entity e : p.getNearbyEntities(32, 16, 32)) {
                if (!(e instanceof LivingEntity le) || !seen.add(e.getUniqueId())) continue;
                String id = le.getPersistentDataContainer().get(Keys.MOB, PersistentDataType.STRING);
                MobManager.MobDef d = id == null ? null : mobs.def(id);
                if (d == null || d.boss || d.aura == null) continue;
                try {
                    le.getWorld().spawnParticle(Particle.valueOf(d.aura.toUpperCase()), le.getLocation().add(0, le.getHeight() * 0.6, 0),
                            5, le.getWidth() * 0.4, le.getHeight() * 0.3, le.getWidth() * 0.4, 0.01);
                } catch (IllegalArgumentException ignored) {
                    // partícula que necesita datos
                }
            }
        }
    }

    private void tick() {
        commonAuras();
        cooldowns.keySet().removeIf(u -> {
            Entity e = Bukkit.getEntity(u);
            return e == null || e.isDead();
        });
        for (UUID id : mobs.activeBosses()) {
            Entity ent = Bukkit.getEntity(id);
            if (!(ent instanceof LivingEntity boss) || boss.isDead()) continue;
            String defId = boss.getPersistentDataContainer().get(Keys.MOB, PersistentDataType.STRING);
            MobManager.MobDef d = defId == null ? null : mobs.def(defId);
            if (d == null) continue;
            aura(boss, d);
            if (shields.containsKey(id) && shielded(id))
                boss.getWorld().spawnParticle(Particle.END_ROD, boss.getLocation().add(0, boss.getHeight() / 2, 0), 16, boss.getWidth(), boss.getHeight() / 2, boss.getWidth(), 0.01);
            Player target = nearestTarget(boss, 32);
            if (target != null && boss instanceof org.bukkit.entity.Warden wd) wd.setAnger(target, 150);   // no se calma ni se entierra
            if (target != null && boss instanceof org.bukkit.entity.Mob m && (m.getTarget() == null || m.getTarget().isDead())) m.setTarget(target);
            if (d.abilities.isEmpty()) continue;
            if (target == null) continue;
            int[] cd = cooldowns.computeIfAbsent(id, k -> initial(d));
            boolean enraged = boss.getPersistentDataContainer().has(Keys.ENRAGED, PersistentDataType.BYTE);
            for (int i = 0; i < d.abilities.size() && i < cd.length; i++) {
                cd[i] -= enraged ? 2 : 1;           // en furia las habilidades van el doble de rápido
                if (cd[i] > 0) continue;
                MobManager.Ability a = d.abilities.get(i);
                cd[i] = a.every();
                try {
                    run(boss, d, a, target);
                } catch (Exception ex) {
                    plugin.getLogger().warning("Habilidad " + a.type() + " de " + d.id + " falló: " + ex.getMessage());
                }
                break; // una habilidad por segundo como máximo
            }
        }
    }

    private int[] initial(MobManager.MobDef d) {
        int[] cd = new int[d.abilities.size()];
        for (int i = 0; i < cd.length; i++) cd[i] = 3 + ThreadLocalRandom.current().nextInt(Math.max(1, d.abilities.get(i).every()));
        return cd;
    }

    private Player nearestTarget(LivingEntity boss, double radius) {
        Player best = null;
        double bestD = radius * radius;
        for (Player p : boss.getWorld().getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE || p.isDead()) continue;
            double dd = p.getLocation().distanceSquared(boss.getLocation());
            if (dd < bestD) {
                bestD = dd;
                best = p;
            }
        }
        return best;
    }

    private List<Player> playersNear(Location loc, double radius) {
        List<Player> out = new ArrayList<>();
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE || p.isDead()) continue;
            if (p.getLocation().distanceSquared(loc) <= radius * radius) out.add(p);
        }
        return out;
    }

    private void announce(LivingEntity boss, String msg, String sound, float pitch) {
        for (Player p : playersNear(boss.getLocation(), 40)) {
            if (msg != null) p.sendActionBar(Txt.mm(msg));
            Txt.sound(p, sound, 1.2f, pitch);
        }
    }

    private void aura(LivingEntity boss, MobManager.MobDef d) {
        if (d.aura == null) return;
        try {
            Particle part = Particle.valueOf(d.aura.toUpperCase());
            boss.getWorld().spawnParticle(part, boss.getLocation().add(0, 1, 0), 12, 0.6, 0.9, 0.6, 0.01);
        } catch (IllegalArgumentException ignored) {
            // partícula que necesita datos o no existe
        }
    }

    private void run(LivingEntity boss, MobManager.MobDef d, MobManager.Ability a, Player target) {
        World w = boss.getWorld();
        Location bl = boss.getLocation();
        switch (a.type()) {
            case "ONDA" -> {
                w.spawnParticle(Particle.EXPLOSION, bl, 6, a.radius() / 3, 0.3, a.radius() / 3, 0);
                announce(boss, "<red>⚠ " + d.name + "<red> golpea el suelo", "entity.generic.explode", 0.7f);
                for (Player p : playersNear(bl, a.radius())) {
                    p.damage(a.power(), boss);
                    Vector push = p.getLocation().toVector().subtract(bl.toVector()).setY(0);
                    if (push.lengthSquared() < 0.01) push = new Vector(1, 0, 0);
                    p.setVelocity(push.normalize().multiply(1.3).setY(0.6));
                }
            }
            case "SALTO" -> {
                Vector dir = target.getLocation().toVector().subtract(bl.toVector());
                double dist = Math.max(1, dir.length());
                boss.setVelocity(dir.normalize().multiply(Math.min(2.2, dist / 6)).setY(0.9));
                announce(boss, "<gold>⚠ " + d.name + "<gold> salta hacia ti", "entity.ravager.attack", 0.8f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (boss.isDead()) return;
                    Location land = boss.getLocation();
                    land.getWorld().spawnParticle(Particle.EXPLOSION, land, 4, 1.5, 0.2, 1.5, 0);
                    Txt.sound(target, "entity.generic.explode", 1f, 0.9f);
                    for (Player p : playersNear(land, Math.max(3, a.radius() / 2))) {
                        p.damage(a.power(), boss);
                        p.setVelocity(p.getVelocity().setY(0.7));
                    }
                }, 22L);
            }
            case "TELEPORTE" -> {
                Location dest = mobs.spawnSpot(target.getLocation(), 3);
                w.spawnParticle(Particle.PORTAL, bl.add(0, 1, 0), 60, 0.5, 1, 0.5, 0.3);
                boss.teleport(dest);
                w.spawnParticle(Particle.PORTAL, dest.clone().add(0, 1, 0), 60, 0.5, 1, 0.5, 0.3);
                announce(boss, "<light_purple>⚠ " + d.name + "<light_purple> aparece a tu lado", "entity.enderman.teleport", 0.6f);
            }
            case "INVOCAR" -> {
                List<String> pool = new ArrayList<>();
                if (a.extra() != null) for (String s : a.extra().split(",")) if (!s.isBlank()) pool.add(s.trim());
                if (pool.isEmpty()) pool.addAll(d.minions);
                if (pool.isEmpty()) return;
                announce(boss, "<dark_purple>⚠ " + d.name + "<dark_purple> invoca " + mobs.theme().plural(), "entity.evoker.prepare_summon", 0.8f);
                for (int i = 0; i < Math.max(1, a.amount()); i++) {
                    mobs.spawn(pool.get(ThreadLocalRandom.current().nextInt(pool.size())), mobs.spawnSpot(bl, 4));
                }
            }
            case "FLECHAS" -> {
                announce(boss, "<yellow>⚠ ¡Lluvia de flechas!", "entity.skeleton.shoot", 0.6f);
                for (Player p : playersNear(target.getLocation(), a.radius())) {
                    for (int i = 0; i < Math.max(4, a.amount()); i++) {
                        Location sky = p.getLocation().add(ThreadLocalRandom.current().nextDouble(-3, 3), 12,
                                ThreadLocalRandom.current().nextDouble(-3, 3));
                        Arrow arrow = w.spawnArrow(sky, new Vector(0, -1, 0), 1.6f, 4f);
                        arrow.setShooter(boss);
                        arrow.setDamage(a.power());
                        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                    }
                }
            }
            case "FUEGO" -> {
                announce(boss, "<red>⚠ ¡Las llamas te alcanzan!", "entity.blaze.shoot", 0.7f);
                for (Player p : playersNear(bl, a.radius())) {
                    w.spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 40, 0.4, 1, 0.4, 0.05);
                    p.setFireTicks(Math.max(p.getFireTicks(), 100));
                    p.damage(a.power(), boss);
                }
            }
            case "RAYO" -> {
                Location strike = target.getLocation();
                w.strikeLightningEffect(strike);
                for (Player p : playersNear(strike, Math.max(2.5, a.radius()))) p.damage(a.power(), boss);
            }
            case "ATRAER" -> {
                announce(boss, "<aqua>⚠ " + d.name + "<aqua> te arrastra", "entity.elder_guardian.curse", 1.2f);
                w.spawnParticle(Particle.CLOUD, bl.clone().add(0, 1, 0), 40, a.radius() / 2, 0.5, a.radius() / 2, 0.05);
                for (Player p : playersNear(bl, a.radius())) {
                    Vector pull = bl.toVector().subtract(p.getLocation().toVector());
                    if (pull.lengthSquared() < 0.01) continue;
                    p.setVelocity(pull.normalize().multiply(1.4).setY(0.4));
                }
            }
            case "EFECTO" -> {
                PotionEffectType type = Reg.effect(a.extra());
                if (type == null) return;
                announce(boss, "<dark_green>⚠ " + d.name + "<dark_green> lanza una maldición", "entity.witch.throw", 0.8f);
                int seconds = Math.max(2, a.amount());
                for (Player p : playersNear(bl, a.radius())) {
                    w.spawnParticle(Particle.WITCH, p.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.05);
                    p.addPotionEffect(new PotionEffect(type, seconds * 20, (int) a.power(), false, true));
                }
            }
            case "EMBESTIDA" -> {
                Location from = bl.clone();
                Location to = target.getLocation();
                Vector dir = to.toVector().subtract(from.toVector()).setY(0);
                if (dir.lengthSquared() < 1) return;
                dir.normalize();
                announce(boss, "<red>⚠ " + d.name + "<red> se prepara para embestir… ¡apártate!", "entity.ravager.roar", 0.8f);
                for (int i = 1; i <= 12; i++) w.spawnParticle(Particle.ANGRY_VILLAGER, from.clone().add(dir.clone().multiply(i)).add(0, 0.3, 0), 1, 0, 0, 0, 0);
                Vector fd = dir.clone();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (boss.isDead()) return;
                    boss.setVelocity(fd.clone().multiply(2.6).setY(0.15));
                    java.util.Set<UUID> hit = new java.util.HashSet<>();
                    for (int t = 1; t <= 14; t++) {
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (boss.isDead()) return;
                            for (Player p : playersNear(boss.getLocation(), 2.3)) {
                                if (!hit.add(p.getUniqueId())) continue;
                                p.damage(a.power(), boss);
                                p.setVelocity(fd.clone().multiply(1.6).setY(0.7));
                                Txt.sound(p, "entity.player.attack.knockback", 1f, 0.6f);
                            }
                        }, t);
                    }
                }, 20L);
            }
            case "CLONES" -> {
                announce(boss, "<light_purple>⚠ " + d.name + "<light_purple> se multiplica… ¿cuál es el verdadero?", "entity.illusioner.mirror_move", 0.8f);
                List<Location> spots = new ArrayList<>();
                for (int i = 0; i < Math.max(2, a.amount()); i++) spots.add(mobs.spawnSpot(target.getLocation(), 7));
                for (Location sp : spots) {
                    Entity c = w.spawnEntity(sp, boss.getType());
                    if (!(c instanceof LivingEntity cl)) { c.remove(); continue; }
                    cl.customName(boss.customName());
                    cl.setCustomNameVisible(true);
                    var sc = cl.getAttribute(org.bukkit.attribute.Attribute.SCALE);
                    var bsc = boss.getAttribute(org.bukkit.attribute.Attribute.SCALE);
                    if (sc != null && bsc != null) sc.setBaseValue(bsc.getValue());
                    var hp = cl.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                    if (hp != null) hp.setBaseValue(1);
                    cl.setHealth(1);
                    var dmg = cl.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
                    if (dmg != null) dmg.setBaseValue(a.power());
                    if (cl.getEquipment() != null && boss.getEquipment() != null) cl.getEquipment().setItemInMainHand(boss.getEquipment().getItemInMainHand());
                    if (cl instanceof org.bukkit.entity.Mob m) m.setTarget(target);
                    cl.setPersistent(false);
                    cl.addScoreboardTag("eclipse_clon");
                    clones.add(cl.getUniqueId());
                    w.spawnParticle(Particle.LARGE_SMOKE, sp.clone().add(0, 1, 0), 25, 0.4, 0.8, 0.4, 0.02);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        clones.remove(cl.getUniqueId());
                        if (cl.isValid()) {
                            w.spawnParticle(Particle.LARGE_SMOKE, cl.getLocation().add(0, 1, 0), 20, 0.4, 0.8, 0.4, 0.02);
                            cl.remove();
                        }
                    }, 20L * Math.max(8, (int) a.radius()));
                }
                // el verdadero cambia de sitio con uno de sus reflejos
                if (!spots.isEmpty()) {
                    Location swap = spots.get(ThreadLocalRandom.current().nextInt(spots.size()));
                    w.spawnParticle(Particle.LARGE_SMOKE, bl.clone().add(0, 1, 0), 25, 0.4, 0.8, 0.4, 0.02);
                    boss.teleport(swap);
                }
            }
            case "METEOROS" -> {
                announce(boss, "<gold>⚠ ¡El techo arde! Sal de los círculos de fuego", "entity.blaze.ambient", 0.5f);
                for (Player p : playersNear(bl, a.radius())) {
                    for (int i = 0; i < Math.max(2, a.amount()); i++) {
                        Location c = p.getLocation().add(ThreadLocalRandom.current().nextDouble(-3.5, 3.5), 0, ThreadLocalRandom.current().nextDouble(-3.5, 3.5));
                        int[] t = {0};
                        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
                            if (t[0]++ < 6) {
                                for (int k = 0; k < 16; k++) {
                                    double ang = Math.PI * 2 * k / 16;
                                    w.spawnParticle(Particle.FLAME, c.clone().add(Math.cos(ang) * 2.2, 0.15, Math.sin(ang) * 2.2), 1, 0, 0, 0, 0);
                                }
                                return;
                            }
                            task.cancel();
                            w.spawnParticle(Particle.EXPLOSION, c.clone().add(0, 0.5, 0), 3, 0.6, 0.2, 0.6, 0);
                            w.spawnParticle(Particle.LAVA, c.clone().add(0, 0.5, 0), 20, 1, 0.3, 1, 0);
                            w.playSound(c, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.2f);
                            for (Player q : playersNear(c, 2.4)) {
                                q.damage(a.power(), boss);
                                q.setFireTicks(Math.max(q.getFireTicks(), 60));
                            }
                        }, 0L, 5L);
                    }
                }
            }
            case "ESCUDO" -> {
                if (shields.containsKey(boss.getUniqueId())) return;
                List<String> pool = new ArrayList<>();
                if (a.extra() != null) for (String s2 : a.extra().split(",")) if (!s2.isBlank()) pool.add(s2.trim());
                if (pool.isEmpty()) pool.addAll(d.minions);
                if (pool.isEmpty()) return;
                java.util.Set<UUID> guards = new java.util.HashSet<>();
                for (int i = 0; i < Math.max(2, a.amount()); i++) {
                    LivingEntity m = mobs.spawn(pool.get(ThreadLocalRandom.current().nextInt(pool.size())), mobs.spawnSpot(bl, 6));
                    if (m != null) {
                        guards.add(m.getUniqueId());
                        m.setGlowing(true);
                    }
                }
                if (guards.isEmpty()) return;
                shields.put(boss.getUniqueId(), guards);
                shieldUntil.put(boss.getUniqueId(), System.currentTimeMillis() + 30_000L);
                announce(boss, "<aqua>🛡 " + d.name + "<aqua> se cubre con un escudo: <white>mata a los que brillan", "block.beacon.activate", 0.6f);
            }
            case "CADENAS" -> {
                announce(boss, "<gray>⚠ Cadenas te atrapan…", "block.chain.place", 0.5f);
                int secs = Math.max(2, a.amount());
                for (Player p : playersNear(bl, a.radius())) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, secs * 20, 5, false, true));
                    PotionEffectType jump = Reg.effect("jump_boost");
                    if (jump != null) p.addPotionEffect(new PotionEffect(jump, secs * 20, 200, false, false));
                    for (int k = 0; k < 8; k++) w.spawnParticle(Particle.ITEM, p.getLocation().add(0, 0.2 * k, 0), 3, 0.3, 0.05, 0.3, 0,
                            new org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_CHAIN));
                    if (a.power() > 0) p.damage(a.power(), boss);
                }
            }
            default -> plugin.getLogger().warning("Habilidad desconocida: " + a.type());
        }
    }
}
