package pe.kydium.eclipserpg.quests;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import pe.kydium.eclipserpg.EclipseRPG;
import pe.kydium.eclipserpg.data.PlayerData;
import pe.kydium.eclipserpg.mobs.MobManager;
import pe.kydium.eclipserpg.util.Keys;
import pe.kydium.eclipserpg.util.Txt;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Historia de 110 misiones: diálogos con NPCs, objetivos, entregas y recompensas. */
public final class QuestManager implements Listener {
    private final EclipseRPG plugin;
    private final List<Quest> quests = new ArrayList<>();
    private final Map<Integer, String> actNames = new HashMap<>();
    private final Set<UUID> talking = new HashSet<>();
    private final DialogUI ui;

    public QuestManager(EclipseRPG plugin) {
        this.plugin = plugin;
        this.ui = new DialogUI(plugin);
    }

    // ---------- Carga ----------

    /** Carga las misiones de la historia activa (historia.txt). */
    public void load(pe.kydium.eclipserpg.story.StoryDoc doc) {
        quests.clear();
        actNames.clear();
        actNames.putAll(doc.acts);
        for (pe.kydium.eclipserpg.story.StoryDoc.Mission m : doc.missions) {
            if (m.npc == null) continue;
            Quest q = new Quest();
            q.id = m.id;
            q.number = quests.size() + 1;
            q.act = m.act;
            q.name = m.name;
            q.npc = m.npc;
            q.minLevel = m.level;
            q.boss = m.boss;
            q.noDeath = m.noDeath;
            q.dungeon = m.dungeon;
            q.event = m.event;
            q.structure = m.structure;
            q.weather = m.weather;
            q.xp = m.xp;
            q.soles = m.money;
            q.start.addAll(m.start);
            q.end.addAll(m.end);
            q.reminder.addAll(m.reminder);
            q.replies.addAll(m.replies);
            q.decline = m.decline;
            for (pe.kydium.eclipserpg.story.StoryDoc.Objective so : m.objectives) {
                Quest.Objective o = new Quest.Objective();
                try {
                    o.type = Quest.Type.valueOf(so.type);
                } catch (IllegalArgumentException | NullPointerException e) {
                    continue;
                }
                o.targets.addAll(so.targets);
                o.amount = so.amount;
                o.corrupted = so.corrupted;
                o.text = so.text;
                q.objectives.add(o);
            }
            for (String sp : m.spawns) {
                String[] parts = sp.split(":");
                q.spawns.add(new Quest.Spawn(parts[0].trim(), parts.length > 1 ? intOf(parts[1].trim(), 1) : 1));
            }
            q.giveOnAccept.addAll(m.receive);
            q.rewards.addAll(m.rewards);
            for (pe.kydium.eclipserpg.story.StoryDoc.Choice c : m.choices) {
                q.choices.add(new Quest.Choice(c.text, new ArrayList<>(c.rewards), c.flag, c.message));
            }
            quests.add(q);
        }
        plugin.getLogger().info("Misiones cargadas: " + quests.size());
    }

    private static int intOf(Object o, int def) {
        if (o == null) return def;
        try {
            return (int) Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public int total() {
        return quests.size();
    }

    public String actName(int act) {
        return actNames.getOrDefault(act, "Acto " + act);
    }

    public Quest current(Player p) {
        PlayerData d = plugin.data().get(p);
        if (d.questIndex < 0 || d.questIndex >= quests.size()) return null;
        Quest q = quests.get(d.questIndex);
        if (d.questProgress.length != q.objectives.size()) {
            d.questProgress = new int[q.objectives.size()];
            d.dirty = true;
        }
        return q;
    }

    public Quest byNumber(int n) {
        return n >= 1 && n <= quests.size() ? quests.get(n - 1) : null;
    }

    public boolean isTalking(Player p) {
        return talking.contains(p.getUniqueId());
    }

    // ---------- Estado ----------

    public enum State { NONE, OTHER_NPC, LOCKED, AVAILABLE, IN_PROGRESS, READY, TALK_TARGET, FINISHED }

    /** Qué ofrece este NPC al jugador ahora mismo. */
    public State stateFor(Player p, String npcId) {
        Quest q = current(p);
        if (q == null) return State.FINISHED;
        PlayerData d = plugin.data().get(p);
        if (!q.npc.equals(npcId)) {
            if (d.questAccepted && pendingTalk(p, q, npcId) >= 0) return State.TALK_TARGET;
            return State.OTHER_NPC;
        }
        if (!d.questAccepted) return d.level < q.minLevel ? State.LOCKED : State.AVAILABLE;
        return isComplete(p, q) ? State.READY : State.IN_PROGRESS;
    }

    private int pendingTalk(Player p, Quest q, String npcId) {
        PlayerData d = plugin.data().get(p);
        for (int i = 0; i < q.objectives.size(); i++) {
            Quest.Objective o = q.objectives.get(i);
            if (o.type == Quest.Type.HABLAR && o.matches(npcId) && d.questProgress[i] < o.amount) return i;
        }
        return -1;
    }

    public int progressOf(Player p, Quest q, int i) {
        Quest.Objective o = q.objectives.get(i);
        if (o.isInventory()) return Math.min(o.amount, countItems(p, o));
        int[] pr = plugin.data().get(p).questProgress;
        return i < pr.length ? Math.min(o.amount, pr[i]) : 0;
    }

    public boolean isComplete(Player p, Quest q) {
        for (int i = 0; i < q.objectives.size(); i++) {
            if (progressOf(p, q, i) < q.objectives.get(i).amount) return false;
        }
        return true;
    }

    // ---------- Hablar con NPC ----------

    public void talk(Player p, String npcId) {
        if (isTalking(p)) return;
        Quest q = current(p);
        String npcName = plugin.npcs().name(npcId);
        PlayerData d = plugin.data().get(p);
        if (q == null) {
            say(p, npcName, List.of(plugin.npcs().talkLines(npcId).isEmpty() ? "Gracias por todo, héroe." : plugin.npcs().talkLines(npcId).get(0)), null);
            return;
        }
        if (!q.npc.equals(npcId)) {
            int idx = d.questAccepted ? pendingTalk(p, q, npcId) : -1;
            if (idx >= 0) {
                d.questProgress[idx] = q.objectives.get(idx).amount;
                d.dirty = true;
                List<String> lines = plugin.npcs().talkLines(npcId);
                say(p, npcName, lines.isEmpty() ? List.of("Cuenta conmigo para lo que viene.") : lines,
                        () -> notifyProgress(p, q));
            } else {
                say(p, npcName, List.of("Ahora no tengo nada para ti. Te busca " + plugin.npcs().name(q.npc) + "<white>."), null);
            }
            return;
        }
        if (!d.questAccepted) {
            if (d.level < q.minLevel) {
                say(p, npcName, List.of("Todavía no estás listo para esto. Vuelve cuando seas <yellow>nivel " + q.minLevel
                        + "<white>. Entrena con contratos, Grietas o explorando ruinas."), null);
                return;
            }
            if (ui.enabled()) {
                List<String> body = new ArrayList<>(q.start);
                if (!q.objectives.isEmpty()) {
                    body.add("");
                    body.add("<gold>Objetivos:");
                    for (Quest.Objective o : q.objectives) body.add("<gray>• <white>" + o.text + " <gray>(" + o.amount + ")");
                }
                // el jugador contesta con algo que tiene que ver con lo que le piden (no "Aceptar misión")
                List<DialogUI.Button> btns = new ArrayList<>();
                List<String> replies = q.replies.isEmpty() ? List.of(q.autoReply()) : q.replies;
                for (String r : replies)
                    btns.add(new DialogUI.Button("<green>➜ <white>" + r, "<gray>Aceptas la misión · " + actName(q.act), () -> accept(p, q)));
                btns.add(new DialogUI.Button("<gray>" + (q.decline == null || q.decline.isBlank() ? "Ahora no puedo. Vuelvo luego." : q.decline),
                        "Puedes volver cuando quieras", null));
                ui.show(p, npcName + " <dark_gray>· <yellow>" + q.name, body, btns, true);
            } else {
                say(p, npcName, q.start, () -> accept(p, q));
            }
            return;
        }
        if (!isComplete(p, q)) {
            List<String> lines = new ArrayList<>(q.reminder.isEmpty() ? List.of("¿Ya terminaste? Esto es lo que falta:") : q.reminder);
            if (ui.enabled()) {
                lines.add("");
                for (int i = 0; i < q.objectives.size(); i++) {
                    Quest.Objective o = q.objectives.get(i);
                    int have = progressOf(p, q, i);
                    lines.add((have >= o.amount ? "<green>✔ " : "<gray>• ") + "<white>" + o.text + " <gray>" + have + "/" + o.amount);
                }
                if (q.noDeath) lines.add("<red>⚠ Si mueres, el progreso se reinicia");
                ui.show(p, npcName + " <dark_gray>· <yellow>" + q.name, lines,
                        List.of(new DialogUI.Button("<yellow>Sigo en ello.", null, null)), true);
            } else {
                say(p, npcName, lines, () -> showObjectives(p, q));
            }
            return;
        }
        if (!q.choices.isEmpty()) {
            if (ui.enabled()) showChoices(p, q);
            else say(p, npcName, q.end.isEmpty() ? List.of("Es hora de decidir.") : q.end, () -> showChoices(p, q));
            return;
        }
        turnIn(p, q, null);
    }

    private void accept(Player p, Quest q) {
        PlayerData d = plugin.data().get(p);
        d.questAccepted = true;
        d.questProgress = new int[q.objectives.size()];
        d.dirty = true;
        for (int i = 0; i < q.objectives.size(); i++) {
            Quest.Objective o = q.objectives.get(i);
            if (o.type == Quest.Type.DIMENSION && o.matches(envName(p.getWorld()))) d.questProgress[i] = o.amount;
        }
        for (String g : q.giveOnAccept) give(p, g);
        if (q.dungeon > 0) plugin.dungeons().ensure(q.dungeon, p);
        if (q.structure != null && !plugin.structures().ensure(q.structure, p))
            plugin.getLogger().warning("La misión \"" + q.name + "\" pide la estructura '" + q.structure + "', que no existe.");
        if (q.weather != null) plugin.world().setWeather(q.weather, 600);
        if (q.event != null && !plugin.world().start(q.event))
            plugin.getLogger().warning("La misión \"" + q.name + "\" pide el evento '" + q.event + "', que no existe en Mundo vivo.");
        for (Quest.Spawn sp : q.spawns) {
            for (int i = 0; i < sp.amount(); i++) {
                plugin.mobs().spawn(sp.mob(), plugin.mobs().spawnSpot(p.getLocation(), 8));
            }
        }
        p.showTitle(Title.title(Txt.mm("<gold>" + q.name), Txt.mm("<gray>" + actName(q.act)),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
        Txt.sound(p, "item.book.page_turn", 1f, 1f);
        if (q.objectives.isEmpty()) {
            if (!q.choices.isEmpty()) showChoices(p, q);
            else turnIn(p, q, null);
            return;
        }
        showObjectives(p, q);
    }

    private void turnIn(Player p, Quest q, Quest.Choice choice) {
        PlayerData d = plugin.data().get(p);
        for (Quest.Objective o : q.objectives) {
            if (o.type == Quest.Type.ENTREGAR) takeItems(p, o, o.amount);
        }
        Runnable finish = () -> {
            for (String r : q.rewards) give(p, r);
            if (choice != null) {
                for (String r : choice.rewards()) give(p, r);
                if (choice.flag() != null) d.flags.add(choice.flag());
            }
            plugin.levels().reward(p, q.xp, q.soles);
            d.questIndex++;
            d.questAccepted = false;
            d.questProgress = new int[0];
            d.dirty = true;
            Txt.sound(p, "ui.toast.challenge_complete", 1f, 1.1f);
            p.showTitle(Title.title(Txt.mm("<green>¡Misión completada!"), Txt.mm("<white>" + q.name),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
            Quest next = current(p);
            if (next == null) {
                Txt.send(p, "<gold><bold>¡Completaste " + plugin.title() + "!");
                return;
            }
            if (next.act != q.act) {
                Txt.send(p, "<light_purple><bold>Nuevo capítulo: " + actName(next.act));
            }
            String lvl = next.minLevel > d.level ? " <gray>(necesitas nivel " + next.minLevel + ")" : "";
            Txt.send(p, "<yellow>Siguiente misión: <white>" + next.name + " <gray>· habla con " + plugin.npcs().name(next.npc) + lvl);
        };
        String npcName = plugin.npcs().name(q.npc);
        // Primero se entregan las recompensas (así cerrar la ventana no las pierde)
        finish.run();
        List<String> lines = new ArrayList<>();
        if (choice != null && choice.message() != null) lines.add(choice.message());
        else lines.addAll(q.end);
        if (lines.isEmpty()) return;
        if (q.xp > 0 || q.soles > 0) {
            lines.add("");
            lines.add("<green>Recompensa: <aqua>+" + Txt.num(q.xp) + " XP <yellow>+" + Txt.num(q.soles) + " ☀");
        }
        Quest next = current(p);
        if (next != null && ui.enabled()) {
            lines.add("<gray>Siguiente: <white>" + next.name + " <gray>· " + plugin.npcs().name(next.npc));
        }
        say(p, npcName + " <dark_gray>· <green>¡Misión completada!", lines, null);
    }

    private void showChoices(Player p, Quest q) {
        if (ui.enabled()) {
            List<DialogUI.Button> buttons = new ArrayList<>();
            for (int i = 0; i < q.choices.size(); i++) {
                final int idx = i;
                buttons.add(new DialogUI.Button("<yellow><bold>" + q.choices.get(i).text(), "Esta decisión no se puede deshacer", () -> choose(p, idx)));
            }
            List<String> body = new ArrayList<>(q.end.isEmpty() ? List.of("Es hora de decidir.") : q.end);
            ui.show(p, "<gold><bold>Elige tu destino", body, buttons, false);
            return;
        }
        p.sendMessage(Component.empty());
        p.sendMessage(Txt.mm("<gold><bold>Elige tu destino:"));
        for (int i = 0; i < q.choices.size(); i++) {
            Quest.Choice c = q.choices.get(i);
            p.sendMessage(Txt.mm("  <yellow><bold>[" + c.text() + "]")
                    .clickEvent(ClickEvent.runCommand("/mision elegir " + i))
                    .hoverEvent(HoverEvent.showText(Txt.mm("<gray>Clic para elegir"))));
        }
        p.sendMessage(Txt.mm("<gray>(Haz clic en una opción del chat)"));
    }

    public void choose(Player p, int index) {
        Quest q = current(p);
        PlayerData d = plugin.data().get(p);
        if (q == null || q.choices.isEmpty() || !d.questAccepted || !isComplete(p, q)) {
            Txt.send(p, "<red>No hay ninguna decisión pendiente.");
            return;
        }
        if (index < 0 || index >= q.choices.size()) return;
        turnIn(p, q, q.choices.get(index));
    }

    // ---------- Diálogos ----------

    /** Muestra líneas de diálogo una por una y al final ejecuta "then". */
    public void say(Player p, String speaker, List<String> lines, Runnable then) {
        if (ui.enabled()) {
            ui.show(p, speaker, lines, List.of(new DialogUI.Button(then == null ? "<yellow>Cerrar" : "<yellow>Continuar", null, then)), then == null);
            return;
        }
        talking.add(p.getUniqueId());
        long delay = 0;
        for (String line : lines) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                p.sendMessage(Txt.mm(speaker + "<dark_gray>: <white>" + line));
                Txt.sound(p, "entity.villager.ambient", 0.6f, 1.2f);
            }, delay);
            delay += Math.max(25, Math.min(70, line.length()));
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            talking.remove(p.getUniqueId());
            if (p.isOnline() && then != null) then.run();
        }, delay);
    }

    public void showObjectives(Player p, Quest q) {
        p.sendMessage(Txt.mm("<gold>▶ " + q.name));
        for (int i = 0; i < q.objectives.size(); i++) {
            Quest.Objective o = q.objectives.get(i);
            int have = progressOf(p, q, i);
            String mark = have >= o.amount ? "<green>✔ " : "<gray>• ";
            p.sendMessage(Txt.mm("  " + mark + "<white>" + o.text + " <gray>" + have + "/" + o.amount));
        }
        if (q.noDeath) p.sendMessage(Txt.mm("  <red>⚠ Si mueres, el progreso se reinicia"));
        p.sendMessage(Txt.mm("<gray>Vuelve con " + plugin.npcs().name(q.npc) + " <gray>al terminar. Diario: <white>/misiones"));
    }

    private void notifyProgress(Player p, Quest q) {
        if (isComplete(p, q)) {
            p.sendActionBar(Txt.mm("<green>✔ Misión lista: vuelve con " + plugin.npcs().name(q.npc)));
            Txt.sound(p, "block.note_block.bell", 1f, 1.4f);
        }
    }

    // ---------- Progreso ----------

    public void addProgress(Player p, Quest.Type type, String id, int amount, boolean corrupted) {
        Quest q = current(p);
        PlayerData d = plugin.data().get(p);
        if (q == null || !d.questAccepted) return;
        boolean changed = false;
        for (int i = 0; i < q.objectives.size(); i++) {
            Quest.Objective o = q.objectives.get(i);
            if (o.type != type || d.questProgress[i] >= o.amount) continue;
            if (o.corrupted && !corrupted) continue;
            if (id != null && !o.matches(id)) continue;
            d.questProgress[i] = Math.min(o.amount, d.questProgress[i] + amount);
            changed = true;
            p.sendActionBar(Txt.mm("<yellow>" + o.text + " <white>" + d.questProgress[i] + "/" + o.amount));
        }
        if (changed) {
            d.dirty = true;
            notifyProgress(p, q);
        }
    }

    private List<Player> creditPlayers(LivingEntity dead, Player killer, boolean big) {
        List<Player> out = new ArrayList<>();
        double radius = big ? (dead.getType() == EntityType.ENDER_DRAGON ? 250 : 64) : 24;
        for (Player p : dead.getWorld().getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            if (p.getLocation().distanceSquared(dead.getLocation()) <= radius * radius) out.add(p);
        }
        if (killer != null && !out.contains(killer)) out.add(killer);
        if (!big && killer == null) out.clear();
        return out;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        LivingEntity le = e.getEntity();
        if (le instanceof Player) return;
        String mobId = le.getPersistentDataContainer().get(Keys.MOB, PersistentDataType.STRING);
        MobManager.MobDef def = mobId == null ? null : plugin.mobs().def(mobId);
        boolean big = (def != null && def.boss) || le.getType() == EntityType.WITHER || le.getType() == EntityType.ENDER_DRAGON;
        String type = le.getType().getKey().getKey();
        boolean corrupted = mobId != null;
        for (Player p : creditPlayers(le, le.getKiller(), big)) {
            addProgress(p, Quest.Type.MATAR, type, 1, corrupted);
            if (mobId != null && !mobId.equals("natural")) addProgress(p, Quest.Type.MOB, mobId, 1, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        addProgress(e.getPlayer(), Quest.Type.MINAR, e.getBlock().getType().getKey().getKey(), 1, false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSmelt(FurnaceExtractEvent e) {
        addProgress(e.getPlayer(), Quest.Type.FUNDIR, e.getItemType().getKey().getKey(), e.getItemAmount(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) addProgress(e.getPlayer(), Quest.Type.PESCAR, null, 1, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent e) {
        if (e.getOwner() instanceof Player p) addProgress(p, Quest.Type.DOMESTICAR, e.getEntity().getType().getKey().getKey(), 1, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent e) {
        addProgress(e.getEnchanter(), Quest.Type.ENCANTAR, null, 1, false);
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        addProgress(e.getPlayer(), Quest.Type.DIMENSION, envName(e.getPlayer().getWorld()), 1, false);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Quest q = current(p);
        PlayerData d = plugin.data().get(p);
        if (q == null || !q.noDeath || !d.questAccepted) return;
        d.questProgress = new int[q.objectives.size()];
        d.dirty = true;
        Txt.send(p, "<red>Moriste: el progreso de \"" + q.name + "\" se reinició.");
    }

    private static String envName(World w) {
        return switch (w.getEnvironment()) {
            case NETHER -> "nether";
            case THE_END -> "the_end";
            default -> "overworld";
        };
    }

    // ---------- Objetos ----------

    private boolean itemMatches(Quest.Objective o, ItemStack it) {
        if (it == null || it.getType().isAir()) return false;
        String custom = plugin.items().idOf(it);
        for (String t : o.targets) {
            if (t.startsWith("custom:")) {
                if (t.substring(7).equals(custom)) return true;
            }
        }
        if (custom != null) return false; // los objetos únicos no cuentan como materiales normales
        return o.matches(it.getType());
    }

    public int countItems(Player p, Quest.Objective o) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) if (itemMatches(o, it)) n += it.getAmount();
        return n;
    }

    private void takeItems(Player p, Quest.Objective o, int amount) {
        ItemStack[] contents = p.getInventory().getContents();
        int left = amount;
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack it = contents[i];
            if (!itemMatches(o, it)) continue;
            int take = Math.min(left, it.getAmount());
            it.setAmount(it.getAmount() - take);
            left -= take;
            if (it.getAmount() <= 0) p.getInventory().setItem(i, null);
        }
    }

    /** "material:BREAD 10", "custom:hoja_del_alba", "pocion:healing 3", "libro:sharpness:4" */
    public void give(Player p, String spec) {
        String[] parts = spec.trim().split("\\s+");
        int amount = parts.length > 1 ? intOf(parts[1], 1) : 1;
        String s = parts[0].contains(":") ? parts[0] : "material:" + parts[0];
        ItemStack base = plugin.shops().buildSpec(s, 1);
        if (base == null) {
            plugin.getLogger().warning("Premio inválido: " + spec);
            return;
        }
        int max = Math.max(1, base.getMaxStackSize());
        while (amount > 0) {
            ItemStack it = base.clone();
            it.setAmount(Math.min(max, amount));
            amount -= it.getAmount();
            plugin.items().give(p, it);
        }
    }

    // ---------- Admin ----------

    public void setQuest(Player p, int number) {
        PlayerData d = plugin.data().get(p);
        d.questIndex = Math.max(0, Math.min(quests.size(), number - 1));
        d.questAccepted = false;
        d.questProgress = new int[0];
        d.dirty = true;
    }

    public Material icon(State st) {
        return switch (st) {
            case AVAILABLE -> Material.WRITABLE_BOOK;
            case READY -> Material.EMERALD;
            case IN_PROGRESS -> Material.BOOK;
            case TALK_TARGET -> Material.PAPER;
            case LOCKED -> Material.BARRIER;
            default -> Material.GRAY_DYE;
        };
    }
}
