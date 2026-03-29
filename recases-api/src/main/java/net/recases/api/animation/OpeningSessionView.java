package net.recases.api.animation;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public interface OpeningSessionView {

    UUID getPlayerId();

    String getPlayerName();

    String getProfileId();

    String getAnimationId();

    int getRequiredSelections();

    int getOpenedCount();

    String getRewardName();

    ItemStack getRewardIcon();

    boolean isRewardRare();

    boolean isGuaranteedReward();
}
