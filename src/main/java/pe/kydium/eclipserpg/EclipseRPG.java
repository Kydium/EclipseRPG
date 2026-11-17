package pe.kydium.eclipserpg;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import pe.kydium.eclipserpg.commands.AdminCommand;
import pe.kydium.eclipserpg.commands.PlayerCommands;
import pe.kydium.eclipserpg.contracts.ContractManager;
import pe.kydium.eclipserpg.data.DataManager;
import pe.kydium.eclipserpg.dungeon.DungeonManager;
import pe.kydium.eclipserpg.dungeon.RuinManager;
import pe.kydium.eclipserpg.dungeon.VillageBuilder;
import pe.kydium.eclipserpg.npc.NpcEntities;
import pe.kydium.eclipserpg.items.ItemManager;
import pe.kydium.eclipserpg.menu.HotkeyMenu;
import pe.kydium.eclipserpg.menu.MenuListener;
import pe.kydium.eclipserpg.mobs.MobManager;
import pe.kydium.eclipserpg.npc.NpcManager;
import pe.kydium.eclipserpg.quests.QuestManager;
import pe.kydium.eclipserpg.rift.RiftManager;
import pe.kydium.eclipserpg.rpg.LevelManager;
import pe.kydium.eclipserpg.shop.ShopManager;
import pe.kydium.eclipserpg.util.Keys;

import java.io.File;

/** EclipseRPG — niveles, Soles, tiendas, Corrompidos, jefes y Grietas para "Crónicas del Eclipse". */
public final class EclipseRPG extends JavaPlugin {
    private DataManager data;
    private LevelManager levels;
    private ItemManager items;
    private MobManager mobs;
    private ShopManager shops;
    private ContractManager contracts;
    private RiftManager rifts;
    private QuestManager quests;
    private NpcManager npcs;
    private DungeonManager dungeons;
    private RuinManager ruins;
    private VillageBuilder village;
    private NpcEntities npcEntities;
    private HotkeyMenu hotkeys;
    private pe.kydium.eclipserpg.world.WorldManager world;
    private pe.kydium.eclipserpg.structure.StructureManager structures;
    private pe.kydium.eclipserpg.story.StoryManager story;
    private pe.kydium.eclipserpg.npc.ResourcePack resourcePack;
    private pe.kydium.eclipserpg.web.EditorApi editor;

    /** Antes se llamaba EclipseRPG: mueve su carpeta (config, historias, jugadores) a la nueva. */
    @Override
    public void onLoad() {
        File now = getDataFolder();
        File old = new File(now.getParentFile(), "EclipseRPG");
        if (!old.isDirectory() || new File(now, "config.yml").exists()) return;
        try {
            if (now.isDirectory()) {
                String[] left = now.list();
                if (left != null && left.length == 0) java.nio.file.Files.delete(now.toPath());
            }
            if (!now.exists()) {
                java.nio.file.Files.move(old.toPath(), now.toPath());
                getLogger().info("Se movió la carpeta plugins/EclipseRPG a plugins/AventuraMaker (el plugin ahora se llama Aventura Maker).");
            }
        } catch (java.io.IOException e) {
            getLogger().warning("No se pudo mover plugins/EclipseRPG a plugins/AventuraMaker: " + e.getMessage()
                    + ". Muévela a mano con el servidor apagado.");
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Keys.init(this);
        checkNewWorld();
        story = new pe.kydium.eclipserpg.story.StoryManager(this);
        story.init();

        data = new DataManager(this);
        levels = new LevelManager(this);
        items = new ItemManager(this);
        mobs = new MobManager(this);
        shops = new ShopManager(this);
        contracts = new ContractManager(this);
        rifts = new RiftManager(this);
        quests = new QuestManager(this);
        npcs = new NpcManager(this);
        dungeons = new DungeonManager(this);
        ruins = new RuinManager(this);
        ruins.load();
        village = new VillageBuilder(this);
        npcEntities = new NpcEntities(this);
        hotkeys = new HotkeyMenu(this);
        world = new pe.kydium.eclipserpg.world.WorldManager(this);
        structures = new pe.kydium.eclipserpg.structure.StructureManager(this);
        loadContent();

        var pm = Bukkit.getPluginManager();
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(levels, this);
        pm.registerEvents(items, this);
        pm.registerEvents(mobs, this);
        pm.registerEvents(contracts, this);
        pm.registerEvents(rifts, this);
        pm.registerEvents(quests, this);
        pm.registerEvents(dungeons, this);
        pm.registerEvents(ruins, this);
        pm.registerEvents(village, this);
        pm.registerEvents(npcEntities, this);
        pm.registerEvents(npcEntities.combat(), this);
        pm.registerEvents(world, this);
        pm.registerEvents(structures, this);
        resourcePack = new pe.kydium.eclipserpg.npc.ResourcePack(this);
        resourcePack.start();
        pm.registerEvents(resourcePack, this);
        editor = new pe.kydium.eclipserpg.web.EditorApi(this);
        if (resourcePack.server() != null) {
            editor.register(resourcePack.server());
            getLogger().info("Editor visual: " + editor.url(null) + "  (o usa /historia editor dentro del juego)");
        }
        pm.registerEvents(hotkeys, this);
        hotkeys.installDatapack();

        var storyCmd = new pe.kydium.eclipserpg.story.StoryCommand(this);
        PluginCommand hc = getCommand("historia");
        if (hc != null) {
            hc.setExecutor(storyCmd);
            hc.setTabCompleter(storyCmd);
        }
        pm.registerEvents(storyCmd, this);
        PluginCommand stc = getCommand("estructura");
        if (stc != null) {
            var sc = new pe.kydium.eclipserpg.structure.StructureCommand(this);
            stc.setExecutor(sc);
            stc.setTabCompleter(sc);
        }
        PluginCommand evc = getCommand("evento");
        if (evc != null) {
            var wc = new pe.kydium.eclipserpg.world.WorldCommand(this);
            evc.setExecutor(wc);
            evc.setTabCompleter(wc);
        }
        PlayerCommands pc = new PlayerCommands(this);
        for (String c : new String[]{"nivel", "soles", "pagar", "misiones", "mision", "guardian"}) bind(c, pc);
        AdminCommand ac = new AdminCommand(this);
        PluginCommand eclipse = getCommand("eclipse");
        if (eclipse != null) {
            eclipse.setExecutor(ac);
            eclipse.setTabCompleter(ac);
        }

        items.startHeldEffectTask();
        mobs.startBarTask();
        mobs.startRegenTask();
        new pe.kydium.eclipserpg.mobs.BossAbilities(this, mobs).start();
        rifts.startTask();
        dungeons.startTask();
        ruins.startTask();
        npcEntities.startTask();
        world.startTasks();
        structures.startTask();
        Bukkit.getScheduler().runTaskLater(this, village::autoGenerate, 100L);
        Bukkit.getScheduler().runTaskLater(this, npcEntities::refreshIfChanged, 60L);
        if (pe.kydium.eclipserpg.test.SmokeTest.enabled()) new pe.kydium.eclipserpg.test.SmokeTest(this).start();
        long autosave = getConfig().getLong("autoguardado-minutos", 5) * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(this, data::saveAll, autosave, autosave);

        for (Player p : Bukkit.getOnlinePlayers()) {
            data.get(p);
            levels.applyAll(p);
        }
        getLogger().info("Aventura Maker listo — historia: " + title());
        // si quedó el jar viejo (EclipseRPG) en la carpeta plugins, apagarlo para que no choquen
        Bukkit.getScheduler().runTask(this, () -> {
            var old = Bukkit.getPluginManager().getPlugin("EclipseRPG");
            if (old != null && old != this && old.isEnabled()) {
                Bukkit.getPluginManager().disablePlugin(old);
                getLogger().severe("Había una copia vieja del plugin (EclipseRPG). La apagué: borra EclipseRPG-1.0.0.jar de la carpeta plugins.");
            }
        });
    }

    @Override
    public void onDisable() {
        if (rifts != null) rifts.stopAll();
        if (world != null) for (String ev : new java.util.ArrayList<>(world.activeEvents())) world.stop(ev, false);
        if (mobs != null) mobs.cleanup();
        if (data != null) data.saveAll();
        if (ruins != null) ruins.save();
        if (npcEntities != null) npcEntities.cleanup();
        if (resourcePack != null) resourcePack.stop();
    }

    /**
     * Si el mundo es nuevo (se borró la carpeta world), olvida la aldea, NPCs, mazmorras y ruinas
     * del mundo anterior para que todo se genere de nuevo, y reinicia el progreso de los jugadores.
     */
    private void checkNewWorld() {
        if (Bukkit.getWorlds().isEmpty()) return;
        String uid = Bukkit.getWorlds().get(0).getUID().toString();
        String prev = getConfig().getString("mundo-uid");
        if (uid.equals(prev)) return;
        boolean hadOld = prev != null || getConfig().getBoolean("aldea.generada", false);
        getConfig().set("mundo-uid", uid);
        if (hadOld) {
            getLogger().info("Mundo nuevo detectado: se reinicia la aldea, los NPCs, las mazmorras y las ruinas.");
            for (String k : new String[]{"campamento", "aldea.centro", "aldea.puestos", "aldea.mundo", "npc-entidades", "relleno-entidades"}) {
                getConfig().set(k, null);
            }
            getConfig().set("aldea.generada", false);
            for (String f : new String[]{"datos-mazmorras.yml", "datos-ruinas.yml", "datos-estructuras.yml", "datos-aldea.json"}) {
                File file = new File(getDataFolder(), f);
                if (file.exists() && !file.delete()) getLogger().warning("No se pudo borrar " + f);
            }
            if (getConfig().getBoolean("reiniciar-progreso-con-mundo-nuevo", true)) {
                File[] players = new File(getDataFolder(), "jugadores").listFiles((d, n) -> n.endsWith(".yml"));
                if (players != null) for (File f : players) if (!f.delete()) getLogger().warning("No se pudo borrar " + f.getName());
                getLogger().info("Progreso de los jugadores reiniciado (mundo nuevo).");
            }
        }
        saveConfig();
    }

    private void bind(String name, PlayerCommands pc) {
        PluginCommand c = getCommand(name);
        if (c != null) {
            c.setExecutor(pc);
            c.setTabCompleter(pc);
        }
    }

    private void loadContent() {
        items.load();
        mobs.load();
        shops.load();
        contracts.load();
        dungeons.load();
        world.load();
        structures.load();
        ruins.reloadSettings();
        var r = story.read();
        story.apply(r);
        quests.load(story.doc());
        npcs.load(story.doc());
        npcEntities.load();
        logStory(r);
    }

    private void logStory(pe.kydium.eclipserpg.story.StoryParser.Result r) {
        getLogger().info("Historia \"" + r.doc().title + "\" (" + story.id() + "): " + r.doc().missions.size() + " misiones, "
                + r.doc().npcs.size() + " NPCs, " + r.errors() + " errores, " + (r.problems().size() - r.errors()) + " avisos");
        int shown = 0;
        for (var pr : r.problems()) {
            if (!pr.error()) continue;
            if (shown++ >= 15) {
                getLogger().warning("…y más. Mira historias/" + story.id() + "/errores.txt");
                break;
            }
            getLogger().warning(pr.toString());
        }
    }

    /**
     * Vuelve a leer la historia. Si tiene errores no la aplica (sigue la anterior) salvo que force sea true.
     * Devuelve el resultado para mostrarlo al jugador.
     */
    public pe.kydium.eclipserpg.story.StoryParser.Result reloadStory(boolean force) {
        reloadConfig();
        items.load();
        mobs.load();
        shops.load();
        contracts.load();
        dungeons.load();
        world.load();
        structures.load();
        npcEntities.combat().load();
        ruins.reloadSettings();
        var r = story.read();
        if (r.errors() > 0 && !force) {
            // guardar el informe pero seguir con la historia que ya funcionaba
            story.reportOnly(r);
            return r;
        }
        story.apply(r);
        quests.load(story.doc());
        npcs.load(story.doc());
        npcEntities.placeStoryNpcs();
        saveConfig();
        logStory(r);
        if (resourcePack != null) {
            String before = resourcePack.sha1();
            resourcePack.rebuild();
            if (!before.equals(resourcePack.sha1())) for (Player p : Bukkit.getOnlinePlayers()) resourcePack.send(p);
        }
        return r;
    }

    public void reloadAll() {
        reloadConfig();
        loadContent();
        for (Player p : Bukkit.getOnlinePlayers()) levels.applyAll(p);
    }

    public DataManager data() { return data; }
    public LevelManager levels() { return levels; }
    public ItemManager items() { return items; }
    public MobManager mobs() { return mobs; }
    public ShopManager shops() { return shops; }
    public ContractManager contracts() { return contracts; }
    public RiftManager rifts() { return rifts; }
    public QuestManager quests() { return quests; }
    public NpcManager npcs() { return npcs; }
    public DungeonManager dungeons() { return dungeons; }
    public RuinManager ruins() { return ruins; }
    public VillageBuilder village() { return village; }
    public NpcEntities npcEntities() { return npcEntities; }
    public HotkeyMenu hotkeys() { return hotkeys; }
    public pe.kydium.eclipserpg.world.WorldManager world() { return world; }
    public pe.kydium.eclipserpg.structure.StructureManager structures() { return structures; }
    public pe.kydium.eclipserpg.story.StoryManager story() { return story; }

    /** Nombre de la historia y de su aldea (para los textos del juego). */
    public String title() { return story == null ? "EclipseRPG" : story.doc().title; }

    public String villageName() { return story == null ? "la Aldea" : story.doc().village; }
    public pe.kydium.eclipserpg.npc.ResourcePack resourcePack() { return resourcePack; }
    public pe.kydium.eclipserpg.web.EditorApi editor() { return editor; }
}
