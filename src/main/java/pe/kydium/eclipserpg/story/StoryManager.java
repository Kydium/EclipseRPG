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
    private static final String[] CORONAS = {"historia.txt", "avanzado/objetos.yml", "avanzado/mobs.yml", "avanzado/tiendas.yml",
            "avanzado/mazmorras.yml", "avanzado/mundo.yml", "skins/LEEME.txt"};

    private static final String[] BUNDLED = {"historia.txt", "avanzado/objetos.yml", "avanzado/mobs.yml",
            "avanzado/tiendas.yml", "avanzado/contratos.yml", "avanzado/mazmorras.yml", "avanzado/mundo.yml", "avanzado/estructuras.yml", "avanzado/bloques.yml", "skins/LEEME.txt"};

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
        // las historias que trae el plugin se ponen UNA vez; si las quitas (/historia quitar) no vuelven solas
        Set<String> given = given();
        if (!given.contains("eclipse") || id().equals("eclipse")) {
            File eclipse = new File(root(), "eclipse");
            for (String f : BUNDLED) copy("historias/eclipse/" + f, new File(eclipse, f), false);
            given.add("eclipse");
        }
        // segunda historia incluida: Las Siete Coronas (se juega con /historia usar coronas confirmar)
        if (!given.contains("coronas") || id().equals("coronas")) {
            File coronas = new File(root(), "coronas");
            for (String f : CORONAS) copy("historias/coronas/" + f, new File(coronas, f), false);
            given.add("coronas");
        }
        // también queda una copia lista para sacar/poner en historias-guardadas/
        for (String sid : new String[]{"eclipse", "coronas"}) {
            File z = new File(saved(), sid + ".zip");
            if (!z.exists()) {
                try {
                    zipBundled(sid, z);
                } catch (IOException e) {
                    plugin.getLogger().warning("No se pudo guardar " + z.getName() + ": " + e.getMessage());
                }
            }
        }
        saveGiven(given);
        installZips();
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

    // ---------------------------------------------------------------- historias extraíbles (zip)

    /** Carpeta donde quedan las historias sacadas o exportadas: plugins/AventuraMaker/historias-guardadas/ */
    public File saved() {
        return new File(plugin.getDataFolder(), "historias-guardadas");
    }

    private Set<String> given() {
        File f = new File(root(), ".incluidas");
        Set<String> out = new TreeSet<>();
        if (f.exists()) {
            try {
                for (String l : Files.readAllLines(f.toPath())) if (!l.isBlank()) out.add(l.trim());
            } catch (IOException ignored) {
                // se vuelven a poner
            }
        } else {
            // servidores que ya tenían las carpetas: cuentan como entregadas
            for (String sid : new String[]{"eclipse", "coronas"}) if (new File(root(), sid + "/historia.txt").exists()) out.add(sid);
        }
        return out;
    }

    private void saveGiven(Set<String> ids) {
        try {
            Files.createDirectories(root().toPath());
            Files.write(new File(root(), ".incluidas").toPath(), ids);
        } catch (IOException ignored) {
            // no importa
        }
    }

    private void zipBundled(String sid, File dest) throws IOException {
        Files.createDirectories(dest.getParentFile().toPath());
        String[] files = sid.equals("eclipse") ? BUNDLED : CORONAS;
        try (java.util.zip.ZipOutputStream zo = new java.util.zip.ZipOutputStream(Files.newOutputStream(dest.toPath()))) {
            for (String f : files) {
                try (InputStream in = plugin.getResource("historias/" + sid + "/" + f)) {
                    if (in == null) continue;
                    zo.putNextEntry(new java.util.zip.ZipEntry(sid + "/" + f));
                    in.transferTo(zo);
                    zo.closeEntry();
                }
            }
        }
    }

    /**
     * Instala los .zip que pongas en historias/ (una historia empaquetada): se descomprimen en su carpeta
     * y el zip pasa a historias-guardadas/. Si la carpeta ya existía, la vieja se guarda como respaldo.
     * @return ids instalados
     */
    public List<String> installZips() {
        List<String> done = new ArrayList<>();
        File[] zips = root().listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".zip"));
        if (zips == null) return done;
        for (File z : zips) {
            try {
                String sid = install(z);
                if (sid == null) {
                    plugin.getLogger().warning("historias/" + z.getName() + " no trae un historia.txt; lo dejo como está.");
                    continue;
                }
                Files.createDirectories(saved().toPath());
                Files.move(z.toPath(), new File(saved(), sid + ".zip").toPath(), StandardCopyOption.REPLACE_EXISTING);
                done.add(sid);
                plugin.getLogger().info("Historia instalada desde " + z.getName() + ": historias/" + sid + "/");
            } catch (IOException e) {
                plugin.getLogger().warning("No se pudo instalar " + z.getName() + ": " + e.getMessage());
            }
        }
        return done;
    }

    /** Descomprime una historia; vale con historia.txt en la raíz del zip o dentro de una carpeta. */
    public String install(File zip) throws IOException {
        String prefix = null;
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zip)) {
            var en = zf.entries();
            while (en.hasMoreElements()) {
                String n = en.nextElement().getName().replace('\\', '/');
                if (n.equals("historia.txt")) { prefix = ""; break; }
                if (n.endsWith("/historia.txt") && n.indexOf('/') == n.length() - "/historia.txt".length() && prefix == null)
                    prefix = n.substring(0, n.length() - "historia.txt".length());
            }
            if (prefix == null) return null;
            String base = prefix.isEmpty() ? zip.getName().replaceAll("(?i)\\.zip$", "") : prefix.substring(0, prefix.length() - 1);
            String sid = StoryParser.slug(base);
            if (sid.isEmpty()) return null;
            File dir = new File(root(), sid);
            if (sid.equals(id()) && dir.exists()) {
                // la historia en uso se reemplaza en el sitio (respaldo antes)
                backup(dir, sid);
            } else if (dir.exists()) backup(dir, sid);
            File canon = dir.getCanonicalFile();
            var en2 = zf.entries();
            while (en2.hasMoreElements()) {
                var e = en2.nextElement();
                String n = e.getName().replace('\\', '/');
                if (!n.startsWith(prefix) || e.isDirectory()) continue;
                File out = new File(dir, n.substring(prefix.length())).getCanonicalFile();
                if (!out.toPath().startsWith(canon.toPath())) continue; // nada fuera de la carpeta
                Files.createDirectories(out.getParentFile().toPath());
                try (InputStream in = zf.getInputStream(e)) {
                    Files.copy(in, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Set<String> g = given();
            g.add(sid);
            saveGiven(g);
            return sid;
        }
    }

    private void backup(File dir, String sid) throws IOException {
        String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmm").format(new java.util.Date());
        zipDir(dir, sid, new File(saved(), sid + "-respaldo-" + stamp + ".zip"));
        deleteDir(dir);
    }

    /** Empaqueta historias/&lt;id&gt;/ en un zip (la carpeta va dentro del zip). */
    public void zipDir(File dir, String sid, File dest) throws IOException {
        Files.createDirectories(dest.getParentFile().toPath());
        java.nio.file.Path base = dir.toPath();
        try (java.util.zip.ZipOutputStream zo = new java.util.zip.ZipOutputStream(Files.newOutputStream(dest.toPath()));
             var walk = Files.walk(base)) {
            for (java.nio.file.Path p : (Iterable<java.nio.file.Path>) walk::iterator) {
                if (Files.isDirectory(p)) continue;
                String rel = base.relativize(p).toString().replace('\\', '/');
                if (rel.equals("errores.txt")) continue;
                zo.putNextEntry(new java.util.zip.ZipEntry(sid + "/" + rel));
                Files.copy(p, zo);
                zo.closeEntry();
            }
        }
    }

    private static void deleteDir(File dir) throws IOException {
        try (var walk = Files.walk(dir.toPath())) {
            for (java.nio.file.Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }

    /** /historia exportar: deja una copia en historias-guardadas/&lt;id&gt;.zip */
    public File export(String sid) throws IOException {
        File dest = new File(saved(), sid + ".zip");
        zipDir(new File(root(), sid), sid, dest);
        return dest;
    }

    /** /historia quitar: la guarda en historias-guardadas/&lt;id&gt;.zip y borra la carpeta (no la que está en uso). */
    public File remove(String sid) throws IOException {
        File dest = export(sid);
        deleteDir(new File(root(), sid));
        return dest;
    }

    /** Lo que hay guardado para volver a poner (sin los respaldos). */
    public List<String> savedIds() {
        List<String> out = new ArrayList<>();
        File[] fs = saved().listFiles((d, n) -> n.endsWith(".zip") && !n.contains("-respaldo-"));
        if (fs != null) for (File f : fs) out.add(f.getName().substring(0, f.getName().length() - 4));
        out.sort(null);
        return out;
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
        // una historia nueva sin "mundo vivo" propio usa el genérico (sin enemigos de la historia de ejemplo)
        String res = name.equals("mundo") && !id().equals("eclipse") ? "motor/mundo.yml" : "historias/eclipse/avanzado/" + name + ".yml";
        try (InputStream in = plugin.getResource(res)) {
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
        for (int act = 1; act <= 99; act++) if (plugin.dungeons().themeFor(act) != null) dungeons.put(act, plain(plugin.dungeons().name(act)));
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

            public Set<String> structures() {
                Set<String> out = new java.util.LinkedHashSet<>();
                var sec = yaml("estructuras").getConfigurationSection("estructuras");
                if (sec != null) for (String k : sec.getKeys(false)) out.add(pe.kydium.eclipserpg.structure.StructureManager.slug(k));
                return out;
            }
        };
    }

    public static String plain(String mm) {
        return mm == null ? "" : mm.replaceAll("<[^>]+>", "").trim();
    }
}
