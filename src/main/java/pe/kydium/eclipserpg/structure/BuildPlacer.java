package pe.kydium.eclipserpg.structure;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.dungeon.Connect;
import pe.kydium.eclipserpg.gen.Build;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Pone en el mundo lo que armó el generador, poco a poco para no trabar el servidor. */
public final class BuildPlacer {

    private BuildPlacer() {}

    private static final Map<String, BlockData> CACHE = new HashMap<>();

    /** BlockData de un texto; si el estado no existe en esta versión prueba sin propiedades; null si el bloque no existe. */
    public static BlockData data(String s) {
        if (CACHE.containsKey(s)) return CACHE.get(s);
        BlockData d = null;
        String full = s.contains(":") ? s : "minecraft:" + s;
        try {
            d = Bukkit.createBlockData(full);
        } catch (IllegalArgumentException e) {
            try {
                d = Bukkit.createBlockData(full.contains("[") ? full.substring(0, full.indexOf('[')) : full);
            } catch (IllegalArgumentException e2) {
                if (s.startsWith("chain")) d = data("iron_" + s);
            }
        }
        CACHE.put(s, d);
        return d;
    }

    /** Coloca la construcción; al final llama a done con la lista de contenedores (cofres, barriles). */
    public static void place(EclipseRPG plugin, World w, Build b, int perTick, IntConsumer progress, Consumer<List<int[]>> done) {
        List<int[]> joins = new ArrayList<>();
        List<int[]> containers = new ArrayList<>();
        Iterator<Map.Entry<Long, String>> it = b.blocks.entrySet().iterator();
        int total = b.blocks.size();
        int[] n = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int budget = perTick;
            while (it.hasNext() && budget-- > 0) {
                Map.Entry<Long, String> e = it.next();
                n[0]++;
                BlockData d = data(e.getValue());
                if (d == null) continue;
                long k = e.getKey();
                int x = Build.kx(k), y = Build.ky(k), z = Build.kz(k);
                if (y < w.getMinHeight() || y >= w.getMaxHeight()) continue;
                Block bl = w.getBlockAt(x, y, z);
                Material m = d.getMaterial();
                bl.setBlockData(d, false);
                if (Connect.needs(d)) joins.add(new int[]{x, y, z});
                if (m == Material.CHEST || m == Material.BARREL || m == Material.TRAPPED_CHEST) containers.add(new int[]{x, y, z});
                if (m == Material.SPAWNER && bl.getState() instanceof org.bukkit.block.CreatureSpawner sp) {
                    sp.setSpawnedType(switch (Math.floorMod(x + z, 3)) {
                        case 0 -> org.bukkit.entity.EntityType.ZOMBIE;
                        case 1 -> org.bukkit.entity.EntityType.SKELETON;
                        default -> org.bukkit.entity.EntityType.SPIDER;
                    });
                    sp.update(true, false);
                }
            }
            if (progress != null) progress.accept(total == 0 ? 100 : 100 * n[0] / total);
            if (it.hasNext()) return;
            task.cancel();
            Connect.fix(w, joins);
            done.accept(containers);
        }, 1L, 1L);
    }
}
