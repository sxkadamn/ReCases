package net.recases.api.reward;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;

public interface RewardActionContext {

    Plugin getPlugin();

    Player getPlayer();

    String getCaseProfileId();

    String getRewardId();

    String getRewardName();

    String getRuntimeId();

    String getAnimationId();

    String getTriggerId();

    boolean isGuaranteedReward();

    boolean isRecovered();

    boolean isRollback();

    int getPityBeforeOpen();

    Map<String, String> getTokens();

    default String replaceTokens(String value) {
        String result = value == null ? "" : value;
        for (Map.Entry<String, String> entry : getTokens().entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
