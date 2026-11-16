package pe.kydium.eclipserpg.contracts;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.persistence.PersistentDataType;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.data.PlayerData;
import pe.kydium.eclipserpg.util.Keys;
import pe.kydium.eclipserpg.util.Txt;

import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Contratos diarios de la Capitana Inés: 3 por jugador, se renuevan cada día. */
public final class ContractManager implements Listener {

    public enum Kind { CORROMPIDOS, MATAR, MINAR, PESCAR }

    public static final class Contract {
        public String id;
        public String name;
        public Kind kind;
        public final Set<String> targets = new HashSet<>();
        public int base;
        public int perTier;
        public long xpPerTier;
        public long solesPerTier;
        public Material icon = Material.PAPER;

        public int amount(int tier) {
            return base + perTier * (tier - 1);
        }
    }

    private final EclipseRPG plugin;
    private final Map<String, Contract> pool = new LinkedHashMap<>();

    public ContractManager(EclipseRPG plugin) {
        this.plugin = plugin;
    }

    public void load() {
        pool.clear();
        YamlConfiguration y = plugin.story().yaml("contratos");
        ConfigurationSection root = y.getConfigurationSection("contratos");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            Contract c = new Contract();
            c.id = id;
            c.name = s.getString("nombre", id);
            try {
                c.kind = Kind.valueOf(s.getString("tipo", "CORROMPIDOS").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("contracts.yml: tipo inválido en " + id);
                continue;
            }
            for (String t : s.getString("objetivo", "").split(",")) {
                if (!t.isBlank()) c.targets.add(t.trim().toLowerCase(Locale.ROOT));
            }
            c.base = s.getInt("cantidad", 10);
            c.perTier = s.getInt("por-tier", 0);
            c.xpPerTier = s.getLong("xp-por-tier", 60);
            c.solesPerTier = s.getLong("soles-por-tier", 25);
            Material icon = Material.matchMaterial(s.getString("icono", "PAPER"));
            if (icon != null) c.icon = icon;
            pool.put(id, c);
        }
        plugin.getLogger().info("Contratos cargados: " + pool.size());
    }

    public Contract get(String id) {
        return pool.get(id);
    }

    private String today() {
        ZoneId zone = ZoneId.of(plugin.getConfig().getString("zona-horaria", "America/Lima"));
        return LocalDate.now(zone).toString();
    }

    /** Si cambió el día, reparte 3 contratos nuevos. */
    public void ensureToday(Player p) {
        PlayerData d = plugin.data().get(p);
        String today = today();
        if (today.equals(d.contractDate) && d.contractIds.size() == 3) return;
        List<String> ids = new ArrayList<>(pool.keySet());
        Collections.shuffle(ids, new Random(p.getUniqueId().getLeastSignificantBits() ^ today.hashCode()));
        d.contractIds.clear();
        for (int i = 0; i < 3 && i < ids.size(); i++) d.contractIds.add(ids.get(i));
        d.contractDate = today;
        d.contractTier = plugin.levels().tier(p);
        d.contractProgress = new int[3];
        d.contractClaimed = new boolean[3];
        d.dirty = true;
    }

    public void open(Player p) {
        ensureToday(p);
        new ContractMenu(plugin, p).open();
    }

    /** Intenta reclamar el contrato i. */
    public void claim(Player p, int i) {
        PlayerData d = plugin.data().get(p);
        if (i < 0 || i >= d.contractIds.size()) return;
        Contract c = pool.get(d.contractIds.get(i));
        if (c == null || d.contractClaimed[i]) return;
        if (d.contractProgress[i] < c.amount(d.contractTier)) {
            Txt.send(p, "<red>Todavía no completas este contrato.");
            return;
        }
        d.contractClaimed[i] = true;
        d.dirty = true;
        plugin.levels().reward(p, c.xpPerTier * d.contractTier, c.solesPerTier * d.contractTier);
        Txt.send(p, "<green>Contrato cumplido: <white>" + c.name);
        Txt.sound(p, "entity.player.levelup", 0.7f, 1.6f);
        boolean all = true;
        for (boolean b : d.contractClaimed) all &= b;
        if (all) {
            var frag = plugin.items().create("fragmento_mejora", 1);
            if (frag != null) {
                plugin.items().give(p, frag);
                Txt.send(p, "<gold>¡Completaste los 3 contratos de hoy! Recibes un Fragmento de Mejora.");
            }
        }
    }

    private void progress(Player p, Kind kind, String target) {
        if (pool.isEmpty()) return;
        ensureToday(p);
        PlayerData d = plugin.data().get(p);
        for (int i = 0; i < d.contractIds.size(); i++) {
            if (d.contractClaimed[i]) continue;
            Contract c = pool.get(d.contractIds.get(i));
            if (c == null || c.kind != kind) continue;
            if (!c.targets.isEmpty() && (target == null || !c.targets.contains(target))) continue;
            int need = c.amount(d.contractTier);
            if (d.contractProgress[i] >= need) continue;
            d.contractProgress[i]++;
            d.dirty = true;
            if (d.contractProgress[i] >= need) {
                Txt.send(p, "<green>Contrato completo: <white>" + c.name + "</white>. Reclámalo con la Capitana Inés.");
                Txt.sound(p, "block.note_block.chime", 1f, 1.5f);
            }
        }
    }

    // ---------- Eventos ----------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        LivingEntity le = e.getEntity();
        Player k = le.getKiller();
        if (k == null) return;
        String type = le.getType().getKey().getKey();
        progress(k, Kind.MATAR, type);
        if (le.getPersistentDataContainer().has(Keys.MOB, PersistentDataType.STRING)) {
            progress(k, Kind.CORROMPIDOS, null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        progress(e.getPlayer(), Kind.MINAR, e.getBlock().getType().getKey().getKey());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) progress(e.getPlayer(), Kind.PESCAR, null);
    }
}
