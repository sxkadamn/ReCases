package net.recases.animations.opening;

import net.recases.app.PluginContext;
import net.recases.management.OpeningSession;
import net.recases.runtime.CaseRuntime;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class MeteorDropOpeningAnimation extends AbstractEntityOpeningAnimation {

    private static final double[][] POSITIONS = {
            {-3.0, 0.3, -2.4},
            {3.0, 0.3, -2.4},
            {-3.0, 0.3, 2.4},
            {3.0, 0.3, 2.4}
    };

    public MeteorDropOpeningAnimation(PluginContext plugin, Player player, CaseRuntime runtime) {
        super(plugin, player, runtime);
    }

    @Override
    protected int startDelayTicks() {
        return 28;
    }

    @Override
    protected void spawnTargets(OpeningSession session) {
        if (runtime.getLocation().getWorld() == null) {
            return;
        }

        runtime.getLocation().getWorld().playSound(runtime.getLocation(), premiumSound(session, Sound.ENTITY_BLAZE_SHOOT, Sound.ITEM_TRIDENT_THUNDER), volume(0.8F), 0.8F);

        for (int i = 0; i < POSITIONS.length; i++) {
            int index = i;
            double[] position = POSITIONS[i];
            new BukkitRunnable() {
                private int tick;

                @Override
                public void run() {
                    if (!isActive(session) || runtime.getLocation().getWorld() == null) {
                        cancel();
                        return;
                    }

                    double y = 5.6D - (tick * 0.55D);
                    runtime.getLocation().getWorld().spawnParticle(premiumParticle(session, Particle.FLAME, Particle.SOUL_FIRE_FLAME), center(position[0], Math.max(y, 0.6D), position[2]), scaled(18), 0.18, 0.18, 0.18, 0.02);
                    runtime.getLocation().getWorld().spawnParticle(premiumParticle(session, Particle.SMOKE, Particle.ASH), center(position[0], Math.max(y + 0.35D, 0.8D), position[2]), scaled(8), 0.12, 0.12, 0.12, 0.01);
                    tick++;

                    if (y <= 0.7D) {
                        ArmorStand stand = (ArmorStand) runtime.getLocation().getWorld().spawnEntity(center(position[0], position[1], position[2]), EntityType.ARMOR_STAND);
                        configureArmorStandTarget(stand, themedLabel(session, "осколок", index + 1), previewDisplayItem(session, "ITEM;MAGMA_CREAM", "Осколок"));
                        trackEntity(session, stand);
                        runtime.getLocation().getWorld().spawnParticle(premiumParticle(session, Particle.LAVA, Particle.SOUL), stand.getLocation().clone().add(0.0, 0.8, 0.0), scaled(14), 0.22, 0.18, 0.22, 0.02);
                        runtime.getLocation().getWorld().spawnParticle(premiumParticle(session, Particle.LARGE_SMOKE, Particle.ASH), stand.getLocation().clone().add(0.0, 0.6, 0.0), scaled(18), 0.28, 0.18, 0.28, 0.02);
                        runtime.getLocation().getWorld().spawnParticle(premiumParticle(session, Particle.EXPLOSION, Particle.GUST_EMITTER_SMALL), stand.getLocation().clone().add(0.0, 0.7, 0.0), 1, 0.0, 0.0, 0.0, 0.0);
                        runtime.getLocation().getWorld().playSound(stand.getLocation(), premiumSound(session, Sound.ENTITY_GENERIC_EXPLODE, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE), volume(0.7F), 0.9F + (index * 0.04F));
                        cancel();
                    }
                }
            }.runTaskTimer(plugin, performance.cadence(i * 4L), performance.cadence(2L));
        }

        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (!isActive(session) || runtime.getLocation().getWorld() == null || tick++ >= 9) {
                    cancel();
                    return;
                }

                runtime.getLocation().getWorld().spawnParticle(premiumParticle(session, Particle.LARGE_SMOKE, Particle.ASH), runtime.getLocation().clone().add(0.5, 0.8, 0.5), scaled(14), 0.8, 0.18, 0.8, 0.02);
            }
        }.runTaskTimer(plugin, performance.cadence(8L), performance.cadence(4L));

        promptSelectionLater(session, 34L);
        pulseTargets(session, premiumParticle(session, Particle.FLAME, Particle.SOUL_FIRE_FLAME), 6L, 18, 0.85, 12, 0.18, 0.02);
    }
}
