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

    public enum RoomType { START, HALL, CRYPT, PRISON, CHASM, RITUAL, TREASURE, BOSS, LIBRARY, FLOODED, LABYRINTH, ARMORY, CAVE, JUNCTION, DESCENT, LANDING }

    /** Distancia vertical entre pisos (el abismo de un piso queda justo encima del techo del de abajo, sin tocarlo). */
    public static final int LEVEL_DY = 16;
    /** Cuadrícula de cada piso: columnas 0..GX (hacia el este) y filas -GZ..GZ. */
    public static final int GX = 7, GZ = 3;

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
        public int level, fy;
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
        public int entranceX, entranceY, entranceZ, levels = 1;
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
        for (int y = top; y > top - len; y--) set(x, y, z, "iron_chain");
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
        return build(minRooms, maxRooms, 1);
    }

    /**
     * Mazmorra de varios pisos: cada piso es un laberinto de salas en una cuadrícula de 8×7 celdas, con
     * pasillos que a veces dan la vuelta (para perderse) y callejones sin salida. Una escalera de caracol
     * baja de la sala más lejana de un piso a la entrada del siguiente; el jefe espera en el último piso.
     */
    public Result build(int minRooms, int maxRooms, int levels) {
        Result res = new Result();
        levels = Math.max(1, Math.min(6, levels));
        List<RoomPlan> all = new ArrayList<>();
        List<Box> corridors = new ArrayList<>();
        Set<Box> alongXSet = new HashSet<>();   // dirección real de cada pasillo (uno de 3×3 parece cuadrado)
        List<int[]> wells = new ArrayList<>();   // cx, cz, fy de arriba, fy de abajo
        RoomPlan start = null;
        int[] from = {0, 0};
        for (int lv = 0; lv < levels; lv++) {
            int fy = floorY - lv * LEVEL_DY;
            boolean last = lv == levels - 1;
            int target = minRooms + rnd.nextInt(Math.max(1, maxRooms - minRooms + 1));
            Map<Long, RoomPlan> cells = new LinkedHashMap<>();
            List<long[]> edges = new ArrayList<>();
            RoomPlan first = new RoomPlan(from[0], from[1]);
            first.start = true;
            first.type = lv == 0 ? RoomType.START : RoomType.LANDING;
            cells.put(cellKey(first.cx, first.cz), first);
            if (lv == 0) start = first;
            List<RoomPlan> order = new ArrayList<>();
            order.add(first);
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 0}};
            int guard = 0;
            RoomPlan cur = first;
            while (cells.size() < target && guard++ < 4000) {
                int[] d = dirs[rnd.nextInt(dirs.length)];
                int nx = cur.cx + d[0], nz = cur.cz + d[1];
                if (nx < 0 || nx > GX || Math.abs(nz) > GZ) {
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
                cur = rnd.nextDouble() < 0.75 ? next : order.get(rnd.nextInt(order.size()));
            }
            // vueltas: algunos vecinos se conectan aunque ya haya camino (el laberinto no es un árbol)
            Set<Long> linked = new HashSet<>();
            for (long[] e : edges) {
                linked.add(e[0] * 31 + e[1]);
                linked.add(e[1] * 31 + e[0]);
            }
            for (RoomPlan r : cells.values()) {
                for (int[] d : new int[][]{{1, 0}, {0, 1}}) {
                    long a = cellKey(r.cx, r.cz), bk = cellKey(r.cx + d[0], r.cz + d[1]);
                    if (!cells.containsKey(bk) || linked.contains(a * 31 + bk)) continue;
                    if (rnd.nextDouble() < 0.22) {
                        edges.add(new long[]{a, bk});
                        linked.add(a * 31 + bk);
                        linked.add(bk * 31 + a);
                    }
                }
            }
            // distancias: la sala más lejana es la del jefe (último piso) o la de la escalera hacia abajo
            Map<Long, List<Long>> adj = new HashMap<>();
            for (long[] e : edges) {
                adj.computeIfAbsent(e[0], x -> new ArrayList<>()).add(e[1]);
                adj.computeIfAbsent(e[1], x -> new ArrayList<>()).add(e[0]);
            }
            Map<Long, Integer> dist = new HashMap<>();
            List<Long> queue = new ArrayList<>();
            long fk = cellKey(first.cx, first.cz);
            queue.add(fk);
            dist.put(fk, 0);
            for (int i = 0; i < queue.size(); i++) {
                long c = queue.get(i);
                for (long n : adj.getOrDefault(c, List.of())) {
                    if (!dist.containsKey(n)) {
                        dist.put(n, dist.get(c) + 1);
                        queue.add(n);
                    }
                }
            }
            long farKey = fk;
            for (Map.Entry<Long, Integer> e : dist.entrySet()) if (e.getValue() > dist.get(farKey)) farKey = e.getKey();
            RoomPlan far = cells.get(farKey);
            if (last) {
                far.boss = true;
                far.type = RoomType.BOSS;
            } else {
                far.type = RoomType.DESCENT;
                wells.add(new int[]{far.cx, far.cz, fy, fy - LEVEL_DY});
                from = new int[]{far.cx, far.cz};
            }
            RoomType[] pool = {RoomType.HALL, RoomType.CRYPT, RoomType.JUNCTION, RoomType.PRISON, RoomType.LIBRARY, RoomType.CHASM,
                    RoomType.LABYRINTH, RoomType.JUNCTION, RoomType.FLOODED, RoomType.RITUAL, RoomType.ARMORY, RoomType.CAVE, RoomType.JUNCTION};
            int pi = rnd.nextInt(pool.length), treasures = 0;
            for (RoomPlan r : cells.values()) {
                if (r == far || r.start) continue;
                int links = adj.getOrDefault(cellKey(r.cx, r.cz), List.of()).size();
                if (links == 1 && treasures < 3 && rnd.nextDouble() < 0.45) {
                    r.treasure = true;
                    r.type = RoomType.TREASURE;
                    treasures++;
                } else {
                    r.type = pool[pi++ % pool.length];
                }
            }
            // geometría de las salas
            for (RoomPlan r : cells.values()) {
                r.level = lv;
                r.fy = fy;
                int ox = baseX + r.cx * CELL, oz = baseZ + r.cz * CELL;
                int half, h;
                switch (r.type) {
                    case BOSS -> { half = 8; h = 11; }
                    case CHASM -> { half = 7; h = 8; }
                    case HALL, RITUAL, LIBRARY, CAVE -> { half = 6 + rnd.nextInt(2); h = 7 + rnd.nextInt(2); }
                    case LABYRINTH -> { half = 7; h = 5; }
                    case JUNCTION -> { half = 2; h = 4; }
                    case DESCENT, LANDING -> { half = 5; h = 7; }
                    default -> { half = 5; h = 6; }
                }
                int c = CELL / 2;
                r.interior = Box.of(ox + c - half, fy + 1, oz + c - half, ox + c + half, fy + h, oz + c + half);
                res.rooms.add(r);
                all.add(r);
            }
            // pasillos (3 de ancho, 4 de alto) entre celdas vecinas de este piso
            for (long[] e : edges) {
                RoomPlan a = cells.get(e[0]), b = cells.get(e[1]);
                Box c;
                if (a.cx != b.cx) {
                    RoomPlan w = a.cx < b.cx ? a : b, east = a.cx < b.cx ? b : a;
                    int z0 = baseZ + w.cz * CELL + CELL / 2 - 1;
                    c = Box.of(w.interior.x2() + 1, fy + 1, z0, east.interior.x1() - 1, fy + 4, z0 + 2);
                    alongXSet.add(c);
                    if (east.boss) res.bossDoors.add(Box.of(east.interior.x1() - 1, fy + 1, z0, east.interior.x1() - 1, fy + 4, z0 + 2));
                    if (w.boss) res.bossDoors.add(Box.of(w.interior.x2() + 1, fy + 1, z0, w.interior.x2() + 1, fy + 4, z0 + 2));
                } else {
                    RoomPlan n = a.cz < b.cz ? a : b, so = a.cz < b.cz ? b : a;
                    int x0 = baseX + n.cx * CELL + CELL / 2 - 1;
                    c = Box.of(x0, fy + 1, n.interior.z2() + 1, x0 + 2, fy + 4, so.interior.z1() - 1);
                    if (so.boss) res.bossDoors.add(Box.of(x0, fy + 1, so.interior.z1() - 1, x0 + 2, fy + 4, so.interior.z1() - 1));
                    if (n.boss) res.bossDoors.add(Box.of(x0, fy + 1, n.interior.z2() + 1, x0 + 2, fy + 4, n.interior.z2() + 1));
                }
                corridors.add(c);
            }
        }

        // Gran escalera desde la superficie hasta la sala de inicio (sube hacia el oeste)
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

        // Cascarones y huecos
        for (RoomPlan r : all) shell(r.interior);
        for (Box c : corridors) shell(c);
        for (Box st : stairBoxes) shell(st);
        for (RoomPlan r : all) carve(r.interior);
        for (Box c : corridors) carve(c);
        for (Box st : stairBoxes) carve(st);
        for (int[] wl : wells) stairwell(baseX + wl[0] * CELL + CELL / 2, baseZ + wl[1] * CELL + CELL / 2, wl[2], wl[3]);
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
            for (int z = sz0; z <= sz0 + 2; z++) {
                int i = stairEndX - x;
                if (i >= 0 && i <= depth && floorY + i + 5 >= surfaceY - 1) {
                    for (int y = floorY + i + 5; y <= surfaceY; y++) air(x, y, z);
                }
            }
        }

        // Portón de la superficie
        gate(stairTopX - 2, surfaceY, sz0 + 1);

        // Decorar cada sala según su tipo
        for (RoomPlan r : all) decorate(r);
        // al menos 2 generadores de monstruos por piso
        for (int lv = 0; lv < levels; lv++) {
            long spawners = 0;
            for (RoomPlan r : all) {
                if (r.level != lv) continue;
                Box in = r.interior;
                for (int x = in.x1(); x <= in.x2() && spawners < 99; x++)
                    for (int z = in.z1(); z <= in.z2(); z++) {
                        Op o = blocks.get(key(x, in.y1(), z));
                        if (o != null && o.special() == Special.SPAWNER) spawners++;
                    }
            }
            for (RoomPlan r : all) {
                if (spawners >= 2) break;
                if (r.level != lv) continue;
                if (r.type == RoomType.START || r.type == RoomType.LANDING || r.type == RoomType.BOSS || r.type == RoomType.CHASM
                        || r.type == RoomType.TREASURE || r.type == RoomType.DESCENT || r.type == RoomType.JUNCTION || r.type == RoomType.LABYRINTH) continue;
                Box in = r.interior;
                int sx = in.x1() + 2, sz = in.z2() - 2;
                Op o = blocks.get(key(sx, in.y1(), sz));
                if (o != null && !o.block().equals("air")) continue;
                set(sx, in.y1(), sz, "spawner", Special.SPAWNER);
                spawners++;
            }
        }

        // Pasillos con arcos, telarañas, trampas y huesos
        for (Box c : corridors) {
            boolean alongX = alongXSet.contains(c);
            int len = alongX ? c.x2() - c.x1() : c.z2() - c.z1();
            int cfy = c.y1() - 1;
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
                set(tx, cfy, tz, "dispenser[facing=up]", Special.TRAP_DISPENSER);
                set(tx, cfy + 1, tz, "stone_pressure_plate");
            }
            if (rnd.nextDouble() < 0.6) set(c.x1(), c.y2(), c.z1(), "cobweb");
            if (rnd.nextDouble() < 0.6) set(c.x2(), c.y2(), c.z2(), "cobweb");
            if (rnd.nextDouble() < 0.5) set(c.x1() + (alongX ? len / 3 : 0), c.y1(), c.z1() + (alongX ? 0 : len / 3), "skeleton_skull[rotation=" + rnd.nextInt(16) + "]");
            // pasillos a oscuras: casi sin luz (para perderse de verdad)
            if (len >= 6 && rnd.nextDouble() < 0.35) set(c.centerX(), c.y2(), c.centerZ(), t.dimLight + "[hanging=true]");
            floorStain(c, 1);
        }

        res.ops.addAll(blocks.values());
        Box bnds = landing.grow(4);
        for (Box st : stairBoxes) bnds = bnds.union(st.grow(1));
        for (RoomPlan r : all) bnds = bnds.union(r.interior.grow(r.type == RoomType.CHASM ? 7 : 1));
        for (Box c : corridors) bnds = bnds.union(c.grow(1));
        res.bounds = bnds;
        res.entranceX = stairTopX - 3;
        res.entranceY = surfaceY + 1;
        res.entranceZ = sz0 + 1;
        res.levels = levels;
        return res;
    }

    /** Escalera de caracol alrededor de un pilar, de la sala de bajada de un piso a la de llegada del siguiente. */
    private void stairwell(int cx, int cz, int upperFy, int lowerFy) {
        // paredes del pozo (en la roca entre los dos pisos; no tapa las salas, que ya están huecas)
        for (int y = lowerFy + 1; y <= upperFy - 1; y++)
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++)
                    if (Math.abs(dx) == 2 || Math.abs(dz) == 2) solid(cx + dx, y, cz + dz, pick(t.wall, "stone_bricks"));
        for (int y = lowerFy + 1; y <= upperFy + 3; y++)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) air(cx + dx, y, cz + dz);
        for (int y = lowerFy + 1; y <= upperFy; y++) set(cx, y, cz, (y - lowerFy) % 6 == 0 ? t.light : pick(t.accent, "chiseled_stone_bricks"));
        int[][] ring = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
        int steps = upperFy - (lowerFy + 1);
        for (int i = 0; i <= steps; i++) {
            int[] pos = ring[i % 8];
            int[] prev = i == 0 ? new int[]{-2, -1} : ring[(i - 1) % 8];
            int fx = prev[0] - pos[0], fz = prev[1] - pos[1];
            String facing = fx > 0 ? "east" : fx < 0 ? "west" : fz > 0 ? "south" : "north";
            set(cx + pos[0], upperFy - i, cz + pos[1], t.stairs + "[facing=" + facing + ",half=bottom]");
        }
        // baranda alrededor del hueco arriba (con entrada por el oeste)
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) != 2 && Math.abs(dz) != 2) continue;
                if (dx == -2 && dz == -1) continue;
                set(cx + dx, upperFy + 1, cz + dz, t.wallBlock);
            }
        set(cx - 2, upperFy + 2, cz - 2, t.dimLight);
        set(cx + 2, upperFy + 2, cz + 2, t.dimLight);
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
            set(gx - 2, gy + 3, zc, "iron_chain");
            set(gx - 2, gy + 2, zc, t.dimLight + "[hanging=true]");
        }
        for (int dz = -2; dz <= 2; dz++) {
            set(gx, gy + 7, gz + dz, acc);
            set(gx, gy + 8, gz + dz, t.slab + "[type=bottom]");
            set(gx - 1, gy + 7, gz + dz, t.stairs + "[facing=east,half=top]");
        }
        set(gx - 1, gy + 6, gz - 1, "iron_chain");
        set(gx - 1, gy + 5, gz - 1, t.dimLight + "[hanging=true]");
        set(gx - 1, gy + 6, gz + 1, "iron_chain");
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
            case JUNCTION -> {
                if (rnd.nextBoolean()) set(cx, top, cz, "cobweb");
                if (rnd.nextDouble() < 0.4) set(in.x1(), y, in.z1(), "skeleton_skull[rotation=" + rnd.nextInt(16) + "]");
                if (rnd.nextDouble() < 0.3) set(cx, top, cz, t.dimLight + "[hanging=true]");
            }
            case DESCENT, LANDING -> {
                // la escalera de caracol ocupa el centro: solo decorar el borde
                for (int[] c : new int[][]{{in.x1() + 1, in.z1() + 1}, {in.x2() - 1, in.z1() + 1}, {in.x1() + 1, in.z2() - 1}, {in.x2() - 1, in.z2() - 1}}) {
                    column(c[0], c[1], y, y + 2, pick(t.accent, "chiseled_stone_bricks"));
                    set(c[0], y + 3, c[1], "candle[candles=3,lit=true]");
                }
                for (int x = in.x1() + 1; x <= in.x2() - 1; x++) {
                    if (Math.abs(x - cx) <= 3) continue;
                    set(x, y - 1, in.z1() + 1, t.blood);
                }
                hangingChain(in.x1() + 2, cz, top, 2);
                hangingChain(in.x2() - 2, cz, top, 2);
                if (r.type == RoomType.DESCENT) {
                    set(cx - 3, y, cz - 3, "skeleton_skull[rotation=10]");
                    set(cx + 3, y, cz + 3, "skeleton_skull[rotation=2]");
                }
            }
            case LIBRARY -> {
                // estanterías en las paredes (con huecos), atriles y velas
                for (int x = in.x1(); x <= in.x2(); x++) {
                    for (int z : new int[]{in.z1(), in.z2()}) {
                        if (Math.abs(x - cx) <= 1) continue;
                        column(x, z, y, Math.min(top, y + 2), rnd.nextDouble() < 0.15 ? "chiseled_bookshelf[facing=" + (z == in.z1() ? "south" : "north") + "]" : "bookshelf");
                    }
                }
                for (int z = in.z1() + 2; z <= in.z2() - 2; z += 3) {
                    for (int x = in.x1() + 2; x <= in.x2() - 2; x++) {
                        if (Math.abs(x - cx) <= 1) continue;
                        set(x, y, z, "bookshelf");
                        if (rnd.nextDouble() < 0.5) set(x, y + 1, z, "bookshelf");
                    }
                }
                set(cx, y, cz, "lectern[facing=south]");
                for (int i = 0; i < 6; i++) set(in.x1() + 1 + rnd.nextInt(Math.max(1, w - 1)), y, in.z1() + 1 + rnd.nextInt(Math.max(1, d - 1)), "candle[candles=" + (1 + rnd.nextInt(4)) + ",lit=true]");
                hangingChain(cx, cz + 2, top, 2);
                if (rnd.nextDouble() < 0.5) set(in.x2() - 1, y, in.z2() - 1, "chest[facing=north]", Special.CHEST);
            }
            case FLOODED -> {
                // charcos hundidos en el suelo (te frenan) con islas de piedra
                for (int x = in.x1() + 1; x <= in.x2() - 1; x++)
                    for (int z = in.z1() + 1; z <= in.z2() - 1; z++) {
                        if (rnd.nextDouble() < 0.62) {
                            set(x, y - 2, z, pick(t.wall, "stone_bricks"));
                            set(x, y - 1, z, "water");
                            if (rnd.nextDouble() < 0.08) set(x, y, z, "lily_pad");
                        }
                    }
                for (int i = 0; i < 5; i++) hangingChain(in.x1() + 2 + rnd.nextInt(Math.max(1, w - 3)), in.z1() + 2 + rnd.nextInt(Math.max(1, d - 3)), top, 1 + rnd.nextInt(3));
                set(cx, y, cz, pick(t.accent, "chiseled_stone_bricks"));
                set(cx, y + 1, cz, "skeleton_skull[rotation=" + rnd.nextInt(16) + "]");
            }
            case LABYRINTH -> {
                // un laberinto dentro de la sala: muros hasta el techo y celdas de 1 bloque
                int n = (w / 2) + 1, m = (d / 2) + 1;   // celdas en coordenadas pares
                boolean[][] open = new boolean[w + 1][d + 1];
                boolean[][] seen = new boolean[n][m];
                java.util.ArrayDeque<int[]> stack = new java.util.ArrayDeque<>();
                stack.push(new int[]{n / 2, m / 2});
                seen[n / 2][m / 2] = true;
                open[(n / 2) * 2][(m / 2) * 2] = true;
                int[] deadEnd = null;   // el cofre va en un callejón sin salida (si va en un cruce, corta el laberinto)
                int[] lastPushed = null;
                while (!stack.isEmpty()) {
                    int[] c = stack.peek();
                    List<int[]> nb = new ArrayList<>();
                    for (int[] dd : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int nx = c[0] + dd[0], nz = c[1] + dd[1];
                        if (nx >= 0 && nz >= 0 && nx < n && nz < m && !seen[nx][nz]) nb.add(new int[]{nx, nz});
                    }
                    if (nb.isEmpty()) {
                        boolean edge = c[0] == 0 || c[1] == 0 || c[0] == n - 1 || c[1] == m - 1;
                        if (deadEnd == null && !edge && c == lastPushed) deadEnd = c;   // recién llegado y sin salida: es una hoja
                        stack.pop();
                        continue;
                    }
                    int[] nx = nb.get(rnd.nextInt(nb.size()));
                    seen[nx[0]][nx[1]] = true;
                    open[nx[0] * 2][nx[1] * 2] = true;
                    open[c[0] + nx[0]][c[1] + nx[1]] = true;
                    stack.push(nx);
                    lastPushed = nx;
                }
                // algunos muros extra abiertos para que haya más de un camino
                for (int i = 0; i < (w * d) / 18; i++) {
                    int lx = rnd.nextInt(w + 1), lz = rnd.nextInt(d + 1);
                    if (deadEnd != null && Math.abs(lx - deadEnd[0] * 2) + Math.abs(lz - deadEnd[1] * 2) <= 1) continue;
                    if ((lx + lz) % 2 == 1) open[lx][lz] = true;
                }
                for (int lx = 0; lx <= w; lx++)
                    for (int lz = 0; lz <= d; lz++) {
                        // las entradas de los pasillos (centro de cada lado) siempre abiertas
                        boolean door = (Math.abs(lx - w / 2) <= 1 && (lz == 0 || lz == d)) || (Math.abs(lz - d / 2) <= 1 && (lx == 0 || lx == w));
                        if (open[lx][lz] || door) continue;
                        column(in.x1() + lx, in.z1() + lz, y, top, rnd.nextDouble() < 0.1 ? pick(t.accent, "chiseled_stone_bricks") : pick(t.wall, "stone_bricks"));
                    }
                for (int i = 0; i < 4; i++) {
                    int lx = rnd.nextInt(n) * 2, lz = rnd.nextInt(m) * 2;
                    set(in.x1() + lx, top, in.z1() + lz, t.dimLight + "[hanging=true]");
                }
                if (deadEnd != null) set(in.x1() + deadEnd[0] * 2, y, in.z1() + deadEnd[1] * 2, "chest[facing=south]", Special.CHEST);
            }
            case ARMORY -> {
                for (int x = in.x1() + 1; x <= in.x2() - 1; x += 2) {
                    set(x, y, in.z1(), "iron_bars");
                    set(x, y + 1, in.z1(), "iron_bars");
                    set(x, y, in.z2(), rnd.nextBoolean() ? "anvil[facing=east]" : "grindstone[face=floor,facing=north]");
                }
                set(cx - 2, y, cz, "smithing_table");
                set(cx + 2, y, cz, "blast_furnace[facing=south]");
                set(cx, y, cz - 2, "barrel[facing=up]");
                set(cx, y, cz + 2, "barrel[facing=up]");
                set(cx, y, cz, "chest[facing=south]", Special.CHEST);
                for (int i = 0; i < 4; i++) set(in.x1() + 1 + rnd.nextInt(Math.max(1, w - 1)), y, in.z1() + 1 + rnd.nextInt(Math.max(1, d - 1)), "cobweb");
                hangingChain(cx, cz, top, 2);
            }
            case CAVE -> {
                // cueva: estalactitas, estalagmitas, rocas y musgo
                for (int x = in.x1(); x <= in.x2(); x++)
                    for (int z = in.z1(); z <= in.z2(); z++) {
                        double rr = rnd.nextDouble();
                        if (rr < 0.22) set(x, y - 1, z, rnd.nextBoolean() ? "moss_block" : "cobblestone");
                        if (rr > 0.94 && Math.abs(x - cx) > 1 && Math.abs(z - cz) > 1) {
                            int hh = 1 + rnd.nextInt(Math.max(1, top - y - 1));
                            column(x, z, y, y + hh - 1, rnd.nextBoolean() ? "cobblestone" : "tuff");
                        }
                        if (rnd.nextDouble() < 0.06) {
                            set(x, top, z, "pointed_dripstone[vertical_direction=down,thickness=tip]");
                        } else if (rnd.nextDouble() < 0.03 && Math.abs(x - cx) > 1) {
                            set(x, y, z, "pointed_dripstone[vertical_direction=up,thickness=tip]");
                        }
                        if (rnd.nextDouble() < 0.05) set(x, y, z, "moss_carpet");
                    }
                set(cx, top, cz, "glow_lichen[down=false,up=true]");
                scatterBones(in, 4);
            }
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
                // pasillo libre por el centro (la puerta del norte entra por ahí)
                for (int x = in.x1(); x <= in.x2(); x++) {
                    if (Math.abs(x - cx) <= 1) continue;
                    set(x, y, zc, x % 3 == 0 ? pick(t.accent, "stone_bricks") : "iron_bars");
                    set(x, y + 1, zc, x % 3 == 0 ? pick(t.accent, "stone_bricks") : "iron_bars");
                    set(x, y + 2, zc, pick(t.wall, "stone_bricks"));
                }
                for (int x = in.x1(); x <= in.x2(); x += 3) {
                    if (Math.abs(x - cx) <= 1) continue;
                    for (int z = in.z1(); z < zc; z++) {
                        set(x, y, z, pick(t.wall, "stone_bricks"));
                        set(x, y + 1, z, pick(t.wall, "stone_bricks"));
                    }
                    set(x + 1, y, in.z1(), "skeleton_skull[rotation=0]");
                    set(x + 2, y, in.z1() + 1, rnd.nextBoolean() ? "bone_block[axis=y]" : "cobweb");
                    set(x + 1, y + 2, in.z1() + 1, "iron_chain");
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
                        for (int yy = y - 5; yy < y; yy++) {
                            boolean wallPit = false;
                            if (yy == y - 5) set(x, yy, z, t.hazard);
                            else air(x, yy, z);
                            if (wallPit) set(x, yy, z, pick(t.wall, "stone_bricks"));
                        }
                    }
                }
                // paredes del abismo
                for (int x = in.x1(); x <= in.x2(); x++) {
                    for (int z = in.z1(); z <= in.z2(); z++) {
                        if (x == in.x1() || x == in.x2() || z == in.z1() || z == in.z2()) {
                            for (int yy = y - 6; yy < y; yy++) solid(x, yy, z, pick(t.wall, "stone_bricks"));
                        }
                        solid(x, y - 6, z, pick(t.wall, "stone_bricks"));
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
                    if (Math.abs(z - cz) <= 2) continue;   // la entrada de los pasillos del este y oeste queda libre
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
