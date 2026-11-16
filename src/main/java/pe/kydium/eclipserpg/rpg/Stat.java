package pe.kydium.eclipserpg.rpg;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;

/** Habilidades en las que el jugador gasta sus puntos. */
public enum Stat {
    VIDA("vida", "<red>❤ Vida", Material.GOLDEN_APPLE),
    FUERZA("fuerza", "<gold>⚔ Fuerza", Material.IRON_SWORD),
    DEFENSA("defensa", "<aqua>🛡 Defensa", Material.IRON_CHESTPLATE),
    AGILIDAD("agilidad", "<green>➶ Agilidad", Material.FEATHER),
    FORTUNA("fortuna", "<yellow>☀ Fortuna", Material.GOLD_INGOT);

    public final String id;
    public final String display;
    public final Material icon;

    Stat(String id, String display, Material icon) {
        this.id = id;
        this.display = display;
        this.icon = icon;
    }

    /** Atributo vanilla que modifica (null = efecto propio del plugin). */
    public Attribute attribute() {
        return switch (this) {
            case VIDA -> Attribute.MAX_HEALTH;
            case FUERZA -> Attribute.ATTACK_DAMAGE;
            case DEFENSA -> Attribute.ARMOR;
            case AGILIDAD -> Attribute.MOVEMENT_SPEED;
            case FORTUNA -> null;
        };
    }

    public AttributeModifier.Operation operation() {
        return this == AGILIDAD ? AttributeModifier.Operation.ADD_SCALAR : AttributeModifier.Operation.ADD_NUMBER;
    }
}
