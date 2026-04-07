package net.recases.services;

import net.recases.app.PluginContext;
import net.recases.protocollib.hologram.Hologram;
import net.recases.protocollib.hologram.HologramLine;
import net.recases.stats.LeaderboardEntry;
import net.recases.stats.LeaderboardType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class LeaderboardHologramService implements AutoCloseable {

    public static final String LEADERBOARD_HOLOGRAM_METADATA = "recases_leaderboard_hologram";

    private final PluginContext plugin;
    private final TextFormatter textFormatter;
    private final List<LeaderboardHologram> holograms = new ArrayList<>();
    private final Map<String, LeaderboardHologram> hologramsById = new LinkedHashMap<>();
    private BukkitTask updateTask;
    private BukkitTask queuedRefreshTask;

    public LeaderboardHologramService(PluginContext plugin, TextFormatter textFormatter) {
        this.plugin = plugin;
        this.textFormatter = textFormatter;
    }

    public void reload() {
        close();

        ConfigurationSection root = plugin.getConfig().getConfigurationSection("leaderboards.holograms");
        if (root == null) {
            return;
        }

        for (String id : root.getKeys(false)) {
            String basePath = "leaderboards.holograms." + id;
            LeaderboardHologram hologram = createHologram(id, basePath);
            if (hologram == null) {
                continue;
            }

            holograms.add(hologram);
            hologramsById.put(id.toLowerCase(Locale.ROOT), hologram);
            hologram.spawn();
        }

        if (!holograms.isEmpty()) {
            long intervalTicks = Math.max(20L, plugin.getConfig().getLong("leaderboards.update-interval-seconds", 30L) * 20L);
            updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, intervalTicks, intervalTicks);
        }
    }

    public void refreshAll() {
        if (holograms.isEmpty()) {
            return;
        }

        List<LeaderboardHologram> snapshot = new ArrayList<>(holograms);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<LeaderboardUpdate> updates = snapshot.stream()
                    .map(LeaderboardHologram::createUpdate)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (!plugin.isEnabled()) {
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> updates.forEach(LeaderboardUpdate::apply));
        });
    }

    public void requestRefresh() {
        if (holograms.isEmpty()) {
            return;
        }
        if (queuedRefreshTask != null) {
            return;
        }

        queuedRefreshTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            queuedRefreshTask = null;
            refreshAll();
        }, 20L);
    }

    public boolean handleInteraction(Entity entity, Player player) {
        if (entity == null || !entity.hasMetadata(LEADERBOARD_HOLOGRAM_METADATA)) {
            return false;
        }

        String hologramId = entity.getMetadata(LEADERBOARD_HOLOGRAM_METADATA).get(0).asString().toLowerCase(Locale.ROOT);
        LeaderboardHologram hologram = hologramsById.get(hologramId);
        if (hologram == null) {
            return false;
        }

        hologram.nextView();
        if (player != null) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1.2F);
        }
        return true;
    }

    public String getCurrentViewId(String hologramId) {
        LeaderboardHologram hologram = getHologram(hologramId);
        return hologram == null ? "" : hologram.getCurrentViewId();
    }

    public String getCurrentScope(String hologramId) {
        LeaderboardHologram hologram = getHologram(hologramId);
        return hologram == null ? "" : hologram.getCurrentScope();
    }

    public String getCurrentType(String hologramId) {
        LeaderboardHologram hologram = getHologram(hologramId);
        return hologram == null ? "" : hologram.getCurrentType();
    }

    public String getNextScope(String hologramId) {
        LeaderboardHologram hologram = getHologram(hologramId);
        return hologram == null ? "" : hologram.getNextScopeValue();
    }

    @Override
    public void close() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        if (queuedRefreshTask != null) {
            queuedRefreshTask.cancel();
            queuedRefreshTask = null;
        }

        for (LeaderboardHologram hologram : holograms) {
            hologram.remove();
        }
        holograms.clear();
        hologramsById.clear();
    }

    private LeaderboardHologram createHologram(String id, String basePath) {
        String worldName = plugin.getConfig().getString(basePath + ".location.world", "");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Skipped leaderboard hologram '" + id + "': world '" + worldName + "' is missing.");
            return null;
        }

        Location location = new Location(
                world,
                plugin.getConfig().getDouble(basePath + ".location.x"),
                plugin.getConfig().getDouble(basePath + ".location.y"),
                plugin.getConfig().getDouble(basePath + ".location.z")
        );

        List<LeaderboardView> views = readViews(id, basePath);
        if (views.isEmpty()) {
            plugin.getLogger().warning("Skipped leaderboard hologram '" + id + "': no valid views configured.");
            return null;
        }

        return new LeaderboardHologram(id, location, views);
    }

    private LeaderboardHologram getHologram(String hologramId) {
        if (hologramId == null) {
            return null;
        }
        return hologramsById.get(hologramId.toLowerCase(Locale.ROOT));
    }

    private List<LeaderboardView> readViews(String id, String basePath) {
        List<LeaderboardView> result = new ArrayList<>();
        ConfigurationSection viewsSection = plugin.getConfig().getConfigurationSection(basePath + ".views");
        if (viewsSection != null) {
            for (String viewId : viewsSection.getKeys(false)) {
                LeaderboardView view = createView(id + ":" + viewId, basePath + ".views." + viewId);
                if (view != null) {
                    result.add(view);
                }
            }
            return result;
        }

        LeaderboardView singleView = createView(id, basePath);
        if (singleView != null) {
            result.add(singleView);
        }
        return result;
    }

    private LeaderboardView createView(String viewId, String basePath) {
        LeaderboardType type = LeaderboardType.fromId(plugin.getConfig().getString(basePath + ".type", "opens"));
        if (type == null) {
            plugin.getLogger().warning("Skipped leaderboard view '" + viewId + "': unknown type.");
            return null;
        }

        String profileId = plugin.getConfig().getString(basePath + ".profile", "").trim().toLowerCase(Locale.ROOT);
        if (!profileId.isEmpty() && !plugin.getCaseService().hasProfile(profileId)) {
            plugin.getLogger().warning("Skipped leaderboard view '" + viewId + "': profile '" + profileId + "' is missing.");
            return null;
        }

        int limit = Math.max(1, Math.min(10, plugin.getConfig().getInt(basePath + ".limit", 10)));
        List<String> headerLines = plugin.getConfig().getStringList(basePath + ".header");
        List<String> lineFormat = plugin.getConfig().getStringList(basePath + ".lines");
        if (lineFormat.isEmpty()) {
            lineFormat = Collections.singletonList("#a8dadc#%position% #ffffff%player% #ffd166- %value%");
        }

        List<String> emptyLines = plugin.getConfig().getStringList(basePath + ".empty-lines");
        if (emptyLines.isEmpty()) {
            emptyLines = Collections.singletonList("#ff6b6bNo data yet");
        }

        return new LeaderboardView(viewId, type, profileId, limit, headerLines, lineFormat, emptyLines);
    }

    private final class LeaderboardHologram {
        private final String id;
        private final Location location;
        private final List<LeaderboardView> views;
        private int currentViewIndex;
        private Hologram hologram;

        private LeaderboardHologram(String id, Location location, List<LeaderboardView> views) {
            this.id = id;
            this.location = location.clone();
            this.views = new ArrayList<>(views);
        }

        private void spawn() {
            hologram = new Hologram(plugin, buildLines(views.get(currentViewIndex), currentViewIndex), location);
            hologram.spawn();
            applyMetadata();
        }

        private void refresh() {
            LeaderboardUpdate update = createUpdate();
            if (update != null) {
                update.apply();
            }
        }

        private void nextView() {
            if (views.size() <= 1) {
                refresh();
                return;
            }

            currentViewIndex = (currentViewIndex + 1) % views.size();
            refresh();
        }

        private void remove() {
            if (hologram != null) {
                hologram.remove();
                hologram = null;
            }
        }

        private String getCurrentViewId() {
            return views.get(currentViewIndex).id;
        }

        private String getCurrentScope() {
            LeaderboardView view = views.get(currentViewIndex);
            return view.profileId.isEmpty() ? "global" : view.profileId;
        }

        private String getCurrentType() {
            return views.get(currentViewIndex).type.getId();
        }

        private String getNextScopeValue() {
            return views.size() <= 1 ? getCurrentScope() : nextScope();
        }

        private synchronized LeaderboardUpdate createUpdate() {
            return new LeaderboardUpdate(this, currentViewIndex, buildLines(views.get(currentViewIndex), currentViewIndex));
        }

        private List<String> buildLines(LeaderboardView view, int viewIndex) {
            List<String> result = new ArrayList<>();
            String scope = view.profileId.isEmpty() ? "global" : view.profileId;
            String nextScope = views.size() <= 1 ? scope : nextScope(viewIndex);

            for (String line : view.headerLines) {
                result.add(format(line, 0, "", 0, scope, nextScope, view.type));
            }

            List<LeaderboardEntry> entries = plugin.getStats().getLeaderboard(view.type, view.profileId.isEmpty() ? null : view.profileId, view.limit);
            if (entries.isEmpty()) {
                for (String line : view.emptyLines) {
                    result.add(format(line, 0, "", 0, scope, nextScope, view.type));
                }
                return colorize(result);
            }

            int position = 1;
            for (LeaderboardEntry entry : entries) {
                for (String line : view.lineFormat) {
                    result.add(format(line, position, entry.getPlayerName(), entry.getValue(), scope, nextScope, view.type));
                }
                position++;
            }

            return colorize(result);
        }

        private synchronized void applyUpdate(LeaderboardUpdate update) {
            if (update.viewIndex != currentViewIndex && views.size() > 1) {
                return;
            }

            if (hologram == null) {
                hologram = new Hologram(plugin, update.lines, location);
                hologram.spawn();
            } else {
                hologram.setLines(update.lines);
            }
            applyMetadata();
        }

        private void applyMetadata() {
            if (hologram == null) {
                return;
            }

            for (HologramLine line : hologram.getHologramLines()) {
                if (line.getStand() != null) {
                    line.getStand().setMetadata(LEADERBOARD_HOLOGRAM_METADATA, new FixedMetadataValue(plugin, id));
                }
            }
        }

        private List<String> colorize(List<String> lines) {
            List<String> result = new ArrayList<>(lines.size());
            for (String line : lines) {
                result.add(textFormatter.colorize(line));
            }
            return result;
        }

        private String format(String line, int position, String player, int value, String scope, String nextScope, LeaderboardType type) {
            return line
                    .replace("%id%", id)
                    .replace("%type%", type.getId())
                    .replace("%scope%", scope)
                    .replace("%next_scope%", nextScope)
                    .replace("%position%", String.valueOf(position))
                    .replace("%player%", player)
                    .replace("%value%", String.valueOf(value));
        }

        private String nextScope() {
            return nextScope(currentViewIndex);
        }

        private String nextScope(int viewIndex) {
            LeaderboardView nextView = views.get((viewIndex + 1) % views.size());
            return nextView.profileId.isEmpty() ? "global" : nextView.profileId;
        }
    }

    private static final class LeaderboardUpdate {
        private final LeaderboardHologram hologram;
        private final int viewIndex;
        private final List<String> lines;

        private LeaderboardUpdate(LeaderboardHologram hologram, int viewIndex, List<String> lines) {
            this.hologram = hologram;
            this.viewIndex = viewIndex;
            this.lines = lines;
        }

        private void apply() {
            hologram.applyUpdate(this);
        }
    }

    private static final class LeaderboardView {
        private final String id;
        private final LeaderboardType type;
        private final String profileId;
        private final int limit;
        private final List<String> headerLines;
        private final List<String> lineFormat;
        private final List<String> emptyLines;

        private LeaderboardView(String id, LeaderboardType type, String profileId, int limit, List<String> headerLines,
                                List<String> lineFormat, List<String> emptyLines) {
            this.id = id;
            this.type = type;
            this.profileId = profileId;
            this.limit = limit;
            this.headerLines = new ArrayList<>(headerLines);
            this.lineFormat = new ArrayList<>(lineFormat);
            this.emptyLines = new ArrayList<>(emptyLines);
        }
    }
}

