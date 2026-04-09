package net.recases.services;

import net.recases.api.trigger.TriggerContext;
import net.recases.api.trigger.TriggerHandler;
import net.recases.api.trigger.TriggerRegistry;
import net.recases.app.PluginContext;
import net.recases.domain.CaseProfile;
import net.recases.management.CaseItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CaseTriggerService implements TriggerRegistry {

    private final PluginContext plugin;
    private final Map<String, List<TriggerHandler>> handlers = new LinkedHashMap<>();

    public CaseTriggerService(PluginContext plugin) {
        this.plugin = plugin;
    }

    @Override
    public synchronized boolean register(String triggerId, TriggerHandler handler) {
        String normalized = normalizeId(triggerId);
        if (normalized.isEmpty() || handler == null) {
            return false;
        }
        handlers.computeIfAbsent(normalized, ignored -> new ArrayList<>());
        if (handlers.get(normalized).contains(handler)) {
            return false;
        }
        handlers.get(normalized).add(handler);
        return true;
    }

    @Override
    public synchronized boolean unregister(String triggerId, TriggerHandler handler) {
        String normalized = normalizeId(triggerId);
        List<TriggerHandler> entries = handlers.get(normalized);
        if (entries == null) {
            return false;
        }
        boolean removed = entries.remove(handler);
        if (entries.isEmpty()) {
            handlers.remove(normalized);
        }
        return removed;
    }

    @Override
    public synchronized boolean isRegistered(String triggerId) {
        List<TriggerHandler> entries = handlers.get(normalizeId(triggerId));
        return entries != null && !entries.isEmpty();
    }

    @Override
    public synchronized List<String> getRegisteredIds() {
        return List.copyOf(handlers.keySet());
    }

    @Override
    public void fire(String triggerId, TriggerContext context) {
        String normalized = normalizeId(triggerId);
        List<TriggerHandler> listeners;
        synchronized (this) {
            listeners = handlers.containsKey(normalized) ? List.copyOf(handlers.get(normalized)) : List.of();
        }
        for (TriggerHandler listener : listeners) {
            try {
                listener.handle(context);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Trigger '" + normalized + "' handler failed: " + exception.getMessage());
            }
        }
    }

    public void fireConfigured(String triggerId, CaseExecutionContext context, CaseProfile profile, CaseItem reward) {
        if (context == null) {
            return;
        }

        String normalized = normalizeId(triggerId);
        CaseExecutionContext triggerContext = context.withTrigger(normalized);
        if (profile != null && !profile.getTriggerActions(normalized).isEmpty()) {
            plugin.getRewardService().execute(triggerContext, profile.getTriggerActions(normalized));
        }
        if (reward != null && !reward.getTriggerActions(normalized).isEmpty()) {
            plugin.getRewardService().execute(triggerContext, reward.getTriggerActions(normalized));
        }
        fire(normalized, triggerContext);
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
