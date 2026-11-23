package pe.kydium.eclipserpg.npc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.util.Txt;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** NPCs del Campamento del Alba (npcs.yml). Citizens los muestra; aquí vive su menú. */
public final class NpcManager {

    public static final class NpcDef {
        public String id;
        public String name;
        public String citizensName;
        public String skin;
        public String plainName;
        public String role = "";
        public String place;
        public String hand;
        public String offhand;
        public String combat;        // guardian | cobarde | null
        public double combatHp, combatDamage;
        public String city;          // ciudad donde vive (null = aldea principal)
        public Material icon = Material.PLAYER_HEAD;
        public final List<String> services = new ArrayList<>();
        public final List<String> talk = new ArrayList<>();
    }

    private final EclipseRPG plugin;
    private final Map<String, NpcDef> npcs = new LinkedHashMap<>();

    public NpcManager(EclipseRPG plugin) {
        this.plugin = plugin;
    }

    /** Carga los NPCs de la historia activa. */
    public void load(pe.kydium.eclipserpg.story.StoryDoc doc) {
        npcs.clear();
        for (pe.kydium.eclipserpg.story.StoryDoc.Npc sn : doc.npcs.values()) {
            NpcDef n = new NpcDef();
            n.id = sn.id;
            n.plainName = sn.name;
            String color = sn.color == null ? "white" : sn.color;
            n.name = "<" + color + ">" + sn.name;
            n.citizensName = sn.name;
            n.skin = sn.skin;
            n.role = sn.role == null ? "" : sn.role;
            n.place = sn.place;
            n.hand = sn.hand;
            n.offhand = sn.offhand;
            n.combat = Combat.normRole(sn.combat);
            n.combatHp = sn.combatHp;
            n.combatDamage = sn.combatDamage;
            n.city = sn.city;
            n.services.addAll(sn.services);
            n.talk.addAll(sn.talk);
            npcs.put(n.id, n);
        }
        plugin.getLogger().info("NPCs cargados: " + npcs.size());
    }

    public Set<String> ids() {
        return npcs.keySet();
    }

    public NpcDef def(String id) {
        return npcs.get(id.toLowerCase());
    }

    public String name(String id) {
        NpcDef n = def(id);
        return n == null ? id : n.name;
    }

    public List<String> talkLines(String id) {
        NpcDef n = def(id);
        return n == null ? List.of() : n.talk;
    }

    /**
     * Al hablar con un NPC: si solo tiene algo de la historia, va directo a la conversación;
     * si además ofrece servicios, abre su ventana con botones.
     */
    public boolean open(Player p, String id) {
        NpcDef n = def(id);
        if (n == null) return false;
        if (!plugin.getConfig().getBoolean("dialogos.ventana", true)) {
            new NpcMenu(plugin, p, n).open();
            return true;
        }
        var qm = plugin.quests();
        var st = qm.stateFor(p, id);
        var q = qm.current(p);
        boolean storyAction = st == pe.kydium.eclipserpg.quests.QuestManager.State.AVAILABLE
                || st == pe.kydium.eclipserpg.quests.QuestManager.State.READY
                || st == pe.kydium.eclipserpg.quests.QuestManager.State.TALK_TARGET
                || st == pe.kydium.eclipserpg.quests.QuestManager.State.IN_PROGRESS;
        if (n.services.isEmpty() && storyAction) {
            qm.talk(p, id);
            return true;
        }
        List<String> body = new ArrayList<>();
        if (n.role != null && !n.role.isBlank()) body.add("<gray><italic>" + n.role);
        if (!n.talk.isEmpty()) body.add("<white>" + n.talk.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(n.talk.size())));
        List<pe.kydium.eclipserpg.quests.DialogUI.Button> b = new ArrayList<>();
        switch (st) {
            case AVAILABLE -> b.add(new pe.kydium.eclipserpg.quests.DialogUI.Button("<yellow>❗ Nueva misión: <white>" + q.name,
                    qm.actName(q.act), () -> qm.talk(p, id)));
            case READY -> b.add(new pe.kydium.eclipserpg.quests.DialogUI.Button("<green>✔ Entregar: <white>" + q.name,
                    "Cumpliste todos los objetivos", () -> qm.talk(p, id)));
            case IN_PROGRESS -> b.add(new pe.kydium.eclipserpg.quests.DialogUI.Button("<gold>⌛ " + q.name,
                    "Misión en curso", () -> qm.talk(p, id)));
            case TALK_TARGET -> b.add(new pe.kydium.eclipserpg.quests.DialogUI.Button("<aqua>💬 Hablar",
                    "Tu misión pide hablar con " + n.plainName, () -> qm.talk(p, id)));
            case LOCKED -> body.add("<gray>🔒 Tengo una misión para ti cuando seas <yellow>nivel " + q.minLevel + "<gray>.");
            default -> { }
        }
        for (String s : n.services) {
            if (s.startsWith("tienda:")) {
                String shop = s.substring(7);
                b.add(new pe.kydium.eclipserpg.quests.DialogUI.Button(plugin.shops().buttonLabel(shop), plugin.shops().buttonHint(shop), () -> plugin.shops().open(p, shop)));
            } else if (s.equals("contratos")) {
                b.add(new pe.kydium.eclipserpg.quests.DialogUI.Button("<yellow>📜 Contratos del día", "3 encargos nuevos cada día",
                        () -> plugin.contracts().open(p)));
            } else if (s.equals("grieta")) {
                long cost = plugin.rifts().cost(p);
                b.add(new pe.kydium.eclipserpg.quests.DialogUI.Button("<light_purple>🌀 Abrir una Grieta <gray>(" + cost + " ☀)",
                        "5 oleadas de enemigos de tu nivel; tus amigos cercanos entran contigo", () -> plugin.rifts().start(p)));
            }
        }
        b.add(new pe.kydium.eclipserpg.quests.DialogUI.Button("<gray>Adiós", null, null));
        new pe.kydium.eclipserpg.quests.DialogUI(plugin).show(p, n.name, body, b, true);
        return true;
    }


    /** Vuelve a poner a todos los NPCs de la historia en la aldea. */
    public void createAll(Player p) {
        int n = plugin.npcEntities().placeStoryNpcs();
        plugin.saveConfig();
        if (n == 0) Txt.send(p, "<red>Todavía no hay aldea. Espera a que se construya o usa /eclipse construiraldea.");
        else Txt.send(p, "<green>Listo: " + n + " NPCs en sus lugares.");
    }


}
