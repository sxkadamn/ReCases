package net.recases.services;

import net.recases.app.PluginContext;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class BedrockSupportService {

    private static final Set<String> COMPLEX_ANIMATIONS = Set.of("wheel", "sphere", "neural");

    private final PluginContext plugin;

    public BedrockSupportService(PluginContext plugin) {
        this.plugin = plugin;
    }

    public void reload() {
    }

    public boolean isBedrockPlayer(Player player) {
        if (player == null || !plugin.getConfig().getBoolean("settings.bedrock.enabled", false)) {
            return false;
        }
        return isFloodgatePlayer(player.getUniqueId()) || isGeyserPlayer(player.getUniqueId());
    }

    public boolean shouldUsePreviewFirstMenus(Player player) {
        if (!isBedrockPlayer(player)) {
            return false;
        }

        return "preview-first".equalsIgnoreCase(plugin.getConfig().getString("settings.bedrock.menu-mode", "preview-first"));
    }

    public String adaptAnimationId(Player player, String animationId) {
        if (!isBedrockPlayer(player) || !plugin.getConfig().getBoolean("settings.bedrock.force-simple-animations", true)) {
            return animationId;
        }

        String normalized = animationId == null ? AnimationService.CLASSIC : animationId.trim().toLowerCase(Locale.ROOT);
        int maxSelections = Math.max(0, plugin.getConfig().getInt("settings.bedrock.max-selections", 1));
        boolean simplifyHeavy = plugin.getConfig().getBoolean("settings.bedrock.simplify-heavy-animations", true);
        if (plugin.getAnimations().getRequiredSelections(normalized) <= maxSelections
                && (!simplifyHeavy || !COMPLEX_ANIMATIONS.contains(normalized))) {
            return normalized;
        }

        String fallback = plugin.getConfig().getString("settings.bedrock.fallback-animation", "anchor-rise");
        return plugin.getAnimations().isRegistered(fallback) ? fallback.toLowerCase(Locale.ROOT) : AnimationService.CLASSIC;
    }

    private boolean isFloodgatePlayer(UUID playerId) {
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Method method = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            return Boolean.TRUE.equals(method.invoke(api, playerId));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private boolean isGeyserPlayer(UUID playerId) {
        try {
            Class<?> apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
            Object api = apiClass.getMethod("api").invoke(null);
            if (api == null) {
                return false;
            }

            try {
                Method directCheck = api.getClass().getMethod("isBedrockPlayer", UUID.class);
                return Boolean.TRUE.equals(directCheck.invoke(api, playerId));
            } catch (NoSuchMethodException ignored) {
                Method connectionByUuid = api.getClass().getMethod("connectionByUuid", UUID.class);
                return connectionByUuid.invoke(api, playerId) != null;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }
}
