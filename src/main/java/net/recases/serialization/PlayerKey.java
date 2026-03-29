package net.recases.serialization;

import org.bukkit.OfflinePlayer;

import java.util.UUID;

public class PlayerKey {

    private final UUID uniqueId;
    private final String playerName;

    public PlayerKey(UUID uniqueId, String playerName) {
        this.uniqueId = uniqueId;
        this.playerName = playerName;
    }

    public static PlayerKey from(OfflinePlayer player) {
        return new PlayerKey(player.getUniqueId(), player.getName() == null ? "unknown" : player.getName());
    }

    public UUID getUniqueId() {
        return uniqueId;
    }

    public String getPlayerName() {
        return playerName;
    }
}

