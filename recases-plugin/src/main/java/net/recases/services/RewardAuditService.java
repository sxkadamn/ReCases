package net.recases.services;

import net.recases.app.PluginContext;
import net.recases.management.CaseItem;
import net.recases.management.OpeningSession;
import net.recases.runtime.CaseRuntime;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class RewardAuditService implements AutoCloseable {

    private final PluginContext plugin;

    public RewardAuditService(PluginContext plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        initialize();
    }

    public void initialize() {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(createTableSql())) {
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to initialize reward audit storage", exception);
        }
    }

    public boolean recordIfAbsent(Player player, CaseRuntime runtime, OpeningSession session, CaseItem reward) {
        String sql = isMysql()
                ? "INSERT IGNORE INTO recases_reward_audit (opening_id, player_id, player_name, case_profile, runtime_id, reward_name, rare_reward, guaranteed_reward, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
                : "INSERT OR IGNORE INTO recases_reward_audit (opening_id, player_id, player_name, case_profile, runtime_id, reward_name, rare_reward, guaranteed_reward, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, session.getOpeningId().toString());
            statement.setString(2, player.getUniqueId().toString());
            statement.setString(3, player.getName());
            statement.setString(4, session.getSelectedCase());
            statement.setString(5, runtime.getId());
            statement.setString(6, reward.getName());
            statement.setBoolean(7, reward.isRare());
            statement.setBoolean(8, session.isGuaranteedReward());
            statement.setLong(9, System.currentTimeMillis());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to write reward audit record", exception);
        }
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

    private String createTableSql() {
        if (isMysql()) {
            return "CREATE TABLE IF NOT EXISTS recases_reward_audit (" +
                    "opening_id VARCHAR(36) NOT NULL PRIMARY KEY," +
                    "player_id VARCHAR(36) NOT NULL," +
                    "player_name VARCHAR(32) NOT NULL," +
                    "case_profile VARCHAR(64) NOT NULL," +
                    "runtime_id VARCHAR(64) NOT NULL," +
                    "reward_name TEXT NOT NULL," +
                    "rare_reward BOOLEAN NOT NULL," +
                    "guaranteed_reward BOOLEAN NOT NULL," +
                    "created_at BIGINT NOT NULL" +
                    ")";
        }

        return "CREATE TABLE IF NOT EXISTS recases_reward_audit (" +
                "opening_id TEXT NOT NULL PRIMARY KEY," +
                "player_id TEXT NOT NULL," +
                "player_name TEXT NOT NULL," +
                "case_profile TEXT NOT NULL," +
                "runtime_id TEXT NOT NULL," +
                "reward_name TEXT NOT NULL," +
                "rare_reward INTEGER NOT NULL," +
                "guaranteed_reward INTEGER NOT NULL," +
                "created_at INTEGER NOT NULL" +
                ")";
    }

    @Override
    public void close() {
    }
}
