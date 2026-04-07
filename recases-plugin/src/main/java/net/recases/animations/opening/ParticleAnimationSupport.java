package net.recases.animations.opening;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.util.Vector;

final class ParticleAnimationSupport {

    private ParticleAnimationSupport() {
    }

    static void drawLine(Location start, Location end, double step, ParticleEmitter emitter) {
        World world = start.getWorld();
        if (world == null || end.getWorld() == null || step <= 0.0D) {
            return;
        }

        Vector direction = end.toVector().subtract(start.toVector());
        double distance = direction.length();
        if (distance <= 0.001D) {
            emitter.emit(start, 1);
            return;
        }

        int points = Math.max(1, (int) Math.ceil(distance / step));
        Vector increment = direction.multiply(1.0D / points);
        Location cursor = start.clone();
        for (int index = 0; index <= points; index++) {
            emitter.emit(cursor, 1);
            cursor.add(increment);
        }
    }

    static Particle resolveParticle(String raw, Particle fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }

        String normalized = raw.trim().toUpperCase(java.util.Locale.ROOT);
        if ("REDSTONE".equals(normalized)) {
            normalized = "DUST";
        }

        try {
            return Particle.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    @FunctionalInterface
    interface ParticleEmitter {
        void emit(Location location, int count);
    }
}
