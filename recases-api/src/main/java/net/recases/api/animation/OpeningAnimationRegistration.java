package net.recases.api.animation;

import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.Objects;

public final class OpeningAnimationRegistration {

    private final Plugin owner;
    private final String id;
    private final String displayName;
    private final int requiredSelections;
    private final OpeningAnimationFactory factory;

    private OpeningAnimationRegistration(Plugin owner, String id, String displayName, int requiredSelections, OpeningAnimationFactory factory) {
        this.owner = owner;
        this.id = id;
        this.displayName = displayName;
        this.requiredSelections = requiredSelections;
        this.factory = factory;
    }

    public static OpeningAnimationRegistration create(Plugin owner, String id, String displayName, int requiredSelections, OpeningAnimationFactory factory) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(factory, "factory");

        String normalizedId = normalizeId(id);
        String normalizedDisplayName = displayName == null || displayName.trim().isEmpty() ? buildDisplayName(normalizedId) : displayName.trim();
        if (requiredSelections < 0) {
            throw new IllegalArgumentException("requiredSelections cannot be negative");
        }

        return new OpeningAnimationRegistration(owner, normalizedId, normalizedDisplayName, requiredSelections, factory);
    }

    public Plugin getOwner() {
        return owner;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getRequiredSelections() {
        return requiredSelections;
    }

    public OpeningAnimationFactory getFactory() {
        return factory;
    }

    public static String normalizeId(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("animation id cannot be blank");
        }
        return id.trim().toLowerCase(Locale.ROOT);
    }

    public static String defaultDisplayName(String id) {
        return buildDisplayName(normalizeId(id));
    }

    private static String buildDisplayName(String id) {
        String[] parts = id.split("-");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.length() == 0 ? id : builder.toString();
    }
}
