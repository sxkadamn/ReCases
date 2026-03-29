package net.recases.api.animation;

import net.recases.api.ReCasesApi;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public interface OpeningAnimationContext {

    ReCasesApi getApi();

    Player getPlayer();

    String getRuntimeId();

    Location getRuntimeLocation();

    boolean isRuntimeAvailable();

    boolean isOpeningActive();

    String getProfileId();

    OpeningSessionView getSession();

    void removeRuntimeHologram();

    void abortOpening(boolean refundKey);

    void completeOpening();

    void registerTargetChest(Location location);

    void registerTargetEntity(Entity entity);
}
