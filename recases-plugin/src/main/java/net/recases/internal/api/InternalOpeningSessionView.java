package net.recases.internal.api;

import net.recases.api.animation.OpeningSessionView;
import net.recases.management.CaseItem;
import net.recases.management.OpeningSession;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

final class InternalOpeningSessionView implements OpeningSessionView {

    private final OpeningSession session;

    InternalOpeningSessionView(OpeningSession session) {
        this.session = session;
    }

    @Override
    public UUID getPlayerId() {
        return session.getPlayerId();
    }

    @Override
    public String getPlayerName() {
        return session.getPlayerName();
    }

    @Override
    public String getProfileId() {
        return session.getSelectedCase();
    }

    @Override
    public String getAnimationId() {
        return session.getAnimationId();
    }

    @Override
    public int getRequiredSelections() {
        return session.getRequiredSelections();
    }

    @Override
    public int getOpenedCount() {
        return session.getOpenedCount();
    }

    @Override
    public String getRewardName() {
        CaseItem reward = session.getFinalReward();
        return reward == null ? "" : reward.getName();
    }

    @Override
    public ItemStack getRewardIcon() {
        CaseItem reward = session.getFinalReward();
        return reward == null ? null : reward.getIcon();
    }

    @Override
    public boolean isRewardRare() {
        CaseItem reward = session.getFinalReward();
        return reward != null && reward.isRare();
    }

    @Override
    public boolean isGuaranteedReward() {
        return session.isGuaranteedReward();
    }
}
