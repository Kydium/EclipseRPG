package pe.kydium.eclipserpg.test;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.dungeon.Box;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Prueba automática (solo con -Declipse.prueba=true): genera la aldea, NPCs, una ruina y la
 * mazmorra del acto 1 en un servidor real, revisa que todo esté bien y apaga el servidor.
 * La compilación de GitHub la ejecuta antes de publicar.
 */
public final class SmokeTest {

    private final EclipseRPG plugin;
    private final List<String> fails = new ArrayList<>();
    private int ticks;
    private int stage;
    private int wait;
    private final Map<UUID, Location> start = new HashMap<>();
    private BukkitTask task;

    public SmokeTest(EclipseRPG plugin) {
        this.plugin = plugin;
    }

    public static boolean enabled() {
        return Boolean.getBoolean("eclipse.prueba");
    }

    private void ok(String m) {
        plugin.getLogger().info("[PRUEBA] OK: " + m);
    }

    private void fail(String m) {
        fails.add(m);
        plugin.getLogger().severe("[PRUEBA] FALLO: " + m);
    }

    private void check(boolean cond, String m) {
        if (cond) ok(m);
        else fail(m);
    }

    public void start() {
        plugin.getLogger().info("[PRUEBA] Iniciando prueba automática…");
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        ticks++;
        try {
            if (ticks > 900) {
                fail("tiempo agotado en la etapa " + stage);
                finish();
                return;
            }
            if (wait > 0) {
                wait--;
                return;
            }
            switch (stage) {
                case 0 -> { // esperar la aldea
                    if (plugin.village().isGenerated() && !plugin.village().isBusy()) {
                        stage = 1;
                        wait = 5;
                    }
                }
                case 1 -> {
                    checkVillage();
                    stage = 2;
                    wait = 15;
                }
                case 2 -> {
                    checkBlockNames();
                    checkWalking();
                    checkRuin();
                    plugin.dungeons().buildForTest(1);
                    stage = 3;
                }
                case 3 -> {
                    if (plugin.dungeons().isBuilt(1)) {
                        checkDungeon();
                        finish();
                    }
                }
                default -> { }
            }
        } catch (Throwable t) {
            fail("excepción: " + t);
            t.printStackTrace();
            finish();
        }
    }

    private void checkVillage() {
        Location c = plugin.village().center();
        check(c != null, "la aldea tiene centro guardado");
        if (c == null) return;
        World w = c.getWorld();
        int cx = c.getBlockX(), cy = c.getBlockY() - 1, cz = c.getBlockZ();
        int built = 0, panes = 0, joined = 0;
        for (int x = cx - 44; x <= cx + 44; x++)
            for (int z = cz - 44; z <= cz + 44; z++)
                for (int y = cy + 1; y <= cy + 14; y++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (b.getType().isAir()) continue;
                    built++;
                    if (b.getType() == Material.GLASS_PANE && b.getBlockData() instanceof MultipleFacing mf) {
                        panes++;
                        if (!mf.getFaces().isEmpty()) joined++;
                    }
                }
        plugin.getLogger().info("[PRUEBA] aldea en " + cx + " " + cy + " " + cz + ": " + built + " bloques, " + panes + " vidrios");
        check(built > 8000, "la aldea tiene bloques construidos (" + built + ")");
        check(panes > 0 && joined >= panes * 0.9, "los vidrios están unidos (" + joined + "/" + panes + ")");
        Block below = w.getBlockAt(cx, cy, cz);
        check(!below.isLiquid() && below.getType() != Material.ICE, "la aldea no está sobre agua/hielo (" + below.getType() + ")");

        Map<String, UUID> story = plugin.npcEntities().storyIds();
        int alive = 0;
        for (Map.Entry<String, UUID> e : story.entrySet()) {
            Entity en = Bukkit.getEntity(e.getValue());
            if (en instanceof Mannequin m && m.isValid()) {
                alive++;
                if (m.customName() == null) fail("el NPC " + e.getKey() + " no tiene nombre");
            } else fail("el NPC " + e.getKey() + " no existe como maniquí");
        }
        check(alive == 8, "los 8 NPCs de la historia existen (" + alive + ")");
        int fill = 0;
        for (UUID u : plugin.npcEntities().fillerIds()) {
            Entity en = Bukkit.getEntity(u);
            if (en instanceof Mannequin && en.isValid()) {
                fill++;
                start.put(u, en.getLocation());
            }
        }
        check(fill >= 25, "hay aldeanos de relleno (" + fill + ")");
        check(plugin.getConfig().contains("campamento.x"), "el campamento quedó guardado");
    }

    private void checkWalking() {
        int moved = 0, stuck = 0;
        for (Map.Entry<UUID, Location> e : start.entrySet()) {
            Entity en = Bukkit.getEntity(e.getKey());
            if (en == null) continue;
            Location now = en.getLocation();
            if (now.distance(e.getValue()) > 0.5) moved++;
            Block feet = now.getBlock(), head = feet.getRelative(0, 1, 0);
            boolean inside = !head.isPassable() || (!feet.isPassable() && feet.getBoundingBox().getMaxY() > now.getY() + 0.05);
            if (inside) {
                stuck++;
                plugin.getLogger().info("[PRUEBA] dentro de bloque: " + feet.getType() + "/" + head.getType() + " en " + now.getBlockX() + " " + now.getY() + " " + now.getBlockZ());
            }
            if (now.getY() < e.getValue().getY() - 3) fail("un aldeano cayó (" + now + ")");
        }
        check(moved >= start.size() / 3, "los aldeanos caminan (" + moved + "/" + start.size() + ")");
        check(stuck == 0, "ningún aldeano dentro de un bloque (" + stuck + ")");
    }

    private void checkRuin() {
        Location c = plugin.village().center();
        World w = c.getWorld();
        int x = c.getBlockX() + 200, z = c.getBlockZ() + 200;
        w.getChunkAt(x >> 4, z >> 4).load(true);
        Block chest = plugin.ruins().buildAt(w, x, z);
        check(chest != null, "se construyó una ruina");
        if (chest != null && chest.getState() instanceof Chest ch) {
            int items = count(ch.getBlockInventory().getContents());
            check(items > 0, "el cofre de la ruina tiene botín (" + items + " objetos)");
        } else if (chest != null) fail("la ruina no tiene cofre en " + chest.getLocation());
    }

    private void checkDungeon() {
        Box b = plugin.dungeons().bounds(1);
        int[] e = plugin.dungeons().entrance(1);
        check(b != null && e != null, "la mazmorra del acto 1 tiene límites y entrada");
        if (b == null) return;
        World w = plugin.village().center().getWorld();
        int chests = 0, full = 0, spawners = 0, solid = 0;
        for (int x = b.x1(); x <= b.x2(); x++)
            for (int z = b.z1(); z <= b.z2(); z++)
                for (int y = b.y1(); y <= b.y2(); y++) {
                    Block bl = w.getBlockAt(x, y, z);
                    if (!bl.getType().isAir()) solid++;
                    if (bl.getType() == Material.CHEST && bl.getState() instanceof Chest ch) {
                        chests++;
                        if (count(ch.getBlockInventory().getContents()) > 0) full++;
                    }
                    if (bl.getType() == Material.SPAWNER) spawners++;
                }
        plugin.getLogger().info("[PRUEBA] mazmorra: " + solid + " bloques, " + chests + " cofres, " + spawners + " generadores");
        check(solid > 3000, "la mazmorra tiene bloques (" + solid + ")");
        check(chests > 0 && full == chests, "todos los cofres de la mazmorra tienen botín (" + full + "/" + chests + ")");
        check(spawners > 0, "la mazmorra tiene generadores (" + spawners + ")");
        Block gate = w.getBlockAt(e[0], e[1], e[2]);
        check(gate.isPassable(), "la entrada de la mazmorra está libre (" + gate.getType() + ")");
    }

    /** Revisa que todos los bloques de los 10 temas y de las ruinas existan en esta versión. */
    private void checkBlockNames() {
        java.util.Set<String> bad = new java.util.TreeSet<>();
        java.util.Set<String> keys = java.util.Set.of("suelo", "pared", "techo", "acento", "decoracion", "luz", "escalera",
                "losa", "muro", "peligro", "luz-tenue", "sangre", "tesoro");
        for (int act = 1; act <= 10; act++) {
            var t = plugin.dungeons().themeFor(act);
            if (t == null) {
                fail("no hay tema para el acto " + act);
                continue;
            }
            for (String k : keys) {
                List<String> vals = t.isList(k) ? t.getStringList(k) : (t.isString(k) ? List.of(t.getString(k)) : List.of());
                for (String v : vals) if (!valid(v)) bad.add("acto " + act + " " + k + "=" + v);
            }
            for (String line : t.getStringList("botin")) {
                String spec = line.trim().split("\\s+")[0];
                String sp = spec.contains(":") ? spec : "material:" + spec;
                if (plugin.shops().buildSpec(sp, 1) == null) bad.add("acto " + act + " botín " + spec);
            }
        }
        for (boolean desert : new boolean[]{false, true})
            for (pe.kydium.eclipserpg.dungeon.RuinBuilder.Variant v : pe.kydium.eclipserpg.dungeon.RuinBuilder.Variant.values())
                for (String st : new pe.kydium.eclipserpg.dungeon.RuinBuilder(1, desert).build(v).blocks.values())
                    if (!valid(st)) bad.add("ruina " + v + " " + st);
        for (String b : bad) plugin.getLogger().severe("[PRUEBA] bloque/objeto inválido: " + b);
        check(bad.isEmpty(), "todos los bloques y botines de mazmorras y ruinas existen (" + bad.size() + " malos)");
    }

    private static boolean valid(String state) {
        try {
            Bukkit.createBlockData(state.contains(":") ? state : "minecraft:" + state);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static int count(ItemStack[] items) {
        int n = 0;
        for (ItemStack it : items) if (it != null && !it.getType().isAir()) n++;
        return n;
    }

    private void finish() {
        if (task != null) task.cancel();
        if (fails.isEmpty()) plugin.getLogger().info("[PRUEBA] RESULTADO: TODO BIEN");
        else plugin.getLogger().severe("[PRUEBA] RESULTADO: " + fails.size() + " FALLOS");
        Bukkit.getScheduler().runTaskLater(plugin, Bukkit::shutdown, 40L);
    }
}
