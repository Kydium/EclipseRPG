package pe.kydium.eclipserpg.story;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.YamlConfiguration;
import pe.kydium.eclipserpg.EclipseRPG;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Historias: cada una vive en plugins/AventuraMaker/historias/&lt;id&gt;/ con un historia.txt en español
 * sencillo, una carpeta skins/ opcional y una carpeta avanzado/ opcional (objetos, mobs, tiendas,
 * contratos y mazmorras en YAML). El motor no cambia entre historias.
 */
public final class StoryManager {

    /** Archivos de la historia de ejemplo que trae el plugin. */
    private static final String[] BUNDLED = {"historia.txt", "avanzado/objetos.yml", "avanzado/mobs.yml",
            "avanzado/tiendas.yml", "avanzado/contratos.yml", "avanzado/mazmorras.yml", "skins/LEEME.txt"};

    /** Lugares de la aldea donde se pueden poner NPCs (vienen del plano). */
    public static final List<String> PLACES = List.of("casa", "cuartel", "herreria", "tienda", "biblioteca", "mapas", "torre",
            "tienda_negra", "plaza_norte", "plaza_sur", "plaza_este", "plaza_oeste", "fuente", "muelle", "granja",
            "puerta_norte", "puerta_sur", "puerta_este", "puerta_oeste");

    private final EclipseRPG plugin;
    private Dictionary dict;
    private StoryDoc doc = new StoryDoc();
    private List<StoryParser.Problem> problems = new ArrayList<>();
    /** skin → archivo propio de la historia (null = viene con el plugin). */
    private final Map<String, File> skins = new TreeMap<>();
    private final Set<String> slimSkins = new TreeSet<>();

    public StoryManager(EclipseRPG plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------- carpetas

    public String id() {
        return plugin.getConfig().getString("historia", "eclipse").toLowerCase(Locale.ROOT);
    }

    public File root() {
        return new File(plugin.getDataFolder(), "historias");
    }

    public File folder() {
        return new File(root(), id());
    }

    public File file() {
        return new File(folder(), "historia.txt");
    }

    /** Prepara carpetas, copia la historia de ejemplo y las guías, y mueve los archivos viejos. */
    public void init() {
        File data = plugin.getDataFolder();
        // archivos de versiones anteriores → antiguo/
        File old = new File(data, "antiguo");
        for (String f : new String[]{"quests.yml", "npcs.yml", "items.yml", "mobs.yml", "shops.yml", "contracts.yml", "mazmorras.yml"}) {
            File src = new File(data, f);
            if (!src.exists()) continue;
            try {
                Files.createDirectories(old.toPath());
                Files.move(src.toPath(), new File(old, f).toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().warning("No se pudo mover " + f + " a antiguo/: " + e.getMessage());
            }
        }
        File eclipse = new File(root(), "eclipse");
        for (String f : BUNDLED) copy("historias/eclipse/" + f, new File(eclipse, f), false);
        // la guía y el texto para IA se actualizan siempre
        copy("GUIA.txt", new File(data, "GUIA.txt"), true);
        copy("PROMPT-IA.txt", new File(data, "PROMPT-IA.txt"), true);
        copy("motor/plantilla.txt", new File(data, "motor/plantilla.txt"), true);
        copy("motor/diccionario.txt", new File(data, "motor/diccionario.txt"), true);
        try (InputStreamReader r = new InputStreamReader(plugin.getResource("motor/diccionario.txt"), StandardCharsets.UTF_8)) {
            dict = Dictionary.read(r);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo leer el diccionario", e);
        }
    }

    private void copy(String resource, File dest, boolean overwrite) {
        if (dest.exists() && !overwrite) return;
        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) return;
            Files.createDirectories(dest.getParentFile().toPath());
            Files.copy(in, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo copiar " + resource + ": " + e.getMessage());
        }
    }

    /** Crea una historia nueva a partir de la plantilla. */
    public boolean create(String newId) {
        File dir = new File(root(), newId);
        if (dir.exists()) return false;
        copy("motor/plantilla.txt", new File(dir, "historia.txt"), true);
        copy("historias/eclipse/skins/LEEME.txt", new File(dir, "skins/LEEME.txt"), true);
        return true;
    }

    public List<String> available() {
        List<String> out = new ArrayList<>();
        File[] dirs = root().listFiles(File::isDirectory);
        if (dirs != null) for (File d : dirs) if (new File(d, "historia.txt").exists()) out.add(d.getName());
        out.sort(null);
        return out;
    }

    /** Un archivo avanzado (objetos, mobs, tiendas, contratos, mazmorras) de la historia; si no lo trae, el de ejemplo. */
    public YamlConfiguration yaml(String name) {
        File f = new File(folder(), "avanzado/" + name + ".yml");
        if (f.exists()) return YamlConfiguration.loadConfiguration(f);
        try (InputStream in = plugin.getResource("historias/eclipse/avanzado/" + name + ".yml")) {
            if (in == null) return new YamlConfiguration();
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return new YamlConfiguration();
        }
    }

    // ---------------------------------------------------------------- skins

    public void loadSkins() {
        skins.clear();
        slimSkins.clear();
        try (InputStream in = plugin.getResource("skins/lista.txt")) {
            if (in != null) {
                for (String l : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                    l = l.trim();
                    if (l.isEmpty()) continue;
                    String[] p = l.split(" ");
                    skins.put(p[0], null);
                    if (p.length > 1 && p[1].equals("slim")) slimSkins.add(p[0]);
                }
            }
        } catch (IOException ignored) {
            // sin catálogo
        }
        File[] own = new File(folder(), "skins").listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".png"));
        if (own != null) {
            for (File f : own) {
                String name = StoryParser.slug(f.getName().substring(0, f.getName().length() - 4));
                try {
                    BufferedImage img = ImageIO.read(f);
                    if (img == null || img.getWidth() != 64 || img.getHeight() != 64) {
                        plugin.getLogger().warning("Skin " + f.getName() + " no es una skin de 64x64; se ignora.");
                        continue;
                    }
                    skins.put(name, f);
                    // brazos de 3 píxeles (modelo "slim")
                    if (((img.getRGB(54, 20) >>> 24) & 0xff) == 0 && ((img.getRGB(55, 31) >>> 24) & 0xff) == 0) slimSkins.add(name);
                    else slimSkins.remove(name);
                } catch (IOException e) {
                    plugin.getLogger().warning("No se pudo leer la skin " + f.getName());
                }
            }
        }
    }

    public Map<String, File> skins() {
        return skins;
    }

    public boolean slim(String skin) {
        return slimSkins.contains(skin);
    }

    // ---------------------------------------------------------------- lectura

    /** Lee historia.txt. Devuelve el resultado sin aplicarlo. */
    public StoryParser.Result read() {
        loadSkins();
        List<String> lines;
        try {
            lines = Files.readAllLines(file().toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            try {
                lines = Files.readAllLines(file().toPath(), java.nio.charset.Charset.forName("windows-1252"));
            } catch (IOException e2) {
                return new StoryParser.Result(new StoryDoc(), List.of(new StoryParser.Problem(0, true,
                        "No encuentro " + file().getPath() + ". Usa /historia nueva <nombre> o revisa 'historia:' en config.yml")));
            }
        }
        return new StoryParser(dict, catalog()).parse(lines);
    }

    /** Aplica una historia ya leída (sin errores, o con errores si force). */
    public void apply(StoryParser.Result r) {
        doc = r.doc();
        problems = r.problems();
        writeReport();
    }

    /** Guarda los problemas de una lectura sin cambiar la historia que está en uso. */
    public void reportOnly(StoryParser.Result r) {
        problems = r.problems();
        writeReport();
    }

    public void writeReport() {
        File f = new File(folder(), "errores.txt");
        try {
            if (problems.isEmpty()) {
                Files.deleteIfExists(f.toPath());
                return;
            }
            List<String> out = new ArrayList<>();
            out.add("Revisión de " + file().getName() + " — " + java.time.LocalDateTime.now().withNano(0));
            out.add("");
            for (StoryParser.Problem p : problems) out.add(p.toString());
            Files.write(f.toPath(), out, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // no importa
        }
    }

    public StoryDoc doc() {
        return doc;
    }

    public List<StoryParser.Problem> problems() {
        return problems;
    }

    public long errorCount() {
        return problems.stream().filter(StoryParser.Problem::error).count();
    }

    public Dictionary dictionary() {
        return dict;
    }

    public StoryParser.Catalog catalog() {
        Map<String, String> mobs = new LinkedHashMap<>();
        for (String id : plugin.mobs().ids()) {
            var d = plugin.mobs().def(id);
            mobs.put(id, d == null || d.name == null ? id : plain(d.name));
        }
        Map<String, String> items = new LinkedHashMap<>();
        for (String id : plugin.items().ids()) {
            var d = plugin.items().def(id);
            items.put(id, d == null || d.name == null ? id : plain(d.name));
        }
        Map<Integer, String> dungeons = new TreeMap<>();
        for (int act = 1; act <= 20; act++) if (plugin.dungeons().themeFor(act) != null) dungeons.put(act, plain(plugin.dungeons().name(act)));
        Set<String> shops = new TreeSet<>(plugin.shops().ids());
        Set<String> skinNames = new TreeSet<>(skins.keySet());
        Set<String> places = new java.util.LinkedHashSet<>(PLACES);
        return new StoryParser.Catalog() {
            public boolean isItem(String id) {
                Material m = Material.matchMaterial(id);
                return m != null && m.isItem();
            }

            public boolean isBlock(String id) {
                Material m = Material.matchMaterial(id);
                return m != null && m.isBlock();
            }

            public boolean isEntity(String id) {
                return Registry.ENTITY_TYPE.get(NamespacedKey.minecraft(id)) != null;
            }

            public Map<String, String> customMobs() {
                return mobs;
            }

            public Map<String, String> customItems() {
                return items;
            }

            public Map<Integer, String> dungeons() {
                return dungeons;
            }

            public Set<String> shops() {
                return shops;
            }

            public Set<String> skins() {
                return skinNames;
            }

            public Set<String> places() {
                return places;
            }
        };
    }

    public static String plain(String mm) {
        return mm == null ? "" : mm.replaceAll("<[^>]+>", "").trim();
    }
}
