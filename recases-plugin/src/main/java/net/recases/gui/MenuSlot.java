package net.recases.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MenuSlot {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private ItemStack item;
    private SlotListener listener;
    private boolean cancelClick = true;
    private int position;

    public MenuSlot(ItemStack item) {
        this.item = item.clone();
    }

    public MenuSlot(Material material) {
        this(new ItemStack(material));
    }

    public ItemStack getItem() {
        return item.clone();
    }

    public MenuSlot setItem(ItemStack item) {
        this.item = item.clone();
        return this;
    }

    public MenuSlot setAmount(int amount) {
        this.item.setAmount(amount);
        return this;
    }

    public MenuSlot setDisplay(String display) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(LEGACY_SERIALIZER.deserialize(display == null ? "" : display));
            item.setItemMeta(meta);
        }
        return this;
    }

    public MenuSlot setLoreList(List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.lore(lore.stream()
                    .map(line -> LEGACY_SERIALIZER.deserialize(line == null ? "" : line))
                    .collect(Collectors.toList()));
            item.setItemMeta(meta);
        }
        return this;
    }

    public MenuSlot setLore(String... lore) {
        List<String> list = new ArrayList<>();
        Collections.addAll(list, lore);
        return setLoreList(list);
    }

    public MenuSlot setPlayerOwner(String name) {
        if (!(item.getItemMeta() instanceof SkullMeta)) {
            return this;
        }
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(name));
        item.setItemMeta(meta);
        return this;
    }

    public MenuSlot hideAttributes(boolean hidden) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return this;
        }
        ItemFlag[] flags = {
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_DESTROYS,
                ItemFlag.HIDE_PLACED_ON,
                ItemFlag.HIDE_UNBREAKABLE
        };
        if (hidden) {
            meta.addItemFlags(flags);
        } else {
            meta.removeItemFlags(flags);
        }
        item.setItemMeta(meta);
        return this;
    }

    public MenuSlot setEnchanted(boolean enchanted) {
        if (enchanted) {
            item.addUnsafeEnchantment(Enchantment.LURE, 1);
        } else {
            item.removeEnchantment(Enchantment.LURE);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return this;
    }

    public MenuSlot setListener(SlotListener listener) {
        this.listener = listener;
        return this;
    }

    public boolean hasListener() {
        return listener != null;
    }

    public SlotListener getListener() {
        return listener;
    }

    public MenuSlot cancelClick(boolean cancelClick) {
        this.cancelClick = cancelClick;
        return this;
    }

    public boolean shouldCancelClick() {
        return cancelClick;
    }

    public int getPosition() {
        return position;
    }

    void setPosition(int position) {
        this.position = position;
    }
}

