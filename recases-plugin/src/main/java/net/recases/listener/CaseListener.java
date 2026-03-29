package net.recases.listener;

import net.recases.animations.opening.AbstractEntityOpeningAnimation;
import net.recases.animations.opening.AnchorRiseOpeningAnimation;
import net.recases.animations.opening.RainlyOpeningAnimation;
import net.recases.animations.opening.SwordsOpeningAnimation;
import net.recases.animations.opening.WheelOpeningAnimation;
import net.recases.app.PluginContext;
import net.recases.gui.create.CasesGUI;
import net.recases.protocollib.hologram.HologramLine;
import net.recases.runtime.CaseRuntime;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class CaseListener implements Listener {

    private final PluginContext plugin;

    public CaseListener(PluginContext plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        CaseRuntime runtime = plugin.getCaseService().getRuntime(block);
        if (runtime == null) {
            return;
        }
        if (!runtime.isAvailable()) {
            plugin.getMessages().send(event.getPlayer(), "messages.case-unavailable", "#ff6b6bЭта точка кейса сейчас недоступна. Попробуйте позже или перезагрузите плагин.");
            event.setCancelled(true);
            return;
        }

        new CasesGUI(plugin, runtime).open(event.getPlayer());
        event.setCancelled(true);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!block.hasMetadata(CaseRuntime.CASE_METADATA) && !block.hasMetadata("case_open_chest") && !block.hasMetadata("case_platform")) {
            return;
        }

        plugin.getWorldService().knockback(event.getPlayer());
        event.setCancelled(true);
    }

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (entity.hasMetadata(AbstractEntityOpeningAnimation.ENTITY_TARGET_METADATA)) {
            return;
        }
        if (plugin.getLeaderboardHolograms().handleInteraction(entity, event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        if (!entity.hasMetadata("armor_head")
                && !entity.hasMetadata(HologramLine.HOLOGRAM_METADATA)
                && !entity.hasMetadata(WheelOpeningAnimation.WHEEL_ENTITY_METADATA)
                && !entity.hasMetadata(SwordsOpeningAnimation.SWORDS_DECOR_METADATA)
                && !entity.hasMetadata(AnchorRiseOpeningAnimation.ANCHOR_RISE_METADATA)
                && !entity.hasMetadata(RainlyOpeningAnimation.RAINLY_METADATA)) {
            return;
        }

        plugin.getWorldService().knockback(event.getPlayer());
        event.setCancelled(true);
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity().hasMetadata("armor_head")
                || event.getEntity().hasMetadata(HologramLine.HOLOGRAM_METADATA)
                || event.getEntity().hasMetadata(AbstractEntityOpeningAnimation.ENTITY_TARGET_METADATA)
                || event.getEntity().hasMetadata(WheelOpeningAnimation.WHEEL_ENTITY_METADATA)
                || event.getEntity().hasMetadata(SwordsOpeningAnimation.SWORDS_DECOR_METADATA)
                || event.getEntity().hasMetadata(AnchorRiseOpeningAnimation.ANCHOR_RISE_METADATA)
                || event.getEntity().hasMetadata(RainlyOpeningAnimation.RAINLY_METADATA)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onManipulate(PlayerArmorStandManipulateEvent event) {
        if (event.getRightClicked().hasMetadata("armor_head")
                || event.getRightClicked().hasMetadata(HologramLine.HOLOGRAM_METADATA)
                || event.getRightClicked().hasMetadata(AbstractEntityOpeningAnimation.ENTITY_TARGET_METADATA)
                || event.getRightClicked().hasMetadata(WheelOpeningAnimation.WHEEL_ENTITY_METADATA)
                || event.getRightClicked().hasMetadata(SwordsOpeningAnimation.SWORDS_DECOR_METADATA)
                || event.getRightClicked().hasMetadata(AnchorRiseOpeningAnimation.ANCHOR_RISE_METADATA)
                || event.getRightClicked().hasMetadata(RainlyOpeningAnimation.RAINLY_METADATA)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        abortPlayerOpenings(event.getPlayer());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        abortPlayerOpenings(event.getPlayer());
    }

    private void abortPlayerOpenings(Player player) {
        plugin.getCaseService().abortOpeningsFor(player, true);
    }
}
