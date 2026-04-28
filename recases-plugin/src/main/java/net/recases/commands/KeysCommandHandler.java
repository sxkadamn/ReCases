package net.recases.commands;

import org.bukkit.command.CommandSender;

final class KeysCommandHandler {

    private final CaseCommands commands;

    KeysCommandHandler(CaseCommands commands) {
        this.commands = commands;
    }

    boolean showBalance(CommandSender sender, String[] args) {
        return commands.showBalance(sender, args);
    }

    boolean changeKeys(CommandSender sender, String[] args, CaseCommands.ChangeMode mode) {
        return commands.changeKeys(sender, args, mode);
    }
}
