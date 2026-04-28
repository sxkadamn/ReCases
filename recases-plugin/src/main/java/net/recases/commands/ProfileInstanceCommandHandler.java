package net.recases.commands;

import org.bukkit.command.CommandSender;

final class ProfileInstanceCommandHandler {

    private final CaseCommands commands;

    ProfileInstanceCommandHandler(CaseCommands commands) {
        this.commands = commands;
    }

    boolean saveInstanceLocation(CommandSender sender, String[] args) {
        return commands.saveInstanceLocation(sender, args);
    }

    boolean createProfile(CommandSender sender, String[] args) {
        return commands.createProfile(sender, args);
    }

    boolean deleteProfile(CommandSender sender, String[] args) {
        return commands.deleteProfile(sender, args);
    }

    boolean createInstance(CommandSender sender, String[] args) {
        return commands.createInstance(sender, args);
    }

    boolean deleteInstance(CommandSender sender, String[] args) {
        return commands.deleteInstance(sender, args);
    }

    boolean setProfileAnimation(CommandSender sender, String[] args) {
        return commands.setProfileAnimation(sender, args);
    }

    boolean setInstanceAnimation(CommandSender sender, String[] args) {
        return commands.setInstanceAnimation(sender, args);
    }

    boolean openEditor(CommandSender sender, String[] args) {
        return commands.openEditor(sender, args);
    }
}
