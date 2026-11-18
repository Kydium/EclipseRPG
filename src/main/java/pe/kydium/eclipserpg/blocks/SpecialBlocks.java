package pe.kydium.eclipserpg.blocks;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.util.Keys;
import pe.kydium.eclipserpg.util.Txt;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Bloques especiales de bloques.yml: minerales que se regeneran, bloques que solo dan experiencia
 * y máquinas que fabrican solas. Los bloques colocados se guardan en datos-bloques.yml.
 */
public final class SpecialBlocks implements Listener, TabExecutor {

    public static final class Def {
        public String id, name = "", type = "regenera";
        public Material block = Material.STONE, waiting = Material.BEDROCK;
        public int seconds = 30, xp;
        public boolean unbreakable = true;
        public final List<String> drops = new ArrayList<>(), produce = new ArrayList<>();
        public String fuel;
    }

    static final class Placed {
        String def;
        long readyAt;          // regenera/experiencia: cuándo vuelve; máquina: cuándo fabrica
        final List<ItemStack> stored = new ArrayList<>();
    }

    private final EclipseRPG plugin;
    private final NamespacedKey KEY;
    private final Map<String, Def> defs = new LinkedHashMap<>();
    private final Map<String, Placed> placed = new HashMap<>(); // "mundo;x;y;z"
    private boolean dirty;

    public SpecialBlocks(EclipseRPG plugin) {
        this.plugin = plugin;
        this.KEY = Keys.k("bloque_especial");
    }

    public void load() {
        defs.clear();
        ConfigurationSection root = plugin.story().yaml("bloques").getConfigurationSection("bloques");
        if (root != null) for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            Def d = new Def();
            d.id = id;
            d.name = s.getString("nombre", id);
            d.type = s.getString("tipo", "regenera").toLowerCase(Locale.ROOT).replace("á", "a");
            if (d.type.startsWith("exp") || d.type.equals("xp")) d.type = "experiencia";
            else if (d.type.startsWith("maq") || d.type.startsWith("gener") || d.type.startsWith("auto")) d.type = "maquina";
            else d.type = "regenera";
            Material b = Material.matchMaterial(s.getString("bloque", "STONE"));
            d.block = b != null && b.isBlock() ? b : Material.STONE;
            Material w = Material.matchMaterial(s.getString("mientras", "BEDROCK"));
            d.waiting = w != null && w.isBlock() ? w : Material.BEDROCK;
            d.seconds = Math.max(1, s.getInt("tiempo", 30));
            d.xp = Math.max(0, s.getInt("experiencia", 0));
            d.unbreakable = s.getBoolean("irrompible", !d.type.equals("maquina"));
            d.drops.addAll(s.getStringList("suelta"));
            d.produce.addAll(s.getStringList("produce"));
            d.fuel = s.getString("combustible");
            defs.put(id, d);
        }
        loadPlaced();
    }

    public Map<String, Def> defs() {
        return defs;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) savePlaced();
        }, 600L, 600L);
    }

    // ---------------------------------------------------------------- objeto que coloca el bloque

    /** "bloque:<id>" para tiendas, botín y misiones. */
    public ItemStack item(String id, int amount) {
        Def d = defs.get(id);
        if (d == null) return null;
        ItemStack it = new ItemStack(d.block.isItem() ? d.block : Material.STONE, Math.max(1, amount));
        var meta = it.getItemMeta();
        meta.displayName(Txt.item(d.name));
        List<String> lore = new ArrayList<>();
        lore.add(switch (d.type) {
            case "experiencia" -> "<green>Bloque de experiencia · <white>" + d.xp + " de XP";
            case "maquina" -> "<gold>Máquina · <white>fabrica cada " + time(d.seconds);
            default -> "<aqua>Mineral que se regenera · <white>" + time(d.seconds);
        });
        if (d.type.equals("maquina")) {
            for (String p : d.produce) lore.add("<gray>• " + pretty(p));
            if (d.fuel != null) lore.add("<gray>Gasta: <white>" + pretty(d.fuel));
            lore.add("<dark_gray>Pon un cofre pegado o sácalo con clic derecho.");
        }
        lore.add("<dark_gray>Colócalo donde quieras.");
        meta.lore(Txt.lore(lore));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, id);
        it.setItemMeta(meta);
        return it;
    }

    private static String time(int s) {
        return s >= 3600 ? (s / 3600) + " h" : s >= 60 ? (s / 60) + " min" : s + " s";
    }

    private static String pretty(String spec) {
        String[] a = spec.trim().split("\\s+");
        String name = a[0].contains(":") ? a[0].substring(a[0].indexOf(':') + 1) : a[0];
        return (a.length > 1 ? a[1] + " " : "") + name.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        ItemStack it = e.getItemInHand();
        String id = it.hasItemMeta() ? it.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING) : null;
        if (id == null || !defs.containsKey(id)) return;
        put(e.getBlockPlaced(), id);
        Txt.send(e.getPlayer(), "<green>✔ " + defs.get(id).name + " <green>colocado.");
    }

    public void put(Block b, String id) {
        Def d = defs.get(id);
        if (d == null) return;
        b.setType(d.block, false);
        Placed p = new Placed();
        p.def = id;
        p.readyAt = d.type.equals("maquina") ? System.currentTimeMillis() + d.seconds * 1000L : 0;
        placed.put(key(b), p);
        dirty = true;
        b.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, b.getLocation().add(0.5, 1, 0.5), 12, 0.3, 0.2, 0.3, 0);
    }

    private static String key(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    private static Block block(String k) {
        String[] a = k.split(";");
        World w = Bukkit.getWorld(a[0]);
        return w == null ? null : w.getBlockAt(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]));
    }

    // ---------------------------------------------------------------- picar

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Placed p = placed.get(key(b));
        if (p == null) return;
        Def d = defs.get(p.def);
        Player pl = e.getPlayer();
        if (d == null) {
            placed.remove(key(b));
            return;
        }
        boolean remove = pl.isSneaking() && (pl.hasPermission("eclipserpg.admin") || !d.unbreakable);
        if (d.type.equals("maquina")) {
            if (!remove && d.unbreakable) {
                e.setCancelled(true);
                Txt.send(pl, "<gray>Esta máquina no se puede romper.");
                return;
            }
            e.setDropItems(false);
            for (ItemStack it : p.stored) b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), it);
            ItemStack self = item(d.id, 1);
            if (self != null) b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), self);
            placed.remove(key(b));
            dirty = true;
            return;
        }
        if (remove) {
            e.setDropItems(false);
            ItemStack self = item(d.id, 1);
            if (self != null) b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), self);
            placed.remove(key(b));
            dirty = true;
            Txt.send(pl, "<yellow>Quitaste " + d.name + "<yellow>.");
            return;
        }
        if (System.currentTimeMillis() < p.readyAt) {
            e.setCancelled(true);
            return;
        }
        // suelta lo suyo y queda esperando
        e.setDropItems(d.drops.isEmpty() && !d.type.equals("experiencia"));
        e.setExpToDrop(0);
        Location c = b.getLocation().add(0.5, 0.5, 0.5);
        if (!d.type.equals("experiencia")) for (String spec : d.drops) {
            ItemStack it = roll(spec);
            if (it != null) b.getWorld().dropItemNaturally(c, it);
        }
        if (d.xp > 0) b.getWorld().spawn(c, ExperienceOrb.class, o -> o.setExperience(d.xp));
        p.readyAt = System.currentTimeMillis() + d.seconds * 1000L;
        dirty = true;
        Bukkit.getScheduler().runTask(plugin, () -> b.setType(d.waiting, false));
    }

    private ItemStack roll(String spec) {
        String[] a = spec.trim().split("\\s+");
        int n = 1;
        if (a.length > 1) {
            String[] r = a[1].split("-");
            try {
                int lo = Integer.parseInt(r[0]), hi = r.length > 1 ? Integer.parseInt(r[1]) : lo;
                n = lo + ThreadLocalRandom.current().nextInt(Math.max(1, hi - lo + 1));
            } catch (NumberFormatException ignored) {
                // 1
            }
        }
        if (n <= 0) return null;
        String s = a[0].contains(":") ? a[0] : "material:" + a[0];
        return plugin.shops().buildSpec(s, n);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> placed.containsKey(key(b)));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> placed.containsKey(key(b)));
    }

    // ---------------------------------------------------------------- máquinas

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null || e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Placed p = placed.get(key(e.getClickedBlock()));
        if (p == null) return;
        Def d = defs.get(p.def);
        if (d == null) return;
        e.setCancelled(true);
        Player pl = e.getPlayer();
        if (!d.type.equals("maquina")) {
            long left = Math.max(0, (p.readyAt - System.currentTimeMillis()) / 1000);
            Txt.send(pl, d.name + " <gray>· " + (left > 0 ? "vuelve en <white>" + left + " s" : "<green>listo para picar"));
            return;
        }
        if (!p.stored.isEmpty()) {
            for (ItemStack it : p.stored) pl.getInventory().addItem(it).values().forEach(l -> pl.getWorld().dropItem(pl.getLocation(), l));
            p.stored.clear();
            dirty = true;
            pl.playSound(pl.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1f);
            Txt.send(pl, "<green>Sacaste lo que fabricó " + d.name + "<green>.");
        } else {
            long left = Math.max(0, (p.readyAt - System.currentTimeMillis()) / 1000);
            Txt.send(pl, d.name + " <gray>· fabrica de nuevo en <white>" + left + " s" + (d.fuel != null ? " <gray>(gasta " + pretty(d.fuel) + ", ponlo en un cofre pegado)" : ""));
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Placed> en : placed.entrySet()) {
            Placed p = en.getValue();
            Def d = defs.get(p.def);
            if (d == null || p.readyAt == 0 || now < p.readyAt) continue;
            Block b;
            try {
                b = block(en.getKey());
            } catch (RuntimeException ex) {
                continue;
            }
            if (b == null || !b.getWorld().isChunkLoaded(b.getX() >> 4, b.getZ() >> 4)) continue;
            if (d.type.equals("maquina")) {
                if (!b.getType().equals(d.block)) b.setType(d.block, false);
                p.readyAt = now + d.seconds * 1000L;
                Container chest = adjacentChest(b);
                if (d.fuel != null && !takeFuel(chest, d.fuel)) continue;
                for (String spec : d.produce) {
                    ItemStack it = roll(spec);
                    if (it == null) continue;
                    if (chest != null && chest.getInventory().addItem(it).isEmpty()) continue;
                    if (p.stored.size() < 54) p.stored.add(it);
                }
                b.getWorld().spawnParticle(Particle.SMOKE, b.getLocation().add(0.5, 1.1, 0.5), 6, 0.2, 0.1, 0.2, 0.01);
                b.getWorld().playSound(b.getLocation(), Sound.BLOCK_PISTON_EXTEND, 0.4f, 1.3f);
            } else {
                p.readyAt = 0;
                b.setType(d.block, false);
                b.getWorld().spawnParticle(Particle.WAX_OFF, b.getLocation().add(0.5, 0.5, 0.5), 12, 0.4, 0.4, 0.4, 0.5);
                b.getWorld().playSound(b.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.2f);
            }
            dirty = true;
        }
    }

    private static Container adjacentChest(Block b) {
        for (BlockFace f : new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST})
            if (b.getRelative(f).getState() instanceof Container c && !(c instanceof org.bukkit.block.BrewingStand)) return c;
        return null;
    }

    private boolean takeFuel(Container chest, String spec) {
        if (chest == null) return false;
        String[] a = spec.trim().split("\\s+");
        Material m = Material.matchMaterial(a[0]);
        int n = a.length > 1 ? Math.max(1, Integer.parseInt(a[1].replaceAll("\\D", "").isEmpty() ? "1" : a[1].replaceAll("\\D", ""))) : 1;
        if (m == null || !chest.getInventory().containsAtLeast(new ItemStack(m), n)) return false;
        chest.getInventory().removeItem(new ItemStack(m, n));
        return true;
    }

    // ---------------------------------------------------------------- guardar

    private File dataFile() {
        return new File(plugin.getDataFolder(), "datos-bloques.yml");
    }

    private void loadPlaced() {
        placed.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile());
        ConfigurationSection s = y.getConfigurationSection("bloques");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            ConfigurationSection b = s.getConfigurationSection(k);
            if (b == null) continue;
            Placed p = new Placed();
            p.def = b.getString("tipo");
            p.readyAt = b.getLong("listo", 0);
            List<?> st = b.getList("guardado");
            if (st != null) for (Object o : st) if (o instanceof ItemStack it) p.stored.add(it);
            placed.put(k.replace('|', ';').replace(',', '.'), p);
        }
    }

    public void savePlaced() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, Placed> e : placed.entrySet()) {
            String k = "bloques." + e.getKey().replace('.', ',').replace(';', '|');
            y.set(k + ".tipo", e.getValue().def);
            y.set(k + ".listo", e.getValue().readyAt);
            if (!e.getValue().stored.isEmpty()) y.set(k + ".guardado", e.getValue().stored);
        }
        try {
            y.save(dataFile());
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudieron guardar los bloques especiales: " + ex.getMessage());
        }
    }

    public boolean isSpecial(Block b) {
        return placed.containsKey(key(b));
    }

    public int placedCount() {
        return placed.size();
    }

    // ---------------------------------------------------------------- /bloque

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Solo en el juego.");
            return true;
        }
        if (!p.hasPermission("eclipserpg.admin")) {
            Txt.send(p, "<red>Solo para administradores.");
            return true;
        }
        String sub = a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : "lista";
        Block t = p.getTargetBlockExact(6);
        switch (sub) {
            case "poner" -> {
                if (a.length < 2 || !defs.containsKey(a[1])) {
                    Txt.send(p, "<red>Usa /bloque poner <id>. Hay: <white>" + String.join(", ", defs.keySet()));
                    return true;
                }
                if (t == null) {
                    Txt.send(p, "<red>Mira un bloque (a 6 bloques o menos).");
                    return true;
                }
                put(t, a[1]);
                Txt.send(p, "<green>✔ " + defs.get(a[1]).name + " <green>puesto.");
            }
            case "quitar" -> {
                if (t == null || placed.remove(key(t)) == null) Txt.send(p, "<red>Ese bloque no es especial.");
                else {
                    dirty = true;
                    Txt.send(p, "<yellow>Ya no es un bloque especial.");
                }
            }
            case "dar" -> {
                if (a.length < 2 || !defs.containsKey(a[1])) {
                    Txt.send(p, "<red>Usa /bloque dar <id> [cantidad]. Hay: <white>" + String.join(", ", defs.keySet()));
                    return true;
                }
                int n = a.length > 2 ? Math.max(1, Integer.parseInt(a[2].replaceAll("\\D", "1"))) : 1;
                plugin.items().give(p, item(a[1], n));
            }
            default -> {
                Txt.send(p, "<gold>Bloques especiales <gray>(" + placed.size() + " colocados):");
                for (Def d : defs.values()) Txt.send(p, " <white>" + d.id + " <gray>· " + d.type + " · " + d.name);
                Txt.send(p, "<gray>/bloque poner <id> · /bloque quitar · /bloque dar <id> [n]");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1) return List.of("lista", "poner", "quitar", "dar");
        if (a.length == 2 && (a[0].equalsIgnoreCase("poner") || a[0].equalsIgnoreCase("dar"))) return new ArrayList<>(defs.keySet());
        return List.of();
    }
}
