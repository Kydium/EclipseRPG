package pe.kydium.eclipserpg.rpg;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.data.PlayerData;
import pe.kydium.eclipserpg.menu.Menu;
import pe.kydium.eclipserpg.util.Txt;

import java.util.ArrayList;
import java.util.List;

/** Menú /nivel: ver el progreso y repartir puntos. */
public final class StatsMenu extends Menu {
    private static final int[] STAT_SLOTS = {10, 11, 12, 13, 14, 15, 16, 17};
    private static final int INFO_SLOT = 4;
    private static final int RESET_SLOT = 22;
    private final EclipseRPG plugin;

    public StatsMenu(EclipseRPG plugin, Player player) {
        super(player, 3, "<dark_gray>Nivel y atributos");
        this.plugin = plugin;
    }

    @Override
    public void render() {
        inventory.clear();
        LevelManager lm = plugin.levels();
        PlayerData d = plugin.data().get(player);

        List<String> info = new ArrayList<>();
        if (d.level < lm.maxLevel()) {
            long need = lm.xpNeeded(d.level);
            info.add("<gray>XP: <aqua>" + Txt.num(d.xp) + "</aqua> / " + Txt.num(need));
            info.add(bar(d.xp, need));
        } else {
            info.add("<gold>¡Nivel máximo!");
        }
        info.add("");
        info.add("<gray>Puntos libres: <yellow>" + d.points);
        info.add("<gray>Soles: <yellow>" + Txt.num(d.soles) + " ☀");
        info.add("<gray>Tier de enemigos: <light_purple>" + lm.tierFor(d.level));
        inventory.setItem(INFO_SLOT, icon(Material.EXPERIENCE_BOTTLE, "<gold><bold>Nivel " + d.level, info));

        Stat[] stats = Stat.values();
        int spent = 0;
        for (Stat s : stats) spent += d.stat(s.id);
        for (int i = 0; i < stats.length && i < STAT_SLOTS.length; i++) {
            Stat s = stats[i];
            int val = d.stat(s.id);
            int max = lm.maxPoints(s);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + lm.statDescription(s));
            lore.add("");
            lore.add("<gray>Puntos: <white>" + val + "</white> / " + max + " " + pips(val, max));
            if (val > 0) {
                lore.add("<gray>Ahora:");
                for (String l : lm.statEffects(s, val)) lore.add(" <green>" + l);
            }
            if (val < max) {
                lore.add("<gray>Con el siguiente punto:");
                for (String l : lm.statEffects(s, val + 1)) lore.add(" <dark_green>" + l);
            }
            lore.add("");
            if (val >= max) lore.add("<red>Al máximo");
            else if (d.points <= 0) lore.add("<red>No tienes puntos libres");
            else lore.add("<yellow>Clic: +1 · Shift+clic: +5");
            inventory.setItem(STAT_SLOTS[i], icon(s.icon, s.display + " <dark_gray>[" + val + "]", lore));
        }
        info.add("<gray>Puntos repartidos: <white>" + spent);
        inventory.setItem(INFO_SLOT, icon(Material.EXPERIENCE_BOTTLE, "<gold><bold>Nivel " + d.level, info));

        long cost = plugin.getConfig().getLong("stats.costo-reinicio", 500);
        inventory.setItem(RESET_SLOT, icon(Material.GRINDSTONE, "<red>Reiniciar puntos",
                List.of("<gray>Te devuelve todos los puntos.", "<gray>Cuesta <yellow>" + Txt.num(cost) + " ☀", "", "<yellow>Clic para reiniciar")));
        fillEmpty();
    }

    private static String pips(int val, int max) {
        int total = 10, filled = max <= 0 ? 0 : (int) Math.round(val * 10.0 / max);
        return "<gold>" + "■".repeat(filled) + "<dark_gray>" + "■".repeat(total - filled);
    }

    private String bar(long xp, long need) {
        int total = 20;
        int filled = (int) Math.min(total, Math.round((double) xp / need * total));
        return "<aqua>" + "▌".repeat(filled) + "<dark_gray>" + "▌".repeat(total - filled);
    }

    @Override
    public void onClick(InventoryClickEvent e, int slot) {
        PlayerData d = plugin.data().get(player);
        LevelManager lm = plugin.levels();
        if (slot == RESET_SLOT) {
            int spent = 0;
            for (int v : d.stats.values()) spent += v;
            if (spent == 0) {
                Txt.send(player, "<gray>No tienes puntos gastados.");
                return;
            }
            long cost = plugin.getConfig().getLong("stats.costo-reinicio", 500);
            if (!lm.takeSoles(player, cost)) {
                Txt.send(player, "<red>Necesitas " + Txt.num(cost) + " ☀ para reiniciar.");
                return;
            }
            d.points += spent;
            d.stats.clear();
            d.dirty = true;
            lm.applyStats(player);
            Txt.sound(player, "block.grindstone.use", 1f, 1f);
            render();
            return;
        }
        Stat[] stats = Stat.values();
        for (int i = 0; i < STAT_SLOTS.length && i < stats.length; i++) {
            if (STAT_SLOTS[i] != slot) continue;
            Stat s = stats[i];
            if (d.points <= 0 || d.stat(s.id) >= lm.maxPoints(s)) {
                Txt.sound(player, "entity.villager.no", 1f, 1f);
                return;
            }
            int add = Math.min(e.isShiftClick() ? 5 : 1, Math.min(d.points, lm.maxPoints(s) - d.stat(s.id)));
            d.points -= add;
            d.stats.put(s.id, d.stat(s.id) + add);
            d.dirty = true;
            lm.applyStats(player);
            Txt.sound(player, "entity.experience_orb.pickup", 1f, 1.4f);
            render();
            return;
        }
    }
}
