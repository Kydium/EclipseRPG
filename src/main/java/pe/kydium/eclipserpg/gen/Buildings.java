package pe.kydium.eclipserpg.gen;

import java.util.Random;

/**
 * Edificios de aldea. Cada uno sale distinto con cada semilla (medidas, ventanas, muebles, chimenea…).
 * Convención: la puerta está en la pared de z = 0 (el frente), mirando al camino.
 */
public final class Buildings {

    private Buildings() {}

    private static int between(Random r, int a, int b) {
        return a + r.nextInt(b - a + 1);
    }

    private static String pick(Random r, String... o) {
        return o[r.nextInt(o.length)];
    }

    private static final String[] BEDS = {"red", "blue", "green", "yellow", "white", "light_blue", "orange", "brown", "cyan", "lime"};
    private static final String[] POTS = {"potted_poppy", "potted_dandelion", "potted_blue_orchid", "potted_allium", "potted_oxeye_daisy",
            "potted_cornflower", "potted_red_tulip", "potted_fern", "potted_azalea_bush"};

    /** Techo según el estilo: dos aguas o plano. Devuelve la altura de la cumbre. */
    private static int roof(Canvas c, int x1, int z1, int x2, int z2, int y, Style s, Random r) {
        if (s.flatRoofs) {
            c.flatRoof(x1, z1, x2, z2, y, s, false);
            return y + 1;
        }
        boolean alongX = (x2 - x1) >= (z2 - z1);
        return c.gable(x1, z1, x2, z2, y, s, 1, alongX);
    }

    /** Mesa: valla + placa (o losa en desierto). */
    private static void table(Canvas c, int x, int y, int z, Style s) {
        c.set(x, y, z, s.fence);
        c.set(x, y + 1, z, s.planks.replace("_planks", "_pressure_plate"));
    }

    private static void chair(Canvas c, int x, int y, int z, String facing, Style s) {
        c.set(x, y, z, s.woodStairs(facing));
    }

    private static void wallLight(Canvas c, int x, int y, int z, String facing, Style s) {
        c.set(x, y, z, s.light.equals("soul_lantern") ? "soul_wall_torch[facing=" + facing + "]" : "wall_torch[facing=" + facing + "]");
    }

    /** Macetas o jardineras debajo de las ventanas del frente (por fuera, sobre el borde). */
    private static void flowerBoxes(Canvas c, int x1, int x2, int y, Style s, Random r) {
        for (int x = x1 + 1; x < x2; x++) {
            String b = c.get(x, y, 0);
            if (b != null && b.contains("pane") && r.nextBoolean()) c.set(x, y - 1, -1, s.trapdoor + "[facing=north,half=top,open=false]");
        }
    }

    // ------------------------------------------------------------------ casas

    /** Casa común. size 0 = chica, 1 = mediana, 2 = grande (dos pisos). */
    public static Canvas house(Style s, Random r, int size) {
        int w = size == 0 ? between(r, 7, 8) : size == 1 ? between(r, 9, 11) : between(r, 10, 12);
        int d = size == 0 ? 7 : size == 1 ? between(r, 7, 8) : between(r, 8, 9);
        int h = size == 0 ? 4 : 4;
        Canvas c = new Canvas(w, d);
        c.room(0, 0, w - 1, d - 1, 0, h, s, r, true);
        int top = h;
        if (size == 2) {
            // segundo piso
            c.fill(1, h, 1, w - 2, h, d - 2, s.floor);
            c.room(0, 0, w - 1, d - 1, h, h, s, r, true);
            c.fill(1, h + 1, 1, w - 2, h + h - 1, d - 2, "air");
            top = h * 2;
            // escalera interior (escalones de madera pegados al fondo)
            for (int i = 0; i < h; i++) {
                c.set(1 + i, 1 + i, d - 2, s.woodStairs("east"));
                c.set(1 + i, h, d - 2, "air");
            }
            c.set(1 + h, h, d - 2, "air");
            c.windows(0, 0, w - 1, d - 1, h + 2, 3, s, false);
            c.bed(w - 3, h + 1, 2, "south", pick(r, BEDS));
            if (w >= 11) c.bed(w - 5, h + 1, 2, "south", pick(r, BEDS));
            c.set(2, h + 1, 1, "chest[facing=south]");
            c.set(w - 2, h + 1, d - 2, pick(r, POTS));
            wallLight(c, w / 2, h + 3, 1, "south", s);
        }
        int door = w / 2;
        c.door(door, 1, 0, "south", s.door);
        c.windows(0, 0, w - 1, d - 1, 2, size == 0 ? 3 : 2, s, false);
        // muebles del piso de abajo
        if (size < 2) {
            c.bed(w - 2, 1, d - 3, "south", pick(r, BEDS));
            if (size == 1 && r.nextBoolean()) c.bed(w - 3, 1, d - 3, "south", pick(r, BEDS));
        }
        c.set(1, 1, d - 2, "crafting_table");
        c.set(2, 1, d - 2, "furnace[facing=north]");
        c.set(1, 1, 1, r.nextBoolean() ? "barrel[facing=up]" : "chest[facing=east]");
        if (w > 8) {
            table(c, w / 2, 1, d / 2, s);
            chair(c, w / 2 - 1, 1, d / 2, "east", s);
            c.set(w / 2, 2, d / 2, pick(r, POTS));
            c.set(w / 2, 1, d / 2, s.fence);
        }
        c.set(1, 1, d / 2, "composter");
        wallLight(c, door - 1 < 1 ? 1 : door - 1, 3, 1, "south", s);
        wallLight(c, w - 2, 3, d - 2, "north", s);
        for (int x = 2; x < w - 2; x += 2) if (r.nextInt(3) == 0) c.set(x, 1, 2, s.bed.equals("red") ? "red_carpet" : pick(r, BEDS) + "_carpet");
        // chimenea
        if (!s.flatRoofs && r.nextInt(3) > 0) c.chimney(0 == r.nextInt(2) ? 1 : w - 2, d - 1, top + 1, top + (d / 2) + 3, s);
        roof(c, 0, 0, w - 1, d - 1, top + 1, s, r);
        flowerBoxes(c, 0, w - 1, 2, s, r);
        // farol en la puerta
        c.set(door + 1, 3, -1, s.light.equals("soul_lantern") ? "soul_wall_torch[facing=north]" : "wall_torch[facing=north]");
        c.mark("villager", door, 1, 2, "", 0);
        return c;
    }

    // ------------------------------------------------------------------ edificios con NPC

    public static Canvas mayor(Style s, Random r) {
        Canvas c = house(s, r, 2);
        int w = c.w, d = c.d;
        // jardín y estandarte
        c.set(w / 2 - 2, 1, -1, s.flower1);
        c.set(w / 2 + 2, 1, -1, s.flower2);
        for (int y = 1; y <= 4; y++) c.set(w - 1, y, -1, y == 4 ? "lantern" : s.fence);
        for (int y = 1; y <= 4; y++) c.set(0, y, -1, y == 4 ? "lantern" : s.fence);
        c.set(w / 2, 3, 1, "white_wall_banner[facing=south]");
        c.mark("npc", w / 2 + 1, 1, 2, "casa", 180);
        return c;
    }

    public static Canvas barracks(Style s, Random r) {
        int w = between(r, 11, 13), d = 9, h = 5;
        Canvas c = new Canvas(w, d);
        Style st = s;
        c.fill(0, 0, 0, w - 1, 0, d - 1, s.stone);
        c.fill(1, 1, 1, w - 2, h, d - 2, "air");
        for (int y = 1; y <= h; y++)
            for (int x = 0; x < w; x++)
                for (int z = 0; z < d; z++)
                    if (x == 0 || z == 0 || x == w - 1 || z == d - 1) c.set(x, y, z, (x == 0 || x == w - 1) && (z == 0 || z == d - 1) ? s.stone2 : s.stoneMix(r));
        for (int x = 2; x < w - 1; x += 3) {
            c.set(x, 3, 0, "iron_bars");
            c.set(x, 3, d - 1, "iron_bars");
        }
        c.door(w / 2, 1, 0, "south", "iron_door");
        c.set(w / 2 + 1, 2, -1, "stone_button[face=wall,facing=north]");
        c.set(w / 2 - 1, 2, 1, "stone_button[face=wall,facing=south]");
        for (int x = 1; x < w - 1; x += 2) c.bed(x, 1, d - 3, "south", "red");
        c.set(1, 1, 1, "anvil[facing=east]");
        c.set(w - 2, 1, 1, "barrel[facing=up]");
        c.set(w - 2, 2, 1, "barrel[facing=up]");
        c.set(1, 1, 2, "smithing_table");
        c.set(w - 2, 1, 2, "fletching_table");
        wallLight(c, 2, 3, 1, "south", s);
        wallLight(c, w - 3, 3, 1, "south", s);
        c.flatRoof(0, 0, w - 1, d - 1, h + 1, st, true);
        c.set(w / 2, h + 2, d / 2, "lantern");
        for (int y = h + 2; y <= h + 6; y++) c.set(1, y, 1, y == h + 6 ? "red_wool" : s.fence);
        c.set(2, h + 6, 1, "red_wool");
        c.mark("npc", w / 2, 1, 2, "cuartel", 180);
        c.mark("villager", w / 2 + 2, 1, 4, "guard", 0);
        return c;
    }

    public static Canvas smithy(Style s, Random r) {
        int w = between(r, 10, 11), d = 9, h = 4;
        Canvas c = new Canvas(w, d);
        // cuarto del fondo
        c.room(0, 4, w - 1, d - 1, 0, h, s, r, true);
        c.fill(0, 0, 0, w - 1, 0, 3, s.stone);
        // frente abierto con postes
        for (int x : new int[]{0, w - 1}) for (int y = 1; y <= h; y++) c.set(x, y, 0, s.logY());
        for (int x = 0; x < w; x++) c.set(x, h, 0, s.logX());
        for (int z = 1; z < 4; z++) {
            c.set(0, h, z, s.logZ());
            c.set(w - 1, h, z, s.logZ());
        }
        c.fill(1, 1, 4, w - 2, 3, 4, "air");
        c.set(1, 1, 4, s.wall);
        c.set(w - 2, 1, 4, s.wall);
        c.set(2, 1, 1, "anvil[facing=east]");
        c.set(4, 1, 2, "grindstone[face=floor,facing=north]");
        c.set(w - 2, 1, 1, "smithing_table");
        c.set(w - 2, 1, 2, "blast_furnace[facing=west,lit=true]");
        c.set(w - 3, 1, 5, "lava_cauldron");
        c.set(w - 2, 1, 5, "blast_furnace[facing=west,lit=true]");
        c.set(1, 1, 7, "chest[facing=east]");
        c.set(1, 1, 6, "barrel[facing=up]");
        c.set(w - 2, 1, 7, "stonecutter[facing=west]");
        c.bed(3, 1, 7, "east", pick(r, BEDS));
        c.set(1, 2, 2, "lantern");
        c.set(1, 1, 2, s.stone);
        c.chimney(w - 2, d - 1, h + 1, h + 7, s);
        roof(c, 0, 0, w - 1, d - 1, h + 1, s, r);
        c.mark("npc", w / 2, 1, 2, "herreria", 180);
        return c;
    }

    public static Canvas shop(Style s, Random r) {
        int w = between(r, 9, 10), d = 8, h = 4;
        Canvas c = new Canvas(w, d);
        c.room(0, 0, w - 1, d - 1, 0, h, s, r, true);
        c.door(1, 1, 0, "south", s.door);
        // mostrador abierto al frente
        for (int x = 3; x < w - 1; x++) {
            c.set(x, 2, 0, "air");
            c.set(x, 1, 0, s.planks.replace("_planks", "_slab[type=top]"));
            c.set(x, 1, 1, "barrel[facing=up]");
        }
        // toldo a rayas
        for (int x = 2; x < w; x++) {
            c.set(x, h, -1, (x % 2 == 0 ? s.accent : s.accent2));
            c.set(x, h - 1, -2, (x % 2 == 0 ? s.accent : s.accent2).replace("_wool", "_carpet"));
        }
        for (int z = 3; z < d - 1; z++) {
            c.set(1, 1, z, "chest[facing=east]");
            c.set(w - 2, 1, z, "barrel[facing=up]");
            c.set(w - 2, 2, z, "barrel[facing=up]");
        }
        c.set(w / 2, 1, d - 2, "crafting_table");
        c.set(w / 2 + 1, 1, d - 2, "loom[facing=north]");
        wallLight(c, w / 2, 3, d - 2, "north", s);
        roof(c, 0, 0, w - 1, d - 1, h + 1, s, r);
        c.mark("npc", w / 2, 1, 2, "tienda", 180);
        return c;
    }

    public static Canvas library(Style s, Random r) {
        int w = between(r, 11, 12), d = between(r, 9, 10), h = 6;
        Canvas c = new Canvas(w, d);
        c.room(0, 0, w - 1, d - 1, 0, h, s, r, true);
        c.door(w / 2, 1, 0, "south", s.door);
        for (int x = 2; x <= w - 3; x += 3) {
            c.set(x, 3, 0, s.window);
            c.set(x, 4, 0, s.window);
        }
        for (int z = 1; z < d - 1; z++) {
            for (int y = 1; y <= 3; y++) {
                c.set(1, y, z, "bookshelf");
                c.set(w - 2, y, z, "bookshelf");
            }
        }
        for (int x = 2; x < w - 2; x++) for (int y = 1; y <= 3; y++) c.set(x, y, d - 2, x == w / 2 && y == 1 ? "lectern[facing=north]" : "bookshelf");
        c.set(w / 2, 1, d / 2, "enchanting_table");
        c.set(w / 2 - 2, 1, d / 2, s.woodStairs("east"));
        c.set(w / 2 + 2, 1, d / 2, s.woodStairs("west"));
        c.set(w / 2, 4, d / 2, "iron_chain[axis=y]");
        c.set(w / 2, 5, d / 2, "iron_chain[axis=y]");
        c.set(w / 2, 3, d / 2, "lantern[hanging=true]");
        c.set(w / 2, 6, d / 2, s.planks);
        roof(c, 0, 0, w - 1, d - 1, h + 1, s, r);
        c.mark("npc", w / 2 + 1, 1, 2, "biblioteca", 180);
        return c;
    }

    public static Canvas cartographer(Style s, Random r) {
        Canvas c = house(s, r, 0);
        int w = c.w, d = c.d;
        c.set(1, 1, d - 2, "cartography_table");
        c.set(2, 1, d - 2, "chest[facing=north]");
        c.set(w / 2 - 1, 3, d - 1, "air");
        c.set(w / 2 - 1, 2, d - 2, "blue_wall_banner[facing=north]");
        c.set(2, 2, d - 2, "air");
        c.mark("npc", w / 2, 1, 2, "mapas", 180);
        c.markers.removeIf(m -> m.type().equals("villager"));
        return c;
    }

    public static Canvas watchtower(Style s, Random r) {
        int h = between(r, 11, 14);
        Canvas c = new Canvas(5, 5);
        c.margin = 2;
        for (int y = 0; y <= h; y++)
            for (int x = 0; x < 5; x++)
                for (int z = 0; z < 5; z++) {
                    boolean edge = x == 0 || z == 0 || x == 4 || z == 4;
                    boolean corner = (x == 0 || x == 4) && (z == 0 || z == 4);
                    if (y == 0) c.set(x, y, z, s.stone);
                    else if (corner) c.set(x, y, z, y < 5 ? s.stone : s.logY());
                    else if (edge) c.set(x, y, z, y < 5 ? s.stoneMix(r) : (y % 4 == 2 && (x == 2 || z == 2) ? s.window : s.planks));
                    else c.set(x, y, z, "air");
                }
        c.door(2, 1, 0, "south", s.door);
        for (int y = 1; y <= h; y++) c.set(2, y, 3, "ladder[facing=north]");
        // plataforma con baranda
        c.fill(-1, h + 1, -1, 5, h + 1, 5, s.planks);
        c.set(2, h + 1, 3, "air");
        c.set(2, h + 1, 3, s.trapdoor + "[facing=north,half=top,open=false]");
        for (int x = -1; x <= 5; x++) {
            c.set(x, h + 2, -1, s.fence);
            c.set(x, h + 2, 5, s.fence);
        }
        for (int z = -1; z <= 5; z++) {
            c.set(-1, h + 2, z, s.fence);
            c.set(5, h + 2, z, s.fence);
        }
        for (int[] p : new int[][]{{-1, -1}, {5, -1}, {-1, 5}, {5, 5}}) {
            c.set(p[0], h + 3, p[1], s.fence);
            c.set(p[0], h + 4, p[1], s.logY());
        }
        c.gable(-1, -1, 5, 5, h + 5, s, 0, true);
        c.set(2, h + 2, 2, "lantern");
        c.mark("npc", 2, 1, -2, "torre", 0);
        c.mark("villager", 2, h + 2, 1, "guard", 0);
        return c;
    }

    public static Canvas chapel(Style s, Random r) {
        int w = 9, d = between(r, 13, 15), h = 7;
        Canvas c = new Canvas(w, d);
        c.room(0, 0, w - 1, d - 1, 0, h, s, r, true);
        // campanario al frente
        for (int y = 1; y <= h + 7; y++)
            for (int x = 2; x <= 6; x++)
                for (int z = 0; z <= 3; z++) {
                    boolean edge = x == 2 || x == 6 || z == 0 || z == 3;
                    if (edge && y <= h + 4) c.set(x, y, z, (x == 2 || x == 6) && (z == 0 || z == 3) ? s.logY() : s.stoneMix(r));
                }
        for (int x = 3; x <= 5; x++) for (int z = 1; z <= 2; z++) c.set(x, h + 2, z, s.floor);
        c.fill(3, h + 3, 0, 5, h + 4, 0, "air");
        c.fill(3, h + 3, 3, 5, h + 4, 3, "air");
        c.fill(2, h + 3, 1, 2, h + 4, 2, "air");
        c.fill(6, h + 3, 1, 6, h + 4, 2, "air");
        c.set(4, h + 4, 1, "bell[attachment=ceiling,facing=north]");
        c.set(4, h + 5, 1, s.planks);
        c.gable(2, 0, 6, 3, h + 5, s, 0, false);
        c.door(4, 1, 0, "south", s.door);
        c.set(4, 3, 0, "air");
        c.set(4, 3, 0, s.window);
        for (int z = 5; z < d - 2; z += 2) {
            c.set(0, 3, z, "white_stained_glass_pane");
            c.set(0, 4, z, "yellow_stained_glass_pane");
            c.set(w - 1, 3, z, "white_stained_glass_pane");
            c.set(w - 1, 4, z, "yellow_stained_glass_pane");
        }
        // bancos y altar
        for (int z = 5; z < d - 4; z += 2) {
            c.set(2, 1, z, s.woodStairs("north"));
            c.set(3, 1, z, s.woodStairs("north"));
            c.set(5, 1, z, s.woodStairs("north"));
            c.set(6, 1, z, s.woodStairs("north"));
        }
        c.fill(2, 1, d - 3, 6, 1, d - 2, "white_carpet");
        c.set(4, 1, d - 2, "brewing_stand");
        c.set(3, 1, d - 2, "candle[candles=3,lit=true]");
        c.set(5, 1, d - 2, "candle[candles=3,lit=true]");
        wallLight(c, 1, 4, 6, "east", s);
        wallLight(c, w - 2, 4, 6, "west", s);
        c.gable(0, 4, w - 1, d - 1, h + 1, s, 1, false);
        c.mark("villager", 4, 1, d - 4, "cleric", 0);
        return c;
    }

    public static Canvas tavern(Style s, Random r) {
        Canvas c = house(s, r, 2);
        int w = c.w, d = c.d;
        // mesas
        for (int x = 2; x < w - 2; x += 3) {
            table(c, x, 1, 3, s);
            chair(c, x - 1, 1, 3, "east", s);
        }
        c.set(w - 2, 1, 1, "barrel[facing=up]");
        c.set(w - 2, 2, 1, "barrel[facing=up]");
        c.set(w - 3, 1, 1, "barrel[facing=up]");
        c.set(w / 2, 3, -1, "oak_hanging_sign[attached=false,rotation=0]");
        c.set(w / 2, 4, -1, "iron_chain[axis=y]");
        c.mark("villager", 2, 1, 4, "", 0);
        return c;
    }

    public static Canvas blackMarket(Style s, Random r) {
        Style dark = Style.get("oscuro");
        int w = 6, d = 6, h = 4;
        Canvas c = new Canvas(w, d);
        c.room(0, 0, w - 1, d - 1, 0, h, dark, r, true);
        c.fill(0, 0, 0, w - 1, 0, d - 1, "dark_oak_planks");
        for (int z = 1; z < d - 1; z++) c.set(1, 1, z, "chest[facing=east]");
        c.set(w - 2, 1, d - 2, "barrel[facing=up]");
        c.set(w - 2, 1, 1, "cobweb");
        c.set(w - 2, 3, d - 2, "cobweb");
        c.fill(2, 1, 2, 3, 1, 3, "purple_carpet");
        c.set(3, 3, 3, "soul_lantern[hanging=true]");
        c.set(3, 4, 3, "iron_chain[axis=y]");
        c.door(2, 1, 0, "south", "dark_oak_door");
        c.set(3, 2, 0, "iron_bars");
        c.set(3, 3, -1, "soul_wall_torch[facing=north]");
        c.gable(0, 0, w - 1, d - 1, h + 1, dark, 1, true);
        c.mark("npc", 3, 1, 2, "tienda_negra", 180);
        return c;
    }

    public static Canvas farm(Style s, Random r) {
        int w = between(r, 11, 13), d = between(r, 9, 11);
        Canvas c = new Canvas(w, d);
        c.margin = 1;
        String crop2 = pick(r, "wheat[age=7]", "carrots[age=7]", "potatoes[age=7]", "beetroots[age=3]");
        String crop1 = s.crop.equals("melon_stem") ? "melon_stem[age=7]" : s.crop + "[age=" + (s.crop.equals("beetroots") ? 3 : 7) + "]";
        for (int x = 0; x < w; x++)
            for (int z = 0; z < d; z++) {
                boolean edge = x == 0 || z == 0 || x == w - 1 || z == d - 1;
                if (edge) {
                    c.set(x, 0, z, s.id.equals("desierto") ? "sandstone" : "grass_block");
                    c.set(x, 1, z, x == w / 2 && z == 0 ? s.gate + "[facing=south,open=false]" : s.fence);
                } else if (x == w / 2) {
                    c.set(x, 0, z, "water");
                } else {
                    c.set(x, 0, z, "farmland[moisture=7]");
                    c.set(x, 1, z, x < w / 2 ? crop1 : crop2);
                }
            }
        c.set(w / 2, 1, d / 2, s.planks.replace("_planks", "_slab[type=bottom,waterlogged=false]"));
        c.set(w / 2, 0, d / 2, "water");
        c.set(w - 1, 2, 0, "lantern");
        c.set(0, 2, 0, "lantern");
        c.set(1, 0, -1, "composter");
        c.set(2, 0, -1, "hay_block");
        c.mark("npc", w / 2 + 1, 0, -1, "granja", 180);
        c.mark("villager", w / 2 - 2, 1, 2, "farmer", 0);
        return c;
    }

    public static Canvas pen(Style s, Random r) {
        int w = between(r, 8, 10), d = between(r, 7, 9);
        Canvas c = new Canvas(w, d);
        for (int x = 0; x < w; x++)
            for (int z = 0; z < d; z++) {
                boolean edge = x == 0 || z == 0 || x == w - 1 || z == d - 1;
                c.set(x, 0, z, s.id.equals("desierto") ? "coarse_dirt" : r.nextInt(5) == 0 ? "coarse_dirt" : "grass_block");
                if (edge) c.set(x, 1, z, x == w / 2 && z == 0 ? s.gate + "[facing=south,open=false]" : s.fence);
            }
        c.set(1, 1, d - 2, "hay_block");
        c.set(2, 1, d - 2, "hay_block");
        c.set(1, 2, d - 2, "hay_block");
        c.set(w - 2, 1, d - 2, "water_cauldron[level=3]");
        String[] animals = {"cow", "sheep", "pig", "chicken"};
        String a = pick(r, animals), b = pick(r, animals);
        for (int i = 0; i < 4; i++) c.mark("animal", 2 + i % (w - 4), 1, 2 + i / 2, i < 2 ? a : b, 0);
        return c;
    }

    public static Canvas stall(Style s, Random r) {
        Canvas c = new Canvas(3, 3);
        c.margin = 0;
        String a = pick(r, "red_wool", "yellow_wool", "blue_wool", "green_wool", "orange_wool", "purple_wool", "cyan_wool");
        for (int[] p : new int[][]{{0, 0}, {2, 0}, {0, 2}, {2, 2}}) {
            c.set(p[0], 1, p[1], s.fence);
            c.set(p[0], 2, p[1], s.fence);
        }
        c.fill(0, 3, 0, 2, 3, 2, a);
        c.set(1, 1, 0, s.planks.replace("_planks", "_slab[type=top]"));
        c.set(1, 2, 0, pick(r, "melon", "pumpkin", "hay_block", "barrel[facing=up]", "cake"));
        c.set(1, 1, 2, "barrel[facing=up]");
        c.set(1, 1, 1, "air");
        c.mark("villager", 1, 1, 1, pick(r, "farmer", "butcher", "fisherman", "shepherd", "fletcher"), 0);
        return c;
    }

    public static Canvas well(Style s, Random r) {
        Canvas c = new Canvas(4, 4);
        c.margin = 1;
        for (int x = 0; x < 4; x++)
            for (int z = 0; z < 4; z++) {
                boolean edge = x == 0 || z == 0 || x == 3 || z == 3;
                c.set(x, 0, z, edge ? s.stone : "water");
                c.set(x, -1, z, edge ? s.stone : "water");
                c.set(x, -2, z, s.stone);
                if (edge) c.set(x, 1, z, s.stoneSlab + "[type=bottom]");
            }
        for (int[] p : new int[][]{{0, 0}, {3, 0}, {0, 3}, {3, 3}}) {
            c.set(p[0], 1, p[1], s.fence);
            c.set(p[0], 2, p[1], s.fence);
        }
        c.fill(0, 3, 0, 3, 3, 3, s.slab(false));
        return c;
    }

    /** Fuente de la plaza (rígida, con su borde). */
    public static Canvas fountain(Style s, Random r, int size) {
        int n = size >= 2 ? 7 : 5;
        Canvas c = new Canvas(n, n);
        c.margin = 2;
        int m = n / 2;
        for (int x = 0; x < n; x++)
            for (int z = 0; z < n; z++) {
                boolean edge = x == 0 || z == 0 || x == n - 1 || z == n - 1;
                c.set(x, -1, z, s.stone);
                c.set(x, 0, z, edge ? s.stone : "water");
                if (edge) c.set(x, 1, z, s.stoneSlab + "[type=bottom]");
            }
        for (int y = 0; y <= 3; y++) c.set(m, y, m, y == 3 ? s.stoneSlab + "[type=bottom]" : s.stoneWall);
        c.set(m, 2, m, s.stone);
        c.set(m, 4, m, s.light);
        if (size >= 2) for (int[] p : new int[][]{{m - 1, m}, {m + 1, m}, {m, m - 1}, {m, m + 1}}) c.set(p[0], 2, p[1], "water");
        c.mark("center", m, 1, -2, "", 0);
        c.mark("npc", m, 1, -2, "fuente", 0);
        return c;
    }
}
