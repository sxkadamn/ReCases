package net.recases.gui.create;

import net.recases.app.PluginContext;
import net.recases.domain.CaseProfile;
import net.recases.gui.Menu;
import net.recases.gui.MenuSlot;
import net.recases.runtime.CaseRuntime;
import net.recases.services.TextFormatter;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

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
        List<CaseProfile> profiles = plugin.getCaseService().getAvailableProfiles(player, runtime);
        if (profiles.isEmpty()) {
            plugin.getMessages().send(player, "messages.menu-not-configured", "#ff6b6bР’ РєРѕРЅС„РёРіРµ РЅРµС‚ РїСЂРѕС„РёР»РµР№ РєРµР№СЃРѕРІ.");
            return;
        }

        Menu menu = plugin.getMenuManager().createMenu(
                "case-selector:" + runtime.getId(),
                plugin.getMessages().getComponent("menus.case-selector.title", "#ffd166РљРµР№СЃС‹"),
                plugin.getConfig().getInt("menus.case-selector.rows", 6)
        );
        menu.cancelTopInventoryClicks(true).cancelBottomInventoryClicks(true);
        int addedProfiles = 0;
        boolean previewFirst = plugin.getBedrockSupport().shouldUsePreviewFirstMenus(player);

        for (CaseProfile profile : profiles) {
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

            List<String> lore = profile.getMenuLore().stream()
                    .map(line -> line.replace("%keys%", String.valueOf(plugin.getStorage().getCaseAmount(player, profile.getId()))))
                    .map(textFormatter::colorize)
                    .collect(Collectors.toList());
            if (previewFirst) {
                lore.add(textFormatter.colorize(plugin.getMessages().get("menus.case-selector.bedrock-hint", "#74c0fcTap to preview rewards and open")));
            } else {
                lore.add(textFormatter.colorize(plugin.getMessages().get("menus.case-selector.open-hint", "#80ed99Р›РљРњ: РѕС‚РєСЂС‹С‚СЊ РєРµР№СЃ")));
                lore.add(textFormatter.colorize(plugin.getMessages().get("menus.case-selector.preview-hint", "#74c0fcРџРљРњ: РїСЂРµРІСЊСЋ РЅР°РіСЂР°Рґ")));
            }

            ItemStack icon = plugin.getItemFactory().create(profile.getMenuMaterial(), profile.getMenuDisplay(), lore);
            menu.setSlot(profile.getMenuSlot(), new MenuSlot(icon)
                    .setListener((clicker, clickType, openedMenu, clickedSlot) -> handleProfileClick(clicker, clickType, profile.getId())));
            addedProfiles++;
        }

        if (addedProfiles == 0) {
            plugin.getMessages().send(player, "messages.menu-not-configured", "#ff6b6bР’ РєРѕРЅС„РёРіРµ РЅРµС‚ РєРѕСЂСЂРµРєС‚РЅС‹С… РїСЂРѕС„РёР»РµР№ РєРµР№СЃРѕРІ.");
            return;
        }

        plugin.getMenuManager().open(player, menu);
    }

    private void handleProfileClick(Player player, ClickType clickType, String profileId) {
        if (plugin.getBedrockSupport().shouldUsePreviewFirstMenus(player)) {
            previewGUI.open(player, runtime, profileId);
            return;
        }

        if (clickType == ClickType.RIGHT || clickType == ClickType.SHIFT_RIGHT) {
            previewGUI.open(player, runtime, profileId);
            return;
        }

        plugin.getCaseService().beginOpening(player, runtime, profileId);
    }
}
