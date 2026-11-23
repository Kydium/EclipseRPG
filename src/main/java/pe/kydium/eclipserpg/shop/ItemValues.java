package pe.kydium.eclipserpg.shop;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.items.ItemManager;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Cuánto paga el mercader por cualquier objeto (en Soles). Nunca más que una parte de lo que cuesta
 * comprarlo en las tiendas, para que no se pueda ganar dinero comprando y vendiendo.
 */
public final class ItemValues {

    private static final Map<Material, Double> FIXED = new HashMap<>();

    static {
        put(40, "DIAMOND");
        put(15, "EMERALD");
        put(300, "NETHERITE_INGOT");
        put(70, "NETHERITE_SCRAP", "ANCIENT_DEBRIS");
        put(6, "GOLD_INGOT");
        put(4, "IRON_INGOT", "RAW_GOLD");
        put(3, "RAW_IRON");
        put(1, "COPPER_INGOT", "COAL");
        put(0.7, "RAW_COPPER", "CHARCOAL");
        put(1.5, "LAPIS_LAZULI", "QUARTZ");
        put(0.6, "REDSTONE", "GLOWSTONE_DUST");
        put(2, "AMETHYST_SHARD", "GUNPOWDER", "LEATHER", "SLIME_BALL", "PRISMARINE_CRYSTALS");
        put(0.5, "GOLD_NUGGET", "IRON_NUGGET", "FEATHER", "FLINT", "CLAY_BALL", "PRISMARINE_SHARD", "INK_SAC", "HONEYCOMB");
        put(8, "ENDER_PEARL", "ECHO_SHARD");
        put(10, "BLAZE_ROD", "ENDER_EYE", "BREEZE_ROD");
        put(20, "GHAST_TEAR", "DRAGON_BREATH");
        put(6, "PHANTOM_MEMBRANE", "MAGMA_CREAM", "RABBIT_FOOT", "TURTLE_SCUTE", "ARMADILLO_SCUTE");
        put(0.8, "STRING", "BONE", "SPIDER_EYE", "SUGAR_CANE", "PAPER", "BOOK");
        put(0.3, "ROTTEN_FLESH", "EGG", "ARROW", "STICK", "WHEAT_SEEDS", "BONE_MEAL");
        put(3, "EXPERIENCE_BOTTLE", "GLOW_INK_SAC");
        put(25, "NAUTILUS_SHELL", "SHULKER_SHELL", "NAME_TAG", "SADDLE");
        put(40, "GOLDEN_APPLE");
        put(500, "ENCHANTED_GOLDEN_APPLE", "TOTEM_OF_UNDYING");
        put(300, "HEART_OF_THE_SEA", "TRIDENT", "HEAVY_CORE");
        put(1500, "ELYTRA", "NETHER_STAR");
        put(150, "MACE");
        put(30, "TRIAL_KEY", "DIAMOND_HORSE_ARMOR");
        put(120, "OMINOUS_TRIAL_KEY");
        put(10, "IRON_HORSE_ARMOR", "GOLDEN_HORSE_ARMOR");
        put(1, "BREAD", "COOKED_BEEF", "COOKED_PORKCHOP", "COOKED_MUTTON", "COOKED_CHICKEN", "COOKED_SALMON", "COOKED_COD", "BAKED_POTATO", "PUMPKIN_PIE");
        put(0.4, "WHEAT", "CARROT", "POTATO", "BEETROOT", "APPLE", "MELON_SLICE", "SWEET_BERRIES", "COCOA_BEANS", "PUMPKIN", "MELON");
        put(5, "GOLDEN_CARROT");
        // bloques de recursos = 9 lingotes
        block("DIAMOND_BLOCK", "DIAMOND", 9);
        block("EMERALD_BLOCK", "EMERALD", 9);
        block("NETHERITE_BLOCK", "NETHERITE_INGOT", 9);
        block("GOLD_BLOCK", "GOLD_INGOT", 9);
        block("IRON_BLOCK", "IRON_INGOT", 9);
        block("COPPER_BLOCK", "COPPER_INGOT", 9);
        block("COAL_BLOCK", "COAL", 9);
        block("LAPIS_BLOCK", "LAPIS_LAZULI", 9);
        block("REDSTONE_BLOCK", "REDSTONE", 9);
        block("RAW_IRON_BLOCK", "RAW_IRON", 9);
        block("RAW_GOLD_BLOCK", "RAW_GOLD", 9);
        block("RAW_COPPER_BLOCK", "RAW_COPPER", 9);
        block("QUARTZ_BLOCK", "QUARTZ", 4);
    }

    private static void block(String block, String unit, int n) {
        Material b = Material.matchMaterial(block), u = Material.matchMaterial(unit);
        if (b != null && u != null && FIXED.containsKey(u)) FIXED.put(b, FIXED.get(u) * n);
    }

    private static void put(double v, String... names) {
        for (String n : names) {
            Material m = Material.matchMaterial(n);
            if (m != null) FIXED.put(m, v);
        }
    }

    private final EclipseRPG plugin;
    private Map<Material, Double> buyCaps;

    public ItemValues(EclipseRPG plugin) {
        this.plugin = plugin;
    }

    /** Se vuelve a calcular al recargar las tiendas. */
    public void reset() {
        buyCaps = null;
    }

    /** Por qué no se puede vender (null = se puede). */
    public String refuse(ItemStack it) {
        if (it == null || it.getType().isAir() || pe.kydium.eclipserpg.menu.Menu.isFiller(it)) return "";
        String id = plugin.items().idOf(it);
        if (id != null) {
            var ci = plugin.items().def(id);
            if (ci == null) return "<red>Pip no reconoce ese objeto.";
            if (ci.backpack > 0) return "<red>Las mochilas no se venden (perderías lo que llevan dentro).";
            if (customValue(id, ci) <= 0) return "<red>Pip no compra eso: es importante para la historia.";
        }
        ItemMeta meta = it.getItemMeta();
        if (meta instanceof BlockStateMeta bsm && bsm.hasBlockState() && bsm.getBlockState() instanceof org.bukkit.block.Container c && !c.getInventory().isEmpty())
            return "<red>Vacía la caja antes de venderla.";
        if (meta instanceof BundleMeta bm && bm.hasItems()) return "<red>Vacía el saco antes de venderlo.";
        return null;
    }

    /** Valor de toda la pila (puede ser 0 para cosas sin valor, como tierra). */
    public double value(ItemStack it) {
        if (it == null || it.getType().isAir() || refuse(it) != null) return 0;
        double each = unit(it);
        return each * it.getAmount() * plugin.getConfig().getDouble("tiendas.venta-general.multiplicador", 1.0);
    }

    private double customValue(String id, ItemManager.CustomItem ci) {
        switch (id) {
            case "fragmento_mejora" -> { return 30; }
            case "fragmento_mejora_superior" -> { return 120; }
            case "fragmento_mejora_ancestral" -> { return 450; }
            case "esencia_corrupta" -> { return 4; }
            case "corazon_corrupto" -> { return 40; }
            default -> { }
        }
        String rk = ItemManager.rarityKey(ci);
        if (rk == null) return 0;
        return switch (rk) {
            case "comun" -> 15;
            case "poco_comun" -> 35;
            case "raro" -> 80;
            case "epico" -> 250;
            case "legendario" -> 900;
            case "mitico" -> 2500;
            default -> 20;
        };
    }

    private double unit(ItemStack it) {
        String id = plugin.items().idOf(it);
        double v;
        if (id != null) {
            v = customValue(id, plugin.items().def(id));
        } else {
            v = base(it.getType());
            v = Math.min(v, cap(it.getType()));
        }
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            for (Map.Entry<Enchantment, Integer> en : meta.getEnchants().entrySet()) v += en.getValue() * 6;
            if (meta instanceof EnchantmentStorageMeta bm) for (int lvl : bm.getStoredEnchants().values()) v += lvl * 8;
            if (meta instanceof Damageable dm && it.getType().getMaxDurability() > 0 && dm.hasDamage()) {
                double left = 1 - (double) dm.getDamage() / it.getType().getMaxDurability();
                v *= Math.max(0.1, left);
            }
        }
        int up = plugin.items().upgradeLevel(it);
        if (up > 0) v += up * up * 25;
        return Math.max(0, v);
    }

    /** Valor de un material por su tipo. */
    private static double base(Material m) {
        Double f = FIXED.get(m);
        if (f != null) return f;
        String n = m.name();
        if (n.endsWith("_ORE")) {
            if (n.contains("DIAMOND")) return 40;
            if (n.contains("EMERALD")) return 15;
            if (n.contains("GOLD")) return 4;
            if (n.contains("IRON")) return 3;
            if (n.contains("LAPIS")) return 6;
            if (n.contains("REDSTONE")) return 3;
            return 1;
        }
        double tier = n.startsWith("NETHERITE_") ? 400 : n.startsWith("DIAMOND_") ? 80 : n.startsWith("IRON_") ? 15 : n.startsWith("CHAINMAIL_") ? 10
                : n.startsWith("GOLDEN_") ? 8 : n.startsWith("LEATHER_") ? 3 : n.startsWith("STONE_") ? 2 : n.startsWith("WOODEN_") ? 1 : n.startsWith("COPPER_") ? 4 : 0;
        if (tier > 0 && (ItemManager.isWeapon(m) || ItemManager.isArmor(m) || ItemManager.isTool(m))) {
            double piece = n.endsWith("_CHESTPLATE") ? 8 : n.endsWith("_LEGGINGS") ? 7 : n.endsWith("_HELMET") ? 5 : n.endsWith("_BOOTS") ? 4
                    : n.endsWith("_PICKAXE") || n.endsWith("_AXE") ? 3 : n.endsWith("_SWORD") || n.endsWith("_HOE") || n.endsWith("_SPEAR") ? 2 : 1;
            return tier * piece / 4;
        }
        if (m == Material.BOW || m == Material.CROSSBOW || m == Material.SHIELD || m == Material.FISHING_ROD) return 4;
        if (n.startsWith("MUSIC_DISC")) return 50;
        if (n.contains("POTION")) return 5;
        if (n.endsWith("_LOG") || n.endsWith("_WOOD") || n.endsWith("_STEM") || n.endsWith("_HYPHAE")) return 0.4;
        if (n.endsWith("_PLANKS")) return 0.1;
        if (n.endsWith("_WOOL")) return 0.5;
        if (n.contains("GLASS")) return 0.2;
        if (n.endsWith("TERRACOTTA") || n.endsWith("CONCRETE")) return 0.3;
        if (m.isEdible()) return 0.5;
        if (m.isBlock()) {
            if (n.contains("DIRT") || n.equals("GRASS_BLOCK") || n.contains("STONE") || n.contains("DEEPSLATE") || n.equals("SAND") || n.equals("GRAVEL")
                    || n.contains("NETHERRACK") || n.contains("ANDESITE") || n.contains("DIORITE") || n.contains("GRANITE") || n.contains("TUFF") || n.contains("COBBLED"))
                return 0.02;
            return 0.15;
        }
        return 0.1;
    }

    /** Lo máximo que se paga por unidad: 40 % de lo que cuesta comprarlo en cualquier tienda. */
    private double cap(Material m) {
        if (buyCaps == null) {
            buyCaps = new HashMap<>();
            for (String sid : plugin.shops().ids()) {
                var s = plugin.shops().shop(sid);
                if (s == null) continue;
                for (var e : s.entries) {
                    if (e.kind != ShopManager.Kind.COMPRAR || e.payWith != null || e.item == null) continue;
                    String rest = e.item.startsWith("material:") ? e.item.substring(9) : e.item.contains(":") ? null : e.item;
                    if (rest == null) continue;
                    Material mm = Material.matchMaterial(rest.toUpperCase(Locale.ROOT));
                    if (mm == null) continue;
                    double per = (double) e.price / Math.max(1, e.amount) * 0.4;
                    buyCaps.merge(mm, per, Math::min);
                }
            }
        }
        return buyCaps.getOrDefault(m, Double.MAX_VALUE);
    }
}
