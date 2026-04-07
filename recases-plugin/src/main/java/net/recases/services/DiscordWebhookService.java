package net.recases.services;

import net.recases.app.PluginContext;
import net.recases.management.CaseItem;
import net.recases.management.OpeningSession;
import net.recases.runtime.CaseRuntime;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DiscordWebhookService implements AutoCloseable {

    private final PluginContext plugin;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "recases-webhook");
        thread.setDaemon(true);
        return thread;
    });
    private HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private boolean enabled;
    private String url;
    private String username;
    private String avatarUrl;
    private boolean notifyRare;
    private boolean notifyGuaranteed;
    private int rareColor;
    private int guaranteedColor;
    private int defaultColor;
    private String thumbnailUrl;
    private final List<WebhookLink> links = new ArrayList<>();

    public DiscordWebhookService(PluginContext plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("settings.webhooks.discord.enabled", false);
        url = plugin.getConfig().getString("settings.webhooks.discord.url", "").trim();
        username = plugin.getConfig().getString("settings.webhooks.discord.username", "ReCases");
        avatarUrl = plugin.getConfig().getString("settings.webhooks.discord.avatar-url", "").trim();
        notifyRare = plugin.getConfig().getBoolean("settings.webhooks.discord.notify-rare", true);
        notifyGuaranteed = plugin.getConfig().getBoolean("settings.webhooks.discord.notify-guaranteed", true);
        rareColor = plugin.getConfig().getInt("settings.webhooks.discord.embed.rare-color", 7929855);
        guaranteedColor = plugin.getConfig().getInt("settings.webhooks.discord.embed.guaranteed-color", 16766720);
        defaultColor = plugin.getConfig().getInt("settings.webhooks.discord.embed.default-color", 7631988);
        thumbnailUrl = plugin.getConfig().getString("settings.webhooks.discord.embed.thumbnail-url", "").trim();

        links.clear();
        ConfigurationSection linksSection = plugin.getConfig().getConfigurationSection("settings.webhooks.discord.links");
        if (linksSection != null) {
            for (String id : linksSection.getKeys(false)) {
                String label = linksSection.getString(id + ".label", "").trim();
                String targetUrl = linksSection.getString(id + ".url", "").trim();
                if (!label.isEmpty() && !targetUrl.isEmpty()) {
                    links.add(new WebhookLink(label, targetUrl));
                }
            }
        }
    }

    public void notifyReward(Player player, CaseRuntime runtime, OpeningSession session, CaseItem reward) {
        if (!shouldNotify(session.isGuaranteedReward(), reward.isRare())) {
            return;
        }
        executor.execute(() -> send(buildPayload(NotificationContext.live(player, runtime, session, reward, resolveServerId()))));
    }

    public void notifyRecoveredReward(Player player, RewardAuditService.PendingOpening opening, CaseItem reward) {
        if (!shouldNotify(opening.isGuaranteedReward(), reward.isRare())) {
            return;
        }
        executor.execute(() -> send(buildPayload(NotificationContext.recovered(player, opening, reward))));
    }

    private boolean shouldNotify(boolean guaranteed, boolean rare) {
        if (!enabled || url.isEmpty()) {
            return false;
        }
        if (guaranteed && notifyGuaranteed) {
            return true;
        }
        return rare && notifyRare;
    }

    private String buildPayload(NotificationContext context) {
        StringBuilder payload = new StringBuilder(1024);
        payload.append('{');
        payload.append("\"username\":\"").append(escape(username)).append("\",");
        payload.append("\"avatar_url\":\"").append(escape(avatarUrl)).append("\",");
        payload.append("\"content\":\"\",");
        payload.append("\"embeds\":[{");
        payload.append("\"title\":\"").append(escape(resolveTitle(context))).append("\",");
        payload.append("\"color\":").append(resolveColor(context)).append(',');
        payload.append("\"description\":\"").append(escape(resolveDescription(context))).append("\",");
        appendThumbnail(payload, context);
        appendFields(payload, context);
        payload.append("\"footer\":{\"text\":\"").append(escape("Server: " + context.serverId)).append("\"}");
        payload.append("}]");
        appendComponents(payload, context);
        payload.append('}');
        return payload.toString();
    }

    private void appendThumbnail(StringBuilder payload, NotificationContext context) {
        String resolvedThumbnail = replaceTokens(thumbnailUrl, context);
        if (!resolvedThumbnail.isEmpty()) {
            payload.append(",\"thumbnail\":{\"url\":\"").append(escape(resolvedThumbnail)).append("\"}");
        }
    }

    private void appendFields(StringBuilder payload, NotificationContext context) {
        payload.append(",\"fields\":[");
        appendField(payload, "Player", context.playerName, true, true);
        appendField(payload, "Case", context.caseProfile, true, false);
        appendField(payload, "Instance", context.runtimeId, true, false);
        appendField(payload, "Animation", context.animationId, true, false);
        appendField(payload, "Reward", context.rewardName, false, false);
        appendField(payload, "Reward Id", context.rewardId, true, false);
        appendField(payload, "Transaction", context.transactionId, false, false);
        appendField(payload, "Pity", String.valueOf(context.pityBefore), true, false);
        appendField(payload, "Guaranteed", String.valueOf(context.guaranteed), true, false);
        appendField(payload, "Recovered", String.valueOf(context.recovered), true, false);
        payload.append(']');
    }

    private void appendField(StringBuilder payload, String name, String value, boolean inline, boolean first) {
        if (!first) {
            payload.append(',');
        }
        payload.append('{')
                .append("\"name\":\"").append(escape(name)).append("\",")
                .append("\"value\":\"").append(escape(value == null || value.isEmpty() ? "-" : value)).append("\",")
                .append("\"inline\":").append(inline)
                .append('}');
    }

    private void appendComponents(StringBuilder payload, NotificationContext context) {
        if (links.isEmpty()) {
            return;
        }

        payload.append(",\"components\":[{\"type\":1,\"components\":[");
        boolean first = true;
        for (WebhookLink link : links) {
            String resolvedUrl = replaceTokens(link.url, context);
            if (resolvedUrl.isEmpty()) {
                continue;
            }
            if (!first) {
                payload.append(',');
            }
            first = false;
            payload.append('{')
                    .append("\"type\":2,")
                    .append("\"style\":5,")
                    .append("\"label\":\"").append(escape(link.label)).append("\",")
                    .append("\"url\":\"").append(escape(resolvedUrl)).append("\"")
                    .append('}');
        }
        payload.append("]}]");
    }

    private String resolveTitle(NotificationContext context) {
        if (context.recovered) {
            return context.guaranteed ? "Recovered guaranteed reward" : "Recovered reward";
        }
        if (context.guaranteed && context.rare) {
            return "Guaranteed rare reward";
        }
        if (context.guaranteed) {
            return "Guaranteed reward";
        }
        if (context.rare) {
            return "Rare reward";
        }
        return "Reward";
    }

    private String resolveDescription(NotificationContext context) {
        return context.playerName + " opened " + context.caseProfile + " and received " + context.rewardName;
    }

    private int resolveColor(NotificationContext context) {
        if (context.guaranteed) {
            return guaranteedColor;
        }
        if (context.rare) {
            return rareColor;
        }
        return defaultColor;
    }

    private void send(String payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            client.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (IOException | InterruptedException | IllegalArgumentException exception) {
            plugin.getLogger().warning("Failed to send Discord webhook: " + exception.getMessage());
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private String replaceTokens(String value, NotificationContext context) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }

        return value
                .replace("%player%", context.playerName)
                .replace("%player_uuid%", context.playerId)
                .replace("%case%", context.caseProfile)
                .replace("%instance%", context.runtimeId)
                .replace("%animation%", context.animationId)
                .replace("%reward%", context.rewardName)
                .replace("%reward_id%", context.rewardId)
                .replace("%transaction%", context.transactionId)
                .replace("%server%", context.serverId);
    }

    private String escape(String value) {
        String source = value == null ? "" : value;
        return source.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private String resolveServerId() {
        String configured = plugin.getConfig().getString("settings.server-id", "").trim();
        return configured.isEmpty() ? "default" : configured;
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class WebhookLink {
        private final String label;
        private final String url;

        private WebhookLink(String label, String url) {
            this.label = label;
            this.url = url;
        }
    }

    private static final class NotificationContext {
        private final String playerName;
        private final String playerId;
        private final String caseProfile;
        private final String runtimeId;
        private final String animationId;
        private final String rewardId;
        private final String rewardName;
        private final String transactionId;
        private final String serverId;
        private final boolean rare;
        private final boolean guaranteed;
        private final boolean recovered;
        private final int pityBefore;

        private NotificationContext(String playerName, String playerId, String caseProfile, String runtimeId, String animationId,
                                    String rewardId, String rewardName, String transactionId, String serverId,
                                    boolean rare, boolean guaranteed, boolean recovered, int pityBefore) {
            this.playerName = playerName;
            this.playerId = playerId;
            this.caseProfile = caseProfile;
            this.runtimeId = runtimeId;
            this.animationId = animationId;
            this.rewardId = rewardId;
            this.rewardName = rewardName;
            this.transactionId = transactionId;
            this.serverId = serverId;
            this.rare = rare;
            this.guaranteed = guaranteed;
            this.recovered = recovered;
            this.pityBefore = pityBefore;
        }

        private static NotificationContext live(Player player, CaseRuntime runtime, OpeningSession session, CaseItem reward, String serverId) {
            return new NotificationContext(
                    player.getName(),
                    player.getUniqueId().toString(),
                    session.getSelectedCase(),
                    runtime.getId(),
                    session.getAnimationId(),
                    reward.getId(),
                    reward.getName(),
                    session.getTransactionId().toString(),
                    serverId,
                    reward.isRare(),
                    session.isGuaranteedReward(),
                    false,
                    session.getPityBeforeOpen()
            );
        }

        private static NotificationContext recovered(Player player, RewardAuditService.PendingOpening opening, CaseItem reward) {
            return new NotificationContext(
                    player.getName(),
                    player.getUniqueId().toString(),
                    opening.getCaseProfile(),
                    opening.getRuntimeId(),
                    opening.getAnimationId(),
                    reward.getId(),
                    reward.getName(),
                    opening.getTransactionId().toString(),
                    opening.getServerId(),
                    reward.isRare(),
                    opening.isGuaranteedReward(),
                    true,
                    opening.getPityBefore()
            );
        }
    }
}
