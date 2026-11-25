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

    private static final List<String> SUBS = List.of("info", "editor", "recargar", "revisar", "errores", "colocar", "skins", "lugares",
            "lista", "nueva", "usar", "quitar", "poner", "exportar");
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
            case "editor" -> {
                if (plugin.editor() == null || plugin.resourcePack().server() == null) {
                    Txt.send(s, "<red>El editor no está disponible (el puerto del servidor web no se pudo abrir). Mira la consola.");
                    return true;
                }
                String url = plugin.editor().url(s instanceof Player p ? p : null);
                s.sendMessage(Txt.mm("<gold>✎ Editor visual: </gold><aqua><u><click:open_url:'" + url + "'>clic aquí para abrirlo</click></u>"));
                Txt.send(s, "<gray>Ábrelo en el navegador de la PC donde está el servidor (o de un amigo con Tailscale).");
                Txt.send(s, "<dark_gray>No compartas este link: permite editar la historia.");
            }
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
                for (String n : st.installZips()) Txt.send(s, "<green>✔ Instalé la historia <white>" + n + " <green>(venía en un .zip)");
                Txt.send(s, "<gold>Historias en la carpeta <white>historias/<gold>:");
                for (String id : st.available())
                    Txt.send(s, (id.equals(st.id()) ? "<green>▶ " : "<gray>  ") + "<white>" + id + (id.equals(st.id()) ? " <green>(en uso)" : ""));
                List<String> sv = new ArrayList<>(st.savedIds());
                sv.removeAll(st.available());
                if (!sv.isEmpty()) Txt.send(s, "<gray>Guardadas (sin poner): <white>" + String.join("<gray>, <white>", sv) + " <gray>→ <white>/historia poner <nombre>");
                Txt.send(s, "<gray>Crear: <white>/historia nueva <nombre> <gray>· Cambiar: <white>/historia usar <nombre>");
                Txt.send(s, "<gray>Sacar: <white>/historia quitar <nombre> <gray>· Copia en zip: <white>/historia exportar <nombre>");
            }
            case "exportar", "quitar", "poner" -> {
                if (a.length < 2) {
                    Txt.send(s, "<yellow>Uso: <white>/historia " + a[0].toLowerCase(java.util.Locale.ROOT) + " <nombre>");
                    return true;
                }
                String id = StoryParser.slug(a[1]);
                String act = a[0].toLowerCase(java.util.Locale.ROOT);
                try {
                    if (act.equals("poner")) {
                        java.io.File z = new java.io.File(st.saved(), id + ".zip");
                        if (!z.exists()) {
                            Txt.send(s, "<red>No hay <white>historias-guardadas/" + id + ".zip<red>. Pon el .zip en la carpeta historias/ y usa /historia lista.");
                            return true;
                        }
                        if (st.available().contains(id)) {
                            Txt.send(s, "<yellow>'" + id + "' ya está puesta. <gray>(Para reemplazarla con el zip, quítala primero.)");
                            return true;
                        }
                        String got = st.install(z);
                        Txt.send(s, got == null ? "<red>Ese zip no trae historia.txt." : "<green>✔ Puse la historia <white>" + got + "<green>. Juégala con <white>/historia usar " + got);
                        return true;
                    }
                    if (!st.available().contains(id)) {
                        Txt.send(s, "<red>No existe la historia '" + id + "'. Mira /historia lista");
                        return true;
                    }
                    if (act.equals("exportar")) {
                        java.io.File f = st.export(id);
                        Txt.send(s, "<green>✔ Copia lista: <white>plugins/AventuraMaker/historias-guardadas/" + f.getName());
                        Txt.send(s, "<gray>Pásala a otro servidor y ponla en su carpeta historias/ (se instala sola).");
                    } else {
                        if (id.equals(st.id())) {
                            Txt.send(s, "<red>Esa historia se está jugando. Cambia a otra primero con /historia usar.");
                            return true;
                        }
                        java.io.File f = st.remove(id);
                        Txt.send(s, "<green>✔ Saqué '" + id + "'. Quedó guardada en <white>historias-guardadas/" + f.getName());
                        Txt.send(s, "<gray>Para volver a ponerla: <white>/historia poner " + id);
                    }
                } catch (java.io.IOException ex) {
                    Txt.send(s, "<red>No se pudo: " + escape(ex.getMessage()));
                }
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
                    Txt.send(s, "<yellow>⚠ Se guardan las misiones de '" + st.id() + "' y se sigue donde se quedó cada uno en '" + id + "' (nivel y objetos no cambian).");
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
                switchQuests(plugin, prev, id);
                Txt.send(s, "<green>✔ Ahora se juega <white>" + r.doc().title + "<green>. Las misiones de '" + prev + "' quedaron guardadas.");
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
        Txt.send(s, "<gray>Archivo: <white>plugins/AventuraMaker/historias/" + st.id() + "/historia.txt");
        Txt.send(s, "<gray>Comandos: <white>editor · revisar · recargar · errores · colocar · skins · lugares · lista · nueva · usar · quitar · poner · exportar");
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

    /** Guarda el avance de misiones de la historia anterior y recupera el de la nueva (cada historia lleva el suyo). */
    public static void switchQuests(EclipseRPG plugin, String from, String to) {
        if (from == null || from.equals(to)) return;
        plugin.data().saveAll();
        java.io.File[] files = new java.io.File(plugin.getDataFolder(), "jugadores").listFiles((dir, n) -> n.endsWith(".yml"));
        if (files != null) for (java.io.File f : files) {
            var y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
            y.set("historias." + from + ".mision", y.getInt("historia.mision", 0));
            y.set("historias." + from + ".aceptada", y.getBoolean("historia.aceptada", false));
            y.set("historias." + from + ".progreso", y.getIntegerList("historia.progreso"));
            y.set("historias." + from + ".marcas", y.getStringList("historia.marcas"));
            y.set("historia.marcas", y.getStringList("historias." + to + ".marcas"));
            y.set("historia.mision", y.getInt("historias." + to + ".mision", 0));
            y.set("historia.aceptada", y.getBoolean("historias." + to + ".aceptada", false));
            y.set("historia.progreso", y.getIntegerList("historias." + to + ".progreso"));
            try {
                y.save(f);
            } catch (java.io.IOException ignored) {
                // no importa
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) plugin.data().reload(p);
    }

    public static void resetQuests(EclipseRPG plugin) {
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
        } else if (a.length == 2 && (a[0].equalsIgnoreCase("usar") || a[0].equalsIgnoreCase("quitar") || a[0].equalsIgnoreCase("exportar"))) {
            out.addAll(plugin.story().available());
        } else if (a.length == 2 && a[0].equalsIgnoreCase("poner")) {
            out.addAll(plugin.story().savedIds());
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
