package net.recases.services;

import net.recases.app.PluginContext;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromoCodeServiceTest {

    @Test
    void allowsOnlyOneSuccessfulRedeemWhenMaxUsesIsOne() throws Exception {
        Path dataFolder = Files.createTempDirectory("recases-promocode-test");
        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.storage.type", "sqlite");
        config.set("settings.storage.sqlite.file", "promo-test.db");

        CaseService caseService = mock(CaseService.class);
        StorageService storageService = mock(StorageService.class);
        when(caseService.hasProfile("alpha")).thenReturn(true);

        PluginContext plugin = proxyPlugin(dataFolder, config, caseService, storageService);
        PromoCodeService service = new PromoCodeService(plugin);
        service.initialize();
        assertTrue(service.createCode("ONE", "alpha", 2, 1, "tester"));

        OfflinePlayer first = offlinePlayer("First");
        OfflinePlayer second = offlinePlayer("Second");

        AtomicInteger successCount = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        Runnable redeemFirst = () -> redeem(service, first, start, done, successCount);
        Runnable redeemSecond = () -> redeem(service, second, start, done, successCount);

        Thread one = new Thread(redeemFirst, "promo-redeem-1");
        Thread two = new Thread(redeemSecond, "promo-redeem-2");
        one.start();
        two.start();
        start.countDown();
        done.await();

        assertEquals(1, successCount.get());
        verify(storageService, times(1)).addCase(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("alpha"), org.mockito.ArgumentMatchers.eq(2));
        assertEquals(1, service.getCode("ONE").usedCount());
    }

    private static void redeem(PromoCodeService service, OfflinePlayer player, CountDownLatch start, CountDownLatch done, AtomicInteger successCount) {
        try {
            start.await();
            if (service.redeem(player, "ONE").success()) {
                successCount.incrementAndGet();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            done.countDown();
        }
    }

    private static OfflinePlayer offlinePlayer(String name) {
        OfflinePlayer player = mock(OfflinePlayer.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn(name);
        return player;
    }

    private static PluginContext proxyPlugin(Path dataFolder, YamlConfiguration config, CaseService caseService, StorageService storageService) {
        return (PluginContext) Proxy.newProxyInstance(
                PluginContext.class.getClassLoader(),
                new Class[]{PluginContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getConfig" -> config;
                    case "getDataFolder" -> dataFolder.toFile();
                    case "getCaseService" -> caseService;
                    case "getStorage" -> storageService;
                    case "getLogger" -> java.util.logging.Logger.getLogger("PromoCodeServiceTest");
                    case "isEnabled" -> true;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "PluginContextProxy";
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == double.class) {
            return 0D;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }
}
