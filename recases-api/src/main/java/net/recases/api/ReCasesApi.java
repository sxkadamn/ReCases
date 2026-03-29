package net.recases.api;

import org.bukkit.plugin.Plugin;

public interface ReCasesApi {

    Plugin getPlugin();

    OpeningAnimationRegistry getOpeningAnimationRegistry();
}
