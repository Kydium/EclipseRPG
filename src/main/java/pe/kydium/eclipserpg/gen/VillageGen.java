package pe.kydium.eclipserpg.gen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Aldea al azar: plaza con fuente, caminos que siguen el terreno, edificios con sus NPCs, granjas, corrales,
 * mercado, muelle o estanque, y defensas según el tamaño (sin muro, empalizada de troncos o murallas de piedra
 * con torres y portones). Cada edificio se asienta entero a su altura, nunca se deforma.
 */
public final class VillageGen {

    public enum Size { CHICA, MEDIANA, GRANDE, ENORME }

    /** Trazado de las calles: cada uno da un pueblo con otra forma. */
    public enum Layout { RADIAL, CUADRICULA, LINEAL, ANILLO, RAMIFICADO }

    private Layout layout;

    public VillageGen layout(Layout l) {
        this.layout = l;
        return this;
    }

    public Layout layout() {
        return layout;
    }

    private final Terrain t;
    private final Random r;
    private final Style s;
    private final Size size;
    private final int cx, cz;
    private final Build out = new Build();
    private final Set<Long> used = new HashSet<>();     // ocupado por caminos o edificios
    private final Set<Long> road = new HashSet<>();
    private final List<List<int[]>> roads = new ArrayList<>();
    private final List<int[]> roadEnds = new ArrayList<>();  // {x, z, dirx*100, dirz*100}
    private int R, plazaR, wallR;
    private final Set<String> placedNpcs = new HashSet<>();

    public VillageGen(Terrain t, Random r, Style s, Size size, int cx, int cz) {
        this.t = t;
        this.r = r;
        this.s = s;
        this.size = size;
        this.cx = cx;
        this.cz = cz;
    }

    /** Radio que ocupa la aldea (para cargar el terreno antes). */
    public static int radiusFor(Size size) {
        return switch (size) {
            case CHICA -> 46;
            case MEDIANA -> 58;
            case GRANDE -> 74;
            case ENORME -> 104;
        };
    }

    private static long col(int x, int z) {
        return Build.col(x, z);
    }

    private int g(int x, int z) {
        return out.ground(t, x, z);
    }

    // ------------------------------------------------------------------ generar

    public Build generate() {
        R = radiusFor(size) - 6;
        wallR = R + 3;
        plazaR = size == Size.CHICA ? 6 : size == Size.MEDIANA ? 8 : size == Size.GRANDE ? 10 : 13;
        if (layout == null) layout = Layout.values()[r.nextInt(Layout.values().length)];
        plaza();
        switch (layout) {
            case CUADRICULA -> gridRoads();
            case LINEAL -> linearRoads();
            case ANILLO -> ringRoads();
            case RAMIFICADO -> branchRoads();
            default -> {
                mainRoads();
                sideRoads();
            }
        }
        paveRoads();
        placeBuildings();
        water();
        decorate();
        defenses();
        plazaMarkers();
        return out;
    }

    // ------------------------------------------------------------------ plaza

    private void plaza() {
        Canvas f = Buildings.fountain(s, r, size.ordinal() >= Size.GRANDE.ordinal() ? 2 : 1);
        int ox = cx - f.w / 2, oz = cz - f.d / 2;
        for (int x = -plazaR; x <= plazaR; x++)
            for (int z = -plazaR; z <= plazaR; z++) {
                if (x * x + z * z > plazaR * plazaR + 2) continue;
                int wx = cx + x, wz = cz + z;
                used.add(col(wx, wz));
                road.add(col(wx, wz));
            }
        f.stamp(out, t, ox, oz, 0, s, r);
        // bancos y faroles alrededor
        int n = size.ordinal() >= Size.GRANDE.ordinal() ? 8 : 4;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n + Math.PI / n;
            int x = cx + (int) Math.round(Math.cos(a) * (plazaR - 1)), z = cz + (int) Math.round(Math.sin(a) * (plazaR - 1));
            lampPost(x, z);
        }
    }

    private void plazaMarkers() {
        String[] names = {"plaza_norte", "plaza_este", "plaza_sur", "plaza_oeste"};
        int[][] dir = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int i = 0; i < 4; i++) {
            int x = cx + dir[i][0] * (plazaR - 2), z = cz + dir[i][1] * (plazaR - 2);
            float yaw = new float[]{0, 90, 180, -90}[i];
            out.mark("npc", x, g(x, z) + 1, z, names[i], yaw);
        }
        for (int i = 0; i < (size == Size.CHICA ? 3 : 6); i++) {
            double a = r.nextDouble() * Math.PI * 2;
            int x = cx + (int) Math.round(Math.cos(a) * (plazaR - 1.5)), z = cz + (int) Math.round(Math.sin(a) * (plazaR - 1.5));
            out.mark("villager", x, g(x, z) + 1, z, "", 0);
        }
    }

    // ------------------------------------------------------------------ caminos

    private void mainRoads() {
        int n = size == Size.CHICA ? 3 : 4;
        double base = r.nextDouble() * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            double a = base + Math.PI * 2 * i / n + (r.nextDouble() - 0.5) * 0.5;
            List<int[]> pts = walk(cx + Math.cos(a) * plazaR, cz + Math.sin(a) * plazaR, a, wallR + 4, 0.08);
            roads.add(pts);
            int[] last = pts.get(pts.size() - 1);
            roadEnds.add(new int[]{last[0], last[1], (int) Math.round(Math.cos(a) * 100), (int) Math.round(Math.sin(a) * 100)});
            mark(pts, size == Size.CHICA ? 1 : 2);
        }
    }

    private void sideRoads() {
        int per = size == Size.CHICA ? 2 : size == Size.MEDIANA ? 3 : 4;
        List<List<int[]>> mains = new ArrayList<>(roads);
        for (List<int[]> m : mains) {
            for (int k = 0; k < per; k++) {
                int idx = Math.min(m.size() - 1, (int) (m.size() * (0.3 + 0.5 * r.nextDouble())));
                int[] p = m.get(idx);
                int[] q = m.get(Math.max(0, idx - 2));
                double a = Math.atan2(p[1] - q[1], p[0] - q[0]) + (r.nextBoolean() ? 1 : -1) * (Math.PI / 2 + (r.nextDouble() - 0.5) * 0.4);
                int len = (int) (R * (0.35 + r.nextDouble() * 0.3));
                List<int[]> pts = walk(p[0], p[1], a, len, 0.12);
                // que no se salga del muro
                pts.removeIf(c -> dist(c[0], c[1]) > R);
                if (pts.size() < 6) continue;
                roads.add(pts);
                mark(pts, 1);
            }
        }
    }

    /** Calle recta (o casi) entre dos puntos. */
    private List<int[]> line(double x1, double z1, double x2, double z2) {
        List<int[]> pts = new ArrayList<>();
        int n = (int) Math.ceil(Math.hypot(x2 - x1, z2 - z1) * 2);
        int lx = Integer.MIN_VALUE, lz = 0;
        for (int i = 0; i <= n; i++) {
            int x = (int) Math.round(x1 + (x2 - x1) * i / (double) n), z = (int) Math.round(z1 + (z2 - z1) * i / (double) n);
            if (x == lx && z == lz) continue;
            lx = x;
            lz = z;
            if (dist(x, z) <= R + 1 || i < 3) pts.add(new int[]{x, z});
        }
        return pts;
    }

    private void spoke(double a, int half) {
        List<int[]> pts = walk(cx + Math.cos(a) * plazaR, cz + Math.sin(a) * plazaR, a, wallR + 4, 0.03);
        roads.add(pts);
        int[] last = pts.get(pts.size() - 1);
        roadEnds.add(new int[]{last[0], last[1], (int) Math.round(Math.cos(a) * 100), (int) Math.round(Math.sin(a) * 100)});
        mark(pts, half);
    }

    /** Cuadrícula: dos avenidas en cruz y calles paralelas. */
    private void gridRoads() {
        int half = size == Size.CHICA ? 1 : 2;
        for (int i = 0; i < 4; i++) spoke(Math.PI / 2 * i, half);
        int step = size.ordinal() >= Size.GRANDE.ordinal() ? 18 : 17;
        for (int k = -3; k <= 3; k++) {
            if (k == 0) continue;
            int off = k * step;
            if (Math.abs(off) > R - 8) continue;
            double span = Math.sqrt(Math.max(0, (double) R * R - off * off)) - 4;
            List<int[]> a = line(cx + off, cz - span, cx + off, cz + span);
            List<int[]> b = line(cx - span, cz + off, cx + span, cz + off);
            for (List<int[]> l : List.of(a, b)) if (l.size() > 6) {
                roads.add(l);
                mark(l, 1);
            }
        }
    }

    /** Lineal: un camino largo que cruza el pueblo y callejones a los lados. */
    private void linearRoads() {
        double a = r.nextDouble() * Math.PI;
        int half = size == Size.CHICA ? 1 : 2;
        spoke(a, half);
        spoke(a + Math.PI, half);
        List<int[]> main = new ArrayList<>(roads.get(0));
        java.util.Collections.reverse(main);
        main.addAll(roads.get(1));
        for (int i = 10; i < main.size() - 10; i += 11) {
            int[] p = main.get(i), q = main.get(i - 3);
            double ang = Math.atan2(p[1] - q[1], p[0] - q[0]) + (((i / 11) % 2 == 0) ? 1 : -1) * Math.PI / 2;
            int len = (int) (R * (0.25 + r.nextDouble() * 0.25));
            List<int[]> lane = walk(p[0], p[1], ang, len, 0.05);
            lane.removeIf(c -> dist(c[0], c[1]) > R);
            if (lane.size() < 5) continue;
            roads.add(lane);
            mark(lane, 1);
        }
    }

    /** Anillo: una calle circular alrededor de la plaza y radios hacia afuera. */
    private void ringRoads() {
        int half = size == Size.CHICA ? 1 : 2;
        double base = r.nextDouble() * Math.PI * 2;
        int n = size == Size.CHICA ? 3 : 4;
        for (int i = 0; i < n; i++) spoke(base + Math.PI * 2 * i / n, half);
        double rr = R * (size == Size.CHICA ? 0.5 : 0.45);
        List<int[]> ring = new ArrayList<>();
        int lx = Integer.MIN_VALUE, lz = 0;
        for (int i = 0; i <= 720; i++) {
            double ang = Math.PI * 2 * i / 720;
            int x = cx + (int) Math.round(Math.cos(ang) * rr), z = cz + (int) Math.round(Math.sin(ang) * rr);
            if (x == lx && z == lz) continue;
            lx = x;
            lz = z;
            ring.add(new int[]{x, z});
        }
        roads.add(ring);
        mark(ring, 1);
        if (size != Size.CHICA) {
            double rr2 = R * 0.8;
            List<int[]> outer = new ArrayList<>();
            for (int i = 0; i <= 720; i++) {
                double ang = Math.PI * 2 * i / 720;
                int x = cx + (int) Math.round(Math.cos(ang) * rr2), z = cz + (int) Math.round(Math.sin(ang) * rr2);
                if (!outer.isEmpty() && outer.get(outer.size() - 1)[0] == x && outer.get(outer.size() - 1)[1] == z) continue;
                outer.add(new int[]{x, z});
            }
            roads.add(outer);
            mark(outer, 1);
        }
    }

    /** Ramificado: caminos que se abren en ramas y sub-ramas, como un pueblo que creció solo. */
    private void branchRoads() {
        int n = size == Size.CHICA ? 2 : 3;
        double base = r.nextDouble() * Math.PI * 2;
        for (int i = 0; i < n; i++) spoke(base + Math.PI * 2 * i / n + (r.nextDouble() - 0.5) * 0.6, size == Size.CHICA ? 1 : 2);
        List<List<int[]>> frontier = new ArrayList<>(roads);
        for (int depth = 0; depth < 2; depth++) {
            List<List<int[]>> next = new ArrayList<>();
            for (List<int[]> m : frontier) {
                int branches = depth == 0 ? 2 + r.nextInt(2) : 1 + r.nextInt(2);
                for (int k = 0; k < branches; k++) {
                    if (m.size() < 10) break;
                    int idx = 5 + r.nextInt(m.size() - 8);
                    int[] p = m.get(idx), q = m.get(Math.max(0, idx - 3));
                    double a = Math.atan2(p[1] - q[1], p[0] - q[0]) + (r.nextBoolean() ? 1 : -1) * (0.6 + r.nextDouble() * 0.6);
                    List<int[]> b = walk(p[0], p[1], a, (int) (R * (0.3 + r.nextDouble() * 0.25)), 0.25);
                    b.removeIf(c -> dist(c[0], c[1]) > R);
                    if (b.size() < 6) continue;
                    roads.add(b);
                    mark(b, 1);
                    next.add(b);
                }
            }
            frontier = next;
        }
    }

    private List<int[]> walk(double x, double z, double a, int len, double wobble) {
        List<int[]> pts = new ArrayList<>();
        int lastX = Integer.MIN_VALUE, lastZ = Integer.MIN_VALUE;
        for (int i = 0; i < len * 3; i++) {
            if (i % 8 == 0) a += (r.nextDouble() - 0.5) * wobble * 2;
            x += Math.cos(a) * 0.5;
            z += Math.sin(a) * 0.5;
            int ix = (int) Math.round(x), iz = (int) Math.round(z);
            if (ix == lastX && iz == lastZ) continue;
            lastX = ix;
            lastZ = iz;
            pts.add(new int[]{ix, iz});
            if (dist(ix, iz) > len + plazaR) break;
        }
        return pts;
    }

    private double dist(int x, int z) {
        return Math.hypot(x - cx, z - cz);
    }

    private void mark(List<int[]> pts, int half) {
        for (int[] p : pts)
            for (int dx = -half; dx <= half; dx++)
                for (int dz = -half; dz <= half; dz++) {
                    if (dx * dx + dz * dz > half * half + 1) continue;
                    long k = col(p[0] + dx, p[1] + dz);
                    road.add(k);
                    used.add(k);
                }
    }

    /** Pone los caminos sobre el terreno tal como está (y puentes sobre el agua). */
    private void paveRoads() {
        for (long k : road) {
            int x = (int) (k >> 32), z = (int) k;
            if (dist(x, z) > wallR + 6) continue;
            int y = g(x, z);
            if (dist(x, z) <= plazaR) {
                out.soft(x, y, z, size == Size.CHICA ? s.pathMix(r) : (r.nextInt(5) == 0 ? s.stone2 : s.stone));
                for (int yy = y + 1; yy <= y + 3; yy++) out.soft(x, yy, z, "air");
                continue;
            }
            if (t.water(x, z) && !out.groundOverride.containsKey(col(x, z))) {
                out.set(x, y + 1, z, s.planks);
                continue;
            }
            out.soft(x, y, z, s.pathMix(r));
            for (int yy = y + 1; yy <= y + 3; yy++) out.soft(x, yy, z, "air");
        }
    }

    // ------------------------------------------------------------------ edificios

    private record Kind(String npc, BiFunction<Style, Random, Canvas> make, int where) {}  // where: 0 cerca, 1 lejos, 2 cualquiera

    private void placeBuildings() {
        List<Kind> must = new ArrayList<>(List.of(
                new Kind("casa", Buildings::mayor, 0),
                new Kind("herreria", Buildings::smithy, 0),
                new Kind("tienda", Buildings::shop, 0),
                new Kind("biblioteca", Buildings::library, 2),
                new Kind("mapas", Buildings::cartographer, 2),
                new Kind("cuartel", Buildings::barracks, 2),
                new Kind("torre", Buildings::watchtower, 1),
                new Kind("granja", Buildings::farm, 1),
                new Kind("tienda_negra", Buildings::blackMarket, 1)));
        if (size != Size.CHICA) must.add(2, new Kind(null, Buildings::chapel, 0));
        if (size.ordinal() >= Size.GRANDE.ordinal()) must.add(3, new Kind(null, Buildings::tavern, 0));
        List<int[]> spots = candidates();
        for (Kind k : must) {
            boolean ok = false;
            List<int[]> order = new ArrayList<>(spots);
            if (k.where == 0) order.sort(Comparator.comparingDouble(p -> dist(p[0], p[1])));
            else if (k.where == 1) order.sort(Comparator.comparingDouble(p -> -dist(p[0], p[1])));
            else java.util.Collections.shuffle(order, r);
            for (int attempt = 0; attempt < 3 && !ok; attempt++) {
                Canvas c = k.make.apply(s, r);
                for (int[] sp : order) if (tryPlaceShift(c, sp)) {
                    ok = true;
                    break;
                }
            }
            if (ok && k.npc != null) placedNpcs.add(k.npc);
            else if (!ok && k.npc != null) {
                // no cupo: el personaje se queda en la plaza
                double a = r.nextDouble() * Math.PI * 2;
                int x = cx + (int) Math.round(Math.cos(a) * (plazaR + 1)), z = cz + (int) Math.round(Math.sin(a) * (plazaR + 1));
                out.mark("npc", x, g(x, z) + 1, z, k.npc, 0);
            }
        }
        // relleno: casas de todos los tamaños, granjas, corrales, pozos
        int target = switch (size) {
            case CHICA -> 9;
            case MEDIANA -> 18;
            case GRANDE -> 32;
            case ENORME -> 56;
        };
        java.util.Collections.shuffle(spots, r);
        java.util.Map<int[], Double> key = new java.util.IdentityHashMap<>();
        for (int[] p : spots) key.put(p, dist(p[0], p[1]) + r.nextDouble() * 12);
        spots.sort(Comparator.comparingDouble(key::get));
        int placed = 0;
        for (int[] sp : spots) {
            if (placed >= target) break;
            double far = dist(sp[0], sp[1]) / R;
            int roll = r.nextInt(100);
            Canvas c;
            if (far > 0.65 && roll < 30) c = Buildings.farm(s, r);
            else if (far > 0.55 && roll < 45) c = Buildings.pen(s, r);
            else if (roll < 50) c = Buildings.house(s, r, 0);
            else if (roll < 85) c = Buildings.house(s, r, 1);
            else if (roll < 95) c = Buildings.house(s, r, 2);
            else c = Buildings.well(s, r);
            c.markers.removeIf(m -> m.type().equals("npc"));
            if (tryPlaceShift(c, sp)) placed++;
        }
        // mercado en la plaza
        int stalls = size == Size.CHICA ? 2 : size == Size.MEDIANA ? 3 : size == Size.GRANDE ? 5 : 8;
        for (int i = 0; i < stalls * 4 && stalls > 0; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            int rad = plazaR + 2;
            int x = cx + (int) (Math.cos(a) * rad), z = cz + (int) (Math.sin(a) * rad);
            Canvas c = Buildings.stall(s, r);
            int rot = facing(cx - x, cz - z);
            if (freeRect(x - 1, z - 1, x + 1, z + 1, 1) && Canvas.slope(out, t, x - 1, z - 1, x + 1, z + 1) <= 2) {
                c.stamp(out, t, x - 1, z - 1, rot, s, r);
                occupy(x - 1, z - 1, x + 1, z + 1, 1);
                stalls--;
            }
        }
    }

    /** Lugares a los lados de los caminos: {x, z, nx*100, nz*100, mitad del camino}. */
    private List<int[]> candidates() {
        List<int[]> out = new ArrayList<>();
        for (int ri = 0; ri < roads.size(); ri++) {
            List<int[]> pts = roads.get(ri);
            int half = ri < (size == Size.CHICA ? 3 : 4) ? (size == Size.CHICA ? 1 : 2) : 1;
            for (int i = 4; i < pts.size() - 2; i += 3) {
                int[] p = pts.get(i), q = pts.get(i - 3);
                double dx = p[0] - q[0], dz = p[1] - q[1], len = Math.hypot(dx, dz);
                if (len == 0) continue;
                for (int side : new int[]{-1, 1}) {
                    double nx = -dz / len * side, nz = dx / len * side;
                    out.add(new int[]{p[0], p[1], (int) Math.round(nx * 100), (int) Math.round(nz * 100), half});
                }
            }
        }
        return out;
    }

    /** Rotación para que el frente (puerta) mire hacia (dx, dz). */
    private static int facing(double dx, double dz) {
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? 1 : 3;
        return dz > 0 ? 2 : 0;
    }

    private boolean freeRect(int x1, int z1, int x2, int z2, int pad) {
        for (int x = x1 - pad; x <= x2 + pad; x++)
            for (int z = z1 - pad; z <= z2 + pad; z++) if (used.contains(col(x, z))) return false;
        return true;
    }

    private void occupy(int x1, int z1, int x2, int z2, int pad) {
        for (int x = x1 - pad; x <= x2 + pad; x++) for (int z = z1 - pad; z <= z2 + pad; z++) used.add(col(x, z));
    }

    /** Prueba el lote y también corrido un poco a los lados. */
    private boolean tryPlaceShift(Canvas c, int[] sp) {
        double tx = -sp[3] / 100.0, tz = sp[2] / 100.0;
        for (int sh : new int[]{0, 3, -3, 6, -6}) {
            int[] q = {sp[0] + (int) Math.round(tx * sh), sp[1] + (int) Math.round(tz * sh), sp[2], sp[3], sp[4]};
            if (tryPlace(c, q)) return true;
        }
        return false;
    }

    private boolean tryPlace(Canvas c, int[] sp) {
        double nx = sp[2] / 100.0, nz = sp[3] / 100.0;
        int rot = facing(-nx, -nz);   // la puerta mira al camino
        int W = c.worldW(rot), D = c.worldD(rot);
        double extent = Math.abs(nx) > Math.abs(nz) ? W : D;
        double gap = sp[4] + 2;
        double ccx = sp[0] + nx * (gap + extent / 2.0), ccz = sp[1] + nz * (gap + extent / 2.0);
        int ox = (int) Math.round(ccx - W / 2.0), oz = (int) Math.round(ccz - D / 2.0);
        int x2 = ox + W - 1, z2 = oz + D - 1;
        // dentro de la aldea
        for (int[] corner : new int[][]{{ox, oz}, {x2, oz}, {ox, z2}, {x2, z2}}) if (dist(corner[0], corner[1]) > R - 1) return false;
        if (!freeRect(ox, oz, x2, z2, 1)) return false;
        int sl = Canvas.slope(out, t, ox, oz, x2, z2);
        if (sl < 0 || sl > (c.stilts ? 10 : 7)) return false;
        c.stamp(out, t, ox, oz, rot, s, r);
        occupy(ox, oz, x2, z2, 1);
        // senderito de la puerta al camino
        int[] door = c.toWorld(c.w / 2, -1, rot, ox, oz);
        int x = door[0], z = door[1];
        for (int i = 0; i < 6 && !road.contains(col(x, z)); i++) {
            if (!used.contains(col(x, z)) || i == 0) {
                int y = g(x, z);
                out.soft(x, y, z, s.pathMix(r));
                used.add(col(x, z));
            }
            x += Integer.signum(sp[0] - x) != 0 && Math.abs(sp[0] - x) >= Math.abs(sp[1] - z) ? Integer.signum(sp[0] - x) : 0;
            z += Math.abs(sp[1] - z) > Math.abs(sp[0] - x) ? Integer.signum(sp[1] - z) : 0;
        }
        return true;
    }

    // ------------------------------------------------------------------ agua: muelle o estanque

    private void water() {
        // ¿hay agua de verdad dentro de la aldea? → muelle
        int[] best = null;
        double bd = Double.MAX_VALUE;
        for (int x = cx - R; x <= cx + R; x += 2)
            for (int z = cz - R; z <= cz + R; z += 2) {
                if (dist(x, z) > R || !t.water(x, z) || out.groundOverride.containsKey(col(x, z))) continue;
                int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] n : nb) {
                    int lx = x - n[0] * 2, lz = z - n[1] * 2;
                    if (!t.water(lx, lz) && !used.contains(col(lx, lz))) {
                        double d = dist(x, z);
                        if (d < bd) {
                            bd = d;
                            best = new int[]{lx, lz, n[0], n[1]};
                        }
                    }
                }
            }
        if (best != null) {
            dock(best[0], best[1], best[2], best[3]);
            return;
        }
        // si no, un estanque con su muelle
        List<int[]> spots = candidates();
        java.util.Collections.shuffle(spots, r);
        for (int[] sp : spots) {
            int px = sp[0] + (int) Math.round(sp[2] / 100.0 * 8), pz = sp[1] + (int) Math.round(sp[3] / 100.0 * 8);
            if (dist(px, pz) > R - 6 || !freeRect(px - 5, pz - 5, px + 5, pz + 5, 0)) continue;
            int sl = Canvas.slope(out, t, px - 5, pz - 5, px + 5, pz + 5);
            if (sl < 0 || sl > 3) continue;
            int min = Integer.MAX_VALUE;
            for (int x = px - 5; x <= px + 5; x++) for (int z = pz - 5; z <= pz + 5; z++) min = Math.min(min, g(x, z));
            for (int x = px - 5; x <= px + 5; x++)
                for (int z = pz - 5; z <= pz + 5; z++) {
                    double e = Math.pow((x - px) / 4.6, 2) + Math.pow((z - pz) / 3.6, 2);
                    int gy = g(x, z);
                    if (e <= 1) {
                        for (int y = min + 1; y <= gy + 1; y++) out.set(x, y, z, "air");
                        out.set(x, min, z, "water");
                        out.set(x, min - 1, z, e < 0.5 ? "water" : "clay");
                        out.set(x, min - 2, z, "clay");
                        if (r.nextInt(9) == 0 && e < 0.7) out.set(x, min + 1, z, "lily_pad");
                        out.setGround(x, z, min);
                    } else if (e <= 1.6) {
                        for (int y = min + 1; y <= gy; y++) out.set(x, y, z, "air");
                        for (int y = gy + 1; y <= min; y++) out.set(x, y, z, "dirt");
                        out.set(x, min, z, s.id.equals("desierto") ? "sand" : "grass_block");
                        if (r.nextInt(4) == 0) out.set(x, min + 1, z, r.nextBoolean() ? "sugar_cane" : s.flower1);
                        out.setGround(x, z, min);
                    }
                    used.add(col(x, z));
                }
            // muelle chiquito al estanque
            int dx = -(int) Math.signum(sp[2]), dz = -(int) Math.signum(sp[3]);
            if (Math.abs(sp[2]) > Math.abs(sp[3])) dz = 0; else dx = 0;
            int sx = px + dx * 5, sz = pz + dz * 5;
            for (int i = 0; i <= 3; i++) out.set(sx - dx * i, min + 1, sz - dz * i, s.planks.replace("_planks", "_slab[type=bottom]"));
            out.set(sx - dx * 3 + dz, min + 1, sz - dz * 3 + dx, s.fence);
            out.set(sx - dx * 3 + dz, min + 2, sz - dz * 3 + dx, s.light);
            out.mark("npc", sx - dx * 2, min + 1, sz - dz * 2, "muelle", 0);
            out.mark("villager", sx, min + 1, sz, "fisherman", 0);
            return;
        }
        // último recurso: el pescador en la plaza
        out.mark("npc", cx + 3, g(cx + 3, cz + 3) + 1, cz + 3, "muelle", 0);
    }

    private void dock(int lx, int lz, int dx, int dz) {
        int y = t.ground(lx + dx * 2, lz + dz * 2);   // superficie del agua
        for (int i = 0; i <= 7; i++) {
            int x = lx + dx * i, z = lz + dz * i;
            for (int w = -1; w <= 1; w++) {
                int wx = x + dz * w, wz = z + dx * w;
                out.set(wx, y + 1, wz, s.planks);
                if (t.water(wx, wz)) for (int yy = y; yy >= y - 4; yy--) if (Math.abs(w) == 1 && i % 3 == 0) out.set(wx, yy, wz, s.logY());
            }
            if (i % 3 == 0) {
                out.set(x + dz * 2, y + 2, z + dx * 2, s.fence);
                out.set(x - dz * 2, y + 2, z - dx * 2, s.fence);
            }
        }
        int ex = lx + dx * 7, ez = lz + dz * 7;
        out.set(ex + dz, y + 2, ez + dx, s.fence);
        out.set(ex + dz, y + 3, ez + dx, s.light);
        out.set(ex - dz, y + 2, ez - dx, "barrel[facing=up]");
        out.mark("npc", lx + dx * 5, y + 2, lz + dz * 5, "muelle", 0);
        out.mark("villager", ex, y + 2, ez, "fisherman", 0);
    }

    // ------------------------------------------------------------------ decoración

    private void lampPost(int x, int z) {
        if (t.water(x, z) && !out.groundOverride.containsKey(col(x, z))) return;
        int y = g(x, z);
        out.set(x, y, z, s.stone);
        out.set(x, y + 1, z, s.stoneWall);
        out.set(x, y + 2, z, s.fence);
        out.set(x, y + 3, z, s.fence);
        out.set(x, y + 4, z, s.light);
        used.add(col(x, z));
    }

    private void decorate() {
        // faroles a lo largo de los caminos
        for (List<int[]> pts : roads) {
            for (int i = 6; i < pts.size(); i += 11) {
                int[] p = pts.get(i), q = pts.get(Math.max(0, i - 2));
                double dx = p[0] - q[0], dz = p[1] - q[1], len = Math.max(1, Math.hypot(dx, dz));
                int side = (i / 11) % 2 == 0 ? 1 : -1;
                for (int off = 2; off <= 3; off++) {
                    int x = p[0] + (int) Math.round(-dz / len * off * side), z = p[1] + (int) Math.round(dx / len * off * side);
                    if (!used.contains(col(x, z)) && dist(x, z) < R + 2) {
                        lampPost(x, z);
                        break;
                    }
                }
            }
        }
        // flores, pasto y fardos sueltos
        int n = size == Size.CHICA ? 60 : size == Size.MEDIANA ? 110 : size == Size.GRANDE ? 180 : 300;
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = plazaR + r.nextDouble() * (R - plazaR);
            int x = cx + (int) (Math.cos(a) * d), z = cz + (int) (Math.sin(a) * d);
            if (used.contains(col(x, z)) || t.water(x, z)) continue;
            String surf = t.surface(x, z);
            if (surf == null || !(surf.contains("grass") || surf.contains("sand") || surf.contains("dirt") || surf.contains("snow") || surf.contains("mud"))) continue;
            int y = g(x, z);
            int roll = r.nextInt(10);
            String b = roll < 4 ? s.flower1 : roll < 7 ? s.flower2 : roll < 8 ? "hay_block" : roll < 9 ? "barrel[facing=up]" : "short_grass";
            if (s.id.equals("desierto") && b.equals("cactus")) out.set(x, y + 2, z, "cactus");
            out.soft(x, y + 1, z, b);
        }
    }

    // ------------------------------------------------------------------ defensas

    private void defenses() {
        List<int[]> gates = new ArrayList<>();
        for (int[] e : roadEnds) gates.add(e);
        switch (size) {
            case CHICA -> arches(gates);
            case MEDIANA -> wall(gates, false);
            case GRANDE, ENORME -> wall(gates, true);
        }
        gateMarkers(gates);
    }

    /** Arcos de entrada con faroles (aldeas chicas). */
    private void arches(List<int[]> gates) {
        for (int[] e : gates) {
            double ax = e[2] / 100.0, az = e[3] / 100.0;
            int x = cx + (int) Math.round(ax * (R - 2)), z = cz + (int) Math.round(az * (R - 2));
            // el arco cruza el camino
            int px = (int) Math.round(-az * 3), pz = (int) Math.round(ax * 3);
            int y = Math.max(g(x + px, z + pz), g(x - px, z - pz));
            for (int sgn : new int[]{-1, 1}) {
                int bx = x + px * sgn, bz = z + pz * sgn;
                int by = g(bx, bz);
                for (int yy = by + 1; yy <= y + 5; yy++) out.set(bx, yy, bz, s.logY());
                out.set(bx, y + 6, bz, s.light);
            }
            for (int i = -3; i <= 3; i++) out.set(x + (int) Math.round(-az * i), y + 5, z + (int) Math.round(ax * i), Math.abs(az) > Math.abs(ax) ? s.logX() : s.logZ());
            e[0] = x;
            e[1] = z;
        }
        // cerca baja alrededor de los campos
    }

    /** Muro alrededor: empalizada de troncos (mediana) o muralla de piedra con torres y portones (grande). */
    private void wall(List<int[]> gates, boolean stone) {
        int H = stone ? 8 : 5;
        double ph1 = r.nextDouble() * 6, ph2 = r.nextDouble() * 6;
        List<int[]> ring = new ArrayList<>();
        int steps = (int) (wallR * 2 * Math.PI * 3);
        int lx = Integer.MIN_VALUE, lz = 0;
        for (int i = 0; i <= steps; i++) {
            double a = Math.PI * 2 * i / steps;
            double rad = wallR + Math.sin(a * 3 + ph1) * 2.5 + Math.sin(a * 5 + ph2) * 1.5;
            int x = cx + (int) Math.round(Math.cos(a) * rad), z = cz + (int) Math.round(Math.sin(a) * rad);
            if (x == lx && z == lz) continue;
            lx = x;
            lz = z;
            ring.add(new int[]{x, z, (int) (a * 1000)});
        }
        // dónde abrir portones: donde los caminos principales cruzan el muro
        List<double[]> openings = new ArrayList<>();
        for (int[] e : gates) openings.add(new double[]{Math.atan2(e[3], e[2])});
        Set<Long> done = new HashSet<>();
        int towerEvery = stone ? 34 : 0, sinceTower = 0;
        for (int i = 0; i < ring.size(); i++) {
            int[] p = ring.get(i);
            double a = p[2] / 1000.0;
            boolean gap = false;
            for (double[] o : openings) {
                double da = Math.abs(Math.atan2(Math.sin(a - o[0]), Math.cos(a - o[0])));
                if (da * wallR < 2.6) gap = true;
            }
            if (!done.add(col(p[0], p[1]))) continue;
            if (t.water(p[0], p[1]) && !out.groundOverride.containsKey(col(p[0], p[1]))) {
                // sobre el agua: pilotes
                int y = g(p[0], p[1]);
                for (int yy = y - 3; yy <= y + H; yy++) out.set(p[0], yy, p[1], stone ? s.stoneMix(r) : s.logY());
                continue;
            }
            int y = g(p[0], p[1]);
            if (gap) {
                out.soft(p[0], y, p[1], s.pathMix(r));
                for (int yy = y + 1; yy <= y + H - 2; yy++) out.set(p[0], yy, p[1], "air");
                for (int yy = y + H - 1; yy <= y + H; yy++) out.set(p[0], yy, p[1], stone ? s.stoneMix(r) : s.logY());
                if (stone) out.set(p[0], y + H - 2, p[1], "iron_bars");
                continue;
            }
            for (int yy = y - 2; yy <= y + H; yy++) out.set(p[0], yy, p[1], stone ? s.stoneMix(r) : (s.id.equals("desierto") ? "stripped_jungle_log[axis=y]" : s.logY()));
            if (stone) {
                // almenas y pasarela de adentro
                if (i % 2 == 0) out.set(p[0], y + H + 1, p[1], s.stoneMix(r));
                int ix = cx + (int) Math.round((p[0] - cx) * (wallR - 1.0) / wallR), iz = cz + (int) Math.round((p[1] - cz) * (wallR - 1.0) / wallR);
                if (!done.contains(col(ix, iz)) && !used.contains(col(ix, iz))) {
                    int iy = g(ix, iz);
                    for (int yy = iy - 1; yy < y + H - 1; yy++) out.soft(ix, yy, iz, s.stoneMix(r));
                    out.set(ix, y + H - 1, iz, s.stoneSlab + "[type=top]");
                }
                if (++sinceTower >= towerEvery) {
                    sinceTower = 0;
                    tower(p[0], p[1], y, H);
                }
            } else {
                // puntas de la empalizada
                if (i % 3 == 0) out.set(p[0], y + H + 1, p[1], s.fence);
                if (i % 23 == 0) {
                    out.set(p[0], y + H + 1, p[1], s.fence);
                    out.set(p[0], y + H + 2, p[1], s.light);
                }
            }
        }
        // portones: dos torres o postes a los lados
        for (int[] e : gates) {
            double a = Math.atan2(e[3], e[2]);
            double rad = wallR + Math.sin(a * 3 + ph1) * 2.5 + Math.sin(a * 5 + ph2) * 1.5;
            int x = cx + (int) Math.round(Math.cos(a) * rad), z = cz + (int) Math.round(Math.sin(a) * rad);
            double px = -Math.sin(a), pz = Math.cos(a);
            for (int sgn : new int[]{-1, 1}) {
                int tx = x + (int) Math.round(px * 4 * sgn), tz = z + (int) Math.round(pz * 4 * sgn);
                if (stone) tower(tx, tz, g(tx, tz), H + 2);
                else {
                    int ty = g(tx, tz);
                    for (int yy = ty + 1; yy <= ty + H + 2; yy++) out.set(tx, yy, tz, s.logY());
                    out.set(tx, ty + H + 3, tz, s.light);
                }
                int gx = x + (int) Math.round(px * sgn - Math.cos(a) * 4), gz = z + (int) Math.round(pz * sgn - Math.sin(a) * 4);
                out.mark("villager", gx, g(gx, gz) + 1, gz, "guard", 0);
            }
            e[0] = x - (int) Math.round(Math.cos(a) * 3);
            e[1] = z - (int) Math.round(Math.sin(a) * 3);
        }
    }

    /** Torre redonda de la muralla, rígida (piso a una sola altura). */
    private void tower(int x, int z, int y0, int H) {
        int rad = 3;
        int base = Integer.MAX_VALUE;
        for (int dx = -rad; dx <= rad; dx++) for (int dz = -rad; dz <= rad; dz++) base = Math.min(base, g(x + dx, z + dz));
        int top = y0 + H + 3;
        for (int dx = -rad; dx <= rad; dx++)
            for (int dz = -rad; dz <= rad; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > rad + 0.4) continue;
                int wx = x + dx, wz = z + dz;
                boolean shell = d > rad - 0.8;
                for (int yy = base - 1; yy <= top; yy++) out.set(wx, yy, wz, shell ? s.stoneMix(r) : (yy == top ? s.planks : "air"));
                if (shell && (dx + dz) % 2 == 0) out.set(wx, top + 1, wz, s.stoneMix(r));
                if (shell && (dx == 0 || dz == 0)) out.set(wx, top - 3, wz, "iron_bars");
                used.add(col(wx, wz));
            }
        out.set(x, top + 1, z, s.light);
        // el guardia vigila al pie de la torre, por dentro (arriba se caería caminando)
        int ix = cx + (int) Math.round((x - cx) * (1 - 6.0 / Math.max(6, Math.hypot(x - cx, z - cz)))), iz = cz + (int) Math.round((z - cz) * (1 - 6.0 / Math.max(6, Math.hypot(x - cx, z - cz))));
        if (!used.contains(col(ix, iz))) out.mark("villager", ix, g(ix, iz) + 1, iz, "guard", 0);
    }

    /** Asigna puerta_norte/sur/este/oeste a la entrada que más mira a cada lado. */
    private void gateMarkers(List<int[]> gates) {
        String[] names = {"puerta_norte", "puerta_este", "puerta_sur", "puerta_oeste"};
        double[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int i = 0; i < 4; i++) {
            int[] best = null;
            double bd = -9;
            for (int[] e : gates) {
                double ax = e[2] / 100.0, az = e[3] / 100.0;
                double dot = ax * dirs[i][0] + az * dirs[i][1];
                if (dot > bd) {
                    bd = dot;
                    best = e;
                }
            }
            if (best == null) continue;
            int x = best[0], z = best[1];
            out.mark("npc", x, g(x, z) + 1, z, names[i], 0);
        }
    }

    public Set<String> placedNpcs() {
        return placedNpcs;
    }
}
