package pe.kydium.eclipserpg.menu;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class MenuListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) {
            menu.onBottomClick(e, e.getSlot());
            return;
        }
        menu.onClick(e, e.getRawSlot());
    }

    @EventHandler
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu menu) menu.onClose();
        if (e.getPlayer() instanceof org.bukkit.entity.Player p) clean(p);
    }

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        clean(e.getPlayer());
    }

    /** Quita cristales de relleno que se hayan colado al inventario (versiones anteriores del yunque). */
    public static void clean(org.bukkit.entity.Player p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) if (Menu.isFiller(inv.getItem(i))) inv.setItem(i, null);
        if (Menu.isFiller(p.getItemOnCursor())) p.setItemOnCursor(null);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }
}
