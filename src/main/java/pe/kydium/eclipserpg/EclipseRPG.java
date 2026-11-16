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

    @Override
    public void onEnable() {
        saveDefaultConfig();
        for (String f : new String[]{"items.yml", "mobs.yml", "shops.yml", "contracts.yml", "quests.yml", "npcs.yml", "mazmorras.yml"}) {
            if (!new File(getDataFolder(), f).exists()) saveResource(f, false);
        }
        Keys.init(this);
        checkNewWorld();

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
        pm.registerEvents(new pe.kydium.eclipserpg.npc.ResourcePack(this), this);
        pm.registerEvents(hotkeys, this);
        hotkeys.installDatapack();

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
        new pe.kydium.eclipserpg.mobs.BossAbilities(this, mobs).start();
        rifts.startTask();
        dungeons.startTask();
        ruins.startTask();
        npcEntities.startTask();
        Bukkit.getScheduler().runTaskLater(this, village::autoGenerate, 100L);
        long autosave = getConfig().getLong("autoguardado-minutos", 5) * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(this, data::saveAll, autosave, autosave);

        for (Player p : Bukkit.getOnlinePlayers()) {
            data.get(p);
            levels.applyAll(p);
        }
        getLogger().info("EclipseRPG listo. ¡Que vuelva el sol!");
    }

    @Override
    public void onDisable() {
        if (rifts != null) rifts.stopAll();
        if (mobs != null) mobs.cleanup();
        if (data != null) data.saveAll();
        if (ruins != null) ruins.save();
        if (npcEntities != null) npcEntities.cleanup();
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
            for (String f : new String[]{"datos-mazmorras.yml", "datos-ruinas.yml"}) {
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
        quests.load();
        npcs.load();
        dungeons.load();
        ruins.reloadSettings();
        npcEntities.load();
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
    public VillageBuilder village() { return village; }
    public NpcEntities npcEntities() { return npcEntities; }
    public HotkeyMenu hotkeys() { return hotkeys; }
}
