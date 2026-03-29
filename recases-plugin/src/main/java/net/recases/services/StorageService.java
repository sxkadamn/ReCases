package net.recases.services;

import net.recases.serialization.KeysSerialization;
import net.recases.runtime.cache.KeyCache;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

public class StorageService implements AutoCloseable {

    private final JavaPlugin plugin;
    private final KeyCache cache;
    private KeysSerialization keys;

    public StorageService(JavaPlugin plugin, KeyCache cache) {
        this.plugin = plugin;
        this.cache = cache;
    }

    public void reload() {
        cache.clear();
        if (keys != null) {
            keys.close();
        }
        keys = new KeysSerialization(plugin, cache);
    }

    public int getCaseAmount(OfflinePlayer player, String caseName) {
        return keys.getCaseAmount(player, caseName);
    }

    public void addCase(OfflinePlayer player, String caseName, int amount) {
        keys.addCase(player, caseName, amount);
    }

    public void removeCase(OfflinePlayer player, String caseName, int amount) {
        keys.removeCase(player, caseName, amount);
    }

    public void setCase(OfflinePlayer player, String caseName, int amount) {
        keys.setCase(player, caseName, amount);
    }

    @Override
    public void close() {
        if (keys != null) {
            keys.close();
        }
    }
}

