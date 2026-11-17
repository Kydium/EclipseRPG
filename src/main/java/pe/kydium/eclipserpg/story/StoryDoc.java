package pe.kydium.eclipserpg.story;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Una historia leída de historia.txt (sin dependencias de Bukkit). */
public final class StoryDoc {

    public String title = "Historia sin título";
    public String description = "";
    public String currency = "monedas";
    public String village = "la Aldea";
    public final Map<Integer, String> acts = new TreeMap<>();
    public final Map<String, Npc> npcs = new LinkedHashMap<>();
    public final List<Mission> missions = new ArrayList<>();

    public static final class Npc {
        public int line;
        public String id;
        public String name;
        public String color = "white";
        public String skin;
        public String role = "";
        public String place;
        public String hand;
        public String offhand;
        public String combat;
        public double combatHp, combatDamage;
        public final List<String> services = new ArrayList<>();
        public final List<String> talk = new ArrayList<>();
    }

    public static final class Objective {
        public int line;
        public String type;
        public final List<String> targets = new ArrayList<>();
        public int amount = 1;
        public boolean corrupted;
        public String text;
    }

    public static final class Choice {
        public int line;
        public String text;
        public final List<String> rewards = new ArrayList<>();
        public String flag;
        public String message;
    }

    public static final class Mission {
        public int line;
        public String id;
        public String name;
        public int act = 1;
        public String npc;
        public int level = 1;
        public boolean boss;
        public boolean noDeath;
        public int dungeon;
        public String structure; // estructura que se construye al aceptar
        public String event;     // evento del mundo que empieza al aceptar
        public String weather;   // clima al aceptar
        public long xp;
        public long money;
        public final List<String> start = new ArrayList<>();
        public final List<String> end = new ArrayList<>();
        public final List<String> reminder = new ArrayList<>();
        public final List<String> replies = new ArrayList<>(); // lo que responde el jugador para aceptar
        public String decline;                                  // lo que responde para no aceptar todavía
        public final List<Objective> objectives = new ArrayList<>();
        public final List<String> spawns = new ArrayList<>();   // "mob:n"
        public final List<String> receive = new ArrayList<>();  // specs
        public final List<String> rewards = new ArrayList<>(); // specs
        public final List<Choice> choices = new ArrayList<>();
    }
}
