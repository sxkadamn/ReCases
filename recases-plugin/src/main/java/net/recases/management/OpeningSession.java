package net.recases.management;

import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class OpeningSession {

    private final UUID openingId;
    private final UUID transactionId;
    private final UUID playerId;
    private final String playerName;
    private final String selectedCase;
    private final String animationId;
    private final int requiredSelections;
    private final CaseItem finalReward;
    private final boolean guaranteedReward;
    private final int pityBeforeOpen;
    private final long startedAt;
    private final boolean testMode;
    private final Set<Location> chestLocations = new LinkedHashSet<>();
    private final Set<Location> platformLocations = new LinkedHashSet<>();
    private final Set<UUID> targetEntityIds = new LinkedHashSet<>();
    private final Set<String> openedTargets = new LinkedHashSet<>();
    private Location openingAnchor;
    private String distributedLockToken = "";
    private boolean keyConsumed;
    private boolean rewardGranted;

    public OpeningSession(Player player, String selectedCase, String animationId, int requiredSelections, CaseItem finalReward, boolean guaranteedReward) {
        this(player, selectedCase, animationId, requiredSelections, finalReward, guaranteedReward, 0, false);
    }

    public OpeningSession(Player player, String selectedCase, String animationId, int requiredSelections, CaseItem finalReward,
                          boolean guaranteedReward, int pityBeforeOpen, boolean testMode) {
        this.openingId = UUID.randomUUID();
        this.transactionId = UUID.randomUUID();
        this.playerId = player.getUniqueId();
        this.playerName = player.getName();
        this.selectedCase = selectedCase;
        this.animationId = animationId;
        this.requiredSelections = requiredSelections;
        this.finalReward = finalReward;
        this.guaranteedReward = guaranteedReward;
        this.pityBeforeOpen = Math.max(0, pityBeforeOpen);
        this.startedAt = System.currentTimeMillis();
        this.testMode = testMode;
    }

    public UUID getOpeningId() {
        return openingId;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getSelectedCase() {
        return selectedCase;
    }

    public String getAnimationId() {
        return animationId;
    }

    public int getRequiredSelections() {
        return requiredSelections;
    }

    public CaseItem getFinalReward() {
        return finalReward;
    }

    public boolean isGuaranteedReward() {
        return guaranteedReward;
    }

    public int getPityBeforeOpen() {
        return pityBeforeOpen;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public boolean isTestMode() {
        return testMode;
    }

    public Set<Location> getChestLocations() {
        return chestLocations;
    }

    public Set<Location> getPlatformLocations() {
        return platformLocations;
    }

    public Set<UUID> getTargetEntityIds() {
        return targetEntityIds;
    }

    public Location getOpeningAnchor() {
        return openingAnchor == null ? null : openingAnchor.clone();
    }

    public void setOpeningAnchor(Location openingAnchor) {
        this.openingAnchor = openingAnchor == null ? null : openingAnchor.clone();
    }

    public String getDistributedLockToken() {
        return distributedLockToken;
    }

    public void setDistributedLockToken(String distributedLockToken) {
        this.distributedLockToken = distributedLockToken == null ? "" : distributedLockToken;
    }

    public boolean isParticipant(Player player) {
        return playerId.equals(player.getUniqueId());
    }

    public boolean isTrackedChest(Location location) {
        return chestLocations.contains(location);
    }

    public boolean markOpened(Location location) {
        return openedTargets.add("block:" + location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ());
    }

    public boolean isTrackedEntity(Entity entity) {
        return entity != null && targetEntityIds.contains(entity.getUniqueId());
    }

    public boolean markOpened(Entity entity) {
        return entity != null && openedTargets.add("entity:" + entity.getUniqueId());
    }

    public int getOpenedCount() {
        return openedTargets.size();
    }

    public void trackEntity(Entity entity) {
        if (entity != null) {
            targetEntityIds.add(entity.getUniqueId());
        }
    }

    public void clearTrackedEntities() {
        targetEntityIds.stream()
                .map(Bukkit::getEntity)
                .filter(Objects::nonNull)
                .forEach(Entity::remove);
        targetEntityIds.clear();
    }

    public boolean hasConsumedKey() {
        return keyConsumed;
    }

    public void markKeyConsumed() {
        this.keyConsumed = true;
    }

    public void clearConsumedKey() {
        this.keyConsumed = false;
    }

    public boolean isRewardGranted() {
        return rewardGranted;
    }

    public void markRewardGranted() {
        this.rewardGranted = true;
    }
}

