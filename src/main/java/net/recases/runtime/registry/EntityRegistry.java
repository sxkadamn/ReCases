package net.recases.runtime.registry;

import net.recases.protocollib.hologram.Hologram;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ArmorStand;

import java.util.ArrayList;
import java.util.List;

public class EntityRegistry {

    private final List<Hologram> holograms = new ArrayList<>();
    private final List<ArmorStand> armorStands = new ArrayList<>();
    private final List<Entity> entities = new ArrayList<>();

    public void addHologram(Hologram hologram) {
        holograms.add(hologram);
    }

    public void removeHologram(Hologram hologram) {
        holograms.remove(hologram);
    }

    public void addArmorStand(ArmorStand armorStand) {
        armorStands.add(armorStand);
    }

    public void removeArmorStand(ArmorStand armorStand) {
        armorStands.remove(armorStand);
    }

    public void addEntity(Entity entity) {
        entities.add(entity);
    }

    public void removeEntity(Entity entity) {
        entities.remove(entity);
    }

    public void clear() {
        for (Hologram hologram : new ArrayList<>(holograms)) {
            hologram.remove();
        }
        holograms.clear();

        for (ArmorStand armorStand : new ArrayList<>(armorStands)) {
            armorStand.remove();
        }
        armorStands.clear();

        for (Entity entity : new ArrayList<>(entities)) {
            entity.remove();
        }
        entities.clear();
    }
}

