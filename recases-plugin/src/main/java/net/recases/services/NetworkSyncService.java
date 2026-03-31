package net.recases.services;

import net.recases.app.PluginContext;
import net.recases.runtime.cache.KeyCache;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

public final class NetworkSyncService implements AutoCloseable {

    private final PluginContext plugin;
    private final KeyCache keyCache;
    private final StatsService statsService;
    private BukkitTask refreshTask;

    public NetworkSyncService(PluginContext plugin, KeyCache keyCache, StatsService statsService) {
        this.plugin = plugin;
        this.keyCache = keyCache;
        this.statsService = statsService;
    }

    public void reload() {
        close();
        if (!plugin.getConfig().getBoolean("settings.network-sync.enabled", false)) {
            return;
        }

        if (!"mysql".equalsIgnoreCase(plugin.getConfig().getString("settings.storage.type", "sqlite"))) {
            plugin.getLogger().warning("Network sync requires settings.storage.type: mysql. Sync task was not started.");
            return;
        }

        long intervalTicks = Math.max(20L, plugin.getConfig().getLong("settings.network-sync.poll-interval-seconds", 15L) * 20L);
        refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (plugin.getConfig().getBoolean("settings.network-sync.sync-keys", true)) {
                keyCache.clear();
            }
            if (plugin.getConfig().getBoolean("settings.network-sync.sync-stats", true) && statsService.isUsingMysqlBackend()) {
                statsService.reload();
                if (plugin.isEnabled()) {
                    Bukkit.getScheduler().runTask(plugin, () -> plugin.getLeaderboardHolograms().refreshAll());
                }
            }
        }, intervalTicks, intervalTicks);
    }

    @Override
    public void close() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }
}
