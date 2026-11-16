package pe.kydium.eclipserpg.dungeon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Diseña una mazmorra: salas temáticas en una cuadrícula de celdas de 18x18 unidas por
 * pasillos con arcos, una gran escalera que baja desde un portón en la superficie y la
 * arena del jefe en la sala más lejana. Solo calcula bloques (como texto de estado de
 * bloque, p. ej. "stone_brick_stairs[facing=west]"); DungeonManager los coloca.
 */
public final class DungeonBuilder {

    public static final int CELL = 18;

    public enum Special { NONE, CHEST, SPAWNER, TRAP_DISPENSER }

    public enum RoomType { START, HALL, CRYPT, PRISON, CHASM, RITUAL, TREASURE, BOSS }

    public record Op(int x, int y, int z, String block, Special special) {}

    /** Paleta de bloques de un acto (ids de Minecraft). */
    public static final class Theme {
        public List<String> floor = new ArrayList<>();
        public List<String> wall = new ArrayList<>();
        public List<String> ceiling = new ArrayList<>();
        public List<String> accent = new ArrayList<>();
        public List<String> decor = new ArrayList<>();
        public String light = "glowstone";
        public String stairs = "stone_brick_stairs";
        public String slab = "stone_brick_slab";
        public String wallBlock = "cobblestone_wall";
        public String hazard = "lava";
        public String dimLight = "soul_lantern";
        public String blood = "nether_wart_block";
        public String treasure = "gold_block";
    }

    public static final class RoomPlan {
        public final int cx, cz;
        public Box interior;
        public RoomType type = RoomType.HALL;
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
    private final Theme t;
    private final int baseX, floorY, baseZ, surfaceY;
    private final Map<Long, Op> blocks = new LinkedHashMap<>();
    private final Set<Long> carved = new HashSet<>();

    public DungeonBuilder(long seed, Theme theme, int entranceX, int floorY, int entranceZ, int surfaceY) {
        this.rnd = new Random(seed);
        this.t = theme;
        // la escalera baja hacia el este: la sala de inicio queda al este de la entrada
        int depth = surfaceY - floorY;
        this.baseX = entranceX + depth + 4 - CELL / 2;
        this.baseZ = entranceZ - CELL / 2;
        this.floorY = floorY;
        this.surfaceY = surfaceY;
    }

    // ---------- utilidades ----------

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private String pick(List<String> l, String def) {
        return l.isEmpty() ? def : l.get(rnd.nextInt(l.size()));
    }

    private void solid(int x, int y, int z, String b) {
        long k = key(x, y, z);
        if (carved.contains(k)) return;
        blocks.put(k, new Op(x, y, z, b, Special.NONE));
    }

    private void set(int x, int y, int z, String b) {
        set(x, y, z, b, Special.NONE);
    }

    private void set(int x, int y, int z, String b, Special sp) {
        blocks.put(key(x, y, z), new Op(x, y, z, b, sp));
    }

    private boolean isAirAt(int x, int y, int z) {
        Op o = blocks.get(key(x, y, z));
        return o != null && o.block().equals("air");
    }

    private void air(int x, int y, int z) {
        long k = key(x, y, z);
        carved.add(k);
        blocks.put(k, new Op(x, y, z, "air", Special.NONE));
    }

    private void shell(Box in) {
        Box out = in.grow(1);
        for (int x = out.x1(); x <= out.x2(); x++) {
            for (int z = out.z1(); z <= out.z2(); z++) {
                solid(x, out.y1(), z, pick(t.floor, "stone_bricks"));
                solid(x, out.y2(), z, pick(t.ceiling, "stone_bricks"));
                for (int y = out.y1() + 1; y < out.y2(); y++) {
                    if (x == out.x1() || x == out.x2() || z == out.z1() || z == out.z2()) {
                        String m = rnd.nextDouble() < 0.14 && !t.accent.isEmpty() ? pick(t.accent, "stone_bricks") : pick(t.wall, "stone_bricks");
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

    private void column(int x, int z, int y1, int y2, String b) {
        for (int y = y1; y <= y2; y++) set(x, y, z, b);
    }

    /** Cadena colgando del techo con un farol tenue al final. */
    private void hangingChain(int x, int z, int top, int len) {
        for (int y = top; y > top - len; y--) set(x, y, z, "chain");
        set(x, top - len, z, t.dimLight + "[hanging=true]");
    }

    private void floorStain(Box in, int count) {
        for (int i = 0; i < count; i++) {
            int x = in.x1() + rnd.nextInt(in.x2() - in.x1() + 1);
            int z = in.z1() + rnd.nextInt(in.z2() - in.z1() + 1);
            set(x, in.y1() - 1, z, t.blood);
            if (rnd.nextBoolean() && x + 1 <= in.x2()) set(x + 1, in.y1() - 1, z, t.blood);
        }
    }

    private void scatterBones(Box in, int count) {
        for (int i = 0; i < count; i++) {
            int x = in.x1() + 1 + rnd.nextInt(Math.max(1, in.x2() - in.x1() - 1));
            int z = in.z1() + 1 + rnd.nextInt(Math.max(1, in.z2() - in.z1() - 1));
            if (!isAirAt(x, in.y1(), z)) continue;
            double r = rnd.nextDouble();
            String b = r < 0.35 ? "skeleton_skull[rotation=" + rnd.nextInt(16) + "]"
                    : r < 0.6 ? "bone_block[axis=" + (rnd.nextBoolean() ? "x" : "z") + "]"
                    : r < 0.8 ? "cobweb" : pick(t.decor, "cobweb");
            set(x, in.y1(), z, b);
        }
    }

    // ---------- diseño ----------

    public Result build(int minRooms, int maxRooms) {
        Result res = new Result();
        int target = minRooms + rnd.nextInt(Math.max(1, maxRooms - minRooms + 1));

        // 1) Recorrido aleatorio de celdas (la entrada es la celda 0,0 y la escalera llega por el oeste)
        Map<Long, RoomPlan> cells = new LinkedHashMap<>();
        List<long[]> edges = new ArrayList<>();
        RoomPlan start = new RoomPlan(0, 0);
        start.start = true;
        start.type = RoomType.START;
        cells.put(cellKey(0, 0), start);
        List<RoomPlan> order = new ArrayList<>();
        order.add(start);
        int[][] dirs = {{1, 0}, {0, 1}, {0, -1}, {1, 0}};
        int guard = 0;
        RoomPlan cur = start;
        while (cells.size() < target && guard++ < 800) {
            int[] d = dirs[rnd.nextInt(dirs.length)];
            int nx = cur.cx + d[0], nz = cur.cz + d[1];
            if (nx < 0 || nx > 4 || Math.abs(nz) > 2) {
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

        // 2) Distancias desde la entrada: la sala más lejana es la del jefe
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
        for (Map.Entry<Long, Integer> e : dist.entrySet()) if (e.getValue() > dist.get(bossKey)) bossKey = e.getKey();
        RoomPlan bossRoom = cells.get(bossKey);
        bossRoom.boss = true;
        bossRoom.type = RoomType.BOSS;
        RoomType[] pool = {RoomType.HALL, RoomType.CRYPT, RoomType.PRISON, RoomType.CHASM, RoomType.RITUAL, RoomType.HALL};
        int pi = rnd.nextInt(pool.length);
        for (RoomPlan r : cells.values()) {
            if (r.boss || r.start) continue;
            int links = adj.getOrDefault(cellKey(r.cx, r.cz), List.of()).size();
            if (links == 1) {
                r.treasure = true;
                r.type = RoomType.TREASURE;
            } else {
                r.type = pool[pi++ % pool.length];
            }
        }

        // 3) Geometría: salas de distintos tamaños y alturas
        for (RoomPlan r : cells.values()) {
            int ox = baseX + r.cx * CELL, oz = baseZ + r.cz * CELL;
            int half, h;
            switch (r.type) {
                case BOSS -> { half = 8; h = 11; }
                case CHASM -> { half = 7; h = 8; }
                case HALL, RITUAL -> { half = 6 + rnd.nextInt(2); h = 7; }
                case START -> { half = 5; h = 6; }
                default -> { half = 5; h = 6; }
            }
            int c = CELL / 2;
            r.interior = Box.of(ox + c - half, floorY + 1, oz + c - half, ox + c + half, floorY + h, oz + c + half);
            res.rooms.add(r);
        }

        // 4) Pasillos (3 de ancho, 4 de alto) entre celdas vecinas
        List<Box> corridors = new ArrayList<>();
        for (long[] e : edges) {
            RoomPlan a = cells.get(e[0]), b = cells.get(e[1]);
            Box c;
            if (a.cx != b.cx) {
                RoomPlan w = a.cx < b.cx ? a : b, east = a.cx < b.cx ? b : a;
                int z0 = baseZ + w.cz * CELL + CELL / 2 - 1;
                c = Box.of(w.interior.x2() + 1, floorY + 1, z0, east.interior.x1() - 1, floorY + 4, z0 + 2);
                if (east.boss) res.bossDoors.add(Box.of(east.interior.x1() - 1, floorY + 1, z0, east.interior.x1() - 1, floorY + 4, z0 + 2));
                if (w.boss) res.bossDoors.add(Box.of(w.interior.x2() + 1, floorY + 1, z0, w.interior.x2() + 1, floorY + 4, z0 + 2));
            } else {
                RoomPlan n = a.cz < b.cz ? a : b, s = a.cz < b.cz ? b : a;
                int x0 = baseX + n.cx * CELL + CELL / 2 - 1;
                c = Box.of(x0, floorY + 1, n.interior.z2() + 1, x0 + 2, floorY + 4, s.interior.z1() - 1);
                if (s.boss) res.bossDoors.add(Box.of(x0, floorY + 1, s.interior.z1() - 1, x0 + 2, floorY + 4, s.interior.z1() - 1));
                if (n.boss) res.bossDoors.add(Box.of(x0, floorY + 1, n.interior.z2() + 1, x0 + 2, floorY + 4, n.interior.z2() + 1));
            }
            corridors.add(c);
        }

        // 5) Gran escalera desde la superficie hasta la sala de inicio (sube hacia el oeste)
        int depth = surfaceY - floorY;
        int sz0 = baseZ + CELL / 2 - 1;
        int stairEndX = start.interior.x1() - 1;          // pegada a la pared oeste de la sala de inicio
        int stairTopX = stairEndX - depth;                 // arriba, en la superficie
        List<Box> stairBoxes = new ArrayList<>();
        for (int i = 0; i <= depth; i++) {
            int x = stairEndX - i;
            int yFloor = floorY + i;                       // la escalera sube 1 por bloque
            stairBoxes.add(Box.of(x, yFloor + 1, sz0, x, yFloor + 4, sz0 + 2));
        }
        Box landing = Box.of(stairTopX - 3, surfaceY + 1, sz0, stairTopX - 1, surfaceY + 4, sz0 + 2);

        // 6) Cascarones y huecos
        for (RoomPlan r : cells.values()) shell(r.interior);
        for (Box c : corridors) shell(c);
        for (Box s : stairBoxes) shell(s);
        for (RoomPlan r : cells.values()) carve(r.interior);
        for (Box c : corridors) carve(c);
        for (Box s : stairBoxes) carve(s);
        for (int i = 0; i <= depth; i++) {
            int x = stairEndX - i;
            for (int z = sz0; z <= sz0 + 2; z++) set(x, floorY + i, z, t.stairs + "[facing=west,half=bottom]");
            // arcos cada 4 escalones
            if (i % 4 == 0) {
                for (int y = floorY + i + 1; y <= floorY + i + 4; y++) {
                    set(x, y, sz0 - 1, pick(t.accent, "chiseled_stone_bricks"));
                    set(x, y, sz0 + 3, pick(t.accent, "chiseled_stone_bricks"));
                }
                if (i % 8 == 0 && i > 0) set(x, floorY + i + 4, sz0 + 1, t.dimLight + "[hanging=true]");
            }
            if (rnd.nextDouble() < 0.12) set(x, floorY + i + 4, sz0 + (rnd.nextBoolean() ? 0 : 2), "cobweb");
        }
        // la parte alta de la escalera es una zanja abierta: nada sobresale del suelo
        for (int x = stairTopX - 3; x <= stairTopX + 6; x++) {
            for (int z = sz0 - 1; z <= sz0 + 3; z++) {
                for (int y = surfaceY + 1; y <= surfaceY + 9; y++) air(x, y, z);
                boolean side = z == sz0 - 1 || z == sz0 + 3;
                if (side && x >= stairTopX - 1) set(x, surfaceY + 1, z, (x % 3 == 0) ? pick(t.accent, "chiseled_stone_bricks") : t.wallBlock);
                if (side && x % 3 == 0 && x >= stairTopX) set(x, surfaceY + 2, z, "soul_lantern");
            }
            // el techo de la zanja que quede bajo la superficie se quita para que se vea la escalera
            for (int z = sz0; z <= sz0 + 2; z++) {
                int i = stairEndX - x;
                if (i >= 0 && i <= depth && floorY + i + 5 >= surfaceY - 1) {
                    for (int y = floorY + i + 5; y <= surfaceY; y++) air(x, y, z);
                }
            }
        }

        // 7) Portón de la superficie
        gate(stairTopX - 2, surfaceY, sz0 + 1);

        // 8) Decorar cada sala según su tipo
        for (RoomPlan r : cells.values()) decorate(r);

        // 9) Pasillos con arcos, telarañas, trampas y huesos
        for (Box c : corridors) {
            boolean alongX = (c.x2() - c.x1()) > (c.z2() - c.z1());
            int len = alongX ? c.x2() - c.x1() : c.z2() - c.z1();
            for (int i = 0; i <= len; i += 4) {
                int x = alongX ? c.x1() + i : c.x1();
                int z = alongX ? c.z1() : c.z1() + i;
                if (alongX) {
                    for (int y = c.y1(); y <= c.y2(); y++) {
                        set(x, y, c.z1() - 1, pick(t.accent, "chiseled_stone_bricks"));
                        set(x, y, c.z2() + 1, pick(t.accent, "chiseled_stone_bricks"));
                    }
                    set(x, c.y2(), c.z1(), t.stairs + "[facing=north,half=top]");
                    set(x, c.y2(), c.z2(), t.stairs + "[facing=south,half=top]");
                } else {
                    for (int y = c.y1(); y <= c.y2(); y++) {
                        set(c.x1() - 1, y, z, pick(t.accent, "chiseled_stone_bricks"));
                        set(c.x2() + 1, y, z, pick(t.accent, "chiseled_stone_bricks"));
                    }
                    set(c.x1(), c.y2(), z, t.stairs + "[facing=west,half=top]");
                    set(c.x2(), c.y2(), z, t.stairs + "[facing=east,half=top]");
                }
            }
            if (len >= 4 && rnd.nextDouble() < 0.5) {
                int tx = c.centerX(), tz = c.centerZ();
                set(tx, floorY, tz, "dispenser[facing=up]", Special.TRAP_DISPENSER);
                set(tx, floorY + 1, tz, "stone_pressure_plate");
            }
            if (rnd.nextDouble() < 0.6) set(c.x1(), c.y2(), c.z1(), "cobweb");
            if (rnd.nextDouble() < 0.6) set(c.x2(), c.y2(), c.z2(), "cobweb");
            if (rnd.nextDouble() < 0.5) set(c.x1() + (alongX ? len / 3 : 0), c.y1(), c.z1() + (alongX ? 0 : len / 3), "skeleton_skull[rotation=" + rnd.nextInt(16) + "]");
            floorStain(c, 1);
        }

        res.ops.addAll(blocks.values());
        Box bnds = landing.grow(4);
        for (Box s : stairBoxes) bnds = bnds.union(s.grow(1));
        for (RoomPlan r : cells.values()) bnds = bnds.union(r.interior.grow(1));
        for (Box c : corridors) bnds = bnds.union(c.grow(1));
        res.bounds = bnds;
        res.entranceX = stairTopX - 3;
        res.entranceY = surfaceY + 1;
        res.entranceZ = sz0 + 1;
        return res;
    }

    /** Portón de piedra con torres, cráneos y fuego de almas. */
    private void gate(int gx, int gy, int gz) {
        String acc = pick(t.accent, "chiseled_stone_bricks");
        for (int dz : new int[]{-3, 3}) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int ddz = -1; ddz <= 1; ddz++) {
                    int zz = gz + dz + (dz > 0 ? ddz + 1 : ddz - 1);
                    for (int y = gy - 2; y <= gy + 9; y++) set(gx + dx, y, zz, y >= gy + 8 ? acc : pick(t.wall, "stone_bricks"));
                }
            }
            int zc = gz + dz + (dz > 0 ? 1 : -1);
            set(gx - 1, gy + 10, zc - 1, "iron_bars");
            set(gx + 1, gy + 10, zc + 1, "iron_bars");
            set(gx, gy + 10, zc, t.wallBlock);
            set(gx, gy + 11, zc, "soul_campfire[lit=true]");
            set(gx - 2, gy + 6, zc, "skeleton_skull[rotation=4]");
            set(gx - 2, gy + 3, zc, "chain");
            set(gx - 2, gy + 2, zc, t.dimLight + "[hanging=true]");
        }
        for (int dz = -2; dz <= 2; dz++) {
            set(gx, gy + 7, gz + dz, acc);
            set(gx, gy + 8, gz + dz, t.slab + "[type=bottom]");
            set(gx - 1, gy + 7, gz + dz, t.stairs + "[facing=east,half=top]");
        }
        set(gx - 1, gy + 6, gz - 1, "chain");
        set(gx - 1, gy + 5, gz - 1, t.dimLight + "[hanging=true]");
        set(gx - 1, gy + 6, gz + 1, "chain");
        set(gx - 1, gy + 5, gz + 1, t.dimLight + "[hanging=true]");
        // escombros y muros rotos alrededor
        for (int i = 0; i < 14; i++) {
            int x = gx - 6 + rnd.nextInt(8), z = gz - 7 + rnd.nextInt(15);
            if (Math.abs(z - gz) <= 2 && x >= gx - 3) continue;
            int h = 1 + rnd.nextInt(3);
            for (int y = gy + 1; y <= gy + h; y++) set(x, y, z, rnd.nextBoolean() ? pick(t.wall, "cobblestone") : t.wallBlock);
        }
        set(gx - 4, gy + 1, gz - 3, "skeleton_skull[rotation=12]");
        set(gx - 5, gy + 1, gz + 4, "bone_block[axis=x]");
    }

    private void decorate(RoomPlan r) {
        Box in = r.interior;
        int y = in.y1(), top = in.y2();
        int w = in.x2() - in.x1(), d = in.z2() - in.z1();
        // zócalo de acento
        for (int x = in.x1(); x <= in.x2(); x++) {
            set(x, y - 1, in.z1(), pick(t.accent, "chiseled_stone_bricks"));
            set(x, y - 1, in.z2(), pick(t.accent, "chiseled_stone_bricks"));
        }
        for (int z = in.z1(); z <= in.z2(); z++) {
            set(in.x1(), y - 1, z, pick(t.accent, "chiseled_stone_bricks"));
            set(in.x2(), y - 1, z, pick(t.accent, "chiseled_stone_bricks"));
        }
        int cx = in.centerX(), cz = in.centerZ();
        switch (r.type) {
            case START -> {
                hangingChain(cx, cz, top, 2);
                set(in.x1() + 1, y, in.z2() - 1, "skeleton_skull[rotation=6]");
                scatterBones(in, 3);
            }
            case HALL -> {
                // dos filas de pilares con cadenas
                for (int x = in.x1() + 2; x <= in.x2() - 2; x += 3) {
                    for (int z : new int[]{in.z1() + 2, in.z2() - 2}) {
                        column(x, z, y, top, pick(t.accent, "chiseled_stone_bricks"));
                        set(x, y, z, t.wallBlock);
                    }
                    if (rnd.nextBoolean()) hangingChain(x, cz, top, 2 + rnd.nextInt(2));
                }
                scatterBones(in, 7);
                floorStain(in, 4);
                if (rnd.nextDouble() < 0.6) set(cx, y, cz, "spawner", Special.SPAWNER);
                if (rnd.nextDouble() < 0.4) set(in.x2() - 1, y, in.z1() + 1, "chest[facing=west]", Special.CHEST);
            }
            case CRYPT -> {
                // ataúdes de piedra a lo largo de las paredes, velas encima
                for (int x = in.x1() + 1; x <= in.x2() - 2; x += 3) {
                    for (int z : new int[]{in.z1() + 1, in.z2() - 1}) {
                        set(x, y, z, t.slab + "[type=bottom]");
                        set(x + 1, y, z, t.slab + "[type=bottom]");
                        if (rnd.nextBoolean()) set(x, y + 1, z, "candle[candles=" + (1 + rnd.nextInt(4)) + ",lit=true]");
                    }
                }
                // sarcófago central
                set(cx, y, cz, pick(t.accent, "chiseled_stone_bricks"));
                set(cx + 1, y, cz, pick(t.accent, "chiseled_stone_bricks"));
                set(cx, y + 1, cz, "skeleton_skull[rotation=" + rnd.nextInt(16) + "]");
                set(cx + 1, y + 1, cz, "candle[candles=3,lit=true]");
                hangingChain(cx - 2, cz, top, 3);
                scatterBones(in, 5);
                floorStain(in, 2);
            }
            case PRISON -> {
                // celdas con barrotes en un lado
                int zc = in.z1() + 3;
                for (int x = in.x1(); x <= in.x2(); x++) set(x, y, zc, x % 3 == 0 ? pick(t.accent, "stone_bricks") : "iron_bars");
                for (int x = in.x1(); x <= in.x2(); x++) set(x, y + 1, zc, x % 3 == 0 ? pick(t.accent, "stone_bricks") : "iron_bars");
                for (int x = in.x1(); x <= in.x2(); x++) set(x, y + 2, zc, pick(t.wall, "stone_bricks"));
                for (int x = in.x1(); x <= in.x2(); x += 3) {
                    for (int z = in.z1(); z < zc; z++) {
                        set(x, y, z, pick(t.wall, "stone_bricks"));
                        set(x, y + 1, z, pick(t.wall, "stone_bricks"));
                    }
                    set(x + 1, y, in.z1(), "skeleton_skull[rotation=0]");
                    set(x + 2, y, in.z1() + 1, rnd.nextBoolean() ? "bone_block[axis=y]" : "cobweb");
                    set(x + 1, y + 2, in.z1() + 1, "chain");
                }
                scatterBones(in, 4);
                floorStain(in, 3);
                set(in.x2() - 1, y, in.z2() - 1, "chest[facing=north]", Special.CHEST);
            }
            case CHASM -> {
                // abismo con puente en cruz y peligro al fondo
                for (int x = in.x1(); x <= in.x2(); x++) {
                    for (int z = in.z1(); z <= in.z2(); z++) {
                        boolean bridge = Math.abs(x - cx) <= 1 || Math.abs(z - cz) <= 1;
                        boolean ledge = x == in.x1() || x == in.x2() || z == in.z1() || z == in.z2();
                        if (bridge || ledge) continue;
                        for (int yy = y - 9; yy < y; yy++) {
                            boolean wallPit = false;
                            if (yy == y - 9) set(x, yy, z, t.hazard);
                            else air(x, yy, z);
                            if (wallPit) set(x, yy, z, pick(t.wall, "stone_bricks"));
                        }
                    }
                }
                // paredes del abismo
                for (int x = in.x1(); x <= in.x2(); x++) {
                    for (int z = in.z1(); z <= in.z2(); z++) {
                        if (x == in.x1() || x == in.x2() || z == in.z1() || z == in.z2()) {
                            for (int yy = y - 10; yy < y; yy++) solid(x, yy, z, pick(t.wall, "stone_bricks"));
                        }
                        solid(x, y - 10, z, pick(t.wall, "stone_bricks"));
                    }
                }
                // barandas del puente
                for (int x = in.x1() + 1; x < in.x2(); x++) {
                    if (Math.abs(x - cx) <= 1) continue;
                    set(x, y, cz - 2, rnd.nextDouble() < 0.8 ? t.wallBlock : "air");
                    set(x, y, cz + 2, rnd.nextDouble() < 0.8 ? t.wallBlock : "air");
                }
                for (int z = in.z1() + 1; z < in.z2(); z++) {
                    if (Math.abs(z - cz) <= 1) continue;
                    set(cx - 2, y, z, rnd.nextDouble() < 0.8 ? t.wallBlock : "air");
                    set(cx + 2, y, z, rnd.nextDouble() < 0.8 ? t.wallBlock : "air");
                }
                hangingChain(cx, cz, top, 3);
                hangingChain(in.x1() + 2, in.z1() + 2, top, 5);
                hangingChain(in.x2() - 2, in.z2() - 2, top, 4);
            }
            case RITUAL -> {
                // círculo ritual con altar, velas y fuego de almas
                for (int x = in.x1() + 1; x < in.x2(); x++) {
                    for (int z = in.z1() + 1; z < in.z2(); z++) {
                        double dd = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                        if (dd > 3.3 && dd < 4.4) set(x, y - 1, z, "crying_obsidian");
                        else if (dd <= 1.5) set(x, y - 1, z, pick(t.accent, "chiseled_stone_bricks"));
                    }
                }
                set(cx, y, cz, pick(t.accent, "chiseled_stone_bricks"));
                set(cx, y + 1, cz, "candle[candles=4,lit=true]");
                for (int[] o : new int[][]{{4, 0}, {-4, 0}, {0, 4}, {0, -4}}) {
                    set(cx + o[0], y, cz + o[1], "soul_campfire[lit=true]");
                }
                for (int[] o : new int[][]{{3, 3}, {-3, 3}, {3, -3}, {-3, -3}}) {
                    set(cx + o[0], y, cz + o[1], "candle[candles=" + (1 + rnd.nextInt(4)) + ",lit=true]");
                }
                scatterBones(in, 3);
                floorStain(in, 5);
            }
            case TREASURE -> {
                for (int i = 0; i < 6; i++) {
                    int x = in.x1() + 1 + rnd.nextInt(Math.max(1, w - 1)), z = in.z1() + 1 + rnd.nextInt(Math.max(1, d - 1));
                    set(x, y, z, rnd.nextDouble() < 0.6 ? t.treasure : "barrel[facing=up]");
                }
                set(in.x1() + 1, y, in.z1() + 1, "chest[facing=south]", Special.CHEST);
                set(in.x2() - 1, y, in.z2() - 1, "chest[facing=north]", Special.CHEST);
                set(cx, y, cz, "chest[facing=south]", Special.CHEST);
                hangingChain(cx, cz + 2, top, 2);
                scatterBones(in, 2);
            }
            case BOSS -> {
                // anillos en el suelo
                for (int x = in.x1(); x <= in.x2(); x++) {
                    for (int z = in.z1(); z <= in.z2(); z++) {
                        double dd = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                        if (Math.abs(dd - 3) < 0.5 || Math.abs(dd - 6) < 0.5) set(x, y - 1, z, pick(t.accent, "chiseled_stone_bricks"));
                        if (dd < 1.2) set(x, y - 1, z, "crying_obsidian");
                    }
                }
                // anillo de pilares con fuego de almas
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * 2 * i / 8;
                    int px = cx + (int) Math.round(Math.cos(a) * 6), pz = cz + (int) Math.round(Math.sin(a) * 6);
                    column(px, pz, y, y + 4, pick(t.accent, "chiseled_stone_bricks"));
                    set(px, y + 5, pz, "soul_campfire[lit=true]");
                    hangingChain(px, pz, top, Math.max(1, top - (y + 7)));
                }
                // trono en el lado norte
                int tz = in.z1() + 1;
                for (int dx = -2; dx <= 2; dx++) {
                    set(cx + dx, y, tz, pick(t.accent, "chiseled_stone_bricks"));
                    set(cx + dx, y, tz + 1, t.stairs + "[facing=north,half=bottom]");
                }
                set(cx, y + 1, tz, t.stairs + "[facing=north,half=bottom]");
                column(cx - 1, tz, y + 1, y + 4, pick(t.accent, "chiseled_stone_bricks"));
                column(cx + 1, tz, y + 1, y + 4, pick(t.accent, "chiseled_stone_bricks"));
                column(cx, tz, y + 2, y + 5, t.treasure);
                set(cx - 1, y + 5, tz, "skeleton_skull[rotation=0]");
                set(cx + 1, y + 5, tz, "skeleton_skull[rotation=0]");
                // foso de peligro junto a las paredes este y oeste
                for (int z = in.z1() + 3; z <= in.z2() - 3; z++) {
                    set(in.x1(), y - 1, z, t.hazard.equals("water") ? "magma_block" : t.hazard);
                    set(in.x2(), y - 1, z, t.hazard.equals("water") ? "magma_block" : t.hazard);
                }
                // luz del jefe: pocas fuentes, mucha sombra
                set(cx, top + 1, cz, t.light);
                scatterBones(in, 8);
                floorStain(in, 8);
            }
        }
        // telarañas en las esquinas altas
        for (int[] c : new int[][]{{in.x1(), in.z1()}, {in.x2(), in.z1()}, {in.x1(), in.z2()}, {in.x2(), in.z2()}}) {
            if (rnd.nextDouble() < 0.7) set(c[0], top, c[1], "cobweb");
        }
    }

    private static long cellKey(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }
}
