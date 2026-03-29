package net.recases.services;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class WorldService {

    private final JavaPlugin plugin;

    public WorldService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void knockback(Player player) {
        Location playerLocation = player.getLocation();
        Vector knockbackVector = playerLocation.getDirection().multiply(-1).normalize().multiply(2);
        knockbackVector.setY(0.5);
        player.setVelocity(knockbackVector);
    }

    public void removePlatform(Location baseLocation) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                Location location = baseLocation.clone().add(x, -1, z);
                Block block = location.getBlock();
                if (block.getType() == Material.STONE && block.hasMetadata("case_platform")) {
                    block.setType(Material.AIR);
                }
            }
        }
    }

    public void orientChest(Location mainLocation, Block block) {
        if (!(block.getBlockData() instanceof Directional)) {
            return;
        }

        int mainX = mainLocation.getBlockX();
        int mainZ = mainLocation.getBlockZ();
        int blockX = block.getLocation().getBlockX();
        int blockZ = block.getLocation().getBlockZ();

        Directional directionalBlockData = (Directional) block.getBlockData();
        BlockFace facing;
        if (Math.abs(mainX - blockX) > Math.abs(mainZ - blockZ)) {
            facing = mainX < blockX ? BlockFace.WEST : BlockFace.EAST;
        } else {
            facing = mainZ < blockZ ? BlockFace.NORTH : BlockFace.SOUTH;
        }

        directionalBlockData.setFacing(facing);
        block.setBlockData(directionalBlockData);
        block.setMetadata("facing", new FixedMetadataValue(plugin, facing.name()));
    }
}


