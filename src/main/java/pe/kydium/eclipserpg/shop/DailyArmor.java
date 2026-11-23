package pe.kydium.eclipserpg.shop;

import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import pe.kydium.eclipserpg.util.Keys;
import pe.kydium.eclipserpg.util.Reg;
import pe.kydium.eclipserpg.util.Txt;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Armaduras únicas que el mercader vende cada día: pieza, material, encantamientos, bonificaciones,
 * adornos y nombre al azar (pero iguales para todos durante ese día).
 */
public final class DailyArmor {

    public record Piece(ItemStack item, long price, int level, String rarity) {}

    private static final String[] PIECES = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};
    private static final String[][] NOUNS = {
            {"Yelmo", "Casco", "Capucha", "Corona", "Visera"},
            {"Peto", "Coraza", "Cota", "Armadura", "Jubón"},
            {"Grebas", "Perneras", "Quijotes", "Calzas", "Faldar"},
            {"Botas", "Escarpes", "Sandalias", "Borceguíes", "Pisadas"}};
    private static final String[] OF = {"del Alba Rota", "de la Ceniza", "del Vigía", "de Hornoviejo", "del Eclipse", "de la Marea",
            "del Errante", "de Velkar", "de las Dunas", "del Último Sol", "de la Grieta", "del Lobo Gris", "de Brasas", "del Viento Encadenado",
            "de la Abuela Tula", "del Bosque Podrido", "de los Mil Pasos", "del Centinela", "de la Luna Roja", "del Rey Caído"};
    private static final String[] TIERS = {"LEATHER", "CHAINMAIL", "IRON", "GOLDEN", "DIAMOND", "NETHERITE"};
    private static final int[] TIER_W = {14, 20, 30, 10, 19, 7};
    private static final int[] TIER_PRICE = {60, 160, 260, 220, 1100, 4200};
    private static final int[] TIER_LVL = {1, 5, 8, 6, 18, 30};
    private static final String[][] RARITY = {{"<white>", "Común"}, {"<aqua>", "Rara"}, {"<light_purple>", "Épica"}, {"<gold>", "Legendaria"}};

    private DailyArmor() {}

    public static Piece make(long day, int index) {
        Random r = new Random(day * 7919L + index * 104729L + 17);
        String piece = PIECES[r.nextInt(PIECES.length)];
        int tier = pick(r, TIER_W);
        Material m = Material.matchMaterial(TIERS[tier] + "_" + piece);
        if (m == null) m = Material.IRON_CHESTPLATE;
        ItemStack it = new ItemStack(m);
        ArmorMeta meta = (ArmorMeta) it.getItemMeta();
        EquipmentSlot slot = m.getEquipmentSlot();
        EquipmentSlotGroup group = slot.getGroup();
        // los atributos de fábrica (si no, al poner los propios desaparece la armadura normal)
        try {
            var defs = m.getDefaultAttributeModifiers(slot);
            for (var en : defs.entries()) meta.addAttributeModifier(en.getKey(), en.getValue());
        } catch (RuntimeException | LinkageError ignored) {
            // sin datos de fábrica
        }
        int perks = 0;
        List<String> lore = new ArrayList<>();
        // encantamientos
        String[] prot = {"protection", "fire_protection", "blast_protection", "projectile_protection"};
        int pl = 1 + r.nextInt(4) + (r.nextDouble() < 0.12 ? 1 : 0);   // a veces V
        add(meta, prot[r.nextInt(prot.length)], pl);
        perks += pl >= 4 ? 2 : 1;
        if (r.nextDouble() < 0.7) { add(meta, "unbreaking", 1 + r.nextInt(3)); perks++; }
        if (r.nextDouble() < 0.06) { add(meta, "mending", 1); perks += 2; }
        switch (piece) {
            case "HELMET" -> { if (r.nextBoolean()) { add(meta, "respiration", 1 + r.nextInt(3)); perks++; } if (r.nextDouble() < 0.4) add(meta, "aqua_affinity", 1); }
            case "CHESTPLATE" -> { if (r.nextDouble() < 0.35) { add(meta, "thorns", 1 + r.nextInt(3)); perks++; } }
            case "LEGGINGS" -> { if (r.nextDouble() < 0.3) { add(meta, "swift_sneak", 1 + r.nextInt(3)); perks++; } }
            default -> {
                if (r.nextBoolean()) { add(meta, "feather_falling", 1 + r.nextInt(4)); perks++; }
                if (r.nextDouble() < 0.3) { add(meta, "depth_strider", 1 + r.nextInt(3)); perks++; }
            }
        }
        // bonificaciones únicas
        int bonuses = 1 + (r.nextDouble() < 0.5 ? 1 : 0) + (r.nextDouble() < 0.2 ? 1 : 0);
        List<Integer> opts = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8));
        java.util.Collections.shuffle(opts, r);
        for (int b = 0; b < bonuses; b++) {
            int o = opts.get(b);
            switch (o) {
                case 0 -> { int v = 1 + r.nextInt(4); attr(meta, Attribute.MAX_HEALTH, "vida", v, false, group); lore.add("<red>❤ +" + v + " de vida máxima"); }
                case 1 -> { int v = 2 + r.nextInt(6); attr(meta, Attribute.MOVEMENT_SPEED, "velocidad", v / 100.0, true, group); lore.add("<green>➶ +" + v + "% de velocidad"); }
                case 2 -> { double v = 0.5 * (1 + r.nextInt(4)); attr(meta, Attribute.ATTACK_DAMAGE, "danio", v, false, group); lore.add("<gold>⚔ +" + fmt(v) + " de daño"); }
                case 3 -> { int v = 5 + r.nextInt(11); attr(meta, Attribute.KNOCKBACK_RESISTANCE, "firmeza", v / 100.0, false, group); lore.add("<gray>⛨ +" + v + "% de firmeza"); }
                case 4 -> { double v = 0.5 * (1 + r.nextInt(4)); attr(meta, Attribute.LUCK, "suerte", v, false, group); lore.add("<light_purple>☘ +" + fmt(v) + " de suerte"); }
                case 5 -> { double v = 0.5 * (1 + r.nextInt(4)); attr(meta, Attribute.ARMOR_TOUGHNESS, "dureza", v, false, group); lore.add("<aqua>🛡 +" + fmt(v) + " de dureza"); }
                case 6 -> { int v = 1 + r.nextInt(3); attr(meta, Attribute.SAFE_FALL_DISTANCE, "caida", v, false, group); lore.add("<white>☁ +" + v + " de caída segura"); }
                case 7 -> { int v = 3 + r.nextInt(8); attr(meta, Attribute.ATTACK_SPEED, "golpe", v / 100.0, true, group); lore.add("<yellow>⚡ +" + v + "% de velocidad de ataque"); }
                default -> { double v = 1 + r.nextInt(2); attr(meta, Attribute.OXYGEN_BONUS, "aire", v, false, group); lore.add("<aqua>≈ +" + fmt(v) + " de aire bajo el agua"); }
            }
            perks += 2;
        }
        int rarity = perks >= 9 ? 3 : perks >= 7 ? 2 : perks >= 4 ? 1 : 0;
        // adorno y color
        try {
            List<TrimPattern> pats = new ArrayList<>();
            Registry.TRIM_PATTERN.forEach(pats::add);
            List<TrimMaterial> mats = new ArrayList<>();
            Registry.TRIM_MATERIAL.forEach(mats::add);
            if (!pats.isEmpty() && !mats.isEmpty() && (rarity > 0 || r.nextBoolean()))
                meta.setTrim(new ArmorTrim(mats.get(r.nextInt(mats.size())), pats.get(r.nextInt(pats.size()))));
        } catch (RuntimeException | LinkageError ignored) {
            // sin adornos en esta versión
        }
        if (meta instanceof LeatherArmorMeta lm) lm.setColor(Color.fromRGB(r.nextInt(0xFFFFFF)));
        int nounIdx = java.util.Arrays.asList(PIECES).indexOf(piece);
        String name = RARITY[rarity][0] + NOUNS[nounIdx][r.nextInt(NOUNS[nounIdx].length)] + " " + OF[r.nextInt(OF.length)];
        meta.displayName(Txt.item(name));
        List<String> full = new ArrayList<>();
        full.add("<dark_gray>Pieza única · " + RARITY[rarity][1]);
        full.addAll(lore);
        full.add("<gray><i>Solo hoy en la tienda de Pip.");
        meta.lore(Txt.lore(full));
        if (rarity >= 2) meta.setEnchantmentGlintOverride(true);
        // para que la mejora del yunque conserve nombre y descripción
        meta.getPersistentDataContainer().set(Keys.k("nombre_base"), PersistentDataType.STRING, GsonComponentSerializer.gson().serialize(Txt.item(name)));
        meta.getPersistentDataContainer().set(Keys.k("lore_base"), PersistentDataType.STRING, String.join("\n", full));
        meta.getPersistentDataContainer().set(Keys.k("armadura_dia"), PersistentDataType.STRING, day + ":" + index);
        it.setItemMeta(meta);
        double pieceMul = switch (piece) { case "CHESTPLATE" -> 1.6; case "LEGGINGS" -> 1.4; case "HELMET" -> 1.0; default -> 0.9; };
        long price = Math.round(TIER_PRICE[tier] * pieceMul * (1 + perks * 0.3) / 10.0) * 10;
        int lvl = TIER_LVL[tier] + rarity * 3;
        return new Piece(it, price, lvl, RARITY[rarity][1]);
    }

    private static int pick(Random r, int[] w) {
        int sum = 0;
        for (int x : w) sum += x;
        int v = r.nextInt(sum);
        for (int i = 0; i < w.length; i++) {
            v -= w[i];
            if (v < 0) return i;
        }
        return 0;
    }

    private static void add(ItemMeta meta, String ench, int lvl) {
        Enchantment e = Reg.enchant(ench);
        if (e != null) meta.addEnchant(e, lvl, true);
    }

    private static void attr(ItemMeta meta, Attribute a, String id, double v, boolean scalar, EquipmentSlotGroup g) {
        NamespacedKey key = Keys.k("dia_" + id);
        meta.addAttributeModifier(a, new AttributeModifier(key, v, scalar ? AttributeModifier.Operation.ADD_SCALAR : AttributeModifier.Operation.ADD_NUMBER, g));
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
