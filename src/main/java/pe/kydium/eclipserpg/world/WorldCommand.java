package pe.kydium.eclipserpg.world;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.util.Txt;

import java.util.ArrayList;
import java.util.List;

/** /evento lista | iniciar &lt;id&gt; | parar [id] | clima &lt;tipo&gt; [segundos] | estado */
public final class WorldCommand implements TabExecutor {
    private final EclipseRPG plugin;

    public WorldCommand(EclipseRPG plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        WorldManager w = plugin.world();
        String sub = a.length == 0 ? "estado" : a[0].toLowerCase();
        switch (sub) {
            case "lista" -> {
                Txt.send(s, "<yellow>Eventos del mundo:");
                for (String id : w.eventIds())
                    Txt.send(s, " <gray>• <white>" + id + " <gray>— <reset>" + w.eventName(id) + (w.activeEvents().contains(id) ? " <green>(activo)" : ""));
                if (w.eventIds().isEmpty()) Txt.send(s, " <gray>No hay ninguno. Créalos en el editor: «Mundo vivo».");
            }
            case "iniciar", "empezar", "start" -> {
                if (a.length < 2) Txt.send(s, "<red>Uso: /evento iniciar <id>");
                else if (w.start(a[1])) Txt.send(s, "<green>Evento iniciado: <reset>" + w.eventName(a[1].toLowerCase()));
                else Txt.send(s, "<red>No existe el evento '" + a[1] + "'. Mira /evento lista");
            }
            case "parar", "detener", "stop" -> {
                List<String> ids = a.length > 1 ? List.of(a[1].toLowerCase()) : new ArrayList<>(w.activeEvents());
                int n = 0;
                for (String id : ids) if (w.stop(id, false)) n++;
                Txt.send(s, n > 0 ? "<green>Eventos detenidos: " + n : "<gray>No había eventos activos.");
            }
            case "clima" -> {
                int secs = 600;
                if (a.length > 2) {
                    try {
                        secs = Integer.parseInt(a[2]);
                    } catch (NumberFormatException ignored) {
                        // 10 minutos
                    }
                }
                if (a.length > 1 && w.setWeather(a[1], secs)) Txt.send(s, "<green>Clima: " + a[1] + " durante " + secs + " s");
                else Txt.send(s, "<red>Uso: /evento clima <despejado|lluvia|tormenta> [segundos]");
            }
            default -> {
                Txt.send(s, "<yellow>Mundo: <white>" + w.describe());
                Txt.send(s, "<yellow>Eventos activos: <white>" + (w.activeEvents().isEmpty() ? "ninguno" : String.join(", ", w.activeEvents())));
                if (s instanceof Player p) Txt.send(s, "<yellow>Te afecta: <white>" + (w.rulesOn(p).isEmpty() ? "nada" : String.join(", ", w.rulesOn(p))));
                Txt.send(s, "<gray>/evento lista · iniciar <id> · parar · clima <tipo>");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) out.addAll(List.of("estado", "lista", "iniciar", "parar", "clima"));
        else if (a.length == 2 && a[0].equalsIgnoreCase("clima")) out.addAll(List.of("despejado", "lluvia", "tormenta"));
        else if (a.length == 2) out.addAll(plugin.world().eventIds());
        String last = a[a.length - 1].toLowerCase();
        out.removeIf(x -> !x.startsWith(last));
        return out;
    }
}
