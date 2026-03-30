package net.recases.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;

public class Menu {

    private final String id;
    private final Component title;
    private final Inventory inventory;
    private final Map<Integer, MenuSlot> slots = new HashMap<>();
    private boolean cancelTopInventoryClicks = true;
    private boolean cancelBottomInventoryClicks = true;
    private CloseListener closeListener;

    public Menu(String id, Component title, int rows) {
        this.id = id;
        this.title = title;
        int normalizedRows = Math.max(1, Math.min(6, rows));
        this.inventory = Bukkit.createInventory(null, normalizedRows * 9, title);
    }

    public String getID() {
        return id;
    }

    public Component getTitle() {
        return title;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public MenuSlot getSlot(int position) {
        return slots.get(position);
    }

    public Menu setSlot(int position, MenuSlot slot) {
        if (position < 0 || position >= inventory.getSize()) {
            return this;
        }
        slot.setPosition(position);
        slots.put(position, slot);
        inventory.setItem(position, slot.getItem());
        return this;
    }

    public Menu removeSlot(int position) {
        if (position < 0 || position >= inventory.getSize()) {
            return this;
        }
        slots.remove(position);
        inventory.clear(position);
        return this;
    }

    public boolean hasSlot(int slot) {
        return slots.containsKey(slot);
    }

    public Menu refreshItems() {
        inventory.clear();
        for (Map.Entry<Integer, MenuSlot> entry : slots.entrySet()) {
            inventory.setItem(entry.getKey(), entry.getValue().getItem());
        }
        for (HumanEntity viewer : inventory.getViewers()) {
            if (viewer instanceof Player) {
                ((Player) viewer).updateInventory();
            }
        }
        return this;
    }

    public Menu refreshSlot(int slot) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return this;
        }
        if (!slots.containsKey(slot)) {
            inventory.clear(slot);
        } else {
            inventory.setItem(slot, slots.get(slot).getItem());
        }
        for (HumanEntity viewer : inventory.getViewers()) {
            if (viewer instanceof Player) {
                ((Player) viewer).updateInventory();
            }
        }
        return this;
    }

    public Menu cancelTopInventoryClicks(boolean cancel) {
        this.cancelTopInventoryClicks = cancel;
        return this;
    }

    public boolean shouldCancelTopInventoryClicks() {
        return cancelTopInventoryClicks;
    }

    public Menu cancelBottomInventoryClicks(boolean cancel) {
        this.cancelBottomInventoryClicks = cancel;
        return this;
    }

    public boolean shouldCancelBottomInventoryClicks() {
        return cancelBottomInventoryClicks;
    }

    public boolean hasCloseListener() {
        return closeListener != null;
    }

    public CloseListener getCloseListener() {
        return closeListener;
    }

    public Menu onClose(CloseListener closeListener) {
        this.closeListener = closeListener;
        return this;
    }
}

