package pe.kydium.eclipserpg.shop;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.data.PlayerData;
import pe.kydium.eclipserpg.items.ItemManager;
import pe.kydium.eclipserpg.items.Powers;
import pe.kydium.eclipserpg.menu.Menu;
import pe.kydium.eclipserpg.menu.Ui;
import pe.kydium.eclipserpg.util.Txt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tienda de un NPC. Siempre el mismo diseño (6 filas):
 * <pre>
 *  fila 1   pestañas de lo que vende (categorías) ··········· [💰 Vender]
 *  fila 2-5 los objetos de la pestaña (36 por página)
 *  fila 6   [Cerrar] · [◀] · [tus Soles] · [▶] · [⚒ Yunque] · [Ayuda]
 * </pre>
 * Vender tiene UNA sola pantalla ({@link SellMenu}): pones lo que quieras vender y te dice cuánto pagan;
 * los "pedidos" de tiendas.yml (tipo: vender) se pagan mejor ahí mismo. El yunque ({@link ForgeMenu}) es el único
 * lugar para mejorar y forjar runas.
 */
public final class ShopMenu extends Menu {

    /** Categorías en el orden de las pestañas. */
    enum Cat {
        COMIDA("comida", "<gold>🍖 Comida", Material.BREAD, "Comida para el camino"),
        BLOQUES("bloques", "<white>🧱 Bloques y luz", Material.LANTERN, "Bloques, luces y decoración"),
        HERRAMIENTAS("herramientas", "<yellow>⛏ Herramientas", Material.IRON_PICKAXE, "Picos, hachas, palas y útiles"),
        ARMAS("armas", "<red>⚔ Armas", Material.IRON_SWORD, "Espadas, arcos, hachas y flechas"),
        ARMADURAS("armaduras", "<aqua>🛡 Armaduras", Material.IRON_CHESTPLATE, "Cascos, petos, grebas, botas y escudos"),
        ARMADURAS_DIA("novedades", "<light_purple>✦ Novedades del día", Material.NETHER_STAR, "Piezas únicas: cambian cada día a medianoche"),
        RUNAS_ATAQUE("runas-ataque", "<red>✦ Runas de ataque", Material.AMETHYST_SHARD, "Poderes para armas (penetración, robo de vida…)"),
        RUNAS_DEFENSA("runas-defensa", "<aqua>✦ Runas de defensa", Material.PRISMARINE_SHARD, "Poderes para armaduras"),
        RUNAS_HERRAMIENTA("runas-herramienta", "<green>✦ Runas de herramienta", Material.ECHO_SHARD, "Poderes para picos, hachas, azadas y cañas"),
        MAGIA("magia", "<dark_purple>⚗ Pociones y magia", Material.BREWING_STAND, "Pociones, libros encantados y experiencia"),
        ESPECIAL("especiales", "<green>🎒 Especiales", Material.BUNDLE, "Mochilas, mapas, bloques especiales y rarezas"),
        MEJORAS("mejoras", "<gold>⚒ Fragmentos", Material.AMETHYST_CLUSTER, "Fragmentos para mejorar en el yunque"),
        TRUEQUE("trueque", "<light_purple>💎 Trueque", Material.AMETHYST_SHARD, "Se paga con objetos en vez de Soles");

        final String id, label, desc;
        final Material icon;

        Cat(String id, String label, Material icon, String desc) {
            this.id = id;
            this.label = label;
            this.icon = icon;
            this.desc = desc;
        }

        static Cat of(String id) {
            if (id == null) return null;
            String k = id.toLowerCase(Locale.ROOT).trim();
            switch (k) {
                case "armaduras-dia", "del-dia", "dia" -> { return ARMADURAS_DIA; }
                case "runas" -> { return RUNAS_ATAQUE; }
                case "pociones" -> { return MAGIA; }
                case "fragmentos" -> { return MEJORAS; }
                default -> { }
            }
            for (Cat c : values()) if (c.id.equals(k) || c.name().equalsIgnoreCase(k)) return c;
            return null;
        }
    }

    private static final int TABS = 8, SELL_TAB = 8, FIRST = 9, PAGE = 36;

    private final EclipseRPG plugin;
    private final ShopManager.Shop shop;
    private final Map<Cat, List<ShopManager.Entry>> byCat = new LinkedHashMap<>();
    private final Map<Integer, ShopManager.Entry> bySlot = new HashMap<>();
    private final Map<Integer, Cat> tabs = new HashMap<>();
    private final List<ShopManager.Entry> orders = new ArrayList<>();
    private ShopManager.Entry forge;
    private final boolean sells;
    private Cat current;
    private int page;

    public ShopMenu(EclipseRPG plugin, Player player, ShopManager.Shop shop, List<ShopManager.Entry> entries) {
        super(player, 6, "<dark_gray>" + Ui.plain(shop.title));
        this.plugin = plugin;
        this.shop = shop;
        for (ShopManager.Entry e : entries) {
            switch (e.kind) {
                case VENDER -> orders.add(e);
                case MEJORA -> forge = e;
                default -> byCat.computeIfAbsent(category(plugin, e), k -> new ArrayList<>()).add(e);
            }
        }
        this.sells = !orders.isEmpty() || plugin.shops().buysAnything(shop);
        // pestañas en el orden del enum; si hay más de 8, las sobrantes van a "Especiales"
        Map<Cat, List<ShopManager.Entry>> sorted = new LinkedHashMap<>();
        for (Cat c : Cat.values()) if (byCat.containsKey(c)) sorted.put(c, byCat.get(c));
        while (sorted.size() > TABS) {
            Cat extra = null;
            for (Cat c : sorted.keySet()) if (c != Cat.ESPECIAL) extra = c;
            if (extra == null) break;
            List<ShopManager.Entry> moved = sorted.remove(extra);
            sorted.computeIfAbsent(Cat.ESPECIAL, k -> new ArrayList<>()).addAll(moved);
        }
        byCat.clear();
        for (Cat c : Cat.values()) if (sorted.containsKey(c)) byCat.put(c, sorted.get(c));
        current = byCat.isEmpty() ? null : byCat.keySet().iterator().next();
        layout();
    }

    /** Categoría de un objeto: la de tiendas.yml ("categoria:") o la que corresponde a lo que es. */
    public static String categoryOf(EclipseRPG plugin, ShopManager.Entry e) {
        return category(plugin, e).id;
    }

    static Cat category(EclipseRPG plugin, ShopManager.Entry e) {
        String spec = e.item == null ? "" : e.item.toLowerCase(Locale.ROOT);
        if (spec.startsWith("runa:")) {
            String t = Powers.normType(spec.substring(5).split(":")[0]);
            if (t != null && Powers.isArmorType(t)) return Cat.RUNAS_DEFENSA;
            if (t != null && Powers.isToolType(t)) return Cat.RUNAS_HERRAMIENTA;
            return Cat.RUNAS_ATAQUE;
        }
        Cat set = Cat.of(e.category);
        if (set != null) return set;
        if (spec.startsWith("custom:fragmento_mejora")) return Cat.MEJORAS;
        if (e.payWith != null) return Cat.TRUEQUE;
        if (spec.startsWith("armadura-dia") || spec.startsWith("diario:") || e.daily != null) return Cat.ARMADURAS_DIA;
        if (spec.startsWith("pocion") || spec.startsWith("libro:")) return Cat.MAGIA;
        if (spec.startsWith("minimapa") || spec.startsWith("mapa") || spec.startsWith("bloque:")) return Cat.ESPECIAL;
        if (spec.startsWith("especial:")) return Cat.ARMAS;
        Material m;
        if (spec.startsWith("custom:")) {
            var ci = plugin.items().def(spec.substring(7));
            if (ci == null || ci.backpack > 0) return Cat.ESPECIAL;
            m = ci.material;
        } else {
            m = Material.matchMaterial(spec.startsWith("material:") ? spec.substring(9) : spec);
        }
        if (m == null) return Cat.ESPECIAL;
        if (ItemManager.isWeapon(m)) return Cat.ARMAS;
        if (ItemManager.isArmor(m) || m == Material.SHIELD) return Cat.ARMADURAS;
        if (ItemManager.isTool(m) || m == Material.FLINT_AND_STEEL || m.name().endsWith("BUCKET") || m == Material.COMPASS || m == Material.CLOCK
                || m == Material.SPYGLASS || m == Material.LEAD || m == Material.NAME_TAG || m == Material.BRUSH) return Cat.HERRAMIENTAS;
        if (m.isEdible()) return Cat.COMIDA;
        if (m == Material.ARROW || m == Material.SPECTRAL_ARROW || m == Material.FIREWORK_ROCKET) return Cat.ARMAS;
        if (m.name().contains("POTION") || m == Material.EXPERIENCE_BOTTLE || m == Material.ENCHANTED_BOOK) return Cat.MAGIA;
        if (m.isBlock()) return Cat.BLOQUES;
        return Cat.ESPECIAL;
    }

    private List<ShopManager.Entry> currentList() {
        return current == null ? List.of() : byCat.getOrDefault(current, List.of());
    }

    private int pages() {
        return Math.max(1, (currentList().size() + PAGE - 1) / PAGE);
    }

    private void layout() {
        bySlot.clear();
        tabs.clear();
        int i = 0;
        for (Cat c : byCat.keySet()) if (i < TABS) tabs.put(i++, c);
        List<ShopManager.Entry> list = currentList();
        int from = page * PAGE;
        for (int k = from; k < Math.min(list.size(), from + PAGE); k++) bySlot.put(FIRST + (k - from), list.get(k));
    }

    @Override
    public void render() {
        inventory.clear();
        PlayerData d = plugin.data().get(player);
        // pestañas
        for (Map.Entry<Integer, Cat> t : tabs.entrySet()) {
            Cat c = t.getValue();
            boolean here = c == current;
            int n = byCat.getOrDefault(c, List.of()).size();
            ItemStack ic = Ui.button(c.icon, (here ? "<bold>" : "") + c.label, List.of("<gray>" + c.desc, "<dark_gray>" + n + " a la venta"),
                    here ? "Estás aquí" : "Clic: ver");
            inventory.setItem(t.getKey(), here ? Ui.glint(ic) : ic);
        }
        if (sells) {
            List<String> l = new ArrayList<>();
            l.add("<gray>Pon lo que quieras vender y mira");
            l.add("<gray>cuánto te pagan antes de aceptar.");
            if (!orders.isEmpty()) {
                l.add("");
                l.add("<gold>Paga mejor por:");
                int k = 0;
                for (ShopManager.Entry o : orders) {
                    if (k++ >= 6) {
                        l.add("<dark_gray>… y " + (orders.size() - 6) + " más");
                        break;
                    }
                    l.add("<dark_gray>• <white>" + plugin.shops().specName(o.item) + " <dark_gray>x" + o.amount + " <gray>→ <yellow>"
                            + plugin.shops().priceText(plugin.shops().priceOf(shop, o)));
                }
            }
            if (!plugin.shops().buysAnything(shop)) {
                l.add("");
                l.add("<dark_gray>Solo compra lo de la lista.");
            }
            inventory.setItem(SELL_TAB, Ui.button(Material.GOLD_INGOT, "<green><bold>💰 Vender", l, "Clic: abrir la venta"));
        }
        // objetos
        for (Map.Entry<Integer, ShopManager.Entry> en : bySlot.entrySet()) {
            ItemStack display = display(en.getValue(), d);
            if (display != null) inventory.setItem(en.getKey(), display);
        }
        if (byCat.isEmpty())
            inventory.setItem(22, Ui.button(Material.BARRIER, "<gray>No vende nada por ahora", List.of(sells ? "<gray>Pero te compra: usa <green>💰 Vender" : "")));
        // barra de abajo
        inventory.setItem(Ui.NAV_BACK, Ui.close());
        if (page > 0) inventory.setItem(Ui.NAV_PREV, Ui.prev(page, pages()));
        inventory.setItem(Ui.NAV_WALLET, Ui.wallet(plugin, player));
        if (page < pages() - 1) inventory.setItem(Ui.NAV_NEXT, Ui.next(page, pages()));
        if (forge != null)
            inventory.setItem(Ui.NAV_EXTRA, Ui.button(Material.ANVIL, "<gold><bold>⚒ Yunque", List.of("<gray>Mejorar +1…+" + plugin.items().maxUpgrade()
                    + " con fragmentos,", "<gray>forjar runas y juntar fragmentos."), "Clic: abrir el yunque"));
        inventory.setItem(Ui.NAV_HELP, help());
        Ui.barRow(inventory, 5);
        fillEmpty();
    }

    private ItemStack help() {
        List<String> l = new ArrayList<>();
        if (current != null) l.add("<white>" + current.desc);
        l.add("");
        l.add("<gray>Clic en un objeto: comprar 1.");
        l.add("<gray>Shift+clic: comprar 4 de una vez.");
        if (current == Cat.ARMADURAS_DIA) {
            l.add("");
            l.add("<light_purple>Las novedades cambian a medianoche.");
            l.add("<gray>Hay una de cada una: ¡el primero se la lleva!");
        }
        if (current == Cat.RUNAS_ATAQUE || current == Cat.RUNAS_DEFENSA || current == Cat.RUNAS_HERRAMIENTA) {
            l.add("");
            l.add("<light_purple>Las runas se forjan en el yunque");
            l.add("<gray>y cuestan niveles de experiencia.");
            l.add("<gold>Contra jefes: <white>Penetración <gray>y <white>Cazajefes<gray>.");
        }
        if (shop.variation > 0) {
            l.add("");
            l.add("<gray>Los precios cambian un poco cada día:");
            l.add("<green>▼ oferta <gray>· <red>▲ más caro que lo normal");
        }
        return Ui.help("Cómo comprar", l);
    }

    private ItemStack display(ShopManager.Entry e, PlayerData d) {
        ShopManager sm = plugin.shops();
        ItemStack display = sm.build(e);
        if (display == null) return null;
        display = display.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta == null) return null;
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        // las runas traen "cómo forjarla" de 6 líneas: en la tienda sobra, lo dice la ayuda
        if (Powers.isRune(display) && lore.size() > 2) lore = new ArrayList<>(lore.subList(0, 2));
        lore.add(Txt.item("<dark_gray>─────────────────"));
        if (e.name != null) meta.displayName(Txt.item(e.name));
        if (e.amount > 1) lore.add(Txt.item("<gray>Cantidad: <white>" + e.amount));
        lore.add(Txt.item("<gray>Precio: <gold>" + priceText(e)));
        boolean locked = false;
        if (e.quest > 0 && d.questIndex < e.quest) {
            lore.add(Txt.item("<red>🔒 Se desbloquea en la misión " + e.quest));
            locked = true;
        }
        if (e.minLevel > 1) {
            boolean ok = d.level >= e.minLevel;
            lore.add(Txt.item("<gray>Nivel: " + (ok ? "<green>" : "<red>") + e.minLevel + (ok ? " ✔" : " (tienes " + d.level + ")")));
            locked |= !ok;
        }
        if (e.stock > 0) {
            int left = sm.stockLeft(e);
            lore.add(Txt.item(left > 0 ? "<gray>Quedan: <white>" + left + "<gray>/" + e.stock
                    : e.daily != null ? "<red>Vendida · mañana habrá otras" : "<red>Agotado · vuelve en " + sm.restockMinutes(e) + " min"));
        }
        lore.add(Component.empty());
        if (!locked) lore.add(Txt.item(Ui.action(e.daily != null || e.stock == 1 ? "Clic: comprar" : "Clic: comprar · Shift+clic: x4")));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        display.setItemMeta(meta);
        return display;
    }

    @Override
    public void onClick(InventoryClickEvent e, int slot) {
        Cat c = tabs.get(slot);
        if (c != null) {
            if (c != current) {
                current = c;
                page = 0;
                layout();
                Txt.sound(player, "item.book.page_turn", 1f, 1.1f);
                render();
            }
            return;
        }
        if (slot == SELL_TAB && sells) {
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> new SellMenu(plugin, player, shop, orders).open());
            return;
        }
        if (slot == Ui.NAV_BACK) {
            player.closeInventory();
            return;
        }
        if (slot == Ui.NAV_EXTRA && forge != null) {
            long price = forge.price;
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> new ForgeMenu(plugin, player, shop, price).open());
            return;
        }
        if (slot == Ui.NAV_PREV && page > 0) {
            page--;
            layout();
            render();
            return;
        }
        if (slot == Ui.NAV_NEXT && page < pages() - 1) {
            page++;
            layout();
            render();
            return;
        }
        ShopManager.Entry entry = bySlot.get(slot);
        if (entry == null) return;
        buy(entry, e.isShiftClick() ? 4 : 1);
        render();
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
            Txt.send(player, entry.daily != null ? "<red>Alguien ya compró esa pieza. Mañana habrá otras." : "<red>Agotado. Vuelve en " + sm.restockMinutes(entry) + " min.");
            Txt.sound(player, "entity.villager.no", 1f, 1f);
        } else if (bought == 0) {
            Txt.send(player, entry.payWith != null ? "<red>Necesitas " + entry.price + " × " + sm.specName(entry.payWith) + "." : "<red>No te alcanzan los Soles.");
            Txt.sound(player, "entity.villager.no", 1f, 1f);
        } else {
            Txt.sound(player, "entity.experience_orb.pickup", 1f, 1.2f);
            if (bought > 1) player.sendActionBar(Txt.mm("<green>Compraste " + bought + " · <gold>-" + Txt.num(entry.payWith != null ? entry.price * bought : price * bought)
                    + (entry.payWith != null ? " " + sm.specName(entry.payWith) : " ☀")));
        }
    }
}
