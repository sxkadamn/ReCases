package net.recases.exampleaddon;

import net.recases.api.ReCasesApi;
import net.recases.api.animation.OpeningAnimationRegistration;
import org.bukkit.Bukkit;
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
                        3,
                        context -> new CrystalBurstAnimation(this, context)
                )
        );

        if (!registered) {
            getLogger().severe("Анимация с id '" + ANIMATION_ID + "' уже зарегистрирована.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("Зарегистрирована анимация открытия '" + ANIMATION_ID + "'.");

        api.getRewardActionRegistry().register("crystal-broadcast", (context, arguments) -> {
            if (arguments.length < 2) {
                return;
            }
            Bukkit.broadcastMessage("[CrystalAddon] " + context.replaceTokens(arguments[1]));
        });

        api.getConditionRegistry().register("crystal-player", (context, arguments) ->
                arguments.length < 2 || context.getPlayer().getName().equalsIgnoreCase(context.replaceTokens(arguments[1])));

        api.getTriggerRegistry().register("reward-granted", context -> {
            if (ANIMATION_ID.equalsIgnoreCase(context.getAnimationId())) {
                getLogger().info("Триггер reward-granted сработал для анимации crystal-burst и игрока " + context.getPlayer().getName());
            }
        });
    }
}
