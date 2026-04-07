package net.recases.animations.opening;

import net.recases.app.PluginContext;
import net.recases.management.CaseItem;
import net.recases.management.OpeningSession;
import net.recases.runtime.CaseRuntime;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

public class RainlyOpeningAnimation implements OpeningAnimation {

    public static final String RAINLY_METADATA = "case_rainly_item";

    private final PluginContext plugin;
    private final Player player;
    private final CaseRuntime runtime;
    private final AnimationPerformance performance;
    private final Particle rainParticle;

    public RainlyOpeningAnimation(PluginContext plugin, Player player, CaseRuntime runtime) {
        this.plugin = plugin;
        this.player = player;
        this.runtime = runtime;
        this.performance = AnimationPerformance.create(plugin);
        this.rainParticle = resolveRainParticle();
    }

    @Override
    public boolean play() {
        OpeningSession session = runtime.getSession();
        if (session == null || !runtime.isAvailable() || session.getFinalReward() == null) {
            return false;
        }

        runtime.removeHologram();
        runtime.getLocation().getBlock().setType(Material.AIR);
        player.playSound(runtime.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, volume(1.0F), 0.9F);
        startPulse();

        Location spawn = runtime.getLocation().clone().add(0.5, 0.3, 0.5);
        ArmorStand stand = createStand(spawn, session.getFinalReward().getIcon());
        session.trackEntity(stand);

        Location caseBase = runtime.getLocation().clone();
        Location rain1 = caseBase.clone().add(-1.0D, 2.1D, -1.0D);
        Location rain2 = caseBase.clone().add(2.0D, 2.1D, -1.0D);
        Location rain3 = caseBase.clone().add(2.0D, 2.1D, 2.0D);
        Location rain4 = caseBase.clone().add(-1.0D, 2.1D, 2.0D);
        Location cloud1 = rain1.clone().add(0.0D, 0.5D, 0.0D);
        Location cloud2 = rain2.clone().add(0.0D, 0.5D, 0.0D);
        Location cloud3 = rain3.clone().add(0.0D, 0.5D, 0.0D);
        Location cloud4 = rain4.clone().add(0.0D, 0.5D, 0.0D);

        new BukkitRunnable() {
            private int tick;
            private double swirl;

            @Override
            public void run() {
                if (!isActive(session) || stand.isDead()) {
                    removeStand(stand);
                    plugin.getCaseService().abortOpening(runtime, true);
                    cancel();
                    return;
                }

                spawnRain(rain1, rain2, rain3, rain4, cloud1, cloud2, cloud3, cloud4);
                rotateStand(stand);

                if (tick <= revealTicks() && tick % rewardChangeInterval() == 0) {
                    CaseItem fakeReward = randomReward(session);
                    stand.getEquipment().setHelmet(fakeReward.getIcon());
                    stand.customName(plugin.getTextFormatter().asComponent(fakeReward.getName()));
                    runtime.getLocation().getWorld().playSound(runtime.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, volume(1.0F), 1.8F);
                    spawnSwirl(stand.getLocation());
                }

                if (tick == revealTick()) {
                    stand.getEquipment().setHelmet(session.getFinalReward().getIcon());
                    stand.customName(plugin.getTextFormatter().asComponent(session.getFinalReward().getName()));
                    runtime.getLocation().getWorld().spawnParticle(Particle.EXPLOSION, runtime.getLocation().clone().add(0.5, 0.35, 0.5), 1, 0.0, 0.0, 0.0, 0.0);
                    runtime.getLocation().getWorld().playSound(runtime.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, volume(1.0F), 1.0F);
                }

                if (tick >= endTick()) {
                    removeStand(stand);
                    plugin.getOpeningResults().complete(player, runtime, session, session.getFinalReward());
                    cancel();
                    return;
                }

                tick += performance.stepTicks(2);
                swirl += 0.25D;
            }

            private void spawnSwirl(Location center) {
                int samples = Math.max(4, scaled(10));
                for (int index = 0; index < samples; index++) {
                    double phi = (Math.PI * 2.0D * index) / samples;
                    double x = 0.09D * (9.0D - swirl * 2.5D) * Math.cos(swirl + phi);
                    double z = 0.09D * (9.0D - swirl * 2.5D) * Math.sin(swirl + phi);
                    center.getWorld().spawnParticle(Particle.FIREWORK, center.clone().add(x, 0.4D, z), 1, 0.02D, 0.02D, 0.02D, 0.0D);
                }
                if (swirl >= 22.0D) {
                    swirl = 0.0D;
                }
            }
        }.runTaskTimer(plugin, 0L, performance.cadence(2L));

        return true;
    }

    private ArmorStand createStand(Location location, ItemStack item) {
        ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setCollidable(false);
        stand.setSmall(true);
        stand.setSilent(true);
        stand.setCustomNameVisible(true);
        stand.getEquipment().setHelmet(item);
        stand.setMetadata(RAINLY_METADATA, new FixedMetadataValue(plugin, runtime.getId()));
        return stand;
    }

    private void rotateStand(ArmorStand stand) {
        Location location = stand.getLocation().clone();
        location.setYaw(location.getYaw() + 20.0F);
        stand.teleport(location);
    }

    private void spawnRain(Location rain1, Location rain2, Location rain3, Location rain4,
                           Location cloud1, Location cloud2, Location cloud3, Location cloud4) {
        rain1.getWorld().spawnParticle(rainParticle, rain1, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        rain2.getWorld().spawnParticle(rainParticle, rain2, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        rain3.getWorld().spawnParticle(rainParticle, rain3, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        rain4.getWorld().spawnParticle(rainParticle, rain4, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        rain1.getWorld().spawnParticle(Particle.CLOUD, cloud1, 1, 0.08D, 0.0D, 0.08D, 0.0D);
        rain2.getWorld().spawnParticle(Particle.CLOUD, cloud2, 1, 0.08D, 0.0D, 0.08D, 0.0D);
        rain3.getWorld().spawnParticle(Particle.CLOUD, cloud3, 1, 0.08D, 0.0D, 0.08D, 0.0D);
        rain4.getWorld().spawnParticle(Particle.CLOUD, cloud4, 1, 0.08D, 0.0D, 0.08D, 0.0D);
    }

    private CaseItem randomReward(OpeningSession session) {
        CaseItem fake = plugin.getCaseService().getRandomReward(session.getSelectedCase());
        return fake == null ? session.getFinalReward() : fake;
    }

    private Particle resolveRainParticle() {
        String configured = plugin.getConfig().getString("settings.animations.rainly.falling-particle", "DRIPPING_OBSIDIAN_TEAR");
        try {
            return Particle.valueOf(configured.toUpperCase());
        } catch (IllegalArgumentException exception) {
            return Particle.DRIPPING_OBSIDIAN_TEAR;
        }
    }

    private void removeStand(ArmorStand stand) {
        if (stand != null && stand.isValid()) {
            stand.remove();
        }
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
                runtime.getLocation().getWorld().spawnParticle(Particle.ENCHANT, runtime.getLocation().clone().add(0.5, 0.3, 0.5), scaled(10), 0.2, 0.18, 0.2, 0.02);
                runtime.getLocation().getWorld().spawnParticle(Particle.CLOUD, runtime.getLocation().clone().add(0.5, 0.55, 0.5), scaled(6), 0.35, 0.12, 0.35, 0.01);
            }
        }.runTaskTimer(plugin, 0L, performance.cadence(2L));
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

    private int rewardChangeInterval() {
        return Math.max(2, plugin.getConfig().getInt("settings.animations.rainly.reward-change-interval", 4));
    }

    private int revealTicks() {
        return Math.max(20, plugin.getConfig().getInt("settings.animations.rainly.reveal-ticks", 30));
    }

    private int revealTick() {
        return revealTicks() + 2;
    }

    private int endTick() {
        return Math.max(40, plugin.getConfig().getInt("settings.animations.rainly.end-ticks", 70));
    }

    private int scaled(int base) {
        return performance.particles(base);
    }

    private float volume(float base) {
        return performance.volume(base);
    }
}
