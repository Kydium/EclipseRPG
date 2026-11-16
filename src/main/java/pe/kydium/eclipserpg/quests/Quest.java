package pe.kydium.eclipserpg.quests;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Una misión de la historia (quests.yml). */
public final class Quest {

    public enum Type { MATAR, MOB, TENER, ENTREGAR, FUNDIR, MINAR, PESCAR, DOMESTICAR, ENCANTAR, DIMENSION, HABLAR, MAZMORRA }

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
    public long xp;
    public long soles;
    public final List<String> start = new ArrayList<>();
    public final List<String> end = new ArrayList<>();
    public final List<String> reminder = new ArrayList<>();
    public final List<Objective> objectives = new ArrayList<>();
    public final List<Spawn> spawns = new ArrayList<>();
    public final List<String> giveOnAccept = new ArrayList<>();
    public final List<String> rewards = new ArrayList<>();
    public final List<Choice> choices = new ArrayList<>();
}
