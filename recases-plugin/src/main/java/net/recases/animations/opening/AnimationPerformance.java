package net.recases.animations.opening;

import net.recases.app.PluginContext;

import java.util.Locale;

final class AnimationPerformance {

    private static final int MEDIUM_LOAD_THRESHOLD = 3;
    private static final int HIGH_LOAD_THRESHOLD = 5;

    private final double particleScale;
    private final float soundScale;
    private final int activeOpenings;
    private final int motionInterval;
    private final int connectionBudget;
    private final Profile profile;

    private AnimationPerformance(double particleScale, float soundScale, int activeOpenings, int motionInterval, int connectionBudget, Profile profile) {
        this.particleScale = particleScale;
        this.soundScale = soundScale;
        this.activeOpenings = activeOpenings;
        this.motionInterval = motionInterval;
        this.connectionBudget = connectionBudget;
        this.profile = profile;
    }

    static AnimationPerformance create(PluginContext plugin) {
        int activeOpenings = Math.max(1, plugin.getCaseService().getActiveOpeningCount());
        Profile profile = Profile.fromConfig(plugin.getConfig().getString("settings.animations.performance.profile", "balanced"));
        boolean adaptiveLoad = plugin.getConfig().getBoolean("settings.animations.performance.adaptive-load", true);

        double configuredParticles = Math.max(0.1D, plugin.getConfig().getDouble("settings.animations.intensity.particles", 1.0D));
        double particleMultiplier = configuredParticles * profile.particleMultiplier;
        if (adaptiveLoad) {
            particleMultiplier *= 1.0D / Math.sqrt(activeOpenings);
        }
        double particleScale = Math.max(profile.minParticleScale, particleMultiplier);

        float soundScale = (float) Math.max(0.0D, plugin.getConfig().getDouble("settings.animations.intensity.sound", 1.0D) * profile.soundMultiplier);
        int motionInterval = adaptiveLoad ? profile.motionInterval(activeOpenings) : profile.baseMotionInterval;
        int connectionBudget = adaptiveLoad ? profile.connectionBudget(activeOpenings) : profile.baseConnectionBudget;
        return new AnimationPerformance(particleScale, soundScale, activeOpenings, motionInterval, connectionBudget, profile);
    }

    int particles(int base) {
        if (base <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.round(base * particleScale));
    }

    float volume(float base) {
        return base * soundScale;
    }

    int motionInterval() {
        return motionInterval;
    }

    long cadence(long baseTicks) {
        return Math.max(1L, baseTicks * motionInterval);
    }

    int stepTicks(int baseTicks) {
        return Math.max(1, baseTicks * motionInterval);
    }

    double lineStep(double baseStep) {
        return baseStep / Math.min(1.0D, particleScale);
    }

    int connectionBudget(int total) {
        return Math.min(total, connectionBudget);
    }

    int activeOpenings() {
        return activeOpenings;
    }

    int limitNeuralNodes(int configured) {
        return Math.min(configured, profile.neuralNodeLimit(activeOpenings));
    }

    int limitSphereItems(int configured) {
        return Math.min(configured, profile.sphereItemLimit(activeOpenings));
    }

    int limitWheelSlots(int configured) {
        return Math.min(configured, profile.wheelSlotLimit(activeOpenings));
    }

    static boolean isKnownProfile(String value) {
        return Profile.isKnown(value);
    }

    private enum Profile {
        PRETTY("pretty", 1.2D, 1.0D, 1, 36, 0.45D),
        BALANCED("balanced", 1.0D, 1.0D, 1, 28, 0.25D),
        LITE("lite", 0.65D, 0.9D, 2, 16, 0.2D);

        private final String id;
        private final double particleMultiplier;
        private final double soundMultiplier;
        private final int baseMotionInterval;
        private final int baseConnectionBudget;
        private final double minParticleScale;

        Profile(String id, double particleMultiplier, double soundMultiplier, int baseMotionInterval, int baseConnectionBudget, double minParticleScale) {
            this.id = id;
            this.particleMultiplier = particleMultiplier;
            this.soundMultiplier = soundMultiplier;
            this.baseMotionInterval = baseMotionInterval;
            this.baseConnectionBudget = baseConnectionBudget;
            this.minParticleScale = minParticleScale;
        }

        private int motionInterval(int activeOpenings) {
            return switch (this) {
                case PRETTY -> activeOpenings >= HIGH_LOAD_THRESHOLD ? 2 : 1;
                case BALANCED -> activeOpenings >= HIGH_LOAD_THRESHOLD ? 3 : activeOpenings >= MEDIUM_LOAD_THRESHOLD ? 2 : 1;
                case LITE -> activeOpenings >= HIGH_LOAD_THRESHOLD ? 4 : activeOpenings >= MEDIUM_LOAD_THRESHOLD ? 3 : 2;
            };
        }

        private int connectionBudget(int activeOpenings) {
            return switch (this) {
                case PRETTY -> activeOpenings >= HIGH_LOAD_THRESHOLD ? 18 : activeOpenings >= MEDIUM_LOAD_THRESHOLD ? 28 : 40;
                case BALANCED -> activeOpenings >= HIGH_LOAD_THRESHOLD ? 10 : activeOpenings >= MEDIUM_LOAD_THRESHOLD ? 18 : 28;
                case LITE -> activeOpenings >= HIGH_LOAD_THRESHOLD ? 8 : activeOpenings >= MEDIUM_LOAD_THRESHOLD ? 12 : 16;
            };
        }

        private int neuralNodeLimit(int activeOpenings) {
            return switch (this) {
                case PRETTY -> activeOpenings >= HIGH_LOAD_THRESHOLD ? 18 : activeOpenings >= MEDIUM_LOAD_THRESHOLD ? 24 : 27;
                case BALANCED -> activeOpenings >= HIGH_LOAD_THRESHOLD ? 12 : activeOpenings >= MEDIUM_LOAD_THRESHOLD ? 18 : 27;
                case LITE -> activeOpenings >= HIGH_LOAD_THRESHOLD ? 8 : activeOpenings >= MEDIUM_LOAD_THRESHOLD ? 12 : 16;
            };
        }

        private int sphereItemLimit(int activeOpenings) {
            return switch (this) {
                case PRETTY -> activeOpenings >= HIGH_LOAD_THRESHOLD ? 12 : activeOpenings >= MEDIUM_LOAD_THRESHOLD ? 14 : 18;
                case BALANCED -> activeOpenings >= HIGH_LOAD_THRESHOLD ? 10 : activeOpenings >= MEDIUM_LOAD_THRESHOLD ? 12 : 16;
                case LITE -> activeOpenings >= HIGH_LOAD_THRESHOLD ? 8 : activeOpenings >= MEDIUM_LOAD_THRESHOLD ? 10 : 12;
            };
        }

        private int wheelSlotLimit(int activeOpenings) {
            return switch (this) {
                case PRETTY -> activeOpenings >= HIGH_LOAD_THRESHOLD ? 8 : 10;
                case BALANCED -> activeOpenings >= HIGH_LOAD_THRESHOLD ? 6 : activeOpenings >= MEDIUM_LOAD_THRESHOLD ? 8 : 8;
                case LITE -> activeOpenings >= HIGH_LOAD_THRESHOLD ? 5 : 6;
            };
        }

        private static Profile fromConfig(String raw) {
            String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
            for (Profile profile : values()) {
                if (profile.id.equals(normalized)) {
                    return profile;
                }
            }
            return BALANCED;
        }

        private static boolean isKnown(String raw) {
            String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
            for (Profile profile : values()) {
                if (profile.id.equals(normalized)) {
                    return true;
                }
            }
            return false;
        }
    }
}
