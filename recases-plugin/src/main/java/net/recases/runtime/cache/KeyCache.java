package net.recases.runtime.cache;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntUnaryOperator;

public class KeyCache {

    private final Map<UUID, Map<String, Integer>> cachedKeyAmounts = new ConcurrentHashMap<>();

    public Integer getKeyAmount(UUID playerId, String caseName) {
        Map<String, Integer> playerCache = cachedKeyAmounts.get(playerId);
        if (playerCache == null) {
            return null;
        }
        return playerCache.get(normalizeCaseName(caseName));
    }

    public void putKeyAmount(UUID playerId, String caseName, int amount) {
        cachedKeyAmounts
                .computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>())
                .put(normalizeCaseName(caseName), Math.max(0, amount));
    }

    public void invalidateKeyAmount(UUID playerId, String caseName) {
        Map<String, Integer> playerCache = cachedKeyAmounts.get(playerId);
        if (playerCache == null) {
            return;
        }

        playerCache.remove(normalizeCaseName(caseName));
        if (playerCache.isEmpty()) {
            cachedKeyAmounts.remove(playerId);
        }
    }

    public int updateKeyAmount(UUID playerId, String caseName, int fallback, IntUnaryOperator updater) {
        return cachedKeyAmounts
                .computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>())
                .compute(normalizeCaseName(caseName), (key, current) -> Math.max(0, updater.applyAsInt(current == null ? fallback : current)));
    }

    public void clear() {
        cachedKeyAmounts.clear();
    }

    private String normalizeCaseName(String caseName) {
        return caseName.toLowerCase();
    }
}

