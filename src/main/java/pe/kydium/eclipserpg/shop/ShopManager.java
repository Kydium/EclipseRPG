package pe.kydium.eclipserpg.shop;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.util.Reg;
import pe.kydium.eclipserpg.util.Txt;

import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Tiendas de los NPCs definidas en shops.yml. */
public final class ShopManager {

    public enum Kind { COMPRAR, VENDER, MEJORA }

    public static final class Entry {
        public Kind kind = Kind.COMPRAR;
        public int slot;
        public String item;          // material:X | custom:id | pocion:tipo | pocion-arrojadiza:tipo | libro:ench:nivel
        public int amount = 1;
        public long price;
        public int minLevel = 1;
        public String name;          // nombre visible opcional
        public final Map<String, Integer> enchants = new LinkedHashMap<>();
    }

    public static final class Shop {
        public String id;
        public String title;
        public int rows = 4;
        public final List<Entry> entries = new ArrayList<>();
        public boolean rotating;
        public int show = 3;
        public int[] rotatingSlots = {11, 13, 15};
    }

    private final EclipseRPG plugin;
    private final Map<String, Shop> shops = new LinkedHashMap<>();

    public ShopManager(EclipseRPG plugin) {
        this.plugin = plugin;
    }

    public void load() {
        shops.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "shops.yml"));
        ConfigurationSection root = y.getConfigurationSection("tiendas");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            Shop shop = new Shop();
            shop.id = id;
            shop.title = s.getString("titulo", id);
            shop.rows = Math.max(1, Math.min(6, s.getInt("filas", 4)));
            shop.rotating = s.getBoolean("rotativa", false);
            shop.show = s.getInt("mostrar", 3);
            List<Integer> rs = s.getIntegerList("slots-rotativos");
            if (!rs.isEmpty()) shop.rotatingSlots = rs.stream().mapToInt(Integer::intValue).toArray();
            for (Map<?, ?> m : s.getMapList("objetos")) {
                Entry e = new Entry();
                Object tipo = m.get("tipo");
                if (tipo != null) {
                    try {
                        e.kind = Kind.valueOf(tipo.toString().toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ex) {
                        plugin.getLogger().warning("shops.yml: tipo desconocido " + tipo + " en " + id);
                        continue;
                    }
                }
                e.slot = intOf(m.get("slot"), 0);
                e.item = m.get("item") == null ? null : m.get("item").toString();
                e.amount = intOf(m.get("cantidad"), 1);
                e.price = intOf(m.get("precio"), 0);
                e.minLevel = intOf(m.get("nivel"), 1);
                e.name = m.get("nombre") == null ? null : m.get("nombre").toString();
                Object en = m.get("encantamientos");
                if (en instanceof Map<?, ?> em) {
                    for (Map.Entry<?, ?> x : em.entrySet()) e.enchants.put(x.getKey().toString(), intOf(x.getValue(), 1));
                } else if (en instanceof ConfigurationSection es) {
                    for (String k : es.getKeys(false)) e.enchants.put(k, es.getInt(k));
                }
                shop.entries.add(e);
            }
            shops.put(id, shop);
        }
        plugin.getLogger().info("Tiendas cargadas: " + shops.size());
    }

    private static int intOf(Object o, int def) {
        if (o == null) return def;
        try {
            return (int) Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public Set<String> ids() {
        return shops.keySet();
    }

    public boolean open(Player p, String id) {
        Shop s = shops.get(id);
        if (s == null) return false;
        new ShopMenu(plugin, p, s, visibleEntries(s)).open();
        return true;
    }

    /** En tiendas rotativas se muestran unos pocos objetos que cambian cada día. */
    public List<Entry> visibleEntries(Shop s) {
        if (!s.rotating) return s.entries;
        ZoneId zone = ZoneId.of(plugin.getConfig().getString("zona-horaria", "America/Lima"));
        long day = LocalDate.now(zone).toEpochDay();
        List<Entry> pool = new ArrayList<>(s.entries);
        Collections.shuffle(pool, new Random(day * 31 + s.id.hashCode()));
        List<Entry> out = new ArrayList<>();
        for (int i = 0; i < Math.min(s.show, pool.size()) && i < s.rotatingSlots.length; i++) {
            Entry src = pool.get(i);
            Entry copy = new Entry();
            copy.kind = src.kind;
            copy.slot = s.rotatingSlots[i];
            copy.item = src.item;
            copy.amount = src.amount;
            copy.price = src.price;
            copy.minLevel = src.minLevel;
            copy.name = src.name;
            copy.enchants.putAll(src.enchants);
            out.add(copy);
        }
        return out;
    }

    /** Construye el objeto que se vende/compra. */
    public ItemStack build(Entry e) {
        if (e.item == null) return null;
        String spec = e.item;
        String type = spec.contains(":") ? spec.substring(0, spec.indexOf(':')).toLowerCase(Locale.ROOT) : "material";
        String rest = spec.contains(":") ? spec.substring(spec.indexOf(':') + 1) : spec;
        ItemStack it = null;
        switch (type) {
            case "custom" -> it = plugin.items().create(rest, e.amount);
            case "material" -> {
                Material m = Material.matchMaterial(rest);
                if (m != null) it = new ItemStack(m, e.amount);
            }
            case "pocion", "pocion-arrojadiza", "pocion-persistente" -> {
                Material m = switch (type) {
                    case "pocion-arrojadiza" -> Material.SPLASH_POTION;
                    case "pocion-persistente" -> Material.LINGERING_POTION;
                    default -> Material.POTION;
                };
                PotionType pt = Reg.potion(rest);
                it = new ItemStack(m, e.amount);
                if (pt != null && it.getItemMeta() instanceof PotionMeta pm) {
                    pm.setBasePotionType(pt);
                    it.setItemMeta(pm);
                }
            }
            case "libro" -> {
                String[] parts = rest.split(":");
                Enchantment ench = Reg.enchant(parts[0]);
                int lvl = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                it = new ItemStack(Material.ENCHANTED_BOOK, 1);
                if (ench != null && it.getItemMeta() instanceof EnchantmentStorageMeta bm) {
                    bm.addStoredEnchant(ench, lvl, true);
                    it.setItemMeta(bm);
                }
            }
            default -> { }
        }
        if (it == null) return null;
        if (!e.enchants.isEmpty()) {
            ItemMeta meta = it.getItemMeta();
            if (meta != null) {
                for (Map.Entry<String, Integer> en : e.enchants.entrySet()) {
                    Enchantment ench = Reg.enchant(en.getKey());
                    if (ench != null) meta.addEnchant(ench, en.getValue(), true);
                }
                it.setItemMeta(meta);
            }
        }
        return it;
    }

    /** Material simple para ventas (el jugador entrega ese material). */
    public Material sellMaterial(Entry e) {
        if (e.item == null) return null;
        String rest = e.item.startsWith("material:") ? e.item.substring(9) : e.item;
        return Material.matchMaterial(rest);
    }

    public String priceText(long price) {
        return Txt.num(price) + " ☀";
    }
}
