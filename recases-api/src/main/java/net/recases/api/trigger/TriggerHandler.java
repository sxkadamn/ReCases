package net.recases.api.trigger;

@FunctionalInterface
public interface TriggerHandler {

    void handle(TriggerContext context);
}
