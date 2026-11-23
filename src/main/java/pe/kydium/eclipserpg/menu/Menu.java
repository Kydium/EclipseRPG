package pe.kydium.eclipserpg.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import pe.kydium.eclipserpg.util.Txt;

import java.util.List;

/** Menú (inventario) propio del plugin. Los clics se cancelan siempre. */
public abstract class Menu implements InventoryHolder {
    protected final Player player;
    protected Inventory inventory;

    protected Menu(Player player, int rows, String title) {
        this.player = player;
        this.inventory = Bukkit.createInventory(this, rows * 9, Txt.mm(title));
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    /** Dibuja el contenido del menú. */
    public abstract void render();

    /** Clic dentro del inventario superior (ya cancelado). */
    public abstract void onClick(InventoryClickEvent e, int slot);

    /** Clic en el inventario del jugador (abajo) mientras el menú está abierto (ya cancelado). */
    public void onBottomClick(InventoryClickEvent e, int slot) {
    }

    /** Al cerrar el menú. */
    public void onClose() {
    }

    public void open() {
        render();
        player.openInventory(inventory);
    }

    public static ItemStack icon(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Txt.item(name));
            meta.lore(Txt.lore(lore));
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            it.setItemMeta(meta);
        }
        return it;
    }

    public static ItemStack filler() {
        ItemStack it = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            meta.getPersistentDataContainer().set(pe.kydium.eclipserpg.util.Keys.k("relleno"), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            it.setItemMeta(meta);
        }
        return it;
    }

    /** ¿Es un cristal de relleno de un menú? (nunca debe llegar al inventario de nadie) */
    public static boolean isFiller(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;
        ItemMeta m = it.getItemMeta();
        if (m.getPersistentDataContainer().has(pe.kydium.eclipserpg.util.Keys.k("relleno"))) return true;
        if (it.getType() != Material.BLACK_STAINED_GLASS_PANE) return false;
        return Component.text(" ").equals(m.displayName());
    }

    /** Huecos que el jugador usa para poner sus cosas: no se rellenan con cristal. */
    protected boolean isInputSlot(int slot) {
        return false;
    }

    protected void fillEmpty() {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null && !isInputSlot(i)) inventory.setItem(i, filler());
        }
    }
}
