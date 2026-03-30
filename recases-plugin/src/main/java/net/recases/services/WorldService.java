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

    public Location createOpeningAnchor(Location caseLocation, Location playerLocation, double distance) {
        if (caseLocation == null) {
            return null;
        }
        return caseLocation.clone().add(0.5, 0.0, 0.5);
    }

    public void teleportToOpeningAnchor(Player player, Location openingAnchor, Location caseLocation) {
        if (player == null) {
            return;
        }

        Location target = caseLocation == null ? null : caseLocation.clone().add(0.5, 0.0, 0.5);
        if (target == null) {
            return;
        }

        if (caseLocation != null) {
            aimAt(target, caseLocation.clone().add(0.5, 0.5, 0.5));
        }
        player.teleport(target);
    }

    public void pullToward(Player player, Location target, double horizontalStrength, double verticalStrength) {
        if (player == null || target == null || target.getWorld() == null) {
            return;
        }

        if (player.getWorld() != target.getWorld()) {
            player.teleport(target.clone().add(0.5, 0.0, 0.5));
            return;
        }

        Location playerLocation = player.getLocation();
        Vector targetVector = target.toVector().add(new Vector(0.5, playerLocation.getY() - target.getY(), 0.5));
        Vector pullVector = targetVector.subtract(playerLocation.toVector());
        pullVector.setY(0.0D);
        if (pullVector.lengthSquared() < 0.0001D) {
            return;
        }

        pullVector.normalize().multiply(horizontalStrength);
        pullVector.setY(verticalStrength);
        player.setVelocity(pullVector);
    }

    public void pushAway(Player player, Location source, double horizontalStrength, double verticalStrength) {
        if (player == null || source == null || source.getWorld() == null || player.getWorld() != source.getWorld()) {
            return;
        }

        Location playerLocation = player.getLocation();
        Vector pushVector = playerLocation.toVector().subtract(source.toVector().add(new Vector(0.5, 0.0, 0.5)));
        if (pushVector.lengthSquared() < 0.0001D) {
            pushVector = player.getLocation().getDirection().multiply(-1);
        }
        if (pushVector.lengthSquared() < 0.0001D) {
            pushVector = new Vector(1, 0, 0);
        }

        pushVector.normalize().multiply(horizontalStrength);
        pushVector.setY(verticalStrength);
        player.setVelocity(pushVector);
    }

    private void aimAt(Location source, Location target) {
        Vector direction = target.toVector().subtract(source.toVector());
        if (direction.lengthSquared() < 0.0001D) {
            return;
        }
        source.setDirection(direction);
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


