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

public class VoidRiftOpeningAnimation extends AbstractEntityOpeningAnimation {

    private static final double[][] POSITIONS = {
            {-2.8, 1.0, 0.0},
            {0.0, 1.6, -2.8},
            {2.8, 1.0, 0.0},
            {0.0, 1.6, 2.8}
    };

    public VoidRiftOpeningAnimation(PluginContext plugin, Player player, CaseRuntime runtime) {
        super(plugin, player, runtime);
    }

    @Override
    protected int startDelayTicks() {
        return 26;
    }

    @Override
    protected void spawnTargets(OpeningSession session) {
        if (runtime.getLocation().getWorld() == null) {
            return;
        }

        for (int i = 0; i < POSITIONS.length; i++) {
            int index = i;
            double[] position = POSITIONS[i];
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!isActive(session) || runtime.getLocation().getWorld() == null) {
                        return;
                    }

                    runtime.getLocation().getWorld().spawnParticle(premiumParticle(session, Particle.PORTAL, Particle.DRAGON_BREATH), center(position[0], 1.0, position[2]), 32, 0.28, 0.55, 0.28, 0.2);
                    ArmorStand stand = (ArmorStand) runtime.getLocation().getWorld().spawnEntity(center(position[0], position[1], position[2]), EntityType.ARMOR_STAND);
                    configureArmorStandTarget(stand, themedLabel(session, "разлом", index + 1), previewDisplayItem(session, "ITEM;ENDER_EYE", "Разлом"));
                    stand.setBodyYaw(index * 90.0F);
                    trackEntity(session, stand);
                    runtime.getLocation().getWorld().playSound(stand.getLocation(), premiumSound(session, Sound.ENTITY_ENDERMAN_TELEPORT, Sound.BLOCK_END_PORTAL_SPAWN), 0.65F, 0.9F);
                }
            }.runTaskLater(plugin, i * 6L);
        }

        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (!isActive(session) || runtime.getLocation().getWorld() == null || tick++ >= 18) {
                    cancel();
                    return;
                }
                runtime.getLocation().getWorld().spawnParticle(premiumParticle(session, Particle.PORTAL, Particle.DRAGON_BREATH), runtime.getLocation().clone().add(0.5, 1.0, 0.5), 36, 1.4, 0.45, 1.4, 0.24);
                runtime.getLocation().getWorld().spawnParticle(premiumParticle(session, Particle.REVERSE_PORTAL, Particle.GLOW), runtime.getLocation().clone().add(0.5, 1.65, 0.5), 10, 0.2, 0.35, 0.2, 0.01);
                runtime.getLocation().getWorld().playSound(runtime.getLocation(), premiumSound(session, Sound.BLOCK_PORTAL_AMBIENT, Sound.BLOCK_END_PORTAL_FRAME_FILL), 0.4F, 0.7F + (tick * 0.02F));
            }
        }.runTaskTimer(plugin, 0L, 2L);

        promptSelectionLater(session, 34L);
        pulseTargets(session, premiumParticle(session, Particle.DRAGON_BREATH, Particle.GLOW), 7L, 18, 0.95, 10, 0.16, 0.01);
    }
}
