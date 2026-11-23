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
        public int stock;            // 0 = sin límite
        public int restock = 20;     // minutos para reponer el stock
        public int quest;            // misiones completadas necesarias
        public String payWith;       // se paga con este objeto en vez de dinero (trueque)
        public String key;           // tienda:slot (para el stock)
        public String category;      // categoría elegida en tiendas.yml (si no, se adivina)
        public DailyArmor.Piece daily;   // armadura del día ya armada
    }

    public static final class Shop {
        public String id;
        public String title;
        public int rows = 4;
        public final List<Entry> entries = new ArrayList<>();
        public boolean rotating;
        public int show = 3;
        public int[] rotatingSlots = {11, 13, 15};
        public String opens = "siempre";   // siempre | dia | noche
        public String onlyEvent;           // solo abre durante este evento del mundo
        public int quest;                  // misiones completadas necesarias para entrar
        public double variation;           // 0.2 = los precios cambian ±20% cada día
        public String button;              // texto del botón en el NPC ("⚔ Armas y armaduras")
        public String hint;                // lo que se ve al pasar el mouse
    }

    /** Texto del botón del NPC para esta tienda: el que pusiste en "boton", o uno sacado del título. */
    public String buttonLabel(String id) {
        Shop s = shops.get(id);
        if (s == null) return "<green>🛒 Tienda";
        if (s.button != null && !s.button.isBlank()) return s.button.contains("<") ? s.button : "<green>" + s.button;
        String t = s.title == null ? id : s.title.replaceAll("<[^>]+>", "").trim();
        if (t.contains("·")) t = t.substring(t.lastIndexOf('·') + 1).trim();
        if (t.isEmpty()) t = Txt.pretty(id);
        String k = t.toLowerCase(Locale.ROOT);
        String icon = k.contains("runa") ? "✦" : k.contains("herramienta") || k.contains("pico") ? "⛏" : k.contains("arm") || k.contains("herrer") || k.contains("forja") ? "⚔"
                : k.contains("alquim") || k.contains("poci") || k.contains("bruja") ? "⚗" : k.contains("oscuro") || k.contains("negro") ? "☾"
                : k.contains("comida") || k.contains("posada") || k.contains("cocina") ? "🍖" : k.contains("pesca") || k.contains("mar") ? "🎣" : "🛒";
        return "<green>" + icon + " <white>" + t;
    }

    public String buttonHint(String id) {
        Shop s = shops.get(id);
        return s != null && s.hint != null && !s.hint.isBlank() ? s.hint : "Comprar y vender";
    }

    private final EclipseRPG plugin;
    private final Map<String, Shop> shops = new LinkedHashMap<>();
    private final ItemValues values;

    public ItemValues values() {
        return values;
    }

    /** ¿Esta tienda compra cualquier objeto? (por defecto la tienda general de Pip) */
    public boolean buysAnything(Shop s) {
        if (s == null) return false;
        List<String> l = plugin.getConfig().getStringList("tiendas.compran-todo");
        return l.isEmpty() ? s.id.equals("general") : l.contains(s.id);
    }

    /** Qué novedades del día trae cada vendedor: tienda → ["tipo:cantidad", …] (config tiendas.novedades). */
    private List<String> dailyKinds(Shop s) {
        var sec = plugin.getConfig().getConfigurationSection("tiendas.novedades");
        if (sec != null) return sec.getStringList(s.id);
        return switch (s.id) {
            case "general" -> List.of("armaduras:5", "curiosidades:3");
            case "herreria" -> List.of("armas:4", "herramientas:2");
            case "alquimia" -> List.of("pociones:4", "libros:2");
            case "trueque" -> List.of("reliquias:2");
            default -> List.of();
        };
    }

    /** Las novedades de hoy de esta tienda (cambian a medianoche, iguales para todos, una de cada una). */
    public List<Entry> dailyArmor(Shop s) {
        List<Entry> out = new ArrayList<>();
        if (s == null || s.rotating) return out;
        ZoneId zone = ZoneId.of(plugin.getConfig().getString("zona-horaria", "America/Lima"));
        long day = LocalDate.now(zone).toEpochDay();
        for (String spec : dailyKinds(s)) {
            String[] a = spec.split(":");
            String kind = a[0].trim().toLowerCase(Locale.ROOT);
            int n = a.length > 1 ? Math.max(1, Math.min(9, intOf(a[1], 1))) : 3;
            for (int i = 0; i < n; i++) {
                DailyArmor.Piece pc = DailyGoods.make(kind, day, i);
                if (pc == null) continue;
                // estilo único del paquete de texturas (el mismo para todos ese día)
                if (plugin.resourcePack() != null && !kind.equals("pociones") && !kind.equals("libros"))
                    plugin.resourcePack().decorate(pc.item(), new Random(day * 92821L + i * 31L + (s.id + kind).hashCode()));
                Entry e = new Entry();
                e.kind = Kind.COMPRAR;
                e.item = "diario:" + kind + ":" + day + ":" + i;
                e.price = pc.price();
                e.amount = pc.item().getAmount();
                e.minLevel = pc.level();
                e.stock = 1;
                e.restock = 24 * 60;
                e.key = s.id + ":dia:" + kind + ":" + day + ":" + i;
                e.category = "novedades";
                e.daily = pc;
                if (kind.equals("reliquias")) e.payWith = "custom:corazon_corrupto";
                out.add(e);
            }
        }
        return out;
    }

    public ShopManager(EclipseRPG plugin) {
        this.plugin = plugin;
        this.values = new ItemValues(plugin);
    }

    public void load() {
        shops.clear();
        values.reset();
        YamlConfiguration y = plugin.story().yaml("tiendas");
        ConfigurationSection root = y.getConfigurationSection("tiendas");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            Shop shop = new Shop();
            shop.id = id;
            shop.title = s.getString("titulo", id);
            shop.button = s.getString("boton");
            shop.hint = s.getString("descripcion");
            shop.rows = Math.max(1, Math.min(6, s.getInt("filas", 4)));
            shop.rotating = s.getBoolean("rotativa", false);
            shop.show = s.getInt("mostrar", 3);
            shop.opens = s.getString("abre", "siempre").toLowerCase(Locale.ROOT);
            String oe = s.getString("solo-evento");
            shop.onlyEvent = oe == null || oe.isBlank() ? null : oe.toLowerCase(Locale.ROOT);
            shop.quest = s.getInt("mision", 0);
            shop.variation = Math.max(0, Math.min(0.9, s.getDouble("variacion", 0)));
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
                e.stock = intOf(m.get("stock"), 0);
                e.restock = Math.max(1, intOf(m.get("reabastece"), 20));
                e.quest = intOf(m.get("mision"), 0);
                e.payWith = m.get("paga-con") == null || m.get("paga-con").toString().isBlank() ? null : m.get("paga-con").toString();
                e.key = id + ":" + e.slot;
                e.category = m.get("categoria") == null ? null : m.get("categoria").toString().toLowerCase(Locale.ROOT);
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

    public Shop shop(String id) {
        return shops.get(id);
    }

    public boolean open(Player p, String id) {
        Shop s = shops.get(id);
        if (s == null) return false;
        String closed = closedReason(p, s);
        if (closed != null) {
            Txt.send(p, closed);
            Txt.sound(p, "block.chest.locked", 1f, 1f);
            return true;
        }
        new ShopMenu(plugin, p, s, visibleEntries(s)).open();
        return true;
    }

    /** Por qué la tienda está cerrada para este jugador (null = abierta). */
    public String closedReason(Player p, Shop s) {
        boolean night = pe.kydium.eclipserpg.world.WorldManager.isNight(p.getWorld());
        if (s.opens.equals("dia") && night) return "<red>Esta tienda solo abre de día. Vuelve cuando amanezca.";
        if (s.opens.equals("noche") && !night) return "<red>Esta tienda solo abre de noche.";
        if (s.onlyEvent != null && !plugin.world().activeEvents().contains(s.onlyEvent))
            return "<red>Esta tienda solo abre durante <reset>" + plugin.world().eventName(s.onlyEvent) + "<red>.";
        if (s.quest > 0 && plugin.data().get(p).questIndex < s.quest)
            return "<red>Aún no confían en ti aquí. Avanza en la historia (misión " + s.quest + ").";
        return null;
    }

    // ---------- stock y precios ----------

    private final Map<String, int[]> stock = new java.util.HashMap<>();     // key -> {vendidos}
    private final Map<String, Long> stockSince = new java.util.HashMap<>();

    /** Unidades que quedan (Integer.MAX_VALUE si no tiene límite). */
    public int stockLeft(Entry e) {
        if (e.stock <= 0 || e.key == null) return Integer.MAX_VALUE;
        Long since = stockSince.get(e.key);
        if (since != null && System.currentTimeMillis() - since >= e.restock * 60_000L) {
            stock.remove(e.key);
            stockSince.remove(e.key);
        }
        int[] sold = stock.get(e.key);
        return Math.max(0, e.stock - (sold == null ? 0 : sold[0]));
    }

    public void useStock(Entry e) {
        if (e.stock <= 0 || e.key == null) return;
        stock.computeIfAbsent(e.key, k -> new int[1])[0]++;
        stockSince.putIfAbsent(e.key, System.currentTimeMillis());
    }

    /** Minutos que faltan para reponer. */
    public long restockMinutes(Entry e) {
        Long since = e.key == null ? null : stockSince.get(e.key);
        if (since == null) return 0;
        return Math.max(1, (e.restock * 60_000L - (System.currentTimeMillis() - since) + 59_999L) / 60_000L);
    }

    /** Precio de hoy (si la tienda tiene variación de precios, cambia cada día). */
    public long priceOf(Shop s, Entry e) {
        if (s == null || s.variation <= 0 || e.payWith != null || e.kind == Kind.MEJORA) return e.price;
        ZoneId zone = ZoneId.of(plugin.getConfig().getString("zona-horaria", "America/Lima"));
        long day = LocalDate.now(zone).toEpochDay();
        double f = new Random(day * 131 + (s.id + e.item + e.slot).hashCode()).nextDouble() * 2 - 1;
        return Math.max(1, Math.round(e.price * (1 + f * s.variation)));
    }

    /** Cuántos tiene el jugador del objeto con el que se paga (trueque). */
    public int countSpec(Player p, String spec) {
        String rest = spec.startsWith("material:") ? spec.substring(9) : spec;
        if (rest.startsWith("custom:")) return plugin.items().count(p, rest.substring(7));
        Material m = Material.matchMaterial(rest);
        if (m == null) return 0;
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (it != null && it.getType() == m && plugin.items().idOf(it) == null) n += it.getAmount();
        return n;
    }

    public void takeSpec(Player p, String spec, int amount) {
        String rest = spec.startsWith("material:") ? spec.substring(9) : spec;
        if (rest.startsWith("custom:")) {
            plugin.items().take(p, rest.substring(7), amount);
            return;
        }
        Material m = Material.matchMaterial(rest);
        if (m == null) return;
        int left = amount;
        ItemStack[] inv = p.getInventory().getStorageContents();
        for (int i = 0; i < inv.length && left > 0; i++) {
            ItemStack it = inv[i];
            if (it == null || it.getType() != m || plugin.items().idOf(it) != null) continue;
            int take = Math.min(left, it.getAmount());
            it.setAmount(it.getAmount() - take);
            left -= take;
            inv[i] = it.getAmount() <= 0 ? null : it;
        }
        p.getInventory().setStorageContents(inv);
    }

    public String specName(String spec) {
        String rest = spec.startsWith("material:") ? spec.substring(9) : spec;
        if (rest.startsWith("custom:")) {
            var d = plugin.items().def(rest.substring(7));
            return d == null ? rest.substring(7) : pe.kydium.eclipserpg.story.StoryManager.plain(d.name);
        }
        if (rest.startsWith("runa:")) {
            String t = pe.kydium.eclipserpg.items.Powers.normType(rest.substring(5).split(":")[0]);
            return t == null ? "Runa al azar" : "Runa de " + pe.kydium.eclipserpg.story.StoryManager.plain(pe.kydium.eclipserpg.items.Powers.TYPES.get(t)[0]).replaceAll("^[^A-Za-zÁÉÍÓÚáéíóúñ]+", "");
        }
        if (rest.startsWith("especial:")) return "Arma especial " + rest.substring(9);
        if (rest.startsWith("minimapa") || rest.startsWith("mapa:")) return "Mapa del aventurero";
        return Txt.pretty(rest);
    }

    /** En tiendas rotativas se muestran unos pocos objetos que cambian cada día. */
    public List<Entry> visibleEntries(Shop s) {
        if (!s.rotating) {
            List<Entry> daily = dailyArmor(s);
            if (daily.isEmpty()) return s.entries;
            List<Entry> all = new ArrayList<>(s.entries);
            all.addAll(daily);
            return all;
        }
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
            copy.stock = src.stock;
            copy.restock = src.restock;
            copy.quest = src.quest;
            copy.payWith = src.payWith;
            copy.key = src.key;
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
            case "runa" -> {
                it = pe.kydium.eclipserpg.items.Powers.rune(rest);
                it.setAmount(Math.max(1, e.amount));
            }
            case "especial" -> {
                // un arma/armadura especial al azar de esa rareza o mayor (por defecto épica)
                List<String> ids = plugin.items().ofRarity(rest.isBlank() ? "epico" : rest);
                if (!ids.isEmpty()) it = plugin.items().create(ids.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(ids.size())), 1);
            }
            case "minimapa", "mapa" -> it = plugin.minimap() == null ? null : plugin.minimap().item();
            case "armadura-dia" -> {
                String[] p = rest.split(":");
                try {
                    it = DailyArmor.make(Long.parseLong(p[0]), Integer.parseInt(p[1])).item();
                } catch (RuntimeException ex) {
                    it = null;
                }
            }
            case "diario" -> {
                String[] p = rest.split(":");
                try {
                    DailyArmor.Piece pc = DailyGoods.make(p[0], Long.parseLong(p[1]), Integer.parseInt(p[2]));
                    it = pc == null ? null : pc.item();
                } catch (RuntimeException ex) {
                    it = null;
                }
            }
            case "bloque" -> it = plugin.specialBlocks() == null ? null : plugin.specialBlocks().item(rest, Math.max(1, e.amount));
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

    /** Construye un objeto desde texto: "material:X", "custom:id", "pocion:tipo", "libro:ench:nivel" + cantidad. */
    public ItemStack buildSpec(String spec, int amount) {
        Entry e = new Entry();
        e.item = spec;
        e.amount = Math.max(1, amount);
        ItemStack it = build(e);
        if (it != null && !spec.startsWith("custom:") && !spec.startsWith("especial:") && !spec.startsWith("bloque:") && !spec.startsWith("minimapa")) it.setAmount(Math.min(it.getMaxStackSize(), e.amount));
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
