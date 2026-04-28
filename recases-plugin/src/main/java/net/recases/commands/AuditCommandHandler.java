package net.recases.commands;

import org.bukkit.command.CommandSender;

final class AuditCommandHandler {

    private final CaseCommands commands;

    AuditCommandHandler(CaseCommands commands) {
        this.commands = commands;
    }

    boolean showHistory(CommandSender sender, String[] args, boolean adminMode) {
        return commands.showHistory(sender, args, adminMode);
    }

    boolean showAudit(CommandSender sender, String[] args) {
        return commands.showAudit(sender, args);
    }
}
