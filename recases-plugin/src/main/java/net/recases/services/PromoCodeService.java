package net.recases.services;

import net.recases.app.PluginContext;
import org.bukkit.OfflinePlayer;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class PromoCodeService implements AutoCloseable {

    private final PluginContext plugin;

    public PromoCodeService(PluginContext plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        initialize();
    }

    public void initialize() {
        try (Connection connection = openConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(createCodesTableSql())) {
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(createUsesTableSql())) {
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to initialize promo code storage", exception);
        }
    }

    public boolean createCode(String rawCode, String profileId, int amount, int maxUses, String createdBy) {
        String code = normalizeCode(rawCode);
        String normalizedProfile = profileId == null ? "" : profileId.trim().toLowerCase(Locale.ROOT);
        if (code.isEmpty() || normalizedProfile.isEmpty() || amount <= 0 || maxUses <= 0 || !plugin.getCaseService().hasProfile(normalizedProfile)) {
            return false;
        }

        String sql = isMysql()
                ? "INSERT INTO recases_promo_codes (code, profile_id, amount, max_uses, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE code = code"
                : "INSERT OR IGNORE INTO recases_promo_codes (code, profile_id, amount, max_uses, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            statement.setString(2, normalizedProfile);
            statement.setInt(3, amount);
            statement.setInt(4, maxUses);
            statement.setString(5, createdBy == null ? "" : createdBy);
            statement.setLong(6, System.currentTimeMillis());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to create promo code", exception);
        }
    }

    public boolean deleteCode(String rawCode) {
        String code = normalizeCode(rawCode);
        if (code.isEmpty()) {
            return false;
        }

        try (Connection connection = openConnection()) {
            try (PreparedStatement uses = connection.prepareStatement("DELETE FROM recases_promo_code_uses WHERE code = ?")) {
                uses.setString(1, code);
                uses.executeUpdate();
            }
            try (PreparedStatement codes = connection.prepareStatement("DELETE FROM recases_promo_codes WHERE code = ?")) {
                codes.setString(1, code);
                return codes.executeUpdate() > 0;
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to delete promo code", exception);
        }
    }

    public RedemptionResult redeem(OfflinePlayer player, String rawCode) {
        if (player == null) {
            return RedemptionResult.failed("Player not found.");
        }

        String code = normalizeCode(rawCode);
        if (code.isEmpty()) {
            return RedemptionResult.failed("Promo code is empty.");
        }

        PromoCodeEntry redeemedEntry;
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                PromoCodeEntry entry = getCode(connection, code, true);
                if (entry == null) {
                    connection.rollback();
                    return RedemptionResult.failed("Promo code was not found.");
                }
                if (!plugin.getCaseService().hasProfile(entry.profileId())) {
                    connection.rollback();
                    return RedemptionResult.failed("Promo code profile no longer exists.");
                }
                if (entry.usedCount() >= entry.maxUses()) {
                    connection.rollback();
                    return RedemptionResult.failed("Promo code usage limit has been reached.");
                }
                if (hasUsed(connection, code, player.getUniqueId())) {
                    connection.rollback();
                    return RedemptionResult.failed("You have already redeemed this promo code.");
                }

                try (PreparedStatement statement = connection.prepareStatement(insertUseSql())) {
                    statement.setString(1, code);
                    statement.setString(2, player.getUniqueId().toString());
                    statement.setString(3, player.getName() == null ? player.getUniqueId().toString() : player.getName());
                    statement.setLong(4, System.currentTimeMillis());
                    if (statement.executeUpdate() <= 0) {
                        connection.rollback();
                        return RedemptionResult.failed("Promo code was already redeemed.");
                    }
                }

                connection.commit();
                redeemedEntry = entry;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to redeem promo code", exception);
        }

        plugin.getStorage().addCase(player, redeemedEntry.profileId(), redeemedEntry.amount());
        return RedemptionResult.success(code, redeemedEntry.profileId(), redeemedEntry.amount());
    }

    public List<PromoCodeEntry> listCodes(int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, 50));
        String sql = "SELECT c.code, c.profile_id, c.amount, c.max_uses, c.created_by, c.created_at, COUNT(u.player_id) AS used_count " +
                "FROM recases_promo_codes c " +
                "LEFT JOIN recases_promo_code_uses u ON u.code = c.code " +
                "GROUP BY c.code, c.profile_id, c.amount, c.max_uses, c.created_by, c.created_at " +
                "ORDER BY c.created_at DESC LIMIT ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, normalizedLimit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<PromoCodeEntry> entries = new ArrayList<>();
                while (resultSet.next()) {
                    entries.add(mapCode(resultSet));
                }
                return entries;
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to list promo codes", exception);
        }
    }

    public PromoCodeEntry getCode(String rawCode) {
        String code = normalizeCode(rawCode);
        if (code.isEmpty()) {
            return null;
        }

        try (Connection connection = openConnection()) {
            return getCode(connection, code, false);
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to load promo code", exception);
        }
    }

    @Override
    public void close() {
    }

    private boolean hasUsed(Connection connection, String code, UUID playerId) throws SQLException {
        String sql = "SELECT 1 FROM recases_promo_code_uses WHERE code = ? AND player_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            statement.setString(2, playerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private PromoCodeEntry getCode(Connection connection, String code, boolean lockForUpdate) throws SQLException {
        String sql = "SELECT c.code, c.profile_id, c.amount, c.max_uses, c.created_by, c.created_at, COUNT(u.player_id) AS used_count " +
                "FROM recases_promo_codes c " +
                "LEFT JOIN recases_promo_code_uses u ON u.code = c.code " +
                "WHERE c.code = ? " +
                "GROUP BY c.code, c.profile_id, c.amount, c.max_uses, c.created_by, c.created_at" +
                (lockForUpdate && isMysql() ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapCode(resultSet) : null;
            }
        }
    }

    private PromoCodeEntry mapCode(ResultSet resultSet) throws SQLException {
        return new PromoCodeEntry(
                resultSet.getString("code"),
                resultSet.getString("profile_id"),
                Math.max(1, resultSet.getInt("amount")),
                Math.max(1, resultSet.getInt("max_uses")),
                Math.max(0, resultSet.getInt("used_count")),
                resultSet.getString("created_by"),
                resultSet.getLong("created_at")
        );
    }

    private String insertUseSql() {
        return isMysql()
                ? "INSERT IGNORE INTO recases_promo_code_uses (code, player_id, player_name, used_at) VALUES (?, ?, ?, ?)"
                : "INSERT OR IGNORE INTO recases_promo_code_uses (code, player_id, player_name, used_at) VALUES (?, ?, ?, ?)";
    }

    private Connection openConnection() throws SQLException {
        if (isMysql()) {
            return DriverManager.getConnection(
                    "jdbc:mysql://" + plugin.getConfig().getString("settings.storage.mysql.host", "127.0.0.1")
                            + ":" + plugin.getConfig().getInt("settings.storage.mysql.port", 3306)
                            + "/" + plugin.getConfig().getString("settings.storage.mysql.database", "recases")
                            + "?useSSL=" + plugin.getConfig().getBoolean("settings.storage.mysql.use-ssl", false)
                            + "&characterEncoding=utf8&serverTimezone=UTC&autoReconnect=true",
                    plugin.getConfig().getString("settings.storage.mysql.username", "root"),
                    plugin.getConfig().getString("settings.storage.mysql.password", "")
            );
        }

        File databaseFile = new File(plugin.getDataFolder(), plugin.getConfig().getString("settings.storage.sqlite.file", "keys.db"));
        return DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
    }

    private boolean isMysql() {
        return "mysql".equalsIgnoreCase(plugin.getConfig().getString("settings.storage.type", "sqlite"));
    }

    private String createCodesTableSql() {
        if (isMysql()) {
            return "CREATE TABLE IF NOT EXISTS recases_promo_codes (" +
                    "code VARCHAR(64) NOT NULL PRIMARY KEY," +
                    "profile_id VARCHAR(64) NOT NULL," +
                    "amount INT NOT NULL," +
                    "max_uses INT NOT NULL," +
                    "created_by VARCHAR(96) NOT NULL," +
                    "created_at BIGINT NOT NULL" +
                    ")";
        }
        return "CREATE TABLE IF NOT EXISTS recases_promo_codes (" +
                "code TEXT NOT NULL PRIMARY KEY," +
                "profile_id TEXT NOT NULL," +
                "amount INTEGER NOT NULL," +
                "max_uses INTEGER NOT NULL," +
                "created_by TEXT NOT NULL," +
                "created_at INTEGER NOT NULL" +
                ")";
    }

    private String createUsesTableSql() {
        if (isMysql()) {
            return "CREATE TABLE IF NOT EXISTS recases_promo_code_uses (" +
                    "code VARCHAR(64) NOT NULL," +
                    "player_id VARCHAR(36) NOT NULL," +
                    "player_name VARCHAR(32) NOT NULL," +
                    "used_at BIGINT NOT NULL," +
                    "PRIMARY KEY (code, player_id)" +
                    ")";
        }
        return "CREATE TABLE IF NOT EXISTS recases_promo_code_uses (" +
                "code TEXT NOT NULL," +
                "player_id TEXT NOT NULL," +
                "player_name TEXT NOT NULL," +
                "used_at INTEGER NOT NULL," +
                "PRIMARY KEY (code, player_id)" +
                ")";
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record PromoCodeEntry(String code, String profileId, int amount, int maxUses, int usedCount, String createdBy, long createdAt) {
    }

    public record RedemptionResult(boolean success, String code, String profileId, int amount, String message) {

        public static RedemptionResult success(String code, String profileId, int amount) {
            return new RedemptionResult(true, code, profileId, amount, "");
        }

        public static RedemptionResult failed(String message) {
            return new RedemptionResult(false, "", "", 0, message == null ? "" : message);
        }
    }
}
