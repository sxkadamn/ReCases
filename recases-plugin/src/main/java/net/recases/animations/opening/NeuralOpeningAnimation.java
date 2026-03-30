package net.recases.animations.opening;

import net.recases.app.PluginContext;
import net.recases.management.CaseItem;
import net.recases.management.OpeningSession;
import net.recases.runtime.CaseRuntime;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class NeuralOpeningAnimation implements OpeningAnimation {

    public static final String NEURAL_ENTITY_METADATA = "case_neural_item";

    private final PluginContext plugin;
    private final Player player;
    private final CaseRuntime runtime;

    public NeuralOpeningAnimation(PluginContext plugin, Player player, CaseRuntime runtime) {
        this.plugin = plugin;
        this.player = player;
        this.runtime = runtime;
    }

    @Override
    public boolean play() {
        OpeningSession session = runtime.getSession();
        if (session == null || !runtime.isAvailable() || session.getFinalReward() == null) {
            return false;
        }

        runtime.removeHologram();
        hideCaseBlock();
        player.playSound(runtime.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, volume(1.0F), 0.9F);

        Location center = runtime.getLocation().clone().add(0.5D, neuralCenterYOffset(), 0.5D);
        startGrid(session, center);
        return true;
    }

    private void startGrid(OpeningSession session, Location center) {
        int gridSize = Math.max(2, plugin.getConfig().getInt("settings.animations.neural.grid-size", 3));
        int verticalLayers = Math.max(2, plugin.getConfig().getInt("settings.animations.neural.vertical-layers", 3));
        double spacing = Math.max(0.4D, plugin.getConfig().getDouble("settings.animations.neural.spacing", 1.2D));
        double layerHeight = Math.max(0.2D, plugin.getConfig().getDouble("settings.animations.neural.layer-height", 0.55D));

        List<Node> nodes = new ArrayList<>();
        int total = gridSize * gridSize * verticalLayers;
        int winnerIndex = ThreadLocalRandom.current().nextInt(total);
        double horizontalStart = -((gridSize - 1) / 2.0D);
        double verticalStart = -((verticalLayers - 1) / 2.0D);
        int index = 0;

        for (int layer = 0; layer < verticalLayers; layer++) {
            for (int x = 0; x < gridSize; x++) {
                for (int z = 0; z < gridSize; z++) {
                    boolean winner = index == winnerIndex;
                    CaseItem item = winner ? session.getFinalReward() : fallbackPrize(session);
                    Location baseLocation = center.clone().add(
                            (horizontalStart + x) * spacing,
                            (verticalStart + layer) * layerHeight,
                            (horizontalStart + z) * spacing
                    );
                    ArmorStand stand = spawn(center, item);
                    nodes.add(new Node(stand, baseLocation, winner, layer));
                    index++;
                }
            }
        }

        animateNodeSpawn(session, nodes, center, verticalLayers);
    }

    private void animateNodeSpawn(OpeningSession session, List<Node> nodes, Location center, int layerCount) {
        new BukkitRunnable() {
            private int currentLayer;

            @Override
            public void run() {
                if (!isActive(session)) {
                    clearNodes(nodes);
                    cancel();
                    plugin.getCaseService().abortOpening(runtime, true);
                    return;
                }

                if (currentLayer >= layerCount) {
                    cancel();
                    buildNetwork(session, nodes, center);
                    return;
                }

                int layer = currentLayer++;
                for (Node node : nodes) {
                    if (node.layer != layer) {
                        continue;
                    }
                    animateNodeMaterialization(session, center, node);
                }

                center.getWorld().playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, volume(0.35F), 0.7F + (currentLayer * 0.18F));
            }
        }.runTaskTimer(plugin, 0L, Math.max(1, spawnIntervalTicks()));
    }

    private void animateNodeMaterialization(OpeningSession session, Location center, Node node) {
        Location start = node.baseLocation.clone().add(0.0D, -spawnRiseDistance(), 0.0D);
        node.stand.teleport(start);

        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (!isActive(session) || node.stand.isDead()) {
                    cancel();
                    return;
                }

                tick++;
                double progress = Math.min(1.0D, tick / (double) spawnDurationTicks());
                Location next = interpolate(start, node.baseLocation, easeOut(progress));
                node.stand.teleport(next);
                spawnDust(visualLocation(next), baseDust(), 2);
                drawImpulse(center, visualLocation(next), progress, baseDust());

                if (progress >= 1.0D) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void buildNetwork(OpeningSession session, List<Node> nodes, Location center) {
        new BukkitRunnable() {
            private int ticks;

            @Override
            public void run() {
                if (!isActive(session)) {
                    clearNodes(nodes);
                    cancel();
                    plugin.getCaseService().abortOpening(runtime, true);
                    return;
                }

                ticks++;
                List<Connection> connections = activeConnections(nodes);
                for (Connection connection : connections) {
                    drawLine(connection.from, connection.to, lineDust());
                    drawImpulse(connection.from, connection.to, pulseProgress(ticks, connection.seed), baseDust());
                }

                for (Node node : nodes) {
                    spawnNodePulse(node, ticks);
                }

                if (ticks % 8 == 0) {
                    center.getWorld().playSound(center, Sound.BLOCK_NOTE_BLOCK_BIT, volume(0.18F), 1.65F);
                }
                if (ticks % 16 == 0) {
                    center.getWorld().playSound(center, Sound.BLOCK_NOTE_BLOCK_HAT, volume(0.12F), 1.9F);
                }

                if (ticks < networkTicks()) {
                    return;
                }

                cancel();
                processPhase(session, nodes);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void processPhase(OpeningSession session, List<Node> nodes) {
        new BukkitRunnable() {
            private int ticks;
            private boolean eliminating;

            @Override
            public void run() {
                if (!isActive(session)) {
                    clearNodes(nodes);
                    cancel();
                    plugin.getCaseService().abortOpening(runtime, true);
                    return;
                }

                ticks++;
                List<Connection> connections = activeConnections(nodes);
                for (Connection connection : connections) {
                    drawLine(connection.from, connection.to, lineDust());
                    drawImpulse(connection.from, connection.to, pulseProgress(ticks, connection.seed), baseDust());
                }

                for (Node node : nodes) {
                    Location next = node.baseLocation.clone().add(
                            random(-jitterAmount(), jitterAmount()),
                            random(-jitterAmount(), jitterAmount()),
                            random(-jitterAmount(), jitterAmount())
                    );
                    node.stand.teleport(next);
                    spawnDust(visualLocation(next), node.winner ? winnerDust() : baseDust(), 1);
                }

                if (!eliminating && ticks % eliminateIntervalTicks() == 0 && nodes.size() > 1) {
                    eliminating = true;
                    eliminateOne(nodes, () -> eliminating = false);
                }

                if (nodes.size() > 1 || eliminating) {
                    return;
                }

                cancel();
                highlightWinner(session, nodes.get(0), connections);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void eliminateOne(List<Node> nodes, Runnable onDone) {
        Iterator<Node> iterator = nodes.iterator();
        Node removed = null;

        while (iterator.hasNext()) {
            Node next = iterator.next();
            if (next.winner) {
                continue;
            }
            removed = next;
            iterator.remove();
            break;
        }

        if (removed == null) {
            onDone.run();
            return;
        }

        Node target = removed;
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (!target.stand.isValid()) {
                    cancel();
                    onDone.run();
                    return;
                }

                tick++;
                Location visual = visualLocation(target.stand.getLocation());
                Location jittered = target.stand.getLocation().clone().add(
                        random(-0.04D, 0.04D),
                        random(-0.02D, 0.05D),
                        random(-0.04D, 0.04D)
                );
                target.stand.teleport(jittered);
                spawnDust(visual, errorDust(), scaled(3));
                visual.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, visual, scaled(2), 0.05D, 0.05D, 0.05D, 0.01D);

                if (tick % 3 == 0) {
                    visual.getWorld().playSound(visual, Sound.UI_BUTTON_CLICK, volume(0.3F), 1.7F);
                }

                if (tick < overloadTicks()) {
                    return;
                }

                spawnDust(visual, errorDust(), scaled(8));
                visual.getWorld().spawnParticle(Particle.SMOKE, visual, scaled(10), 0.08D, 0.08D, 0.08D, 0.02D);
                visual.getWorld().playSound(visual, Sound.BLOCK_FIRE_EXTINGUISH, volume(0.45F), 0.95F);
                removeIfValid(target.stand);
                cancel();
                onDone.run();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void highlightWinner(OpeningSession session, Node winner, List<Connection> lastConnections) {
        List<Location> anchors = new ArrayList<>();
        for (Connection connection : lastConnections) {
            anchors.add(connection.from);
            anchors.add(connection.to);
        }
        if (anchors.isEmpty()) {
            anchors.add(runtime.getLocation().clone().add(0.5D, neuralCenterYOffset(), 0.5D));
        }

        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (!isActive(session) || winner.stand.isDead()) {
                    removeIfValid(winner.stand);
                    cancel();
                    plugin.getCaseService().abortOpening(runtime, true);
                    return;
                }

                tick++;
                double rise = Math.min(hoverRiseHeight(), tick * 0.03D);
                float yaw = (float) (tick * hoverSpinPerTick());
                Location pose = winner.baseLocation.clone().add(0.0D, rise, 0.0D);
                pose.setYaw(yaw);
                winner.stand.teleport(pose);

                Location visual = visualLocation(pose);
                spawnDust(visual, winnerDust(), scaled(4));
                for (int index = 0; index < anchors.size(); index++) {
                    Location anchor = anchors.get(index);
                    double pull = Math.min(1.0D, tick / (double) winnerHighlightTicks());
                    Location pulled = interpolate(anchor, visual, pull);
                    drawLine(pulled, visual, winnerDust());
                    drawImpulse(pulled, visual, pulseProgress(tick, index * 17L), winnerDust());
                }

                if (tick % 6 == 0) {
                    visual.getWorld().playSound(visual, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, volume(0.42F), 1.25F);
                }

                if (tick < winnerHighlightTicks()) {
                    return;
                }

                cancel();
                launchWinnerToCase(session, winner);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void launchWinnerToCase(OpeningSession session, Node winner) {
        new BukkitRunnable() {
            private final Location destination = runtime.getLocation().clone().add(0.5D, 0.72D, 0.5D);
            private int tick;

            @Override
            public void run() {
                if (!isActive(session) || winner.stand.isDead()) {
                    removeIfValid(winner.stand);
                    cancel();
                    plugin.getCaseService().abortOpening(runtime, true);
                    return;
                }

                tick++;
                Location current = winner.stand.getLocation();
                Vector direction = destination.toVector().subtract(current.toVector());
                if (direction.lengthSquared() <= 0.03D || tick >= 20) {
                    winner.stand.teleport(destination);
                    finish(session, winner);
                    cancel();
                    return;
                }

                direction.normalize().multiply(0.22D);
                Location next = current.clone().add(direction);
                next.setYaw(current.getYaw() + 28.0F);
                winner.stand.teleport(next);
                Location visual = visualLocation(next);
                spawnDust(visual, winnerDust(), 3);
                if (tick % 3 == 0) {
                    visual.getWorld().playSound(visual, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, volume(0.25F), 1.5F);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void finish(OpeningSession session, Node winner) {
        Location visual = visualLocation(winner.stand.getLocation());
        spawnDust(visual, winnerDust(), scaled(20));
        visual.getWorld().spawnParticle(Particle.DRAGON_BREATH, visual, scaled(24), 0.12D, 0.12D, 0.12D, 0.02D);
        visual.getWorld().playSound(visual, Sound.BLOCK_BEACON_ACTIVATE, volume(0.8F), 0.8F);
        visual.getWorld().playSound(visual, Sound.ENTITY_PLAYER_LEVELUP, volume(1.0F), 1.05F);
        removeIfValid(winner.stand);

        session.markRewardGranted();
        plugin.getRewardService().execute(player, session.getFinalReward().getActions());
        plugin.getStats().recordOpening(player, session.getSelectedCase(), session.getFinalReward(), session.isGuaranteedReward());
        plugin.getLeaderboardHolograms().refreshAll();
        plugin.getMessages().send(player, "messages.case-reward-received", "#80ed99Вы получили награду: #ffffff%reward%", "%reward%", session.getFinalReward().getName());
        plugin.getCaseService().completeOpening(runtime);
    }

    private void spawnNodePulse(Node node, int ticks) {
        Location visual = visualLocation(node.stand.getLocation());
        spawnDust(visual, node.winner ? winnerDust() : baseDust(), 1);
        if (ticks % 5 == 0) {
            visual.getWorld().playSound(visual, Sound.BLOCK_NOTE_BLOCK_BIT, volume(0.08F), node.winner ? 2.0F : 1.55F);
        }
    }

    private List<Connection> activeConnections(List<Node> nodes) {
        List<Connection> connections = new ArrayList<>();
        for (int first = 0; first < nodes.size(); first++) {
            Node a = nodes.get(first);
            for (int second = first + 1; second < nodes.size(); second++) {
                Node b = nodes.get(second);
                if (a.baseLocation.distanceSquared(b.baseLocation) > connectionDistanceSquared()) {
                    continue;
                }
                connections.add(new Connection(visualLocation(a.stand.getLocation()), visualLocation(b.stand.getLocation()), first * 31L + second * 17L));
            }
        }
        return connections;
    }

    private ArmorStand spawn(Location location, CaseItem item) {
        ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setInvisible(true);
        stand.setGravity(false);
        stand.setMarker(true);
        stand.setSmall(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.getEquipment().setHelmet(item.getIcon());
        stand.setCustomNameVisible(true);
        stand.customName(plugin.getTextFormatter().asComponent(item.getName()));
        stand.setMetadata(NEURAL_ENTITY_METADATA, new FixedMetadataValue(plugin, runtime.getId()));
        runtime.getSession().trackEntity(stand);
        return stand;
    }

    private void drawLine(Location start, Location end, Particle.DustOptions dust) {
        Vector direction = end.toVector().subtract(start.toVector());
        double distance = direction.length();
        if (distance <= 0.001D) {
            return;
        }

        direction.normalize().multiply(lineStep());
        Location cursor = start.clone();
        for (double moved = 0.0D; moved < distance; moved += lineStep()) {
            spawnDust(cursor, dust, 1);
            cursor.add(direction);
        }
    }

    private void drawImpulse(Location start, Location end, double progress, Particle.DustOptions dust) {
        Location point = interpolate(start, end, Math.max(0.0D, Math.min(1.0D, progress)));
        spawnDust(point, dust, scaled(3));
    }

    private void spawnDust(Location location, Particle.DustOptions dust, int count) {
        if (location.getWorld() == null) {
            return;
        }
        location.getWorld().spawnParticle(Particle.DUST, location, count, 0.03D, 0.03D, 0.03D, 0.0D, dust);
    }

    private Location visualLocation(Location standLocation) {
        return standLocation.clone().add(0.0D, 1.15D, 0.0D);
    }

    private Location interpolate(Location start, Location end, double progress) {
        return start.clone().add(end.toVector().subtract(start.toVector()).multiply(progress));
    }

    private double easeOut(double progress) {
        double value = Math.max(0.0D, Math.min(1.0D, progress));
        return 1.0D - Math.pow(1.0D - value, 3.0D);
    }

    private double pulseProgress(long ticks, long seed) {
        long cycle = Math.max(6, impulseCycleTicks());
        return ((ticks + seed) % cycle) / (double) cycle;
    }

    private void clearNodes(List<Node> nodes) {
        for (Node node : nodes) {
            removeIfValid(node.stand);
        }
    }

    private void removeIfValid(ArmorStand stand) {
        if (stand != null && stand.isValid()) {
            stand.remove();
        }
    }

    private CaseItem fallbackPrize(OpeningSession session) {
        CaseItem prize = plugin.getCaseService().getRandomReward(session.getSelectedCase());
        return prize == null ? session.getFinalReward() : prize;
    }

    private void hideCaseBlock() {
        if (runtime.getLocation().getWorld() != null) {
            runtime.getLocation().getBlock().setType(Material.AIR);
        }
    }

    private boolean isActive(OpeningSession session) {
        return session != null
                && runtime.getSession() == session
                && runtime.isOpening()
                && runtime.getLocation().getWorld() != null
                && player.isOnline()
                && session.isParticipant(player)
                && session.getFinalReward() != null;
    }

    private double random(double min, double max) {
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    private int scaled(int base) {
        double scale = Math.max(0.1D, plugin.getConfig().getDouble("settings.animations.intensity.particles", 1.0D));
        return Math.max(1, (int) Math.round(base * scale));
    }

    private float volume(float base) {
        double scale = Math.max(0.0D, plugin.getConfig().getDouble("settings.animations.intensity.sound", 1.0D));
        return (float) (base * scale);
    }

    private double neuralYOffset() {
        return plugin.getConfig().getDouble("settings.animations.neural.y-offset", 1.2D);
    }

    private double neuralCenterYOffset() {
        return Math.max(0.28D, neuralYOffset() - 0.85D);
    }

    private double spawnRiseDistance() {
        return Math.max(0.2D, plugin.getConfig().getDouble("settings.animations.neural.spawn-rise-distance", 0.8D));
    }

    private int spawnIntervalTicks() {
        return Math.max(1, plugin.getConfig().getInt("settings.animations.neural.spawn-interval-ticks", 2));
    }

    private int spawnDurationTicks() {
        return Math.max(2, plugin.getConfig().getInt("settings.animations.neural.spawn-duration-ticks", 7));
    }

    private int networkTicks() {
        return Math.max(10, plugin.getConfig().getInt("settings.animations.neural.network-ticks", 40));
    }

    private int eliminateIntervalTicks() {
        return Math.max(2, plugin.getConfig().getInt("settings.animations.neural.eliminate-interval-ticks", 10));
    }

    private int winnerHighlightTicks() {
        return Math.max(8, plugin.getConfig().getInt("settings.animations.neural.winner-highlight-ticks", 24));
    }

    private int overloadTicks() {
        return Math.max(4, plugin.getConfig().getInt("settings.animations.neural.overload-ticks", 8));
    }

    private int impulseCycleTicks() {
        return Math.max(6, plugin.getConfig().getInt("settings.animations.neural.impulse-cycle-ticks", 18));
    }

    private double jitterAmount() {
        return Math.max(0.0D, plugin.getConfig().getDouble("settings.animations.neural.jitter", 0.05D));
    }

    private double lineStep() {
        return Math.max(0.05D, plugin.getConfig().getDouble("settings.animations.neural.line-step", 0.2D));
    }

    private double hoverRiseHeight() {
        return Math.max(0.1D, plugin.getConfig().getDouble("settings.animations.neural.hover-rise-height", 0.65D));
    }

    private double hoverSpinPerTick() {
        return plugin.getConfig().getDouble("settings.animations.neural.hover-spin-per-tick", 18.0D);
    }

    private double connectionDistanceSquared() {
        double distance = Math.max(0.4D, plugin.getConfig().getDouble("settings.animations.neural.connection-distance", 2.5D));
        return distance * distance;
    }

    private Particle.DustOptions baseDust() {
        return dust("settings.animations.neural.color.base", 80, 170, 255, 1.15D);
    }

    private Particle.DustOptions lineDust() {
        return dust("settings.animations.neural.color.line", 110, 210, 255, 0.95D);
    }

    private Particle.DustOptions errorDust() {
        return dust("settings.animations.neural.color.error", 255, 90, 90, 1.2D);
    }

    private Particle.DustOptions winnerDust() {
        return dust("settings.animations.neural.color.winner", 255, 215, 90, 1.3D);
    }

    private Particle.DustOptions dust(String path, int red, int green, int blue, double size) {
        return new Particle.DustOptions(
                Color.fromRGB(
                        clampColor(plugin.getConfig().getInt(path + ".red", red)),
                        clampColor(plugin.getConfig().getInt(path + ".green", green)),
                        clampColor(plugin.getConfig().getInt(path + ".blue", blue))
                ),
                (float) Math.max(0.5D, plugin.getConfig().getDouble(path + ".size", size))
        );
    }

    private int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static final class Node {
        private final ArmorStand stand;
        private final Location baseLocation;
        private final boolean winner;
        private final int layer;

        private Node(ArmorStand stand, Location baseLocation, boolean winner, int layer) {
            this.stand = stand;
            this.baseLocation = baseLocation;
            this.winner = winner;
            this.layer = layer;
        }
    }

    private static final class Connection {
        private final Location from;
        private final Location to;
        private final long seed;

        private Connection(Location from, Location to, long seed) {
            this.from = from;
            this.to = to;
            this.seed = seed;
        }
    }
}
