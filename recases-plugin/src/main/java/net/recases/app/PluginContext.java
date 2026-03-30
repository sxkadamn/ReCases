package net.recases.app;

import net.recases.gui.impl.MenuManager;
import net.recases.services.AnimationService;
import net.recases.services.CaseService;
import net.recases.services.ItemFactory;
import net.recases.services.MessageService;
import net.recases.services.LeaderboardHologramService;
import net.recases.services.RewardService;
import net.recases.services.StatsService;
import net.recases.services.StorageService;
import net.recases.services.TextFormatter;
import net.recases.services.WorldService;
import net.recases.services.SchematicService;
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

    AnimationService getAnimations();

    StorageService getStorage();

    StatsService getStats();

    LeaderboardHologramService getLeaderboardHolograms();

    CaseService getCaseService();

    FileConfiguration getConfig();

    void saveConfig();

    void reloadPluginState();
}

