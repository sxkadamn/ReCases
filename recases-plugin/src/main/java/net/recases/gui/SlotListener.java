package net.recases.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

@FunctionalInterface
public interface SlotListener {

    void handle(Player player, ClickType clickType, Menu menu, MenuSlot slot);
}

