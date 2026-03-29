package net.recases;

import net.recases.api.ReCasesApi;
import net.recases.app.PluginContext;
import net.recases.animations.round.RoundListener;
import net.recases.commands.CaseCommands;
import net.recases.gui.impl.MenuListener;
import net.recases.gui.impl.MenuManager;
import net.recases.listener.CaseListener;
import net.recases.listener.PluginHooksListener;
import net.recases.placeholders.ReCasesExpansion;
import net.recases.runtime.cache.KeyCache;
import net.recases.runtime.registry.EntityRegistry;
import net.recases.services.AnimationService;
import net.recases.services.CaseService;
import net.recases.services.ConfigService;
import net.recases.services.ItemFactory;
import net.recases.services.LeaderboardHologramService;
import net.recases.services.MessageService;
import net.recases.services.RewardService;
import net.recases.services.StatsService;
import net.recases.services.StorageService;
import net.recases.services.TextFormatter;
import net.recases.services.WorldService;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class ReCases extends JavaPlugin implements PluginContext, ReCasesApi {

    private EntityRegistry entityRegistry;
    private KeyCache keyCache;
    private MenuManager menuManager;
    private TextFormatter textFormatter;
    private ItemFactory itemFactory;
    private WorldService worldService;
    private MessageService messageService;
    private ConfigService configService;
    private AnimationService animationService;
    private RewardService rewardService;
    private StorageService storageService;
    private StatsService statsService;
    private LeaderboardHologramService leaderboardHologramService;
    private CaseService caseService;

    @Override
    public void onEnable() {
        if (!isSupportedServerVersion()) {
            getLogger().severe("ReCases требует Paper/Spigot 1.20 или новее. Текущая версия сервера: " + getServer().getBukkitVersion());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();

        entityRegistry = new EntityRegistry();
        keyCache = new KeyCache();
        menuManager = new MenuManager();
        textFormatter = new TextFormatter();
        itemFactory = new ItemFactory();
        worldService = new WorldService(this);
        messageService = new MessageService(this, textFormatter);
        configService = new ConfigService(this);
        animationService = new AnimationService(this);
        rewardService = new RewardService(this, textFormatter);
        storageService = new StorageService(this, keyCache);
        statsService = new StatsService(this);
        leaderboardHologramService = new LeaderboardHologramService(this, textFormatter);
        caseService = new CaseService(this, entityRegistry, itemFactory, textFormatter, worldService);

        reloadPluginState();
        registerEvents();
        registerCommands();
        registerPlaceholderExpansion();
        getServer().getServicesManager().register(ReCasesApi.class, this, this, ServicePriority.Normal);
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregister(ReCasesApi.class, this);
        leaderboardHologramService.close();
        caseService.clear();
        statsService.close();
        storageService.close();
    }

    public void reloadPluginState() {
        configService.reload();
        caseService.clear();
        storageService.reload();
        statsService.reload();
        caseService.reload();
        leaderboardHologramService.reload();
    }

    private void registerPlaceholderExpansion() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ReCasesExpansion(this).register();
        }
    }

    private void registerEvents() {
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new CaseListener(this), this);
        pluginManager.registerEvents(new RoundListener(this, entityRegistry), this);
        pluginManager.registerEvents(new MenuListener(this), this);
        pluginManager.registerEvents(new PluginHooksListener(this, animationService), this);
    }

    private void registerCommands() {
        if (getCommand("cases") == null) {
            return;
        }

        CaseCommands commands = new CaseCommands(this);
        getCommand("cases").setExecutor(commands);
        getCommand("cases").setTabCompleter(commands);
    }

    public MenuManager getMenuManager() {
        return menuManager;
    }

    public TextFormatter getTextFormatter() {
        return textFormatter;
    }

    public ItemFactory getItemFactory() {
        return itemFactory;
    }

    public WorldService getWorldService() {
        return worldService;
    }

    public MessageService getMessages() {
        return messageService;
    }

    public ConfigService getConfigs() {
        return configService;
    }

    public RewardService getRewardService() {
        return rewardService;
    }

    public AnimationService getAnimations() {
        return animationService;
    }

    @Override
    public JavaPlugin getPlugin() {
        return this;
    }

    @Override
    public AnimationService getOpeningAnimationRegistry() {
        return animationService;
    }

    public StorageService getStorage() {
        return storageService;
    }

    public StatsService getStats() {
        return statsService;
    }

    public LeaderboardHologramService getLeaderboardHolograms() {
        return leaderboardHologramService;
    }

    public CaseService getCaseService() {
        return caseService;
    }

    private boolean isSupportedServerVersion() {
        String version = getServer().getBukkitVersion();
        String[] mainAndBuild = version.split("-", 2);
        String[] parts = mainAndBuild[0].split("\\.");
        if (parts.length < 2) {
            return false;
        }

        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            return major > 1 || (major == 1 && minor >= 20);
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}

