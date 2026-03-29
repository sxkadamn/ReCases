package net.recases.animations;

import net.recases.app.PluginContext;
import net.recases.animations.round.RoundAnimation;
import net.recases.runtime.CaseRuntime;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class AnimationArray {

    private final Animation animation;

    public AnimationArray(PluginContext plugin, Player player, Block block) {
        CaseRuntime runtime = plugin.getCaseService().getRuntime(block);
        this.animation = runtime == null ? () -> { } : new RoundAnimation(plugin, player, runtime);
    }

    public Animation getAnimation() {
        return animation;
    }
}

