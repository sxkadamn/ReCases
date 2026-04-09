package net.recases.api.reward;

import java.util.List;

public interface RewardActionRegistry {

    boolean register(String actionId, RewardActionHandler handler);

    boolean unregister(String actionId);

    boolean isRegistered(String actionId);

    List<String> getRegisteredIds();
}
