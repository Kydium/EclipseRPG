package pe.kydium.eclipserpg.dungeon;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.util.Txt;

/**
 * La aldea es indestructible: nadie rompe ni pone bloques, no la dañan explosiones, fuego, endermans,
 * pistones ni líquidos que entran desde fuera. Los admins en modo creativo sí pueden editarla.
 */
public final class VillageGuard implements Listener {

    private final EclipseRPG plugin;
    private long lastMsg;

    public VillageGuard(EclipseRPG plugin) {
        this.plugin = plugin;
    }

    private boolean on() {
        return plugin.getConfig().getBoolean("aldea.indestructible", true);
    }

    public boolean inside(Location l) {
        if (l == null || !on()) return false;
        Location c = plugin.village().center();
        if (c == null || c.getWorld() == null || !c.getWorld().equals(l.getWorld())) return false;
        int r = plugin.village().radius() + plugin.getConfig().getInt("aldea.margen-proteccion", 6);
        double dx = l.getX() - c.getX(), dz = l.getZ() - c.getZ();
        return dx * dx + dz * dz <= (double) r * r && l.getY() > c.getY() - 40;
    }

    private boolean inside(Block b) {
        return inside(b.getLocation().add(0.5, 0, 0.5));
    }

    private boolean bypass(Player p) {
        return p.getGameMode() == GameMode.CREATIVE && p.hasPermission("eclipserpg.admin");
    }

    private void warn(Player p) {
        long now = System.currentTimeMillis();
        if (now - lastMsg < 1500) return;
        lastMsg = now;
        p.sendActionBar(Txt.mm("<gold>🛡 La aldea está protegida: no se puede romper ni construir aquí."));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!inside(e.getBlock()) || bypass(e.getPlayer())) return;
        // los bloques especiales (menas que se regeneran…) sí se pueden picar
        if (plugin.specialBlocks() != null && plugin.specialBlocks().isSpecial(e.getBlock())) return;
        e.setCancelled(true);
        warn(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!inside(e.getBlock()) || bypass(e.getPlayer())) return;
        e.setCancelled(true);
        warn(e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (inside(e.getBlockClicked().getRelative(e.getBlockFace())) && !bypass(e.getPlayer())) {
            e.setCancelled(true);
            warn(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (inside(e.getBlockClicked()) && !bypass(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTrample(PlayerInteractEvent e) {
        if (e.getAction() == Action.PHYSICAL && e.getClickedBlock() != null && e.getClickedBlock().getType() == Material.FARMLAND
                && inside(e.getClickedBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(this::inside);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(this::inside);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (inside(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (!inside(e.getBlock())) return;
        if (e.getPlayer() != null && bypass(e.getPlayer())) return;
        // dejar prender fogatas/velas con mechero a mano no hace daño, pero el fuego que se propaga sí
        if (e.getCause() == BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL && e.getPlayer() != null) {
            e.setCancelled(true);
            warn(e.getPlayer());
            return;
        }
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        if (e.getNewState().getType() == Material.FIRE && inside(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        // líquidos de fuera no entran
        if (inside(e.getToBlock()) && !inside(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonOut(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks())
            if (inside(b) || inside(b.getRelative(e.getDirection()))) {
                e.setCancelled(true);
                return;
            }
        if (inside(e.getBlock().getRelative(e.getDirection())) && !inside(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonIn(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks())
            if (inside(b) && !inside(e.getBlock())) {
                e.setCancelled(true);
                return;
            }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityBlock(EntityChangeBlockEvent e) {
        // endermans, devastadores, wither… (las cosechas de los aldeanos y la arena que cae se dejan)
        if (!inside(e.getBlock())) return;
        if (e.getEntity() instanceof Player p && bypass(p)) return;
        if (e.getEntity() instanceof org.bukkit.entity.Villager || e.getEntity() instanceof org.bukkit.entity.FallingBlock) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHanging(HangingBreakEvent e) {
        if (!inside(e.getEntity().getLocation())) return;
        if (e instanceof org.bukkit.event.hanging.HangingBreakByEntityEvent be && be.getRemover() instanceof Player p && bypass(p)) return;
        if (e.getCause() == HangingBreakEvent.RemoveCause.PHYSICS) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent e) {
        if (inside(e.getBlock())) e.setCancelled(true);
    }
}
