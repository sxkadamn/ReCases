package net.recases.services;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.recases.app.PluginContext;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class DiscordBotService extends ListenerAdapter implements AutoCloseable {

    private final PluginContext plugin;
    private JDA jda;
    private boolean enabled;
    private String token = "";
    private String guildId = "";
    private String moderationChannelId = "";
    private final List<String> allowedRoleIds = new ArrayList<>();

    public DiscordBotService(PluginContext plugin) {
        this.plugin = plugin;
    }

    public synchronized void reload() {
        shutdownJda();
        enabled = plugin.getConfig().getBoolean("settings.integrations.discord-bot.enabled", false);
        token = plugin.getConfig().getString("settings.integrations.discord-bot.token", "").trim();
        guildId = plugin.getConfig().getString("settings.integrations.discord-bot.guild-id", "").trim();
        moderationChannelId = plugin.getConfig().getString("settings.integrations.discord-bot.moderation-channel-id", "").trim();

        allowedRoleIds.clear();
        for (String roleId : plugin.getConfig().getStringList("settings.integrations.discord-bot.allowed-role-ids")) {
            if (roleId != null && !roleId.trim().isEmpty()) {
                allowedRoleIds.add(roleId.trim());
            }
        }

        if (!enabled || token.isEmpty()) {
            return;
        }

        try {
            jda = JDABuilder.createLight(token)
                    .addEventListeners(this)
                    .build();
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Failed to start Discord bot: " + exception.getMessage());
            jda = null;
        }
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        registerCommands(event.getJDA());
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!isAllowed(event)) {
            event.reply("Not enough permissions.").setEphemeral(true).queue();
            return;
        }

        switch (event.getName()) {
            case "recases-audit" -> handleAudit(event);
            case "recases-givekey" -> handleGiveKey(event);
            case "recases-promocode-create" -> handlePromoCreate(event);
            case "recases-promocode-delete" -> handlePromoDelete(event);
            case "recases-promocode-list" -> handlePromoList(event);
            default -> event.reply("Unknown command.").setEphemeral(true).queue();
        }
    }

    public void sendModerationLog(String source, String actor, String action, String details) {
        if (jda == null || moderationChannelId.isEmpty()) {
            return;
        }

        StringBuilder message = new StringBuilder("[").append(source).append("] ")
                .append(actor == null || actor.isEmpty() ? "unknown" : actor)
                .append(" -> ")
                .append(action == null || action.isEmpty() ? "action" : action);
        if (details != null && !details.trim().isEmpty()) {
            message.append(" | ").append(details);
        }

        MessageChannel channel = jda.getChannelById(MessageChannel.class, moderationChannelId);
        if (channel != null) {
            channel.sendMessage(message.toString()).queue();
        }
    }

    @Override
    public synchronized void close() {
        shutdownJda();
    }

    private void registerCommands(JDA instance) {
        List<CommandData> commands = List.of(
                Commands.slash("recases-audit", "Show recent ReCases reward history")
                        .addOption(OptionType.STRING, "player", "Minecraft player", false)
                        .addOption(OptionType.INTEGER, "limit", "How many entries to show", false),
                Commands.slash("recases-givekey", "Give ReCases keys to a player")
                        .addOption(OptionType.STRING, "player", "Minecraft player", true)
                        .addOption(OptionType.STRING, "profile", "Case profile id", true)
                        .addOption(OptionType.INTEGER, "amount", "How many keys to add", true),
                Commands.slash("recases-promocode-create", "Create a ReCases promo code")
                        .addOption(OptionType.STRING, "code", "Promo code", true)
                        .addOption(OptionType.STRING, "profile", "Case profile id", true)
                        .addOption(OptionType.INTEGER, "amount", "Keys per redemption", true)
                        .addOption(OptionType.INTEGER, "max_uses", "How many times it can be redeemed", true),
                Commands.slash("recases-promocode-delete", "Delete a ReCases promo code")
                        .addOption(OptionType.STRING, "code", "Promo code", true),
                Commands.slash("recases-promocode-list", "List active ReCases promo codes")
        );

        if (!guildId.isEmpty()) {
            Guild guild = instance.getGuildById(guildId);
            if (guild != null) {
                guild.updateCommands().addCommands(commands).queue();
                return;
            }
        }
        instance.updateCommands().addCommands(commands).queue();
    }

    private void handleAudit(SlashCommandInteractionEvent event) {
        String playerName = event.getOption("player", "", OptionMapping::getAsString);
        int limit = clamp(event.getOption("limit", 10, OptionMapping::getAsInt), 1, 10);

        try {
            String response = callSync(() -> {
                OfflinePlayer target = playerName.isEmpty() ? null : findPlayer(playerName);
                if (!playerName.isEmpty() && target == null) {
                    return "Minecraft player not found.";
                }
                List<RewardAuditService.AuditEntry> entries = plugin.getRewardAudit().getRecentEntries(target == null ? null : target.getUniqueId(), limit);
                if (entries.isEmpty()) {
                    return "No audit data.";
                }
                List<String> lines = new ArrayList<>();
                for (RewardAuditService.AuditEntry entry : entries) {
                    String state = entry.isRolledBack() ? "rolled-back" : entry.isRestored() ? "restored" : "active";
                    lines.add(entry.getPlayerName() + " -> " + entry.getRewardName() + " | case=" + entry.getCaseProfile() + " | state=" + state + " | tx=" + shortTransaction(entry.getTransactionId().toString()));
                }
                return String.join("\n", lines);
            });
            event.reply(response).setEphemeral(true).queue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            event.reply("Discord audit request was interrupted.").setEphemeral(true).queue();
        } catch (ExecutionException | TimeoutException exception) {
            event.reply("Failed to load audit data: " + exception.getMessage()).setEphemeral(true).queue();
        }
    }

    private void handleGiveKey(SlashCommandInteractionEvent event) {
        String playerName = requiredString(event, "player");
        String profileId = requiredString(event, "profile").toLowerCase(Locale.ROOT);
        int amount = clamp(event.getOption("amount", 1, OptionMapping::getAsInt), 1, 100000);

        try {
            String response = callSync(() -> {
                OfflinePlayer target = findPlayer(playerName);
                if (target == null) {
                    return "Minecraft player not found.";
                }
                if (!plugin.getCaseService().hasProfile(profileId)) {
                    return "Case profile was not found.";
                }
                plugin.getStorage().addCase(target, profileId, amount);
                sendModerationLog("discord", event.getUser().getName(), "givekey", "player=" + playerName + " profile=" + profileId + " amount=" + amount);
                return "Added " + amount + " keys for profile '" + profileId + "' to " + playerName + ".";
            });
            event.reply(response).setEphemeral(true).queue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            event.reply("Request was interrupted.").setEphemeral(true).queue();
        } catch (ExecutionException | TimeoutException exception) {
            event.reply("Failed to give keys: " + exception.getMessage()).setEphemeral(true).queue();
        }
    }

    private void handlePromoCreate(SlashCommandInteractionEvent event) {
        String code = requiredString(event, "code");
        String profileId = requiredString(event, "profile").toLowerCase(Locale.ROOT);
        int amount = clamp(event.getOption("amount", 1, OptionMapping::getAsInt), 1, 100000);
        int maxUses = clamp(event.getOption("max_uses", 1, OptionMapping::getAsInt), 1, 100000);

        try {
            String response = callSync(() -> {
                if (!plugin.getCaseService().hasProfile(profileId)) {
                    return "Case profile was not found.";
                }
                boolean created = plugin.getPromoCodes().createCode(code, profileId, amount, maxUses, event.getUser().getName());
                if (!created) {
                    return "Failed to create promo code.";
                }
                sendModerationLog("discord", event.getUser().getName(), "promocode-create", "code=" + code.toLowerCase(Locale.ROOT) + " profile=" + profileId + " amount=" + amount + " maxUses=" + maxUses);
                return "Promo code '" + code.toLowerCase(Locale.ROOT) + "' created.";
            });
            event.reply(response).setEphemeral(true).queue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            event.reply("Request was interrupted.").setEphemeral(true).queue();
        } catch (ExecutionException | TimeoutException exception) {
            event.reply("Failed to create promo code: " + exception.getMessage()).setEphemeral(true).queue();
        }
    }

    private void handlePromoDelete(SlashCommandInteractionEvent event) {
        String code = requiredString(event, "code");
        try {
            String response = callSync(() -> {
                boolean deleted = plugin.getPromoCodes().deleteCode(code);
                if (!deleted) {
                    return "Promo code was not found.";
                }
                sendModerationLog("discord", event.getUser().getName(), "promocode-delete", "code=" + code.toLowerCase(Locale.ROOT));
                return "Promo code '" + code.toLowerCase(Locale.ROOT) + "' deleted.";
            });
            event.reply(response).setEphemeral(true).queue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            event.reply("Request was interrupted.").setEphemeral(true).queue();
        } catch (ExecutionException | TimeoutException exception) {
            event.reply("Failed to delete promo code: " + exception.getMessage()).setEphemeral(true).queue();
        }
    }

    private void handlePromoList(SlashCommandInteractionEvent event) {
        try {
            String response = callSync(() -> {
                List<PromoCodeService.PromoCodeEntry> entries = plugin.getPromoCodes().listCodes(10);
                if (entries.isEmpty()) {
                    return "No promo codes.";
                }
                List<String> lines = new ArrayList<>();
                for (PromoCodeService.PromoCodeEntry entry : entries) {
                    lines.add(entry.code() + " | profile=" + entry.profileId() + " | amount=" + entry.amount() + " | uses=" + entry.usedCount() + "/" + entry.maxUses());
                }
                return String.join("\n", lines);
            });
            event.reply(response).setEphemeral(true).queue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            event.reply("Request was interrupted.").setEphemeral(true).queue();
        } catch (ExecutionException | TimeoutException exception) {
            event.reply("Failed to list promo codes: " + exception.getMessage()).setEphemeral(true).queue();
        }
    }

    private boolean isAllowed(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (member == null) {
            return false;
        }
        if (!allowedRoleIds.isEmpty()) {
            return member.getRoles().stream().anyMatch(role -> allowedRoleIds.contains(role.getId()));
        }
        return member.hasPermission(Permission.ADMINISTRATOR);
    }

    private OfflinePlayer findPlayer(String name) {
        org.bukkit.entity.Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        return Arrays.stream(Bukkit.getOfflinePlayers())
                .filter(player -> player.getName() != null && player.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    private String requiredString(SlashCommandInteractionEvent event, String optionName) {
        OptionMapping option = event.getOption(optionName);
        return option == null ? "" : option.getAsString();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String shortTransaction(String value) {
        return value.length() <= 8 ? value : value.substring(0, 8);
    }

    private String callSync(Task task) throws InterruptedException, ExecutionException, TimeoutException {
        return Bukkit.getScheduler().callSyncMethod(plugin, task::run).get(10, TimeUnit.SECONDS);
    }

    private synchronized void shutdownJda() {
        if (jda == null) {
            return;
        }
        jda.shutdownNow();
        jda = null;
    }

    @FunctionalInterface
    private interface Task {
        String run();
    }
}
