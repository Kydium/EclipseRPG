package pe.kydium.eclipserpg.quests;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.data.PlayerData;
import pe.kydium.eclipserpg.menu.Menu;

import java.util.ArrayList;
import java.util.List;

/** /misiones: diario con la misión actual y el avance de la historia. */
public final class QuestMenu extends Menu {
    private final EclipseRPG plugin;

    public QuestMenu(EclipseRPG plugin, Player player) {
        super(player, 3, "<dark_gray>Diario · Crónicas del Eclipse");
        this.plugin = plugin;
    }

    @Override
    public void render() {
        inventory.clear();
        QuestManager qm = plugin.quests();
        PlayerData d = plugin.data().get(player);
        Quest q = qm.current(player);
        int done = Math.min(d.questIndex, qm.total());

        List<String> prog = new ArrayList<>();
        prog.add("<gray>Completadas: <white>" + done + "</white> / " + qm.total());
        int filled = qm.total() == 0 ? 0 : Math.round(20f * done / qm.total());
        prog.add("<gold>" + "▌".repeat(filled) + "<dark_gray>" + "▌".repeat(20 - filled));
        prog.add("");
        prog.add("<gray>Fragmentos Solares: <yellow>" + Math.min(10, solarCount(done)) + "/10");
        inventory.setItem(11, icon(Material.CLOCK, "<gold>Progreso de la historia", prog));

        if (q == null) {
            inventory.setItem(13, icon(Material.NETHER_STAR, "<gold><bold>¡Historia completada!",
                    List.of("<gray>El sol volvió a brillar.")));
        } else {
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + qm.actName(q.act));
            lore.add("<gray>Habla con: " + plugin.npcs().name(q.npc));
            if (q.minLevel > 1) lore.add("<gray>Nivel requerido: " + (d.level >= q.minLevel ? "<green>" : "<red>") + q.minLevel);
            lore.add("");
            if (!d.questAccepted) {
                lore.add("<yellow>Aún no aceptada: ve con " + plugin.npcs().name(q.npc));
            } else if (q.objectives.isEmpty()) {
                lore.add("<yellow>Habla con " + plugin.npcs().name(q.npc));
            } else {
                for (int i = 0; i < q.objectives.size(); i++) {
                    Quest.Objective o = q.objectives.get(i);
                    int have = qm.progressOf(player, q, i);
                    lore.add((have >= o.amount ? "<green>✔ " : "<gray>• ") + "<white>" + o.text + " <gray>" + have + "/" + o.amount);
                }
                if (q.noDeath) lore.add("<red>⚠ Si mueres, se reinicia");
                if (qm.isComplete(player, q)) {
                    lore.add("");
                    lore.add("<green>¡Lista! Vuelve con " + plugin.npcs().name(q.npc));
                }
            }
            Material m = q.boss ? Material.WITHER_SKELETON_SKULL : Material.WRITABLE_BOOK;
            inventory.setItem(13, icon(m, "<yellow><bold>" + q.name + " <gray>(" + q.number + ")", lore));
        }
        inventory.setItem(15, icon(Material.EXPERIENCE_BOTTLE, "<aqua>Nivel " + d.level,
                List.of("<gray>Abre /nivel para repartir puntos")));
        fillEmpty();
    }

    private int solarCount(int done) {
        // un Fragmento Solar por cada acto terminado
        int n = 0;
        for (int i = 1; i <= done; i++) {
            Quest q = plugin.quests().byNumber(i);
            Quest next = plugin.quests().byNumber(i + 1);
            if (q != null && (next == null || next.act != q.act)) n++;
        }
        return n;
    }

    @Override
    public void onClick(InventoryClickEvent e, int slot) {
        if (slot == 15) new pe.kydium.eclipserpg.rpg.StatsMenu(plugin, player).open();
    }
}
