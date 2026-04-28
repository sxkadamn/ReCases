package net.recases.commands;

import org.bukkit.command.CommandSender;

final class AdminCommandHandler {

    private final CaseCommands commands;

    AdminCommandHandler(CaseCommands commands) {
        this.commands = commands;
    }

    void showProfiles(CommandSender sender) {
        commands.showProfiles(sender);
    }

    void showInstances(CommandSender sender) {
        commands.showInstances(sender);
    }

    boolean showTop(CommandSender sender, String[] args) {
        return commands.showTop(sender, args);
    }

    boolean runDoctor(CommandSender sender) {
        return commands.runDoctor(sender);
    }
}
