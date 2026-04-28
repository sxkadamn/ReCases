package net.recases.commands;

import org.bukkit.command.CommandSender;

final class OpeningToolsCommandHandler {

    private final CaseCommands commands;

    OpeningToolsCommandHandler(CaseCommands commands) {
        this.commands = commands;
    }

    boolean handlePreset(CommandSender sender, String[] args) {
        return commands.handlePreset(sender, args);
    }

    boolean runAnimationTest(CommandSender sender, String[] args) {
        return commands.runAnimationTest(sender, args);
    }

    boolean simulateDrops(CommandSender sender, String[] args) {
        return commands.simulateDrops(sender, args);
    }
}
