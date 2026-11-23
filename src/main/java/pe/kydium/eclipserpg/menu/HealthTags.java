package pe.kydium.eclipserpg.menu;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import pe.kydium.eclipserpg.EclipseRPG;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Vida de cada jugador debajo de su nombre ("18/24 ❤"), para que los demás la vean. */
public final class HealthTags {

    private static final String OBJ = "eclipse_vida";
    private final EclipseRPG plugin;
    private final Map<UUID, String> last = new HashMap<>();

    public HealthTags(EclipseRPG plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("hud.vida-sobre-la-cabeza", true)) {
            Objective o = Bukkit.getScoreboardManager().getMainScoreboard().getObjective(OBJ);
            if (o != null) o.unregister();
            return;
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 5L);
    }

    private Objective objective() {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective o = sb.getObjective(OBJ);
        if (o == null) o = sb.registerNewObjective(OBJ, Criteria.DUMMY, Component.empty());
        if (!Component.empty().equals(o.displayName())) o.displayName(Component.empty());
        if (o.getDisplaySlot() != DisplaySlot.BELOW_NAME) o.setDisplaySlot(DisplaySlot.BELOW_NAME);
        return o;
    }

    private int n;

    private void tick() {
        Objective o = objective();
        // los NPCs (maniquíes) no muestran nada debajo del nombre
        if (n++ % 8 != 0) {
            players(o);
            return;
        }
        java.util.Set<String> alive = new java.util.HashSet<>();
        for (org.bukkit.World w : Bukkit.getWorlds())
            for (org.bukkit.entity.Mannequin m : w.getEntitiesByClass(org.bukkit.entity.Mannequin.class)) {
                alive.add(m.getUniqueId().toString());
                var sc = o.getScoreFor(m);
                if (sc.isScoreSet()) continue;
                sc.setScore(0);
                try {
                    sc.numberFormat(NumberFormat.blank());
                } catch (RuntimeException | LinkageError ignored) {
                    // nada
                }
            }
        // borra los de maniquíes que ya no están (cuerpos que desaparecieron, NPCs de otra versión…)
        Scoreboard sb = o.getScoreboard();
        if (sb != null) for (String entry : sb.getEntries())
            if (entry.length() == 36 && entry.charAt(8) == '-' && !alive.contains(entry) && o.getScore(entry).isScoreSet()) o.getScore(entry).resetScore();
        players(o);
    }

    private void players(Objective o) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            var max = p.getAttribute(Attribute.MAX_HEALTH);
            int hp = (int) Math.ceil(p.getHealth() + p.getAbsorptionAmount());
            int mx = (int) Math.round(max == null ? 20 : max.getValue());
            String key = hp + "/" + mx;
            if (key.equals(last.get(p.getUniqueId())) && o.getScore(p.getName()).isScoreSet()) continue;
            last.put(p.getUniqueId(), key);
            double f = mx <= 0 ? 1 : (double) hp / mx;
            NamedTextColor c = f > 0.6 ? NamedTextColor.GREEN : f > 0.3 ? NamedTextColor.YELLOW : NamedTextColor.RED;
            var score = o.getScore(p.getName());
            score.setScore(hp);
            try {
                score.numberFormat(NumberFormat.fixed(Component.text(hp, c).append(Component.text("/" + mx, NamedTextColor.GRAY))
                        .append(Component.text(" ❤", NamedTextColor.RED))));
            } catch (RuntimeException | LinkageError ignored) {
                // sin formato: se ve solo el número
            }
        }
    }
}
