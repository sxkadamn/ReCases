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
            getLogger().severe("API ReCases недоступно.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        boolean registered = api.getOpeningAnimationRegistry().register(
                OpeningAnimationRegistration.create(
                        this,
                        ANIMATION_ID,
                        "Кристальный всплеск",
                        1,
                        CrystalBurstAnimation::new
                )
        );

        if (!registered) {
            getLogger().severe("Анимация с id '" + ANIMATION_ID + "' уже зарегистрирована.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("Зарегистрирована анимация открытия '" + ANIMATION_ID + "'.");
    }
}
