package pe.kydium.eclipserpg.menu;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.data.PlayerData;
import pe.kydium.eclipserpg.quests.Quest;
import pe.kydium.eclipserpg.util.Icons;
import pe.kydium.eclipserpg.util.Keys;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Panel de la misión a la izquierda de la pantalla, en un cuadro semitransparente que no desaparece.
 * Usa barras de jefe blancas (el paquete de recursos las vuelve invisibles): cada línea es el título de una barra,
 * corrido hacia la izquierda con espacios de la letra eclipse:hud.
 */
public final class QuestPanel {

    public static final int LINES = 6;
    private static final Key HUD = Key.key("eclipse", "hud");
    private static final Key ICONS = Key.key("eclipse", "iconos");
    public static final int STEP = 6;                    // cada letra eclipse:panel_N baja N×6 píxeles
    public static final int FONTS = 100;                 // eclipse:panel_0 … panel_99
    public static final int LINE = 12;                   // alto de cada línea del cuadro
    private static final char BOX_CHAR = '\ue010';
    private static final String EXTRA = "áéíóúñü¿¡·";
    private static final int PAD = 4, ICON = 11;         // margen y lo que avanza el ícono + 1 espacio
    private static final int MIN_SEGMENTS = 7, MAX_SEGMENTS = 14;
    private static final int TEXT_MAX = MAX_SEGMENTS * 16 - PAD - ICON - PAD;

    private record Line(String icon, TextColor color, String text) {}

    private final EclipseRPG plugin;
    private final Map<UUID, List<BossBar>> bars = new HashMap<>();
    private final Map<UUID, List<Line>> shown = new HashMap<>();

    public QuestPanel(EclipseRPG plugin) {
        this.plugin = plugin;
    }

    /** ¿Este jugador ve el panel? (el panel reemplaza los avisos de avance de abajo) */
    public boolean on(Player p) {
        if (!plugin.getConfig().getBoolean("hud.panel.activo", true)) return false;
        if (plugin.resourcePack() == null || !plugin.resourcePack().hasPack(p)) return false;
        Byte off = p.getPersistentDataContainer().get(Keys.k("panel_off"), PersistentDataType.BYTE);
        return off == null || off == 0;
    }

    public void setOn(Player p, boolean on) {
        p.getPersistentDataContainer().set(Keys.k("panel_off"), PersistentDataType.BYTE, (byte) (on ? 0 : 1));
        if (!on) hide(p);
        else update(p);
    }

    /** Distancia del borde izquierdo del cuadro al centro de la pantalla (negativo = a la izquierda). */
    public int offset(Player p) {
        Integer v = p.getPersistentDataContainer().get(Keys.k("panel_px"), PersistentDataType.INTEGER);
        return v != null ? v : defX(scale(p));
    }

    /** Altura del cuadro: píxeles desde arriba de la pantalla hasta la primera línea. */
    public int top(Player p) {
        Integer v = p.getPersistentDataContainer().get(Keys.k("panel_py"), PersistentDataType.INTEGER);
        return v != null ? v : defY(scale(p));
    }

    /** Tamaño de interfaz de Minecraft (Opciones > Gráficos) con el que se calcula la posición. */
    public int scale(Player p) {
        Integer v = p.getPersistentDataContainer().get(Keys.k("panel_escala"), PersistentDataType.INTEGER);
        return v != null ? v : Math.max(1, Math.min(6, plugin.getConfig().getInt("hud.panel.escala", 2)));
    }

    /** Pega el cuadro al borde izquierdo y a media altura para ese tamaño de interfaz (pantalla de 1920×1080). */
    public void setScale(Player p, int s) {
        s = Math.max(1, Math.min(6, s));
        var pdc = p.getPersistentDataContainer();
        pdc.set(Keys.k("panel_escala"), PersistentDataType.INTEGER, s);
        pdc.remove(Keys.k("panel_px"));
        pdc.remove(Keys.k("panel_py"));
        shown.remove(p.getUniqueId());
        update(p);
    }

    private static int defX(int s) {
        return 6 - 960 / s;
    }

    private static int defY(int s) {
        return 525 / s - 24;
    }

    public void setOffset(Player p, int x) {
        p.getPersistentDataContainer().set(Keys.k("panel_px"), PersistentDataType.INTEGER, Math.max(-1000, Math.min(400, x)));
        shown.remove(p.getUniqueId());
        update(p);
    }

    public void setTop(Player p, int y) {
        p.getPersistentDataContainer().set(Keys.k("panel_py"), PersistentDataType.INTEGER, Math.max(3, Math.min(maxTop(), y)));
        shown.remove(p.getUniqueId());
        update(p);
    }

    private static int maxTop() {
        return 3 + STEP * (FONTS - 1 - (LINE / STEP) * (LINES - 1));
    }

    public void update(Player p) {
        if (!on(p)) {
            hide(p);
            return;
        }
        List<Line> lines = lines(p);
        if (lines.isEmpty()) {
            hide(p);
            return;
        }
        if (lines.equals(shown.get(p.getUniqueId())) && bars.containsKey(p.getUniqueId())) return;
        shown.put(p.getUniqueId(), lines);
        List<BossBar> list = bars.get(p.getUniqueId());
        if (list == null) {
            list = new ArrayList<>();
            BossBar b = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
            list.add(b);
            p.showBossBar(b);
            bars.put(p.getUniqueId(), list);
        }
        list.get(0).name(render(lines, offset(p), top(p)));
    }

    public void hide(Player p) {
        List<BossBar> list = bars.remove(p.getUniqueId());
        shown.remove(p.getUniqueId());
        if (list != null) for (BossBar b : list) p.hideBossBar(b);
    }

    public void forget(UUID id) {
        bars.remove(id);
        shown.remove(id);
    }

    // ---------------------------------------------------------------- contenido

    private List<Line> lines(Player p) {
        List<Line> out = new ArrayList<>();
        Quest q = plugin.quests().current(p);
        if (q == null) return out;
        PlayerData d = plugin.data().get(p);
        String title = q.name + (q.stages > 1 ? " · " + q.stage + "/" + q.stages : "");
        out.add(new Line("pergamino", NamedTextColor.GOLD, title));
        if (!d.questAccepted) {
            out.add(new Line("alerta", NamedTextColor.YELLOW, "Habla con " + plain(plugin.npcs().name(q.npc))));
            String where = plain(plugin.quests().whereIs(q.npc)).trim();
            if (!where.isEmpty()) out.add(new Line("ciudad", NamedTextColor.GRAY, where.replaceAll("[()]", "")));
        } else {
            boolean all = true;
            for (int i = 0; i < q.objectives.size() && out.size() < LINES - 1; i++) {
                Quest.Objective o = q.objectives.get(i);
                int have = Math.min(o.amount, plugin.quests().progressOf(p, q, i));
                boolean done = have >= o.amount;
                all &= done;
                String txt = plain(o.text) + (o.amount > 1 ? " " + have + "/" + o.amount : "");
                out.add(new Line(done ? "check" : "punto", done ? NamedTextColor.GRAY : NamedTextColor.WHITE, txt));
            }
            if (all && !q.objectives.isEmpty() && !q.auto)
                out.add(new Line("alerta", NamedTextColor.GREEN, "Vuelve con " + plain(plugin.npcs().name(q.npc))));
        }
        String streak = plugin.mobs().streakText(p);
        if (streak != null) {
            if (out.size() >= LINES) out.remove(out.size() - 1);
            out.add(new Line("llama", NamedTextColor.LIGHT_PURPLE, streak));
        }
        while (out.size() > LINES) out.remove(out.size() - 1);
        return out;
    }

    private static String plain(String mini) {
        if (mini == null) return "";
        return mini.replaceAll("<[^>]*>", "").replace('\n', ' ').trim();
    }

    // ---------------------------------------------------------------- dibujo

    /**
     * Todo el cuadro va en el título de UNA barra de jefe. Cada línea usa la letra eclipse:panel_N, que es la normal
     * pero bajada N×6 píxeles (ascent negativo), así el cuadro queda a media pantalla y con las líneas parejas.
     */
    private Component render(List<Line> lines, int off, int top) {
        int a = Math.max(0, Math.min(FONTS - 1 - (LINE / STEP) * (LINES - 1), Math.round((top - 3) / (float) STEP)));
        List<String> texts = new ArrayList<>();
        int widest = 0;
        for (Line l : lines) {
            String t = fit(clean(l.text()), TEXT_MAX);
            texts.add(t);
            widest = Math.max(widest, width(t));
        }
        int segs = Math.max(MIN_SEGMENTS, Math.min(MAX_SEGMENTS, (PAD + ICON + widest + PAD + 15) / 16));
        int boxW = segs * 16 + 1;
        StringBuilder box = new StringBuilder();
        for (int i = 0; i < segs; i++) {
            if (i > 0) box.append(Hud.space(-1));
            box.append(BOX_CHAR);
        }
        var out = Component.text();
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            Key font = Key.key("eclipse", "panel_" + (a + i * (LINE / STEP)));
            String t = texts.get(i);
            int tw = width(t);
            out.append(Component.text(box.toString()).font(font).color(NamedTextColor.WHITE).shadowColor(ShadowColor.none()))
                    .append(Component.text(Hud.space(-boxW + PAD)).font(HUD))
                    .append(Component.text(String.valueOf(Icons.glyph(l.icon()))).font(font).color(NamedTextColor.WHITE).shadowColor(ShadowColor.none()))
                    .append(Component.text(Hud.space(ICON - iconAdvance(l.icon()))).font(HUD))
                    .append(Component.text(t).font(font).color(l.color()))
                    .append(Component.text(Hud.space(-(PAD + ICON + tw))).font(HUD));
        }
        // lo que avanza todo es -2×off: Minecraft centra el título, así el borde izquierdo queda en centro + off
        out.append(Component.text(Hud.space(-2 * off)).font(HUD));
        return out.build();
    }

    private static final Map<String, Integer> ICON_ADV = new HashMap<>();

    /** Lo que avanza un ícono en Minecraft: su ancho real (columna más a la derecha) escalado a 9 px + 1. */
    static int iconAdvance(String name) {
        return ICON_ADV.computeIfAbsent(name, n -> {
            try (var in = QuestPanel.class.getResourceAsStream("/motor/hud/iconos/" + n + ".png")) {
                if (in == null) return 10;
                java.awt.image.BufferedImage im = javax.imageio.ImageIO.read(in);
                int right = 0;
                for (int x = 0; x < im.getWidth(); x++)
                    for (int y = 0; y < im.getHeight(); y++)
                        if ((im.getRGB(x, y) >>> 24) != 0) right = x + 1;
                return (int) (0.5 + right * (9.0 / im.getHeight())) + 1;
            } catch (Exception e) {
                return 10;
            }
        });
    }

    /** Solo letras que tiene la letra del panel: las mayúsculas con tilde pierden la tilde. */
    static String clean(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if ((c >= 32 && c < 127) || EXTRA.indexOf(c) >= 0) b.append(c);
            else {
                String n = java.text.Normalizer.normalize(String.valueOf(c), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
                if (!n.isEmpty() && n.charAt(0) >= 32 && n.charAt(0) < 127) b.append(n.charAt(0));
            }
        }
        return b.toString();
    }

    /** Letra eclipse:panel_N para el paquete de recursos: la normal + tildes + cuadro + íconos, bajada `shift` píxeles. */
    public static String fontJson(int shift) {
        StringBuilder rows = new StringBuilder();
        String[] ascii = {"", "", " !\"#$%&'()*+,-./", "0123456789:;<=>?", "@ABCDEFGHIJKLMNO", "PQRSTUVWXYZ[\\]^_", "`abcdefghijklmno", "pqrstuvwxyz{|}~"};
        for (int r = 0; r < 16; r++) {
            String row = r < ascii.length ? ascii[r] : "";
            if (r == 2) row = "\u0000" + row.substring(1);
            StringBuilder q = new StringBuilder();
            for (int c = 0; c < 16; c++) q.append(c < row.length() ? row.charAt(c) : '\u0000');
            if (r > 0) rows.append(", ");
            rows.append('"').append(esc(q.toString())).append('"');
        }
        return "{\"providers\": ["
                + "{\"type\": \"space\", \"advances\": {\" \": 4" + spaces() + "}}, "
                + "{\"type\": \"bitmap\", \"file\": \"minecraft:font/ascii.png\", \"ascent\": " + (7 - shift) + ", \"height\": 8, \"chars\": [" + rows + "]}, "
                + "{\"type\": \"bitmap\", \"file\": \"eclipse:hud/panel_letras.png\", \"ascent\": " + (7 - shift) + ", \"height\": 8, \"chars\": [\"" + esc(EXTRA) + "\"]}, "
                + "{\"type\": \"bitmap\", \"file\": \"eclipse:hud/caja_panel.png\", \"ascent\": " + (9 - shift) + ", \"height\": " + LINE + ", \"chars\": [\"" + esc(String.valueOf(BOX_CHAR)) + "\"]}, "
                + Icons.providers(8 - shift) + "]}";
    }

    /** Los mismos espacios positivos y negativos de eclipse:hud (el cuadro los usa para juntar sus pedazos). */
    private static String spaces() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            b.append(String.format(", \"\\u%04x\": %d", 0xF801 + i, 1 << i));
            b.append(String.format(", \"\\u%04x\": %d", 0xF811 + i, -(1 << i)));
        }
        return b.toString();
    }

    private static String esc(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c < 32 || c > 126) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.toString();
    }

    private static String fit(String s, int max) {
        if (width(s) <= max) return s;
        String cut = s;
        while (!cut.isEmpty() && width(cut + "..") > max) cut = cut.substring(0, cut.length() - 1);
        return cut.stripTrailing() + "..";
    }

    /** Ancho en píxeles con la letra normal de Minecraft (incluye el píxel de separación). */
    static int width(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) w += charWidth(s.charAt(i));
        return w;
    }

    private static int charWidth(char c) {
        return switch (c) {
            case ' ' -> 4;
            case '!', ',', '.', ':', ';', '|', 'i', '\'', '¡', '·' -> 2;
            case 'l', '`', 'í', 'ì', 'î', 'ï' -> 3;
            case 'I', 't', '[', ']', 'Í' -> 4;
            case '"', '(', ')', '*', '<', '>', 'f', 'k', '{', '}' -> 5;
            case '@', '~' -> 7;
            default -> 6;
        };
    }
}
