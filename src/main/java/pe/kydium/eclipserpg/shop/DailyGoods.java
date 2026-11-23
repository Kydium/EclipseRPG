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
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pe.kydium.eclipserpg.items.Powers;
import pe.kydium.eclipserpg.util.Keys;
import pe.kydium.eclipserpg.util.Reg;
import pe.kydium.eclipserpg.util.Txt;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Novedades del día de cada vendedor: armas y herramientas de Bruno, elixires y libros de Fray Odón,
 * armaduras y curiosidades de Pip, reliquias de Sabina. Todo sale de la fecha: igual para todos ese día.
 */
public final class DailyGoods {

    static final String[] OF = {"del Alba Rota", "de la Ceniza", "del Vigía", "de Hornoviejo", "del Eclipse", "de la Marea",
            "del Errante", "de Velkar", "de las Dunas", "del Último Sol", "de la Grieta", "del Lobo Gris", "de Brasas", "del Viento Encadenado",
            "del Bosque Podrido", "de los Mil Pasos", "del Centinela", "de la Luna Roja", "del Rey Caído", "de la Forja Antigua",
            "del Cazador", "de la Tormenta", "del Capataz", "de Medianoche"};
    static final String[][] RARITY = {{"<white>", "Común"}, {"<aqua>", "Rara"}, {"<light_purple>", "Épica"}, {"<gold>", "Legendaria"}};

    private DailyGoods() {}

    /** tipo: armaduras | armas | herramientas | pociones | libros | curiosidades | reliquias */
    public static DailyArmor.Piece make(String kind, long day, int i) {
        Random r = new Random(day * 7919L + i * 104729L + kind.hashCode() * 31L);
        return switch (kind) {
            case "armaduras" -> DailyArmor.make(day, i);
            case "armas" -> weapon(r);
            case "herramientas" -> tool(r);
            case "pociones" -> potion(r);
            case "libros" -> book(r);
            case "curiosidades" -> curiosity(r, day, i);
            case "reliquias" -> relic(r, day, i);
            default -> null;
        };
    }

    // ---------------------------------------------------------------- armas

    private static DailyArmor.Piece weapon(Random r) {
        String[][] kinds = {{"SWORD", "30"}, {"AXE", "18"}, {"SPEAR", "10"}, {"BOW", "14"}, {"CROSSBOW", "10"}, {"TRIDENT", "8"}, {"MACE", "6"}};
        String k = weighted(r, kinds);
        String tier = r.nextInt(100) < 50 ? "IRON" : r.nextInt(100) < 76 ? "DIAMOND" : "NETHERITE";
        Material m = switch (k) {
            case "BOW" -> Material.BOW;
            case "CROSSBOW" -> Material.CROSSBOW;
            case "TRIDENT" -> Material.TRIDENT;
            case "MACE" -> Material.MACE;
            default -> Material.matchMaterial(tier + "_" + k);
        };
        if (m == null) {
            k = "SWORD";
            m = Material.matchMaterial(tier + "_SWORD");
        }
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        copyDefaults(meta, m, EquipmentSlot.HAND);
        int perks = 0;
        switch (k) {
            case "SWORD", "SPEAR" -> {
                perks += ench(meta, r, new String[]{"sharpness", "smite", "bane_of_arthropods"}[r.nextInt(3)], 5);
                if (r.nextDouble() < 0.4) perks += ench(meta, r, "fire_aspect", 2);
                if (r.nextDouble() < 0.4) perks += ench(meta, r, "looting", 3);
                if (r.nextDouble() < 0.3) perks += ench(meta, r, k.equals("SWORD") ? "sweeping_edge" : "knockback", 3);
            }
            case "AXE" -> {
                perks += ench(meta, r, r.nextBoolean() ? "sharpness" : "smite", 5);
                if (r.nextDouble() < 0.5) perks += ench(meta, r, "efficiency", 5);
            }
            case "BOW" -> {
                perks += ench(meta, r, "power", 5);
                if (r.nextDouble() < 0.4) perks += ench(meta, r, "punch", 2);
                if (r.nextDouble() < 0.4) perks += ench(meta, r, "flame", 1);
                if (r.nextDouble() < 0.25) perks += ench(meta, r, "infinity", 1);
            }
            case "CROSSBOW" -> {
                perks += ench(meta, r, "quick_charge", 3);
                perks += ench(meta, r, r.nextBoolean() ? "multishot" : "piercing", r.nextBoolean() ? 1 : 4);
            }
            case "TRIDENT" -> {
                perks += ench(meta, r, "impaling", 5);
                perks += ench(meta, r, r.nextBoolean() ? "loyalty" : "riptide", 3);
                if (r.nextDouble() < 0.4) perks += ench(meta, r, "channeling", 1);
            }
            default -> {
                perks += ench(meta, r, r.nextBoolean() ? "density" : "breach", 5);
                if (r.nextDouble() < 0.5) perks += ench(meta, r, "wind_burst", 3);
            }
        }
        if (r.nextDouble() < 0.7) perks += ench(meta, r, "unbreaking", 3);
        if (r.nextDouble() < 0.06) perks += 2 + ench(meta, r, "mending", 1) * 0;
        List<String> lore = new ArrayList<>();
        // bonificaciones
        int b = 1 + (r.nextDouble() < 0.4 ? 1 : 0);
        List<Integer> opts = shuffled(r, 5);
        for (int x = 0; x < b; x++) {
            switch (opts.get(x)) {
                case 0 -> { double v = 0.5 * (2 + r.nextInt(5)); attr(meta, Attribute.ATTACK_DAMAGE, "danio", v, false, EquipmentSlotGroup.MAINHAND); lore.add("<gold>⚔ +" + fmt(v) + " de daño"); }
                case 1 -> { int v = 5 + r.nextInt(11); attr(meta, Attribute.ATTACK_SPEED, "golpe", v / 100.0, true, EquipmentSlotGroup.MAINHAND); lore.add("<yellow>⚡ +" + v + "% de velocidad de ataque"); }
                case 2 -> { int v = 3 + r.nextInt(6); attr(meta, Attribute.MOVEMENT_SPEED, "velocidad", v / 100.0, true, EquipmentSlotGroup.MAINHAND); lore.add("<green>➶ +" + v + "% de velocidad"); }
                case 3 -> { int v = 2 + r.nextInt(3); attr(meta, Attribute.MAX_HEALTH, "vida", v, false, EquipmentSlotGroup.MAINHAND); lore.add("<red>❤ +" + v + " de vida máxima"); }
                default -> { double v = 0.5 * (1 + r.nextInt(4)); attr(meta, Attribute.LUCK, "suerte", v, false, EquipmentSlotGroup.MAINHAND); lore.add("<light_purple>☘ +" + fmt(v) + " de suerte"); }
            }
            perks += 2;
        }
        // un poder de runa ya forjado
        if (r.nextDouble() < 0.6) {
            perks += 2;
            Powers.setRunes(meta, List.of(power(r, "")));
        }
        int rarity = rarity(perks);
        String[] nouns = switch (k) {
            case "SWORD" -> new String[]{"Espada", "Hoja", "Filo", "Tizona", "Mandoble"};
            case "AXE" -> new String[]{"Hacha", "Segur", "Hacha de guerra"};
            case "SPEAR" -> new String[]{"Lanza", "Pica", "Alabarda"};
            case "BOW" -> new String[]{"Arco", "Arco largo", "Arco curvo"};
            case "CROSSBOW" -> new String[]{"Ballesta", "Ballesta pesada"};
            case "TRIDENT" -> new String[]{"Tridente", "Arpón"};
            default -> new String[]{"Maza", "Martillo", "Lucero del alba"};
        };
        String name = RARITY[rarity][0] + nouns[r.nextInt(nouns.length)] + " " + OF[r.nextInt(OF.length)];
        finish(meta, name, rarity, lore, "Solo hoy en la herrería de Bruno.");
        it.setItemMeta(meta);
        int tierIdx = tier.equals("IRON") ? 0 : tier.equals("DIAMOND") ? 1 : 2;
        long base = switch (k) {
            case "TRIDENT" -> 2600;
            case "MACE" -> 3600;
            case "BOW", "CROSSBOW" -> 500;
            default -> new long[]{400, 1500, 5200}[tierIdx];
        };
        long price = round10(base * (1 + perks * 0.25));
        int lvl = (k.equals("BOW") || k.equals("CROSSBOW") ? 6 : new int[]{8, 18, 30}[tierIdx]) + rarity * 3;
        if (k.equals("MACE") || k.equals("TRIDENT")) lvl = Math.max(lvl, 22);
        return new DailyArmor.Piece(it, price, lvl, RARITY[rarity][1]);
    }

    // ---------------------------------------------------------------- herramientas

    private static DailyArmor.Piece tool(Random r) {
        String[][] kinds = {{"PICKAXE", "40"}, {"SHOVEL", "15"}, {"AXE", "15"}, {"HOE", "10"}, {"FISHING_ROD", "12"}, {"SHEARS", "8"}};
        String k = weighted(r, kinds);
        String tier = r.nextInt(100) < 50 ? "IRON" : r.nextInt(100) < 80 ? "DIAMOND" : "NETHERITE";
        Material m = k.equals("FISHING_ROD") ? Material.FISHING_ROD : k.equals("SHEARS") ? Material.SHEARS : Material.matchMaterial(tier + "_" + k);
        if (m == null) m = Material.IRON_PICKAXE;
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        copyDefaults(meta, m, EquipmentSlot.HAND);
        int perks = 0;
        if (k.equals("FISHING_ROD")) {
            perks += ench(meta, r, "luck_of_the_sea", 3);
            perks += ench(meta, r, "lure", 3);
        } else {
            perks += ench(meta, r, "efficiency", 5);
            if (!k.equals("SHEARS") && r.nextDouble() < 0.55) perks += ench(meta, r, r.nextDouble() < 0.75 ? "fortune" : "silk_touch", 3);
        }
        if (r.nextDouble() < 0.75) perks += ench(meta, r, "unbreaking", 3);
        if (r.nextDouble() < 0.08) { ench(meta, r, "mending", 1); perks += 2; }
        List<String> lore = new ArrayList<>();
        if (!k.equals("FISHING_ROD")) {
            int v = 1 + r.nextInt(4);
            attr(meta, Attribute.MINING_EFFICIENCY, "picar", v, false, EquipmentSlotGroup.MAINHAND);
            lore.add("<yellow>⛏ +" + v + " de eficiencia al picar");
            perks++;
        }
        if (r.nextDouble() < 0.4) {
            double v = 0.5 * (1 + r.nextInt(4));
            attr(meta, Attribute.LUCK, "suerte", v, false, EquipmentSlotGroup.MAINHAND);
            lore.add("<light_purple>☘ +" + fmt(v) + " de suerte");
            perks++;
        }
        String scope = k.equals("HOE") ? "(azada)" : k.equals("FISHING_ROD") ? "(caña)" : "(herramienta)";
        if (r.nextDouble() < 0.65) {
            Powers.setRunes(meta, List.of(power(r, scope)));
            perks += 2;
        }
        int rarity = rarity(perks + 1);
        String[] nouns = switch (k) {
            case "PICKAXE" -> new String[]{"Pico", "Zapapico", "Pico minero"};
            case "SHOVEL" -> new String[]{"Pala", "Laya"};
            case "AXE" -> new String[]{"Hacha de leñador", "Destral"};
            case "HOE" -> new String[]{"Azada", "Hoz"};
            case "FISHING_ROD" -> new String[]{"Caña", "Caña de pescar"};
            default -> new String[]{"Tijeras", "Cizalla"};
        };
        String name = RARITY[rarity][0] + nouns[r.nextInt(nouns.length)] + " " + OF[r.nextInt(OF.length)];
        finish(meta, name, rarity, lore, "Solo hoy en la herrería de Bruno.");
        it.setItemMeta(meta);
        int tierIdx = tier.equals("IRON") ? 0 : tier.equals("DIAMOND") ? 1 : 2;
        long base = k.equals("FISHING_ROD") || k.equals("SHEARS") ? 350 : new long[]{300, 1200, 4500}[tierIdx];
        return new DailyArmor.Piece(it, round10(base * (1 + perks * 0.25)), (k.equals("FISHING_ROD") || k.equals("SHEARS") ? 4 : new int[]{6, 16, 28}[tierIdx]) + rarity * 2, RARITY[rarity][1]);
    }

    // ---------------------------------------------------------------- elixires

    private static DailyArmor.Piece potion(Random r) {
        String[][] pool = {{"strength", "2", "<red>"}, {"speed", "3", "<aqua>"}, {"regeneration", "2", "<light_purple>"}, {"resistance", "2", "<gray>"},
                {"fire_resistance", "1", "<gold>"}, {"night_vision", "1", "<blue>"}, {"water_breathing", "1", "<dark_aqua>"}, {"jump_boost", "3", "<green>"},
                {"haste", "3", "<yellow>"}, {"absorption", "4", "<gold>"}, {"health_boost", "3", "<red>"}, {"luck", "3", "<green>"},
                {"slow_falling", "1", "<white>"}, {"invisibility", "1", "<dark_gray>"}, {"conduit_power", "1", "<aqua>"}, {"saturation", "1", "<gold>"}};
        String[] beasts = {"del Toro", "de la Liebre", "del Halcón", "de la Montaña", "del Alba", "de Medianoche", "del Oso", "del Zorro",
                "de la Serpiente", "del Búho", "de la Tortuga", "del Fénix", "de Fray Odón", "del Monje", "de la Marea", "de la Luna"};
        String[] heads = {"Elixir", "Tónico", "Brebaje", "Filtro", "Bálsamo", "Esencia"};
        int n = 1 + (r.nextDouble() < 0.5 ? 1 : 0) + (r.nextDouble() < 0.2 ? 1 : 0);
        Material type = r.nextDouble() < 0.18 ? Material.SPLASH_POTION : r.nextDouble() < 0.05 ? Material.LINGERING_POTION : Material.POTION;
        ItemStack it = new ItemStack(type);
        PotionMeta pm = (PotionMeta) it.getItemMeta();
        List<Integer> idx = shuffled(r, pool.length);
        double score = 0;
        String main = null;
        for (int x = 0; x < n; x++) {
            String[] e = pool[idx.get(x)];
            PotionEffectType pt = Reg.effect(e[0]);
            if (pt == null) continue;
            int amp = r.nextInt(Integer.parseInt(e[1]));
            int minutes = e[0].equals("saturation") ? 0 : 3 + r.nextInt(8);
            int ticks = e[0].equals("saturation") ? 20 * (5 + amp * 5) : minutes * 1200;
            pm.addCustomEffect(new PotionEffect(pt, ticks, amp), true);
            score += (amp + 1) * Math.max(1, minutes) * 12;
            if (main == null) main = e[2];
        }
        pm.setColor(Color.fromRGB(40 + r.nextInt(215), 40 + r.nextInt(215), 40 + r.nextInt(215)));
        int rarity = n >= 3 ? 3 : n == 2 ? 2 : score > 60 ? 1 : 0;
        String name = RARITY[rarity][0] + heads[r.nextInt(heads.length)] + " " + beasts[r.nextInt(beasts.length)]
                + (type == Material.SPLASH_POTION ? " (arrojadizo)" : type == Material.LINGERING_POTION ? " (persistente)" : "");
        finish(pm, name, rarity, new ArrayList<>(), "Solo hoy en la alquimia de Fray Odón.");
        it.setItemMeta(pm);
        it.setAmount(1 + r.nextInt(2));
        return new DailyArmor.Piece(it, round10(80 + score * 2.2), 1 + rarity * 4, RARITY[rarity][1]);
    }

    private static DailyArmor.Piece book(Random r) {
        List<Enchantment> all = new ArrayList<>();
        Registry.ENCHANTMENT.forEach(all::add);
        all.removeIf(e -> e.isCursed());
        Enchantment e = all.get(r.nextInt(all.size()));
        int lvl = Math.max(1, e.getMaxLevel() - (r.nextDouble() < 0.5 ? 1 : 0));
        boolean over = e.getMaxLevel() > 1 && r.nextDouble() < 0.12;
        if (over) lvl = e.getMaxLevel() + 1;
        ItemStack it = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta bm = (EnchantmentStorageMeta) it.getItemMeta();
        bm.addStoredEnchant(e, lvl, true);
        if (over) {
            bm.displayName(Txt.item("<gold>Libro prohibido"));
            bm.lore(Txt.lore(List.of("<gray>Un nivel que no existe en ningún otro sitio.", "<gray><i>Solo hoy con Fray Odón.")));
        } else bm.lore(Txt.lore(List.of("<gray><i>Solo hoy con Fray Odón.")));
        it.setItemMeta(bm);
        long price = round10(120L * lvl * (e.getMaxLevel() == 1 ? 4 : 1) * (over ? 4 : 1));
        return new DailyArmor.Piece(it, price, over ? 25 : 1 + lvl * 3, over ? "Legendaria" : "Rara");
    }

    // ---------------------------------------------------------------- curiosidades de Pip

    private static DailyArmor.Piece curiosity(Random r, long day, int i) {
        // {material, cantidad, precio, nivel, peso}
        Object[][] pool = {{"TOTEM_OF_UNDYING", 1, 2500, 20, 6}, {"ENCHANTED_GOLDEN_APPLE", 1, 3000, 25, 4}, {"GOLDEN_APPLE", 3, 360, 6, 10},
                {"NAME_TAG", 1, 150, 1, 8}, {"SADDLE", 1, 200, 1, 6}, {"HEART_OF_THE_SEA", 1, 1500, 15, 4}, {"NAUTILUS_SHELL", 2, 300, 8, 5},
                {"SHULKER_SHELL", 2, 1200, 20, 5}, {"ELYTRA", 1, 15000, 40, 2}, {"EXPERIENCE_BOTTLE", 16, 400, 1, 8}, {"ENDER_PEARL", 4, 160, 5, 8},
                {"FIREWORK_ROCKET", 16, 120, 1, 6}, {"SPYGLASS", 1, 120, 1, 5}, {"RECOVERY_COMPASS", 1, 450, 10, 4}, {"DIAMOND_HORSE_ARMOR", 1, 600, 10, 4},
                {"NETHERITE_UPGRADE_SMITHING_TEMPLATE", 1, 2500, 25, 3}, {"GOAT_HORN", 1, 300, 1, 4}, {"BUNDLE", 1, 100, 1, 5},
                {"TRIM", 1, 400, 5, 10}, {"DISC", 1, 250, 1, 8}, {"TRIAL_KEY", 2, 300, 10, 4}, {"OMINOUS_TRIAL_KEY", 1, 900, 20, 2}};
        int sum = 0;
        for (Object[] o : pool) sum += (int) o[4];
        // elige sin repetir dentro del mismo día: se descarta lo que ya salió en índices anteriores
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (int j = 0; j < i; j++) used.add(pickIdx(new Random(day * 7919L + j * 104729L + "curiosidades".hashCode() * 31L), pool, sum));
        int k = pickIdx(r, pool, sum);
        for (int t = 0; t < 30 && used.contains(k); t++) k = pickIdx(r, pool, sum);
        Object[] o = pool[k];
        String id = (String) o[0];
        Material m;
        if (id.equals("TRIM")) {
            List<Material> ts = new ArrayList<>();
            for (Material x : Material.values()) if (x.name().endsWith("_ARMOR_TRIM_SMITHING_TEMPLATE")) ts.add(x);
            m = ts.isEmpty() ? Material.BOOK : ts.get(r.nextInt(ts.size()));
        } else if (id.equals("DISC")) {
            List<Material> ds = new ArrayList<>();
            for (Material x : Material.values()) if (x.name().startsWith("MUSIC_DISC_")) ds.add(x);
            m = ds.isEmpty() ? Material.JUKEBOX : ds.get(r.nextInt(ds.size()));
        } else m = Material.matchMaterial(id);
        if (m == null) m = Material.GOLDEN_APPLE;
        ItemStack it = new ItemStack(m, (int) o[1]);
        ItemMeta meta = it.getItemMeta();
        meta.lore(Txt.lore(List.of("<gray><i>Curiosidad de Pip · solo hoy.")));
        it.setItemMeta(meta);
        int price = (int) o[2];
        return new DailyArmor.Piece(it, price, (int) o[3], price >= 2000 ? "Legendaria" : price >= 800 ? "Épica" : "Rara");
    }

    private static int pickIdx(Random r, Object[][] pool, int sum) {
        int v = r.nextInt(sum);
        for (int k = 0; k < pool.length; k++) {
            v -= (int) pool[k][4];
            if (v < 0) return k;
        }
        return 0;
    }

    // ---------------------------------------------------------------- reliquias de Sabina

    private static DailyArmor.Piece relic(Random r, long day, int i) {
        DailyArmor.Piece best = null;
        for (int t = 0; t < 25; t++) {
            DailyArmor.Piece p = (i % 2 == 0) ? DailyArmor.make(day * 13 + 7 + t, 50 + i) : weapon(r);
            if (best == null || rank(p.rarity()) > rank(best.rarity())) best = p;
            if (rank(best.rarity()) >= 2) break;
        }
        ItemMeta meta = best.item().getItemMeta();
        List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.removeIf(c -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c).contains("Solo hoy"));
        lore.add(Txt.item("<dark_purple><i>Reliquia que Sabina sacó de la Grieta."));
        meta.lore(lore);
        String base = meta.getPersistentDataContainer().get(Keys.k("lore_base"), PersistentDataType.STRING);
        if (base != null) {
            List<String> bl = new ArrayList<>();
            for (String l : base.split("\n")) if (!l.contains("Solo hoy")) bl.add(l);
            bl.add("<dark_purple><i>Reliquia que Sabina sacó de la Grieta.");
            meta.getPersistentDataContainer().set(Keys.k("lore_base"), PersistentDataType.STRING, String.join("\n", bl));
        }
        best.item().setItemMeta(meta);
        // el precio es en Corazones Corruptos
        long hearts = rank(best.rarity()) >= 3 ? 8 + r.nextInt(5) : 4 + r.nextInt(4);
        return new DailyArmor.Piece(best.item(), hearts, Math.max(10, best.level() - 5), best.rarity());
    }

    private static int rank(String rarity) {
        for (int k = 0; k < RARITY.length; k++) if (RARITY[k][1].equals(rarity)) return k;
        return 0;
    }

    // ---------------------------------------------------------------- utilidades

    private static void copyDefaults(ItemMeta meta, Material m, EquipmentSlot slot) {
        try {
            var defs = m.getDefaultAttributeModifiers(slot);
            for (var en : defs.entries()) meta.addAttributeModifier(en.getKey(), en.getValue());
        } catch (RuntimeException | LinkageError ignored) {
            // sin datos de fábrica
        }
    }

    /** Pone un encantamiento con nivel al azar (a veces uno más que el máximo normal). Devuelve 1 o 2 "puntos". */
    private static int ench(ItemMeta meta, Random r, String name, int max) {
        Enchantment e = Reg.enchant(name);
        if (e == null) return 0;
        int lvl = Math.max(1, Math.min(max, 1 + r.nextInt(Math.max(1, max))));
        if (lvl == max && max > 1 && r.nextDouble() < 0.12) lvl++;
        meta.addEnchant(e, lvl, true);
        return lvl >= max ? 2 : 1;
    }

    private static Powers.Power power(Random r, String scope) {
        List<String> ok = new ArrayList<>();
        for (var en : Powers.TYPES.entrySet()) {
            String desc = en.getValue()[4];
            boolean tagged = desc.startsWith("(");
            if (scope.isEmpty() ? !tagged : desc.startsWith(scope) || (scope.equals("(azada)") && desc.startsWith("(herramienta)"))) ok.add(en.getKey());
        }
        if (ok.isEmpty()) ok.add("critico");
        String t = ok.get(r.nextInt(ok.size()));
        String[] def = Powers.TYPES.get(t);
        double v = Math.round(Double.parseDouble(def[2]) * (0.8 + r.nextDouble() * 0.5) * 10) / 10.0;
        double ch = Math.min(1, Double.parseDouble(def[3]) * (ch(r)));
        return new Powers.Power(t, v, ch);
    }

    private static double ch(Random r) {
        return 0.9 + r.nextDouble() * 0.4;
    }

    private static void attr(ItemMeta meta, Attribute a, String id, double v, boolean scalar, EquipmentSlotGroup g) {
        NamespacedKey key = Keys.k("dia_" + id);
        meta.addAttributeModifier(a, new AttributeModifier(key, v, scalar ? AttributeModifier.Operation.ADD_SCALAR : AttributeModifier.Operation.ADD_NUMBER, g));
    }

    private static void finish(ItemMeta meta, String name, int rarity, List<String> lore, String where) {
        meta.displayName(Txt.item(name));
        List<String> full = new ArrayList<>();
        full.add("<dark_gray>Pieza única · " + RARITY[rarity][1]);
        full.addAll(lore);
        full.add("<gray><i>" + where);
        List<String> withRunes = new ArrayList<>(full);
        withRunes.addAll(Powers.runeLore(meta));
        meta.lore(Txt.lore(withRunes));
        if (rarity >= 2) meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(Keys.k("nombre_base"), PersistentDataType.STRING, GsonComponentSerializer.gson().serialize(Txt.item(name)));
        meta.getPersistentDataContainer().set(Keys.k("lore_base"), PersistentDataType.STRING, String.join("\n", full));
    }

    private static int rarity(int perks) {
        return perks >= 10 ? 3 : perks >= 7 ? 2 : perks >= 4 ? 1 : 0;
    }

    private static String weighted(Random r, String[][] opts) {
        int sum = 0;
        for (String[] o : opts) sum += Integer.parseInt(o[1]);
        int v = r.nextInt(sum);
        for (String[] o : opts) {
            v -= Integer.parseInt(o[1]);
            if (v < 0) return o[0];
        }
        return opts[0][0];
    }

    private static List<Integer> shuffled(Random r, int n) {
        List<Integer> l = new ArrayList<>();
        for (int k = 0; k < n; k++) l.add(k);
        java.util.Collections.shuffle(l, r);
        return l;
    }

    private static long round10(double v) {
        return Math.max(10, Math.round(v / 10.0) * 10);
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
