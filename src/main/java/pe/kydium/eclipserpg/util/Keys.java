package pe.kydium.eclipserpg.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Claves de datos persistentes (PDC) que usa el plugin. */
public final class Keys {
    public static NamespacedKey ITEM;     // id del objeto personalizado
    public static NamespacedKey UPGRADE;  // nivel de mejora (+1..+5)
    public static NamespacedKey MOB;      // id del mob personalizado o "natural"
    public static NamespacedKey TIER;     // tier del Corrompido
    public static NamespacedKey RIFT;     // id de la Grieta a la que pertenece
    public static NamespacedKey PHASE;    // el jefe ya entró en fase 2
    public static NamespacedKey BOSS_HP;  // multiplicador de vida aplicado
    public static NamespacedKey DMG_DIV;  // divisor de daño (vida sobre 1024)
    public static NamespacedKey ENRAGED;  // el jefe entró en furia
    public static NamespacedKey DUNGEON;  // mob de una mazmorra (acto)
    public static NamespacedKey NPC;      // NPC de la historia (id)
    public static NamespacedKey FILLER;   // NPC de relleno (rol)
    public static NamespacedKey HOME;     // casa del NPC (x,y,z)

    private Keys() {}

    public static void init(Plugin p) {
        ITEM = new NamespacedKey(p, "item");
        UPGRADE = new NamespacedKey(p, "mejora");
        MOB = new NamespacedKey(p, "mob");
        TIER = new NamespacedKey(p, "tier");
        RIFT = new NamespacedKey(p, "grieta");
        PHASE = new NamespacedKey(p, "fase");
        BOSS_HP = new NamespacedKey(p, "vida_mult");
        DMG_DIV = new NamespacedKey(p, "div_danio");
        ENRAGED = new NamespacedKey(p, "furia");
        DUNGEON = new NamespacedKey(p, "mazmorra");
        NPC = new NamespacedKey(p, "npc");
        FILLER = new NamespacedKey(p, "relleno");
        HOME = new NamespacedKey(p, "casa");
    }
}
