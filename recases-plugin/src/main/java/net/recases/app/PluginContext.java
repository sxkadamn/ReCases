package net.recases.app;

import net.recases.services.CaseConditionService;
import net.recases.services.CaseTriggerService;
import net.recases.gui.impl.MenuManager;
import net.recases.services.AnimationService;
import net.recases.services.BedrockSupportService;
import net.recases.services.CaseService;
import net.recases.services.DiscordBotService;
import net.recases.services.RedisSyncService;
import net.recases.services.ItemFactory;
import net.recases.services.MessageService;
import net.recases.services.LeaderboardHologramService;
import net.recases.services.PromoCodeService;
import net.recases.services.RewardService;
import net.recases.services.StatsService;
import net.recases.services.StorageService;
import net.recases.services.TextFormatter;
import net.recases.services.WorldService;
import net.recases.services.SchematicService;
import net.recases.services.DiscordWebhookService;
import net.recases.services.OpeningResultService;
import net.recases.services.RewardAuditService;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

public interface PluginContext extends Plugin {

    MenuManager getMenuManager();

    TextFormatter getTextFormatter();

    ItemFactory getItemFactory();

    WorldService getWorldService();

    SchematicService getSchematics();

    MessageService getMessages();

    RewardService getRewardService();

    CaseConditionService getConditionService();

    CaseTriggerService getTriggerService();

    AnimationService getAnimations();

    BedrockSupportService getBedrockSupport();

    RedisSyncService getRedisSync();

    StorageService getStorage();

    StatsService getStats();

    LeaderboardHologramService getLeaderboardHolograms();

    CaseService getCaseService();

    RewardAuditService getRewardAudit();

    DiscordWebhookService getDiscordWebhooks();

    DiscordBotService getDiscordBot();

    OpeningResultService getOpeningResults();

    PromoCodeService getPromoCodes();

    FileConfiguration getConfig();

    void saveConfig();

    void saveConfigUtf8();

    void reloadPluginState();
}

