package pe.kydium.eclipserpg.gen;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/** Catálogo de estructuras generadas: cada vez que se piden salen distintas. */
public final class Catalog {

    public record Entry(String id, String name, String category, String description, int radius, Gen gen) {}

    @FunctionalInterface
    public interface Gen {
        Build make(Terrain t, Random r, Style s, int x, int z);
    }

    public static final Map<String, Entry> ALL = new LinkedHashMap<>();

    private static void add(String id, String name, String cat, String desc, int radius, Gen g) {
        ALL.put(id, new Entry(id, name, cat, desc, radius, g));
    }

    static {
        add("aldea_chica", "Aldea chica", "aldea", "Plaza, 3 caminos y unas 15 construcciones, con arcos de entrada.",
                VillageGen.radiusFor(VillageGen.Size.CHICA), (t, r, s, x, z) -> new VillageGen(t, r, s, VillageGen.Size.CHICA, x, z).generate());
        add("aldea_mediana", "Aldea mediana", "aldea", "Capilla, unas 25 construcciones y empalizada de troncos con portones.",
                VillageGen.radiusFor(VillageGen.Size.MEDIANA), (t, r, s, x, z) -> new VillageGen(t, r, s, VillageGen.Size.MEDIANA, x, z).generate());
        add("aldea_grande", "Aldea grande", "aldea", "Posada, capilla, mercado, más de 35 construcciones y murallas de piedra con torres.",
                VillageGen.radiusFor(VillageGen.Size.GRANDE), (t, r, s, x, z) -> new VillageGen(t, r, s, VillageGen.Size.GRANDE, x, z).generate());
        Structures.register();
    }

    static void register(String id, String name, String cat, String desc, int radius, Gen g) {
        add(id, name, cat, desc, radius, g);
    }

    public static Build generate(String id, Terrain t, Random r, Style s, int x, int z) {
        Entry e = ALL.get(id);
        if (e == null) throw new IllegalArgumentException("No existe la estructura " + id);
        return e.gen.make(t, r, s, x, z);
    }
}
