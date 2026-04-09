package net.recases.domain;

import net.recases.management.CaseItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

public class CaseProfile {

    private final String id;
    private final String menuMaterial;
    private final int menuSlot;
    private final String menuDisplay;
    private final List<String> menuLore;
    private final String animationId;
    private final PitySettings pitySettings;
    private final List<String> conditions;
    private final Map<String, List<String>> triggers;
    private final List<CaseItem> rewards;

    public CaseProfile(String id, String menuMaterial, int menuSlot, String menuDisplay, List<String> menuLore, String animationId, int guaranteeAfterOpens, List<CaseItem> rewards) {
        this(id, menuMaterial, menuSlot, menuDisplay, menuLore, animationId, new PitySettings(guaranteeAfterOpens, false, 0, 1.0D, 1.0D), List.of(), Map.of(), rewards);
    }

    public CaseProfile(String id, String menuMaterial, int menuSlot, String menuDisplay, List<String> menuLore, String animationId,
                       PitySettings pitySettings, List<String> conditions, Map<String, List<String>> triggers, List<CaseItem> rewards) {
        this.id = id;
        this.menuMaterial = menuMaterial;
        this.menuSlot = menuSlot;
        this.menuDisplay = menuDisplay;
        this.menuLore = new ArrayList<>(menuLore);
        this.animationId = animationId;
        this.pitySettings = pitySettings;
        this.conditions = new ArrayList<>(conditions == null ? List.of() : conditions);
        this.triggers = copyTriggers(triggers);
        this.rewards = new ArrayList<>(rewards);
    }

    public String getId() {
        return id;
    }

    public String getMenuMaterial() {
        return menuMaterial;
    }

    public int getMenuSlot() {
        return menuSlot;
    }

    public String getMenuDisplay() {
        return menuDisplay;
    }

    public List<String> getMenuLore() {
        return Collections.unmodifiableList(menuLore);
    }

    public String getAnimationId() {
        return animationId;
    }

    public int getGuaranteeAfterOpens() {
        return pitySettings.getHardGuaranteeAfterOpens();
    }

    public PitySettings getPitySettings() {
        return pitySettings;
    }

    public List<String> getConditions() {
        return Collections.unmodifiableList(conditions);
    }

    public Map<String, List<String>> getTriggers() {
        return Collections.unmodifiableMap(triggers);
    }

    public List<String> getTriggerActions(String triggerId) {
        if (triggerId == null || triggerId.trim().isEmpty()) {
            return List.of();
        }
        return triggers.getOrDefault(triggerId.toLowerCase(), List.of());
    }

    public List<CaseItem> getRewards() {
        return Collections.unmodifiableList(rewards);
    }

    public CaseItem getReward(String rewardId) {
        if (rewardId == null || rewardId.trim().isEmpty()) {
            return null;
        }

        for (CaseItem reward : rewards) {
            if (rewardId.equalsIgnoreCase(reward.getId())) {
                return reward;
            }
        }
        return null;
    }

    public boolean hasRewards() {
        return !rewards.isEmpty();
    }

    public CaseItem pickReward(Random random) {
        return pickReward(random, false, 0, reward -> true);
    }

    public CaseItem pickReward(Random random, boolean rareOnly) {
        return pickReward(random, rareOnly, 0, reward -> true);
    }

    public CaseItem pickReward(Random random, boolean rareOnly, int pityBeforeOpen, Predicate<CaseItem> filter) {
        double totalWeight = 0.0D;
        List<WeightedReward> weighted = new ArrayList<>();
        for (CaseItem reward : rewards) {
            if (rareOnly && !reward.isRare()) {
                continue;
            }
            if (filter != null && !filter.test(reward)) {
                continue;
            }
            double weight = reward.getChance();
            if (!rareOnly && reward.isRare()) {
                weight *= pitySettings.getRareWeightMultiplier(pityBeforeOpen);
            }
            if (weight <= 0.0D) {
                continue;
            }
            totalWeight += weight;
            weighted.add(new WeightedReward(reward, totalWeight));
        }
        if (weighted.isEmpty() || totalWeight <= 0.0D) {
            return null;
        }

        double target = random.nextDouble() * totalWeight;
        for (WeightedReward reward : weighted) {
            if (target <= reward.cumulativeWeight()) {
                return reward.caseItem();
            }
        }
        return weighted.get(weighted.size() - 1).caseItem();
    }

    public boolean hasRareRewards() {
        return rewards.stream().anyMatch(CaseItem::isRare);
    }

    public int getTotalChance() {
        return rewards.stream()
                .mapToInt(CaseItem::getChance)
                .sum();
    }

    public double getChancePercent(CaseItem reward) {
        if (reward == null) {
            return 0.0D;
        }

        int totalChance = getTotalChance();
        if (totalChance <= 0) {
            return 0.0D;
        }

        return (reward.getChance() * 100.0D) / totalChance;
    }

    private Map<String, List<String>> copyTriggers(Map<String, List<String>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }

        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            result.put(entry.getKey().toLowerCase(), List.copyOf(entry.getValue()));
        }
        return result;
    }

    private record WeightedReward(CaseItem caseItem, double cumulativeWeight) {
    }
}

