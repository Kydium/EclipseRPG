package pe.kydium.eclipserpg.items;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.util.Keys;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Poderes de las armas y armaduras especiales: robo de vida, crítico, fuego, congelar, veneno, marchitar,
 * sangrado, rayo, explosión, cadena, ejecutar, empuje, ceguera, levitar, al matar (curar / velocidad),
 * espinas y esquivar. Se escriben en objetos.yml como "poderes: [{tipo: robo_de_vida, valor: 15}]".
 */
public final class Powers implements Listener {

    public record Power(String type, double value, double chance) {}

    /** tipo → {nombre visible, unidad, valor por defecto, probabilidad por defecto, descripción} */
    public static final Map<String, String[]> TYPES = new LinkedHashMap<>();

    static {
        t("robo_de_vida", "<red>❤ Robo de vida", "%", "15", "1", "Se cura una parte del daño que hace");
        t("critico", "<yellow>✦ Golpe crítico", "% más daño", "75", "0.2", "A veces pega mucho más fuerte");
        t("fuego", "<gold>🔥 Quemar", " s", "4", "0.3", "Prende fuego al enemigo");
        t("congelar", "<aqua>❄ Congelar", " s", "3", "0.3", "Lo deja lento y congelado");
        t("veneno", "<dark_green>☠ Veneno", " s", "4", "0.3", "Lo envenena");
        t("marchitar", "<dark_gray>☠ Marchitar", " s", "3", "0.25", "Le pone el efecto wither");
        t("sangrado", "<dark_red>🩸 Sangrado", " por segundo", "1.5", "0.35", "Pierde vida durante 4 segundos");
        t("rayo", "<white>⚡ Rayo", " de daño", "6", "0.15", "Cae un rayo sobre el enemigo");
        t("explosion", "<red>💥 Explosión", " de potencia", "2", "0.12", "Explota sin romper bloques ni dañarte");
        t("cadena", "<blue>⛓ Daño en cadena", "% a los cercanos", "50", "0.3", "El golpe salta a otros enemigos cerca");
        t("ejecutar", "<dark_purple>☠ Ejecutar", "% de vida o menos", "25", "1", "Doble daño a los que están casi muertos");
        t("empuje", "<gray>➹ Empuje", "", "1.5", "1", "Lanza lejos al enemigo");
        t("ceguera", "<dark_gray>◉ Ceguera", " s", "3", "0.25", "Lo deja ciego");
        t("levitar", "<light_purple>⇡ Levitar", " s", "2", "0.2", "Lo hace flotar");
        t("al_matar_curar", "<green>✚ Al matar: cura", " de vida", "4", "1", "Te curas al matar");
        t("al_matar_velocidad", "<aqua>➜ Al matar: velocidad", " s", "5", "1", "Corres más rápido al matar");
        t("aturdir", "<yellow>✷ Aturdir", " s", "1.5", "0.15", "Lo deja quieto un momento");
        t("debilitar", "<gray>↓ Debilitar", " s", "4", "0.3", "Pega menos fuerte");
        t("hambre", "<gold>🍖 Hambre", " s", "6", "0.3", "Le da hambre (a jugadores)");
        t("marcar", "<white>◎ Marcar", " s", "8", "0.5", "Brilla y se ve a través de las paredes");
        t("atraer", "<dark_aqua>⇠ Atraer", "", "1", "0.25", "Lo jala hacia ti");
        t("perforar", "<red>➶ Perforar", "% de daño directo", "20", "1", "Daño extra que ignora la armadura");
        t("golpe_doble", "<yellow>⚔ Golpe doble", "", "0", "0.15", "A veces golpea dos veces");
        t("furia", "<red>⚡ Furia", " s", "4", "0.2", "Te da fuerza al golpear");
        t("al_matar_soles", "<yellow>☀ Al matar: soles", " soles", "5", "1", "Ganas dinero extra al matar");
        t("al_matar_xp", "<aqua>✦ Al matar: XP", " XP", "10", "1", "Ganas XP extra al matar");
        t("al_matar_escudo", "<gold>♦ Al matar: escudo", " s", "6", "1", "Corazones dorados al matar");
        t("duplicar_botin", "<green>✚ Botín doble", "", "0", "0.2", "A veces suelta el doble");
        t("espinas", "<dark_green>✹ Espinas", "% devuelto", "25", "1", "(armadura) Devuelve daño al atacante");
        t("regenerar", "<light_purple>❤ Regenerar", " s", "3", "0.2", "(armadura) Te regeneras al recibir golpes");
        t("resistencia", "<blue>⛨ Resistencia", "% menos daño", "15", "1", "(armadura) Recibes menos daño");
        t("esquivar", "<white>↷ Esquivar", "", "0", "0.1", "(armadura) A veces no recibes el golpe");
    }

    private static void t(String id, String name, String unit, String value, String chance, String desc) {
        TYPES.put(id, new String[]{name, unit, value, chance, desc});
    }

    /** Acepta nombres en español o inglés: "robo de vida", "lifesteal", "crítico"… */
    public static String normType(String s) {
        if (s == null) return null;
        String k = s.toLowerCase(Locale.ROOT).trim().replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
                .replace(" ", "_").replace("-", "_");
        if (TYPES.containsKey(k)) return k;
        return switch (k) {
            case "robo_vida", "vampirismo", "lifesteal", "life_steal", "vampiro", "drenar" -> "robo_de_vida";
            case "crit", "critical", "criticos" -> "critico";
            case "quemar", "fire", "llamas", "incendiar" -> "fuego";
            case "hielo", "frost", "freeze", "escarcha", "ralentizar" -> "congelar";
            case "poison", "envenenar" -> "veneno";
            case "wither" -> "marchitar";
            case "sangrar", "bleed", "hemorragia" -> "sangrado";
            case "trueno", "lightning", "relampago" -> "rayo";
            case "explotar", "explosivo" -> "explosion";
            case "rebote", "chain", "encadenar" -> "cadena";
            case "ejecucion", "execute", "rematar" -> "ejecutar";
            case "knockback", "retroceso", "empujar" -> "empuje";
            case "cegar", "blind", "blindness" -> "ceguera";
            case "levitacion", "levitation", "flotar" -> "levitar";
            case "curar_al_matar", "matar_curar", "al_matar_cura" -> "al_matar_curar";
            case "velocidad_al_matar", "matar_velocidad" -> "al_matar_velocidad";
            case "thorns", "reflejar" -> "espinas";
            case "dodge", "evadir", "evasion" -> "esquivar";
            case "stun", "aturdimiento", "paralizar" -> "aturdir";
            case "weakness", "debilidad" -> "debilitar";
            case "hunger" -> "hambre";
            case "glowing", "brillar", "senalar" -> "marcar";
            case "pull", "jalar", "gancho" -> "atraer";
            case "armor_pierce", "pierce", "penetracion", "perforacion" -> "perforar";
            case "double_hit", "doble_golpe", "golpe_extra" -> "golpe_doble";
            case "rage", "fury", "fuerza", "ira" -> "furia";
            case "al_matar_dinero", "dinero_al_matar", "soles_al_matar", "gold_on_kill" -> "al_matar_soles";
            case "xp_al_matar", "experiencia_al_matar", "xp_on_kill" -> "al_matar_xp";
            case "escudo_al_matar", "absorcion" -> "al_matar_escudo";
            case "botin_doble", "double_loot", "saqueo" -> "duplicar_botin";
            case "regen", "regeneracion" -> "regenerar";
            case "resistance", "proteccion", "aguante" -> "resistencia";
            default -> null;
        };
    }

    /** Líneas de la descripción para un arma con poderes. */
    public static List<String> lore(List<Power> ps) {
        List<String> out = new java.util.ArrayList<>();
        if (ps.isEmpty()) return out;
        out.add("<gray>Poderes:");
        for (Power p : ps) {
            String[] t = TYPES.get(p.type());
            if (t == null) continue;
            String v = p.value() == Math.floor(p.value()) ? String.valueOf((long) p.value()) : String.valueOf(p.value());
            String val = NO_VALUE.contains(p.type()) ? "" : " <white>" + v + "<gray>" + t[1];
            String ch = p.chance() < 1 ? " <dark_gray>(" + Math.round(p.chance() * 100) + "%)" : "";
            out.add(" " + t[0] + val + ch);
        }
        return out;
    }

    static final java.util.Set<String> NO_VALUE = java.util.Set.of("esquivar", "golpe_doble", "duplicar_botin");
    static final java.util.Set<String> ARMOR = java.util.Set.of("espinas", "esquivar", "regenerar", "resistencia");

    // ------------------------------------------------------------------ runas (se forjan en cualquier objeto)

    public static NamespacedKey RUNE, RUNES, SHOT;

    static String ser(List<Power> ps) {
        StringBuilder sb = new StringBuilder();
        for (Power p : ps) sb.append(sb.isEmpty() ? "" : ";").append(p.type()).append(':').append(p.value()).append(':').append(p.chance());
        return sb.toString();
    }

    static List<Power> deser(String s) {
        List<Power> out = new java.util.ArrayList<>();
        if (s == null || s.isBlank()) return out;
        for (String part : s.split(";")) {
            String[] a = part.split(":");
            if (a.length < 3 || !TYPES.containsKey(a[0])) continue;
            try {
                out.add(new Power(a[0], Double.parseDouble(a[1]), Double.parseDouble(a[2])));
            } catch (NumberFormatException ignored) {
                // dato roto
            }
        }
        return out;
    }

    /** Poderes forjados con runas en este objeto (no los que trae de fábrica). */
    public static List<Power> runesOf(org.bukkit.inventory.meta.ItemMeta meta) {
        return meta == null ? List.of() : deser(meta.getPersistentDataContainer().get(RUNES, PersistentDataType.STRING));
    }

    /** Líneas que se añaden a la descripción por las runas. */
    public static List<String> runeLore(org.bukkit.inventory.meta.ItemMeta meta) {
        List<Power> rs = runesOf(meta);
        List<String> out = new java.util.ArrayList<>();
        if (rs.isEmpty()) return out;
        out.add("");
        List<String> l = lore(rs);
        l.set(0, "<light_purple>Runas forjadas:");
        out.addAll(l);
        return out;
    }

    /** Crea una runa: "runa:robo_de_vida:10:1" o "runa:azar". */
    public static ItemStack rune(String spec) {
        String[] a = spec.split(":");
        String type = a.length > 0 ? normType(a[0]) : null;
        java.util.Random r = ThreadLocalRandom.current();
        if (type == null) type = new java.util.ArrayList<>(TYPES.keySet()).get(r.nextInt(TYPES.size()));
        String[] def = TYPES.get(type);
        double v = Double.parseDouble(def[2]), ch = Double.parseDouble(def[3]);
        if (a.length < 2 || normType(a[0]) == null) v = Math.round(v * (0.6 + r.nextDouble() * 0.6) * 10) / 10.0;
        try {
            if (a.length > 1) v = Double.parseDouble(a[1]);
            if (a.length > 2) ch = Double.parseDouble(a[2].replace("%", ""));
        } catch (NumberFormatException ignored) {
            // valores por defecto
        }
        if (ch > 1) ch /= 100;
        Power p = new Power(type, v, ch);
        ItemStack it = new ItemStack(ARMOR.contains(type) ? org.bukkit.Material.PRISMARINE_SHARD : org.bukkit.Material.AMETHYST_SHARD);
        var meta = it.getItemMeta();
        meta.displayName(pe.kydium.eclipserpg.util.Txt.item("<light_purple><bold>Runa</bold> <dark_gray>· " + def[0]));
        List<String> lore = new java.util.ArrayList<>(lore(List.of(p)).subList(1, 2));
        lore.add("<gray>" + def[4]);
        lore.add("");
        lore.add("<yellow>Cómo forjarla:");
        lore.add("<gray>Arma o armadura en la mano, esta runa en");
        lore.add("<gray>la otra mano (tecla F), agáchate y clic derecho.");
        lore.add("<dark_gray>Máximo de runas por objeto: según config.");
        meta.lore(pe.kydium.eclipserpg.util.Txt.lore(lore));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(RUNE, PersistentDataType.STRING, ser(List.of(p)));
        it.setItemMeta(meta);
        return it;
    }

    private static boolean isArmor(org.bukkit.Material m) {
        String n = m.name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS") || m == org.bukkit.Material.SHIELD
                || m == org.bukkit.Material.ELYTRA || m == org.bukkit.Material.TURTLE_HELMET;
    }

    @EventHandler(ignoreCancelled = false)
    public void onForge(org.bukkit.event.player.PlayerInteractEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || !e.getPlayer().isSneaking()) return;
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR && e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack main = p.getInventory().getItemInMainHand(), off = p.getInventory().getItemInOffHand();
        ItemStack runeIt, target;
        if (isRune(off) && !isRune(main)) { runeIt = off; target = main; }
        else if (isRune(main) && !isRune(off)) { runeIt = main; target = off; }
        else return;
        e.setCancelled(true);
        if (target == null || target.getType().isAir() || target.getMaxStackSize() > 1) {
            pe.kydium.eclipserpg.util.Txt.send(p, "<red>Pon un arma, herramienta o armadura en la otra mano.");
            return;
        }
        Power add = deser(runeIt.getItemMeta().getPersistentDataContainer().get(RUNE, PersistentDataType.STRING)).getFirst();
        boolean armor = isArmor(target.getType());
        if (ARMOR.contains(add.type()) != armor) {
            pe.kydium.eclipserpg.util.Txt.send(p, armor ? "<red>Esta runa es para armas, no para armaduras." : "<red>Esta runa es para armaduras.");
            return;
        }
        var meta = target.getItemMeta();
        List<Power> rs = new java.util.ArrayList<>(runesOf(meta));
        int max = plugin.getConfig().getInt("poderes.max-runas", 3);
        int found = -1;
        for (int i = 0; i < rs.size(); i++) if (rs.get(i).type().equals(add.type())) found = i;
        if (found >= 0) {
            Power o = rs.get(found);
            rs.set(found, new Power(o.type(), Math.round((o.value() + add.value()) * 10) / 10.0, Math.min(1, Math.max(o.chance(), add.chance()) + (add.chance() < 1 ? 0.05 : 0))));
        } else if (rs.size() >= max) {
            pe.kydium.eclipserpg.util.Txt.send(p, "<red>Este objeto ya tiene " + max + " runas. Solo puedes reforzar una que ya tenga (misma runa).");
            return;
        } else rs.add(add);
        meta.getPersistentDataContainer().set(RUNES, PersistentDataType.STRING, ser(rs));
        // descripción: la original del objeto + las runas
        String id = plugin.items().idOf(target);
        ItemManager.CustomItem ci = id == null ? null : plugin.items().def(id);
        List<String> base = ci != null ? plugin.items().loreLines(ci, plugin.items().upgradeLevel(target)) : new java.util.ArrayList<>();
        base.addAll(runeLore(meta));
        meta.lore(pe.kydium.eclipserpg.util.Txt.lore(base));
        target.setItemMeta(meta);
        runeIt.setAmount(runeIt.getAmount() - 1);
        p.getWorld().spawnParticle(Particle.ENCHANT, p.getLocation().add(0, 1.2, 0), 60, 0.5, 0.6, 0.5, 0.6);
        p.playSound(p.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1.1f);
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.2f);
        pe.kydium.eclipserpg.util.Txt.send(p, "<light_purple>¡Runa forjada! " + TYPES.get(add.type())[0]);
    }

    private static boolean isRune(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(RUNE);
    }

    private final EclipseRPG plugin;
    private final Map<UUID, Long> cooldown = new HashMap<>();

    public Powers(EclipseRPG plugin) {
        this.plugin = plugin;
        RUNE = Keys.k("runa");
        RUNES = Keys.k("runas");
        SHOT = Keys.k("poderes_disparo");
    }

    /** Poderes de fábrica + runas forjadas. */
    List<Power> of(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return List.of();
        String id = plugin.items().idOf(it);
        ItemManager.CustomItem ci = id == null ? null : plugin.items().def(id);
        List<Power> rs = runesOf(it.getItemMeta());
        if (rs.isEmpty()) return ci == null ? List.of() : ci.powers;
        List<Power> all = new java.util.ArrayList<>(ci == null ? List.of() : ci.powers);
        all.addAll(rs);
        return all;
    }

    private static boolean roll(Power p) {
        return p.chance() >= 1 || ThreadLocalRandom.current().nextDouble() < p.chance();
    }

    private boolean ready(Player p, String what, long ms) {
        long now = System.currentTimeMillis();
        UUID k = new UUID(p.getUniqueId().getMostSignificantBits(), what.hashCode());
        Long last = cooldown.get(k);
        if (last != null && now - last < ms) return false;
        cooldown.put(k, now);
        return true;
    }

    // ------------------------------------------------------------------ disparos

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player) || e.getBow() == null) return;
        List<Power> ps = of(e.getBow());
        if (!ps.isEmpty()) e.getProjectile().getPersistentDataContainer().set(SHOT, PersistentDataType.STRING, ser(ps));
    }

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player p)) return;
        if (!(e.getEntity() instanceof org.bukkit.entity.Trident) || e.getEntity().getPersistentDataContainer().has(SHOT)) return;
        List<Power> ps = of(p.getInventory().getItemInMainHand());
        if (!ps.isEmpty()) e.getEntity().getPersistentDataContainer().set(SHOT, PersistentDataType.STRING, ser(ps));
    }

    // ------------------------------------------------------------------ golpes

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity target) || target instanceof Mannequin) return;
        // armadura de quien recibe
        if (target instanceof Player victim) armor(e, victim);
        Player p;
        List<Power> ps;
        if (e.getDamager() instanceof Player pl) {
            p = pl;
            ps = of(pl.getInventory().getItemInMainHand());
        } else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player pl) {
            p = pl;
            ps = deser(pr.getPersistentDataContainer().get(SHOT, PersistentDataType.STRING));
        } else return;
        if (ps.isEmpty() || target == p || e.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) return;
        double dmg = e.getDamage();
        Location at = target.getLocation().add(0, target.getHeight() / 2, 0);
        for (Power pw : ps) {
            if (!roll(pw)) continue;
            double v = pw.value();
            switch (pw.type()) {
                case "critico" -> {
                    dmg *= 1 + v / 100.0;
                    target.getWorld().spawnParticle(Particle.CRIT, at, 18, 0.3, 0.4, 0.3, 0.2);
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.1f);
                }
                case "ejecutar" -> {
                    AttributeInstance max = target.getAttribute(Attribute.MAX_HEALTH);
                    if (max != null && target.getHealth() <= max.getValue() * v / 100.0) {
                        dmg *= 2;
                        target.getWorld().spawnParticle(Particle.SOUL, at, 12, 0.3, 0.4, 0.3, 0.02);
                    }
                }
                case "fuego" -> target.setFireTicks(Math.max(target.getFireTicks(), (int) (v * 20)));
                case "congelar" -> {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) (v * 20), 3, false, true));
                    target.setFreezeTicks(Math.min(target.getMaxFreezeTicks() + 60, target.getFreezeTicks() + (int) (v * 20) + 140));
                    target.getWorld().spawnParticle(Particle.SNOWFLAKE, at, 20, 0.4, 0.5, 0.4, 0.02);
                }
                case "veneno" -> target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, (int) (v * 20), 1, false, true));
                case "marchitar" -> target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, (int) (v * 20), 1, false, true));
                case "ceguera" -> target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, (int) (v * 20), 0, false, true));
                case "levitar" -> target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, (int) (v * 20), 0, false, true));
                case "empuje" -> {
                    var dir = target.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                    if (dir.lengthSquared() > 0) Bukkit.getScheduler().runTask(plugin, () -> target.setVelocity(dir.normalize().multiply(v * 0.6).setY(0.35)));
                }
                case "sangrado" -> bleed(target, p, v);
                case "aturdir" -> {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) (v * 20), 9, false, true));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, (int) (v * 20), 2, false, true));
                    target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at, 20, 0.3, 0.5, 0.3, 0.1);
                }
                case "debilitar" -> target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, (int) (v * 20), 1, false, true));
                case "hambre" -> target.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, (int) (v * 20), 2, false, true));
                case "marcar" -> target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, (int) (v * 20), 0, false, false));
                case "furia" -> p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, (int) (v * 20), 0, false, true));
                case "atraer" -> {
                    var dir = p.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0);
                    if (dir.lengthSquared() > 1) Bukkit.getScheduler().runTask(plugin, () -> target.setVelocity(dir.normalize().multiply(v * 0.7).setY(0.25)));
                }
                case "perforar" -> {
                    double extra = e.getDamage() * v / 100.0;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (target.isValid() && !target.isDead()) target.setHealth(Math.max(0, target.getHealth() - extra));
                    });
                }
                case "golpe_doble" -> {
                    double again = e.getDamage();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (target.isValid() && !target.isDead()) {
                            target.setNoDamageTicks(0);
                            target.damage(again, p);
                            target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, at, 1, 0, 0, 0, 0);
                        }
                    }, 4L);
                }
                case "rayo" -> {
                    if (!ready(p, "rayo", 1500)) break;
                    target.getWorld().strikeLightningEffect(target.getLocation());
                    Bukkit.getScheduler().runTask(plugin, () -> { if (target.isValid()) target.damage(v, p); });
                }
                case "explosion" -> {
                    if (!ready(p, "explosion", 2000)) break;
                    Location l = target.getLocation();
                    Bukkit.getScheduler().runTask(plugin, () -> l.getWorld().createExplosion(l, (float) Math.min(4, v), false, false, p));
                }
                case "cadena" -> {
                    if (!ready(p, "cadena", 600)) break;
                    double part = e.getDamage() * v / 100.0;
                    int n = 0;
                    for (Entity o : target.getNearbyEntities(5, 3, 5)) {
                        if (n >= 3 || !(o instanceof LivingEntity le) || o == p || o instanceof Player || o instanceof Mannequin
                                || o instanceof org.bukkit.entity.Villager || o instanceof org.bukkit.entity.Tameable tm && tm.isTamed()) continue;
                        if (!(o instanceof org.bukkit.entity.Enemy) && !(o instanceof org.bukkit.entity.Mob)) continue;
                        n++;
                        Location from = at.clone(), to = le.getLocation().add(0, le.getHeight() / 2, 0);
                        var step = to.toVector().subtract(from.toVector()).multiply(1 / 8.0);
                        for (int i = 0; i < 8; i++) {
                            from.add(step);
                            le.getWorld().spawnParticle(Particle.DUST, from, 2, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(120, 180, 255), 1.2f));
                        }
                        Bukkit.getScheduler().runTask(plugin, () -> { if (le.isValid()) le.damage(part, p); });
                    }
                }
                default -> { }
            }
        }
        e.setDamage(dmg);
        for (Power pw : ps) {
            if (!pw.type().equals("robo_de_vida") || !roll(pw)) continue;
            AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
            double heal = dmg * pw.value() / 100.0;
            if (max != null && !p.isDead()) p.setHealth(Math.min(max.getValue(), p.getHealth() + heal));
            p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), 1, 0.2, 0.1, 0.2, 0);
        }
    }

    private void bleed(LivingEntity target, Player from, double perSecond) {
        int[] n = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!target.isValid() || target.isDead() || n[0]++ >= 4) {
                task.cancel();
                return;
            }
            target.damage(perSecond, from);
            target.setNoDamageTicks(0);
            target.getWorld().spawnParticle(Particle.BLOCK, target.getLocation().add(0, 1, 0), 12, 0.25, 0.4, 0.25,
                    org.bukkit.Material.REDSTONE_BLOCK.createBlockData());
        }, 20L, 20L);
    }

    private void armor(EntityDamageByEntityEvent e, Player victim) {
        for (ItemStack it : victim.getInventory().getArmorContents()) {
            for (Power pw : of(it)) {
                if (!roll(pw)) continue;
                switch (pw.type()) {
                    case "esquivar" -> {
                        e.setCancelled(true);
                        victim.getWorld().spawnParticle(Particle.CLOUD, victim.getLocation().add(0, 1, 0), 8, 0.3, 0.4, 0.3, 0.02);
                        victim.playSound(victim.getLocation(), Sound.ENTITY_BREEZE_JUMP, 0.7f, 1.4f);
                        return;
                    }
                    case "resistencia" -> e.setDamage(e.getDamage() * Math.max(0, 1 - pw.value() / 100.0));
                    case "regenerar" -> victim.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, (int) (pw.value() * 20), 1, false, true));
                    case "espinas" -> {
                        Entity src = e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Entity sh ? sh : e.getDamager();
                        if (src instanceof LivingEntity le && le != victim) {
                            double back = e.getDamage() * pw.value() / 100.0;
                            Bukkit.getScheduler().runTask(plugin, () -> { if (le.isValid()) le.damage(back, victim); });
                        }
                    }
                    default -> { }
                }
            }
        }
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        Player p = e.getEntity().getKiller();
        if (p == null) return;
        for (Power pw : of(p.getInventory().getItemInMainHand())) {
            switch (pw.type()) {
                case "al_matar_curar" -> {
                    AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
                    if (max != null) p.setHealth(Math.min(max.getValue(), p.getHealth() + pw.value()));
                    p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), 3, 0.3, 0.2, 0.3, 0);
                }
                case "al_matar_velocidad" -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, (int) (pw.value() * 20), 1, false, true));
                case "al_matar_escudo" -> p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, (int) (pw.value() * 20), 1, false, true));
                case "al_matar_soles" -> { if (roll(pw)) plugin.levels().addSoles(p, Math.round(pw.value())); }
                case "al_matar_xp" -> { if (roll(pw)) plugin.levels().addXp(p, Math.round(pw.value())); }
                case "duplicar_botin" -> {
                    if (roll(pw) && !(e.getEntity() instanceof Player)) {
                        List<ItemStack> extra = new java.util.ArrayList<>();
                        for (ItemStack d : e.getDrops()) if (d != null) extra.add(d.clone());
                        e.getDrops().addAll(extra);
                        p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, e.getEntity().getLocation().add(0, 1, 0), 12, 0.4, 0.4, 0.4, 0);
                    }
                }
                default -> { }
            }
        }
    }
}
