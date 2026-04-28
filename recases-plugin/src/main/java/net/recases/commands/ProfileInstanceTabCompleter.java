package net.recases.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class ProfileInstanceTabCompleter {

    private final CaseCommands commands;

    ProfileInstanceTabCompleter(CaseCommands commands) {
        this.commands = commands;
    }

    List<String> complete(String subcommand, String[] args) {
        if ("set".equals(subcommand) && args.length == 2) {
            return commands.complete(commands.plugin().getCaseService().getRuntimeIds(), args[1]);
        }
        if (Arrays.asList("deleteprofile", "edit", "setprofileanimation").contains(subcommand) && args.length == 2) {
            return commands.complete(commands.plugin().getCaseService().getProfileIds(), args[1]);
        }
        if (Arrays.asList("deleteinstance", "setinstanceanimation").contains(subcommand) && args.length == 2) {
            return commands.complete(commands.plugin().getCaseService().getRuntimeIds(), args[1]);
        }
        if (Arrays.asList("setprofileanimation", "setinstanceanimation").contains(subcommand) && args.length == 3) {
            List<String> values = new ArrayList<>(commands.plugin().getAnimations().getRegisteredIds());
            if ("setinstanceanimation".equals(subcommand)) {
                values.add("clear");
            }
            return commands.complete(values, args[2]);
        }
        return null;
    }
}
