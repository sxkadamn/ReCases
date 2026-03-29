package net.recases.domain;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CaseInstance {

    private final String id;
    private final Location location;
    private final String animationId;
    private final List<String> hologramLines;

    public CaseInstance(String id, Location location, String animationId, List<String> hologramLines) {
        this.id = id;
        this.location = location;
        this.animationId = animationId;
        this.hologramLines = new ArrayList<>(hologramLines);
    }

    public String getId() {
        return id;
    }

    public Location getLocation() {
        return location.clone();
    }

    public String getAnimationId() {
        return animationId;
    }

    public List<String> getHologramLines() {
        return Collections.unmodifiableList(hologramLines);
    }
}

