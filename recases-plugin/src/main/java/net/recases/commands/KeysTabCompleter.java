package net.recases.commands;

import java.util.Arrays;
import java.util.List;

final class KeysTabCompleter {

    private final CaseCommands commands;

    KeysTabCompleter(CaseCommands commands) {
        this.commands = commands;
    }

    List<String> complete(String subcommand, String[] args) {
        if (!Arrays.asList("keys", "give", "take", "setamount").contains(subcommand)) {
            return null;
        }
        if (args.length == 2) {
            return commands.complete(commands.knownOfflinePlayerNames(), args[1]);
        }
        if (args.length == 3) {
            return commands.complete(commands.plugin().getCaseService().getProfileIds(), args[2]);
        }
        if (args.length == 4 && !"keys".equals(subcommand)) {
            return commands.complete(Arrays.asList("1", "3", "5", "10", "32", "64"), args[3]);
        }
        return null;
    }
}
