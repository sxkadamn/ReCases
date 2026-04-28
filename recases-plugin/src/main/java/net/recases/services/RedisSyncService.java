package net.recases.services;

import net.recases.app.PluginContext;
import net.recases.runtime.cache.KeyCache;
import org.bukkit.Bukkit;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.params.SetParams;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class RedisSyncService implements AutoCloseable {

    private static final String KEY_CHANNEL = "keys";
    private static final String STATS_CHANNEL = "stats";
    private static final String RELEASE_LOCK_SCRIPT = "if redis.call('get', KEYS[1]) == ARGV[1] then "
            + "return redis.call('del', KEYS[1]) else return 0 end";

    private final PluginContext plugin;
    private final KeyCache keyCache;
    private final StatsService statsService;
    private JedisPool jedisPool;
    private Thread subscriberThread;
    private volatile boolean running;
    private volatile JedisPubSub subscriber;

    public RedisSyncService(PluginContext plugin, KeyCache keyCache, StatsService statsService) {
        this.plugin = plugin;
        this.keyCache = keyCache;
        this.statsService = statsService;
    }

    public synchronized void reload() {
        close();
        if (!plugin.getConfig().getBoolean("settings.redis.enabled", false)) {
            return;
        }

        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(8);
            poolConfig.setMaxIdle(4);
            poolConfig.setMinIdle(0);
            poolConfig.setTestOnBorrow(true);

            String host = plugin.getConfig().getString("settings.redis.host", "127.0.0.1");
            int port = plugin.getConfig().getInt("settings.redis.port", 6379);
            int timeoutMillis = Math.max(250, plugin.getConfig().getInt("settings.redis.timeout-millis", 2000));
            String password = plugin.getConfig().getString("settings.redis.password", "");
            int database = Math.max(0, plugin.getConfig().getInt("settings.redis.database", 0));

            if (password == null || password.trim().isEmpty()) {
                jedisPool = new JedisPool(poolConfig, host, port, timeoutMillis, null, database);
            } else {
                jedisPool = new JedisPool(poolConfig, host, port, timeoutMillis, password, database);
            }

            try (Jedis jedis = jedisPool.getResource()) {
                jedis.ping();
            }

            running = true;
            startSubscriber();
            plugin.getLogger().info("Redis sync is enabled.");
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Failed to initialize Redis sync: " + exception.getMessage());
            close();
        }
    }

    public boolean isEnabled() {
        return jedisPool != null;
    }

    public boolean supportsSharedData() {
        return isEnabled() && "mysql".equalsIgnoreCase(plugin.getConfig().getString("settings.storage.type", "sqlite"));
    }

    public void publishKeySync(UUID playerId, String caseName) {
        if (!supportsSharedData() || playerId == null || caseName == null
                || !plugin.getConfig().getBoolean("settings.redis.publish-keys", true)) {
            return;
        }
        publish(KEY_CHANNEL, resolveServerId() + "|" + playerId + "|" + caseName.toLowerCase(Locale.ROOT));
    }

    public void publishStatsSync(UUID playerId) {
        if (!supportsSharedData() || playerId == null
                || !plugin.getConfig().getBoolean("settings.redis.publish-stats", true)) {
            return;
        }
        publish(STATS_CHANNEL, resolveServerId() + "|" + playerId);
    }

    public String tryAcquireOpeningLock(UUID playerId) {
        if (!isEnabled() || playerId == null) {
            return "";
        }

        String token = UUID.randomUUID().toString();
        int ttlSeconds = Math.max(30, plugin.getConfig().getInt("settings.redis.lock-ttl-seconds", 180));
        try (Jedis jedis = jedisPool.getResource()) {
            String response = jedis.set(lockKey(playerId), token, SetParams.setParams().nx().ex(ttlSeconds));
            return "OK".equalsIgnoreCase(response) ? token : null;
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Failed to acquire Redis opening lock: " + exception.getMessage());
            return null;
        }
    }

    public void releaseOpeningLock(UUID playerId, String token) {
        if (!isEnabled() || playerId == null || token == null || token.isEmpty()) {
            return;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.eval(RELEASE_LOCK_SCRIPT, List.of(lockKey(playerId)), List.of(token));
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Failed to release Redis opening lock: " + exception.getMessage());
        }
    }

    @Override
    public synchronized void close() {
        running = false;
        if (subscriber != null) {
            try {
                subscriber.unsubscribe();
            } catch (RuntimeException ignored) {
            }
            subscriber = null;
        }
        if (subscriberThread != null) {
            subscriberThread.interrupt();
            subscriberThread = null;
        }
        if (jedisPool != null) {
            jedisPool.close();
            jedisPool = null;
        }
    }

    private void publish(String channelSuffix, String payload) {
        if (!isEnabled()) {
            return;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.publish(channel(channelSuffix), payload);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Failed to publish Redis sync message: " + exception.getMessage());
        }
    }

    private void startSubscriber() {
        subscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                handleMessage(channel, message);
            }
        };

        subscriberThread = new Thread(this::subscribeLoop, "recases-redis-subscriber");
        subscriberThread.setDaemon(true);
        subscriberThread.start();
    }

    private void subscribeLoop() {
        long reconnectMillis = Duration.ofSeconds(Math.max(1L, plugin.getConfig().getLong("settings.redis.subscriber-reconnect-seconds", 5L))).toMillis();
        while (running && jedisPool != null) {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.subscribe(subscriber, channel(KEY_CHANNEL), channel(STATS_CHANNEL));
            } catch (RuntimeException exception) {
                if (running) {
                    plugin.getLogger().warning("Redis subscriber disconnected: " + exception.getMessage());
                }
            }

            if (!running) {
                return;
            }

            try {
                Thread.sleep(reconnectMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void handleMessage(String channel, String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        String[] parts = message.split("\\|");
        if (parts.length < 2 || resolveServerId().equalsIgnoreCase(parts[0])) {
            return;
        }

        if (channel(KEY_CHANNEL).equals(channel)) {
            handleKeyMessage(parts);
            return;
        }
        if (channel(STATS_CHANNEL).equals(channel)) {
            handleStatsMessage(parts);
        }
    }

    private void handleKeyMessage(String[] parts) {
        if (parts.length < 3) {
            return;
        }

        try {
            UUID playerId = UUID.fromString(parts[1]);
            String caseName = parts[2].trim().toLowerCase(Locale.ROOT);
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> keyCache.invalidateKeyAmount(playerId, caseName));
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void handleStatsMessage(String[] parts) {
        try {
            UUID playerId = UUID.fromString(parts[1]);
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    statsService.invalidateRemotePlayer(playerId);
                    plugin.getLeaderboardHolograms().requestRefresh();
                });
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    private String channel(String suffix) {
        String prefix = plugin.getConfig().getString("settings.redis.channel-prefix", "recases").trim();
        if (prefix.isEmpty()) {
            prefix = "recases";
        }
        return prefix + ":" + suffix;
    }

    private String lockKey(UUID playerId) {
        return channel("lock:opening") + ":" + playerId;
    }

    private String resolveServerId() {
        String serverId = plugin.getConfig().getString("settings.server-id", "default").trim();
        return serverId.isEmpty() ? "default" : serverId;
    }
}
