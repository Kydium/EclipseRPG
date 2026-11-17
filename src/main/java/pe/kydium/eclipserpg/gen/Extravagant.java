package pe.kydium.eclipserpg.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Estructuras extravagantes: islas flotantes, ciudadela celeste, galeón volador, árbol gigante, pirámide,
 * volcán, calavera gigante, laberinto y coloso. Todas esconden un tesoro (marca "tesoro") y las mazmorras
 * traen generadores de monstruos (marca "spawner" con el tipo).
 */
public final class Extravagant {
    private Extravagant() {}

    static void register() {
        Catalog.register("isla_flotante", "Isla flotante", "aerea", "Isla en el cielo con árbol, ruinas y una cámara secreta dentro de la roca. Se sube por una columna con escalera.", 16, Extravagant::floatingIsland);
        Catalog.register("ciudadela_celeste", "Ciudadela celeste", "aerea", "Islas en el cielo unidas por puentes, con generadores de monstruos, jefe en el torreón y bóveda escondida bajo el trono.", 30, Extravagant::skyCitadel);
        Catalog.register("galeon_volador", "Galeón volador", "aerea", "Barco pirata flotando en el cielo, con bodega llena de generadores y el tesoro del capitán escondido.", 22, Extravagant::flyingGalleon);
        Catalog.register("arbol_gigante", "Árbol gigante", "estructura", "Árbol enorme con casas colgantes, escalera dentro del tronco y un cofre enterrado entre las raíces.", 18, Extravagant::giantTree);
        Catalog.register("piramide", "Pirámide", "mazmorra", "Pirámide escalonada con pasillos, generadores y una cámara del tesoro sellada bajo el centro.", 24, Extravagant::pyramid);
        Catalog.register("volcan", "Volcán", "mazmorra", "Volcán con lava en el cráter, túneles de lava, generadores y una bóveda escondida en su interior.", 26, Extravagant::volcano);
        Catalog.register("calavera_gigante", "Calavera gigante", "mazmorra", "Cueva con forma de calavera: se entra por la boca, generadores adentro y tesoro detrás de un ojo.", 16, Extravagant::giantSkull);
        Catalog.register("laberinto", "Laberinto", "mazmorra", "Laberinto de setos distinto cada vez, con generadores en los callejones y el tesoro en el centro.", 22, Extravagant::maze);
        Catalog.register("coloso", "Coloso", "estructura", "Estatua gigante de un guerrero con espada; bajo el pedestal hay una bóveda secreta.", 14, Extravagant::colossus);
        Catalog.register("forja_legendaria", "Forja legendaria", "mazmorra", "Forja de piedra negra con cúpula, chimeneas y ríos de lava; en el altar del centro descansa un arma especial y bajo el suelo hay un tesoro.", 13, Extravagant::legendaryForge);
    }

    /** Da vuelta una construcción de norte a sur (la cara mira hacia el otro lado). */
    static Build mirrorZ(Build b, int z0) {
        Build o = new Build();
        for (var e : b.blocks.entrySet()) {
            long k = e.getKey();
            String v = e.getValue();
            if (v.contains("facing=north")) v = v.replace("facing=north", "facing=south");
            else if (v.contains("facing=south")) v = v.replace("facing=south", "facing=north");
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("rotation=(\\d+)").matcher(v);
            if (m.find()) v = m.replaceFirst("rotation=" + ((24 - Integer.parseInt(m.group(1))) % 16));
            o.set(Build.kx(k), Build.ky(k), 2 * z0 - Build.kz(k), v);
        }
        for (var m : b.markers) o.mark(m.type(), m.x(), m.y(), 2 * z0 - m.z(), m.arg(), m.yaw());
        o.groundOverride.putAll(b.groundOverride);
        return o;
    }

    private static int between(Random r, int a, int b) {
        return a + r.nextInt(b - a + 1);
    }

    private static double noise(Random r, double[] ph, double ang) {
        return Math.sin(ang * 3 + ph[0]) * 0.12 + Math.sin(ang * 5 + ph[1]) * 0.08 + Math.sin(ang * 7 + ph[2]) * 0.05;
    }

    private static void treasure(Build out, int x, int y, int z, String facing) {
        out.set(x, y, z, "chest[facing=" + facing + "]");
        out.mark("tesoro", x, y, z);
    }

    private static void spawner(Build out, int x, int y, int z, String mob) {
        out.set(x, y, z, "spawner");
        out.mark("spawner", x, y, z, mob, 0);
    }

    private static void chest(Build out, int x, int y, int z, String facing) {
        out.set(x, y, z, "chest[facing=" + facing + "]");
    }

    /** Columna de piedra con escalera para subir a lo aéreo. */
    private static void ladderPillar(Build out, Terrain t, int x, int z, int topY, String block) {
        int g = out.ground(t, x, z);
        for (int y = g; y <= topY; y++) {
            out.set(x, y, z, block);
            out.set(x, y, z - 1, "ladder[facing=north]");
        }
        out.set(x, g + 1, z - 2, "air");
        for (int y = g + 4; y < topY; y += 8) out.set(x + 1, y, z, "lantern");
        out.mark("entrada", x, g + 1, z - 2);
    }

    /** Cuerpo de una isla flotante: tapa de pasto y cono de roca hacia abajo, con minerales. */
    private static int island(Build out, Random r, int cx, int top, int cz, double rad, String grass, String dirt, String stone, boolean ores) {
        double[] ph = {r.nextDouble() * 6, r.nextDouble() * 6, r.nextDouble() * 6};
        int maxDepth = (int) (rad * 1.3);
        int low = top;
        for (int x = (int) -rad - 2; x <= rad + 2; x++)
            for (int z = (int) -rad - 2; z <= rad + 2; z++) {
                double ang = Math.atan2(z, x), d = Math.hypot(x, z) / (rad * (1 + noise(r, ph, ang)));
                if (d > 1) continue;
                int depth = (int) Math.round(maxDepth * Math.pow(1 - d, 0.75)) + 1;
                for (int k = 0; k < depth; k++) {
                    int y = top - k;
                    String b = k == 0 ? grass : k < 3 ? dirt : stone;
                    if (ores && k > 3 && r.nextInt(40) == 0) b = r.nextInt(4) == 0 ? "diamond_ore" : r.nextBoolean() ? "gold_ore" : "iron_ore";
                    out.set(cx + x, y, cz + z, b);
                    low = Math.min(low, y);
                }
                if (grass.equals("grass_block") && r.nextInt(6) == 0 && d < 0.9) out.soft(cx + x, top + 1, cz + z, r.nextBoolean() ? "short_grass" : r.nextBoolean() ? "poppy" : "azure_bluet");
            }
        // raíces y lianas colgando
        for (int i = 0; i < rad * 2; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = rad * (0.4 + r.nextDouble() * 0.5);
            int x = cx + (int) (Math.cos(a) * d), z = cz + (int) (Math.sin(a) * d);
            int y = top - (int) Math.round(maxDepth * Math.pow(1 - d / rad, 0.75)) - 1;
            for (int k = 0; k < between(r, 2, 6); k++) out.soft(x, y - k, z, r.nextBoolean() ? "hanging_roots" : "cave_vines[age=1,berries=" + r.nextBoolean() + "]");
        }
        return low;
    }

    private static void tree(Build out, Random r, int x, int y, int z, String log, String leaves, int h) {
        for (int k = 1; k <= h; k++) out.set(x, y + k, z, log + "[axis=y]");
        int rr = 3;
        for (int dx = -rr; dx <= rr; dx++)
            for (int dz = -rr; dz <= rr; dz++)
                for (int dy = -2; dy <= 2; dy++)
                    if (dx * dx + dz * dz + dy * dy * 2 <= rr * rr + 1 && r.nextInt(8) > 0) out.soft(x + dx, y + h + dy, z + dz, leaves + "[persistent=true]");
    }

    // ------------------------------------------------------------------ aéreas

    static Build floatingIsland(Terrain t, Random r, Style s, int x, int z) {
        Build out = new Build();
        int g = out.ground(t, x, z);
        int top = g + between(r, 36, 48);
        double rad = between(r, 9, 12);
        island(out, r, x, top, z, rad, "grass_block", "dirt", "stone", true);
        // islitas alrededor
        for (int i = 0; i < 3; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            int ix = x + (int) (Math.cos(a) * (rad + 6)), iz = z + (int) (Math.sin(a) * (rad + 6));
            island(out, r, ix, top + between(r, -6, 5), iz, between(r, 2, 4), "grass_block", "dirt", "stone", true);
        }
        tree(out, r, x + 3, top, z + 2, "oak_log", "oak_leaves", between(r, 5, 7));
        // ruinas de un santuario
        for (int[] p : new int[][]{{-4, -3}, {-4, 1}, {-1, -3}, {-1, 1}}) {
            int hh = between(r, 2, 4);
            for (int k = 1; k <= hh; k++) out.set(x + p[0], top + k, z + p[1], r.nextBoolean() ? "mossy_stone_bricks" : "stone_bricks");
        }
        out.set(x - 2, top + 1, z - 1, "chiseled_stone_bricks");
        out.set(x - 2, top + 2, z - 1, "lantern");
        chest(out, x - 3, top + 1, z - 1, "east");
        // cámara secreta dentro de la roca: se llega cavando bajo el bloque de musgo
        int cy = top - 6;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 0; dy <= 2; dy++) out.set(x + dx, cy + dy, z + dz, "air");
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) out.set(x + dx, cy - 1, z + dz, "gold_block");
        treasure(out, x, cy, z + 1, "north");
        out.set(x - 1, cy, z + 1, "candle[candles=3,lit=true]");
        out.set(x + 1, cy, z + 1, "candle[candles=3,lit=true]");
        out.set(x, top, z - 2, "moss_block");    // la pista
        spawner(out, x + 5, top + 1, z - 4, "phantom");
        out.mark("enemigo", x + 2, top + 1, z - 3);
        ladderPillar(out, t, x - (int) rad + 2, z + 1, top, "stone_bricks");
        out.set(x - (int) rad + 2, top + 1, z + 1, "air");
        return out;
    }

    static Build skyCitadel(Terrain t, Random r, Style s, int x, int z) {
        Build out = new Build();
        String a = r.nextBoolean() ? "quartz_bricks" : "prismarine_bricks", b = a.equals("quartz_bricks") ? "smooth_quartz" : "dark_prismarine", glass = a.equals("quartz_bricks") ? "light_blue_stained_glass" : "cyan_stained_glass";
        int g = out.ground(t, x, z);
        int base = g + between(r, 40, 50);
        int n = between(r, 3, 5);
        List<int[]> isl = new ArrayList<>();
        isl.add(new int[]{x, base + 6, z, 10});
        for (int i = 0; i < n; i++) {
            double ang = Math.PI * 2 * i / n + r.nextDouble() * 0.5;
            int d = between(r, 20, 26);
            isl.add(new int[]{x + (int) (Math.cos(ang) * d), base + between(r, -4, 4), z + (int) (Math.sin(ang) * d), between(r, 5, 7)});
        }
        for (int[] p : isl) island(out, r, p[0], p[1], p[2], p[3], b, "calcite", "end_stone", false);
        // puentes de las islas al centro
        for (int i = 1; i < isl.size(); i++) {
            int[] p = isl.get(i), c = isl.get(0);
            int steps = (int) Math.hypot(c[0] - p[0], c[2] - p[2]);
            for (int k = 0; k <= steps; k++) {
                int bx = p[0] + (c[0] - p[0]) * k / steps, bz = p[2] + (c[2] - p[2]) * k / steps, by = p[1] + (c[1] - p[1]) * k / steps;
                double px = -(c[2] - p[2]) / (double) steps, pz = (c[0] - p[0]) / (double) steps;
                for (int w = -1; w <= 1; w++) out.soft((int) Math.round(bx + px * w), by, (int) Math.round(bz + pz * w), a.replace("_bricks", "_slab").replace("quartz_slab", "quartz_slab") + "[type=top]");
                if (k % 4 == 0) {
                    out.soft((int) Math.round(bx + px * 2), by + 1, (int) Math.round(bz + pz * 2), "end_rod[facing=up]");
                    out.soft((int) Math.round(bx - px * 2), by + 1, (int) Math.round(bz - pz * 2), "end_rod[facing=up]");
                }
            }
            // torrecita en cada isla con generador
            int tx = p[0], tz = p[2], ty = p[1];
            for (int y = ty + 1; y <= ty + 6; y++)
                for (int dx = -2; dx <= 2; dx++)
                    for (int dz = -2; dz <= 2; dz++) {
                        boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                        out.set(tx + dx, y, tz + dz, edge ? (y == ty + 3 && (dx == 0 || dz == 0) ? glass : a) : "air");
                    }
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) out.set(tx + dx, ty + 7, tz + dz, b);
            out.set(tx, ty + 1, tz - 2, "air");
            out.set(tx, ty + 2, tz - 2, "air");
            spawner(out, tx, ty + 1, tz, i % 2 == 0 ? "blaze" : "vex");
            chest(out, tx + 1, ty + 1, tz + 1, "north");
            out.set(tx, ty + 8, tz, "end_rod[facing=up]");
            out.mark("enemigo", tx + 3, ty + 1, tz);
        }
        // torreón central
        int[] c = isl.get(0);
        int ky = c[1];
        for (int f = 0; f < 3; f++) {
            int y0 = ky + f * 6;
            for (int y = y0 + 1; y <= y0 + 6; y++)
                for (int dx = -5; dx <= 5; dx++)
                    for (int dz = -5; dz <= 5; dz++) {
                        double d = Math.hypot(dx, dz);
                        if (d > 5.4) continue;
                        out.set(c[0] + dx, y, c[2] + dz, d > 4.5 ? (y == y0 + 3 && (dx == 0 || dz == 0) ? glass : a) : (y == y0 + 6 ? b : "air"));
                    }
            for (int i = 0; i < 6; i++) {
                double ang = Math.PI * 2 * (i + f * 6) / 12.0;
                out.set(c[0] + (int) Math.round(Math.cos(ang) * 3.5), y0 + 1 + i, c[2] + (int) Math.round(Math.sin(ang) * 3.5), a);
                out.set(c[0] + (int) Math.round(Math.cos(ang) * 3.5), y0 + 6, c[2] + (int) Math.round(Math.sin(ang) * 3.5), i == 5 ? b : "air");
            }
            out.mark("enemigo", c[0] + 2, y0 + 1, c[2]);
            if (f == 1) spawner(out, c[0] - 2, y0 + 1, c[2] - 1, "vex");
        }
        int topY = ky + 18;
        for (int dx = -5; dx <= 5; dx++)
            for (int dz = -5; dz <= 5; dz++) {
                double d = Math.hypot(dx, dz);
                if (d <= 5.4 && d > 4.5) {
                    out.set(c[0] + dx, topY + 1, c[2] + dz, a);
                    if ((dx + dz) % 2 == 0) out.set(c[0] + dx, topY + 2, c[2] + dz, "end_rod[facing=up]");
                }
            }
        for (int dz = -5; dz <= 5; dz++) for (int y = ky + 1; y <= ky + 3; y++) if (Math.abs(dz) <= 1) out.set(c[0], y, c[2] - 5, "air");
        // trono y bóveda escondida debajo
        out.set(c[0], topY + 1, c[2] + 2, a.replace("_bricks", "_stairs").replace("quartz_stairs", "quartz_stairs") + "[facing=south,half=bottom]");
        out.set(c[0] - 1, topY + 1, c[2] + 2, "gold_block");
        out.set(c[0] + 1, topY + 1, c[2] + 2, "gold_block");
        out.mark("jefe", c[0], topY + 1, c[2]);
        int vy = ky - 4;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 0; dy <= 2; dy++) out.set(c[0] + dx, vy + dy, c[2] + dz, "air");
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) out.set(c[0] + dx, vy - 1, c[2] + dz, Math.abs(dx) + Math.abs(dz) < 2 ? "diamond_block" : "gold_block");
        treasure(out, c[0], vy, c[2] + 1, "north");
        treasure(out, c[0] - 1, vy, c[2] - 1, "south");
        out.set(c[0] + 1, vy, c[2] - 1, "soul_lantern");
        // escalera desde el suelo hasta una isla de afuera
        int[] e = isl.get(1);
        ladderPillar(out, t, e[0] + 4, e[2] + 1, e[1], a);
        out.set(e[0] + 4, e[1] + 1, e[2] + 1, "air");
        return out;
    }

    static Build flyingGalleon(Terrain t, Random r, Style s, int x, int z) {
        Build out = new Build();
        int g = out.ground(t, x, z);
        int deck = g + between(r, 34, 44);
        int L = between(r, 28, 34), half = L / 2;
        String hull = "dark_oak_planks", trim = "stripped_dark_oak_log", deckB = "spruce_planks";
        String sail = r.nextBoolean() ? "white_wool" : r.nextBoolean() ? "black_wool" : "red_wool";
        // casco: ancho según la posición, más angosto en proa y popa
        for (int i = -half; i <= half; i++) {
            double f = 1 - Math.pow(Math.abs(i) / (double) (half + 1), 2.2);
            int w = Math.max(1, (int) Math.round(6 * f));
            int depth = Math.max(2, (int) Math.round(7 * f));
            for (int k = 0; k <= depth; k++) {
                int ww = Math.max(0, w - (k > depth - 3 ? (k - (depth - 3)) : 0));
                for (int j = -ww; j <= ww; j++) {
                    boolean shell = Math.abs(j) == ww || k == depth;
                    out.set(x + i, deck - k, z + j, shell ? (k == 1 ? trim + "[axis=x]" : hull) : (k == 0 ? deckB : "air"));
                }
            }
            for (int j = -w; j <= w; j++) out.set(x + i, deck, z + j, deckB);
            out.set(x + i, deck + 1, z - w, "dark_oak_fence");
            out.set(x + i, deck + 1, z + w, "dark_oak_fence");
            // cañones
            if (i % 5 == 0 && Math.abs(i) < half - 4) {
                out.set(x + i, deck - 2, z - w, "dispenser[facing=north]");
                out.set(x + i, deck - 2, z + w, "dispenser[facing=south]");
            }
        }
        // proa y popa
        for (int k = 1; k <= 4; k++) out.set(x + half + k, deck + k / 2, z, k == 4 ? "lantern" : trim + "[axis=x]");
        int px = x - half + 2;
        for (int dx = 0; dx < 7; dx++)
            for (int dz = -3; dz <= 3; dz++)
                for (int y = deck + 1; y <= deck + 4; y++) {
                    boolean edge = dx == 0 || dx == 6 || Math.abs(dz) == 3;
                    out.set(px + dx, y, z + dz, edge ? (y == deck + 3 && dx == 6 && dz == 0 ? "glass_pane" : hull) : "air");
                }
        for (int dx = -1; dx < 8; dx++) for (int dz = -4; dz <= 4; dz++) out.set(px + dx, deck + 5, z + dz, "dark_oak_slab[type=bottom]");
        out.set(px + 6, deck + 1, z, "dark_oak_door[facing=west,half=lower,hinge=left]");
        out.set(px + 6, deck + 2, z, "dark_oak_door[facing=west,half=upper,hinge=left]");
        out.mark("jefe", px + 3, deck + 1, z);
        chest(out, px + 1, deck + 1, z - 2, "east");
        out.set(px + 1, deck + 1, z + 2, "cartography_table");
        out.set(px + 3, deck + 4, z, "lantern[hanging=true]");
        // tesoro del capitán: bajo una tabla suelta de la cabina (hay que romper el piso)
        out.set(px + 3, deck, z + 1, "dark_oak_planks");
        out.set(px + 3, deck - 1, z + 1, "air");
        treasure(out, px + 3, deck - 2, z + 1, "north");
        // mástiles y velas
        for (int m : new int[]{-3, 6}) {
            int mh = m < 0 ? 16 : 13;
            for (int y = deck + 1; y <= deck + mh; y++) out.set(x + m, y, z, "dark_oak_log[axis=y]");
            for (int y = deck + 4; y <= deck + mh - 2; y++) {
                int w = 4 - Math.abs(y - (deck + mh / 2 + 1)) / 3;
                for (int j = -w; j <= w; j++) out.set(x + m + 1, y, z + j, sail);
            }
            for (int j = -5; j <= 5; j++) out.set(x + m, deck + mh - 1, z + j, "dark_oak_fence");
            out.set(x + m, deck + mh + 1, z, "black_banner[rotation=4]");
        }
        // bodega con generadores
        for (int i = -half + 9; i < half - 3; i += 6) {
            spawner(out, x + i, deck - 3, z, r.nextBoolean() ? "skeleton" : "zombie");
            chest(out, x + i + 1, deck - 3, z + 2, "north");
            out.mark("enemigo", x + i - 1, deck - 3, z - 1);
        }
        for (int i = -half + 8; i < half - 2; i += 8) out.set(x + i, deck - 1, z, "air");
        out.set(x + half - 6, deck, z, "dark_oak_trapdoor[facing=north,half=top,open=false]");
        out.set(x + half - 6, deck - 1, z, "air");
        // globos para que se entienda que flota
        for (int bx : new int[]{-8, 9}) {
            for (int dx = -3; dx <= 3; dx++)
                for (int dz = -3; dz <= 3; dz++)
                    for (int dy = -2; dy <= 3; dy++)
                        if (dx * dx + dz * dz + dy * dy * 1.6 <= 12) out.set(x + bx + dx, deck + 18 + dy, z + dz, (dy + 3) % 2 == 0 ? "red_wool" : "white_wool");
            for (int y = deck + 2; y <= deck + 15; y++) {
                out.soft(x + bx - 2, y, z - 3, "iron_chain[axis=y]");
                out.soft(x + bx + 2, y, z + 3, "iron_chain[axis=y]");
            }
        }
        out.mark("enemigo", x + 2, deck + 1, z + 2);
        out.mark("enemigo", x - 6, deck + 1, z - 2);
        ladderPillar(out, t, x + 2, z + 6, deck, "dark_oak_log[axis=y]");
        return out;
    }

    // ------------------------------------------------------------------ en tierra

    static Build giantTree(Terrain t, Random r, Style s, int x, int z) {
        Build out = new Build();
        int g = out.ground(t, x, z);
        String log = r.nextBoolean() ? "dark_oak_log" : "spruce_log", leaves = log.startsWith("dark") ? "dark_oak_leaves" : "spruce_leaves";
        if (s.id.equals("cerezo")) {
            log = "cherry_log";
            leaves = "cherry_leaves";
        }
        String planks = log.replace("_log", "_planks");
        int H = between(r, 26, 32), tr = 3;
        // raíces
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8 + r.nextDouble() * 0.3;
            for (int k = 0; k < 7; k++) {
                int rx = x + (int) Math.round(Math.cos(a) * (tr + k)), rz = z + (int) Math.round(Math.sin(a) * (tr + k));
                int rg = out.ground(t, rx, rz);
                for (int y = rg - 2; y <= rg + Math.max(0, 3 - k / 2); y++) out.set(rx, y, rz, log + "[axis=y]");
            }
        }
        // tronco hueco con escalera en espiral
        for (int y = g - 2; y <= g + H; y++)
            for (int dx = -tr; dx <= tr; dx++)
                for (int dz = -tr; dz <= tr; dz++) {
                    double d = Math.hypot(dx, dz);
                    if (d > tr + 0.4) continue;
                    out.set(x + dx, y, z + dz, d > tr - 0.6 ? log + "[axis=y]" : "air");
                }
        for (int y = g + 1; y < g + H - 2; y++) {
            double a = (y - g) * 0.55;
            int sx = x + (int) Math.round(Math.cos(a) * 1.6), sz = z + (int) Math.round(Math.sin(a) * 1.6);
            out.set(sx, y, sz, planks.replace("_planks", "_slab") + "[type=top]");
            if ((y - g) % 6 == 0) out.set(x + (int) Math.round(Math.cos(a + 3) * 2.4), y + 1, z + (int) Math.round(Math.sin(a + 3) * 2.4), "shroomlight");
        }
        out.set(x, g + 1, z - tr, "air");
        out.set(x, g + 2, z - tr, "air");
        out.set(x, g, z, planks);
        // ramas y copa
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6 + r.nextDouble();
            int by = g + H - between(r, 2, 8);
            int len = between(r, 7, 11);
            int ex = x, ez = z, ey = by;
            for (int k = tr; k <= len; k++) {
                ex = x + (int) Math.round(Math.cos(a) * k);
                ez = z + (int) Math.round(Math.sin(a) * k);
                ey = by + k / 3;
                out.set(ex, ey, ez, log + "[axis=" + (Math.abs(Math.cos(a)) > 0.7 ? "x" : "z") + "]");
            }
            blob(out, r, ex, ey + 1, ez, between(r, 4, 5), leaves);
        }
        blob(out, r, x, g + H + 2, z, 8, leaves);
        // casas en las ramas
        for (int i = 0; i < 3; i++) {
            double a = Math.PI * 2 * i / 3 + 0.4;
            int hx = x + (int) Math.round(Math.cos(a) * 7), hz = z + (int) Math.round(Math.sin(a) * 7), hy = g + 8 + i * 6;
            for (int dx = -3; dx <= 3; dx++)
                for (int dz = -3; dz <= 3; dz++) {
                    out.set(hx + dx, hy, hz + dz, planks);
                    if (Math.abs(dx) == 3 || Math.abs(dz) == 3) out.set(hx + dx, hy + 1, hz + dz, log.replace("_log", "_fence"));
                }
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++)
                    for (int y = hy + 1; y <= hy + 4; y++) {
                        boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                        out.set(hx + dx, y, hz + dz, edge ? (y == hy + 2 && (dx == 0 || dz == 0) ? "glass_pane" : planks) : "air");
                    }
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) out.set(hx + dx, hy + 5, hz + dz, log.replace("_log", "_slab") .replace("_slab", "_slab") + "[type=bottom]");
            int dirx = (int) Math.signum(x - hx), dirz = (int) Math.signum(z - hz);
            out.set(hx + 2 * dirx, hy + 1, hz + 2 * dirz, "air");
            out.set(hx + 2 * dirx, hy + 2, hz + 2 * dirz, "air");
            for (int k = 3; k <= 4; k++) out.set(hx + k * dirx, hy, hz + k * dirz, planks);
            chest(out, hx - dirx, hy + 1, hz - dirz, "north");
            out.set(hx + dirx, hy + 4, hz + dirz, "lantern[hanging=true]");
        }
        // tesoro enterrado entre las raíces
        treasure(out, x + 2, g - 3, z + 5, "north");
        out.set(x + 2, g - 2, z + 5, "coarse_dirt");
        out.set(x + 2, g - 1, z + 5, "coarse_dirt");
        out.set(x + 2, g + 1, z + 5, "brown_mushroom");
        out.mark("entrada", x, g + 1, z - tr - 2);
        return out;
    }

    private static void blob(Build out, Random r, int x, int y, int z, int rad, String leaves) {
        for (int dx = -rad; dx <= rad; dx++)
            for (int dz = -rad; dz <= rad; dz++)
                for (int dy = -rad / 2; dy <= rad / 2 + 1; dy++)
                    if (dx * dx + dz * dz + dy * dy * 3 <= rad * rad && r.nextInt(7) > 0) out.soft(x + dx, y + dy, z + dz, leaves + "[persistent=true]");
    }

    static Build pyramid(Terrain t, Random r, Style s, int x, int z) {
        Build out = new Build();
        int half = between(r, 15, 20);
        int g = Integer.MAX_VALUE;
        for (int dx = -half; dx <= half; dx += 4) for (int dz = -half; dz <= half; dz += 4) g = Math.min(g, out.ground(t, x + dx, z + dz));
        String a = "sandstone", b = "cut_sandstone", c = "smooth_sandstone";
        for (int lvl = 0; lvl <= half; lvl++) {
            int y = g + lvl, h = half - lvl;
            for (int dx = -h; dx <= h; dx++)
                for (int dz = -h; dz <= h; dz++) {
                    boolean shell = Math.abs(dx) >= h - 1 || Math.abs(dz) >= h - 1 || lvl == 0;
                    out.set(x + dx, y, z + dz, shell ? (lvl % 4 == 3 ? "chiseled_sandstone" : (Math.abs(dx) == h || Math.abs(dz) == h) && lvl % 2 == 1 ? b : a) : "air");
                }
        }
        // cimientos hasta el suelo
        for (int dx = -half; dx <= half; dx++)
            for (int dz = -half; dz <= half; dz++)
                for (int y = out.ground(t, x + dx, z + dz) + 1; y < g; y++) out.set(x + dx, y, z + dz, a);
        out.set(x, g + half + 1, z, "gold_block");
        // entrada y pasillo
        for (int k = -half; k <= 0; k++) for (int y = g + 1; y <= g + 3; y++) for (int w = -1; w <= 1; w++) out.set(x + w, y, z + k, "air");
        for (int y = g + 1; y <= g + 5; y++) {
            out.set(x - 2, y, z - half, "chiseled_sandstone");
            out.set(x + 2, y, z - half, "chiseled_sandstone");
        }
        out.mark("entrada", x, g + 1, z - half - 2);
        // salas laterales con generadores
        for (int side : new int[]{-1, 1}) {
            int sx = x + side * (half / 2);
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int y = g + 1; y <= g + 4; y++) out.set(sx + dx, y, z + dz, "air");
            for (int k = 0; k <= half / 2; k++) for (int y = g + 1; y <= g + 2; y++) out.set(x + side * k, y, z, "air");
            spawner(out, sx, g + 1, z, "husk");
            chest(out, sx + side * 3, g + 1, z + 3, "north");
            out.set(sx, g + 4, z, "soul_lantern[hanging=true]");
            out.mark("enemigo", sx - side, g + 1, z - 2);
        }
        // sala central con jefe y la cámara sellada debajo (marcada con terracota azul, como las de verdad)
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) for (int y = g + 1; y <= g + 7; y++) out.set(x + dx, y, z + dz, "air");
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) out.set(x + dx, g, z + dz, Math.abs(dx) + Math.abs(dz) <= 1 ? "blue_terracotta" : "orange_terracotta");
        out.mark("jefe", x, g + 1, z + 2);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int y = g - 6; y <= g - 2; y++) out.set(x + dx, y, z + dz, "air");
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            out.set(x + dx, g - 7, z + dz, "cut_sandstone");
            if (Math.abs(dx) == 3 || Math.abs(dz) == 3) for (int y = g - 6; y <= g - 1; y++) out.set(x + dx, y, z + dz, c);
        }
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) out.set(x + dx, g - 1, z + dz, c);
        treasure(out, x - 2, g - 6, z, "east");
        treasure(out, x + 2, g - 6, z, "west");
        treasure(out, x, g - 6, z + 2, "north");
        out.set(x, g - 6, z, "gold_block");
        out.set(x, g - 5, z, "skeleton_skull[rotation=8]");
        return mirrorZ(out, z);
    }

    static Build volcano(Terrain t, Random r, Style s, int x, int z) {
        Build out = new Build();
        int R = between(r, 20, 24), H = between(r, 22, 28), crater = 5;
        int g = out.ground(t, x, z);
        double[] ph = {r.nextDouble() * 6, r.nextDouble() * 6, r.nextDouble() * 6};
        for (int dx = -R - 2; dx <= R + 2; dx++)
            for (int dz = -R - 2; dz <= R + 2; dz++) {
                double ang = Math.atan2(dz, dx), d = Math.hypot(dx, dz) / (R * (1 + noise(r, ph, ang)));
                if (d > 1) continue;
                int top = g + (int) Math.round(H * Math.pow(1 - d, 1.25));
                int gg = out.ground(t, x + dx, z + dz);
                double dc = Math.hypot(dx, dz);
                for (int y = Math.min(gg, g) - 1; y <= top; y++) {
                    String b = y == top ? (r.nextInt(5) == 0 ? "magma_block" : r.nextBoolean() ? "basalt[axis=y]" : "blackstone") : r.nextInt(9) == 0 ? "tuff" : r.nextBoolean() ? "blackstone" : "basalt[axis=y]";
                    if (dc < crater && y > g + H - 6) b = y <= g + H - 3 ? "lava" : "air";
                    out.set(x + dx, y, z + dz, b);
                }
                // ríos de lava (secos: magma) bajando por la ladera
                if (Math.abs(Math.sin(ang * 4 + ph[0])) < 0.05 && d > 0.25) out.set(x + dx, top, z + dz, "magma_block");
            }
        // túnel de lava (hueco) hasta la bóveda: se entra por una grieta al pie
        double a = r.nextDouble() * Math.PI * 2;
        int ex = x + (int) Math.round(Math.cos(a) * (R - 2)), ez = z + (int) Math.round(Math.sin(a) * (R - 2));
        int vy = g + 2;
        for (int k = R - 1; k >= 3; k--) {
            int tx = x + (int) Math.round(Math.cos(a) * k), tz = z + (int) Math.round(Math.sin(a) * k);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int y = vy; y <= vy + 2; y++) out.set(tx + dx, y, tz + dz, "air");
            if (k % 5 == 0) out.set(tx, vy + 3, tz, "shroomlight");
        }
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) for (int y = vy; y <= vy + 5; y++) out.set(x + dx, y, z + dz, Math.hypot(dx, dz) <= 4.3 ? "air" : "blackstone");
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) if (Math.hypot(dx, dz) <= 4.3) out.set(x + dx, vy - 1, z + dz, "polished_blackstone_bricks");
        spawner(out, x - 2, vy, z + 2, "magma_cube");
        spawner(out, x + 2, vy, z - 2, "blaze");
        out.mark("jefe", x, vy, z);
        out.mark("enemigo", x + 2, vy, z + 2);
        out.set(x - 3, vy + 4, z, "shroomlight");
        out.set(x + 3, vy + 4, z, "shroomlight");
        // la bóveda: detrás de la pared del fondo (hay que romper la piedra negra)
        int bx = x - (int) Math.round(Math.cos(a) * 7), bz = z - (int) Math.round(Math.sin(a) * 7);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int y = vy; y <= vy + 2; y++) out.set(bx + dx, y, bz + dz, "air");
        out.set(bx, vy - 1, bz, "ancient_debris");
        treasure(out, bx, vy, bz, "north");
        out.set(bx + 1, vy, bz, "gilded_blackstone");
        out.mark("entrada", ex, out.ground(t, ex, ez) + 1, ez);
        return out;
    }

    static Build giantSkull(Terrain t, Random r, Style s, int x, int z) {
        Build out = new Build();
        int g = out.ground(t, x, z);
        int R = between(r, 9, 11);
        int cy = g + R - 2;
        // cráneo: esfera alargada hueca
        for (int dx = -R; dx <= R; dx++)
            for (int dz = -R - 2; dz <= R; dz++)
                for (int dy = -R; dy <= R; dy++) {
                    double e = (dx * dx) / (double) (R * R) + (dz * dz) / (double) ((R + 1) * (R + 1)) + (dy * dy) / (double) ((R - 1) * (R - 1));
                    if (e > 1) continue;
                    int y = cy + dy;
                    if (y < g - 1) continue;
                    String b = e > 0.72 ? (r.nextInt(6) == 0 ? "calcite" : "bone_block[axis=y]") : "air";
                    out.set(x + dx, y, z + dz, b);
                }
        // mandíbula y dientes
        for (int dx = -R + 2; dx <= R - 2; dx++)
            for (int dz = -R - 3; dz <= -R + 2; dz++)
                for (int y = g - 1; y <= g + 1; y++) out.set(x + dx, y, z + dz, "bone_block[axis=y]");
        for (int dx = -R + 3; dx <= R - 3; dx++) {
            if (Math.abs(dx) <= 3) {
                for (int y = g + 2; y <= g + 5; y++) out.set(x + dx, y, z - R - 1, "air");
                for (int y = g + 2; y <= g + 5; y++) out.set(x + dx, y, z - R, "air");
            }
            if (dx % 2 == 0) {
                out.set(x + dx, g + 5, z - R - 1, "quartz_pillar[axis=y]");
                out.set(x + dx, g + 2, z - R - 1, "quartz_pillar[axis=y]");
            }
        }
        // ojos (uno esconde el tesoro)
        int eyY = cy + 2;
        for (int side : new int[]{-1, 1}) {
            int ex = x + side * 4;
            for (int dx = -2; dx <= 2; dx++)
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -R - 1; dz <= -R + 3; dz++) out.set(ex + dx, eyY + dy, z + dz, "air");
                    out.set(ex + dx, eyY + dy, z - R + 4, side < 0 ? "crying_obsidian" : "black_concrete");
                }
            out.set(ex, eyY, z - R + 3, side < 0 ? "soul_lantern" : "redstone_lamp[lit=true]");
        }
        treasure(out, x + 4, eyY, z - R + 5, "north");
        out.set(x + 4, eyY, z - R + 4, "black_concrete");
        // interior
        for (int dx = -R + 2; dx <= R - 2; dx++) for (int dz = -R + 1; dz <= R - 2; dz++) if (dx * dx + dz * dz < (R - 2) * (R - 2)) out.set(x + dx, g, z + dz, r.nextInt(4) == 0 ? "bone_block[axis=y]" : "soul_soil");
        spawner(out, x - 3, g + 1, z + 2, "skeleton");
        spawner(out, x + 3, g + 1, z + 2, "wither_skeleton");
        chest(out, x, g + 1, z + R - 3, "north");
        out.mark("jefe", x, g + 1, z + 1);
        out.mark("enemigo", x - 2, g + 1, z - 2);
        out.mark("enemigo", x + 2, g + 1, z - 2);
        for (int i = 0; i < 6; i++) out.set(x + between(r, -5, 5), g + 1, z + between(r, -4, 5), i % 2 == 0 ? "soul_lantern" : "skeleton_skull[rotation=" + r.nextInt(16) + "]");
        out.mark("entrada", x, g + 2, z - R - 5);
        return mirrorZ(out, z);
    }

    static Build maze(Terrain t, Random r, Style s, int x, int z) {
        Build out = new Build();
        int cells = between(r, 8, 10), cs = 4;           // celdas de 3 de ancho + pared
        int size = cells * cs + 1, half = size / 2;
        boolean[][] open = new boolean[size][size];
        // laberinto perfecto (backtracking)
        boolean[][] vis = new boolean[cells][cells];
        java.util.ArrayDeque<int[]> st = new java.util.ArrayDeque<>();
        st.push(new int[]{0, 0});
        vis[0][0] = true;
        List<int[]> deadEnds = new ArrayList<>();
        while (!st.isEmpty()) {
            int[] c = st.peek();
            List<int[]> nb = new ArrayList<>();
            int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] dd : d) {
                int nx = c[0] + dd[0], nz = c[1] + dd[1];
                if (nx >= 0 && nz >= 0 && nx < cells && nz < cells && !vis[nx][nz]) nb.add(new int[]{nx, nz, dd[0], dd[1]});
            }
            for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) open[c[0] * cs + 1 + i][c[1] * cs + 1 + j] = true;
            if (nb.isEmpty()) {
                deadEnds.add(c);
                st.pop();
                continue;
            }
            int[] n = nb.get(r.nextInt(nb.size()));
            for (int k = 1; k <= cs; k++)
                for (int w = 0; w < 3; w++) {
                    int ox = c[0] * cs + 1 + (n[2] != 0 ? (n[2] > 0 ? 2 + k : 0 - k) : w);
                    int oz = c[1] * cs + 1 + (n[3] != 0 ? (n[3] > 0 ? 2 + k : 0 - k) : w);
                    if (ox >= 0 && oz >= 0 && ox < size && oz < size) open[ox][oz] = true;
                }
            vis[n[0]][n[1]] = true;
            st.push(new int[]{n[0], n[1]});
        }
        // centro abierto
        int mid = cells / 2;
        for (int i = -1; i < cs + 2; i++) for (int j = -1; j < cs + 2; j++) open[mid * cs + i][mid * cs + j] = true;
        // entrada
        for (int w = 0; w < 3; w++) open[1 + w][0] = true;
        String wall = s.id.equals("desierto") ? "sandstone" : r.nextBoolean() ? "oak_leaves[persistent=true]" : "azalea_leaves[persistent=true]";
        String floor = s.id.equals("desierto") ? "smooth_sandstone" : "moss_block";
        int g = Integer.MAX_VALUE;
        for (int i = 0; i < size; i += 3) for (int j = 0; j < size; j += 3) g = Math.min(g, out.ground(t, x - half + i, z - half + j));
        for (int i = 0; i < size; i++)
            for (int j = 0; j < size; j++) {
                int wx = x - half + i, wz = z - half + j;
                int gg = out.ground(t, wx, wz);
                for (int y = gg + 1; y < g; y++) out.set(wx, y, wz, "dirt");
                for (int y = g + 1; y <= gg + 2; y++) out.set(wx, y, wz, "air");
                out.set(wx, g, wz, open[i][j] ? (r.nextInt(6) == 0 ? "coarse_dirt" : floor) : "stone_bricks");
                if (!open[i][j]) for (int y = g + 1; y <= g + 4; y++) out.set(wx, y, wz, y == g + 1 ? "mossy_stone_bricks" : wall);
            }
        // centro: pabellón con el tesoro
        int mx = x - half + mid * cs + 2, mz = z - half + mid * cs + 2;
        for (int[] p : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) for (int y = g + 1; y <= g + 4; y++) out.set(mx + p[0], y, mz + p[1], "quartz_pillar[axis=y]");
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) out.set(mx + dx, g + 5, mz + dz, "smooth_quartz_slab[type=bottom]");
        out.set(mx, g + 4, mz, "lantern[hanging=true]");
        treasure(out, mx, g + 1, mz, "south");
        out.mark("jefe", mx, g + 1, mz - 2);
        // generadores en los callejones sin salida
        java.util.Collections.shuffle(deadEnds, r);
        int n = 0;
        for (int[] c : deadEnds) {
            if (n >= 4) break;
            if (Math.abs(c[0] - mid) <= 1 && Math.abs(c[1] - mid) <= 1) continue;
            int sx = x - half + c[0] * cs + 2, sz = z - half + c[1] * cs + 2;
            if (n % 2 == 0) spawner(out, sx, g + 1, sz, r.nextBoolean() ? "spider" : "zombie");
            else chest(out, sx, g + 1, sz, "north");
            out.mark("enemigo", sx, g + 1, sz + 1);
            n++;
        }
        out.mark("entrada", x - half + 2, g + 1, z - half - 2);
        return out;
    }

    static Build colossus(Terrain t, Random r, Style s, int x, int z) {
        Build out = new Build();
        int g = Integer.MAX_VALUE;
        for (int dx = -6; dx <= 6; dx += 3) for (int dz = -6; dz <= 6; dz += 3) g = Math.min(g, out.ground(t, x + dx, z + dz));
        String st = r.nextBoolean() ? "stone_bricks" : "polished_andesite", cl = r.nextBoolean() ? "gold_block" : "copper_block", acc = "chiseled_stone_bricks";
        // pedestal
        for (int dx = -6; dx <= 6; dx++)
            for (int dz = -6; dz <= 6; dz++) {
                int gg = out.ground(t, x + dx, z + dz);
                for (int y = gg + 1; y < g; y++) out.set(x + dx, y, z + dz, st);
                for (int y = g; y <= g + 4; y++) out.set(x + dx, y, z + dz, (Math.abs(dx) == 6 || Math.abs(dz) == 6) && y == g + 4 ? acc : st);
            }
        int b = g + 5;
        // piernas
        for (int side : new int[]{-1, 1})
            for (int y = b; y <= b + 7; y++)
                for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) out.set(x + side * 2 + (side < 0 ? -dx : dx), y, z + dz, y < b + 2 ? cl : st);
        // torso
        for (int y = b + 8; y <= b + 15; y++) for (int dx = -3; dx <= 3; dx++) for (int dz = -1; dz <= 2; dz++) out.set(x + dx, y, z + dz, y == b + 8 ? cl : st);
        // brazos: uno sostiene la espada hacia arriba
        for (int y = b + 9; y <= b + 15; y++) for (int dz = 0; dz <= 1; dz++) out.set(x - 4, y, z + dz, st);
        for (int y = b + 12; y <= b + 15; y++) for (int dz = 0; dz <= 1; dz++) out.set(x + 4, y, z + dz, st);
        for (int k = 0; k <= 3; k++) out.set(x + 5, b + 15, z - k, st);
        for (int y = b + 13; y <= b + 26; y++) out.set(x + 5, y, z - 4, y < b + 16 ? "dark_oak_log[axis=y]" : y == b + 16 ? cl : "iron_block");
        for (int dz = -6; dz <= -2; dz++) out.set(x + 5, b + 16, z + dz + 0, dz == -4 ? cl : cl);
        // cabeza y casco
        for (int y = b + 16; y <= b + 19; y++) for (int dx = -2; dx <= 2; dx++) for (int dz = -1; dz <= 2; dz++) out.set(x + dx, y, z + dz, y >= b + 18 ? cl : st);
        out.set(x - 1, b + 17, z - 1, "sea_lantern");
        out.set(x + 1, b + 17, z - 1, "sea_lantern");
        for (int y = b + 20; y <= b + 22; y++) out.set(x, y, z, "red_wool");
        // bóveda bajo el pedestal: se entra por una losa que suena hueca
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int y = g - 1; y <= g + 2; y++) out.set(x + dx, y, z + dz, "air");
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) out.set(x + dx, g - 2, z + dz, "gold_block");
        treasure(out, x, g - 1, z + 3, "north");
        treasure(out, x - 3, g - 1, z, "east");
        out.set(x + 3, g - 1, z, "lantern");
        out.set(x, g + 3, z - 6, acc);
        out.set(x, g + 3, z - 5, "air");
        out.set(x, g + 4, z - 6, "stone_brick_slab[type=bottom]");
        out.mark("entrada", x, g + 1, z - 8);
        return mirrorZ(out, z);
    }

    /** Forja legendaria: sala redonda con cúpula, lava, yunques y un altar con un arma especial garantizada. */
    static Build legendaryForge(Terrain t, Random r, Style s, int x, int z) {
        Build out = new Build();
        int g = Integer.MIN_VALUE;
        for (int dx = -9; dx <= 9; dx += 3) for (int dz = -9; dz <= 9; dz += 3) g = Math.max(g, out.ground(t, x + dx, z + dz));
        g = Math.min(g, out.ground(t, x, z) + 3) + 1;
        String wall = r.nextBoolean() ? "polished_blackstone_bricks" : "deepslate_bricks";
        String alt = wall.startsWith("deep") ? "cracked_deepslate_bricks" : "cracked_polished_blackstone_bricks";
        String roof = r.nextBoolean() ? "deepslate_tiles" : "blackstone";
        String gold = r.nextInt(3) == 0 ? "copper_block" : "gilded_blackstone";
        int R = 9, H = 7;
        // plataforma y cimientos
        for (int dx = -R - 2; dx <= R + 2; dx++)
            for (int dz = -R - 2; dz <= R + 2; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > R + 2.4) continue;
                int gg = out.ground(t, x + dx, z + dz);
                for (int y = gg + 1; y < g; y++) out.set(x + dx, y, z + dz, y < g - 1 ? "cobbled_deepslate" : wall);
                out.set(x + dx, g, z + dz, d > R + 1.2 ? "polished_blackstone" : d <= 2.5 ? gold : r.nextInt(7) == 0 ? "cracked_polished_blackstone_bricks" : "polished_blackstone");
                for (int y = g + 1; y <= g + H + 8; y++) out.set(x + dx, y, z + dz, "air");
            }
        // muros redondos con ventanas
        for (int dx = -R; dx <= R; dx++)
            for (int dz = -R; dz <= R; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < R - 0.5 || d > R + 0.5) continue;
                for (int y = g + 1; y <= g + H; y++) {
                    boolean window = (y == g + 3 || y == g + 4) && (Math.abs(dx) <= 1 || Math.abs(dz) <= 1) && dz > -R + 1;
                    out.set(x + dx, y, z + dz, window ? "iron_bars" : y == g + 1 || y == g + H ? gold.equals("copper_block") ? "cut_copper" : "chiseled_polished_blackstone" : r.nextInt(6) == 0 ? alt : wall);
                }
            }
        // cúpula
        for (int k = 0; k <= 5; k++) {
            double rr = R + 0.5 - k * 1.6;
            int y = g + H + 1 + k;
            for (int dx = -R - 1; dx <= R + 1; dx++)
                for (int dz = -R - 1; dz <= R + 1; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d <= rr && d > rr - 1.8) out.set(x + dx, y, z + dz, roof);
                    if (k == 5 && d <= rr) out.set(x + dx, y, z + dz, d < 1 ? "glass" : roof);
                }
        }
        // chimeneas
        int chim = 2 + r.nextInt(2);
        double a0 = r.nextDouble() * Math.PI * 2;
        for (int i = 0; i < chim; i++) {
            double a = a0 + i * Math.PI * 2 / chim;
            int cx = x + (int) Math.round(Math.cos(a) * 6), cz = z + (int) Math.round(Math.sin(a) * 6);
            if (cz < z - 4 && Math.abs(cx - x) <= 2) cz = z - 3;
            for (int y = g + 1; y <= g + H + 9; y++) {
                for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++) if (ox != 0 || oz != 0) out.set(cx + ox, y, cz + oz, y <= g + 2 ? "blast_furnace[facing=" + (ox < 0 ? "west" : ox > 0 ? "east" : oz < 0 ? "north" : "south") + "]" : "bricks");
                out.set(cx, y, cz, y == g + 1 ? "lava" : y == g + H + 9 ? "campfire" : "air");
            }
            out.set(cx, g, cz, wall);
            out.set(cx + 1, g + 2, cz, "iron_bars");
        }
        // ríos de lava en canales cruzados (con rejas encima para no caerse)
        for (int k = -6; k <= 6; k++) {
            if (Math.abs(k) <= 3) continue;
            for (int[] c : new int[][]{{k, 0}, {0, k}}) {
                if (c[1] < -3 && c[0] == 0) continue; // el paso de la entrada queda libre
                out.set(x + c[0], g - 1, z + c[1], wall);
                out.set(x + c[0], g, z + c[1], "lava");
                out.set(x + c[0], g + 1, z + c[1], "iron_bars");
            }
        }
        // estaciones de herrero junto al muro
        String[] stations = {"anvil[facing=north]", "chipped_anvil[facing=east]", "smithing_table", "grindstone[face=floor,facing=north]", "blast_furnace[facing=south]", "smoker[facing=south]", "barrel[facing=up]", "lava_cauldron", "anvil[facing=west]"};
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI * 2 / 12 + 0.26;
            int sx = x + (int) Math.round(Math.cos(a) * (R - 1.5)), sz = z + (int) Math.round(Math.sin(a) * (R - 1.5));
            if (sz < z - R + 3 && Math.abs(sx - x) <= 2) continue;
            out.set(sx, g + 1, sz, stations[r.nextInt(stations.length)]);
            if (r.nextInt(3) == 0) out.set(sx, g + 3, sz, "soul_lantern[hanging=false]");
        }
        // altar con el arma especial
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) out.set(x + dx, g + 1, z + dz, "polished_blackstone_bricks");
        out.set(x, g + 2, z, "chest[facing=north]");
        out.mark("arma_especial", x, g + 2, z, r.nextInt(3) == 0 ? "mitico" : "legendario", 0);
        for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            out.set(x + c[0], g + 1, z + c[1], "polished_blackstone_wall");
            out.set(x + c[0], g + 2, z + c[1], "polished_blackstone_wall");
            out.set(x + c[0], g + 3, z + c[1], "soul_lantern[hanging=false]");
        }
        for (int y = g + H + 1; y >= g + 4; y--) out.set(x, y, z, y == g + 4 ? "lantern[hanging=true]" : "iron_chain[axis=y]");
        // entrada con arco
        for (int y = g + 1; y <= g + 4; y++) for (int dx = -1; dx <= 1; dx++) out.set(x + dx, y, z - R, "air");
        out.set(x - 1, g + 4, z - R, "polished_blackstone_brick_stairs[facing=east,half=top]");
        out.set(x + 1, g + 4, z - R, "polished_blackstone_brick_stairs[facing=west,half=top]");
        out.set(x - 2, g + 5, z - R - 1, "lantern");
        out.set(x + 2, g + 5, z - R - 1, "lantern");
        for (int y = g + 1; y <= g + 4; y++) { out.set(x - 2, y, z - R - 1, wall); out.set(x + 2, y, z - R - 1, wall); }
        out.mark("entrada", x, g + 1, z - R - 3);
        // generadores y enemigos
        int sp = 2 + r.nextInt(2);
        String[] mobs = {"blaze", "wither_skeleton", "magma_cube", "skeleton"};
        for (int i = 0; i < sp; i++) {
            double a = a0 + Math.PI / chim + i * Math.PI * 2 / sp;
            int sx = x + (int) Math.round(Math.cos(a) * 5), sz = z + (int) Math.round(Math.sin(a) * 5);
            if (sz < z - 3 && Math.abs(sx - x) <= 1) sx += 3;
            spawner(out, sx, g + 1, sz, mobs[r.nextInt(mobs.length)]);
        }
        for (int i = 0; i < 5; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            out.mark("enemigo", x + (int) Math.round(Math.cos(a) * 4), g + 1, z + (int) Math.round(Math.sin(a) * 4));
        }
        out.mark("jefe", x, g + 1, z + 3);
        // tesoro escondido bajo el suelo: trampilla junto a un yunque, escalera hacia abajo
        int hx = x + (r.nextBoolean() ? 5 : -5), hz = z + 4;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            for (int y = g - 4; y <= g - 2; y++) out.set(hx + dx, y, hz + dz, Math.abs(dx) == 2 || Math.abs(dz) == 2 ? "cobbled_deepslate" : "air");
            out.set(hx + dx, g - 5, hz + dz, "gold_block");
            out.set(hx + dx, g - 1, hz + dz, "cobbled_deepslate");
        }
        out.set(hx, g, hz - 2, "spruce_trapdoor[facing=south,half=top]");
        out.set(hx, g - 1, hz - 2, "air");
        for (int y = g - 4; y <= g - 1; y++) out.set(hx, y, hz - 3, "cobbled_deepslate");
        for (int y = g - 4; y <= g - 1; y++) out.set(hx, y, hz - 2, "ladder[facing=south]");
        treasure(out, hx, g - 4, hz + 1, "north");
        treasure(out, hx - 1, g - 4, hz + 1, "north");
        out.set(hx + 1, g - 4, hz, "lantern");
        return out;
    }
}
