package pe.kydium.eclipserpg.mobs;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.util.Keys;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Vida de los monstruos junto a su nombre ("☠ Zombi Corrompido ❤ 14/20") unos segundos después de cada golpe.
 * Luego el nombre vuelve a ser el de antes (los que no tenían nombre lo pierden, así siguen desapareciendo solos).
 */
public final class MobHealth implements Listener {

    private final EclipseRPG plugin;
    private final NamespacedKey BASE, HAD, SHOWN;
    private final Map<UUID, Long> until = new HashMap<>();

    public MobHealth(EclipseRPG plugin) {
        this.plugin = plugin;
        BASE = Keys.k("vida_nombre");
        HAD = Keys.k("vida_tenia_nombre");
        SHOWN = Keys.k("vida_visible");
    }

    private boolean on() {
        return plugin.getConfig().getBoolean("hud.vida-monstruos", true);
    }

    private boolean applies(Entity e) {
        if (!(e instanceof LivingEntity) || e instanceof Player || e instanceof ArmorStand || e instanceof Mannequin) return false;
        if (plugin.npcEntities() != null && plugin.npcEntities().isNpcOrFiller(e)) return false;
        if (e.getScoreboardTags().contains(pe.kydium.eclipserpg.npc.Combat.CORPSE)) return false;
        return plugin.mobs() == null || !plugin.mobs().hasBar(e);
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            var it = until.entrySet().iterator();
            while (it.hasNext()) {
                var en = it.next();
                if (en.getValue() > now) continue;
                it.remove();
                Entity e = Bukkit.getEntity(en.getKey());
                if (e instanceof LivingEntity le) restore(le);
            }
        }, 20L, 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!on() || !applies(e.getEntity())) return;
        LivingEntity le = (LivingEntity) e.getEntity();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!le.isValid() || le.isDead()) return;
            show(le);
            until.put(le.getUniqueId(), System.currentTimeMillis() + plugin.getConfig().getLong("hud.vida-monstruos-segundos", 6) * 1000L);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent e) {
        if (!(e.getEntity() instanceof LivingEntity le) || !until.containsKey(le.getUniqueId())) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (le.isValid() && !le.isDead()) show(le);
        });
    }

    /** Antes de que nadie lea el nombre del muerto (mensajes, premios), le devuelve su nombre. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(EntityDeathEvent e) {
        until.remove(e.getEntity().getUniqueId());
        restore(e.getEntity());
    }

    private void show(LivingEntity le) {
        PersistentDataContainer pdc = le.getPersistentDataContainer();
        if (!pdc.has(BASE)) {
            Component cur = le.customName();
            pdc.set(HAD, PersistentDataType.BYTE, (byte) (cur != null ? 1 : 0));
            pdc.set(SHOWN, PersistentDataType.BYTE, (byte) (le.isCustomNameVisible() ? 1 : 0));
            pdc.set(BASE, PersistentDataType.STRING, cur == null ? "" : GsonComponentSerializer.gson().serialize(cur));
        }
        String raw = pdc.get(BASE, PersistentDataType.STRING);
        Component base = raw == null || raw.isEmpty() ? Component.translatable(le.getType().translationKey()) : GsonComponentSerializer.gson().deserialize(raw);
        var maxAttr = le.getAttribute(Attribute.MAX_HEALTH);
        double max = maxAttr == null ? le.getHealth() : maxAttr.getValue();
        int hp = (int) Math.ceil(le.getHealth() + le.getAbsorptionAmount());
        int mx = (int) Math.ceil(max);
        double f = mx <= 0 ? 1 : (double) hp / mx;
        NamedTextColor c = f > 0.6 ? NamedTextColor.GREEN : f > 0.3 ? NamedTextColor.YELLOW : NamedTextColor.RED;
        le.customName(base.append(Component.text("  ❤ ", NamedTextColor.RED)).append(Component.text(hp, c)).append(Component.text("/" + mx, NamedTextColor.GRAY)));
        le.setCustomNameVisible(true);
    }

    private void restore(LivingEntity le) {
        PersistentDataContainer pdc = le.getPersistentDataContainer();
        if (!pdc.has(BASE)) return;
        String raw = pdc.get(BASE, PersistentDataType.STRING);
        Byte had = pdc.get(HAD, PersistentDataType.BYTE), shown = pdc.get(SHOWN, PersistentDataType.BYTE);
        le.customName(had != null && had == 1 && raw != null && !raw.isEmpty() ? GsonComponentSerializer.gson().deserialize(raw) : null);
        le.setCustomNameVisible(shown != null && shown == 1);
        pdc.remove(BASE);
        pdc.remove(HAD);
        pdc.remove(SHOWN);
    }

    /** Si el servidor se cerró de golpe, algunos quedaron con la vida en el nombre: se arreglan al cargar. */
    @EventHandler
    public void onLoad(org.bukkit.event.world.EntitiesLoadEvent e) {
        for (Entity en : e.getEntities())
            if (en instanceof LivingEntity le && le.getPersistentDataContainer().has(BASE) && !until.containsKey(le.getUniqueId())) restore(le);
    }

    /** Al apagar: devuelve los nombres para que no queden con la vida pegada. */
    public void restoreAll() {
        for (UUID u : until.keySet()) {
            Entity e = Bukkit.getEntity(u);
            if (e instanceof LivingEntity le) restore(le);
        }
        until.clear();
    }
}
