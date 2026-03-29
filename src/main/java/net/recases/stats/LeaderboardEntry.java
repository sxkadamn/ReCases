package net.recases.stats;

import java.util.UUID;

public class LeaderboardEntry {

    private final UUID playerId;
    private final String playerName;
    private final int value;

    public LeaderboardEntry(UUID playerId, String playerName, int value) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.value = value;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public int getValue() {
        return value;
    }
}

