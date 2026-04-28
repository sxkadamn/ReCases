package net.recases.commands;

import net.recases.services.PromoCodeService;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

final class PromoCodeTabCompleter {

    private static final List<String> PROMOCODE_ACTIONS = Arrays.asList("list", "create", "delete");

    private final CaseCommands commands;

    PromoCodeTabCompleter(CaseCommands commands) {
        this.commands = commands;
    }

    List<String> complete(String subcommand, String[] args) {
        if ("promocode".equals(subcommand)) {
            if (args.length == 2) {
                return commands.complete(PROMOCODE_ACTIONS, args[1]);
            }
            if ("create".equalsIgnoreCase(args[1])) {
                if (args.length == 4) {
                    return commands.complete(commands.plugin().getCaseService().getProfileIds(), args[3]);
                }
                if (args.length == 5 || args.length == 6) {
                    return commands.complete(Arrays.asList("1", "3", "5", "10"), args[args.length - 1]);
                }
            }
            if ("delete".equalsIgnoreCase(args[1]) && args.length == 3) {
                return commands.complete(codes(), args[2]);
            }
            return null;
        }

        if ("redeem".equals(subcommand) && args.length == 2) {
            return commands.complete(codes(), args[1]);
        }
        return null;
    }

    private List<String> codes() {
        return commands.plugin().getPromoCodes().listCodes(20).stream()
                .map(PromoCodeService.PromoCodeEntry::code)
                .collect(Collectors.toList());
    }
}
