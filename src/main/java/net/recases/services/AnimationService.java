package net.recases.services;

import net.recases.api.OpeningAnimationRegistry;
import net.recases.api.animation.OpeningAnimationContext;
import net.recases.api.animation.OpeningAnimationFactory;
import net.recases.api.animation.OpeningAnimationRegistration;
import net.recases.api.animation.OpeningAnimation;
import net.recases.internal.api.InternalOpeningAnimationContext;
import net.recases.animations.opening.AnchorRiseOpeningAnimation;
import net.recases.animations.opening.CircleOpeningAnimation;
import net.recases.animations.opening.ClassicOpeningAnimation;
import net.recases.animations.opening.MeteorDropOpeningAnimation;
import net.recases.animations.opening.RainlyOpeningAnimation;
import net.recases.animations.opening.SwordsOpeningAnimation;
import net.recases.animations.opening.VoidRiftOpeningAnimation;
import net.recases.animations.opening.WheelOpeningAnimation;
import net.recases.app.PluginContext;
import net.recases.domain.CaseProfile;
import net.recases.runtime.CaseRuntime;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AnimationService implements OpeningAnimationRegistry {

    public static final String CLASSIC = "classic";
    public static final String CIRCLE = "circle";
    public static final String METEOR_DROP = "meteor-drop";
    public static final String VOID_RIFT = "void-rift";
    public static final String WHEEL = "wheel";
    public static final String SWORDS = "swords";
    public static final String ANCHOR_RISE = "anchor-rise";
    public static final String RAINLY = "rainly";

    private final JavaPlugin plugin;
    private final Map<String, OpeningAnimationRegistration> animations = new LinkedHashMap<>();

    public AnimationService(JavaPlugin plugin) {
        this.plugin = plugin;
        registerBuiltIns();
    }

    public List<String> getKnownAnimations() {
        return getRegisteredIds();
    }

    public String resolveAnimationId(CaseRuntime runtime, CaseProfile profile) {
        String configured = runtime.getAnimationId();
        if (configured == null || configured.trim().isEmpty()) {
            configured = profile.getAnimationId();
        }

        String animationId = configured == null || configured.trim().isEmpty()
                ? CLASSIC
                : configured.toLowerCase(Locale.ROOT);

        if (isRegistered(animationId)) {
            return animationId;
        }

        plugin.getLogger().warning("Unknown animation '" + animationId + "'. Falling back to '" + CLASSIC + "'.");
        return CLASSIC;
    }

    public OpeningAnimation create(PluginContext context, Player player, CaseRuntime runtime, CaseProfile profile) {
        String animationId = resolveAnimationId(runtime, profile);
        OpeningAnimationRegistration registration = animations.get(animationId);
        if (registration == null) {
            registration = animations.get(CLASSIC);
        }
        OpeningAnimationContext animationContext = new InternalOpeningAnimationContext((net.recases.api.ReCasesApi) plugin, context, runtime, profile, player);
        return registration.getFactory().create(animationContext);
    }

    public int getRequiredSelections(String animationId) {
        OpeningAnimationRegistration registration = animations.get(normalizeIdOrDefault(animationId));
        return registration == null ? 1 : registration.getRequiredSelections();
    }

    public String getDisplayName(String animationId) {
        OpeningAnimationRegistration registration = animations.get(normalizeIdOrDefault(animationId));
        return registration == null ? OpeningAnimationRegistration.defaultDisplayName(CLASSIC) : registration.getDisplayName();
    }

    @Override
    public boolean register(OpeningAnimationRegistration registration) {
        if (registration == null) {
            throw new IllegalArgumentException("registration cannot be null");
        }
        String animationId = registration.getId();
        if (animations.containsKey(animationId)) {
            return false;
        }
        animations.put(animationId, registration);
        return true;
    }

    @Override
    public boolean unregister(String animationId) {
        String normalized = OpeningAnimationRegistration.normalizeId(animationId);
        OpeningAnimationRegistration registration = animations.get(normalized);
        if (registration == null || registration.getOwner().equals(plugin)) {
            return false;
        }
        animations.remove(normalized);
        return true;
    }

    public int unregisterOwnedBy(Plugin owner) {
        int removed = 0;
        for (String animationId : new ArrayList<>(animations.keySet())) {
            OpeningAnimationRegistration registration = animations.get(animationId);
            if (registration != null && registration.getOwner().equals(owner) && !owner.equals(plugin)) {
                animations.remove(animationId);
                removed++;
            }
        }
        return removed;
    }

    @Override
    public boolean isRegistered(String animationId) {
        return animations.containsKey(normalizeIdOrDefault(animationId));
    }

    @Override
    public List<String> getRegisteredIds() {
        return Collections.unmodifiableList(new ArrayList<>(animations.keySet()));
    }

    private void registerBuiltIns() {
        registerBuiltIn(CLASSIC, "Classic", 1, classicFactory());
        registerBuiltIn(CIRCLE, "Circle", 2, context -> new CircleOpeningAnimation(pluginContext(context), context.getPlayer(), runtime(context)));
        registerBuiltIn(METEOR_DROP, "Meteor Drop", 1, context -> new MeteorDropOpeningAnimation(pluginContext(context), context.getPlayer(), runtime(context)));
        registerBuiltIn(VOID_RIFT, "Void Rift", 1, context -> new VoidRiftOpeningAnimation(pluginContext(context), context.getPlayer(), runtime(context)));
        registerBuiltIn(WHEEL, "Wheel", 0, context -> new WheelOpeningAnimation(pluginContext(context), context.getPlayer(), runtime(context)));
        registerBuiltIn(SWORDS, "Swords", 1, context -> new SwordsOpeningAnimation(pluginContext(context), context.getPlayer(), runtime(context)));
        registerBuiltIn(ANCHOR_RISE, "Anchor Rise", 1, context -> new AnchorRiseOpeningAnimation(pluginContext(context), context.getPlayer(), runtime(context)));
        registerBuiltIn(RAINLY, "Rainly", 1, context -> new RainlyOpeningAnimation(pluginContext(context), context.getPlayer(), runtime(context)));
    }

    private void registerBuiltIn(String id, String displayName, int requiredSelections, OpeningAnimationFactory factory) {
        animations.put(id, OpeningAnimationRegistration.create(plugin, id, displayName, requiredSelections, factory));
    }

    private OpeningAnimationFactory classicFactory() {
        return context -> new ClassicOpeningAnimation(pluginContext(context), context.getPlayer(), runtime(context));
    }

    private String normalizeIdOrDefault(String animationId) {
        if (animationId == null || animationId.trim().isEmpty()) {
            return CLASSIC;
        }
        return animationId.toLowerCase(Locale.ROOT);
    }

    private PluginContext pluginContext(OpeningAnimationContext context) {
        return internalContext(context).pluginContext();
    }

    private CaseRuntime runtime(OpeningAnimationContext context) {
        return internalContext(context).runtime();
    }

    private InternalOpeningAnimationContext internalContext(OpeningAnimationContext context) {
        if (!(context instanceof InternalOpeningAnimationContext)) {
            throw new IllegalArgumentException("Unsupported opening animation context: " + context.getClass().getName());
        }
        return (InternalOpeningAnimationContext) context;
    }
}

