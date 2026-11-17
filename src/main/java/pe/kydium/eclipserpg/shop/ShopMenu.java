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
    private final List<ShopManager.Entry> buys = new ArrayList<>(), sells = new ArrayList<>();
    private final int infoSlot;
    /** Si la tienda compra y vende, se separa en dos secciones con pestañas abajo. */
    private final boolean tabs;
    private boolean selling;
    private int tabBuy = -1, tabSell = -1;

    public ShopMenu(EclipseRPG plugin, Player player, ShopManager.Shop shop, List<ShopManager.Entry> entries) {
        super(player, shop.rows, shop.title);
        this.plugin = plugin;
        this.shop = shop;
        for (ShopManager.Entry e : entries) (e.kind == ShopManager.Kind.VENDER ? sells : buys).add(e);
        this.infoSlot = shop.rows * 9 - 1;
        this.tabs = !buys.isEmpty() && !sells.isEmpty() && shop.rows >= 2;
        if (tabs) {
            // dos huecos libres en la última fila para las pestañas
            java.util.Set<Integer> used = new java.util.HashSet<>();
            for (ShopManager.Entry e : buys) used.add(e.slot);
            int last = (shop.rows - 1) * 9;
            for (int sl : new int[]{last + 3, last + 5, last + 2, last + 6, last + 1, last + 7, last, last + 4})
                if (!used.contains(sl) && sl != infoSlot) {
                    if (tabBuy < 0) tabBuy = sl;
                    else if (tabSell < 0) {
                        tabSell = sl;
                        break;
                    }
                }
        }
        layout();
    }

    /** Pone en su sitio lo de la sección abierta: lo que vende el NPC en sus huecos, y lo que compra ordenado. */
    private void layout() {
        bySlot.clear();
        if (!tabs) {
            for (ShopManager.Entry e : buys) bySlot.put(e.slot, e);
            for (ShopManager.Entry e : sells) bySlot.put(e.slot, e);
            return;
        }
        if (!selling) {
            for (ShopManager.Entry e : buys) bySlot.put(e.slot, e);
            return;
        }
        List<Integer> free = new ArrayList<>();
        int rows = shop.rows;
        // primero el centro (sin bordes), luego lo que quede
        for (int r = 1; r < rows - 1; r++) for (int c = 1; c < 8; c++) free.add(r * 9 + c);
        for (int sl = 0; sl < (rows - 1) * 9; sl++) if (!free.contains(sl)) free.add(sl);
        int i = 0;
        for (ShopManager.Entry e : sells) if (i < free.size()) bySlot.put(free.get(i++), e);
    }

    @Override
    public void render() {
        inventory.clear();
        PlayerData d = plugin.data().get(player);
        ShopManager sm = plugin.shops();
        for (ShopManager.Entry e : bySlot.values()) {
            int at = slotOf(e);
            if (at < 0 || at >= inventory.getSize()) continue;
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
                    lore.add(Txt.item("<gray>Precio: <yellow>" + priceText(e)));
                    if (e.quest > 0 && d.questIndex < e.quest) lore.add(Txt.item("<red>Se desbloquea al avanzar en la historia (misión " + e.quest + ")"));
                    if (e.stock > 0) {
                        int left = sm.stockLeft(e);
                        lore.add(Txt.item(left > 0 ? "<gray>Quedan: <white>" + left + "<gray> de " + e.stock
                                : "<red>Agotado · se repone en " + sm.restockMinutes(e) + " min"));
                    }
                    if (e.minLevel > 1) {
                        String c = d.level >= e.minLevel ? "<green>" : "<red>";
                        lore.add(Txt.item("<gray>Nivel requerido: " + c + e.minLevel));
                    }
                    lore.add(Txt.item("<yellow>Clic: comprar · Shift+clic: x4"));
                }
                case VENDER -> {
                    meta.displayName(Txt.item("<green>Vender " + (e.name != null ? e.name : Txt.pretty(display.getType().name()))));
                    lore.add(Txt.item("<gray>Entregas: <white>" + e.amount));
                    lore.add(Txt.item("<gray>Recibes: <yellow>" + sm.priceText(sm.priceOf(shop, e))));
                    lore.add(Txt.item("<yellow>Clic: vender · Shift+clic: todo lo posible"));
                }
                case MEJORA -> {
                    int max = plugin.getConfig().getInt("mejoras.maximo", 5);
                    meta.displayName(Txt.item("<gold>Mejorar arma"));
                    lore.add(Txt.item("<gray>Ten el arma en la mano (cualquiera:"));
                    lore.add(Txt.item("<gray>espada, hacha, maza, tridente, arco…)."));
                    lore.add(Txt.item("<gray>+1 de daño por mejora (máx. +" + max + ")"));
                    lore.add(Txt.item("<gray>Cuesta: <yellow>" + sm.priceText(e.price) + "</yellow> + 1 Fragmento de Mejora"));
                    lore.add(Txt.item("<yellow>Clic para mejorar"));
                }
            }
            meta.lore(lore);
            display.setItemMeta(meta);
            inventory.setItem(slotOf(e), display);
        }
        if (tabs) {
            ItemStack b = icon(Material.EMERALD, (selling ? "<gray>" : "<green><bold>") + "🛒 Comprar",
                    List.of("<gray>Lo que " + "vende la tienda", selling ? "<yellow>Clic para ver" : "<green>▶ Estás aquí"));
            ItemStack v = icon(Material.GOLD_INGOT, (selling ? "<gold><bold>" : "<gray>") + "💰 Vender",
                    List.of("<gray>Lo que la tienda te compra", selling ? "<green>▶ Estás aquí" : "<yellow>Clic para ver"));
            if (!selling) glint(b);
            else glint(v);
            inventory.setItem(tabBuy, b);
            inventory.setItem(tabSell, v);
        }
        inventory.setItem(infoSlot, icon(Material.SUNFLOWER, "<yellow>Tus Soles: " + Txt.num(d.soles) + " ☀",
                List.of("<gray>Nivel " + d.level)));
        fillEmpty();
    }

    @Override
    public void onClick(InventoryClickEvent e, int slot) {
        if (tabs && (slot == tabBuy || slot == tabSell)) {
            boolean want = slot == tabSell;
            if (want != selling) {
                selling = want;
                layout();
                Txt.sound(player, "item.book.page_turn", 1f, 1.1f);
                render();
            }
            return;
        }
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

    private int slotOf(ShopManager.Entry e) {
        for (Map.Entry<Integer, ShopManager.Entry> en : bySlot.entrySet()) if (en.getValue() == e) return en.getKey();
        return e.slot;
    }

    private static void glint(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        m.setEnchantmentGlintOverride(true);
        it.setItemMeta(m);
    }

    private String priceText(ShopManager.Entry e) {
        ShopManager sm = plugin.shops();
        if (e.payWith != null) return e.price + " × " + sm.specName(e.payWith);
        long today = sm.priceOf(shop, e);
        String t = sm.priceText(today);
        if (today < e.price) t += " <green>▼ oferta";
        else if (today > e.price) t += " <red>▲";
        return t;
    }

    private void buy(ShopManager.Entry entry, int times) {
        PlayerData d = plugin.data().get(player);
        if (entry.quest > 0 && d.questIndex < entry.quest) {
            Txt.send(player, "<red>Esto se desbloquea al avanzar en la historia.");
            Txt.sound(player, "entity.villager.no", 1f, 1f);
            return;
        }
        if (d.level < entry.minLevel) {
            Txt.send(player, "<red>Necesitas nivel " + entry.minLevel + " para comprar esto.");
            Txt.sound(player, "entity.villager.no", 1f, 1f);
            return;
        }
        int bought = 0;
        ShopManager sm = plugin.shops();
        boolean soldOut = false;
        long price = sm.priceOf(shop, entry);
        for (int i = 0; i < times; i++) {
            if (sm.stockLeft(entry) <= 0) {
                soldOut = true;
                break;
            }
            ItemStack it = sm.build(entry);
            if (it == null) break;
            if (entry.payWith != null) {
                if (sm.countSpec(player, entry.payWith) < entry.price) break;
                sm.takeSpec(player, entry.payWith, (int) entry.price);
            } else if (!plugin.levels().takeSoles(player, price)) break;
            plugin.items().give(player, it);
            sm.useStock(entry);
            bought++;
        }
        if (bought == 0 && soldOut) {
            Txt.send(player, "<red>Agotado. Se repone en " + sm.restockMinutes(entry) + " min.");
            Txt.sound(player, "entity.villager.no", 1f, 1f);
        } else if (bought == 0) {
            Txt.send(player, entry.payWith != null ? "<red>Necesitas " + entry.price + " × " + sm.specName(entry.payWith) + "." : "<red>No te alcanza el dinero.");
            Txt.sound(player, "entity.villager.no", 1f, 1f);
        } else {
            Txt.sound(player, "entity.experience_orb.pickup", 1f, 1.2f);
        }
    }

    private void sell(ShopManager.Entry entry, boolean all) {
        ShopManager sm = plugin.shops();
        if (entry.item == null) return;
        long price = sm.priceOf(shop, entry);
        int sold = 0;
        int limit = all ? 64 : 1;
        while (sold < limit && sm.stockLeft(entry) > 0 && sm.countSpec(player, entry.item) >= entry.amount) {
            sm.takeSpec(player, entry.item, entry.amount);
            plugin.levels().addSoles(player, price);
            sm.useStock(entry);
            sold++;
        }
        if (sold == 0) {
            Txt.send(player, "<red>Necesitas " + entry.amount + " de ese objeto.");
            Txt.sound(player, "entity.villager.no", 1f, 1f);
        } else {
            Txt.send(player, "<green>Vendiste " + (sold * entry.amount) + " y ganaste <yellow>" + Txt.num(sold * price) + " ☀");
            Txt.sound(player, "entity.villager.yes", 1f, 1f);
        }
    }

    private void upgrade(ShopManager.Entry entry) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        String id = plugin.items().idOf(hand);
        var def = id == null ? null : plugin.items().def(id);
        int max = plugin.getConfig().getInt("mejoras.maximo", 5);
        if (!plugin.items().upgradable(hand)) {
            Txt.send(player, "<red>Ten un arma en la mano principal (espada, hacha, maza, tridente, arco…).");
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
