package net.recases.animations.opening;

import net.recases.app.PluginContext;
import net.recases.management.CaseItem;
import net.recases.management.OpeningSession;
import net.recases.runtime.CaseRuntime;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class SphereOpeningAnimation implements OpeningAnimation {

    public static final String SPHERE_ENTITY_METADATA = "case_sphere_item";

    private static final double VISUAL_HEIGHT_OFFSET = 1.15D;

    private final PluginContext plugin;
    private final Player player;
    private final CaseRuntime runtime;
    private final AnimationPerformance performance;
    private final int motionInterval;
    private final int spawnIntervalTicks;
    private final int itemCount;
    private final double radius;
    private final double minRadius;
    private final double sphereCenterYOffset;
    private final double speedX;
    private final double speedY;
    private final double totalRotation;
    private final int vectorIntervalTicks;
    private final double shrinkStep;
    private final double lineStep;
    private final double neighborDistanceSquared;
    private final Particle vectorParticle;
    private final Particle itemParticle;
    private final Particle winParticle;
    private final Particle.DustOptions dustOptions;
    private final float caseYaw;

    public SphereOpeningAnimation(PluginContext plugin, Player player, CaseRuntime runtime) {
        this.plugin = plugin;
        this.player = player;
        this.runtime = runtime;
        this.performance = AnimationPerformance.create(plugin);
        this.motionInterval = performance.motionInterval();
        this.spawnIntervalTicks = Math.max(1, plugin.getConfig().getInt("settings.animations.sphere.spawn-interval-ticks", 2));
        this.itemCount = resolveItemCount();
        this.radius = Math.max(1.0D, plugin.getConfig().getDouble("settings.animations.sphere.radius", 2.5D));
        this.minRadius = Math.max(0.6D, plugin.getConfig().getDouble("settings.animations.sphere.min-radius", 1.8D));
        double yOffset = plugin.getConfig().getDouble("settings.animations.sphere.y-offset", 1.5D);
        this.sphereCenterYOffset = Math.max(0.3D, yOffset - 0.95D);
        this.speedX = Math.max(0.1D, plugin.getConfig().getDouble("settings.animations.sphere.rotation-speed-x", 1.25D));
        this.speedY = Math.max(0.1D, plugin.getConfig().getDouble("settings.animations.sphere.rotation-speed-y", 1.75D));
        this.totalRotation = Math.max(180.0D, plugin.getConfig().getDouble("settings.animations.sphere.total-rotation", 360.0D));
        this.vectorIntervalTicks = Math.max(4, plugin.getConfig().getInt("settings.animations.sphere.vector-interval-ticks", 14));
        this.shrinkStep = Math.max(0.0D, plugin.getConfig().getDouble("settings.animations.sphere.shrink-step", 0.05D));
        this.lineStep = Math.max(0.05D, plugin.getConfig().getDouble("settings.animations.sphere.line-step", 0.2D));
        double neighborDistance = Math.max(0.2D, plugin.getConfig().getDouble("settings.animations.sphere.neighbor-distance-factor", 1.0D)) * radius;
        this.neighborDistanceSquared = neighborDistance * neighborDistance;

        OpeningSession session = runtime.getSession();
        boolean premium = session != null
                && session.getFinalReward() != null
                && (session.getFinalReward().isRare() || session.isGuaranteedReward());
        this.vectorParticle = ParticleAnimationSupport.resolveParticle(
                plugin.getConfig().getString("settings.animations.sphere.vector-particle"),
                premium ? Particle.DUST : Particle.FLAME
        );
        this.itemParticle = ParticleAnimationSupport.resolveParticle(
                plugin.getConfig().getString("settings.animations.sphere.item-particle"),
                premium ? Particle.DUST : Particle.END_ROD
        );
        this.winParticle = ParticleAnimationSupport.resolveParticle(
                plugin.getConfig().getString("settings.animations.sphere.win-particle"),
                premium ? Particle.DRAGON_BREATH : Particle.GLOW
        );
        this.dustOptions = new Particle.DustOptions(
                Color.fromRGB(
                        clampColor(plugin.getConfig().getInt("settings.animations.sphere.color.red", 101)),
                        clampColor(plugin.getConfig().getInt("settings.animations.sphere.color.green", 20)),
                        clampColor(plugin.getConfig().getInt("settings.animations.sphere.color.blue", 5))
                ),
                1.0F
        );
        this.caseYaw = resolveCaseYaw();
    }

    @Override
    public boolean play() {
        OpeningSession session = runtime.getSession();
        if (session == null || !runtime.isAvailable() || session.getFinalReward() == null) {
            return false;
        }

        runtime.removeHologram();
        hideCaseBlock();
        player.playSound(runtime.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, volume(1.0F), 0.8F);
        startPulse();

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isActive(session)) {
                    plugin.getCaseService().abortOpening(runtime, true);
                    return;
                }
                startSphere(session);
            }
        }.runTaskLater(plugin, performance.cadence(10L));
        return true;
    }

    private void startSphere(OpeningSession session) {
        Location center = runtime.getLocation().clone().add(0.5D, sphereCenterYOffset, 0.5D);
        List<SphereSlot> slots = buildSlots(session, center);
        if (slots.isEmpty()) {
            plugin.getCaseService().abortOpening(runtime, true);
            return;
        }

        List<ConnectionLink> connections = buildConnections(slots);
        spawnSlots(session, center, slots, connections);
    }

    private List<SphereSlot> buildSlots(OpeningSession session, Location center) {
        int winnerIndex = ThreadLocalRandom.current().nextInt(itemCount);
        List<SphereSlot> slots = new ArrayList<>(itemCount);

        for (int index = 0; index < itemCount; index++) {
            boolean winner = index == winnerIndex;
            CaseItem prize = winner ? session.getFinalReward() : fallbackPrize(session);
            Vector unitVector = fibonacciSpherePoint(index, itemCount);
            ArmorStand stand = createStand(center, prize);
            slots.add(new SphereSlot(stand, unitVector, winner));
        }
        return slots;
    }

    private List<ConnectionLink> buildConnections(List<SphereSlot> slots) {
        List<ConnectionLink> connections = new ArrayList<>();
        for (int first = 0; first < slots.size(); first++) {
            Vector a = slots.get(first).unitVector.clone().multiply(radius);
            for (int second = first + 1; second < slots.size(); second++) {
                Vector b = slots.get(second).unitVector.clone().multiply(radius);
                if (a.distanceSquared(b) <= neighborDistanceSquared) {
                    connections.add(new ConnectionLink(slots.get(first), slots.get(second)));
                }
            }
        }
        return connections;
    }

    private void spawnSlots(OpeningSession session, Location center, List<SphereSlot> slots, List<ConnectionLink> connections) {
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

                if (index >= slots.size()) {
                    cancel();
                    rotateSphere(session, center, slots, connections);
                    return;
                }

                animateSlotSpawn(session, center, slots.get(index++));
            }
        }.runTaskTimer(plugin, 0L, performance.cadence(spawnIntervalTicks));
    }

    private void animateSlotSpawn(OpeningSession session, Location center, SphereSlot slot) {
        Location spawn = oriented(center.clone());
        slot.stand.teleport(spawn);
        slot.stand.getWorld().playSound(spawn, Sound.ITEM_ARMOR_EQUIP_GENERIC, volume(0.7F), 1.15F);

        new BukkitRunnable() {
            private int elapsed;

            @Override
            public void run() {
                if (!isActive(session) || slot.stand.isDead()) {
                    cancel();
                    return;
                }

                elapsed += motionInterval;
                double progress = Math.min(1.0D, elapsed / 7.0D);
                Location target = center.clone().add(slot.unitVector.clone().multiply(radius * progress));
                slot.currentLocation = target;
                slot.stand.teleport(oriented(target));
                slot.stand.setHeadPose(lookPose(target, center));
                spawnParticle(visualItemLocation(target), itemParticle, 2);

                if (progress >= 1.0D) {
                    slot.stand.getWorld().playSound(visualItemLocation(target), Sound.BLOCK_AMETHYST_BLOCK_CHIME, volume(0.45F), 0.95F);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, motionInterval);
    }

    private void rotateSphere(OpeningSession session, Location center, List<SphereSlot> slots, List<ConnectionLink> connections) {
        new BukkitRunnable() {
            private double angleX;
            private double angleY;
            private double currentRadius = radius;
            private int vectorElapsed;
            private int soundBucket = -1;

            @Override
            public void run() {
                if (!isActive(session)) {
                    clearSlots(slots);
                    cancel();
                    plugin.getCaseService().abortOpening(runtime, true);
                    return;
                }

                angleX += speedX * motionInterval;
                angleY += speedY * motionInterval;
                vectorElapsed += motionInterval;

                for (SphereSlot slot : slots) {
                    Vector rotated = slot.unitVector.clone()
                            .multiply(currentRadius)
                            .rotateAroundX(Math.toRadians(angleX))
                            .rotateAroundY(Math.toRadians(angleY));
                    Location target = center.clone().add(rotated);
                    slot.currentLocation = target;
                    slot.stand.teleport(oriented(target));
                    slot.stand.setHeadPose(lookPose(target, center));
                    spawnParticle(visualItemLocation(target), itemParticle, 1);
                }

                if (vectorElapsed >= vectorIntervalTicks) {
                    vectorElapsed = 0;
                    drawConnections(connections);
                    currentRadius = Math.max(minRadius, currentRadius - (shrinkStep * motionInterval));
                }

                int currentBucket = (int) (angleY / 36.0D);
                if (currentBucket != soundBucket) {
                    soundBucket = currentBucket;
                    center.getWorld().playSound(center, Sound.UI_BUTTON_CLICK, volume(0.4F), 1.2F);
                }

                if (angleX < totalRotation && angleY < totalRotation) {
                    return;
                }

                cancel();
                finishSphere(session, slots);
            }
        }.runTaskTimer(plugin, 0L, motionInterval);
    }

    private void finishSphere(OpeningSession session, List<SphereSlot> slots) {
        SphereSlot winner = slots.stream().filter(slot -> slot.winner).findFirst().orElse(slots.get(0));
        if (winner.currentLocation == null) {
            winner.currentLocation = winner.stand.getLocation();
        }

        new BukkitRunnable() {
            private final Location destination = runtime.getLocation().clone().add(0.5D, -0.45D + VISUAL_HEIGHT_OFFSET, 0.5D);
            private int elapsed;

            @Override
            public void run() {
                if (!isActive(session) || winner.stand.isDead()) {
                    clearSlots(slots);
                    cancel();
                    plugin.getCaseService().abortOpening(runtime, true);
                    return;
                }

                elapsed += motionInterval;
                Vector direction = destination.toVector().subtract(winner.stand.getLocation().toVector());
                if (direction.lengthSquared() <= 0.03D || elapsed >= 24) {
                    winner.stand.teleport(oriented(destination.clone()));
                    winner.stand.setHeadPose(lookPose(destination, runtime.getLocation().clone().add(0.5D, 0.7D, 0.5D)));
                    cancel();
                    dissolveLosers(session, winner, slots);
                    return;
                }

                direction.normalize().multiply(0.18D * motionInterval);
                Location next = winner.stand.getLocation().clone().add(direction);
                winner.stand.teleport(oriented(next));
                spawnParticle(visualItemLocation(next), winParticle, 3);
                if ((elapsed / motionInterval) % 2 == 0) {
                    next.getWorld().playSound(visualItemLocation(next), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, volume(0.45F), 1.25F);
                }
            }
        }.runTaskTimer(plugin, 0L, motionInterval);
    }

    private void dissolveLosers(OpeningSession session, SphereSlot winner, List<SphereSlot> slots) {
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
                    complete(session, winner, slots);
                    return;
                }

                SphereSlot slot = slots.get(index++);
                Location location = visualItemLocation(slot.stand.getLocation());
                spawnParticle(location, vectorParticle, 8);
                location.getWorld().playSound(location, Sound.BLOCK_FIRE_EXTINGUISH, volume(0.35F), 1.1F);
                removeIfValid(slot.stand);
            }
        }.runTaskTimer(plugin, 0L, performance.cadence(2L));
    }

    private void complete(OpeningSession session, SphereSlot winner, List<SphereSlot> slots) {
        Location center = visualItemLocation(runtime.getLocation().clone().add(0.5D, -0.4D, 0.5D));
        spawnParticle(center, winParticle, scaled(24));
        center.getWorld().playSound(center, Sound.ENTITY_PLAYER_LEVELUP, volume(0.95F), 1.0F);

        removeIfValid(winner.stand);
        clearSlots(slots);
        plugin.getOpeningResults().complete(player, runtime, session, session.getFinalReward());
    }

    private void drawConnections(List<ConnectionLink> connections) {
        if (connections.isEmpty()) {
            return;
        }

        int budget = performance.connectionBudget(connections.size());
        for (int index = 0; index < budget; index++) {
            ConnectionLink connection = connections.get(index);
            ParticleAnimationSupport.drawLine(
                    currentLocation(connection.first),
                    currentLocation(connection.second),
                    performance.lineStep(lineStep),
                    (location, count) -> spawnParticle(location, vectorParticle, count)
            );
        }
    }

    private Location currentLocation(SphereSlot slot) {
        Location base = slot.currentLocation == null ? slot.stand.getLocation() : slot.currentLocation;
        return visualItemLocation(base);
    }

    private ArmorStand createStand(Location center, CaseItem prize) {
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
        stand.setMetadata(SPHERE_ENTITY_METADATA, new FixedMetadataValue(plugin, runtime.getId()));
        runtime.getSession().trackEntity(stand);
        return stand;
    }

    private void clearSlots(List<SphereSlot> slots) {
        for (SphereSlot slot : slots) {
            removeIfValid(slot.stand);
        }
    }

    private void removeIfValid(ArmorStand stand) {
        if (stand != null && stand.isValid()) {
            stand.remove();
        }
    }

    private Vector fibonacciSpherePoint(int index, int count) {
        double samples = Math.max(1.0D, count);
        double y = 1.0D - ((index + 0.5D) * (2.0D / samples));
        double radiusFactor = Math.sqrt(Math.max(0.0D, 1.0D - (y * y)));
        double theta = Math.PI * (3.0D - Math.sqrt(5.0D)) * index;
        return new Vector(Math.cos(theta) * radiusFactor, y, Math.sin(theta) * radiusFactor);
    }

    private EulerAngle lookPose(Location from, Location to) {
        Vector direction = to.toVector().subtract(from.toVector()).normalize();
        double pitch = Math.asin(-direction.getY());
        double yaw = Math.atan2(direction.getX(), direction.getZ());
        return new EulerAngle(pitch, yaw, 0.0D);
    }

    private void startPulse() {
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (runtime.getLocation().getWorld() == null || tick++ >= 10) {
                    cancel();
                    return;
                }

                Location center = runtime.getLocation().clone().add(0.5D, 0.32D, 0.5D);
                spawnParticle(center, itemParticle, scaled(10));
                spawnParticle(center.clone().add(0.0D, 0.25D, 0.0D), vectorParticle, scaled(6));
            }
        }.runTaskTimer(plugin, 0L, performance.cadence(2L));
    }

    private void hideCaseBlock() {
        if (runtime.getLocation().getWorld() != null) {
            runtime.getLocation().getBlock().setType(org.bukkit.Material.AIR);
        }
    }

    private void spawnParticle(Location location, Particle particle, int count) {
        if (location.getWorld() == null) {
            return;
        }

        if (particle.getDataType() == Particle.DustOptions.class) {
            location.getWorld().spawnParticle(particle, location, Math.max(1, count), 0.06D, 0.06D, 0.06D, 0.0D, dustOptions);
            return;
        }

        location.getWorld().spawnParticle(particle, location, Math.max(1, count), 0.04D, 0.04D, 0.04D, 0.0D);
    }

    private Location visualItemLocation(Location standLocation) {
        return standLocation.clone().add(0.0D, VISUAL_HEIGHT_OFFSET, 0.0D);
    }

    private Location oriented(Location location) {
        location.setYaw(caseYaw);
        location.setPitch(0.0F);
        return location;
    }

    private float resolveCaseYaw() {
        Block block = runtime.getLocation().getBlock();
        if (block.getBlockData() instanceof Directional directional) {
            Location probe = runtime.getLocation().clone();
            probe.setDirection(directional.getFacing().getDirection());
            return probe.getYaw();
        }
        return runtime.getLocation().getYaw();
    }

    private CaseItem fallbackPrize(OpeningSession session) {
        CaseItem prize = plugin.getCaseService().getRandomReward(session.getSelectedCase());
        return prize == null ? session.getFinalReward() : prize;
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

    private int scaled(int base) {
        return performance.particles(base);
    }

    private float volume(float base) {
        return performance.volume(base);
    }

    private int resolveItemCount() {
        return performance.limitSphereItems(Math.max(8, plugin.getConfig().getInt("settings.animations.sphere.item-count", 16)));
    }

    private int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static final class SphereSlot {
        private final ArmorStand stand;
        private final Vector unitVector;
        private final boolean winner;
        private Location currentLocation;

        private SphereSlot(ArmorStand stand, Vector unitVector, boolean winner) {
            this.stand = stand;
            this.unitVector = unitVector;
            this.winner = winner;
        }
    }

    private static final class ConnectionLink {
        private final SphereSlot first;
        private final SphereSlot second;

        private ConnectionLink(SphereSlot first, SphereSlot second) {
            this.first = first;
            this.second = second;
        }
    }
}
