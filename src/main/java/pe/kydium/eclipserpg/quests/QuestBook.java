package pe.kydium.eclipserpg.quests;

import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.entity.Player;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.data.PlayerData;
import pe.kydium.eclipserpg.util.Txt;

import java.util.ArrayList;
import java.util.List;

/**
 * Diario del Guardián: un cuaderno que se abre con /misiones (o desde el menú del Guardián).
 * Primera hoja: la misión actual y sus objetivos. Después, las misiones ya hechas, en orden y
 * agrupadas por acto. Las páginas se van llenando a medida que avanzas; no revela las que faltan.
 */
public final class QuestBook {

    private static final int LINES = 13;   // líneas útiles por página
    private static final int WIDTH = 19;   // caracteres aprox. por línea

    private QuestBook() {}

    private static int lines(String plain) {
        return Math.max(1, (int) Math.ceil(plain.length() / (double) WIDTH));
    }

    private static String strip(String mm) {
        return mm.replaceAll("<[^>]+>", "");
    }

    /** Acumula renglones y corta páginas. */
    private static final class Pager {
        final List<Component> pages = new ArrayList<>();
        Component page = Component.empty();
        int used;

        void add(Component c, int h) {
            if (used + h > LINES && used > 0) newPage();
            page = page.append(c).append(Component.newline());
            used += h;
        }

        void add(String mm) {
            add(Txt.mm(mm), lines(strip(mm)));
        }

        void newPage() {
            pages.add(page);
            page = Component.empty();
            used = 0;
        }

        List<Component> done() {
            if (used > 0) pages.add(page);
            return pages;
        }
    }

    public static void open(EclipseRPG plugin, Player p) {
        QuestManager qm = plugin.quests();
        PlayerData d = plugin.data().get(p);
        Quest cur = qm.current(p);
        Pager pg = new Pager();

        // ---------- hoja 1: misión actual ----------
        pg.add("<dark_red><bold>   Diario del");
        pg.add("<dark_red><bold>    Guardián");
        pg.add("<dark_gray>" + p.getName() + " · Nivel " + d.level);
        pg.add("");
        if (cur == null) {
            pg.add("<dark_green><bold>¡La historia terminó!");
            pg.add("<black>El sol volvió a brillar gracias a ti.");
        } else {
            pg.add("<dark_purple>" + qm.actName(cur.act));
            pg.add("<black><bold>" + (d.questAccepted ? cur.name : "❗ Nueva misión"));
            pg.add("");
            if (!d.questAccepted) {
                pg.add("<dark_gray>Habla con " + strip(plugin.npcs().name(cur.npc)) + " para empezarla.");
                if (cur.minLevel > d.level) pg.add("<dark_red>Necesitas nivel " + cur.minLevel + ".");
            } else if (cur.objectives.isEmpty()) {
                pg.add("<dark_gray>Habla con " + strip(plugin.npcs().name(cur.npc)) + ".");
            } else {
                for (int i = 0; i < cur.objectives.size(); i++) {
                    Quest.Objective o = cur.objectives.get(i);
                    int have = Math.min(o.amount, qm.progressOf(p, cur, i));
                    boolean ok = have >= o.amount;
                    String count = o.amount > 1 ? " <dark_gray>" + have + "/" + o.amount : "";
                    pg.add((ok ? "<dark_green>✔ " : "<black>• ") + (ok ? "<dark_green><st>" : "<black>") + o.text
                            + (ok ? "</st>" : "") + count);
                }
                if (qm.isComplete(p, cur)) {
                    pg.add("");
                    pg.add("<dark_green>Vuelve con " + strip(plugin.npcs().name(cur.npc)) + ".");
                }
                if (cur.noDeath) pg.add("<dark_red>Si mueres, se reinicia.");
            }
        }
        pg.newPage();

        // ---------- misiones hechas, por acto ----------
        int doneCount = Math.min(d.questIndex, qm.total());
        int lastAct = -1;
        if (doneCount == 0) {
            pg.add("<dark_gray><italic>Aún no hay nada escrito…");
            pg.add("");
            pg.add("<dark_gray>Las misiones que completes aparecerán aquí.");
        }
        for (int n = 1; n <= doneCount; n++) {
            Quest q = qm.byNumber(n);
            if (q == null) continue;
            if (q.act != lastAct) {
                if (lastAct != -1) pg.add("");
                pg.add(Txt.mm("<dark_purple><bold>" + qm.actName(q.act)), lines(qm.actName(q.act)) );
                lastAct = q.act;
            }
            String npc = strip(plugin.npcs().name(q.npc));
            Component line = Txt.mm((q.boss ? "<dark_red>☠ " : "<dark_green>✔ ") + "<black>" + q.name)
                    .hoverEvent(HoverEvent.showText(Txt.mm("<gray>Encargo de <white>" + npc
                            + (q.boss ? "\n<red>Jefe derrotado" : ""))));
            pg.add(line, lines("✔ " + q.name));
        }
        if (cur != null && doneCount > 0) {
            if (cur.act != lastAct) {
                pg.add("");
                pg.add(Txt.mm("<dark_purple><bold>" + qm.actName(cur.act)), lines(qm.actName(cur.act)));
            }
            String shown = d.questAccepted ? cur.name : "❗ Nueva misión";
            Component line = Txt.mm("<gold>➤ <dark_blue><underlined>" + shown)
                    .hoverEvent(HoverEvent.showText(Txt.mm("<yellow>Misión actual\n<gray>Clic para ver los objetivos")))
                    .clickEvent(ClickEvent.changePage(1));
            pg.add(line, lines("➤ " + shown));
        }

        Book book = Book.book(Txt.mm("Diario del Guardián"), Txt.mm(p.getName()), pg.done());
        p.openBook(book);
        p.playSound(p.getLocation(), org.bukkit.Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
    }
}
