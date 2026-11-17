package pe.kydium.eclipserpg.story;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lee historia.txt (español sencillo) y lo convierte en una StoryDoc, juntando todos los
 * problemas con su número de línea. No depende de Bukkit: el catálogo dice qué existe.
 */
public final class StoryParser {

    /** Lo que existe en el juego / en la historia (lo aporta el lado de Bukkit). */
    public interface Catalog {
        boolean isItem(String id);

        boolean isBlock(String id);

        boolean isEntity(String id);

        /** id de mob personalizado → nombre visible (sin colores). */
        Map<String, String> customMobs();

        /** id de objeto personalizado → nombre visible (sin colores). */
        Map<String, String> customItems();

        /** acto → nombre de su mazmorra (sin colores). */
        Map<Integer, String> dungeons();

        Set<String> shops();

        Set<String> skins();

        Set<String> places();

        /** ids de las estructuras propias de la historia. */
        default Set<String> structures() {
            return Set.of();
        }
    }

    public record Problem(int line, boolean error, String message) {
        @Override
        public String toString() {
            return (error ? "ERROR" : "AVISO") + (line > 0 ? " (línea " + line + ")" : "") + ": " + message;
        }
    }

    public record Result(StoryDoc doc, List<Problem> problems) {
        public long errors() {
            return problems.stream().filter(Problem::error).count();
        }
    }

    private static final Map<String, String> VERBS = new HashMap<>();

    static {
        put("MATAR", "matar mata mate mata a elimina eliminar elimine caza cazar acaba acabar derrota derrotar derrote vence vencer venza asesina");
        put("MINAR", "minar mina mine picar pica romper rompe talar tala tale cavar cava cosechar cosecha recolectar recolecta extraer extrae");
        put("TENER", "tener ten tenga conseguir consigue consiga reunir reune junta juntar obtener obten obtenga craftear craftea fabricar fabrica crear crea forjar forja construir construye");
        put("ENTREGAR", "entregar entrega entregue traer trae traiga llevar lleva lleve dar da de");
        put("FUNDIR", "fundir funde funda cocinar cocina cocine hornear hornea asar asa");
        put("PESCAR", "pescar pesca pesque");
        put("DOMESTICAR", "domesticar domestica domar doma adoptar adopta");
        put("ENCANTAR", "encantar encanta encante");
        put("DIMENSION", "entrar entra entre ir ve vaya viajar viaja visitar visita llegar llega cruzar cruza");
        put("HABLAR", "hablar habla hable conversar conversa visitar_npc");
        put("MAZMORRA", "encontrar encuentra encuentre explorar explora descubrir descubre");
    }

    private static void put(String type, String words) {
        for (String w : words.split(" ")) VERBS.putIfAbsent(w, type);
    }

    private static final Map<String, Integer> NUMBERS = Map.ofEntries(
            Map.entry("un", 1), Map.entry("una", 1), Map.entry("uno", 1), Map.entry("dos", 2), Map.entry("tres", 3),
            Map.entry("cuatro", 4), Map.entry("cinco", 5), Map.entry("seis", 6), Map.entry("siete", 7), Map.entry("ocho", 8),
            Map.entry("nueve", 9), Map.entry("diez", 10), Map.entry("once", 11), Map.entry("doce", 12), Map.entry("quince", 15),
            Map.entry("veinte", 20), Map.entry("treinta", 30), Map.entry("cuarenta", 40), Map.entry("cincuenta", 50),
            Map.entry("cien", 100));

    private static final Map<String, String> COLORS = Map.ofEntries(
            Map.entry("verde", "green"), Map.entry("verde oscuro", "dark_green"), Map.entry("rojo", "red"),
            Map.entry("rojo oscuro", "dark_red"), Map.entry("azul", "blue"), Map.entry("azul oscuro", "dark_blue"),
            Map.entry("celeste", "aqua"), Map.entry("aqua", "aqua"), Map.entry("turquesa", "dark_aqua"),
            Map.entry("amarillo", "yellow"), Map.entry("dorado", "gold"), Map.entry("naranja", "gold"),
            Map.entry("morado", "dark_purple"), Map.entry("violeta", "dark_purple"), Map.entry("rosa", "light_purple"),
            Map.entry("blanco", "white"), Map.entry("gris", "gray"), Map.entry("gris oscuro", "dark_gray"),
            Map.entry("negro", "black"));

    private final Dictionary dict;
    private final Catalog cat;
    private final List<Problem> problems = new ArrayList<>();
    private final StoryDoc doc = new StoryDoc();
    private final Map<StoryDoc.Objective, String> pendingTalk = new HashMap<>();

    public StoryParser(Dictionary dict, Catalog cat) {
        this.dict = dict;
        this.cat = cat;
    }

    private void error(int line, String msg) {
        problems.add(new Problem(line, true, msg));
    }

    private void warn(int line, String msg) {
        problems.add(new Problem(line, false, msg));
    }

    public Result parse(List<String> lines) {
        StoryDoc.Npc npc = null;
        StoryDoc.Mission mission = null;
        int currentAct = 1;
        for (int i = 0; i < lines.size(); i++) {
            int ln = i + 1;
            String raw = lines.get(i).replace("\t", "    ").replace("﻿", "");
            String t = raw.trim();
            if (t.isEmpty() || t.startsWith("#") || t.startsWith("//")) continue;
            String head = Dictionary.norm(t).replace(":", " ").replaceAll("\\s+", " ").trim();

            // ---------- encabezados ----------
            if (head.startsWith("historia ") && t.contains(":")) {
                doc.title = after(t);
                npc = null;
                mission = null;
                continue;
            }
            if (head.startsWith("descripcion") && t.contains(":")) {
                doc.description = after(t);
                continue;
            }
            if (head.startsWith("aldea") && t.contains(":")) {
                doc.village = after(t);
                continue;
            }
            if (head.startsWith("moneda") && t.contains(":")) {
                doc.currency = after(t);
                continue;
            }
            if (head.startsWith("acto ")) {
                String rest = t.substring(4).trim();
                int colon = rest.indexOf(':');
                String num = (colon < 0 ? rest : rest.substring(0, colon)).trim();
                try {
                    currentAct = Integer.parseInt(num);
                    doc.acts.put(currentAct, colon < 0 ? "Acto " + currentAct : "Acto " + currentAct + " — " + rest.substring(colon + 1).trim());
                } catch (NumberFormatException e) {
                    error(ln, "El acto debe tener número, por ejemplo:  ACTO 2: Las Minas");
                }
                npc = null;
                mission = null;
                continue;
            }
            if (head.startsWith("npc ") && !raw.startsWith(" ")) {
                String rest = t.substring(4).trim();
                String id = slug(rest.contains(":") ? rest.substring(0, rest.indexOf(':')) : rest);
                if (id.isEmpty()) {
                    error(ln, "Falta el nombre corto del NPC, por ejemplo:  NPC herrero");
                    continue;
                }
                if (doc.npcs.containsKey(id)) error(ln, "Ya existe un NPC llamado '" + id + "' (línea " + doc.npcs.get(id).line + ")");
                npc = new StoryDoc.Npc();
                npc.line = ln;
                npc.id = id;
                npc.name = rest.contains(":") ? rest.substring(rest.indexOf(':') + 1).trim() : capital(rest);
                doc.npcs.put(id, npc);
                mission = null;
                continue;
            }
            if ((head.startsWith("mision ") || head.startsWith("mision:")) && !raw.startsWith(" ")) {
                String name = t.substring(6).replaceFirst("^[:\\s]+", "").trim();
                if (name.isEmpty()) {
                    error(ln, "La misión necesita un título, por ejemplo:  MISIÓN El mapa robado");
                    name = "Misión sin título";
                }
                mission = new StoryDoc.Mission();
                mission.line = ln;
                mission.name = name;
                mission.act = currentAct;
                mission.id = String.format(Locale.ROOT, "m%03d", doc.missions.size() + 1);
                doc.missions.add(mission);
                npc = null;
                continue;
            }

            // ---------- campos "clave: valor" ----------
            int colon = t.indexOf(':');
            if (colon < 0) {
                error(ln, "No entiendo esta línea. Debe ser 'campo: valor' dentro de un NPC o una MISIÓN: \"" + cut(t) + "\"");
                continue;
            }
            String key = Dictionary.norm(t.substring(0, colon));
            String val = t.substring(colon + 1).trim();
            if (npc != null) npcField(npc, key, val, ln);
            else if (mission != null) missionField(mission, key, val, ln);
            else error(ln, "Este campo está fuera de un NPC o MISIÓN: \"" + cut(t) + "\"");
        }
        finish();
        return new Result(doc, problems);
    }

    // ---------------------------------------------------------------- NPC

    private void npcField(StoryDoc.Npc n, String key, String val, int ln) {
        switch (key) {
            case "nombre" -> n.name = val;
            case "color" -> {
                String c = COLORS.get(Dictionary.norm(val));
                if (c == null && val.matches("#?[0-9a-fA-F]{6}")) c = val.startsWith("#") ? val : "#" + val;
                if (c == null) error(ln, "Color desconocido '" + val + "'. Usa: " + String.join(", ", COLORS.keySet()));
                else n.color = c;
            }
            case "skin", "aspecto", "apariencia" -> {
                String s = Dictionary.norm(val).replace(' ', '_');
                if (!cat.skins().contains(s)) {
                    String sug = closest(s, cat.skins());
                    error(ln, "No hay una skin llamada '" + val + "'." + (sug != null ? " ¿Quisiste decir '" + sug + "'?" : "")
                            + " Mira la lista con /historia skins o pon tu propia imagen en la carpeta skins/");
                } else n.skin = s;
            }
            case "oficio", "rol", "titulo", "profesion" -> n.role = val;
            case "lugar", "puesto", "sitio" -> {
                String p = Dictionary.norm(val).replace(' ', '_');
                if (!cat.places().contains(p)) {
                    String sug = closest(p, cat.places());
                    error(ln, "No hay un lugar llamado '" + val + "' en la aldea." + (sug != null ? " ¿Quisiste decir '" + sug + "'?" : "")
                            + " Lugares: " + String.join(", ", cat.places()) + ". (También puedes usar /historia colocar)");
                } else n.place = p;
            }
            case "objeto", "en la mano", "mano", "lleva" -> n.hand = itemId(val, ln, true);
            case "otra mano", "mano izquierda", "escudo" -> n.offhand = itemId(val, ln, true);
            case "servicio", "servicios", "ofrece" -> {
                for (String s : val.split(",")) {
                    String k = Dictionary.norm(s);
                    if (k.isEmpty()) continue;
                    if (k.startsWith("tienda")) {
                        String shop = k.substring(6).trim().replace(' ', '_');
                        if (shop.isEmpty()) error(ln, "Indica qué tienda: 'servicio: tienda herreria'. Tiendas: " + String.join(", ", cat.shops()));
                        else if (!cat.shops().contains(shop)) error(ln, "No existe la tienda '" + shop + "'. Tiendas: " + String.join(", ", cat.shops()));
                        else n.services.add("tienda:" + shop);
                    } else if (k.startsWith("contrato")) n.services.add("contratos");
                    else if (k.startsWith("grieta")) n.services.add("grieta");
                    else error(ln, "Servicio desconocido '" + s.trim() + "'. Usa: tienda <nombre>, contratos, grieta");
                }
            }
            case "charla", "dice", "frase" -> n.talk.add(val);
            case "combate", "pelea", "rol de combate" -> {
                String k = Dictionary.norm(val);
                if (k.startsWith("guardi") || k.equals("defensor")) n.combat = "guardian";
                else if (k.equals("cobarde") || k.equals("huye")) n.combat = "cobarde";
                else if (k.equals("neutral") || k.equals("ninguno") || k.equals("no")) n.combat = null;
                else error(ln, "combate: no entiendo '" + val + "'. Usa: guardián, cobarde o neutral");
            }
            case "vida" -> n.combatHp = Math.max(1, intVal(val, ln, 40));
            case "dano", "danio", "ataque" -> n.combatDamage = Math.max(0, intVal(val, ln, 6));
            default -> error(ln, "El NPC no tiene un campo '" + key + "'. Campos: nombre, color, skin, oficio, lugar, objeto, servicio, charla, combate, vida, daño");
        }
    }

    // ---------------------------------------------------------------- MISIÓN

    private void missionField(StoryDoc.Mission m, String key, String val, int ln) {
        switch (key) {
            case "acto" -> m.act = intVal(val, ln, m.act);
            case "npc", "da", "la da", "lo da", "quien", "quien la da", "personaje" -> m.npc = val;
            case "nivel", "nivel minimo" -> m.level = Math.max(1, intVal(val, ln, 1));
            case "dice", "dialogo", "inicio", "texto" -> m.start.add(val);
            case "final", "al terminar", "termina", "al entregar", "fin" -> m.end.add(val);
            case "recuerda", "recordatorio", "pista" -> m.reminder.add(val);
            case "responde", "respuesta", "respuestas", "aceptar", "acepta", "yo digo", "jugador", "jugador dice", "contesto", "contesta" -> m.replies.add(val);
            case "rechaza", "rechazar", "no acepta", "negarse" -> m.decline = val;
            case "objetivo", "objetivos", "tarea" -> {
                StoryDoc.Objective o = objective(val, ln);
                if (o != null) m.objectives.add(o);
            }
            case "aparecen", "aparece", "enemigos" -> {
                for (String s : val.split(",")) {
                    String sp = spawn(s.trim(), ln);
                    if (sp != null) m.spawns.add(sp);
                }
            }
            case "recibe", "al aceptar", "le dan" -> rewards(val, ln, m, m.receive, false);
            case "premio", "premios", "recompensa", "recompensas" -> rewards(val, ln, m, m.rewards, true);
            case "jefe" -> m.boss = bool(val);
            case "sin morir", "no morir" -> m.noDeath = bool(val);
            case "mazmorra" -> {
                m.dungeon = intVal(val, ln, 0);
                if (m.dungeon > 0 && !cat.dungeons().containsKey(m.dungeon))
                    error(ln, "No existe la mazmorra " + m.dungeon + ". Hay mazmorras para los actos " + cat.dungeons().keySet());
            }
            case "eleccion", "opcion", "decision" -> choice(val, ln, m);
            case "estructura" -> {
                m.structure = slug(val);
                if (!cat.structures().contains(m.structure))
                    error(ln, "No existe la estructura '" + val + "'. Estructuras: " + cat.structures());
            }
            case "evento" -> m.event = slug(val);
            case "clima" -> {
                String k = Dictionary.norm(val);
                if (k.equals("tormenta") || k.equals("lluvia") || k.equals("despejado")) m.weather = k;
                else error(ln, "clima: no entiendo '" + val + "'. Usa: despejado, lluvia o tormenta");
            }
            default -> error(ln, "La misión no tiene un campo '" + key + "'. Campos: npc, acto, nivel, dice, responde, rechaza, final, objetivo, premio, aparecen, recibe, jefe, mazmorra, estructura, eleccion, evento, clima");
        }
    }

    private void choice(String val, int ln, StoryDoc.Mission m) {
        StoryDoc.Choice c = new StoryDoc.Choice();
        c.line = ln;
        String[] parts = val.split("->", 2);
        c.text = parts[0].trim();
        if (parts.length > 1) {
            for (String kv : parts[1].split(";")) {
                int col = kv.indexOf(':');
                if (col < 0) continue;
                String k = Dictionary.norm(kv.substring(0, col));
                String v = kv.substring(col + 1).trim();
                switch (k) {
                    case "premio", "premios" -> rewards(v, ln, m, c.rewards, false);
                    case "mensaje", "dice" -> c.message = v;
                    case "marca" -> c.flag = slug(v);
                    default -> error(ln, "En una elección usa: -> premio: ...; mensaje: ...; marca: ...");
                }
            }
        }
        m.choices.add(c);
    }

    // ---------------------------------------------------------------- objetivos

    private StoryDoc.Objective objective(String val, int ln) {
        String shown = val;
        int bar = val.indexOf('|');
        if (bar >= 0) {
            shown = val.substring(bar + 1).trim();
            val = val.substring(0, bar).trim();
        }
        StoryDoc.Objective o = new StoryDoc.Objective();
        o.line = ln;
        o.text = capital(shown);
        List<String> words = new ArrayList<>(Arrays.asList(Dictionary.norm(val).split(" ")));
        if (words.isEmpty() || words.get(0).isEmpty()) {
            error(ln, "Objetivo vacío");
            return null;
        }
        String verb = words.remove(0);
        String type = VERBS.get(verb);
        // "derrota al rey" / "ve al nether" / "habla con bruno"
        if (type == null) {
            if (verb.equals("mazmorra") || verb.equals("completa") || verb.equals("completar")) type = "MAZMORRA";
            else {
                error(ln, "No entiendo el verbo '" + verb + "'. Usa: matar, minar, talar, tener, entregar, fundir, pescar, domesticar, encantar, ir al nether, hablar con, explorar la mazmorra");
                return null;
            }
        }
        // cantidad
        while (!words.isEmpty() && Set.of("a", "al", "el", "la", "los", "las", "con").contains(words.get(0))) words.remove(0);
        if (!words.isEmpty()) {
            Integer n = number(words.get(0));
            if (n != null) {
                o.amount = n;
                words.remove(0);
            }
        }
        // corrompidos
        boolean corrupt = words.removeIf(w -> w.startsWith("corromp") || w.startsWith("corrupt"));
        o.corrupted = corrupt;
        String rest = String.join(" ", words).trim();

        switch (type) {
            case "MATAR" -> {
                String mob = customMob(rest);
                if (mob != null) {
                    o.type = "MOB";
                    o.targets.add(mob);
                    return o;
                }
                o.type = "MATAR";
                if (rest.isEmpty()) {
                    o.targets.add("*");
                    return o;
                }
                return targets(o, rest, "mobs", ln) ? o : null;
            }
            case "DOMESTICAR" -> {
                o.type = type;
                return targets(o, rest, "mobs", ln) ? o : null;
            }
            case "MINAR", "TENER", "ENTREGAR", "FUNDIR" -> {
                o.type = type;
                if (rest.isEmpty()) {
                    error(ln, "Falta qué cosa en el objetivo: \"" + val + "\"");
                    return null;
                }
                String ci = customItem(rest);
                if (ci != null && !type.equals("MINAR")) {
                    o.targets.add("custom:" + ci);
                    return o;
                }
                return targets(o, rest, "objetos", ln) ? o : null;
            }
            case "PESCAR", "ENCANTAR" -> {
                o.type = type;
                List<String> ids = rest.isEmpty() ? null : dict.lookup("objetos", rest);
                if (ids == null || ids.contains("*")) o.targets.add("*");
                else o.targets.addAll(ids);
                return o;
            }
            case "DIMENSION" -> {
                o.type = type;
                List<String> ids = dict.lookup("dimensiones", rest);
                if (ids == null) {
                    // "entra a la mazmorra 3"
                    if (rest.contains("mazmorra")) return dungeonObjective(o, rest, ln, val);
                    error(ln, "¿A qué dimensión? Usa 'ir al nether' o 'ir al end' (escribiste \"" + val + "\")");
                    return null;
                }
                o.targets.addAll(ids);
                return o;
            }
            case "HABLAR" -> {
                o.type = type;
                if (rest.isEmpty()) {
                    error(ln, "¿Con quién? Ejemplo:  objetivo: habla con bruno");
                    return null;
                }
                pendingTalk.put(o, rest);
                return o;
            }
            case "MAZMORRA" -> {
                o.type = type;
                return dungeonObjective(o, rest, ln, val);
            }
            default -> {
                return null;
            }
        }
    }

    private StoryDoc.Objective dungeonObjective(StoryDoc.Objective o, String rest, int ln, String val) {
        // "encuentra la torre_vigia" / "explora la estructura torre vigia"
        String sr = rest.replaceFirst("^(la |el )?estructura ", "").trim().replace(' ', '_');
        if (cat.structures().contains(sr)) {
            o.type = "ESTRUCTURA";
            o.targets.add(sr);
            return o;
        }
        if (rest.contains("estructura")) {
            error(ln, "No existe la estructura '" + sr + "'. Estructuras: " + cat.structures());
            return null;
        }
        o.type = "MAZMORRA";
        for (String w : rest.split(" ")) {
            Integer n = number(w);
            if (n != null) {
                if (!cat.dungeons().containsKey(n)) error(ln, "No existe la mazmorra " + n);
                o.targets.add(String.valueOf(n));
                return o;
            }
        }
        String k = Dictionary.key(rest);
        for (Map.Entry<Integer, String> e : cat.dungeons().entrySet()) {
            if (Dictionary.key(e.getValue()).equals(k)) {
                o.targets.add(String.valueOf(e.getKey()));
                return o;
            }
        }
        error(ln, "¿Qué mazmorra? Escribe su número: 'explora la mazmorra 3' (escribiste \"" + val + "\")");
        return null;
    }

    private boolean targets(StoryDoc.Objective o, String rest, String section, int ln) {
        for (String part : rest.split(",| o ")) {
            part = part.trim();
            if (part.isEmpty()) continue;
            List<String> ids = dict.lookup(section, part);
            if (ids != null) {
                o.targets.addAll(ids);
                continue;
            }
            String rawId = Dictionary.rawId(part.replace(' ', '_'));
            boolean ok = rawId != null && (rawId.contains("*")
                    || (section.equals("mobs") ? cat.isEntity(rawId) : cat.isItem(rawId) || cat.isBlock(rawId)));
            if (ok) {
                o.targets.add(rawId);
                continue;
            }
            String sug = dict.suggest(section, part);
            error(ln, "No conozco '" + part + "'" + (sug != null ? ". ¿Quisiste decir '" + sug + "'?" : "")
                    + ". Puedes usar el nombre en español del diccionario o el id de Minecraft (ej. iron_ingot)");
            return false;
        }
        return !o.targets.isEmpty();
    }

    // ---------------------------------------------------------------- premios

    private static final Set<String> ENGLISH_POTIONS = Set.of("healing", "strong_healing", "harming", "strong_harming", "regeneration",
            "strong_regeneration", "long_regeneration", "strength", "strong_strength", "long_strength", "swiftness", "strong_swiftness",
            "long_swiftness", "slowness", "long_slowness", "fire_resistance", "long_fire_resistance", "night_vision", "long_night_vision",
            "invisibility", "long_invisibility", "water_breathing", "long_water_breathing", "leaping", "strong_leaping", "long_leaping",
            "slow_falling", "long_slow_falling", "poison", "strong_poison", "long_poison", "weakness", "long_weakness", "luck",
            "turtle_master", "strong_turtle_master", "long_turtle_master", "wind_charged", "weaving", "oozing", "infested");
    private static final Set<String> ENGLISH_ENCHANTS = Set.of("sharpness", "smite", "bane_of_arthropods", "knockback", "fire_aspect",
            "looting", "sweeping_edge", "efficiency", "silk_touch", "unbreaking", "fortune", "power", "punch", "flame", "infinity",
            "luck_of_the_sea", "lure", "loyalty", "impaling", "riptide", "channeling", "multishot", "quick_charge", "piercing", "mending",
            "protection", "fire_protection", "feather_falling", "blast_protection", "projectile_protection", "respiration", "aqua_affinity",
            "thorns", "depth_strider", "frost_walker", "soul_speed", "swift_sneak", "density", "breach", "wind_burst", "lunge");

    private void rewards(String val, int ln, StoryDoc.Mission m, List<String> out, boolean allowXp) {
        for (String part : val.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            List<String> w = new ArrayList<>(Arrays.asList(Dictionary.norm(p).split(" ")));
            Integer n = number(w.get(0));
            int amount = 1;
            if (n != null) {
                amount = n;
                w.remove(0);
            } else if (w.size() > 1 && number(w.get(w.size() - 1)) != null && !w.get(0).equals("libro") && !w.get(0).startsWith("enchanted")) {
                amount = number(w.remove(w.size() - 1));
            }
            String rest = String.join(" ", w);
            String cur = Dictionary.norm(doc.currency);
            if (Set.of("xp", "experiencia", "exp", "puntos experiencia", "puntos de experiencia").contains(rest)) {
                if (allowXp) m.xp += amount;
                else error(ln, "Aquí no se puede dar experiencia");
                continue;
            }
            if (rest.equals(cur) || rest.equals("monedas") || rest.equals("moneda") || rest.equals("soles") || rest.equals("sol")
                    || (cur.endsWith("s") && rest.equals(cur.substring(0, cur.length() - 1)))) {
                if (allowXp) m.money += amount;
                else error(ln, "Aquí no se puede dar dinero");
                continue;
            }
            if (rest.startsWith("objeto ") || rest.startsWith("especial ")) {
                String id = customItem(rest.substring(rest.indexOf(' ') + 1));
                if (id == null) error(ln, "No existe el objeto especial '" + rest.substring(rest.indexOf(' ') + 1) + "'. Están en objetos.yml");
                else out.add("custom:" + id + " " + amount);
                continue;
            }
            String ci = customItem(rest);
            if (ci != null) {
                out.add("custom:" + ci + " " + amount);
                continue;
            }
            // también como en los comandos, en inglés: potion of healing, healing potion, enchanted book sharpness 3
            rest = rest.replaceFirst("^minecraft:", "");
            if (rest.matches("^(splash |lingering )?potions? (of )?.+") || rest.matches(".+ potions?$"))
                rest = "pocion " + rest.replaceFirst("^(splash |lingering )?potions? (of )?", "").replaceFirst(" potions?$", "");
            if (rest.matches("^enchanted[ _]book (of )?.+")) rest = "libro " + rest.replaceFirst("^enchanted[ _]book (of )?", "");
            if (rest.startsWith("pocion") || rest.startsWith("pociones")) {
                String kind = rest.replaceFirst("^pocion(es)?( de)?", "").trim();
                if (kind.isEmpty()) kind = "curacion";
                List<String> ids = dict.lookup("pociones", kind);
                if (ids == null && kind.matches("[a-z_ ]+") && ENGLISH_POTIONS.contains(kind.replace(' ', '_'))) ids = List.of(kind.replace(' ', '_'));
                if (ids == null) error(ln, "Poción desconocida '" + kind + "'. Ejemplos: curación, fuerza, velocidad, regeneración (o en inglés: healing, strength)");
                else out.add("pocion:" + ids.get(0) + " " + amount);
                continue;
            }
            if (rest.startsWith("libro ") && !rest.equals("libro")) {
                String[] ws = rest.substring(6).replaceFirst("^de ", "").trim().split(" ");
                int lvl = 1;
                String ench = rest.substring(6).trim();
                Integer last = number(ws[ws.length - 1]);
                if (last != null && ws.length > 1) {
                    lvl = last;
                    ench = String.join(" ", Arrays.copyOf(ws, ws.length - 1));
                }
                List<String> ids = dict.lookup("encantamientos", ench);
                if (ids == null && ENGLISH_ENCHANTS.contains(ench.replace(' ', '_'))) ids = List.of(ench.replace(' ', '_'));
                if (ids == null) error(ln, "Encantamiento desconocido '" + ench + "'. Ejemplos: filo, eficiencia, protección, irrompibilidad");
                else out.add("libro:" + ids.get(0) + ":" + lvl + " " + amount);
                continue;
            }
            List<String> ids = dict.lookup("objetos", rest);
            String id = null;
            if (ids != null) {
                for (String x : ids) if (!x.contains("*")) {
                    id = x;
                    break;
                }
                if (id == null) {
                    error(ln, "'" + rest + "' es un grupo; como premio di uno concreto (ej. 'roble' → oak_log)");
                    continue;
                }
            } else {
                String r = Dictionary.rawId(rest.replace(' ', '_'));
                if (r != null && cat.isItem(r)) id = r;
            }
            if (id == null) {
                String sug = dict.suggest("objetos", rest);
                error(ln, "No conozco el premio '" + part.trim() + "'" + (sug != null ? ". ¿Quisiste decir '" + sug + "'?" : ""));
                continue;
            }
            out.add(id + " " + amount);
        }
    }

    private String spawn(String s, int ln) {
        if (s.isEmpty()) return null;
        List<String> w = new ArrayList<>(Arrays.asList(Dictionary.norm(s).split(" ")));
        int n = 1;
        Integer x = number(w.get(0));
        if (x != null) {
            n = x;
            w.remove(0);
        }
        String mob = customMob(String.join(" ", w));
        if (mob == null) {
            error(ln, "No existe el enemigo '" + String.join(" ", w) + "' en mobs.yml (aparecen: usa enemigos personalizados)");
            return null;
        }
        return mob + ":" + n;
    }

    // ---------------------------------------------------------------- utilidades

    private String customMob(String phrase) {
        return custom(phrase, cat.customMobs());
    }

    private String customItem(String phrase) {
        return custom(phrase, cat.customItems());
    }

    private static String custom(String phrase, Map<String, String> map) {
        if (phrase == null || phrase.isBlank()) return null;
        String id = Dictionary.norm(phrase).replace(' ', '_');
        if (map.containsKey(id)) return id;
        String k = Dictionary.key(phrase);
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (Dictionary.key(e.getValue()).equals(k)) return e.getKey();
        }
        // plural simple
        String ks = k.replaceAll("(es|s)$", "");
        for (Map.Entry<String, String> e : map.entrySet()) {
            String v = Dictionary.key(e.getValue());
            if (v.equals(ks) || Dictionary.key(e.getKey().replace('_', ' ')).equals(ks)) return e.getKey();
        }
        return null;
    }

    private String itemId(String val, int ln, boolean single) {
        List<String> ids = dict.lookup("objetos", val);
        if (ids != null) {
            for (String x : ids) if (!x.contains("*")) return x;
        }
        String r = Dictionary.rawId(Dictionary.norm(val).replace(' ', '_'));
        if (r != null && cat.isItem(r)) return r;
        String sug = dict.suggest("objetos", val);
        error(ln, "No conozco el objeto '" + val + "'" + (sug != null ? ". ¿Quisiste decir '" + sug + "'?" : ""));
        return null;
    }

    private void finish() {
        // NPCs que dan misiones y a los que hay que hablar
        for (StoryDoc.Mission m : doc.missions) {
            if (m.npc == null) {
                error(m.line, "La misión \"" + m.name + "\" no dice quién la da. Añade:  npc: <nombre del NPC>");
            } else {
                String id = npcRef(m.npc);
                if (id == null) error(m.line, "La misión \"" + m.name + "\" la da '" + m.npc + "', pero no hay un NPC así." + npcHint(m.npc));
                else m.npc = id;
            }
            if (m.start.isEmpty()) warn(m.line, "La misión \"" + m.name + "\" no tiene diálogo ('dice:')");
            if (m.boss && m.dungeon == 0 && m.objectives.stream().noneMatch(o -> o.type.equals("MOB") || o.type.equals("MATAR")))
                warn(m.line, "La misión de jefe \"" + m.name + "\" no tiene un objetivo para derrotar a un jefe");
            if (!doc.acts.containsKey(m.act)) doc.acts.put(m.act, "Acto " + m.act);
        }
        for (Map.Entry<StoryDoc.Objective, String> e : pendingTalk.entrySet()) {
            String id = npcRef(e.getValue());
            if (id == null) error(e.getKey().line, "No hay un NPC '" + e.getValue() + "' para hablar." + npcHint(e.getValue()));
            else e.getKey().targets.add(id);
        }
        for (StoryDoc.Npc n : doc.npcs.values()) {
            if (n.skin == null) {
                warn(n.line, "El NPC '" + n.id + "' no tiene skin: usaré una de aldeano");
                n.skin = cat.skins().contains("aldeano_1") ? "aldeano_1" : cat.skins().stream().findFirst().orElse(null);
            }
        }
        if (doc.missions.isEmpty()) error(0, "La historia no tiene ninguna MISIÓN");
        if (doc.npcs.isEmpty()) error(0, "La historia no tiene ningún NPC");
        problems.sort((a, b) -> Integer.compare(a.line(), b.line()));
    }

    private String npcRef(String ref) {
        String s = slug(ref);
        if (doc.npcs.containsKey(s)) return s;
        String k = Dictionary.key(ref);
        for (StoryDoc.Npc n : doc.npcs.values()) {
            if (Dictionary.key(n.name).equals(k)) return n.id;
        }
        for (StoryDoc.Npc n : doc.npcs.values()) {
            String nk = Dictionary.key(n.name);
            if (nk.contains(k) && k.length() >= 3) return n.id;
        }
        return null;
    }

    private String npcHint(String ref) {
        String best = closest(slug(ref), doc.npcs.keySet());
        return best != null ? " ¿Quisiste decir '" + best + "'?" : " NPCs: " + String.join(", ", doc.npcs.keySet());
    }

    private static String closest(String s, java.util.Collection<String> options) {
        String best = null;
        int bd = Integer.MAX_VALUE;
        for (String o : options) {
            int d = Dictionary.lev(s, o);
            if (d < bd) {
                bd = d;
                best = o;
            }
        }
        return best != null && bd <= Math.max(2, s.length() / 3) ? best : null;
    }

    private static Integer number(String w) {
        if (w.matches("\\d{1,6}")) return Integer.parseInt(w);
        return NUMBERS.get(w);
    }

    private int intVal(String v, int ln, int def) {
        Integer n = number(Dictionary.norm(v));
        if (n == null) {
            error(ln, "Aquí va un número (escribiste \"" + v + "\")");
            return def;
        }
        return n;
    }

    private static boolean bool(String v) {
        return Set.of("si", "yes", "true", "1", "verdadero").contains(Dictionary.norm(v));
    }

    public static String slug(String s) {
        return Dictionary.norm(s).replace(' ', '_').replaceAll("[^a-z0-9_]", "");
    }

    private static String after(String t) {
        int c = t.indexOf(':');
        return c < 0 ? "" : t.substring(c + 1).trim();
    }

    private static String capital(String s) {
        s = s.trim();
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String cut(String s) {
        return s.length() > 60 ? s.substring(0, 57) + "…" : s;
    }
}
