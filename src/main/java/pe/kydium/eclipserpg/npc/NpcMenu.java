package pe.kydium.eclipserpg.npc;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.data.PlayerData;
import pe.kydium.eclipserpg.menu.Menu;
import pe.kydium.eclipserpg.quests.Quest;
import pe.kydium.eclipserpg.quests.QuestManager;

import java.util.ArrayList;
import java.util.List;

/** Menú al hacer clic en un NPC: misión + servicios (tienda, contratos, Grieta). */
public final class NpcMenu extends Menu {
    private static final int QUEST_SLOT = 11;
    private static final int[] SERVICE_SLOTS = {14, 15, 16};
    private final EclipseRPG plugin;
    private final NpcManager.NpcDef npc;

    public NpcMenu(EclipseRPG plugin, Player player, NpcManager.NpcDef npc) {
        super(player, 3, npc.name);
        this.plugin = plugin;
        this.npc = npc;
    }

    @Override
    public void render() {
        inventory.clear();
        QuestManager qm = plugin.quests();
        QuestManager.State st = qm.stateFor(player, npc.id);
        Quest q = qm.current(player);
        PlayerData d = plugin.data().get(player);
        List<String> lore = new ArrayList<>();
        String title;
        switch (st) {
            case AVAILABLE -> {
                title = "<yellow><bold>¡Nueva misión!";
                lore.add("<white>" + q.name);
                lore.add("<gray>" + qm.actName(q.act));
                lore.add("");
                lore.add("<yellow>Clic para hablar");
            }
            case LOCKED -> {
                title = "<red>Misión bloqueada";
                lore.add("<white>" + q.name);
                lore.add("<gray>Necesitas nivel <red>" + q.minLevel + " <gray>(tienes " + d.level + ")");
                lore.add("<gray>Entrena con contratos y Grietas.");
            }
            case IN_PROGRESS -> {
                title = "<gold>Misión en curso";
                lore.add("<white>" + q.name);
                for (int i = 0; i < q.objectives.size(); i++) {
                    Quest.Objective o = q.objectives.get(i);
                    int have = qm.progressOf(player, q, i);
                    lore.add((have >= o.amount ? "<green>✔ " : "<gray>• ") + "<white>" + o.text + " <gray>" + have + "/" + o.amount);
                }
                lore.add("");
                lore.add("<yellow>Clic para hablar");
            }
            case READY -> {
                title = "<green><bold>¡Entregar misión!";
                lore.add("<white>" + q.name);
                lore.add("");
                lore.add("<green>Clic para entregar");
            }
            case TALK_TARGET -> {
                title = "<aqua>Te están buscando";
                lore.add("<gray>Tu misión pide hablar con " + npc.name);
                lore.add("<yellow>Clic para hablar");
            }
            case FINISHED -> {
                title = "<gold>Historia completada";
                lore.add("<gray>Gracias, Guardián del Alba.");
            }
            default -> {
                title = "<gray>Sin misiones por ahora";
                if (q != null) lore.add("<gray>Te busca " + plugin.npcs().name(q.npc));
                lore.add("<yellow>Clic para hablar");
            }
        }
        inventory.setItem(QUEST_SLOT, icon(qm.icon(st), title, lore));

        for (int i = 0; i < npc.services.size() && i < SERVICE_SLOTS.length; i++) {
            String s = npc.services.get(i);
            if (s.startsWith("tienda:")) {
                inventory.setItem(SERVICE_SLOTS[i], icon(Material.EMERALD, "<green>Tienda", List.of("<gray>Comprar y vender")));
            } else if (s.equals("contratos")) {
                inventory.setItem(SERVICE_SLOTS[i], icon(Material.PAPER, "<yellow>Contratos diarios", List.of("<gray>3 encargos por día")));
            } else if (s.equals("grieta")) {
                long cost = plugin.rifts().cost(player);
                inventory.setItem(SERVICE_SLOTS[i], icon(Material.END_PORTAL_FRAME, "<dark_purple>Abrir una Grieta",
                        List.of("<gray>5 oleadas de Corrompidos de tu nivel", "<gray>Cuesta <yellow>" + cost + " ☀",
                                "<gray>Tus amigos cercanos entran contigo")));
            }
        }
        fillEmpty();
    }

    @Override
    public void onClick(InventoryClickEvent e, int slot) {
        if (slot == QUEST_SLOT) {
            QuestManager.State st = plugin.quests().stateFor(player, npc.id);
            if (st == QuestManager.State.LOCKED || st == QuestManager.State.FINISHED) return;
            player.closeInventory();
            plugin.quests().talk(player, npc.id);
            return;
        }
        for (int i = 0; i < SERVICE_SLOTS.length && i < npc.services.size(); i++) {
            if (SERVICE_SLOTS[i] != slot) continue;
            String s = npc.services.get(i);
            if (s.startsWith("tienda:")) {
                plugin.shops().open(player, s.substring(7));
            } else if (s.equals("contratos")) {
                plugin.contracts().open(player);
            } else if (s.equals("grieta")) {
                player.closeInventory();
                plugin.rifts().start(player);
            }
            return;
        }
    }
}
