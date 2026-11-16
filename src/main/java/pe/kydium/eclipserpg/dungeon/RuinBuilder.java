package pe.kydium.eclipserpg.dungeon;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Diseña ruinas corruptas pequeñas (caben dentro de un chunk, ±6 bloques del centro).
 * Coordenadas relativas: y = 0 es el nivel del suelo.
 */
public final class RuinBuilder {

    public enum Variant { TOWER, CIRCLE, CRYPT }

    public static final class Result {
        public final Map<String, String> blocks = new LinkedHashMap<>(); // "x,y,z" -> estado
        public int chestX, chestY, chestZ;
        public int altarX, altarY, altarZ;
        public Variant variant;
    }

    private final Random r;
    private final List<String> floor;
    private final List<String> wall;
    private final String accent, stairs, wallBlock;
    private Result res;

    public RuinBuilder(long seed, boolean desert) {
        this.r = new Random(seed);
        if (desert) {
            floor = List.of("sandstone", "cut_sandstone", "smooth_sandstone");
            wall = List.of("sandstone", "cut_sandstone", "chiseled_sandstone");
            accent = "chiseled_sandstone";
            stairs = "sandstone_stairs";
            wallBlock = "sandstone_wall";
        } else {
            floor = List.of("mossy_stone_bricks", "cracked_stone_bricks", "stone_bricks", "mossy_cobblestone");
            wall = List.of("mossy_stone_bricks", "cracked_stone_bricks", "stone_bricks", "mossy_cobblestone", "cobblestone");
            accent = "chiseled_stone_bricks";
            stairs = "stone_brick_stairs";
            wallBlock = "mossy_stone_brick_wall";
        }
    }

    private void set(int x, int y, int z, String b) {
        res.blocks.put(x + "," + y + "," + z, b);
    }

    private String f() {
        return floor.get(r.nextInt(floor.size()));
    }

    private String w() {
        return wall.get(r.nextInt(wall.size()));
    }

    public Result build(Variant v) {
        res = new Result();
        res.variant = v;
        switch (v) {
            case TOWER -> tower();
            case CIRCLE -> circle();
            case CRYPT -> crypt();
        }
        return res;
    }

    private void base(int rad) {
        for (int x = -rad; x <= rad; x++)
            for (int z = -rad; z <= rad; z++) {
                if (x * x + z * z > rad * rad + 1) continue;
                set(x, 0, z, r.nextDouble() < 0.85 ? f() : "coarse_dirt");
                for (int y = 1; y <= 12; y++) set(x, y, z, "air");
            }
    }

    private void stains(int n, int rad) {
        for (int i = 0; i < n; i++) {
            int x = r.nextInt(rad * 2 + 1) - rad, z = r.nextInt(rad * 2 + 1) - rad;
            set(x, 0, z, "nether_wart_block");
        }
    }

    private void bones(int n, int rad) {
        for (int i = 0; i < n; i++) {
            int x = r.nextInt(rad * 2 + 1) - rad, z = r.nextInt(rad * 2 + 1) - rad;
            if (Math.abs(x) <= 1 && Math.abs(z) <= 1) continue;
            String b = switch (r.nextInt(3)) {
                case 0 -> "skeleton_skull[rotation=" + r.nextInt(16) + "]";
                case 1 -> "bone_block[axis=x]";
                default -> "cobweb";
            };
            set(x, 1, z, b);
        }
    }

    /** Torre en ruinas de 7x7 con la parte de arriba rota. */
    private void tower() {
        base(5);
        int h = 9 + r.nextInt(4);
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                boolean edge = Math.abs(x) == 3 || Math.abs(z) == 3;
                boolean corner = Math.abs(x) == 3 && Math.abs(z) == 3;
                if (!edge) continue;
                // altura irregular: la torre está rota
                int top = corner ? h : h - r.nextInt(6);
                for (int y = 1; y <= top; y++) {
                    boolean window = (x == 0 || z == 0) && (y == 4 || y == 8);
                    boolean door = x == 0 && z == -3 && y <= 2;
                    if (door) continue;
                    set(x, y, z, window ? "iron_bars" : (corner ? accent : w()));
                }
            }
        }
        // piso intermedio roto
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++)
                if (r.nextDouble() < 0.6) set(x, 5, z, f());
        set(0, 1, 2, accent);
        set(0, 2, 2, "crying_obsidian");
        set(0, 3, 2, "soul_lantern");
        set(1, 1, 1, "chest[facing=north]");
        res.chestX = 1; res.chestY = 1; res.chestZ = 1;
        res.altarX = 0; res.altarY = 2; res.altarZ = 2;
        set(-2, 4, -2, "iron_chain");
        set(-2, 3, -2, "soul_lantern[hanging=true]");
        bones(5, 2);
        stains(4, 4);
        // escombros caídos alrededor
        for (int i = 0; i < 10; i++) {
            int x = r.nextInt(11) - 5, z = r.nextInt(11) - 5;
            if (Math.abs(x) <= 3 && Math.abs(z) <= 3) continue;
            set(x, 1, z, r.nextBoolean() ? w() : stairs + "[facing=" + (r.nextBoolean() ? "north" : "east") + "]");
        }
    }

    /** Círculo de pilares de obsidiana con un altar corrupto en el centro. */
    private void circle() {
        base(6);
        for (int x = -6; x <= 6; x++)
            for (int z = -6; z <= 6; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (Math.abs(d - 4.5) < 0.5) set(x, 0, z, accent);
                if (d < 1.6) set(x, 0, z, "crying_obsidian");
            }
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            int px = (int) Math.round(Math.cos(a) * 5), pz = (int) Math.round(Math.sin(a) * 5);
            int h = 3 + r.nextInt(5);
            for (int y = 1; y <= h; y++) set(px, y, pz, r.nextDouble() < 0.25 ? "crying_obsidian" : "obsidian");
            if (r.nextBoolean()) set(px, h + 1, pz, "soul_campfire[lit=true]");
            else set(px, h + 1, pz, "iron_bars");
        }
        set(0, 1, 0, accent);
        set(0, 2, 0, "crying_obsidian");
        set(0, 3, 0, "soul_campfire[lit=true]");
        set(1, 1, 0, "chest[facing=west]");
        res.chestX = 1; res.chestY = 1; res.chestZ = 0;
        res.altarX = 0; res.altarY = 2; res.altarZ = 0;
        for (int[] c : new int[][]{{2, 2}, {-2, 2}, {2, -2}, {-2, -2}}) set(c[0], 1, c[1], "candle[candles=" + (1 + r.nextInt(4)) + ",lit=true]");
        bones(6, 4);
        stains(6, 4);
    }

    /** Mausoleo con escalera hacia una cripta pequeña bajo tierra. */
    private void crypt() {
        base(5);
        // mausoleo de 7x7
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                boolean edge = Math.abs(x) == 3 || Math.abs(z) == 3;
                if (edge) {
                    for (int y = 1; y <= 4; y++) {
                        boolean door = z == -3 && Math.abs(x) <= 1 && y <= 3;
                        if (door) continue;
                        set(x, y, z, (Math.abs(x) == 3 && Math.abs(z) == 3) ? accent : w());
                    }
                }
                set(x, 5, z, r.nextDouble() < 0.85 ? stairs.replace("_stairs", "_slab") + "[type=bottom]" : "air");
            }
        }
        for (int x = -1; x <= 1; x++) set(x, 4, -3, accent);
        set(-2, 1, -4, "skeleton_skull[rotation=8]");
        set(2, 1, -4, "skeleton_skull[rotation=8]");
        // interior: ataúdes, velas y el cofre al fondo
        for (int x : new int[]{-2, 2}) {
            for (int z = -1; z <= 1; z++) set(x, 1, z, stairs.replace("_stairs", "_slab") + "[type=bottom]");
            set(x, 2, 0, "candle[candles=" + (1 + r.nextInt(4)) + ",lit=true]");
        }
        set(0, 1, 2, accent);
        set(0, 2, 2, "crying_obsidian");
        set(0, 3, 2, "soul_lantern");
        res.altarX = 0; res.altarY = 2; res.altarZ = 2;
        set(1, 1, 2, "chest[facing=north]");
        res.chestX = 1; res.chestY = 1; res.chestZ = 2;
        set(0, 4, 0, "iron_chain");
        set(0, 3, 0, "soul_lantern[hanging=true]");
        // lápidas alrededor
        for (int i = 0; i < 6; i++) {
            int x = r.nextInt(11) - 5, z = r.nextInt(11) - 5;
            if (Math.abs(x) <= 4 && Math.abs(z) <= 4) continue;
            set(x, 1, z, wallBlock);
            if (r.nextBoolean()) set(x, 2, z, "skeleton_skull[rotation=" + r.nextInt(16) + "]");
        }
        bones(4, 2);
        stains(3, 2);
    }
}
