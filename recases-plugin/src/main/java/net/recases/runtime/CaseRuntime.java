package net.recases.runtime;

import net.recases.domain.CaseInstance;
import net.recases.management.OpeningSession;
import net.recases.protocollib.hologram.Hologram;
import net.recases.services.TextFormatter;
import net.recases.services.WorldService;
import net.recases.runtime.registry.EntityRegistry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.stream.Collectors;

public class CaseRuntime {

    public static final String CASE_METADATA = "case_instance";

    private final JavaPlugin plugin;
    private final EntityRegistry entityRegistry;
    private final TextFormatter textFormatter;
    private final WorldService worldService;
    private final CaseInstance definition;
    private Hologram hologram;
    private OpeningSession session;

    public CaseRuntime(JavaPlugin plugin, EntityRegistry entityRegistry, TextFormatter textFormatter, WorldService worldService, CaseInstance definition) {
        this.plugin = plugin;
        this.entityRegistry = entityRegistry;
        this.textFormatter = textFormatter;
        this.worldService = worldService;
        this.definition = definition;
    }

    public void spawn() {
        if (!spawnBlock()) {
            return;
        }
        spawnHologram();
    }

    public void remove() {
        removeHologram();
        clearOpeningArtifacts();
        worldService.removePlatform(getLocation());
        if (getLocation().getWorld() != null) {
            getLocation().getBlock().setType(Material.AIR);
        }
        session = null;
    }

    public boolean spawnBlock() {
        if (getLocation().getWorld() == null) {
            return false;
        }
        Block block = getLocation().getBlock();
        block.setType(Material.ENDER_CHEST);
        block.setMetadata(CASE_METADATA, new FixedMetadataValue(plugin, getId()));
        return true;
    }

    public void spawnHologram() {
        if (getLocation().getWorld() == null) {
            return;
        }
        removeHologram();
        List<String> lines = definition.getHologramLines().stream().map(textFormatter::colorize).collect(Collectors.toList());
        hologram = new Hologram(plugin, lines, getLocation().clone().add(0.5, 2.5, 0.5));
        hologram.spawn();
        entityRegistry.addHologram(hologram);
    }

    public void removeHologram() {
        if (hologram == null) {
            return;
        }
        hologram.remove();
        entityRegistry.removeHologram(hologram);
        hologram = null;
    }

    public String getId() {
        return definition.getId();
    }

    public Location getLocation() {
        return definition.getLocation();
    }

    public String getAnimationId() {
        return definition.getAnimationId();
    }

    public OpeningSession getSession() {
        return session;
    }

    public void setSession(OpeningSession session) {
        this.session = session;
    }

    public boolean isOpening() {
        return session != null;
    }

    public boolean isAvailable() {
        return getLocation().getWorld() != null
                && getLocation().getBlock().getType() == Material.ENDER_CHEST
                && getLocation().getBlock().hasMetadata(CASE_METADATA);
    }

    public void clearSession() {
        this.session = null;
    }

    public void resetOpeningState() {
        removeHologram();
        clearOpeningArtifacts();
        worldService.removePlatform(getLocation());
        session = null;
        if (getLocation().getWorld() != null) {
            getLocation().getBlock().setType(Material.AIR);
        }
        spawnBlock();
        spawnHologram();
    }

    private void clearOpeningArtifacts() {
        if (session == null) {
            return;
        }

        for (Location chestLocation : session.getChestLocations()) {
            chestLocation.getBlock().setType(Material.AIR);
        }
        for (Location platformLocation : session.getPlatformLocations()) {
            platformLocation.getBlock().setType(Material.AIR);
        }
        session.clearTrackedEntities();
    }
}

