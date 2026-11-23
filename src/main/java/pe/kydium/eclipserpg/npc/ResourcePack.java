package pe.kydium.eclipserpg.npc;

import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.util.Txt;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Paquete de recursos con las skins de los NPCs. El propio servidor lo arma (skins del catálogo
 * + las de la historia) y lo sirve por HTTP en un puerto propio, así no depende de internet.
 * Si el jugador no puede descargarlo, se intenta con la copia publicada en GitHub (solo catálogo).
 */
public final class ResourcePack implements Listener {

    private final EclipseRPG plugin;
    private byte[] zip;
    private String sha1 = "";
    private HttpServer server;
    private int port;
    private String fallbackUrl = "";
    private String fallbackSha1 = "";
    private final Set<UUID> triedFallback = new HashSet<>();
    private final ExtraPacks extras;
    /** Objetos de objetos.yml que tienen modelo propio en el paquete (eclipse:<id>). */
    private final Set<String> itemModels = new HashSet<>();

    public ResourcePack(EclipseRPG plugin) {
        this.plugin = plugin;
        this.extras = new ExtraPacks(plugin);
        Properties p = new Properties();
        try (InputStream in = plugin.getResource("pack.properties")) {
            if (in != null) p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // sin copia en GitHub
        }
        fallbackUrl = p.getProperty("url", "");
        fallbackSha1 = p.getProperty("sha1", "");
    }

    // ---------------------------------------------------------------- armar

    /** Arma el .zip con todas las skins. Se llama al iniciar y al recargar la historia. */
    public void rebuild() {
        try (ByteArrayOutputStream bout = new ByteArrayOutputStream(); ZipOutputStream z = new ZipOutputStream(bout)) {
            put(z, "pack.mcmeta", resource("motor/pack.mcmeta"));
            // HUD: moneda y letra desplazada para mostrar el dinero junto a la barra rápida
            put(z, "assets/eclipse/font/hud.json", resource("motor/hud/hud.json"));
            put(z, "assets/eclipse/textures/hud/coin.png", resource("motor/hud/coin.png"));
            put(z, "assets/eclipse/textures/hud/caja.png", resource("motor/hud/caja.png"));
            // panel de misión: letras bajadas de 6 en 6 píxeles para ponerlo a media pantalla
            put(z, "assets/eclipse/textures/hud/caja_panel.png", resource("motor/hud/caja_panel.png"));
            put(z, "assets/eclipse/textures/hud/panel_letras.png", resource("motor/hud/panel_letras.png"));
            for (int f = 0; f < pe.kydium.eclipserpg.menu.QuestPanel.FONTS; f++)
                put(z, "assets/eclipse/font/panel_" + f + ".json", pe.kydium.eclipserpg.menu.QuestPanel.fontJson(f * pe.kydium.eclipserpg.menu.QuestPanel.STEP).getBytes(StandardCharsets.UTF_8));
            // íconos dibujados (reemplazan emojis y símbolos) y barras de jefe blancas invisibles (las usa el panel de misión)
            put(z, "assets/eclipse/font/iconos.json", pe.kydium.eclipserpg.util.Icons.fontJson().getBytes(StandardCharsets.UTF_8));
            for (String ic : pe.kydium.eclipserpg.util.Icons.NAMES)
                put(z, "assets/eclipse/textures/hud/iconos/" + ic + ".png", resource("motor/hud/iconos/" + ic + ".png"));
            byte[] clear = resource("motor/hud/barra_vacia.png");
            put(z, "assets/minecraft/textures/gui/sprites/boss_bar/white_background.png", clear);
            put(z, "assets/minecraft/textures/gui/sprites/boss_bar/white_progress.png", clear);
            // Corrompidos: texturas con venas moradas para los monstruos de Minecraft (todos son Corrompidos en la historia)
            Map<String, byte[]> textures = new java.util.TreeMap<>();
            if (plugin.getConfig().getBoolean("paquete-recursos.monstruos-corrompidos", true)) {
                byte[] list = resource("motor/corrupcion/lista.txt");
                if (list != null) for (String rel : new String(list, StandardCharsets.UTF_8).split("\\R")) {
                    rel = rel.trim();
                    if (rel.isEmpty()) continue;
                    byte[] png = resource("motor/corrupcion/" + rel);
                    if (png == null) continue;
                    textures.put(rel, png);
                }
            }
            // texturas propias de la historia: historias/<historia>/texturas/zombie/zombie.png, etc.
            File own = new File(plugin.story().folder(), "texturas");
            if (own.isDirectory()) {
                try (var walk = Files.walk(own.toPath())) {
                    for (var path : (Iterable<java.nio.file.Path>) walk.filter(x -> x.toString().endsWith(".png"))::iterator) {
                        String rel = own.toPath().relativize(path).toString().replace('\\', '/').toLowerCase(java.util.Locale.ROOT);
                        textures.put(rel, Files.readAllBytes(path));
                    }
                }
            }
            for (Map.Entry<String, byte[]> t : textures.entrySet()) put(z, "assets/minecraft/textures/entity/" + t.getKey(), t.getValue());
            int tex = textures.size();
            // objetos con "textura:" que usan un modelo de un paquete extra
            itemModels.clear();
            for (String id : plugin.items().ids()) {
                var ci = plugin.items().def(id);
                if (ci == null || ci.texture == null || ci.texture.isBlank()) continue;
                com.google.gson.JsonElement model = extras.model(ci.texture, ci.material.getKey().getKey());
                if (model == null) {
                    if (!extras.packs().isEmpty()) plugin.getLogger().warning("objetos.yml: " + id + " pide la textura \"" + ci.texture + "\" pero ningún paquete extra la tiene.");
                    continue;
                }
                com.google.gson.JsonObject def = new com.google.gson.JsonObject();
                def.add("model", model);
                put(z, "assets/eclipse/items/" + modelKey(id) + ".json", def.toString().getBytes(StandardCharsets.UTF_8));
                itemModels.add(id);
            }
            int n = 0;
            for (Map.Entry<String, File> e : plugin.story().skins().entrySet()) {
                byte[] png = e.getValue() == null ? resource("skins/" + e.getKey() + ".png") : Files.readAllBytes(e.getValue().toPath());
                if (png == null) continue;
                put(z, "assets/eclipse/textures/npc/" + e.getKey() + ".png", png);
                n++;
            }
            z.finish();
            zip = bout.toByteArray();
            sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(zip));
            plugin.getLogger().info("Paquete de skins listo: " + n + " skins, " + tex + " texturas de monstruos, " + itemModels.size()
                    + " objetos con modelo (" + zip.length / 1024 + " KB)");
        } catch (Exception e) {
            plugin.getLogger().warning("No se pudo armar el paquete de skins: " + e.getMessage());
            zip = null;
        }
    }

    private byte[] resource(String path) throws IOException {
        try (InputStream in = plugin.getResource(path)) {
            return in == null ? null : in.readAllBytes();
        }
    }

    private static void put(ZipOutputStream z, String name, byte[] data) throws IOException {
        if (data == null) return;
        ZipEntry e = new ZipEntry(name);
        e.setTime(1767225600000L); // fecha fija: mismo contenido = mismo hash
        z.putNextEntry(e);
        z.write(data);
        z.closeEntry();
    }

    // ---------------------------------------------------------------- servir

    /** Nombre válido para el modelo de un objeto (eclipse:<id>). */
    public static String modelKey(String id) {
        return id.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
    }

    public boolean hasModel(String id) {
        return itemModels.contains(id);
    }

    public ExtraPacks extras() {
        return extras;
    }

    public void start() {
        extras.scan();
        rebuild();
        if (!plugin.getConfig().getBoolean("paquete-recursos.activo", true)) return;
        port = plugin.getConfig().getInt("paquete-recursos.puerto", 25580);
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", ex -> {
                byte[] body = zip;
                String path = ex.getRequestURI().getPath();
                for (ExtraPacks.Pack xp : extras.packs()) {
                    if (!path.equals(xp.path())) continue;
                    ex.getResponseHeaders().add("Content-Type", "application/zip");
                    ex.sendResponseHeaders(200, xp.file().length());
                    try (OutputStream os = ex.getResponseBody()) {
                        Files.copy(xp.file().toPath(), os);
                    }
                    return;
                }
                if (body == null || !path.startsWith("/eclipse-") || !path.endsWith(".zip")) {
                    ex.sendResponseHeaders(404, -1);
                    ex.close();
                    return;
                }
                ex.getResponseHeaders().add("Content-Type", "application/zip");
                ex.sendResponseHeaders(200, body.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(body);
                }
            });
            server.setExecutor(Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "AventuraMaker-paquete");
                t.setDaemon(true);
                return t;
            }));
            server.start();
            plugin.getLogger().info("Sirviendo el paquete de skins en el puerto " + port);
        } catch (IOException e) {
            server = null;
            plugin.getLogger().warning("No se pudo abrir el puerto " + port + " para el paquete de skins (" + e.getMessage()
                    + "). Se usará la copia de GitHub. Cambia 'paquete-recursos.puerto' en config.yml si está ocupado.");
        }
    }

    public void stop() {
        if (server != null) server.stop(0);
        server = null;
    }

    public HttpServer server() {
        return server;
    }

    public int port() {
        return port;
    }

    public String sha1() {
        return sha1;
    }

    public byte[] bytes() {
        return zip;
    }

    private String localUrl(Player p) {
        if (server == null || zip == null) return null;
        String host = plugin.getConfig().getString("paquete-recursos.direccion", "");
        if (host == null || host.isBlank()) {
            InetSocketAddress vh = p.getVirtualHost();
            host = vh == null ? null : vh.getHostString();
        }
        if (host == null || host.isBlank()) return null;
        if (host.contains(":") && !host.startsWith("[")) host = "[" + host + "]"; // IPv6
        return "http://" + host + ":" + port + "/eclipse-" + sha1.substring(0, 12) + ".zip";
    }

    public void send(Player p) {
        if (!plugin.getConfig().getBoolean("paquete-recursos.activo", true)) return;
        String url = localUrl(p);
        String hash = sha1;
        if (url == null) {
            if (fallbackUrl.isBlank()) return;
            url = fallbackUrl;
            hash = fallbackSha1;
            triedFallback.add(p.getUniqueId());
        }
        request(p, url, hash);
    }

    private void request(Player p, String url, String hash) {
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(UUID.nameUUIDFromBytes("eclipse-npc".getBytes(StandardCharsets.UTF_8)),
                URI.create(url), hash);
        java.util.List<ResourcePackInfo> all = new java.util.ArrayList<>();
        // primero los paquetes extra (abajo), al final el del plugin (arriba: gana)
        String base = url.startsWith("http://") && url.contains("/eclipse-") ? url.substring(0, url.indexOf("/eclipse-")) : null;
        if (base != null) for (ExtraPacks.Pack xp : extras.packs())
            all.add(ResourcePackInfo.resourcePackInfo(UUID.nameUUIDFromBytes(("eclipse-extra-" + xp.file().getName()).getBytes(StandardCharsets.UTF_8)),
                    URI.create(base + xp.path()), xp.sha1()));
        all.add(info);
        p.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(all)
                .replace(true)
                .required(plugin.getConfig().getBoolean("paquete-recursos.obligatorio", false))
                .prompt(Txt.mm("<gold>" + plugin.story().doc().title + "<gray> usa un paquete de recursos: skins de NPCs, HUD"
                        + (extras.packs().isEmpty() ? "" : ", texturas de armas y armaduras") + " y monstruos corrompidos.")));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        triedFallback.remove(p.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) send(p);
        }, 20L);
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent e) {
        Player p = e.getPlayer();
        switch (e.getStatus()) {
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD -> {
                plugin.getLogger().warning(p.getName() + ": no pudo descargar el paquete de skins (" + e.getStatus() + ")");
                if (!triedFallback.contains(p.getUniqueId()) && !fallbackUrl.isBlank()) {
                    triedFallback.add(p.getUniqueId());
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (p.isOnline()) request(p, fallbackUrl, fallbackSha1);
                    }, 20L);
                } else {
                    Txt.send(p, "<red>No se pudo descargar el paquete de skins. <gray>Los NPCs se verán sin su ropa.");
                }
            }
            case DECLINED -> Txt.send(p, "<yellow>Rechazaste el paquete de skins: los NPCs se verán sin su ropa. "
                    + "<gray>Actívalo en Multijugador → Editar servidor → Paquetes de recursos.");
            case SUCCESSFULLY_LOADED -> {
                if (!triedFallback.contains(p.getUniqueId())) loaded.add(p.getUniqueId());
            }
            default -> { }
        }
    }

    private final java.util.Set<UUID> loaded = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** ¿Este jugador tiene cargado nuestro paquete (con la letra del HUD)? */
    public boolean hasPack(Player p) {
        return loaded.contains(p.getUniqueId());
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        loaded.remove(e.getPlayer().getUniqueId());
    }
}
