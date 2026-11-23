package pe.kydium.eclipserpg.npc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import pe.kydium.eclipserpg.EclipseRPG;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Paquetes de texturas extra (por ejemplo "New Default+") que el dueño del servidor pone en
 * {@code plugins/AventuraMaker/paquetes/}. Se mandan a los jugadores tal cual, ANTES del paquete del plugin
 * (así las skins de NPCs, el HUD y los Corrompidos siguen ganando).
 * <p>
 * Además se lee qué modelos traen por nombre ("Hawkbrand", "Champion's Armor Chestplate"…) y qué aspectos de
 * armadura ({@code equipment}), para que los objetos de objetos.yml los usen con {@code textura:} y {@code aspecto:}
 * sin tener que ponerles ese nombre en inglés.
 */
public final class ExtraPacks {

    public record Pack(File file, String sha1, long size) {
        public String path() {
            return "/extra-" + sha1.substring(0, 12) + ".zip";
        }
    }

    /** Formato de paquete de recursos del cliente (Minecraft 26.x). */
    private static final int FORMAT = 82;

    private final EclipseRPG plugin;
    private final List<Pack> packs = new ArrayList<>();
    /** nombre en minúsculas → objeto base (netherite_sword) → modelo (JSON del "model" del caso). */
    private final Map<String, Map<String, JsonElement>> byName = new HashMap<>();
    /** id de aspecto de armadura (icons/minecraft_dungeons/archers_armor_1) */
    private final Set<String> equipment = new LinkedHashSet<>();

    public ExtraPacks(EclipseRPG plugin) {
        this.plugin = plugin;
    }

    public File folder() {
        return new File(plugin.getDataFolder(), "paquetes");
    }

    public List<Pack> packs() {
        return packs;
    }

    public int models() {
        return byName.size();
    }

    public int looks() {
        return equipment.size();
    }

    public void scan() {
        packs.clear();
        byName.clear();
        equipment.clear();
        File dir = folder();
        if (!dir.isDirectory()) {
            dir.mkdirs();
            try {
                Files.writeString(new File(dir, "LEEME.txt").toPath(), """
                        Pon aquí paquetes de texturas (.zip) para que todos los jugadores los reciban al entrar.
                        Se cargan por orden alfabético y el paquete del plugin va encima (NPCs, HUD, Corrompidos).
                        Si el paquete trae modelos con nombre (como New Default+), en objetos.yml puedes usar:
                          textura: "Hawkbrand"          (el nombre del modelo en el paquete)
                          aspecto: "champions_armor"    (cómo se ve la armadura puesta)
                        Usa /eclipse texturas para ver cuántos modelos y aspectos se encontraron.
                        Consejo: quita la carpeta assets/minecraft/sounds del .zip si pesa mucho.
                        """, StandardCharsets.UTF_8);
            } catch (IOException ignored) {
                // sin instrucciones
            }
            return;
        }
        if (!plugin.getConfig().getBoolean("paquete-recursos.extra", true)) return;
        File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".zip"));
        if (files == null) return;
        java.util.Arrays.sort(files);
        for (File f : files) {
            try {
                packs.add(new Pack(f, sha1(f), f.length()));
                index(f);
            } catch (Exception e) {
                plugin.getLogger().warning("Paquete extra " + f.getName() + ": " + e.getMessage());
            }
        }
        if (!packs.isEmpty())
            plugin.getLogger().info("Paquetes de texturas extra: " + packs.size() + " (" + byName.size() + " modelos con nombre, " + equipment.size() + " aspectos de armadura)");
    }

    private static String sha1(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        try (InputStream in = Files.newInputStream(f.toPath())) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        return HexFormat.of().formatHex(md.digest());
    }

    // ---------------------------------------------------------------- índice de modelos

    /** Lee items/*.json y equipment/ del paquete base y de sus capas para el formato actual (las de después ganan). */
    private void index(File f) throws IOException {
        try (ZipFile z = new ZipFile(f)) {
            List<String> roots = new ArrayList<>();
            roots.add("");
            ZipEntry mc = z.getEntry("pack.mcmeta");
            if (mc != null) {
                try (InputStream in = z.getInputStream(mc)) {
                    String txt = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("﻿", "");
                    JsonObject o = JsonParser.parseString(txt).getAsJsonObject();
                    if (o.has("overlays") && o.getAsJsonObject("overlays").has("entries"))
                        for (JsonElement en : o.getAsJsonObject("overlays").getAsJsonArray("entries")) {
                            JsonObject eo = en.getAsJsonObject();
                            if (applies(eo) && eo.has("directory")) roots.add(eo.get("directory").getAsString() + "/");
                        }
                }
            }
            Map<String, ZipEntry> items = new LinkedHashMap<>();
            for (String root : roots) {
                String ip = root + "assets/minecraft/items/", ep = root + "assets/minecraft/equipment/";
                var en = z.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    String n = e.getName();
                    if (e.isDirectory() || !n.endsWith(".json")) continue;
                    if (n.startsWith(ip) && n.indexOf('/', ip.length()) < 0) items.put(n.substring(ip.length(), n.length() - 5), e);
                    else if (n.startsWith(ep)) equipment.add(n.substring(ep.length(), n.length() - 5));
                }
            }
            for (Map.Entry<String, ZipEntry> e : items.entrySet()) {
                try (InputStream in = z.getInputStream(e.getValue()); var r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    JsonElement root = JsonParser.parseReader(r);
                    walk(root, e.getKey());
                } catch (Exception ignored) {
                    // json roto: se salta
                }
            }
        }
    }

    private static boolean applies(JsonObject eo) {
        int min = 0, max = Integer.MAX_VALUE;
        if (eo.has("formats")) {
            JsonElement fm = eo.get("formats");
            if (fm.isJsonArray()) {
                min = fm.getAsJsonArray().get(0).getAsInt();
                max = fm.getAsJsonArray().get(1).getAsInt();
            } else if (fm.isJsonPrimitive()) min = max = fm.getAsInt();
            else if (fm.isJsonObject()) {
                min = fm.getAsJsonObject().get("min_inclusive").getAsInt();
                max = fm.getAsJsonObject().get("max_inclusive").getAsInt();
            }
        }
        if (eo.has("min_format")) min = first(eo.get("min_format"));
        if (eo.has("max_format")) max = first(eo.get("max_format"));
        return FORMAT >= min && FORMAT <= max;
    }

    private static int first(JsonElement e) {
        return e.isJsonArray() ? e.getAsJsonArray().get(0).getAsInt() : e.getAsInt();
    }

    private void walk(JsonElement el, String base) {
        if (el == null) return;
        if (el.isJsonArray()) {
            for (JsonElement x : el.getAsJsonArray()) walk(x, base);
            return;
        }
        if (!el.isJsonObject()) return;
        JsonObject o = el.getAsJsonObject();
        if ("minecraft:select".equals(str(o, "type")) && "minecraft:component".equals(str(o, "property"))
                && "minecraft:custom_name".equals(str(o, "component")) && o.has("cases")) {
            for (JsonElement c : o.getAsJsonArray("cases")) {
                if (!c.isJsonObject() || !c.getAsJsonObject().has("model")) continue;
                JsonElement when = c.getAsJsonObject().get("when");
                JsonElement model = c.getAsJsonObject().get("model");
                List<String> names = new ArrayList<>();
                if (when.isJsonPrimitive()) names.add(when.getAsString());
                else if (when.isJsonArray()) for (JsonElement w : (JsonArray) when) if (w.isJsonPrimitive()) names.add(w.getAsString());
                for (String n : names) byName.computeIfAbsent(key(n), k -> new LinkedHashMap<>()).put(base, model);
            }
        }
        for (Map.Entry<String, JsonElement> en : o.entrySet()) walk(en.getValue(), base);
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : null;
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT).replace("’", "'").trim();
    }

    // ---------------------------------------------------------------- consultas

    /** Modelo para un objeto con {@code textura: <nombre>} (prefiere el del mismo tipo de objeto). */
    public JsonElement model(String name, String baseItem) {
        if (name == null) return null;
        Map<String, JsonElement> m = byName.get(key(name));
        if (m == null || m.isEmpty()) return null;
        JsonElement same = m.get(baseItem);
        return same != null ? same : m.values().iterator().next();
    }

    /** Nombres de modelos que contienen el texto (para /eclipse texturas buscar). */
    public List<String> search(String text, int max) {
        List<String> out = new ArrayList<>();
        String t = key(text);
        for (Map.Entry<String, Map<String, JsonElement>> e : byName.entrySet()) {
            if (!e.getKey().contains(t)) continue;
            out.add(e.getKey() + " §7(" + String.join(", ", e.getValue().keySet()) + ")");
            if (out.size() >= max) break;
        }
        return out;
    }

    /**
     * Aspecto de armadura: "champions_armor" → icons/minecraft_dungeons/champions_armor_1 (o _2 para grebas),
     * o el id exacto si existe.
     */
    public String look(String name, boolean leggings) {
        if (name == null || name.isBlank()) return null;
        String n = name.toLowerCase(Locale.ROOT).trim().replace(' ', '_').replace("'", "");
        if (n.startsWith("minecraft:")) n = n.substring(10);
        if (equipment.contains(n)) return n;
        String suffix = leggings ? "_2" : "_1";
        for (String e : equipment) if (e.endsWith("/" + n + suffix) || e.equals(n + suffix)) return e;
        for (String e : equipment) if (e.endsWith("/" + n)) return e;
        return null;
    }
}
