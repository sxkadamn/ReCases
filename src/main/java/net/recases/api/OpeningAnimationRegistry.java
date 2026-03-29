package net.recases.api;

import net.recases.api.animation.OpeningAnimationRegistration;

import java.util.List;

public interface OpeningAnimationRegistry {

    boolean register(OpeningAnimationRegistration registration);

    boolean unregister(String animationId);

    boolean isRegistered(String animationId);

    List<String> getRegisteredIds();
}
