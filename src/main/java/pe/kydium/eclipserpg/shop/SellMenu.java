package pe.kydium.eclipserpg.shop;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.menu.Menu;
import pe.kydium.eclipserpg.menu.Ui;
import pe.kydium.eclipserpg.util.Txt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * La única pantalla para vender. Pones objetos arriba (clic en tu inventario), ves cuánto te pagan y aceptas.
 * <ul>
 *   <li>Los <b>pedidos</b> del comerciante (tiendas.yml, tipo: vender) se pagan a su precio, que es mejor.</li>
 *   <li>Si la tienda compra de todo ({@code tiendas.compran-todo}), lo demás se paga según lo que vale.</li>
 *   <li>Lo que no se vende vuelve a tu inventario.</li>
 * </ul>
 */
public final class SellMenu extends Menu {
    private static final int AREA = 45, ALL = 46, INFO = 48, SELL = 50;
    private final EclipseRPG plugin;
    private final ShopManager.Shop shop;
    private final List<ShopManager.Entry> orders;
    private final boolean anything;
    private boolean done;

    public SellMenu(EclipseRPG plugin, Player player, ShopManager.Shop shop) {
        this(plugin, player, shop, shop == null ? List.of() : shop.entries.stream().filter(e -> e.kind == ShopManager.Kind.VENDER).toList());
    }

    public SellMenu(EclipseRPG plugin, Player player, ShopManager.Shop shop, List<ShopManager.Entry> orders) {
        super(player, 6, "<dark_gray>Vender · " + (shop == null ? "Mercader" : Ui.plain(shop.title)));
        this.plugin = plugin;
        this.shop = shop;
        this.orders = orders;
        this.anything = shop == null || plugin.shops().buysAnything(shop);
    }

    @Override
    protected boolean isInputSlot(int slot) {
        return slot < AREA;
    }

    // ------------------------------------------------------------------ precios

    /** Pedido del comerciante que corresponde a este objeto (o null). */
    private ShopManager.Entry orderFor(ItemStack it) {
        if (it == null) return null;
        String id = plugin.items().idOf(it);
        for (ShopManager.Entry o : orders) {
            if (o.item == null) continue;
            String spec = o.item.startsWith("material:") ? o.item.substring(9) : o.item;
            if (spec.startsWith("custom:")) {
                if (spec.substring(7).equals(id)) return o;
            } else if (id == null) {
                Material m = Material.matchMaterial(spec.toUpperCase(Locale.ROOT));
                if (m != null && m == it.getType()) return o;
            }
        }
        return null;
    }

    /** Cuánto se vende y cuánto se paga de cada pila (simulando el stock de los pedidos). */
    private record Quote(long pay, int[] sold) {}

    private Quote quote() {
        int[] sold = new int[AREA];
        double total = 0;
        Map<String, Integer> stock = new HashMap<>();
        for (int i = 0; i < AREA; i++) {
            ItemStack it = real(inventory.getItem(i));
            if (it == null) continue;
            int n = it.getAmount();
            ShopManager.Entry o = orderFor(it);
            if (o != null) {
                int left = stock.computeIfAbsent(o.key == null ? o.item : o.key, k -> plugin.shops().stockLeft(o));
                int bundles = Math.min(n / Math.max(1, o.amount), left);
                stock.put(o.key == null ? o.item : o.key, left - bundles);
                total += bundles * (double) plugin.shops().priceOf(shop, o);
                sold[i] = bundles * Math.max(1, o.amount);
                n -= sold[i];
            }
            if (n > 0 && anything && plugin.shops().values().refuse(it) == null) {
                ItemStack rest = it.clone();
                rest.setAmount(n);
                total += plugin.shops().values().value(rest);
                sold[i] += n;
            }
        }
        return new Quote((long) Math.floor(total), sold);
    }

    private static ItemStack real(ItemStack it) {
        return it == null || isFiller(it) ? null : it;
    }

    /** ¿Lo compra? null = sí; si no, el motivo. */
    private String refuse(ItemStack it) {
        if (orderFor(it) != null) return null;
        String no = plugin.shops().values().refuse(it);
        if (no != null) return no;
        if (!anything) return "<red>" + (shop == null ? "Este comerciante" : Ui.plain(shop.title)) + " no compra eso. <gray>Mira la lista en ❔.";
        if (plugin.shops().values().value(it) <= 0) return "<gray>Eso no vale nada aquí (tierra, piedra…).";
        return null;
    }

    // ------------------------------------------------------------------ pantalla

    @Override
    public void render() {
        for (int i = AREA; i < 54; i++) inventory.setItem(i, null);
        Quote q = quote();
        int items = 0, kept = 0;
        for (int i = 0; i < AREA; i++) {
            ItemStack it = real(inventory.getItem(i));
            if (it == null) continue;
            items += q.sold()[i];
            kept += it.getAmount() - q.sold()[i];
        }
        inventory.setItem(Ui.NAV_BACK, shop != null ? Ui.back("la tienda") : Ui.close());
        inventory.setItem(ALL, Ui.button(Material.HOPPER, "<yellow>Poner todo lo vendible",
                List.of("<gray>Pasa de tu inventario lo que te compran.", "<dark_gray>No toca la barra rápida ni la armadura."), "Clic: poner"));
        List<String> info = new ArrayList<>();
        info.add("<gray>1. Clic en objetos de tu inventario para");
        info.add("<gray>   ponerlos arriba (clic arriba = devolver).");
        info.add("<gray>2. Mira el total en el botón verde.");
        info.add("<gray>3. Clic en el botón verde para vender.");
        if (!orders.isEmpty()) {
            info.add("");
            info.add("<gold>Pedidos (pagan mejor):");
            int k = 0;
            for (ShopManager.Entry o : orders) {
                if (k++ >= 10) break;
                int left = plugin.shops().stockLeft(o);
                info.add("<dark_gray>• <white>" + plugin.shops().specName(o.item) + " <dark_gray>x" + o.amount + " <gray>→ <yellow>"
                        + plugin.shops().priceText(plugin.shops().priceOf(shop, o)) + (o.stock > 0 ? " <dark_gray>(" + left + " más)" : ""));
            }
        }
        info.add("");
        info.add(anything ? "<gray>Además compra casi de todo a su valor." : "<gray>Solo compra lo de los pedidos.");
        inventory.setItem(INFO, Ui.help("Cómo vender", info));
        inventory.setItem(Ui.NAV_WALLET, Ui.wallet(plugin, player));
        List<String> sl = new ArrayList<>();
        sl.add("<gray>Vendes: <white>" + items + " objeto(s)");
        if (kept > 0) sl.add("<gray>Vuelven a tu inventario: <white>" + kept);
        inventory.setItem(SELL, Ui.button(q.pay() > 0 ? Material.EMERALD_BLOCK : Material.GRAY_DYE,
                (q.pay() > 0 ? "<green><bold>Vender por " : "<gray>Vender por ") + Txt.num(q.pay()) + " ☀", sl, q.pay() > 0 ? "Clic: aceptar" : "Pon algo arriba"));
        Ui.barRow(inventory, 5);
    }

    @Override
    public void onBottomClick(InventoryClickEvent e, int slot) {
        ItemStack it = player.getInventory().getItem(slot);
        if (it == null || it.getType().isAir()) return;
        String no = refuse(it);
        if (no != null) {
            if (!no.isEmpty()) player.sendActionBar(Txt.mm(no));
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
            ItemStack it = real(inventory.getItem(slot));
            inventory.setItem(slot, null);
            if (it != null) give(it);
            render();
            return;
        }
        if (slot == ALL) {
            ItemStack[] inv = player.getInventory().getStorageContents();
            int moved = 0;
            for (int i = 9; i < inv.length; i++) {
                ItemStack it = inv[i];
                if (it == null || refuse(it) != null) continue;
                var left = inventory.addItem(it.clone());
                player.getInventory().setItem(i, left.isEmpty() ? null : left.values().iterator().next());
                moved++;
            }
            if (moved == 0) player.sendActionBar(Txt.mm("<gray>No tienes nada que te compren aquí (fuera de la barra rápida)."));
            Txt.sound(player, "item.bundle.insert", 1f, 1f);
            render();
            return;
        }
        if (slot == Ui.NAV_BACK) {
            returnAll();
            done = true;
            if (shop != null) org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> plugin.shops().open(player, shop.id));
            else player.closeInventory();
            return;
        }
        if (slot == SELL) sell();
    }

    private void sell() {
        Quote q = quote();
        int count = 0;
        // gasta el stock de los pedidos
        for (int i = 0; i < AREA; i++) {
            ItemStack it = real(inventory.getItem(i));
            if (it == null) continue;
            ShopManager.Entry o = orderFor(it);
            if (o != null && o.stock > 0) {
                int bundles = Math.min(q.sold()[i], it.getAmount()) / Math.max(1, o.amount);
                for (int k = 0; k < bundles; k++) plugin.shops().useStock(o);
            }
            count += q.sold()[i];
            int keep = it.getAmount() - q.sold()[i];
            inventory.setItem(i, null);
            if (keep > 0) {
                ItemStack back = it.clone();
                back.setAmount(keep);
                give(back);
            }
        }
        if (count == 0) {
            player.sendActionBar(Txt.mm("<gray>No pusiste nada que te compren."));
            Txt.sound(player, "entity.villager.no", 1f, 1f);
            render();
            return;
        }
        if (q.pay() > 0) plugin.levels().addSoles(player, q.pay());
        Txt.send(player, "<green>Vendiste " + count + " objeto(s) por <gold>" + Txt.num(q.pay()) + " ☀");
        Txt.sound(player, "entity.villager.yes", 1f, 1f);
        Txt.sound(player, "block.amethyst_block.chime", 0.6f, 1.6f);
        render();
    }

    private void give(ItemStack it) {
        for (ItemStack l : player.getInventory().addItem(it).values()) player.getWorld().dropItem(player.getLocation(), l);
    }

    private void returnAll() {
        for (int i = 0; i < AREA; i++) {
            ItemStack it = real(inventory.getItem(i));
            inventory.setItem(i, null);
            if (it != null) give(it);
        }
    }

    @Override
    public void onClose() {
        if (!done) returnAll();
    }
}
