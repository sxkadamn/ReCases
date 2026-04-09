package net.recases.services;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.title.Title;
import net.recases.api.reward.RewardActionHandler;
import net.recases.api.reward.RewardActionRegistry;
import net.recases.app.PluginContext;
import net.recases.management.CaseItem;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public class RewardService implements RewardActionRegistry {

    private static final Pattern SAFE_TOKEN = Pattern.compile("[A-Za-z0-9_./:-]+");

    private final PluginContext plugin;
    private final TextFormatter textFormatter;
    private final ItemFactory itemFactory;
    private final Map<String, RewardActionHandler> handlers = new LinkedHashMap<>();

    public RewardService(PluginContext plugin, TextFormatter textFormatter, ItemFactory itemFactory) {
        this.plugin = plugin;
        this.textFormatter = textFormatter;
        this.itemFactory = itemFactory;
        registerBuiltIns();
    }

    @Override
    public synchronized boolean register(String actionId, RewardActionHandler handler) {
        String normalized = normalizeId(actionId);
        if (normalized.isEmpty() || handler == null || handlers.containsKey(normalized)) {
            return false;
        }
        handlers.put(normalized, handler);
        return true;
    }

    @Override
    public synchronized boolean unregister(String actionId) {
        return handlers.remove(normalizeId(actionId)) != null;
    }

    @Override
    public synchronized boolean isRegistered(String actionId) {
        return handlers.containsKey(normalizeId(actionId));
    }

    @Override
    public synchronized List<String> getRegisteredIds() {
        return List.copyOf(handlers.keySet());
    }

    public void execute(Player player, CaseItem reward) {
        if (reward == null) {
            return;
        }
        execute(createContext(player, "", "", "", reward, false, 0, "", false, false), reward.getActions());
    }

    public void execute(Player player, List<String> commands) {
        execute(createContext(player, "", "", "", null, false, 0, "", false, false), commands);
    }

    public void execute(Player player, List<String> commands, CaseItem reward) {
        execute(createContext(player, "", "", "", reward, false, 0, "", false, false), commands);
    }

    public void execute(CaseExecutionContext context, List<String> commands) {
        if (context == null || commands == null) {
            return;
        }

        for (String commandLine : commands) {
            if (commandLine == null || commandLine.trim().isEmpty()) {
                continue;
            }

            String[] arguments = commandLine.split(";", -1);
            String action = normalizeId(arguments[0]);
            RewardActionHandler handler = lookup(action);
            if (handler == null) {
                plugin.getLogger().warning(plugin.getConfig()
                        .getString("console.unknown-reward-action", "Неизвестное действие награды: %action%")
                        .replace("%action%", action));
                continue;
            }

            try {
                handler.execute(context, arguments);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Действие награды '" + action + "' пропущено: " + exception.getMessage());
            }
        }
    }

    public void rollback(CaseExecutionContext context, CaseItem reward) {
        if (context == null || reward == null) {
            return;
        }
        List<String> commands = reward.getRollbackActions().isEmpty() ? reward.getActions() : reward.getRollbackActions();
        rollback(context, commands);
    }

    public void rollback(CaseExecutionContext context, List<String> commands) {
        if (context == null || commands == null) {
            return;
        }

        for (int index = commands.size() - 1; index >= 0; index--) {
            String commandLine = commands.get(index);
            if (commandLine == null || commandLine.trim().isEmpty()) {
                continue;
            }

            String[] arguments = commandLine.split(";", -1);
            String action = normalizeId(arguments[0]);
            RewardActionHandler handler = lookup(action);
            if (handler == null) {
                continue;
            }

            try {
                handler.rollback(context.withRollback(true), arguments);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Rollback действия '" + action + "' пропущен: " + exception.getMessage());
            }
        }
    }

    public CaseExecutionContext createContext(Player player, String profileId, String runtimeId, String animationId, CaseItem reward,
                                              boolean guaranteedReward, int pityBeforeOpen, String triggerId,
                                              boolean recovered, boolean rollback) {
        if (player == null) {
            return null;
        }

        return CaseExecutionContext.builder(plugin, player)
                .caseProfileId(profileId)
                .runtimeId(runtimeId)
                .animationId(animationId)
                .rewardId(reward == null ? "" : reward.getId())
                .rewardName(reward == null ? "" : reward.getName())
                .guaranteedReward(guaranteedReward)
                .pityBeforeOpen(pityBeforeOpen)
                .triggerId(triggerId)
                .recovered(recovered)
                .rollback(rollback)
                .build();
    }

    private synchronized RewardActionHandler lookup(String actionId) {
        return handlers.get(actionId);
    }

    private void registerBuiltIns() {
        register("message", (context, arguments) -> {
            requireLength(arguments, 2, "message requires text.");
            context.getPlayer().sendMessage(textFormatter.asComponent(resolve(arguments[1], context)));
        });
        register("msg", lookupAlias("message"));
        register("broadcast", (context, arguments) -> {
            requireLength(arguments, 2, "broadcast requires text.");
            broadcast(textFormatter.asComponent(resolve(arguments[1], context)));
        });
        register("bc", lookupAlias("broadcast"));
        register("broadcast-hover", (context, arguments) -> {
            requireLength(arguments, 3, "broadcast-hover requires text and hover.");
            broadcastWithHover(resolve(arguments[1], context), resolve(arguments[2], context));
        });
        register("command", new RewardActionHandler() {
            @Override
            public void execute(net.recases.api.reward.RewardActionContext context, String[] arguments) {
                requireLength(arguments, 2, "command requires a value.");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolve(arguments[1], (CaseExecutionContext) context));
            }
        });
        register("cmd", lookupAlias("command"));
        register("random-command-group", new RewardActionHandler() {
            @Override
            public void execute(net.recases.api.reward.RewardActionContext context, String[] arguments) {
                requireLength(arguments, 2, "random-command-group has no commands.");
                dispatchRandomCommand((CaseExecutionContext) context, arguments[1]);
            }
        });
        register("random-cmd-group", lookupAlias("random-command-group"));
        register("item-give", new RewardActionHandler() {
            @Override
            public void execute(net.recases.api.reward.RewardActionContext context, String[] arguments) {
                giveItem((CaseExecutionContext) context, arguments);
            }

            @Override
            public void rollback(net.recases.api.reward.RewardActionContext context, String[] arguments) {
                rollbackItem((CaseExecutionContext) context, arguments);
            }
        });
        register("sound", new RewardActionHandler() {
            @Override
            public void execute(net.recases.api.reward.RewardActionContext context, String[] arguments) {
                playSound((CaseExecutionContext) context, arguments);
            }
        });
        register("firework", new RewardActionHandler() {
            @Override
            public void execute(net.recases.api.reward.RewardActionContext context, String[] arguments) {
                spawnFirework((CaseExecutionContext) context, arguments);
            }
        });
        register("effect", new RewardActionHandler() {
            @Override
            public void execute(net.recases.api.reward.RewardActionContext context, String[] arguments) {
                applyEffect((CaseExecutionContext) context, arguments, false);
            }

            @Override
            public void rollback(net.recases.api.reward.RewardActionContext context, String[] arguments) {
                applyEffect((CaseExecutionContext) context, arguments, true);
            }
        });
        register("lp-group-add", new RewardActionHandler() {
            @Override
            public void execute(net.recases.api.reward.RewardActionContext context, String[] arguments) {
                requireLength(arguments, 2, "lp-group-add requires a group.");
                dispatchLuckPerms((CaseExecutionContext) context, "user %player% parent add " + requireSafe(resolve(arguments[1], (CaseExecutionContext) context), "group"));
            }

            @Override
            public void rollback(net.recases.api.reward.RewardActionContext context, String[] arguments) {
                requireLength(arguments, 2, "lp-group-add requires a group.");
                dispatchLuckPerms((CaseExecutionContext) context, "user %player% parent remove " + requireSafe(resolve(arguments[1], (CaseExecutionContext) context), "group"));
            }
        });
        register("lp-group-remove", new RewardActionHandler() {
            @Override
            public void execute(net.recases.api.reward.RewardActionContext context, String[] arguments) {
                requireLength(arguments, 2, "lp-group-remove requires a group.");
                dispatchLuckPerms((CaseExecutionContext) context, "user %player% parent remove " + requireSafe(resolve(arguments[1], (CaseExecutionContext) context), "group"));
            }

            @Override
            public void rollback(net.recases.api.reward.RewardActionContext context, String[] arguments) {
                requireLength(arguments, 2, "lp-group-remove requires a group.");
                dispatchLuckPerms((CaseExecutionContext) context, "user %player% parent add " + requireSafe(resolve(arguments[1], (CaseExecutionContext) context), "group"));
            }
        });
        register("lp-group-add-temp", new RewardActionHandler() {
            @Override
            public void execute(net.recases.api.reward.RewardActionContext context, String[] arguments) {
                requireLength(arguments, 3, "lp-group-add-temp requires a group and duration.");
                dispatchLuckPerms((CaseExecutionContext) context, "user %player% parent addtemp "
                        + requireSafe(resolve(arguments[1], (CaseExecutionContext) context), "group") + " "
                        + requireSafe(resolve(arguments[2], (CaseExecutionContext) context), "duration"));
            }

            @Override
            public void rollback(net.recases.api.reward.RewardActionContext context, String[] arguments) {
                requireLength(arguments, 2, "lp-group-add-temp requires a group.");
                dispatchLuckPerms((CaseExecutionContext) context, "user %player% parent remove "
                        + requireSafe(resolve(arguments[1], (CaseExecutionContext) context), "group"));
            }
        });
        register("lp-permission-set", new RewardActionHandler() {
            @Override
            public void execute(net.recases.api.reward.RewardActionContext context, String[] arguments) {
                requireLength(arguments, 3, "lp-permission-set requires a permission and boolean.");
                dispatchLuckPerms((CaseExecutionContext) context, "user %player% permission set "
                        + requireSafe(resolve(arguments[1], (CaseExecutionContext) context), "permission") + " "
                        + parseBoolean(resolve(arguments[2], (CaseExecutionContext) context)));
            }

            @Override
            public void rollback(net.recases.api.reward.RewardActionContext context, String[] arguments) {
                requireLength(arguments, 3, "lp-permission-set requires a permission and boolean.");
                boolean current = Boolean.parseBoolean(parseBoolean(resolve(arguments[2], (CaseExecutionContext) context)));
                dispatchLuckPerms((CaseExecutionContext) context, "user %player% permission set "
                        + requireSafe(resolve(arguments[1], (CaseExecutionContext) context), "permission") + " "
                        + String.valueOf(!current));
            }
        });
        register("title", (context, arguments) -> context.getPlayer().showTitle(Title.title(
                textFormatter.asComponent(arguments.length > 1 ? resolve(arguments[1], (CaseExecutionContext) context) : ""),
                textFormatter.asComponent(arguments.length > 2 ? resolve(arguments[2], (CaseExecutionContext) context) : ""),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2500), Duration.ofMillis(500))
        )));
        register("key-give", new RewardActionHandler() {
            @Override
            public void execute(net.recases.api.reward.RewardActionContext context, String[] arguments) {
                requireLength(arguments, 3, "key-give requires profile and amount.");
                plugin.getStorage().addCase(context.getPlayer(), resolve(arguments[1], (CaseExecutionContext) context), parsePositiveInt(resolve(arguments[2], (CaseExecutionContext) context), "amount"));
            }

            @Override
            public void rollback(net.recases.api.reward.RewardActionContext context, String[] arguments) {
                requireLength(arguments, 3, "key-give requires profile and amount.");
                plugin.getStorage().removeCase(context.getPlayer(), resolve(arguments[1], (CaseExecutionContext) context), parsePositiveInt(resolve(arguments[2], (CaseExecutionContext) context), "amount"));
            }
        });
        register("key-take", new RewardActionHandler() {
            @Override
            public void execute(net.recases.api.reward.RewardActionContext context, String[] arguments) {
                requireLength(arguments, 3, "key-take requires profile and amount.");
                plugin.getStorage().removeCase(context.getPlayer(), resolve(arguments[1], (CaseExecutionContext) context), parsePositiveInt(resolve(arguments[2], (CaseExecutionContext) context), "amount"));
            }

            @Override
            public void rollback(net.recases.api.reward.RewardActionContext context, String[] arguments) {
                requireLength(arguments, 3, "key-take requires profile and amount.");
                plugin.getStorage().addCase(context.getPlayer(), resolve(arguments[1], (CaseExecutionContext) context), parsePositiveInt(resolve(arguments[2], (CaseExecutionContext) context), "amount"));
            }
        });
    }

    private RewardActionHandler lookupAlias(String actionId) {
        return new RewardActionHandler() {
            @Override
            public void execute(net.recases.api.reward.RewardActionContext context, String[] arguments) {
                RewardActionHandler target = lookup(actionId);
                if (target != null) {
                    target.execute(context, arguments);
                }
            }

            @Override
            public void rollback(net.recases.api.reward.RewardActionContext context, String[] arguments) {
                RewardActionHandler target = lookup(actionId);
                if (target != null) {
                    target.rollback(context, arguments);
                }
            }
        };
    }

    private void giveItem(CaseExecutionContext context, String[] arguments) {
        ParsedItemAction parsed = parseItemAction(context, arguments);
        int remaining = parsed.amount;
        while (remaining > 0) {
            ItemStack batch = parsed.item.clone();
            batch.setAmount(Math.min(batch.getMaxStackSize(), remaining));
            Map<Integer, ItemStack> leftovers = context.getPlayer().getInventory().addItem(batch);
            for (ItemStack leftover : leftovers.values()) {
                context.getPlayer().getWorld().dropItemNaturally(context.getPlayer().getLocation(), leftover);
            }
            remaining -= batch.getAmount();
        }
    }

    private void rollbackItem(CaseExecutionContext context, String[] arguments) {
        ParsedItemAction parsed = parseItemAction(context, arguments);
        removeMatchingItems(context.getPlayer().getInventory(), parsed.item, parsed.amount);
    }

    private ParsedItemAction parseItemAction(CaseExecutionContext context, String[] arguments) {
        ItemStack stack;
        int amount;
        if (arguments.length > 1 && !arguments[1].trim().isEmpty() && !isInteger(resolve(arguments[1], context))) {
            int amountIndex = arguments.length > 2 && isInteger(resolve(arguments[arguments.length - 1], context)) ? arguments.length - 1 : -1;
            String definition = amountIndex == -1
                    ? String.join(";", Arrays.stream(arguments, 1, arguments.length).map(part -> resolve(part, context)).toArray(String[]::new))
                    : String.join(";", Arrays.stream(arguments, 1, amountIndex).map(part -> resolve(part, context)).toArray(String[]::new));
            String displayName = context.getRewardName().isEmpty() ? "Reward item" : context.getRewardName();
            stack = itemFactory.createActionItem(definition, displayName);
            amount = amountIndex == -1 ? 1 : Math.max(1, Integer.parseInt(resolve(arguments[amountIndex], context)));
        } else {
            if (context.getRewardId().isEmpty() || context.getCaseProfileId().isEmpty()) {
                throw new IllegalArgumentException("item-give without reward context requires an explicit item definition.");
            }
            CaseItem reward = plugin.getCaseService().getReward(context.getCaseProfileId(), context.getRewardId());
            if (reward == null) {
                throw new IllegalArgumentException("Reward '" + context.getRewardId() + "' is missing.");
            }
            stack = reward.getIcon();
            amount = arguments.length > 1 && !arguments[1].trim().isEmpty()
                    ? Math.max(1, Integer.parseInt(resolve(arguments[1], context)))
                    : 1;
        }
        return new ParsedItemAction(stack, amount);
    }

    private void removeMatchingItems(PlayerInventory inventory, ItemStack target, int amount) {
        int remaining = Math.max(1, amount);
        for (int slot = 0; slot < inventory.getSize() && remaining > 0; slot++) {
            ItemStack content = inventory.getItem(slot);
            if (content == null || !content.isSimilar(target)) {
                continue;
            }

            int remove = Math.min(remaining, content.getAmount());
            content.setAmount(content.getAmount() - remove);
            if (content.getAmount() <= 0) {
                inventory.setItem(slot, null);
            } else {
                inventory.setItem(slot, content);
            }
            remaining -= remove;
        }
    }

    private void playSound(CaseExecutionContext context, String[] arguments) {
        requireLength(arguments, 2, "sound action requires a sound name.");
        Sound sound = Sound.valueOf(resolve(arguments[1], context).trim().toUpperCase(Locale.ROOT));
        float volume = arguments.length > 2 && !arguments[2].trim().isEmpty() ? Float.parseFloat(resolve(arguments[2], context)) : 1.0F;
        float pitch = arguments.length > 3 && !arguments[3].trim().isEmpty() ? Float.parseFloat(resolve(arguments[3], context)) : 1.0F;
        context.getPlayer().playSound(context.getPlayer().getLocation(), sound, volume, pitch);
    }

    private void spawnFirework(CaseExecutionContext context, String[] arguments) {
        String colorSpec = arguments.length > 1 && !arguments[1].trim().isEmpty() ? resolve(arguments[1], context) : "#58a6ff";
        String typeSpec = arguments.length > 2 && !arguments[2].trim().isEmpty() ? resolve(arguments[2], context) : "BALL";
        int power = arguments.length > 3 && !arguments[3].trim().isEmpty() ? Math.max(0, Integer.parseInt(resolve(arguments[3], context))) : 1;

        Location location = context.getPlayer().getLocation().clone().add(0.0D, 1.0D, 0.0D);
        Firework firework = context.getPlayer().getWorld().spawn(location, Firework.class);
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

    private void applyEffect(CaseExecutionContext context, String[] arguments, boolean rollback) {
        requireLength(arguments, 2, "effect action requires a potion effect type.");
        PotionEffectType type = PotionEffectType.getByName(resolve(arguments[1], context).trim().toUpperCase(Locale.ROOT));
        if (type == null) {
            throw new IllegalArgumentException("Unknown potion effect: " + arguments[1]);
        }
        if (rollback) {
            context.getPlayer().removePotionEffect(type);
            return;
        }

        int duration = arguments.length > 2 && !arguments[2].trim().isEmpty() ? Math.max(1, Integer.parseInt(resolve(arguments[2], context))) : 100;
        int amplifier = arguments.length > 3 && !arguments[3].trim().isEmpty() ? Math.max(0, Integer.parseInt(resolve(arguments[3], context))) : 0;
        context.getPlayer().addPotionEffect(new PotionEffect(type, duration, amplifier));
    }

    private void dispatchRandomCommand(CaseExecutionContext context, String rawGroup) {
        String[] options = rawGroup.split("\\|");
        List<String> commands = new ArrayList<>();
        for (String option : options) {
            String trimmed = resolve(option, context).trim();
            if (!trimmed.isEmpty()) {
                commands.add(trimmed);
            }
        }
        if (commands.isEmpty()) {
            throw new IllegalArgumentException("random-command-group has no commands.");
        }

        String chosen = commands.get(ThreadLocalRandom.current().nextInt(commands.size()));
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), chosen);
    }

    private void broadcastWithHover(String text, String hover) {
        Component component = textFormatter.asComponent(text).hoverEvent(HoverEvent.showText(textFormatter.asComponent(hover)));
        Bukkit.getConsoleSender().sendMessage(textFormatter.asComponent(text));
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

        return switch (normalized.toUpperCase(Locale.ROOT)) {
            case "RED" -> Color.RED;
            case "ORANGE" -> Color.ORANGE;
            case "YELLOW" -> Color.YELLOW;
            case "GREEN" -> Color.LIME;
            case "BLUE" -> Color.BLUE;
            case "AQUA", "CYAN" -> Color.AQUA;
            case "PURPLE" -> Color.PURPLE;
            case "WHITE" -> Color.WHITE;
            case "BLACK" -> Color.BLACK;
            case "FUCHSIA", "PINK" -> Color.FUCHSIA;
            default -> throw new IllegalArgumentException("Unknown firework color: " + value);
        };
    }

    private void dispatchLuckPerms(net.recases.api.reward.RewardActionContext context, String commandSuffix) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolve("lp " + commandSuffix, context));
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

    private int parsePositiveInt(String value, String field) {
        try {
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Некорректное число для " + field + ": " + value);
        }
    }

    private boolean isInteger(String value) {
        try {
            Integer.parseInt(value.trim());
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private void requireLength(String[] arguments, int required, String message) {
        if (arguments.length < required) {
            throw new IllegalArgumentException(message);
        }
    }

    private String resolve(String text, net.recases.api.reward.RewardActionContext context) {
        String resolved = context.replaceTokens(text);
        return resolved.replace("%player", context.getPlayer().getName());
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record ParsedItemAction(ItemStack item, int amount) {
    }
}
