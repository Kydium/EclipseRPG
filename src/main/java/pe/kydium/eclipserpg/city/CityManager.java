package pe.kydium.eclipserpg.city;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.npc.NpcManager;
import pe.kydium.eclipserpg.story.StoryDoc;
import pe.kydium.eclipserpg.structure.StructureManager;
import pe.kydium.eclipserpg.util.Txt;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Otras ciudades del mundo (bloques CIUDAD de historia.txt): aldeas generadas lejos de la aldea
 * principal, con sus propios NPCs. Se construyen la primera vez que la historia te manda allí.
 */
public final class CityManager {

    public static final String PREFIX = "ciudad_";

    private final EclipseRPG plugin;
    private final Map<String, StoryDoc.City> cities = new LinkedHashMap<>();
    private final List<StructureManager.Def> defs = new ArrayList<>();
    private final Set<String> announced = new HashSet<>();
    private final Map<String, Long> failedAt = new java.util.HashMap<>();

    public CityManager(EclipseRPG plugin) {
        this.plugin = plugin;
    }

    public void load(StoryDoc doc) {
        cities.clear();
        defs.clear();
        cities.putAll(doc.cities);
        for (StoryDoc.City c : cities.values()) {
            StructureManager.Def d = new StructureManager.Def();
            d.id = PREFIX + c.id;
            d.name = "<gold>" + c.name;
            d.gen = "aldea_" + c.size;
            d.category = "aldea";
            d.style = c.style == null || c.style.isBlank() ? "auto" : c.style;
            d.appears = "manual";
            d.max = 1;
            d.distMin = c.distMin;
            d.distMax = c.distMax;
            d.slope = 14;
            d.city = true;
            d.title = "<gold><bold>" + c.name;
            d.subtitle = c.subtitle == null || c.subtitle.isBlank() ? "<gray>Ciudad" : "<gray>" + c.subtitle;
            d.lore.addAll(c.lore);
            d.xp = 150;
            if (c.direction != null) d.angle = switch (c.direction) {
                case "este" -> 0.0;
                case "sur" -> Math.PI / 2;
                case "oeste" -> Math.PI;
                default -> -Math.PI / 2;   // norte (z negativo)
            };
            defs.add(d);
        }
        plugin.structures().putCityDefs(defs);
        if (!cities.isEmpty()) plugin.getLogger().info("Ciudades de la historia: " + cities.size() + " (" + built() + " construidas)");
        Bukkit.getScheduler().runTaskLater(plugin, this::placeMissingNpcs, 40L);
    }

    public List<StructureManager.Def> structureDefs() {
        return defs;
    }

    public Set<String> ids() {
        return cities.keySet();
    }

    public String name(String id) {
        StoryDoc.City c = cities.get(id);
        return c == null ? id : "<gold>" + c.name;
    }

    private int built() {
        int n = 0;
        for (String id : cities.keySet()) if (isBuilt(id)) n++;
        return n;
    }

    public StructureManager.Inst instance(String id) {
        for (StructureManager.Inst i : plugin.structures().instances()) if (i.id.equals(PREFIX + id)) return i;
        return null;
    }

    public boolean isBuilt(String id) {
        return instance(id) != null;
    }

    /** ¿Está dentro de alguna ciudad construida? (protección, sin monstruos) */
    public boolean inside(Location l, int pad) {
        if (l == null || l.getWorld() == null) return false;
        for (StructureManager.Inst i : plugin.structures().instances()) {
            if (!i.id.startsWith(PREFIX) || !i.world.equals(l.getWorld().getName())) continue;
            int r = Math.max(i.rx, i.rz) + pad;
            double dx = l.getX() - i.x, dz = l.getZ() - i.z;
            if (dx * dx + dz * dz <= (double) r * r && l.getY() > i.y - 40) return true;
        }
        return false;
    }

    /**
     * Construye la ciudad si todavía no existe. Con compass=true (o al terminar de construirse) le da
     * al jugador la brújula. Mientras se construye no hace nada más.
     */
    public void ensure(String id, Player p, boolean compass) {
        StoryDoc.City c = cities.get(id);
        if (c == null) return;
        StructureManager.Inst have = instance(id);
        if (have != null) {
            if (compass && p != null) plugin.structures().giveCompass(p, have);
            return;
        }
        Long failed = failedAt.get(id);
        if (failed != null && System.currentTimeMillis() - failed < 120_000L) return;   // reintenta cada 2 minutos
        java.util.UUID who = p == null ? null : p.getUniqueId();
        if (!plugin.structures().build(PREFIX + id, null, inst -> {
            if (inst == null) {
                failedAt.put(id, System.currentTimeMillis());
                plugin.getLogger().warning("No se pudo construir la ciudad " + c.name + "; se reintentará en 2 minutos");
                return;
            }
            placeNpcs(id, inst);
            Player pl = who == null ? null : Bukkit.getPlayer(who);
            if (pl != null) {
                Txt.send(pl, "<gold>🏰 " + c.name + " <gray>apareció en el mapa. Sigue el punto dorado o la brújula.");
                plugin.structures().giveCompass(pl, inst);
            }
        })) return;
        if (p != null && announced.add(p.getUniqueId() + id)) Txt.send(p, "<gray>El camino hacia <gold>" + c.name + "<gray> se está abriendo… (unos segundos)");
    }

    /** Pone en la ciudad a los NPCs que viven en ella. */
    public void placeNpcs(String cityId, StructureManager.Inst inst) {
        World w = Bukkit.getWorld(inst.world);
        if (w == null) return;
        Set<String> used = new HashSet<>();
        List<NpcManager.NpcDef> later = new ArrayList<>();
        for (String id : plugin.npcs().ids()) {
            NpcManager.NpcDef d = plugin.npcs().def(id);
            if (d == null || !cityId.equals(d.city)) continue;
            if (plugin.getConfig().contains("npc-lugares." + id + ".x")) continue;   // ya tiene sitio
            float[] at = d.place == null ? null : inst.places.get(d.place);
            if (at != null && used.add(d.place)) put(w, id, at, 0);
            else if (at != null) put(w, id, at, 1.5);
            else later.add(d);
        }
        for (NpcManager.NpcDef d : later) {
            String free = null;
            for (String k : inst.places.keySet()) if (!used.contains(k)) {
                free = k;
                break;
            }
            if (free != null) {
                used.add(free);
                put(w, d.id, inst.places.get(free), 0);
            } else put(w, d.id, new float[]{inst.x + 0.5f, inst.y + 1, inst.z + 0.5f, 0}, used.size() * 1.5);
        }
        plugin.saveConfig();
    }

    private void put(World w, String npcId, float[] at, double shift) {
        Location l = new Location(w, at[0] + shift, at[1], at[2], at[3], 0);
        plugin.npcEntities().setCustomSpot(npcId, l);
    }

    /** Ciudades ya construidas con NPCs nuevos (la historia cambió): los pone. */
    public void placeMissingNpcs() {
        for (String id : cities.keySet()) {
            StructureManager.Inst inst = instance(id);
            if (inst != null) placeNpcs(id, inst);
        }
    }
}
