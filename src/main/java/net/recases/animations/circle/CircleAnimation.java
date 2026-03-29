package net.recases.animations.circle;

import net.recases.app.PluginContext;
import net.recases.animations.Animation;
import net.recases.animations.round.RoundAnimation;
import net.recases.runtime.CaseRuntime;
import org.bukkit.entity.Player;

public class CircleAnimation implements Animation {

    private final PluginContext plugin;
    private final Player player;
    private final CaseRuntime runtime;

    public CircleAnimation(PluginContext plugin, Player player, CaseRuntime runtime) {
        this.plugin = plugin;
        this.player = player;
        this.runtime = runtime;
    }

    @Override
    public void playAnimation() {
        if (runtime != null) {
            new RoundAnimation(plugin, player, runtime).playAnimation();
        }
    }
}

