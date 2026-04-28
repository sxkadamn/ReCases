package net.recases.domain;

import net.recases.management.CaseItem;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseProfileTest {

    @Test
    void hardGuaranteePicksRareRewardWhenRareOnlyIsRequested() {
        CaseItem common = reward("common", 100, false);
        CaseItem rare = reward("rare", 5, true);
        CaseProfile profile = new CaseProfile(
                "alpha",
                "CHEST",
                0,
                "Alpha",
                List.of(),
                "classic",
                new PitySettings(5, false, 0, 1.0D, 1.0D),
                List.of(),
                Map.of(),
                List.of(common, rare)
        );

        boolean guarantee = profile.getPitySettings().isHardGuaranteeReached(4);
        CaseItem picked = profile.pickReward(new Random(7), guarantee, 4, item -> true);

        assertTrue(guarantee, "hard guarantee should be active on 5th open");
        assertEquals("rare", picked.getId(), "rare reward should be selected when rareOnly=true");
    }

    @Test
    void pityCurveIncreasesRareWeightMultiplier() {
        PitySettings settings = new PitySettings(10, true, 2, 3.0D, 1.0D);

        double early = settings.getRareWeightMultiplier(0);
        double mid = settings.getRareWeightMultiplier(5);
        double late = settings.getRareWeightMultiplier(9);

        assertEquals(1.0D, early, 0.0001D);
        assertTrue(mid > early, "multiplier must grow after curve start");
        assertTrue(late > mid, "multiplier must continue growing near guarantee");
    }

    private static CaseItem reward(String id, int chance, boolean rare) {
        return new CaseItem(id, id, new ItemStack(Material.STONE), List.of("message;ok"), List.of(), List.of(), Map.of(), chance, rare) {
        };
    }
}
