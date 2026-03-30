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
}

