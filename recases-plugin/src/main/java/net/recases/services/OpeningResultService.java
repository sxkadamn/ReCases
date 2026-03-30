package net.recases.services;

import net.recases.app.PluginContext;
import net.recases.management.CaseItem;
import net.recases.management.OpeningSession;
import net.recases.runtime.CaseRuntime;
import org.bukkit.entity.Player;

public class OpeningResultService {

    private final PluginContext plugin;

    public OpeningResultService(PluginContext plugin) {
        this.plugin = plugin;
    }

    public boolean complete(Player player, CaseRuntime runtime, OpeningSession session, CaseItem reward) {
        if (player == null || runtime == null || session == null || reward == null || session.isRewardGranted()) {
            return false;
        }

        boolean recorded = plugin.getRewardAudit().recordIfAbsent(player, runtime, session, reward);
        if (!recorded) {
            plugin.getLogger().warning("Skipped duplicate reward delivery for opening " + session.getOpeningId());
            plugin.getCaseService().completeOpening(runtime);
            return false;
        }

        session.markRewardGranted();
        plugin.getRewardService().execute(player, reward.getActions());
        plugin.getStats().recordOpening(player, session.getSelectedCase(), reward, session.isGuaranteedReward());
        plugin.getLeaderboardHolograms().refreshAll();
        plugin.getMessages().send(player, "messages.case-reward-received", "#80ed99Вы получили награду: #ffffff%reward%", "%reward%", reward.getName());
        plugin.getDiscordWebhooks().notifyReward(player, runtime, session, reward);
        plugin.getCaseService().completeOpening(runtime);
        return true;
    }
}
