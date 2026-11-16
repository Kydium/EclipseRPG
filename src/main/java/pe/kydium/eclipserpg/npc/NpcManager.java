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
        public Material icon = Material.PLAYER_HEAD;
        public final List<String> services = new ArrayList<>();
        public final List<String> talk = new ArrayList<>();
    }

    private final EclipseRPG plugin;
    private final Map<String, NpcDef> npcs = new LinkedHashMap<>();

    public NpcManager(EclipseRPG plugin) {
        this.plugin = plugin;
    }

    public void load() {
        npcs.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "npcs.yml"));
        ConfigurationSection root = y.getConfigurationSection("npcs");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            NpcDef n = new NpcDef();
            n.id = id.toLowerCase();
            n.name = s.getString("nombre", id);
            n.citizensName = s.getString("nombre-citizens", id);
            n.skin = s.getString("skin");
            Material m = Material.matchMaterial(s.getString("icono", "PLAYER_HEAD"));
            if (m != null) n.icon = m;
            n.services.addAll(s.getStringList("servicios"));
            n.talk.addAll(s.getStringList("charla"));
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

    public boolean open(Player p, String id) {
        NpcDef n = def(id);
        if (n == null) return false;
        new NpcMenu(plugin, p, n).open();
        return true;
    }

    /**
     * Crea los NPCs de Citizens en círculo alrededor del campamento, usando los comandos
     * de Citizens como el jugador (que debe ser OP).
     */
    public void createAll(Player p) {
        if (Bukkit.getPluginManager().getPlugin("Citizens") == null) {
            Txt.send(p, "<red>Citizens no está instalado.");
            return;
        }
        Location center = plugin.items().campLocation();
        Location back = p.getLocation();
        List<NpcDef> list = new ArrayList<>(npcs.values());
        double radius = plugin.getConfig().getDouble("npcs.radio-campamento", 6);
        long delay = 0;
        for (int i = 0; i < list.size(); i++) {
            NpcDef n = list.get(i);
            double angle = 2 * Math.PI * i / list.size();
            Location at = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            at = at.getWorld().getHighestBlockAt(at).getLocation().add(0.5, 1, 0.5);
            // mirando hacia el centro del campamento
            at.setDirection(center.toVector().subtract(at.toVector()));
            final Location spot = at;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                p.teleport(spot);
                p.performCommand("npc create " + n.citizensName);
                p.performCommand("npc command add eclipse npc " + n.id + " <p>");
                p.performCommand("npc lookclose");
                if (n.skin != null && !n.skin.isBlank()) p.performCommand("npc skin " + n.skin);
            }, delay);
            delay += 15;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            p.teleport(back);
            Txt.send(p, "<green>Listo: se crearon " + list.size() + " NPCs alrededor del campamento.");
            Txt.send(p, "<gray>Puedes moverlos con <white>/npc select</white> y <white>/npc tphere</white>.");
        }, delay + 5);
    }
}
