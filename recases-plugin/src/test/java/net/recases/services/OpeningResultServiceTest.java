package net.recases.services;

import net.recases.app.PluginContext;
import net.recases.domain.CaseProfile;
import net.recases.management.CaseItem;
import net.recases.management.OpeningSession;
import net.recases.runtime.CaseRuntime;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpeningResultServiceTest {

    @Test
    void marksRewardGrantedOnlyAfterRewardExecution() {
        PluginContext plugin = mock(PluginContext.class);
        RewardAuditService rewardAudit = mock(RewardAuditService.class);
        RewardService rewardService = mock(RewardService.class);
        CaseService caseService = mock(CaseService.class);
        StatsService statsService = mock(StatsService.class);
        LeaderboardHologramService holograms = mock(LeaderboardHologramService.class);
        MessageService messages = mock(MessageService.class);
        DiscordWebhookService webhooks = mock(DiscordWebhookService.class);
        CaseTriggerService triggerService = mock(CaseTriggerService.class);
        CaseExecutionContext context = mock(CaseExecutionContext.class);
        Player player = mock(Player.class);
        CaseRuntime runtime = mock(CaseRuntime.class);

        when(plugin.getRewardAudit()).thenReturn(rewardAudit);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("OpeningResultServiceTest"));
        when(plugin.getRewardService()).thenReturn(rewardService);
        when(plugin.getCaseService()).thenReturn(caseService);
        when(plugin.getStats()).thenReturn(statsService);
        when(plugin.getLeaderboardHolograms()).thenReturn(holograms);
        when(plugin.getMessages()).thenReturn(messages);
        when(plugin.getDiscordWebhooks()).thenReturn(webhooks);
        when(plugin.getTriggerService()).thenReturn(triggerService);
        when(rewardService.createContext(any(), any(), any(), any(), any(), any(Boolean.class), any(Integer.class), any(), any(Boolean.class), any(Boolean.class)))
                .thenReturn(context);
        when(context.withTrigger("opening-complete")).thenReturn(context);
        when(caseService.getProfile("alpha")).thenReturn(mock(CaseProfile.class));
        when(runtime.getId()).thenReturn("runtime-1");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Tester");

        OpeningSession session = new OpeningSession(player, "alpha", "classic", 1, reward(), false, 0, false);
        when(rewardAudit.recordIfAbsent(eq(player), eq(runtime), eq(session), any(CaseItem.class))).thenReturn(true);

        assertTrue(new OpeningResultService(plugin).complete(player, runtime, session, reward()));

        InOrder order = inOrder(rewardAudit, rewardService, statsService, triggerService, caseService);
        order.verify(rewardAudit).recordIfAbsent(eq(player), eq(runtime), eq(session), any(CaseItem.class));
        order.verify(rewardService).execute(eq(context), any(List.class));
        order.verify(statsService).recordOpening(eq(player), eq("alpha"), any(CaseItem.class), eq(false));
        order.verify(rewardAudit).markRewardGranted(session);
        order.verify(rewardAudit).discardPending(session);
        order.verify(caseService).completeOpening(runtime);
    }

    private static CaseItem reward() {
        return new CaseItem("reward-1", "Reward", new ItemStack(Material.STONE), List.of("message;ok"), List.of(), List.of(), Map.of(), 1, false) {
        };
    }
}
