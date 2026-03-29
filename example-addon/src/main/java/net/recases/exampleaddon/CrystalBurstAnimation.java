package net.recases.exampleaddon;

import net.recases.api.animation.OpeningAnimation;
import net.recases.api.animation.OpeningAnimationContext;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class CrystalBurstAnimation implements OpeningAnimation {

    private static final int[][] OFFSETS = {
            {3, 0, 0},
            {-2, 0, 2},
            {-2, 0, -2}
    };

    private final Plugin plugin;
    private final OpeningAnimationContext context;

    public CrystalBurstAnimation(Plugin plugin, OpeningAnimationContext context) {
        this.plugin = plugin;
        this.context = context;
    }

    @Override
    public boolean play() {
        if (!context.isRuntimeAvailable() || !context.isOpeningActive()) {
            return false;
        }

        World world = context.getRuntimeLocation().getWorld();
        if (world == null) {
            context.abortOpening(true);
            return false;
        }

        Player player = context.getPlayer();
        Location base = context.getRuntimeLocation().getBlock().getLocation();
        context.removeRuntimeHologram();
        base.getBlock().setType(Material.AIR);
        player.teleport(base.clone().add(0.5, 0.0, 0.5));
        world.playSound(base, Sound.BLOCK_ENDER_CHEST_OPEN, 1.0F, 0.9F);

        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (!context.isOpeningActive() || base.getWorld() == null) {
                    cancel();
                    return;
                }

                tick++;
                world.spawnParticle(Particle.END_ROD, base.clone().add(0.5, 1.0, 0.5), 16, 0.45, 0.45, 0.45, 0.02);
                world.spawnParticle(Particle.GLOW, base.clone().add(0.5, 0.7, 0.5), 10, 0.18, 0.18, 0.18, 0.01);
                world.playSound(base, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.55F, 0.9F + (tick * 0.05F));

                if (tick >= 5) {
                    cancel();
                    spawnSelectionChests(base);
                }
            }
        }.runTaskTimer(plugin, 0L, 4L);

        return true;
    }

    private void spawnSelectionChests(Location base) {
        World world = base.getWorld();
        if (world == null || !context.isOpeningActive()) {
            context.abortOpening(true);
            return;
        }

        for (int[] offset : OFFSETS) {
            Location chestLocation = base.clone().add(offset[0], offset[1], offset[2]);
            Block block = chestLocation.getBlock();
            block.setType(Material.ENDER_CHEST);
            context.registerTargetChest(chestLocation);

            world.spawnParticle(Particle.WAX_ON, chestLocation.clone().add(0.5, 0.8, 0.5), 14, 0.2, 0.25, 0.2, 0.01);
            world.playSound(chestLocation, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.45F, 1.2F);
        }

        world.playSound(base, Sound.BLOCK_BEACON_ACTIVATE, 0.8F, 1.15F);
        playerHint();
    }

    private void playerHint() {
        Player player = context.getPlayer();
        player.sendMessage("§bНажмите на один из кристальных сундуков.");
        player.sendTitle("§dCrystal Burst", "§fВыберите один сундук", 5, 40, 10);
    }
}
