package net.recases.gui.impl;

import net.recases.app.PluginContext;
import net.recases.gui.Menu;
import net.recases.gui.MenuSlot;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

public class MenuListener implements Listener {

    private final PluginContext plugin;

    public MenuListener(PluginContext plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        Menu menu = plugin.getMenuManager().getMenu(topInventory);
        if (menu == null) {
            return;
        }

        boolean clickedTop = event.getClickedInventory() != null && event.getClickedInventory().equals(topInventory);
        if (clickedTop && menu.shouldCancelTopInventoryClicks()) {
            event.setCancelled(true);
        }
        if (!clickedTop && menu.shouldCancelBottomInventoryClicks()) {
            event.setCancelled(true);
        }
        if (!clickedTop || !(event.getWhoClicked() instanceof Player)) {
            return;
        }

        MenuSlot slot = menu.getSlot(event.getSlot());
        if (slot == null) {
            return;
        }

        if (slot.shouldCancelClick()) {
            event.setCancelled(true);
        }

        if (slot.hasListener()) {
            slot.getListener().handle((Player) event.getWhoClicked(), event.getClick(), menu, slot);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Menu menu = plugin.getMenuManager().getMenu(event.getInventory());
        if (menu == null || !(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        if (menu.hasCloseListener()) {
            menu.getCloseListener().handle(player);
        }
        plugin.getMenuManager().close(player, event.getInventory());
    }
}

