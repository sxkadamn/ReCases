package net.recases.animations.opening;

import net.recases.app.PluginContext;
import net.recases.management.OpeningSession;
import net.recases.runtime.CaseRuntime;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;

public class SwordsOpeningAnimation implements OpeningAnimation {

    public static final String SWORDS_DECOR_METADATA = "case_swords_decor";

    private static final int[][] POSITIONS = {
            {-2, 0, -2},
            {2, 0, -2},
            {-2, 0, 2},
            {2, 0, 2}
    };

    private final PluginContext plugin;
    private final Player player;
    private final CaseRuntime runtime;

    public SwordsOpeningAnimation(PluginContext plugin, Player player, CaseRuntime runtime) {
        this.plugin = plugin;
        this.player = player;
        this.runtime = runtime;
    }

    @Override
    public boolean play() {
        OpeningSession session = runtime.getSession();
        if (session == null || !runtime.isAvailable()) {
            return false;
        }

        runtime.removeHologram();
        player.teleport(runtime.getLocation().clone().add(0.5, 0.0, 0.5));
        player.playSound(runtime.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, volume(1.0F), 0.9F);
        startPulse();

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isActive(session)) {
                    plugin.getCaseService().abortOpening(runtime, true);
                    return;
                }
                spawnPedestals(session);
            }
        }.runTaskLater(plugin, 10L);
        return true;
    }

    private void spawnPedestals(OpeningSession session) {
        for (int i = 0; i < POSITIONS.length; i++) {
            int[] offset = POSITIONS[i];
            Location blockLocation = runtime.getLocation().clone().add(offset[0], offset[1], offset[2]);
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!isActive(session)) {
                        return;
                    }

                    Block block = blockLocation.getBlock();
                    block.setType(Material.ANDESITE);
                    block.setMetadata("case_open_chest", new FixedMetadataValue(plugin, runtime.getId()));
                    session.getChestLocations().add(blockLocation);
                    block.getWorld().spawnParticle(Particle.BLOCK, blockLocation.toCenterLocation(), 14, 0.22, 0.22, 0.22, Material.ANDESITE.createBlockData());
                    block.getWorld().playSound(blockLocation, Sound.BLOCK_STONE_PLACE, volume(0.9F), 1.0F);
                }
            }.runTaskLater(plugin, i * 3L);

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!isActive(session)) {
                        return;
                    }
                    spawnSword(session, blockLocation);
                }
            }.runTaskLater(plugin, 14L + (i * 4L));
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isActive(session)) {
                    return;
                }
                promptSelection();
            }
        }.runTaskLater(plugin, 42L);
    }

    private void spawnSword(OpeningSession session, Location blockLocation) {
        if (blockLocation.getWorld() == null) {
            return;
        }

        Location start = runtime.getLocation().clone().add(0.5, 1.2, 0.5);
        Location end = blockLocation.clone().add(0.9, 0.9, 0.3);
        ArmorStand stand = (ArmorStand) blockLocation.getWorld().spawnEntity(start, EntityType.ARMOR_STAND);
        stand.setInvisible(true);
        stand.setMarker(true);
        stand.setGravity(false);
        stand.setSmall(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.getEquipment().setHelmet(new org.bukkit.inventory.ItemStack(Material.NETHERITE_SWORD));
        stand.setHeadPose(new EulerAngle(Math.PI, 0.0D, Math.toRadians(45.0D)));
        stand.setMetadata(SWORDS_DECOR_METADATA, new FixedMetadataValue(plugin, runtime.getId()));
        session.trackEntity(stand);

        blockLocation.getWorld().spawnParticle(Particle.WITCH, start.clone().add(0.0, 1.0, 0.0), 20, 0.0, 0.0, 0.0, 0.01);
        blockLocation.getWorld().spawnParticle(Particle.REVERSE_PORTAL, start.clone().add(0.0, 1.0, 0.0), 20, 0.0, 0.0, 0.0, 0.08);
        blockLocation.getWorld().playSound(start, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, volume(1.0F), 1.0F);

        new BukkitRunnable() {
            private int step;

            @Override
            public void run() {
                if (!isActive(session) || blockLocation.getWorld() == null || stand.isDead()) {
                    cancel();
                    return;
                }

                step++;
                double progress = Math.min(1.0D, step / 10.0D);
                double x = lerp(start.getX(), end.getX(), progress);
                double z = lerp(start.getZ(), end.getZ(), progress);
                double y = lerp(start.getY(), end.getY(), progress) + (Math.sin(progress * Math.PI) * 1.4D);
                stand.teleport(new Location(blockLocation.getWorld(), x, y, z));
                if (progress >= 1.0D) {
                    blockLocation.getBlock().setType(Material.COBBLESTONE);
                    blockLocation.getBlock().setMetadata("case_open_chest", new FixedMetadataValue(plugin, runtime.getId()));
                    blockLocation.getWorld().spawnParticle(Particle.CLOUD, end.clone().add(0.5, 0.9, 0.5), 8, 0.05, 0.05, 0.05, 0.03);
                    blockLocation.getWorld().playSound(end, Sound.ITEM_TRIDENT_HIT_GROUND, volume(1.0F), 1.0F);
                    blockLocation.getWorld().playSound(end, Sound.ITEM_TRIDENT_RETURN, volume(1.0F), 1.4F);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void promptSelection() {
        plugin.getMessages().title(
                player,
                "messages.case-select-target-title",
                "#ffd166Сделайте выбор",
                "messages.case-select-target-subtitle",
                "#ffffffАнимация: %animation%",
                5,
                40,
                10,
                "%animation%",
                plugin.getAnimations().getDisplayName("swords")
        );
        plugin.getMessages().send(
                player,
                "messages.case-select-target-chat",
                "#a8dadcНажмите по камню с мечом, чтобы завершить открытие.",
                "%animation%",
                plugin.getAnimations().getDisplayName("swords")
        );
    }

    private boolean isActive(OpeningSession session) {
        return session != null
                && runtime.getSession() == session
                && runtime.isOpening()
                && runtime.getLocation().getWorld() != null
                && player.isOnline()
                && session.isParticipant(player);
    }

    private double lerp(double from, double to, double progress) {
        return from + ((to - from) * progress);
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
                runtime.getLocation().getWorld().spawnParticle(Particle.ENCHANT, runtime.getLocation().clone().add(0.5, 0.8, 0.5), 10, 0.2, 0.18, 0.2, 0.02);
                runtime.getLocation().getWorld().spawnParticle(Particle.CRIT, runtime.getLocation().clone().add(0.5, 1.0, 0.5), 6, 0.35, 0.12, 0.35, 0.01);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private float volume(float base) {
        double scale = Math.max(0.0D, plugin.getConfig().getDouble("settings.animations.intensity.sound", 1.0D));
        return (float) (base * scale);
    }
}
