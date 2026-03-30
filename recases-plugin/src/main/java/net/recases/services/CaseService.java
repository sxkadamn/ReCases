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
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.ConfigurationSection;

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
        plugin.saveConfig();
        plugin.reloadPluginState();
        return true;
    }

    public boolean deleteProfile(String profileId) {
        String normalized = normalizeId(profileId);
        if (!plugin.getConfig().contains("profiles." + normalized)) {
            return false;
        }
        plugin.getConfig().set("profiles." + normalized, null);
        plugin.saveConfig();
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
        plugin.saveConfig();
        plugin.reloadPluginState();
        return true;
    }

    public boolean deleteInstance(String instanceId) {
        String normalized = normalizeId(instanceId);
        if (!plugin.getConfig().contains("cases.instances." + normalized)) {
            return false;
        }
        plugin.getConfig().set("cases.instances." + normalized, null);
        plugin.saveConfig();
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
        plugin.saveConfig();
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
        plugin.saveConfig();
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
        plugin.saveConfig();
        plugin.reloadPluginState();
        return true;
    }

    public boolean removeReward(String profileId, String rewardId) {
        String basePath = "profiles." + normalizeId(profileId) + ".rewards." + normalizeId(rewardId);
        if (!plugin.getConfig().contains(basePath)) {
            return false;
        }
        plugin.getConfig().set(basePath, null);
        plugin.saveConfig();
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
        plugin.saveConfig();
        plugin.reloadPluginState();
        return true;
    }

    public boolean toggleRewardRare(String profileId, String rewardId) {
        String basePath = "profiles." + normalizeId(profileId) + ".rewards." + normalizeId(rewardId);
        if (!plugin.getConfig().contains(basePath)) {
            return false;
        }
        plugin.getConfig().set(basePath + ".rare", !plugin.getConfig().getBoolean(basePath + ".rare", false));
        plugin.saveConfig();
        plugin.reloadPluginState();
        return true;
    }

    public boolean setRewardIcon(String profileId, String rewardId, ItemStack icon) {
        String basePath = "profiles." + normalizeId(profileId) + ".rewards." + normalizeId(rewardId);
        if (!plugin.getConfig().contains(basePath) || icon == null || icon.getType().isAir()) {
            return false;
        }
        plugin.getConfig().set(basePath + ".material", itemFactory.serialize(icon));
        plugin.saveConfig();
        plugin.reloadPluginState();
        return true;
    }

    public boolean setRewardDisplayName(String profileId, String rewardId, String displayName) {
        String basePath = "profiles." + normalizeId(profileId) + ".rewards." + normalizeId(rewardId);
        if (!plugin.getConfig().contains(basePath)) {
            return false;
        }
        plugin.getConfig().set(basePath + ".name", displayName);
        plugin.saveConfig();
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
                                        textFormatter.colorize(displayName)
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
                    menuSection.getString("animation", plugin.getConfig().getString("profiles." + profileId + ".animation", "classic")),
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

    private String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}

