package pe.kydium.eclipserpg.items;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.util.Keys;
import pe.kydium.eclipserpg.util.Reg;
import pe.kydium.eclipserpg.util.Txt;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Objetos únicos definidos en items.yml (armas de jefes, fragmentos, pergaminos...). */
public final class ItemManager implements Listener {

    public record AttrDef(String attribute, double value, String operation, String slot) {}

    public static final class CustomItem {
        public String id;
        public Material material;
        public String name;
        public List<String> lore = new ArrayList<>();
        public boolean unbreakable;
        public boolean glint;
        public boolean upgradable;
        public String color;
        public String action;        // "campamento"
        public String heldEffect;    // efecto mientras se tiene en la mano
        public int heldEffectLevel;
        public final Map<String, Integer> enchants = new LinkedHashMap<>();
        public final List<AttrDef> attributes = new ArrayList<>();
        public String rarity;        // comun, poco_comun, raro, epico, legendario, mitico
        public boolean rpgStyle;     // ocultar los atributos de Minecraft y mostrarlos en español
        public final List<Powers.Power> powers = new ArrayList<>();
        public int backpack;          // mochila: cuántos huecos tiene (0 = no es mochila)
        public String model;          // modelo de otro objeto (ej. minecraft:brown_bundle)
        public String texture;        // textura: nombre de un modelo de un paquete extra ("Hawkbrand")
        public String look;           // aspecto: cómo se ve la armadura puesta ("champions_armor")
    }

    /** Rarezas: color del nombre y etiqueta al final de la descripción. */
    private static final Map<String, String[]> RARITIES = Map.of(
            "comun", new String[]{"<white>", "<gray>", "COMÚN"},
            "poco_comun", new String[]{"<green>", "<green>", "POCO COMÚN"},
            "raro", new String[]{"<aqua>", "<aqua>", "RARO"},
            "epico", new String[]{"<light_purple>", "<light_purple>", "ÉPICO"},
            "legendario", new String[]{"<gold>", "<gold>", "LEGENDARIO"},
            "mitico", new String[]{"<gradient:#ff5555:#ffaa00>", "<gradient:#ff5555:#ffaa00>", "MÍTICO"});

    private static final Map<String, String> STAT_NAMES = Map.ofEntries(
            Map.entry("attack_damage", "<red>⚔ Daño"), Map.entry("attack_speed", "<yellow>⚡ Velocidad de ataque"),
            Map.entry("max_health", "<red>❤ Vida"), Map.entry("armor", "<blue>⛨ Armadura"),
            Map.entry("armor_toughness", "<blue>⛨ Dureza"), Map.entry("movement_speed", "<aqua>➤ Velocidad"),
            Map.entry("knockback_resistance", "<gray>⚓ Resistencia al empuje"), Map.entry("luck", "<green>☘ Suerte"),
            Map.entry("attack_knockback", "<gold>✊ Empuje"), Map.entry("scale", "<light_purple>⬍ Tamaño"),
            Map.entry("entity_interaction_range", "<gray>➹ Alcance"), Map.entry("block_interaction_range", "<gray>➹ Alcance de bloques"),
            Map.entry("mining_efficiency", "<gold>⛏ Eficiencia al minar"), Map.entry("jump_strength", "<green>⤒ Salto"),
            Map.entry("safe_fall_distance", "<green>☁ Caída segura"), Map.entry("oxygen_bonus", "<aqua>≈ Aire bajo el agua"));

    public static String rarityKey(CustomItem ci) {
        return ci.rarity == null ? null : ci.rarity.toLowerCase(java.util.Locale.ROOT).replace(" ", "_").replace("ú", "u").replace("é", "e").replace("í", "i");
    }

    private static String stat(double v) {
        if (v == Math.rint(v)) return String.valueOf((long) v);
        java.math.BigDecimal b = java.math.BigDecimal.valueOf(v).setScale(Math.abs(v) < 1 ? 2 : 1, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
        return b.toPlainString();
    }

    /** Nombre con el color de su rareza (si el nombre no trae color propio) y el nivel de mejora. */
    public String displayName(CustomItem ci, int lvl) {
        String n = ci.name == null ? ci.id : ci.name;
        String rk = rarityKey(ci);
        String[] r = rk == null ? null : RARITIES.get(rk);
        if (r != null && !n.trim().startsWith("<")) n = r[0] + n;
        return lvl > 0 ? n + " " + tierColor(lvl) + "+" + lvl : n;
    }

    /** Descripción completa: líneas propias, estadísticas en español (estilo RPG) y la rareza. */
    public List<String> loreLines(CustomItem ci, int lvl) {
        List<String> out = new ArrayList<>(ci.lore);
        if (ci.backpack > 0) {
            if (!out.isEmpty()) out.add("");
            out.add("<gold>🎒 Mochila <gray>· <white>" + ci.backpack + " huecos");
            out.add("<gray>Clic derecho para abrirla.");
        }
        if (ci.rpgStyle) {
            Map<String, Double> sum = new LinkedHashMap<>();
            for (AttrDef a : ci.attributes) {
                String k = a.attribute().toLowerCase(java.util.Locale.ROOT).replace("generic.", "").replace("minecraft:", "");
                sum.merge(k, a.value(), Double::sum);
            }
            if (lvl > 0 && !isArmor(ci.material) && !isTool(ci.material)) sum.merge("attack_damage", weaponBonus(lvl), Double::sum);
            if (!sum.isEmpty()) {
                if (!out.isEmpty()) out.add("");
                out.add("<gray>Estadísticas:");
                for (Map.Entry<String, Double> e : sum.entrySet()) {
                    double v = e.getValue();
                    String label = STAT_NAMES.getOrDefault(e.getKey(), "<gray>" + Txt.pretty(e.getKey().toUpperCase(java.util.Locale.ROOT)));
                    String val;
                    if (e.getKey().equals("attack_damage")) val = "<white>" + stat(1 + v);
                    else if (e.getKey().equals("attack_speed")) val = "<white>" + stat(4 + v);
                    else val = (v >= 0 ? "<green>+" : "<red>") + stat(v);
                    out.add(" " + label + ": " + val);
                }
            }
        }
        if (!ci.powers.isEmpty()) {
            if (!out.isEmpty()) out.add("");
            out.addAll(Powers.lore(ci.powers));
        }
        if (lvl > 0 && isArmor(ci.material)) out.addAll(upgradeLines(UpKind.ARMOR, ci.material, lvl));
        else if (lvl > 0 && isTool(ci.material)) out.addAll(upgradeLines(UpKind.TOOL, ci.material, lvl));
        else if (lvl > 0) out.add(tierColor(lvl) + "✦ " + tierName(lvl).replaceAll("<[^>]+>", "") + " +" + lvl);
        String rk = rarityKey(ci);
        String[] r = rk == null ? null : RARITIES.get(rk);
        if (r != null) {
            out.add("");
            out.add(r[1] + "<bold>✦ " + r[2]);
        }
        return out;
    }

    private final EclipseRPG plugin;
    private final Map<String, CustomItem> items = new LinkedHashMap<>();

    public ItemManager(EclipseRPG plugin) {
        this.plugin = plugin;
    }

    public void load() {
        items.clear();
        YamlConfiguration y = plugin.story().yaml("objetos");
        ConfigurationSection root = y.getConfigurationSection("items");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            Material m = Material.matchMaterial(s.getString("material", "STONE"));
            if (m == null) {
                plugin.getLogger().warning("items.yml: material inválido en " + id);
                continue;
            }
            CustomItem ci = new CustomItem();
            ci.id = id;
            ci.material = m;
            ci.name = s.getString("nombre", id);
            ci.lore = s.getStringList("lore");
            ci.unbreakable = s.getBoolean("irrompible", false);
            ci.glint = s.getBoolean("brillo", false);
            ci.upgradable = s.getBoolean("mejorable", false);
            ci.color = s.getString("color");
            ci.rarity = s.getString("rareza");
            ci.rpgStyle = s.getBoolean("estilo-rpg", false);
            ci.action = s.getString("accion");
            ci.backpack = Math.max(0, Math.min(10000, s.getInt("mochila", 0)));
            ci.model = s.getString("modelo");
            ci.texture = s.getString("textura");
            ci.look = s.getString("aspecto");
            ci.heldEffect = s.getString("efecto-en-mano");
            ci.heldEffectLevel = s.getInt("efecto-nivel", 0);
            ConfigurationSection en = s.getConfigurationSection("encantamientos");
            if (en != null) for (String k : en.getKeys(false)) ci.enchants.put(k, en.getInt(k));
            for (Map<?, ?> a : s.getMapList("atributos")) {
                Object attr = a.get("atributo");
                Object val = a.get("valor");
                if (attr == null || val == null) continue;
                Object op = a.get("operacion");
                Object slot = a.get("slot");
                ci.attributes.add(new AttrDef(attr.toString(), Double.parseDouble(val.toString()),
                        op == null ? null : op.toString(), slot == null ? null : slot.toString()));
            }
            for (Map<?, ?> a : s.getMapList("poderes")) {
                Object t = a.get("tipo");
                String type = t == null ? null : Powers.normType(t.toString());
                if (type == null) {
                    plugin.getLogger().warning("objetos.yml: poder desconocido en " + id + ": " + t);
                    continue;
                }
                String[] def = Powers.TYPES.get(type);
                double val = num(a.get("valor"), Double.parseDouble(def[2]));
                double ch = num(a.get("probabilidad"), Double.parseDouble(def[3]));
                if (ch > 1) ch /= 100.0;
                ci.powers.add(new Powers.Power(type, val, Math.max(0, Math.min(1, ch))));
            }
            items.put(id, ci);
        }
        plugin.getLogger().info("Objetos cargados: " + items.size());
    }

    private static double num(Object o, double def) {
        if (o == null) return def;
        try {
            return Double.parseDouble(o.toString().replace("%", "").replace(",", ".").trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Objetos de cierta rareza o mayor (para tiendas y botín "especial:"). */
    public List<String> ofRarity(String min) {
        List<String> order = List.of("comun", "poco_comun", "raro", "epico", "legendario", "mitico");
        int m = Math.max(0, order.indexOf(min == null ? "epico" : min.toLowerCase(java.util.Locale.ROOT)));
        List<String> out = new ArrayList<>();
        for (CustomItem ci : items.values()) {
            String rk = rarityKey(ci);
            if (rk != null && order.indexOf(rk) >= m && ci.action == null) out.add(ci.id);
        }
        return out;
    }

    public Set<String> ids() {
        return items.keySet();
    }

    public CustomItem def(String id) {
        return items.get(id);
    }

    public ItemStack create(String id, int amount) {
        CustomItem ci = items.get(id);
        if (ci == null) return null;
        ItemStack it = new ItemStack(ci.material, Math.max(1, amount));
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        meta.displayName(Txt.item(displayName(ci, 0)));
        meta.lore(Txt.lore(loreLines(ci, 0)));
        if (ci.rpgStyle) meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
        for (Map.Entry<String, Integer> e : ci.enchants.entrySet()) {
            Enchantment ench = Reg.enchant(e.getKey());
            if (ench == null) {
                plugin.getLogger().warning("items.yml: encantamiento desconocido '" + e.getKey() + "' en " + id);
                continue;
            }
            if (meta instanceof EnchantmentStorageMeta book) book.addStoredEnchant(ench, e.getValue(), true);
            else meta.addEnchant(ench, e.getValue(), true);
        }
        int idx = 0;
        for (AttrDef a : ci.attributes) {
            Attribute attr = Reg.attribute(a.attribute());
            if (attr == null) {
                plugin.getLogger().warning("items.yml: atributo desconocido '" + a.attribute() + "' en " + id);
                continue;
            }
            NamespacedKey key = pe.kydium.eclipserpg.util.Keys.k(("it_" + id + "_" + idx++).toLowerCase());
            meta.addAttributeModifier(attr, new AttributeModifier(key, a.value(), Reg.operation(a.operation()), Reg.slot(a.slot())));
        }
        if (ci.unbreakable) meta.setUnbreakable(true);
        if (ci.model != null && !ci.model.isBlank()) {
            NamespacedKey mk = NamespacedKey.fromString(ci.model.toLowerCase(java.util.Locale.ROOT).trim());
            if (mk != null) meta.setItemModel(mk);
        }
        var rp = plugin.resourcePack();
        if (ci.texture != null && rp != null && rp.hasModel(id))
            meta.setItemModel(new NamespacedKey("eclipse", pe.kydium.eclipserpg.npc.ResourcePack.modelKey(id)));
        if (ci.look != null && rp != null && isArmor(ci.material)) {
            String asset = rp.extras().look(ci.look, ci.material.name().endsWith("_LEGGINGS"));
            NamespacedKey ak = asset == null ? null : NamespacedKey.fromString("minecraft:" + asset);
            if (ak != null) {
                var eq = meta.getEquippable();
                eq.setSlot(ci.material.getEquipmentSlot());
                eq.setModel(ak);
                meta.setEquippable(eq);
            }
        }
        if (ci.backpack > 0) meta.setMaxStackSize(1);
        if (ci.glint) meta.setEnchantmentGlintOverride(true);
        if (ci.color != null && meta instanceof LeatherArmorMeta leather) {
            try {
                leather.setColor(Color.fromRGB(Integer.parseInt(ci.color.replace("#", ""), 16)));
            } catch (NumberFormatException ignored) {
                // color inválido
            }
        }
        meta.getPersistentDataContainer().set(Keys.ITEM, PersistentDataType.STRING, id);
        it.setItemMeta(meta);
        return it;
    }

    /** Id del objeto personalizado o null. */
    public String idOf(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(Keys.ITEM, PersistentDataType.STRING);
    }

    public int upgradeLevel(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return 0;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return 0;
        Integer v = meta.getPersistentDataContainer().get(Keys.UPGRADE, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    // ---------- Mejoras (yunque de Bruno) ----------

    public enum UpKind { WEAPON, ARMOR, TOOL }

    /** Qué se puede mejorar: armas, armaduras y herramientas (de Minecraft o de la historia). Null si no se puede. */
    public UpKind upKind(ItemStack it) {
        if (it == null || it.getType().isAir()) return null;
        String id = idOf(it);
        CustomItem ci = id == null ? null : items.get(id);
        if (ci != null && ci.backpack > 0) return null;
        Material m = it.getType();
        if (isWeapon(m)) return UpKind.WEAPON;
        if (isArmor(m)) return UpKind.ARMOR;
        if (isTool(m)) return UpKind.TOOL;
        if (ci != null && (ci.upgradable || !ci.powers.isEmpty())) return UpKind.WEAPON;
        return null;
    }

    public boolean upgradable(ItemStack it) {
        return upKind(it) != null;
    }

    public static boolean isWeapon(Material m) {
        if (m == null) return false;
        String n = m.name();
        return n.endsWith("_SWORD") || n.endsWith("_AXE") || n.endsWith("_SPEAR") || m == Material.MACE || m == Material.TRIDENT
                || m == Material.BOW || m == Material.CROSSBOW;
    }

    public static boolean isArmor(Material m) {
        if (m == null) return false;
        String n = m.name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS");
    }

    public static boolean isTool(Material m) {
        if (m == null) return false;
        String n = m.name();
        return n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE") || m == Material.SHEARS || m == Material.FISHING_ROD;
    }

    /** Nivel máximo de mejora (+10 por defecto). */
    public int maxUpgrade() {
        return Math.max(1, Math.min(15, plugin.getConfig().getInt("mejoras.niveles", 10)));
    }

    /** Fragmento que pide subir AL nivel indicado: I hasta +5, Superior +6 a +8, Ancestral +9 en adelante. */
    public static String fragmentFor(int targetLevel) {
        return targetLevel <= 5 ? "fragmento_mejora" : targetLevel <= 8 ? "fragmento_mejora_superior" : "fragmento_mejora_ancestral";
    }

    /** Probabilidad de que la mejora salga bien (si falla se pierde el fragmento, el objeto no se rompe). */
    public double upgradeChance(int targetLevel) {
        if (targetLevel <= 5) return 1.0;
        if (targetLevel <= 8) return plugin.getConfig().getDouble("mejoras.probabilidad-superior", 0.8);
        return plugin.getConfig().getDouble("mejoras.probabilidad-ancestral", 0.6);
    }

    /** Daño total que da la mejora de un arma: +1 por nivel hasta +5, +1.5 hasta +8 y +2 desde +9. */
    public static double weaponBonus(int lvl) {
        double v = 0;
        for (int i = 1; i <= lvl; i++) v += i <= 5 ? 1 : i <= 8 ? 1.5 : 2;
        return v;
    }

    public static String tierName(int lvl) {
        return lvl >= 9 ? "<light_purple>Ancestral" : lvl >= 6 ? "<aqua>Superior" : "<yellow>Mejorada";
    }

    private static String tierColor(int lvl) {
        return lvl >= 9 ? "<light_purple>" : lvl >= 6 ? "<aqua>" : "<yellow>";
    }

    /** Lo que da la mejora, en una línea por bonificación. */
    public static List<String> upgradeLines(UpKind k, Material m, int lvl) {
        List<String> out = new ArrayList<>();
        if (lvl <= 0 || k == null) return out;
        String c = tierColor(lvl);
        out.add(c + "✦ " + tierName(lvl).replaceAll("<[^>]+>", "") + " +" + lvl);
        switch (k) {
            case WEAPON -> out.add(" <gray>+" + stat(weaponBonus(lvl)) + " de daño" + (m == Material.BOW || m == Material.CROSSBOW ? " por flecha" : ""));
            case ARMOR -> {
                out.add(" <gray>+" + stat(0.5 * lvl) + " de armadura");
                if (lvl > 5) out.add(" <gray>+" + stat(0.5 * (lvl - 5)) + " de dureza");
                if (lvl > 8) out.add(" <gray>+" + (lvl - 8) * 2 + " de vida máxima");
            }
            case TOOL -> {
                out.add(" <gray>+" + lvl * 2 + " de eficiencia al picar");
                if (lvl > 5) out.add(" <gray>+" + (lvl - 5) * 10 + "% de velocidad de rotura");
            }
        }
        return out;
    }

    public boolean upgrade(ItemStack it, int maxLevel) {
        String id = idOf(it);
        CustomItem ci = id == null ? null : items.get(id);
        UpKind kind = upKind(it);
        if (kind == null) return false;
        int lvl = upgradeLevel(it);
        if (lvl >= maxLevel) return false;
        lvl++;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return false;
        EquipmentSlot slot = kind == UpKind.ARMOR ? it.getType().getEquipmentSlot() : EquipmentSlot.HAND;
        EquipmentSlotGroup group = kind == UpKind.ARMOR ? slot.getGroup() : EquipmentSlotGroup.MAINHAND;
        // un objeto normal no tiene atributos propios: copia los de fábrica antes de sumar la mejora
        if (!meta.hasAttributeModifiers()) {
            try {
                var defs = it.getType().getDefaultAttributeModifiers(slot);
                for (var en : defs.entries()) meta.addAttributeModifier(en.getKey(), en.getValue());
            } catch (RuntimeException | LinkageError ignored) {
                // sin datos de fábrica
            }
        }
        NamespacedKey key = pe.kydium.eclipserpg.util.Keys.k("mejora");
        for (Attribute at : new Attribute[]{Attribute.ATTACK_DAMAGE, Attribute.ARMOR, Attribute.ARMOR_TOUGHNESS, Attribute.MAX_HEALTH,
                Attribute.MINING_EFFICIENCY, Attribute.BLOCK_BREAK_SPEED}) {
            Collection<AttributeModifier> mods = meta.getAttributeModifiers(at);
            if (mods != null) for (AttributeModifier m : new ArrayList<>(mods)) if (key.equals(m.getKey())) meta.removeAttributeModifier(at, m);
        }
        switch (kind) {
            case WEAPON -> meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                    new AttributeModifier(key, weaponBonus(lvl), AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            case ARMOR -> {
                meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(key, 0.5 * lvl, AttributeModifier.Operation.ADD_NUMBER, group));
                if (lvl > 5) meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, new AttributeModifier(key, 0.5 * (lvl - 5), AttributeModifier.Operation.ADD_NUMBER, group));
                if (lvl > 8) meta.addAttributeModifier(Attribute.MAX_HEALTH, new AttributeModifier(key, (lvl - 8) * 2, AttributeModifier.Operation.ADD_NUMBER, group));
            }
            case TOOL -> {
                meta.addAttributeModifier(Attribute.MINING_EFFICIENCY, new AttributeModifier(key, lvl * 2, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
                if (lvl > 5) meta.addAttributeModifier(Attribute.BLOCK_BREAK_SPEED, new AttributeModifier(key, (lvl - 5) * 0.1, AttributeModifier.Operation.ADD_SCALAR, EquipmentSlotGroup.MAINHAND));
            }
        }
        if (lvl >= 9) meta.setEnchantmentGlintOverride(true);
        List<String> ll;
        if (ci != null) {
            meta.displayName(Txt.item(displayName(ci, lvl)));
            ll = loreLines(ci, lvl);
        } else {
            var color = lvl >= 9 ? net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE
                    : lvl >= 6 ? net.kyori.adventure.text.format.NamedTextColor.AQUA : net.kyori.adventure.text.format.NamedTextColor.YELLOW;
            net.kyori.adventure.text.Component base = meta.hasDisplayName() && meta.getPersistentDataContainer().has(pe.kydium.eclipserpg.util.Keys.k("nombre_base"))
                    ? net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().deserialize(meta.getPersistentDataContainer().get(pe.kydium.eclipserpg.util.Keys.k("nombre_base"), PersistentDataType.STRING))
                    : net.kyori.adventure.text.Component.translatable(it.getType().translationKey());
            meta.displayName(base.append(net.kyori.adventure.text.Component.text(" +" + lvl)).color(color)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            ll = new ArrayList<>(extraLore(meta));
            ll.addAll(upgradeLines(kind, it.getType(), lvl));
        }
        ll.addAll(Powers.runeLore(meta));
        meta.lore(Txt.lore(ll));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.UPGRADE, PersistentDataType.INTEGER, lvl);
        it.setItemMeta(meta);
        return true;
    }

    /** Líneas propias que trae un objeto (armaduras del día de Pip) para no perderlas al mejorarlo. */
    private static List<String> extraLore(ItemMeta meta) {
        String raw = meta.getPersistentDataContainer().get(pe.kydium.eclipserpg.util.Keys.k("lore_base"), PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(List.of(raw.split("\n")));
    }

    public int count(Player p, String id) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (id.equals(idOf(it))) n += it.getAmount();
        }
        return n;
    }

    /** Quita una cantidad de un objeto personalizado del inventario. */
    public boolean take(Player p, String id, int amount) {
        if (count(p, id) < amount) return false;
        ItemStack[] contents = p.getInventory().getContents();
        int left = amount;
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack it = contents[i];
            if (!id.equals(idOf(it))) continue;
            int take = Math.min(left, it.getAmount());
            it.setAmount(it.getAmount() - take);
            left -= take;
            if (it.getAmount() <= 0) p.getInventory().setItem(i, null);
        }
        return true;
    }

    public void give(Player p, ItemStack it) {
        if (it == null) return;
        for (ItemStack rest : p.getInventory().addItem(it).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
    }

    // ---------- Campamento ----------

    public Location campLocation() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("campamento");
        if (s != null && s.getString("mundo") != null) {
            World w = Bukkit.getWorld(s.getString("mundo", "world"));
            if (w != null) {
                return new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                        (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
            }
        }
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        ItemStack it = e.getItem();
        String id = idOf(it);
        CustomItem ci = id == null ? null : items.get(id);
        if (ci == null || ci.action == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (ci.action.toLowerCase().startsWith("invocar:")) {
            String mob = ci.action.substring(8);
            if (plugin.mobs().def(mob) == null) return;
            it.setAmount(it.getAmount() - 1);
            Location at = plugin.mobs().spawnSpot(p.getLocation(), 7);
            p.getWorld().strikeLightningEffect(at);
            for (Player near : p.getWorld().getPlayers()) {
                if (near.getLocation().distanceSquared(at) < 48 * 48) {
                    Txt.send(near, "<dark_red><bold>¡" + p.getName() + " rompió un sello! Algo despierta...");
                    Txt.sound(near, "entity.wither.spawn", 0.7f, 0.8f);
                }
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.mobs().spawn(mob, at), 40L);
            return;
        }
        if (ci.action.equalsIgnoreCase("campamento")) {
            it.setAmount(it.getAmount() - 1);
            p.teleportAsync(campLocation()).thenRun(() -> {
                Txt.sound(p, "entity.enderman.teleport", 1f, 1f);
                Txt.send(p, "<yellow>Volviste a " + plugin.villageName() + ".");
            });
        }
    }

    /** Efectos que dan algunos objetos mientras se tienen en la mano principal. */
    public void startHeldEffectTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                String id = idOf(p.getInventory().getItemInMainHand());
                CustomItem ci = id == null ? null : items.get(id);
                if (ci == null || ci.heldEffect == null) continue;
                PotionEffectType type = Reg.effect(ci.heldEffect);
                if (type == null) continue;
                p.addPotionEffect(new PotionEffect(type, 300, ci.heldEffectLevel, true, false, true));
            }
        }, 40L, 40L);
    }
}
