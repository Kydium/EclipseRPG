package pe.kydium.eclipserpg.quests;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Una misión de la historia (quests.yml). */
public final class Quest {

    public enum Type { MATAR, MOB, TENER, ENTREGAR, FUNDIR, MINAR, PESCAR, DOMESTICAR, ENCANTAR, DIMENSION, HABLAR, MAZMORRA, ESTRUCTURA }

    public static final class Objective {
        public Type type;
        public final List<String> targets = new ArrayList<>(); // en minúsculas
        public int amount = 1;
        public boolean corrupted;
        public String text;

        /** ¿El objetivo acepta este id (material, entidad, mob o npc)? */
        public boolean matches(String id) {
            if (targets.isEmpty() || targets.contains("*")) return true;
            String v = id.toLowerCase(Locale.ROOT);
            for (String t : targets) {
                if (t.equals(v)) return true;
                if (t.startsWith("*") && v.endsWith(t.substring(1))) return true;
                if (t.endsWith("*") && v.startsWith(t.substring(0, t.length() - 1))) return true;
            }
            return false;
        }

        public boolean matches(Material m) {
            return matches(m.getKey().getKey());
        }

        /** Objetivos que se revisan en el inventario al entregar. */
        public boolean isInventory() {
            return type == Type.TENER || type == Type.ENTREGAR;
        }
    }

    public record Spawn(String mob, int amount) {}

    public record Choice(String text, List<String> rewards, String flag, String message) {}

    public String id;
    public int number;
    public int act;
    public String name;
    public String npc;
    public int minLevel = 1;
    public boolean boss;
    public boolean noDeath;
    public int dungeon;
    public String event;
    public String structure;
    public String weather;
    public long xp;
    public long soles;
    public final List<String> start = new ArrayList<>();
    public final List<String> end = new ArrayList<>();
    public final List<String> reminder = new ArrayList<>();
    public final List<String> replies = new ArrayList<>();
    public String decline;

    private static final java.util.Map<String, String> FUTURE = new java.util.HashMap<>();

    static {
        String[][] v = {{"derrota", "Derrotaré"}, {"vence", "Venceré"}, {"elimina", "Eliminaré"}, {"entrega", "Te traeré"}, {"trae", "Te traeré"},
                {"lleva", "Llevaré"}, {"consigue", "Conseguiré"}, {"ten", "Conseguiré"}, {"obten", "Conseguiré"}, {"junta", "Juntaré"},
                {"recolecta", "Recolectaré"}, {"encuentra", "Encontraré"}, {"busca", "Buscaré"}, {"entra", "Iré"}, {"ve", "Iré"}, {"viaja", "Viajaré"},
                {"domestica", "Domesticaré"}, {"encanta", "Encantaré"}, {"pesca", "Pescaré"}, {"mina", "Minaré"}, {"pica", "Picaré"},
                {"tala", "Talaré"}, {"funde", "Fundiré"}, {"cocina", "Cocinaré"}, {"habla", "Hablaré"}, {"explora", "Exploraré"},
                {"completa", "Completaré"}, {"supera", "Superaré"}, {"sobrevive", "Sobreviviré"}, {"cosecha", "Cosecharé"},
                {"construye", "Construiré"}, {"visita", "Visitaré"}, {"caza", "Cazaré"}, {"protege", "Protegeré"}, {"defiende", "Defenderé"},
                {"limpia", "Limpiaré"}, {"rescata", "Rescataré"}, {"investiga", "Investigaré"}, {"fabrica", "Fabricaré"}, {"crea", "Crearé"}};
        for (String[] a : v) FUTURE.put(a[0], a[1]);
    }

    /** Respuesta del jugador según lo que pide la misión: "¡Cuenta conmigo! Te traeré 20 panes." */
    public String autoReply() {
        if (objectives.isEmpty()) return "¡Cuenta conmigo!";
        String t = objectives.get(0).text == null ? "" : objectives.get(0).text.replaceAll("<[^>]+>", "").trim();
        String[] w = t.split("\\s+", 2);
        String verb = w[0].toLowerCase(Locale.ROOT).replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u");
        String rest = w.length > 1 ? w[1] : "";
        if (!rest.isEmpty()) rest = Character.toLowerCase(rest.charAt(0)) + rest.substring(1);
        String core;
        if (verb.equals("mata") || verb.equals("matar") || verb.equals("acaba")) {
            String r2 = rest.replaceFirst("^\\d+\\s+", "");
            core = rest.matches("^1\\s.*") ? "Acabaré con " + r2 : "Acabaré con esos " + r2;
        } else if (FUTURE.containsKey(verb)) core = FUTURE.get(verb) + (rest.isEmpty() ? "" : " " + rest);
        else core = "Me encargo de eso";
        String[] lead = boss ? new String[]{"¡Que venga!", "No le tengo miedo.", "Es hora de acabar con esto."}
                : new String[]{"¡Cuenta conmigo!", "¡Hecho!", "Déjamelo a mí.", "Entendido.", "Voy enseguida."};
        String l = lead[Math.floorMod((id == null ? name : id).hashCode(), lead.length)];
        return l + " " + core + (core.endsWith(".") || core.endsWith("!") ? "" : ".");
    }
    public final List<Objective> objectives = new ArrayList<>();
    public final List<Spawn> spawns = new ArrayList<>();
    public final List<String> giveOnAccept = new ArrayList<>();
    public final List<String> rewards = new ArrayList<>();
    public final List<Choice> choices = new ArrayList<>();
}
