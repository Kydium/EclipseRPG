package pe.kydium.eclipserpg.shop;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.menu.Menu;
import pe.kydium.eclipserpg.util.Txt;

import java.util.ArrayList;
import java.util.List;

/**
 * "Vender lo que sea": el jugador pasa objetos de su inventario a los 45 huecos de arriba (clic en ellos),
 * ve cuánto le pagan y confirma. Lo que no venda vuelve a su inventario al cerrar.
 */
public final class SellMenu extends Menu {
    private static final int AREA = 45, INFO = 49, SELL = 50, BACK = 45, ALL = 48;
    private final EclipseRPG plugin;
    private final ShopManager.Shop shop;
    private boolean done;

    public SellMenu(EclipseRPG plugin, Player player, ShopManager.Shop shop) {
        super(player, 6, "<dark_gray>Vender a " + (shop == null ? "Pip" : shop.title.replaceAll("<[^>]+>", "").replaceAll("·.*$", "").trim()));
        this.plugin = plugin;
        this.shop = shop;
    }

    @Override
    protected boolean isInputSlot(int slot) {
        return slot < AREA;
    }

    @Override
    public void render() {
        for (int i = AREA; i < 54; i++) inventory.setItem(i, filler());
        double total = total();
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Haz clic en objetos de tu inventario");
        lore.add("<gray>para ponerlos aquí arriba.");
        lore.add("<gray>Clic en uno de arriba para devolverlo.");
        lore.add("");
        lore.add("<gray>Te pagan: <yellow>" + Txt.num((long) Math.floor(total)) + " ☀");
        lore.add("<dark_gray>Las cosas comunes (tierra, piedra…) valen casi nada.");
        inventory.setItem(INFO, icon(Material.WRITABLE_BOOK, "<gold>¿Cómo funciona?", lore));
        inventory.setItem(SELL, icon(Material.EMERALD_BLOCK, "<green><bold>Vender todo · " + Txt.num((long) Math.floor(total)) + " ☀",
                List.of("<gray>Vende lo que pusiste arriba", "<yellow>Clic para confirmar")));
        inventory.setItem(ALL, icon(Material.HOPPER, "<yellow>Poner todo lo vendible",
                List.of("<gray>Pasa tu inventario (no la barra rápida", "<gray>ni la armadura) a la venta")));
        if (shop != null) inventory.setItem(BACK, icon(Material.ARROW, "<gray>◀ Volver a la tienda", List.of()));
    }

    private double total() {
        double t = 0;
        for (int i = 0; i < AREA; i++) t += plugin.shops().values().value(inventory.getItem(i));
        return t;
    }

    @Override
    public void onBottomClick(InventoryClickEvent e, int slot) {
        ItemStack it = player.getInventory().getItem(slot);
        if (it == null || it.getType().isAir()) return;
        String no = plugin.shops().values().refuse(it);
        if (no != null) {
            if (!no.isEmpty()) Txt.send(player, no);
            Txt.sound(player, "entity.villager.no", 1f, 1f);
            return;
        }
        var left = inventory.addItem(it.clone());
        player.getInventory().setItem(slot, left.isEmpty() ? null : left.values().iterator().next());
        Txt.sound(player, "item.bundle.insert", 0.8f, 1.2f);
        render();
    }

    @Override
    public void onClick(InventoryClickEvent e, int slot) {
        if (slot < AREA) {
            ItemStack it = inventory.getItem(slot);
            if (it == null || isFiller(it)) {
                inventory.setItem(slot, null);
                return;
            }
            inventory.setItem(slot, null);
            give(it);
            render();
            return;
        }
        if (slot == ALL) {
            ItemStack[] inv = player.getInventory().getStorageContents();
            for (int i = 9; i < inv.length; i++) {
                ItemStack it = inv[i];
                if (it == null || plugin.shops().values().refuse(it) != null) continue;
                if (plugin.shops().values().value(it) <= 0) continue;   // lo que no vale nada se queda
                var left = inventory.addItem(it.clone());
                player.getInventory().setItem(i, left.isEmpty() ? null : left.values().iterator().next());
            }
            Txt.sound(player, "item.bundle.insert", 1f, 1f);
            render();
            return;
        }
        if (slot == BACK && shop != null) {
            returnAll();
            done = true;
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> plugin.shops().open(player, shop.id));
            return;
        }
        if (slot != SELL) return;
        double total = 0;
        int count = 0;
        for (int i = 0; i < AREA; i++) {
            ItemStack it = inventory.getItem(i);
            if (it == null || isFiller(it)) continue;
            total += plugin.shops().values().value(it);
            count += it.getAmount();
            inventory.setItem(i, null);
        }
        long pay = (long) Math.floor(total);
        if (count == 0) {
            Txt.send(player, "<gray>No pusiste nada para vender.");
            return;
        }
        if (pay > 0) plugin.levels().addSoles(player, pay);
        Txt.send(player, "<green>Vendiste " + count + " objeto(s) por <yellow>" + Txt.num(pay) + " ☀");
        Txt.sound(player, "entity.villager.yes", 1f, 1f);
        Txt.sound(player, "block.amethyst_block.chime", 0.6f, 1.6f);
        render();
    }

    private void give(ItemStack it) {
        for (ItemStack l : player.getInventory().addItem(it).values()) player.getWorld().dropItem(player.getLocation(), l);
    }

    private void returnAll() {
        for (int i = 0; i < AREA; i++) {
            ItemStack it = inventory.getItem(i);
            inventory.setItem(i, null);
            if (it == null || isFiller(it)) continue;
            give(it);
        }
    }

    @Override
    public void onClose() {
        if (!done) returnAll();
    }
}
