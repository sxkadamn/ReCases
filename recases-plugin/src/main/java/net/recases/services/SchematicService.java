package net.recases.services;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import net.recases.management.OpeningSession;
import net.recases.runtime.CaseRuntime;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

public class SchematicService implements AutoCloseable {

    private final JavaPlugin plugin;
    private final Map<Path, Clipboard> clipboardCache = new LinkedHashMap<>();
    private final Map<String, ActiveScene> activeScenes = new LinkedHashMap<>();
    private boolean worldEditWarningLogged;

    public SchematicService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        ensureSchematicsFolder().mkdirs();
        clipboardCache.clear();
        worldEditWarningLogged = false;
    }

    public void pasteAnimationScene(OpeningSession session, CaseRuntime runtime) {
        if (session == null || runtime == null) {
            return;
        }

        cleanup(runtime);

        SceneConfig config = readConfig(session.getAnimationId());
        if (config == null) {
            return;
        }
        if (!isWorldEditAvailable()) {
            logMissingWorldEdit(config.fileName());
            return;
        }

        World bukkitWorld = runtime.getLocation().getWorld();
        if (bukkitWorld == null) {
            return;
        }

        try {
            com.sk89q.worldedit.world.World adaptedWorld = BukkitAdapter.adapt(bukkitWorld);
            Clipboard clipboard = loadClipboard(config.file());
            BlockVector3 pasteOrigin = BlockVector3.at(
                    runtime.getLocation().getBlockX() + config.offsetX(),
                    runtime.getLocation().getBlockY() + config.offsetY(),
                    runtime.getLocation().getBlockZ() + config.offsetZ()
            );

            CuboidRegion targetRegion = targetRegion(clipboard, adaptedWorld, pasteOrigin);
            BlockArrayClipboard snapshot = snapshotRegion(targetRegion, adaptedWorld);
            pasteClipboard(clipboard, adaptedWorld, pasteOrigin, config.ignoreAir());
            activeScenes.put(runtime.getId(), new ActiveScene(bukkitWorld.getName(), snapshot));
        } catch (Exception exception) {
            plugin.getLogger().log(
                    Level.WARNING,
                    "Failed to paste schematic scene for animation '" + session.getAnimationId()
                            + "' at case '" + runtime.getId() + "': " + exception.getMessage(),
                    exception
            );
        }
    }

    public void cleanup(CaseRuntime runtime) {
        if (runtime == null) {
            return;
        }
        cleanup(runtime.getId());
    }

    @Override
    public void close() {
        for (String runtimeId : activeScenes.keySet().toArray(String[]::new)) {
            cleanup(runtimeId);
        }
        clipboardCache.clear();
    }

    private void cleanup(String runtimeId) {
        ActiveScene scene = activeScenes.remove(runtimeId);
        if (scene == null || !isWorldEditAvailable()) {
            return;
        }

        World world = Bukkit.getWorld(scene.worldName());
        if (world == null) {
            return;
        }

        try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
            Operation operation = new ClipboardHolder(scene.snapshot())
                    .createPaste(editSession)
                    .to(scene.snapshot().getOrigin())
                    .ignoreAirBlocks(false)
                    .copyEntities(false)
                    .copyBiomes(true)
                    .build();
            Operations.complete(operation);
            editSession.flushSession();
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to restore pasted schematic scene: " + exception.getMessage(), exception);
        }
    }

    private SceneConfig readConfig(String animationId) {
        if (!plugin.getConfig().getBoolean("settings.schematics.enabled", false)) {
            return null;
        }

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("settings.schematics.animations." + animationId);
        if (section == null || !section.getBoolean("enabled", false)) {
            return null;
        }

        String fileName = section.getString("file", "").trim();
        if (fileName.isEmpty()) {
            plugin.getLogger().warning("Schematic scene for animation '" + animationId + "' is enabled but file is empty.");
            return null;
        }

        File file = ensureSchematicsFolder().toPath().resolve(fileName).normalize().toFile();
        if (!file.isFile()) {
            plugin.getLogger().warning("Schematic scene file for animation '" + animationId + "' not found: " + file.getAbsolutePath());
            return null;
        }

        return new SceneConfig(
                fileName,
                file,
                section.getInt("offset.x", 0),
                section.getInt("offset.y", 0),
                section.getInt("offset.z", 0),
                section.getBoolean("ignore-air", false)
        );
    }

    private Clipboard loadClipboard(File file) throws IOException {
        Path path = file.toPath().toAbsolutePath().normalize();
        Clipboard cached = clipboardCache.get(path);
        if (cached != null) {
            return cached;
        }

        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            throw new IOException("Unknown schematic format: " + file.getName());
        }

        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            Clipboard clipboard = reader.read();
            clipboardCache.put(path, clipboard);
            return clipboard;
        }
    }

    private CuboidRegion targetRegion(Clipboard clipboard, com.sk89q.worldedit.world.World world, BlockVector3 pasteOrigin) {
        BlockVector3 minimum = pasteOrigin.add(clipboard.getRegion().getMinimumPoint().subtract(clipboard.getOrigin()));
        BlockVector3 maximum = pasteOrigin.add(clipboard.getRegion().getMaximumPoint().subtract(clipboard.getOrigin()));
        return new CuboidRegion(world, minimum, maximum);
    }

    private BlockArrayClipboard snapshotRegion(CuboidRegion region, com.sk89q.worldedit.world.World world) throws Exception {
        BlockArrayClipboard snapshot = new BlockArrayClipboard(region);
        snapshot.setOrigin(region.getMinimumPoint());
        ForwardExtentCopy copy = new ForwardExtentCopy(world, region, snapshot, region.getMinimumPoint());
        copy.setCopyingBiomes(true);
        copy.setCopyingEntities(false);
        Operations.complete(copy);
        return snapshot;
    }

    private void pasteClipboard(Clipboard clipboard, com.sk89q.worldedit.world.World world, BlockVector3 pasteOrigin, boolean ignoreAir) throws Exception {
        try (EditSession editSession = WorldEdit.getInstance().newEditSession(world)) {
            Operation operation = new ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(pasteOrigin)
                    .ignoreAirBlocks(ignoreAir)
                    .copyEntities(false)
                    .copyBiomes(true)
                    .build();
            Operations.complete(operation);
            editSession.flushSession();
        }
    }

    private boolean isWorldEditAvailable() {
        Plugin worldEdit = plugin.getServer().getPluginManager().getPlugin("WorldEdit");
        return worldEdit != null && worldEdit.isEnabled();
    }

    private void logMissingWorldEdit(String fileName) {
        if (worldEditWarningLogged) {
            return;
        }
        plugin.getLogger().warning("WorldEdit is not installed or not enabled. Schematic scene '" + fileName + "' will be skipped.");
        worldEditWarningLogged = true;
    }

    private File ensureSchematicsFolder() {
        String folderName = plugin.getConfig().getString("settings.schematics.folder", "schematics");
        return new File(plugin.getDataFolder(), folderName);
    }

    private record SceneConfig(String fileName, File file, int offsetX, int offsetY, int offsetZ, boolean ignoreAir) {
    }

    private record ActiveScene(String worldName, BlockArrayClipboard snapshot) {
    }
}
