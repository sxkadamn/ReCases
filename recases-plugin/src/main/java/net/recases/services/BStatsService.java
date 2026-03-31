package net.recases.services;

import net.recases.app.PluginContext;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.MultiLineChart;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class BStatsService implements AutoCloseable {

    private final PluginContext plugin;
    private volatile String storageType = "sqlite";
    private volatile String defaultLocale = "ru";
    private volatile String networkSyncState = "disabled";
    private volatile String discordWebhookState = "disabled";
    private volatile int configuredProfiles;
    private volatile int configuredInstances;

    private Metrics metrics;
    private int activePluginId = -1;

    public BStatsService(PluginContext plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        refreshSnapshot();

        boolean enabled = plugin.getConfig().getBoolean("settings.metrics.bstats.enabled", false);
        int pluginId = plugin.getConfig().getInt("settings.metrics.bstats.plugin-id", 0);
        if (!enabled) {
            shutdownMetrics();
            return;
        }

        if (pluginId <= 0) {
            shutdownMetrics();
            plugin.getLogger().warning("bStats is enabled but settings.metrics.bstats.plugin-id is not set.");
            return;
        }

        if (metrics != null && activePluginId == pluginId) {
            return;
        }

        shutdownMetrics();
        metrics = new Metrics(plugin, pluginId);
        activePluginId = pluginId;
        registerCharts(metrics);
        plugin.getLogger().info("bStats metrics enabled with plugin id " + pluginId + ".");
    }

    @Override
    public void close() {
        shutdownMetrics();
    }

    private void refreshSnapshot() {
        storageType = normalize(plugin.getConfig().getString("settings.storage.type", "sqlite"), "sqlite");
        defaultLocale = normalize(plugin.getConfig().getString("settings.locale.default", "ru"), "ru");
        networkSyncState = plugin.getConfig().getBoolean("settings.network-sync.enabled", false) ? "enabled" : "disabled";
        discordWebhookState = plugin.getConfig().getBoolean("settings.webhooks.discord.enabled", false) ? "enabled" : "disabled";
        configuredProfiles = countChildren(plugin.getConfig().getConfigurationSection("profiles"));
        configuredInstances = countChildren(plugin.getConfig().getConfigurationSection("cases.instances"));
    }

    private void registerCharts(Metrics metrics) {
        metrics.addCustomChart(new SimplePie("storage_backend", () -> storageType));
        metrics.addCustomChart(new SimplePie("default_locale", () -> defaultLocale));
        metrics.addCustomChart(new SimplePie("network_sync", () -> networkSyncState));
        metrics.addCustomChart(new SimplePie("discord_webhook", () -> discordWebhookState));
        metrics.addCustomChart(new SingleLineChart("configured_profiles", () -> configuredProfiles));
        metrics.addCustomChart(new SingleLineChart("configured_instances", () -> configuredInstances));
        metrics.addCustomChart(new SingleLineChart("tracked_players", () -> plugin.getStats().getTrackedPlayersCount()));
        metrics.addCustomChart(new MultiLineChart("opening_totals", () -> {
            Map<String, Integer> values = new LinkedHashMap<>();
            values.put("opens", plugin.getStats().getGlobalOpens());
            values.put("rare_wins", plugin.getStats().getGlobalRareWins());
            values.put("guaranteed_wins", plugin.getStats().getGlobalGuaranteedWins());
            return values;
        }));
    }

    private void shutdownMetrics() {
        if (metrics != null) {
            metrics.shutdown();
            metrics = null;
        }
        activePluginId = -1;
    }

    private int countChildren(ConfigurationSection section) {
        return section == null ? 0 : section.getKeys(false).size();
    }

    private String normalize(String value, String fallback) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? fallback : normalized;
    }
}
