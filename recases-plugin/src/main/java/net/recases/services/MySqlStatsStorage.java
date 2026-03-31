package net.recases.services;

import net.recases.management.CaseItem;
import net.recases.stats.PlayerStatsRecord;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class MySqlStatsStorage {

    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final boolean useSsl;

    MySqlStatsStorage(String host, int port, String database, String username, String password, boolean useSsl) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.useSsl = useSsl;
    }

    void initialize() {
        try (Connection connection = openConnection()) {
            try (PreparedStatement playerStats = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS recases_player_stats (" +
                            "player_id VARCHAR(36) NOT NULL PRIMARY KEY," +
                            "player_name VARCHAR(32) NOT NULL," +
                            "total_opens INT NOT NULL," +
                            "total_rare_wins INT NOT NULL," +
                            "total_guaranteed_wins INT NOT NULL," +
                            "last_reward_name TEXT NOT NULL," +
                            "last_reward_profile VARCHAR(64) NOT NULL" +
                            ")")) {
                playerStats.executeUpdate();
            }

            try (PreparedStatement profileStats = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS recases_profile_stats (" +
                            "player_id VARCHAR(36) NOT NULL," +
                            "profile_id VARCHAR(64) NOT NULL," +
                            "opens INT NOT NULL," +
                            "rare_wins INT NOT NULL," +
                            "guaranteed_wins INT NOT NULL," +
                            "pity INT NOT NULL," +
                            "last_reward_name TEXT NOT NULL," +
                            "PRIMARY KEY (player_id, profile_id)" +
                            ")")) {
                profileStats.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to initialize MySQL stats storage", exception);
        }
    }

    Map<UUID, PlayerStatsRecord> loadAll() {
        Map<UUID, PlayerStatsRecord> result = new LinkedHashMap<>();
        try (Connection connection = openConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT player_id, player_name, total_opens, total_rare_wins, total_guaranteed_wins, last_reward_name, last_reward_profile " +
                            "FROM recases_player_stats");
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    UUID playerId = parseUuid(resultSet.getString("player_id"));
                    if (playerId == null) {
                        continue;
                    }

                    PlayerStatsRecord record = new PlayerStatsRecord(playerId);
                    record.setPlayerName(resultSet.getString("player_name"));
                    record.setTotalOpens(Math.max(0, resultSet.getInt("total_opens")));
                    record.setTotalRareWins(Math.max(0, resultSet.getInt("total_rare_wins")));
                    record.setTotalGuaranteedWins(Math.max(0, resultSet.getInt("total_guaranteed_wins")));
                    record.setLastRewardName(resultSet.getString("last_reward_name"));
                    record.setLastRewardProfile(resultSet.getString("last_reward_profile"));
                    result.put(playerId, record);
                }
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT player_id, profile_id, opens, rare_wins, guaranteed_wins, pity, last_reward_name " +
                            "FROM recases_profile_stats");
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    UUID playerId = parseUuid(resultSet.getString("player_id"));
                    if (playerId == null) {
                        continue;
                    }

                    PlayerStatsRecord record = result.computeIfAbsent(playerId, PlayerStatsRecord::new);
                    PlayerStatsRecord.ProfileStatsRecord profile = record.getOrCreateProfile(resultSet.getString("profile_id"));
                    profile.setOpens(Math.max(0, resultSet.getInt("opens")));
                    profile.setRareWins(Math.max(0, resultSet.getInt("rare_wins")));
                    profile.setGuaranteedWins(Math.max(0, resultSet.getInt("guaranteed_wins")));
                    profile.setPity(Math.max(0, resultSet.getInt("pity")));
                    profile.setLastRewardName(resultSet.getString("last_reward_name"));
                }
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to load MySQL stats", exception);
        }

        return result;
    }

    PlayerStatsRecord loadPlayer(UUID playerId) {
        if (playerId == null) {
            return null;
        }

        PlayerStatsRecord record = null;
        try (Connection connection = openConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT player_name, total_opens, total_rare_wins, total_guaranteed_wins, last_reward_name, last_reward_profile " +
                            "FROM recases_player_stats WHERE player_id = ?")) {
                statement.setString(1, playerId.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        record = new PlayerStatsRecord(playerId);
                        record.setPlayerName(resultSet.getString("player_name"));
                        record.setTotalOpens(Math.max(0, resultSet.getInt("total_opens")));
                        record.setTotalRareWins(Math.max(0, resultSet.getInt("total_rare_wins")));
                        record.setTotalGuaranteedWins(Math.max(0, resultSet.getInt("total_guaranteed_wins")));
                        record.setLastRewardName(resultSet.getString("last_reward_name"));
                        record.setLastRewardProfile(resultSet.getString("last_reward_profile"));
                    }
                }
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT profile_id, opens, rare_wins, guaranteed_wins, pity, last_reward_name " +
                            "FROM recases_profile_stats WHERE player_id = ?")) {
                statement.setString(1, playerId.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        if (record == null) {
                            record = new PlayerStatsRecord(playerId);
                        }

                        PlayerStatsRecord.ProfileStatsRecord profile = record.getOrCreateProfile(resultSet.getString("profile_id"));
                        profile.setOpens(Math.max(0, resultSet.getInt("opens")));
                        profile.setRareWins(Math.max(0, resultSet.getInt("rare_wins")));
                        profile.setGuaranteedWins(Math.max(0, resultSet.getInt("guaranteed_wins")));
                        profile.setPity(Math.max(0, resultSet.getInt("pity")));
                        profile.setLastRewardName(resultSet.getString("last_reward_name"));
                    }
                }
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to load player stats from MySQL", exception);
        }

        return record;
    }

    void recordOpening(Player player, String profileId, CaseItem reward, boolean guaranteed) {
        int rareIncrement = reward.isRare() ? 1 : 0;
        int guaranteedIncrement = guaranteed ? 1 : 0;
        int initialPity = reward.isRare() ? 0 : 1;

        try (Connection connection = openConnection()) {
            try (PreparedStatement playerUpsert = connection.prepareStatement(
                    "INSERT INTO recases_player_stats (player_id, player_name, total_opens, total_rare_wins, total_guaranteed_wins, last_reward_name, last_reward_profile) " +
                            "VALUES (?, ?, 1, ?, ?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE " +
                            "player_name = VALUES(player_name), " +
                            "total_opens = total_opens + 1, " +
                            "total_rare_wins = total_rare_wins + VALUES(total_rare_wins), " +
                            "total_guaranteed_wins = total_guaranteed_wins + VALUES(total_guaranteed_wins), " +
                            "last_reward_name = VALUES(last_reward_name), " +
                            "last_reward_profile = VALUES(last_reward_profile)")) {
                playerUpsert.setString(1, player.getUniqueId().toString());
                playerUpsert.setString(2, player.getName());
                playerUpsert.setInt(3, rareIncrement);
                playerUpsert.setInt(4, guaranteedIncrement);
                playerUpsert.setString(5, reward.getName());
                playerUpsert.setString(6, profileId.toLowerCase());
                playerUpsert.executeUpdate();
            }

            try (PreparedStatement profileUpsert = connection.prepareStatement(
                    "INSERT INTO recases_profile_stats (player_id, profile_id, opens, rare_wins, guaranteed_wins, pity, last_reward_name) " +
                            "VALUES (?, ?, 1, ?, ?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE " +
                            "opens = opens + 1, " +
                            "rare_wins = rare_wins + VALUES(rare_wins), " +
                            "guaranteed_wins = guaranteed_wins + VALUES(guaranteed_wins), " +
                            "pity = CASE WHEN VALUES(rare_wins) > 0 THEN 0 ELSE pity + 1 END, " +
                            "last_reward_name = VALUES(last_reward_name)")) {
                profileUpsert.setString(1, player.getUniqueId().toString());
                profileUpsert.setString(2, profileId.toLowerCase());
                profileUpsert.setInt(3, rareIncrement);
                profileUpsert.setInt(4, guaranteedIncrement);
                profileUpsert.setInt(5, initialPity);
                profileUpsert.setString(6, reward.getName());
                profileUpsert.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to record opening in MySQL stats storage", exception);
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                "jdbc:mysql://" + host + ":" + port + "/" + database
                        + "?useSSL=" + useSsl
                        + "&characterEncoding=utf8"
                        + "&serverTimezone=UTC"
                        + "&autoReconnect=true",
                username,
                password
        );
    }

    private UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
