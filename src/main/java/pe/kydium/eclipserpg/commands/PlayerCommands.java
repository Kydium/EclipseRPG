package pe.kydium.eclipserpg.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.data.PlayerData;
import pe.kydium.eclipserpg.rpg.StatsMenu;
import pe.kydium.eclipserpg.util.Txt;

import java.util.ArrayList;
import java.util.List;

/** /nivel, /soles y /pagar */
public final class PlayerCommands implements CommandExecutor, TabCompleter {
    private final EclipseRPG plugin;

    public PlayerCommands(EclipseRPG plugin) {
        this.plugin = plugin;
    }

    /** /guardian panel [ocultar|mostrar|izquierda|derecha|arriba|abajo|escala <1-6>|<número>] */
    private void panel(Player p, String[] args) {
        var pn = plugin.hud().panel();
        String a = args.length > 1 ? args[1].toLowerCase() : "";
        switch (a) {
            case "ocultar", "off", "no" -> {
                pn.setOn(p, false);
                Txt.send(p, "<gray>Panel de misión oculto. Vuelve con <white>/g panel mostrar");
            }
            case "mostrar", "on", "si", "sí" -> {
                pn.setOn(p, true);
                Txt.send(p, "<green>Panel de misión visible.");
            }
            case "izquierda", "izq" -> pn.setOffset(p, pn.offset(p) - 10);
            case "derecha", "der" -> pn.setOffset(p, pn.offset(p) + 10);
            case "arriba", "subir" -> pn.setTop(p, pn.top(p) - 12);
            case "abajo", "bajar" -> pn.setTop(p, pn.top(p) + 12);
            case "escala", "tamaño", "tamano", "interfaz" -> {
                try {
                    pn.setScale(p, Integer.parseInt(args[2]));
                    Txt.send(p, "<green>Panel acomodado para el tamaño de interfaz " + pn.scale(p) + ".");
                } catch (RuntimeException e) {
                    Txt.send(p, "<gray>Usa <white>/g panel escala <1-6></white> con el número de Opciones > Gráficos > Tamaño de interfaz.");
                }
            }
            default -> {
                try {
                    pn.setOffset(p, Integer.parseInt(a));
                } catch (NumberFormatException e) {
                    plugin.hotkeys().panelMenu(p);
                }
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Solo jugadores.");
            return true;
        }
        switch (cmd.getName().toLowerCase()) {
            case "nivel" -> new StatsMenu(plugin, p).open();
            case "guardian" -> {
                if (args.length > 0 && args[0].toLowerCase().startsWith("pan")) panel(p, args);
                else if (args.length > 0 && args[0].toLowerCase().startsWith("br")) plugin.quests().missionCompass(p);
                else if (args.length > 0 && args[0].toLowerCase().startsWith("est")) plugin.hotkeys().status(p);
                else if (args.length > 0 && (args[0].toLowerCase().startsWith("com") || args[0].toLowerCase().startsWith("tien"))) plugin.hotkeys().traders(p);
                else plugin.hotkeys().open(p);
            }
            case "misiones" -> pe.kydium.eclipserpg.quests.QuestBook.open(plugin, p);
            case "mision" -> {
                if (args.length >= 2 && args[0].equalsIgnoreCase("elegir")) {
                    try {
                        plugin.quests().choose(p, Integer.parseInt(args[1]));
                    } catch (NumberFormatException e) {
                        Txt.send(p, "<red>Opción inválida.");
                    }
                } else {
                    var q = plugin.quests().current(p);
                    if (q == null) Txt.send(p, "<gold>Ya completaste toda la historia.");
                    else plugin.quests().showObjectives(p, q);
                }
            }
            case "soles" -> {
                PlayerData d = plugin.data().get(p);
                Txt.send(p, "Tienes <yellow>" + Txt.num(d.soles) + " ☀ Soles</yellow>.");
            }
            case "pagar" -> {
                if (args.length < 2) {
                    Txt.send(p, "<gray>Uso: /pagar <jugador> <cantidad>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null || target == p) {
                    Txt.send(p, "<red>Ese jugador no está conectado.");
                    return true;
                }
                long amount;
                try {
                    amount = Long.parseLong(args[1]);
                } catch (NumberFormatException e) {
                    Txt.send(p, "<red>Cantidad inválida.");
                    return true;
                }
                if (amount <= 0) {
                    Txt.send(p, "<red>La cantidad debe ser mayor a 0.");
                    return true;
                }
                if (!plugin.levels().takeSoles(p, amount)) {
                    Txt.send(p, "<red>No tienes suficientes Soles.");
                    return true;
                }
                plugin.levels().addSoles(target, amount);
                Txt.send(p, "<green>Le pagaste <yellow>" + Txt.num(amount) + " ☀</yellow> a " + target.getName() + ".");
                Txt.send(target, "<green>" + p.getName() + " te pagó <yellow>" + Txt.num(amount) + " ☀</yellow>.");
            }
            default -> { }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (cmd.getName().equalsIgnoreCase("pagar") && args.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) out.add(p.getName());
            }
        }
        return out;
    }
}
