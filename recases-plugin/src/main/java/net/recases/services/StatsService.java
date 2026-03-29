package net.recases.services;

import net.recases.app.PluginContext;
import net.recases.domain.CaseProfile;
import net.recases.management.CaseItem;
import net.recases.stats.LeaderboardEntry;
import net.recases.stats.LeaderboardType;
import net.recases.stats.PlayerStatsRecord;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class StatsService implements AutoCloseable {

    private final PluginContext plugin;
    private final File file;
    private final Map<UUID, PlayerStatsRecord> records = new LinkedHashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "recases-stats-writer");
        thread.setDaemon(true);
        return thread;
    });

    public StatsService(PluginContext plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
    }

    public synchronized void reload() {
        records.clear();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (config.getConfigurationSection("players") == null) {
            return;
        }

        for (String rawUuid : config.getConfigurationSection("players").getKeys(false)) {
            UUID playerId;
            try {
                playerId = UUID.fromString(rawUuid);
            } catch (IllegalArgumentException exception) {
                continue;
            }

            String basePath = "players." + rawUuid;
            PlayerStatsRecord record = new PlayerStatsRecord(playerId);
            record.setPlayerName(config.getString(basePath + ".name", playerId.toString()));
            record.setTotalOpens(Math.max(0, config.getInt(basePath + ".totals.opens", 0)));
            record.setTotalRareWins(Math.max(0, config.getInt(basePath + ".totals.rare-wins", 0)));
            record.setTotalGuaranteedWins(Math.max(0, config.getInt(basePath + ".totals.guaranteed-wins", 0)));
            record.setLastRewardName(config.getString(basePath + ".last-reward.name", ""));
            record.setLastRewardProfile(config.getString(basePath + ".last-reward.profile", ""));

            if (config.getConfigurationSection(basePath + ".profiles") != null) {
                for (String profileId : config.getConfigurationSection(basePath + ".profiles").getKeys(false)) {
                    PlayerStatsRecord.ProfileStatsRecord profile = record.getOrCreateProfile(profileId);
                    String profilePath = basePath + ".profiles." + profileId;
                    profile.setOpens(Math.max(0, config.getInt(profilePath + ".opens", 0)));
                    profile.setRareWins(Math.max(0, config.getInt(profilePath + ".rare-wins", 0)));
                    profile.setGuaranteedWins(Math.max(0, config.getInt(profilePath + ".guaranteed-wins", 0)));
                    profile.setPity(Math.max(0, config.getInt(profilePath + ".pity", 0)));
                    profile.setLastRewardName(config.getString(profilePath + ".last-reward", ""));
                }
            }

            records.put(playerId, record);
        }
    }

    public synchronized void recordOpening(Player player, String profileId, CaseItem reward, boolean guaranteed) {
        PlayerStatsRecord record = getOrCreateRecord(player.getUniqueId(), player.getName());
        record.setPlayerName(player.getName());
        record.setTotalOpens(record.getTotalOpens() + 1);
        record.setLastRewardName(reward.getName());
        record.setLastRewardProfile(profileId.toLowerCase(Locale.ROOT));

        PlayerStatsRecord.ProfileStatsRecord profile = record.getOrCreateProfile(profileId);
        profile.setOpens(profile.getOpens() + 1);
        profile.setLastRewardName(reward.getName());

        if (reward.isRare()) {
            record.setTotalRareWins(record.getTotalRareWins() + 1);
            profile.setRareWins(profile.getRareWins() + 1);
            profile.setPity(0);
        } else {
            profile.setPity(profile.getPity() + 1);
        }

        if (guaranteed) {
            record.setTotalGuaranteedWins(record.getTotalGuaranteedWins() + 1);
            profile.setGuaranteedWins(profile.getGuaranteedWins() + 1);
        }

        saveAsync();
    }

    public synchronized boolean shouldGuarantee(OfflinePlayer player, CaseProfile profile) {
        if (player == null || profile == null || profile.getGuaranteeAfterOpens() <= 0 || !profile.hasRareRewards()) {
            return false;
        }

        return getPity(player, profile.getId()) + 1 >= profile.getGuaranteeAfterOpens();
    }

    public synchronized int getPity(OfflinePlayer player, String profileId) {
        PlayerStatsRecord.ProfileStatsRecord profile = getProfileRecord(player, profileId);
        return profile == null ? 0 : profile.getPity();
    }

    public synchronized int getPityLeft(OfflinePlayer player, CaseProfile profile) {
        if (profile == null || profile.getGuaranteeAfterOpens() <= 0 || !profile.hasRareRewards()) {
            return 0;
        }

        return Math.max(0, profile.getGuaranteeAfterOpens() - getPity(player, profile.getId()));
    }

    public synchronized String getLastRewardName(OfflinePlayer player) {
        PlayerStatsRecord record = getRecord(player);
        return record == null ? "" : safe(record.getLastRewardName());
    }

    public synchronized String getLastRewardName(OfflinePlayer player, String profileId) {
        PlayerStatsRecord.ProfileStatsRecord profile = getProfileRecord(player, profileId);
        return profile == null ? "" : safe(profile.getLastRewardName());
    }

    public synchronized int getOpens(OfflinePlayer player, String profileId) {
        if (profileId == null || profileId.trim().isEmpty()) {
            PlayerStatsRecord record = getRecord(player);
            return record == null ? 0 : record.getTotalOpens();
        }

        PlayerStatsRecord.ProfileStatsRecord profile = getProfileRecord(player, profileId);
        return profile == null ? 0 : profile.getOpens();
    }

    public synchronized int getRareWins(OfflinePlayer player, String profileId) {
        if (profileId == null || profileId.trim().isEmpty()) {
            PlayerStatsRecord record = getRecord(player);
            return record == null ? 0 : record.getTotalRareWins();
        }

        PlayerStatsRecord.ProfileStatsRecord profile = getProfileRecord(player, profileId);
        return profile == null ? 0 : profile.getRareWins();
    }

    public synchronized int getGuaranteedWins(OfflinePlayer player, String profileId) {
        if (profileId == null || profileId.trim().isEmpty()) {
            PlayerStatsRecord record = getRecord(player);
            return record == null ? 0 : record.getTotalGuaranteedWins();
        }

        PlayerStatsRecord.ProfileStatsRecord profile = getProfileRecord(player, profileId);
        return profile == null ? 0 : profile.getGuaranteedWins();
    }

    public synchronized int getGlobalOpens() {
        return records.values().stream().mapToInt(PlayerStatsRecord::getTotalOpens).sum();
    }

    public synchronized int getGlobalRareWins() {
        return records.values().stream().mapToInt(PlayerStatsRecord::getTotalRareWins).sum();
    }

    public synchronized int getGlobalGuaranteedWins() {
        return records.values().stream().mapToInt(PlayerStatsRecord::getTotalGuaranteedWins).sum();
    }

    public synchronized int getTrackedPlayersCount() {
        return records.size();
    }

    public synchronized int getGuaranteeProgressPercent(OfflinePlayer player, CaseProfile profile) {
        if (player == null || profile == null || profile.getGuaranteeAfterOpens() <= 0 || !profile.hasRareRewards()) {
            return 0;
        }

        int pity = getPity(player, profile.getId());
        return Math.min(100, (int) Math.round((pity * 100.0D) / profile.getGuaranteeAfterOpens()));
    }

    public synchronized List<LeaderboardEntry> getLeaderboard(LeaderboardType type, String profileId, int limit) {
        int normalizedLimit = Math.max(1, limit);
        return records.values().stream()
                .map(record -> {
                    int value = getValue(record, type, profileId);
                    if (value <= 0) {
                        return null;
                    }

                    String playerName = safe(record.getPlayerName());
                    return new LeaderboardEntry(record.getPlayerId(), playerName.isEmpty() ? record.getPlayerId().toString() : playerName, value);
                })
                .filter(entry -> entry != null)
                .sorted(Comparator.comparingInt(LeaderboardEntry::getValue).reversed()
                        .thenComparing(LeaderboardEntry::getPlayerName, String.CASE_INSENSITIVE_ORDER))
                .limit(normalizedLimit)
                .collect(Collectors.toList());
    }

    public synchronized Collection<String> getTrackedPlayerNames() {
        return records.values().stream()
                .map(PlayerStatsRecord::getPlayerName)
                .filter(name -> name != null && !name.trim().isEmpty())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    @Override
    public void close() {
        saveAsync();
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
                writer.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }
    }

    private PlayerStatsRecord getOrCreateRecord(UUID playerId, String playerName) {
        PlayerStatsRecord record = records.get(playerId);
        if (record == null) {
            record = new PlayerStatsRecord(playerId);
            record.setPlayerName(playerName == null || playerName.trim().isEmpty() ? playerId.toString() : playerName);
            records.put(playerId, record);
        }
        return record;
    }

    private PlayerStatsRecord getRecord(OfflinePlayer player) {
        if (player == null) {
            return null;
        }
        return records.get(player.getUniqueId());
    }

    private PlayerStatsRecord.ProfileStatsRecord getProfileRecord(OfflinePlayer player, String profileId) {
        PlayerStatsRecord record = getRecord(player);
        if (record == null || profileId == null || profileId.trim().isEmpty()) {
            return null;
        }
        return record.getProfile(profileId.toLowerCase(Locale.ROOT));
    }

    private int getValue(PlayerStatsRecord record, LeaderboardType type, String profileId) {
        if (profileId != null && !profileId.trim().isEmpty()) {
            PlayerStatsRecord.ProfileStatsRecord profile = record.getProfile(profileId.toLowerCase(Locale.ROOT));
            if (profile == null) {
                return 0;
            }

            switch (type) {
                case RARE:
                    return profile.getRareWins();
                case GUARANTEED:
                    return profile.getGuaranteedWins();
                case OPENS:
                default:
                    return profile.getOpens();
            }
        }

        switch (type) {
            case RARE:
                return record.getTotalRareWins();
            case GUARANTEED:
                return record.getTotalGuaranteedWins();
            case OPENS:
            default:
                return record.getTotalOpens();
        }
    }

    private void saveAsync() {
        YamlConfiguration snapshot = buildSnapshot();
        if (writer.isShutdown()) {
            saveSnapshot(snapshot);
            return;
        }
        writer.execute(() -> saveSnapshot(snapshot));
    }

    private synchronized YamlConfiguration buildSnapshot() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        YamlConfiguration config = new YamlConfiguration();
        for (PlayerStatsRecord record : records.values()) {
            String basePath = "players." + record.getPlayerId();
            config.set(basePath + ".name", record.getPlayerName());
            config.set(basePath + ".totals.opens", record.getTotalOpens());
            config.set(basePath + ".totals.rare-wins", record.getTotalRareWins());
            config.set(basePath + ".totals.guaranteed-wins", record.getTotalGuaranteedWins());
            config.set(basePath + ".last-reward.name", record.getLastRewardName());
            config.set(basePath + ".last-reward.profile", record.getLastRewardProfile());

            for (Map.Entry<String, PlayerStatsRecord.ProfileStatsRecord> entry : record.getProfileStats().entrySet()) {
                String profilePath = basePath + ".profiles." + entry.getKey();
                PlayerStatsRecord.ProfileStatsRecord profile = entry.getValue();
                config.set(profilePath + ".opens", profile.getOpens());
                config.set(profilePath + ".rare-wins", profile.getRareWins());
                config.set(profilePath + ".guaranteed-wins", profile.getGuaranteedWins());
                config.set(profilePath + ".pity", profile.getPity());
                config.set(profilePath + ".last-reward", profile.getLastRewardName());
            }
        }
        return config;
    }

    private void saveSnapshot(YamlConfiguration config) {
        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save stats.yml: " + exception.getMessage());
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}

