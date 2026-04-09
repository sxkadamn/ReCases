package net.recases.management;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class CaseItem {
    private final String id;
    private final ItemStack icon;
    private final String name;
    private final List<String> actions;
    private final List<String> rollbackActions;
    private final List<String> conditions;
    private final Map<String, List<String>> triggers;
    private final int chance;
    private final boolean rare;

    public CaseItem(String name, ItemStack icon, List<String> actions, int chance, boolean rare) {
        this("", name, icon, actions, List.of(), List.of(), Map.of(), chance, rare);
    }

    public CaseItem(String id, String name, ItemStack icon, List<String> actions, int chance, boolean rare) {
        this(id, name, icon, actions, List.of(), List.of(), Map.of(), chance, rare);
    }

    public CaseItem(String id, String name, ItemStack icon, List<String> actions, List<String> rollbackActions,
                    List<String> conditions, Map<String, List<String>> triggers, int chance, boolean rare) {
        this.id = id == null ? "" : id;
        this.icon = icon;
        this.name = name;
        this.actions = new ArrayList<>(actions == null ? List.of() : actions);
        this.rollbackActions = new ArrayList<>(rollbackActions == null ? List.of() : rollbackActions);
        this.conditions = new ArrayList<>(conditions == null ? List.of() : conditions);
        this.triggers = copyTriggers(triggers);
        this.chance = chance;
        this.rare = rare;
    }

    public String getId() {
        return id;
    }

    public int getChance() {
        return chance;
    }

    public String getName() {
        return this.name;
    }

    public List<String> getActions() {
        return Collections.unmodifiableList(actions);
    }

    public List<String> getRollbackActions() {
        return Collections.unmodifiableList(rollbackActions);
    }

    public List<String> getConditions() {
        return Collections.unmodifiableList(conditions);
    }

    public Map<String, List<String>> getTriggers() {
        return Collections.unmodifiableMap(triggers);
    }

    public List<String> getTriggerActions(String triggerId) {
        if (triggerId == null || triggerId.trim().isEmpty()) {
            return List.of();
        }
        return triggers.getOrDefault(triggerId.toLowerCase(), List.of());
    }

    public final ItemStack getIcon() {
        return this.icon.clone();
    }

    public boolean isRare() {
        return rare;
    }

    private Map<String, List<String>> copyTriggers(Map<String, List<String>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }

        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            result.put(entry.getKey().toLowerCase(), List.copyOf(entry.getValue()));
        }
        return result;
    }
}

