package net.recases.services;

import net.recases.api.condition.ConditionContext;
import net.recases.api.reward.RewardActionContext;
import net.recases.api.trigger.TriggerContext;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CaseExecutionContext implements RewardActionContext, ConditionContext, TriggerContext {

    private final Plugin plugin;
    private final Player player;
    private final String caseProfileId;
    private final String rewardId;
    private final String rewardName;
    private final String runtimeId;
    private final String animationId;
    private final String triggerId;
    private final boolean guaranteedReward;
    private final boolean recovered;
    private final boolean rollback;
    private final int pityBeforeOpen;
    private final Map<String, String> tokens;

    private CaseExecutionContext(Builder builder) {
        this.plugin = builder.plugin;
        this.player = builder.player;
        this.caseProfileId = safe(builder.caseProfileId);
        this.rewardId = safe(builder.rewardId);
        this.rewardName = safe(builder.rewardName);
        this.runtimeId = safe(builder.runtimeId);
        this.animationId = safe(builder.animationId);
        this.triggerId = safe(builder.triggerId);
        this.guaranteedReward = builder.guaranteedReward;
        this.recovered = builder.recovered;
        this.rollback = builder.rollback;
        this.pityBeforeOpen = Math.max(0, builder.pityBeforeOpen);
        this.tokens = buildTokens(builder.extraTokens);
    }

    public static Builder builder(Plugin plugin, Player player) {
        return new Builder(plugin, player);
    }

    public Builder toBuilder() {
        return builder(plugin, player)
                .caseProfileId(caseProfileId)
                .rewardId(rewardId)
                .rewardName(rewardName)
                .runtimeId(runtimeId)
                .animationId(animationId)
                .triggerId(triggerId)
                .guaranteedReward(guaranteedReward)
                .recovered(recovered)
                .rollback(rollback)
                .pityBeforeOpen(pityBeforeOpen)
                .tokens(tokens);
    }

    @Override
    public Plugin getPlugin() {
        return plugin;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public String getCaseProfileId() {
        return caseProfileId;
    }

    @Override
    public String getRewardId() {
        return rewardId;
    }

    @Override
    public String getRewardName() {
        return rewardName;
    }

    @Override
    public String getRuntimeId() {
        return runtimeId;
    }

    @Override
    public String getAnimationId() {
        return animationId;
    }

    @Override
    public String getTriggerId() {
        return triggerId;
    }

    @Override
    public boolean isGuaranteedReward() {
        return guaranteedReward;
    }

    @Override
    public boolean isRecovered() {
        return recovered;
    }

    @Override
    public boolean isRollback() {
        return rollback;
    }

    @Override
    public int getPityBeforeOpen() {
        return pityBeforeOpen;
    }

    @Override
    public Map<String, String> getTokens() {
        return tokens;
    }

    @Override
    public String replaceTokens(String value) {
        String result = value == null ? "" : value;
        for (Map.Entry<String, String> entry : tokens.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    public CaseExecutionContext withTrigger(String triggerId) {
        return toBuilder().triggerId(triggerId).build();
    }

    public CaseExecutionContext withRollback(boolean rollback) {
        return toBuilder().rollback(rollback).build();
    }

    public CaseExecutionContext withRecovered(boolean recovered) {
        return toBuilder().recovered(recovered).build();
    }

    private Map<String, String> buildTokens(Map<String, String> extras) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("%player%", player.getName());
        values.put("%player_uuid%", player.getUniqueId().toString());
        values.put("%case%", caseProfileId);
        values.put("%reward%", rewardName);
        values.put("%reward_id%", rewardId);
        values.put("%instance%", runtimeId);
        values.put("%animation%", animationId);
        values.put("%trigger%", triggerId);
        values.put("%pity%", String.valueOf(pityBeforeOpen));
        values.put("%guaranteed%", String.valueOf(guaranteedReward));
        values.put("%recovered%", String.valueOf(recovered));
        values.put("%rollback%", String.valueOf(rollback));
        values.putAll(extras);
        return Map.copyOf(values);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public static final class Builder {
        private final Plugin plugin;
        private final Player player;
        private String caseProfileId = "";
        private String rewardId = "";
        private String rewardName = "";
        private String runtimeId = "";
        private String animationId = "";
        private String triggerId = "";
        private boolean guaranteedReward;
        private boolean recovered;
        private boolean rollback;
        private int pityBeforeOpen;
        private final Map<String, String> extraTokens = new LinkedHashMap<>();

        private Builder(Plugin plugin, Player player) {
            this.plugin = plugin;
            this.player = player;
        }

        public Builder caseProfileId(String caseProfileId) {
            this.caseProfileId = caseProfileId;
            return this;
        }

        public Builder rewardId(String rewardId) {
            this.rewardId = rewardId;
            return this;
        }

        public Builder rewardName(String rewardName) {
            this.rewardName = rewardName;
            return this;
        }

        public Builder runtimeId(String runtimeId) {
            this.runtimeId = runtimeId;
            return this;
        }

        public Builder animationId(String animationId) {
            this.animationId = animationId;
            return this;
        }

        public Builder triggerId(String triggerId) {
            this.triggerId = triggerId;
            return this;
        }

        public Builder guaranteedReward(boolean guaranteedReward) {
            this.guaranteedReward = guaranteedReward;
            return this;
        }

        public Builder recovered(boolean recovered) {
            this.recovered = recovered;
            return this;
        }

        public Builder rollback(boolean rollback) {
            this.rollback = rollback;
            return this;
        }

        public Builder pityBeforeOpen(int pityBeforeOpen) {
            this.pityBeforeOpen = pityBeforeOpen;
            return this;
        }

        public Builder token(String key, String value) {
            this.extraTokens.put(key, value == null ? "" : value);
            return this;
        }

        public Builder tokens(Map<String, String> tokens) {
            this.extraTokens.putAll(tokens);
            return this;
        }

        public CaseExecutionContext build() {
            return new CaseExecutionContext(this);
        }
    }
}
