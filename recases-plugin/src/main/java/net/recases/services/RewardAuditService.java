package net.recases.services;

import net.recases.app.PluginContext;
import net.recases.management.CaseItem;
import net.recases.management.OpeningSession;
import net.recases.runtime.CaseRuntime;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class RewardAuditService implements AutoCloseable {

    private final PluginContext plugin;

    public RewardAuditService(PluginContext plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        initialize();
        recoverPendingOpenings();
    }

    public void initialize() {
        try (Connection connection = openConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(createAuditTableSql())) {
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(createPendingTableSql())) {
                statement.executeUpdate();
            }

            ensureColumn(connection, "recases_reward_audit", "ALTER TABLE recases_reward_audit ADD COLUMN transaction_id VARCHAR(36) NOT NULL DEFAULT ''");
            ensureColumn(connection, "recases_reward_audit", "ALTER TABLE recases_reward_audit ADD COLUMN animation_id VARCHAR(64) NOT NULL DEFAULT ''");
            ensureColumn(connection, "recases_reward_audit", "ALTER TABLE recases_reward_audit ADD COLUMN reward_id VARCHAR(96) NOT NULL DEFAULT ''");
            ensureColumn(connection, "recases_reward_audit", "ALTER TABLE recases_reward_audit ADD COLUMN pity_before INT NOT NULL DEFAULT 0");
            ensureColumn(connection, "recases_reward_audit", "ALTER TABLE recases_reward_audit ADD COLUMN pity_after INT NOT NULL DEFAULT 0");
            ensureColumn(connection, "recases_reward_audit", "ALTER TABLE recases_reward_audit ADD COLUMN server_id VARCHAR(64) NOT NULL DEFAULT ''");

            ensureColumn(connection, "recases_pending_openings", "ALTER TABLE recases_pending_openings ADD COLUMN transaction_id VARCHAR(36) NOT NULL DEFAULT ''");
            ensureColumn(connection, "recases_pending_openings", "ALTER TABLE recases_pending_openings ADD COLUMN reward_id VARCHAR(96) NOT NULL DEFAULT ''");
            ensureColumn(connection, "recases_pending_openings", "ALTER TABLE recases_pending_openings ADD COLUMN pity_before INT NOT NULL DEFAULT 0");
            ensureColumn(connection, "recases_pending_openings", "ALTER TABLE recases_pending_openings ADD COLUMN server_id VARCHAR(64) NOT NULL DEFAULT ''");
            ensureColumn(connection, "recases_pending_openings", "ALTER TABLE recases_pending_openings ADD COLUMN started_at BIGINT NOT NULL DEFAULT 0");
            ensureColumn(connection, "recases_pending_openings", "ALTER TABLE recases_pending_openings ADD COLUMN updated_at BIGINT NOT NULL DEFAULT 0");
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to initialize reward audit storage", exception);
        }
    }

    public void trackOpening(Player player, CaseRuntime runtime, OpeningSession session) {
        if (player == null || runtime == null || session == null || session.isTestMode()) {
            return;
        }

        String sql = isMysql()
                ? "INSERT INTO recases_pending_openings (opening_id, transaction_id, player_id, player_name, case_profile, runtime_id, animation_id, reward_id, reward_name, rare_reward, guaranteed_reward, pity_before, server_id, key_consumed, reward_granted, started_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE transaction_id = VALUES(transaction_id), player_name = VALUES(player_name), runtime_id = VALUES(runtime_id), animation_id = VALUES(animation_id), reward_id = VALUES(reward_id), reward_name = VALUES(reward_name), rare_reward = VALUES(rare_reward), guaranteed_reward = VALUES(guaranteed_reward), pity_before = VALUES(pity_before), server_id = VALUES(server_id), updated_at = VALUES(updated_at)"
                : "INSERT OR REPLACE INTO recases_pending_openings (opening_id, transaction_id, player_id, player_name, case_profile, runtime_id, animation_id, reward_id, reward_name, rare_reward, guaranteed_reward, pity_before, server_id, key_consumed, reward_granted, started_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindPendingStatement(statement, player, runtime, session, false, false, session.getStartedAt(), System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to persist pending opening", exception);
        }
    }

    public void markKeyConsumed(OpeningSession session) {
        updatePendingState(session, "key_consumed", true);
    }

    public void markRewardGranted(OpeningSession session) {
        updatePendingState(session, "reward_granted", true);
    }

    public void discardPending(OpeningSession session) {
        if (session == null || session.isTestMode()) {
            return;
        }
        deletePending(session.getOpeningId());
    }

    public boolean recordIfAbsent(Player player, CaseRuntime runtime, OpeningSession session, CaseItem reward) {
        if (player == null || runtime == null || session == null || reward == null || session.isTestMode()) {
            return false;
        }

        String sql = isMysql()
                ? "INSERT IGNORE INTO recases_reward_audit (opening_id, transaction_id, player_id, player_name, case_profile, runtime_id, animation_id, reward_id, reward_name, rare_reward, guaranteed_reward, pity_before, pity_after, server_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                : "INSERT OR IGNORE INTO recases_reward_audit (opening_id, transaction_id, player_id, player_name, case_profile, runtime_id, animation_id, reward_id, reward_name, rare_reward, guaranteed_reward, pity_before, pity_after, server_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, session.getOpeningId().toString());
            statement.setString(2, session.getTransactionId().toString());
            statement.setString(3, player.getUniqueId().toString());
            statement.setString(4, player.getName());
            statement.setString(5, session.getSelectedCase());
            statement.setString(6, runtime.getId());
            statement.setString(7, session.getAnimationId());
            statement.setString(8, reward.getId());
            statement.setString(9, reward.getName());
            statement.setBoolean(10, reward.isRare());
            statement.setBoolean(11, session.isGuaranteedReward());
            statement.setInt(12, session.getPityBeforeOpen());
            statement.setInt(13, reward.isRare() ? 0 : session.getPityBeforeOpen() + 1);
            statement.setString(14, resolveServerId());
            statement.setLong(15, System.currentTimeMillis());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to write reward audit record", exception);
        }
    }

    public List<AuditEntry> getRecentEntries(UUID playerId, int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, 50));
        String sql = "SELECT opening_id, transaction_id, player_id, player_name, case_profile, runtime_id, animation_id, reward_id, reward_name, rare_reward, guaranteed_reward, pity_before, pity_after, server_id, created_at " +
                "FROM recases_reward_audit "
                + (playerId == null ? "" : "WHERE player_id = ? ")
                + "ORDER BY created_at DESC LIMIT ?";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameterIndex = 1;
            if (playerId != null) {
                statement.setString(parameterIndex++, playerId.toString());
            }
            statement.setInt(parameterIndex, normalizedLimit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<AuditEntry> entries = new ArrayList<>();
                while (resultSet.next()) {
                    entries.add(new AuditEntry(
                            parseUuid(resultSet.getString("opening_id")),
                            parseUuid(resultSet.getString("transaction_id")),
                            parseUuid(resultSet.getString("player_id")),
                            resultSet.getString("player_name"),
                            resultSet.getString("case_profile"),
                            resultSet.getString("runtime_id"),
                            resultSet.getString("animation_id"),
                            resultSet.getString("reward_id"),
                            resultSet.getString("reward_name"),
                            resultSet.getBoolean("rare_reward"),
                            resultSet.getBoolean("guaranteed_reward"),
                            Math.max(0, resultSet.getInt("pity_before")),
                            Math.max(0, resultSet.getInt("pity_after")),
                            resultSet.getString("server_id"),
                            resultSet.getLong("created_at")
                    ));
                }
                return entries;
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to load reward audit records", exception);
        }
    }

    public void recoverPendingOpenings() {
        String recoveryMode = resolveRecoveryMode();
        for (PendingOpening opening : loadPendingOpenings(null)) {
            if (opening.isRewardGranted()) {
                deletePending(opening.getOpeningId());
                continue;
            }
            if (!opening.isKeyConsumed()) {
                deletePending(opening.getOpeningId());
                continue;
            }
            if ("grant-if-possible".equals(recoveryMode)) {
                Player player = Bukkit.getPlayer(opening.getPlayerId());
                if (player != null && player.isOnline()) {
                    deliverRecoveredReward(player, opening);
                }
                continue;
            }

            refundPendingKey(opening);
            deletePending(opening.getOpeningId());
        }
    }

    public void recoverPendingRewards(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        String recoveryMode = resolveRecoveryMode();
        for (PendingOpening opening : loadPendingOpenings(player.getUniqueId())) {
            if (opening.isRewardGranted()) {
                deletePending(opening.getOpeningId());
                continue;
            }
            if (!opening.isKeyConsumed()) {
                deletePending(opening.getOpeningId());
                continue;
            }
            if ("grant-if-possible".equals(recoveryMode)) {
                deliverRecoveredReward(player, opening);
                continue;
            }

            refundPendingKey(opening);
            deletePending(opening.getOpeningId());
        }
    }

    private void deliverRecoveredReward(Player player, PendingOpening opening) {
        CaseItem reward = plugin.getCaseService().getReward(opening.getCaseProfile(), opening.getRewardId());
        if (reward == null) {
            refundPendingKey(opening);
            deletePending(opening.getOpeningId());
            plugin.getLogger().warning("Recovered opening " + opening.getOpeningId() + " was refunded because reward '" + opening.getRewardId() + "' is missing.");
            return;
        }

        if (!recordRecoveredIfAbsent(player, opening, reward)) {
            deletePending(opening.getOpeningId());
            return;
        }

        plugin.getRewardService().execute(player, reward);
        plugin.getStats().recordOpening(player, opening.getCaseProfile(), reward, opening.isGuaranteedReward());
        plugin.getLeaderboardHolograms().requestRefresh();
        plugin.getMessages().send(player, "messages.case-recovery-granted", "#80ed99Recovered reward: #ffffff%reward%", "%reward%", reward.getName());
        plugin.getDiscordWebhooks().notifyRecoveredReward(player, opening, reward);
        deletePending(opening.getOpeningId());
    }

    private boolean recordRecoveredIfAbsent(Player player, PendingOpening opening, CaseItem reward) {
        String sql = isMysql()
                ? "INSERT IGNORE INTO recases_reward_audit (opening_id, transaction_id, player_id, player_name, case_profile, runtime_id, animation_id, reward_id, reward_name, rare_reward, guaranteed_reward, pity_before, pity_after, server_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                : "INSERT OR IGNORE INTO recases_reward_audit (opening_id, transaction_id, player_id, player_name, case_profile, runtime_id, animation_id, reward_id, reward_name, rare_reward, guaranteed_reward, pity_before, pity_after, server_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, opening.getOpeningId().toString());
            statement.setString(2, opening.getTransactionId().toString());
            statement.setString(3, player.getUniqueId().toString());
            statement.setString(4, player.getName());
            statement.setString(5, opening.getCaseProfile());
            statement.setString(6, opening.getRuntimeId());
            statement.setString(7, opening.getAnimationId());
            statement.setString(8, reward.getId());
            statement.setString(9, reward.getName());
            statement.setBoolean(10, reward.isRare());
            statement.setBoolean(11, opening.isGuaranteedReward());
            statement.setInt(12, opening.getPityBefore());
            statement.setInt(13, reward.isRare() ? 0 : opening.getPityBefore() + 1);
            statement.setString(14, opening.getServerId());
            statement.setLong(15, System.currentTimeMillis());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to recover pending reward delivery", exception);
        }
    }

    private List<PendingOpening> loadPendingOpenings(UUID playerId) {
        String sql = "SELECT opening_id, transaction_id, player_id, player_name, case_profile, runtime_id, animation_id, reward_id, reward_name, rare_reward, guaranteed_reward, pity_before, server_id, key_consumed, reward_granted, started_at, updated_at " +
                "FROM recases_pending_openings "
                + (playerId == null ? "" : "WHERE player_id = ? ")
                + "ORDER BY started_at ASC";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (playerId != null) {
                statement.setString(1, playerId.toString());
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                List<PendingOpening> openings = new ArrayList<>();
                while (resultSet.next()) {
                    openings.add(new PendingOpening(
                            parseUuid(resultSet.getString("opening_id")),
                            parseUuid(resultSet.getString("transaction_id")),
                            parseUuid(resultSet.getString("player_id")),
                            resultSet.getString("player_name"),
                            resultSet.getString("case_profile"),
                            resultSet.getString("runtime_id"),
                            resultSet.getString("animation_id"),
                            resultSet.getString("reward_id"),
                            resultSet.getString("reward_name"),
                            resultSet.getBoolean("rare_reward"),
                            resultSet.getBoolean("guaranteed_reward"),
                            Math.max(0, resultSet.getInt("pity_before")),
                            resultSet.getString("server_id"),
                            resultSet.getBoolean("key_consumed"),
                            resultSet.getBoolean("reward_granted"),
                            resultSet.getLong("started_at"),
                            resultSet.getLong("updated_at")
                    ));
                }
                return openings;
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to load pending openings", exception);
        }
    }

    private void updatePendingState(OpeningSession session, String column, boolean value) {
        if (session == null || session.isTestMode()) {
            return;
        }

        String sql = "UPDATE recases_pending_openings SET " + column + " = ?, updated_at = ? WHERE opening_id = ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, value);
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, session.getOpeningId().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to update pending opening state", exception);
        }
    }

    private void deletePending(UUID openingId) {
        if (openingId == null) {
            return;
        }

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM recases_pending_openings WHERE opening_id = ?")) {
            statement.setString(1, openingId.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to delete pending opening", exception);
        }
    }

    private void refundPendingKey(PendingOpening opening) {
        if (opening == null || !opening.isKeyConsumed()) {
            return;
        }

        OfflinePlayer player = Bukkit.getOfflinePlayer(opening.getPlayerId());
        plugin.getStorage().addCase(player, opening.getCaseProfile(), 1);
    }

    private void bindPendingStatement(PreparedStatement statement, Player player, CaseRuntime runtime, OpeningSession session,
                                      boolean keyConsumed, boolean rewardGranted, long startedAt, long updatedAt) throws SQLException {
        CaseItem reward = session.getFinalReward();
        statement.setString(1, session.getOpeningId().toString());
        statement.setString(2, session.getTransactionId().toString());
        statement.setString(3, player.getUniqueId().toString());
        statement.setString(4, player.getName());
        statement.setString(5, session.getSelectedCase());
        statement.setString(6, runtime.getId());
        statement.setString(7, session.getAnimationId());
        statement.setString(8, reward == null ? "" : reward.getId());
        statement.setString(9, reward == null ? "" : reward.getName());
        statement.setBoolean(10, reward != null && reward.isRare());
        statement.setBoolean(11, session.isGuaranteedReward());
        statement.setInt(12, session.getPityBeforeOpen());
        statement.setString(13, resolveServerId());
        statement.setBoolean(14, keyConsumed);
        statement.setBoolean(15, rewardGranted);
        statement.setLong(16, startedAt);
        statement.setLong(17, updatedAt);
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

    private String createAuditTableSql() {
        if (isMysql()) {
            return "CREATE TABLE IF NOT EXISTS recases_reward_audit (" +
                    "opening_id VARCHAR(36) NOT NULL PRIMARY KEY," +
                    "transaction_id VARCHAR(36) NOT NULL," +
                    "player_id VARCHAR(36) NOT NULL," +
                    "player_name VARCHAR(32) NOT NULL," +
                    "case_profile VARCHAR(64) NOT NULL," +
                    "runtime_id VARCHAR(64) NOT NULL," +
                    "animation_id VARCHAR(64) NOT NULL," +
                    "reward_id VARCHAR(96) NOT NULL," +
                    "reward_name TEXT NOT NULL," +
                    "rare_reward BOOLEAN NOT NULL," +
                    "guaranteed_reward BOOLEAN NOT NULL," +
                    "pity_before INT NOT NULL," +
                    "pity_after INT NOT NULL," +
                    "server_id VARCHAR(64) NOT NULL," +
                    "created_at BIGINT NOT NULL" +
                    ")";
        }

        return "CREATE TABLE IF NOT EXISTS recases_reward_audit (" +
                "opening_id TEXT NOT NULL PRIMARY KEY," +
                "transaction_id TEXT NOT NULL," +
                "player_id TEXT NOT NULL," +
                "player_name TEXT NOT NULL," +
                "case_profile TEXT NOT NULL," +
                "runtime_id TEXT NOT NULL," +
                "animation_id TEXT NOT NULL," +
                "reward_id TEXT NOT NULL," +
                "reward_name TEXT NOT NULL," +
                "rare_reward INTEGER NOT NULL," +
                "guaranteed_reward INTEGER NOT NULL," +
                "pity_before INTEGER NOT NULL," +
                "pity_after INTEGER NOT NULL," +
                "server_id TEXT NOT NULL," +
                "created_at INTEGER NOT NULL" +
                ")";
    }

    private String createPendingTableSql() {
        if (isMysql()) {
            return "CREATE TABLE IF NOT EXISTS recases_pending_openings (" +
                    "opening_id VARCHAR(36) NOT NULL PRIMARY KEY," +
                    "transaction_id VARCHAR(36) NOT NULL," +
                    "player_id VARCHAR(36) NOT NULL," +
                    "player_name VARCHAR(32) NOT NULL," +
                    "case_profile VARCHAR(64) NOT NULL," +
                    "runtime_id VARCHAR(64) NOT NULL," +
                    "animation_id VARCHAR(64) NOT NULL," +
                    "reward_id VARCHAR(96) NOT NULL," +
                    "reward_name TEXT NOT NULL," +
                    "rare_reward BOOLEAN NOT NULL," +
                    "guaranteed_reward BOOLEAN NOT NULL," +
                    "pity_before INT NOT NULL," +
                    "server_id VARCHAR(64) NOT NULL," +
                    "key_consumed BOOLEAN NOT NULL," +
                    "reward_granted BOOLEAN NOT NULL," +
                    "started_at BIGINT NOT NULL," +
                    "updated_at BIGINT NOT NULL" +
                    ")";
        }

        return "CREATE TABLE IF NOT EXISTS recases_pending_openings (" +
                "opening_id TEXT NOT NULL PRIMARY KEY," +
                "transaction_id TEXT NOT NULL," +
                "player_id TEXT NOT NULL," +
                "player_name TEXT NOT NULL," +
                "case_profile TEXT NOT NULL," +
                "runtime_id TEXT NOT NULL," +
                "animation_id TEXT NOT NULL," +
                "reward_id TEXT NOT NULL," +
                "reward_name TEXT NOT NULL," +
                "rare_reward INTEGER NOT NULL," +
                "guaranteed_reward INTEGER NOT NULL," +
                "pity_before INTEGER NOT NULL," +
                "server_id TEXT NOT NULL," +
                "key_consumed INTEGER NOT NULL," +
                "reward_granted INTEGER NOT NULL," +
                "started_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL" +
                ")";
    }

    private void ensureColumn(Connection connection, String tableName, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException exception) {
            String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase(Locale.ROOT);
            if (message.contains("duplicate")
                    || message.contains("exists")
                    || message.contains("already exists")
                    || message.contains("duplicate column")) {
                return;
            }
            if (!tableExists(connection, tableName)) {
                return;
            }
            throw exception;
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (ResultSet resultSet = connection.getMetaData().getTables(null, null, tableName, null)) {
            return resultSet.next();
        }
    }

    private UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return new UUID(0L, 0L);
        }
    }

    private String resolveServerId() {
        String configured = plugin.getConfig().getString("settings.server-id", "").trim();
        if (!configured.isEmpty()) {
            return configured;
        }

        String networkServerId = plugin.getConfig().getString("settings.network-sync.server-id", "").trim();
        if (!networkServerId.isEmpty()) {
            return networkServerId;
        }
        return "default";
    }

    private String resolveRecoveryMode() {
        return plugin.getConfig().getString("settings.opening-recovery.mode", "grant-if-possible")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    @Override
    public void close() {
    }

    public static final class AuditEntry {
        private final UUID openingId;
        private final UUID transactionId;
        private final UUID playerId;
        private final String playerName;
        private final String caseProfile;
        private final String runtimeId;
        private final String animationId;
        private final String rewardId;
        private final String rewardName;
        private final boolean rareReward;
        private final boolean guaranteedReward;
        private final int pityBefore;
        private final int pityAfter;
        private final String serverId;
        private final long createdAt;

        private AuditEntry(UUID openingId, UUID transactionId, UUID playerId, String playerName, String caseProfile, String runtimeId,
                           String animationId, String rewardId, String rewardName, boolean rareReward, boolean guaranteedReward,
                           int pityBefore, int pityAfter, String serverId, long createdAt) {
            this.openingId = openingId;
            this.transactionId = transactionId;
            this.playerId = playerId;
            this.playerName = playerName == null ? "" : playerName;
            this.caseProfile = caseProfile == null ? "" : caseProfile;
            this.runtimeId = runtimeId == null ? "" : runtimeId;
            this.animationId = animationId == null ? "" : animationId;
            this.rewardId = rewardId == null ? "" : rewardId;
            this.rewardName = rewardName == null ? "" : rewardName;
            this.rareReward = rareReward;
            this.guaranteedReward = guaranteedReward;
            this.pityBefore = pityBefore;
            this.pityAfter = pityAfter;
            this.serverId = serverId == null ? "" : serverId;
            this.createdAt = createdAt;
        }

        public UUID getOpeningId() {
            return openingId;
        }

        public UUID getTransactionId() {
            return transactionId;
        }

        public UUID getPlayerId() {
            return playerId;
        }

        public String getPlayerName() {
            return playerName;
        }

        public String getCaseProfile() {
            return caseProfile;
        }

        public String getRuntimeId() {
            return runtimeId;
        }

        public String getAnimationId() {
            return animationId;
        }

        public String getRewardId() {
            return rewardId;
        }

        public String getRewardName() {
            return rewardName;
        }

        public boolean isRareReward() {
            return rareReward;
        }

        public boolean isGuaranteedReward() {
            return guaranteedReward;
        }

        public int getPityBefore() {
            return pityBefore;
        }

        public int getPityAfter() {
            return pityAfter;
        }

        public String getServerId() {
            return serverId;
        }

        public long getCreatedAt() {
            return createdAt;
        }
    }

    public static final class PendingOpening {
        private final UUID openingId;
        private final UUID transactionId;
        private final UUID playerId;
        private final String playerName;
        private final String caseProfile;
        private final String runtimeId;
        private final String animationId;
        private final String rewardId;
        private final String rewardName;
        private final boolean rareReward;
        private final boolean guaranteedReward;
        private final int pityBefore;
        private final String serverId;
        private final boolean keyConsumed;
        private final boolean rewardGranted;
        private final long startedAt;
        private final long updatedAt;

        private PendingOpening(UUID openingId, UUID transactionId, UUID playerId, String playerName, String caseProfile, String runtimeId,
                               String animationId, String rewardId, String rewardName, boolean rareReward, boolean guaranteedReward,
                               int pityBefore, String serverId, boolean keyConsumed, boolean rewardGranted, long startedAt, long updatedAt) {
            this.openingId = openingId;
            this.transactionId = transactionId;
            this.playerId = playerId;
            this.playerName = playerName == null ? "" : playerName;
            this.caseProfile = caseProfile == null ? "" : caseProfile;
            this.runtimeId = runtimeId == null ? "" : runtimeId;
            this.animationId = animationId == null ? "" : animationId;
            this.rewardId = rewardId == null ? "" : rewardId;
            this.rewardName = rewardName == null ? "" : rewardName;
            this.rareReward = rareReward;
            this.guaranteedReward = guaranteedReward;
            this.pityBefore = pityBefore;
            this.serverId = serverId == null ? "" : serverId;
            this.keyConsumed = keyConsumed;
            this.rewardGranted = rewardGranted;
            this.startedAt = startedAt;
            this.updatedAt = updatedAt;
        }

        public UUID getOpeningId() {
            return openingId;
        }

        public UUID getTransactionId() {
            return transactionId;
        }

        public UUID getPlayerId() {
            return playerId;
        }

        public String getPlayerName() {
            return playerName;
        }

        public String getCaseProfile() {
            return caseProfile;
        }

        public String getRuntimeId() {
            return runtimeId;
        }

        public String getAnimationId() {
            return animationId;
        }

        public String getRewardId() {
            return rewardId;
        }

        public String getRewardName() {
            return rewardName;
        }

        public boolean isRareReward() {
            return rareReward;
        }

        public boolean isGuaranteedReward() {
            return guaranteedReward;
        }

        public int getPityBefore() {
            return pityBefore;
        }

        public String getServerId() {
            return serverId;
        }

        public boolean isKeyConsumed() {
            return keyConsumed;
        }

        public boolean isRewardGranted() {
            return rewardGranted;
        }

        public long getStartedAt() {
            return startedAt;
        }

        public long getUpdatedAt() {
            return updatedAt;
        }
    }
}
