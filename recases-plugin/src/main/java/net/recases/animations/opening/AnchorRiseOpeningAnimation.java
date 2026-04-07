package net.recases.animations.opening;

import net.recases.app.PluginContext;
import net.recases.management.CaseItem;
import net.recases.management.OpeningSession;
import net.recases.runtime.CaseRuntime;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class AnchorRiseOpeningAnimation implements OpeningAnimation {

    public static final String ANCHOR_RISE_METADATA = "case_anchor_rise";

    private final PluginContext plugin;
    private final Player player;
    private final CaseRuntime runtime;
    private final AnimationPerformance performance;

    public AnchorRiseOpeningAnimation(PluginContext plugin, Player player, CaseRuntime runtime) {
        this.plugin = plugin;
        this.player = player;
        this.runtime = runtime;
        this.performance = AnimationPerformance.create(plugin);
    }

    @Override
    public boolean play() {
        OpeningSession session = runtime.getSession();
        if (session == null || !runtime.isAvailable() || session.getFinalReward() == null) {
            return false;
        }

        runtime.removeHologram();
        player.playSound(runtime.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, volume(1.0F), 0.82F);
        startPulse();

        Block block = runtime.getLocation().getBlock();
        BlockData originalData = block.getBlockData().clone();
        block.setType(Material.RESPAWN_ANCHOR);

        new BukkitRunnable() {
            private int charge = 2;

            @Override
            public void run() {
                if (!isActive(session)) {
                    restoreBlock(block, originalData);
                    plugin.getCaseService().abortOpening(runtime, true);
                    cancel();
                    return;
                }

                if (charge > 4) {
                    cancel();
                    startItemAnimation(session, block, originalData);
                    return;
                }

                updateAnchorCharge(block, charge++);
            }
        }.runTaskTimer(plugin, 0L, performance.cadence(10L));

        return true;
    }

    private void startItemAnimation(OpeningSession session, Block block, BlockData originalData) {
        Location start = runtime.getLocation().clone().add(0.5, 0.5, 0.5);
        ArmorStand stand = createArmorStand(start, session.getFinalReward().getIcon());
        session.trackEntity(stand);
        List<ItemStack> rewards = buildRewardPool(session);

        new BukkitRunnable() {
            private int tick;
            private int phase;
            private Vector velocity = new Vector(0.0D, riseSpeed(), 0.0D);
            private boolean fireworksLaunched;

            @Override
            public void run() {
                if (!isActive(session) || stand.isDead()) {
                    restoreBlock(block, originalData);
                    removeStand(stand);
                    plugin.getCaseService().abortOpening(runtime, true);
                    cancel();
                    return;
                }

                tick += performance.motionInterval();
                if (tick >= totalDuration()) {
                    cancel();
                    finish(session, stand, block, originalData);
                    return;
                }

                if (tick % itemChangeInterval() == 0 && phase != 2 && !rewards.isEmpty()) {
                    stand.getEquipment().setHelmet(rewards.get(ThreadLocalRandom.current().nextInt(rewards.size())));
                    playCycleSound(stand.getLocation());
                }

                if (phase == 0 && stand.getLocation().getY() >= start.getY() + riseHeight()) {
                    phase = 1;
                    velocity = new Vector(0.0D, 0.0D, 0.0D);
                } else if (phase == 1 && tick >= riseDuration() + hoverDuration()) {
                    phase = 2;
                    velocity = start.toVector().subtract(stand.getLocation().toVector()).multiply(fallMultiplier());
                }

                if (phase == 2 && !fireworksLaunched) {
                    launchFireworks(start.clone().add(0.0D, 1.0D, 0.0D));
                    fireworksLaunched = true;
                }

                stand.teleport(stand.getLocation().add(velocity.clone().multiply(performance.motionInterval())));

                if (phase == 2 && stand.getLocation().distanceSquared(start) < 0.25D) {
                    velocity.multiply(0.0D);
                    stand.teleport(start);
                }

                spawnTrailParticles(stand.getLocation(), phase);
            }
        }.runTaskTimer(plugin, 0L, performance.motionInterval());
    }

    private void finish(OpeningSession session, ArmorStand stand, Block block, BlockData originalData) {
        stand.getEquipment().setHelmet(session.getFinalReward().getIcon());

        new BukkitRunnable() {
            private int charge = 3;

            @Override
            public void run() {
                if (!isActive(session)) {
                    restoreBlock(block, originalData);
                    removeStand(stand);
                    plugin.getCaseService().abortOpening(runtime, true);
                    cancel();
                    return;
                }

                if (charge < 0) {
                    restoreBlock(block, originalData);
                    stand.remove();
                    plugin.getOpeningResults().complete(player, runtime, session, session.getFinalReward());
                    cancel();
                    return;
                }

                updateAnchorCharge(block, charge--);
            }
        }.runTaskTimer(plugin, 0L, performance.cadence(10L));
    }

    private ArmorStand createArmorStand(Location location, ItemStack item) {
        ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setCollidable(false);
        stand.setSmall(true);
        stand.setMarker(true);
        stand.setSilent(true);
        stand.getEquipment().setHelmet(item);
        stand.setMetadata(ANCHOR_RISE_METADATA, new FixedMetadataValue(plugin, runtime.getId()));
        return stand;
    }

    private List<ItemStack> buildRewardPool(OpeningSession session) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < fakeRewardPoolSize(); i++) {
            CaseItem fake = plugin.getCaseService().getRandomReward(session.getSelectedCase());
            if (fake != null) {
                items.add(fake.getIcon());
            }
        }
        if (items.isEmpty()) {
            items.add(session.getFinalReward().getIcon());
        }
        return items;
    }

    private void spawnTrailParticles(Location location, int phase) {
        if (location.getWorld() == null) {
            return;
        }
        if (phase < 2) {
            location.getWorld().spawnParticle(Particle.ENCHANT, location.clone().add(0.0D, 0.2D, 0.0D), scaled(8), 0.08, 0.12, 0.08, 0.01);
        } else {
            location.getWorld().spawnParticle(Particle.FIREWORK, location.clone().add(0.0D, 0.15D, 0.0D), scaled(10), 0.08, 0.08, 0.08, 0.01);
        }
    }

    private void playCycleSound(Location location) {
        location.getWorld().playSound(location, Sound.BLOCK_NOTE_BLOCK_PLING, volume(0.5F), 2.0F);
    }

    private void launchFireworks(Location location) {
        if (location.getWorld() == null) {
            return;
        }
        Firework firework = (Firework) location.getWorld().spawnEntity(location, EntityType.FIREWORK_ROCKET);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .withColor(Color.RED, Color.ORANGE, Color.YELLOW)
                .with(FireworkEffect.Type.BURST)
                .flicker(true)
                .build());
        meta.setPower(1);
        firework.setFireworkMeta(meta);
        firework.setVelocity(new Vector(0.0D, 0.5D, 0.0D));
    }

    private void updateAnchorCharge(Block block, int charge) {
        if (block.getType() != Material.RESPAWN_ANCHOR) {
            return;
        }
        RespawnAnchor anchor = (RespawnAnchor) block.getBlockData();
        anchor.setCharges(Math.min(4, Math.max(0, charge)));
        block.setBlockData(anchor);
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, volume(1.0F), 1.0F);
    }

    private void restoreBlock(Block block, BlockData originalData) {
        block.setBlockData(originalData);
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, volume(1.0F), 1.0F);
    }

    private void removeStand(ArmorStand stand) {
        if (stand != null && stand.isValid()) {
            stand.remove();
        }
    }

    private boolean isActive(OpeningSession session) {
        return session != null
                && runtime.getSession() == session
                && runtime.isOpening()
                && runtime.getLocation().getWorld() != null
                && player.isOnline()
                && session.isParticipant(player)
                && session.getFinalReward() != null;
    }

    private int riseDuration() {
        return Math.max(10, plugin.getConfig().getInt("settings.animations.anchor-rise.rise-duration", 30));
    }

    private int hoverDuration() {
        return Math.max(10, plugin.getConfig().getInt("settings.animations.anchor-rise.hover-duration", 20));
    }

    private int totalDuration() {
        return riseDuration() + hoverDuration() + fallDuration();
    }

    private int fallDuration() {
        return Math.max(10, plugin.getConfig().getInt("settings.animations.anchor-rise.fall-duration", 20));
    }

    private int itemChangeInterval() {
        return Math.max(1, plugin.getConfig().getInt("settings.animations.anchor-rise.item-change-interval", 5));
    }

    private double riseSpeed() {
        return plugin.getConfig().getDouble("settings.animations.anchor-rise.rise-speed", 0.2D);
    }

    private double riseHeight() {
        return plugin.getConfig().getDouble("settings.animations.anchor-rise.rise-height", 6.0D);
    }

    private double fallMultiplier() {
        return plugin.getConfig().getDouble("settings.animations.anchor-rise.fall-multiplier", 0.05D);
    }

    private int fakeRewardPoolSize() {
        return Math.max(4, plugin.getConfig().getInt("settings.animations.anchor-rise.fake-reward-pool-size", 12));
    }

    private int scaled(int base) {
        return performance.particles(base);
    }

    private void startPulse() {
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (runtime.getLocation().getWorld() == null || tick++ >= 12) {
                    cancel();
                    return;
                }
                runtime.getLocation().getWorld().spawnParticle(Particle.ENCHANT, runtime.getLocation().clone().add(0.5, 0.85, 0.5), scaled(10), 0.18, 0.18, 0.18, 0.02);
                runtime.getLocation().getWorld().spawnParticle(Particle.GLOW, runtime.getLocation().clone().add(0.5, 1.05, 0.5), scaled(6), 0.32, 0.15, 0.32, 0.01);
            }
        }.runTaskTimer(plugin, 0L, performance.cadence(2L));
    }

    private float volume(float base) {
        return performance.volume(base);
    }
}
