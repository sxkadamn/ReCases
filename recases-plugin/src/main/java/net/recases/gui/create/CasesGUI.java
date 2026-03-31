package net.recases.gui.create;

import net.recases.app.PluginContext;
import net.recases.domain.CaseProfile;
import net.recases.gui.Menu;
import net.recases.gui.MenuSlot;
import net.recases.runtime.CaseRuntime;
import net.recases.services.TextFormatter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;
import java.util.stream.Collectors;

public class CasesGUI {

    private final PluginContext plugin;
    private final CaseRuntime runtime;
    private final TextFormatter textFormatter;
    private final CasePreviewGUI previewGUI;

    public CasesGUI(PluginContext plugin, CaseRuntime runtime) {
        this.plugin = plugin;
        this.runtime = runtime;
        this.textFormatter = plugin.getTextFormatter();
        this.previewGUI = new CasePreviewGUI(plugin);
    }

    public void open(Player player) {
        if (plugin.getCaseService().getProfiles().isEmpty()) {
            plugin.getMessages().send(player, "messages.menu-not-configured", "#ff6b6bВ конфиге нет профилей кейсов.");
            return;
        }

        Menu menu = plugin.getMenuManager().createMenu(
                "case-selector:" + runtime.getId(),
                plugin.getMessages().getComponent("menus.case-selector.title", "#ffd166Кейсы"),
                plugin.getConfig().getInt("menus.case-selector.rows", 6)
        );
        menu.cancelTopInventoryClicks(true).cancelBottomInventoryClicks(true);
        int addedProfiles = 0;

        for (CaseProfile profile : plugin.getCaseService().getProfiles()) {
            if (profile.getMenuSlot() < 0 || profile.getMenuSlot() >= menu.getInventory().getSize()) {
                plugin.getLogger().warning("Skipped profile '" + profile.getId() + "': menu slot " + profile.getMenuSlot()
                        + " is outside inventory size " + menu.getInventory().getSize() + ".");
                continue;
            }
            if (menu.hasSlot(profile.getMenuSlot())) {
                plugin.getLogger().warning("Skipped profile '" + profile.getId() + "': menu slot " + profile.getMenuSlot()
                        + " is already occupied.");
                continue;
            }

            Material material = Material.matchMaterial(profile.getMenuMaterial());
            List<String> lore = profile.getMenuLore().stream()
                    .map(line -> line.replace("%keys%", String.valueOf(plugin.getStorage().getCaseAmount(player, profile.getId()))))
                    .map(textFormatter::colorize)
                    .collect(Collectors.toList());
            lore.add(textFormatter.colorize(plugin.getMessages().get("menus.case-selector.open-hint", "#80ed99ЛКМ: открыть кейс")));
            lore.add(textFormatter.colorize(plugin.getMessages().get("menus.case-selector.preview-hint", "#74c0fcПКМ: превью наград")));

            menu.setSlot(profile.getMenuSlot(), new MenuSlot(material == null ? Material.CHEST : material)
                    .setDisplay(textFormatter.colorize(profile.getMenuDisplay()))
                    .setLoreList(lore)
                    .setListener((clicker, clickType, openedMenu, clickedSlot) -> handleProfileClick(clicker, clickType, profile.getId())));
            addedProfiles++;
        }

        if (addedProfiles == 0) {
            plugin.getMessages().send(player, "messages.menu-not-configured", "#ff6b6bВ конфиге нет корректных профилей кейсов.");
            return;
        }

        plugin.getMenuManager().open(player, menu);
    }

    private void handleProfileClick(Player player, ClickType clickType, String profileId) {
        if (clickType == ClickType.RIGHT || clickType == ClickType.SHIFT_RIGHT) {
            previewGUI.open(player, runtime, profileId);
            return;
        }

        plugin.getCaseService().beginOpening(player, runtime, profileId);
    }
}
