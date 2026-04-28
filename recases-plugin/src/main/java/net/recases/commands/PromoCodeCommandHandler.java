package net.recases.commands;

import org.bukkit.command.CommandSender;

final class PromoCodeCommandHandler {

    private final CaseCommands commands;

    PromoCodeCommandHandler(CaseCommands commands) {
        this.commands = commands;
    }

    boolean handlePromoCode(CommandSender sender, String[] args) {
        return commands.handlePromoCode(sender, args);
    }

    boolean redeemCode(CommandSender sender, String[] args) {
        return commands.redeemCode(sender, args);
    }
}
