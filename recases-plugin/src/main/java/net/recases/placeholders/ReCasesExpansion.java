package net.recases.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.recases.ReCases;
import net.recases.domain.CaseProfile;
import net.recases.stats.LeaderboardEntry;
import net.recases.stats.LeaderboardType;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public class ReCasesExpansion extends PlaceholderExpansion {

    private final ReCases plugin;

    public ReCasesExpansion(ReCases plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "recases";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        String normalized = params.toLowerCase(Locale.ROOT);

        if ("last_reward".equals(normalized)) {
            return emptyIfBlank(player == null ? "" : plugin.getStats().getLastRewardName(player));
        }
        if ("global_opens".equals(normalized)) {
            return String.valueOf(plugin.getStats().getGlobalOpens());
        }
        if ("global_rare_wins".equals(normalized)) {
            return String.valueOf(plugin.getStats().getGlobalRareWins());
        }
        if ("global_guaranteed_hits".equals(normalized)) {
            return String.valueOf(plugin.getStats().getGlobalGuaranteedWins());
        }
        if ("global_players".equals(normalized)) {
            return String.valueOf(plugin.getStats().getTrackedPlayersCount());
        }
        if ("opens".equals(normalized)) {
            return player == null ? "0" : String.valueOf(plugin.getStats().getOpens(player, null));
        }
        if ("rare_wins".equals(normalized)) {
            return player == null ? "0" : String.valueOf(plugin.getStats().getRareWins(player, null));
        }
        if ("guaranteed_hits".equals(normalized)) {
            return player == null ? "0" : String.valueOf(plugin.getStats().getGuaranteedWins(player, null));
        }

        if (normalized.startsWith("keys_")) {
            return player == null ? "0" : String.valueOf(plugin.getStorage().getCaseAmount(player, normalized.substring("keys_".length())));
        }
        if (normalized.startsWith("last_reward_")) {
            return player == null ? "" : emptyIfBlank(plugin.getStats().getLastRewardName(player, normalized.substring("last_reward_".length())));
        }
        if (normalized.startsWith("opens_")) {
            return player == null ? "0" : String.valueOf(plugin.getStats().getOpens(player, normalized.substring("opens_".length())));
        }
        if (normalized.startsWith("rare_wins_")) {
            return player == null ? "0" : String.valueOf(plugin.getStats().getRareWins(player, normalized.substring("rare_wins_".length())));
        }
        if (normalized.startsWith("guaranteed_hits_")) {
            return player == null ? "0" : String.valueOf(plugin.getStats().getGuaranteedWins(player, normalized.substring("guaranteed_hits_".length())));
        }
        if (normalized.startsWith("pity_")) {
            return player == null ? "0" : String.valueOf(plugin.getStats().getPity(player, normalized.substring("pity_".length())));
        }
        if (normalized.startsWith("pity_left_")) {
            if (player == null) {
                return "0";
            }

            CaseProfile profile = plugin.getCaseService().getProfile(normalized.substring("pity_left_".length()));
            return profile == null ? "0" : String.valueOf(plugin.getStats().getPityLeft(player, profile));
        }
        if (normalized.startsWith("guarantee_chance_")) {
            if (player == null) {
                return "0";
            }

            CaseProfile profile = plugin.getCaseService().getProfile(normalized.substring("guarantee_chance_".length()));
            return profile == null ? "0" : String.valueOf(plugin.getStats().getGuaranteeProgressPercent(player, profile));
        }
        if (normalized.startsWith("hologram_")) {
            return resolveHologram(normalized.substring("hologram_".length()));
        }

        if (normalized.startsWith("top_profile_")) {
            return resolveProfileTop(normalized.substring("top_profile_".length()));
        }
        if (normalized.startsWith("top_")) {
            return resolveGlobalTop(normalized.substring("top_".length()));
        }

        return null;
    }

    private String resolveHologram(String params) {
        String field;
        String hologramId;
        if (params.endsWith("_next_scope")) {
            field = "next_scope";
            hologramId = params.substring(0, params.length() - "_next_scope".length());
        } else if (params.endsWith("_scope")) {
            field = "scope";
            hologramId = params.substring(0, params.length() - "_scope".length());
        } else if (params.endsWith("_type")) {
            field = "type";
            hologramId = params.substring(0, params.length() - "_type".length());
        } else if (params.endsWith("_view")) {
            field = "view";
            hologramId = params.substring(0, params.length() - "_view".length());
        } else {
            return null;
        }

        if ("scope".equals(field)) {
            return plugin.getLeaderboardHolograms().getCurrentScope(hologramId);
        }
        if ("type".equals(field)) {
            return plugin.getLeaderboardHolograms().getCurrentType(hologramId);
        }
        if ("view".equals(field)) {
            return plugin.getLeaderboardHolograms().getCurrentViewId(hologramId);
        }
        if ("next_scope".equals(field)) {
            return plugin.getLeaderboardHolograms().getNextScope(hologramId);
        }
        return null;
    }

    private String resolveGlobalTop(String params) {
        String[] parts = params.split("_");
        if (parts.length != 3) {
            return null;
        }

        LeaderboardType type = LeaderboardType.fromId(parts[0]);
        if (type == null) {
            return null;
        }

        int rank = parseRank(parts[1]);
        if (rank < 1) {
            return null;
        }

        return resolveLeaderboardValue(plugin.getStats().getLeaderboard(type, null, rank), rank, parts[2]);
    }

    private String resolveProfileTop(String params) {
        String[] parts = params.split("_");
        if (parts.length != 4) {
            return null;
        }

        LeaderboardType type = LeaderboardType.fromId(parts[0]);
        if (type == null) {
            return null;
        }

        String profileId = parts[1];
        int rank = parseRank(parts[2]);
        if (rank < 1 || !plugin.getCaseService().hasProfile(profileId)) {
            return null;
        }

        return resolveLeaderboardValue(plugin.getStats().getLeaderboard(type, profileId, rank), rank, parts[3]);
    }

    private String resolveLeaderboardValue(List<LeaderboardEntry> entries, int rank, String part) {
        if (entries.size() < rank) {
            return "";
        }

        LeaderboardEntry entry = entries.get(rank - 1);
        if ("name".equals(part)) {
            return entry.getPlayerName();
        }
        if ("value".equals(part)) {
            return String.valueOf(entry.getValue());
        }
        return null;
    }

    private int parseRank(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private String emptyIfBlank(String value) {
        return value == null ? "" : value;
    }
}
