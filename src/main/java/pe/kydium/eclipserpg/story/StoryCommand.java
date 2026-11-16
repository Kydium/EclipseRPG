package pe.kydium.eclipserpg.story;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.data.PlayerData;
import pe.kydium.eclipserpg.util.Txt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /historia — crear, revisar, recargar y cambiar historias sin tocar el código. */
public final class StoryCommand implements org.bukkit.command.CommandExecutor, TabCompleter, Listener {

    private static final List<String> SUBS = List.of("info", "recargar", "revisar", "errores", "colocar", "skins", "lugares",
            "lista", "nueva", "usar");
    private final EclipseRPG plugin;

    public StoryCommand(EclipseRPG plugin) {
        this.plugin = plugin;
    }

    private static final String LINE = "<dark_gray><st>                                                  </st>";

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, @NotNull String[] a) {
        String sub = a.length == 0 ? "info" : a[0].toLowerCase(Locale.ROOT);
        StoryManager st = plugin.story();
        switch (sub) {
            case "info" -> info(s);
            case "recargar" -> {
                boolean force = a.length > 1 && a[1].equalsIgnoreCase("forzar");
                StoryParser.Result r = plugin.reloadStory(force);
                if (r.errors() > 0 && !force) {
                    Txt.send(s, "<red>✖ La historia tiene " + r.errors() + " error(es). <gray>Sigue en uso la versión anterior.");
                    showProblems(s, r.problems(), 8, true);
                    Txt.send(s, "<gray>Corrige el archivo y vuelve a usar <white>/historia recargar<gray>. Lista completa: <white>historias/"
                            + st.id() + "/errores.txt");
                } else {
                    Txt.send(s, "<green>✔ Historia recargada: <white>" + r.doc().missions.size() + "</white> misiones, <white>"
                            + r.doc().npcs.size() + "</white> NPCs" + (r.problems().isEmpty() ? "." : " <yellow>(" + r.problems().size() + " avisos)"));
                    if (!r.problems().isEmpty()) showProblems(s, r.problems(), 5, false);
                }
            }
            case "revisar" -> {
                StoryParser.Result r = st.read();
                st.reportOnly(r);
                if (r.problems().isEmpty()) Txt.send(s, "<green>✔ historia.txt está perfecto: " + r.doc().missions.size() + " misiones, "
                        + r.doc().npcs.size() + " NPCs. Usa /historia recargar para aplicarlo.");
                else {
                    Txt.send(s, (r.errors() > 0 ? "<red>✖ " + r.errors() + " error(es)" : "<green>✔ Sin errores") + "<gray>, "
                            + (r.problems().size() - r.errors()) + " aviso(s):");
                    showProblems(s, r.problems(), 10, false);
                }
            }
            case "errores" -> {
                if (st.problems().isEmpty()) Txt.send(s, "<green>✔ No hay errores ni avisos en la historia.");
                else showProblems(s, st.problems(), 30, false);
            }
            case "colocar" -> {
                if (!(s instanceof Player p)) {
                    Txt.send(s, "<red>Úsalo dentro del juego, parado donde quieres al NPC.");
                    return true;
                }
                if (a.length < 2) {
                    Txt.send(s, "<yellow>Uso: <white>/historia colocar <npc> <gray>(párate donde lo quieres) · <white>/historia colocar <npc> auto");
                    return true;
                }
                String id = StoryParser.slug(a[1]);
                if (plugin.npcs().def(id) == null) {
                    Txt.send(s, "<red>No hay un NPC '" + a[1] + "'. NPCs: <white>" + String.join(", ", plugin.npcs().ids()));
                    return true;
                }
                if (a.length > 2 && a[2].equalsIgnoreCase("auto")) {
                    plugin.getConfig().set("npc-lugares." + id, null);
                    plugin.npcEntities().placeStoryNpcs();
                    plugin.saveConfig();
                    Txt.send(s, "<green>✔ " + plugin.npcs().def(id).plainName + " vuelve a su lugar de la aldea.");
                } else {
                    plugin.npcEntities().setCustomSpot(id, p.getLocation());
                    Txt.send(s, "<green>✔ " + plugin.npcs().def(id).plainName + " ahora vive aquí. <gray>(se guarda aunque reinicies)");
                }
            }
            case "skins" -> {
                List<String> own = new ArrayList<>(), base = new ArrayList<>();
                st.skins().forEach((k, f) -> (f == null ? base : own).add(k));
                Txt.send(s, LINE);
                Txt.send(s, "<gold><bold>Skins disponibles</bold> <gray>(escribe el nombre en 'skin:')");
                Txt.send(s, "<white>" + String.join("<gray>, <white>", base));
                if (!own.isEmpty()) Txt.send(s, "<aqua>Tuyas: <white>" + String.join("<gray>, <white>", own));
                Txt.send(s, "<gray>Para usar tu propia skin: pon un PNG de 64x64 en <white>historias/" + st.id() + "/skins/");
                Txt.send(s, LINE);
            }
            case "lugares" -> {
                Txt.send(s, "<gold>Lugares de la aldea <gray>(para 'lugar:'): <white>" + String.join("<gray>, <white>", StoryManager.PLACES));
                Txt.send(s, "<gray>O párate en cualquier sitio y usa <white>/historia colocar <npc>");
            }
            case "lista" -> {
                Txt.send(s, "<gold>Historias en la carpeta <white>historias/<gold>:");
                for (String id : st.available())
                    Txt.send(s, (id.equals(st.id()) ? "<green>▶ " : "<gray>  ") + "<white>" + id + (id.equals(st.id()) ? " <green>(en uso)" : ""));
                Txt.send(s, "<gray>Crear: <white>/historia nueva <nombre> <gray>· Cambiar: <white>/historia usar <nombre>");
            }
            case "nueva" -> {
                if (a.length < 2) {
                    Txt.send(s, "<yellow>Uso: <white>/historia nueva <nombre>");
                    return true;
                }
                String id = StoryParser.slug(a[1]);
                if (id.isEmpty() || !st.create(id)) Txt.send(s, "<red>Ya existe una historia con ese nombre (o el nombre no es válido).");
                else {
                    Txt.send(s, "<green>✔ Creé <white>historias/" + id + "/historia.txt <green>con un ejemplo corto.");
                    Txt.send(s, "<gray>Edítalo con el Bloc de notas, revisa con <white>/historia revisar <gray>y actívalo con <white>/historia usar " + id);
                }
            }
            case "usar" -> {
                if (a.length < 2) {
                    Txt.send(s, "<yellow>Uso: <white>/historia usar <nombre>");
                    return true;
                }
                String id = StoryParser.slug(a[1]);
                if (!st.available().contains(id)) {
                    Txt.send(s, "<red>No existe la historia '" + id + "'. Mira /historia lista");
                    return true;
                }
                if (a.length < 3 || !a[2].equalsIgnoreCase("confirmar")) {
                    Txt.send(s, "<yellow>⚠ Cambiar de historia reinicia las misiones de todos los jugadores (no su nivel).");
                    Txt.send(s, "<yellow>Escribe <white>/historia usar " + id + " confirmar <yellow>para continuar.");
                    return true;
                }
                String prev = st.id();
                plugin.getConfig().set("historia", id);
                plugin.saveConfig();
                StoryParser.Result r = plugin.reloadStory(false);
                if (r.errors() > 0) {
                    plugin.getConfig().set("historia", prev);
                    plugin.saveConfig();
                    plugin.reloadStory(false);
                    Txt.send(s, "<red>✖ '" + id + "' tiene " + r.errors() + " error(es); sigo con '" + prev + "'.");
                    showProblems(s, r.problems(), 8, true);
                    return true;
                }
                resetQuests();
                Txt.send(s, "<green>✔ Ahora se juega <white>" + r.doc().title + "<green>. Las misiones empiezan de cero.");
            }
            default -> Txt.send(s, "<yellow>Subcomandos: <white>" + String.join(", ", SUBS));
        }
        return true;
    }

    private void info(CommandSender s) {
        StoryManager st = plugin.story();
        StoryDoc d = st.doc();
        Txt.send(s, LINE);
        Txt.send(s, "<gold><bold>" + d.title + "</bold> <gray>(" + st.id() + ")");
        if (!d.description.isEmpty()) Txt.send(s, "<gray><italic>" + d.description);
        Txt.send(s, "<white>" + d.missions.size() + "<gray> misiones · <white>" + d.npcs.size() + "<gray> NPCs · <white>" + d.acts.size() + "<gray> actos");
        long err = st.errorCount(), warn = st.problems().size() - err;
        Txt.send(s, err > 0 ? "<red>✖ " + err + " error(es) <gray>— /historia errores" : "<green>✔ Sin errores" + (warn > 0 ? " <yellow>(" + warn + " avisos)" : ""));
        Txt.send(s, "<gray>Archivo: <white>plugins/EclipseRPG/historias/" + st.id() + "/historia.txt");
        Txt.send(s, "<gray>Comandos: <white>revisar · recargar · errores · colocar · skins · lugares · lista · nueva · usar");
        Txt.send(s, LINE);
    }

    private void showProblems(CommandSender s, List<StoryParser.Problem> ps, int max, boolean onlyErrors) {
        int n = 0;
        for (StoryParser.Problem p : ps) {
            if (onlyErrors && !p.error()) continue;
            if (n++ >= max) {
                Txt.send(s, "<gray>…y " + (ps.size() - max) + " más (historias/" + plugin.story().id() + "/errores.txt)");
                break;
            }
            String where = p.line() > 0 ? "<white>línea " + p.line() + "<gray>: " : "";
            Txt.send(s, (p.error() ? "<red>✖ " : "<yellow>⚠ ") + where + "<gray>" + escape(p.message()));
        }
    }

    private static String escape(String m) {
        return m.replace("<", "\\<");
    }

    private void resetQuests() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = plugin.data().get(p);
            d.questIndex = 0;
            d.questAccepted = false;
            d.questProgress = new int[0];
            d.dirty = true;
        }
        plugin.data().saveAll();
        java.io.File[] files = new java.io.File(plugin.getDataFolder(), "jugadores").listFiles((dir, n) -> n.endsWith(".yml"));
        if (files == null) return;
        for (java.io.File f : files) {
            var y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
            y.set("historia.mision", 0);
            y.set("historia.aceptada", false);
            y.set("historia.progreso", new ArrayList<Integer>());
            try {
                y.save(f);
            } catch (java.io.IOException ignored) {
                // no importa
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!p.hasPermission("eclipserpg.admin") || plugin.story().errorCount() == 0) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> Txt.send(p, "<red>⚠ La historia tiene " + plugin.story().errorCount()
                + " error(es). <gray>Mira <white>/historia errores"), 60L);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String x : SUBS) if (x.startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(x);
        } else if (a.length == 2 && a[0].equalsIgnoreCase("colocar")) {
            for (String x : plugin.npcs().ids()) if (x.startsWith(a[1].toLowerCase(Locale.ROOT))) out.add(x);
        } else if (a.length == 2 && a[0].equalsIgnoreCase("usar")) {
            out.addAll(plugin.story().available());
        } else if (a.length == 3 && a[0].equalsIgnoreCase("colocar")) {
            out.add("auto");
        } else if (a.length == 3 && a[0].equalsIgnoreCase("usar")) {
            out.add("confirmar");
        } else if (a.length == 2 && a[0].equalsIgnoreCase("recargar")) {
            out.add("forzar");
        }
        return out;
    }
}
