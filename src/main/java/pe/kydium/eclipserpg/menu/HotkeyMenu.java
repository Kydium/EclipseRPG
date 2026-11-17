package pe.kydium.eclipserpg.menu;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
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
 * - Shift + F (cambiar de mano agachado) abre el menú del Guardián.
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

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (!e.getPlayer().isSneaking() || !plugin.getConfig().getBoolean("atajos.shift-f", true)) return;
        e.setCancelled(true);
        open(e.getPlayer());
    }

    /** Menú principal del Guardián. */
    public void open(Player p) {
        PlayerData d = plugin.data().get(p);
        Quest q = plugin.quests().current(p);
        List<String> body = new ArrayList<>();
        body.add("<gold>Nivel " + d.level + " <gray>· <yellow>" + Txt.num(d.soles) + " ☀ Soles");
        if (d.points > 0) body.add("<green>Tienes " + d.points + " punto(s) de habilidad sin usar");
        if (q != null && d.questAccepted) body.add("<gray>Misión actual: <white>" + q.name);
        else if (q != null) body.add("<gray>Sin misión activa · te busca " + plugin.npcs().name(q.npc));
        else body.add("<gold>¡Historia completada!");
        List<DialogUI.Button> buttons = new ArrayList<>();
        buttons.add(new DialogUI.Button("<gold>⚔ Nivel y habilidades", "Reparte tus puntos", () -> new StatsMenu(plugin, p).open()));
        buttons.add(new DialogUI.Button("<yellow>📖 Diario de misiones", "Tu misión y el avance de la historia", () -> pe.kydium.eclipserpg.quests.QuestBook.open(plugin, p)));
        buttons.add(new DialogUI.Button("<aqua>✦ Ver objetivos en el chat", null, () -> {
            Quest cur = plugin.quests().current(p);
            if (cur != null) plugin.quests().showObjectives(p, cur);
        }));
        buttons.add(new DialogUI.Button("<light_purple>🧭 Brújula de la aldea", "Si perdiste la tuya", () -> {
            plugin.village().giveCompass(p);
        }));
        buttons.add(new DialogUI.Button("<gray>Cerrar", null, null));
        ui.show(p, "<gold><bold>☀ " + plugin.title(), body, buttons, true);
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
        dialog.getParentFile().mkdirs();
        tag.getParentFile().mkdirs();
        Files.writeString(mcmeta.toPath(), """
                {"pack": {"description": "Aventura Maker: menú con la tecla G", "min_format": 82, "max_format": 999}}
                """, StandardCharsets.UTF_8);
        Files.writeString(dialog.toPath(), """
                {
                  "type": "minecraft:multi_action",
                  "title": {"text": "☀ Menú del aventurero", "color": "gold", "bold": true},
                  "external_title": {"text": "Menú del aventurero", "color": "gold"},
                  "body": [{"type": "minecraft:plain_message", "contents": {"text": "Tu nivel, tus misiones y tu brújula", "color": "gray"}}],
                  "columns": 1,
                  "actions": [
                    {"label": {"text": "☀ Abrir el menú", "color": "gold"}, "action": {"type": "minecraft:run_command", "command": "guardian"}},
                    {"label": {"text": "⚔ Nivel y habilidades", "color": "yellow"}, "action": {"type": "minecraft:run_command", "command": "nivel"}},
                    {"label": {"text": "📖 Diario de misiones", "color": "yellow"}, "action": {"type": "minecraft:run_command", "command": "misiones"}},
                    {"label": {"text": "✦ Objetivos en el chat", "color": "aqua"}, "action": {"type": "minecraft:run_command", "command": "mision"}}
                  ]
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(tag.toPath(), """
                {"values": ["eclipse:menu"]}
                """, StandardCharsets.UTF_8);
        return isNew;
    }

    /** Se llama al iniciar el plugin, antes de cargar los mundos: así Minecraft lo activa solo. */
    /** ¿El paquete ya estaba al arrancar? Los diálogos solo se cargan al iniciar el servidor. */
    public static boolean presentAtStartup;

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
