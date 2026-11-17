package pe.kydium.eclipserpg.gen;

import java.util.List;
import java.util.Map;
import java.util.Random;

/** Paleta de materiales de un estilo (llanura, taiga, desierto, cerezo…). */
public final class Style {
    public String id, name;
    public String planks, log, stripped, wood;       // madera
    public String wall, wall2;                       // relleno de paredes
    public String foundation, foundation2;           // cimientos
    public String floor;
    public String roof;                              // prefijo: roof + "_stairs", roof + "_slab"
    public String roofFull;                          // bloque lleno del techo
    public boolean flatRoofs;                        // desierto: techos planos
    public String path, path2;                       // caminos
    public String fence, gate, door, trapdoor;
    public String window = "glass_pane";
    public String light = "lantern";
    public String stone, stone2, stoneStairs, stoneSlab, stoneWall; // muros y torres
    public String accent, accent2;                   // toldos, detalles (lana, terracota)
    public String bed = "red";
    public String crop = "wheat";
    public String leaves = "oak_leaves";
    public String flower1 = "poppy", flower2 = "dandelion";

    public String stairs(String facing, boolean top) {
        return roof + "_stairs[facing=" + facing + ",half=" + (top ? "top" : "bottom") + "]";
    }

    public String woodStairs(String facing) {
        return planks.replace("_planks", "_stairs") + "[facing=" + facing + ",half=bottom]";
    }

    public String slab(boolean top) {
        return roof + "_slab[type=" + (top ? "top" : "bottom") + "]";
    }

    public String logY() {
        return log + "[axis=y]";
    }

    public String logX() {
        return log + "[axis=x]";
    }

    public String logZ() {
        return log + "[axis=z]";
    }

    public String wallMix(Random r) {
        return wall2 != null && r.nextInt(5) == 0 ? wall2 : wall;
    }

    public String foundationMix(Random r) {
        return foundation2 != null && r.nextInt(3) == 0 ? foundation2 : foundation;
    }

    public String pathMix(Random r) {
        return path2 != null && r.nextInt(4) == 0 ? path2 : path;
    }

    public String stoneMix(Random r) {
        return stone2 != null && r.nextInt(4) == 0 ? stone2 : stone;
    }

    private static Style make(String id, String name, String wood, String wall, String wall2, String foundation, String foundation2,
                              String roof, String roofFull, String path, String path2, String stone, String stone2,
                              String stoneStairs, String stoneSlab, String stoneWall, String accent, String accent2) {
        Style s = new Style();
        s.id = id;
        s.name = name;
        s.wood = wood;
        s.planks = wood + "_planks";
        s.log = wood.equals("crimson") || wood.equals("warped") ? wood + "_stem" : wood + "_log";
        s.stripped = "stripped_" + s.log;
        s.wall = wall;
        s.wall2 = wall2;
        s.foundation = foundation;
        s.foundation2 = foundation2;
        s.floor = s.planks;
        s.roof = roof;
        s.roofFull = roofFull;
        s.path = path;
        s.path2 = path2;
        s.fence = wood + "_fence";
        s.gate = wood + "_fence_gate";
        s.door = wood + "_door";
        s.trapdoor = wood + "_trapdoor";
        s.stone = stone;
        s.stone2 = stone2;
        s.stoneStairs = stoneStairs;
        s.stoneSlab = stoneSlab;
        s.stoneWall = stoneWall;
        s.accent = accent;
        s.accent2 = accent2;
        s.leaves = wood.equals("dark_oak") ? "dark_oak_leaves" : wood + "_leaves";
        return s;
    }

    public static final Map<String, Style> ALL = new java.util.LinkedHashMap<>();

    static {
        Style llanura = make("llanura", "Llanura", "oak", "calcite", "oak_planks", "cobblestone", "mossy_cobblestone",
                "dark_oak", "dark_oak_planks", "dirt_path", "coarse_dirt", "stone_bricks", "mossy_stone_bricks",
                "stone_brick_stairs", "stone_brick_slab", "stone_brick_wall", "red_wool", "white_wool");
        llanura.stripped = "stripped_oak_log";
        ALL.put("llanura", llanura);

        Style taiga = make("taiga", "Taiga", "spruce", "spruce_planks", "stripped_spruce_log", "cobblestone", "stone",
                "spruce", "spruce_planks", "dirt_path", "coarse_dirt", "cobblestone", "mossy_cobblestone",
                "cobblestone_stairs", "cobblestone_slab", "cobblestone_wall", "brown_wool", "green_wool");
        taiga.crop = "potatoes";
        taiga.flower1 = "sweet_berry_bush[age=3]";
        taiga.flower2 = "fern";
        ALL.put("taiga", taiga);

        Style nieve = make("nieve", "Nieve", "spruce", "spruce_planks", "white_terracotta", "stone_bricks", "cracked_stone_bricks",
                "deepslate_tile", "deepslate_tiles", "gravel", "packed_ice", "stone_bricks", "polished_andesite",
                "stone_brick_stairs", "stone_brick_slab", "stone_brick_wall", "light_blue_wool", "white_wool");
        nieve.crop = "beetroots";
        nieve.flower1 = "fern";
        nieve.flower2 = "fern";
        nieve.leaves = "spruce_leaves";
        ALL.put("nieve", nieve);

        Style desierto = make("desierto", "Desierto", "jungle", "smooth_sandstone", "sandstone", "cut_sandstone", "sandstone",
                "smooth_sandstone", "smooth_sandstone", "smooth_sandstone", "sand", "cut_sandstone", "chiseled_sandstone",
                "sandstone_stairs", "sandstone_slab", "sandstone_wall", "cyan_wool", "orange_wool");
        desierto.flatRoofs = true;
        desierto.crop = "carrots";
        desierto.flower1 = "dead_bush";
        desierto.flower2 = "cactus";
        desierto.floor = "smooth_sandstone";
        desierto.leaves = "jungle_leaves";
        ALL.put("desierto", desierto);

        Style sabana = make("sabana", "Sabana", "acacia", "orange_terracotta", "acacia_planks", "terracotta", "cobblestone",
                "acacia", "acacia_planks", "dirt_path", "coarse_dirt", "terracotta", "red_terracotta",
                "granite_stairs", "granite_slab", "granite_wall", "yellow_wool", "orange_wool");
        sabana.crop = "melon_stem";
        sabana.flower1 = "short_grass";
        sabana.flower2 = "dandelion";
        ALL.put("sabana", sabana);

        Style cerezo = make("cerezo", "Cerezo", "cherry", "white_terracotta", "cherry_planks", "stone_bricks", "polished_andesite",
                "deepslate_tile", "deepslate_tiles", "gravel", "packed_mud", "polished_andesite", "stone_bricks",
                "deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tile_wall", "pink_wool", "red_wool");
        cerezo.log = "dark_oak_log";
        cerezo.stripped = "stripped_cherry_log";
        cerezo.flower1 = "pink_petals[flower_amount=4]";
        cerezo.flower2 = "azure_bluet";
        ALL.put("cerezo", cerezo);

        Style pantano = make("pantano", "Pantano", "mangrove", "mud_bricks", "mangrove_planks", "mud_bricks", "packed_mud",
                "mangrove", "mangrove_planks", "packed_mud", "mud", "mud_bricks", "packed_mud",
                "mud_brick_stairs", "mud_brick_slab", "mud_brick_wall", "lime_wool", "brown_wool");
        pantano.flower1 = "blue_orchid";
        pantano.flower2 = "brown_mushroom";
        ALL.put("pantano", pantano);

        Style selva = make("selva", "Selva", "jungle", "jungle_planks", "stripped_jungle_log", "mossy_cobblestone", "cobblestone",
                "jungle", "jungle_planks", "dirt_path", "coarse_dirt", "mossy_stone_bricks", "cracked_stone_bricks",
                "mossy_stone_brick_stairs", "mossy_stone_brick_slab", "mossy_stone_brick_wall", "lime_wool", "yellow_wool");
        selva.crop = "melon_stem";
        ALL.put("selva", selva);

        Style oscuro = make("oscuro", "Oscuro", "dark_oak", "deepslate_bricks", "cracked_deepslate_bricks", "cobbled_deepslate", "blackstone",
                "deepslate_tile", "deepslate_tiles", "cobbled_deepslate", "gravel", "deepslate_bricks", "cracked_deepslate_bricks",
                "deepslate_brick_stairs", "deepslate_brick_slab", "deepslate_brick_wall", "purple_wool", "black_wool");
        oscuro.light = "soul_lantern";
        ALL.put("oscuro", oscuro);
    }

    public static Style get(String id) {
        Style s = ALL.get(id);
        return s == null ? ALL.get("llanura") : s;
    }

    /** Estilo que va con un bioma. */
    public static String forBiome(String biome) {
        String b = biome == null ? "" : biome;
        if (b.contains("desert") || b.contains("badlands")) return "desierto";
        if (b.contains("savanna")) return "sabana";
        if (b.contains("snow") || b.contains("frozen") || b.contains("ice") || b.contains("grove") || b.contains("peaks")) return "nieve";
        if (b.contains("taiga")) return "taiga";
        if (b.contains("cherry")) return "cerezo";
        if (b.contains("swamp") || b.contains("mangrove")) return "pantano";
        if (b.contains("jungle") || b.contains("bamboo")) return "selva";
        if (b.contains("dark_forest") || b.contains("pale")) return "oscuro";
        return "llanura";
    }

    public static List<String> ids() {
        return List.copyOf(ALL.keySet());
    }
}
