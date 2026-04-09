package net.recases.api.trigger;

import java.util.List;

public interface TriggerRegistry {

    boolean register(String triggerId, TriggerHandler handler);

    boolean unregister(String triggerId, TriggerHandler handler);

    boolean isRegistered(String triggerId);

    List<String> getRegisteredIds();

    void fire(String triggerId, TriggerContext context);
}
