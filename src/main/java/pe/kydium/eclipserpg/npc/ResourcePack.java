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

    public ResourcePack(EclipseRPG plugin) {
        this.plugin = plugin;
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
            plugin.getLogger().info("Paquete de skins listo: " + n + " skins (" + zip.length / 1024 + " KB)");
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

    public void start() {
        rebuild();
        if (!plugin.getConfig().getBoolean("paquete-recursos.activo", true)) return;
        port = plugin.getConfig().getInt("paquete-recursos.puerto", 25580);
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", ex -> {
                byte[] body = zip;
                String path = ex.getRequestURI().getPath();
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
            server.setExecutor(Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "EclipseRPG-paquete");
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
        p.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .replace(true)
                .required(plugin.getConfig().getBoolean("paquete-recursos.obligatorio", false))
                .prompt(Txt.mm("<gold>" + plugin.story().doc().title + "<gray> usa un paquete pequeño con las skins de sus NPCs.")));
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
            default -> { }
        }
    }
}
