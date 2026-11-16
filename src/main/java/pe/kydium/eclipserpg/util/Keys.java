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

    /** Las claves siguen en el espacio "eclipserpg" aunque el plugin se llame Aventura Maker: así no se pierden datos guardados. */
    public static NamespacedKey k(String key) {
        return NamespacedKey.fromString("eclipserpg:" + key);
    }

    public static void init(Plugin p) {
        ITEM = k("item");
        UPGRADE = k("mejora");
        MOB = k("mob");
        TIER = k("tier");
        RIFT = k("grieta");
        PHASE = k("fase");
        BOSS_HP = k("vida_mult");
        DMG_DIV = k("div_danio");
        ENRAGED = k("furia");
        DUNGEON = k("mazmorra");
        NPC = k("npc");
        FILLER = k("relleno");
        HOME = k("casa");
    }
}
