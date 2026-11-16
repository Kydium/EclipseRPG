package pe.kydium.eclipserpg.story;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Diccionario español → ids de Minecraft (motor/diccionario.txt). Sin dependencias de Bukkit. */
public final class Dictionary {

    private static final Set<String> STOP = Set.of("de", "del", "la", "las", "el", "los", "a", "al", "un", "una", "unos", "unas",
            "con", "y", "en", "para", "por", "su", "sus", "mas");

    private final Map<String, Map<String, List<String>>> sections = new HashMap<>();
    private final Map<String, Map<String, String>> display = new HashMap<>();

    public static Dictionary read(Reader in) throws IOException {
        Dictionary d = new Dictionary();
        BufferedReader r = new BufferedReader(in);
        String sec = "objetos", line;
        while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                sec = line.substring(1, line.length() - 1).trim();
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            List<String> ids = new ArrayList<>();
            for (String id : line.substring(eq + 1).split(",")) if (!id.isBlank()) ids.add(id.trim().toLowerCase(Locale.ROOT));
            for (String name : line.substring(0, eq).split(",")) {
                if (name.isBlank()) continue;
                d.sections.computeIfAbsent(sec, k -> new LinkedHashMap<>()).putIfAbsent(key(name), ids);
                d.display.computeIfAbsent(sec, k -> new LinkedHashMap<>()).putIfAbsent(key(name), name.trim());
            }
        }
        return d;
    }

    /** minúsculas, sin tildes, sin signos. */
    public static String norm(String s) {
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        n = n.replace('ñ', 'n').replaceAll("[^a-z0-9_*:\\- ]", " ");
        return n.replaceAll("\\s+", " ").trim();
    }

    /** clave de búsqueda: normalizado y sin palabras vacías. */
    public static String key(String s) {
        StringBuilder b = new StringBuilder();
        for (String w : norm(s).split(" ")) {
            if (w.isEmpty() || STOP.contains(w)) continue;
            if (b.length() > 0) b.append(' ');
            b.append(w);
        }
        return b.toString();
    }

    private static List<String> forms(String w) {
        List<String> out = new ArrayList<>();
        out.add(w);
        if (w.length() > 3 && w.endsWith("s")) out.add(w.substring(0, w.length() - 1));
        if (w.length() > 4 && w.endsWith("es")) out.add(w.substring(0, w.length() - 2));
        if (w.length() > 4 && w.endsWith("ces")) out.add(w.substring(0, w.length() - 3) + "z");
        return out;
    }

    /** Busca un nombre en una sección. Acepta plurales ("lingotes de hierro", "peces"). */
    public List<String> lookup(String section, String phrase) {
        Map<String, List<String>> m = sections.getOrDefault(section, Map.of());
        String k = key(phrase);
        if (k.isEmpty()) return null;
        List<String> hit = m.get(k);
        if (hit != null) return hit;
        String[] ws = k.split(" ");
        if (ws.length > 4) return null;
        List<String> combos = new ArrayList<>(List.of(""));
        for (String w : ws) {
            List<String> next = new ArrayList<>();
            for (String c : combos) for (String f : forms(w)) next.add(c.isEmpty() ? f : c + " " + f);
            combos = next;
        }
        for (String c : combos) {
            hit = m.get(c);
            if (hit != null) return hit;
        }
        return null;
    }

    /** id "crudo" escrito por el usuario (iron_ingot, minecraft:iron_ingot). */
    public static String rawId(String phrase) {
        String n = phrase.trim().toLowerCase(Locale.ROOT);
        if (n.startsWith("minecraft:")) n = n.substring(10);
        return n.matches("[a-z0-9_*]+") ? n : null;
    }

    /** El nombre más parecido en la sección (para sugerir cuando no se entiende algo). */
    public String suggest(String section, String phrase) {
        String k = key(phrase);
        String best = null;
        int bd = Integer.MAX_VALUE;
        for (Map.Entry<String, String> e : display.getOrDefault(section, Map.of()).entrySet()) {
            int d = lev(k, e.getKey());
            if (d < bd) {
                bd = d;
                best = e.getValue();
            }
        }
        return best != null && bd <= Math.max(3, k.length() / 3) ? best : null;
    }

    public static int lev(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int c = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + c);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    public int size(String section) {
        return sections.getOrDefault(section, Map.of()).size();
    }
}
