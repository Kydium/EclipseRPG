package pe.kydium.eclipserpg.shop;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.data.PlayerData;
import pe.kydium.eclipserpg.menu.Menu;
import pe.kydium.eclipserpg.util.Txt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Inventario de una tienda. Clic izquierdo = 1 vez, Shift + clic = 4 veces. */
public final class ShopMenu extends Menu {
    private final EclipseRPG plugin;
    private final ShopManager.Shop shop;
    private final Map<Integer, ShopManager.Entry> bySlot = new HashMap<>();
    private final int infoSlot;

    public ShopMenu(EclipseRPG plugin, Player player, ShopManager.Shop shop, List<ShopManager.Entry> entries) {
        super(player, shop.rows, shop.title);
        this.plugin = plugin;
        this.shop = shop;
        for (ShopManager.Entry e : entries) bySlot.put(e.slot, e);
        this.infoSlot = shop.rows * 9 - 1;
    }

    @Override
    public void render() {
        inventory.clear();
        PlayerData d = plugin.data().get(player);
        ShopManager sm = plugin.shops();
        for (ShopManager.Entry e : bySlot.values()) {
            if (e.slot < 0 || e.slot >= inventory.getSize()) continue;
            ItemStack display = e.kind == ShopManager.Kind.MEJORA
                    ? new ItemStack(Material.ANVIL)
                    : sm.build(e);
            if (display == null) continue;
            display = display.clone();
            ItemMeta meta = display.getItemMeta();
            if (meta == null) continue;
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Component.empty());
            switch (e.kind) {
                case COMPRAR -> {
                    if (e.name != null) meta.displayName(Txt.item(e.name));
                    lore.add(Txt.item("<gray>Cantidad: <white>" + e.amount));
                    lore.add(Txt.item("<gray>Precio: <yellow>" + sm.priceText(e.price)));
                    if (e.minLevel > 1) {
                        String c = d.level >= e.minLevel ? "<green>" : "<red>";
                        lore.add(Txt.item("<gray>Nivel requerido: " + c + e.minLevel));
                    }
                    lore.add(Txt.item("<yellow>Clic: comprar · Shift+clic: x4"));
                }
                case VENDER -> {
                    meta.displayName(Txt.item("<green>Vender " + (e.name != null ? e.name : Txt.pretty(display.getType().name()))));
                    lore.add(Txt.item("<gray>Entregas: <white>" + e.amount));
                    lore.add(Txt.item("<gray>Recibes: <yellow>" + sm.priceText(e.price)));
                    lore.add(Txt.item("<yellow>Clic: vender · Shift+clic: todo lo posible"));
                }
                case MEJORA -> {
                    int max = plugin.getConfig().getInt("mejoras.maximo", 5);
                    meta.displayName(Txt.item("<gold>Mejorar arma única"));
                    lore.add(Txt.item("<gray>Ten el arma única en la mano."));
                    lore.add(Txt.item("<gray>+1 de daño por mejora (máx. +" + max + ")"));
                    lore.add(Txt.item("<gray>Cuesta: <yellow>" + sm.priceText(e.price) + "</yellow> + 1 Fragmento de Mejora"));
                    lore.add(Txt.item("<yellow>Clic para mejorar"));
                }
            }
            meta.lore(lore);
            display.setItemMeta(meta);
            inventory.setItem(e.slot, display);
        }
        inventory.setItem(infoSlot, icon(Material.SUNFLOWER, "<yellow>Tus Soles: " + Txt.num(d.soles) + " ☀",
                List.of("<gray>Nivel " + d.level)));
        fillEmpty();
    }

    @Override
    public void onClick(InventoryClickEvent e, int slot) {
        ShopManager.Entry entry = bySlot.get(slot);
        if (entry == null) return;
        boolean shift = e.isShiftClick();
        switch (entry.kind) {
            case COMPRAR -> buy(entry, shift ? 4 : 1);
            case VENDER -> sell(entry, shift);
            case MEJORA -> upgrade(entry);
        }
        render();
    }

    private void buy(ShopManager.Entry entry, int times) {
        PlayerData d = plugin.data().get(player);
        if (d.level < entry.minLevel) {
            Txt.send(player, "<red>Necesitas nivel " + entry.minLevel + " para comprar esto.");
            Txt.sound(player, "entity.villager.no", 1f, 1f);
            return;
        }
        int bought = 0;
        for (int i = 0; i < times; i++) {
            if (!plugin.levels().takeSoles(player, entry.price)) break;
            ItemStack it = plugin.shops().build(entry);
            if (it == null) break;
            plugin.items().give(player, it);
            bought++;
        }
        if (bought == 0) {
            Txt.send(player, "<red>No te alcanzan los Soles.");
            Txt.sound(player, "entity.villager.no", 1f, 1f);
        } else {
            Txt.sound(player, "entity.experience_orb.pickup", 1f, 1.2f);
        }
    }

    private void sell(ShopManager.Entry entry, boolean all) {
        Material m = plugin.shops().sellMaterial(entry);
        if (m == null) return;
        int sold = 0;
        int limit = all ? 64 : 1;
        while (sold < limit && player.getInventory().containsAtLeast(new ItemStack(m), entry.amount)) {
            player.getInventory().removeItem(new ItemStack(m, entry.amount));
            plugin.levels().addSoles(player, entry.price);
            sold++;
        }
        if (sold == 0) {
            Txt.send(player, "<red>Necesitas " + entry.amount + " de ese objeto.");
            Txt.sound(player, "entity.villager.no", 1f, 1f);
        } else {
            Txt.send(player, "<green>Vendiste " + (sold * entry.amount) + " y ganaste <yellow>" + Txt.num(sold * entry.price) + " ☀");
            Txt.sound(player, "entity.villager.yes", 1f, 1f);
        }
    }

    private void upgrade(ShopManager.Entry entry) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        String id = plugin.items().idOf(hand);
        var def = id == null ? null : plugin.items().def(id);
        int max = plugin.getConfig().getInt("mejoras.maximo", 5);
        if (def == null || !def.upgradable) {
            Txt.send(player, "<red>Ten un arma única en la mano principal.");
            return;
        }
        if (plugin.items().upgradeLevel(hand) >= max) {
            Txt.send(player, "<yellow>Esta arma ya está al máximo (+" + max + ").");
            return;
        }
        if (plugin.items().count(player, "fragmento_mejora") < 1) {
            Txt.send(player, "<red>Necesitas un Fragmento de Mejora.");
            return;
        }
        if (plugin.levels().soles(player) < entry.price) {
            Txt.send(player, "<red>Necesitas " + plugin.shops().priceText(entry.price) + ".");
            return;
        }
        plugin.levels().takeSoles(player, entry.price);
        plugin.items().take(player, "fragmento_mejora", 1);
        plugin.items().upgrade(hand, max);
        player.getInventory().setItemInMainHand(hand);
        Txt.send(player, "<gold>¡Bruno mejoró tu arma a +" + plugin.items().upgradeLevel(hand) + "!");
        Txt.sound(player, "block.anvil.use", 1f, 1f);
    }
}
