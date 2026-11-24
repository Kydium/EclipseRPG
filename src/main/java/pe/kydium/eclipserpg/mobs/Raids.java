package pe.kydium.eclipserpg.mobs;

import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.util.Txt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * Oleadas como las invasiones de Minecraft: el grupo aparece LEJOS (36-48 bloques, en la superficie, por un lado)
 * y viene caminando hacia el jugador. Brillan unos segundos para que se vean llegar, suena el cuerno y el jugador
 * sabe por dónde vienen ("¡Vienen del norte!"). Mientras dure, se les recuerda a quién atacar.
 */
public final class Raids {

    private final EclipseRPG plugin;
    /** enemigo → jugador al que van a buscar */
    private final Map<UUID, UUID> hunters = new HashMap<>();

    public Raids(EclipseRPG plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 40L);
    }

    /** Hace aparecer la oleada lejos del jugador y la manda hacia él. Devuelve lo que apareció. */
    public List<LivingEntity> send(Player p, List<String> mobs) {
        return send(p, mobs, null);
    }

    /**
     * @param spawner cómo crear cada mob en un sitio (por defecto plugin.mobs().spawn)
     */
    public List<LivingEntity> send(Player p, List<String> mobs, Function<Object[], LivingEntity> spawner) {
        List<LivingEntity> out = new ArrayList<>();
        if (mobs.isEmpty()) return out;
        int min = plugin.getConfig().getInt("misiones.oleadas.distancia-min", 34);
        int max = Math.max(min + 1, plugin.getConfig().getInt("misiones.oleadas.distancia-max", 46));
        Location origin = origin(p, min, max);
        World w = origin.getWorld();
        for (String id : mobs) {
            Location at = spread(origin, 4);
            LivingEntity le = spawner == null ? null : spawner.apply(new Object[]{id, at});
            if (le == null) le = plugin.mobs().spawn(id, at);
            if (le == null) continue;
            march(le, p);
            out.add(le);
        }
        // aviso: cuerno, humo donde aparecen y por dónde vienen
        w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, origin.clone().add(0, 1, 0), 30, 2, 3, 2, 0.02);
        w.playSound(origin, "event.raid.horn", 6f, 1f);
        Txt.sound(p, "event.raid.horn", 0.9f, 1f);
        p.sendActionBar(Txt.mm("<red>⚔ ¡Una oleada viene del <white>" + direction(p.getLocation(), origin) + "<red>! <gray>(" + out.size() + " enemigos)"));
        return out;
    }

    /** Que vaya a buscar al jugador y que se vea venir. */
    public void march(LivingEntity le, Player p) {
        var fr = le.getAttribute(Attribute.FOLLOW_RANGE);
        if (fr != null && fr.getBaseValue() < 72) fr.setBaseValue(72);
        le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 8, 0, false, false));
        if (le instanceof Mob m) m.setTarget(p);
        le.setRemoveWhenFarAway(false);   // que no desaparezca mientras viene caminando
        le.removeScoreboardTag("eclipse_salvaje");
        hunters.put(le.getUniqueId(), p.getUniqueId());
    }

    /** Un sitio en la superficie a 34-46 bloques, mejor fuera de la aldea y sin agua. */
    private Location origin(Player p, int min, int max) {
        World w = p.getWorld();
        Location c = p.getLocation();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location village = plugin.village() == null ? null : plugin.village().center();
        Location best = null;
        double bestScore = -1;
        for (int tries = 0; tries < 24; tries++) {
            double ang = r.nextDouble() * Math.PI * 2;
            int dist = r.nextInt(min, max + 1);
            int x = c.getBlockX() + (int) Math.round(Math.cos(ang) * dist), z = c.getBlockZ() + (int) Math.round(Math.sin(ang) * dist);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            Location l;
            if (w.getEnvironment() == World.Environment.NORMAL) {
                int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                Block ground = w.getBlockAt(x, y, z);
                if (ground.isLiquid() || Math.abs(y - c.getY()) > 20) continue;
                l = new Location(w, x + 0.5, y + 1, z + 0.5);
            } else {
                l = plugin.mobs().spawnSpot(new Location(w, x, c.getY(), z), 6);
                if (Math.abs(l.getY() - c.getY()) > 12) continue;
            }
            // mejor lejos del centro de la aldea (que vengan de afuera) y a una altura parecida
            double score = 100 - Math.abs(l.getY() - c.getY());
            if (village != null && village.getWorld() == w) score += Math.min(60, l.distance(village) - c.distance(village));
            if (score > bestScore) {
                bestScore = score;
                best = l;
            }
            if (tries >= 8 && best != null) break;
        }
        return best != null ? best : plugin.mobs().spawnSpot(c, 14);
    }

    private Location spread(Location o, int r) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Location base = o.clone().add(rnd.nextInt(-r, r + 1), 0, rnd.nextInt(-r, r + 1));
        return plugin.mobs().spawnSpot(base, 2);
    }

    /** "norte", "sureste"… desde el jugador hacia el punto. */
    public static String direction(Location from, Location to) {
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        double ang = Math.toDegrees(Math.atan2(dx, -dz));   // 0 = norte (-z), 90 = este
        if (ang < 0) ang += 360;
        String[] n = {"norte", "noreste", "este", "sureste", "sur", "suroeste", "oeste", "noroeste"};
        return n[(int) Math.round(ang / 45.0) % 8];
    }

    /** Cada 2 s: si se distrajeron, vuelven a ir por el jugador. */
    private void tick() {
        if (hunters.isEmpty()) return;
        Set<UUID> dead = new HashSet<>();
        for (Map.Entry<UUID, UUID> e : hunters.entrySet()) {
            Entity ent = Bukkit.getEntity(e.getKey());
            Player p = Bukkit.getPlayer(e.getValue());
            if (!(ent instanceof Mob m) || !m.isValid() || p == null || !p.isOnline() || p.isDead() || p.getWorld() != m.getWorld()
                    || p.getLocation().distanceSquared(m.getLocation()) > 120 * 120) {
                dead.add(e.getKey());
                if (ent instanceof LivingEntity le && le.isValid() && !plugin.mobs().isBoss(le)) le.setRemoveWhenFarAway(true);
                continue;
            }
            LivingEntity t = m.getTarget();
            if (t == null || !t.isValid()) m.setTarget(p);
        }
        for (UUID u : dead) hunters.remove(u);
    }
}
