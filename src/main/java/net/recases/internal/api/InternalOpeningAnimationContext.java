package net.recases.internal.api;

import net.recases.api.ReCasesApi;
import net.recases.api.animation.OpeningAnimationContext;
import net.recases.api.animation.OpeningSessionView;
import net.recases.animations.opening.AbstractEntityOpeningAnimation;
import net.recases.app.PluginContext;
import net.recases.domain.CaseProfile;
import net.recases.management.OpeningSession;
import net.recases.runtime.CaseRuntime;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;

public final class InternalOpeningAnimationContext implements OpeningAnimationContext {

    private final ReCasesApi api;
    private final PluginContext pluginContext;
    private final CaseRuntime runtime;
    private final CaseProfile profile;
    private final Player player;
    private final OpeningSessionView sessionView;

    public InternalOpeningAnimationContext(ReCasesApi api, PluginContext pluginContext, CaseRuntime runtime, CaseProfile profile, Player player) {
        this.api = api;
        this.pluginContext = pluginContext;
        this.runtime = runtime;
        this.profile = profile;
        this.player = player;
        this.sessionView = new InternalOpeningSessionView(runtime.getSession());
    }

    @Override
    public ReCasesApi getApi() {
        return api;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public String getRuntimeId() {
        return runtime.getId();
    }

    @Override
    public Location getRuntimeLocation() {
        return runtime.getLocation().clone();
    }

    @Override
    public boolean isRuntimeAvailable() {
        return runtime.isAvailable();
    }

    @Override
    public boolean isOpeningActive() {
        OpeningSession session = runtime.getSession();
        return session != null && runtime.isOpening() && session.isParticipant(player);
    }

    @Override
    public String getProfileId() {
        return profile.getId();
    }

    @Override
    public OpeningSessionView getSession() {
        return sessionView;
    }

    @Override
    public void removeRuntimeHologram() {
        runtime.removeHologram();
    }

    @Override
    public void abortOpening(boolean refundKey) {
        pluginContext.getCaseService().abortOpening(runtime, refundKey);
    }

    @Override
    public void completeOpening() {
        pluginContext.getCaseService().completeOpening(runtime);
    }

    @Override
    public void registerTargetChest(Location location) {
        OpeningSession session = requireSession();
        Location normalized = location.getBlock().getLocation();
        normalized.getBlock().setMetadata("case_open_chest", new FixedMetadataValue((Plugin) pluginContext, runtime.getId()));
        session.getChestLocations().add(normalized);
    }

    @Override
    public void registerTargetEntity(Entity entity) {
        OpeningSession session = requireSession();
        entity.setMetadata(AbstractEntityOpeningAnimation.ENTITY_TARGET_METADATA, new FixedMetadataValue((Plugin) pluginContext, runtime.getId()));
        session.trackEntity(entity);
    }

    public PluginContext pluginContext() {
        return pluginContext;
    }

    public CaseRuntime runtime() {
        return runtime;
    }

    private OpeningSession requireSession() {
        OpeningSession session = runtime.getSession();
        if (session == null) {
            throw new IllegalStateException("opening session is not active");
        }
        return session;
    }
}
