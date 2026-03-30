package net.recases.gui.create;

import net.recases.app.PluginContext;
import net.recases.domain.CaseProfile;
import net.recases.gui.Menu;
import net.recases.gui.MenuSlot;
import net.recases.management.CaseItem;
import net.recases.management.OpeningSession;
import net.recases.runtime.CaseRuntime;
import net.recases.services.TextFormatter;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class CasesGUI {

    private final PluginContext plugin;
    private final CaseRuntime runtime;
    private final TextFormatter textFormatter;

    public CasesGUI(PluginContext plugin, CaseRuntime runtime) {
        this.plugin = plugin;
        this.runtime = runtime;
        this.textFormatter = plugin.getTextFormatter();
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

            menu.setSlot(profile.getMenuSlot(), new MenuSlot(material == null ? Material.CHEST : material)
                    .setDisplay(textFormatter.colorize(profile.getMenuDisplay()))
                    .setLoreList(lore)
                    .setListener((clicker, clickType, openedMenu, clickedSlot) -> startOpening(clicker, profile.getId())));
            addedProfiles++;
        }

        if (addedProfiles == 0) {
            plugin.getMessages().send(player, "messages.menu-not-configured", "#ff6b6bВ конфиге нет корректных профилей кейсов.");
            return;
        }

        plugin.getMenuManager().open(player, menu);
    }

    private void startOpening(Player player, String profileId) {
        if (!runtime.isAvailable()) {
            plugin.getMessages().send(player, "messages.case-unavailable", "#ff6b6bЭта точка кейса сейчас недоступна. Попробуйте позже или перезагрузите плагин.");
            player.closeInventory();
            return;
        }

        CaseProfile profile = plugin.getCaseService().getProfile(profileId);
        if (profile == null) {
            plugin.getMessages().send(player, "messages.case-not-found", "#ff6b6bПрофиль кейса '#ffffff%case%#ff6b6b' не найден.", "%case%", profileId);
            player.closeInventory();
            return;
        }

        if (runtime.isOpening()) {
            plugin.getMessages().send(player, "messages.case-busy", "#ff6b6bЭтот кейс уже открывает другой игрок.");
            player.closeInventory();
            return;
        }

        if (plugin.getStorage().getCaseAmount(player, profileId) <= 0) {
            plugin.getMessages().send(player, "messages.no-keys", "#ff6b6bУ вас нет ключей от этого профиля кейса.");
            player.closeInventory();
            return;
        }

        CaseItem reward = plugin.getCaseService().getRandomReward(profileId);
        if (reward == null) {
            plugin.getMessages().send(player, "messages.case-no-rewards", "#ff6b6bДля этого профиля кейса не настроены награды.");
            player.closeInventory();
            return;
        }

        boolean guaranteedReward = plugin.getStats().shouldGuarantee(player, profile);
        if (guaranteedReward) {
            reward = plugin.getCaseService().getGuaranteedReward(profileId);
            if (reward == null) {
                guaranteedReward = false;
                reward = plugin.getCaseService().getRandomReward(profileId);
            }
        }
        if (reward == null) {
            plugin.getMessages().send(player, "messages.case-no-rewards", "#ff6b6bДля этого профиля кейса не настроены награды.");
            player.closeInventory();
            return;
        }

        String animationId = plugin.getAnimations().resolveAnimationId(runtime, profile);
        String animationName = plugin.getAnimations().getDisplayName(animationId);
        int requiredSelections = plugin.getAnimations().getRequiredSelections(animationId);
        OpeningSession session = new OpeningSession(player, profileId, animationId, requiredSelections, reward, guaranteedReward);
        session.setOpeningAnchor(plugin.getWorldService().createOpeningAnchor(
                runtime.getLocation(),
                player.getLocation(),
                plugin.getConfig().getDouble("settings.opening-guard.owner-anchor-distance", 2.15D)
        ));
        runtime.setSession(session);
        plugin.getSchematics().pasteAnimationScene(session, runtime);
        if (!plugin.getAnimations().create(plugin, player, runtime, profile).play()) {
            plugin.getSchematics().cleanup(runtime);
            runtime.clearSession();
            plugin.getMessages().send(player, "messages.case-unavailable", "#ff6b6bЭта точка кейса сейчас недоступна. Попробуйте позже или перезагрузите плагин.");
            player.closeInventory();
            return;
        }

        plugin.getStorage().removeCase(player, profileId, 1);
        session.markKeyConsumed();
        plugin.getMessages().send(
                player,
                "messages.case-opening-started",
                "#ffd166Вы начали открытие профиля #ffffff%case% #ffd166на точке #ffffff%instance% #ffd166с анимацией #ffffff%animation%#ffd166.",
                "%case%", profileId,
                "%instance%", runtime.getId(),
                "%animation%", animationName
        );
        player.closeInventory();
    }
}
