package net.recases.listener;

import net.recases.app.PluginContext;
import net.recases.services.UpdateService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class UpdateNotifierListener implements Listener {

    private final PluginContext plugin;
    private final UpdateService updateService;

    public UpdateNotifierListener(PluginContext plugin, UpdateService updateService) {
        this.plugin = plugin;
        this.updateService = updateService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!updateService.shouldNotifyAdminsOnJoin() || !updateService.isUpdateAvailable()) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission("recases.admin")) {
            return;
        }

        plugin.getMessages().send(
                player,
                "messages.update-available-player",
                "#ffd166Доступно обновление ReCases: #ffffff%current% #ffd166→ #80ed99%latest% #a8dadc(%url%)",
                "%current%", plugin.getDescription().getVersion(),
                "%latest%", updateService.getLatestVersion(),
                "%url%", updateService.getResourceUrl()
        );

        if (updateService.isUpdateDownloaded()) {
            plugin.getMessages().send(
                    player,
                    "messages.update-downloaded-player",
                    "#80ed99Новая версия уже загружена в папку #ffffff/plugins/update#80ed99. Перезапустите сервер."
            );
        }
    }
}
