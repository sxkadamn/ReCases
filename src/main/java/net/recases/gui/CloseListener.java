package net.recases.gui;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface CloseListener {

    void handle(Player player);
}

