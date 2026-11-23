package pe.kydium.eclipserpg.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Íconos dibujados (paquete de recursos, letra eclipse:iconos) que reemplazan a los símbolos y emojis
 * que Minecraft muestra borrosos o como cuadritos. Se aplican solos en {@link Txt#mm}.
 */
public final class Icons {

    /** Nombres de los dibujos (motor/hud/iconos/*.png), en el orden de sus letras (…). */
    public static final List<String> NAMES = List.of("sol", "espada", "libro", "brujula", "estrella", "calavera", "corazon", "mapa",
            "escudo", "ciudad", "check", "cruz", "reloj", "cofre", "pergamino", "gema", "llama", "nivel", "alerta", "punto",
            "persona", "ojo", "trofeo");

    private static final Map<Integer, String> BY_CHAR = new LinkedHashMap<>();
    /** Se activa si el servidor manda el paquete de recursos. */
    public static volatile boolean enabled = false;

    static {
        put("☀", "sol");
        put("✔", "check");
        put("✓", "check");
        put("✖", "cruz");
        put("✘", "cruz");
        put("⚔", "espada");
        put("🗡", "espada");
        put("🪓", "espada");
        put("☠", "calavera");
        put("💀", "calavera");
        put("✦", "estrella");
        put("★", "estrella");
        put("✹", "estrella");
        put("❗", "alerta");
        put("⚠", "alerta");
        put("✚", "corazon");
        put("❤", "corazon");
        put("🩸", "corazon");
        put("🍖", "corazon");
        put("🗺", "mapa");
        put("🧭", "brujula");
        put("📖", "libro");
        put("📜", "pergamino");
        put("💬", "pergamino");
        put("🛡", "escudo");
        put("⛨", "escudo");
        put("🔒", "escudo");
        put("🏰", "ciudad");
        put("⏳", "reloj");
        put("⌛", "reloj");
        put("🔥", "llama");
        put("💥", "llama");
        put("💎", "gema");
        put("🧲", "gema");
        put("🌱", "gema");
        put("🌾", "gema");
        put("💰", "cofre");
        put("🎁", "cofre");
        put("🛒", "cofre");
        put("🎒", "cofre");
        put("👁", "ojo");
        put("🌀", "ojo");
        put("🎣", "punto");
        put("🏆", "trofeo");
    }

    private static void put(String emoji, String name) {
        BY_CHAR.put(emoji.codePointAt(0), name);
    }

    private Icons() {}

    public static char glyph(String name) {
        int i = NAMES.indexOf(name);
        return (char) (0xE100 + Math.max(0, i));
    }

    /** "<font:eclipse:iconos><white>X</white></font>" (blanco para que el color del texto no lo tiña). */
    public static String tag(String name) {
        return "<font:eclipse:iconos><white>" + glyph(name) + "</white></font>";
    }

    /** Reemplaza los emojis y símbolos conocidos por los dibujos (si el paquete está activo). */
    public static String apply(String s) {
        if (!enabled || s == null || s.isEmpty()) return s;
        StringBuilder b = null;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            int n = Character.charCount(cp);
            String name = BY_CHAR.get(cp);
            if (name != null) {
                if (b == null) b = new StringBuilder(s.length() + 64).append(s, 0, i);
                b.append(tag(name));
                // se come el selector de variante (U+FE0F) que traen algunos emojis
                if (i + n < s.length() && s.charAt(i + n) == '️') n++;
            } else if (b != null) b.appendCodePoint(cp);
            i += n;
        }
        return b == null ? s : b.toString();
    }

    /** JSON de la letra para el paquete de recursos. */
    public static String fontJson() {
        return "{\"providers\": [" + providers(8) + "]}";
    }

    /** Proveedores de los íconos con la altura indicada (el panel los baja con ascent negativo). */
    public static String providers(int ascent) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < NAMES.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("{\"type\": \"bitmap\", \"file\": \"eclipse:hud/iconos/").append(NAMES.get(i))
                    .append(".png\", \"ascent\": ").append(ascent).append(", \"height\": 9, \"chars\": [\"\\u").append(String.format("%04x", 0xE100 + i)).append("\"]}");
        }
        return sb.toString();
    }
}
