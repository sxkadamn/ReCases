package net.recases.services;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.title.Title;
import net.recases.management.CaseItem;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public class RewardService {

    private static final Pattern SAFE_TOKEN = Pattern.compile("[A-Za-z0-9_./:-]+");

    private final JavaPlugin plugin;
    private final TextFormatter textFormatter;
    private final ItemFactory itemFactory;

    public RewardService(JavaPlugin plugin, TextFormatter textFormatter, ItemFactory itemFactory) {
        this.plugin = plugin;
        this.textFormatter = textFormatter;
        this.itemFactory = itemFactory;
    }

    public void execute(Player player, CaseItem reward) {
        if (reward == null) {
            return;
        }
        execute(player, reward.getActions(), reward);
    }

    public void execute(Player player, List<String> commands) {
        execute(player, commands, null);
    }

    public void execute(Player player, List<String> commands, CaseItem reward) {
        if (commands == null) {
            return;
        }

        for (String commandLine : commands) {
            if (commandLine == null || commandLine.trim().isEmpty()) {
                continue;
            }

            String[] arguments = commandLine.split(";", -1);
            String action = arguments[0].trim().toLowerCase(Locale.ROOT);
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
                    case "broadcast-hover":
                        if (arguments.length > 2) {
                            broadcastWithHover(arguments[1], arguments[2], player);
                        }
                        break;
                    case "command":
                    case "cmd":
                        if (arguments.length > 1) {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), replacePlayer(arguments[1], player));
                        }
                        break;
                    case "random-command-group":
                    case "random-cmd-group":
                        if (arguments.length > 1) {
                            dispatchRandomCommand(player, arguments[1]);
                        }
                        break;
                    case "item-give":
                        giveItem(player, reward, arguments);
                        break;
                    case "sound":
                        playSound(player, arguments);
                        break;
                    case "firework":
                        spawnFirework(player, arguments);
                        break;
                    case "effect":
                        applyEffect(player, arguments);
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
                                .getString("console.unknown-reward-action", "Неизвестное действие награды: %action%")
                                .replace("%action%", action));
                        break;
                }
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Действие награды '" + action + "' пропущено: " + exception.getMessage());
            }
        }
    }

    private void giveItem(Player player, CaseItem reward, String[] arguments) {
        ItemStack stack;
        int amount;
        if (arguments.length > 1 && !arguments[1].trim().isEmpty() && !isInteger(arguments[1])) {
            stack = itemFactory.createActionItem(arguments[1], reward == null ? "Reward item" : reward.getName());
            amount = arguments.length > 2 ? Math.max(1, Integer.parseInt(arguments[2])) : 1;
        } else {
            if (reward == null) {
                throw new IllegalArgumentException("item-give without reward context requires an explicit item definition.");
            }
            stack = reward.getIcon();
            amount = arguments.length > 1 && !arguments[1].trim().isEmpty() ? Math.max(1, Integer.parseInt(arguments[1])) : 1;
        }

        int remaining = amount;
        while (remaining > 0) {
            ItemStack batch = stack.clone();
            batch.setAmount(Math.min(batch.getMaxStackSize(), remaining));
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(batch);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            remaining -= batch.getAmount();
        }
    }

    private void playSound(Player player, String[] arguments) {
        if (arguments.length < 2) {
            throw new IllegalArgumentException("sound action requires a sound name.");
        }

        Sound sound = Sound.valueOf(arguments[1].trim().toUpperCase(Locale.ROOT));
        float volume = arguments.length > 2 && !arguments[2].trim().isEmpty() ? Float.parseFloat(arguments[2]) : 1.0F;
        float pitch = arguments.length > 3 && !arguments[3].trim().isEmpty() ? Float.parseFloat(arguments[3]) : 1.0F;
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private void spawnFirework(Player player, String[] arguments) {
        String colorSpec = arguments.length > 1 && !arguments[1].trim().isEmpty() ? arguments[1] : "#58a6ff";
        String typeSpec = arguments.length > 2 && !arguments[2].trim().isEmpty() ? arguments[2] : "BALL";
        int power = arguments.length > 3 && !arguments[3].trim().isEmpty() ? Math.max(0, Integer.parseInt(arguments[3])) : 1;

        Location location = player.getLocation().clone().add(0.0D, 1.0D, 0.0D);
        Firework firework = player.getWorld().spawn(location, Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        FireworkEffect.Builder builder = FireworkEffect.builder()
                .with(FireworkEffect.Type.valueOf(typeSpec.trim().toUpperCase(Locale.ROOT)))
                .flicker(true)
                .trail(true);
        for (Color color : parseColors(colorSpec)) {
            builder.withColor(color);
        }
        meta.addEffect(builder.build());
        meta.setPower(power);
        firework.setFireworkMeta(meta);
        Bukkit.getScheduler().runTaskLater(plugin, firework::detonate, 2L);
    }

    private void applyEffect(Player player, String[] arguments) {
        if (arguments.length < 2) {
            throw new IllegalArgumentException("effect action requires a potion effect type.");
        }

        PotionEffectType type = PotionEffectType.getByName(arguments[1].trim().toUpperCase(Locale.ROOT));
        if (type == null) {
            throw new IllegalArgumentException("Unknown potion effect: " + arguments[1]);
        }

        int duration = arguments.length > 2 && !arguments[2].trim().isEmpty() ? Math.max(1, Integer.parseInt(arguments[2])) : 100;
        int amplifier = arguments.length > 3 && !arguments[3].trim().isEmpty() ? Math.max(0, Integer.parseInt(arguments[3])) : 0;
        player.addPotionEffect(new PotionEffect(type, duration, amplifier));
    }

    private void dispatchRandomCommand(Player player, String rawGroup) {
        String[] options = rawGroup.split("\\|");
        List<String> commands = new ArrayList<>();
        for (String option : options) {
            String trimmed = option.trim();
            if (!trimmed.isEmpty()) {
                commands.add(trimmed);
            }
        }
        if (commands.isEmpty()) {
            throw new IllegalArgumentException("random-command-group has no commands.");
        }

        String chosen = commands.get(ThreadLocalRandom.current().nextInt(commands.size()));
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), replacePlayer(chosen, player));
    }

    private void broadcastWithHover(String text, String hover, Player player) {
        Component component = textFormatter.asComponent(replacePlayer(text, player))
                .hoverEvent(HoverEvent.showText(textFormatter.asComponent(replacePlayer(hover, player))));
        Bukkit.getConsoleSender().sendMessage(textFormatter.asComponent(replacePlayer(text, player)));
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(component);
        }
    }

    private void broadcast(Component component) {
        Bukkit.getConsoleSender().sendMessage(component);
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(component);
        }
    }

    private List<Color> parseColors(String raw) {
        List<Color> result = new ArrayList<>();
        for (String token : raw.split(",")) {
            String value = token.trim();
            if (value.isEmpty()) {
                continue;
            }
            result.add(parseColor(value));
        }
        if (result.isEmpty()) {
            result.add(Color.fromRGB(88, 166, 255));
        }
        return result;
    }

    private Color parseColor(String value) {
        String normalized = value.trim();
        if (normalized.startsWith("#") && normalized.length() == 7) {
            return Color.fromRGB(
                    Integer.parseInt(normalized.substring(1, 3), 16),
                    Integer.parseInt(normalized.substring(3, 5), 16),
                    Integer.parseInt(normalized.substring(5, 7), 16)
            );
        }

        switch (normalized.toUpperCase(Locale.ROOT)) {
            case "RED":
                return Color.RED;
            case "ORANGE":
                return Color.ORANGE;
            case "YELLOW":
                return Color.YELLOW;
            case "GREEN":
                return Color.LIME;
            case "BLUE":
                return Color.BLUE;
            case "AQUA":
            case "CYAN":
                return Color.AQUA;
            case "PURPLE":
                return Color.PURPLE;
            case "WHITE":
                return Color.WHITE;
            case "BLACK":
                return Color.BLACK;
            case "FUCHSIA":
            case "PINK":
                return Color.FUCHSIA;
            default:
                throw new IllegalArgumentException("Unknown firework color: " + value);
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
            throw new IllegalArgumentException("Небезопасное значение LuckPerms для поля " + field + ": " + value);
        }
        return trimmed;
    }

    private String parseBoolean(String value) {
        String normalized = value == null ? "true" : value.trim().toLowerCase(Locale.ROOT);
        if (!"true".equals(normalized) && !"false".equals(normalized)) {
            throw new IllegalArgumentException("Некорректное булево значение: " + value);
        }
        return normalized;
    }

    private boolean isInteger(String value) {
        try {
            Integer.parseInt(value.trim());
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
