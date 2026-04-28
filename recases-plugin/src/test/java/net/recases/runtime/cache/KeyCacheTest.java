package net.recases.runtime.cache;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KeyCacheTest {

    @Test
    void returnsNullWhenEntryExpires() throws Exception {
        KeyCache cache = new KeyCache();
        UUID playerId = UUID.randomUUID();

        cache.putKeyAmount(playerId, "alpha", 5);

        assertEquals(5, cache.getKeyAmount(playerId, "alpha", 1000L));
        Thread.sleep(20L);
        assertNull(cache.getKeyAmount(playerId, "alpha", 1L));
    }
}
