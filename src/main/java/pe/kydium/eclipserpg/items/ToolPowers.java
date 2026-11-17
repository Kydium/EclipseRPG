package pe.kydium.eclipserpg.items;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pe.kydium.eclipserpg.EclipseRPG;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Poderes de herramientas: minar en área, vetas, talador, fundir, imán, botín extra, XP, hallazgo de gemas,
 * detector de menas, prisa, visión, durabilidad, autorreparar, replantar, cosecha en área y pesca.
 * Agachado se mina normal (sin área, vetas ni talador).
 */
public final class ToolPowers implements Listener {

    private final EclipseRPG plugin;
    private final Powers powers;
    /** Jugadores rompiendo en cadena (para no repetir el poder en cada bloque extra). */
    private final Set<UUID> busy = new HashSet<>();
    private static final Map<Material, ItemStack> SMELT = new HashMap<>();

    public ToolPowers(EclipseRPG plugin, Powers powers) {
        this.plugin = plugin;
        this.powers = powers;
        startHeldTask();
    }

    /** Resultado de fundir un material (o null). */
    public static ItemStack smelt(Material m) {
        if (SMELT.isEmpty()) {
            Iterator<Recipe> it = Bukkit.recipeIterator();
            while (it.hasNext()) {
                Recipe r = it.next();
                if (r instanceof FurnaceRecipe fr && fr.getInputChoice() instanceof org.bukkit.inventory.RecipeChoice.MaterialChoice mc)
                    for (Material in : mc.getChoices()) SMELT.putIfAbsent(in, fr.getResult());
            }
            SMELT.putIfAbsent(Material.AIR, new ItemStack(Material.AIR));
        }
        ItemStack r = SMELT.get(m);
        return r == null || r.getType().isAir() ? null : r.clone();
    }

    private static boolean roll(Powers.Power p) {
        return p.chance() >= 1 || ThreadLocalRandom.current().nextDouble() < p.chance();
    }

    private Powers.Power get(List<Powers.Power> ps, String type) {
        Powers.Power best = null;
        for (Powers.Power p : ps) if (p.type().equals(type) && (best == null || p.value() > best.value())) best = p;
        return best;
    }

    private static boolean isOre(Material m) {
        String n = m.name();
        return n.endsWith("_ORE") || m == Material.ANCIENT_DEBRIS;
    }

    private static String oreKey(Material m) {
        return m.name().replace("DEEPSLATE_", "").replace("NETHER_", "");
    }

    private static boolean stoneLike(Material m) {
        return m == Material.STONE || m == Material.DEEPSLATE || m == Material.TUFF || m == Material.ANDESITE || m == Material.DIORITE
                || m == Material.GRANITE || m == Material.NETHERRACK || m == Material.CALCITE || m == Material.BLACKSTONE || m == Material.BASALT;
    }

    // ------------------------------------------------------------------ romper bloques

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        ItemStack tool = p.getInventory().getItemInMainHand();
        List<Powers.Power> ps = powers.of(tool);
        if (ps.isEmpty()) return;
        Block b = e.getBlock();
        Material type = b.getType();
        // XP extra
        Powers.Power xp = get(ps, "xp_minar");
        if (xp != null && roll(xp) && !busy.contains(p.getUniqueId())) {
            Location l = b.getLocation().add(0.5, 0.5, 0.5);
            b.getWorld().spawn(l, ExperienceOrb.class, o -> o.setExperience((int) Math.max(1, xp.value())));
        }
        // autorreparar
        Powers.Power rep = get(ps, "reparar");
        if (rep != null && roll(rep)) repair(tool, (int) Math.max(1, rep.value()));
        // hallazgo de gemas en la piedra
        Powers.Power gem = get(ps, "hallazgo");
        if (gem != null && stoneLike(type) && roll(gem)) {
            Material[] g = {Material.DIAMOND, Material.EMERALD, Material.EMERALD, Material.LAPIS_LAZULI, Material.GOLD_NUGGET, Material.GOLD_NUGGET, Material.AMETHYST_SHARD, Material.QUARTZ};
            Material m = g[ThreadLocalRandom.current().nextInt(g.length)];
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), new ItemStack(m, m == Material.GOLD_NUGGET ? 3 : 1));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.6f);
            p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, b.getLocation().add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3, 0);
        }
        // detector de menas
        Powers.Power det = get(ps, "detector");
        if (det != null && stoneLike(type) && !busy.contains(p.getUniqueId()) && roll(det)) detect(p, b, (int) Math.min(10, Math.max(2, det.value())));
        // replantar
        boolean crop = b.getBlockData() instanceof Ageable ag && ag.getAge() >= ag.getMaximumAge() && isCrop(type);
        if (crop && get(ps, "replantar") != null) {
            Material seedBlock = type;
            Bukkit.getScheduler().runTask(plugin, () -> {
                Block now = b.getWorld().getBlockAt(b.getLocation());
                if (now.getType().isAir() && cropSoil(now.getRelative(BlockFace.DOWN).getType(), seedBlock)) now.setType(seedBlock);
            });
        }
        if (busy.contains(p.getUniqueId()) || p.isSneaking()) return;
        // rompe en cadena (área, vetas, talador, cosecha en área)
        List<Block> extra = new ArrayList<>();
        Powers.Power vein = get(ps, "vetas");
        Powers.Power fell = get(ps, "talador");
        Powers.Power area = get(ps, "area");
        Powers.Power harvest = get(ps, "cosecha_area");
        if (vein != null && isOre(type)) extra.addAll(flood(b, x -> isOre(x.getType()) && oreKey(x.getType()).equals(oreKey(type)), (int) Math.min(64, vein.value()), true));
        else if (fell != null && Tag.LOGS.isTagged(type)) extra.addAll(flood(b, x -> Tag.LOGS.isTagged(x.getType()), (int) Math.min(256, fell.value()), true));
        else if (crop && harvest != null) {
            int r = (int) Math.min(3, Math.max(1, harvest.value()));
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    Block o = b.getRelative(dx, 0, dz);
                    if (o.equals(b) || !isCrop(o.getType()) || !(o.getBlockData() instanceof Ageable a) || a.getAge() < a.getMaximumAge()) continue;
                    extra.add(o);
                }
        } else if (area != null && b.isPreferredTool(tool)) {
            int r = (int) Math.min(2, Math.max(1, area.value()));
            float pitch = p.getLocation().getPitch();
            BlockFace face = p.getFacing();
            boolean vertical = Math.abs(pitch) > 55;
            for (int i = -r; i <= r; i++)
                for (int j = -r; j <= r; j++) {
                    if (i == 0 && j == 0) continue;
                    Block o = vertical ? b.getRelative(i, 0, j)
                            : face == BlockFace.NORTH || face == BlockFace.SOUTH ? b.getRelative(i, j, 0) : b.getRelative(0, j, i);
                    float hard = o.getType().getHardness();
                    if (o.getType().isAir() || hard < 0 || hard > Math.max(3, type.getHardness() + 2) || !o.isPreferredTool(tool)
                            || o.getState() instanceof org.bukkit.block.Container) continue;
                    extra.add(o);
                }
        }
        if (extra.isEmpty()) return;
        busy.add(p.getUniqueId());
        try {
            for (Block o : extra) {
                if (tool.getType().isAir() || p.getInventory().getItemInMainHand().getType().isAir()) break;
                p.breakBlock(o);
            }
        } finally {
            busy.remove(p.getUniqueId());
        }
    }

    /** Bloques conectados (incluye diagonales) que cumplen la condición, sin el inicial. */
    private List<Block> flood(Block start, java.util.function.Predicate<Block> ok, int max, boolean diag) {
        List<Block> out = new ArrayList<>();
        Set<Block> seen = new HashSet<>();
        ArrayDeque<Block> q = new ArrayDeque<>();
        q.add(start);
        seen.add(start);
        while (!q.isEmpty() && out.size() < max) {
            Block c = q.poll();
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        if (!diag && Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) continue;
                        Block n = c.getRelative(dx, dy, dz);
                        if (seen.add(n) && ok.test(n)) {
                            out.add(n);
                            q.add(n);
                            if (out.size() >= max) return out;
                        }
                    }
        }
        return out;
    }

    private static boolean isCrop(Material m) {
        return m == Material.WHEAT || m == Material.CARROTS || m == Material.POTATOES || m == Material.BEETROOTS || m == Material.NETHER_WART;
    }

    private static boolean cropSoil(Material soil, Material crop) {
        return crop == Material.NETHER_WART ? soil == Material.SOUL_SAND : soil == Material.FARMLAND;
    }

    private void repair(ItemStack tool, int amount) {
        if (tool == null || !(tool.getItemMeta() instanceof Damageable d) || !d.hasDamage()) return;
        d.setDamage(Math.max(0, d.getDamage() - amount));
        tool.setItemMeta(d);
    }

    private void detect(Player p, Block from, int r) {
        List<Location> found = new ArrayList<>();
        for (int dx = -r; dx <= r && found.size() < 6; dx++)
            for (int dy = -r; dy <= r && found.size() < 6; dy++)
                for (int dz = -r; dz <= r && found.size() < 6; dz++) {
                    Block o = from.getRelative(dx, dy, dz);
                    if (isOre(o.getType())) found.add(o.getLocation().add(0.5, 0.5, 0.5));
                }
        if (found.isEmpty()) return;
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
        int[] n = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (n[0]++ >= 8 || !p.isOnline()) {
                task.cancel();
                return;
            }
            for (Location l : found) p.spawnParticle(Particle.END_ROD, l, 6, 0.25, 0.25, 0.25, 0.01);
        }, 0L, 10L);
    }

    // ------------------------------------------------------------------ lo que suelta

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent e) {
        Player p = e.getPlayer();
        List<Powers.Power> ps = powers.of(p.getInventory().getItemInMainHand());
        if (ps.isEmpty() || e.getItems().isEmpty()) return;
        boolean fundir = get(ps, "fundir") != null;
        Powers.Power extra = get(ps, "botin_extra");
        boolean iman = get(ps, "iman") != null;
        boolean blockItself = e.getBlockState().getType().isItem();
        if (fundir) {
            boolean any = false;
            for (Item it : e.getItems()) {
                ItemStack s = it.getItemStack();
                ItemStack r = smelt(s.getType());
                if (r == null || s.getType() == Material.WET_SPONGE) continue;
                r.setAmount(Math.min(r.getMaxStackSize(), s.getAmount() * r.getAmount()));
                it.setItemStack(r);
                any = true;
            }
            if (any) e.getBlock().getWorld().spawnParticle(Particle.FLAME, e.getBlock().getLocation().add(0.5, 0.5, 0.5), 6, 0.2, 0.2, 0.2, 0.01);
        }
        // botín extra (no para bloques que se sueltan a sí mismos, como piedra lisa o troncos, para no duplicar bloques)
        List<ItemStack> bonus = new ArrayList<>();
        if (extra != null && roll(extra))
            for (Item it : e.getItems()) {
                ItemStack st = it.getItemStack();
                if (blockItself && st.getType() == e.getBlockState().getType()) continue;
                bonus.add(st.clone());
            }
        if (iman) {
            Iterator<Item> it = e.getItems().iterator();
            while (it.hasNext()) {
                Item i = it.next();
                Map<Integer, ItemStack> left = p.getInventory().addItem(i.getItemStack());
                if (left.isEmpty()) it.remove();
                else i.setItemStack(left.values().iterator().next());
            }
        }
        Location at = e.getBlock().getLocation().add(0.5, 0.5, 0.5);
        for (ItemStack st : bonus) {
            if (iman) p.getInventory().addItem(st).values().forEach(l -> p.getWorld().dropItemNaturally(at, l));
            else p.getWorld().dropItemNaturally(at, st);
        }
        if (!bonus.isEmpty()) p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at, 8, 0.3, 0.3, 0.3, 0);
    }

    // ------------------------------------------------------------------ durabilidad

    @EventHandler(ignoreCancelled = true)
    public void onWear(PlayerItemDamageEvent e) {
        Powers.Power d = get(powers.of(e.getItem()), "durabilidad");
        if (d != null && roll(d)) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ pesca

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(e.getCaught() instanceof Item caught)) return;
        Player p = e.getPlayer();
        ItemStack rod = p.getInventory().getItemInMainHand().getType() == Material.FISHING_ROD ? p.getInventory().getItemInMainHand() : p.getInventory().getItemInOffHand();
        List<Powers.Power> ps = powers.of(rod);
        if (ps.isEmpty()) return;
        Powers.Power dbl = get(ps, "pesca_doble");
        if (dbl != null && roll(dbl)) p.getInventory().addItem(caught.getItemStack().clone()).values().forEach(l -> p.getWorld().dropItem(p.getLocation(), l));
        Powers.Power tr = get(ps, "pesca_tesoro");
        if (tr != null && roll(tr)) {
            String[] pool = {"DIAMOND 2", "EMERALD 4", "GOLD_INGOT 4", "NAUTILUS_SHELL 1", "HEART_OF_THE_SEA 1", "libro:mending:1 1", "libro:luck_of_the_sea:3 1",
                    "ENCHANTED_GOLDEN_APPLE 1", "runa:azar 1", "NAME_TAG 1", "EXPERIENCE_BOTTLE 8", "especial:raro 1"};
            String[] pick = pool[ThreadLocalRandom.current().nextInt(pool.length)].split(" ");
            ItemStack it = plugin.shops().buildSpec(pick[0].contains(":") ? pick[0] : "material:" + pick[0], Integer.parseInt(pick[1]));
            if (it != null) {
                p.getInventory().addItem(it).values().forEach(l -> p.getWorld().dropItem(p.getLocation(), l));
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
                pe.kydium.eclipserpg.util.Txt.send(p, "<gold>🎣 ¡Pescaste un tesoro!");
            }
        }
    }

    // ------------------------------------------------------------------ mientras la tienes en la mano

    private boolean helmetVision(Player p) {
        return get(powers.of(p.getInventory().getHelmet()), "vision") != null;
    }

    private void startHeldTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                List<Powers.Power> ps = powers.of(p.getInventory().getItemInMainHand());
                if (ps.isEmpty() && !helmetVision(p)) continue;
                Powers.Power haste = get(ps, "prisa");
                if (haste != null) p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 60, (int) Math.max(0, Math.min(4, haste.value() - 1)), true, false, true));
                if (get(ps, "vision") != null || helmetVision(p)) p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 300, 0, true, false, true));
            }
        }, 40L, 40L);
    }
}
