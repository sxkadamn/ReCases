package net.recases.management;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class CaseItem {
    private final ItemStack icon;
    private final String name;
    private final List<String> actions;
    private final int chance;
    private final boolean rare;

    public CaseItem(String name, ItemStack icon, List<String> actions, int chance, boolean rare) {
        this.icon = icon;
        this.name = name;
        this.actions = actions;
        this.chance = chance;
        this.rare = rare;
    }

    public int getChance() {
        return chance;
    }

    public String getName() {
        return this.name;
    }

    public List<String> getActions() {
        return actions;
    }

    public final ItemStack getIcon() {
        return this.icon.clone();
    }

    public boolean isRare() {
        return rare;
    }

}

