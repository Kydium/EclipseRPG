package pe.kydium.eclipserpg.rift;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.util.Keys;
import pe.kydium.eclipserpg.util.Txt;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Grietas: oleadas de Corrompidos del nivel del grupo para farmear. */
public final class RiftManager implements Listener {

    private static final class Rift {
        final UUID id = UUID.randomUUID();
        final Set<UUID> players = new LinkedHashSet<>();
        final Set<UUID> mobs = new LinkedHashSet<>();
        int tier;
        int wave = 0;
        int countdown = 3;
        long started = System.currentTimeMillis();
    }

    private final EclipseRPG plugin;
    private final Map<UUID, Rift> rifts = new HashMap<>();
    private final Map<UUID, UUID> playerRift = new HashMap<>();

    public RiftManager(EclipseRPG plugin) {
        this.plugin = plugin;
    }

    private ConfigurationSection cfg() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("grieta");
        return s != null ? s : plugin.getConfig().createSection("grieta");
    }

    public boolean inRift(Player p) {
        return playerRift.containsKey(p.getUniqueId());
    }

    public long cost(Player p) {
        return cfg().getLong("costo-por-tier", 50) * plugin.levels().tier(p);
    }

    public void start(Player leader) {
        if (inRift(leader)) {
            Txt.send(leader, "<red>Ya estás dentro de una Grieta.");
            return;
        }
        long cost = cost(leader);
        if (!plugin.levels().takeSoles(leader, cost)) {
            Txt.send(leader, "<red>Abrir una Grieta cuesta <yellow>" + Txt.num(cost) + " ☀</yellow>.");
            return;
        }
        Rift r = new Rift();
        r.tier = plugin.levels().tier(leader);
        double radius = cfg().getDouble("radio-grupo", 15);
        for (Player p : leader.getWorld().getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR || inRift(p)) continue;
            if (p.getLocation().distanceSquared(leader.getLocation()) <= radius * radius) r.players.add(p.getUniqueId());
        }
        r.players.add(leader.getUniqueId());
        int maxTier = r.tier;
        for (UUID u : r.players) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) maxTier = Math.max(maxTier, plugin.levels().tier(p));
        }
        r.tier = maxTier;
        rifts.put(r.id, r);
        for (UUID u : r.players) {
            playerRift.put(u, r.id);
            Player p = Bukkit.getPlayer(u);
            if (p == null) continue;
            p.showTitle(Title.title(Txt.mm("<dark_purple><bold>¡Se abre una Grieta!"),
                    Txt.mm("<gray>Tier " + r.tier + " · " + waves() + " oleadas"),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(500))));
            Txt.sound(p, "block.end_portal.spawn", 0.6f, 1.2f);
        }
    }

    private int waves() {
        return cfg().getInt("oleadas", 5);
    }

    public void startTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        long maxMs = cfg().getLong("tiempo-maximo-minutos", 10) * 60_000L;
        for (Rift r : new ArrayList<>(rifts.values())) {
            r.players.removeIf(u -> {
                Player p = Bukkit.getPlayer(u);
                boolean gone = p == null || p.isDead();
                if (gone) playerRift.remove(u);
                return gone;
            });
            if (r.players.isEmpty()) {
                finish(r, false, "Todos cayeron");
                continue;
            }
            if (System.currentTimeMillis() - r.started > maxMs) {
                finish(r, false, "Se acabó el tiempo");
                continue;
            }
            r.mobs.removeIf(u -> {
                Entity e = Bukkit.getEntity(u);
                return e == null || e.isDead() || !e.isValid();
            });
            if (r.mobs.isEmpty()) {
                if (r.wave >= waves()) {
                    finish(r, true, null);
                    continue;
                }
                if (r.countdown > 0) {
                    actionBar(r, "<light_purple>Oleada " + (r.wave + 1) + " en " + r.countdown + "...");
                    r.countdown--;
                    continue;
                }
                spawnWave(r);
                r.countdown = 5;
            }
            actionBar(r, "<dark_purple>Grieta T" + r.tier + " <gray>· Oleada <white>" + r.wave + "/" + waves()
                    + " <gray>· Enemigos: <red>" + r.mobs.size());
        }
    }

    private void actionBar(Rift r, String msg) {
        for (UUID u : r.players) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) p.sendActionBar(Txt.mm(msg));
        }
    }

    private void spawnWave(Rift r) {
        r.wave++;
        List<String> pool = cfg().getStringList("mobs-por-tier." + r.tier);
        if (pool.isEmpty()) pool = cfg().getStringList("mobs-por-tier.1");
        if (pool.isEmpty()) {
            finish(r, false, "Grieta sin mobs configurados");
            return;
        }
        int count = cfg().getInt("mobs-base", 3) + 2 * r.wave + 2 * (r.players.size() - 1);
        List<Player> online = new ArrayList<>();
        for (UUID u : r.players) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) online.add(p);
        }
        for (int i = 0; i < count; i++) {
            Player target = online.get(ThreadLocalRandom.current().nextInt(online.size()));
            String id = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            Location loc = plugin.mobs().spawnSpot(target.getLocation(), 9);
            tag(r, plugin.mobs().spawn(id, loc));
        }
        if (r.wave == waves()) {
            Player target = online.get(0);
            String id = pool.get(0);
            Location loc = plugin.mobs().spawnSpot(target.getLocation(), 7);
            tag(r, plugin.mobs().spawn(id, loc, cfg().getDouble("vida-guardian", 4.0),
                    "<dark_purple><bold>Guardián de la Grieta <gray>[T" + r.tier + "]", true));
            for (Player p : online) Txt.sound(p, "entity.wither.spawn", 0.5f, 1.4f);
        }
        for (Player p : online) Txt.sound(p, "entity.illusioner.cast_spell", 1f, 0.7f);
    }

    private void tag(Rift r, LivingEntity le) {
        if (le == null) return;
        le.getPersistentDataContainer().set(Keys.RIFT, PersistentDataType.STRING, r.id.toString());
        r.mobs.add(le.getUniqueId());
    }

    private void finish(Rift r, boolean win, String reason) {
        rifts.remove(r.id);
        for (UUID u : r.mobs) {
            Entity e = Bukkit.getEntity(u);
            if (e != null) e.remove();
        }
        for (UUID u : r.players) {
            playerRift.remove(u);
            Player p = Bukkit.getPlayer(u);
            if (p == null) continue;
            if (win) {
                long xp = cfg().getLong("xp-por-tier", 300) * r.tier;
                long soles = cfg().getLong("soles-por-tier", 60) * r.tier;
                plugin.levels().reward(p, xp, soles);
                p.showTitle(Title.title(Txt.mm("<gold><bold>¡Grieta cerrada!"), Txt.mm("<aqua>+" + Txt.num(xp) + " XP · <yellow>+" + Txt.num(soles) + " ☀"),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(600))));
                Txt.sound(p, "ui.toast.challenge_complete", 1f, 1f);
                if (ThreadLocalRandom.current().nextDouble() < cfg().getDouble("prob-fragmento", 0.25) * plugin.levels().lootMultiplier(p)) {
                    ItemStack frag = plugin.items().create("fragmento_mejora", 1);
                    if (frag != null) {
                        plugin.items().give(p, frag);
                        Txt.send(p, "<gold>¡La Grieta dejó un Fragmento de Mejora!");
                    }
                }
            } else {
                Txt.send(p, "<red>La Grieta se cerró: " + reason + ".");
            }
        }
    }

    public void stopAll() {
        for (Rift r : new ArrayList<>(rifts.values())) finish(r, false, "el servidor se reinicia");
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        UUID id = playerRift.remove(p.getUniqueId());
        if (id == null) return;
        Rift r = rifts.get(id);
        if (r != null) r.players.remove(p.getUniqueId());
        Txt.send(p, "<red>Caíste en la Grieta.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = playerRift.remove(e.getPlayer().getUniqueId());
        if (id == null) return;
        Rift r = rifts.get(id);
        if (r != null) r.players.remove(e.getPlayer().getUniqueId());
    }
}
