package net.recases.listener;

import net.recases.services.AnimationService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

public class PluginHooksListener implements Listener {

    private final Plugin plugin;
    private final AnimationService animationService;

    public PluginHooksListener(Plugin plugin, AnimationService animationService) {
        this.plugin = plugin;
        this.animationService = animationService;
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        Plugin disabledPlugin = event.getPlugin();
        if (disabledPlugin.equals(plugin)) {
            return;
        }

        int removed = animationService.unregisterOwnedBy(disabledPlugin);
        if (removed > 0) {
            plugin.getLogger().info("Unregistered " + removed + " addon animation(s) from plugin '" + disabledPlugin.getName() + "'.");
        }
    }
}
