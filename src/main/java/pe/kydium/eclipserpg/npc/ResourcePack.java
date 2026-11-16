package pe.kydium.eclipserpg.npc;

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

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.UUID;

/**
 * Envía al entrar el paquete de recursos con las skins de los NPCs.
 * La dirección y el hash vienen dentro del plugin (los pone la compilación de GitHub),
 * o se pueden cambiar en config.yml (paquete-recursos.url / sha1).
 */
public final class ResourcePack implements Listener {

    private final EclipseRPG plugin;
    private String url;
    private String sha1;

    public ResourcePack(EclipseRPG plugin) {
        this.plugin = plugin;
        Properties p = new Properties();
        try (InputStream in = plugin.getResource("pack.properties")) {
            if (in != null) p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // sin paquete
        }
        url = plugin.getConfig().getString("paquete-recursos.url", "");
        sha1 = plugin.getConfig().getString("paquete-recursos.sha1", "");
        if (url == null || url.isBlank()) {
            url = p.getProperty("url", "");
            sha1 = p.getProperty("sha1", "");
        }
        if (url.isBlank()) plugin.getLogger().warning("No hay paquete de recursos: los NPCs se verán sin skin.");
        else plugin.getLogger().info("Paquete de recursos de NPCs: " + url);
    }

    public void send(Player p) {
        if (url == null || url.isBlank() || !plugin.getConfig().getBoolean("paquete-recursos.activo", true)) return;
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8)),
                URI.create(url), sha1 == null ? "" : sha1);
        p.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .replace(true)
                .required(plugin.getConfig().getBoolean("paquete-recursos.obligatorio", false))
                .prompt(Txt.mm("<gold>Crónicas del Eclipse<gray> necesita su paquete de skins para ver a los NPCs.")));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) send(p);
        }, 10L);
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent e) {
        switch (e.getStatus()) {
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD -> {
                Txt.send(e.getPlayer(), "<red>No se pudo descargar el paquete de skins de los NPCs (" + e.getStatus() + ").");
                plugin.getLogger().warning(e.getPlayer().getName() + ": paquete de recursos " + e.getStatus());
            }
            case DECLINED -> Txt.send(e.getPlayer(), "<yellow>Rechazaste el paquete: los NPCs se verán sin skin.");
            default -> { }
        }
    }
}
