package pe.kydium.eclipserpg.items;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.util.Keys;
import pe.kydium.eclipserpg.util.Txt;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mochilas: objetos de objetos.yml con "mochila: <huecos>". Cualquier tamaño (9, 27, 54, 200, 1000…):
 * hasta 54 huecos es una sola ventana; con más, páginas de 45 huecos con flechas abajo.
 * Lo guardado vive en plugins/AventuraMaker/mochilas/<id>.dat (el objeto solo lleva su id).
 */
public final class Backpacks implements Listener {

    private static final int PAGE = 45;

    private final EclipseRPG plugin;
    private final NamespacedKey BAG;
    private final Map<UUID, Holder> open = new HashMap<>(); // id de la mochila → ventana abierta

    public Backpacks(EclipseRPG plugin) {
        this.plugin = plugin;
        this.BAG = Keys.k("mochila_id");
    }

    final class Holder implements InventoryHolder {
        final UUID bag;
        final int slots;
        final String title;
        final ItemStack[] content;
        int page;
        boolean turning;
        Inventory inv;
        UUID viewer;

        Holder(UUID bag, int slots, String title, ItemStack[] content) {
            this.bag = bag;
            this.slots = slots;
            this.title = title;
            this.content = content;
        }

        boolean paged() {
            return slots > 54;
        }

        int pages() {
            return paged() ? (slots + PAGE - 1) / PAGE : 1;
        }

        /** Hueco de la ventana → índice en la mochila (o -1 si es borde / botón / hueco bloqueado). */
        int index(int raw) {
            if (paged()) {
                if (raw >= PAGE) return -1;
                int i = page * PAGE + raw;
                return i < slots ? i : -1;
            }
            return raw < slots ? raw : -1;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    public int size(ItemStack it) {
        String id = it == null ? null : plugin.items().idOf(it);
        ItemManager.CustomItem ci = id == null ? null : plugin.items().def(id);
        return ci == null ? 0 : ci.backpack;
    }

    private UUID bagId(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        String s = it.getItemMeta().getPersistentDataContainer().get(BAG, PersistentDataType.STRING);
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private File file(UUID id) {
        return new File(plugin.getDataFolder(), "mochilas/" + id + ".dat");
    }

    private ItemStack[] load(UUID id, int slots) {
        ItemStack[] out = new ItemStack[slots];
        File f = file(id);
        if (!f.exists()) return out;
        try {
            ItemStack[] saved = ItemStack.deserializeItemsFromBytes(Files.readAllBytes(f.toPath()));
            for (int i = 0; i < saved.length; i++) {
                if (saved[i] == null || saved[i].getType().isAir()) continue;
                if (i < slots) out[i] = saved[i];
                else spill(out, saved[i]);
            }
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("No se pudo leer la mochila " + id + ": " + e.getMessage());
        }
        return out;
    }

    /** Si la mochila se hizo más chica, lo que no entra busca otro hueco libre (o se pierde en el registro). */
    private static void spill(ItemStack[] out, ItemStack it) {
        for (int i = 0; i < out.length; i++) if (out[i] == null) {
            out[i] = it;
            return;
        }
    }

    private void save(Holder h) {
        try {
            File f = file(h.bag);
            Files.createDirectories(f.getParentFile().toPath());
            ItemStack[] copy = Arrays.stream(h.content).map(i -> i == null ? ItemStack.empty() : i).toArray(ItemStack[]::new);
            Files.write(f.toPath(), ItemStack.serializeItemsAsBytes(copy));
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("No se pudo guardar la mochila " + h.bag + ": " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- abrir

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        int slots = size(it);
        if (slots <= 0) return;
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null && e.getClickedBlock().getType().isInteractable()
                && !e.getPlayer().isSneaking()) return; // dejar abrir puertas, cofres…
        e.setCancelled(true);
        openBag(e.getPlayer(), it, slots);
    }

    public void openBag(Player p, ItemStack it, int slots) {
        UUID id = bagId(it);
        if (id == null) {
            id = UUID.randomUUID();
            var meta = it.getItemMeta();
            meta.getPersistentDataContainer().set(BAG, PersistentDataType.STRING, id.toString());
            it.setItemMeta(meta);
        }
        if (open.containsKey(id)) {
            Txt.send(p, "<red>Esa mochila ya está abierta.");
            return;
        }
        String title = it.getItemMeta().hasDisplayName() ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(it.getItemMeta().displayName()) : "Mochila";
        Holder h = new Holder(id, slots, title, load(id, slots));
        h.viewer = p.getUniqueId();
        open.put(id, h);
        show(p, h);
        p.playSound(p.getLocation(), Sound.ITEM_BUNDLE_INSERT, 0.8f, 0.9f);
    }

    private void show(Player p, Holder h) {
        int rows = h.paged() ? 6 : Math.max(1, (h.slots + 8) / 9);
        String t = "<dark_gray>🎒 " + h.title + (h.paged() ? " <gray>(" + (h.page + 1) + "/" + h.pages() + ")" : "");
        Inventory inv = Bukkit.createInventory(h, rows * 9, Txt.mm(t));
        h.inv = inv;
        for (int raw = 0; raw < rows * 9; raw++) {
            int i = h.index(raw);
            if (i >= 0) inv.setItem(raw, h.content[i]);
            else inv.setItem(raw, filler(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
        if (h.paged()) {
            for (int raw = PAGE; raw < 54; raw++) inv.setItem(raw, filler(Material.BLACK_STAINED_GLASS_PANE, " "));
            if (h.page > 0) inv.setItem(45, filler(Material.ARROW, "<yellow>◀ Página anterior"));
            if (h.page < h.pages() - 1) inv.setItem(53, filler(Material.ARROW, "<yellow>Página siguiente ▶"));
            ItemStack info = filler(Material.BUNDLE, "<gold>Página " + (h.page + 1) + " de " + h.pages());
            var m = info.getItemMeta();
            m.lore(Txt.lore(List.of("<gray>" + h.slots + " huecos en total", "<gray>Usa las flechas para cambiar de página")));
            info.setItemMeta(m);
            inv.setItem(49, info);
        }
        p.openInventory(inv);
    }

    private ItemStack filler(Material m, String name) {
        ItemStack it = new ItemStack(m);
        var meta = it.getItemMeta();
        meta.displayName(Txt.item(name));
        meta.getPersistentDataContainer().set(BAG, PersistentDataType.STRING, "borde");
        meta.setHideTooltip(name.isBlank());
        it.setItemMeta(meta);
        return it;
    }

    private boolean isFiller(ItemStack it) {
        return it != null && it.hasItemMeta() && "borde".equals(it.getItemMeta().getPersistentDataContainer().get(BAG, PersistentDataType.STRING));
    }

    /** Copia lo que hay en la ventana a la mochila (página actual). */
    private void sync(Holder h) {
        if (h.inv == null) return;
        for (int raw = 0; raw < h.inv.getSize(); raw++) {
            int i = h.index(raw);
            if (i < 0) continue;
            ItemStack it = h.inv.getItem(raw);
            h.content[i] = it == null || it.getType().isAir() ? null : it;
        }
    }

    // ---------------------------------------------------------------- dentro de la ventana

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) {
            return;
        }
        Player p = (Player) e.getWhoClicked();
        ItemStack cur = e.getCurrentItem(), cursor = e.getCursor();
        // la mochila abierta no se mueve, y no se meten mochilas dentro de mochilas
        if (h.bag.equals(bagId(cur)) || h.bag.equals(bagId(cursor))) {
            e.setCancelled(true);
            return;
        }
        if (e.getClick() == ClickType.NUMBER_KEY) {
            ItemStack hb = p.getInventory().getItem(e.getHotbarButton());
            if (h.bag.equals(bagId(hb)) || (e.getClickedInventory() == e.getView().getTopInventory() && size(hb) > 0)) {
                e.setCancelled(true);
                return;
            }
        }
        if (e.getClick() == ClickType.SWAP_OFFHAND && size(p.getInventory().getItemInOffHand()) > 0) {
            e.setCancelled(true);
            return;
        }
        boolean top = e.getClickedInventory() == e.getView().getTopInventory();
        if (top) {
            if (h.index(e.getRawSlot()) < 0) {
                e.setCancelled(true);
                if (h.paged() && e.getRawSlot() == 45 && h.page > 0) turn(p, h, -1);
                else if (h.paged() && e.getRawSlot() == 53 && h.page < h.pages() - 1) turn(p, h, 1);
                return;
            }
            if (size(cursor) > 0 && e.getAction() != InventoryAction.PICKUP_ALL && e.getAction() != InventoryAction.PICKUP_HALF) {
                e.setCancelled(true);
                Txt.send(p, "<red>No puedes guardar una mochila dentro de otra.");
                return;
            }
        } else if (e.isShiftClick()) {
            if (size(cur) > 0 || isFiller(cur) || cur != null && cur.getType().name().endsWith("STAINED_GLASS_PANE")) {
                e.setCancelled(true);
                if (size(cur) > 0) Txt.send(p, "<red>No puedes guardar una mochila dentro de otra.");
                return;
            }
        }
        if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) e.setCancelled(true); // doble clic juntaría los bordes
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        int topSize = e.getView().getTopInventory().getSize();
        for (int raw : e.getRawSlots()) {
            if (raw < topSize && (h.index(raw) < 0 || size(e.getOldCursor()) > 0)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    private void turn(Player p, Holder h, int d) {
        sync(h);
        h.page += d;
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1f);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            h.turning = true;
            show(p, h);
            h.turning = false;
        });
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h) || h.inv != e.getInventory()) return;
        sync(h);
        save(h);
        if (h.turning) return;
        open.remove(h.bag);
        if (e.getPlayer() instanceof Player p) p.playSound(p.getLocation(), Sound.ITEM_BUNDLE_DROP_CONTENTS, 0.6f, 1.1f);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        UUID id = bagId(e.getItemDrop().getItemStack());
        if (id != null && open.containsKey(id)) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        HumanEntity p = e.getPlayer();
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof Holder h) {
            sync(h);
            save(h);
            open.remove(h.bag);
        }
    }

    public void closeAll() {
        for (Holder h : new ArrayList<>(open.values())) {
            sync(h);
            save(h);
            Player p = Bukkit.getPlayer(h.viewer);
            if (p != null) p.closeInventory();
        }
        open.clear();
    }
}
