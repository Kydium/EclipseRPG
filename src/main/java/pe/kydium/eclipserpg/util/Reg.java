package pe.kydium.eclipserpg.util;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.Locale;

/** Búsquedas en los registros de Minecraft a partir de texto de configuración. */
public final class Reg {
    private Reg() {}

    public static NamespacedKey key(String k) {
        if (k == null) return null;
        String s = k.toLowerCase(Locale.ROOT).trim();
        if (s.contains(":")) return NamespacedKey.fromString(s);
        return NamespacedKey.minecraft(s);
    }

    public static Enchantment enchant(String k) {
        NamespacedKey nk = key(k);
        return nk == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(nk);
    }

    public static Attribute attribute(String k) {
        NamespacedKey nk = key(k);
        return nk == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.ATTRIBUTE).get(nk);
    }

    public static PotionEffectType effect(String k) {
        NamespacedKey nk = key(k);
        return nk == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT).get(nk);
    }

    public static PotionType potion(String k) {
        NamespacedKey nk = key(k);
        return nk == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.POTION).get(nk);
    }

    public static EntityType entity(String k) {
        NamespacedKey nk = key(k);
        return nk == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENTITY_TYPE).get(nk);
    }

    public static EquipmentSlotGroup slot(String s) {
        if (s == null) return EquipmentSlotGroup.ANY;
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "mainhand", "mano" -> EquipmentSlotGroup.MAINHAND;
            case "offhand", "mano2" -> EquipmentSlotGroup.OFFHAND;
            case "hand", "manos" -> EquipmentSlotGroup.HAND;
            case "head", "casco" -> EquipmentSlotGroup.HEAD;
            case "chest", "pecho" -> EquipmentSlotGroup.CHEST;
            case "legs", "piernas" -> EquipmentSlotGroup.LEGS;
            case "feet", "pies" -> EquipmentSlotGroup.FEET;
            case "armor", "armadura" -> EquipmentSlotGroup.ARMOR;
            default -> EquipmentSlotGroup.ANY;
        };
    }

    public static AttributeModifier.Operation operation(String s) {
        if (s == null) return AttributeModifier.Operation.ADD_NUMBER;
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "add_scalar", "porcentaje", "add_multiplied_base" -> AttributeModifier.Operation.ADD_SCALAR;
            case "multiply", "multiplicar", "add_multiplied_total" -> AttributeModifier.Operation.MULTIPLY_SCALAR_1;
            default -> AttributeModifier.Operation.ADD_NUMBER;
        };
    }
}
