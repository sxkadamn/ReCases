package net.recases.animations.opening;

import net.recases.app.PluginContext;
import net.recases.management.CaseItem;
import net.recases.management.OpeningSession;
import net.recases.runtime.CaseRuntime;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public class ItemDisplayRollOpeningAnimation implements OpeningAnimation {

    public static final String ITEMDISPLAY_ROLL_METADATA = "case_itemdisplay_roll";

    private final PluginContext plugin;
    private final Player player;
    private final CaseRuntime runtime;
    private final AnimationPerformance performance;

    public ItemDisplayRollOpeningAnimation(PluginContext plugin, Player player, CaseRuntime runtime) {
        this.plugin = plugin;
        this.player = player;
        this.runtime = runtime;
        this.performance = AnimationPerformance.create(plugin);
    }

    @Override
    public boolean play() {
        OpeningSession session = runtime.getSession();
        if (!isActive(session)) {
            return false;
        }

        Config cfg = Config.load(plugin.getConfig(), runtime, session);
        Basis basis = Basis.capture(player, cfg.distance, cfg.heightOffset);
        DisplayRig rig = spawnRig(session, cfg, basis);
        if (rig == null) {
            plugin.getCaseService().abortOpening(runtime, true);
            return false;
        }

        runtime.removeHologram();
        rig.item.setItemStack(session.getFinalReward().getIcon());
        playSound(cfg.startSound, 0.95F, 1.08F);
        startRolling(session, cfg, rig, basis);
        return true;
    }

    private DisplayRig spawnRig(OpeningSession session, Config cfg, Basis basis) {
        Location center = basis.center();
        if (center.getWorld() == null) {
            return null;
        }

        TextDisplay panel = createTextDisplay(session, basis.offset(0.0D, 0.0D, -0.06D), cfg.panelWidth);
        panel.setBackgroundColor(cfg.panelBackground);
        panel.setShadowed(false);
        panel.text(plugin.getTextFormatter().asComponent(" \n \n "));
        panel.setTextOpacity((byte) 0);

        TextDisplay accent = createTextDisplay(session, basis.offset(0.0D, 0.93D, -0.05D), cfg.panelWidth);
        accent.setBackgroundColor(cfg.accentBackground);
        accent.text(plugin.getTextFormatter().asComponent(format(cfg.accentLabel, session, session.getFinalReward(), cfg.preset)));

        TextDisplay status = createTextDisplay(session, basis.offset(0.0D, 0.56D, 0.03D), cfg.panelWidth);
        status.setBackgroundColor(cfg.statusBackground);

        TextDisplay name = createTextDisplay(session, basis.offset(0.0D, -0.62D, 0.03D), cfg.panelWidth);
        name.setBackgroundColor(cfg.nameBackground);

        ItemDisplay item = createItemDisplay(session, center, cfg.viewRange);
        return new DisplayRig(panel, accent, status, name, item);
    }

    private void startRolling(OpeningSession session, Config cfg, DisplayRig rig, Basis startBasis) {
        List<CaseItem> sequence = buildPreviewSequence(session, cfg.scrollSteps);
        new BukkitRunnable() {
            private int tick;
            private int lastStep = -1;
            private Basis basis = startBasis;

            @Override
            public void run() {
                if (!isRigActive(session, rig)) {
                    cancel();
                    plugin.getCaseService().abortOpening(runtime, true);
                    return;
                }

                basis = cfg.nextBasis(player, basis);
                double progress = Math.min(1.0D, tick / (double) cfg.totalTicks);
                double eased = reelEase(progress, cfg.easingPower);
                int step = Math.min(cfg.scrollSteps, (int) Math.floor(cfg.scrollSteps * eased));

                CaseItem preview = step >= cfg.scrollSteps
                        ? session.getFinalReward()
                        : sequence.get(Math.min(sequence.size() - 1, step));
                drawRollingFrame(session, cfg, rig, basis, preview, tick, progress);

                if (step != lastStep) {
                    double phase = step / (double) Math.max(1, cfg.scrollSteps);
                    playSound(phase < 0.68D ? cfg.tickFastSound : cfg.tickSlowSound, 0.42F, phase < 0.68D ? 1.28F : 0.92F);
                    lastStep = step;
                }

                if (tick % 2 == 0) {
                    double particleScale = 0.45D + ((1.0D - progress) * 0.55D);
                    spawnParticles(cfg.rollingParticle, basis.offset(0.0D, 0.05D, 0.0D), Math.max(1, (int) Math.round(cfg.rollingParticleCount * particleScale)));
                }

                if (tick++ <= cfg.totalTicks) {
                    return;
                }

                cancel();
                startReveal(session, cfg, rig, basis);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void startReveal(OpeningSession session, Config cfg, DisplayRig rig, Basis startBasis) {
        playSound(cfg.stopSound, 0.75F, 1.0F);

        new BukkitRunnable() {
            private int tick;
            private Basis basis = startBasis;

            @Override
            public void run() {
                if (!isRigActive(session, rig)) {
                    cancel();
                    plugin.getCaseService().abortOpening(runtime, true);
                    return;
                }

                basis = cfg.nextBasis(player, basis);
                double progress = Math.min(1.0D, tick / (double) cfg.revealTicks);
                double eased = easeOut(progress, 2.4D);
                double settle = easeOut(progress, 1.6D);
                double zoom = cfg.zoomDistance * eased;
                double shake = tick < cfg.shakeTicks
                        ? cfg.shakeAmplitude * Math.pow(1.0D - tick / (double) Math.max(1, cfg.shakeTicks), 2.0D)
                        : 0.0D;
                double jitterX = randomInRange(shake);
                double jitterY = randomInRange(shake * 0.35D);

                rig.panel.teleport(basis.offset(jitterX * 0.25D, jitterY * 0.18D, -0.06D));
                rig.accent.teleport(basis.offset(jitterX * 0.2D, 0.93D + jitterY * 0.15D, -0.05D));
                rig.status.teleport(basis.offset(jitterX, 0.56D + jitterY, zoom));
                rig.name.teleport(basis.offset(jitterX, -0.62D + jitterY, zoom));
                rig.status.text(plugin.getTextFormatter().asComponent(format(cfg.revealText(session), session, session.getFinalReward(), cfg.preset)));
                rig.name.text(plugin.getTextFormatter().asComponent(format(cfg.nameText, session, session.getFinalReward(), cfg.preset)));
                rig.panel.setBackgroundColor(withAlpha(cfg.panelBackground, (int) lerp(160.0D, 230.0D, settle)));
                rig.accent.setBackgroundColor(withAlpha(cfg.accentBackground, (int) lerp(90.0D, 180.0D, settle)));
                rig.status.setBackgroundColor(withAlpha(cfg.statusBackground, (int) lerp(120.0D, 180.0D, settle)));
                rig.name.setBackgroundColor(withAlpha(cfg.nameBackground, (int) lerp(130.0D, 200.0D, settle)));

                rig.item.setItemStack(session.getFinalReward().getIcon());
                double pulse = Math.sin(progress * Math.PI * 3.0D) * cfg.revealPulseAmplitude * (1.0D - progress);
                double scale = lerp(cfg.itemScale, cfg.revealScale, eased) + pulse;
                double zoomPunch = Math.sin(Math.min(1.0D, progress * 1.15D) * Math.PI) * cfg.revealZoomPunch;
                double yLift = lerp(0.0D, cfg.revealLift, eased) + (Math.sin(progress * Math.PI * 2.0D) * 0.02D * (1.0D - progress));
                double spin = lerp(cfg.spinPerTick * 1.45D, cfg.revealSettleSpin, eased) * tick;
                spin(rig.item, basis.offset(jitterX, jitterY + yLift, zoom + zoomPunch), scale, spin, lerp(cfg.baseTiltDegrees, cfg.revealTiltDegrees, eased));

                spawnParticles(cfg.revealParticle(session), basis.offset(jitterX, 0.05D + jitterY, zoom), cfg.revealParticleCount);
                if (tick == 0) {
                    playSound(cfg.winSound(session), 1.0F, 1.0F);
                }

                if (tick++ <= cfg.revealTicks) {
                    return;
                }

                cancel();
                plugin.getOpeningResults().complete(player, runtime, session, session.getFinalReward());
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void drawRollingFrame(OpeningSession session, Config cfg, DisplayRig rig, Basis basis, CaseItem preview, int tick, double progress) {
        rig.panel.teleport(basis.offset(0.0D, 0.0D, -0.06D));
        rig.accent.teleport(basis.offset(0.0D, 0.93D, -0.05D));
        rig.status.teleport(basis.offset(0.0D, 0.56D, 0.03D));
        rig.name.teleport(basis.offset(0.0D, -0.62D, 0.03D));
        rig.status.text(plugin.getTextFormatter().asComponent(format(cfg.rollingText, session, preview, cfg.preset)));
        rig.name.text(plugin.getTextFormatter().asComponent(format(cfg.nameText, session, preview, cfg.preset)));
        rig.panel.setBackgroundColor(withAlpha(cfg.panelBackground, (int) lerp(145.0D, 185.0D, 1.0D - progress)));
        rig.accent.setBackgroundColor(withAlpha(cfg.accentBackground, (int) lerp(75.0D, 125.0D, 1.0D - progress)));
        rig.status.setBackgroundColor(withAlpha(cfg.statusBackground, (int) lerp(105.0D, 150.0D, 1.0D - progress)));
        rig.name.setBackgroundColor(withAlpha(cfg.nameBackground, (int) lerp(115.0D, 165.0D, 1.0D - progress)));

        rig.item.setItemStack(preview.getIcon());
        double calm = 1.0D - progress;
        double bob = Math.sin(tick * cfg.bobFrequency) * cfg.bobAmplitude * (0.55D + calm);
        double swayX = Math.sin((tick * 0.09D) + 0.75D) * cfg.driftAmplitude * calm;
        double scale = cfg.itemScale + (Math.sin(tick * 0.18D) * cfg.idlePulseAmplitude * (0.35D + calm));
        double spin = tick * lerp(cfg.spinPerTick, cfg.revealSettleSpin, progress);
        double tilt = cfg.baseTiltDegrees + (Math.sin(tick * 0.11D) * cfg.tiltSwingAmplitude * calm);
        spin(rig.item, basis.offset(swayX, bob, 0.0D), scale, spin, tilt);
    }

    private TextDisplay createTextDisplay(OpeningSession session, Location location, int width) {
        TextDisplay display = (TextDisplay) location.getWorld().spawnEntity(location, EntityType.TEXT_DISPLAY);
        applyMeta(session, display);
        display.setBillboard(Display.Billboard.CENTER);
        display.setInterpolationDuration(3);
        display.setInterpolationDelay(0);
        display.setDefaultBackground(false);
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setSeeThrough(true);
        display.setShadowed(false);
        display.setLineWidth(width);
        return display;
    }

    private ItemDisplay createItemDisplay(OpeningSession session, Location location, float viewRange) {
        ItemDisplay display = (ItemDisplay) location.getWorld().spawnEntity(location, EntityType.ITEM_DISPLAY);
        applyMeta(session, display);
        display.setBillboard(Display.Billboard.CENTER);
        display.setInterpolationDuration(3);
        display.setInterpolationDelay(0);
        display.setViewRange(viewRange);
        return display;
    }

    private void spin(ItemDisplay display, Location location, double scale, double spin, double tilt) {
        display.teleport(location);
        display.setTransformation(new Transformation(
                new Vector3f(),
                new Quaternionf().rotateX((float) Math.toRadians(tilt)).rotateY((float) Math.toRadians(spin)),
                new Vector3f((float) scale, (float) scale, (float) scale),
                new Quaternionf()
        ));
    }

    private void applyMeta(OpeningSession session, Entity entity) {
        entity.setMetadata(ITEMDISPLAY_ROLL_METADATA, new FixedMetadataValue(plugin, runtime.getId()));
        entity.setVisibleByDefault(false);
        entity.setInvulnerable(true);
        player.showEntity(plugin, entity);
        session.trackEntity(entity);
    }

    private void spawnParticles(Particle particle, Location location, int count) {
        player.spawnParticle(particle, location, scaled(count), 0.28D, 0.24D, 0.28D, 0.02D);
    }

    private void playSound(Sound sound, float volume, float pitch) {
        if (sound != null) {
            player.playSound(player.getLocation(), sound, performance.volume(volume), pitch);
        }
    }

    private String format(String text, OpeningSession session, CaseItem reward, String preset) {
        String value = text == null ? "" : text;
        return value.replace("%player%", player.getName())
                .replace("%case%", session.getSelectedCase())
                .replace("%instance%", runtime.getId())
                .replace("%preset%", preset)
                .replace("%reward%", reward == null ? "" : reward.getName());
    }

    private boolean isRigActive(OpeningSession session, DisplayRig rig) {
        return isActive(session)
                && rig.panel.isValid()
                && rig.accent.isValid()
                && rig.status.isValid()
                && rig.name.isValid()
                && rig.item.isValid();
    }

    private boolean isActive(OpeningSession session) {
        return session != null
                && runtime.getSession() == session
                && runtime.isOpening()
                && player.isOnline()
                && session.isParticipant(player)
                && session.getFinalReward() != null;
    }

    private double easeOut(double progress, double power) {
        double clamped = Math.max(0.0D, Math.min(1.0D, progress));
        return 1.0D - Math.pow(1.0D - clamped, power);
    }

    private double reelEase(double progress, double power) {
        double clamped = Math.max(0.0D, Math.min(1.0D, progress));
        double staged;
        if (clamped < 0.18D) {
            staged = easeOut(clamped / 0.18D, 1.18D) * 0.20D;
        } else if (clamped < 0.72D) {
            staged = 0.20D + (smoothstep((clamped - 0.18D) / 0.54D) * 0.55D);
        } else {
            staged = 0.75D + (easeOut((clamped - 0.72D) / 0.28D, power + 0.35D) * 0.25D);
        }
        return Math.max(0.0D, Math.min(1.0D, staged));
    }

    private double smoothstep(double value) {
        double clamped = Math.max(0.0D, Math.min(1.0D, value));
        return clamped * clamped * (3.0D - (2.0D * clamped));
    }

    private double randomInRange(double range) {
        return range <= 0.0D ? 0.0D : ThreadLocalRandom.current().nextDouble(-range, range);
    }

    private double lerp(double start, double end, double factor) {
        double clamped = Math.max(0.0D, Math.min(1.0D, factor));
        return start + ((end - start) * clamped);
    }

    private Color withAlpha(Color color, int alpha) {
        return Color.fromARGB(
                Math.max(0, Math.min(255, alpha)),
                color.getRed(),
                color.getGreen(),
                color.getBlue()
        );
    }

    private List<CaseItem> buildPreviewSequence(OpeningSession session, int steps) {
        List<CaseItem> sequence = new ArrayList<>(Math.max(steps, 1));
        CaseItem previous = null;
        for (int i = 0; i < Math.max(steps, 1); i++) {
            CaseItem reward = pickDifferentReward(session, previous);
            sequence.add(reward);
            previous = reward;
        }
        return sequence;
    }

    private CaseItem pickDifferentReward(OpeningSession session, CaseItem previous) {
        CaseItem fallback = session.getFinalReward();
        CaseItem candidate = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            candidate = plugin.getCaseService().getRandomReward(session.getSelectedCase());
            if (candidate == null) {
                candidate = fallback;
            }
            if (previous == null || !previous.getId().equalsIgnoreCase(candidate.getId())) {
                return candidate;
            }
        }
        return candidate == null ? fallback : candidate;
    }

    private int scaled(int base) {
        return performance.particles(base);
    }

    private record DisplayRig(TextDisplay panel, TextDisplay accent, TextDisplay status, TextDisplay name, ItemDisplay item) {
    }

    private record Basis(Location center, Vector forward, Vector right) {
        private static Basis capture(Player player, double distance, double heightOffset) {
            Location eye = player.getEyeLocation();
            Vector forward = eye.getDirection().clone().normalize();
            Vector right = new Vector(0.0D, 1.0D, 0.0D).crossProduct(forward.clone());
            if (right.lengthSquared() < 0.0001D) {
                right = new Vector(1.0D, 0.0D, 0.0D);
            } else {
                right.normalize();
            }
            return new Basis(eye.clone().add(forward.clone().multiply(distance)).add(0.0D, heightOffset, 0.0D), forward, right);
        }

        private Basis lerp(Basis target, double factor) {
            double f = Math.max(0.0D, Math.min(1.0D, factor));
            Location interpolated = center.clone().add(target.center.clone().subtract(center).toVector().multiply(f));
            Vector newForward = forward.clone().multiply(1.0D - f).add(target.forward.clone().multiply(f));
            Vector newRight = right.clone().multiply(1.0D - f).add(target.right.clone().multiply(f));
            if (newForward.lengthSquared() > 0.0001D) {
                newForward.normalize();
            }
            if (newRight.lengthSquared() > 0.0001D) {
                newRight.normalize();
            }
            return new Basis(interpolated, newForward, newRight);
        }

        private Location offset(double x, double y, double z) {
            return center.clone().add(right.clone().multiply(x)).add(0.0D, y, 0.0D).add(forward.clone().multiply(z));
        }
    }

    private static final class Config {
        private final String preset;
        private final String positionMode;
        private final double distance;
        private final double heightOffset;
        private final double smoothFactor;
        private final int totalTicks;
        private final int revealTicks;
        private final int scrollSteps;
        private final double easingPower;
        private final double itemScale;
        private final double revealScale;
        private final double zoomDistance;
        private final double shakeAmplitude;
        private final int shakeTicks;
        private final double baseTiltDegrees;
        private final double revealTiltDegrees;
        private final double spinPerTick;
        private final double revealSettleSpin;
        private final double bobAmplitude;
        private final double bobFrequency;
        private final double driftAmplitude;
        private final double idlePulseAmplitude;
        private final double tiltSwingAmplitude;
        private final double revealLift;
        private final double revealPulseAmplitude;
        private final double revealZoomPunch;
        private final float viewRange;
        private final int panelWidth;
        private final String accentLabel;
        private final String rollingText;
        private final String nameText;
        private final String revealCommonText;
        private final String revealRareText;
        private final String revealGuaranteedText;
        private final Sound startSound;
        private final Sound tickFastSound;
        private final Sound tickSlowSound;
        private final Sound stopSound;
        private final Sound commonWinSound;
        private final Sound rareWinSound;
        private final Sound guaranteedWinSound;
        private final Particle rollingParticle;
        private final Particle commonParticle;
        private final Particle rareParticle;
        private final Particle guaranteedParticle;
        private final int rollingParticleCount;
        private final int revealParticleCount;
        private final Color panelBackground;
        private final Color accentBackground;
        private final Color statusBackground;
        private final Color nameBackground;

        private Config(String preset, String positionMode, double distance, double heightOffset, double smoothFactor,
                       int totalTicks, int revealTicks, int scrollSteps, double easingPower, double itemScale,
                       double revealScale, double zoomDistance, double shakeAmplitude, int shakeTicks,
                       double baseTiltDegrees, double revealTiltDegrees, double spinPerTick, double revealSettleSpin,
                       double bobAmplitude, double bobFrequency, double driftAmplitude, double idlePulseAmplitude,
                       double tiltSwingAmplitude, double revealLift, double revealPulseAmplitude, double revealZoomPunch, float viewRange, int panelWidth,
                       String accentLabel, String rollingText, String nameText, String revealCommonText,
                       String revealRareText, String revealGuaranteedText, Sound startSound, Sound tickFastSound,
                       Sound tickSlowSound, Sound stopSound, Sound commonWinSound, Sound rareWinSound,
                       Sound guaranteedWinSound, Particle rollingParticle, Particle commonParticle,
                       Particle rareParticle, Particle guaranteedParticle, int rollingParticleCount,
                       int revealParticleCount, Color panelBackground, Color accentBackground, Color statusBackground, Color nameBackground) {
            this.preset = preset;
            this.positionMode = positionMode;
            this.distance = distance;
            this.heightOffset = heightOffset;
            this.smoothFactor = smoothFactor;
            this.totalTicks = totalTicks;
            this.revealTicks = revealTicks;
            this.scrollSteps = scrollSteps;
            this.easingPower = easingPower;
            this.itemScale = itemScale;
            this.revealScale = revealScale;
            this.zoomDistance = zoomDistance;
            this.shakeAmplitude = shakeAmplitude;
            this.shakeTicks = shakeTicks;
            this.baseTiltDegrees = baseTiltDegrees;
            this.revealTiltDegrees = revealTiltDegrees;
            this.spinPerTick = spinPerTick;
            this.revealSettleSpin = revealSettleSpin;
            this.bobAmplitude = bobAmplitude;
            this.bobFrequency = bobFrequency;
            this.driftAmplitude = driftAmplitude;
            this.idlePulseAmplitude = idlePulseAmplitude;
            this.tiltSwingAmplitude = tiltSwingAmplitude;
            this.revealLift = revealLift;
            this.revealPulseAmplitude = revealPulseAmplitude;
            this.revealZoomPunch = revealZoomPunch;
            this.viewRange = viewRange;
            this.panelWidth = panelWidth;
            this.accentLabel = accentLabel;
            this.rollingText = rollingText;
            this.nameText = nameText;
            this.revealCommonText = revealCommonText;
            this.revealRareText = revealRareText;
            this.revealGuaranteedText = revealGuaranteedText;
            this.startSound = startSound;
            this.tickFastSound = tickFastSound;
            this.tickSlowSound = tickSlowSound;
            this.stopSound = stopSound;
            this.commonWinSound = commonWinSound;
            this.rareWinSound = rareWinSound;
            this.guaranteedWinSound = guaranteedWinSound;
            this.rollingParticle = rollingParticle;
            this.commonParticle = commonParticle;
            this.rareParticle = rareParticle;
            this.guaranteedParticle = guaranteedParticle;
            this.rollingParticleCount = rollingParticleCount;
            this.revealParticleCount = revealParticleCount;
            this.panelBackground = panelBackground;
            this.accentBackground = accentBackground;
            this.statusBackground = statusBackground;
            this.nameBackground = nameBackground;
        }

        private static Config load(FileConfiguration config, CaseRuntime runtime, OpeningSession session) {
            String preset = stringValue(config, runtime, session, "", "preset", "tech").toLowerCase(Locale.ROOT);
            return new Config(
                    preset,
                    stringValue(config, runtime, session, preset, "position.mode", "smooth-follow"),
                    doubleValue(config, runtime, session, preset, "position.distance", doubleValue(config, runtime, session, preset, "distance", 2.2D)),
                    doubleValue(config, runtime, session, preset, "position.height-offset", doubleValue(config, runtime, session, preset, "height-offset", -0.2D)),
                    doubleValue(config, runtime, session, preset, "position.smooth-factor", 0.22D),
                    Math.max(35, intValue(config, runtime, session, preset, "total-ticks", 72)),
                    Math.max(18, intValue(config, runtime, session, preset, "reveal-ticks", 28)),
                    Math.max(12, intValue(config, runtime, session, preset, "scroll-steps", 30)),
                    Math.max(2.0D, doubleValue(config, runtime, session, preset, "easing-power", 4.0D)),
                    Math.max(0.6D, doubleValue(config, runtime, session, preset, "item-scale", 1.3D)),
                    Math.max(0.8D, doubleValue(config, runtime, session, preset, "reveal-scale", 1.85D)),
                    Math.max(0.0D, doubleValue(config, runtime, session, preset, "zoom-distance", 0.38D)),
                    Math.max(0.0D, doubleValue(config, runtime, session, preset, "shake-amplitude", 0.06D)),
                    Math.max(0, intValue(config, runtime, session, preset, "shake-ticks", 10)),
                    doubleValue(config, runtime, session, preset, "base-tilt-degrees", 12.0D),
                    doubleValue(config, runtime, session, preset, "reveal-tilt-degrees", 4.0D),
                    doubleValue(config, runtime, session, preset, "spin-per-tick", 14.0D),
                    doubleValue(config, runtime, session, preset, "reveal-settle-spin", 4.0D),
                    doubleValue(config, runtime, session, preset, "motion.bob-amplitude", 0.035D),
                    doubleValue(config, runtime, session, preset, "motion.bob-frequency", 0.22D),
                    doubleValue(config, runtime, session, preset, "motion.drift-amplitude", 0.03D),
                    doubleValue(config, runtime, session, preset, "motion.idle-pulse-amplitude", 0.045D),
                    doubleValue(config, runtime, session, preset, "motion.tilt-swing-amplitude", 4.5D),
                    doubleValue(config, runtime, session, preset, "motion.reveal-lift", 0.1D),
                    doubleValue(config, runtime, session, preset, "motion.reveal-pulse-amplitude", 0.12D),
                    doubleValue(config, runtime, session, preset, "motion.reveal-zoom-punch", 0.18D),
                    (float) doubleValue(config, runtime, session, preset, "view-range", 0.65D),
                    Math.max(120, intValue(config, runtime, session, preset, "panel-width", 260)),
                    stringValue(config, runtime, session, preset, "accent-label", "#f8f1df&lКЕЙС: %case%"),
                    stringValue(config, runtime, session, preset, "texts.rolling", "#ffd166&lПРОКРУТКА..."),
                    stringValue(config, runtime, session, preset, "texts.name", "%reward%"),
                    stringValue(config, runtime, session, preset, "texts.reveal-common", "#80ed99&lВАШ ПРИЗ"),
                    stringValue(config, runtime, session, preset, "texts.reveal-rare", "#ff922b&lРЕДКИЙ ПРИЗ!"),
                    stringValue(config, runtime, session, preset, "texts.reveal-guaranteed", "#ffd166&lГАРАНТ!"),
                    soundValue(stringValue(config, runtime, session, preset, "sounds.start", "BLOCK_ENDER_CHEST_OPEN"), Sound.BLOCK_ENDER_CHEST_OPEN),
                    soundValue(stringValue(config, runtime, session, preset, "sounds.tick-fast", "UI_BUTTON_CLICK"), Sound.UI_BUTTON_CLICK),
                    soundValue(stringValue(config, runtime, session, preset, "sounds.tick-slow", "BLOCK_NOTE_BLOCK_HAT"), Sound.BLOCK_NOTE_BLOCK_HAT),
                    soundValue(stringValue(config, runtime, session, preset, "sounds.stop", "BLOCK_AMETHYST_CLUSTER_BREAK"), Sound.BLOCK_AMETHYST_CLUSTER_BREAK),
                    soundValue(stringValue(config, runtime, session, preset, "sounds.win-common", "ENTITY_PLAYER_LEVELUP"), Sound.ENTITY_PLAYER_LEVELUP),
                    soundValue(stringValue(config, runtime, session, preset, "sounds.win-rare", "UI_TOAST_CHALLENGE_COMPLETE"), Sound.UI_TOAST_CHALLENGE_COMPLETE),
                    soundValue(stringValue(config, runtime, session, preset, "sounds.win-guaranteed", "ITEM_TOTEM_USE"), Sound.ITEM_TOTEM_USE),
                    ParticleAnimationSupport.resolveParticle(stringValue(config, runtime, session, preset, "particles.rolling", "END_ROD"), Particle.END_ROD),
                    ParticleAnimationSupport.resolveParticle(stringValue(config, runtime, session, preset, "particles.common", "END_ROD"), Particle.END_ROD),
                    ParticleAnimationSupport.resolveParticle(stringValue(config, runtime, session, preset, "particles.rare", "DRAGON_BREATH"), Particle.DRAGON_BREATH),
                    ParticleAnimationSupport.resolveParticle(stringValue(config, runtime, session, preset, "particles.guaranteed", "TOTEM_OF_UNDYING"), Particle.TOTEM_OF_UNDYING),
                    Math.max(1, intValue(config, runtime, session, preset, "particle-counts.rolling", 6)),
                    Math.max(2, intValue(config, runtime, session, preset, "particle-counts.reveal", 18)),
                    parseColor(stringValue(config, runtime, session, preset, "panel-background", "#CC08131D"), 180),
                    parseColor(stringValue(config, runtime, session, preset, "accent-background", "#6639A0FF"), 110),
                    parseColor(stringValue(config, runtime, session, preset, "status-background", "#B30B1118"), 150),
                    parseColor(stringValue(config, runtime, session, preset, "name-background", "#C40B1118"), 165)
            );
        }

        private Basis nextBasis(Player player, Basis current) {
            Basis target = Basis.capture(player, distance, heightOffset);
            return switch (positionMode.toLowerCase(Locale.ROOT)) {
                case "locked" -> current;
                case "follow" -> target;
                default -> current.lerp(target, smoothFactor);
            };
        }

        private String revealText(OpeningSession session) {
            if (session.isGuaranteedReward()) {
                return revealGuaranteedText;
            }
            return session.getFinalReward().isRare() ? revealRareText : revealCommonText;
        }

        private Particle revealParticle(OpeningSession session) {
            if (session.isGuaranteedReward()) {
                return guaranteedParticle;
            }
            return session.getFinalReward().isRare() ? rareParticle : commonParticle;
        }

        private Sound winSound(OpeningSession session) {
            if (session.isGuaranteedReward()) {
                return guaranteedWinSound;
            }
            return session.getFinalReward().isRare() ? rareWinSound : commonWinSound;
        }

        private static String stringValue(FileConfiguration config, CaseRuntime runtime, OpeningSession session, String preset, String key, String fallback) {
            String instancePath = "cases.instances." + runtime.getId() + ".animations.item-display-roll." + key;
            if (config.isSet(instancePath)) {
                return config.getString(instancePath, fallback);
            }

            String profilePath = "profiles." + session.getSelectedCase() + ".animations.item-display-roll." + key;
            if (config.isSet(profilePath)) {
                return config.getString(profilePath, fallback);
            }

            if (!preset.isBlank()) {
                String presetPath = "settings.animations.item-display-roll.presets." + preset + "." + key;
                if (config.isSet(presetPath)) {
                    return config.getString(presetPath, fallback);
                }
            }

            return config.getString("settings.animations.item-display-roll." + key, fallback);
        }

        private static int intValue(FileConfiguration config, CaseRuntime runtime, OpeningSession session, String preset, String key, int fallback) {
            try {
                return Integer.parseInt(stringValue(config, runtime, session, preset, key, Integer.toString(fallback)));
            } catch (NumberFormatException exception) {
                return fallback;
            }
        }

        private static double doubleValue(FileConfiguration config, CaseRuntime runtime, OpeningSession session, String preset, String key, double fallback) {
            try {
                return Double.parseDouble(stringValue(config, runtime, session, preset, key, Double.toString(fallback)));
            } catch (NumberFormatException exception) {
                return fallback;
            }
        }

        private static Sound soundValue(String raw, Sound fallback) {
            try {
                return Sound.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (Exception exception) {
                return fallback;
            }
        }

        private static Color parseColor(String raw, int alpha) {
            try {
                if (raw.startsWith("#") && raw.length() == 7) {
                    int rgb = Integer.parseInt(raw.substring(1), 16);
                    return Color.fromARGB(alpha, (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255);
                }
                if (raw.startsWith("#") && raw.length() == 9) {
                    int argb = (int) Long.parseLong(raw.substring(1), 16);
                    return Color.fromARGB((argb >> 24) & 255, (argb >> 16) & 255, (argb >> 8) & 255, argb & 255);
                }
            } catch (Exception ignored) {
            }
            return Color.fromARGB(alpha, 8, 19, 29);
        }
    }
}
