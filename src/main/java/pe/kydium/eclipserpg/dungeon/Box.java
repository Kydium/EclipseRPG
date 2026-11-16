package pe.kydium.eclipserpg.dungeon;

import org.bukkit.Location;

import java.util.List;

/** Caja de bloques (inclusive) en coordenadas del mundo. */
public record Box(int x1, int y1, int z1, int x2, int y2, int z2) {

    public static Box of(int ax, int ay, int az, int bx, int by, int bz) {
        return new Box(Math.min(ax, bx), Math.min(ay, by), Math.min(az, bz),
                Math.max(ax, bx), Math.max(ay, by), Math.max(az, bz));
    }

    public Box grow(int n) {
        return new Box(x1 - n, y1 - n, z1 - n, x2 + n, y2 + n, z2 + n);
    }

    public boolean contains(Location l) {
        return contains(l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    public boolean contains(int x, int y, int z) {
        return x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
    }

    public int centerX() {
        return (x1 + x2) / 2;
    }

    public int centerZ() {
        return (z1 + z2) / 2;
    }

    public Box union(Box o) {
        return new Box(Math.min(x1, o.x1), Math.min(y1, o.y1), Math.min(z1, o.z1),
                Math.max(x2, o.x2), Math.max(y2, o.y2), Math.max(z2, o.z2));
    }

    public List<Integer> toList() {
        return List.of(x1, y1, z1, x2, y2, z2);
    }

    public static Box fromList(List<Integer> l) {
        return new Box(l.get(0), l.get(1), l.get(2), l.get(3), l.get(4), l.get(5));
    }
}
