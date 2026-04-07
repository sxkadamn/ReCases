package net.recases.animations.opening;

import net.recases.app.PluginContext;
import net.recases.management.OpeningSession;
import net.recases.runtime.CaseRuntime;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class CircleOpeningAnimation extends AbstractChestOpeningAnimation {

    private static final int[][] OFFSETS = {
            {0, 0, -4},
            {-3, 0, -2},
            {-3, 0, 2},
            {0, 0, 4},
            {3, 0, 2},
            {3, 0, -2}
    };

    public CircleOpeningAnimation(PluginContext plugin, Player player, CaseRuntime runtime) {
        super(plugin, player, runtime);
    }

    @Override
    protected int startDelayTicks() {
        return 26;
    }

    @Override
    protected void beforeSpawn(OpeningSession session) {
        super.beforeSpawn(session);
        new BukkitRunnable() {
            private int step;

            @Override
            public void run() {
                if (!isActive(session) || runtime.getLocation().getWorld() == null || step++ >= 16) {
                    cancel();
                    return;
                }

                double angle = step * (Math.PI / 8);
                double x = Math.cos(angle) * 3.4;
                double z = Math.sin(angle) * 3.4;
                runtime.getLocation().getWorld().spawnParticle(isPremiumReward(session) ? Particle.WITCH : Particle.END_ROD, runtime.getLocation().clone().add(0.5 + x, 1.0, 0.5 + z), scaled(8), 0.08, 0.08, 0.08, 0.01);
                runtime.getLocation().getWorld().spawnParticle(isPremiumReward(session) ? Particle.DRAGON_BREATH : Particle.GLOW, runtime.getLocation().clone().add(0.5 - x, 0.6, 0.5 - z), scaled(4), 0.04, 0.04, 0.04, 0.0);
                runtime.getLocation().getWorld().playSound(runtime.getLocation(), isPremiumReward(session) ? Sound.BLOCK_AMETHYST_CLUSTER_HIT : Sound.BLOCK_NOTE_BLOCK_CHIME, volume(0.45F), 0.8F + (step * 0.03F));
            }
        }.runTaskTimer(plugin, 0L, performance.cadence(2L));
    }

    @Override
    protected void spawnChests(OpeningSession session, int[][] offsets) {
        for (int i = 0; i < offsets.length; i++) {
            int[] offset = offsets[i];
            long delay = (i % 2 == 0 ? i : i + 2L) * 2L;
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!isActive(session)) {
                        return;
                    }
                    spawnChestTarget(session, center(offset[0], offset[1], offset[2]), isPremiumReward(session) ? Material.PURPUR_BLOCK : Material.SMOOTH_STONE);
                }
            }.runTaskLater(plugin, performance.cadence(delay));
        }
    }

    @Override
    protected void afterSpawn(OpeningSession session) {
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (!isActive(session) || runtime.getLocation().getWorld() == null || tick++ >= 12) {
                    cancel();
                    return;
                }

                runtime.getLocation().getWorld().spawnParticle(isPremiumReward(session) ? Particle.DRAGON_BREATH : Particle.GLOW, runtime.getLocation().clone().add(0.5, 0.8, 0.5), scaled(18), 2.8, 0.1, 2.8, 0.02);
                runtime.getLocation().getWorld().spawnParticle(isPremiumReward(session) ? Particle.WITCH : Particle.END_ROD, runtime.getLocation().clone().add(0.5, 1.45, 0.5), scaled(10), 0.2, 0.25, 0.2, 0.01);
            }
        }.runTaskTimer(plugin, performance.cadence(10L), performance.cadence(4L));
    }

    @Override
    protected int[][] offsets() {
        return OFFSETS;
    }
}
