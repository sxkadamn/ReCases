package net.recases.animations.opening;

import net.recases.app.PluginContext;
import net.recases.management.CaseItem;
import net.recases.management.OpeningSession;
import net.recases.runtime.CaseRuntime;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Lidded;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public class WheelOpeningAnimation implements OpeningAnimation {

    public static final String WHEEL_ENTITY_METADATA = "case_wheel_item";

    private final PluginContext plugin;
    private final Player player;
    private final CaseRuntime runtime;

    public WheelOpeningAnimation(PluginContext plugin, Player player, CaseRuntime runtime) {
        this.plugin = plugin;
        this.player = player;
        this.runtime = runtime;
    }

    @Override
    public boolean play() {
        OpeningSession session = runtime.getSession();
        if (session == null || !runtime.isAvailable() || session.getFinalReward() == null) {
            return false;
        }

        runtime.removeHologram();
        hideCaseBlock();
        player.playSound(runtime.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, volume(1.0F), 0.85F);
        startPulse(session);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isActive(session)) {
                    plugin.getCaseService().abortOpening(runtime, true);
                    return;
                }
                startWheel(session);
            }
        }.runTaskLater(plugin, 12L);
        return true;
    }

    private void startWheel(OpeningSession session) {
        if (!isActive(session) || runtime.getLocation().getWorld() == null) {
            plugin.getCaseService().abortOpening(runtime, true);
            return;
        }

        openChest();
        Location center = runtime.getLocation().clone().add(0.5, wheelCenterYOffset(), 0.5);
        List<WheelSlot> slots = buildSlots(session, center);
        if (slots.isEmpty()) {
            plugin.getCaseService().abortOpening(runtime, true);
            return;
        }

        spawnSlots(session, center, slots);
    }

    private List<WheelSlot> buildSlots(OpeningSession session, Location center) {
        List<WheelSlot> slots = new ArrayList<>();
        int count = Math.max(6, plugin.getConfig().getInt("settings.animations.wheel.prize-count", 8));
        double angleStep = 360.0D / count;
        int winnerIndex = ThreadLocalRandom.current().nextInt(count);

        for (int i = 0; i < count; i++) {
            boolean winner = i == winnerIndex;
            CaseItem prize = winner ? session.getFinalReward() : fallbackPrize(session);
            ArmorStand stand = createWheelStand(session, prize, center);
            slots.add(new WheelSlot(stand, prize, i * angleStep, winner));
        }
        return slots;
    }

    private void spawnSlots(OpeningSession session, Location center, List<WheelSlot> slots) {
        new BukkitRunnable() {
            private int index;

            @Override
            public void run() {
                if (!isActive(session) || runtime.getLocation().getWorld() == null) {
                    clearSlots(slots);
                    cancel();
                    plugin.getCaseService().abortOpening(runtime, true);
                    return;
                }

                if (index >= slots.size()) {
                    cancel();
                    spinWheel(session, center, slots);
                    return;
                }

                WheelSlot slot = slots.get(index++);
                animateSlotSpawn(session, center, slot);
            }
        }.runTaskTimer(plugin, 0L, spawnIntervalTicks());
    }

    private void animateSlotSpawn(OpeningSession session, Location center, WheelSlot slot) {
        Location spawn = center.clone().add(0.0, 0.15, 0.0);
        slot.stand.teleport(oriented(spawn));
        slot.stand.getWorld().playSound(spawn, Sound.ITEM_ARMOR_EQUIP_GENERIC, volume(0.8F), 1.1F);

        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (!isActive(session) || slot.stand.isDead()) {
                    cancel();
                    return;
                }

                tick++;
                double progress = Math.min(1.0D, tick / 8.0D);
                Location target = orbitPosition(center, slot.baseAngle, radius() * progress);
                slot.stand.teleport(target);
                spawnOrbitParticle(visualItemLocation(target), trailParticle(session), 2);

                if (progress >= 1.0D) {
                    Location visualTarget = visualItemLocation(target);
                    slot.stand.getWorld().playSound(visualTarget, Sound.BLOCK_AMETHYST_BLOCK_CHIME, volume(0.55F), 0.9F + (float) (slot.baseAngle / 360.0D));
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spinWheel(OpeningSession session, Location center, List<WheelSlot> slots) {
        WheelSlot winner = slots.stream().filter(slot -> slot.winner).findFirst().orElse(slots.get(0));

        new BukkitRunnable() {
            private double angleOffset;
            private double speed = maxSpeed();
            private long tick;

            @Override
            public void run() {
                if (!isActive(session) || runtime.getLocation().getWorld() == null) {
                    clearSlots(slots);
                    cancel();
                    plugin.getCaseService().abortOpening(runtime, true);
                    return;
                }

                tick++;
                angleOffset += speed;

                for (WheelSlot slot : slots) {
                    slot.currentAngle = normalizeDegrees(slot.baseAngle + angleOffset);
                    Location target = orbitPosition(center, slot.currentAngle, radius());
                    slot.stand.teleport(target);
                    spawnOrbitParticle(visualItemLocation(target), trailParticle(session), 1);
                }

                if (tick % 2L == 0L) {
                    runtime.getLocation().getWorld().playSound(runtime.getLocation(), Sound.UI_BUTTON_CLICK, volume(0.55F), 0.85F + (float) Math.min(speed / Math.max(1.0D, maxSpeed()), 0.8D));
                }

                speed = nextSpeed(speed, angleOffset);
                if (speed > minSpeed()) {
                    return;
                }

                if (distanceToSettle(winner.currentAngle) > Math.max(4.0D, minSpeed() * 2.0D)) {
                    return;
                }

                cancel();
                celebrateWinner(session, center, winner, slots);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void celebrateWinner(OpeningSession session, Location center, WheelSlot winner, List<WheelSlot> slots) {
        if (!isActive(session) || runtime.getLocation().getWorld() == null) {
            clearSlots(slots);
            plugin.getCaseService().abortOpening(runtime, true);
            return;
        }

        closeChest();
        Location winnerLocation = visualItemLocation(winner.stand.getLocation());
        spawnWinBurst(winnerLocation, session);
        runtime.getLocation().getWorld().playSound(runtime.getLocation(), isPremiumReward(session) ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.BLOCK_NOTE_BLOCK_CHIME, volume(1.0F), 1.05F);

        new BukkitRunnable() {
            private int index;

            @Override
            public void run() {
                if (!isActive(session)) {
                    clearSlots(slots);
                    cancel();
                    plugin.getCaseService().abortOpening(runtime, true);
                    return;
                }

                while (index < slots.size() && slots.get(index) == winner) {
                    index++;
                }

                if (index >= slots.size()) {
                    cancel();
                    moveWinnerToCase(session, winner, slots);
                    return;
                }

                WheelSlot slot = slots.get(index++);
                Location visualLocation = visualItemLocation(slot.stand.getLocation());
                spawnOrbitParticle(visualLocation, vanishParticle(session), 10);
                slot.stand.getWorld().playSound(visualLocation, Sound.BLOCK_FIRE_EXTINGUISH, volume(0.45F), 1.15F);
                removeIfValid(slot.stand);
            }
        }.runTaskTimer(plugin, 6L, 3L);
    }

    private void moveWinnerToCase(OpeningSession session, WheelSlot winner, List<WheelSlot> slots) {
        Location destination = runtime.getLocation().clone().add(0.5, -0.45 + visualHeightOffset(), 0.5);

        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (!isActive(session) || winner.stand.isDead()) {
                    clearSlots(slots);
                    cancel();
                    plugin.getCaseService().abortOpening(runtime, true);
                    return;
                }

                tick++;
                Vector direction = destination.toVector().subtract(winner.stand.getLocation().toVector());
                if (direction.lengthSquared() <= 0.02D || tick >= 20) {
                    winner.stand.teleport(oriented(destination.clone()));
                    finishWheel(session, winner, slots);
                    cancel();
                    return;
                }

                direction.normalize().multiply(0.22D);
                Location next = winner.stand.getLocation().clone().add(direction);
                winner.stand.teleport(oriented(next));
                spawnOrbitParticle(visualItemLocation(next), winParticle(session), 3);
                if (tick % 2 == 0) {
                    next.getWorld().playSound(visualItemLocation(next), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, volume(0.4F), 1.3F);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void finishWheel(OpeningSession session, WheelSlot winner, List<WheelSlot> slots) {
        Location caseCenter = visualItemLocation(runtime.getLocation().clone().add(0.5, -0.45, 0.5));
        spawnOrbitParticle(caseCenter, isPremiumReward(session) ? Particle.DRAGON_BREATH : Particle.END_ROD, scaled(24));
        caseCenter.getWorld().playSound(caseCenter, Sound.ENTITY_PLAYER_LEVELUP, volume(0.9F), 1.0F);
        removeIfValid(winner.stand);
        clearSlots(slots);

        plugin.getOpeningResults().complete(player, runtime, session, session.getFinalReward());
    }

    private ArmorStand createWheelStand(OpeningSession session, CaseItem prize, Location center) {
        ArmorStand stand = (ArmorStand) center.getWorld().spawnEntity(oriented(center.clone()), EntityType.ARMOR_STAND);
        stand.setInvisible(true);
        stand.setMarker(true);
        stand.setGravity(false);
        stand.setSmall(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.setCustomNameVisible(true);
        stand.customName(plugin.getTextFormatter().asComponent(prize.getName()));
        stand.getEquipment().setHelmet(prize.getIcon());
        stand.setMetadata(WHEEL_ENTITY_METADATA, new FixedMetadataValue(plugin, runtime.getId()));
        session.trackEntity(stand);
        return stand;
    }

    private void clearSlots(List<WheelSlot> slots) {
        for (WheelSlot slot : slots) {
            removeIfValid(slot.stand);
        }
    }

    private void openChest() {
        modifyLidded(true);
    }

    private void closeChest() {
        modifyLidded(false);
    }

    private void modifyLidded(boolean open) {
        Block block = runtime.getLocation().getBlock();
        BlockState state = block.getState();
        if (!(state instanceof Lidded lidded)) {
            return;
        }

        if (open) {
            lidded.open();
            block.getWorld().playSound(block.getLocation(), block.getType() == Material.CHEST ? Sound.BLOCK_CHEST_OPEN : Sound.BLOCK_ENDER_CHEST_OPEN, volume(1.0F), 1.0F);
        } else {
            lidded.close();
            block.getWorld().playSound(block.getLocation(), block.getType() == Material.CHEST ? Sound.BLOCK_CHEST_CLOSE : Sound.BLOCK_ENDER_CHEST_CLOSE, volume(1.0F), 1.0F);
        }
        state.update();
    }

    private void startPulse(OpeningSession session) {
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (runtime.getLocation().getWorld() == null || tick++ >= 12) {
                    cancel();
                    return;
                }

                Location center = runtime.getLocation().clone().add(0.5, 0.28, 0.5);
                runtime.getLocation().getWorld().spawnParticle(
                        isPremiumReward(session) ? Particle.GLOW : Particle.WITCH,
                        center,
                        scaled(10),
                        0.2,
                        0.18,
                        0.2,
                        0.01
                );
                spawnOrbitParticle(center.clone().add(0.0, 0.25, 0.0), trailParticle(session), 4);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private Location orbitPosition(Location center, double degrees, double radius) {
        double radians = Math.toRadians(degrees);
        String axis = wheelAxis();
        Location location = center.clone();

        if ("x".equals(axis)) {
            location.add(0.0D, Math.sin(radians) * radius, Math.cos(radians) * radius);
        } else {
            location.add(Math.cos(radians) * radius, Math.sin(radians) * radius, 0.0D);
        }
        return oriented(location);
    }

    private Location oriented(Location location) {
        location.setYaw(caseYaw());
        location.setPitch(0.0F);
        return location;
    }

    private float caseYaw() {
        Block block = runtime.getLocation().getBlock();
        if (block.getBlockData() instanceof Directional directional) {
            return yawFor(directional.getFacing());
        }
        return runtime.getLocation().getYaw();
    }

    private float yawFor(BlockFace facing) {
        Location probe = runtime.getLocation().clone();
        probe.setDirection(facing.getDirection());
        return probe.getYaw();
    }

    private double nextSpeed(double currentSpeed, double angleOffset) {
        double min = minSpeed();
        if (angleOffset < minTotalRotation()) {
            return Math.max(min, currentSpeed - (speedFade() * 0.35D));
        }
        return Math.max(min, currentSpeed - speedFade());
    }

    private double distanceToSettle(double degrees) {
        double diff = Math.abs(normalizeDegrees(degrees) - settleDegrees());
        return Math.min(diff, 360.0D - diff);
    }

    private double normalizeDegrees(double degrees) {
        double normalized = degrees % 360.0D;
        return normalized < 0.0D ? normalized + 360.0D : normalized;
    }

    private void spawnWinBurst(Location location, OpeningSession session) {
        spawnOrbitParticle(location.clone().add(0.0, 0.35, 0.0), winParticle(session), scaled(20));
        location.getWorld().playSound(location, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, volume(0.7F), isPremiumReward(session) ? 0.7F : 1.15F);
    }

    private Location visualItemLocation(Location standLocation) {
        return standLocation.clone().add(0.0D, visualHeightOffset(), 0.0D);
    }

    private void hideCaseBlock() {
        if (runtime.getLocation().getWorld() != null) {
            runtime.getLocation().getBlock().setType(Material.AIR);
        }
    }

    private double visualHeightOffset() {
        return 1.15D;
    }

    private void spawnOrbitParticle(Location location, Particle particle, int count) {
        if (location.getWorld() == null) {
            return;
        }

        if (particle.getDataType() == Particle.DustOptions.class) {
            location.getWorld().spawnParticle(
                    particle,
                    location,
                    count,
                    0.08D,
                    0.08D,
                    0.08D,
                    0.0D,
                    dustOptions()
            );
            return;
        }

        location.getWorld().spawnParticle(particle, location, count, 0.08D, 0.08D, 0.08D, 0.01D);
    }

    private Particle trailParticle(OpeningSession session) {
        return configuredParticle("settings.animations.wheel.particle", isPremiumReward(session) ? Particle.DUST : Particle.FLAME);
    }

    private Particle winParticle(OpeningSession session) {
        return configuredParticle("settings.animations.wheel.win-particle", isPremiumReward(session) ? Particle.DRAGON_BREATH : Particle.SMOKE);
    }

    private Particle vanishParticle(OpeningSession session) {
        return configuredParticle("settings.animations.wheel.vanish-particle", isPremiumReward(session) ? Particle.GLOW : Particle.SMOKE);
    }

    private Particle configuredParticle(String path, Particle fallback) {
        String raw = plugin.getConfig().getString(path, fallback.name());
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }

        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if ("REDSTONE".equals(normalized)) {
            normalized = "DUST";
        }

        try {
            return Particle.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private Particle.DustOptions dustOptions() {
        return new Particle.DustOptions(
                Color.fromRGB(
                        clampColor(plugin.getConfig().getInt("settings.animations.wheel.color.red", 101)),
                        clampColor(plugin.getConfig().getInt("settings.animations.wheel.color.green", 20)),
                        clampColor(plugin.getConfig().getInt("settings.animations.wheel.color.blue", 5))
                ),
                1.0F
        );
    }

    private int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
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

    private void removeIfValid(ArmorStand stand) {
        if (stand != null && stand.isValid()) {
            stand.remove();
        }
    }

    private CaseItem fallbackPrize(OpeningSession session) {
        CaseItem prize = plugin.getCaseService().getRandomReward(session.getSelectedCase());
        return prize == null ? session.getFinalReward() : prize;
    }

    private boolean isPremiumReward(OpeningSession session) {
        return session.getFinalReward() != null && (session.getFinalReward().isRare() || session.isGuaranteedReward());
    }

    private int scaled(int base) {
        double scale = Math.max(0.1D, plugin.getConfig().getDouble("settings.animations.intensity.particles", 1.0D));
        return Math.max(1, (int) Math.round(base * scale));
    }

    private int spawnIntervalTicks() {
        return Math.max(1, plugin.getConfig().getInt("settings.animations.wheel.spawn-interval-ticks", 4));
    }

    private double radius() {
        return Math.max(0.8D, plugin.getConfig().getDouble("settings.animations.wheel.radius", 2.0D));
    }

    private double wheelYOffset() {
        return plugin.getConfig().getDouble("settings.animations.wheel.y-offset", 1.1D);
    }

    private double wheelCenterYOffset() {
        return Math.max(0.2D, wheelYOffset() - 0.75D);
    }

    private double maxSpeed() {
        return Math.max(2.0D, plugin.getConfig().getDouble("settings.animations.wheel.max-speed", 16.0D));
    }

    private double minSpeed() {
        return Math.max(0.8D, plugin.getConfig().getDouble("settings.animations.wheel.min-speed", 2.0D));
    }

    private double speedFade() {
        return Math.max(0.01D, plugin.getConfig().getDouble("settings.animations.wheel.speed-fade", 0.09D));
    }

    private double minTotalRotation() {
        return Math.max(180.0D, plugin.getConfig().getDouble("settings.animations.wheel.min-total-rotation", 540.0D));
    }

    private double settleDegrees() {
        return plugin.getConfig().getDouble("settings.animations.wheel.settle-degrees", 270.0D);
    }

    private String wheelAxis() {
        return plugin.getConfig().getString("settings.animations.wheel.axis", "z").trim().toLowerCase(Locale.ROOT);
    }

    private float volume(float base) {
        double scale = Math.max(0.0D, plugin.getConfig().getDouble("settings.animations.intensity.sound", 1.0D));
        return (float) (base * scale);
    }

    private static final class WheelSlot {
        private final ArmorStand stand;
        private final CaseItem prize;
        private final boolean winner;
        private final double baseAngle;
        private double currentAngle;

        private WheelSlot(ArmorStand stand, CaseItem prize, double baseAngle, boolean winner) {
            this.stand = stand;
            this.prize = prize;
            this.baseAngle = baseAngle;
            this.currentAngle = baseAngle;
            this.winner = winner;
        }
    }
}
