package pe.kydium.eclipserpg.items;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.util.Reg;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Conjuntos completos (objetos.yml &gt; conjuntos): con 2 y 4 piezas puestas se ganan efectos,
 * y con las 4 piezas + un arma o herramienta del mismo conjunto en la mano se pega más fuerte.
 */
public final class ArmorSets implements Listener {

    private final EclipseRPG plugin;

    public ArmorSets(EclipseRPG plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) apply(p);
        }, 40L, 40L);
    }

    /** Cuántas piezas de cada conjunto lleva puestas. */
    public Map<String, Integer> worn(Player p) {
        Map<String, Integer> out = new HashMap<>();
        for (ItemStack it : p.getInventory().getArmorContents()) {
            String set = setOf(it);
            if (set != null) out.merge(set, 1, Integer::sum);
        }
        return out;
    }

    public String setOf(ItemStack it) {
        String id = plugin.items().idOf(it);
        if (id == null) return null;
        ItemManager.CustomItem ci = plugin.items().def(id);
        return ci == null ? null : ci.set;
    }

    private void apply(Player p) {
        Map<String, Integer> w = worn(p);
        for (Map.Entry<String, Integer> e : w.entrySet()) {
            ItemManager.SetDef sd = plugin.items().set(e.getKey());
            if (sd == null) continue;
            for (Map.Entry<Integer, List<String[]>> b : sd.bonus().entrySet()) {
                if (e.getValue() < b.getKey()) continue;
                for (String[] ef : b.getValue()) {
                    PotionEffectType t = Reg.effect(ef[0]);
                    if (t == null) continue;
                    int amp = Math.max(0, Integer.parseInt(ef[1]) - 1);
                    PotionEffect cur = p.getPotionEffect(t);
                    if (cur != null && (cur.getAmplifier() > amp || cur.getDuration() > 120)) continue;
                    // visión nocturna con menos de 10 s parpadea: se le da más tiempo
                    int dur = t.equals(PotionEffectType.NIGHT_VISION) ? 300 : 90;
                    p.addPotionEffect(new PotionEffect(t, dur, amp, true, false, true));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        String set = setOf(p.getInventory().getItemInMainHand());
        if (set == null) return;
        ItemManager.SetDef sd = plugin.items().set(set);
        if (sd == null || sd.weapon() <= 0) return;
        if (worn(p).getOrDefault(set, 0) < 4) return;
        e.setDamage(e.getDamage() * (1 + sd.weapon() / 100.0));
    }
}
