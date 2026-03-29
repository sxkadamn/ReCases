package net.recases.animations.round;

import net.recases.app.PluginContext;
import net.recases.animations.Animation;
import net.recases.management.OpeningSession;
import net.recases.runtime.CaseRuntime;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

public class RoundAnimation implements Animation {

    private static final int[][] CHEST_OFFSETS = {
            {0, 0, -2},
            {-2, 0, 0},
            {0, 0, 2},
            {2, 0, 0}
    };

    private final PluginContext plugin;
    private final Player player;
    private final CaseRuntime runtime;

    public RoundAnimation(PluginContext plugin, Player player, CaseRuntime runtime) {
        this.plugin = plugin;
        this.player = player;
        this.runtime = runtime;
    }

    @Override
    public void playAnimation() {
        OpeningSession session = runtime.getSession();
        if (session == null || !runtime.isAvailable()) {
            return;
        }

        runtime.removeHologram();
        player.teleport(runtime.getLocation().clone().add(0.5, 0.0, 0.5));
        player.playSound(runtime.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0F, 1.0F);

        new BukkitRunnable() {
            @Override
            public void run() {
                spawnLayout(session);
            }
        }.runTaskLater(plugin, 20L);
    }

    private void spawnLayout(OpeningSession session) {
        Block mainBlock = runtime.getLocation().getBlock();
        mainBlock.setType(Material.AIR);

        int platformY = mainBlock.getY() - 1;
        for (int[] offset : CHEST_OFFSETS) {
            Location chestLocation = mainBlock.getLocation().clone().add(offset[0], offset[1], offset[2]);
            Block chest = chestLocation.getBlock();
            chest.setType(Material.CHEST);
            chest.setMetadata("case_open_chest", new FixedMetadataValue(plugin, runtime.getId()));
            plugin.getWorldService().orientChest(mainBlock.getLocation(), chest);
            session.getChestLocations().add(chestLocation);

            Block platform = chestLocation.clone().add(0, platformY - chestLocation.getBlockY(), 0).getBlock();
            platform.setType(Material.STONE);
            platform.setMetadata("case_platform", new FixedMetadataValue(plugin, runtime.getId()));
        }
    }
}

