package net.recases.commands;

import net.recases.app.PluginContext;
import net.recases.runtime.CaseRuntime;
import net.recases.services.MessageService;
import net.recases.stats.LeaderboardEntry;
import net.recases.stats.LeaderboardType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class CaseCommands implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "help", "list", "instances", "keys", "give", "take", "setamount", "set", "reload", "top"
    );
    private static final List<String> TOP_TYPES = Arrays.asList("opens", "rare", "guaranteed");

    private final PluginContext plugin;
    private final MessageService messages;

    public CaseCommands(PluginContext plugin) {
        this.plugin = plugin;
        this.messages = plugin.getMessages();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("recases.admin")) {
            messages.send(sender, "messages.no-permission", "У вас нет прав.");
            return true;
        }

        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            messages.sendList(sender, "messages.help", Collections.emptyList(), "%label%", label);
            messages.send(sender, "messages.help-top", "#ffd166/%label% top <opens|rare|guaranteed> [profile] [limit] #a8dadc- показать таблицы лидеров", "%label%", label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list":
                showProfiles(sender);
                return true;
            case "instances":
                showInstances(sender);
                return true;
            case "keys":
                return showBalance(sender, args);
            case "give":
                return changeKeys(sender, args, ChangeMode.GIVE);
            case "take":
                return changeKeys(sender, args, ChangeMode.TAKE);
            case "setamount":
                return changeKeys(sender, args, ChangeMode.SET);
            case "set":
                return saveInstanceLocation(sender, args);
            case "reload":
                plugin.reloadPluginState();
                messages.send(sender, "messages.reload-complete", "Конфигурация и рантайм перезагружены.");
                return true;
            case "top":
                return showTop(sender, args);
            default:
                messages.send(sender, "messages.command-unknown", "Неизвестная подкоманда.");
                return true;
        }
    }

    private void showProfiles(CommandSender sender) {
        List<String> profiles = plugin.getCaseService().getProfileIds();
        if (profiles.isEmpty()) {
            messages.send(sender, "messages.case-list-empty", "Профили кейсов не настроены.");
            return;
        }

        messages.send(sender,
                "messages.case-list-format",
                "Профили кейсов: %cases%",
                "%cases%", String.join(", ", profiles)
        );
    }

    private void showInstances(CommandSender sender) {
        List<String> instances = plugin.getCaseService().getRuntimeIds();
        if (instances.isEmpty()) {
            messages.send(sender, "messages.instance-list-empty", "Физические экземпляры кейсов не настроены.");
            return;
        }

        messages.send(sender,
                "messages.instance-list-format",
                "Экземпляры кейсов: %instances%",
                "%instances%", String.join(", ", instances)
        );
    }

    private boolean showBalance(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "messages.usage-keys", "/cases keys <player> <profile>");
            return true;
        }

        OfflinePlayer target = findPlayer(args[1]);
        if (target == null) {
            messages.send(sender, "messages.player-not-found", "Игрок не найден.");
            return true;
        }

        String profileId = args[2].toLowerCase(Locale.ROOT);
        if (!plugin.getCaseService().hasProfile(profileId)) {
            messages.send(sender, "messages.case-not-found", "Профиль кейса '%case%' не найден.", "%case%", profileId);
            return true;
        }

        messages.send(sender,
                "messages.case-balance",
                "У игрока %player% %amount% ключ(ей) для профиля %case%.",
                "%player%", playerName(target),
                "%amount%", String.valueOf(plugin.getStorage().getCaseAmount(target, profileId)),
                "%case%", profileId
        );
        return true;
    }

    private boolean changeKeys(CommandSender sender, String[] args, ChangeMode mode) {
        if (args.length < 4) {
            messages.send(sender, mode.usagePath, mode.usageFallback);
            return true;
        }

        OfflinePlayer target = findPlayer(args[1]);
        if (target == null) {
            messages.send(sender, "messages.player-not-found", "Игрок не найден.");
            return true;
        }

        String profileId = args[2].toLowerCase(Locale.ROOT);
        if (!plugin.getCaseService().hasProfile(profileId)) {
            messages.send(sender, "messages.case-not-found", "Профиль кейса '%case%' не найден.", "%case%", profileId);
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[3]);
        } catch (NumberFormatException exception) {
            messages.send(sender, "messages.amount-invalid", "Количество должно быть числом.");
            return true;
        }

        if (amount < 0) {
            messages.send(sender, "messages.amount-positive", "Количество не может быть отрицательным.");
            return true;
        }

        int before = plugin.getStorage().getCaseAmount(target, profileId);
        if (mode == ChangeMode.GIVE) {
            plugin.getStorage().addCase(target, profileId, amount);
        } else if (mode == ChangeMode.TAKE) {
            plugin.getStorage().removeCase(target, profileId, amount);
        } else {
            plugin.getStorage().setCase(target, profileId, amount);
        }

        int applied = mode == ChangeMode.TAKE ? Math.min(amount, before) : amount;
        messages.send(sender,
                mode.messagePath,
                mode.messageFallback,
                "%player%", playerName(target),
                "%amount%", String.valueOf(applied),
                "%case%", profileId
        );
        messages.send(sender,
                "messages.case-balance-after",
                "Текущий баланс %player%: %amount% ключ(ей) для %case%.",
                "%player%", playerName(target),
                "%amount%", String.valueOf(plugin.getStorage().getCaseAmount(target, profileId)),
                "%case%", profileId
        );
        return true;
    }

    private boolean saveInstanceLocation(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            messages.send(sender, "messages.player-only", "Эта команда доступна только игрокам.");
            return true;
        }

        if (args.length < 2) {
            messages.send(sender, "messages.usage-set", "/cases set <instance>");
            return true;
        }

        String instanceId = args[1].toLowerCase(Locale.ROOT);
        CaseRuntime runtime = plugin.getCaseService().getRuntime(instanceId);
        if (runtime == null && !plugin.getConfig().contains("cases.instances." + instanceId)) {
            messages.send(sender, "messages.instance-not-found", "Экземпляр кейса '%instance%' не найден.", "%instance%", instanceId);
            return true;
        }

        Player player = (Player) sender;
        Location location = player.getLocation().getBlock().getLocation();
        String basePath = "cases.instances." + instanceId + ".location";
        plugin.getConfig().set(basePath + ".world", location.getWorld().getName());
        plugin.getConfig().set(basePath + ".x", location.getX());
        plugin.getConfig().set(basePath + ".y", location.getY());
        plugin.getConfig().set(basePath + ".z", location.getZ());
        plugin.saveConfig();
        plugin.reloadPluginState();
        messages.send(sender,
                "messages.case-position-saved",
                "Позиция экземпляра кейса %instance% обновлена.",
                "%instance%", instanceId
        );
        return true;
    }

    private boolean showTop(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "messages.usage-top", "/cases top <opens|rare|guaranteed> [profile] [limit]");
            return true;
        }

        LeaderboardType type = LeaderboardType.fromId(args[1]);
        if (type == null) {
            messages.send(sender, "messages.top-type-unknown", "Неизвестный тип топа. Доступно: opens, rare, guaranteed.");
            return true;
        }

        String profileId = null;
        int limit = 10;

        if (args.length >= 3) {
            if (isInteger(args[2])) {
                limit = parsePositiveInt(args[2], 10);
            } else {
                profileId = args[2].toLowerCase(Locale.ROOT);
                if (!plugin.getCaseService().hasProfile(profileId)) {
                    messages.send(sender, "messages.case-not-found", "Профиль кейса '%case%' не найден.", "%case%", profileId);
                    return true;
                }
            }
        }

        if (args.length >= 4) {
            limit = parsePositiveInt(args[3], 10);
        }

        List<LeaderboardEntry> entries = plugin.getStats().getLeaderboard(type, profileId, Math.max(1, Math.min(limit, 20)));
        if (entries.isEmpty()) {
            messages.send(sender, "messages.top-empty", "Данных для таблицы лидеров пока нет.");
            return true;
        }

        messages.send(sender,
                "messages.top-header",
                "#ffd166Топ %type% %scope%",
                "%type%", type.getId(),
                "%scope%", profileId == null ? "(глобально)" : "(" + profileId + ")"
        );

        int position = 1;
        for (LeaderboardEntry entry : entries) {
            messages.send(sender,
                    "messages.top-line",
                    "#a8dadc#%position% #ffffff%player% #ffd166- %value%",
                    "%position%", String.valueOf(position++),
                    "%player%", entry.getPlayerName(),
                    "%value%", String.valueOf(entry.getValue())
            );
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("recases.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return complete(SUBCOMMANDS, args[0]);
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if ("set".equals(subcommand) && args.length == 2) {
            return complete(plugin.getCaseService().getRuntimeIds(), args[1]);
        }

        if ("top".equals(subcommand)) {
            if (args.length == 2) {
                return complete(TOP_TYPES, args[1]);
            }
            if (args.length == 3) {
                List<String> values = plugin.getCaseService().getProfileIds();
                values.addAll(Arrays.asList("5", "10", "15"));
                return complete(values, args[2]);
            }
            if (args.length == 4) {
                return complete(Arrays.asList("5", "10", "15", "20"), args[3]);
            }
        }

        if ("keys".equals(subcommand) || "give".equals(subcommand) || "take".equals(subcommand) || "setamount".equals(subcommand)) {
            if (args.length == 2) {
                return complete(Arrays.stream(Bukkit.getOfflinePlayers())
                        .map(OfflinePlayer::getName)
                        .filter(name -> name != null && !name.isEmpty())
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList()), args[1]);
            }
            if (args.length == 3) {
                return complete(plugin.getCaseService().getProfileIds(), args[2]);
            }
            if (args.length == 4 && !"keys".equals(subcommand)) {
                return complete(Arrays.asList("1", "3", "5", "10", "32", "64"), args[3]);
            }
        }

        return Collections.emptyList();
    }

    private List<String> complete(List<String> values, String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        return values.stream()
                .distinct()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted()
                .collect(Collectors.toList());
    }

    private OfflinePlayer findPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }

        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.getName() != null && player.getName().equalsIgnoreCase(name)) {
                return player;
            }
        }
        return null;
    }

    private String playerName(OfflinePlayer player) {
        return player.getName() != null ? player.getName() : player.getUniqueId().toString();
    }

    private boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private int parsePositiveInt(String value, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private enum ChangeMode {
        GIVE(
                "messages.usage-give",
                "/cases give <player> <profile> <amount>",
                "messages.case-given",
                "Выдано %amount% ключ(ей) %case% игроку %player%."
        ),
        TAKE(
                "messages.usage-take",
                "/cases take <player> <profile> <amount>",
                "messages.case-taken",
                "Удалено %amount% ключ(ей) %case% у игрока %player%."
        ),
        SET(
                "messages.usage-setamount",
                "/cases setamount <player> <profile> <amount>",
                "messages.case-set",
                "Баланс %player% для %case% установлен на %amount%."
        );

        private final String usagePath;
        private final String usageFallback;
        private final String messagePath;
        private final String messageFallback;

        ChangeMode(String usagePath, String usageFallback, String messagePath, String messageFallback) {
            this.usagePath = usagePath;
            this.usageFallback = usageFallback;
            this.messagePath = messagePath;
            this.messageFallback = messageFallback;
        }
    }
}

