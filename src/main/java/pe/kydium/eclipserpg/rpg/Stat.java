package pe.kydium.eclipserpg.rpg;

import org.bukkit.Material;

/**
 * Atributos al estilo Elden Ring / Dark Souls. Los ids viejos se conservan para no perder puntos:
 * vida = Vigor, defensa = Aguante, agilidad = Destreza, fortuna = Arcano.
 */
public enum Stat {
    VIDA("vida", "<red>❤ Vigor", Material.GOLDEN_APPLE, 1.0, 30, "Más vida máxima."),
    MENTE("mente", "<aqua>👁 Mente", Material.ENDER_EYE, 2.0, 20, "Los poderes de tus armas saltan más seguido y se recargan antes."),
    DEFENSA("defensa", "<green>🛡 Aguante", Material.IRON_CHESTPLATE, 0.5, 20, "Armadura, menos empuje al recibir golpes y menos hambre."),
    FUERZA("fuerza", "<gold>⚔ Fuerza", Material.IRON_AXE, 0.5, 20, "Más daño cuerpo a cuerpo; aún más con hachas y mazas."),
    AGILIDAD("agilidad", "<yellow>➶ Destreza", Material.BOW, 1.0, 15, "Velocidad al moverte y atacar; más daño con arcos y tridentes."),
    INTELIGENCIA("inteligencia", "<blue>✦ Inteligencia", Material.ENCHANTED_BOOK, 4.0, 20, "Más daño de los poderes mágicos de tus armas (rayo, cadena, sangrado…)."),
    FE("fe", "<white>☀ Fe", Material.TOTEM_OF_UNDYING, 3.0, 20, "Te curas más y haces más daño a los Corrompidos."),
    FORTUNA("fortuna", "<light_purple>💎 Arcano", Material.AMETHYST_SHARD, 3.0, 15, "Más Soles y botín, y más Corazones Corruptos.");

    public final String id;
    public final String display;
    public final Material icon;
    public final double defPerPoint;
    public final int defMax;
    public final String summary;

    Stat(String id, String display, Material icon, double defPerPoint, int defMax, String summary) {
        this.id = id;
        this.display = display;
        this.icon = icon;
        this.defPerPoint = defPerPoint;
        this.defMax = defMax;
        this.summary = summary;
    }
}
