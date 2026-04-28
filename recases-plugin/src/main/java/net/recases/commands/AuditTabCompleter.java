package net.recases.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class AuditTabCompleter {

    private static final List<String> LIMITS = Arrays.asList("10", "20", "50");

    private final CaseCommands commands;

    AuditTabCompleter(CaseCommands commands) {
        this.commands = commands;
    }

    List<String> complete(String subcommand, String[] args) {
        if ("audit".equals(subcommand) || "history".equals(subcommand)) {
            if (args.length == 2) {
                List<String> values = new ArrayList<>(commands.knownOfflinePlayerNames());
                values.addAll(LIMITS);
                return commands.complete(values, args[1]);
            }
            if (args.length == 3) {
                return commands.complete(LIMITS, args[2]);
            }
            return null;
        }

        if (Arrays.asList("rollback", "restore").contains(subcommand) && args.length == 2) {
            return Collections.emptyList();
        }
        return null;
    }
}
