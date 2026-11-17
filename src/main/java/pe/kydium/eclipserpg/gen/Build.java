package pe.kydium.eclipserpg.gen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resultado de un generador: bloques (coordenadas del mundo) y marcas (NPCs, enemigos, cofres…). */
public final class Build {

    public record Marker(String type, int x, int y, int z, String arg, float yaw) {}

    /** Bloques en orden de colocación; el último que se escribe en una posición gana. */
    public final Map<Long, String> blocks = new LinkedHashMap<>();
    public final List<Marker> markers = new ArrayList<>();
    /** Columnas del terreno que el generador ya modificó: altura nueva del suelo. */
    public final Map<Long, Integer> groundOverride = new HashMap<>();
    public int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
    public int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

    public static long key(int x, int y, int z) {
        return ((long) (x + 33_554_432) << 38) | ((long) (z + 33_554_432) << 12) | (y + 2048L);
    }

    public static int kx(long k) {
        return (int) (k >>> 38) - 33_554_432;
    }

    public static int kz(long k) {
        return (int) ((k >>> 12) & 0x3FFFFFFL) - 33_554_432;
    }

    public static int ky(long k) {
        return (int) (k & 0xFFFL) - 2048;
    }

    public static long col(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    public void set(int x, int y, int z, String b) {
        long k = key(x, y, z);
        blocks.remove(k);
        blocks.put(k, b);
        if (x < minX) minX = x;
        if (y < minY) minY = y;
        if (z < minZ) minZ = z;
        if (x > maxX) maxX = x;
        if (y > maxY) maxY = y;
        if (z > maxZ) maxZ = z;
    }

    public String get(int x, int y, int z) {
        return blocks.get(key(x, y, z));
    }

    /** Solo si ahí no puso nada el generador todavía. */
    public void soft(int x, int y, int z, String b) {
        if (!blocks.containsKey(key(x, y, z))) set(x, y, z, b);
    }

    public void mark(String type, int x, int y, int z, String arg, float yaw) {
        markers.add(new Marker(type, x, y, z, arg, yaw));
    }

    public void mark(String type, int x, int y, int z) {
        mark(type, x, y, z, "", 0);
    }

    /** Suelo actual, teniendo en cuenta lo que ya cambió el generador. */
    public int ground(Terrain t, int x, int z) {
        Integer g = groundOverride.get(col(x, z));
        return g != null ? g : t.ground(x, z);
    }

    public void setGround(int x, int z, int y) {
        groundOverride.put(col(x, z), y);
    }
}
