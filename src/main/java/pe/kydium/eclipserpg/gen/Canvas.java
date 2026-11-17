package pe.kydium.eclipserpg.gen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Un edificio en coordenadas propias: x de 0 a w-1, z de 0 a d-1, la puerta mira al norte (z = 0).
 * y = 0 es la capa del piso; el edificio NUNCA se deforma: al ponerlo en el mundo se asienta entero a una
 * altura, con cimientos (o pilotes) hacia abajo y excavando la ladera hacia arriba.
 */
public final class Canvas {
    public final int w, d;
    public final Map<Long, String> blocks = new LinkedHashMap<>();
    public final List<Build.Marker> markers = new ArrayList<>();
    public int height;            // la altura más alta usada
    public int margin = 1;        // terreno alrededor que se nivela con el piso
    public boolean stilts;        // pilotes en vez de cimientos llenos (cabañas, torres de vigía)
    public boolean noClear;       // no excavar por encima (ruinas que se funden con el terreno)
    public int sink;              // hundir el piso (negativo = enterrado)

    public Canvas(int w, int d) {
        this.w = w;
        this.d = d;
    }

    private static long k(int x, int y, int z) {
        return Build.key(x, y, z);
    }

    public void set(int x, int y, int z, String b) {
        if (b == null) return;
        long kk = k(x, y, z);
        blocks.remove(kk);
        blocks.put(kk, b);
        if (y > height) height = y;
    }

    public String get(int x, int y, int z) {
        return blocks.get(k(x, y, z));
    }

    public boolean has(int x, int y, int z) {
        String b = blocks.get(k(x, y, z));
        return b != null && !b.equals("air");
    }

    public void soft(int x, int y, int z, String b) {
        if (!blocks.containsKey(k(x, y, z))) set(x, y, z, b);
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, String b) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, b);
    }

    public void mark(String type, int x, int y, int z, String arg, float yaw) {
        markers.add(new Build.Marker(type, x, y, z, arg, yaw));
    }

    // ------------------------------------------------------------------ piezas

    /** Piso, paredes con postes en las esquinas, viga arriba y aire adentro. */
    public void room(int x1, int z1, int x2, int z2, int y0, int h, Style s, Random r, boolean framed) {
        fill(x1, y0, z1, x2, y0, z2, s.floor);
        fill(x1 + 1, y0 + 1, z1 + 1, x2 - 1, y0 + h, z2 - 1, "air");
        for (int y = y0 + 1; y <= y0 + h; y++) {
            for (int x = x1; x <= x2; x++) {
                set(x, y, z1, s.wallMix(r));
                set(x, y, z2, s.wallMix(r));
            }
            for (int z = z1; z <= z2; z++) {
                set(x1, y, z, s.wallMix(r));
                set(x2, y, z, s.wallMix(r));
            }
        }
        if (framed) {
            for (int y = y0 + 1; y <= y0 + h; y++) {
                set(x1, y, z1, s.logY());
                set(x2, y, z1, s.logY());
                set(x1, y, z2, s.logY());
                set(x2, y, z2, s.logY());
            }
            for (int x = x1 + 1; x < x2; x++) {
                set(x, y0 + h, z1, s.stripped + "[axis=x]");
                set(x, y0 + h, z2, s.stripped + "[axis=x]");
            }
            for (int z = z1 + 1; z < z2; z++) {
                set(x1, y0 + h, z, s.stripped + "[axis=z]");
                set(x2, y0 + h, z, s.stripped + "[axis=z]");
            }
            // zócalo de piedra
            for (int x = x1 + 1; x < x2; x++) {
                set(x, y0 + 1, z1, s.foundationMix(r));
                set(x, y0 + 1, z2, s.foundationMix(r));
            }
            for (int z = z1 + 1; z < z2; z++) {
                set(x1, y0 + 1, z, s.foundationMix(r));
                set(x2, y0 + 1, z, s.foundationMix(r));
            }
        }
    }

    /** Ventanas en las paredes del cuarto, cada "gap" bloques, sin tocar esquinas ni la puerta. */
    public void windows(int x1, int z1, int x2, int z2, int y, int gap, Style s, boolean tall) {
        for (int x = x1 + 2; x <= x2 - 2; x += gap) {
            win(x, y, z1, s, tall);
            win(x, y, z2, s, tall);
        }
        for (int z = z1 + 2; z <= z2 - 2; z += gap) {
            win(x1, y, z, s, tall);
            win(x2, y, z, s, tall);
        }
    }

    private void win(int x, int y, int z, Style s, boolean tall) {
        String cur = get(x, y, z);
        if (cur != null && (cur.contains("door") || cur.equals("air"))) return;
        set(x, y, z, s.window);
        if (tall) set(x, y + 1, z, s.window);
    }

    public void door(int x, int y, int z, String facing, String mat) {
        set(x, y, z, mat + "[facing=" + facing + ",half=lower,hinge=left]");
        set(x, y + 1, z, mat + "[facing=" + facing + ",half=upper,hinge=left]");
    }

    /** Techo a dos aguas: la cumbrera va a lo largo de X si alongX, si no a lo largo de Z. */
    public int gable(int x1, int z1, int x2, int z2, int baseY, Style s, int over, boolean alongX) {
        if (!alongX) return gableZ(x1, z1, x2, z2, baseY, s, over);
        int lo = z1 - over, hi = z2 + over, y = baseY, k = 0;
        while (lo <= hi) {
            for (int x = x1 - over; x <= x2 + over; x++) {
                if (lo == hi) set(x, y, lo, s.slab(false));
                else {
                    set(x, y, lo, s.stairs("south", false));
                    set(x, y, hi, s.stairs("north", false));
                    if (k > 0) {
                        // por debajo, para que no se vea hueco
                        set(x, y - 1, lo, s.roofFull);
                        set(x, y - 1, hi, s.roofFull);
                    }
                }
            }
            // frontones
            for (int z = lo + 1; z <= hi - 1; z++) {
                if (z < z1 || z > z2) continue;
                set(x1, y, z, s.wall.equals("calcite") || s.wall.contains("terracotta") ? s.planks : s.wall);
                set(x2, y, z, s.wall.equals("calcite") || s.wall.contains("terracotta") ? s.planks : s.wall);
            }
            lo++;
            hi--;
            y++;
            k++;
        }
        // los bordes de abajo del alero
        for (int x = x1 - over; x <= x2 + over; x++) {
            soft(x, baseY - 1, z1 - over, s.stairs("north", true));
            soft(x, baseY - 1, z2 + over, s.stairs("south", true));
        }
        return y;
    }

    private int gableZ(int x1, int z1, int x2, int z2, int baseY, Style s, int over) {
        int lo = x1 - over, hi = x2 + over, y = baseY, k = 0;
        while (lo <= hi) {
            for (int z = z1 - over; z <= z2 + over; z++) {
                if (lo == hi) set(lo, y, z, s.slab(false));
                else {
                    set(lo, y, z, s.stairs("east", false));
                    set(hi, y, z, s.stairs("west", false));
                    if (k > 0) {
                        set(lo, y - 1, z, s.roofFull);
                        set(hi, y - 1, z, s.roofFull);
                    }
                }
            }
            for (int x = lo + 1; x <= hi - 1; x++) {
                if (x < x1 || x > x2) continue;
                set(x, y, z1, s.wall.equals("calcite") || s.wall.contains("terracotta") ? s.planks : s.wall);
                set(x, y, z2, s.wall.equals("calcite") || s.wall.contains("terracotta") ? s.planks : s.wall);
            }
            lo++;
            hi--;
            y++;
            k++;
        }
        for (int z = z1 - over; z <= z2 + over; z++) {
            soft(x1 - over, baseY - 1, z, s.stairs("west", true));
            soft(x2 + over, baseY - 1, z, s.stairs("east", true));
        }
        return y;
    }

    /** Techo plano con pretil (desierto, fuertes). */
    public void flatRoof(int x1, int z1, int x2, int z2, int y, Style s, boolean battlements) {
        fill(x1, y, z1, x2, y, z2, s.wall.contains("sandstone") ? "smooth_sandstone" : s.stone);
        for (int x = x1; x <= x2; x++) {
            parapet(x, y + 1, z1, s, battlements, x);
            parapet(x, y + 1, z2, s, battlements, x);
        }
        for (int z = z1; z <= z2; z++) {
            parapet(x1, y + 1, z, s, battlements, z);
            parapet(x2, y + 1, z, s, battlements, z);
        }
    }

    private void parapet(int x, int y, int z, Style s, boolean battlements, int i) {
        if (battlements) {
            set(x, y, z, s.stone);
            if (i % 2 == 0) set(x, y + 1, z, s.stoneSlab + "[type=bottom]");
        } else set(x, y, z, s.wall.contains("sandstone") ? "cut_sandstone_slab[type=bottom]" : s.stoneSlab + "[type=bottom]");
    }

    public void bed(int x, int y, int z, String facing, String color) {
        int dx = facing.equals("east") ? 1 : facing.equals("west") ? -1 : 0;
        int dz = facing.equals("south") ? 1 : facing.equals("north") ? -1 : 0;
        set(x, y, z, color + "_bed[facing=" + facing + ",part=foot]");
        set(x + dx, y, z + dz, color + "_bed[facing=" + facing + ",part=head]");
    }

    public void chimney(int x, int z, int fromY, int toY, Style s) {
        for (int y = fromY; y <= toY; y++) set(x, y, z, y == toY ? "campfire[lit=true,signal_fire=false]" : (y % 3 == 0 ? "cobblestone" : "bricks"));
    }

    // ------------------------------------------------------------------ poner en el mundo

    private static final Pattern FACING = Pattern.compile("facing=(north|east|south|west)");
    private static final Pattern AXIS = Pattern.compile("axis=(x|z)");
    private static final Pattern ROT = Pattern.compile("rotation=(\\d+)");
    private static final String[] DIRS = {"north", "east", "south", "west"};

    public static String rotate(String b, int rot) {
        if (rot == 0 || b.indexOf('[') < 0) return b;
        Matcher m = FACING.matcher(b);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            int i = java.util.Arrays.asList(DIRS).indexOf(m.group(1));
            m.appendReplacement(sb, "facing=" + DIRS[(i + rot) % 4]);
        }
        m.appendTail(sb);
        String out = sb.toString();
        if (rot % 2 == 1) {
            Matcher a = AXIS.matcher(out);
            sb = new StringBuilder();
            while (a.find()) a.appendReplacement(sb, "axis=" + (a.group(1).equals("x") ? "z" : "x"));
            a.appendTail(sb);
            out = sb.toString();
        }
        Matcher rr = ROT.matcher(out);
        if (rr.find()) out = rr.replaceFirst("rotation=" + ((Integer.parseInt(rr.group(1)) + rot * 4) % 16));
        return out;
    }

    /** Ancho y fondo en el mundo según la rotación. */
    public int worldW(int rot) {
        return rot % 2 == 0 ? w : d;
    }

    public int worldD(int rot) {
        return rot % 2 == 0 ? d : w;
    }

    public int[] toWorld(int lx, int lz, int rot, int ox, int oz) {
        return switch (rot & 3) {
            case 1 -> new int[]{ox + (d - 1 - lz), oz + lx};
            case 2 -> new int[]{ox + (w - 1 - lx), oz + (d - 1 - lz)};
            case 3 -> new int[]{ox + lz, oz + (w - 1 - lx)};
            default -> new int[]{ox + lx, oz + lz};
        };
    }

    /** Altura a la que conviene asentar el edificio (la mediana del suelo bajo él). */
    public int baseHeight(Build out, Terrain t, int ox, int oz, int rot) {
        List<Integer> hs = new ArrayList<>();
        for (int x = 0; x < worldW(rot); x++) for (int z = 0; z < worldD(rot); z++) hs.add(out.ground(t, ox + x, oz + z));
        hs.sort(Integer::compare);
        return hs.get(hs.size() / 2) + sink;
    }

    /** Diferencia de altura del terreno bajo el edificio, y si toca agua (-1). */
    public static int slope(Build out, Terrain t, int x1, int z1, int x2, int z2) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                if (t.water(x, z) && !out.groundOverride.containsKey(Build.col(x, z))) return -1;
                int g = out.ground(t, x, z);
                min = Math.min(min, g);
                max = Math.max(max, g);
            }
        return max - min;
    }

    /**
     * Pone el edificio con su esquina (rotada) en ox, oz. Devuelve la altura del piso.
     * Debajo: cimientos de piedra (o pilotes si queda muy alto); arriba: se excava la ladera.
     */
    public int stamp(Build out, Terrain t, int ox, int oz, int rot, Style s, Random r) {
        int base = baseHeight(out, t, ox, oz, rot);
        return stampAt(out, t, ox, oz, base, rot, s, r);
    }

    public int stampAt(Build out, Terrain t, int ox, int oz, int base, int rot, Style s, Random r) {
        int W = worldW(rot), D = worldD(rot), m = margin;
        String top = s.id.equals("desierto") ? "sand" : s.id.equals("nieve") ? "snow_block" : s.id.equals("pantano") ? "mud" : "grass_block";
        // 1) terreno: nivelar solo un borde pequeño alrededor, cimientos bajo el edificio
        for (int x = -m; x < W + m; x++)
            for (int z = -m; z < D + m; z++) {
                int wx = ox + x, wz = oz + z;
                boolean inside = x >= 0 && z >= 0 && x < W && z < D;
                int g = out.ground(t, wx, wz);
                String surf = t.surface(wx, wz);
                String topHere = surf == null || surf.contains("water") || surf.contains("ice") ? top : surf.equals("dirt_path") ? top : surf;
                if (!noClear) for (int y = base + 1; y <= base + height + 2; y++) out.set(wx, y, wz, "air");
                if (inside) {
                    int depth = base - g;
                    boolean post = stilts && depth > 2;
                    boolean edge = x == 0 || z == 0 || x == W - 1 || z == D - 1;
                    boolean corner = (x == 0 || x == W - 1) && (z == 0 || z == D - 1);
                    for (int y = g + 1; y < base; y++) {
                        if (!post) out.set(wx, y, wz, s.foundationMix(r));
                        else if (corner || (edge && (x + z) % 3 == 0)) out.set(wx, y, wz, s.logY());
                    }
                    if (g >= base && !noClear) for (int y = base; y <= g; y++) out.set(wx, y, wz, "air");
                } else if (!noClear && Math.abs(g - base) <= 6) {
                    for (int y = g + 1; y < base; y++) out.set(wx, y, wz, "dirt");
                    if (g >= base) for (int y = base; y <= g; y++) out.set(wx, y, wz, "air");
                    out.set(wx, base, wz, s.id.equals("desierto") ? "sand" : topHere.contains("_path") ? top : topHere);
                    // los bordes que quedaron altos o bajos se suavizan con un escalón
                    out.setGround(wx, wz, base);
                }
                if (inside) out.setGround(wx, wz, base);
            }
        // 2) el edificio
        for (Map.Entry<Long, String> e : blocks.entrySet()) {
            long kk = e.getKey();
            int lx = Build.kx(kk), ly = Build.ky(kk), lz = Build.kz(kk);
            int[] p = toWorld(lx, lz, rot, ox, oz);
            out.set(p[0], base + ly, p[1], rotate(e.getValue(), rot));
        }
        for (Build.Marker mk : markers) {
            int[] p = toWorld(mk.x(), mk.z(), rot, ox, oz);
            out.mark(mk.type(), p[0], base + mk.y(), p[1], mk.arg(), mk.yaw() + rot * 90f);
        }
        return base;
    }
}
