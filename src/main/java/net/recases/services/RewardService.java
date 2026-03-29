package net.recases.services;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class RewardService {

    private static final Pattern SAFE_TOKEN = Pattern.compile("[A-Za-z0-9_./:-]+");

    private final JavaPlugin plugin;
    private final TextFormatter textFormatter;

    public RewardService(JavaPlugin plugin, TextFormatter textFormatter) {
        this.plugin = plugin;
        this.textFormatter = textFormatter;
    }

    public void execute(Player player, List<String> commands) {
        if (commands == null) {
            return;
        }

        for (String commandLine : commands) {
            if (commandLine == null || commandLine.trim().isEmpty()) {
                continue;
            }

            String[] arguments = commandLine.split(";", 3);
            String action = arguments[0].trim().toLowerCase();
            try {
                switch (action) {
                    case "message":
                    case "msg":
                        if (arguments.length > 1) {
                            player.sendMessage(textFormatter.asComponent(replacePlayer(arguments[1], player)));
                        }
                        break;
                    case "broadcast":
                    case "bc":
                        if (arguments.length > 1) {
                            broadcast(textFormatter.asComponent(replacePlayer(arguments[1], player)));
                        }
                        break;
                    case "command":
                    case "cmd":
                        if (arguments.length > 1) {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), replacePlayer(arguments[1], player));
                        }
                        break;
                    case "lp-group-add":
                        if (arguments.length > 1) {
                            dispatchLuckPerms(player, "user %player% parent add " + requireSafe(arguments[1], "group"));
                        }
                        break;
                    case "lp-group-remove":
                        if (arguments.length > 1) {
                            dispatchLuckPerms(player, "user %player% parent remove " + requireSafe(arguments[1], "group"));
                        }
                        break;
                    case "lp-group-add-temp":
                        if (arguments.length > 2) {
                            dispatchLuckPerms(player, "user %player% parent addtemp "
                                    + requireSafe(arguments[1], "group") + " "
                                    + requireSafe(arguments[2], "duration"));
                        }
                        break;
                    case "lp-permission-set":
                        if (arguments.length > 2) {
                            dispatchLuckPerms(player, "user %player% permission set "
                                    + requireSafe(arguments[1], "permission") + " "
                                    + parseBoolean(arguments[2]));
                        }
                        break;
                    case "title":
                        player.showTitle(Title.title(
                                textFormatter.asComponent(arguments.length > 1 ? replacePlayer(arguments[1], player) : ""),
                                textFormatter.asComponent(arguments.length > 2 ? replacePlayer(arguments[2], player) : ""),
                                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2500), Duration.ofMillis(500))
                        ));
                        break;
                    default:
                        plugin.getLogger().warning(plugin.getConfig()
                                .getString("console.unknown-reward-action", "Unknown reward command action: %action%")
                                .replace("%action%", action));
                        break;
                }
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Skipped reward action '" + action + "': " + exception.getMessage());
            }
        }
    }

    private void broadcast(net.kyori.adventure.text.Component component) {
        Bukkit.getConsoleSender().sendMessage(component);
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(component);
        }
    }

    private String replacePlayer(String text, Player player) {
        return text.replace("%player%", player.getName()).replace("%player", player.getName());
    }

    private void dispatchLuckPerms(Player player, String commandSuffix) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), replacePlayer("lp " + commandSuffix, player));
    }

    private String requireSafe(String value, String field) {
        String trimmed = value == null ? "" : value.trim();
        if (!SAFE_TOKEN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Unsafe LuckPerms " + field + ": " + value);
        }
        return trimmed;
    }

    private String parseBoolean(String value) {
        String normalized = value == null ? "true" : value.trim().toLowerCase(Locale.ROOT);
        if (!"true".equals(normalized) && !"false".equals(normalized)) {
            throw new IllegalArgumentException("Invalid boolean value: " + value);
        }
        return normalized;
    }
}
