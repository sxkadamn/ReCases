package net.recases.services;

import net.recases.management.CaseItem;
import net.recases.stats.PlayerStatsRecord;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
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
                            "opens_today INT NOT NULL DEFAULT 0," +
                            "total_rare_wins INT NOT NULL," +
                            "total_guaranteed_wins INT NOT NULL," +
                            "daily_date VARCHAR(16) NOT NULL DEFAULT ''," +
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
                            "opens_today INT NOT NULL DEFAULT 0," +
                            "daily_date VARCHAR(16) NOT NULL DEFAULT ''," +
                            "rare_wins INT NOT NULL," +
                            "guaranteed_wins INT NOT NULL," +
                            "pity INT NOT NULL," +
                            "last_reward_name TEXT NOT NULL," +
                            "PRIMARY KEY (player_id, profile_id)" +
                            ")")) {
                profileStats.executeUpdate();
            }

            ensureColumn(connection, "ALTER TABLE recases_player_stats ADD COLUMN opens_today INT NOT NULL DEFAULT 0");
            ensureColumn(connection, "ALTER TABLE recases_player_stats ADD COLUMN daily_date VARCHAR(16) NOT NULL DEFAULT ''");
            ensureColumn(connection, "ALTER TABLE recases_profile_stats ADD COLUMN opens_today INT NOT NULL DEFAULT 0");
            ensureColumn(connection, "ALTER TABLE recases_profile_stats ADD COLUMN daily_date VARCHAR(16) NOT NULL DEFAULT ''");
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to initialize MySQL stats storage", exception);
        }
    }

    Map<UUID, PlayerStatsRecord> loadAll() {
        Map<UUID, PlayerStatsRecord> result = new LinkedHashMap<>();
        try (Connection connection = openConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT player_id, player_name, total_opens, opens_today, total_rare_wins, total_guaranteed_wins, daily_date, last_reward_name, last_reward_profile " +
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
                    record.setTotalOpensToday(Math.max(0, resultSet.getInt("opens_today")));
                    record.setTotalRareWins(Math.max(0, resultSet.getInt("total_rare_wins")));
                    record.setTotalGuaranteedWins(Math.max(0, resultSet.getInt("total_guaranteed_wins")));
                    record.setDailyDate(resultSet.getString("daily_date"));
                    record.setLastRewardName(resultSet.getString("last_reward_name"));
                    record.setLastRewardProfile(resultSet.getString("last_reward_profile"));
                    result.put(playerId, record);
                }
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT player_id, profile_id, opens, opens_today, rare_wins, guaranteed_wins, pity, last_reward_name " +
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
                    profile.setOpensToday(Math.max(0, resultSet.getInt("opens_today")));
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
                    "SELECT player_name, total_opens, opens_today, total_rare_wins, total_guaranteed_wins, daily_date, last_reward_name, last_reward_profile " +
                            "FROM recases_player_stats WHERE player_id = ?")) {
                statement.setString(1, playerId.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        record = new PlayerStatsRecord(playerId);
                        record.setPlayerName(resultSet.getString("player_name"));
                        record.setTotalOpens(Math.max(0, resultSet.getInt("total_opens")));
                        record.setTotalOpensToday(Math.max(0, resultSet.getInt("opens_today")));
                        record.setTotalRareWins(Math.max(0, resultSet.getInt("total_rare_wins")));
                        record.setTotalGuaranteedWins(Math.max(0, resultSet.getInt("total_guaranteed_wins")));
                        record.setDailyDate(resultSet.getString("daily_date"));
                        record.setLastRewardName(resultSet.getString("last_reward_name"));
                        record.setLastRewardProfile(resultSet.getString("last_reward_profile"));
                    }
                }
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT profile_id, opens, opens_today, rare_wins, guaranteed_wins, pity, last_reward_name " +
                            "FROM recases_profile_stats WHERE player_id = ?")) {
                statement.setString(1, playerId.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        if (record == null) {
                            record = new PlayerStatsRecord(playerId);
                        }

                        PlayerStatsRecord.ProfileStatsRecord profile = record.getOrCreateProfile(resultSet.getString("profile_id"));
                        profile.setOpens(Math.max(0, resultSet.getInt("opens")));
                        profile.setOpensToday(Math.max(0, resultSet.getInt("opens_today")));
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
        String currentDate = LocalDate.now(ZoneId.systemDefault()).toString();

        try (Connection connection = openConnection()) {
            try (PreparedStatement playerUpsert = connection.prepareStatement(
                    "INSERT INTO recases_player_stats (player_id, player_name, total_opens, opens_today, total_rare_wins, total_guaranteed_wins, daily_date, last_reward_name, last_reward_profile) " +
                            "VALUES (?, ?, 1, 1, ?, ?, ?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE " +
                            "player_name = VALUES(player_name), " +
                            "total_opens = total_opens + 1, " +
                            "opens_today = CASE WHEN daily_date = VALUES(daily_date) THEN opens_today + 1 ELSE VALUES(opens_today) END, " +
                            "total_rare_wins = total_rare_wins + VALUES(total_rare_wins), " +
                            "total_guaranteed_wins = total_guaranteed_wins + VALUES(total_guaranteed_wins), " +
                            "daily_date = VALUES(daily_date), " +
                            "last_reward_name = VALUES(last_reward_name), " +
                            "last_reward_profile = VALUES(last_reward_profile)")) {
                playerUpsert.setString(1, player.getUniqueId().toString());
                playerUpsert.setString(2, player.getName());
                playerUpsert.setInt(3, rareIncrement);
                playerUpsert.setInt(4, guaranteedIncrement);
                playerUpsert.setString(5, currentDate);
                playerUpsert.setString(6, reward.getName());
                playerUpsert.setString(7, profileId.toLowerCase());
                playerUpsert.executeUpdate();
            }

            try (PreparedStatement profileUpsert = connection.prepareStatement(
                    "INSERT INTO recases_profile_stats (player_id, profile_id, opens, opens_today, daily_date, rare_wins, guaranteed_wins, pity, last_reward_name) " +
                            "VALUES (?, ?, 1, 1, ?, ?, ?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE " +
                            "opens = opens + 1, " +
                            "opens_today = CASE WHEN daily_date = VALUES(daily_date) THEN opens_today + 1 ELSE VALUES(opens_today) END, " +
                            "daily_date = VALUES(daily_date), " +
                            "rare_wins = rare_wins + VALUES(rare_wins), " +
                            "guaranteed_wins = guaranteed_wins + VALUES(guaranteed_wins), " +
                            "pity = CASE WHEN VALUES(rare_wins) > 0 THEN 0 ELSE pity + 1 END, " +
                            "last_reward_name = VALUES(last_reward_name)")) {
                profileUpsert.setString(1, player.getUniqueId().toString());
                profileUpsert.setString(2, profileId.toLowerCase());
                profileUpsert.setString(3, currentDate);
                profileUpsert.setInt(4, rareIncrement);
                profileUpsert.setInt(5, guaranteedIncrement);
                profileUpsert.setInt(6, initialPity);
                profileUpsert.setString(7, reward.getName());
                profileUpsert.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to record opening in MySQL stats storage", exception);
        }
    }

    void replacePlayerRecord(PlayerStatsRecord record) {
        if (record == null) {
            return;
        }

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                deletePlayerInternal(connection, record.getPlayerId());
                if (record.getTotalOpens() > 0 || !record.getProfileStats().isEmpty()) {
                    try (PreparedStatement playerInsert = connection.prepareStatement(
                            "INSERT INTO recases_player_stats (player_id, player_name, total_opens, opens_today, total_rare_wins, total_guaranteed_wins, daily_date, last_reward_name, last_reward_profile) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                        playerInsert.setString(1, record.getPlayerId().toString());
                        playerInsert.setString(2, record.getPlayerName());
                        playerInsert.setInt(3, record.getTotalOpens());
                        playerInsert.setInt(4, record.getTotalOpensToday());
                        playerInsert.setInt(5, record.getTotalRareWins());
                        playerInsert.setInt(6, record.getTotalGuaranteedWins());
                        playerInsert.setString(7, record.getDailyDate());
                        playerInsert.setString(8, record.getLastRewardName());
                        playerInsert.setString(9, record.getLastRewardProfile());
                        playerInsert.executeUpdate();
                    }

                    try (PreparedStatement profileInsert = connection.prepareStatement(
                            "INSERT INTO recases_profile_stats (player_id, profile_id, opens, opens_today, daily_date, rare_wins, guaranteed_wins, pity, last_reward_name) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                        for (Map.Entry<String, PlayerStatsRecord.ProfileStatsRecord> entry : record.getProfileStats().entrySet()) {
                            PlayerStatsRecord.ProfileStatsRecord profile = entry.getValue();
                            profileInsert.setString(1, record.getPlayerId().toString());
                            profileInsert.setString(2, entry.getKey());
                            profileInsert.setInt(3, profile.getOpens());
                            profileInsert.setInt(4, profile.getOpensToday());
                            profileInsert.setString(5, record.getDailyDate());
                            profileInsert.setInt(6, profile.getRareWins());
                            profileInsert.setInt(7, profile.getGuaranteedWins());
                            profileInsert.setInt(8, profile.getPity());
                            profileInsert.setString(9, profile.getLastRewardName());
                            profileInsert.addBatch();
                        }
                        profileInsert.executeBatch();
                    }
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to replace player stats in MySQL", exception);
        }
    }

    void deletePlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }

        try (Connection connection = openConnection()) {
            deletePlayerInternal(connection, playerId);
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to delete player stats from MySQL", exception);
        }
    }

    private void ensureColumn(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException exception) {
            String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase();
            if (message.contains("duplicate")
                    || message.contains("exists")
                    || message.contains("already exists")) {
                return;
            }
            throw exception;
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

    private void deletePlayerInternal(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement deleteProfiles = connection.prepareStatement("DELETE FROM recases_profile_stats WHERE player_id = ?")) {
            deleteProfiles.setString(1, playerId.toString());
            deleteProfiles.executeUpdate();
        }
        try (PreparedStatement deletePlayer = connection.prepareStatement("DELETE FROM recases_player_stats WHERE player_id = ?")) {
            deletePlayer.setString(1, playerId.toString());
            deletePlayer.executeUpdate();
        }
    }

    private UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
