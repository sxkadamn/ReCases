package net.recases.domain;

import net.recases.management.CaseItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class CaseProfile {

    private final String id;
    private final String menuMaterial;
    private final int menuSlot;
    private final String menuDisplay;
    private final List<String> menuLore;
    private final String animationId;
    private final int guaranteeAfterOpens;
    private final List<CaseItem> rewards;

    public CaseProfile(String id, String menuMaterial, int menuSlot, String menuDisplay, List<String> menuLore, String animationId, int guaranteeAfterOpens, List<CaseItem> rewards) {
        this.id = id;
        this.menuMaterial = menuMaterial;
        this.menuSlot = menuSlot;
        this.menuDisplay = menuDisplay;
        this.menuLore = new ArrayList<>(menuLore);
        this.animationId = animationId;
        this.guaranteeAfterOpens = guaranteeAfterOpens;
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
        return guaranteeAfterOpens;
    }

    public List<CaseItem> getRewards() {
        return Collections.unmodifiableList(rewards);
    }

    public boolean hasRewards() {
        return !rewards.isEmpty();
    }

    public CaseItem pickReward(Random random) {
        return pickReward(random, false);
    }

    public CaseItem pickReward(Random random, boolean rareOnly) {
        List<CaseItem> weighted = new ArrayList<>();
        for (CaseItem reward : rewards) {
            if (rareOnly && !reward.isRare()) {
                continue;
            }
            for (int i = 0; i < reward.getChance(); i++) {
                weighted.add(reward);
            }
        }
        return weighted.isEmpty() ? null : weighted.get(random.nextInt(weighted.size()));
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
}

