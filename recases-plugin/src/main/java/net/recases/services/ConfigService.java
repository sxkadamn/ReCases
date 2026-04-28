package net.recases.services;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ConfigService {

    private static final int CURRENT_VERSION = 13;
    private static final Set<String> KNOWN_ANIMATIONS = Set.of(
            "classic",
            "circle",
            "meteor-drop",
            "void-rift",
            "wheel",
            "sphere",
            "neural",
            "swords",
            "anchor-rise",
            "rainly"
    );
    private static final Set<String> KNOWN_PERFORMANCE_PROFILES = Set.of("pretty", "balanced", "lite");
    private static final Set<String> KNOWN_RECOVERY_MODES = Set.of("grant-if-possible", "refund");
    private static final Set<String> KNOWN_BEDROCK_MENU_MODES = Set.of("preview-first", "inventory");

    private final JavaPlugin plugin;

    public ConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        try {
            plugin.reloadConfig();
        } catch (RuntimeException exception) {
            recoverBrokenConfig(exception);
            plugin.reloadConfig();
        }
        migrateIfNeeded();
        validate();
    }

    private void migrateIfNeeded() {
        int version = plugin.getConfig().getInt("config-version", 1);
        if (version >= CURRENT_VERSION) {
            return;
        }

        backupConfig(version);
        if (plugin.getConfig().getConfigurationSection("cases.instances") == null) {
            plugin.getConfig().set("cases.instances.main.location.world", plugin.getConfig().getString("case.spawn.world", "world"));
            plugin.getConfig().set("cases.instances.main.location.x", plugin.getConfig().getDouble("case.spawn.x", 0.0));
            plugin.getConfig().set("cases.instances.main.location.y", plugin.getConfig().getDouble("case.spawn.y", 64.0));
            plugin.getConfig().set("cases.instances.main.location.z", plugin.getConfig().getDouble("case.spawn.z", 0.0));
            plugin.getConfig().set("cases.instances.main.hologram.lines", plugin.getConfig().getStringList("case.hologram.lines"));
        }

        if (plugin.getConfig().getConfigurationSection("profiles") == null) {
            ConfigurationSection menuItems = plugin.getConfig().getConfigurationSection("menus.case-selector.items");
            ConfigurationSection rewards = plugin.getConfig().getConfigurationSection("rewards");
            if (menuItems != null && rewards != null) {
                for (String profileId : menuItems.getKeys(false)) {
                    plugin.getConfig().set("profiles." + profileId + ".menu", menuItems.getConfigurationSection(profileId));
                    plugin.getConfig().set("profiles." + profileId + ".rewards", rewards.getConfigurationSection(profileId + ".items"));
                }
            }
        }

        if (plugin.getConfig().getConfigurationSection("settings.schematics") == null) {
            plugin.getConfig().set("settings.schematics.enabled", false);
            plugin.getConfig().set("settings.schematics.folder", "schematics");
            plugin.getConfig().set("settings.schematics.animations.classic.enabled", false);
            plugin.getConfig().set("settings.schematics.animations.classic.file", "classic-stage.schem");
            plugin.getConfig().set("settings.schematics.animations.classic.offset.x", -2);
            plugin.getConfig().set("settings.schematics.animations.classic.offset.y", -1);
            plugin.getConfig().set("settings.schematics.animations.classic.offset.z", -2);
            plugin.getConfig().set("settings.schematics.animations.classic.ignore-air", false);
        }

        if (plugin.getConfig().getConfigurationSection("settings.locale") == null) {
            plugin.getConfig().set("settings.locale.default", "ru");
        }

        if (plugin.getConfig().getConfigurationSection("settings.webhooks.discord") == null) {
            plugin.getConfig().set("settings.webhooks.discord.enabled", false);
            plugin.getConfig().set("settings.webhooks.discord.url", "");
            plugin.getConfig().set("settings.webhooks.discord.username", "ReCases");
            plugin.getConfig().set("settings.webhooks.discord.avatar-url", "");
            plugin.getConfig().set("settings.webhooks.discord.notify-rare", true);
            plugin.getConfig().set("settings.webhooks.discord.notify-guaranteed", true);
        }

        if (plugin.getConfig().getConfigurationSection("settings.updater") == null) {
            plugin.getConfig().set("settings.updater.enabled", false);
            plugin.getConfig().set("settings.updater.check-on-startup", true);
            plugin.getConfig().set("settings.updater.notify-admins-on-join", true);
            plugin.getConfig().set("settings.updater.auto-download", false);
            plugin.getConfig().set("settings.updater.resource-id", 0);
            plugin.getConfig().set("settings.updater.timeout-seconds", 10);
            plugin.getConfig().set("settings.updater.download-url", "");
        }

        if (plugin.getConfig().getConfigurationSection("settings.network-sync") == null) {
            plugin.getConfig().set("settings.network-sync.enabled", false);
            plugin.getConfig().set("settings.network-sync.poll-interval-seconds", 15);
            plugin.getConfig().set("settings.network-sync.sync-keys", true);
            plugin.getConfig().set("settings.network-sync.sync-stats", true);
        }

        if (plugin.getConfig().getConfigurationSection("settings.redis") == null) {
            plugin.getConfig().set("settings.redis.enabled", false);
            plugin.getConfig().set("settings.redis.host", "127.0.0.1");
            plugin.getConfig().set("settings.redis.port", 6379);
            plugin.getConfig().set("settings.redis.password", "");
            plugin.getConfig().set("settings.redis.database", 0);
            plugin.getConfig().set("settings.redis.timeout-millis", 2000);
            plugin.getConfig().set("settings.redis.channel-prefix", "recases");
            plugin.getConfig().set("settings.redis.publish-keys", true);
            plugin.getConfig().set("settings.redis.publish-stats", true);
            plugin.getConfig().set("settings.redis.subscriber-reconnect-seconds", 5);
            plugin.getConfig().set("settings.redis.lock-ttl-seconds", 180);
        }

        if (plugin.getConfig().getConfigurationSection("settings.bedrock") == null) {
            plugin.getConfig().set("settings.bedrock.enabled", false);
            plugin.getConfig().set("settings.bedrock.menu-mode", "preview-first");
            plugin.getConfig().set("settings.bedrock.force-simple-animations", true);
            plugin.getConfig().set("settings.bedrock.simplify-heavy-animations", true);
            plugin.getConfig().set("settings.bedrock.max-selections", 1);
            plugin.getConfig().set("settings.bedrock.fallback-animation", "anchor-rise");
        }

        if (plugin.getConfig().getConfigurationSection("settings.integrations.custom-items") == null) {
            plugin.getConfig().set("settings.integrations.custom-items.itemsadder.enabled", true);
            plugin.getConfig().set("settings.integrations.custom-items.oraxen.enabled", true);
            plugin.getConfig().set("settings.integrations.custom-items.nexo.enabled", true);
            plugin.getConfig().set("settings.integrations.custom-items.mmoitems.enabled", true);
        }

        if (plugin.getConfig().getConfigurationSection("settings.integrations.discord-bot") == null) {
            plugin.getConfig().set("settings.integrations.discord-bot.enabled", false);
            plugin.getConfig().set("settings.integrations.discord-bot.token", "");
            plugin.getConfig().set("settings.integrations.discord-bot.guild-id", "");
            plugin.getConfig().set("settings.integrations.discord-bot.moderation-channel-id", "");
            plugin.getConfig().set("settings.integrations.discord-bot.allowed-role-ids", List.of());
        }

        if (plugin.getConfig().getConfigurationSection("settings.metrics.bstats") == null) {
            plugin.getConfig().set("settings.metrics.bstats.enabled", false);
            plugin.getConfig().set("settings.metrics.bstats.plugin-id", 0);
        }

        if (plugin.getConfig().getConfigurationSection("settings.presets") == null) {
            plugin.getConfig().set("settings.presets.folder", "presets");
        }

        if (plugin.getConfig().getConfigurationSection("settings.animations.performance") == null) {
            plugin.getConfig().set("settings.animations.performance.profile", "balanced");
            plugin.getConfig().set("settings.animations.performance.adaptive-load", true);
        }

        if (plugin.getConfig().getConfigurationSection("settings.animations.dynamic-selection") == null) {
            plugin.getConfig().set("settings.animations.dynamic-selection.enabled", true);
            plugin.getConfig().set("settings.animations.dynamic-selection.max-player-distance", 24.0D);
            plugin.getConfig().set("settings.animations.dynamic-selection.active-openings-threshold", 4);
            plugin.getConfig().set("settings.animations.dynamic-selection.min-tps", 18.5D);
            plugin.getConfig().set("settings.animations.dynamic-selection.distant-fallback", "classic");
            plugin.getConfig().set("settings.animations.dynamic-selection.overload-fallback", "classic");
            plugin.getConfig().set("settings.animations.dynamic-selection.medium-overload-fallback", "circle");
        }

        if (!plugin.getConfig().contains("settings.server-id")) {
            plugin.getConfig().set("settings.server-id", "default");
        }

        if (plugin.getConfig().getConfigurationSection("settings.opening-recovery") == null) {
            plugin.getConfig().set("settings.opening-recovery.mode", "grant-if-possible");
        }

        if (plugin.getConfig().getConfigurationSection("settings.webhooks.discord.embed") == null) {
            plugin.getConfig().set("settings.webhooks.discord.embed.rare-color", 7929855);
            plugin.getConfig().set("settings.webhooks.discord.embed.guaranteed-color", 16766720);
            plugin.getConfig().set("settings.webhooks.discord.embed.default-color", 7631988);
            plugin.getConfig().set("settings.webhooks.discord.embed.thumbnail-url", "");
        }

        migrateRewardActions();
        migratePityCurves();
        plugin.getConfig().set("config-version", CURRENT_VERSION);
        ((net.recases.app.PluginContext) plugin).saveConfigUtf8();
    }

    private void recoverBrokenConfig(RuntimeException cause) {
        Path dataFolder = plugin.getDataFolder().toPath();
        Path configPath = dataFolder.resolve("config.yml");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path backupPath = dataFolder.resolve("config.invalid-" + timestamp + ".yml");

        try {
            Files.createDirectories(dataFolder);
            if (Files.exists(configPath)) {
                Files.move(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            }
            try (InputStream inputStream = plugin.getResource("config.yml")) {
                if (inputStream == null) {
                    throw new IOException("Default config.yml resource is missing");
                }
                Files.copy(inputStream, configPath, StandardCopyOption.REPLACE_EXISTING);
            }
            plugin.getLogger().severe("config.yml was not valid UTF-8/YAML and has been replaced. Backup: " + backupPath.getFileName());
        } catch (IOException ioException) {
            throw new UncheckedIOException("Failed to recover broken config.yml", ioException);
        }
    }

    private void validate() {
        List<String> warnings = collectValidationWarnings();
        if (warnings.isEmpty()) {
            plugin.getLogger().info("Config validation passed: no issues found.");
            return;
        }

        plugin.getLogger().warning("Config validation found " + warnings.size() + " issue(s):");
        for (String warning : warnings) {
            plugin.getLogger().warning(" - " + warning);
        }
    }

    public List<String> collectValidationWarnings() {
        List<String> warnings = new ArrayList<>();
        validateAnimationSettings(warnings);

        ConfigurationSection profiles = plugin.getConfig().getConfigurationSection("profiles");
        if (profiles == null || profiles.getKeys(false).isEmpty()) {
            warnings.add("No case profiles configured.");
        } else {
            for (String profileId : profiles.getKeys(false)) {
                String materialName = plugin.getConfig().getString("profiles." + profileId + ".menu.material", "CHEST");
                if (!isValidMaterialDefinition(materialName, true)) {
                    warnings.add("Profile '" + profileId + "' has invalid menu material '" + materialName + "'.");
                }
                String profileAnimation = plugin.getConfig().getString("profiles." + profileId + ".animation", "classic");
                if (!isKnownAnimation(profileAnimation)) {
                    warnings.add("Profile '" + profileId + "' has unknown animation '" + profileAnimation + "'.");
                }
                int slot = plugin.getConfig().getInt("profiles." + profileId + ".menu.slot", -1);
                if (slot < 0 || slot > 53) {
                    warnings.add("Profile '" + profileId + "' has invalid menu slot '" + slot + "'.");
                }

                ConfigurationSection rewards = plugin.getConfig().getConfigurationSection("profiles." + profileId + ".rewards");
                if (rewards == null || rewards.getKeys(false).isEmpty()) {
                    warnings.add("Profile '" + profileId + "' has no rewards section.");
                    continue;
                }

                int guaranteeAfterOpens = plugin.getConfig().getInt("profiles." + profileId + ".guarantee.after-opens", 0);
                if (guaranteeAfterOpens < 0) {
                    warnings.add("Profile '" + profileId + "' has negative guarantee.after-opens.");
                }

                boolean hasRareRewards = false;

                for (String rewardId : rewards.getKeys(false)) {
                    String materialDefinition = plugin.getConfig().getString("profiles." + profileId + ".rewards." + rewardId + ".material", "");
                    if (materialDefinition.trim().isEmpty()) {
                        warnings.add("Reward '" + rewardId + "' in profile '" + profileId + "' has empty material.");
                    } else if (!isValidMaterialDefinition(materialDefinition, true)) {
                        warnings.add("Reward '" + rewardId + "' in profile '" + profileId + "' has invalid material '" + materialDefinition + "'.");
                    }

                    int chance = plugin.getConfig().getInt("profiles." + profileId + ".rewards." + rewardId + ".chance", 0);
                    if (chance <= 0) {
                        warnings.add("Reward '" + rewardId + "' in profile '" + profileId + "' has non-positive chance.");
                    }

                    if (plugin.getConfig().getBoolean("profiles." + profileId + ".rewards." + rewardId + ".rare", false)) {
                        hasRareRewards = true;
                    }
                }

                if (guaranteeAfterOpens > 0 && !hasRareRewards) {
                    warnings.add("Profile '" + profileId + "' has guarantee configured but no rare rewards.");
                }
            }
        }

        ConfigurationSection instances = plugin.getConfig().getConfigurationSection("cases.instances");
        if (instances == null || instances.getKeys(false).isEmpty()) {
            warnings.add("No physical case instances configured.");
        } else {
            for (String instanceId : instances.getKeys(false)) {
                String world = plugin.getConfig().getString("cases.instances." + instanceId + ".location.world", "");
                if (world.trim().isEmpty()) {
                    warnings.add("Instance '" + instanceId + "' has no world configured.");
                }
                String instanceAnimation = plugin.getConfig().getString("cases.instances." + instanceId + ".animation", "");
                if (!instanceAnimation.trim().isEmpty() && !isKnownAnimation(instanceAnimation)) {
                    warnings.add("Instance '" + instanceId + "' has unknown animation '" + instanceAnimation + "'.");
                }
            }
        }

        ConfigurationSection leaderboardHolograms = plugin.getConfig().getConfigurationSection("leaderboards.holograms");
        if (leaderboardHolograms != null) {
            for (String hologramId : leaderboardHolograms.getKeys(false)) {
                ConfigurationSection views = plugin.getConfig().getConfigurationSection("leaderboards.holograms." + hologramId + ".views");
                if (views != null && !views.getKeys(false).isEmpty()) {
                    for (String viewId : views.getKeys(false)) {
                        validateLeaderboardView(warnings, hologramId + "/" + viewId, views.getConfigurationSection(viewId));
                    }
                    continue;
                }

                validateLeaderboardView(warnings, hologramId, leaderboardHolograms.getConfigurationSection(hologramId));
            }
        }

        if (plugin.getConfig().getBoolean("settings.updater.enabled", false)) {
            int resourceId = plugin.getConfig().getInt("settings.updater.resource-id", 0);
            String downloadUrl = plugin.getConfig().getString("settings.updater.download-url", "").trim();
            if (resourceId <= 0) {
                warnings.add("Updater is enabled but settings.updater.resource-id is not set.");
            }
            if (plugin.getConfig().getBoolean("settings.updater.auto-download", false)
                    && resourceId <= 0
                    && downloadUrl.isEmpty()) {
                warnings.add("Updater auto-download is enabled but no resource-id or direct download-url is configured.");
            }
        }

        if (plugin.getConfig().getBoolean("settings.network-sync.enabled", false)) {
            if (!"mysql".equalsIgnoreCase(plugin.getConfig().getString("settings.storage.type", "sqlite"))) {
                warnings.add("Network sync is enabled but storage.type is not mysql.");
            }
            if (plugin.getConfig().getInt("settings.network-sync.poll-interval-seconds", 15) < 1) {
                warnings.add("settings.network-sync.poll-interval-seconds must be at least 1.");
            }
        }

        if (plugin.getConfig().getBoolean("settings.redis.enabled", false)) {
            if (!"mysql".equalsIgnoreCase(plugin.getConfig().getString("settings.storage.type", "sqlite"))) {
                warnings.add("Redis sync is enabled but storage.type is not mysql.");
            }
            if (plugin.getConfig().getString("settings.redis.host", "").trim().isEmpty()) {
                warnings.add("settings.redis.host cannot be empty.");
            }
            if (plugin.getConfig().getInt("settings.redis.port", 6379) <= 0) {
                warnings.add("settings.redis.port must be greater than 0.");
            }
            if (plugin.getConfig().getInt("settings.redis.lock-ttl-seconds", 180) < 30) {
                warnings.add("settings.redis.lock-ttl-seconds must be at least 30.");
            }
        }

        String bedrockMenuMode = plugin.getConfig().getString("settings.bedrock.menu-mode", "preview-first").trim().toLowerCase();
        if (!KNOWN_BEDROCK_MENU_MODES.contains(bedrockMenuMode)) {
            warnings.add("settings.bedrock.menu-mode must be one of: preview-first, inventory.");
        }
        if (plugin.getConfig().getInt("settings.bedrock.max-selections", 1) < 0) {
            warnings.add("settings.bedrock.max-selections cannot be negative.");
        }
        String bedrockFallback = plugin.getConfig().getString("settings.bedrock.fallback-animation", "anchor-rise");
        if (!isKnownAnimation(bedrockFallback)) {
            warnings.add("settings.bedrock.fallback-animation must reference a known animation.");
        }

        if (plugin.getConfig().getBoolean("settings.metrics.bstats.enabled", false)
                && plugin.getConfig().getInt("settings.metrics.bstats.plugin-id", 0) <= 0) {
            warnings.add("bStats is enabled but settings.metrics.bstats.plugin-id is not set.");
        }

        if (plugin.getConfig().getBoolean("settings.integrations.discord-bot.enabled", false)
                && plugin.getConfig().getString("settings.integrations.discord-bot.token", "").trim().isEmpty()) {
            warnings.add("Discord bot is enabled but settings.integrations.discord-bot.token is empty.");
        }

        if (plugin.getConfig().getString("settings.presets.folder", "presets").trim().isEmpty()) {
            warnings.add("settings.presets.folder cannot be empty.");
        }

        String recoveryMode = plugin.getConfig().getString("settings.opening-recovery.mode", "grant-if-possible").trim().toLowerCase();
        if (!KNOWN_RECOVERY_MODES.contains(recoveryMode)) {
            warnings.add("settings.opening-recovery.mode must be one of: grant-if-possible, refund.");
        }
        if (plugin.getConfig().getString("settings.server-id", "default").trim().isEmpty()) {
            warnings.add("settings.server-id cannot be empty.");
        }

        return warnings;
    }

    private void migrateRewardActions() {
        ConfigurationSection profiles = plugin.getConfig().getConfigurationSection("profiles");
        if (profiles == null) {
            return;
        }

        for (String profileId : profiles.getKeys(false)) {
            ConfigurationSection rewards = plugin.getConfig().getConfigurationSection("profiles." + profileId + ".rewards");
            if (rewards == null) {
                continue;
            }

            for (String rewardId : rewards.getKeys(false)) {
                String basePath = "profiles." + profileId + ".rewards." + rewardId;
                if (plugin.getConfig().isList(basePath + ".actions")) {
                    continue;
                }
                if (plugin.getConfig().isList(basePath + ".commands")) {
                    plugin.getConfig().set(basePath + ".actions", plugin.getConfig().getStringList(basePath + ".commands"));
                    plugin.getConfig().set(basePath + ".commands", null);
                }
            }
        }
    }

    private void migratePityCurves() {
        ConfigurationSection profiles = plugin.getConfig().getConfigurationSection("profiles");
        if (profiles == null) {
            return;
        }

        for (String profileId : profiles.getKeys(false)) {
            String basePath = "profiles." + profileId + ".guarantee.curve";
            if (!plugin.getConfig().contains(basePath + ".enabled")) {
                plugin.getConfig().set(basePath + ".enabled", true);
            }
            if (!plugin.getConfig().contains(basePath + ".start-after")) {
                plugin.getConfig().set(basePath + ".start-after", 0);
            }
            if (!plugin.getConfig().contains(basePath + ".max-rare-weight-multiplier")) {
                plugin.getConfig().set(basePath + ".max-rare-weight-multiplier", 3.5D);
            }
            if (!plugin.getConfig().contains(basePath + ".exponent")) {
                plugin.getConfig().set(basePath + ".exponent", 1.4D);
            }
            if (!plugin.getConfig().contains("profiles." + profileId + ".conditions")) {
                plugin.getConfig().set("profiles." + profileId + ".conditions", List.of());
            }
            if (!plugin.getConfig().contains("profiles." + profileId + ".triggers")) {
                plugin.getConfig().set("profiles." + profileId + ".triggers", null);
            }

            ConfigurationSection rewards = plugin.getConfig().getConfigurationSection("profiles." + profileId + ".rewards");
            if (rewards == null) {
                continue;
            }
            for (String rewardId : rewards.getKeys(false)) {
                String rewardPath = "profiles." + profileId + ".rewards." + rewardId;
                if (!plugin.getConfig().contains(rewardPath + ".conditions")) {
                    plugin.getConfig().set(rewardPath + ".conditions", List.of());
                }
                if (!plugin.getConfig().contains(rewardPath + ".rollback-actions")) {
                    plugin.getConfig().set(rewardPath + ".rollback-actions", List.of());
                }
            }
        }
    }

    private boolean isKnownAnimation(String animationId) {
        String value = animationId == null ? "" : animationId.trim().toLowerCase();
        return value.isEmpty() || KNOWN_ANIMATIONS.contains(value);
    }

    private boolean isValidMaterialDefinition(String definition, boolean allowHeads) {
        if (definition == null || definition.trim().isEmpty()) {
            return false;
        }

        String normalized = definition.trim();
        if (!normalized.contains(";")) {
            return Material.matchMaterial(normalized) != null;
        }

        String prefix = normalized.substring(0, normalized.indexOf(';')).trim().toLowerCase();
        if ("item".equals(prefix)) {
            return Material.matchMaterial(normalized.substring(normalized.indexOf(';') + 1).trim()) != null;
        }
        if ("head".equals(prefix)) {
            return allowHeads;
        }
        return "itemsadder".equals(prefix)
                || "oraxen".equals(prefix)
                || "nexo".equals(prefix)
                || "mmoitems".equals(prefix);
    }

    private void validateAnimationSettings(List<String> warnings) {
        double particleIntensity = plugin.getConfig().getDouble("settings.animations.intensity.particles", 1.0D);
        double soundIntensity = plugin.getConfig().getDouble("settings.animations.intensity.sound", 1.0D);
        double shakeAmplitude = plugin.getConfig().getDouble("settings.animations.winner-item.shake-amplitude", 0.06D);
        double levitationHeight = plugin.getConfig().getDouble("settings.animations.winner-item.levitation-height", 0.45D);
        double bobStrength = plugin.getConfig().getDouble("settings.animations.winner-item.bob-strength", 0.12D);
        String performanceProfile = plugin.getConfig().getString("settings.animations.performance.profile", "balanced");
        String distantFallback = plugin.getConfig().getString("settings.animations.dynamic-selection.distant-fallback", "classic");
        String overloadFallback = plugin.getConfig().getString("settings.animations.dynamic-selection.overload-fallback", "classic");
        String mediumFallback = plugin.getConfig().getString("settings.animations.dynamic-selection.medium-overload-fallback", "circle");

        if (particleIntensity <= 0.0D) {
            warnings.add("settings.animations.intensity.particles must be greater than 0.");
        }
        if (soundIntensity < 0.0D) {
            warnings.add("settings.animations.intensity.sound cannot be negative.");
        }
        if (shakeAmplitude < 0.0D) {
            warnings.add("settings.animations.winner-item.shake-amplitude cannot be negative.");
        }
        if (levitationHeight < 0.0D) {
            warnings.add("settings.animations.winner-item.levitation-height cannot be negative.");
        }
        if (bobStrength < 0.0D) {
            warnings.add("settings.animations.winner-item.bob-strength cannot be negative.");
        }
        if (!KNOWN_PERFORMANCE_PROFILES.contains(performanceProfile == null ? "" : performanceProfile.trim().toLowerCase())) {
            warnings.add("settings.animations.performance.profile must be one of: pretty, balanced, lite.");
        }
        if (!isKnownAnimation(distantFallback)) {
            warnings.add("settings.animations.dynamic-selection.distant-fallback must reference a known animation.");
        }
        if (!isKnownAnimation(overloadFallback)) {
            warnings.add("settings.animations.dynamic-selection.overload-fallback must reference a known animation.");
        }
        if (!isKnownAnimation(mediumFallback)) {
            warnings.add("settings.animations.dynamic-selection.medium-overload-fallback must reference a known animation.");
        }

        ConfigurationSection schematicAnimations = plugin.getConfig().getConfigurationSection("settings.schematics.animations");
        if (schematicAnimations != null) {
            for (String animationId : schematicAnimations.getKeys(false)) {
                ConfigurationSection scene = schematicAnimations.getConfigurationSection(animationId);
                if (scene == null || !scene.getBoolean("enabled", false)) {
                    continue;
                }

                String file = scene.getString("file", "").trim();
                if (file.isEmpty()) {
                    warnings.add("Schematic scene '" + animationId + "' is enabled but file is empty.");
                }
            }
        }
    }

    private void validateLeaderboardView(List<String> warnings, String viewLabel, ConfigurationSection section) {
        if (section == null) {
            warnings.add("Leaderboard hologram '" + viewLabel + "' is missing configuration.");
            return;
        }

        String type = section.getString("type", "opens");
        if (!"opens".equalsIgnoreCase(type) && !"rare".equalsIgnoreCase(type) && !"guaranteed".equalsIgnoreCase(type)) {
            warnings.add("Leaderboard hologram '" + viewLabel + "' has unknown type '" + type + "'.");
        }

        String profile = section.getString("profile", "").trim();
        if (!profile.isEmpty() && !plugin.getConfig().contains("profiles." + profile)) {
            warnings.add("Leaderboard hologram '" + viewLabel + "' references missing profile '" + profile + "'.");
        }
    }

    private void backupConfig(int version) {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Files.copy(
                    plugin.getDataFolder().toPath().resolve("config.yml"),
                    plugin.getDataFolder().toPath().resolve("config.v" + version + "." + timestamp + ".bak"),
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to create config backup: " + exception.getMessage());
        }
    }
}

