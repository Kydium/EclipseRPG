package pe.kydium.eclipserpg.shop;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.data.PlayerData;
import pe.kydium.eclipserpg.items.ItemManager;
import pe.kydium.eclipserpg.menu.Menu;
import pe.kydium.eclipserpg.util.Txt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Inventario de una tienda. Las tiendas grandes se ordenan por categorías (pestañas arriba):
 * comida, bloques, herramientas, armas, armaduras, armaduras del día, magia, especiales, mejoras,
 * trueque, lo que te compran y "vender lo que sea". Clic = 1 vez, Shift + clic = 4 veces.
 */
public final class ShopMenu extends Menu {

    /** Categorías en el orden de las pestañas. */
    enum Cat {
        COMIDA("comida", "<gold>🍖 Comida", Material.BREAD),
        BLOQUES("bloques", "<white>🧱 Bloques y luz", Material.LANTERN),
        HERRAMIENTAS("herramientas", "<yellow>⛏ Herramientas", Material.IRON_PICKAXE),
        ARMAS("armas", "<red>⚔ Armas", Material.IRON_SWORD),
        ARMADURAS("armaduras", "<aqua>🛡 Armaduras", Material.IRON_CHESTPLATE),
        ARMADURAS_DIA("novedades", "<light_purple>✦ Novedades del día", Material.NETHER_STAR),
        MAGIA("magia", "<dark_purple>⚗ Pociones y magia", Material.BREWING_STAND),
        ESPECIAL("especiales", "<green>🎒 Especiales", Material.BUNDLE),
        MEJORAS("mejoras", "<gold>⚒ Mejoras y forja", Material.ANVIL),
        TRUEQUE("trueque", "<light_purple>💎 Trueque", Material.AMETHYST_SHARD),
        VENDER("vender", "<green>💰 Te compra", Material.GOLD_INGOT),
        VENDER_TODO("vender-todo", "<yellow>💰 Vender lo que sea", Material.CHEST);

        final String id, label;
        final Material icon;

        Cat(String id, String label, Material icon) {
            this.id = id;
            this.label = label;
            this.icon = icon;
        }

        static Cat of(String id) {
            if (id == null) return null;
            if (id.equals("armaduras-dia")) return ARMADURAS_DIA;
            for (Cat c : values()) if (c.id.equals(id) || c.name().equalsIgnoreCase(id)) return c;
            return null;
        }
    }

    private static final int PAGE = 36, FIRST = 9;   // filas 1 a 4
    private static final int PREV = 45, NEXT = 53, INFO = 49;

    private final EclipseRPG plugin;
    private final ShopManager.Shop shop;
    private final Map<Integer, ShopManager.Entry> bySlot = new HashMap<>();
    private final List<ShopManager.Entry> all;
    /** Modo clásico (tiendas pequeñas o rotativas): cada objeto en su hueco de tiendas.yml. */
    private final boolean classic;
    private final Map<Cat, List<ShopManager.Entry>> byCat = new LinkedHashMap<>();
    private final Map<Integer, Cat> tabs = new HashMap<>();
    private Cat current;
    private int page;

    public ShopMenu(EclipseRPG plugin, Player player, ShopManager.Shop shop, List<ShopManager.Entry> entries) {
        super(player, categorized(plugin, shop, entries) ? 6 : shop.rows, shop.title);
        this.plugin = plugin;
        this.shop = shop;
        this.all = entries;
        this.classic = !categorized(plugin, shop, entries);
        if (!classic) {
            for (ShopManager.Entry e : entries) byCat.computeIfAbsent(category(plugin, e), k -> new ArrayList<>()).add(e);
            if (plugin.shops().buysAnything(shop)) byCat.put(Cat.VENDER_TODO, List.of());
            // ordenar las pestañas como el enum
            Map<Cat, List<ShopManager.Entry>> sorted = new LinkedHashMap<>();
            for (Cat c : Cat.values()) if (byCat.containsKey(c)) sorted.put(c, byCat.get(c));
            byCat.clear();
            byCat.putAll(sorted);
            // máximo 9 pestañas: lo que sobre pasa a "Especiales" (la de vender lo que sea siempre queda)
            while (byCat.size() > 9) {
                Cat extra = null;
                for (Cat c : byCat.keySet()) if (c != Cat.VENDER_TODO && c != Cat.ESPECIAL) extra = c;
                if (extra == null) break;
                List<ShopManager.Entry> moved = byCat.remove(extra);
                List<ShopManager.Entry> esp = new ArrayList<>(byCat.getOrDefault(Cat.ESPECIAL, List.of()));
                esp.addAll(moved);
                Map<Cat, List<ShopManager.Entry>> re = new LinkedHashMap<>();
                for (Cat c : Cat.values()) if (byCat.containsKey(c) || c == Cat.ESPECIAL) re.put(c, c == Cat.ESPECIAL ? esp : byCat.get(c));
                byCat.clear();
                byCat.putAll(re);
            }
            for (Cat c : byCat.keySet()) if (c != Cat.VENDER_TODO) {
                current = c;
                break;
            }
            if (current == null) current = Cat.VENDER_TODO;
        }
        layout();
    }

    private static boolean categorized(EclipseRPG plugin, ShopManager.Shop shop, List<ShopManager.Entry> entries) {
        if (shop.rotating) return false;
        if (plugin.shops().buysAnything(shop)) return true;
        java.util.Set<Cat> cats = new java.util.HashSet<>();
        for (ShopManager.Entry e : entries) cats.add(category(plugin, e));
        return entries.size() >= 10 && cats.size() >= 2;
    }

    /** Categoría de un objeto: la de tiendas.yml ("categoria:") o la que corresponde a lo que es. */
    public static String categoryOf(EclipseRPG plugin, ShopManager.Entry e) {
        return category(plugin, e).id;
    }

    static Cat category(EclipseRPG plugin, ShopManager.Entry e) {
        Cat set = Cat.of(e.category);
        if (set != null) return set;
        if (e.kind == ShopManager.Kind.MEJORA) return Cat.MEJORAS;
        if (e.kind == ShopManager.Kind.VENDER) return Cat.VENDER;
        String spec = e.item == null ? "" : e.item.toLowerCase(Locale.ROOT);
        if (spec.startsWith("custom:fragmento_mejora")) return Cat.MEJORAS;
        if (e.payWith != null) return Cat.TRUEQUE;
        if (spec.startsWith("armadura-dia") || spec.startsWith("diario:")) return Cat.ARMADURAS_DIA;
        if (spec.startsWith("pocion") || spec.startsWith("libro:") || spec.startsWith("runa:")) return Cat.MAGIA;
        if (spec.startsWith("minimapa") || spec.startsWith("mapa") || spec.startsWith("bloque:")) return Cat.ESPECIAL;
        if (spec.startsWith("especial:")) return Cat.ARMAS;
        Material m = null;
        if (spec.startsWith("custom:")) {
            var ci = plugin.items().def(spec.substring(7));
            if (ci == null) return Cat.ESPECIAL;
            if (ci.backpack > 0) return Cat.ESPECIAL;
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

    private void layout() {
        bySlot.clear();
        tabs.clear();
        if (classic) {
            for (ShopManager.Entry e : all) bySlot.put(e.slot, e);
            return;
        }
        int i = 0;
        for (Cat c : byCat.keySet()) if (i < 9) tabs.put(i++, c);
        List<ShopManager.Entry> list = byCat.getOrDefault(current, List.of());
        int from = page * PAGE;
        for (int k = from; k < Math.min(list.size(), from + PAGE); k++) bySlot.put(FIRST + (k - from), list.get(k));
    }

    @Override
    public void render() {
        inventory.clear();
        PlayerData d = plugin.data().get(player);
        ShopManager sm = plugin.shops();
        for (Map.Entry<Integer, ShopManager.Entry> en : bySlot.entrySet()) {
            int at = en.getKey();
            ShopManager.Entry e = en.getValue();
            if (at < 0 || at >= inventory.getSize()) continue;
            ItemStack display = e.kind == ShopManager.Kind.MEJORA ? new ItemStack(Material.ANVIL) : sm.build(e);
            if (display == null) continue;
            display = display.clone();
            ItemMeta meta = display.getItemMeta();
            if (meta == null) continue;
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Component.empty());
            switch (e.kind) {
                case COMPRAR -> {
                    if (e.name != null) meta.displayName(Txt.item(e.name));
                    if (e.amount > 1) lore.add(Txt.item("<gray>Cantidad: <white>" + e.amount));
                    lore.add(Txt.item("<gray>Precio: <yellow>" + priceText(e)));
                    if (e.quest > 0 && d.questIndex < e.quest) lore.add(Txt.item("<red>Se desbloquea al avanzar en la historia (misión " + e.quest + ")"));
                    if (e.stock > 0) {
                        int left = sm.stockLeft(e);
                        lore.add(Txt.item(left > 0 ? "<gray>Quedan: <white>" + left + "<gray> de " + e.stock
                                : e.daily != null ? "<red>Vendida · mañana habrá otras" : "<red>Agotado · se repone en " + sm.restockMinutes(e) + " min"));
                    }
                    if (e.minLevel > 1) {
                        String c = d.level >= e.minLevel ? "<green>" : "<red>";
                        lore.add(Txt.item("<gray>Nivel requerido: " + c + e.minLevel));
                    }
                    lore.add(Txt.item(e.daily != null || e.stock == 1 ? "<yellow>Clic: comprar" : "<yellow>Clic: comprar · Shift+clic: x4"));
                }
                case VENDER -> {
                    meta.displayName(Txt.item("<green>Vender " + (e.name != null ? e.name : Txt.pretty(display.getType().name()))));
                    lore.add(Txt.item("<gray>Entregas: <white>" + e.amount));
                    lore.add(Txt.item("<gray>Recibes: <yellow>" + sm.priceText(sm.priceOf(shop, e))));
                    lore.add(Txt.item("<yellow>Clic: vender · Shift+clic: todo lo posible"));
                }
                case MEJORA -> {
                    meta.displayName(Txt.item("<gold><bold>⚒ Yunque de Bruno"));
                    for (String l : List.of("<gray>Mejora armas, armaduras y herramientas", "<gray>hasta +" + plugin.items().maxUpgrade() + " con fragmentos, o forja runas.",
                            "<gray>Pones el objeto y el fragmento en sus huecos:", "<gray>no hace falta tenerlo en la mano.", "", "<yellow>Clic para abrir el yunque"))
                        lore.add(Txt.item(l));
                }
            }
            meta.lore(lore);
            display.setItemMeta(meta);
            inventory.setItem(at, display);
        }
        if (!classic) {
            for (Map.Entry<Integer, Cat> t : tabs.entrySet()) {
                Cat c = t.getValue();
                int n = byCat.getOrDefault(c, List.of()).size();
                List<String> l = new ArrayList<>();
                if (c == Cat.VENDER_TODO) {
                    l.add("<gray>Véndele cualquier objeto:");
                    l.add("<gray>minerales, botín de monstruos, armas…");
                } else l.add("<gray>" + n + " objeto(s)");
                l.add(c == current ? "<green>▶ Estás aquí" : "<yellow>Clic para ver");
                ItemStack ic = icon(c.icon, (c == current ? "<bold>" : "") + c.label, l);
                if (c == current) glint(ic);
                inventory.setItem(t.getKey(), ic);
            }
            List<ShopManager.Entry> list = byCat.getOrDefault(current, List.of());
            int pages = Math.max(1, (list.size() + PAGE - 1) / PAGE);
            if (page > 0) inventory.setItem(PREV, icon(Material.ARROW, "<yellow>◀ Página anterior", List.of("<gray>Página " + page + " de " + pages)));
            if (page < pages - 1) inventory.setItem(NEXT, icon(Material.ARROW, "<yellow>Página siguiente ▶", List.of("<gray>Página " + (page + 2) + " de " + pages)));
            if (current == Cat.ARMADURAS_DIA)
                inventory.setItem(47, icon(Material.CLOCK, "<light_purple>Cambian cada día a medianoche", List.of("<gray>Cosas únicas al azar: encantamientos, poderes,",
                        "<gray>bonificaciones y nombres distintos cada día.", "<gray>Solo hay una de cada una: ¡el primero se la lleva!")));
        }
        int info = classic ? shop.rows * 9 - 1 : INFO;
        inventory.setItem(info, icon(Material.SUNFLOWER, "<yellow>Tus Soles: " + Txt.num(d.soles) + " ☀", List.of("<gray>Nivel " + d.level)));
        fillEmpty();
    }

    @Override
    public void onClick(InventoryClickEvent e, int slot) {
        if (!classic) {
            Cat c = tabs.get(slot);
            if (c != null) {
                if (c == Cat.VENDER_TODO) {
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> new SellMenu(plugin, player, shop).open());
                    return;
                }
                if (c != current) {
                    current = c;
                    page = 0;
                    layout();
                    Txt.sound(player, "item.book.page_turn", 1f, 1.1f);
                    render();
                }
                return;
            }
            if (slot == PREV && page > 0) {
                page--;
                layout();
                render();
                return;
            }
            if (slot == NEXT) {
                int pages = Math.max(1, (byCat.getOrDefault(current, List.of()).size() + PAGE - 1) / PAGE);
                if (page < pages - 1) {
                    page++;
                    layout();
                    render();
                }
                return;
            }
        }
        ShopManager.Entry entry = bySlot.get(slot);
        if (entry == null) return;
        boolean shift = e.isShiftClick();
        switch (entry.kind) {
            case COMPRAR -> buy(entry, shift ? 4 : 1);
            case VENDER -> sell(entry, shift);
            case MEJORA -> {
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> new ForgeMenu(plugin, player, shop, entry.price).open());
                return;
            }
        }
        render();
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
            Txt.send(player, entry.daily != null ? "<red>Alguien ya compró esa pieza. Mañana habrá otras." : "<red>Agotado. Se repone en " + sm.restockMinutes(entry) + " min.");
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

}
