package net.recases.listener;

import net.recases.app.PluginContext;
import net.recases.management.OpeningSession;
import net.recases.runtime.CaseRuntime;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class OpeningGuardListener implements Listener {

    private static final long PUSH_COOLDOWN_MILLIS = 350L;

    private final PluginContext plugin;
    private final Map<UUID, Long> lastOwnerPush = new HashMap<>();
    private final Map<UUID, Long> lastIntruderPush = new HashMap<>();

    public OpeningGuardListener(PluginContext plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!hasPositionChanged(event)) {
            return;
        }

        Player player = event.getPlayer();
        long now = System.currentTimeMillis();

        for (CaseRuntime runtime : plugin.getCaseService().getRuntimes()) {
            OpeningSession session = runtime.getSession();
            if (session == null) {
                continue;
            }

            Location caseLocation = runtime.getLocation();
            if (caseLocation.getWorld() == null) {
                continue;
            }

            if (session.isParticipant(player)) {
                protectOwner(player, caseLocation, now);
                return;
            }

            repelIntruder(player, caseLocation, now);
        }
    }

    private void protectOwner(Player player, Location caseLocation, long now) {
        double maxDistance = plugin.getConfig().getDouble("settings.opening-guard.owner-max-distance", 4.0D);
        double maxDistanceSquared = maxDistance * maxDistance;
        Location anchor = caseLocation.clone().add(0.5, 0.0, 0.5);

        if (player.getWorld() != caseLocation.getWorld()) {
            plugin.getWorldService().teleportToOpeningAnchor(player, anchor, caseLocation);
            return;
        }

        double distanceSquared = horizontalDistanceSquared(player.getLocation(), anchor);
        if (distanceSquared <= maxDistanceSquared || isCoolingDown(lastOwnerPush, player.getUniqueId(), now)) {
            return;
        }

        plugin.getWorldService().teleportToOpeningAnchor(player, anchor, caseLocation);
        lastOwnerPush.put(player.getUniqueId(), now);
    }

    private void repelIntruder(Player player, Location caseLocation, long now) {
        double radius = plugin.getConfig().getDouble("settings.opening-guard.other-player-radius", 3.5D);
        double radiusSquared = radius * radius;

        if (player.getWorld() != caseLocation.getWorld()) {
            return;
        }
        if (player.getLocation().distanceSquared(caseLocation) > radiusSquared) {
            return;
        }
        if (isCoolingDown(lastIntruderPush, player.getUniqueId(), now)) {
            return;
        }

        plugin.getWorldService().pushAway(
                player,
                caseLocation,
                plugin.getConfig().getDouble("settings.opening-guard.other-player-push-strength", 1.4D),
                plugin.getConfig().getDouble("settings.opening-guard.other-player-push-vertical", 0.35D)
        );
        lastIntruderPush.put(player.getUniqueId(), now);
    }

    private boolean hasPositionChanged(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return false;
        }

        return from.getWorld() != to.getWorld()
                || from.getX() != to.getX()
                || from.getY() != to.getY()
                || from.getZ() != to.getZ();
    }

    private boolean isCoolingDown(Map<UUID, Long> cooldowns, UUID playerId, long now) {
        Long lastPush = cooldowns.get(playerId);
        return lastPush != null && now - lastPush < PUSH_COOLDOWN_MILLIS;
    }

    private double horizontalDistanceSquared(Location first, Location second) {
        if (first.getWorld() != second.getWorld()) {
            return Double.MAX_VALUE;
        }

        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }

}
