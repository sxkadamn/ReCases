package net.recases.services;

import net.recases.app.PluginContext;
import net.recases.domain.CaseInstance;
import net.recases.domain.CaseProfile;
import net.recases.management.CaseItem;
import net.recases.management.OpeningSession;
import net.recases.runtime.CaseRuntime;
import net.recases.runtime.registry.EntityRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public class CaseService {

    private final PluginContext plugin;
    private final EntityRegistry entityRegistry;
    private final ItemFactory itemFactory;
    private final TextFormatter textFormatter;
    private final WorldService worldService;
    private final Random random = new Random();
    private final Map<String, CaseProfile> profiles = new LinkedHashMap<>();
    private final Map<String, CaseRuntime> runtimes = new LinkedHashMap<>();

    public CaseService(PluginContext plugin, EntityRegistry entityRegistry, ItemFactory itemFactory, TextFormatter textFormatter, WorldService worldService) {
        this.plugin = plugin;
        this.entityRegistry = entityRegistry;
        this.itemFactory = itemFactory;
        this.textFormatter = textFormatter;
        this.worldService = worldService;
    }

    public void reload() {
        clear();
        loadProfiles();
        loadInstances();
    }

    public void clear() {
        runtimes.values().forEach(runtime -> {
            refundPendingKey(runtime.getSession());
            plugin.getSchematics().cleanup(runtime);
            runtime.remove();
        });
        runtimes.clear();
        profiles.clear();
        entityRegistry.clear();
    }

    public Collection<CaseProfile> getProfiles() {
        return Collections.unmodifiableCollection(profiles.values());
    }

    public List<String> getProfileIds() {
        return profiles.keySet().stream().sorted().collect(Collectors.toList());
    }

    public boolean hasProfile(String profileId) {
        return profiles.containsKey(profileId.toLowerCase());
    }

    public CaseProfile getProfile(String profileId) {
        return profiles.get(profileId.toLowerCase());
    }

    public CaseItem getRandomReward(String profileId) {
        CaseProfile profile = getProfile(profileId);
        return profile == null ? null : profile.pickReward(random);
    }

    public CaseItem getGuaranteedReward(String profileId) {
        CaseProfile profile = getProfile(profileId);
        return profile == null ? null : profile.pickReward(random, true);
    }

    public Collection<CaseRuntime> getRuntimes() {
        return Collections.unmodifiableCollection(runtimes.values());
    }

    public List<String> getRuntimeIds() {
        return runtimes.keySet().stream().sorted().collect(Collectors.toList());
    }

    public CaseRuntime getRuntime(String runtimeId) {
        return runtimes.get(runtimeId.toLowerCase());
    }

    public CaseRuntime getRuntime(Block block) {
        if (block == null || !block.hasMetadata(CaseRuntime.CASE_METADATA)) {
            return null;
        }
        String id = block.getMetadata(CaseRuntime.CASE_METADATA).get(0).asString();
        return getRuntime(id);
    }

    public void abortOpening(CaseRuntime runtime, boolean refundKey) {
        if (runtime == null || !runtime.isOpening()) {
            return;
        }

        OpeningSession session = runtime.getSession();
        if (refundKey) {
            refundPendingKey(session);
        }

        plugin.getSchematics().cleanup(runtime);
        runtime.resetOpeningState();
        if (runtime.getLocation().getWorld() != null) {
            runtime.getLocation().getWorld().playSound(runtime.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
        }
    }

    public void completeOpening(CaseRuntime runtime) {
        abortOpening(runtime, false);
    }

    public boolean beginOpening(Player player, CaseRuntime runtime, String profileId) {
        if (player == null || runtime == null) {
            return false;
        }

        if (!runtime.isAvailable()) {
            plugin.getMessages().send(player, "messages.case-unavailable", "#ff6b6bЭта точка кейса сейчас недоступна. Попробуйте позже или перезагрузите плагин.");
            player.closeInventory();
            return false;
        }

        CaseProfile profile = getProfile(profileId);
        if (profile == null) {
            plugin.getMessages().send(player, "messages.case-not-found", "#ff6b6bПрофиль кейса '#ffffff%case%#ff6b6b' не найден.", "%case%", profileId);
            player.closeInventory();
            return false;
        }

        if (runtime.isOpening()) {
            plugin.getMessages().send(player, "messages.case-busy", "#ff6b6bЭтот кейс уже открывает другой игрок.");
            player.closeInventory();
            return false;
        }

        if (plugin.getStorage().getCaseAmount(player, profileId) <= 0) {
            plugin.getMessages().send(player, "messages.no-keys", "#ff6b6bУ вас нет ключей от этого профиля кейса.");
            player.closeInventory();
            return false;
        }

        CaseItem reward = getRandomReward(profileId);
        if (reward == null) {
            plugin.getMessages().send(player, "messages.case-no-rewards", "#ff6b6bДля этого профиля кейса не настроены награды.");
            player.closeInventory();
            return false;
        }

        boolean guaranteedReward = plugin.getStats().shouldGuarantee(player, profile);
        if (guaranteedReward) {
            reward = getGuaranteedReward(profileId);
            if (reward == null) {
                guaranteedReward = false;
                reward = getRandomReward(profileId);
            }
        }
        if (reward == null) {
            plugin.getMessages().send(player, "messages.case-no-rewards", "#ff6b6bДля этого профиля кейса не настроены награды.");
            player.closeInventory();
            return false;
        }

        String animationId = plugin.getAnimations().resolveAnimationId(runtime, profile);
        String animationName = plugin.getAnimations().getDisplayName(animationId);
        int requiredSelections = plugin.getAnimations().getRequiredSelections(animationId);
        OpeningSession session = new OpeningSession(player, profileId, animationId, requiredSelections, reward, guaranteedReward);
        session.setOpeningAnchor(plugin.getWorldService().createOpeningAnchor(
                runtime.getLocation(),
                player.getLocation(),
                plugin.getConfig().getDouble("settings.opening-guard.owner-anchor-distance", 2.15D)
        ));
        runtime.setSession(session);
        runtime.removeHologram();
        plugin.getSchematics().pasteAnimationScene(session, runtime);
        if (!plugin.getAnimations().create(plugin, player, runtime, profile).play()) {
            plugin.getSchematics().cleanup(runtime);
            runtime.clearSession();
            runtime.spawnHologram();
            plugin.getMessages().send(player, "messages.case-unavailable", "#ff6b6bЭта точка кейса сейчас недоступна. Попробуйте позже или перезагрузите плагин.");
            player.closeInventory();
            return false;
        }

        plugin.getStorage().removeCase(player, profileId, 1);
        session.markKeyConsumed();
        plugin.getMessages().send(
                player,
                "messages.case-opening-started",
                "#ffd166Вы начали открытие профиля #ffffff%case% #ffd166на точке #ffffff%instance% #ffd166с анимацией #ffffff%animation%#ffd166.",
                "%case%", profileId,
                "%instance%", runtime.getId(),
                "%animation%", animationName
        );
        player.closeInventory();
        return true;
    }

    public boolean createProfile(String profileId) {
        String normalized = normalizeId(profileId);
        if (normalized.isEmpty() || plugin.getConfig().contains("profiles." + normalized)) {
            return false;
        }

        String basePath = "profiles." + normalized;
        plugin.getConfig().set(basePath + ".animation", AnimationService.CLASSIC);
        plugin.getConfig().set(basePath + ".guarantee.after-opens", 0);
        plugin.getConfig().set(basePath + ".menu.material", Material.CHEST.name());
        plugin.getConfig().set(basePath + ".menu.slot", 22);
        plugin.getConfig().set(basePath + ".menu.display", "#ffd166" + normalized);
        plugin.getConfig().set(basePath + ".menu.lore", List.of("#a8dadcНовый профиль кейса"));
        plugin.getConfig().set(basePath + ".rewards.example.material", "ITEM;DIAMOND");
        plugin.getConfig().set(basePath + ".rewards.example.name", "#ffffffExample reward");
        plugin.getConfig().set(basePath + ".rewards.example.chance", 10);
        plugin.getConfig().set(basePath + ".rewards.example.rare", false);
        plugin.getConfig().set(basePath + ".rewards.example.actions", List.of("message;#80ed99Пример награды"));
        plugin.saveConfigUtf8();
        plugin.reloadPluginState();
        return true;
    }

    public boolean deleteProfile(String profileId) {
        String normalized = normalizeId(profileId);
        if (!plugin.getConfig().contains("profiles." + normalized)) {
            return false;
        }
        plugin.getConfig().set("profiles." + normalized, null);
        plugin.saveConfigUtf8();
        plugin.reloadPluginState();
        return true;
    }

    public boolean createInstance(String instanceId, Location location) {
        String normalized = normalizeId(instanceId);
        if (normalized.isEmpty() || location == null || location.getWorld() == null || plugin.getConfig().contains("cases.instances." + normalized)) {
            return false;
        }

        String basePath = "cases.instances." + normalized;
        plugin.getConfig().set(basePath + ".animation", "");
        plugin.getConfig().set(basePath + ".location.world", location.getWorld().getName());
        plugin.getConfig().set(basePath + ".location.x", location.getBlockX());
        plugin.getConfig().set(basePath + ".location.y", location.getBlockY());
        plugin.getConfig().set(basePath + ".location.z", location.getBlockZ());
        plugin.getConfig().set(basePath + ".hologram.lines", List.of("#ffd166&lКЕЙС", "#ffffffНажмите, чтобы открыть меню"));
        plugin.saveConfigUtf8();
        plugin.reloadPluginState();
        return true;
    }

    public boolean deleteInstance(String instanceId) {
        String normalized = normalizeId(instanceId);
        if (!plugin.getConfig().contains("cases.instances." + normalized)) {
            return false;
        }
        plugin.getConfig().set("cases.instances." + normalized, null);
        plugin.saveConfigUtf8();
        plugin.reloadPluginState();
        return true;
    }

    public boolean setProfileAnimation(String profileId, String animationId) {
        String normalizedProfile = normalizeId(profileId);
        String normalizedAnimation = normalizeId(animationId);
        if (!plugin.getConfig().contains("profiles." + normalizedProfile) || !plugin.getAnimations().isRegistered(normalizedAnimation)) {
            return false;
        }
        plugin.getConfig().set("profiles." + normalizedProfile + ".animation", normalizedAnimation);
        plugin.saveConfigUtf8();
        plugin.reloadPluginState();
        return true;
    }

    public boolean setInstanceAnimation(String instanceId, String animationId) {
        String normalizedInstance = normalizeId(instanceId);
        if (!plugin.getConfig().contains("cases.instances." + normalizedInstance)) {
            return false;
        }

        String normalizedAnimation = normalizeId(animationId);
        if (!normalizedAnimation.isEmpty() && !plugin.getAnimations().isRegistered(normalizedAnimation)) {
            return false;
        }

        plugin.getConfig().set("cases.instances." + normalizedInstance + ".animation", normalizedAnimation);
        plugin.saveConfigUtf8();
        plugin.reloadPluginState();
        return true;
    }

    public String cycleProfileAnimation(String profileId) {
        String normalizedProfile = normalizeId(profileId);
        String current = plugin.getConfig().getString("profiles." + normalizedProfile + ".animation", AnimationService.CLASSIC);
        List<String> ids = plugin.getAnimations().getRegisteredIds();
        if (ids.isEmpty()) {
            return AnimationService.CLASSIC;
        }
        int index = Math.max(-1, ids.indexOf(current));
        String next = ids.get((index + 1) % ids.size());
        setProfileAnimation(normalizedProfile, next);
        return next;
    }

    public List<String> getRewardIds(String profileId) {
        ConfigurationSection rewards = plugin.getConfig().getConfigurationSection("profiles." + normalizeId(profileId) + ".rewards");
        return rewards == null ? List.of() : rewards.getKeys(false).stream().sorted().collect(Collectors.toList());
    }

    public boolean addReward(String profileId, String rewardId, ItemStack icon, String displayName) {
        String normalizedProfile = normalizeId(profileId);
        String normalizedReward = normalizeId(rewardId);
        if (!plugin.getConfig().contains("profiles." + normalizedProfile) || normalizedReward.isEmpty()) {
            return false;
        }

        String basePath = "profiles." + normalizedProfile + ".rewards." + normalizedReward;
        plugin.getConfig().set(basePath + ".material", itemFactory.serialize(icon));
        plugin.getConfig().set(basePath + ".name", displayName == null || displayName.isEmpty() ? normalizedReward : displayName);
        plugin.getConfig().set(basePath + ".chance", 10);
        plugin.getConfig().set(basePath + ".rare", false);
        plugin.getConfig().set(basePath + ".actions", List.of("message;#80ed99Вы получили " + (displayName == null || displayName.isEmpty() ? normalizedReward : displayName)));
        plugin.saveConfigUtf8();
        plugin.reloadPluginState();
        return true;
    }

    public boolean removeReward(String profileId, String rewardId) {
        String basePath = "profiles." + normalizeId(profileId) + ".rewards." + normalizeId(rewardId);
        if (!plugin.getConfig().contains(basePath)) {
            return false;
        }
        plugin.getConfig().set(basePath, null);
        plugin.saveConfigUtf8();
        plugin.reloadPluginState();
        return true;
    }

    public boolean updateRewardChance(String profileId, String rewardId, int delta) {
        String basePath = "profiles." + normalizeId(profileId) + ".rewards." + normalizeId(rewardId);
        if (!plugin.getConfig().contains(basePath)) {
            return false;
        }
        int current = Math.max(1, plugin.getConfig().getInt(basePath + ".chance", 1));
        plugin.getConfig().set(basePath + ".chance", Math.max(1, current + delta));
        plugin.saveConfigUtf8();
        plugin.reloadPluginState();
        return true;
    }

    public boolean toggleRewardRare(String profileId, String rewardId) {
        String basePath = "profiles." + normalizeId(profileId) + ".rewards." + normalizeId(rewardId);
        if (!plugin.getConfig().contains(basePath)) {
            return false;
        }
        plugin.getConfig().set(basePath + ".rare", !plugin.getConfig().getBoolean(basePath + ".rare", false));
        plugin.saveConfigUtf8();
        plugin.reloadPluginState();
        return true;
    }

    public boolean setRewardIcon(String profileId, String rewardId, ItemStack icon) {
        String basePath = "profiles." + normalizeId(profileId) + ".rewards." + normalizeId(rewardId);
        if (!plugin.getConfig().contains(basePath) || icon == null || icon.getType().isAir()) {
            return false;
        }
        plugin.getConfig().set(basePath + ".material", itemFactory.serialize(icon));
        plugin.saveConfigUtf8();
        plugin.reloadPluginState();
        return true;
    }

    public boolean setRewardDisplayName(String profileId, String rewardId, String displayName) {
        String basePath = "profiles." + normalizeId(profileId) + ".rewards." + normalizeId(rewardId);
        if (!plugin.getConfig().contains(basePath)) {
            return false;
        }
        plugin.getConfig().set(basePath + ".name", displayName);
        plugin.saveConfigUtf8();
        plugin.reloadPluginState();
        return true;
    }

    public List<String> getPresetIds() {
        File[] files = getPresetFolder().listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null || files.length == 0) {
            return List.of();
        }

        List<String> ids = new ArrayList<>(files.length);
        for (File file : files) {
            String name = file.getName();
            ids.add(name.substring(0, name.length() - 4));
        }
        Collections.sort(ids);
        return ids;
    }

    public boolean exportPreset(String profileId, String presetId) {
        String normalizedProfile = normalizeId(profileId);
        String normalizedPreset = normalizeId(presetId);
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("profiles." + normalizedProfile);
        if (section == null || normalizedPreset.isEmpty()) {
            return false;
        }

        File folder = getPresetFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            return false;
        }

        YamlConfiguration preset = new YamlConfiguration();
        preset.set("preset-version", 1);
        ConfigurationSection profileSection = preset.createSection("profile");
        profileSection.set("id", normalizedProfile);
        copySection(section, profileSection);

        try {
            preset.save(new File(folder, normalizedPreset + ".yml"));
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to export preset '" + normalizedPreset + "': " + exception.getMessage());
            return false;
        }
    }

    public boolean importPreset(String presetId, String targetProfileId) {
        String normalizedPreset = normalizeId(presetId);
        if (normalizedPreset.isEmpty()) {
            return false;
        }

        File file = new File(getPresetFolder(), normalizedPreset + ".yml");
        if (!file.isFile()) {
            return false;
        }

        YamlConfiguration preset = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = preset.getConfigurationSection("profile");
        if (section == null) {
            return false;
        }

        String normalizedTarget = normalizeId(targetProfileId);
        if (normalizedTarget.isEmpty()) {
            normalizedTarget = normalizeId(section.getString("id", normalizedPreset));
        }
        if (normalizedTarget.isEmpty() || plugin.getConfig().contains("profiles." + normalizedTarget)) {
            return false;
        }

        String basePath = "profiles." + normalizedTarget;
        plugin.getConfig().set(basePath, null);
        ConfigurationSection target = plugin.getConfig().createSection(basePath);
        copySection(section, target, "id");
        plugin.saveConfigUtf8();
        plugin.reloadPluginState();
        return true;
    }

    public void abortOpeningsFor(OfflinePlayer player, boolean refundKey) {
        if (player == null) {
            return;
        }

        new ArrayList<>(runtimes.values()).stream()
                .filter(runtime -> {
                    OpeningSession session = runtime.getSession();
                    return session != null && session.getPlayerId().equals(player.getUniqueId());
                })
                .forEach(runtime -> abortOpening(runtime, refundKey));
    }

    private void refundPendingKey(OpeningSession session) {
        if (session == null || !session.hasConsumedKey() || session.isRewardGranted()) {
            return;
        }

        OfflinePlayer player = Bukkit.getOfflinePlayer(session.getPlayerId());
        plugin.getStorage().addCase(player, session.getSelectedCase(), 1);
        session.clearConsumedKey();
    }

    private void loadProfiles() {
        ConfigurationSection profilesSection = plugin.getConfig().getConfigurationSection("profiles");
        if (profilesSection == null) {
            return;
        }

        for (String profileId : profilesSection.getKeys(false)) {
            ConfigurationSection menuSection = profilesSection.getConfigurationSection(profileId + ".menu");
            ConfigurationSection rewardsSection = profilesSection.getConfigurationSection(profileId + ".rewards");
            if (menuSection == null || rewardsSection == null) {
                continue;
            }

            List<CaseItem> rewards = rewardsSection.getKeys(false).stream()
                    .map(rewardId -> {
                        String basePath = "profiles." + profileId + ".rewards." + rewardId;
                        String displayName = plugin.getConfig().getString(basePath + ".name", rewardId);
                        return new CaseItem(
                                displayName,
                                itemFactory.create(
                                        plugin.getConfig().getString(basePath + ".material", "ITEM;STONE"),
                                        displayName
                                ),
                                readActions(basePath),
                                Math.max(1, plugin.getConfig().getInt(basePath + ".chance", 1)),
                                plugin.getConfig().getBoolean(basePath + ".rare", false)
                        ) {
                        };
                    })
                    .collect(Collectors.toList());

            CaseProfile profile = new CaseProfile(
                    profileId.toLowerCase(),
                    menuSection.getString("material", Material.CHEST.name()),
                    menuSection.getInt("slot", 22),
                    menuSection.getString("display", profileId),
                    menuSection.getStringList("lore"),
                    plugin.getConfig().getString("profiles." + profileId + ".animation", AnimationService.CLASSIC),
                    Math.max(0, plugin.getConfig().getInt("profiles." + profileId + ".guarantee.after-opens", 0)),
                    rewards
            );
            profiles.put(profile.getId(), profile);
        }
    }

    private void loadInstances() {
        ConfigurationSection instancesSection = plugin.getConfig().getConfigurationSection("cases.instances");
        if (instancesSection == null) {
            return;
        }

        for (String instanceId : instancesSection.getKeys(false)) {
            String basePath = "cases.instances." + instanceId;
            String worldName = plugin.getConfig().getString(basePath + ".location.world");
            if (worldName == null || Bukkit.getWorld(worldName) == null) {
                plugin.getLogger().warning("Skipped case instance '" + instanceId + "': world is missing.");
                continue;
            }

            Location location = new Location(
                    Bukkit.getWorld(worldName),
                    plugin.getConfig().getDouble(basePath + ".location.x"),
                    plugin.getConfig().getDouble(basePath + ".location.y"),
                    plugin.getConfig().getDouble(basePath + ".location.z")
            );

            CaseRuntime runtime = new CaseRuntime((org.bukkit.plugin.java.JavaPlugin) plugin, entityRegistry, textFormatter, worldService, new CaseInstance(
                    instanceId.toLowerCase(),
                    location,
                    plugin.getConfig().getString(basePath + ".animation", ""),
                    plugin.getConfig().getStringList(basePath + ".hologram.lines")
            ));
            runtime.spawn();
            runtimes.put(instanceId.toLowerCase(), runtime);
        }
    }

    private List<String> readActions(String basePath) {
        List<String> actions = plugin.getConfig().getStringList(basePath + ".actions");
        return actions.isEmpty() ? plugin.getConfig().getStringList(basePath + ".commands") : actions;
    }

    private File getPresetFolder() {
        return new File(plugin.getDataFolder(), plugin.getConfig().getString("settings.presets.folder", "presets"));
    }

    private void copySection(ConfigurationSection source, ConfigurationSection target, String... excludedKeys) {
        List<String> excluded = List.of(excludedKeys);
        for (String key : source.getKeys(false)) {
            if (excluded.contains(key)) {
                continue;
            }

            if (source.isConfigurationSection(key)) {
                ConfigurationSection child = target.createSection(key);
                copySection(source.getConfigurationSection(key), child);
                continue;
            }
            target.set(key, source.get(key));
        }
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}

