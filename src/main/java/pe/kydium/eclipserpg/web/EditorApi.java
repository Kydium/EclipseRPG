package pe.kydium.eclipserpg.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.story.StoryManager;
import pe.kydium.eclipserpg.story.StoryParser;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Editor visual de historias: el servidor sirve una página (editor.html) y una pequeña API
 * para leer, guardar, revisar y aplicar la historia. Se protege con un código (token) que
 * solo ve quien tiene permiso de administrador.
 */
public final class EditorApi {

    private static final Set<String> YAMLS = Set.of("mobs", "objetos", "tiendas", "contratos", "mazmorras", "mundo", "estructuras");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private final EclipseRPG plugin;
    private String token;

    public EditorApi(EclipseRPG plugin) {
        this.plugin = plugin;
        token = plugin.getConfig().getString("editor.token", "");
        if (token == null || token.length() < 12) {
            String abc = "abcdefghjkmnpqrstuvwxyz23456789";
            SecureRandom r = new SecureRandom();
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 16; i++) b.append(abc.charAt(r.nextInt(abc.length())));
            token = b.toString();
            plugin.getConfig().set("editor.token", token);
            plugin.saveConfig();
        }
    }

    public String token() {
        return token;
    }

    public void register(HttpServer server) {
        server.createContext("/editor", ex -> safe(ex, () -> {
            if (!plugin.getConfig().getBoolean("editor.activo", true)) {
                send(ex, 404, "text/plain", "El editor está desactivado (editor.activo en config.yml)".getBytes(StandardCharsets.UTF_8));
                return;
            }
            try (InputStream in = plugin.getResource("web/editor.html")) {
                send(ex, 200, "text/html; charset=utf-8", in == null ? new byte[0] : in.readAllBytes());
            }
        }));
        server.createContext("/est/", ex -> safe(ex, () -> {
            String name = StoryParser.slug(ex.getRequestURI().getPath().substring(5).replace(".jpg", ""));
            try (InputStream in = plugin.getResource("web/est/" + name + ".jpg")) {
                if (in == null) send(ex, 404, "text/plain", new byte[0]);
                else send(ex, 200, "image/jpeg", in.readAllBytes());
            }
        }));
        server.createContext("/skins/", ex -> safe(ex, () -> {
            String name = ex.getRequestURI().getPath().substring(7).replace(".png", "");
            byte[] png = skinBytes(StoryParser.slug(name));
            if (png == null) send(ex, 404, "text/plain", new byte[0]);
            else send(ex, 200, "image/png", png);
        }));
        server.createContext("/api/", ex -> safe(ex, () -> api(ex)));
    }

    private String resource(String path) throws IOException {
        try (InputStream in = plugin.getResource(path)) {
            return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ---------------------------------------------------------------- rutas

    private void api(HttpExchange ex) throws Exception {
        if (!authorized(ex)) {
            json(ex, 401, err("Código de acceso inválido. Abre el editor con /historia editor dentro del juego."));
            return;
        }
        String path = ex.getRequestURI().getPath().substring(5);
        Map<String, String> q = query(ex);
        String method = ex.getRequestMethod();
        StoryManager st = plugin.story();
        switch (path) {
            case "catalogo" -> json(ex, 200, sync(this::catalog));
            case "prompt" -> {
                try (InputStream in = plugin.getResource("PROMPT-IA.txt")) {
                    JsonObject o = new JsonObject();
                    o.addProperty("texto", in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    json(ex, 200, o);
                }
            }
            case "texto" -> {
                File f = st.file();
                if (method.equals("GET")) {
                    String txt = f.exists() ? Files.readString(f.toPath(), StandardCharsets.UTF_8) : "";
                    JsonObject o = new JsonObject();
                    o.addProperty("texto", txt);
                    o.addProperty("historia", st.id());
                    json(ex, 200, o);
                } else {
                    String body = body(ex);
                    Files.createDirectories(f.getParentFile().toPath());
                    if (f.exists()) Files.copy(f.toPath(), new File(f.getParentFile(), "historia.txt.bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
                    Files.writeString(f.toPath(), body, StandardCharsets.UTF_8);
                    json(ex, 200, ok("Guardado"));
                }
            }
            case "yaml" -> {
                String name = q.getOrDefault("archivo", "");
                if (!YAMLS.contains(name)) {
                    json(ex, 400, err("Archivo no permitido"));
                    return;
                }
                if (method.equals("GET")) {
                    YamlConfiguration y = sync(() -> st.yaml(name));
                    JsonObject o = new JsonObject();
                    o.add("datos", toJson(y));
                    o.addProperty("propio", new File(st.folder(), "avanzado/" + name + ".yml").exists());
                    json(ex, 200, o);
                } else {
                    JsonElement data = JsonParser.parseString(body(ex));
                    YamlConfiguration y = new YamlConfiguration();
                    if (data.isJsonObject()) for (Map.Entry<String, JsonElement> e : data.getAsJsonObject().entrySet()) y.set(e.getKey(), fromJson(e.getValue()));
                    File f = new File(st.folder(), "avanzado/" + name + ".yml");
                    Files.createDirectories(f.getParentFile().toPath());
                    if (f.exists()) Files.copy(f.toPath(), new File(f.getParentFile(), name + ".yml.bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
                    String header = "# Editado con el editor visual de Aventura Maker\n";
                    Files.writeString(f.toPath(), header + y.saveToString(), StandardCharsets.UTF_8);
                    json(ex, 200, ok("Guardado"));
                }
            }
            case "revisar" -> {
                String body = body(ex);
                // también acepta {"texto": "...", "extra": {...}} con lo que el editor tiene sin guardar todavía
                JsonObject extra = null;
                if (body.startsWith("{")) {
                    JsonObject o = JsonParser.parseString(body).getAsJsonObject();
                    body = o.has("texto") ? o.get("texto").getAsString() : "";
                    extra = o.has("extra") && o.get("extra").isJsonObject() ? o.getAsJsonObject("extra") : null;
                }
                List<String> lines = Arrays.asList(body.split("\\R", -1));
                JsonObject ext = extra;
                StoryParser.Result r = sync(() -> new StoryParser(st.dictionary(), withExtra(st.catalog(), ext)).parse(lines));
                json(ex, 200, result(r));
            }
            case "texto-completo" -> {
                // historia + todo lo avanzado en un solo texto (bloques AVANZADO con el YAML tal cual)
                boolean ejemplo = "ejemplo".equals(q.get("origen"));
                StringBuilder sb = new StringBuilder();
                String hist = ejemplo ? resource("historias/eclipse/historia.txt")
                        : st.file().exists() ? Files.readString(st.file().toPath(), StandardCharsets.UTF_8) : "";
                sb.append(hist.stripTrailing()).append("\n\n");
                sb.append("# =====================================================================\n");
                sb.append("#  TODO LO DEMÁS (enemigos, objetos, tiendas, mundo, estructuras, mazmorras).\n");
                sb.append("#  Cada bloque AVANZADO es el archivo .yml con dos espacios delante.\n");
                sb.append("# =====================================================================\n");
                for (String name : List.of("objetos", "tiendas", "mobs", "estructuras", "mazmorras", "mundo", "contratos")) {
                    File f = new File(st.folder(), "avanzado/" + name + ".yml");
                    String y = !ejemplo && f.exists() ? Files.readString(f.toPath(), StandardCharsets.UTF_8)
                            : resource(!ejemplo && name.equals("mundo") && !st.id().equals("eclipse") ? "motor/mundo.yml" : "historias/eclipse/avanzado/" + name + ".yml");
                    if (y.isBlank()) continue;
                    sb.append("\nAVANZADO ").append(name).append("\n");
                    for (String line : y.split("\\R", -1)) sb.append(line.isEmpty() ? "" : "  " + line).append("\n");
                }
                JsonObject o = new JsonObject();
                o.addProperty("texto", sb.toString());
                json(ex, 200, o);
            }
            case "yaml-leer" -> {
                // {"objetos": "texto yaml", ...} → {"datos": {"objetos": {...}}, "errores": {...}}
                JsonObject in = JsonParser.parseString(body(ex)).getAsJsonObject();
                JsonObject datos = new JsonObject(), errores = new JsonObject();
                for (Map.Entry<String, JsonElement> e : in.entrySet()) {
                    if (!YAMLS.contains(e.getKey())) {
                        errores.addProperty(e.getKey(), "No conozco el archivo «" + e.getKey() + "». Usa: " + String.join(", ", YAMLS));
                        continue;
                    }
                    YamlConfiguration y = new YamlConfiguration();
                    try {
                        y.loadFromString(e.getValue().getAsString());
                        datos.add(e.getKey(), toJson(y));
                    } catch (org.bukkit.configuration.InvalidConfigurationException ice) {
                        String m = ice.getMessage() == null ? "" : ice.getMessage();
                        errores.addProperty(e.getKey(), "El bloque AVANZADO " + e.getKey() + " tiene un error de formato: " + m.lines().limit(4).reduce((a, b) -> a + " " + b).orElse(""));
                    }
                }
                JsonObject o = new JsonObject();
                o.add("datos", datos);
                o.add("errores", errores);
                json(ex, 200, o);
            }
            case "vacio" -> {
                // para "empezar desde cero": todo vacío menos las mazmorras (sin jefes de la historia) y el mundo genérico
                JsonObject av = new JsonObject();
                YamlConfiguration mz = new YamlConfiguration();
                mz.loadFromString(resource("historias/eclipse/avanzado/mazmorras.yml"));
                var sec = mz.getConfigurationSection("mazmorras");
                if (sec != null) for (String k : sec.getKeys(false)) {
                    mz.set("mazmorras." + k + ".jefe", null);
                    mz.set("mazmorras." + k + ".enemigos", null);
                }
                av.add("mazmorras", toJson(mz));
                YamlConfiguration mu = new YamlConfiguration();
                mu.loadFromString(resource("motor/mundo.yml"));
                av.add("mundo", toJson(mu));
                for (String[] r : new String[][]{{"mobs", "mobs"}, {"objetos", "items"}, {"tiendas", "tiendas"}, {"estructuras", "estructuras"}}) {
                    JsonObject root = new JsonObject();
                    root.add(r[1], new JsonObject());
                    av.add(r[0], root);
                }
                JsonObject o = new JsonObject();
                o.add("avanzado", av);
                json(ex, 200, o);
            }
            case "traducir" -> {
                JsonObject in = JsonParser.parseString(body(ex)).getAsJsonObject();
                JsonObject out = sync(() -> translate(in));
                json(ex, 200, out);
            }
            case "aplicar" -> {
                StoryParser.Result r = sync(() -> plugin.reloadStory(false));
                JsonObject o = result(r);
                o.addProperty("aplicado", r.errors() == 0);
                json(ex, 200, o);
            }
            case "skin" -> {
                String name = StoryParser.slug(q.getOrDefault("nombre", ""));
                if (name.isEmpty()) {
                    json(ex, 400, err("Falta el nombre de la skin"));
                    return;
                }
                byte[] data;
                if (q.containsKey("url")) data = download(q.get("url"));
                else try (InputStream in = ex.getRequestBody()) {
                    data = in.readNBytes(2_000_000);
                }
                byte[] png = normalizeSkin(data);
                File f = new File(st.folder(), "skins/" + name + ".png");
                Files.createDirectories(f.getParentFile().toPath());
                Files.write(f.toPath(), png);
                sync(() -> {
                    st.loadSkins();
                    if (plugin.resourcePack() != null) plugin.resourcePack().rebuild();
                    return null;
                });
                JsonObject o = ok("Skin guardada");
                o.addProperty("nombre", name);
                json(ex, 200, o);
            }
            case "historias" -> {
                if (method.equals("GET")) {
                    JsonObject o = new JsonObject();
                    o.add("lista", GSON.toJsonTree(st.available()));
                    o.addProperty("actual", st.id());
                    json(ex, 200, o);
                } else {
                    String action = q.getOrDefault("accion", "");
                    String id = StoryParser.slug(q.getOrDefault("id", ""));
                    if (action.equals("nueva")) {
                        boolean made = !id.isEmpty() && st.create(id);
                        json(ex, made ? 200 : 400, made ? ok("Historia creada") : err("Ya existe o el nombre no es válido"));
                    } else if (action.equals("usar")) {
                        if (!st.available().contains(id)) {
                            json(ex, 400, err("No existe esa historia"));
                            return;
                        }
                        StoryParser.Result r = sync(() -> {
                            String prev = st.id();
                            plugin.getConfig().set("historia", id);
                            plugin.saveConfig();
                            StoryParser.Result rr = plugin.reloadStory(false);
                            if (rr.errors() > 0) {
                                plugin.getConfig().set("historia", prev);
                                plugin.saveConfig();
                                plugin.reloadStory(false);
                            } else pe.kydium.eclipserpg.story.StoryCommand.resetQuests(plugin);
                            return rr;
                        });
                        JsonObject o = result(r);
                        o.addProperty("aplicado", r.errors() == 0);
                        json(ex, 200, o);
                    } else json(ex, 400, err("Acción desconocida"));
                }
            }
            default -> json(ex, 404, err("Ruta desconocida"));
        }
    }

    // ---------------------------------------------------------------- catálogo

    private JsonObject catalog() {
        StoryManager st = plugin.story();
        JsonObject o = new JsonObject();
        o.addProperty("historia", st.id());
        o.addProperty("titulo", st.doc().title);
        o.add("historias", GSON.toJsonTree(st.available()));
        JsonArray skins = new JsonArray();
        st.skins().forEach((k, f) -> {
            JsonObject s = new JsonObject();
            s.addProperty("nombre", k);
            s.addProperty("propia", f != null);
            s.addProperty("slim", st.slim(k));
            skins.add(s);
        });
        o.add("skins", skins);
        o.add("lugares", GSON.toJsonTree(StoryManager.PLACES));
        o.add("tiendas", GSON.toJsonTree(new ArrayList<>(plugin.shops().ids())));
        StoryParser.Catalog c = st.catalog();
        o.add("mobsPropios", GSON.toJsonTree(c.customMobs()));
        o.add("objetosPropios", GSON.toJsonTree(c.customItems()));
        Map<String, String> dun = new LinkedHashMap<>();
        c.dungeons().forEach((k, v) -> dun.put(String.valueOf(k), v));
        o.add("mazmorras", GSON.toJsonTree(dun));
        List<String> mats = new ArrayList<>();
        for (Material m : Material.values()) if (!m.isLegacy() && m.isItem() && !m.isAir()) mats.add(m.getKey().getKey());
        o.add("materiales", GSON.toJsonTree(mats));
        List<String> ents = new ArrayList<>();
        for (EntityType t : EntityType.values()) {
            if (t == EntityType.UNKNOWN || !t.isAlive() || !t.isSpawnable()) continue;
            ents.add(t.getKey().getKey());
        }
        o.add("entidades", GSON.toJsonTree(ents));
        List<String> biomes = new ArrayList<>();
        try {
            io.papermc.paper.registry.RegistryAccess.registryAccess().getRegistry(io.papermc.paper.registry.RegistryKey.BIOME)
                    .forEach(b -> biomes.add(b.getKey().getKey()));
        } catch (Throwable ignored) {
            // sin lista de biomas: el editor deja escribirlos a mano
        }
        java.util.Collections.sort(biomes);
        o.add("biomas", GSON.toJsonTree(biomes));
        o.add("eventos", GSON.toJsonTree(new ArrayList<>(plugin.world().eventIds())));
        o.add("planos", GSON.toJsonTree(new ArrayList<>(plugin.structures().planNames())));
        JsonArray gens = new JsonArray();
        for (var e : pe.kydium.eclipserpg.gen.Catalog.ALL.values()) {
            JsonObject g = new JsonObject();
            g.addProperty("id", e.id());
            g.addProperty("nombre", e.name());
            g.addProperty("categoria", e.category());
            g.addProperty("descripcion", e.description());
            gens.add(g);
        }
        o.add("generadores", gens);
        o.add("estilos", GSON.toJsonTree(pe.kydium.eclipserpg.gen.Style.ids()));
        Map<String, Integer> placed = new LinkedHashMap<>();
        for (String sid : plugin.structures().ids()) placed.put(sid, plugin.structures().count(sid));
        o.add("estructurasEnMundo", GSON.toJsonTree(placed));
        List<String> effects = new ArrayList<>();
        Registry.EFFECT.forEach(e -> effects.add(e.getKey().getKey()));
        o.add("efectos", GSON.toJsonTree(effects));
        List<String> parts = new ArrayList<>();
        for (Particle p : Particle.values()) if (p.getDataType() == Void.class) parts.add(p.name());
        o.add("particulas", GSON.toJsonTree(parts));
        List<String> attrs = new ArrayList<>();
        Registry.ATTRIBUTE.forEach(a -> attrs.add(a.getKey().getKey()));
        o.add("atributos", GSON.toJsonTree(attrs));
        o.add("diccionario", dictionaryNames());
        o.addProperty("moneda", st.doc().currency);
        return o;
    }

    private static Set<String> strs(JsonObject o, String k) {
        Set<String> out = new java.util.LinkedHashSet<>();
        if (o != null && o.has(k) && o.get(k).isJsonArray()) for (JsonElement e : o.getAsJsonArray(k)) out.add(e.getAsString());
        return out;
    }

    /** El catálogo del servidor más lo que el editor tiene sin guardar (enemigos, objetos, tiendas…). */
    private static StoryParser.Catalog withExtra(StoryParser.Catalog base, JsonObject x) {
        if (x == null) return base;
        Map<String, String> mobs = new LinkedHashMap<>(base.customMobs());
        if (x.has("mobs") && x.get("mobs").isJsonObject()) x.getAsJsonObject("mobs").entrySet().forEach(e -> mobs.putIfAbsent(e.getKey(), e.getValue().getAsString()));
        Map<String, String> items = new LinkedHashMap<>(base.customItems());
        if (x.has("objetos") && x.get("objetos").isJsonObject()) x.getAsJsonObject("objetos").entrySet().forEach(e -> items.putIfAbsent(e.getKey(), e.getValue().getAsString()));
        Map<Integer, String> dungeons = new java.util.TreeMap<>(base.dungeons());
        if (x.has("mazmorras") && x.get("mazmorras").isJsonObject()) x.getAsJsonObject("mazmorras").entrySet().forEach(e -> {
            try {
                dungeons.putIfAbsent(Integer.parseInt(e.getKey()), e.getValue().getAsString());
            } catch (NumberFormatException ignored) {
                // solo números
            }
        });
        Set<String> shops = new java.util.LinkedHashSet<>(base.shops());
        shops.addAll(strs(x, "tiendas"));
        Set<String> structs = new java.util.LinkedHashSet<>(base.structures());
        structs.addAll(strs(x, "estructuras"));
        return new StoryParser.Catalog() {
            public boolean isItem(String id) { return base.isItem(id); }
            public boolean isBlock(String id) { return base.isBlock(id); }
            public boolean isEntity(String id) { return base.isEntity(id); }
            public Map<String, String> customMobs() { return mobs; }
            public Map<String, String> customItems() { return items; }
            public Map<Integer, String> dungeons() { return dungeons; }
            public Set<String> shops() { return shops; }
            public Set<String> skins() { return base.skins(); }
            public Set<String> places() { return base.places(); }
            public Set<String> structures() { return structs; }
        };
    }

    /** Traduce nombres en español a ids de Minecraft: {"objetos":["pan","lingotes de hierro"], "mobs":[…], "encantamientos":[…], "pociones":[…]} */
    private JsonObject translate(JsonObject in) {
        JsonObject out = new JsonObject();
        var dict = plugin.story().dictionary();
        for (String sec : List.of("objetos", "mobs", "encantamientos", "pociones")) {
            JsonObject m = new JsonObject();
            for (String phrase : strs(in, sec)) {
                String id = null;
                List<String> hit = dict == null ? null : dict.lookup(sec, phrase);
                if (hit != null) for (String h : hit) {
                    String c = h.contains("*") ? h.replace("*", "oak") : h;
                    if (sec.equals("objetos") && Material.matchMaterial(c) == null) continue;
                    id = c;
                    break;
                }
                if (id == null) {
                    String raw = pe.kydium.eclipserpg.story.Dictionary.rawId(phrase).replace(' ', '_');
                    boolean ok = switch (sec) {
                        case "objetos" -> Material.matchMaterial(raw) != null;
                        case "mobs" -> pe.kydium.eclipserpg.util.Reg.entity(raw) != null;
                        case "encantamientos" -> pe.kydium.eclipserpg.util.Reg.enchant(raw) != null;
                        default -> pe.kydium.eclipserpg.util.Reg.effect(raw) != null
                                || org.bukkit.Registry.POTION.get(org.bukkit.NamespacedKey.minecraft(raw)) != null;
                    };
                    if (ok) id = raw;
                }
                if (id != null) m.addProperty(phrase, id.toLowerCase(java.util.Locale.ROOT));
            }
            out.add(sec, m);
        }
        return out;
    }

    private JsonObject dictionaryNames() {
        JsonObject o = new JsonObject();
        String sec = "objetos";
        Map<String, List<String>> out = new LinkedHashMap<>();
        Map<String, String> idName = new LinkedHashMap<>();
        try (InputStream in = plugin.getResource("motor/diccionario.txt")) {
            for (String l : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                l = l.trim();
                if (l.isEmpty() || l.startsWith("#")) continue;
                if (l.startsWith("[")) {
                    sec = l.substring(1, l.length() - 1);
                    continue;
                }
                int eq = l.indexOf('=');
                if (eq < 0) continue;
                String first = l.substring(0, eq).split(",")[0].trim();
                out.computeIfAbsent(sec, k -> new ArrayList<>()).add(first);
                String ids = l.substring(eq + 1).trim();
                if (sec.equals("objetos") && !ids.contains(",") && !ids.contains("*")) idName.putIfAbsent(ids, first);
            }
        } catch (IOException ignored) {
            // sin diccionario
        }
        out.forEach((k, v) -> o.add(k, GSON.toJsonTree(v)));
        o.add("idNombre", GSON.toJsonTree(idName));
        return o;
    }

    private JsonObject result(StoryParser.Result r) {
        JsonObject o = new JsonObject();
        JsonArray arr = new JsonArray();
        for (StoryParser.Problem p : r.problems()) {
            JsonObject e = new JsonObject();
            e.addProperty("linea", p.line());
            e.addProperty("error", p.error());
            e.addProperty("mensaje", p.message());
            arr.add(e);
        }
        o.add("problemas", arr);
        o.addProperty("errores", r.errors());
        o.addProperty("misiones", r.doc().missions.size());
        o.addProperty("npcs", r.doc().npcs.size());
        return o;
    }

    // ---------------------------------------------------------------- skins

    private byte[] skinBytes(String name) throws IOException {
        File f = plugin.story().skins().get(name);
        if (f != null) return Files.readAllBytes(f.toPath());
        if (!plugin.story().skins().containsKey(name)) return null;
        try (InputStream in = plugin.getResource("skins/" + name + ".png")) {
            return in == null ? null : in.readAllBytes();
        }
    }

    private byte[] download(String url) throws Exception {
        if (!url.startsWith("http://") && !url.startsWith("https://")) throw new IllegalArgumentException("El link debe empezar con http:// o https://");
        HttpClient c = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build();
        HttpResponse<byte[]> r = c.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("User-Agent", "AventuraMaker-editor").build(), HttpResponse.BodyHandlers.ofByteArray());
        if (r.statusCode() != 200) throw new IllegalArgumentException("El link respondió " + r.statusCode());
        if (r.body().length > 2_000_000) throw new IllegalArgumentException("La imagen es demasiado grande");
        return r.body();
    }

    /** Acepta skins de 64x64 y las antiguas de 64x32 (las convierte). */
    private static byte[] normalizeSkin(byte[] data) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
        if (img == null) throw new IllegalArgumentException("Eso no es una imagen PNG");
        if (img.getWidth() == 64 && img.getHeight() == 32) {
            BufferedImage n = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = n.createGraphics();
            g.drawImage(img, 0, 0, null);
            // pierna y brazo izquierdos = copia de los derechos
            g.drawImage(img.getSubimage(0, 16, 16, 16), 16, 48, null);
            g.drawImage(img.getSubimage(40, 16, 16, 16), 32, 48, null);
            g.dispose();
            img = n;
        }
        if (img.getWidth() != 64 || img.getHeight() != 64)
            throw new IllegalArgumentException("Una skin de Minecraft mide 64x64 píxeles (esta mide " + img.getWidth() + "x" + img.getHeight() + ")");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    // ---------------------------------------------------------------- YAML <-> JSON

    private static JsonElement toJson(Object v) {
        if (v == null) return com.google.gson.JsonNull.INSTANCE;
        if (v instanceof ConfigurationSection s) {
            JsonObject o = new JsonObject();
            for (String k : s.getKeys(false)) o.add(k, toJson(s.get(k)));
            return o;
        }
        if (v instanceof Map<?, ?> m) {
            JsonObject o = new JsonObject();
            for (Map.Entry<?, ?> e : m.entrySet()) o.add(String.valueOf(e.getKey()), toJson(e.getValue()));
            return o;
        }
        if (v instanceof List<?> l) {
            JsonArray a = new JsonArray();
            for (Object x : l) a.add(toJson(x));
            return a;
        }
        if (v instanceof Boolean b) return new JsonPrimitive(b);
        if (v instanceof Number n) return new JsonPrimitive(n);
        return new JsonPrimitive(String.valueOf(v));
    }

    private static Object fromJson(JsonElement e) {
        if (e == null || e.isJsonNull()) return null;
        if (e.isJsonObject()) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> x : e.getAsJsonObject().entrySet()) {
                Object v = fromJson(x.getValue());
                if (v != null) m.put(x.getKey(), v);
            }
            return m;
        }
        if (e.isJsonArray()) {
            List<Object> l = new ArrayList<>();
            for (JsonElement x : e.getAsJsonArray()) l.add(fromJson(x));
            return l;
        }
        JsonPrimitive p = e.getAsJsonPrimitive();
        if (p.isBoolean()) return p.getAsBoolean();
        if (p.isNumber()) {
            double d = p.getAsDouble();
            if (d == Math.rint(d) && Math.abs(d) < 1e15) return (long) d == (int) d ? (Object) (int) d : (Object) (long) d;
            return d;
        }
        return p.getAsString();
    }

    // ---------------------------------------------------------------- utilidades HTTP

    private interface Handler {
        void run() throws Exception;
    }

    private void safe(HttpExchange ex, Handler h) {
        try {
            h.run();
        } catch (Exception e) {
            try {
                String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                if (e instanceof java.util.concurrent.ExecutionException && e.getCause() != null) msg = String.valueOf(e.getCause().getMessage());
                json(ex, 500, err(msg));
            } catch (IOException ignored) {
                // conexión cerrada
            }
        } finally {
            ex.close();
        }
    }

    private boolean authorized(HttpExchange ex) {
        String t = ex.getRequestHeaders().getFirst("X-Token");
        if (t == null) t = query(ex).get("t");
        return token.equals(t);
    }

    private static Map<String, String> query(HttpExchange ex) {
        Map<String, String> out = new HashMap<>();
        String q = ex.getRequestURI().getRawQuery();
        if (q == null) return out;
        for (String kv : q.split("&")) {
            int i = kv.indexOf('=');
            if (i < 0) continue;
            out.put(URLDecoder.decode(kv.substring(0, i), StandardCharsets.UTF_8), URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8));
        }
        return out;
    }

    private static String body(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            return new String(in.readNBytes(5_000_000), StandardCharsets.UTF_8);
        }
    }

    private <T> T sync(Callable<T> c) throws Exception {
        if (Bukkit.isPrimaryThread()) return c.call();
        return Bukkit.getScheduler().callSyncMethod(plugin, c).get(30, TimeUnit.SECONDS);
    }

    private static JsonObject ok(String m) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        o.addProperty("mensaje", m);
        return o;
    }

    private static JsonObject err(String m) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", false);
        o.addProperty("mensaje", m);
        return o;
    }

    private static void json(HttpExchange ex, int code, JsonElement o) throws IOException {
        send(ex, code, "application/json; charset=utf-8", GSON.toJson(o).getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange ex, int code, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, body.length == 0 ? -1 : body.length);
        if (body.length > 0) try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    /** Dirección del editor para un jugador (con la misma IP con la que se conectó). */
    public String url(org.bukkit.entity.Player p) {
        String host = plugin.getConfig().getString("paquete-recursos.direccion", "");
        if ((host == null || host.isBlank()) && p != null && p.getVirtualHost() != null) host = p.getVirtualHost().getHostString();
        if (host == null || host.isBlank()) host = "localhost";
        if (host.contains(":") && !host.startsWith("[")) host = "[" + host + "]";
        return "http://" + host + ":" + plugin.getConfig().getInt("paquete-recursos.puerto", 25580) + "/editor?t=" + token;
    }

    @SuppressWarnings("unused")
    private static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
