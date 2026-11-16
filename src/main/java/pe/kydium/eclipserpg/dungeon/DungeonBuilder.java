package pe.kydium.eclipserpg.dungeon;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Diseña una mazmorra: salas en una cuadrícula de celdas de 16x16 unidas por pasillos,
 * con entrada desde la superficie y la sala del jefe en la celda más lejana.
 * Solo calcula bloques; DungeonManager los coloca poco a poco.
 */
public final class DungeonBuilder {

    public static final int CELL = 16;

    public enum Special { NONE, CHEST, SPAWNER, TRAP_DISPENSER, SCAFFOLD }

    public record Op(int x, int y, int z, Material mat, Special special) {}

    public static final class Theme {
        public List<Material> floor = new ArrayList<>();
        public List<Material> wall = new ArrayList<>();
        public List<Material> ceiling = new ArrayList<>();
        public List<Material> accent = new ArrayList<>();
        public List<Material> decor = new ArrayList<>();
        public Material light = Material.GLOWSTONE;
    }

    public static final class RoomPlan {
        public final int cx, cz;
        public Box interior;
        public boolean boss, start, treasure;

        RoomPlan(int cx, int cz) {
            this.cx = cx;
            this.cz = cz;
        }
    }

    public static final class Result {
        public final List<Op> ops = new ArrayList<>();
        public final List<RoomPlan> rooms = new ArrayList<>();
        public final List<Box> bossDoors = new ArrayList<>();
        public Box bounds;
        public int entranceX, entranceY, entranceZ;
    }

    private final Random rnd;
    private final Theme theme;
    private final int baseX, floorY, baseZ, surfaceY;
    private final Map<Long, Op> blocks = new LinkedHashMap<>();
    private final Set<Long> carved = new HashSet<>();

    public DungeonBuilder(long seed, Theme theme, int entranceX, int floorY, int entranceZ, int surfaceY) {
        this.rnd = new Random(seed);
        this.theme = theme;
        // la celda (0,0) queda centrada en la entrada
        this.baseX = entranceX - CELL / 2;
        this.baseZ = entranceZ - CELL / 2;
        this.floorY = floorY;
        this.surfaceY = surfaceY;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private Material pick(List<Material> l, Material def) {
        return l.isEmpty() ? def : l.get(rnd.nextInt(l.size()));
    }

    private void solid(int x, int y, int z, Material m) {
        long k = key(x, y, z);
        if (carved.contains(k)) return;
        blocks.put(k, new Op(x, y, z, m, Special.NONE));
    }

    private void set(int x, int y, int z, Material m, Special sp) {
        long k = key(x, y, z);
        blocks.put(k, new Op(x, y, z, m, sp));
    }

    private void air(int x, int y, int z) {
        long k = key(x, y, z);
        carved.add(k);
        blocks.put(k, new Op(x, y, z, Material.AIR, Special.NONE));
    }

    /** Paredes, suelo y techo alrededor de un interior. */
    private void shell(Box in, boolean lights, int lightStep) {
        Box out = in.grow(1);
        for (int x = out.x1(); x <= out.x2(); x++) {
            for (int z = out.z1(); z <= out.z2(); z++) {
                solid(x, out.y1(), z, pick(theme.floor, Material.STONE_BRICKS));
                boolean light = lights && (x - in.x1()) % lightStep == 1 && (z - in.z1()) % lightStep == 1
                        && in.contains(x, in.y1(), z);
                solid(x, out.y2(), z, light ? theme.light : pick(theme.ceiling, Material.STONE_BRICKS));
                for (int y = out.y1() + 1; y < out.y2(); y++) {
                    if (x == out.x1() || x == out.x2() || z == out.z1() || z == out.z2()) {
                        Material m = rnd.nextDouble() < 0.12 && !theme.accent.isEmpty()
                                ? pick(theme.accent, Material.STONE_BRICKS) : pick(theme.wall, Material.STONE_BRICKS);
                        solid(x, y, z, m);
                    }
                }
            }
        }
    }

    private void carve(Box in) {
        for (int x = in.x1(); x <= in.x2(); x++)
            for (int y = in.y1(); y <= in.y2(); y++)
                for (int z = in.z1(); z <= in.z2(); z++) air(x, y, z);
    }

    public Result build(int minRooms, int maxRooms) {
        Result res = new Result();
        int target = minRooms + rnd.nextInt(Math.max(1, maxRooms - minRooms + 1));

        // 1) Recorrido aleatorio de celdas
        Map<Long, RoomPlan> cells = new LinkedHashMap<>();
        List<long[]> edges = new ArrayList<>();
        RoomPlan start = new RoomPlan(0, 0);
        start.start = true;
        cells.put(cellKey(0, 0), start);
        List<RoomPlan> order = new ArrayList<>();
        order.add(start);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int guard = 0;
        RoomPlan cur = start;
        while (cells.size() < target && guard++ < 500) {
            int[] d = dirs[rnd.nextInt(4)];
            int nx = cur.cx + d[0], nz = cur.cz + d[1];
            if (Math.abs(nx) > 3 || Math.abs(nz) > 3) {
                cur = order.get(rnd.nextInt(order.size()));
                continue;
            }
            long k = cellKey(nx, nz);
            RoomPlan next = cells.get(k);
            if (next == null) {
                next = new RoomPlan(nx, nz);
                cells.put(k, next);
                order.add(next);
                edges.add(new long[]{cellKey(cur.cx, cur.cz), k});
            }
            cur = rnd.nextDouble() < 0.7 ? next : order.get(rnd.nextInt(order.size()));
        }

        // 2) Sala del jefe = la más lejana desde la entrada (por pasillos)
        Map<Long, List<Long>> adj = new HashMap<>();
        for (long[] e : edges) {
            adj.computeIfAbsent(e[0], x -> new ArrayList<>()).add(e[1]);
            adj.computeIfAbsent(e[1], x -> new ArrayList<>()).add(e[0]);
        }
        Map<Long, Integer> dist = new HashMap<>();
        List<Long> queue = new ArrayList<>();
        queue.add(cellKey(0, 0));
        dist.put(cellKey(0, 0), 0);
        for (int i = 0; i < queue.size(); i++) {
            long c = queue.get(i);
            for (long n : adj.getOrDefault(c, List.of())) {
                if (!dist.containsKey(n)) {
                    dist.put(n, dist.get(c) + 1);
                    queue.add(n);
                }
            }
        }
        long bossKey = cellKey(0, 0);
        for (Map.Entry<Long, Integer> e : dist.entrySet()) {
            if (e.getValue() > dist.get(bossKey)) bossKey = e.getKey();
        }
        cells.get(bossKey).boss = true;
        for (RoomPlan r : cells.values()) {
            int links = adj.getOrDefault(cellKey(r.cx, r.cz), List.of()).size();
            if (!r.boss && !r.start && links == 1) r.treasure = true;
        }

        // 3) Geometría de las salas
        for (RoomPlan r : cells.values()) {
            int ox = baseX + r.cx * CELL, oz = baseZ + r.cz * CELL;
            if (r.boss) r.interior = Box.of(ox + 1, floorY + 1, oz + 1, ox + 14, floorY + 8, oz + 14);
            else r.interior = Box.of(ox + 3, floorY + 1, oz + 3, ox + 12, floorY + 5, oz + 12);
            res.rooms.add(r);
        }

        // 4) Pasillos
        List<Box> corridors = new ArrayList<>();
        for (long[] e : edges) {
            RoomPlan a = cells.get(e[0]), b = cells.get(e[1]);
            Box c;
            if (a.cx != b.cx) {
                RoomPlan w = a.cx < b.cx ? a : b, east = a.cx < b.cx ? b : a;
                int z0 = baseZ + w.cz * CELL + 7;
                c = Box.of(w.interior.x2() + 1, floorY + 1, z0, east.interior.x1() - 1, floorY + 3, z0 + 2);
                if (east.boss) res.bossDoors.add(Box.of(east.interior.x1() - 1, floorY + 1, z0, east.interior.x1() - 1, floorY + 3, z0 + 2));
                if (w.boss) res.bossDoors.add(Box.of(w.interior.x2() + 1, floorY + 1, z0, w.interior.x2() + 1, floorY + 3, z0 + 2));
            } else {
                RoomPlan n = a.cz < b.cz ? a : b, s = a.cz < b.cz ? b : a;
                int x0 = baseX + n.cx * CELL + 7;
                c = Box.of(x0, floorY + 1, n.interior.z2() + 1, x0 + 2, floorY + 3, s.interior.z1() - 1);
                if (s.boss) res.bossDoors.add(Box.of(x0, floorY + 1, s.interior.z1() - 1, x0 + 2, floorY + 3, s.interior.z1() - 1));
                if (n.boss) res.bossDoors.add(Box.of(x0, floorY + 1, n.interior.z2() + 1, x0 + 2, floorY + 3, n.interior.z2() + 1));
            }
            corridors.add(c);
        }

        // 5) Conducto de entrada hasta la superficie
        int ex = baseX + CELL / 2, ez = baseZ + CELL / 2;
        Box shaft = Box.of(ex - 1, start.interior.y2() + 1, ez - 1, ex + 1, surfaceY + 1, ez + 1);

        // 6) Colocar: primero cascarones, después interiores
        for (RoomPlan r : cells.values()) shell(r.interior, true, r.boss ? 4 : 4);
        for (Box c : corridors) shell(c, true, 6);
        shell(shaft, false, 99);
        for (RoomPlan r : cells.values()) carve(r.interior);
        for (Box c : corridors) carve(c);
        carve(shaft);
        // abrir el techo del conducto en la superficie
        for (int x = ex - 1; x <= ex + 1; x++)
            for (int z = ez - 1; z <= ez + 1; z++) air(x, surfaceY + 2, z);

        // escalera de andamios para subir/bajar
        for (int y = floorY + 1; y <= surfaceY + 1; y++) set(ex, y, ez, Material.SCAFFOLDING, Special.SCAFFOLD);

        // 7) Arco de entrada en la superficie
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                if (corner) {
                    for (int y = surfaceY + 1; y <= surfaceY + 5; y++) set(ex + dx, y, ez + dz, pick(theme.accent, Material.CHISELED_STONE_BRICKS), Special.NONE);
                    set(ex + dx, surfaceY + 6, ez + dz, theme.light, Special.NONE);
                } else if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                    set(ex + dx, surfaceY + 1, ez + dz, pick(theme.wall, Material.STONE_BRICKS), Special.NONE);
                    if (rnd.nextBoolean()) set(ex + dx, surfaceY + 5, ez + dz, pick(theme.wall, Material.STONE_BRICKS), Special.NONE);
                }
            }
        }

        // 8) Decoración, cofres, generadores y trampas
        for (RoomPlan r : cells.values()) {
            Box in = r.interior;
            int y = in.y1();
            for (int x = in.x1(); x <= in.x2(); x++) {
                for (int z = in.z1(); z <= in.z2(); z++) {
                    boolean edge = x == in.x1() || x == in.x2() || z == in.z1() || z == in.z2();
                    boolean nearShaft = r.start && Math.abs(x - ex) <= 1 && Math.abs(z - ez) <= 1;
                    if (edge && !nearShaft && !theme.decor.isEmpty() && rnd.nextDouble() < 0.10) {
                        set(x, y, z, pick(theme.decor, Material.COBWEB), Special.NONE);
                    }
                }
            }
            if (r.treasure) {
                set(in.x1() + 1, y, in.z1() + 1, Material.CHEST, Special.CHEST);
                set(in.x2() - 1, y, in.z2() - 1, Material.CHEST, Special.CHEST);
            } else if (!r.boss && !r.start && rnd.nextDouble() < 0.35) {
                set(in.x2() - 1, y, in.z1() + 1, Material.CHEST, Special.CHEST);
            }
            if (!r.boss && !r.start && !r.treasure && rnd.nextDouble() < 0.4) {
                set(in.centerX(), y, in.centerZ(), Material.SPAWNER, Special.SPAWNER);
            }
            if (r.boss) {
                // pilares en las esquinas de la sala del jefe
                int[][] pil = {{in.x1() + 2, in.z1() + 2}, {in.x2() - 2, in.z1() + 2}, {in.x1() + 2, in.z2() - 2}, {in.x2() - 2, in.z2() - 2}};
                for (int[] pc : pil) {
                    for (int py = in.y1(); py <= in.y2(); py++) set(pc[0], py, pc[1], pick(theme.accent, Material.CHISELED_STONE_BRICKS), Special.NONE);
                }
            }
        }
        for (Box c : corridors) {
            int len = Math.max(c.x2() - c.x1(), c.z2() - c.z1());
            if (len >= 4 && rnd.nextDouble() < 0.45) {
                int tx = c.centerX(), tz = c.centerZ();
                set(tx, floorY, tz, Material.DISPENSER, Special.TRAP_DISPENSER);
                set(tx, floorY + 1, tz, Material.STONE_PRESSURE_PLATE, Special.NONE);
            }
            if (rnd.nextDouble() < 0.4) set(c.x1(), c.y2(), c.z1(), Material.COBWEB, Special.NONE);
        }

        res.ops.addAll(blocks.values());
        Box b = shaft.grow(3);
        for (RoomPlan r : cells.values()) b = b.union(r.interior.grow(1));
        for (Box c : corridors) b = b.union(c.grow(1));
        res.bounds = b;
        res.entranceX = ex;
        res.entranceY = surfaceY + 1;
        res.entranceZ = ez;
        return res;
    }

    private static long cellKey(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }
}
