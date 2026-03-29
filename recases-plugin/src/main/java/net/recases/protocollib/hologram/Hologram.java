package net.recases.protocollib.hologram;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Hologram {

    private static final double DEFAULT_LINE_SPACING = 0.3D;

    private final List<HologramLine> hologramLines = new ArrayList<>();
    private final double lineSpacing;
    private final Plugin plugin;

    private Location baseLocation;
    private List<String> lines = new ArrayList<>();
    private boolean spawned;

    public Hologram(Plugin plugin, List<String> lines, Location baseLocation) {
        this(plugin, lines, baseLocation, DEFAULT_LINE_SPACING);
    }

    public Hologram(Plugin plugin, String line, Location baseLocation) {
        this(plugin, Collections.singletonList(line), baseLocation, DEFAULT_LINE_SPACING);
    }

    public Hologram(Plugin plugin, List<String> lines, Location baseLocation, double lineSpacing) {
        this.plugin = plugin;
        this.baseLocation = baseLocation.clone();
        this.lineSpacing = lineSpacing;
        setLines(lines);
    }

    public List<HologramLine> getHologramLines() {
        return Collections.unmodifiableList(hologramLines);
    }

    public List<String> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public String getLine() {
        return String.join("\n", lines);
    }

    public Location getLocation() {
        return baseLocation.clone();
    }

    public boolean isSpawned() {
        return spawned;
    }

    public void spawn() {
        if (spawned) {
            return;
        }

        rebuildLines();
        spawned = true;
    }

    public void setLines(List<String> lines) {
        this.lines = sanitize(lines);
        if (spawned) {
            rebuildLines();
        }
    }

    public void setLine(String line) {
        List<String> singleLine = new ArrayList<>();
        if (line != null) {
            String[] split = line.split("\n");
            Collections.addAll(singleLine, split);
        }
        setLines(singleLine);
    }

    public void setLocation(Location location) {
        this.baseLocation = location.clone();
        if (!spawned) {
            return;
        }

        for (int i = 0; i < hologramLines.size(); i++) {
            hologramLines.get(i).teleport(calculateLineLocation(i));
        }
    }

    public void remove() {
        for (HologramLine hologramLine : hologramLines) {
            hologramLine.remove();
        }
        hologramLines.clear();
        spawned = false;
    }

    public void removeHologram() {
        remove();
    }

    private void rebuildLines() {
        for (HologramLine hologramLine : hologramLines) {
            hologramLine.remove();
        }
        hologramLines.clear();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            hologramLines.add(new HologramLine(plugin, line, calculateLineLocation(hologramLines.size())));
        }
    }

    private Location calculateLineLocation(int index) {
        return baseLocation.clone().subtract(0.0D, lineSpacing * index, 0.0D);
    }

    private List<String> sanitize(List<String> input) {
        List<String> result = new ArrayList<>();
        if (input == null) {
            return result;
        }
        for (String line : input) {
            result.add(line == null ? "" : line);
        }
        return result;
    }
}

