package net.recases.animations.opening;

import net.recases.app.PluginContext;
import net.recases.management.CaseItem;
import net.recases.management.OpeningSession;
import net.recases.runtime.CaseRuntime;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Lidded;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
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
        player.teleport(runtime.getLocation().clone().add(0.5, 0.0, 0.5));
        player.playSound(runtime.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, volume(1.0F), 0.85F);
        startPulse(Particle.WITCH, Particle.END_ROD, 14L);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isActive(session)) {
                    plugin.getCaseService().abortOpening(runtime, true);
                    return;
                }
                startWheel(session);
            }
        }.runTaskLater(plugin, 10L);
        return true;
    }

    private void startWheel(OpeningSession session) {
        if (!isActive(session) || runtime.getLocation().getWorld() == null) {
            plugin.getCaseService().abortOpening(runtime, true);
            return;
        }

        openChest();
        Location wheelCenter = runtime.getLocation().clone().add(0.5, wheelYOffset(), 0.5);
        List<WheelSlot> slots = spawnSlots(session, wheelCenter);
        if (slots.isEmpty()) {
            plugin.getCaseService().abortOpening(runtime, true);
            return;
        }

        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (!isActive(session) || runtime.getLocation().getWorld() == null || tick++ >= 18) {
                    cancel();
                    return;
                }
                runtime.getLocation().getWorld().spawnParticle(Particle.WITCH, runtime.getLocation().clone().add(0.5, 0.85, 0.5), scaled(10), 0.15, 0.2, 0.15, 0.01);
            }
        }.runTaskTimer(plugin, 2L, 2L);

        long wheelStartDelay = Math.max(20L, (long) slots.size() * spawnIntervalTicks() + 10L);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isActive(session)) {
                    plugin.getCaseService().abortOpening(runtime, true);
                    return;
                }
                spinWheel(session, wheelCenter, slots);
            }
        }.runTaskLater(plugin, wheelStartDelay);
    }

    private List<WheelSlot> spawnSlots(OpeningSession session, Location wheelCenter) {
        List<WheelSlot> slots = new ArrayList<>();
        int prizeCount = Math.max(6, plugin.getConfig().getInt("settings.animations.wheel.prize-count", 8));
        int angleStep = 360 / prizeCount;
        int winnerIndex = ThreadLocalRandom.current().nextInt(prizeCount);
        int degrees = normalizeDegrees(topDegrees() + angleStep);

        for (int i = 0; i < prizeCount; i++) {
            boolean winner = i == winnerIndex;
            CaseItem prize = i == winnerIndex
                    ? session.getFinalReward()
                    : fallbackPrize(session);
            ArmorStand stand = createWheelStand(session, prize, wheelCenter);
            WheelSlot slot = new WheelSlot(stand, prize, degrees, winner);
            slots.add(slot);

            int targetDegrees = degrees;
            long delay = (long) i * spawnIntervalTicks();
            new BukkitRunnable() {
                private int step;

                @Override
                public void run() {
                    if (!isActive(session) || runtime.getLocation().getWorld() == null || stand.isDead()) {
                        cancel();
                        return;
                    }

                    step++;
                    double progress = Math.min(1.0D, step / 8.0D);
                    stand.teleport(position(wheelCenter, targetDegrees, radius() * progress));
                    if (progress >= 1.0D) {
                        runtime.getLocation().getWorld().spawnParticle(Particle.END_ROD, stand.getLocation().clone().add(0.0, 0.9, 0.0), scaled(8), 0.08, 0.08, 0.08, 0.01);
                        runtime.getLocation().getWorld().playSound(stand.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, volume(0.75F), 0.95F + (targetDegrees / 360.0F));
                        cancel();
                    }
                }
            }.runTaskTimer(plugin, delay, 1L);

            degrees = normalizeDegrees(degrees + angleStep);
        }

        return slots;
    }

    private ArmorStand createWheelStand(OpeningSession session, CaseItem prize, Location wheelCenter) {
        ArmorStand stand = (ArmorStand) wheelCenter.getWorld().spawnEntity(wheelCenter, EntityType.ARMOR_STAND);
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

    private void spinWheel(OpeningSession session, Location wheelCenter, List<WheelSlot> slots) {
        ArmorStand pointer = createPointer(session);
        WheelSlot winnerSlot = slots.stream()
                .filter(slot -> slot.winner)
                .findFirst()
                .orElse(slots.get(0));

        new BukkitRunnable() {
            private double speed = maxSpeed();

            @Override
            public void run() {
                if (!isActive(session) || runtime.getLocation().getWorld() == null) {
                    removeIfValid(pointer);
                    cancel();
                    plugin.getCaseService().abortOpening(runtime, true);
                    return;
                }

                int currentStep = Math.max(minSpeed(), (int) Math.round(speed));
                for (WheelSlot slot : slots) {
                    int previous = slot.degrees;
                    slot.degrees = normalizeDegrees(slot.degrees + currentStep);
                    slot.stand.teleport(position(wheelCenter, slot.degrees, radius()));
                    if (crossedTop(previous, slot.degrees)) {
                        runtime.getLocation().getWorld().playSound(runtime.getLocation(), Sound.UI_BUTTON_CLICK, volume(0.9F), 1.0F);
                    }
                }

                if (speed > minSpeed()) {
                    speed = Math.max(minSpeed(), speed - speedFade());
                    return;
                }

                if (distanceToTop(winnerSlot.degrees) > minSpeed()) {
                    return;
                }

                cancel();
                removeIfValid(pointer);
                celebrateWinner(session, winnerSlot, wheelCenter, slots);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void celebrateWinner(OpeningSession session, WheelSlot winnerSlot, Location wheelCenter, List<WheelSlot> slots) {
        if (!isActive(session) || runtime.getLocation().getWorld() == null) {
            plugin.getCaseService().abortOpening(runtime, true);
            return;
        }

        runtime.getLocation().getWorld().spawnParticle(isPremiumReward(session) ? Particle.GLOW : Particle.FIREWORK, winnerSlot.stand.getLocation().clone().add(0.0, 0.95, 0.0), scaled(26), 0.2, 0.3, 0.2, 0.02);
        runtime.getLocation().getWorld().playSound(runtime.getLocation(), isPremiumReward(session) ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.BLOCK_NOTE_BLOCK_CHIME, volume(1.0F), 1.05F);
        closeChest();

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isActive(session)) {
                    plugin.getCaseService().abortOpening(runtime, true);
                    return;
                }

                session.markRewardGranted();
                plugin.getRewardService().execute(player, session.getFinalReward().getActions());
                plugin.getStats().recordOpening(player, session.getSelectedCase(), session.getFinalReward(), session.isGuaranteedReward());
                plugin.getLeaderboardHolograms().refreshAll();
                plugin.getMessages().send(player, "messages.case-reward-received", "#80ed99Вы получили награду: #ffffff%reward%", "%reward%", session.getFinalReward().getName());
                clearSlots(slots);
                plugin.getCaseService().completeOpening(runtime);
            }
        }.runTaskLater(plugin, 30L);
    }

    private void clearSlots(List<WheelSlot> slots) {
        for (WheelSlot slot : slots) {
            removeIfValid(slot.stand);
        }
    }

    private ArmorStand createPointer(OpeningSession session) {
        Location location = runtime.getLocation().clone().add(0.86, 0.35, 0.5);
        ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setInvisible(true);
        stand.setMarker(true);
        stand.setGravity(false);
        stand.setSmall(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.setHeadPose(org.bukkit.util.EulerAngle.ZERO);
        stand.getEquipment().setHelmet(new org.bukkit.inventory.ItemStack(Material.DIAMOND_SWORD));
        stand.setMetadata(WHEEL_ENTITY_METADATA, new FixedMetadataValue(plugin, runtime.getId()));
        session.trackEntity(stand);
        return stand;
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

    private void startPulse(Particle primary, Particle secondary, long durationTicks) {
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (runtime.getLocation().getWorld() == null || tick++ >= durationTicks) {
                    cancel();
                    return;
                }
                runtime.getLocation().getWorld().spawnParticle(primary, runtime.getLocation().clone().add(0.5, 0.8, 0.5), scaled(10), 0.18, 0.18, 0.18, 0.01);
                runtime.getLocation().getWorld().spawnParticle(secondary, runtime.getLocation().clone().add(0.5, 1.0, 0.5), scaled(6), 0.45, 0.12, 0.45, 0.01);
            }
        }.runTaskTimer(plugin, 0L, 2L);
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

    private Location position(Location wheelCenter, int degrees, double radius) {
        double radians = Math.toRadians(degrees);
        return wheelCenter.clone().add(Math.cos(radians) * radius, Math.sin(radians) * radius, 0.0D);
    }

    private CaseItem fallbackPrize(OpeningSession session) {
        CaseItem prize = plugin.getCaseService().getRandomReward(session.getSelectedCase());
        return prize == null ? session.getFinalReward() : prize;
    }

    private boolean crossedTop(int from, int to) {
        return (from < topDegrees() && to >= topDegrees()) || (from > topDegrees() && to <= topDegrees());
    }

    private int distanceToTop(int degrees) {
        int diff = Math.abs(normalizeDegrees(degrees) - topDegrees());
        return Math.min(diff, 360 - diff);
    }

    private int normalizeDegrees(double degrees) {
        int normalized = (int) (degrees % 360.0D);
        if (normalized < 0) {
            normalized += 360;
        }
        return normalized == 360 ? 0 : normalized;
    }

    private void removeIfValid(ArmorStand stand) {
        if (stand != null && stand.isValid()) {
            stand.remove();
        }
    }

    private boolean isPremiumReward(OpeningSession session) {
        return session.getFinalReward() != null && (session.getFinalReward().isRare() || session.isGuaranteedReward());
    }

    private int scaled(int base) {
        double scale = Math.max(0.1D, plugin.getConfig().getDouble("settings.animations.intensity.particles", 1.0D));
        return Math.max(1, (int) Math.round(base * scale));
    }

    private int spawnIntervalTicks() {
        return Math.max(1, plugin.getConfig().getInt("settings.animations.wheel.spawn-interval-ticks", 6));
    }

    private double radius() {
        return Math.max(0.8D, plugin.getConfig().getDouble("settings.animations.wheel.radius", 1.65D));
    }

    private double wheelYOffset() {
        return plugin.getConfig().getDouble("settings.animations.wheel.y-offset", 0.15D);
    }

    private int maxSpeed() {
        return Math.max(4, plugin.getConfig().getInt("settings.animations.wheel.max-speed", 15));
    }

    private int minSpeed() {
        return Math.max(1, plugin.getConfig().getInt("settings.animations.wheel.min-speed", 2));
    }

    private double speedFade() {
        return Math.max(0.01D, plugin.getConfig().getDouble("settings.animations.wheel.speed-fade", 0.07D));
    }

    private float volume(float base) {
        double scale = Math.max(0.0D, plugin.getConfig().getDouble("settings.animations.intensity.sound", 1.0D));
        return (float) (base * scale);
    }

    private int topDegrees() {
        return 90;
    }

    private static final class WheelSlot {
        private final ArmorStand stand;
        private final CaseItem prize;
        private final boolean winner;
        private int degrees;

        private WheelSlot(ArmorStand stand, CaseItem prize, int degrees, boolean winner) {
            this.stand = stand;
            this.prize = prize;
            this.degrees = degrees;
            this.winner = winner;
        }
    }
}
