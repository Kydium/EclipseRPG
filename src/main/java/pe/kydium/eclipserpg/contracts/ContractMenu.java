package pe.kydium.eclipserpg.contracts;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.data.PlayerData;
import pe.kydium.eclipserpg.menu.Menu;
import pe.kydium.eclipserpg.util.Txt;

import java.util.ArrayList;
import java.util.List;

public final class ContractMenu extends Menu {
    private static final int[] SLOTS = {11, 13, 15};
    private final EclipseRPG plugin;

    public ContractMenu(EclipseRPG plugin, Player player) {
        super(player, 3, "<dark_gray>Contratos de la Capitana Inés");
        this.plugin = plugin;
    }

    @Override
    public void render() {
        inventory.clear();
        PlayerData d = plugin.data().get(player);
        ContractManager cm = plugin.contracts();
        for (int i = 0; i < d.contractIds.size() && i < SLOTS.length; i++) {
            ContractManager.Contract c = cm.get(d.contractIds.get(i));
            if (c == null) continue;
            int need = c.amount(d.contractTier);
            int have = Math.min(need, d.contractProgress[i]);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Progreso: <white>" + have + "</white> / " + need);
            lore.add("<gray>Recompensa: <aqua>" + Txt.num(c.xpPerTier * d.contractTier) + " XP</aqua> · <yellow>"
                    + Txt.num(c.solesPerTier * d.contractTier) + " ☀");
            lore.add("");
            Material icon = c.icon;
            if (d.contractClaimed[i]) {
                lore.add("<dark_gray>Ya reclamado");
                icon = Material.MAP;
            } else if (have >= need) {
                lore.add("<green>¡Clic para reclamar!");
            } else {
                lore.add("<gray>Se renueva mañana.");
            }
            inventory.setItem(SLOTS[i], icon(icon, (d.contractClaimed[i] ? "<dark_gray>" : "<yellow>") + c.name, lore));
        }
        inventory.setItem(22, icon(Material.NETHER_STAR, "<gold>Bono diario",
                List.of("<gray>Completa los 3 contratos y", "<gray>recibe un Fragmento de Mejora.")));
        fillEmpty();
    }

    @Override
    public void onClick(InventoryClickEvent e, int slot) {
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] == slot) {
                plugin.contracts().claim(player, i);
                render();
                return;
            }
        }
    }
}
