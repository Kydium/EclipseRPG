package pe.kydium.eclipserpg.menu;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.data.PlayerData;
import pe.kydium.eclipserpg.quests.DialogUI;
import pe.kydium.eclipserpg.quests.Quest;
import pe.kydium.eclipserpg.quests.QuestMenu;
import pe.kydium.eclipserpg.rpg.StatsMenu;
import pe.kydium.eclipserpg.util.Txt;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Atajos para abrir los menús:
 * - La tecla de "Acciones rápidas" de Minecraft (G por defecto) abre el mismo menú
 *   gracias a un pequeño paquete de datos que el plugin instala en el mundo.
 */
public final class HotkeyMenu implements Listener {
    private final EclipseRPG plugin;
    private final DialogUI ui;

    public HotkeyMenu(EclipseRPG plugin) {
        this.plugin = plugin;
        this.ui = new DialogUI(plugin);
    }

    /** Menú principal del Guardián. */
    public void open(Player p) {
        PlayerData d = plugin.data().get(p);
        Quest q = plugin.quests().current(p);
        List<String> body = new ArrayList<>();
        body.add("<gold>Nivel " + d.level + " <gray>· <yellow>" + Txt.num(d.soles) + " ☀ Soles");
        if (d.points > 0) body.add("<green>Tienes " + d.points + " punto(s) de habilidad sin usar");
        if (q != null && d.questAccepted) body.add("<gray>Misión actual: <white>" + q.title());
        else if (q != null) body.add("<gray>Sin misión activa · te busca " + plugin.npcs().name(q.npc) + plugin.quests().whereIs(q.npc));
        else body.add("<gold>¡Historia completada!");
        List<DialogUI.Button> buttons = new ArrayList<>();
        buttons.add(new DialogUI.Button("<gold>⚔ Nivel y atributos", "Reparte tus puntos", () -> new StatsMenu(plugin, p).open()));
        buttons.add(new DialogUI.Button("<yellow>📖 Diario de misiones", "Tu misión y el avance de la historia", () -> pe.kydium.eclipserpg.quests.QuestBook.open(plugin, p)));
        buttons.add(new DialogUI.Button("<aqua>✦ Ver objetivos en el chat", null, () -> {
            Quest cur = plugin.quests().current(p);
            if (cur != null) plugin.quests().showObjectives(p, cur);
        }));
        buttons.add(new DialogUI.Button("<light_purple>🧭 Brújula", "La de tu misión (mazmorra) o la de la aldea", () -> plugin.quests().missionCompass(p)));
        buttons.add(new DialogUI.Button("<green>🛒 ¿Dónde compro?", "Comerciantes cercanos y hacia dónde ir", () -> traders(p)));
        buttons.add(new DialogUI.Button("<white>📜 Panel de misión", "Mostrar, ocultar o mover el cuadro de la izquierda", () -> panelMenu(p)));
        buttons.add(new DialogUI.Button("<gray>Cerrar", null, null));
        ui.show(p, "<gold><bold>☀ " + plugin.title(), body, buttons, true);
    }

    @EventHandler
    public void onOpJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        if (!changedAtStartup || !e.getPlayer().isOp()) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) Txt.send(p, "<yellow>⚠ El menú de la tecla G se actualizó: <white>reinicia el servidor una vez más</white> para verlo bien.");
        }, 60L);
    }

    /** Comerciantes del mundo, del más cercano al más lejano, con distancia y dirección. */
    public void traders(Player p) {
        List<String> body = new ArrayList<>();
        List<DialogUI.Button> b = new ArrayList<>();
        var mm = plugin.minimap();
        List<Minimap.Trader> list = mm == null ? List.of() : new ArrayList<>(mm.traders(p));
        org.bukkit.Location me = p.getLocation();
        list.sort(java.util.Comparator.comparingDouble(t -> t.at().distanceSquared(me)));
        if (list.isEmpty()) body.add("<gray>No hay comerciantes en este mundo.");
        else {
            body.add("<gray>En el <white>Mapa del aventurero</white> salen como banderas: <yellow>tiendas</yellow>, <aqua>contratos</aqua>, <light_purple>Grieta</light_purple>.");
            body.add("<gray>Toca uno para que la brújula te lleve.");
        }
        int n = 0;
        for (Minimap.Trader t : list) {
            if (n++ >= 12) break;
            int dx = t.at().getBlockX() - me.getBlockX(), dz = t.at().getBlockZ() - me.getBlockZ();
            int dist = (int) Math.sqrt((double) dx * dx + (double) dz * dz);
            String where = dist < 6 ? "aquí mismo" : dist + " bloques al " + direction(dx, dz);
            String color = t.type() == org.bukkit.map.MapCursor.Type.BANNER_YELLOW ? "<yellow>" : t.type() == org.bukkit.map.MapCursor.Type.BANNER_LIGHT_BLUE ? "<aqua>" : "<light_purple>";
            b.add(new DialogUI.Button(color + "⚑ <white>" + t.name() + " <gray>· " + t.what(), where, () -> point(p, t)));
        }
        b.add(new DialogUI.Button("<gray>Cerrar", null, null));
        ui.show(p, "<green>🛒 ¿Dónde compro?", body, b, true);
    }

    private static String direction(int dx, int dz) {
        String[] n = {"este", "sureste", "sur", "suroeste", "oeste", "noroeste", "norte", "noreste"};
        double a = Math.toDegrees(Math.atan2(dz, dx));
        return n[(int) Math.floorMod(Math.round(a / 45.0), 8)];
    }

    /** Brújula que apunta al comerciante (en la otra mano si está libre). */
    private void point(Player p, Minimap.Trader t) {
        org.bukkit.inventory.ItemStack c = new org.bukkit.inventory.ItemStack(org.bukkit.Material.COMPASS);
        if (c.getItemMeta() instanceof org.bukkit.inventory.meta.CompassMeta m) {
            m.setLodestone(t.at());
            m.setLodestoneTracked(false);
            m.displayName(Txt.item("<green>🧭 Hacia " + t.name()));
            m.lore(Txt.lore(List.of("<gray>" + t.what())));
            m.getPersistentDataContainer().set(pe.kydium.eclipserpg.util.Keys.k("brujula_comercio"), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            c.setItemMeta(m);
        }
        // reemplaza la brújula de comerciante anterior, si había
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            var it = p.getInventory().getItem(i);
            if (it != null && it.getType() == org.bukkit.Material.COMPASS && it.getItemMeta() != null
                    && it.getItemMeta().getPersistentDataContainer().has(pe.kydium.eclipserpg.util.Keys.k("brujula_comercio"))) {
                p.getInventory().setItem(i, c);
                Txt.send(p, "<green>🧭 Tu brújula ahora apunta a <white>" + t.name() + "<green>.");
                return;
            }
        }
        plugin.items().give(p, c);
        Txt.send(p, "<green>🧭 Te di una brújula que apunta a <white>" + t.name() + "<green>.");
    }

    /** Ajustes del panel de misión (cuadro de la izquierda). */
    public void panelMenu(Player p) {
        var pn = plugin.hud().panel();
        boolean on = pn.on(p);
        List<String> body = new ArrayList<>();
        body.add("<gray>El cuadro de la izquierda muestra tu misión y su avance.");
        body.add("<gray>Elige el <white>Tamaño de interfaz</white> que usas en Minecraft");
        body.add("<gray>(Opciones > Gráficos) y el cuadro se pone a media altura, pegado a la izquierda.");
        body.add("<dark_gray>Tamaño " + pn.scale(p) + " · posición " + pn.offset(p) + ", " + pn.top(p));
        List<DialogUI.Button> b = new ArrayList<>();
        for (int s = 1; s <= 4; s++) {
            int sc = s;
            b.add(new DialogUI.Button((pn.scale(p) == s ? "<green>✔ " : "<white>") + "Interfaz " + s, null, () -> {
                pn.setScale(p, sc);
                panelMenu(p);
            }));
        }
        b.add(new DialogUI.Button("<yellow>◀ Izquierda", null, () -> { pn.setOffset(p, pn.offset(p) - 10); panelMenu(p); }));
        b.add(new DialogUI.Button("<yellow>Derecha ▶", null, () -> { pn.setOffset(p, pn.offset(p) + 10); panelMenu(p); }));
        b.add(new DialogUI.Button("<yellow>▲ Subir", null, () -> { pn.setTop(p, pn.top(p) - 12); panelMenu(p); }));
        b.add(new DialogUI.Button("<yellow>▼ Bajar", null, () -> { pn.setTop(p, pn.top(p) + 12); panelMenu(p); }));
        b.add(new DialogUI.Button(on ? "<gray>✖ Ocultar el panel" : "<green>✔ Mostrar el panel", null, () -> pn.setOn(p, !on)));
        b.add(new DialogUI.Button("<gray>Cerrar", null, null));
        ui.show(p, "<gold>📜 Panel de misión", body, b, true);
    }

    /** Tu nivel, soles y misión en el chat (botón "Mi estado" de la tecla G). */
    public void status(Player p) {
        PlayerData d = plugin.data().get(p);
        Quest q = plugin.quests().current(p);
        Txt.send(p, "<gold>☀ Nivel " + d.level + " <gray>· <yellow>" + Txt.num(d.soles) + " Soles"
                + (d.points > 0 ? " <gray>· <green>" + d.points + " punto(s) sin usar" : ""));
        if (q != null && d.questAccepted) plugin.quests().showObjectives(p, q);
        else if (q != null) Txt.send(p, "<gray>Sin misión activa · te busca " + plugin.npcs().name(q.npc) + plugin.quests().whereIs(q.npc));
        else Txt.send(p, "<gold>¡Historia completada!");
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }

    /** ¿Minecraft cargó el paquete de la tecla G? */
    public boolean datapackActive() {
        try {
            for (var pack : Bukkit.getDatapackManager().getEnabledPacks())
                if (pack.getName().contains("eclipse_atajos")) return true;
        } catch (RuntimeException | LinkageError ignored) {
            // API distinta
        }
        return false;
    }

    // ---------- Paquete de datos para la tecla G ----------

    /** Carpeta del mundo principal (world/), leída de server.properties: funciona antes de que cargue el mundo. */
    public static File levelDir() {
        String name = "world";
        File props = new File(Bukkit.getWorldContainer(), "server.properties");
        if (!props.exists()) props = new File("server.properties");
        try (var in = new java.io.FileInputStream(props)) {
            java.util.Properties p = new java.util.Properties();
            p.load(in);
            name = p.getProperty("level-name", "world");
        } catch (IOException ignored) {
            // por defecto "world"
        }
        return new File(Bukkit.getWorldContainer(), name);
    }

    /** Escribe el paquete en world/datapacks/eclipse_atajos. Devuelve true si no existía. */
    public static boolean writePack(File level) throws IOException {
        File root = new File(level, "datapacks/eclipse_atajos");
        File mcmeta = new File(root, "pack.mcmeta");
        File dialog = new File(root, "data/eclipse/dialog/menu.json");
        File tag = new File(root, "data/minecraft/tags/dialog/quick_actions.json");
        boolean isNew = !dialog.exists();
        String before = isNew ? "" : Files.readString(dialog.toPath(), StandardCharsets.UTF_8);
        dialog.getParentFile().mkdirs();
        tag.getParentFile().mkdirs();
        Files.writeString(mcmeta.toPath(), """
                {"pack": {"description": "Aventura Maker: menú con la tecla G", "min_format": 82, "max_format": 999}}
                """, StandardCharsets.UTF_8);
        Files.writeString(dialog.toPath(), """
                {
                  "type": "minecraft:multi_action",
                  "title": [{"text": "\ue100", "font": "eclipse:iconos", "color": "white"}, {"text": " Menú del aventurero", "color": "gold", "bold": true, "font": "minecraft:default"}],
                  "external_title": {"text": "Menú del aventurero", "color": "gold"},
                  "columns": 1,
                  "actions": [
                    {"label": [{"text": "\\ue100", "font": "eclipse:iconos", "color": "white"}, {"text": " Mi estado (nivel, soles y misión)", "color": "gold", "font": "minecraft:default"}], "action": {"type": "minecraft:run_command", "command": "guardian estado"}},
                    {"label": [{"text": "\\ue111", "font": "eclipse:iconos", "color": "white"}, {"text": " Nivel y atributos", "color": "yellow", "font": "minecraft:default"}], "action": {"type": "minecraft:run_command", "command": "nivel"}},
                    {"label": [{"text": "\\ue102", "font": "eclipse:iconos", "color": "white"}, {"text": " Diario de misiones", "color": "yellow", "font": "minecraft:default"}], "action": {"type": "minecraft:run_command", "command": "misiones"}},
                    {"label": [{"text": "\\ue103", "font": "eclipse:iconos", "color": "white"}, {"text": " Brújula (misión o aldea)", "color": "light_purple", "font": "minecraft:default"}], "action": {"type": "minecraft:run_command", "command": "guardian brujula"}},
                    {"label": [{"text": "\\ue10d", "font": "eclipse:iconos", "color": "white"}, {"text": " ¿Dónde compro? (comerciantes)", "color": "green", "font": "minecraft:default"}], "action": {"type": "minecraft:run_command", "command": "guardian comerciantes"}},
                    {"label": [{"text": "\\ue10e", "font": "eclipse:iconos", "color": "white"}, {"text": " Panel de misión", "color": "white", "font": "minecraft:default"}], "action": {"type": "minecraft:run_command", "command": "guardian panel"}}
                  ]
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(tag.toPath(), """
                {"values": ["eclipse:menu"]}
                """, StandardCharsets.UTF_8);
        if (!isNew && !before.equals(Files.readString(dialog.toPath(), StandardCharsets.UTF_8))) changedAtStartup = true;
        return isNew;
    }

    /** Se llama al iniciar el plugin, antes de cargar los mundos: así Minecraft lo activa solo. */
    /** ¿El paquete ya estaba al arrancar? Los diálogos solo se cargan al iniciar el servidor. */
    public static boolean presentAtStartup;
    /** El menú de la tecla G cambió con esta versión: Minecraft sigue usando el viejo hasta el próximo reinicio. */
    public static boolean changedAtStartup;

    public static void installEarly(org.bukkit.plugin.java.JavaPlugin plugin) {
        if (!plugin.getConfig().getBoolean("atajos.tecla-g", true)) return;
        try {
            presentAtStartup = !writePack(levelDir());
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("No se pudo instalar el paquete de la tecla G: " + e.getMessage());
        }
    }

    /** Ya con el mundo cargado: limpia el sitio viejo y, si el paquete no quedó activo, recarga los datos una vez. */
    public boolean installDatapack() {
        if (!plugin.getConfig().getBoolean("atajos.tecla-g", true)) return false;
        World w = Bukkit.getWorlds().get(0);
        File level = levelDir();
        File wrong = new File(w.getWorldFolder(), "datapacks/eclipse_atajos");
        if (!wrong.getAbsoluteFile().toPath().normalize().equals(new File(level, "datapacks/eclipse_atajos").getAbsoluteFile().toPath().normalize()) && wrong.exists())
            deleteTree(wrong);
        try {
            writePack(level);
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo instalar el paquete de la tecla G: " + e.getMessage());
            return false;
        }
        if (!presentAtStartup)
            plugin.getLogger().warning("Se instaló el menú de la tecla G: reinicia el servidor una vez (stop y abrir) para activarlo.");
        else if (changedAtStartup)
            plugin.getLogger().warning("El menú de la tecla G cambió en esta versión: reinicia el servidor UNA vez más para verlo (Minecraft lo lee antes que el plugin).");
        if (datapackActive()) return false;
        plugin.getLogger().info("Activando el paquete de la tecla G (recarga de datos, solo esta vez)…");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Bukkit.reloadData();
            if (!datapackActive()) {
                try {
                    for (var pk : Bukkit.getDatapackManager().getPacks())
                        if (pk.getName().contains("eclipse_atajos") && !pk.isEnabled()) pk.setEnabled(true);
                } catch (RuntimeException | LinkageError ignored) {
                    // API distinta
                }
            }
        }, 20L);
        return true;
    }
}
