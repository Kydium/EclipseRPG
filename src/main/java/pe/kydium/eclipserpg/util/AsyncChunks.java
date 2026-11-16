package pe.kydium.eclipserpg.util;

import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Carga chunks sin congelar el servidor (Paper los genera en otros hilos).
 * Las respuestas llegan en el hilo principal, así que después se puede usar la API normal.
 */
public final class AsyncChunks {

    private AsyncChunks() {}

    /** Chunks que tocan los puntos (x,z) dados. */
    public static Set<Long> around(int[][] points) {
        Set<Long> out = new LinkedHashSet<>();
        for (int[] p : points) out.add(key(p[0] >> 4, p[1] >> 4));
        return out;
    }

    /** Todos los chunks de un rectángulo de bloques. */
    public static Set<Long> area(int x1, int z1, int x2, int z2) {
        Set<Long> out = new LinkedHashSet<>();
        for (int cx = Math.min(x1, x2) >> 4; cx <= Math.max(x1, x2) >> 4; cx++)
            for (int cz = Math.min(z1, z2) >> 4; cz <= Math.max(z1, z2) >> 4; cz++) out.add(key(cx, cz));
        return out;
    }

    public static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xffffffffL);
    }

    /** Carga (y genera si hace falta) todos los chunks; termina cuando estén listos. */
    public static CompletableFuture<Void> load(World w, Collection<Long> chunks) {
        List<CompletableFuture<?>> fs = new ArrayList<>();
        for (long k : chunks) {
            int cx = (int) (k >> 32), cz = (int) k;
            if (w.isChunkLoaded(cx, cz)) continue;
            fs.add(w.getChunkAtAsync(cx, cz, true));
        }
        return CompletableFuture.allOf(fs.toArray(new CompletableFuture[0]));
    }
}
