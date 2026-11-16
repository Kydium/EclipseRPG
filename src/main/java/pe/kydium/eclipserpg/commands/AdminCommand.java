package pe.kydium.eclipserpg.commands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.data.PlayerData;
import pe.kydium.eclipserpg.util.Txt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * /eclipse — comandos de administración. Los NPCs de Citizens y BetonQuest los ejecutan
 * desde la consola con el nombre del jugador.
 */
public final class AdminCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = List.of(
            "tienda", "contratos", "grieta", "xp", "soles", "nivel", "item", "spawn",
            "info", "setcampamento", "recargar", "npc", "crearnpcs", "mision", "construiraldea", "brujula", "reiniciar", "mazmorra", "mazmorras");

    private final EclipseRPG plugin;

    public AdminCommand(EclipseRPG plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, @NotNull String[] a) {
        if (a.length == 0) {
            help(s);
            return true;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "recargar" -> {
                plugin.reloadAll();
                Txt.send(s, "<green>Configuración recargada.");
            }
            case "setcampamento" -> {
                if (!(s instanceof Player p)) {
                    Txt.send(s, "<red>Úsalo dentro del juego.");
                    return true;
                }
                Location l = p.getLocation();
                plugin.getConfig().set("campamento.mundo", l.getWorld().getName());
                plugin.getConfig().set("campamento.x", l.getX());
                plugin.getConfig().set("campamento.y", l.getY());
                plugin.getConfig().set("campamento.z", l.getZ());
                plugin.getConfig().set("campamento.yaw", l.getYaw());
                plugin.getConfig().set("campamento.pitch", l.getPitch());
                plugin.saveConfig();
                Txt.send(s, "<green>Campamento del Alba guardado aquí.");
            }
            case "npc" -> {
                // /eclipse npc <id> <jugador>  (lo usa Citizens al hacer clic)
                if (a.length < 3) return usage(s, "/eclipse npc <id> <jugador>");
                Player p = player(s, a[2]);
                if (p == null) return true;
                if (!plugin.npcs().open(p, a[1])) Txt.send(s, "<red>No existe el NPC " + a[1]);
            }
            case "construiraldea" -> {
                if (!(s instanceof Player p)) {
                    Txt.send(s, "<red>Úsalo dentro del juego.");
                    return true;
                }
                Location here = p.getLocation().getBlock().getLocation().add(0, -1, 0);
                plugin.village().build(here, p);
            }
            case "brujula" -> {
                Player p = a.length > 1 ? player(s, a[1]) : (s instanceof Player pl ? pl : null);
                if (p != null) plugin.village().giveCompass(p);
            }
            case "reiniciar" -> {
                // /eclipse reiniciar <jugador|todos>
                if (a.length < 2) return usage(s, "/eclipse reiniciar <jugador|todos>");
                List<Player> targets = new ArrayList<>();
                if (a[1].equalsIgnoreCase("todos")) targets.addAll(Bukkit.getOnlinePlayers());
                else {
                    Player p = player(s, a[1]);
                    if (p != null) targets.add(p);
                }
                for (Player p : targets) {
                    plugin.data().reset(p.getUniqueId());
                    plugin.levels().applyAll(p);
                    Txt.send(p, "<yellow>Tu progreso fue reiniciado. ¡Empieza de nuevo, Guardián!");
                }
                Txt.send(s, "<green>Reiniciados: " + targets.size());
            }
            case "mazmorra" -> {
                // /eclipse mazmorra <acto> [jugador]
                if (a.length < 2) return usage(s, "/eclipse mazmorra <acto 1-10> [jugador]");
                Long act = number(s, a[1]);
                Player p = a.length > 2 ? player(s, a[2]) : (s instanceof Player pl ? pl : null);
                if (act == null || p == null) return true;
                plugin.dungeons().ensure(act.intValue(), p);
            }
            case "mazmorras" -> {
                for (String line : plugin.dungeons().describe()) s.sendMessage(Txt.mm(line));
            }
            case "crearnpcs" -> {
                if (!(s instanceof Player p)) {
                    Txt.send(s, "<red>Úsalo dentro del juego.");
                    return true;
                }
                plugin.npcs().createAll(p);
            }
            case "mision" -> {
                // /eclipse mision <jugador> <numero>
                if (a.length < 3) return usage(s, "/eclipse mision <jugador> <número 1-" + plugin.quests().total() + ">");
                Player p = player(s, a[1]);
                Long n = number(s, a[2]);
                if (p == null || n == null) return true;
                plugin.quests().setQuest(p, n.intValue());
                var q = plugin.quests().current(p);
                Txt.send(s, p.getName() + " ahora está en la misión " + n + (q == null ? "" : ": " + q.name));
            }
            case "tienda" -> {
                // /eclipse tienda <id> <jugador>
                if (a.length < 3) return usage(s, "/eclipse tienda <id> <jugador>");
                Player p = player(s, a[2]);
                if (p == null) return true;
                if (!plugin.shops().open(p, a[1])) Txt.send(s, "<red>No existe la tienda " + a[1]);
            }
            case "contratos" -> {
                if (a.length < 2) return usage(s, "/eclipse contratos <jugador>");
                Player p = player(s, a[1]);
                if (p != null) plugin.contracts().open(p);
            }
            case "grieta" -> {
                if (a.length < 2) return usage(s, "/eclipse grieta <jugador>");
                Player p = player(s, a[1]);
                if (p != null) plugin.rifts().start(p);
            }
            case "xp" -> {
                if (a.length < 3) return usage(s, "/eclipse xp <jugador> <cantidad>");
                Player p = player(s, a[1]);
                Long n = number(s, a[2]);
                if (p != null && n != null) plugin.levels().reward(p, n, 0);
            }
            case "soles" -> {
                if (a.length < 4) return usage(s, "/eclipse soles <jugador> <dar|quitar|poner> <cantidad>");
                Player p = player(s, a[1]);
                Long n = number(s, a[3]);
                if (p == null || n == null) return true;
                PlayerData d = plugin.data().get(p);
                switch (a[2].toLowerCase(Locale.ROOT)) {
                    case "dar", "add" -> plugin.levels().addSoles(p, n);
                    case "quitar", "take" -> plugin.levels().addSoles(p, -n);
                    case "poner", "set" -> { d.soles = Math.max(0, n); d.dirty = true; }
                    default -> { return usage(s, "/eclipse soles <jugador> <dar|quitar|poner> <cantidad>"); }
                }
                Txt.send(s, p.getName() + " tiene ahora <yellow>" + Txt.num(d.soles) + " ☀");
            }
            case "nivel" -> {
                if (a.length < 3) return usage(s, "/eclipse nivel <jugador> <nivel>");
                Player p = player(s, a[1]);
                Long n = number(s, a[2]);
                if (p == null || n == null) return true;
                plugin.levels().setLevel(p, n.intValue());
                Txt.send(s, p.getName() + " ahora es nivel " + plugin.data().get(p).level);
            }
            case "item" -> {
                if (a.length < 3) return usage(s, "/eclipse item <jugador> <id> [cantidad]");
                Player p = player(s, a[1]);
                if (p == null) return true;
                Long parsed = a.length > 3 ? number(s, a[3]) : Long.valueOf(1);
                int amount = parsed == null ? 1 : (int) Math.max(1, Math.min(64, parsed));
                ItemStack it = plugin.items().create(a[2], amount);
                if (it == null) {
                    Txt.send(s, "<red>No existe el objeto " + a[2]);
                    return true;
                }
                plugin.items().give(p, it);
            }
            case "spawn" -> {
                if (a.length < 3) return usage(s, "/eclipse spawn <mob> <jugador> [cantidad]");
                Player p = player(s, a[2]);
                if (p == null) return true;
                if (plugin.mobs().def(a[1]) == null) {
                    Txt.send(s, "<red>No existe el mob " + a[1]);
                    return true;
                }
                Long parsed = a.length > 3 ? number(s, a[3]) : Long.valueOf(1);
                int amount = parsed == null ? 1 : parsed.intValue();
                for (int i = 0; i < Math.max(1, Math.min(30, amount)); i++) {
                    LivingEntity le = plugin.mobs().spawn(a[1], plugin.mobs().spawnSpot(p.getLocation(), 6));
                    if (le == null) break;
                }
            }
            case "info" -> {
                if (a.length < 2) return usage(s, "/eclipse info <jugador>");
                Player p = player(s, a[1]);
                if (p == null) return true;
                PlayerData d = plugin.data().get(p);
                Txt.send(s, p.getName() + ": nivel " + d.level + " · XP " + d.xp + "/" + plugin.levels().xpNeeded(d.level)
                        + " · puntos " + d.points + " · " + Txt.num(d.soles) + " ☀ · stats " + d.stats);
            }
            default -> help(s);
        }
        return true;
    }

    private void help(CommandSender s) {
        Txt.send(s, "<gold>Comandos de EclipseRPG:");
        String[] lines = {
                "/eclipse tienda <id> <jugador>", "/eclipse contratos <jugador>", "/eclipse grieta <jugador>",
                "/eclipse xp <jugador> <n>", "/eclipse soles <jugador> <dar|quitar|poner> <n>",
                "/eclipse nivel <jugador> <n>", "/eclipse item <jugador> <id> [n]",
                "/eclipse spawn <mob> <jugador> [n]", "/eclipse info <jugador>",
                "/eclipse npc <id> <jugador>", "/eclipse crearnpcs", "/eclipse mision <jugador> <n>",
                "/eclipse construiraldea", "/eclipse brujula [jugador]", "/eclipse reiniciar <jugador|todos>", "/eclipse mazmorra <acto> [jugador]", "/eclipse mazmorras",
                "/eclipse setcampamento", "/eclipse recargar"};
        for (String l : lines) s.sendMessage(Txt.mm("<gray>" + l));
    }

    private boolean usage(CommandSender s, String u) {
        Txt.send(s, "<gray>Uso: " + u);
        return true;
    }

    private Player player(CommandSender s, String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p == null) Txt.send(s, "<red>Jugador no conectado: " + name);
        return p;
    }

    private Long number(CommandSender s, String v) {
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            Txt.send(s, "<red>Número inválido: " + v);
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String alias, @NotNull String[] a) {
        List<String> opts = new ArrayList<>();
        if (a.length == 1) opts.addAll(SUBS);
        else {
            String sub = a[0].toLowerCase(Locale.ROOT);
            if (a.length == 2) {
                switch (sub) {
                    case "tienda" -> opts.addAll(plugin.shops().ids());
                    case "npc" -> opts.addAll(plugin.npcs().ids());
                    case "mision" -> opts.addAll(names());
                    case "spawn" -> opts.addAll(plugin.mobs().ids());
                    case "contratos", "grieta", "xp", "soles", "nivel", "item", "info", "brujula" -> opts.addAll(names());
                    case "reiniciar" -> { opts.add("todos"); opts.addAll(names()); }
                    default -> { }
                }
            } else if (a.length == 3) {
                switch (sub) {
                    case "tienda", "spawn", "npc" -> opts.addAll(names());
                    case "item" -> opts.addAll(plugin.items().ids());
                    case "soles" -> opts.addAll(List.of("dar", "quitar", "poner"));
                    default -> { }
                }
            }
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        opts.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(last));
        return opts;
    }

    private Collection<String> names() {
        List<String> n = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) n.add(p.getName());
        return n;
    }
}
