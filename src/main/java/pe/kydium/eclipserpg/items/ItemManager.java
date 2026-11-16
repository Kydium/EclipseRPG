package pe.kydium.eclipserpg.items;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.util.Keys;
import pe.kydium.eclipserpg.util.Reg;
import pe.kydium.eclipserpg.util.Txt;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Objetos únicos definidos en items.yml (armas de jefes, fragmentos, pergaminos...). */
public final class ItemManager implements Listener {

    public record AttrDef(String attribute, double value, String operation, String slot) {}

    public static final class CustomItem {
        public String id;
        public Material material;
        public String name;
        public List<String> lore = new ArrayList<>();
        public boolean unbreakable;
        public boolean glint;
        public boolean upgradable;
        public String color;
        public String action;        // "campamento"
        public String heldEffect;    // efecto mientras se tiene en la mano
        public int heldEffectLevel;
        public final Map<String, Integer> enchants = new LinkedHashMap<>();
        public final List<AttrDef> attributes = new ArrayList<>();
    }

    private final EclipseRPG plugin;
    private final Map<String, CustomItem> items = new LinkedHashMap<>();

    public ItemManager(EclipseRPG plugin) {
        this.plugin = plugin;
    }

    public void load() {
        items.clear();
        YamlConfiguration y = plugin.story().yaml("objetos");
        ConfigurationSection root = y.getConfigurationSection("items");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            Material m = Material.matchMaterial(s.getString("material", "STONE"));
            if (m == null) {
                plugin.getLogger().warning("items.yml: material inválido en " + id);
                continue;
            }
            CustomItem ci = new CustomItem();
            ci.id = id;
            ci.material = m;
            ci.name = s.getString("nombre", id);
            ci.lore = s.getStringList("lore");
            ci.unbreakable = s.getBoolean("irrompible", false);
            ci.glint = s.getBoolean("brillo", false);
            ci.upgradable = s.getBoolean("mejorable", false);
            ci.color = s.getString("color");
            ci.action = s.getString("accion");
            ci.heldEffect = s.getString("efecto-en-mano");
            ci.heldEffectLevel = s.getInt("efecto-nivel", 0);
            ConfigurationSection en = s.getConfigurationSection("encantamientos");
            if (en != null) for (String k : en.getKeys(false)) ci.enchants.put(k, en.getInt(k));
            for (Map<?, ?> a : s.getMapList("atributos")) {
                Object attr = a.get("atributo");
                Object val = a.get("valor");
                if (attr == null || val == null) continue;
                Object op = a.get("operacion");
                Object slot = a.get("slot");
                ci.attributes.add(new AttrDef(attr.toString(), Double.parseDouble(val.toString()),
                        op == null ? null : op.toString(), slot == null ? null : slot.toString()));
            }
            items.put(id, ci);
        }
        plugin.getLogger().info("Objetos cargados: " + items.size());
    }

    public Set<String> ids() {
        return items.keySet();
    }

    public CustomItem def(String id) {
        return items.get(id);
    }

    public ItemStack create(String id, int amount) {
        CustomItem ci = items.get(id);
        if (ci == null) return null;
        ItemStack it = new ItemStack(ci.material, Math.max(1, amount));
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        meta.displayName(Txt.item(ci.name));
        meta.lore(Txt.lore(ci.lore));
        for (Map.Entry<String, Integer> e : ci.enchants.entrySet()) {
            Enchantment ench = Reg.enchant(e.getKey());
            if (ench == null) {
                plugin.getLogger().warning("items.yml: encantamiento desconocido '" + e.getKey() + "' en " + id);
                continue;
            }
            if (meta instanceof EnchantmentStorageMeta book) book.addStoredEnchant(ench, e.getValue(), true);
            else meta.addEnchant(ench, e.getValue(), true);
        }
        int idx = 0;
        for (AttrDef a : ci.attributes) {
            Attribute attr = Reg.attribute(a.attribute());
            if (attr == null) {
                plugin.getLogger().warning("items.yml: atributo desconocido '" + a.attribute() + "' en " + id);
                continue;
            }
            NamespacedKey key = pe.kydium.eclipserpg.util.Keys.k(("it_" + id + "_" + idx++).toLowerCase());
            meta.addAttributeModifier(attr, new AttributeModifier(key, a.value(), Reg.operation(a.operation()), Reg.slot(a.slot())));
        }
        if (ci.unbreakable) meta.setUnbreakable(true);
        if (ci.glint) meta.setEnchantmentGlintOverride(true);
        if (ci.color != null && meta instanceof LeatherArmorMeta leather) {
            try {
                leather.setColor(Color.fromRGB(Integer.parseInt(ci.color.replace("#", ""), 16)));
            } catch (NumberFormatException ignored) {
                // color inválido
            }
        }
        meta.getPersistentDataContainer().set(Keys.ITEM, PersistentDataType.STRING, id);
        it.setItemMeta(meta);
        return it;
    }

    /** Id del objeto personalizado o null. */
    public String idOf(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(Keys.ITEM, PersistentDataType.STRING);
    }

    public int upgradeLevel(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return 0;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return 0;
        Integer v = meta.getPersistentDataContainer().get(Keys.UPGRADE, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    /** Sube +1 el daño de un arma única. Devuelve false si no se puede. */
    public boolean upgrade(ItemStack it, int maxLevel) {
        String id = idOf(it);
        CustomItem ci = id == null ? null : items.get(id);
        if (ci == null || !ci.upgradable) return false;
        int lvl = upgradeLevel(it);
        if (lvl >= maxLevel) return false;
        lvl++;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return false;
        NamespacedKey key = pe.kydium.eclipserpg.util.Keys.k("mejora");
        Collection<AttributeModifier> mods = meta.getAttributeModifiers(Attribute.ATTACK_DAMAGE);
        if (mods != null) {
            for (AttributeModifier m : new ArrayList<>(mods)) {
                if (key.equals(m.getKey())) meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE, m);
            }
        }
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                new AttributeModifier(key, lvl, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.displayName(Txt.item(ci.name + " <yellow>+" + lvl));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.UPGRADE, PersistentDataType.INTEGER, lvl);
        it.setItemMeta(meta);
        return true;
    }

    public int count(Player p, String id) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (id.equals(idOf(it))) n += it.getAmount();
        }
        return n;
    }

    /** Quita una cantidad de un objeto personalizado del inventario. */
    public boolean take(Player p, String id, int amount) {
        if (count(p, id) < amount) return false;
        ItemStack[] contents = p.getInventory().getContents();
        int left = amount;
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack it = contents[i];
            if (!id.equals(idOf(it))) continue;
            int take = Math.min(left, it.getAmount());
            it.setAmount(it.getAmount() - take);
            left -= take;
            if (it.getAmount() <= 0) p.getInventory().setItem(i, null);
        }
        return true;
    }

    public void give(Player p, ItemStack it) {
        if (it == null) return;
        for (ItemStack rest : p.getInventory().addItem(it).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
    }

    // ---------- Campamento ----------

    public Location campLocation() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("campamento");
        if (s != null && s.getString("mundo") != null) {
            World w = Bukkit.getWorld(s.getString("mundo", "world"));
            if (w != null) {
                return new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                        (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
            }
        }
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        ItemStack it = e.getItem();
        String id = idOf(it);
        CustomItem ci = id == null ? null : items.get(id);
        if (ci == null || ci.action == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (ci.action.toLowerCase().startsWith("invocar:")) {
            String mob = ci.action.substring(8);
            if (plugin.mobs().def(mob) == null) return;
            it.setAmount(it.getAmount() - 1);
            Location at = plugin.mobs().spawnSpot(p.getLocation(), 7);
            p.getWorld().strikeLightningEffect(at);
            for (Player near : p.getWorld().getPlayers()) {
                if (near.getLocation().distanceSquared(at) < 48 * 48) {
                    Txt.send(near, "<dark_red><bold>¡" + p.getName() + " rompió un sello! Algo despierta...");
                    Txt.sound(near, "entity.wither.spawn", 0.7f, 0.8f);
                }
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.mobs().spawn(mob, at), 40L);
            return;
        }
        if (ci.action.equalsIgnoreCase("campamento")) {
            it.setAmount(it.getAmount() - 1);
            p.teleportAsync(campLocation()).thenRun(() -> {
                Txt.sound(p, "entity.enderman.teleport", 1f, 1f);
                Txt.send(p, "<yellow>Volviste a " + plugin.villageName() + ".");
            });
        }
    }

    /** Efectos que dan algunos objetos mientras se tienen en la mano principal. */
    public void startHeldEffectTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                String id = idOf(p.getInventory().getItemInMainHand());
                CustomItem ci = id == null ? null : items.get(id);
                if (ci == null || ci.heldEffect == null) continue;
                PotionEffectType type = Reg.effect(ci.heldEffect);
                if (type == null) continue;
                p.addPotionEffect(new PotionEffect(type, 300, ci.heldEffectLevel, true, false, true));
            }
        }, 40L, 40L);
    }
}
