package net.recases.serialization;

import net.recases.runtime.cache.KeyCache;
import net.recases.services.RedisSyncService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class KeysSerialization implements AutoCloseable {

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final KeyCache cache;
    private final KeyStorage storage;
    private final RedisSyncService redisSyncService;
    private final File legacyYamlFile;
    private final boolean sharedStorageEnabled;
    private final long sharedCacheTtlMillis;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "recases-keys-writer");
        thread.setDaemon(true);
        return thread;
    });

    public KeysSerialization(org.bukkit.plugin.java.JavaPlugin plugin, KeyCache cache, RedisSyncService redisSyncService) {
        this.plugin = plugin;
        this.cache = cache;
        this.redisSyncService = redisSyncService;
        this.legacyYamlFile = new File(plugin.getDataFolder(), "keys.yml");
        this.sharedStorageEnabled = "mysql".equalsIgnoreCase(plugin.getConfig().getString("settings.storage.type", "sqlite"))
                && (plugin.getConfig().getBoolean("settings.network-sync.enabled", false)
                || plugin.getConfig().getBoolean("settings.redis.enabled", false));
        this.sharedCacheTtlMillis = Math.max(0L, plugin.getConfig().getLong("settings.storage.shared-cache-ttl-millis", 2000L));
        this.storage = createStorage(plugin.getConfig());
        this.storage.initialize();
        migrateLegacyYamlIfNeeded(plugin.getConfig());
    }

    public void setCase(OfflinePlayer player, String caseName, int amount) {
        PlayerKey playerKey = PlayerKey.from(player);
        String normalizedCaseName = normalizeCaseName(caseName);
        int normalized = Math.max(0, amount);
        cache.putKeyAmount(playerKey.getUniqueId(), normalizedCaseName, normalized);
        submitWrite(() -> {
            storage.setCaseAmount(playerKey, normalizedCaseName, normalized);
            publishKeyUpdate(playerKey, normalizedCaseName);
        });
    }

    public void addCase(OfflinePlayer player, String caseName, int amount) {
        if (amount <= 0) {
            return;
        }
        changeCaseAmount(player, caseName, amount);
    }

    public int getCaseAmount(OfflinePlayer player, String caseName) {
        PlayerKey playerKey = PlayerKey.from(player);
        String normalizedCaseName = normalizeCaseName(caseName);
        if (sharedStorageEnabled && !isRedisCacheEnabled()) {
            Integer cached = cache.getKeyAmount(playerKey.getUniqueId(), normalizedCaseName, sharedCacheTtlMillis);
            if (cached != null) {
                return cached;
            }
        }

        Integer cached = cache.getKeyAmount(playerKey.getUniqueId(), normalizedCaseName);
        if (cached != null) {
            return cached;
        }

        int amount = storage.getCaseAmount(playerKey, normalizedCaseName);
        cache.putKeyAmount(playerKey.getUniqueId(), normalizedCaseName, amount);
        return amount;
    }

    public void removeCase(OfflinePlayer player, String caseName, int amount) {
        if (amount <= 0) {
            return;
        }
        changeCaseAmount(player, caseName, -amount);
    }

    @Override
    public void close() {
        writer.shutdown();
        try {
            writer.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        storage.close();
    }

    private void changeCaseAmount(OfflinePlayer player, String caseName, int delta) {
        PlayerKey playerKey = PlayerKey.from(player);
        String normalizedCaseName = normalizeCaseName(caseName);
        int current = resolveCurrentAmount(playerKey, normalizedCaseName);
        int updated = Math.max(0, current + delta);
        cache.putKeyAmount(playerKey.getUniqueId(), normalizedCaseName, updated);
        submitWrite(() -> {
            int remote = storage.changeCaseAmount(playerKey, normalizedCaseName, delta);
            cache.putKeyAmount(playerKey.getUniqueId(), normalizedCaseName, remote);
            publishKeyUpdate(playerKey, normalizedCaseName);
        });
    }

    private int resolveCurrentAmount(PlayerKey playerKey, String caseName) {
        Integer cached = cache.getKeyAmount(playerKey.getUniqueId(), caseName);
        if (cached != null) {
            return cached;
        }

        int amount = storage.getCaseAmount(playerKey, caseName);
        cache.putKeyAmount(playerKey.getUniqueId(), caseName, amount);
        return amount;
    }

    private void submitWrite(Runnable action) {
        if (!writer.isShutdown()) {
            writer.submit(action);
            return;
        }
        action.run();
    }

    private KeyStorage createStorage(FileConfiguration config) {
        StorageType type = StorageType.fromConfig(config.getString("settings.storage.type", "sqlite"));
        try {
            return createConfiguredStorage(type, config);
        } catch (RuntimeException exception) {
            if (type != StorageType.MYSQL) {
                throw exception;
            }

            plugin.getLogger().warning(config.getString("console.storage-fallback", "MySQL is unavailable. Falling back to SQLite."));
            return createConfiguredStorage(StorageType.SQLITE, config);
        }
    }

    private KeyStorage createConfiguredStorage(StorageType type, FileConfiguration config) {
        switch (type) {
            case MYSQL:
                return new MySqlKeyStorage(
                        config.getString("settings.storage.mysql.host", "127.0.0.1"),
                        config.getInt("settings.storage.mysql.port", 3306),
                        config.getString("settings.storage.mysql.database", "recases"),
                        config.getString("settings.storage.mysql.username", "root"),
                        config.getString("settings.storage.mysql.password", ""),
                        config.getBoolean("settings.storage.mysql.use-ssl", false)
                );
            case SQLITE:
            default:
                return new SqliteKeyStorage(new File(plugin.getDataFolder(), config.getString("settings.storage.sqlite.file", "keys.db")));
        }
    }

    private void migrateLegacyYamlIfNeeded(FileConfiguration config) {
        if (!config.getBoolean("settings.storage.migrate-from-yaml", true) || !legacyYamlFile.exists() || !storage.isEmpty()) {
            return;
        }

        FileConfiguration legacy = YamlConfiguration.loadConfiguration(legacyYamlFile);
        if (legacy.getConfigurationSection("keys") == null) {
            return;
        }

        for (String playerName : legacy.getConfigurationSection("keys").getKeys(false)) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
            if (legacy.getConfigurationSection("keys." + playerName) == null) {
                continue;
            }

            for (String caseName : legacy.getConfigurationSection("keys." + playerName).getKeys(false)) {
                int amount = Math.max(0, legacy.getInt("keys." + playerName + "." + caseName, 0));
                if (amount > 0) {
                    setCase(player, caseName, amount);
                }
            }
        }

        File backup = new File(plugin.getDataFolder(), "keys.yml.bak");
        if (backup.exists()) {
            backup.delete();
        }
        legacyYamlFile.renameTo(backup);
        plugin.getLogger().info(config.getString("console.yaml-migrated", "Migrated keys.yml to %storage% storage.")
                .replace("%storage%", config.getString("settings.storage.type", "sqlite")));
    }

    private String normalizeCaseName(String caseName) {
        return caseName == null ? "" : caseName.toLowerCase();
    }

    private boolean isRedisCacheEnabled() {
        return redisSyncService != null && redisSyncService.supportsSharedData();
    }

    private void publishKeyUpdate(PlayerKey playerKey, String caseName) {
        if (redisSyncService == null) {
            return;
        }
        redisSyncService.publishKeySync(playerKey.getUniqueId(), caseName);
    }
}

