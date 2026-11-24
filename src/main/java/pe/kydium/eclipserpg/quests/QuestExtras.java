package pe.kydium.eclipserpg.quests;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.data.PlayerData;
import pe.kydium.eclipserpg.util.Txt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Misiones con más variedad:
 * - "objetivo: resiste 90 segundos" + "oleadas: zombi_corrompido 3 cada 20": aguanta mientras llegan oleadas.
 * - "objetivo: busca el tesoro": se entierra un cofre lejos; el punto dorado y el mapa llevan a él.
 * - "acertijo / correcta / incorrecta": el NPC te pone una pregunta con varias respuestas.
 * - "tiempo: 5 min": cuenta atrás; si no terminas a tiempo, la misión vuelve a empezar.
 */
public final class QuestExtras implements Listener {

    private final EclipseRPG plugin;
    private final QuestManager qm;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Integer> waveClock = new HashMap<>();
    private final Map<UUID, Long> riddleWait = new HashMap<>();

    QuestExtras(EclipseRPG plugin, QuestManager qm) {
        this.plugin = plugin;
        this.qm = qm;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 60L, 20L);
    }

    // ---------------------------------------------------------------- datos guardados (en las marcas del jugador)

    private static String get(PlayerData d, String prefix) {
        for (String f : d.flags) if (f.startsWith(prefix)) return f.substring(prefix.length());
        return null;
    }

    private static void put(PlayerData d, String prefix, String value) {
        d.flags.removeIf(f -> f.startsWith(prefix));
        if (value != null) d.flags.add(prefix + value);
        d.dirty = true;
    }

    /** Al aceptar: plazo y tesoro. */
    void onAccept(Player p, Quest q) {
        PlayerData d = plugin.data().get(p);
        clear(p, q);
        if (q.timeLimit > 0) {
            put(d, "plazo:" + q.id + ":", String.valueOf(System.currentTimeMillis() + q.timeLimit * 1000L));
            Txt.send(p, "<gold>⏳ Tienes <white>" + clock(q.timeLimit) + "</white> para completarla.");
        }
        for (Quest.Objective o : q.objectives) {
            if (o.type == Quest.Type.TESORO) {
                Location t = buryTreasure(p);
                if (t != null) {
                    put(d, "tesoro:" + q.id + ":", t.getWorld().getName() + ";" + t.getBlockX() + ";" + t.getBlockY() + ";" + t.getBlockZ());
                    Txt.send(p, "<gold>🗺 Hay un tesoro enterrado a unos " + (int) t.distance(p.getLocation())
                            + " bloques. <gray>Sigue el <gold>punto dorado</gold> y cava donde brillan las partículas.");
                }
            }
            if (o.type == Quest.Type.SOBREVIVIR) waveClock.put(p.getUniqueId(), 0);
        }
    }

    /** Al terminar o fallar: se borra lo de esta misión. */
    void clear(Player p, Quest q) {
        PlayerData d = plugin.data().get(p);
        d.flags.removeIf(f -> f.startsWith("plazo:" + q.id + ":") || f.startsWith("tesoro:" + q.id + ":"));
        d.dirty = true;
        BossBar b = bars.remove(p.getUniqueId());
        if (b != null) p.hideBossBar(b);
        waveClock.remove(p.getUniqueId());
    }

    public String timeLeft(Player p, Quest q) {
        String dl = get(plugin.data().get(p), "plazo:" + q.id + ":");
        if (dl == null) return null;
        long left = Math.max(0, (Long.parseLong(dl) - System.currentTimeMillis()) / 1000);
        return clock((int) left);
    }

    private static String clock(int s) {
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    // ---------------------------------------------------------------- tesoro

    private Location buryTreasure(Player p) {
        World w = p.getWorld();
        Location base = plugin.village().center() != null && plugin.village().center().getWorld() == w ? plugin.village().center() : p.getLocation();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int tries = 0; tries < 30; tries++) {
            double a = r.nextDouble() * Math.PI * 2, dist = 130 + r.nextDouble() * 140;
            int x = base.getBlockX() + (int) (Math.cos(a) * dist), z = base.getBlockZ() + (int) (Math.sin(a) * dist);
            int y = w.getHighestBlockYAt(x, z);
            Block top = w.getBlockAt(x, y, z);
            if (top.isLiquid() || top.getType().name().contains("LEAVES") || !top.getType().isSolid()) continue;
            Block chest = w.getBlockAt(x, y - 2, z);
            chest.setType(Material.CHEST, false);
            if (chest.getState() instanceof Chest c) {
                c.getInventory().clear();
                plugin.structures().fillTable(c.getInventory(), pe.kydium.eclipserpg.structure.StructureManager.LOOT_TREASURE, 4, 5);
                c.customName(Txt.mm("<gold>Tesoro enterrado"));
                c.update(true, false);
            }
            // tierra encima para cavar
            w.getBlockAt(x, y - 1, z).setType(w.getBlockAt(x, y - 1, z).getType().isSolid() ? w.getBlockAt(x, y - 1, z).getType() : Material.DIRT, false);
            return chest.getLocation();
        }
        return null;
    }

    public Location treasureOf(Player p, Quest q) {
        String v = get(plugin.data().get(p), "tesoro:" + q.id + ":");
        if (v == null) return null;
        String[] a = v.split(";");
        World w = Bukkit.getWorld(a[0]);
        return w == null ? null : new Location(w, Integer.parseInt(a[1]) + 0.5, Integer.parseInt(a[2]), Integer.parseInt(a[3]) + 0.5);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOpen(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null || e.getClickedBlock().getType() != Material.CHEST) return;
        Player p = e.getPlayer();
        Quest q = qm.current(p);
        if (q == null || !plugin.data().get(p).questAccepted) return;
        Location t = treasureOf(p, q);
        if (t == null || !t.getBlock().equals(e.getClickedBlock())) return;
        qm.addProgress(p, Quest.Type.TESORO, null, 1, false);
        put(plugin.data().get(p), "tesoro:" + q.id + ":", null);
        p.showTitle(net.kyori.adventure.title.Title.title(Txt.mm("<gold>¡Tesoro!"), Txt.mm("<gray>Encontraste el cofre enterrado")));
        Txt.sound(p, "ui.toast.challenge_complete", 0.8f, 1.2f);
    }

    // ---------------------------------------------------------------- acertijo

    /** ¿La misión espera que resuelvas un acertijo ahora? */
    boolean pendingRiddle(Player p, Quest q) {
        if (q.riddle == null || q.answer == null) return false;
        for (int i = 0; i < q.objectives.size(); i++)
            if (q.objectives.get(i).type == Quest.Type.RESPONDER && qm.progressOf(p, q, i) < 1) {
                // primero lo demás
                for (int j = 0; j < q.objectives.size(); j++)
                    if (j != i && q.objectives.get(j).type != Quest.Type.RESPONDER && qm.progressOf(p, q, j) < q.objectives.get(j).amount) return false;
                return true;
            }
        return false;
    }

    void askRiddle(Player p, Quest q, String npcName, DialogUI ui) {
        Long wait = riddleWait.get(p.getUniqueId());
        if (wait != null && wait > System.currentTimeMillis()) {
            Txt.send(p, "<gray>" + npcName + "<gray>: Piénsalo un poco más… vuelve en unos segundos.");
            return;
        }
        List<String> answers = new ArrayList<>(q.wrong);
        answers.add(q.answer);
        Collections.shuffle(answers);
        List<DialogUI.Button> btns = new ArrayList<>();
        for (String a : answers) {
            boolean ok = a.equals(q.answer);
            btns.add(new DialogUI.Button("<white>" + a, null, () -> {
                if (ok) {
                    qm.addProgress(p, Quest.Type.RESPONDER, null, 1, false);
                    Txt.sound(p, "entity.player.levelup", 0.8f, 1.4f);
                    Txt.send(p, "<green>✔ " + npcName + "<green>: ¡Exacto!");
                    Bukkit.getScheduler().runTaskLater(plugin, () -> qm.talk(p, q.npc), 10L);
                } else {
                    riddleWait.put(p.getUniqueId(), System.currentTimeMillis() + 8000);
                    Txt.sound(p, "entity.villager.no", 1f, 1f);
                    Txt.send(p, "<red>✖ " + npcName + "<red>: No… no es eso. Piénsalo y vuelve a hablarme.");
                }
            }));
        }
        List<String> body = new ArrayList<>();
        body.add("<gold>Acertijo:");
        body.add("<white>" + q.riddle);
        ui.show(p, npcName + " <dark_gray>· <yellow>" + q.name, body, btns, true);
    }

    // ---------------------------------------------------------------- cada segundo

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Quest q = qm.current(p);
            PlayerData d = plugin.data().get(p);
            if (q == null || !d.questAccepted) {
                BossBar b = bars.remove(p.getUniqueId());
                if (b != null) p.hideBossBar(b);
                continue;
            }
            // plazo
            String dl = get(d, "plazo:" + q.id + ":");
            if (dl != null) {
                long left = (Long.parseLong(dl) - now) / 1000;
                if (qm.isComplete(p, q)) {
                    BossBar b = bars.remove(p.getUniqueId());
                    if (b != null) p.hideBossBar(b);
                } else if (left <= 0) {
                    clear(p, q);
                    d.questAccepted = false;
                    d.questProgress = new int[q.objectives.size()];
                    d.dirty = true;
                    p.showTitle(net.kyori.adventure.title.Title.title(Txt.mm("<red>⏳ Se acabó el tiempo"),
                            Txt.mm("<gray>Habla con " + plugin.npcs().name(q.npc) + " <gray>para intentarlo otra vez")));
                    Txt.sound(p, "block.bell.use", 1f, 0.6f);
                    continue;
                } else {
                    BossBar b = bars.computeIfAbsent(p.getUniqueId(), k -> {
                        BossBar nb = BossBar.bossBar(Txt.mm(""), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
                        p.showBossBar(nb);
                        return nb;
                    });
                    b.name(Txt.mm("<gold>⏳ " + q.name + " <white>" + clock((int) left)));
                    b.progress(Math.max(0f, Math.min(1f, left / (float) Math.max(1, q.timeLimit))));
                    b.color(left < 30 ? BossBar.Color.RED : BossBar.Color.YELLOW);
                }
            }
            // resistir + oleadas
            for (int i = 0; i < q.objectives.size(); i++) {
                Quest.Objective o = q.objectives.get(i);
                if (o.type != Quest.Type.SOBREVIVIR || qm.progressOf(p, q, i) >= o.amount) continue;
                if (p.isDead()) break;
                int c = waveClock.merge(p.getUniqueId(), 1, Integer::sum);
                if (!q.waves.isEmpty() && (c == 1 || c % Math.max(5, q.waveEvery) == 0)) wave(p, q);
                qm.addProgress(p, Quest.Type.SOBREVIVIR, null, 1, false);
                break;
            }
            // pista del tesoro (si falta el cofre, se entierra ahora)
            Location t = treasureOf(p, q);
            if (t == null) {
                for (int i = 0; i < q.objectives.size(); i++)
                    if (q.objectives.get(i).type == Quest.Type.TESORO && qm.progressOf(p, q, i) < 1) {
                        Location nt = buryTreasure(p);
                        if (nt != null) put(d, "tesoro:" + q.id + ":", nt.getWorld().getName() + ";" + nt.getBlockX() + ";" + nt.getBlockY() + ";" + nt.getBlockZ());
                        t = nt;
                        break;
                    }
            }
            if (t != null && t.getWorld() == p.getWorld()) {
                double dist = t.distance(p.getLocation());
                if (dist < 45) p.sendActionBar(Txt.mm("<gold>🗺 El tesoro está cerca… <white>" + (int) dist + " bloques"));
                if (dist < 14) p.spawnParticle(Particle.HAPPY_VILLAGER, t.clone().add(0, 2.6, 0), 10, 0.3, 0.3, 0.3, 0);
            }
        }
    }

    private void wave(Player p, Quest q) {
        // como una invasión: aparecen lejos, por un lado, y vienen caminando
        List<String> ids = new ArrayList<>();
        for (Quest.Spawn sp : q.waves) for (int i = 0; i < sp.amount(); i++) ids.add(sp.mob());
        plugin.raids().send(p, ids);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Quest q = qm.current(p);
        PlayerData d = plugin.data().get(p);
        if (q == null || !d.questAccepted) return;
        boolean reset = false;
        for (int i = 0; i < q.objectives.size(); i++)
            if (q.objectives.get(i).type == Quest.Type.SOBREVIVIR && i < d.questProgress.length && d.questProgress[i] < q.objectives.get(i).amount) {
                d.questProgress[i] = 0;
                reset = true;
            }
        if (reset) {
            d.dirty = true;
            waveClock.put(p.getUniqueId(), 0);
            Txt.send(p, "<red>Caíste: tienes que resistir desde el principio.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        bars.remove(e.getPlayer().getUniqueId());
    }
}
