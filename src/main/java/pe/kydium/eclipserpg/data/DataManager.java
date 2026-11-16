package pe.kydium.eclipserpg.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import pe.kydium.eclipserpg.EclipseRPG;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Guarda y carga el progreso de cada jugador en plugins/AventuraMaker/jugadores/<uuid>.yml */
public final class DataManager {
    private final EclipseRPG plugin;
    private final File folder;
    private final Map<UUID, PlayerData> cache = new HashMap<>();

    public DataManager(EclipseRPG plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "jugadores");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("No se pudo crear la carpeta de jugadores");
        }
    }

    public PlayerData get(Player p) {
        return get(p.getUniqueId());
    }

    public PlayerData get(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::load);
    }

    private PlayerData load(UUID uuid) {
        PlayerData d = new PlayerData(uuid);
        File f = new File(folder, uuid + ".yml");
        if (!f.exists()) return d;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        d.level = Math.max(1, y.getInt("nivel", 1));
        d.xp = y.getLong("xp", 0);
        d.points = y.getInt("puntos", 0);
        d.soles = y.getLong("soles", 0);
        ConfigurationSection st = y.getConfigurationSection("stats");
        if (st != null) for (String k : st.getKeys(false)) d.stats.put(k, st.getInt(k));
        d.contractDate = y.getString("contratos.fecha", "");
        d.contractTier = y.getInt("contratos.tier", 1);
        d.contractIds.addAll(y.getStringList("contratos.ids"));
        List<Integer> prog = y.getIntegerList("contratos.progreso");
        for (int i = 0; i < 3 && i < prog.size(); i++) d.contractProgress[i] = prog.get(i);
        List<Boolean> cl = y.getBooleanList("contratos.reclamado");
        for (int i = 0; i < 3 && i < cl.size(); i++) d.contractClaimed[i] = cl.get(i);
        d.rewards.addAll(y.getStringList("premios"));
        d.questIndex = y.getInt("historia.mision", 0);
        d.questAccepted = y.getBoolean("historia.aceptada", false);
        List<Integer> qp = y.getIntegerList("historia.progreso");
        d.questProgress = new int[qp.size()];
        for (int i = 0; i < qp.size(); i++) d.questProgress[i] = qp.get(i);
        d.flags.addAll(y.getStringList("historia.marcas"));
        return d;
    }

    public void save(UUID uuid) {
        PlayerData d = cache.get(uuid);
        if (d == null) return;
        YamlConfiguration y = new YamlConfiguration();
        y.set("nivel", d.level);
        y.set("xp", d.xp);
        y.set("puntos", d.points);
        y.set("soles", d.soles);
        for (Map.Entry<String, Integer> e : d.stats.entrySet()) y.set("stats." + e.getKey(), e.getValue());
        y.set("contratos.fecha", d.contractDate);
        y.set("contratos.tier", d.contractTier);
        y.set("contratos.ids", d.contractIds);
        List<Integer> prog = new ArrayList<>();
        for (int v : d.contractProgress) prog.add(v);
        y.set("contratos.progreso", prog);
        List<Boolean> cl = new ArrayList<>();
        for (boolean v : d.contractClaimed) cl.add(v);
        y.set("contratos.reclamado", cl);
        y.set("premios", new ArrayList<>(d.rewards));
        y.set("historia.mision", d.questIndex);
        y.set("historia.aceptada", d.questAccepted);
        List<Integer> qp = new ArrayList<>();
        for (int v : d.questProgress) qp.add(v);
        y.set("historia.progreso", qp);
        y.set("historia.marcas", new ArrayList<>(d.flags));
        try {
            y.save(new File(folder, uuid + ".yml"));
            d.dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar " + uuid + ": " + ex.getMessage());
        }
    }

    /** Borra todo el progreso de un jugador. */
    public void reset(UUID uuid) {
        cache.put(uuid, new PlayerData(uuid));
        save(uuid);
    }

    public void unload(UUID uuid) {
        save(uuid);
        cache.remove(uuid);
    }

    public void saveAll() {
        for (UUID u : new ArrayList<>(cache.keySet())) save(u);
    }
}
