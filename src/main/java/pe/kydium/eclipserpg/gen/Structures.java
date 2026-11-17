package pe.kydium.eclipserpg.gen;

import java.util.Random;

/**
 * Estructuras sueltas, mazmorras grandes y bases. Todas cambian con cada semilla (medidas, alturas, ruinas,
 * materiales, muebles) y se asientan en el terreno sin deformarse.
 */
public final class Structures {
    private Structures() {}

    static void register() {
        Catalog.register("torre_mago", "Torre del mago", "estructura", "Torre redonda de varios pisos con biblioteca, alquimia y techo cónico.", 12, Structures::wizardTower);
        Catalog.register("molino", "Molino", "estructura", "Molino de viento con aspas, trigo alrededor y almacén.", 14, Structures::windmill);
        Catalog.register("faro", "Faro", "estructura", "Faro de franjas con galería, luz arriba y casita del farero.", 12, Structures::lighthouse);
        Catalog.register("ruinas_castillo", "Ruinas de castillo", "estructura", "Murallas rotas, torres caídas y una torre del homenaje en ruinas con botín.", 22, Structures::castleRuins);
        Catalog.register("templo", "Templo antiguo", "estructura", "Templo con columnas, escalinata y altar con cofre.", 14, Structures::temple);
        Catalog.register("cabana", "Cabaña", "estructura", "Cabaña con porche, chimenea y huerto; sobre pilotes si hay pendiente.", 10, Structures::cabin);
        Catalog.register("granja_aislada", "Granja", "estructura", "Casa, granero grande, campos de cultivo y corral.", 20, Structures::farmstead);
        Catalog.register("cementerio", "Cementerio", "estructura", "Tumbas, rejas y una cripta con escalera a una sala con botín.", 16, Structures::graveyard);
        Catalog.register("campamento", "Campamento de bandidos", "estructura", "Tiendas de lana alrededor de una fogata, cajas y botín.", 12, Structures::camp);
        Catalog.register("posada_camino", "Posada del camino", "estructura", "Posada de dos pisos con establo y pozo.", 16, Structures::inn);
        Catalog.register("fortaleza", "Fortaleza", "mazmorra", "Gran fortaleza con murallas, torres, patio y torre del homenaje con jefe arriba.", 28, Structures::fortress);
        Catalog.register("torre_oscura", "Torre oscura", "mazmorra", "Torre de 5 a 7 pisos con enemigos en cada piso y el jefe en la cima.", 12, Structures::darkTower);
        Catalog.register("cripta_profunda", "Cripta profunda", "mazmorra", "Mausoleo en la superficie y catacumbas con muchas salas bajo tierra.", 22, Structures::deepCrypt);
        Catalog.register("base_inicial", "Base inicial", "base", "Casa de dos pisos amueblada: almacén, cocina, encantamientos, camas y huerto.", 12, Structures::starterBase);
        Catalog.register("base_fortificada", "Base fortificada", "base", "Muralla con torres y portón, casa grande, huerto y corral adentro.", 22, Structures::fortifiedBase);
        Catalog.register("bunker", "Búnker", "base", "Caseta en la superficie y refugio bajo tierra con almacén, dormitorio y granja.", 12, Structures::bunker);
        Catalog.register("base_granja", "Base granja", "base", "Casa, granero, muchos campos y corrales: para vivir del campo.", 22, Structures::farmBase);
    }

    // ------------------------------------------------------------------ utilidades

    private static int between(Random r, int a, int b) {
        return a + r.nextInt(b - a + 1);
    }

    /** Pone un edificio centrado en (x, z). */
    private static int place(Build out, Terrain t, Canvas c, int x, int z, int rot, Style s, Random r) {
        int ox = x - c.worldW(rot) / 2, oz = z - c.worldD(rot) / 2;
        return c.stamp(out, t, ox, oz, rot, s, r);
    }

    private static String stone(Style s, Random r) {
        return s.stoneMix(r);
    }

    private static String ruin(Random r, String a, String b, String c) {
        int n = r.nextInt(10);
        return n < 5 ? a : n < 8 ? b : c;
    }

    /** Torre redonda dentro de un lienzo (centro en cx, cz). */
    private static void roundShell(Canvas c, int cx, int cz, double rad, int y1, int y2, String wall, String inside) {
        for (int x = (int) -Math.ceil(rad); x <= Math.ceil(rad); x++)
            for (int z = (int) -Math.ceil(rad); z <= Math.ceil(rad); z++) {
                double d = Math.hypot(x, z);
                if (d > rad + 0.35) continue;
                boolean shell = d > rad - 0.75;
                for (int y = y1; y <= y2; y++) c.set(cx + x, y, cz + z, shell ? wall : inside);
            }
    }

    private static void cone(Canvas c, int cx, int cz, double rad, int y, String block) {
        double rr = rad + 1;
        while (rr > 0.2) {
            for (int x = (int) -Math.ceil(rr); x <= Math.ceil(rr); x++)
                for (int z = (int) -Math.ceil(rr); z <= Math.ceil(rr); z++)
                    if (Math.hypot(x, z) <= rr + 0.3) c.set(cx + x, y, cz + z, block);
            rr -= 0.75;
            y++;
        }
        c.set(cx, y, cz, "lightning_rod");
    }

    private static void loot(Canvas c, int x, int y, int z, String facing) {
        c.set(x, y, z, "chest[facing=" + facing + "]");
    }

    // ------------------------------------------------------------------ estructuras

    static Build wizardTower(Terrain t, Random r, Style s, int x, int z) {
        Build out = new Build();
        int rad = between(r, 4, 5), floors = between(r, 4, 5), fh = 5;
        int n = rad * 2 + 3, m = n / 2;
        Canvas c = new Canvas(n, n);
        c.margin = 2;
        String wall = r.nextBoolean() ? "stone_bricks" : "deepslate_bricks";
        String accent = r.nextBoolean() ? "purpur_block" : "amethyst_block";
        String roof = r.nextBoolean() ? "purple_terracotta" : "deepslate_tiles";
        int top = floors * fh;
        roundShell(c, m, m, rad, 0, top, wall, "air");
        for (int f = 0; f <= floors; f++) roundShell(c, m, m, rad - 0.6, f * fh, f * fh, "dark_oak_planks", "dark_oak_planks");
        // anillos de color
        for (int f = 1; f <= floors; f++) roundShell(c, m, m, rad, f * fh, f * fh, accent, "dark_oak_planks");
        // escalera de mano por dentro
        for (int y = 1; y <= top; y++) {
            c.set(m, y, m + rad - 1, "ladder[facing=north]");
        }
        // ventanas
        for (int f = 0; f < floors; f++) {
            int y = f * fh + 2;
            c.set(m - rad, y, m, "purple_stained_glass_pane");
            c.set(m + rad, y, m, "purple_stained_glass_pane");
            c.set(m - rad, y + 1, m, "purple_stained_glass_pane");
            c.set(m + rad, y + 1, m, "purple_stained_glass_pane");
            if (f > 0) {
                c.set(m, y, m - rad, "glass_pane");
                c.set(m, y + 1, m - rad, "glass_pane");
            }
        }
        c.door(m, 1, m - rad, "south", "dark_oak_door");
        // pisos
        c.set(m - 2, 1, m, "bookshelf");
        c.set(m + 2, 1, m, "brewing_stand");
        c.set(m + 2, 1, m - 1, "cauldron");
        c.set(m - 1, fh + 1, m - 2, "bookshelf");
        c.set(m, fh + 1, m - 2, "bookshelf");
        c.set(m + 1, fh + 1, m - 2, "bookshelf");
        c.set(m - 1, fh + 2, m - 2, "bookshelf");
        c.set(m + 1, fh + 2, m - 2, "bookshelf");
        c.set(m, fh + 1, m, "lectern[facing=north]");
        c.bed(m - 2, fh * 2 + 1, m - 1, "south", "purple");
        loot(c, m + 2, fh * 2 + 1, m, "west");
        c.set(m, fh * 3 + 1, m, "enchanting_table");
        for (int[] p : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) c.set(m + p[0], fh * 3 + 1, m + p[1], "bookshelf");
        if (floors > 4) loot(c, m - 2, fh * 4 + 1, m, "east");
        for (int f = 0; f < floors; f++) c.set(m, f * fh + fh - 1, m, "iron_chain[axis=y]");
        for (int f = 0; f < floors; f++) c.set(m, f * fh + fh - 2, m, "soul_lantern[hanging=true]");
        // balcón y techo cónico
        for (int a = 0; a < 360; a += 8) {
            int bx = m + (int) Math.round(Math.cos(Math.toRadians(a)) * (rad + 1)), bz = m + (int) Math.round(Math.sin(Math.toRadians(a)) * (rad + 1));
            c.set(bx, top, bz, "dark_oak_slab[type=top]");
            c.set(bx, top + 1, bz, "dark_oak_fence");
        }
        cone(c, m, m, rad, top + 3, roof);
        for (int y = top + 1; y <= top + 2; y++) roundShell(c, m, m, rad, y, y, wall, "air");
        c.set(m, top + 1, m, "amethyst_cluster[facing=up]");
        c.mark("entrada", m, 1, m - rad - 2, "", 0);
        c.mark("enemigo", m + 1, fh + 1, m + 1, "", 0);
        c.mark("enemigo", m - 1, fh * 2 + 1, m + 1, "", 0);
        c.mark("jefe", m, top - fh + 1, m - 1, "", 0);
        place(out, t, c, x, z, r.nextInt(4), s, r);
        return out;
    }

    static Build windmill(Terrain t, Random r, Style s, int x, int z) {
        Build out = new Build();
        int h = between(r, 11, 14);
        Canvas c = new Canvas(9, 9);
        c.margin = 2;
        for (int y = 0; y <= h; y++) {
            int inset = y < 4 ? 0 : (y - 4) / 4 + 1;
            for (int xx = inset; xx < 9 - inset; xx++)
                for (int zz = inset; zz < 9 - inset; zz++) {
                    boolean edge = xx == inset || zz == inset || xx == 8 - inset || zz == 8 - inset;
                    boolean corner = (xx == inset || xx == 8 - inset) && (zz == inset || zz == 8 - inset);
                    if (y == 0) c.set(xx, y, zz, s.foundation);
                    else if (edge) c.set(xx, y, zz, y < 4 ? s.foundationMix(r) : corner ? s.logY() : (s.wall.equals("calcite") ? "white_terracotta" : s.planks));
                    else c.set(xx, y, zz, "air");
                }
            if (y == 4 || y == 9) for (int xx = 1; xx < 8; xx++) for (int zz = 1; zz < 8; zz++) if (c.get(xx, y, zz) != null && c.get(xx, y, zz).equals("air")) c.set(xx, y, zz, s.planks);
        }
        int topIn = (h - 4) / 4 + 1;
        c.gable(topIn, topIn, 8 - topIn, 8 - topIn, h + 1, s, 1, true);
        c.door(4, 1, 0, "south", s.door);
        c.set(2, 2, 0, s.window);
        c.set(6, 2, 0, s.window);
        for (int y = 1; y <= h; y++) c.set(4, y, 7 - Math.min(3, (Math.max(0, y - 4)) / 4 + 0), "ladder[facing=north]");
        c.set(1, 1, 1, "hay_block");
        c.set(1, 2, 1, "hay_block");
        c.set(2, 1, 1, "hay_block");
        c.set(7, 1, 1, "barrel[facing=up]");
        c.set(7, 1, 2, "barrel[facing=up]");
        loot(c, 7, 1, 7, "west");
        // aspas en el frente
        int hubY = h - 2, hz = topIn - 1;
        c.set(4, hubY, hz, s.logZ());
        int len = 6;
        for (int i = 1; i <= len; i++) {
            c.set(4, hubY + i, hz - 1, s.fence);
            c.set(4, hubY - i, hz - 1, s.fence);
            c.set(4 + i, hubY, hz - 1, s.fence);
            c.set(4 - i, hubY, hz - 1, s.fence);
            if (i >= 2) {
                for (int k = 1; k <= 2; k++) {
                    c.set(4 + k, hubY + i, hz - 1, "white_wool");
                    c.set(4 - k, hubY - i, hz - 1, "white_wool");
                    c.set(4 + i, hubY - k, hz - 1, "white_wool");
                    c.set(4 - i, hubY + k, hz - 1, "white_wool");
                }
            }
        }
        c.set(4, hubY, hz - 1, s.logZ());
        int rot = r.nextInt(4);
        place(out, t, c, x, z, rot, s, r);
        // campos
        Canvas f1 = Buildings.farm(s, r);
        f1.markers.clear();
        place(out, t, f1, x + 12, z + 1, 0, s, r);
        Canvas f2 = Buildings.farm(s, r);
        f2.markers.clear();
        place(out, t, f2, x - 12, z + 1, 0, s, r);
        out.mark("entrada", x, out.ground(t, x, z) + 1, z - 7, "", 0);
        return out;
    }

    static Build lighthouse(Terrain t, Random r, Style s, int x, int z) {
        Build out = new Build();
        int rad = 3, h = between(r, 18, 24), n = 11, m = 5;
        Canvas c = new Canvas(n, n);
        c.margin = 2;
        String a = r.nextBoolean() ? "red_concrete" : "blue_concrete";
        roundShell(c, m, m, rad + 1, 0, 0, "stone_bricks", "stone_bricks");
        for (int y = 1; y <= h; y++) roundShell(c, m, m, rad, y, y, (y / 4) % 2 == 0 ? "white_concrete" : a, "air");
        for (int y = 1; y <= h; y++) c.set(m, y, m + rad - 1, "ladder[facing=north]");
        for (int y = 3; y < h; y += 4) {
            c.set(m - rad, y, m, "glass_pane");
            c.set(m + rad, y, m, "glass_pane");
        }
        c.door(m, 1, m - rad, "south", "spruce_door");
        roundShell(c, m, m, rad + 1, h + 1, h + 1, "stone_brick_slab[type=top]", "spruce_planks");
        for (int ang = 0; ang < 360; ang += 10) {
            int bx = m + (int) Math.round(Math.cos(Math.toRadians(ang)) * (rad + 1)), bz = m + (int) Math.round(Math.sin(Math.toRadians(ang)) * (rad + 1));
            c.set(bx, h + 2, bz, "iron_bars");
        }
        roundShell(c, m, m, rad - 1, h + 2, h + 4, "glass", "air");
        c.set(m, h + 2, m, "sea_lantern");
        c.set(m, h + 3, m, "glowstone");
        roundShell(c, m, m, rad - 1, h + 5, h + 5, a, a);
        cone(c, m, m, rad - 2, h + 6, a);
        c.mark("entrada", m, 1, m - rad - 2, "", 0);
        int rot = r.nextInt(4);
        place(out, t, c, x, z, rot, s, r);
        Canvas house = Buildings.house(Style.get(s.id.equals("desierto") ? "desierto" : "taiga"), r, 0);
        house.markers.clear();
        loot(house, 1, 1, 2, "east");
        place(out, t, house, x + 10, z, 3, s, r);
        return out;
    }

    static Build castleRuins(Terrain t, Random r, Style s, int x, int z) {
        Build out = new Build();
        int half = between(r, 11, 14), H = between(r, 6, 9);
        String a = s.stone, b = "mossy_" + (s.stone.equals("stone_bricks") ? "stone_bricks" : "cobblestone"), cr = "cracked_stone_bricks";
        // muralla rota que sigue el terreno, con huecos
        for (int i = -half; i <= half; i++) {
            for (int[] p : new int[][]{{x + i, z - half}, {x + i, z + half}, {x - half, z + i}, {x + half, z + i}}) {
                double decay = Math.sin(i * 0.7 + r.nextDouble()) * 2 + r.nextDouble() * 3;
                int hh = (int) Math.max(0, H - decay * 1.4);
                if (r.nextInt(9) == 0) hh = 0;
                int g = out.ground(t, p[0], p[1]);
                for (int y = g - 1; y <= g + hh; y++) out.set(p[0], y, p[1], ruin(r, a, b, cr));
                if (hh == H && Math.abs(i) % 2 == 0) out.set(p[0], g + hh + 1, p[1], ruin(r, a, b, cr));
            }
        }
        // escombros
        for (int k = 0; k < half * 3; k++) {
            int px = x + between(r, -half - 3, half + 3), pz = z + between(r, -half - 3, half + 3);
            int g = out.ground(t, px, pz);
            out.soft(px, g + 1, pz, r.nextBoolean() ? ruin(r, "cobblestone", "mossy_cobblestone", "gravel") : "stone_brick_slab[type=bottom]");
        }
        // torres de las esquinas, algunas caídas
        for (int[] p : new int[][]{{-half, -half}, {half, -half}, {-half, half}, {half, half}}) {
            Canvas tw = new Canvas(7, 7);
            tw.margin = 0;
            int th = r.nextInt(3) == 0 ? between(r, 3, 6) : between(r, H + 3, H + 7);
            for (int y = 0; y <= th; y++)
                for (int xx = 0; xx < 7; xx++)
                    for (int zz = 0; zz < 7; zz++) {
                        double d = Math.hypot(xx - 3, zz - 3);
                        if (d > 3.3) continue;
                        boolean shell = d > 2.3;
                        if (shell && !(y > th - 3 && r.nextInt(3) == 0)) tw.set(xx, y, zz, ruin(r, a, b, cr));
                        else if (!shell) tw.set(xx, y, zz, y == 0 ? a : "air");
                    }
            if (th > H) for (int xx = 0; xx < 7; xx++) for (int zz = 0; zz < 7; zz++) if (Math.hypot(xx - 3, zz - 3) > 2.3 && Math.hypot(xx - 3, zz - 3) <= 3.3 && (xx + zz) % 2 == 0) tw.set(xx, th + 1, zz, a);
            tw.mark("enemigo", 3, 1, 3, "", 0);
            place(out, t, tw, x + p[0], z + p[1], 0, s, r);
        }
        // torre del homenaje en ruinas
        Canvas k = new Canvas(11, 11);
        k.margin = 2;
        int kh = between(r, 12, 16);
        for (int y = 0; y <= kh; y++)
            for (int xx = 0; xx < 11; xx++)
                for (int zz = 0; zz < 11; zz++) {
                    boolean edge = xx == 0 || zz == 0 || xx == 10 || zz == 10;
                    if (y == 0 || y == 6) {
                        if (!(y == 6 && r.nextInt(4) == 0)) k.set(xx, y, zz, y == 0 ? a : "spruce_planks");
                    } else if (edge) {
                        boolean broken = y > kh - 5 && (xx + zz + y) % 3 == 0 && r.nextBoolean();
                        if (!broken) k.set(xx, y, zz, ruin(r, a, b, cr));
                    } else k.set(xx, y, zz, "air");
                }
        k.fill(4, 1, 0, 6, 4, 0, "air");
        k.set(5, 2, 10, "iron_bars");
        for (int y = 1; y <= 6; y++) k.set(9, y, 9, "ladder[facing=west]");
        loot(k, 1, 1, 9, "east");
        loot(k, 9, 7, 1, "west");
        k.set(5, 1, 5, "cobweb");
        k.set(2, 7, 5, "cobweb");
        k.mark("jefe", 5, 7, 5, "", 0);
        k.mark("enemigo", 3, 1, 3, "", 0);
        k.mark("enemigo", 7, 1, 7, "", 0);
        k.mark("entrada", 5, 1, -3, "", 0);
        place(out, t, k, x, z, r.nextInt(4), s, r);
        return out;
    }

    static Build temple(Terrain t, Random r, Style s, int x, int z) {
        Build out = new Build();
        boolean desert = s.id.equals("desierto");
        String pillar = desert ? "cut_sandstone" : r.nextBoolean() ? "quartz_pillar[axis=y]" : "stone_bricks";
        String base = desert ? "smooth_sandstone" : "polished_andesite";
        String roofS = desert ? "sandstone" : "stone_brick";
        int w = 11, d = between(r, 15, 19), h = 6;
        Canvas c = new Canvas(w, d);
        c.margin = 2;
        // podio con escalinata
        c.fill(0, 0, 0, w - 1, 0, d - 1, base);
        c.fill(0, 1, 2, w - 1, 1, d - 1, base);
        for (int xx = 2; xx < w - 2; xx++) c.set(xx, 1, 1, roofS + "_stairs[facing=south,half=bottom]");
        // columnas
        for (int zz = 3; zz < d; zz += 3)
            for (int xx : new int[]{0, w - 1}) for (int y = 2; y <= h + 1; y++) c.set(xx, y, zz, pillar);
        for (int xx = 0; xx < w; xx += 2) for (int y = 2; y <= h + 1; y++) c.set(xx, y, 2, pillar);
        // cella
        for (int y = 2; y <= h + 1; y++)
            for (int xx = 2; xx <= w - 3; xx++)
                for (int zz = 6; zz <= d - 2; zz++) {
                    boolean edge = xx == 2 || xx == w - 3 || zz == 6 || zz == d - 2;
                    c.set(xx, y, zz, edge ? (desert ? "sandstone" : "stone_bricks") : "air");
                }
        c.fill(w / 2, 2, 6, w / 2, 4, 6, "air");
        c.fill(0, h + 2, 2, w - 1, h + 2, d - 1, desert ? "smooth_sandstone" : "smooth_stone");
        Style rs = Style.get(desert ? "desierto" : "llanura");
        Style temp = new Style();
        temp.roof = roofS;
        temp.roofFull = desert ? "sandstone" : "stone_bricks";
        temp.wall = desert ? "sandstone" : "stone_bricks";
        temp.planks = temp.wall;
        c.gable(0, 2, w - 1, d - 1, h + 3, temp, 0, false);
        // altar
        c.set(w / 2, 2, d - 3, desert ? "chiseled_sandstone" : "chiseled_stone_bricks");
        loot(c, w / 2, 2, d - 4, "north");
        c.set(w / 2 - 1, 2, d - 3, "candle[candles=4,lit=true]");
        c.set(w / 2 + 1, 2, d - 3, "candle[candles=4,lit=true]");
        c.set(w / 2, 3, d - 3, desert ? "orange_stained_glass" : "gold_block");
        for (int zz = 8; zz < d - 3; zz += 3) {
            c.set(3, 2, zz, "lantern");
            c.set(w - 4, 2, zz, "lantern");
        }
        c.mark("entrada", w / 2, 1, -1, "", 0);
        c.mark("enemigo", w / 2, 2, 9, "", 0);
        c.mark("jefe", w / 2, 2, d - 5, "", 0);
        place(out, t, c, x, z, r.nextInt(4), rs, r);
        return out;
    }

    static Build cabin(Terrain t, Random r, Style s, int x, int z) {
        Build out = new Build();
        Style st = s.id.equals("desierto") ? s : Style.get(r.nextBoolean() ? "taiga" : s.id);
        Canvas c = Buildings.house(st, r, r.nextInt(3) == 0 ? 1 : 0);
        c.stilts = true;
        c.margin = 2;
        c.markers.clear();
        // porche
        for (int xx = 0; xx < c.w; xx++) {
            c.set(xx, 0, -1, st.planks);
            c.set(xx, 0, -2, st.planks);
            if (xx == 0 || xx == c.w - 1) for (int y = 1; y <= 3; y++) c.set(xx, y, -2, st.fence);
            else if (xx != c.w / 2) c.set(xx, 1, -2, st.fence);
        }
        c.set(1, 1, -1, st.woodStairs("east"));
        loot(c, c.w - 2, 1, 1, "west");
        c.mark("entrada", c.w / 2, 1, -3, "", 0);
        place(out, t, c, x, z, r.nextInt(4), st, r);
        return out;
    }

    static Build farmstead(Terrain t, Random r, Style s, int x, int z) {
        Build out = new Build();
        Canvas house = Buildings.house(s, r, 1);
        house.markers.clear();
        loot(house, 1, 1, 2, "east");
        place(out, t, house, x - 8, z - 6, 2, s, r);
        place(out, t, barnC(s, r), x + 8, z - 6, 2, s, r);
        for (int i = 0; i < 2; i++) {
            Canvas f = Buildings.farm(s, r);
            f.markers.clear();
            place(out, t, f, x - 8 + i * 16, z + 9, 0, s, r);
        }
        Canvas p = Buildings.pen(s, r);
        place(out, t, p, x + 18, z + 9, 0, s, r);
        out.mark("entrada", x, out.ground(t, x, z) + 1, z, "", 0);
        return out;
    }

    private static Canvas barnC(Style s, Random r) {
        int w = between(r, 11, 13), d = between(r, 13, 15), h = 6;
        Canvas c = new Canvas(w, d);
        Style b = Style.get("taiga");
        Style bb = new Style();
        bb.id = b.id;
        bb.floor = "coarse_dirt";
        bb.wall = r.nextBoolean() ? "red_terracotta" : "spruce_planks";
        bb.log = "spruce_log";
        bb.stripped = "stripped_spruce_log";
        bb.foundation = "cobblestone";
        bb.foundation2 = "stone";
        bb.roof = "dark_oak";
        bb.roofFull = "dark_oak_planks";
        bb.planks = "spruce_planks";
        c.room(0, 0, w - 1, d - 1, 0, h, bb, r, true);
        c.fill(w / 2 - 1, 1, 0, w / 2 + 1, 4, 0, "air");
        c.fill(w / 2 - 1, 1, d - 1, w / 2 + 1, 4, d - 1, "air");
        for (int zz = 1; zz < d - 1; zz++) {
            for (int y = 1; y <= (zz % 4 == 0 ? 3 : 2); y++) c.set(1, y, zz, "hay_block");
            c.set(w - 2, 1, zz, zz % 3 == 0 ? "spruce_fence" : "hay_block");
        }
        c.fill(1, h - 2, 1, w - 2, h - 2, 4, "spruce_planks");
        c.set(2, h - 1, 2, "hay_block");
        c.set(w / 2, h - 3, d / 2, "lantern[hanging=true]");
        c.set(w / 2, h - 2, d / 2, "iron_chain[axis=y]");
        loot(c, w - 2, 1, d - 2, "west");
        c.gable(0, 0, w - 1, d - 1, h + 1, bb, 1, false);
        for (int i = 0; i < 3; i++) c.mark("animal", 3 + i, 1, d - 4, i == 0 ? "cow" : "horse", 0);
        return c;
    }

    static Build graveyard(Terrain t, Random r, Style s, int x, int z) {
        Build out = new Build();
        int hw = between(r, 9, 12), hd = between(r, 8, 10);
        // reja que sigue el terreno
        for (int i = -hw; i <= hw; i++)
            for (int[] p : new int[][]{{x + i, z - hd}, {x + i, z + hd}}) fencePost(out, t, p, i, r);
        for (int i = -hd; i <= hd; i++)
            for (int[] p : new int[][]{{x - hw, z + i}, {x + hw, z + i}}) fencePost(out, t, p, i, r);
        // tumbas
        for (int gx = x - hw + 2; gx <= x + hw - 2; gx += 3)
            for (int gz = z - hd + 2; gz <= z + hd - 6; gz += 3) {
                if (r.nextInt(5) == 0) continue;
                int g = out.ground(t, gx, gz);
                out.set(gx, g, gz, "coarse_dirt");
                out.set(gx, g, gz + 1, "podzol");
                String head = r.nextInt(3) == 0 ? "cobblestone_wall" : r.nextBoolean() ? "stone_brick_wall" : "mossy_stone_brick_wall";
                out.set(gx, g + 1, gz - 1, head);
                if (r.nextInt(3) == 0) out.set(gx, g + 2, gz - 1, "candle[candles=1,lit=true]");
                if (r.nextInt(4) == 0) out.soft(gx + 1, g + 1, gz, r.nextBoolean() ? "dead_bush" : "skeleton_skull[rotation=" + r.nextInt(16) + "]");
            }
        // cripta con escalera a una sala
        Canvas c = new Canvas(7, 7);
        c.margin = 1;
        c.fill(0, 0, 0, 6, 0, 6, "polished_andesite");
        for (int y = 1; y <= 4; y++)
            for (int xx = 0; xx < 7; xx++)
                for (int zz = 0; zz < 7; zz++)
                    if (xx == 0 || zz == 0 || xx == 6 || zz == 6) c.set(xx, y, zz, ruin(r, "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks"));
                    else c.set(xx, y, zz, "air");
        c.fill(2, 1, 0, 4, 3, 0, "air");
        c.set(2, 1, 0, "stone_brick_wall");
        c.set(4, 1, 0, "stone_brick_wall");
        Style st = new Style();
        st.roof = "stone_brick";
        st.roofFull = "stone_bricks";
        st.wall = "stone_bricks";
        st.planks = "stone_bricks";
        c.gable(0, 0, 6, 6, 5, st, 0, true);
        // escalera hacia abajo
        for (int i = 0; i < 6; i++) {
            c.fill(2, -i, 2 + i, 4, -i + 3, 2 + i, "air");
            c.set(3, -i, 2 + i, "stone_brick_stairs[facing=north,half=bottom]");
            c.set(2, -i, 2 + i, "stone_brick_stairs[facing=north,half=bottom]");
            c.set(4, -i, 2 + i, "stone_brick_stairs[facing=north,half=bottom]");
        }
        // sala de abajo
        int fy = -6;
        c.fill(-3, fy - 1, 7, 9, fy + 4, 15, "stone_bricks");
        c.fill(-2, fy, 8, 8, fy + 3, 14, "air");
        c.fill(2, fy, 2, 4, fy + 3, 8, "air");
        for (int xx = -2; xx <= 8; xx += 2) c.set(xx, fy, 14, xx % 4 == 0 ? "chest[facing=north]" : "skeleton_skull[rotation=8]");
        c.set(-2, fy + 2, 11, "soul_wall_torch[facing=east]");
        c.set(8, fy + 2, 11, "soul_wall_torch[facing=west]");
        c.set(3, fy, 11, "spawner");
        c.set(-2, fy, 8, "cobweb");
        c.set(8, fy + 3, 9, "cobweb");
        c.mark("enemigo", 0, fy, 10, "", 0);
        c.mark("enemigo", 6, fy, 10, "", 0);
        c.mark("jefe", 3, fy, 13, "", 0);
        c.mark("entrada", 3, 1, -2, "", 0);
        c.noClear = false;
        place(out, t, c, x, z + hd - 4, 0, s, r);
        return out;
    }

    private static void fencePost(Build out, Terrain t, int[] p, int i, Random r) {
        int g = out.ground(t, p[0], p[1]);
        if (Math.abs(i) % 5 == 0) {
            out.set(p[0], g + 1, p[1], "stone_bricks");
            out.set(p[0], g + 2, p[1], "stone_brick_wall");
            if (r.nextInt(3) == 0) out.set(p[0], g + 3, p[1], "soul_lantern");
        } else {
            out.set(p[0], g + 1, p[1], "iron_bars");
            out.set(p[0], g + 2, p[1], "iron_bars");
        }
    }

    static Build camp(Terrain t, Random r, Style s, int x, int z) {
        Build out = new Build();
        Canvas c = new Canvas(17, 17);
        c.margin = 1;
        c.noClear = false;
        for (int xx = 0; xx < 17; xx++)
            for (int zz = 0; zz < 17; zz++) {
                double d = Math.hypot(xx - 8, zz - 8);
                if (d <= 8.3) c.set(xx, 0, zz, d < 2.5 ? "coarse_dirt" : r.nextInt(4) == 0 ? "rooted_dirt" : "coarse_dirt");
            }
        c.set(8, 1, 8, "campfire[lit=true]");
        for (int[] p : new int[][]{{6, 8}, {10, 8}, {8, 6}, {8, 10}}) c.set(p[0], 1, p[1], "spruce_log[axis=" + (p[0] == 8 ? "x" : "z") + "]");
        String[] wools = {"white_wool", "brown_wool", "light_gray_wool", "green_wool", "red_wool"};
        int tents = between(r, 3, 5);
        for (int i = 0; i < tents; i++) {
            double a = Math.PI * 2 * i / tents + r.nextDouble() * 0.4;
            int tx = 8 + (int) Math.round(Math.cos(a) * 5.5), tz = 8 + (int) Math.round(Math.sin(a) * 5.5);
            String wool = wools[r.nextInt(wools.length)];
            boolean alongX = Math.abs(Math.cos(a)) < 0.7;
            for (int k = -1; k <= 1; k++) {
                int px = tx + (alongX ? k : 0), pz = tz + (alongX ? 0 : k);
                c.set(px + (alongX ? 0 : -1), 1, pz + (alongX ? -1 : 0), wool);
                c.set(px + (alongX ? 0 : 1), 1, pz + (alongX ? 1 : 0), wool);
                c.set(px, 2, pz, wool);
                c.set(px, 1, pz, "air");
            }
            c.mark("enemigo", tx, 1, tz, "", 0);
        }
        c.set(13, 1, 3, "chest[facing=west]");
        c.set(3, 1, 13, "barrel[facing=up]");
        c.set(4, 1, 13, "barrel[facing=up]");
        c.set(13, 1, 13, "target");
        c.set(3, 1, 3, "crafting_table");
        c.set(12, 1, 8, "anvil[facing=north]");
        c.mark("jefe", 8, 1, 11, "", 0);
        c.mark("entrada", 8, 1, -2, "", 0);
        place(out, t, c, x, z, 0, s, r);
        return out;
    }

    static Build inn(Terrain t, Random r, Style s, int x, int z) {
        Build out = new Build();
        Canvas c = Buildings.tavern(s, r);
        c.markers.clear();
        loot(c, 1, 1, 2, "east");
        place(out, t, c, x, z - 4, 0, s, r);
        Canvas st = Buildings.pen(s, r);
        st.markers.clear();
        st.mark("animal", 2, 1, 2, "horse", 0);
        st.mark("animal", 4, 1, 3, "horse", 0);
        place(out, t, st, x + 13, z - 4, 0, s, r);
        place(out, t, Buildings.well(s, r), x - 2, z + 7, 0, s, r);
        out.mark("entrada", x, out.ground(t, x, z + 3) + 1, z + 3, "", 0);
        return out;
    }

    // ------------------------------------------------------------------ mazmorras grandes

    static Build fortress(Terrain t, Random r, Style s, int x, int z) {
        Build out = new Build();
        Style d = Style.get(r.nextBoolean() ? "oscuro" : "llanura");
        String a = d.stone, b = d.stone2;
        int half = between(r, 17, 21), H = between(r, 9, 11);
        int gateSide = r.nextInt(4);
        // muralla (piso rígido por tramos de 5 para que no se deforme)
        for (int i = -half; i <= half; i++) {
            int[][] pts = {{x + i, z - half, 0}, {x + half, z + i, 1}, {x + i, z + half, 2}, {x - half, z + i, 3}};
            for (int[] p : pts) {
                boolean gate = p[2] == gateSide && Math.abs(i) <= 2;
                int g = segGround(out, t, p[0], p[1], p[2] % 2 == 0, x, z);
                for (int y = g - 3; y <= g + H; y++) out.set(p[0], y, p[1], r.nextInt(4) == 0 ? b : a);
                if (Math.abs(i) % 2 == 0) out.set(p[0], g + H + 1, p[1], a);
                if (gate) {
                    for (int y = g + 1; y <= g + 5; y++) out.set(p[0], y, p[1], "air");
                    out.set(p[0], g + 5, p[1], "iron_bars");
                    out.set(p[0], g, p[1], d.path);
                }
                if (!gate && Math.abs(i) % 6 == 3) out.set(p[0], g + H - 3, p[1], "iron_bars");
                // pasarela por dentro
                int ix = p[0] + (p[2] == 1 ? -1 : p[2] == 3 ? 1 : 0), iz = p[1] + (p[2] == 0 ? 1 : p[2] == 2 ? -1 : 0);
                if (!gate) out.set(ix, g + H - 1, iz, d.stoneSlab + "[type=top]");
            }
        }
        // torres
        for (int[] p : new int[][]{{-half, -half}, {half, -half}, {-half, half}, {half, half}}) {
            Canvas tw = new Canvas(9, 9);
            tw.margin = 0;
            int th = H + between(r, 5, 8);
            roundShell(tw, 4, 4, 4, 0, th, a, "air");
            roundShell(tw, 4, 4, 3.4, 0, 0, a, a);
            roundShell(tw, 4, 4, 3.4, th - 4, th - 4, "dark_oak_planks", "dark_oak_planks");
            for (int y = 1; y <= th; y++) tw.set(4, y, 7, "ladder[facing=north]");
            for (int xx = 0; xx < 9; xx++) for (int zz = 0; zz < 9; zz++) if (Math.hypot(xx - 4, zz - 4) > 3.25 && Math.hypot(xx - 4, zz - 4) <= 4.35 && (xx + zz) % 2 == 0) tw.set(xx, th + 1, zz, a);
            tw.set(4, th - 3, 4, d.light);
            tw.mark("enemigo", 4, th - 3, 3, "", 0);
            tw.mark("enemigo", 3, 1, 4, "", 0);
            place(out, t, tw, x + p[0], z + p[1], 0, d, r);
        }
        // torre del homenaje
        Canvas k = new Canvas(15, 15);
        k.margin = 3;
        int floors = 3, fh = 6;
        for (int f = 0; f < floors; f++) {
            int y0 = f * fh;
            k.fill(0, y0, 0, 14, y0, 14, f == 0 ? a : "dark_oak_planks");
            for (int y = y0 + 1; y <= y0 + fh; y++)
                for (int xx = 0; xx < 15; xx++)
                    for (int zz = 0; zz < 15; zz++) {
                        boolean edge = xx == 0 || zz == 0 || xx == 14 || zz == 14;
                        boolean corner = (xx == 0 || xx == 14) && (zz == 0 || zz == 14);
                        if (edge) k.set(xx, y, zz, corner ? b : (y == y0 + 3 && (xx % 4 == 2 || zz % 4 == 2) ? "iron_bars" : a));
                        else k.set(xx, y, zz, "air");
                    }
            // escalera entre pisos
            if (f < floors - 1) for (int i = 0; i < fh; i++) {
                k.set(2 + i, y0 + 1 + i, 12, d.stoneStairs + "[facing=east,half=bottom]");
                k.set(2 + i, y0 + fh, 12, "air");
                k.set(2 + i, y0 + fh, 13, "air");
            }
            k.set(7, y0 + fh - 1, 7, d.light.equals("soul_lantern") ? "soul_lantern[hanging=true]" : "lantern[hanging=true]");
            k.mark("enemigo", 4, y0 + 1, 4, "", 0);
            k.mark("enemigo", 10, y0 + 1, 4, "", 0);
            loot(k, 13, y0 + 1, 1, "west");
        }
        int top = floors * fh;
        k.fill(0, top, 0, 14, top, 14, a);
        for (int xx = 0; xx < 15; xx++) for (int zz = 0; zz < 15; zz++) if ((xx == 0 || zz == 0 || xx == 14 || zz == 14)) {
            k.set(xx, top + 1, zz, a);
            if ((xx + zz) % 2 == 0) k.set(xx, top + 2, zz, a);
        }
        for (int i = 0; i < fh; i++) k.set(2 + i, top - fh + 1 + i, 12, d.stoneStairs + "[facing=east,half=bottom]");
        k.fill(2, top, 12, 8, top, 13, "air");
        k.door(7, 1, 0, "south", "iron_door");
        k.set(7, 3, -1, d.light.equals("soul_lantern") ? "soul_wall_torch[facing=north]" : "wall_torch[facing=north]");
        k.fill(5, top + 1, 5, 9, top + 1, 9, "red_carpet");
        loot(k, 7, top + 1, 10, "north");
        k.set(6, top + 1, 10, "gold_block");
        k.set(8, top + 1, 10, "gold_block");
        for (int y = top + 2; y <= top + 7; y++) k.set(1, y, 1, d.fence);
        k.set(2, top + 7, 1, "black_wool");
        k.set(2, top + 6, 1, "black_wool");
        k.set(3, top + 7, 1, "black_wool");
        k.mark("jefe", 7, top + 1, 7, "", 0);
        int rot = (gateSide + 2) % 4;
        place(out, t, k, x, z, rot, d, r);
        // cuartel del patio
        Canvas bar = Buildings.barracks(d, r);
        bar.markers.clear();
        bar.mark("enemigo", bar.w / 2, 1, 4, "", 0);
        int bx = gateSide % 2 == 0 ? x + half - 9 : x, bz = gateSide % 2 == 0 ? z : z + half - 9;
        place(out, t, bar, bx, bz, rot, d, r);
        int[] gd = {0, 1, 0, -1};
        int[] gz = {-1, 0, 1, 0};
        out.mark("entrada", x + gd[gateSide] * (half + 3), out.ground(t, x + gd[gateSide] * (half + 3), z + gz[gateSide] * (half + 3)) + 1, z + gz[gateSide] * (half + 3), "", 0);
        return out;
    }

    /** Altura del suelo para un tramo de muralla: igual en bloques de 5, así no se ve ondulada. */
    private static int segGround(Build out, Terrain t, int x, int z, boolean alongX, int cx, int cz) {
        int s0 = alongX ? Math.floorDiv(x - cx, 5) * 5 + cx : x, s1 = alongX ? z : Math.floorDiv(z - cz, 5) * 5 + cz;
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < 5; i++) min = Math.min(min, out.ground(t, alongX ? s0 + i : s0, alongX ? s1 : s1 + i));
        return min;
    }

    static Build darkTower(Terrain t, Random r, Style s, int x, int z) {
        Build out = new Build();
        int floors = between(r, 5, 7), fh = 6, n = 13, m = 6;
        Canvas c = new Canvas(n, n);
        c.margin = 3;
        String a = "deepslate_bricks", b = "cracked_deepslate_bricks", crown = r.nextBoolean() ? "crying_obsidian" : "blackstone";
        roundShell(c, m, m, 6.2, 0, 0, "polished_blackstone", "polished_blackstone");
        for (int f = 0; f < floors; f++) {
            int y0 = f * fh;
            for (int y = y0 + 1; y <= y0 + fh; y++) roundShell(c, m, m, 5.6, y, y, r.nextInt(4) == 0 ? b : a, "air");
            if (f > 0) roundShell(c, m, m, 5, y0, y0, "polished_blackstone_bricks", "dark_oak_planks");
            // escalera en espiral pegada a la pared
            for (int i = 0; i < fh; i++) {
                double ang = Math.PI * 2 * (i + f * 2) / 12.0;
                int sx = m + (int) Math.round(Math.cos(ang) * 4), sz = m + (int) Math.round(Math.sin(ang) * 4);
                c.set(sx, y0 + 1 + i, sz, "polished_blackstone_bricks");
                c.set(sx, y0 + fh, sz, i == fh - 1 ? "polished_blackstone_bricks" : "air");
                c.set(sx, y0 + fh + 1, sz, "air");
            }
            for (int[] p : new int[][]{{m - 5, m}, {m + 5, m}, {m, m + 5}}) {
                c.set(p[0], y0 + 3, p[1], "iron_bars");
                c.set(p[0], y0 + 4, p[1], "iron_bars");
            }
            c.set(m, y0 + fh - 1, m, "soul_lantern[hanging=true]");
            c.mark("enemigo", m + 2, y0 + 1, m - 2, "", 0);
            c.mark("enemigo", m - 2, y0 + 1, m + 2, "", 0);
            if (f % 2 == 1) loot(c, m - 3, y0 + 1, m - 3, "south");
            if (f == 2) c.set(m, y0 + 1, m, "spawner");
        }
        int top = floors * fh;
        roundShell(c, m, m, 6.2, top, top, "polished_blackstone", "polished_blackstone");
        for (int xx = 0; xx < n; xx++)
            for (int zz = 0; zz < n; zz++) {
                double d = Math.hypot(xx - m, zz - m);
                if (d > 5.4 && d <= 6.6) {
                    c.set(xx, top + 1, zz, "polished_blackstone_wall");
                    if ((xx + zz) % 3 == 0) {
                        c.set(xx, top + 2, zz, crown);
                        c.set(xx, top + 3, zz, crown);
                    }
                }
            }
        c.set(m, top + 1, m, "respawn_anchor[charges=4]");
        loot(c, m, top + 1, m + 2, "north");
        c.door(m, 1, m - 6, "south", "iron_door");
        c.fill(m - 1, 1, m - 6, m + 1, 3, m - 6, "air");
        c.set(m, 1, m - 6, "air");
        c.mark("jefe", m, top + 1, m - 2, "", 0);
        c.mark("entrada", m, 1, m - 9, "", 0);
        place(out, t, c, x, z, r.nextInt(4), Style.get("oscuro"), r);
        return out;
    }

    static Build deepCrypt(Terrain t, Random r, Style s, int x, int z) {
        Build out = new Build();
        Canvas c = new Canvas(9, 9);
        c.margin = 2;
        String a = "stone_bricks";
        c.fill(0, 0, 0, 8, 0, 8, "polished_andesite");
        for (int y = 1; y <= 5; y++)
            for (int xx = 0; xx < 9; xx++)
                for (int zz = 0; zz < 9; zz++)
                    if (xx == 0 || zz == 0 || xx == 8 || zz == 8) c.set(xx, y, zz, ruin(r, a, "mossy_stone_bricks", "cracked_stone_bricks"));
                    else c.set(xx, y, zz, "air");
        for (int[] p : new int[][]{{0, 0}, {8, 0}, {0, 8}, {8, 8}}) for (int y = 1; y <= 7; y++) c.set(p[0], y, p[1], "polished_andesite");
        c.fill(3, 1, 0, 5, 4, 0, "air");
        Style st = new Style();
        st.roof = "stone_brick";
        st.roofFull = "stone_bricks";
        st.wall = "stone_bricks";
        st.planks = "stone_bricks";
        c.gable(0, 0, 8, 8, 6, st, 0, true);
        // escalera larga hacia abajo
        int depth = 14;
        for (int i = 0; i < depth; i++) {
            c.fill(3, -i, 1 + i, 5, -i + 3, 1 + i, "air");
            for (int xx = 3; xx <= 5; xx++) c.set(xx, -i, 1 + i, "stone_brick_stairs[facing=north,half=bottom]");
            c.set(2, -i + 1, 1 + i, a);
            c.set(6, -i + 1, 1 + i, a);
            if (i % 4 == 2) c.set(2, -i + 2, 1 + i, "soul_wall_torch[facing=east]");
        }
        // catacumbas: salas unidas por pasillos, al azar
        int fy = -depth;
        int[][] rooms = new int[between(r, 5, 8)][];
        rooms[0] = new int[]{4, depth + 6};
        for (int i = 1; i < rooms.length; i++) {
            int[] prev = rooms[r.nextInt(i)];
            int dir = r.nextInt(3);
            int dx = dir == 0 ? -12 : dir == 1 ? 12 : 0, dz = dir == 2 ? 12 : 0;
            rooms[i] = new int[]{prev[0] + dx, prev[1] + dz};
            corridor(c, prev, rooms[i], fy, r);
        }
        corridor(c, new int[]{4, depth}, rooms[0], fy, r);
        for (int i = 0; i < rooms.length; i++) {
            int[] ro = rooms[i];
            int hw = between(r, 3, 4);
            c.fill(ro[0] - hw - 1, fy - 1, ro[1] - hw - 1, ro[0] + hw + 1, fy + 5, ro[1] + hw + 1, ruin(r, a, "mossy_stone_bricks", "cracked_stone_bricks"));
            for (int xx = ro[0] - hw - 1; xx <= ro[0] + hw + 1; xx++)
                for (int zz = ro[1] - hw - 1; zz <= ro[1] + hw + 1; zz++)
                    for (int y = fy - 1; y <= fy + 5; y++) {
                        boolean shell = xx == ro[0] - hw - 1 || xx == ro[0] + hw + 1 || zz == ro[1] - hw - 1 || zz == ro[1] + hw + 1 || y == fy - 1 || y == fy + 5;
                        String cur = c.get(xx, y, zz);
                        if (!shell) c.set(xx, y, zz, "air");
                        else if (cur == null || !cur.equals("air")) c.set(xx, y, zz, ruin(r, a, "mossy_stone_bricks", "cracked_stone_bricks"));
                    }
            // nichos con calaveras
            for (int k = -hw; k <= hw; k += 2) {
                c.set(ro[0] + k, fy + 1, ro[1] - hw - 1, "bone_block[axis=y]");
                if (r.nextInt(3) == 0) c.set(ro[0] + k, fy + 2, ro[1] + hw, "cobweb");
            }
            c.set(ro[0], fy + 4, ro[1], "soul_lantern[hanging=true]");
            if (i > 0) c.mark("enemigo", ro[0] + 1, fy, ro[1], "", 0);
            if (i > 0 && i % 2 == 0) loot(c, ro[0] - hw, fy, ro[1] - hw, "south");
            if (i == 2) c.set(ro[0], fy, ro[1], "spawner");
        }
        int[] last = rooms[rooms.length - 1];
        loot(c, last[0], fy, last[1] + 2, "north");
        c.set(last[0] - 1, fy, last[1] + 2, "gold_block");
        c.set(last[0] + 1, fy, last[1] + 2, "gold_block");
        c.mark("jefe", last[0], fy, last[1], "", 0);
        c.mark("entrada", 4, 1, -2, "", 0);
        c.noClear = false;
        place(out, t, c, x, z, 0, s, r);
        return out;
    }

    private static void corridor(Canvas c, int[] a, int[] b, int fy, Random r) {
        int x = a[0], z = a[1];
        while (x != b[0] || z != b[1]) {
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++)
                    for (int y = fy - 1; y <= fy + 4; y++) {
                        boolean shell = Math.abs(dx) == 2 || Math.abs(dz) == 2 || y == fy - 1 || y == fy + 4;
                        String cur = c.get(x + dx, y, z + dz);
                        if (!shell) c.set(x + dx, y, z + dz, "air");
                        else if (cur == null) c.set(x + dx, y, z + dz, r.nextInt(5) == 0 ? "mossy_stone_bricks" : "stone_bricks");
                    }
            if (x != b[0]) x += Integer.signum(b[0] - x);
            else z += Integer.signum(b[1] - z);
        }
    }

    // ------------------------------------------------------------------ bases

    static Canvas baseHouse(Style s, Random r) {
        int w = between(r, 13, 15), d = between(r, 11, 12), h = 5;
        Canvas c = new Canvas(w, d);
        c.margin = 2;
        c.room(0, 0, w - 1, d - 1, 0, h, s, r, true);
        c.fill(1, h, 1, w - 2, h, d - 2, s.floor);
        c.room(0, 0, w - 1, d - 1, h, h, s, r, true);
        c.fill(1, h + 1, 1, w - 2, h * 2 - 1, d - 2, "air");
        c.door(w / 2, 1, 0, "south", s.door);
        c.windows(0, 0, w - 1, d - 1, 2, 3, s, true);
        c.windows(0, 0, w - 1, d - 1, h + 2, 3, s, false);
        // escalera
        for (int i = 0; i < h; i++) {
            c.set(w - 2 - i, 1 + i, d - 2, s.woodStairs("west"));
            c.set(w - 2 - i, h, d - 2, "air");
        }
        c.set(w - 2 - h, h, d - 2, "air");
        // almacén: pared de cofres
        for (int zz = 1; zz < d - 2; zz++) {
            c.set(1, 1, zz, "chest[facing=east]");
            c.set(1, 2, zz, "chest[facing=east]");
        }
        // cocina y taller
        c.set(w - 2, 1, 1, "crafting_table");
        c.set(w - 3, 1, 1, "furnace[facing=south]");
        c.set(w - 4, 1, 1, "smoker[facing=south]");
        c.set(w - 5, 1, 1, "blast_furnace[facing=south]");
        c.set(w - 2, 1, 2, "anvil[facing=north]");
        c.set(w - 2, 1, 3, "smithing_table");
        c.set(w - 2, 1, 4, "grindstone[face=floor,facing=west]");
        c.set(w / 2, 1, d / 2, "cartography_table");
        // arriba: dormitorio y encantamientos
        c.bed(2, h + 1, d - 3, "south", "red");
        c.bed(4, h + 1, d - 3, "south", "blue");
        c.set(2, h + 1, 1, "chest[facing=south]");
        int ex = w / 2 + 1, ez = 3;
        c.set(ex, h + 1, ez, "enchanting_table");
        for (int xx = ex - 2; xx <= ex + 2; xx++) for (int y = h + 1; y <= h + 2; y++) {
            c.set(xx, y, ez - 2 < 1 ? 1 : ez - 2, "bookshelf");
        }
        for (int zz = ez - 1; zz <= ez + 1; zz++) for (int y = h + 1; y <= h + 2; y++) {
            c.set(ex - 2, y, zz, "bookshelf");
            c.set(ex + 2, y, zz, "bookshelf");
        }
        c.set(ex, h + 1, ez, "enchanting_table");
        c.set(w - 3, h + 1, d - 3, "brewing_stand");
        c.set(w - 2, h + 1, d - 3, "cauldron");
        for (int y : new int[]{3, h + 3}) {
            c.set(w / 2, y, 1, "wall_torch[facing=south]");
            c.set(w / 2, y, d - 2, "wall_torch[facing=north]");
        }
        c.chimney(w - 3, d - 1, h * 2 + 1, h * 2 + 6, s);
        if (s.flatRoofs) c.flatRoof(0, 0, w - 1, d - 1, h * 2 + 1, s, false);
        else c.gable(0, 0, w - 1, d - 1, h * 2 + 1, s, 1, true);
        c.mark("entrada", w / 2, 1, -2, "", 0);
        return c;
    }

    static Build starterBase(Terrain t, Random r, Style s, int x, int z) {
        Build out = new Build();
        Canvas c = baseHouse(s, r);
        int rot = r.nextInt(4);
        place(out, t, c, x, z, rot, s, r);
        Canvas f = Buildings.farm(s, r);
        f.markers.clear();
        int[] off = {0, 0};
        switch (rot) {
            case 0 -> off = new int[]{0, 13};
            case 1 -> off = new int[]{-14, 0};
            case 2 -> off = new int[]{0, -13};
            default -> off = new int[]{14, 0};
        }
        place(out, t, f, x + off[0], z + off[1], rot, s, r);
        return out;
    }

    static Build fortifiedBase(Terrain t, Random r, Style s, int x, int z) {
        Build out = new Build();
        int half = between(r, 16, 18), H = 7;
        int gateSide = r.nextInt(4);
        for (int i = -half; i <= half; i++) {
            int[][] pts = {{x + i, z - half, 0}, {x + half, z + i, 1}, {x + i, z + half, 2}, {x - half, z + i, 3}};
            for (int[] p : pts) {
                boolean gate = p[2] == gateSide && Math.abs(i) <= 1;
                int g = segGround(out, t, p[0], p[1], p[2] % 2 == 0, x, z);
                for (int y = g - 2; y <= g + H; y++) out.set(p[0], y, p[1], s.stoneMix(r));
                if (Math.abs(i) % 2 == 0) out.set(p[0], g + H + 1, p[1], s.stone);
                if (gate) {
                    for (int y = g + 1; y <= g + 4; y++) out.set(p[0], y, p[1], "air");
                    out.set(p[0], g, p[1], s.pathMix(r));
                }
            }
        }
        for (int[] p : new int[][]{{-half, -half}, {half, -half}, {-half, half}, {half, half}}) {
            Canvas tw = new Canvas(7, 7);
            tw.margin = 0;
            int th = H + 5;
            for (int y = 0; y <= th; y++)
                for (int xx = 0; xx < 7; xx++)
                    for (int zz = 0; zz < 7; zz++) {
                        boolean edge = xx == 0 || zz == 0 || xx == 6 || zz == 6;
                        tw.set(xx, y, zz, y == 0 || y == th - 3 ? s.stone : edge ? s.stoneMix(r) : "air");
                    }
            for (int xx = 0; xx < 7; xx++) for (int zz = 0; zz < 7; zz++) if ((xx == 0 || zz == 0 || xx == 6 || zz == 6) && (xx + zz) % 2 == 0) tw.set(xx, th + 1, zz, s.stone);
            for (int y = 1; y < th - 3; y++) tw.set(3, y, 5, "ladder[facing=north]");
            tw.set(3, th - 3, 5, s.trapdoor + "[facing=north,half=top,open=false]");
            tw.set(3, th - 2, 3, s.light);
            place(out, t, tw, x + p[0], z + p[1], 0, s, r);
        }
        Canvas house = baseHouse(s, r);
        int rot = gateSide;
        place(out, t, house, x, z - (gateSide == 0 ? -4 : 4), rot, s, r);
        Canvas f = Buildings.farm(s, r);
        f.markers.clear();
        place(out, t, f, x - half + 9, z + half - 8, 0, s, r);
        Canvas p = Buildings.pen(s, r);
        place(out, t, p, x + half - 8, z + half - 8, 0, s, r);
        int[] gd = {0, 1, 0, -1}, gz = {-1, 0, 1, 0};
        out.mark("entrada", x + gd[gateSide] * (half + 2), out.ground(t, x + gd[gateSide] * (half + 2), z + gz[gateSide] * (half + 2)) + 1, z + gz[gateSide] * (half + 2), "", 0);
        return out;
    }

    static Build bunker(Terrain t, Random r, Style s, int x, int z) {
        Build out = new Build();
        Canvas c = new Canvas(5, 5);
        c.margin = 2;
        c.fill(0, 0, 0, 4, 0, 4, "polished_andesite");
        for (int y = 1; y <= 3; y++)
            for (int xx = 0; xx < 5; xx++)
                for (int zz = 0; zz < 5; zz++)
                    c.set(xx, y, zz, (xx == 0 || zz == 0 || xx == 4 || zz == 4) ? (y == 2 && (xx == 2 || zz == 2) ? "glass_pane" : "stone_bricks") : "air");
        c.fill(0, 4, 0, 4, 4, 4, "stone_brick_slab[type=bottom]");
        c.door(2, 1, 0, "south", "iron_door");
        c.set(3, 2, -1, "stone_button[face=wall,facing=north]");
        c.set(1, 2, 1, "stone_button[face=wall,facing=south]");
        int depth = 14;
        for (int y = -depth; y <= 0; y++) {
            c.set(2, y, 3, y == 0 ? "iron_trapdoor[facing=north,half=top,open=false]" : "ladder[facing=north]");
            c.set(2, y, 4, "stone_bricks");
            c.set(1, y, 3, "stone_bricks");
            c.set(3, y, 3, "stone_bricks");
            if (y < 0) c.set(2, y, 2, "stone_bricks");
        }
        int fy = -depth;
        // sala principal, almacén, dormitorio y granja
        int[][] rooms = {{-6, 2, 10, 14}, {-14, 2, -7, 12}, {11, 2, 18, 12}, {-6, 15, 10, 24}};
        for (int[] ro : rooms) {
            c.fill(ro[0] - 1, fy - 1, ro[1] - 1, ro[2] + 1, fy + 4, ro[3] + 1, "deepslate_bricks");
            c.fill(ro[0], fy, ro[1], ro[2], fy + 3, ro[3], "air");
            c.fill(ro[0], fy - 1, ro[1], ro[2], fy - 1, ro[3], "polished_andesite");
            for (int xx = ro[0] + 2; xx <= ro[2] - 2; xx += 4) for (int zz = ro[1] + 2; zz <= ro[3] - 2; zz += 4) c.set(xx, fy + 4, zz, "sea_lantern");
        }
        c.fill(-6, fy, 7, -6, fy + 2, 8, "air");
        c.fill(-7, fy, 7, -7, fy + 2, 8, "air");
        c.fill(10, fy, 7, 11, fy + 2, 8, "air");
        c.fill(1, fy, 14, 3, fy + 2, 15, "air");
        c.fill(1, fy, 3, 3, fy + 2, 3, "air");
        c.set(2, fy, 3, "ladder[facing=north]");
        // almacén
        for (int zz = 2; zz <= 12; zz++) {
            c.set(-14, fy, zz, "chest[facing=east]");
            c.set(-14, fy + 1, zz, "chest[facing=east]");
            c.set(-7 - 0, fy, zz, zz % 2 == 0 ? "barrel[facing=up]" : "air");
        }
        // dormitorio
        c.bed(13, fy, 3, "south", "white");
        c.bed(15, fy, 3, "south", "white");
        c.set(17, fy, 2, "chest[facing=west]");
        c.set(17, fy, 11, "enchanting_table");
        for (int xx = 15; xx <= 18; xx++) c.set(xx, fy, 12, "bookshelf");
        // sala principal
        c.set(-4, fy, 2, "crafting_table");
        c.set(-3, fy, 2, "furnace[facing=south]");
        c.set(-2, fy, 2, "blast_furnace[facing=south]");
        c.set(-1, fy, 2, "smoker[facing=south]");
        c.set(8, fy, 2, "anvil[facing=north]");
        c.set(9, fy, 2, "smithing_table");
        c.set(9, fy, 13, "brewing_stand");
        // granja interior con luz
        for (int xx = -5; xx <= 9; xx++)
            for (int zz = 16; zz <= 23; zz++) {
                if (zz == 19) c.set(xx, fy - 1, zz, "water");
                else {
                    c.set(xx, fy - 1, zz, "farmland[moisture=7]");
                    c.set(xx, fy, zz, (xx + zz) % 3 == 0 ? "carrots[age=7]" : (xx + zz) % 3 == 1 ? "potatoes[age=7]" : "wheat[age=7]");
                }
            }
        c.mark("entrada", 2, 1, -2, "", 0);
        c.noClear = false;
        place(out, t, c, x, z, r.nextInt(4), s, r);
        return out;
    }

    static Build farmBase(Terrain t, Random r, Style s, int x, int z) {
        Build out = new Build();
        Canvas house = baseHouse(s, r);
        place(out, t, house, x - 9, z - 10, 2, s, r);
        place(out, t, barnC(s, r), x + 9, z - 10, 2, s, r);
        for (int i = 0; i < 3; i++) {
            Canvas f = Buildings.farm(s, r);
            f.markers.clear();
            place(out, t, f, x - 15 + i * 15, z + 5, 0, s, r);
        }
        for (int i = 0; i < 2; i++) {
            Canvas p = Buildings.pen(s, r);
            place(out, t, p, x - 8 + i * 16, z + 18, 0, s, r);
        }
        out.mark("entrada", x, out.ground(t, x, z) + 1, z - 2, "", 0);
        return out;
    }
}
