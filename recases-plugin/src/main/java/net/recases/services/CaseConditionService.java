package net.recases.services;

import me.clip.placeholderapi.PlaceholderAPI;
import net.recases.api.condition.ConditionContext;
import net.recases.api.condition.ConditionHandler;
import net.recases.api.condition.ConditionRegistry;
import net.recases.app.PluginContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CaseConditionService implements ConditionRegistry {

    private final PluginContext plugin;
    private final Map<String, ConditionHandler> handlers = new LinkedHashMap<>();

    public CaseConditionService(PluginContext plugin) {
        this.plugin = plugin;
        registerBuiltIns();
    }

    @Override
    public synchronized boolean register(String conditionId, ConditionHandler handler) {
        String normalized = normalizeId(conditionId);
        if (normalized.isEmpty() || handler == null || handlers.containsKey(normalized)) {
            return false;
        }
        handlers.put(normalized, handler);
        return true;
    }

    @Override
    public synchronized boolean unregister(String conditionId) {
        return handlers.remove(normalizeId(conditionId)) != null;
    }

    @Override
    public synchronized boolean isRegistered(String conditionId) {
        return handlers.containsKey(normalizeId(conditionId));
    }

    @Override
    public synchronized List<String> getRegisteredIds() {
        return List.copyOf(handlers.keySet());
    }

    public boolean matches(CaseExecutionContext context, List<String> conditions) {
        if (context == null || conditions == null || conditions.isEmpty()) {
            return true;
        }

        for (String line : conditions) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }

            String[] arguments = line.split(";", -1);
            String conditionId = normalizeId(arguments[0]);
            ConditionHandler handler;
            synchronized (this) {
                handler = handlers.get(conditionId);
            }
            if (handler == null) {
                plugin.getLogger().warning("Unknown condition '" + conditionId + "' in profile/reward configuration.");
                return false;
            }

            try {
                if (!handler.test(context, arguments)) {
                    return false;
                }
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Condition '" + conditionId + "' failed validation: " + exception.getMessage());
                return false;
            }
        }
        return true;
    }

    private void registerBuiltIns() {
        register("permission", (context, arguments) -> {
            requireLength(arguments, 2, "permission requires a node");
            return context.getPlayer().hasPermission(resolve(arguments[1], context));
        });
        register("world", (context, arguments) -> {
            requireLength(arguments, 2, "world requires a world name");
            return context.getPlayer().getWorld().getName().equalsIgnoreCase(resolve(arguments[1], context));
        });
        register("bedrock", (context, arguments) -> {
            boolean expected = arguments.length < 2 || parseBoolean(resolve(arguments[1], context));
            return plugin.getBedrockSupport().isBedrockPlayer(context.getPlayer()) == expected;
        });
        register("pity-min", (context, arguments) -> {
            requireLength(arguments, 2, "pity-min requires a value");
            return context.getPityBeforeOpen() >= parseInt(resolve(arguments[1], context), "pity-min");
        });
        register("pity-max", (context, arguments) -> {
            requireLength(arguments, 2, "pity-max requires a value");
            return context.getPityBeforeOpen() <= parseInt(resolve(arguments[1], context), "pity-max");
        });
        register("opens-min", (context, arguments) -> compareStat(context, arguments, StatField.OPENS, true));
        register("opens-max", (context, arguments) -> compareStat(context, arguments, StatField.OPENS, false));
        register("rare-wins-min", (context, arguments) -> compareStat(context, arguments, StatField.RARE_WINS, true));
        register("rare-wins-max", (context, arguments) -> compareStat(context, arguments, StatField.RARE_WINS, false));
        register("guaranteed-wins-min", (context, arguments) -> compareStat(context, arguments, StatField.GUARANTEED_WINS, true));
        register("guaranteed-wins-max", (context, arguments) -> compareStat(context, arguments, StatField.GUARANTEED_WINS, false));
        register("placeholder-equals", (context, arguments) -> {
            requireLength(arguments, 3, "placeholder-equals requires a placeholder and expected value");
            if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
                return false;
            }
            String actual = PlaceholderAPI.setPlaceholders(context.getPlayer(), resolve(arguments[1], context));
            return actual.equalsIgnoreCase(resolve(arguments[2], context));
        });
    }

    private boolean compareStat(ConditionContext context, String[] arguments, StatField field, boolean minimumCheck) {
        requireLength(arguments, 2, field.id + " requires a value");
        int target = parseInt(resolve(arguments[1], context), field.id);
        String profileId = arguments.length > 2 && !arguments[2].trim().isEmpty()
                ? resolve(arguments[2], context).toLowerCase(Locale.ROOT)
                : context.getCaseProfileId();
        int actual = switch (field) {
            case OPENS -> plugin.getStats().getOpens(context.getPlayer(), profileId);
            case RARE_WINS -> plugin.getStats().getRareWins(context.getPlayer(), profileId);
            case GUARANTEED_WINS -> plugin.getStats().getGuaranteedWins(context.getPlayer(), profileId);
        };
        return minimumCheck ? actual >= target : actual <= target;
    }

    private void requireLength(String[] arguments, int required, String message) {
        if (arguments.length < required) {
            throw new IllegalArgumentException(message);
        }
    }

    private int parseInt(String value, String field) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " expects an integer: " + value);
        }
    }

    private boolean parseBoolean(String value) {
        String normalized = value == null ? "true" : value.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized)) {
            return false;
        }
        throw new IllegalArgumentException("Invalid boolean: " + value);
    }

    private String resolve(String value, ConditionContext context) {
        String resolved = context.replaceTokens(value);
        return resolved.replace("%player", context.getPlayer().getName());
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private enum StatField {
        OPENS("opens"),
        RARE_WINS("rare-wins"),
        GUARANTEED_WINS("guaranteed-wins");

        private final String id;

        StatField(String id) {
            this.id = id;
        }
    }
}
