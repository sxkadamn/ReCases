package net.recases.api.condition;

import java.util.List;

public interface ConditionRegistry {

    boolean register(String conditionId, ConditionHandler handler);

    boolean unregister(String conditionId);

    boolean isRegistered(String conditionId);

    List<String> getRegisteredIds();
}
