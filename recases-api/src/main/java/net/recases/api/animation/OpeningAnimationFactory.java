package net.recases.api.animation;

@FunctionalInterface
public interface OpeningAnimationFactory {

    OpeningAnimation create(OpeningAnimationContext context);
}
