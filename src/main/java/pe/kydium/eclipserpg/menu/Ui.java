package pe.kydium.eclipserpg.menu;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.data.PlayerData;
import pe.kydium.eclipserpg.util.Txt;

import java.util.ArrayList;
import java.util.List;

/**
 * Estilo común de todos los menús, para que se vean como un solo juego:
 * <ul>
 *   <li>Título: {@code "<dark_gray>Lugar · <nombre>"} (sin colores chillones, el inventario ya tiene fondo).</li>
 *   <li>Botones: nombre en color, descripción gris, una línea vacía y las acciones con "▸".</li>
 *   <li>Barra de abajo (fila 6): ◀ volver / cerrar · ◀ página · tu dinero · página ▶ · ayuda.</li>
 * </ul>
 */
public final class Ui {
    private Ui() {}

    /** Huecos de la barra de abajo en un menú de 6 filas. */
    public static final int NAV_BACK = 45, NAV_PREV = 47, NAV_WALLET = 49, NAV_NEXT = 51, NAV_EXTRA = 52, NAV_HELP = 53;

    /** Botón con descripción y acciones ("Clic: comprar", "Shift+clic: x4"…). */
    public static ItemStack button(Material m, String title, List<String> desc, String... actions) {
        List<String> lore = new ArrayList<>();
        for (String d : desc) lore.add(d.isEmpty() || d.startsWith("<") ? d : "<gray>" + d);
        if (actions.length > 0) {
            if (!lore.isEmpty()) lore.add("");
            for (String a : actions) lore.add(action(a));
        }
        ItemStack it = Menu.icon(m, title, lore);
        ItemMeta meta = it.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(meta);
        return it;
    }

    /** "Clic: comprar" → "▸ Clic: comprar" con el verbo resaltado. */
    public static String action(String a) {
        int c = a.indexOf(':');
        if (c < 0) return "<dark_gray>▸ <yellow>" + a;
        return "<dark_gray>▸ <yellow>" + a.substring(0, c + 1) + "<white>" + a.substring(c + 1);
    }

    public static ItemStack glint(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        m.setEnchantmentGlintOverride(true);
        it.setItemMeta(m);
        return it;
    }

    /** Cristal de la barra de abajo (gris, un poco más claro que el fondo). */
    public static ItemStack bar() {
        ItemStack it = Menu.filler();
        it = it.withType(Material.GRAY_STAINED_GLASS_PANE);
        return it;
    }

    /** Fila completa de cristal gris (la barra). */
    public static void barRow(Inventory inv, int row) {
        for (int i = row * 9; i < row * 9 + 9 && i < inv.getSize(); i++) if (inv.getItem(i) == null) inv.setItem(i, bar());
    }

    public static ItemStack back(String to) {
        return button(Material.ARROW, "<white>◀ Volver", List.of("<gray>A " + to), "Clic: volver");
    }

    public static ItemStack close() {
        return button(Material.BARRIER, "<red>Cerrar", List.of());
    }

    public static ItemStack prev(int page, int pages) {
        return button(Material.SPECTRAL_ARROW, "<white>◀ Página anterior", List.of("<gray>Página " + page + " de " + pages));
    }

    public static ItemStack next(int page, int pages) {
        return button(Material.SPECTRAL_ARROW, "<white>Página siguiente ▶", List.of("<gray>Página " + (page + 2) + " de " + pages));
    }

    /** Monedero: Soles, nivel y niveles de experiencia. */
    public static ItemStack wallet(EclipseRPG plugin, Player p) {
        PlayerData d = plugin.data().get(p);
        return button(Material.SUNFLOWER, "<gold>" + Txt.num(d.soles) + " ☀ <yellow>Soles",
                List.of("<gray>Nivel <white>" + d.level + "<gray> · Experiencia <green>" + p.getLevel() + " niveles"));
    }

    public static ItemStack help(String title, List<String> lines) {
        return button(Material.KNOWLEDGE_BOOK, "<aqua>" + title, lines);
    }

    /** Nombre limpio de una tienda o NPC (sin colores ni "Tienda ·"). */
    public static String plain(String s) {
        return s == null ? "" : s.replaceAll("<[^>]+>", "").trim();
    }
}
