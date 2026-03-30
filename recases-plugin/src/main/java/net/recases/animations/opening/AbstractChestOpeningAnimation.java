package net.recases.animations.opening;

import net.recases.app.PluginContext;
import net.recases.management.OpeningSession;
import net.recases.runtime.CaseRuntime;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

public abstract class AbstractChestOpeningAnimation implements OpeningAnimation {

    protected final PluginContext plugin;
    protected final Player player;
    protected final CaseRuntime runtime;

    protected AbstractChestOpeningAnimation(PluginContext plugin, Player player, CaseRuntime runtime) {
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
        player.playSound(runtime.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0F, 1.0F);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isActive(session)) {
                    plugin.getCaseService().abortOpening(runtime, true);
                    return;
                }

                beforeSpawn(session);
                spawnChests(session, offsets());
                afterSpawn(session);
            }
        }.runTaskLater(plugin, startDelayTicks());
        return true;
    }

    protected void beforeSpawn(OpeningSession session) {
        runtime.getLocation().getBlock().setType(Material.AIR);
    }

    protected void afterSpawn(OpeningSession session) {
    }

    protected int startDelayTicks() {
        return 20;
    }

    protected boolean isActive(OpeningSession session) {
        return session != null
                && runtime.getSession() == session
                && runtime.isOpening()
                && runtime.getLocation().getWorld() != null
                && player.isOnline()
                && session.isParticipant(player);
    }

    protected abstract int[][] offsets();

    protected void spawnChests(OpeningSession session, int[][] offsets) {
        for (int[] offset : offsets) {
            spawnChestTarget(session, center(offset[0], offset[1], offset[2]));
        }
    }

    protected Location center(double x, double y, double z) {
        return runtime.getLocation().clone().add(x, y, z);
    }

    protected void spawnChestTarget(OpeningSession session, Location chestLocation) {
        spawnChestTarget(session, chestLocation, isPremiumReward(session) ? Material.POLISHED_BLACKSTONE_BRICKS : Material.STONE);
    }

    protected void spawnChestTarget(OpeningSession session, Location chestLocation, Material platformMaterial) {
        if (chestLocation.getWorld() == null) {
            return;
        }

        Block mainBlock = runtime.getLocation().getBlock();
        Block chest = chestLocation.getBlock();
        chest.setType(Material.CHEST);
        chest.setMetadata("case_open_chest", new FixedMetadataValue(plugin, runtime.getId()));
        plugin.getWorldService().orientChest(mainBlock.getLocation(), chest);
        session.getChestLocations().add(chestLocation);

        Block platform = chestLocation.clone().add(0, mainBlock.getY() - 1 - chestLocation.getBlockY(), 0).getBlock();
        platform.setType(platformMaterial);
        platform.setMetadata("case_platform", new FixedMetadataValue(plugin, runtime.getId()));
        session.getPlatformLocations().add(platform.getLocation());

        chest.getWorld().spawnParticle(getChestSpawnParticle(session), chestLocation.toCenterLocation(), 10, 0.25, 0.25, 0.25, 0.01);
        chest.getWorld().playSound(chestLocation, getChestSpawnSound(session), 0.7F, isPremiumReward(session) ? 0.85F : 1.4F);
        if (isPremiumReward(session)) {
            chest.getWorld().spawnParticle(Particle.GLOW, chestLocation.toCenterLocation().add(0.0, 0.6, 0.0), 12, 0.18, 0.18, 0.18, 0.01);
        }
    }

    protected boolean isPremiumReward(OpeningSession session) {
        return session != null && session.getFinalReward() != null && (session.getFinalReward().isRare() || session.isGuaranteedReward());
    }

    protected Particle getChestSpawnParticle(OpeningSession session) {
        return isPremiumReward(session) ? Particle.WAX_ON : Particle.HAPPY_VILLAGER;
    }

    protected Sound getChestSpawnSound(OpeningSession session) {
        return isPremiumReward(session) ? Sound.BLOCK_AMETHYST_BLOCK_CHIME : Sound.BLOCK_CHEST_LOCKED;
    }

    protected ItemStack premiumPreview(OpeningSession session, String fallbackMaterial, String fallbackName) {
        if (isPremiumReward(session) && session.getFinalReward() != null) {
            return session.getFinalReward().getIcon();
        }
        return plugin.getItemFactory().create("ITEM;" + fallbackMaterial, fallbackName);
    }
}
