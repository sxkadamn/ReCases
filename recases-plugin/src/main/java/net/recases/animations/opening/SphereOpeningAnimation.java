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
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public class SphereOpeningAnimation implements OpeningAnimation {

    public static final String SPHERE_ENTITY_METADATA = "case_sphere_item";

    private final PluginContext plugin;
    private final Player player;
    private final CaseRuntime runtime;

    public SphereOpeningAnimation(PluginContext plugin, Player player, CaseRuntime runtime) {
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
        player.playSound(runtime.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, volume(1.0F), 0.8F);
        startPulse(session);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isActive(session)) {
                    plugin.getCaseService().abortOpening(runtime, true);
                    return;
                }
                startSphere(session);
            }
        }.runTaskLater(plugin, 10L);
        return true;
    }

    private void startSphere(OpeningSession session) {
        Location center = runtime.getLocation().clone().add(0.5D, sphereCenterYOffset(), 0.5D);
        List<SphereSlot> slots = buildSlots(session, center);
        if (slots.isEmpty()) {
            plugin.getCaseService().abortOpening(runtime, true);
            return;
        }

        spawnSlots(session, center, slots);
    }

    private List<SphereSlot> buildSlots(OpeningSession session, Location center) {
        int count = Math.max(8, plugin.getConfig().getInt("settings.animations.sphere.item-count", 16));
        int winnerIndex = ThreadLocalRandom.current().nextInt(count);
        List<SphereSlot> slots = new ArrayList<>(count);

        for (int index = 0; index < count; index++) {
            boolean winner = index == winnerIndex;
            CaseItem prize = winner ? session.getFinalReward() : fallbackPrize(session);
            Vector baseVector = fibonacciSpherePoint(index, count).multiply(radius());
            ArmorStand stand = createStand(center, prize);
            slots.add(new SphereSlot(stand, baseVector, winner));
        }
        return slots;
    }

    private void spawnSlots(OpeningSession session, Location center, List<SphereSlot> slots) {
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
                    rotateSphere(session, center, slots);
                    return;
                }

                SphereSlot slot = slots.get(index++);
                Location spawn = center.clone();
                slot.stand.teleport(oriented(spawn));
                slot.stand.getWorld().playSound(spawn, Sound.ITEM_ARMOR_EQUIP_GENERIC, volume(0.7F), 1.15F);

                new BukkitRunnable() {
                    private int tick;

                    @Override
                    public void run() {
                        if (!isActive(session) || slot.stand.isDead()) {
                            cancel();
                            return;
                        }

                        tick++;
                        double progress = Math.min(1.0D, tick / 7.0D);
                        Location target = center.clone().add(slot.baseVector.clone().multiply(progress));
                        slot.currentLocation = target;
                        slot.stand.teleport(oriented(target));
                        slot.stand.setHeadPose(lookPose(target, center));
                        spawnParticle(visualItemLocation(target), itemParticle(session), 2);

                        if (progress >= 1.0D) {
                            slot.stand.getWorld().playSound(visualItemLocation(target), Sound.BLOCK_AMETHYST_BLOCK_CHIME, volume(0.45F), 0.95F);
                            cancel();
                        }
                    }
                }.runTaskTimer(plugin, 0L, 1L);
            }
        }.runTaskTimer(plugin, 0L, spawnIntervalTicks());
    }

    private void rotateSphere(OpeningSession session, Location center, List<SphereSlot> slots) {
        new BukkitRunnable() {
            private double angleX;
            private double angleY;
            private double currentRadius = radius();
            private int vectorTimer;

            @Override
            public void run() {
                if (!isActive(session)) {
                    clearSlots(slots);
                    cancel();
                    plugin.getCaseService().abortOpening(runtime, true);
                    return;
                }

                angleX += speedX();
                angleY += speedY();
                vectorTimer++;

                for (SphereSlot slot : slots) {
                    Vector rotated = slot.baseVector.clone()
                            .normalize()
                            .multiply(currentRadius)
                            .rotateAroundX(Math.toRadians(angleX))
                            .rotateAroundY(Math.toRadians(angleY));
                    Location target = center.clone().add(rotated);
                    slot.currentLocation = target;
                    slot.stand.teleport(oriented(target));
                    slot.stand.setHeadPose(lookPose(target, center));
                    spawnParticle(visualItemLocation(target), itemParticle(session), 1);
                }

                if (vectorTimer >= vectorIntervalTicks()) {
                    vectorTimer = 0;
                    drawConnections(slots, session);
                    currentRadius = Math.max(minRadius(), currentRadius - shrinkStep());
                }

                if (((int) angleY) % 36 == 0) {
                    center.getWorld().playSound(center, Sound.UI_BUTTON_CLICK, volume(0.4F), 1.2F);
                }

                if (angleX < totalRotation() && angleY < totalRotation()) {
                    return;
                }

                cancel();
                finishSphere(session, slots);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void finishSphere(OpeningSession session, List<SphereSlot> slots) {
        SphereSlot winner = slots.stream().filter(slot -> slot.winner).findFirst().orElse(slots.get(0));
        if (winner.currentLocation == null) {
            winner.currentLocation = winner.stand.getLocation();
        }

        new BukkitRunnable() {
            private final Location destination = runtime.getLocation().clone().add(0.5D, -0.45D + visualHeightOffset(), 0.5D);
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
                if (direction.lengthSquared() <= 0.03D || tick >= 24) {
                    winner.stand.teleport(oriented(destination.clone()));
                    winner.stand.setHeadPose(lookPose(destination, runtime.getLocation().clone().add(0.5D, 0.7D, 0.5D)));
                    cancel();
                    dissolveLosers(session, winner, slots);
                    return;
                }

                direction.normalize().multiply(0.18D);
                Location next = winner.stand.getLocation().clone().add(direction);
                winner.stand.teleport(oriented(next));
                spawnParticle(visualItemLocation(next), winParticle(session), 3);
                if (tick % 2 == 0) {
                    next.getWorld().playSound(visualItemLocation(next), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, volume(0.45F), 1.25F);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
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
                spawnParticle(location, vectorParticle(session), 8);
                location.getWorld().playSound(location, Sound.BLOCK_FIRE_EXTINGUISH, volume(0.35F), 1.1F);
                removeIfValid(slot.stand);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void complete(OpeningSession session, SphereSlot winner, List<SphereSlot> slots) {
        Location center = visualItemLocation(runtime.getLocation().clone().add(0.5D, -0.4D, 0.5D));
        spawnParticle(center, winParticle(session), scaled(24));
        center.getWorld().playSound(center, Sound.ENTITY_PLAYER_LEVELUP, volume(0.95F), 1.0F);

        removeIfValid(winner.stand);
        clearSlots(slots);

        plugin.getOpeningResults().complete(player, runtime, session, session.getFinalReward());
    }

    private void drawConnections(List<SphereSlot> slots, OpeningSession session) {
        double threshold = Math.max(0.4D, radius() * neighborDistanceFactor());
        Particle particle = vectorParticle(session);

        for (int first = 0; first < slots.size(); first++) {
            Location a = currentLocation(slots.get(first));
            for (int second = first + 1; second < slots.size(); second++) {
                Location b = currentLocation(slots.get(second));
                if (a.distanceSquared(b) > threshold * threshold) {
                    continue;
                }
                drawLine(a, b, particle);
            }
        }
    }

    private void drawLine(Location start, Location end, Particle particle) {
        Vector direction = end.toVector().subtract(start.toVector());
        double distance = direction.length();
        if (distance <= 0.001D) {
            return;
        }

        direction.normalize().multiply(lineStep());
        Location cursor = start.clone();
        for (double moved = 0.0D; moved < distance; moved += lineStep()) {
            spawnParticle(cursor, particle, 1);
            cursor.add(direction);
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
        double radius = Math.sqrt(Math.max(0.0D, 1.0D - (y * y)));
        double theta = Math.PI * (3.0D - Math.sqrt(5.0D)) * index;
        double x = Math.cos(theta) * radius;
        double z = Math.sin(theta) * radius;
        return new Vector(x, y, z);
    }

    private EulerAngle lookPose(Location from, Location to) {
        Vector direction = to.toVector().subtract(from.toVector()).normalize();
        double pitch = Math.asin(-direction.getY());
        double yaw = Math.atan2(direction.getX(), direction.getZ());
        return new EulerAngle(pitch, yaw, 0.0D);
    }

    private void startPulse(OpeningSession session) {
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (runtime.getLocation().getWorld() == null || tick++ >= 10) {
                    cancel();
                    return;
                }

                Location center = runtime.getLocation().clone().add(0.5D, 0.32D, 0.5D);
                spawnParticle(center, itemParticle(session), scaled(10));
                spawnParticle(center.clone().add(0.0D, 0.25D, 0.0D), vectorParticle(session), scaled(6));
            }
        }.runTaskTimer(plugin, 0L, 2L);
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
            location.getWorld().spawnParticle(
                    particle,
                    location,
                    count,
                    0.06D,
                    0.06D,
                    0.06D,
                    0.0D,
                    dustOptions()
            );
            return;
        }

        location.getWorld().spawnParticle(particle, location, count, 0.04D, 0.04D, 0.04D, 0.0D);
    }

    private Location visualItemLocation(Location standLocation) {
        return standLocation.clone().add(0.0D, visualHeightOffset(), 0.0D);
    }

    private double visualHeightOffset() {
        return 1.15D;
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

    private Particle vectorParticle(OpeningSession session) {
        return configuredParticle("settings.animations.sphere.vector-particle", isPremiumReward(session) ? Particle.DUST : Particle.FLAME);
    }

    private Particle itemParticle(OpeningSession session) {
        return configuredParticle("settings.animations.sphere.item-particle", isPremiumReward(session) ? Particle.DUST : Particle.END_ROD);
    }

    private Particle winParticle(OpeningSession session) {
        return configuredParticle("settings.animations.sphere.win-particle", isPremiumReward(session) ? Particle.DRAGON_BREATH : Particle.GLOW);
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
                        clampColor(plugin.getConfig().getInt("settings.animations.sphere.color.red", 101)),
                        clampColor(plugin.getConfig().getInt("settings.animations.sphere.color.green", 20)),
                        clampColor(plugin.getConfig().getInt("settings.animations.sphere.color.blue", 5))
                ),
                1.0F
        );
    }

    private int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private CaseItem fallbackPrize(OpeningSession session) {
        CaseItem prize = plugin.getCaseService().getRandomReward(session.getSelectedCase());
        return prize == null ? session.getFinalReward() : prize;
    }

    private boolean isPremiumReward(OpeningSession session) {
        return session != null
                && session.getFinalReward() != null
                && (session.getFinalReward().isRare() || session.isGuaranteedReward());
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
        double scale = Math.max(0.1D, plugin.getConfig().getDouble("settings.animations.intensity.particles", 1.0D));
        return Math.max(1, (int) Math.round(base * scale));
    }

    private float volume(float base) {
        double scale = Math.max(0.0D, plugin.getConfig().getDouble("settings.animations.intensity.sound", 1.0D));
        return (float) (base * scale);
    }

    private int spawnIntervalTicks() {
        return Math.max(1, plugin.getConfig().getInt("settings.animations.sphere.spawn-interval-ticks", 2));
    }

    private double radius() {
        return Math.max(1.0D, plugin.getConfig().getDouble("settings.animations.sphere.radius", 2.5D));
    }

    private double minRadius() {
        return Math.max(0.6D, plugin.getConfig().getDouble("settings.animations.sphere.min-radius", 1.8D));
    }

    private double yOffset() {
        return plugin.getConfig().getDouble("settings.animations.sphere.y-offset", 1.5D);
    }

    private double sphereCenterYOffset() {
        return Math.max(0.3D, yOffset() - 0.95D);
    }

    private double speedX() {
        return Math.max(0.1D, plugin.getConfig().getDouble("settings.animations.sphere.rotation-speed-x", 1.25D));
    }

    private double speedY() {
        return Math.max(0.1D, plugin.getConfig().getDouble("settings.animations.sphere.rotation-speed-y", 1.75D));
    }

    private double totalRotation() {
        return Math.max(180.0D, plugin.getConfig().getDouble("settings.animations.sphere.total-rotation", 360.0D));
    }

    private int vectorIntervalTicks() {
        return Math.max(4, plugin.getConfig().getInt("settings.animations.sphere.vector-interval-ticks", 14));
    }

    private double shrinkStep() {
        return Math.max(0.0D, plugin.getConfig().getDouble("settings.animations.sphere.shrink-step", 0.05D));
    }

    private double neighborDistanceFactor() {
        return Math.max(0.2D, plugin.getConfig().getDouble("settings.animations.sphere.neighbor-distance-factor", 1.0D));
    }

    private double lineStep() {
        return Math.max(0.05D, plugin.getConfig().getDouble("settings.animations.sphere.line-step", 0.2D));
    }

    private static final class SphereSlot {
        private final ArmorStand stand;
        private final Vector baseVector;
        private final boolean winner;
        private Location currentLocation;

        private SphereSlot(ArmorStand stand, Vector baseVector, boolean winner) {
            this.stand = stand;
            this.baseVector = baseVector;
            this.winner = winner;
        }
    }
}
