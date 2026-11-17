package pe.kydium.eclipserpg.structure;

import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import pe.kydium.eclipserpg.gen.Build;
import pe.kydium.eclipserpg.gen.Terrain;

import java.util.HashMap;
import java.util.Map;

/** El terreno real del mundo para el generador (los chunks tienen que estar cargados). */
public final class WorldTerrain implements Terrain {
    private final World w;
    private final Map<Long, Block> top = new HashMap<>();

    public WorldTerrain(World w) {
        this.w = w;
    }

    private static boolean skip(Material m) {
        String n = m.name();
        return m.isAir() || n.endsWith("_LOG") || n.endsWith("_WOOD") || n.endsWith("_LEAVES") || n.endsWith("_STEM") || n.endsWith("_HYPHAE")
                || !m.isSolid() && !n.equals("WATER") && !n.equals("LAVA") || n.contains("MUSHROOM_BLOCK") || n.equals("BEE_NEST") || n.equals("MOSS_CARPET")
                || n.equals("SNOW") || n.equals("BAMBOO") || n.equals("CACTUS") || n.equals("MANGROVE_ROOTS") || n.equals("PALE_MOSS_CARPET");
    }

    private Block topBlock(int x, int z) {
        return top.computeIfAbsent(Build.col(x, z), k -> {
            Block b = w.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            int guard = 0;
            while (guard++ < 40 && b.getY() > w.getMinHeight() + 1 && skip(b.getType())) b = b.getRelative(0, -1, 0);
            return b;
        });
    }

    @Override
    public int ground(int x, int z) {
        return topBlock(x, z).getY();
    }

    @Override
    public boolean water(int x, int z) {
        Material m = topBlock(x, z).getType();
        return m == Material.WATER || m == Material.LAVA || m == Material.ICE || m == Material.PACKED_ICE || m == Material.BLUE_ICE
                || m == Material.KELP || m == Material.SEAGRASS || m == Material.TALL_SEAGRASS || topBlock(x, z).getBlockData() instanceof org.bukkit.block.data.Waterlogged wl && wl.isWaterlogged();
    }

    @Override
    public String surface(int x, int z) {
        return topBlock(x, z).getType().getKey().getKey();
    }

    public String biome(int x, int z) {
        return w.getBiome(x, ground(x, z), z).getKey().getKey();
    }
}
