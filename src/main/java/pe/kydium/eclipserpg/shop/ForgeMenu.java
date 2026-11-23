package pe.kydium.eclipserpg.shop;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.items.ItemManager;
import pe.kydium.eclipserpg.items.Powers;
import pe.kydium.eclipserpg.menu.Menu;
import pe.kydium.eclipserpg.util.Txt;

import java.util.ArrayList;
import java.util.List;

/**
 * Yunque de Bruno: se pone el objeto (arma, armadura o herramienta) en un hueco y el fragmento
 * o la runa en el otro, sin tenerlo en la mano. Lo que quede vuelve al inventario al cerrar.
 */
public final class ForgeMenu extends Menu {
    private static final int ITEM = 20, MAT = 24, GO = 22, INFO = 4, BACK = 36, COMBINE = 40;
    private final EclipseRPG plugin;
    private final ShopManager.Shop shop;
    private final long basePrice;
    private boolean done;

    public ForgeMenu(EclipseRPG plugin, Player player, ShopManager.Shop shop, long basePrice) {
        super(player, 5, "<dark_gray>⚒ Yunque de Bruno");
        this.plugin = plugin;
        this.shop = shop;
        this.basePrice = basePrice;
    }

    @Override
    protected boolean isInputSlot(int slot) {
        return slot == ITEM || slot == MAT;
    }

    private ItemStack item() {
        return real(inventory.getItem(ITEM));
    }

    private ItemStack mat() {
        return real(inventory.getItem(MAT));
    }

    private static ItemStack real(ItemStack it) {
        return it == null || isFiller(it) ? null : it;
    }

    @Override
    public void render() {
        ItemStack keepItem = item(), keepMat = mat();
        inventory.clear();
        if (keepItem != null) inventory.setItem(ITEM, keepItem);
        if (keepMat != null) inventory.setItem(MAT, keepMat);
        ItemManager im = plugin.items();
        // marcos de los huecos
        for (int s : new int[]{10, 11, 12, 19, 21, 28, 29, 30}) inventory.setItem(s, icon(Material.ORANGE_STAINED_GLASS_PANE, "<gold>⬇ Objeto a mejorar", List.of("<gray>Arma, armadura o herramienta")));
        for (int s : new int[]{14, 15, 16, 23, 25, 32, 33, 34}) inventory.setItem(s, icon(Material.PURPLE_STAINED_GLASS_PANE, "<light_purple>⬇ Fragmento o runa", List.of("<gray>Fragmento de Mejora / Superior /", "<gray>Ancestral, o una Runa")));
        List<String> info = new ArrayList<>(List.of("<gray>Haz clic en un objeto de tu inventario", "<gray>para ponerlo en su hueco.",
                "<gray>Clic en un hueco para sacarlo.", "", "<white>Fragmentos:", "<gray>+1 a +5 · <light_purple>de Mejora",
                "<gray>+6 a +8 · <aqua>Superior <gray>(80 %)", "<gray>+9 y +10 · <light_purple>Ancestral <gray>(60 %)",
                "<dark_gray>Si falla, se pierde el fragmento, no el objeto.", "", "<white>Runas: <gray>dan poderes (cuestan niveles de XP).", "<gray>Hasta 5 por objeto, +1 a +5 y +1 a +10.", "<gray>Shift+clic en el botón: forja todas a la vez."));
        inventory.setItem(INFO, icon(Material.BOOK, "<gold>¿Cómo se usa el yunque?", info));
        inventory.setItem(GO, button());
        inventory.setItem(COMBINE, icon(Material.AMETHYST_CLUSTER, "<aqua>Juntar fragmentos",
                List.of("<gray>3 Fragmentos de Mejora → 1 Superior", "<gray>3 Superiores → 1 Ancestral", "", "<yellow>Clic: junta los que tengas en el inventario")));
        if (shop != null) inventory.setItem(BACK, icon(Material.ARROW, "<gray>◀ Volver a la tienda", List.of()));
        inventory.setItem(44, icon(Material.SUNFLOWER, "<yellow>Tus Soles: " + Txt.num(plugin.levels().soles(player)) + " ☀",
                List.of("<gray>Niveles de XP: " + player.getLevel())));
        fillEmpty();
    }

    private ItemStack button() {
        ItemManager im = plugin.items();
        ItemStack it = item(), m = mat();
        if (it == null) return icon(Material.GRAY_DYE, "<gray>Pon un objeto a la izquierda", List.of());
        if (m == null) return icon(Material.GRAY_DYE, "<gray>Pon un fragmento o una runa a la derecha", List.of());
        if (Powers.isRune(m)) {
            int have = Powers.runesOf(it.getItemMeta()).size(), max = plugin.powers().maxRunes(it);
            return icon(Material.ENCHANTING_TABLE, "<light_purple><bold>Forjar runa", List.of("<gray>Runas en el objeto: <white>" + have + " / " + max,
                    "<dark_gray>(+1 hueco al llegar a +5 y otro a +10)", "<gray>Cuesta niveles de experiencia", "",
                    "<yellow>Clic: forjar 1 · Shift+clic: forjar todas las de la derecha"));
        }
        ItemManager.UpKind k = im.upKind(it);
        if (k == null) return icon(Material.BARRIER, "<red>Eso no se puede mejorar", List.of("<gray>Armas, armaduras y herramientas"));
        int lvl = im.upgradeLevel(it), max = im.maxUpgrade();
        if (lvl >= max) return icon(Material.NETHER_STAR, "<gold>¡Ya está al máximo (+" + max + ")!", List.of());
        int t = lvl + 1;
        String need = ItemManager.fragmentFor(t);
        String have = im.idOf(m);
        var nd = im.def(need);
        List<String> l = new ArrayList<>();
        l.add("<white>+" + lvl + " → " + ItemManager.tierName(t) + " +" + t);
        l.addAll(ItemManager.upgradeLines(k, it.getType(), t));
        l.add("");
        l.add("<gray>Necesita: <white>1 " + (nd == null ? need : nd.name));
        l.add("<gray>Cuesta: <yellow>" + Txt.num(basePrice * t) + " ☀");
        l.add("<gray>Éxito: <white>" + Math.round(im.upgradeChance(t) * 100) + "%");
        if (!need.equals(have)) {
            l.add("");
            l.add("<red>El fragmento de la derecha no sirve para este nivel.");
            return icon(Material.RED_DYE, "<red>Falta el fragmento correcto", l);
        }
        l.add("");
        l.add("<yellow>Clic para mejorar");
        return icon(t >= 9 ? Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE : Material.ANVIL, "<gold><bold>Mejorar", l);
    }

    @Override
    public void onBottomClick(InventoryClickEvent e, int slot) {
        ItemStack it = player.getInventory().getItem(slot);
        if (it == null || it.getType().isAir()) return;
        ItemManager im = plugin.items();
        String id = im.idOf(it);
        boolean isMat = Powers.isRune(it) || (id != null && id.startsWith("fragmento_mejora"));
        int target = isMat ? MAT : ITEM;
        if (!isMat && !im.upgradable(it) && it.getMaxStackSize() > 1) {
            Txt.send(player, "<red>Eso no se puede mejorar.");
            return;
        }
        ItemStack old = real(inventory.getItem(target));
        if (old != null && old.isSimilar(it) && old.getAmount() < old.getMaxStackSize()) {
            int move = Math.min(it.getAmount(), old.getMaxStackSize() - old.getAmount());
            old.setAmount(old.getAmount() + move);
            it.setAmount(it.getAmount() - move);
            inventory.setItem(target, old);
            player.getInventory().setItem(slot, it.getAmount() <= 0 ? null : it);
        } else {
            inventory.setItem(target, it.clone());
            player.getInventory().setItem(slot, old);
        }
        Txt.sound(player, "block.anvil.place", 0.5f, 1.6f);
        render();
    }

    @Override
    public void onClick(InventoryClickEvent e, int slot) {
        if (slot == ITEM || slot == MAT) {
            ItemStack it = real(inventory.getItem(slot));
            inventory.setItem(slot, null);
            if (it == null) {
                render();
                return;
            }
            inventory.setItem(slot, null);
            give(it);
            render();
            return;
        }
        if (slot == BACK && shop != null) {
            returnAll();
            done = true;
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> plugin.shops().open(player, shop.id));
            return;
        }
        if (slot == COMBINE) {
            combine();
            render();
            return;
        }
        if (slot != GO) return;
        ItemStack it = item(), m = mat();
        if (it == null || m == null) return;
        if (Powers.isRune(m)) {
            int times = e.isShiftClick() ? m.getAmount() : 1, done = 0;
            for (int k = 0; k < times && m.getAmount() > 0; k++) {
                String err = plugin.powers().forge(player, it, m);
                if (err != null) {
                    Txt.send(player, err);
                    break;
                }
                done++;
            }
            if (done > 1) Txt.send(player, "<light_purple>Forjaste " + done + " runas.");
            inventory.setItem(MAT, m.getAmount() <= 0 ? null : m);
            inventory.setItem(ITEM, it);
            render();
            return;
        }
        upgrade(it, m);
        render();
    }

    private void upgrade(ItemStack it, ItemStack m) {
        ItemManager im = plugin.items();
        if (!im.upgradable(it)) {
            Txt.send(player, "<red>Eso no se puede mejorar.");
            return;
        }
        int lvl = im.upgradeLevel(it), max = im.maxUpgrade();
        if (lvl >= max) return;
        int t = lvl + 1;
        String need = ItemManager.fragmentFor(t);
        if (!need.equals(im.idOf(m))) {
            var nd = im.def(need);
            Txt.send(player, "<red>Para +" + t + " necesitas 1 " + (nd == null ? need : nd.name) + "<red>.");
            Txt.sound(player, "entity.villager.no", 1f, 1f);
            return;
        }
        long cost = basePrice * t;
        if (!plugin.levels().takeSoles(player, cost)) {
            Txt.send(player, "<red>Necesitas " + Txt.num(cost) + " ☀.");
            return;
        }
        m.setAmount(m.getAmount() - 1);
        inventory.setItem(MAT, m.getAmount() <= 0 ? null : m);
        if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() >= im.upgradeChance(t)) {
            Txt.send(player, "<red>✖ La mejora falló… el fragmento se quebró. <gray>(sigue en +" + lvl + ")");
            Txt.sound(player, "block.anvil.destroy", 1f, 1f);
            return;
        }
        im.upgrade(it, max);
        plugin.powers().refreshLore(it);
        inventory.setItem(ITEM, it);
        Txt.send(player, "<gold>¡Bruno lo dejó en " + ItemManager.tierName(t) + " +" + t + "<gold>!");
        Txt.sound(player, "block.anvil.use", 1f, t >= 9 ? 0.7f : t >= 6 ? 0.85f : 1f);
        if (t >= 6) Txt.sound(player, "ui.toast.challenge_complete", 0.6f, 1.2f);
        player.getWorld().spawnParticle(org.bukkit.Particle.WAX_OFF, player.getLocation().add(0, 1.2, 0), 30, 0.4, 0.5, 0.4, 0.5);
    }

    /** Junta 3 → 1 (de Mejora → Superior → Ancestral) con lo que tenga en el inventario. */
    private void combine() {
        ItemManager im = plugin.items();
        int made = 0;
        String[][] steps = {{"fragmento_mejora", "fragmento_mejora_superior"}, {"fragmento_mejora_superior", "fragmento_mejora_ancestral"}};
        for (String[] st : steps) {
            while (im.count(player, st[0]) >= 3) {
                im.take(player, st[0], 3);
                ItemStack out = im.create(st[1], 1);
                if (out != null) im.give(player, out);
                made++;
            }
        }
        if (made == 0) {
            Txt.send(player, "<gray>Necesitas 3 fragmentos iguales en tu inventario.");
            Txt.sound(player, "entity.villager.no", 1f, 1f);
        } else {
            Txt.send(player, "<aqua>Bruno fundió los fragmentos: " + made + " nuevo(s).");
            Txt.sound(player, "block.amethyst_block.resonate", 1f, 1f);
        }
    }

    private void give(ItemStack it) {
        for (ItemStack l : player.getInventory().addItem(it).values()) player.getWorld().dropItem(player.getLocation(), l);
    }

    private void returnAll() {
        for (int s : new int[]{ITEM, MAT}) {
            ItemStack it = real(inventory.getItem(s));
            inventory.setItem(s, null);
            if (it == null) continue;
            inventory.setItem(s, null);
            give(it);
        }
    }

    @Override
    public void onClose() {
        if (!done) returnAll();
    }
}
