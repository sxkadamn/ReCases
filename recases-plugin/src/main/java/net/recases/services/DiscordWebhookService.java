package net.recases.services;

import net.recases.app.PluginContext;
import net.recases.management.CaseItem;
import net.recases.management.OpeningSession;
import net.recases.runtime.CaseRuntime;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
    }

    public void notifyReward(Player player, CaseRuntime runtime, OpeningSession session, CaseItem reward) {
        if (!enabled || url.isEmpty()) {
            return;
        }
        if ((!reward.isRare() || !notifyRare) && (!session.isGuaranteedReward() || !notifyGuaranteed)) {
            return;
        }

        String title;
        if (session.isGuaranteedReward() && reward.isRare()) {
            title = "Guaranteed rare reward";
        } else if (session.isGuaranteedReward()) {
            title = "Guaranteed reward";
        } else {
            title = "Rare reward";
        }

        String payload = "{"
                + "\"username\":\"" + escape(username) + "\","
                + "\"avatar_url\":\"" + escape(avatarUrl) + "\","
                + "\"content\":\"\","
                + "\"embeds\":[{"
                + "\"title\":\"" + escape(title) + "\","
                + "\"color\":" + (session.isGuaranteedReward() ? 16766720 : 7929855) + ","
                + "\"description\":\""
                + escape("Player: " + player.getName()
                        + "\nCase: " + session.getSelectedCase()
                        + "\nInstance: " + runtime.getId()
                        + "\nReward: " + reward.getName()
                        + "\nRare: " + reward.isRare()
                        + "\nGuaranteed: " + session.isGuaranteedReward())
                + "\""
                + "}]"
                + "}";

        executor.execute(() -> send(payload));
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

    private String escape(String value) {
        String source = value == null ? "" : value;
        return source.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
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
}
