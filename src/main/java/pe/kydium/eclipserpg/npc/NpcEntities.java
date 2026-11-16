package pe.kydium.eclipserpg.npc;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.quests.QuestManager;
import pe.kydium.eclipserpg.util.Keys;
import pe.kydium.eclipserpg.util.Txt;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * NPCs de la historia como aldeanos quietos (sin Citizens): miran al jugador,
 * abren su menú al hacer clic y muestran un "?" encima cuando tienen misión para ti.
 */
public final class NpcEntities implements Listener {

    /** Tipos de marcador: nueva misión, lista para entregar, te buscan, bloqueada por nivel. */
    private enum Mark {
        NEW("<yellow><bold>?", QuestManager.State.AVAILABLE),
        READY("<green><bold>✔", QuestManager.State.READY),
        TALK("<aqua><bold>?", QuestManager.State.TALK_TARGET),
        LOCKED("<gray><bold>?", QuestManager.State.LOCKED);

        final String text;
        final QuestManager.State state;

        Mark(String text, QuestManager.State state) {
            this.text = text;
            this.state = state;
        }
    }

    private final EclipseRPG plugin;
    private final Map<String, UUID> npcs = new HashMap<>();
    private final Map<String, Map<Mark, UUID>> marks = new HashMap<>();

    public NpcEntities(EclipseRPG plugin) {
        this.plugin = plugin;
    }

    public void load() {
        npcs.clear();
        var sec = plugin.getConfig().getConfigurationSection("npc-entidades");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            try {
                npcs.put(id, UUID.fromString(sec.getString(id, "")));
            } catch (IllegalArgumentException ignored) {
                // uuid inválido
            }
        }
    }

    private Villager.Profession profession(String npcId) {
        String p = plugin.getConfig().getString("npc-profesiones." + npcId, switch (npcId) {
            case "tula" -> "farmer";
            case "bruno" -> "weaponsmith";
            case "ines" -> "armorer";
            case "pip" -> "leatherworker";
            case "lia" -> "cartographer";
            case "odon" -> "librarian";
            case "velkar" -> "cleric";
            default -> "nitwit";
        });
        return switch (p) {
            case "farmer" -> Villager.Profession.FARMER;
            case "weaponsmith" -> Villager.Profession.WEAPONSMITH;
            case "armorer" -> Villager.Profession.ARMORER;
            case "leatherworker" -> Villager.Profession.LEATHERWORKER;
            case "cartographer" -> Villager.Profession.CARTOGRAPHER;
            case "librarian" -> Villager.Profession.LIBRARIAN;
            case "cleric" -> Villager.Profession.CLERIC;
            case "toolsmith" -> Villager.Profession.TOOLSMITH;
            case "butcher" -> Villager.Profession.BUTCHER;
            default -> Villager.Profession.NITWIT;
        };
    }

    /** Crea (o recrea) los NPCs en sus puestos. */
    public void spawnAll(Map<String, Location> spots) {
        for (Map.Entry<String, Location> e : spots.entrySet()) spawn(e.getKey(), e.getValue());
        plugin.saveConfig();
    }

    public void spawn(String id, Location at) {
        NpcManager.NpcDef def = plugin.npcs().def(id);
        if (def == null || at.getWorld() == null) return;
        // quitar el anterior si existe
        UUID old = npcs.get(id);
        if (old != null) {
            Entity e = Bukkit.getEntity(old);
            if (e != null) e.remove();
        }
        removeMarks(id);
        if (!(at.getWorld().spawnEntity(at, EntityType.VILLAGER) instanceof Villager v)) return;
        v.setAI(false);
        v.setInvulnerable(true);
        v.setSilent(true);
        v.setPersistent(true);
        v.setRemoveWhenFarAway(false);
        v.setCollidable(false);
        v.setProfession(profession(id));
        v.setVillagerLevel(5);
        v.customName(Txt.mm(def.name));
        v.setCustomNameVisible(true);
        v.setRotation(at.getYaw(), 0);
        v.getPersistentDataContainer().set(Keys.NPC, PersistentDataType.STRING, id);
        npcs.put(id, v.getUniqueId());
        plugin.getConfig().set("npc-entidades." + id, v.getUniqueId().toString());
    }

    private String npcId(Entity e) {
        return e.getPersistentDataContainer().get(Keys.NPC, PersistentDataType.STRING);
    }

    // ---------- Eventos ----------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEntityEvent e) {
        String id = npcId(e.getRightClicked());
        if (id == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        plugin.npcs().open(e.getPlayer(), id);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (npcId(e.getEntity()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent e) {
        if (npcId(e.getEntity()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCareer(VillagerCareerChangeEvent e) {
        if (npcId(e.getEntity()) != null) e.setCancelled(true);
    }

    // ---------- Mirar al jugador y marcadores "?" ----------

    public void startTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 10L);
    }

    private void tick() {
        for (Map.Entry<String, UUID> e : npcs.entrySet()) {
            Entity ent = Bukkit.getEntity(e.getValue());
            if (!(ent instanceof Villager v) || !v.isValid()) continue;
            lookAtNearest(v);
            Map<Mark, UUID> m = ensureMarks(e.getKey(), v);
            for (Player p : v.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(v.getLocation()) > 64 * 64) continue;
                QuestManager.State st = p.getGameMode() == GameMode.SPECTATOR ? QuestManager.State.NONE
                        : plugin.quests().stateFor(p, e.getKey());
                for (Mark mk : Mark.values()) {
                    Entity disp = Bukkit.getEntity(m.get(mk));
                    if (disp == null) continue;
                    boolean show = mk.state == st;
                    if (show && !p.canSee(disp)) p.showEntity(plugin, disp);
                    else if (!show && p.canSee(disp)) p.hideEntity(plugin, disp);
                }
            }
        }
    }

    private void lookAtNearest(Villager v) {
        Player best = null;
        double bd = 8 * 8;
        for (Player p : v.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(v.getLocation());
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        if (best == null) return;
        Location from = v.getEyeLocation();
        Location to = best.getEyeLocation();
        double dx = to.getX() - from.getX(), dy = to.getY() - from.getY(), dz = to.getZ() - from.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        v.setRotation(yaw, pitch);
    }

    private Map<Mark, UUID> ensureMarks(String id, Villager v) {
        Map<Mark, UUID> m = marks.computeIfAbsent(id, k -> new HashMap<>());
        World w = v.getWorld();
        Location at = v.getLocation().add(0, 2.45, 0);
        for (Mark mk : Mark.values()) {
            UUID u = m.get(mk);
            Entity e = u == null ? null : Bukkit.getEntity(u);
            if (e != null && e.isValid()) {
                if (e.getLocation().distanceSquared(at) > 0.5) e.teleport(at);
                continue;
            }
            TextDisplay td = (TextDisplay) w.spawnEntity(at, EntityType.TEXT_DISPLAY);
            td.text(Txt.mm(mk.text));
            td.setBillboard(Display.Billboard.CENTER);
            td.setVisibleByDefault(false);
            td.setPersistent(false);
            td.setShadowed(true);
            td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            td.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(2.2f, 2.2f, 2.2f), new AxisAngle4f()));
            m.put(mk, td.getUniqueId());
        }
        return m;
    }

    private void removeMarks(String id) {
        Map<Mark, UUID> m = marks.remove(id);
        if (m == null) return;
        for (UUID u : m.values()) {
            Entity e = Bukkit.getEntity(u);
            if (e != null) e.remove();
        }
    }

    public void cleanup() {
        for (String id : marks.keySet().toArray(new String[0])) removeMarks(id);
    }

    public boolean has(String id) {
        return npcs.containsKey(id);
    }
}
