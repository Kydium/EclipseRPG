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
        if (q != null) body.add("<gray>Misión actual: <white>" + q.name);
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

    /** Instala el paquete de datos en el mundo principal. Devuelve true si es nuevo. */
    public boolean installDatapack() {
        if (!plugin.getConfig().getBoolean("atajos.tecla-g", true)) return false;
        World w = Bukkit.getWorlds().get(0);
        // en Paper 26 la carpeta del mundo es world/dimensions/minecraft/overworld: los datapacks van en world/datapacks
        File level = w.getWorldFolder().getAbsoluteFile();
        for (File f = level; f != null; f = f.getParentFile()) if (new File(f, "level.dat").exists()) { level = f; break; }
        File wrong = new File(w.getWorldFolder(), "datapacks/eclipse_atajos");
        File root = new File(level, "datapacks/eclipse_atajos");
        if (!wrong.getAbsoluteFile().equals(root.getAbsoluteFile()) && wrong.exists()) deleteTree(wrong);
        File mcmeta = new File(root, "pack.mcmeta");
        File dialog = new File(root, "data/eclipse/dialog/menu.json");
        File tag = new File(root, "data/minecraft/tags/dialog/quick_actions.json");
        boolean isNew = !dialog.exists();
        try {
            dialog.getParentFile().mkdirs();
            tag.getParentFile().mkdirs();
            Files.writeString(mcmeta.toPath(), """
                    {"pack": {"description": "Aventura Maker: menú con la tecla G", "min_format": 80, "max_format": 200}}
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
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo instalar el paquete de datos de atajos: " + e.getMessage());
            return false;
        }
        plugin.getLogger().info("Paquete de la tecla G en " + root.getPath());
        if (isNew) {
            plugin.getLogger().info("Paquete de datos de atajos instalado: recargando datos para activar la tecla G…");
            Bukkit.getScheduler().runTaskLater(plugin, Bukkit::reloadData, 20L);
        }
        return isNew;
    }
}
