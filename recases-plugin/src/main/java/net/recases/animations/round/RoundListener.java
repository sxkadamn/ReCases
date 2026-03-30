package net.recases.animations.round;

import net.recases.animations.opening.AbstractEntityOpeningAnimation;
import net.recases.app.PluginContext;
import net.recases.management.CaseItem;
import net.recases.management.OpeningSession;
import net.recases.protocollib.hologram.Hologram;
import net.recases.runtime.CaseRuntime;
import net.recases.runtime.registry.EntityRegistry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

public class RoundListener implements Listener {

    private final PluginContext plugin;
    private final EntityRegistry entityRegistry;

    public RoundListener(PluginContext plugin, EntityRegistry entityRegistry) {
        this.plugin = plugin;
        this.entityRegistry = entityRegistry;
    }

    @EventHandler
    public void onBlockInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || !block.hasMetadata("case_open_chest")) {
            return;
        }

        String runtimeId = block.getMetadata("case_open_chest").get(0).asString();
        CaseRuntime runtime = plugin.getCaseService().getRuntime(runtimeId);
        OpeningSession session = runtime == null ? null : runtime.getSession();
        if (runtime == null || session == null || !runtime.isOpening()) {
            return;
        }

        Player player = event.getPlayer();
        if (!session.isParticipant(player)) {
            event.setCancelled(true);
            plugin.getMessages().send(player, "messages.case-opened-by-other", "#ff6b6bЭтот кейс сейчас открывает другой игрок.");
            return;
        }
        if (!session.isTrackedChest(block.getLocation()) || !session.markOpened(block.getLocation())) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        reveal(player, runtime, session, block.getLocation().toCenterLocation(), () -> block.setType(Material.AIR));
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        handleEntitySelection(event.getPlayer(), event.getRightClicked(), event);
    }

    @EventHandler
    public void onEntityInteractAt(PlayerInteractAtEntityEvent event) {
        handleEntitySelection(event.getPlayer(), event.getRightClicked(), event);
    }

    @EventHandler
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        handleEntitySelection(event.getPlayer(), event.getRightClicked(), event);
    }

    private void handleEntitySelection(Player player, Entity clickedEntity, Cancellable event) {
        if (!clickedEntity.hasMetadata(AbstractEntityOpeningAnimation.ENTITY_TARGET_METADATA)) {
            return;
        }

        String runtimeId = clickedEntity.getMetadata(AbstractEntityOpeningAnimation.ENTITY_TARGET_METADATA).get(0).asString();
        CaseRuntime runtime = plugin.getCaseService().getRuntime(runtimeId);
        OpeningSession session = runtime == null ? null : runtime.getSession();
        if (runtime == null || session == null || !runtime.isOpening()) {
            event.setCancelled(true);
            return;
        }

        if (!session.isParticipant(player)) {
            event.setCancelled(true);
            plugin.getMessages().send(player, "messages.case-opened-by-other", "#ff6b6bЭтот кейс сейчас открывает другой игрок.");
            return;
        }
        if (!session.isTrackedEntity(clickedEntity) || !session.markOpened(clickedEntity)) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        reveal(player, runtime, session, clickedEntity.getLocation().clone().add(0.0, 0.8, 0.0), () -> {
            entityRegistry.removeEntity(clickedEntity);
            clickedEntity.remove();
        });
    }

    private void reveal(Player player, CaseRuntime runtime, OpeningSession session, Location center, Runnable removeTarget) {
        World world = center.getWorld();
        if (runtime.getLocation().getWorld() == null || world == null) {
            plugin.getCaseService().abortOpening(runtime, true);
            return;
        }

        boolean finalPick = session.getOpenedCount() >= session.getRequiredSelections();
        CaseItem reward = session.getFinalReward();
        if (reward == null) {
            plugin.getMessages().send(player, "messages.case-reward-failed", "#ff6b6bНе удалось выбрать награду. Проверьте конфиг кейса.");
            plugin.getCaseService().abortOpening(runtime, true);
            return;
        }

        removeTarget.run();

        if (!finalPick) {
            playMinorReveal(world, center, session);
            spawnRewardDisplay(runtime, session, player, reward, center, 18L, false);
            return;
        }

        long revealLead = playFinalReveal(world, center, session);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (runtime.getSession() != session || !runtime.isOpening()) {
                    return;
                }
                spawnRewardDisplay(runtime, session, player, reward, center, 36L, true);
            }
        }.runTaskLater(plugin, revealLead);
    }

    private void spawnRewardDisplay(CaseRuntime runtime, OpeningSession session, Player player, CaseItem reward, Location center, long displayTicks, boolean finalPick) {
        World world = center.getWorld();
        if (world == null) {
            plugin.getCaseService().abortOpening(runtime, true);
            return;
        }

        ArmorStand stand = (ArmorStand) world.spawnEntity(center.clone().add(0.0, 0.35, 0.0), EntityType.ARMOR_STAND);
        stand.setInvisible(true);
        stand.setMarker(true);
        stand.setGravity(false);
        stand.setSmall(true);
        stand.setMetadata("armor_head", new FixedMetadataValue(plugin, runtime.getId()));
        stand.setHelmet(reward.getIcon());
        entityRegistry.addArmorStand(stand);

        Hologram hologram = new Hologram(plugin, reward.getName(), center.clone().add(0.0, 1.8, 0.0));
        hologram.spawn();
        entityRegistry.addHologram(hologram);

        world.spawnParticle(isPremium(session) ? Particle.GLOW : Particle.EXPLOSION, center, scaled(25), 0.3, 0.3, 0.3, 0.02);
        world.playSound(center, isPremium(session) ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_GENERIC_EXPLODE, volume(1.0F), finalPick ? 0.95F : 1.2F);
        playRewardChord(world, center, session);
        startWinnerMotion(stand, hologram, center, displayTicks);

        new BukkitRunnable() {
            @Override
            public void run() {
                stand.remove();
                entityRegistry.removeArmorStand(stand);
                hologram.remove();
                entityRegistry.removeHologram(hologram);

                if (!finalPick) {
                    return;
                }

                session.markRewardGranted();
                plugin.getRewardService().execute(player, reward.getActions());
                plugin.getStats().recordOpening(player, session.getSelectedCase(), reward, session.isGuaranteedReward());
                plugin.getLeaderboardHolograms().refreshAll();
                plugin.getMessages().send(player, "messages.case-reward-received", "#80ed99Вы получили награду: #ffffff%reward%", "%reward%", reward.getName());
                plugin.getCaseService().completeOpening(runtime);
            }
        }.runTaskLater(plugin, displayTicks);
    }

    private void playMinorReveal(World world, Location center, OpeningSession session) {
        world.spawnParticle(isPremium(session) ? Particle.GLOW : Particle.CRIT, center, scaled(16), 0.35, 0.2, 0.35, 0.03);
        world.playSound(center, isPremium(session) ? Sound.BLOCK_AMETHYST_BLOCK_CHIME : Sound.BLOCK_CHEST_OPEN, volume(0.75F), isPremium(session) ? 1.05F : 1.25F);
    }

    private long playFinalReveal(World world, Location center, OpeningSession session) {
        switch (session.getAnimationId().toLowerCase()) {
            case "circle":
                return revealCircle(world, center, session);
            case "meteor-drop":
                return revealMeteor(world, center, session);
            case "void-rift":
                return revealVoid(world, center, session);
            case "swords":
                return revealSwords(world, center, session);
            case "classic":
            default:
                return revealClassic(world, center, session);
        }
    }

    private long revealClassic(World world, Location center, OpeningSession session) {
        new BukkitRunnable() {
            private int ring;

            @Override
            public void run() {
                if (ring++ >= 5) {
                    cancel();
                    return;
                }
                double radius = 0.8 + (ring * 0.45);
                world.spawnParticle(isPremium(session) ? Particle.END_ROD : Particle.WAX_ON, center.clone().add(0.0, 0.2 + ring * 0.15, 0.0), scaled(18), radius, 0.05, radius, 0.01);
                world.playSound(center, isPremium(session) ? Sound.BLOCK_BEACON_POWER_SELECT : Sound.BLOCK_AMETHYST_BLOCK_RESONATE, volume(0.55F), 0.9F + ring * 0.05F);
            }
        }.runTaskTimer(plugin, 0L, 4L);
        return 20L;
    }

    private long revealCircle(World world, Location center, OpeningSession session) {
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (tick++ >= 14) {
                    cancel();
                    return;
                }
                double radius = 3.0 - (tick * 0.17);
                for (int i = 0; i < 8; i++) {
                    double angle = Math.toRadians((tick * 20) + (i * 45));
                    world.spawnParticle(isPremium(session) ? Particle.DRAGON_BREATH : Particle.GLOW, center.clone().add(Math.cos(angle) * radius, 0.6, Math.sin(angle) * radius), scaled(1), 0.0, 0.0, 0.0, 0.0);
                }
                world.playSound(center, Sound.BLOCK_NOTE_BLOCK_CHIME, volume(0.45F), 0.8F + tick * 0.03F);
            }
        }.runTaskTimer(plugin, 0L, 2L);
        return 24L;
    }

    private long revealMeteor(World world, Location center, OpeningSession session) {
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (tick++ >= 8) {
                    cancel();
                    return;
                }
                world.spawnParticle(isPremium(session) ? Particle.SOUL_FIRE_FLAME : Particle.FLAME, center.clone().add(0.0, 1.8 - tick * 0.18, 0.0), scaled(16), 0.25, 0.25, 0.25, 0.01);
                world.playSound(center, isPremium(session) ? Sound.ITEM_TRIDENT_THUNDER : Sound.ENTITY_BLAZE_SHOOT, volume(0.5F), 0.8F + tick * 0.03F);
            }
        }.runTaskTimer(plugin, 0L, 2L);
        new BukkitRunnable() {
            @Override
            public void run() {
                world.spawnParticle(isPremium(session) ? Particle.ASH : Particle.LARGE_SMOKE, center, scaled(32), 0.45, 0.2, 0.45, 0.02);
                world.playSound(center, isPremium(session) ? Sound.ENTITY_DRAGON_FIREBALL_EXPLODE : Sound.ENTITY_GENERIC_EXPLODE, volume(0.8F), 0.9F);
            }
        }.runTaskLater(plugin, 18L);
        return 24L;
    }

    private long revealVoid(World world, Location center, OpeningSession session) {
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (tick++ >= 16) {
                    cancel();
                    return;
                }
                double radius = 2.0 - (tick * 0.08);
                world.spawnParticle(isPremium(session) ? Particle.DRAGON_BREATH : Particle.PORTAL, center.clone().add(0.0, 0.7, 0.0), scaled(18), radius, 0.2, radius, 0.03);
                world.playSound(center, isPremium(session) ? Sound.BLOCK_END_PORTAL_FRAME_FILL : Sound.BLOCK_PORTAL_AMBIENT, volume(0.4F), 0.7F + tick * 0.02F);
            }
        }.runTaskTimer(plugin, 0L, 2L);
        return 24L;
    }

    private long revealSwords(World world, Location center, OpeningSession session) {
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (tick++ >= 8) {
                    cancel();
                    return;
                }
                Particle particle = isPremium(session) ? Particle.LAVA : Particle.BLOCK;
                if (particle == Particle.BLOCK) {
                    world.spawnParticle(
                            particle,
                            center.clone().add(0.0, 0.35 + (tick * 0.08), 0.0),
                            scaled(18),
                            0.3,
                            0.25,
                            0.3,
                            0.0,
                            Material.CRACKED_STONE_BRICKS.createBlockData()
                    );
                } else {
                    world.spawnParticle(
                            particle,
                            center.clone().add(0.0, 0.35 + (tick * 0.08), 0.0),
                            scaled(18),
                            0.3,
                            0.25,
                            0.3,
                            0.02
                    );
                }
                world.playSound(center, isPremium(session) ? Sound.ITEM_TRIDENT_HIT_GROUND : Sound.BLOCK_ANVIL_DESTROY, volume(0.45F), 0.85F + (tick * 0.04F));
            }
        }.runTaskTimer(plugin, 0L, 3L);
        return 22L;
    }

    private void playRewardChord(World world, Location center, OpeningSession session) {
        if (isPremium(session)) {
            world.playSound(center, Sound.BLOCK_NOTE_BLOCK_BELL, volume(0.55F), 0.9F);
            world.playSound(center, Sound.BLOCK_NOTE_BLOCK_CHIME, volume(0.45F), 1.15F);
            world.playSound(center, Sound.BLOCK_NOTE_BLOCK_PLING, volume(0.35F), 1.45F);
            return;
        }

        world.playSound(center, Sound.BLOCK_NOTE_BLOCK_PLING, volume(0.45F), 1.0F);
        world.playSound(center, Sound.BLOCK_NOTE_BLOCK_CHIME, volume(0.35F), 1.2F);
    }

    private boolean isPremium(OpeningSession session) {
        return session.getFinalReward() != null && (session.getFinalReward().isRare() || session.isGuaranteedReward());
    }

    private void startWinnerMotion(ArmorStand stand, Hologram hologram, Location base, long displayTicks) {
        double shakeAmplitude = Math.max(0.0D, plugin.getConfig().getDouble("settings.animations.winner-item.shake-amplitude", 0.06D));
        double levitationHeight = Math.max(0.0D, plugin.getConfig().getDouble("settings.animations.winner-item.levitation-height", 0.45D));
        double bobStrength = Math.max(0.0D, plugin.getConfig().getDouble("settings.animations.winner-item.bob-strength", 0.12D));

        new BukkitRunnable() {
            private long tick;

            @Override
            public void run() {
                if (tick++ >= displayTicks || stand.isDead()) {
                    cancel();
                    return;
                }

                double bob = Math.sin(tick * 0.35D) * bobStrength;
                double shakeX = Math.cos(tick * 1.2D) * shakeAmplitude;
                double shakeZ = Math.sin(tick * 1.15D) * shakeAmplitude;
                Location itemLocation = base.clone().add(shakeX, levitationHeight + bob, shakeZ);
                stand.teleport(itemLocation);
                hologram.setLocation(itemLocation.clone().add(0.0, 1.45, 0.0));
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private int scaled(int base) {
        double scale = Math.max(0.1D, plugin.getConfig().getDouble("settings.animations.intensity.particles", 1.0D));
        return Math.max(1, (int) Math.round(base * scale));
    }

    private float volume(float base) {
        double scale = Math.max(0.0D, plugin.getConfig().getDouble("settings.animations.intensity.sound", 1.0D));
        return (float) (base * scale);
    }
}