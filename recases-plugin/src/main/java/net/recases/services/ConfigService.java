package net.recases.services;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ConfigService {

    private static final int CURRENT_VERSION = 5;
    private static final Set<String> KNOWN_ANIMATIONS = Set.of(
            "classic",
            "circle",
            "meteor-drop",
            "void-rift",
            "wheel",
            "swords",
            "anchor-rise",
            "rainly"
    );

    private final JavaPlugin plugin;

    public ConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
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

        migrateRewardActions();
        plugin.getConfig().set("config-version", CURRENT_VERSION);
        plugin.saveConfig();
    }

    private void validate() {
        List<String> warnings = new ArrayList<>();

        validateAnimationSettings(warnings);

        ConfigurationSection profiles = plugin.getConfig().getConfigurationSection("profiles");
        if (profiles == null || profiles.getKeys(false).isEmpty()) {
            warnings.add("No case profiles configured.");
        } else {
            for (String profileId : profiles.getKeys(false)) {
                String materialName = plugin.getConfig().getString("profiles." + profileId + ".menu.material", "CHEST");
                if (Material.matchMaterial(materialName) == null) {
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
                    } else if (materialDefinition.toLowerCase().startsWith("item;")) {
                        String material = materialDefinition.substring(5);
                        if (Material.matchMaterial(material) == null) {
                            warnings.add("Reward '" + rewardId + "' in profile '" + profileId + "' has invalid material '" + material + "'.");
                        }
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

        if (warnings.isEmpty()) {
            plugin.getLogger().info("Config validation passed: no issues found.");
            return;
        }

        plugin.getLogger().warning("Config validation found " + warnings.size() + " issue(s):");
        for (String warning : warnings) {
            plugin.getLogger().warning(" - " + warning);
        }
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

    private boolean isKnownAnimation(String animationId) {
        String value = animationId == null ? "" : animationId.trim().toLowerCase();
        return value.isEmpty() || KNOWN_ANIMATIONS.contains(value);
    }

    private void validateAnimationSettings(List<String> warnings) {
        double particleIntensity = plugin.getConfig().getDouble("settings.animations.intensity.particles", 1.0D);
        double soundIntensity = plugin.getConfig().getDouble("settings.animations.intensity.sound", 1.0D);
        double shakeAmplitude = plugin.getConfig().getDouble("settings.animations.winner-item.shake-amplitude", 0.06D);
        double levitationHeight = plugin.getConfig().getDouble("settings.animations.winner-item.levitation-height", 0.45D);
        double bobStrength = plugin.getConfig().getDouble("settings.animations.winner-item.bob-strength", 0.12D);

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

