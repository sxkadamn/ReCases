package net.recases.api;

import org.bukkit.plugin.Plugin;
import net.recases.api.condition.ConditionRegistry;
import net.recases.api.reward.RewardActionRegistry;
import net.recases.api.trigger.TriggerRegistry;

public interface ReCasesApi {

    Plugin getPlugin();

    OpeningAnimationRegistry getOpeningAnimationRegistry();

    RewardActionRegistry getRewardActionRegistry();

    ConditionRegistry getConditionRegistry();

    TriggerRegistry getTriggerRegistry();
}
