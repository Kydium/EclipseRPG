package pe.kydium.eclipserpg.menu;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.data.PlayerData;
import pe.kydium.eclipserpg.quests.Quest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * HUD del jugador:
 * - El dinero (moneda + cantidad) abajo a la derecha, junto a la barra rápida (usa la letra del paquete de recursos).
 * - Puntos en la barra de localización (donde va la barra de experiencia) que marcan hacia dónde está la misión:
 *   el NPC que la da, la estructura o mazmorra que hay que encontrar, y la aldea.
 */
public final class Hud implements Listener {

    private static final String TAG = "eclipse_waypoint";
    private static final Key FONT = Key.key("eclipse", "hud");

    private final EclipseRPG plugin;
    /** jugador → [punto de misión, punto de aldea] */
    private final Map<UUID, UUID[]> marks = new HashMap<>();

    private final QuestPanel panel;

    public Hud(EclipseRPG plugin) {
        this.plugin = plugin;
        this.panel = new QuestPanel(plugin);
    }

    public QuestPanel panel() {
        return panel;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tick++;
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    money(p);
                    waypoints(p);
                    panel.update(p);
                } catch (RuntimeException ex) {
                    plugin.getLogger().fine("HUD: " + ex.getMessage());
                }
            }
        }, 40L, 20L);
    }

    // ---------------------------------------------------------------- dinero

    private int tick;

    private void money(Player p) {
        if (!plugin.getConfig().getBoolean("hud.monedas", true) || plugin.resourcePack() == null || !plugin.resourcePack().hasPack(p)) return;
        // la barra de acción dura ~3 s: se refresca cada 2 s
        if ((tick & 1) == 1) return;
        PlayerData d = plugin.data().get(p);
        String num = format(d.soles);
        int w = 9 + 4 + width(num); // moneda + espacio + número
        int shift = plugin.getConfig().getInt("hud.desplazamiento", 100);
        String text = space(shift) + " " + num + space(-(shift + w));
        p.sendActionBar(Component.text(text, NamedTextColor.WHITE).font(FONT));
    }

    private static String format(long v) {
        String s = Long.toString(Math.abs(v));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append('.');
            b.append(s.charAt(i));
        }
        return (v < 0 ? "-" : "") + b;
    }

    private static int width(String s) {
        int w = 0;
        for (char c : s.toCharArray()) w += c == '.' || c == ',' ? 2 : c == '-' ? 6 : 6;
        return w;
    }

    /** Espacio de N píxeles con los caracteres de la letra (positivos o negativos). */
    static String space(int px) {
        StringBuilder b = new StringBuilder();
        int base = px < 0 ? 0xF811 : 0xF801;
        int v = Math.abs(px);
        for (int i = 7; i >= 0; i--) {
            int bit = 1 << i;
            while (v >= bit) {
                b.append((char) (base + i));
                v -= bit;
            }
        }
        return b.toString();
    }

    // ---------------------------------------------------------------- barra de localización

    /** A dónde tiene que ir el jugador para su misión (o null). */
    public Location target(Player p) {
        Quest q = plugin.quests().current(p);
        if (q == null) return null;
        PlayerData d = plugin.data().get(p);
        if (d.questAccepted && !plugin.quests().isComplete(p, q)) {
            for (int i = 0; i < q.objectives.size(); i++) {
                Quest.Objective o = q.objectives.get(i);
                if (plugin.quests().progressOf(p, q, i) >= o.amount) continue;
                switch (o.type) {
                    case HABLAR -> {
                        for (String t : o.targets) {
                            Location l = plugin.npcEntities().spotOf(t);
                            if (l != null) return l;
                        }
                    }
                    case ESTRUCTURA -> {
                        String id = o.targets.isEmpty() ? q.structure : o.targets.get(0);
                        if (id != null && id.startsWith("ciudad_") && !plugin.cities().isBuilt(id.substring(7)))
                            plugin.cities().ensure(id.substring(7), p, false);
                        var inst = plugin.structures().nearest(id, p.getLocation());
                        if (inst != null) {
                            int[] en = inst.entrance != null ? inst.entrance : new int[]{inst.x, inst.y, inst.z};
                            var w = Bukkit.getWorld(inst.world);
                            if (w != null) return new Location(w, en[0] + 0.5, en[1], en[2] + 0.5);
                        }
                    }
                    case TESORO -> {
                        Location t = plugin.quests().extras().treasureOf(p, q);
                        if (t != null) return t;
                    }
                    case MAZMORRA -> {
                        Location l = plugin.dungeons().entranceLocation(q.dungeon > 0 ? q.dungeon : q.act);
                        if (l != null) return l;
                    }
                    default -> { }
                }
            }
            if (q.dungeon > 0) {
                Location l = plugin.dungeons().entranceLocation(q.dungeon);
                if (l != null) return l;
            }
            if (q.city != null) {
                var ci = plugin.cities().instance(q.city);
                if (ci != null && Bukkit.getWorld(ci.world) != null) return new Location(Bukkit.getWorld(ci.world), ci.x + 0.5, ci.y + 1, ci.z + 0.5);
            }
            if (q.structure != null) {
                var inst = plugin.structures().nearest(q.structure, p.getLocation());
                if (inst != null && Bukkit.getWorld(inst.world) != null) return new Location(Bukkit.getWorld(inst.world), inst.x + 0.5, inst.y, inst.z + 0.5);
            }
            return null;
        }
        // sin aceptar o lista para entregar: el NPC que la da
        return plugin.npcEntities().spotOf(q.npc);
    }

    private void waypoints(Player p) {
        boolean on = plugin.getConfig().getBoolean("hud.localizador", true);
        UUID[] m = marks.computeIfAbsent(p.getUniqueId(), k -> new UUID[2]);
        Location quest = on ? target(p) : null;
        Location village = on && plugin.getConfig().getBoolean("hud.localizador-aldea", true) ? plugin.village().center() : null;
        m[0] = place(p, m[0], quest, plugin.getConfig().getString("hud.color-mision", "gold"), 3);
        m[1] = place(p, m[1], village, plugin.getConfig().getString("hud.color-aldea", "green"), 40);
    }

    /** Pone (o mueve) el punto. Si el destino está lejos, el punto va en esa dirección a 64 bloques (siempre cargado). */
    private UUID place(Player p, UUID cur, Location to, String color, double hideNear) {
        Entity e = cur == null ? null : Bukkit.getEntity(cur);
        if (to == null || to.getWorld() == null || !to.getWorld().equals(p.getWorld()) || to.distanceSquared(p.getLocation()) < hideNear * hideNear) {
            if (e != null) e.remove();
            return null;
        }
        Location from = p.getLocation();
        var dir = to.toVector().subtract(from.toVector());
        double dist = dir.length();
        Location at = dist > 64 ? from.clone().add(dir.normalize().multiply(64)) : to.clone();
        at.setY(Math.max(at.getWorld().getMinHeight() + 1, Math.min(at.getWorld().getMaxHeight() - 1, to.getY())));
        if (e == null || !e.isValid() || !e.getWorld().equals(at.getWorld())) {
            if (e != null) e.remove();
            e = at.getWorld().spawn(at, ArmorStand.class, a -> {
                a.setMarker(true);
                a.setInvisible(true);
                a.setInvulnerable(true);
                a.setGravity(false);
                a.setSilent(true);
                a.setPersistent(false);
                a.addScoreboardTag(TAG);
                AttributeInstance r = a.getAttribute(Attribute.WAYPOINT_TRANSMIT_RANGE);
                if (r != null) r.setBaseValue(100000);
            });
            String cmd = "waypoint modify " + e.getUniqueId() + " color "
                    + (color.matches("#?[0-9a-fA-F]{6}") ? "hex " + color.replace("#", "") : color.toLowerCase(java.util.Locale.ROOT));
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            } catch (RuntimeException ignored) {
                // si el color no se puede poner, queda el color automático
            }
        } else e.teleport(at);
        return e.getUniqueId();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        panel.forget(e.getPlayer().getUniqueId());
        UUID[] m = marks.remove(e.getPlayer().getUniqueId());
        if (m != null) for (UUID u : m) {
            Entity en = u == null ? null : Bukkit.getEntity(u);
            if (en != null) en.remove();
        }
    }

    @EventHandler
    public void onLoad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) if (en.getScoreboardTags().contains(TAG)) en.remove();
    }

    public void cleanup() {
        for (UUID[] m : marks.values()) for (UUID u : m) {
            Entity en = u == null ? null : Bukkit.getEntity(u);
            if (en != null) en.remove();
        }
        marks.clear();
    }
}
