package net.recases.gui.impl;

import net.kyori.adventure.text.Component;
import net.recases.gui.Menu;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MenuManager {

    private final Map<Inventory, Menu> menusByInventory = new HashMap<>();
    private final Map<UUID, Menu> openMenus = new HashMap<>();

    public Menu createMenu(String id, Component title, int rows) {
        Menu menu = new Menu(id, title, rows);
        menusByInventory.put(menu.getInventory(), menu);
        return menu;
    }

    public void open(Player player, Menu menu) {
        openMenus.put(player.getUniqueId(), menu);
        player.openInventory(menu.getInventory());
    }

    public Menu getMenu(Inventory inventory) {
        return menusByInventory.get(inventory);
    }

    public void close(Player player, Inventory inventory) {
        openMenus.remove(player.getUniqueId());
        menusByInventory.remove(inventory);
    }
}

