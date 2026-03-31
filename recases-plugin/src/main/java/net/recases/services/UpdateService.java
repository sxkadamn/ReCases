package net.recases.services;

import net.recases.app.PluginContext;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class UpdateService implements AutoCloseable {

    private static final String LEGACY_VERSION_ENDPOINT = "https://api.spigotmc.org/legacy/update.php?resource=%d";
    private static final String SPIGET_DOWNLOAD_ENDPOINT = "https://api.spiget.org/v2/resources/%d/download";
    private static final String SPIGOT_RESOURCE_URL = "https://www.spigotmc.org/resources/%d/";
    private static final Pattern VERSION_SPLIT = Pattern.compile("[^a-z0-9]+");

    private final PluginContext plugin;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "recases-updater");
        thread.setDaemon(true);
        return thread;
    });
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    private boolean enabled;
    private boolean checkOnStartup;
    private boolean notifyAdminsOnJoin;
    private boolean autoDownload;
    private int resourceId;
    private int timeoutSeconds;
    private String downloadUrl = "";
    private volatile String latestVersion = "";
    private volatile boolean updateAvailable;
    private volatile boolean updateDownloaded;

    public UpdateService(PluginContext plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("settings.updater.enabled", false);
        checkOnStartup = plugin.getConfig().getBoolean("settings.updater.check-on-startup", true);
        notifyAdminsOnJoin = plugin.getConfig().getBoolean("settings.updater.notify-admins-on-join", true);
        autoDownload = plugin.getConfig().getBoolean("settings.updater.auto-download", false);
        resourceId = Math.max(0, plugin.getConfig().getInt("settings.updater.resource-id", 0));
        timeoutSeconds = Math.max(5, plugin.getConfig().getInt("settings.updater.timeout-seconds", 10));
        downloadUrl = plugin.getConfig().getString("settings.updater.download-url", "").trim();

        if (!enabled) {
            latestVersion = "";
            updateAvailable = false;
            updateDownloaded = false;
            return;
        }

        if (resourceId <= 0) {
            plugin.getLogger().warning("Updater enabled, but settings.updater.resource-id is not set.");
            return;
        }

        if (checkOnStartup) {
            checkForUpdatesAsync();
        }
    }

    public void checkForUpdatesAsync() {
        if (!enabled || resourceId <= 0 || executor.isShutdown()) {
            return;
        }

        executor.execute(this::checkForUpdates);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean shouldNotifyAdminsOnJoin() {
        return enabled && notifyAdminsOnJoin;
    }

    public boolean isAutoDownloadEnabled() {
        return autoDownload;
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public boolean isUpdateDownloaded() {
        return updateDownloaded;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getResourceUrl() {
        if (resourceId <= 0) {
            return "";
        }
        return String.format(Locale.ROOT, SPIGOT_RESOURCE_URL, resourceId);
    }

    private void checkForUpdates() {
        String currentVersion = safe(plugin.getDescription().getVersion());
        String remoteVersion = fetchLatestVersion();
        if (remoteVersion.isEmpty()) {
            plugin.getLogger().warning("Could not check updates from Spigot.");
            return;
        }

        latestVersion = remoteVersion;
        if (!isRemoteNewer(currentVersion, remoteVersion)) {
            updateAvailable = false;
            updateDownloaded = false;
            plugin.getLogger().info("ReCases updater: you are using the latest version (" + currentVersion + ").");
            return;
        }

        updateAvailable = true;
        plugin.getLogger().warning("ReCases update available: " + currentVersion + " -> " + remoteVersion
                + " (" + getResourceUrl() + ")");

        if (!autoDownload) {
            return;
        }

        if (downloadUpdateJar()) {
            updateDownloaded = true;
            plugin.getLogger().warning("ReCases update downloaded to /plugins/update. Restart server to apply.");
        }
    }

    private String fetchLatestVersion() {
        String endpoint = String.format(Locale.ROOT, LEGACY_VERSION_ENDPOINT, resourceId);
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("User-Agent", "ReCases-Updater/" + safe(plugin.getDescription().getVersion()))
                    .GET()
                    .build();
        } catch (IllegalArgumentException exception) {
            return "";
        }

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                plugin.getLogger().warning("Updater request failed with HTTP " + response.statusCode());
                return "";
            }

            String body = safe(response.body()).trim();
            if (body.contains("\n")) {
                body = body.substring(0, body.indexOf('\n')).trim();
            }
            return body;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            plugin.getLogger().warning("Updater check failed: " + exception.getMessage());
            return "";
        }
    }

    private boolean downloadUpdateJar() {
        String endpoint = downloadUrl.isEmpty()
                ? String.format(Locale.ROOT, SPIGET_DOWNLOAD_ENDPOINT, resourceId)
                : downloadUrl;
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("User-Agent", "ReCases-Updater/" + safe(plugin.getDescription().getVersion()))
                    .GET()
                    .build();
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Updater download URL is invalid: " + endpoint);
            return false;
        }

        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / 100 != 2) {
                plugin.getLogger().warning("Updater download failed with HTTP " + response.statusCode());
                return false;
            }

            Path updateDirectory = plugin.getDataFolder().toPath().getParent().resolve("update");
            Files.createDirectories(updateDirectory);

            String fileName = plugin.getDescription().getName() + ".jar";
            Path target = updateDirectory.resolve(fileName);
            Path temporary = updateDirectory.resolve(fileName + ".tmp");

            try (InputStream body = response.body()) {
                Files.copy(body, temporary, StandardCopyOption.REPLACE_EXISTING);
            }

            if (!looksLikeJar(temporary)) {
                Files.deleteIfExists(temporary);
                plugin.getLogger().warning("Downloaded updater file is not a valid jar. Check resource permissions on Spigot.");
                return false;
            }

            moveReplace(temporary, target);
            return true;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            plugin.getLogger().warning("Updater download failed: " + exception.getMessage());
            return false;
        }
    }

    private boolean looksLikeJar(Path file) throws IOException {
        if (!Files.exists(file) || Files.size(file) < 4) {
            return false;
        }

        byte[] header = new byte[4];
        try (InputStream input = Files.newInputStream(file)) {
            if (input.read(header) < 4) {
                return false;
            }
        }
        return header[0] == 'P' && header[1] == 'K';
    }

    private void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean isRemoteNewer(String currentVersion, String remoteVersion) {
        if (currentVersion.equalsIgnoreCase(remoteVersion)) {
            return false;
        }

        int comparison = compareVersions(currentVersion, remoteVersion);
        if (comparison != 0) {
            return comparison < 0;
        }

        return !currentVersion.equalsIgnoreCase(remoteVersion);
    }

    private int compareVersions(String leftRaw, String rightRaw) {
        List<String> left = splitVersion(leftRaw);
        List<String> right = splitVersion(rightRaw);
        int max = Math.max(left.size(), right.size());

        for (int index = 0; index < max; index++) {
            String leftToken = index < left.size() ? left.get(index) : "0";
            String rightToken = index < right.size() ? right.get(index) : "0";

            int tokenCompare = compareVersionToken(leftToken, rightToken);
            if (tokenCompare != 0) {
                return tokenCompare;
            }
        }

        return 0;
    }

    private List<String> splitVersion(String version) {
        String normalized = safe(version).toLowerCase(Locale.ROOT).trim();
        if (normalized.isEmpty()) {
            return List.of("0");
        }

        String[] rawTokens = VERSION_SPLIT.split(normalized);
        List<String> tokens = new ArrayList<>(rawTokens.length);
        for (String token : rawTokens) {
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens.isEmpty() ? List.of("0") : tokens;
    }

    private int compareVersionToken(String left, String right) {
        boolean leftNumeric = isNumeric(left);
        boolean rightNumeric = isNumeric(right);
        if (leftNumeric && rightNumeric) {
            return compareNumeric(left, right);
        }

        if (!leftNumeric && !rightNumeric) {
            int leftWeight = qualifierWeight(left);
            int rightWeight = qualifierWeight(right);
            if (leftWeight != rightWeight) {
                return Integer.compare(leftWeight, rightWeight);
            }
            return left.compareTo(right);
        }

        return leftNumeric ? 1 : -1;
    }

    private int compareNumeric(String left, String right) {
        String normalizedLeft = stripLeadingZeros(left);
        String normalizedRight = stripLeadingZeros(right);
        if (normalizedLeft.length() != normalizedRight.length()) {
            return Integer.compare(normalizedLeft.length(), normalizedRight.length());
        }
        return normalizedLeft.compareTo(normalizedRight);
    }

    private String stripLeadingZeros(String value) {
        int index = 0;
        while (index < value.length() - 1 && value.charAt(index) == '0') {
            index++;
        }
        return value.substring(index);
    }

    private int qualifierWeight(String token) {
        if (token.startsWith("snapshot") || token.startsWith("dev")) {
            return -5;
        }
        if (token.startsWith("alpha") || "a".equals(token)) {
            return -4;
        }
        if (token.startsWith("beta") || "b".equals(token)) {
            return -3;
        }
        if (token.startsWith("pre") || token.startsWith("preview")) {
            return -2;
        }
        if (token.startsWith("rc")) {
            return -1;
        }
        if (token.startsWith("release") || token.startsWith("final") || token.startsWith("stable")) {
            return 0;
        }
        return 0;
    }

    private boolean isNumeric(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return !value.isEmpty();
    }

    private String safe(String value) {
        return value == null ? "" : value;
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
