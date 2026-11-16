package pe.kydium.eclipserpg.dungeon;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Wall;

import java.util.Collection;

/**
 * Une vidrios, barrotes, vallas y muros con sus vecinos después de construir
 * (si no, cada panel de vidrio se ve como un poste suelto).
 */
public final class Connect {

    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private Connect() {}

    public static boolean needs(BlockData d) {
        return d instanceof MultipleFacing || d instanceof Wall;
    }

    public static void fix(World w, Collection<int[]> positions) {
        for (int[] p : positions) fix(w.getBlockAt(p[0], p[1], p[2]));
    }

    public static void fix(Block b) {
        BlockData d = b.getBlockData();
        Material m = b.getType();
        if (d instanceof Wall wall) {
            boolean[] c = new boolean[4];
            for (int i = 0; i < 4; i++) {
                c[i] = connects(b, SIDES[i], Kind.WALL, m);
                wall.setHeight(SIDES[i], c[i] ? Wall.Height.LOW : Wall.Height.NONE);
            }
            boolean straight = (c[0] && c[2] && !c[1] && !c[3]) || (c[1] && c[3] && !c[0] && !c[2]);
            wall.setUp(!straight || !b.getRelative(BlockFace.UP).getType().isAir());
            b.setBlockData(wall, false);
            return;
        }
        if (!(d instanceof MultipleFacing mf)) return;
        Kind k = kind(m);
        if (k == null) return;
        for (BlockFace f : SIDES) {
            if (mf.getAllowedFaces().contains(f)) mf.setFace(f, connects(b, f, k, m));
        }
        b.setBlockData(mf, false);
    }

    private enum Kind { PANE, FENCE, WALL }

    private static Kind kind(Material m) {
        String n = m.name();
        if (n.endsWith("GLASS_PANE") || m == Material.IRON_BARS) return Kind.PANE;
        if (n.endsWith("_FENCE")) return Kind.FENCE;
        return null;
    }

    private static boolean connects(Block b, BlockFace f, Kind k, Material self) {
        Block n = b.getRelative(f);
        Material nm = n.getType();
        if (nm.isAir()) return false;
        String nn = nm.name();
        BlockData nd = n.getBlockData();
        Kind nk = nd instanceof Wall ? Kind.WALL : kind(nm);
        boolean gate = nn.endsWith("FENCE_GATE");
        switch (k) {
            case PANE -> { if (nk == Kind.PANE || nk == Kind.WALL) return true; }
            case FENCE -> {
                if (nk == Kind.FENCE) return (nm == Material.NETHER_BRICK_FENCE) == (self == Material.NETHER_BRICK_FENCE);
                if (gate) return true;
            }
            case WALL -> { if (nk == Kind.WALL || nk == Kind.PANE || gate) return true; }
        }
        if (nn.endsWith("LEAVES")) return false;
        return nd.isFaceSturdy(f.getOppositeFace(), BlockSupport.FULL);
    }
}
