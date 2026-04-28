package net.recases.runtime.cache;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntUnaryOperator;

public class KeyCache {

    private final Map<UUID, Map<String, CachedAmount>> cachedKeyAmounts = new ConcurrentHashMap<>();

    public Integer getKeyAmount(UUID playerId, String caseName) {
        Map<String, CachedAmount> playerCache = cachedKeyAmounts.get(playerId);
        if (playerCache == null) {
            return null;
        }
        CachedAmount cachedAmount = playerCache.get(normalizeCaseName(caseName));
        return cachedAmount == null ? null : cachedAmount.amount;
    }

    public Integer getKeyAmount(UUID playerId, String caseName, long maxAgeMillis) {
        Map<String, CachedAmount> playerCache = cachedKeyAmounts.get(playerId);
        if (playerCache == null) {
            return null;
        }

        CachedAmount cachedAmount = playerCache.get(normalizeCaseName(caseName));
        if (cachedAmount == null) {
            return null;
        }
        if (maxAgeMillis > 0L && System.currentTimeMillis() - cachedAmount.updatedAt > maxAgeMillis) {
            return null;
        }
        return cachedAmount.amount;
    }

    public void putKeyAmount(UUID playerId, String caseName, int amount) {
        cachedKeyAmounts
                .computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>())
                .put(normalizeCaseName(caseName), new CachedAmount(Math.max(0, amount), System.currentTimeMillis()));
    }

    public void invalidateKeyAmount(UUID playerId, String caseName) {
        Map<String, CachedAmount> playerCache = cachedKeyAmounts.get(playerId);
        if (playerCache == null) {
            return;
        }

        playerCache.remove(normalizeCaseName(caseName));
        if (playerCache.isEmpty()) {
            cachedKeyAmounts.remove(playerId);
        }
    }

    public int updateKeyAmount(UUID playerId, String caseName, int fallback, IntUnaryOperator updater) {
        CachedAmount updated = cachedKeyAmounts
                .computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>())
                .compute(normalizeCaseName(caseName), (key, current) -> new CachedAmount(
                        Math.max(0, updater.applyAsInt(current == null ? fallback : current.amount)),
                        System.currentTimeMillis()
                ));
        return updated.amount;
    }

    public void clear() {
        cachedKeyAmounts.clear();
    }

    private String normalizeCaseName(String caseName) {
        return caseName.toLowerCase();
    }

    private static final class CachedAmount {
        private final int amount;
        private final long updatedAt;

        private CachedAmount(int amount, long updatedAt) {
            this.amount = amount;
            this.updatedAt = updatedAt;
        }
    }
}

