package net.recases.animations.opening;

import net.recases.app.PluginContext;
import net.recases.management.OpeningSession;
import net.recases.runtime.CaseRuntime;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class ClassicOpeningAnimation extends AbstractChestOpeningAnimation {

    private static final int[][] OFFSETS = {
            {0, 0, -4},
            {-4, 0, 0},
            {4, 0, 0},
            {0, 0, 4}
    };

    public ClassicOpeningAnimation(PluginContext plugin, Player player, CaseRuntime runtime) {
        super(plugin, player, runtime);
    }

    @Override
    protected int startDelayTicks() {
        return 18;
    }

    @Override
    protected void beforeSpawn(OpeningSession session) {
        super.beforeSpawn(session);
        new BukkitRunnable() {
            private int pulse;

            @Override
            public void run() {
                if (!isActive(session) || pulse++ >= 6 || runtime.getLocation().getWorld() == null) {
                    cancel();
                    return;
                }

                runtime.getLocation().getWorld().spawnParticle(isPremiumReward(session) ? Particle.GLOW : Particle.ENCHANT, runtime.getLocation().clone().add(0.5, 1.0, 0.5), scaled(18), 0.9, 0.4, 0.9, 0.05);
                runtime.getLocation().getWorld().spawnParticle(isPremiumReward(session) ? Particle.END_ROD : Particle.CRIT, runtime.getLocation().clone().add(0.5, 0.6, 0.5), scaled(8), 0.15, 0.15, 0.15, 0.01);
                runtime.getLocation().getWorld().playSound(runtime.getLocation(), isPremiumReward(session) ? Sound.BLOCK_BEACON_POWER_SELECT : Sound.BLOCK_AMETHYST_BLOCK_RESONATE, volume(0.6F), 0.9F + (pulse * 0.06F));
            }
        }.runTaskTimer(plugin, 0L, performance.cadence(4L));
    }

    @Override
    protected void afterSpawn(OpeningSession session) {
        if (runtime.getLocation().getWorld() == null) {
            return;
        }

        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (!isActive(session) || runtime.getLocation().getWorld() == null || tick++ >= 10) {
                    cancel();
                    return;
                }

                double radius = 1.2D + (tick * 0.22D);
                runtime.getLocation().getWorld().spawnParticle(
                        isPremiumReward(session) ? Particle.END_ROD : Particle.WAX_ON,
                        runtime.getLocation().clone().add(0.5, 0.8, 0.5),
                        scaled(18),
                        radius,
                        0.12,
                        radius,
                        0.01
                );
                runtime.getLocation().getWorld().spawnParticle(
                        isPremiumReward(session) ? Particle.GLOW : Particle.HAPPY_VILLAGER,
                        runtime.getLocation().clone().add(0.5, 1.1, 0.5),
                        scaled(12),
                        0.2,
                        0.35,
                        0.2,
                        0.01
                );
            }
        }.runTaskTimer(plugin, performance.cadence(2L), performance.cadence(3L));
        runtime.getLocation().getWorld().playSound(runtime.getLocation(), isPremiumReward(session) ? Sound.BLOCK_ENDER_CHEST_OPEN : Sound.BLOCK_BEACON_ACTIVATE, volume(0.8F), 1.1F);
    }

    @Override
    protected int[][] offsets() {
        return OFFSETS;
    }
}
