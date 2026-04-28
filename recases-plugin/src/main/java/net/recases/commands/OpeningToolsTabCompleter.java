package net.recases.commands;

import java.util.Arrays;
import java.util.List;

final class OpeningToolsTabCompleter {

    private static final List<String> PRESET_ACTIONS = Arrays.asList("list", "export", "import");

    private final CaseCommands commands;

    OpeningToolsTabCompleter(CaseCommands commands) {
        this.commands = commands;
    }

    List<String> complete(String subcommand, String[] args) {
        if ("preset".equals(subcommand)) {
            if (args.length == 2) {
                return commands.complete(PRESET_ACTIONS, args[1]);
            }
            if ("export".equalsIgnoreCase(args[1]) && args.length == 3) {
                return commands.complete(commands.plugin().getCaseService().getProfileIds(), args[2]);
            }
            if ("import".equalsIgnoreCase(args[1])) {
                if (args.length == 3) {
                    return commands.complete(commands.plugin().getCaseService().getPresetIds(), args[2]);
                }
                if (args.length == 4) {
                    return commands.complete(commands.plugin().getCaseService().getProfileIds(), args[3]);
                }
            }
            return null;
        }

        if ("testanim".equals(subcommand)) {
            if (args.length == 2) {
                return commands.complete(commands.plugin().getAnimations().getRegisteredIds(), args[1]);
            }
            if (args.length == 3) {
                return commands.complete(commands.plugin().getCaseService().getProfileIds(), args[2]);
            }
            if (args.length == 4) {
                return commands.complete(commands.plugin().getCaseService().getRuntimeIds(), args[3]);
            }
            return null;
        }

        if ("simulate".equals(subcommand)) {
            if (args.length == 2) {
                return commands.complete(commands.plugin().getCaseService().getProfileIds(), args[1]);
            }
            if (args.length == 3) {
                return commands.complete(Arrays.asList("1000", "5000", "10000"), args[2]);
            }
        }
        return null;
    }
}
