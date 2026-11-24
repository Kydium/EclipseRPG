package pe.kydium.eclipserpg.story;

import org.bukkit.configuration.ConfigurationSection;
import pe.kydium.eclipserpg.EclipseRPG;

/**
 * Cómo se llaman y cómo se ven los monstruos "mejorados" de cada historia (mundo.yml &gt; tema).
 * En Crónicas del Eclipse son los Corrompidos (venas moradas); otra historia puede tener los suyos.
 *
 * <pre>
 * tema:
 *   venas: false                # true = texturas con venas moradas de los Corrompidos
 *   enemigos: Velados           # plural, para los textos
 *   masculino: Velado
 *   femenino: Velada
 *   color: gold                 # color del nombre (MiniMessage)
 *   icono: "☩"
 *   esencia: jiron_de_velo      # objeto común que sueltan (id de objetos.yml)
 *   corazon: lagrima_de_corona  # objeto raro que sueltan (y dan los jefes)
 *   aviso-corazon: "texto la primera vez que te sale uno"
 * </pre>
 */
public record Theme(boolean veins, String plural, String male, String female, String color, String icon,
                    String essence, String heart, String heartName, String heartTip) {

    public static final Theme ECLIPSE = new Theme(true, "Corrompidos", "Corrompido", "Corrompida", "dark_purple", "☠",
            "esencia_corrupta", "corazon_corrupto", "Corazón Corrupto",
            "<light_purple>💎 Sabina la Coleccionista, junto a la fuente de la aldea, cambia Corazones y Esencias Corruptas por objetos raros.");

    public static Theme read(EclipseRPG plugin) {
        if (plugin.story() == null) return ECLIPSE;
        ConfigurationSection s = plugin.story().yaml("mundo").getConfigurationSection("tema");
        if (s == null) return ECLIPSE;
        Theme d = ECLIPSE;
        return new Theme(s.getBoolean("venas", d.veins()), s.getString("enemigos", d.plural()), s.getString("masculino", d.male()),
                s.getString("femenino", s.getString("masculino", d.female())), s.getString("color", d.color()), s.getString("icono", d.icon()),
                s.getString("esencia", d.essence()), s.getString("corazon", d.heart()), s.getString("nombre-corazon", d.heartName()),
                s.getString("aviso-corazon", ""));
    }

    /** "Zombi Velado", "Araña Velada"… */
    public String adjective(String base) { return base.endsWith("a") ? female : male; }

    /** Cambia "Corrompidos" por el nombre de esta historia en un texto fijo. */
    public String words(String text) {
        return text.replace("Corrompidos", plural).replace("Corrompido", male).replace("Corrompida", female);
    }
}
