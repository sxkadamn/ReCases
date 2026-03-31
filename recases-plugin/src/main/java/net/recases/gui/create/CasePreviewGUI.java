package net.recases.gui.create;

import net.recases.app.PluginContext;
import net.recases.domain.CaseProfile;
import net.recases.gui.Menu;
import net.recases.gui.MenuSlot;
import net.recases.management.CaseItem;
import net.recases.runtime.CaseRuntime;
import org.bukkit.Material;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class CasePreviewGUI {

    private static final int PAGE_SIZE = 36;

    private final PluginContext plugin;

    public CasePreviewGUI(PluginContext plugin) {
        this.plugin = plugin;
    }

    public void open(org.bukkit.entity.Player player, CaseRuntime runtime, String profileId) {
        open(player, runtime, profileId, 0);
    }

    private void open(org.bukkit.entity.Player player, CaseRuntime runtime, String profileId, int page) {
        CaseProfile profile = plugin.getCaseService().getProfile(profileId);
        if (profile == null) {
            plugin.getMessages().send(player, "messages.case-not-found", "#ff6b6bПрофиль кейса '#ffffff%case%#ff6b6b' не найден.", "%case%", profileId);
            return;
        }

        List<CaseItem> rewards = new ArrayList<>(profile.getRewards());
        rewards.sort(Comparator.comparing(CaseItem::isRare).reversed()
                .thenComparing(Comparator.comparingInt(CaseItem::getChance).reversed())
                .thenComparing(CaseItem::getName, String.CASE_INSENSITIVE_ORDER));

        int maxPage = Math.max(0, (rewards.size() - 1) / PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, maxPage));

        Menu menu = plugin.getMenuManager().createMenu(
                "case-preview:" + runtime.getId() + ":" + profileId + ":" + currentPage,
                plugin.getMessages().getComponent("menus.preview.title", "#74c0fcПревью %profile%", "%profile%", profileId),
                6
        );
        menu.cancelTopInventoryClicks(true).cancelBottomInventoryClicks(true);

        int keys = plugin.getStorage().getCaseAmount(player, profileId);
        int pity = plugin.getStats().getPity(player, profileId);
        int pityLeft = plugin.getStats().getPityLeft(player, profile);
        int opens = plugin.getStats().getOpens(player, profileId);
        String lastReward = fallback(plugin.getStats().getLastRewardName(player, profileId), plugin.getMessages().get("menus.preview.no-last-reward", "#7f8ea3Ничего еще не выпадало"));

        menu.setSlot(0, new MenuSlot(Material.BOOK)
                .setDisplay(plugin.getMessages().get("menus.preview.profile-info", "#ffd166Профиль %profile%", "%profile%", profileId))
                .setLoreList(List.of(
                        plugin.getMessages().get("menus.preview.profile-animation", "#a8dadcАнимация: #ffffff%animation%", "%animation%", profile.getAnimationId()),
                        plugin.getMessages().get("menus.preview.profile-rewards", "#a8dadcНаград: #ffffff%amount%", "%amount%", String.valueOf(rewards.size())),
                        plugin.getMessages().get("menus.preview.profile-guarantee", "#a8dadcГарант после: #ffffff%amount%", "%amount%", String.valueOf(profile.getGuaranteeAfterOpens()))
                )));

        menu.setSlot(2, new MenuSlot(Material.PLAYER_HEAD)
                .setPlayerOwner(player.getName())
                .setDisplay(plugin.getMessages().get("menus.preview.player-info", "#74c0fcСтатистика %player%", "%player%", player.getName()))
                .setLoreList(List.of(
                        plugin.getMessages().get("menus.preview.player-keys", "#a8dadcКлючей: #ffffff%amount%", "%amount%", String.valueOf(keys)),
                        plugin.getMessages().get("menus.preview.player-opens", "#a8dadcОткрытий: #ffffff%amount%", "%amount%", String.valueOf(opens)),
                        plugin.getMessages().get("menus.preview.player-last-reward", "#a8dadcПоследний дроп: #ffffff%reward%", "%reward%", lastReward)
                )));

        menu.setSlot(4, new MenuSlot(Material.EXPERIENCE_BOTTLE)
                .setDisplay(plugin.getMessages().get("menus.preview.pity-info", "#80ed99Pity и гарант"))
                .setLoreList(List.of(
                        plugin.getMessages().get("menus.preview.pity-current", "#a8dadcТекущий pity: #ffffff%amount%", "%amount%", String.valueOf(pity)),
                        plugin.getMessages().get("menus.preview.pity-left", "#a8dadcДо гаранта: #ffffff%amount%", "%amount%", String.valueOf(pityLeft)),
                        plugin.getMessages().get("menus.preview.pity-progress", "#a8dadcПрогресс: #ffffff%amount%%", "%amount%", String.valueOf(plugin.getStats().getGuaranteeProgressPercent(player, profile)))
                )));

        menu.setSlot(8, new MenuSlot(Material.EMERALD)
                .setDisplay(plugin.getMessages().get("menus.preview.open-button", "#80ed99Открыть кейс"))
                .setLoreList(List.of(
                        plugin.getMessages().get("menus.preview.open-button-lore", "#a8dadcНажмите, чтобы начать открытие")
                ))
                .setListener((clicker, click, openedMenu, slot) -> plugin.getCaseService().beginOpening(clicker, runtime, profileId)));

        int from = currentPage * PAGE_SIZE;
        int to = Math.min(rewards.size(), from + PAGE_SIZE);
        int slot = 9;
        for (int index = from; index < to; index++) {
            CaseItem reward = rewards.get(index);
            double percent = profile.getChancePercent(reward);
            String rareLabel = reward.isRare()
                    ? plugin.getMessages().get("menus.preview.rare-yes", "#80ed99Да")
                    : plugin.getMessages().get("menus.preview.rare-no", "#ff6b6bНет");

            MenuSlot rewardSlot = new MenuSlot(reward.getIcon())
                    .setDisplay(reward.getName())
                    .setLoreList(List.of(
                            plugin.getMessages().get("menus.preview.reward-chance", "#a8dadcВес: #ffffff%amount%", "%amount%", String.valueOf(reward.getChance())),
                            plugin.getMessages().get("menus.preview.reward-percent", "#a8dadcШанс: #ffffff%amount%%", "%amount%", String.format(Locale.US, "%.2f", percent)),
                            plugin.getMessages().get("menus.preview.reward-rare", "#a8dadcРедкая: %value%", "%value%", rareLabel),
                            plugin.getMessages().get("menus.preview.reward-actions", "#a8dadcДействий: #ffffff%amount%", "%amount%", String.valueOf(reward.getActions().size()))
                    ))
                    .hideAttributes(true);
            if (reward.isRare()) {
                rewardSlot.setEnchanted(true);
            }
            menu.setSlot(slot++, rewardSlot);
        }

        menu.setSlot(45, new MenuSlot(Material.ARROW)
                .setDisplay(plugin.getMessages().get("menus.preview.back-button", "#ffd166Назад"))
                .setLoreList(List.of(plugin.getMessages().get("menus.preview.back-button-lore", "#a8dadcВернуться к выбору кейса")))
                .setListener((clicker, click, openedMenu, clickedSlot) -> new CasesGUI(plugin, runtime).open(clicker)));

        if (currentPage > 0) {
            menu.setSlot(47, new MenuSlot(Material.SPECTRAL_ARROW)
                    .setDisplay(plugin.getMessages().get("menus.preview.prev-page", "#74c0fcПредыдущая страница"))
                    .setListener((clicker, click, openedMenu, clickedSlot) -> open(clicker, runtime, profileId, currentPage - 1)));
        }

        menu.setSlot(49, new MenuSlot(Material.PAPER)
                .setDisplay(plugin.getMessages().get("menus.preview.page-indicator", "#ffffffСтраница %current%/%max%",
                        "%current%", String.valueOf(currentPage + 1),
                        "%max%", String.valueOf(maxPage + 1)))
                .setLoreList(List.of(
                        plugin.getMessages().get("menus.preview.page-indicator-lore", "#a8dadcПросмотр всех наград профиля")
                )));

        if (currentPage < maxPage) {
            menu.setSlot(51, new MenuSlot(Material.SPECTRAL_ARROW)
                    .setDisplay(plugin.getMessages().get("menus.preview.next-page", "#74c0fcСледующая страница"))
                    .setListener((clicker, click, openedMenu, clickedSlot) -> open(clicker, runtime, profileId, currentPage + 1)));
        }

        plugin.getMenuManager().open(player, menu);
    }

    private String fallback(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value;
    }
}
