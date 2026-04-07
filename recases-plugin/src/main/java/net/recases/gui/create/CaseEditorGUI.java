package net.recases.gui.create;

import net.recases.app.PluginContext;
import net.recases.domain.CaseProfile;
import net.recases.gui.Menu;
import net.recases.gui.MenuSlot;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class CaseEditorGUI {

    private final PluginContext plugin;

    public CaseEditorGUI(PluginContext plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, String profileId) {
        CaseProfile profile = plugin.getCaseService().getProfile(profileId);
        if (profile == null) {
            plugin.getMessages().send(player, "messages.case-not-found", "#ff6b6bПрофиль кейса '#ffffff%case%#ff6b6b' не найден.", "%case%", profileId);
            return;
        }

        List<String> rewardIds = new ArrayList<>(plugin.getCaseService().getRewardIds(profileId));
        Menu menu = plugin.getMenuManager().createMenu(
                "case-editor:" + profileId,
                plugin.getMessages().getComponent("menus.editor.title", "#74c0fcРедактор %profile%", "%profile%", profileId),
                6
        );
        menu.cancelTopInventoryClicks(true).cancelBottomInventoryClicks(true);

        menu.setSlot(0, new MenuSlot(Material.BOOK)
                .setDisplay(plugin.getMessages().get("menus.editor.profile-info", "#ffd166Профиль %profile%", "%profile%", profileId))
                .setLoreList(plugin.getMessages().getList(
                        "menus.editor.profile-info-lore",
                        List.of(
                                "#a8dadcАнимация: #ffffff%animation%",
                                "#a8dadcНаград: #ffffff%rewards%",
                                "#a8dadcShift+ЛКМ: сменить анимацию"
                        ),
                        "%animation%", profile.getAnimationId(),
                        "%rewards%", String.valueOf(rewardIds.size())
                ))
                .setListener((clicker, click, openedMenu, slot) -> {
                    if (click != ClickType.SHIFT_LEFT) {
                        return;
                    }
                    String next = plugin.getCaseService().cycleProfileAnimation(profileId);
                    plugin.getMessages().send(clicker, "messages.editor-animation-set", "#80ed99Анимация #ffffff%animation% #80ed99установлена для #ffffff%target%#80ed99.", "%animation%", next, "%target%", profileId);
                    plugin.getMessages().send(clicker, "messages.editor-saved", "#80ed99Изменения сохранены и runtime перезагружен.");
                    open(clicker, profileId);
                }));

        menu.setSlot(1, new MenuSlot(Material.EMERALD)
                .setDisplay(plugin.getMessages().get("menus.editor.add-reward", "#80ed99Добавить награду из руки"))
                .setLoreList(plugin.getMessages().getList("menus.editor.add-reward-lore", List.of("#a8dadcВозьмите предмет в руку и кликните")))
                .setListener((clicker, click, openedMenu, slot) -> {
                    ItemStack hand = clicker.getInventory().getItemInMainHand();
                    if (hand.getType().isAir()) {
                        plugin.getMessages().send(clicker, "messages.editor-no-item-in-hand", "#ff6b6bВозьмите предмет в руку перед этим действием.");
                        return;
                    }
                    String rewardId = "reward_" + System.currentTimeMillis();
                    String displayName = plugin.getItemFactory().serializeDisplayName(hand);
                    if (displayName.isEmpty()) {
                        displayName = hand.getType().name();
                    }
                    plugin.getCaseService().addReward(profileId, rewardId, hand, displayName);
                    plugin.getMessages().send(clicker, "messages.editor-reward-added", "#80ed99Награда #ffffff%reward% #80ed99добавлена.", "%reward%", displayName);
                    plugin.getMessages().send(clicker, "messages.editor-saved", "#80ed99Изменения сохранены и runtime перезагружен.");
                    open(clicker, profileId);
                }));

        menu.setSlot(2, new MenuSlot(Material.REPEATER)
                .setDisplay("#ffd166Guarantee")
                .setLore(
                        "#a8dadcCurrent: #ffffff" + profile.getGuaranteeAfterOpens(),
                        "#a8dadcLeft/Right: +/-1",
                        "#a8dadcShift+Left/Right: +/-10"
                )
                .setListener((clicker, click, openedMenu, slot) -> {
                    int delta = 0;
                    if (click == ClickType.LEFT) {
                        delta = 1;
                    } else if (click == ClickType.RIGHT) {
                        delta = -1;
                    } else if (click == ClickType.SHIFT_LEFT) {
                        delta = 10;
                    } else if (click == ClickType.SHIFT_RIGHT) {
                        delta = -10;
                    }
                    if (delta == 0) {
                        return;
                    }

                    int updated = plugin.getCaseService().updateProfileGuarantee(profileId, delta);
                    if (updated >= 0) {
                        plugin.getMessages().send(clicker, "messages.editor-saved", "#80ed99Changes were saved and runtime reloaded.");
                        plugin.getMessages().send(clicker, "messages.editor-guarantee-updated", "#80ed99Guarantee after opens: #ffffff%amount%", "%amount%", String.valueOf(updated));
                        open(clicker, profileId);
                    }
                }));

        menu.setSlot(3, new MenuSlot(Material.BLAZE_POWDER)
                .setDisplay("#74c0fcTest Animation")
                .setLore("#a8dadcRun current profile animation on nearest instance")
                .setListener((clicker, click, openedMenu, slot) -> {
                    var runtime = findNearestRuntime(clicker);
                    if (runtime == null) {
                        plugin.getMessages().send(clicker, "messages.instance-not-found", "#ff6b6bNo nearby case instance was found.");
                        return;
                    }
                    plugin.getCaseService().beginTestOpening(clicker, runtime, profileId, profile.getAnimationId());
                }));

        menu.setSlot(4, new MenuSlot(Material.COMPASS)
                .setDisplay("#74c0fcSimulate x1000")
                .setLore("#a8dadcRun 1000 simulated openings for this profile")
                .setListener((clicker, click, openedMenu, slot) -> simulateProfile(clicker, profile, 1000)));

        menu.setSlot(5, new MenuSlot(Material.CLOCK)
                .setDisplay("#74c0fcSimulate x10000")
                .setLore("#a8dadcRun 10000 simulated openings for this profile")
                .setListener((clicker, click, openedMenu, slot) -> simulateProfile(clicker, profile, 10000)));

        int slotIndex = 9;
        for (int i = 0; i < rewardIds.size() && slotIndex < 54; i++, slotIndex++) {
            String rewardId = rewardIds.get(i);
            CaseProfile refreshed = plugin.getCaseService().getProfile(profileId);
            if (refreshed == null || i >= refreshed.getRewards().size()) {
                break;
            }

            var reward = refreshed.getRewards().get(i);
            String rareLabel = reward.isRare() ? "true" : "false";
            menu.setSlot(slotIndex, new MenuSlot(reward.getIcon())
                    .setDisplay(reward.getName())
                    .setLoreList(plugin.getMessages().getList(
                            "menus.editor.reward-lore",
                            List.of(
                                    "#a8dadcШанс: #ffffff%chance%",
                                    "#a8dadcРедкость: #ffffff%rare%",
                                    "#a8dadcЛКМ: +1 шанс",
                                    "#a8dadcПКМ: -1 шанс",
                                    "#a8dadcShift+ЛКМ: rare on/off",
                                    "#a8dadcShift+ПКМ: иконка из руки",
                                    "#a8dadcQ: удалить награду"
                            ),
                            "%chance%", String.valueOf(reward.getChance()),
                            "%rare%", rareLabel
                    ))
                    .setListener((clicker, click, openedMenu, slot) -> {
                        boolean changed = false;
                        if (click == ClickType.LEFT) {
                            changed = plugin.getCaseService().updateRewardChance(profileId, rewardId, 1);
                        } else if (click == ClickType.RIGHT) {
                            changed = plugin.getCaseService().updateRewardChance(profileId, rewardId, -1);
                        } else if (click == ClickType.SHIFT_LEFT) {
                            changed = plugin.getCaseService().toggleRewardRare(profileId, rewardId);
                        } else if (click == ClickType.SHIFT_RIGHT) {
                            ItemStack hand = clicker.getInventory().getItemInMainHand();
                            if (hand.getType().isAir()) {
                                plugin.getMessages().send(clicker, "messages.editor-no-item-in-hand", "#ff6b6bВозьмите предмет в руку перед этим действием.");
                                return;
                            }
                            changed = plugin.getCaseService().setRewardIcon(profileId, rewardId, hand);
                        } else if (click == ClickType.DROP || click == ClickType.CONTROL_DROP) {
                            changed = plugin.getCaseService().removeReward(profileId, rewardId);
                            plugin.getMessages().send(clicker, "messages.editor-reward-removed", "#ffd166Награда #ffffff%reward% #ffd166удалена.", "%reward%", reward.getName());
                        }

                        if (changed) {
                            plugin.getMessages().send(clicker, "messages.editor-reward-updated", "#80ed99Награда #ffffff%reward% #80ed99обновлена.", "%reward%", reward.getName());
                            plugin.getMessages().send(clicker, "messages.editor-saved", "#80ed99Изменения сохранены и runtime перезагружен.");
                            open(clicker, profileId);
                        }
                    }));
        }

        plugin.getMenuManager().open(player, menu);
        plugin.getMessages().send(player, "messages.editor-opened", "#80ed99Открыт редактор профиля #ffffff%profile%", "%profile%", profileId);
    }
    private void simulateProfile(Player player, CaseProfile profile, int opens) {
        Random random = new Random();
        Map<String, Integer> rewardCounts = new LinkedHashMap<>();
        int pity = 0;
        int rareWins = 0;
        int guaranteedHits = 0;

        for (int i = 0; i < opens; i++) {
            boolean guaranteed = profile.getGuaranteeAfterOpens() > 0
                    && profile.hasRareRewards()
                    && pity + 1 >= profile.getGuaranteeAfterOpens();
            var reward = guaranteed ? profile.pickReward(random, true) : profile.pickReward(random);
            if (reward == null) {
                continue;
            }

            rewardCounts.merge(reward.getName(), 1, Integer::sum);
            if (reward.isRare()) {
                rareWins++;
                pity = 0;
            } else {
                pity++;
            }
            if (guaranteed) {
                guaranteedHits++;
            }
        }

        plugin.getMessages().send(player, "messages.simulate-header", "#74c0fcSimulation for #ffffff%profile% #74c0fc(%opens% opens)", "%profile%", profile.getId(), "%opens%", String.valueOf(opens));
        plugin.getMessages().send(player, "messages.simulate-summary", "#a8dadcRare wins: #ffffff%rare% #a8dadcGuaranteed hits: #ffffff%guaranteed%", "%rare%", String.valueOf(rareWins), "%guaranteed%", String.valueOf(guaranteedHits));
        rewardCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(5)
                .forEach(entry -> plugin.getMessages().send(player, "messages.simulate-line", "#ffffff%reward% #ffd166- %count%", "%reward%", entry.getKey(), "%count%", String.valueOf(entry.getValue())));
    }

    private net.recases.runtime.CaseRuntime findNearestRuntime(Player player) {
        if (player == null || player.getWorld() == null) {
            return null;
        }

        return plugin.getCaseService().getRuntimes().stream()
                .filter(runtime -> runtime.getLocation().getWorld() != null && runtime.getLocation().getWorld().equals(player.getWorld()))
                .min(Comparator.comparingDouble(runtime -> runtime.getLocation().distanceSquared(player.getLocation())))
                .orElse(null);
    }
}
