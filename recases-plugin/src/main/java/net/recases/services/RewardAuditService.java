package net.recases.services;

import net.recases.app.PluginContext;
import net.recases.domain.CaseProfile;
import net.recases.management.CaseItem;
import net.recases.management.OpeningSession;
import net.recases.runtime.CaseRuntime;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

public class RewardAuditService implements AutoCloseable {

    private final PluginContext plugin;
    private BukkitTask cleanupTask;

    public RewardAuditService(PluginContext plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        close();
        initialize();
        recoverPendingOpenings();
        scheduleCleanup();
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
            ensureColumn(connection, "recases_reward_audit", "ALTER TABLE recases_reward_audit ADD COLUMN rolled_back BOOLEAN NOT NULL DEFAULT FALSE");
            ensureColumn(connection, "recases_reward_audit", "ALTER TABLE recases_reward_audit ADD COLUMN restored BOOLEAN NOT NULL DEFAULT FALSE");
            ensureColumn(connection, "recases_reward_audit", "ALTER TABLE recases_reward_audit ADD COLUMN rollback_actor VARCHAR(96) NOT NULL DEFAULT ''");
            ensureColumn(connection, "recases_reward_audit", "ALTER TABLE recases_reward_audit ADD COLUMN restore_actor VARCHAR(96) NOT NULL DEFAULT ''");
            ensureColumn(connection, "recases_reward_audit", "ALTER TABLE recases_reward_audit ADD COLUMN updated_at BIGINT NOT NULL DEFAULT 0");
            ensureColumn(connection, "recases_reward_audit", "ALTER TABLE recases_reward_audit ADD COLUMN reward_actions TEXT NOT NULL DEFAULT ''");
            ensureColumn(connection, "recases_reward_audit", "ALTER TABLE recases_reward_audit ADD COLUMN rollback_actions TEXT NOT NULL DEFAULT ''");

            ensureColumn(connection, "recases_pending_openings", "ALTER TABLE recases_pending_openings ADD COLUMN transaction_id VARCHAR(36) NOT NULL DEFAULT ''");
            ensureColumn(connection, "recases_pending_openings", "ALTER TABLE recases_pending_openings ADD COLUMN reward_id VARCHAR(96) NOT NULL DEFAULT ''");
            ensureColumn(connection, "recases_pending_openings", "ALTER TABLE recases_pending_openings ADD COLUMN pity_before INT NOT NULL DEFAULT 0");
            ensureColumn(connection, "recases_pending_openings", "ALTER TABLE recases_pending_openings ADD COLUMN server_id VARCHAR(64) NOT NULL DEFAULT ''");
            ensureColumn(connection, "recases_pending_openings", "ALTER TABLE recases_pending_openings ADD COLUMN started_at BIGINT NOT NULL DEFAULT 0");
            ensureColumn(connection, "recases_pending_openings", "ALTER TABLE recases_pending_openings ADD COLUMN updated_at BIGINT NOT NULL DEFAULT 0");
            ensureColumn(connection, "recases_pending_openings", "ALTER TABLE recases_pending_openings ADD COLUMN reward_actions TEXT NOT NULL DEFAULT ''");
            ensureColumn(connection, "recases_pending_openings", "ALTER TABLE recases_pending_openings ADD COLUMN rollback_actions TEXT NOT NULL DEFAULT ''");
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to initialize reward audit storage", exception);
        }
    }

    public void trackOpening(Player player, CaseRuntime runtime, OpeningSession session) {
        if (player == null || runtime == null || session == null || session.isTestMode()) {
            return;
        }

        String sql = isMysql()
                ? "INSERT INTO recases_pending_openings (opening_id, transaction_id, player_id, player_name, case_profile, runtime_id, animation_id, reward_id, reward_name, rare_reward, guaranteed_reward, pity_before, server_id, key_consumed, reward_granted, started_at, updated_at, reward_actions, rollback_actions) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE transaction_id = VALUES(transaction_id), player_name = VALUES(player_name), runtime_id = VALUES(runtime_id), animation_id = VALUES(animation_id), reward_id = VALUES(reward_id), reward_name = VALUES(reward_name), rare_reward = VALUES(rare_reward), guaranteed_reward = VALUES(guaranteed_reward), pity_before = VALUES(pity_before), server_id = VALUES(server_id), updated_at = VALUES(updated_at), reward_actions = VALUES(reward_actions), rollback_actions = VALUES(rollback_actions)"
                : "INSERT OR REPLACE INTO recases_pending_openings (opening_id, transaction_id, player_id, player_name, case_profile, runtime_id, animation_id, reward_id, reward_name, rare_reward, guaranteed_reward, pity_before, server_id, key_consumed, reward_granted, started_at, updated_at, reward_actions, rollback_actions) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

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
                ? "INSERT IGNORE INTO recases_reward_audit (opening_id, transaction_id, player_id, player_name, case_profile, runtime_id, animation_id, reward_id, reward_name, rare_reward, guaranteed_reward, pity_before, pity_after, server_id, rolled_back, restored, rollback_actor, restore_actor, created_at, updated_at, reward_actions, rollback_actions) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                : "INSERT OR IGNORE INTO recases_reward_audit (opening_id, transaction_id, player_id, player_name, case_profile, runtime_id, animation_id, reward_id, reward_name, rare_reward, guaranteed_reward, pity_before, pity_after, server_id, rolled_back, restored, rollback_actor, restore_actor, created_at, updated_at, reward_actions, rollback_actions) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindAuditStatement(statement, player, runtime.getId(), session.getAnimationId(), session.getSelectedCase(), session, reward, resolveServerId(), false, false, "", "", System.currentTimeMillis());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to write reward audit record", exception);
        }
    }

    public List<AuditEntry> getRecentEntries(UUID playerId, int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, 50));
        String sql = "SELECT opening_id, transaction_id, player_id, player_name, case_profile, runtime_id, animation_id, reward_id, reward_name, rare_reward, guaranteed_reward, pity_before, pity_after, server_id, rolled_back, restored, rollback_actor, restore_actor, created_at, updated_at, reward_actions, rollback_actions " +
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
                    entries.add(mapAuditEntry(resultSet));
                }
                return entries;
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to load reward audit records", exception);
        }
    }

    public MutationResult rollbackEntry(String identifier, String actorName) {
        if (System.nanoTime() == Long.MIN_VALUE) {
        LookupResult lookup = resolveEntry(identifier);
        if (lookup.ambiguous) {
            return MutationResult.failed("Идентификатор неоднозначен. Укажите более длинный tx/opening id.");
        }
        if (lookup.entry == null) {
            return MutationResult.failed("Запись не найдена.");
        }

        AuditEntry entry = lookup.entry;
        if (entry.isRolledBack()) {
            return MutationResult.failed("Эта выдача уже откатана.");
        }

        Player player = Bukkit.getPlayer(entry.getPlayerId());
        if (player == null || !player.isOnline()) {
            return MutationResult.failed("Для rollback игрок должен быть онлайн.");
        }

        CaseItem reward = resolveRewardSnapshot(entry);
        if (reward == null) {
            return MutationResult.failed("Награда для этой записи больше не существует.");
        }

        CaseProfile profile = plugin.getCaseService().getProfile(entry.getCaseProfile());
        CaseExecutionContext context = plugin.getRewardService().createContext(
                player,
                entry.getCaseProfile(),
                entry.getRuntimeId(),
                entry.getAnimationId(),
                reward,
                entry.isGuaranteedReward(),
                entry.getPityBefore(),
                "reward-rollback",
                false,
                true
        );
        plugin.getRewardService().rollback(context, reward);
        updateAuditState(entry.getOpeningId(), true, false, actorName, entry.getRestoreActor());
        rebuildPlayerStats(entry);
        plugin.getLeaderboardHolograms().requestRefresh();
        plugin.getTriggerService().fireConfigured("reward-rollback", context, profile, reward);
        return MutationResult.success("Rollback выполнен.", entry);
        }
        return mutateEntry(identifier, actorName, true);
    }

    public MutationResult restoreEntry(String identifier, String actorName) {
        if (System.nanoTime() == Long.MIN_VALUE) {
        LookupResult lookup = resolveEntry(identifier);
        if (lookup.ambiguous) {
            return MutationResult.failed("Идентификатор неоднозначен. Укажите более длинный tx/opening id.");
        }
        if (lookup.entry == null) {
            return MutationResult.failed("Запись не найдена.");
        }

        AuditEntry entry = lookup.entry;
        if (!entry.isRolledBack()) {
            return MutationResult.failed("Эта выдача не откатана.");
        }

        Player player = Bukkit.getPlayer(entry.getPlayerId());
        if (player == null || !player.isOnline()) {
            return MutationResult.failed("Для restore игрок должен быть онлайн.");
        }

        CaseItem reward = resolveRewardSnapshot(entry);
        if (reward == null) {
            return MutationResult.failed("Награда для этой записи больше не существует.");
        }

        CaseProfile profile = plugin.getCaseService().getProfile(entry.getCaseProfile());
        CaseExecutionContext context = plugin.getRewardService().createContext(
                player,
                entry.getCaseProfile(),
                entry.getRuntimeId(),
                entry.getAnimationId(),
                reward,
                entry.isGuaranteedReward(),
                entry.getPityBefore(),
                "reward-restore",
                false,
                false
        );
        plugin.getRewardService().execute(context, reward.getActions());
        updateAuditState(entry.getOpeningId(), false, true, entry.getRollbackActor(), actorName);
        rebuildPlayerStats(entry);
        plugin.getLeaderboardHolograms().requestRefresh();
        plugin.getTriggerService().fireConfigured("reward-restore", context, profile, reward);
        return MutationResult.success("Restore выполнен.", entry);
        }
        return mutateEntry(identifier, actorName, false);
    }

    private MutationResult mutateEntry(String identifier, String actorName, boolean rollback) {
        LookupResult lookup = resolveEntry(identifier);
        if (lookup.ambiguous) {
            return MutationResult.failed("Identifier is ambiguous. Use a longer tx/opening id.");
        }
        if (lookup.entry == null) {
            return MutationResult.failed("Audit entry was not found.");
        }

        AuditEntry initialEntry = lookup.entry;
        Player player = Bukkit.getPlayer(initialEntry.getPlayerId());
        if (player == null || !player.isOnline()) {
            return MutationResult.failed(rollback
                    ? "Player must be online for rollback."
                    : "Player must be online for restore.");
        }

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                AuditEntry entry = findEntryForUpdate(connection, initialEntry.getOpeningId());
                if (entry == null) {
                    connection.rollback();
                    return MutationResult.failed("Audit entry was not found.");
                }
                if (rollback && entry.isRolledBack()) {
                    connection.rollback();
                    return MutationResult.failed("This reward has already been rolled back.");
                }
                if (!rollback && !entry.isRolledBack()) {
                    connection.rollback();
                    return MutationResult.failed("This reward is not rolled back.");
                }

                CaseItem reward = resolveRewardSnapshot(entry);
                if (reward == null) {
                    connection.rollback();
                    return MutationResult.failed("Reward definition is missing.");
                }

                CaseProfile profile = plugin.getCaseService().getProfile(entry.getCaseProfile());
                CaseExecutionContext context = plugin.getRewardService().createContext(
                        player,
                        entry.getCaseProfile(),
                        entry.getRuntimeId(),
                        entry.getAnimationId(),
                        reward,
                        entry.isGuaranteedReward(),
                        entry.getPityBefore(),
                        rollback ? "reward-rollback" : "reward-restore",
                        false,
                        rollback
                );

                if (rollback) {
                    plugin.getRewardService().rollback(context, reward);
                    updateAuditState(connection, entry.getOpeningId(), true, false, actorName, entry.getRestoreActor());
                } else {
                    plugin.getRewardService().execute(context, reward.getActions());
                    updateAuditState(connection, entry.getOpeningId(), false, true, entry.getRollbackActor(), actorName);
                }

                connection.commit();
                rebuildPlayerStats(entry);
                plugin.getLeaderboardHolograms().requestRefresh();
                plugin.getTriggerService().fireConfigured(rollback ? "reward-rollback" : "reward-restore", context, profile, reward);
                return MutationResult.success(rollback ? "Rollback completed." : "Restore completed.", entry);
            } catch (RuntimeException | SQLException exception) {
                connection.rollback();
                if (exception instanceof SQLException sqlException) {
                    throw sqlException;
                }
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to mutate reward audit entry", exception);
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
        CaseItem reward = resolveRewardSnapshot(opening);
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

        CaseProfile profile = plugin.getCaseService().getProfile(opening.getCaseProfile());
        CaseExecutionContext context = plugin.getRewardService().createContext(
                player,
                opening.getCaseProfile(),
                opening.getRuntimeId(),
                opening.getAnimationId(),
                reward,
                opening.isGuaranteedReward(),
                opening.getPityBefore(),
                "reward-recovered",
                true,
                false
        );
        plugin.getRewardService().execute(context, reward.getActions());
        plugin.getStats().recordOpening(player, opening.getCaseProfile(), reward, opening.isGuaranteedReward());
        plugin.getLeaderboardHolograms().requestRefresh();
        plugin.getMessages().send(player, "messages.case-recovery-granted", "#80ed99Recovered reward: #ffffff%reward%", "%reward%", reward.getName());
        plugin.getDiscordWebhooks().notifyRecoveredReward(player, opening, reward);
        plugin.getTriggerService().fireConfigured("reward-recovered", context, profile, reward);
        deletePending(opening.getOpeningId());
    }

    private boolean recordRecoveredIfAbsent(Player player, PendingOpening opening, CaseItem reward) {
        String sql = isMysql()
                ? "INSERT IGNORE INTO recases_reward_audit (opening_id, transaction_id, player_id, player_name, case_profile, runtime_id, animation_id, reward_id, reward_name, rare_reward, guaranteed_reward, pity_before, pity_after, server_id, rolled_back, restored, rollback_actor, restore_actor, created_at, updated_at, reward_actions, rollback_actions) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                : "INSERT OR IGNORE INTO recases_reward_audit (opening_id, transaction_id, player_id, player_name, case_profile, runtime_id, animation_id, reward_id, reward_name, rare_reward, guaranteed_reward, pity_before, pity_after, server_id, rolled_back, restored, rollback_actor, restore_actor, created_at, updated_at, reward_actions, rollback_actions) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindAuditStatement(
                    statement,
                    player,
                    opening.getRuntimeId(),
                    opening.getAnimationId(),
                    opening.getCaseProfile(),
                    opening.getTransactionId(),
                    opening.getOpeningId(),
                    reward,
                    opening.isGuaranteedReward(),
                    opening.getPityBefore(),
                    opening.getServerId(),
                    false,
                    false,
                    "",
                    "",
                    System.currentTimeMillis()
            );
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to recover pending reward delivery", exception);
        }
    }

    private List<PendingOpening> loadPendingOpenings(UUID playerId) {
        String sql = "SELECT opening_id, transaction_id, player_id, player_name, case_profile, runtime_id, animation_id, reward_id, reward_name, rare_reward, guaranteed_reward, pity_before, server_id, key_consumed, reward_granted, started_at, updated_at, reward_actions, rollback_actions " +
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
                            resultSet.getLong("updated_at"),
                            decodeCommands(resultSet.getString("reward_actions")),
                            decodeCommands(resultSet.getString("rollback_actions"))
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

    private LookupResult resolveEntry(String identifier) {
        String normalized = identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return new LookupResult(null, false);
        }

        String sql = "SELECT opening_id, transaction_id, player_id, player_name, case_profile, runtime_id, animation_id, reward_id, reward_name, rare_reward, guaranteed_reward, pity_before, pity_after, server_id, rolled_back, restored, rollback_actor, restore_actor, created_at, updated_at, reward_actions, rollback_actions " +
                "FROM recases_reward_audit WHERE opening_id = ? OR transaction_id = ? OR opening_id LIKE ? OR transaction_id LIKE ? ORDER BY created_at DESC LIMIT 2";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalized);
            statement.setString(2, normalized);
            statement.setString(3, normalized + "%");
            statement.setString(4, normalized + "%");
            try (ResultSet resultSet = statement.executeQuery()) {
                List<AuditEntry> matches = new ArrayList<>();
                while (resultSet.next()) {
                    matches.add(mapAuditEntry(resultSet));
                }
                if (matches.isEmpty()) {
                    return new LookupResult(null, false);
                }
                return new LookupResult(matches.get(0), matches.size() > 1);
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to resolve reward audit entry", exception);
        }
    }

    private AuditEntry findEntryForUpdate(Connection connection, UUID openingId) throws SQLException {
        if (connection == null || openingId == null) {
            return null;
        }

        String sql = "SELECT opening_id, transaction_id, player_id, player_name, case_profile, runtime_id, animation_id, reward_id, reward_name, rare_reward, guaranteed_reward, pity_before, pity_after, server_id, rolled_back, restored, rollback_actor, restore_actor, created_at, updated_at, reward_actions, rollback_actions " +
                "FROM recases_reward_audit WHERE opening_id = ?" + (isMysql() ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, openingId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapAuditEntry(resultSet) : null;
            }
        }
    }

    private List<AuditEntry> loadActiveEntries(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }

        String sql = "SELECT opening_id, transaction_id, player_id, player_name, case_profile, runtime_id, animation_id, reward_id, reward_name, rare_reward, guaranteed_reward, pity_before, pity_after, server_id, rolled_back, restored, rollback_actor, restore_actor, created_at, updated_at, reward_actions, rollback_actions " +
                "FROM recases_reward_audit WHERE player_id = ? AND rolled_back = ? ORDER BY created_at ASC";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setBoolean(2, false);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<AuditEntry> entries = new ArrayList<>();
                while (resultSet.next()) {
                    entries.add(mapAuditEntry(resultSet));
                }
                entries.sort(Comparator.comparingLong(AuditEntry::getCreatedAt));
                return entries;
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to load active audit entries", exception);
        }
    }

    private void rebuildPlayerStats(AuditEntry entry) {
        plugin.getStats().rebuildPlayerFromAudit(entry.getPlayerId(), entry.getPlayerName(), loadActiveEntries(entry.getPlayerId()));
    }

    private void updateAuditState(UUID openingId, boolean rolledBack, boolean restored, String rollbackActor, String restoreActor) {
        String sql = "UPDATE recases_reward_audit SET rolled_back = ?, restored = ?, rollback_actor = ?, restore_actor = ?, updated_at = ? WHERE opening_id = ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, rolledBack);
            statement.setBoolean(2, restored);
            statement.setString(3, rollbackActor == null ? "" : rollbackActor);
            statement.setString(4, restoreActor == null ? "" : restoreActor);
            statement.setLong(5, System.currentTimeMillis());
            statement.setString(6, openingId.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to update reward audit state", exception);
        }
    }

    private void updateAuditState(Connection connection, UUID openingId, boolean rolledBack, boolean restored, String rollbackActor, String restoreActor) throws SQLException {
        String sql = "UPDATE recases_reward_audit SET rolled_back = ?, restored = ?, rollback_actor = ?, restore_actor = ?, updated_at = ? WHERE opening_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, rolledBack);
            statement.setBoolean(2, restored);
            statement.setString(3, rollbackActor == null ? "" : rollbackActor);
            statement.setString(4, restoreActor == null ? "" : restoreActor);
            statement.setLong(5, System.currentTimeMillis());
            statement.setString(6, openingId.toString());
            statement.executeUpdate();
        }
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
        statement.setString(18, encodeCommands(reward == null ? List.of() : reward.getActions()));
        statement.setString(19, encodeCommands(reward == null ? List.of() : reward.getRollbackActions()));
    }

    private void bindAuditStatement(PreparedStatement statement, Player player, String runtimeId, String animationId, String caseProfile,
                                    OpeningSession session, CaseItem reward, String serverId, boolean rolledBack, boolean restored,
                                    String rollbackActor, String restoreActor, long timestamp) throws SQLException {
        bindAuditStatement(
                statement,
                player,
                runtimeId,
                animationId,
                caseProfile,
                session.getTransactionId(),
                session.getOpeningId(),
                reward,
                session.isGuaranteedReward(),
                session.getPityBeforeOpen(),
                serverId,
                rolledBack,
                restored,
                rollbackActor,
                restoreActor,
                timestamp
        );
    }

    private void bindAuditStatement(PreparedStatement statement, Player player, String runtimeId, String animationId, String caseProfile,
                                    UUID transactionId, UUID openingId, CaseItem reward, boolean guaranteedReward, int pityBefore,
                                    String serverId, boolean rolledBack, boolean restored, String rollbackActor,
                                    String restoreActor, long timestamp) throws SQLException {
        statement.setString(1, openingId.toString());
        statement.setString(2, transactionId.toString());
        statement.setString(3, player.getUniqueId().toString());
        statement.setString(4, player.getName());
        statement.setString(5, caseProfile);
        statement.setString(6, runtimeId);
        statement.setString(7, animationId);
        statement.setString(8, reward.getId());
        statement.setString(9, reward.getName());
        statement.setBoolean(10, reward.isRare());
        statement.setBoolean(11, guaranteedReward);
        statement.setInt(12, pityBefore);
        statement.setInt(13, reward.isRare() ? 0 : pityBefore + 1);
        statement.setString(14, serverId);
        statement.setBoolean(15, rolledBack);
        statement.setBoolean(16, restored);
        statement.setString(17, rollbackActor == null ? "" : rollbackActor);
        statement.setString(18, restoreActor == null ? "" : restoreActor);
        statement.setLong(19, timestamp);
        statement.setLong(20, timestamp);
        statement.setString(21, encodeCommands(reward.getActions()));
        statement.setString(22, encodeCommands(reward.getRollbackActions()));
    }

    private AuditEntry mapAuditEntry(ResultSet resultSet) throws SQLException {
        return new AuditEntry(
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
                resultSet.getBoolean("rolled_back"),
                resultSet.getBoolean("restored"),
                resultSet.getString("rollback_actor"),
                resultSet.getString("restore_actor"),
                resultSet.getLong("created_at"),
                resultSet.getLong("updated_at"),
                decodeCommands(resultSet.getString("reward_actions")),
                decodeCommands(resultSet.getString("rollback_actions"))
        );
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
                    "rolled_back BOOLEAN NOT NULL DEFAULT FALSE," +
                    "restored BOOLEAN NOT NULL DEFAULT FALSE," +
                    "rollback_actor VARCHAR(96) NOT NULL DEFAULT ''," +
                    "restore_actor VARCHAR(96) NOT NULL DEFAULT ''," +
                    "created_at BIGINT NOT NULL," +
                    "updated_at BIGINT NOT NULL," +
                    "reward_actions TEXT NOT NULL," +
                    "rollback_actions TEXT NOT NULL" +
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
                "rolled_back INTEGER NOT NULL DEFAULT 0," +
                "restored INTEGER NOT NULL DEFAULT 0," +
                "rollback_actor TEXT NOT NULL DEFAULT ''," +
                "restore_actor TEXT NOT NULL DEFAULT ''," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL," +
                "reward_actions TEXT NOT NULL," +
                "rollback_actions TEXT NOT NULL" +
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
                    "updated_at BIGINT NOT NULL," +
                    "reward_actions TEXT NOT NULL," +
                    "rollback_actions TEXT NOT NULL" +
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
                "updated_at INTEGER NOT NULL," +
                "reward_actions TEXT NOT NULL," +
                "rollback_actions TEXT NOT NULL" +
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

    private void scheduleCleanup() {
        long intervalMinutes = Math.max(1L, plugin.getConfig().getLong("settings.audit.cleanup-interval-minutes", 30L));
        cleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupExpiredEntries, intervalMinutes * 1200L, intervalMinutes * 1200L);
    }

    private void cleanupExpiredEntries() {
        long now = System.currentTimeMillis();
        long auditRetentionDays = plugin.getConfig().getLong("settings.audit.retention-days", 90L);
        long pendingRetentionHours = plugin.getConfig().getLong("settings.audit.pending-retention-hours", 168L);

        if (auditRetentionDays > 0) {
            deleteOlderThan("recases_reward_audit", "updated_at", now - auditRetentionDays * 24L * 60L * 60L * 1000L);
        }
        if (pendingRetentionHours > 0) {
            deleteOlderThan("recases_pending_openings", "updated_at", now - pendingRetentionHours * 60L * 60L * 1000L);
        }
    }

    private void deleteOlderThan(String tableName, String timestampColumn, long threshold) {
        String sql = "DELETE FROM " + tableName + " WHERE " + timestampColumn + " > 0 AND " + timestampColumn + " < ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, threshold);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed to cleanup " + tableName + ": " + exception.getMessage());
        }
    }

    private CaseItem resolveRewardSnapshot(AuditEntry entry) {
        CaseItem reward = plugin.getCaseService().getReward(entry.getCaseProfile(), entry.getRewardId());
        if (reward != null) {
            return reward;
        }
        return buildSnapshotReward(entry.getRewardId(), entry.getRewardName(), entry.isRareReward(), entry.getRewardActions(), entry.getRollbackActions());
    }

    private CaseItem resolveRewardSnapshot(PendingOpening opening) {
        CaseItem reward = plugin.getCaseService().getReward(opening.getCaseProfile(), opening.getRewardId());
        if (reward != null) {
            return reward;
        }
        return buildSnapshotReward(opening.getRewardId(), opening.getRewardName(), opening.isRareReward(), opening.getRewardActions(), opening.getRollbackActions());
    }

    private CaseItem buildSnapshotReward(String rewardId, String rewardName, boolean rareReward, List<String> rewardActions, List<String> rollbackActions) {
        if ((rewardActions == null || rewardActions.isEmpty()) && (rollbackActions == null || rollbackActions.isEmpty())) {
            return null;
        }
        return new SnapshotCaseItem(rewardId, rewardName, rewardActions, rollbackActions, rareReward);
    }

    private String encodeCommands(List<String> commands) {
        if (commands == null || commands.isEmpty()) {
            return "";
        }
        return commands.stream()
                .filter(command -> command != null && !command.isEmpty())
                .collect(Collectors.joining("\n"));
    }

    private List<String> decodeCommands(String commands) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }
        return commands.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();
    }

    @Override
    public void close() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
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
        private final boolean rolledBack;
        private final boolean restored;
        private final String rollbackActor;
        private final String restoreActor;
        private final long createdAt;
        private final long updatedAt;
        private final List<String> rewardActions;
        private final List<String> rollbackActions;

        private AuditEntry(UUID openingId, UUID transactionId, UUID playerId, String playerName, String caseProfile, String runtimeId,
                           String animationId, String rewardId, String rewardName, boolean rareReward, boolean guaranteedReward,
                           int pityBefore, int pityAfter, String serverId, boolean rolledBack, boolean restored,
                           String rollbackActor, String restoreActor, long createdAt, long updatedAt,
                           List<String> rewardActions, List<String> rollbackActions) {
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
            this.rolledBack = rolledBack;
            this.restored = restored;
            this.rollbackActor = rollbackActor == null ? "" : rollbackActor;
            this.restoreActor = restoreActor == null ? "" : restoreActor;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.rewardActions = rewardActions == null ? List.of() : List.copyOf(rewardActions);
            this.rollbackActions = rollbackActions == null ? List.of() : List.copyOf(rollbackActions);
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

        public boolean isRolledBack() {
            return rolledBack;
        }

        public boolean isRestored() {
            return restored;
        }

        public String getRollbackActor() {
            return rollbackActor;
        }

        public String getRestoreActor() {
            return restoreActor;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public long getUpdatedAt() {
            return updatedAt;
        }

        public List<String> getRewardActions() {
            return rewardActions;
        }

        public List<String> getRollbackActions() {
            return rollbackActions;
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
        private final List<String> rewardActions;
        private final List<String> rollbackActions;

        private PendingOpening(UUID openingId, UUID transactionId, UUID playerId, String playerName, String caseProfile, String runtimeId,
                               String animationId, String rewardId, String rewardName, boolean rareReward, boolean guaranteedReward,
                               int pityBefore, String serverId, boolean keyConsumed, boolean rewardGranted, long startedAt, long updatedAt,
                               List<String> rewardActions, List<String> rollbackActions) {
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
            this.rewardActions = rewardActions == null ? List.of() : List.copyOf(rewardActions);
            this.rollbackActions = rollbackActions == null ? List.of() : List.copyOf(rollbackActions);
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

        public List<String> getRewardActions() {
            return rewardActions;
        }

        public List<String> getRollbackActions() {
            return rollbackActions;
        }
    }

    private static final class SnapshotCaseItem extends CaseItem {
        private SnapshotCaseItem(String id, String name, List<String> actions, List<String> rollbackActions, boolean rare) {
            super(id, name, null, actions, rollbackActions, List.of(), java.util.Map.of(), 0, rare);
        }
    }

    public static final class MutationResult {
        private final boolean success;
        private final String message;
        private final AuditEntry entry;

        private MutationResult(boolean success, String message, AuditEntry entry) {
            this.success = success;
            this.message = message == null ? "" : message;
            this.entry = entry;
        }

        public static MutationResult success(String message, AuditEntry entry) {
            return new MutationResult(true, message, entry);
        }

        public static MutationResult failed(String message) {
            return new MutationResult(false, message, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public AuditEntry getEntry() {
            return entry;
        }
    }

    private static final class LookupResult {
        private final AuditEntry entry;
        private final boolean ambiguous;

        private LookupResult(AuditEntry entry, boolean ambiguous) {
            this.entry = entry;
            this.ambiguous = ambiguous;
        }
    }
}
