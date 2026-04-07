package net.recases.animations.opening;

import net.recases.app.PluginContext;
import net.recases.management.OpeningSession;
import net.recases.runtime.CaseRuntime;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public abstract class AbstractEntityOpeningAnimation implements OpeningAnimation {

    public static final String ENTITY_TARGET_METADATA = "case_open_target";

    protected final PluginContext plugin;
    protected final Player player;
    protected final CaseRuntime runtime;
    protected final AnimationPerformance performance;

    protected AbstractEntityOpeningAnimation(PluginContext plugin, Player player, CaseRuntime runtime) {
        this.plugin = plugin;
        this.player = player;
        this.runtime = runtime;
        this.performance = AnimationPerformance.create(plugin);
    }

    @Override
    public boolean play() {
        OpeningSession session = runtime.getSession();
        if (session == null || !runtime.isAvailable()) {
            return false;
        }

        runtime.removeHologram();
        runtime.getLocation().getBlock().setType(Material.AIR);
        player.playSound(runtime.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0F, 0.8F);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isActive(session)) {
                    plugin.getCaseService().abortOpening(runtime, true);
                    return;
                }

                beforeSpawn(session);
                spawnTargets(session);
                afterSpawn(session);
            }
        }.runTaskLater(plugin, startDelayTicks());
        return true;
    }

    protected int startDelayTicks() {
        return 20;
    }

    protected void beforeSpawn(OpeningSession session) {
        if (runtime.getLocation().getWorld() == null) {
            return;
        }

        runtime.getLocation().getWorld().spawnParticle(
                Particle.ENCHANT,
                runtime.getLocation().clone().add(0.5, 1.0, 0.5),
                scaled(24),
                0.7,
                0.35,
                0.7,
                0.15
        );
        runtime.getLocation().getWorld().playSound(runtime.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, volume(0.8F), 1.1F);
    }

    protected void afterSpawn(OpeningSession session) {
    }

    protected abstract void spawnTargets(OpeningSession session);

    protected void trackEntity(OpeningSession session, Entity entity) {
        entity.setMetadata(ENTITY_TARGET_METADATA, new FixedMetadataValue(plugin, runtime.getId()));
        session.trackEntity(entity);
    }

    protected void configureLivingTarget(LivingEntity entity, String name) {
        entity.setAI(false);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setGlowing(true);
        entity.setRemoveWhenFarAway(false);
        entity.customName(plugin.getTextFormatter().asComponent(name));
        entity.setCustomNameVisible(true);
    }

    protected void configureArmorStandTarget(ArmorStand stand, String name, ItemStack headItem) {
        stand.setInvisible(true);
        stand.setMarker(false);
        stand.setGravity(false);
        stand.setSmall(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.setArms(false);
        stand.setBasePlate(false);
        stand.customName(plugin.getTextFormatter().asComponent(name));
        stand.setCustomNameVisible(true);
        if (headItem != null) {
            stand.getEquipment().setHelmet(headItem);
        }
    }

    protected boolean isPremiumReward(OpeningSession session) {
        return session != null && session.getFinalReward() != null && (session.getFinalReward().isRare() || session.isGuaranteedReward());
    }

    protected ItemStack previewIcon(OpeningSession session, String fallbackMaterial, String fallbackName) {
        OpeningStyle style = OpeningStyle.of(session);
        if (session != null && session.getFinalReward() != null && isPremiumReward(session)) {
            return session.getFinalReward().getIcon();
        }
        return style.preview(plugin, session, fallbackName);
    }

    protected ItemStack previewDisplayItem(OpeningSession session, String materialDefinition, String fallbackName) {
        if (session != null && session.getFinalReward() != null && isPremiumReward(session)) {
            return session.getFinalReward().getIcon();
        }
        return plugin.getItemFactory().create(materialDefinition, OpeningStyle.of(session).color() + fallbackName);
    }

    protected Sound premiumSound(OpeningSession session, Sound normal, Sound premium) {
        return isPremiumReward(session) ? premium : normal;
    }

    protected Particle premiumParticle(OpeningSession session, Particle normal, Particle premium) {
        return isPremiumReward(session) ? premium : normal;
    }

    protected String themedLabel(OpeningSession session, String role, int index) {
        return OpeningStyle.of(session).roleLabel(role, index);
    }

    protected Location center(double x, double y, double z) {
        return runtime.getLocation().clone().add(x, y, z);
    }

    protected boolean isActive(OpeningSession session) {
        return session != null
                && runtime.getSession() == session
                && runtime.isOpening()
                && runtime.getLocation().getWorld() != null
                && player.isOnline()
                && session.isParticipant(player);
    }

    protected void promptSelection(OpeningSession session) {
        if (!isActive(session)) {
            return;
        }

        String animationName = plugin.getAnimations().getDisplayName(session.getAnimationId());
        plugin.getMessages().title(
                player,
                "messages.case-select-target-title",
                "#ffd166Сделайте выбор",
                "messages.case-select-target-subtitle",
                "#ffffffАнимация: %animation%",
                5,
                40,
                10,
                "%animation%", animationName
        );
        plugin.getMessages().send(
                player,
                "messages.case-select-target-chat",
                "#a8dadcНажмите по сущности, чтобы завершить открытие.",
                "%animation%", animationName
        );
    }

    protected void promptSelectionLater(OpeningSession session, long delayTicks) {
        new BukkitRunnable() {
            @Override
            public void run() {
                promptSelection(session);
            }
        }.runTaskLater(plugin, performance.cadence(delayTicks));
    }

    protected void pulseTargets(OpeningSession session, Particle particle, long periodTicks, int iterations, double yOffset, int count, double spread, double speed) {
        new BukkitRunnable() {
            private int pulse;

            @Override
            public void run() {
                if (!isActive(session) || pulse++ >= iterations) {
                    cancel();
                    return;
                }

                for (UUID entityId : session.getTargetEntityIds()) {
                    Entity entity = Bukkit.getEntity(entityId);
                    if (entity != null && entity.isValid()) {
                        entity.getWorld().spawnParticle(
                                particle,
                                entity.getLocation().clone().add(0.0, yOffset, 0.0),
                                scaled(count),
                                spread,
                                spread,
                                spread,
                                speed
                        );
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, performance.cadence(periodTicks));
    }

    protected int scaled(int base) {
        return performance.particles(base);
    }

    protected float volume(float base) {
        return performance.volume(base);
    }
}
