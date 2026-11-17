package pe.kydium.eclipserpg.structure;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.util.Txt;

import java.util.ArrayList;
import java.util.List;

/** /estructura pos1 | pos2 | guardar &lt;nombre&gt; | colocar &lt;id&gt; | lista | ir &lt;id&gt; | brujula &lt;id&gt; */
public final class StructureCommand implements TabExecutor {
    private final EclipseRPG plugin;

    public StructureCommand(EclipseRPG plugin) {
        this.plugin = plugin;
    }

    private void help(CommandSender s) {
        Txt.send(s, "<yellow>Estructuras propias:");
        Txt.send(s, " <white>1. <gray>Construye lo que quieras (en creativo).");
        Txt.send(s, " <white>2. <gray>Mira una esquina de abajo y escribe <white>/estructura pos1<gray>; mira la esquina contraria de arriba y <white>/estructura pos2");
        Txt.send(s, " <white>3. <white>/estructura guardar <nombre>");
        Txt.send(s, " <white>4. <gray>En el editor (Estructuras) eliges dónde aparece, enemigos, jefe y botín.");
        Txt.send(s, " <gray>Carteles especiales (1ª línea): <white>[enemigo] [jefe] [entrada] [npc]<gray> (en [npc], el id en la 2ª línea)");
        Txt.send(s, " <gray>Otros: <white>/estructura colocar <id> · lista · ir <id> · brujula <id>");
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        StructureManager sm = plugin.structures();
        if (a.length == 0) {
            help(s);
            return true;
        }
        String sub = a[0].toLowerCase();
        if (sub.equals("lista")) {
            Txt.send(s, "<yellow>Estructuras de la historia:");
            for (String id : sm.ids()) {
                StructureManager.Def d = sm.def(id);
                Txt.send(s, " <gray>• <white>" + id + " <gray>— <reset>" + d.name + " <gray>(" + d.appears + ", en el mundo: " + sm.count(id) + ")"
                        + (sm.plan(d.plan) == null ? " <red>falta el plano" : ""));
            }
            Txt.send(s, "<yellow>Planos guardados: <white>" + String.join(", ", sm.planNames()));
            return true;
        }
        if (!(s instanceof Player p)) {
            Txt.send(s, "<red>Este comando se usa dentro del juego.");
            return true;
        }
        switch (sub) {
            case "pos1", "pos2" -> {
                Block b = p.getTargetBlockExact(8);
                if (b == null) b = p.getLocation().getBlock();
                int which = sub.equals("pos1") ? 0 : 1;
                sm.setSelection(p, which, b);
                int[][] sel = sm.selection(p);
                String size = "";
                if (sel[0] != null && sel[1] != null)
                    size = " <gray>· tamaño " + (Math.abs(sel[0][0] - sel[1][0]) + 1) + "×" + (Math.abs(sel[0][1] - sel[1][1]) + 1) + "×" + (Math.abs(sel[0][2] - sel[1][2]) + 1);
                Txt.send(p, "<green>Esquina " + (which + 1) + ": <white>" + b.getX() + " " + b.getY() + " " + b.getZ() + size);
            }
            case "guardar" -> {
                int[][] sel = sm.selection(p);
                if (a.length < 2) Txt.send(p, "<red>Uso: /estructura guardar <nombre>");
                else if (sel == null || sel[0] == null || sel[1] == null) Txt.send(p, "<red>Primero marca las dos esquinas con /estructura pos1 y /estructura pos2");
                else Txt.send(p, sm.savePlan(a[1], p.getWorld(), sel[0], sel[1]));
            }
            case "colocar" -> {
                StructureManager.Def d = a.length < 2 ? null : sm.def(a[1]);
                if (d == null) {
                    Txt.send(p, "<red>No existe esa estructura. Mira /estructura lista");
                    return true;
                }
                Location l = p.getLocation();
                Txt.send(p, "<gray>Construyendo " + d.name + "<gray>…");
                sm.placeAt(d, p.getWorld(), l.getBlockX(), l.getBlockY() - 1, l.getBlockZ(), p,
                        inst -> Txt.send(p, inst == null ? "<red>No se pudo construir." : "<green>Listo: " + d.name));
            }
            case "ir", "brujula" -> {
                StructureManager.Inst i = a.length < 2 ? null : sm.nearest(StructureManager.slug(a[1]), p.getLocation());
                if (i == null) Txt.send(p, "<red>Todavía no hay ninguna de esas en este mundo.");
                else if (sub.equals("brujula")) sm.giveCompass(p, i);
                else {
                    int[] e = i.entrance != null ? i.entrance : new int[]{i.x, i.y + i.h + 2, i.z};
                    p.teleportAsync(new Location(p.getWorld(), e[0] + 0.5, e[1], e[2] + 0.5));
                }
            }
            default -> help(s);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) out.addAll(List.of("pos1", "pos2", "guardar", "colocar", "lista", "ir", "brujula"));
        else if (a.length == 2 && !a[0].equalsIgnoreCase("guardar")) out.addAll(plugin.structures().ids());
        String last = a[a.length - 1].toLowerCase();
        out.removeIf(x -> !x.startsWith(last));
        return out;
    }
}
