package pe.kydium.eclipserpg.util;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Utilidades de texto (MiniMessage) y sonido. */
public final class Txt {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    public static String PREFIX = "<gold>☀</gold> <yellow>Eclipse</yellow> <dark_gray>»</dark_gray> ";

    private Txt() {}

    public static Component mm(String s) {
        return MM.deserialize(s == null ? "" : Icons.apply(s));
    }

    /** Texto para nombres y lore de objetos: sin cursiva por defecto. */
    public static Component item(String s) {
        return MM.deserialize("<!italic>" + (s == null ? "" : Icons.apply(s)));
    }

    public static List<Component> lore(List<String> lines) {
        List<Component> out = new ArrayList<>();
        if (lines != null) for (String l : lines) out.add(item(l));
        return out;
    }

    public static void send(CommandSender s, String msg) {
        s.sendMessage(mm(PREFIX + msg));
    }

    public static String num(long n) {
        return String.format(Locale.US, "%,d", n);
    }

    public static String dec(double d) {
        if (d == Math.rint(d)) return String.valueOf((long) d);
        return String.format(Locale.US, "%.1f", d);
    }

    public static void sound(Player p, String key, float volume, float pitch) {
        try {
            p.playSound(Sound.sound(Key.key(key), Sound.Source.PLAYER, volume, pitch));
        } catch (Exception ignored) {
            // clave de sonido inválida: se ignora
        }
    }

    /** "ZOMBIE_VILLAGER" -> "Zombie villager" */
    public static String pretty(String enumName) {
        String s = enumName.toLowerCase(Locale.ROOT).replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
