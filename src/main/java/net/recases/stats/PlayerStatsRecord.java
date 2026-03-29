package net.recases.stats;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerStatsRecord {

    private final UUID playerId;
    private final Map<String, ProfileStatsRecord> profileStats = new LinkedHashMap<>();
    private String playerName;
    private int totalOpens;
    private int totalRareWins;
    private int totalGuaranteedWins;
    private String lastRewardName = "";
    private String lastRewardProfile = "";

    public PlayerStatsRecord(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public int getTotalOpens() {
        return totalOpens;
    }

    public void setTotalOpens(int totalOpens) {
        this.totalOpens = totalOpens;
    }

    public int getTotalRareWins() {
        return totalRareWins;
    }

    public void setTotalRareWins(int totalRareWins) {
        this.totalRareWins = totalRareWins;
    }

    public int getTotalGuaranteedWins() {
        return totalGuaranteedWins;
    }

    public void setTotalGuaranteedWins(int totalGuaranteedWins) {
        this.totalGuaranteedWins = totalGuaranteedWins;
    }

    public String getLastRewardName() {
        return lastRewardName;
    }

    public void setLastRewardName(String lastRewardName) {
        this.lastRewardName = lastRewardName;
    }

    public String getLastRewardProfile() {
        return lastRewardProfile;
    }

    public void setLastRewardProfile(String lastRewardProfile) {
        this.lastRewardProfile = lastRewardProfile;
    }

    public Map<String, ProfileStatsRecord> getProfileStats() {
        return profileStats;
    }

    public ProfileStatsRecord getOrCreateProfile(String profileId) {
        String normalized = profileId.toLowerCase();
        ProfileStatsRecord stats = profileStats.get(normalized);
        if (stats == null) {
            stats = new ProfileStatsRecord();
            profileStats.put(normalized, stats);
        }
        return stats;
    }

    public ProfileStatsRecord getProfile(String profileId) {
        return profileStats.get(profileId.toLowerCase());
    }

    public static class ProfileStatsRecord {
        private int opens;
        private int rareWins;
        private int guaranteedWins;
        private int pity;
        private String lastRewardName = "";

        public int getOpens() {
            return opens;
        }

        public void setOpens(int opens) {
            this.opens = opens;
        }

        public int getRareWins() {
            return rareWins;
        }

        public void setRareWins(int rareWins) {
            this.rareWins = rareWins;
        }

        public int getGuaranteedWins() {
            return guaranteedWins;
        }

        public void setGuaranteedWins(int guaranteedWins) {
            this.guaranteedWins = guaranteedWins;
        }

        public int getPity() {
            return pity;
        }

        public void setPity(int pity) {
            this.pity = pity;
        }

        public String getLastRewardName() {
            return lastRewardName;
        }

        public void setLastRewardName(String lastRewardName) {
            this.lastRewardName = lastRewardName;
        }
    }
}

