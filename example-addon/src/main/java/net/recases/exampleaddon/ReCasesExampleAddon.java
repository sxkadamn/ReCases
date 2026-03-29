package net.recases.exampleaddon;

import net.recases.api.ReCasesApi;
import net.recases.api.animation.OpeningAnimationRegistration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ReCasesExampleAddon extends JavaPlugin {

    public static final String ANIMATION_ID = "crystal-burst";

    @Override
    public void onEnable() {
        ReCasesApi api = getServer().getServicesManager().load(ReCasesApi.class);
        if (api == null) {
            getLogger().severe("ReCases API is unavailable.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        boolean registered = api.getOpeningAnimationRegistry().register(
                OpeningAnimationRegistration.create(
                        this,
                        ANIMATION_ID,
                        "Crystal Burst",
                        1,
                        CrystalBurstAnimation::new
                )
        );

        if (!registered) {
            getLogger().severe("Animation id '" + ANIMATION_ID + "' is already registered.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("Registered opening animation '" + ANIMATION_ID + "'.");
    }
}
