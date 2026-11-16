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

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Solo jugadores.");
            return true;
        }
        switch (cmd.getName().toLowerCase()) {
            case "nivel" -> new StatsMenu(plugin, p).open();
            case "guardian" -> plugin.hotkeys().open(p);
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
