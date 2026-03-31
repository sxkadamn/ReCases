package net.recases;

import net.recases.api.ReCasesApi;
import net.recases.app.PluginContext;
import net.recases.animations.round.RoundListener;
import net.recases.animations.opening.AbstractEntityOpeningAnimation;
import net.recases.animations.opening.AnchorRiseOpeningAnimation;
import net.recases.animations.opening.RainlyOpeningAnimation;
import net.recases.animations.opening.SwordsOpeningAnimation;
import net.recases.animations.opening.WheelOpeningAnimation;
import net.recases.commands.CaseCommands;
import net.recases.gui.impl.MenuListener;
import net.recases.gui.impl.MenuManager;
import net.recases.listener.CaseListener;
import net.recases.listener.OpeningGuardListener;
import net.recases.listener.PluginHooksListener;
import net.recases.listener.UpdateNotifierListener;
import net.recases.protocollib.hologram.HologramLine;
import net.recases.placeholders.ReCasesExpansion;
import net.recases.runtime.cache.KeyCache;
import net.recases.runtime.registry.EntityRegistry;
import net.recases.services.AnimationService;
import net.recases.services.BStatsService;
import net.recases.services.CaseService;
import net.recases.services.ConfigService;
import net.recases.services.ItemFactory;
import net.recases.services.LeaderboardHologramService;
import net.recases.services.MessageService;
import net.recases.services.NetworkSyncService;
import net.recases.services.RewardService;
import net.recases.services.SchematicService;
import net.recases.services.DiscordWebhookService;
import net.recases.services.OpeningResultService;
import net.recases.services.RewardAuditService;
import net.recases.services.StatsService;
import net.recases.services.StorageService;
import net.recases.services.TextFormatter;
import net.recases.services.UpdateService;
import net.recases.services.WorldService;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public final class ReCases extends JavaPlugin implements PluginContext, ReCasesApi {

    private EntityRegistry entityRegistry;
    private KeyCache keyCache;
    private MenuManager menuManager;
    private TextFormatter textFormatter;
    private ItemFactory itemFactory;
    private WorldService worldService;
    private SchematicService schematicService;
    private MessageService messageService;
    private ConfigService configService;
    private AnimationService animationService;
    private RewardService rewardService;
    private StorageService storageService;
    private StatsService statsService;
    private BStatsService bStatsService;
    private LeaderboardHologramService leaderboardHologramService;
    private CaseService caseService;
    private RewardAuditService rewardAuditService;
    private DiscordWebhookService discordWebhookService;
    private OpeningResultService openingResultService;
    private UpdateService updateService;
    private NetworkSyncService networkSyncService;

    @Override
    public void onEnable() {
        if (!isSupportedServerVersion()) {
            getLogger().severe("ReCases требует Paper/Spigot 1.20 или новее. Текущая версия сервера: " + getServer().getBukkitVersion());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        saveResourceIfMissing("messages_ru.yml");
        saveResourceIfMissing("messages_en.yml");

        entityRegistry = new EntityRegistry();
        keyCache = new KeyCache();
        menuManager = new MenuManager();
        textFormatter = new TextFormatter();
        itemFactory = new ItemFactory();
        worldService = new WorldService(this);
        schematicService = new SchematicService(this);
        messageService = new MessageService(this, textFormatter);
        configService = new ConfigService(this);
        animationService = new AnimationService(this);
        rewardService = new RewardService(this, textFormatter, itemFactory);
        storageService = new StorageService(this, keyCache);
        statsService = new StatsService(this);
        bStatsService = new BStatsService(this);
        rewardAuditService = new RewardAuditService(this);
        discordWebhookService = new DiscordWebhookService(this);
        leaderboardHologramService = new LeaderboardHologramService(this, textFormatter);
        caseService = new CaseService(this, entityRegistry, itemFactory, textFormatter, worldService);
        openingResultService = new OpeningResultService(this);
        updateService = new UpdateService(this);
        networkSyncService = new NetworkSyncService(this, keyCache, statsService);

        reloadPluginState();
        registerEvents();
        registerCommands();
        registerPlaceholderExpansion();
        getServer().getServicesManager().register(ReCasesApi.class, this, this, ServicePriority.Normal);
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregister(ReCasesApi.class, this);
        networkSyncService.close();
        leaderboardHologramService.close();
        caseService.clear();
        schematicService.close();
        cleanupResidualEntities();
        bStatsService.close();
        statsService.close();
        storageService.close();
        rewardAuditService.close();
        discordWebhookService.close();
        updateService.close();
    }

    public void reloadPluginState() {
        configService.reload();
        schematicService.reload();
        messageService.reload();
        caseService.clear();
        storageService.reload();
        statsService.reload();
        rewardAuditService.reload();
        discordWebhookService.reload();
        caseService.reload();
        leaderboardHologramService.reload();
        updateService.reload();
        networkSyncService.reload();
        bStatsService.reload();
    }

    @Override
    public void saveConfigUtf8() {
        try {
            Files.createDirectories(getDataFolder().toPath());
            Files.writeString(getDataFolder().toPath().resolve("config.yml"), getConfig().saveToString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save config.yml in UTF-8", exception);
        }
    }

    private void registerPlaceholderExpansion() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ReCasesExpansion(this).register();
        }
    }

    private void registerEvents() {
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new CaseListener(this), this);
        pluginManager.registerEvents(new OpeningGuardListener(this), this);
        pluginManager.registerEvents(new RoundListener(this, entityRegistry), this);
        pluginManager.registerEvents(new MenuListener(this), this);
        pluginManager.registerEvents(new PluginHooksListener(this, animationService), this);
        pluginManager.registerEvents(new UpdateNotifierListener(this, updateService), this);
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

    @Override
    public SchematicService getSchematics() {
        return schematicService;
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

    public RewardAuditService getRewardAudit() {
        return rewardAuditService;
    }

    public DiscordWebhookService getDiscordWebhooks() {
        return discordWebhookService;
    }

    public OpeningResultService getOpeningResults() {
        return openingResultService;
    }

    public UpdateService getUpdater() {
        return updateService;
    }

    private void saveResourceIfMissing(String resourcePath) {
        java.io.File target = new java.io.File(getDataFolder(), resourcePath);
        if (!target.exists()) {
            saveResource(resourcePath, false);
        }
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

    private void cleanupResidualEntities() {
        for (World world : getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (shouldRemoveOnDisable(entity)) {
                    entity.remove();
                }
            }
        }
    }

    private boolean shouldRemoveOnDisable(Entity entity) {
        return hasOwnedMetadata(entity, HologramLine.HOLOGRAM_METADATA)
                || hasOwnedMetadata(entity, LeaderboardHologramService.LEADERBOARD_HOLOGRAM_METADATA)
                || hasOwnedMetadata(entity, "armor_head")
                || hasOwnedMetadata(entity, AbstractEntityOpeningAnimation.ENTITY_TARGET_METADATA)
                || hasOwnedMetadata(entity, WheelOpeningAnimation.WHEEL_ENTITY_METADATA)
                || hasOwnedMetadata(entity, SwordsOpeningAnimation.SWORDS_DECOR_METADATA)
                || hasOwnedMetadata(entity, AnchorRiseOpeningAnimation.ANCHOR_RISE_METADATA)
                || hasOwnedMetadata(entity, RainlyOpeningAnimation.RAINLY_METADATA);
    }

    private boolean hasOwnedMetadata(Entity entity, String key) {
        if (!entity.hasMetadata(key)) {
            return false;
        }

        List<MetadataValue> metadataValues = entity.getMetadata(key);
        for (MetadataValue metadataValue : metadataValues) {
            if (metadataValue.getOwningPlugin() == this) {
                return true;
            }
        }
        return false;
    }
}

