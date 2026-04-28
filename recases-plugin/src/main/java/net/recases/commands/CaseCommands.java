package net.recases.commands;

import net.recases.app.PluginContext;
import net.recases.domain.CaseProfile;
import net.recases.gui.create.CaseEditorGUI;
import net.recases.management.CaseItem;
import net.recases.runtime.CaseRuntime;
import net.recases.services.MessageService;
import net.recases.services.PromoCodeService;
import net.recases.services.RewardAuditService;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

public class CaseCommands implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "help", "list", "instances", "keys", "give", "take", "setamount", "set", "reload", "top",
            "createprofile", "deleteprofile", "createinstance", "deleteinstance", "setprofileanimation", "setinstanceanimation", "edit", "preset",
            "testanim", "simulate", "audit", "history", "rollback", "restore", "promocode", "redeem", "doctor"
    );
    private static final List<String> TOP_TYPES = Arrays.asList("opens", "rare", "guaranteed");
    private static final DateTimeFormatter AUDIT_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault());

    private final PluginContext plugin;
    private final MessageService messages;
    private final CaseEditorGUI editorGUI;
    private final KeysCommandHandler keysHandler;
    private final AuditCommandHandler auditHandler;
    private final PromoCodeCommandHandler promoCodeHandler;
    private final AdminCommandHandler adminHandler;
    private final ProfileInstanceCommandHandler profileInstanceHandler;
    private final OpeningToolsCommandHandler openingToolsHandler;
    private final KeysTabCompleter keysTabCompleter;
    private final ProfileInstanceTabCompleter profileInstanceTabCompleter;
    private final OpeningToolsTabCompleter openingToolsTabCompleter;
    private final AuditTabCompleter auditTabCompleter;
    private final PromoCodeTabCompleter promoCodeTabCompleter;

    public CaseCommands(PluginContext plugin) {
        this.plugin = plugin;
        this.messages = plugin.getMessages();
        this.editorGUI = new CaseEditorGUI(plugin);
        this.keysHandler = new KeysCommandHandler(this);
        this.auditHandler = new AuditCommandHandler(this);
        this.promoCodeHandler = new PromoCodeCommandHandler(this);
        this.adminHandler = new AdminCommandHandler(this);
        this.profileInstanceHandler = new ProfileInstanceCommandHandler(this);
        this.openingToolsHandler = new OpeningToolsCommandHandler(this);
        this.keysTabCompleter = new KeysTabCompleter(this);
        this.profileInstanceTabCompleter = new ProfileInstanceTabCompleter(this);
        this.openingToolsTabCompleter = new OpeningToolsTabCompleter(this);
        this.auditTabCompleter = new AuditTabCompleter(this);
        this.promoCodeTabCompleter = new PromoCodeTabCompleter(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String subcommand = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        boolean admin = sender.hasPermission("recases.admin");

        if ("help".equals(subcommand)) {
            showHelp(sender, label, admin);
            return true;
        }

        if ("redeem".equals(subcommand)) {
            return promoCodeHandler.redeemCode(sender, args);
        }
        if ("history".equals(subcommand) && !admin) {
            return auditHandler.showHistory(sender, args, false);
        }

        if (!admin) {
            messages.send(sender, "messages.no-permission", "У вас нет прав.");
            return true;
        }

        switch (subcommand) {
            case "list":
                adminHandler.showProfiles(sender);
                return true;
            case "instances":
                adminHandler.showInstances(sender);
                return true;
            case "keys":
                return keysHandler.showBalance(sender, args);
            case "give":
                return keysHandler.changeKeys(sender, args, ChangeMode.GIVE);
            case "take":
                return keysHandler.changeKeys(sender, args, ChangeMode.TAKE);
            case "setamount":
                return keysHandler.changeKeys(sender, args, ChangeMode.SET);
            case "set":
                return profileInstanceHandler.saveInstanceLocation(sender, args);
            case "reload":
                plugin.reloadPluginState();
                messages.send(sender, "messages.reload-complete", "Конфиг и runtime кейсов перезагружены.");
                return true;
            case "top":
                return adminHandler.showTop(sender, args);
            case "createprofile":
                return profileInstanceHandler.createProfile(sender, args);
            case "deleteprofile":
                return profileInstanceHandler.deleteProfile(sender, args);
            case "createinstance":
                return profileInstanceHandler.createInstance(sender, args);
            case "deleteinstance":
                return profileInstanceHandler.deleteInstance(sender, args);
            case "setprofileanimation":
                return profileInstanceHandler.setProfileAnimation(sender, args);
            case "setinstanceanimation":
                return profileInstanceHandler.setInstanceAnimation(sender, args);
            case "edit":
                return profileInstanceHandler.openEditor(sender, args);
            case "preset":
                return openingToolsHandler.handlePreset(sender, args);
            case "testanim":
                return openingToolsHandler.runAnimationTest(sender, args);
            case "simulate":
                return openingToolsHandler.simulateDrops(sender, args);
            case "audit":
                return auditHandler.showAudit(sender, args);
            case "history":
                return auditHandler.showHistory(sender, args, true);
            case "rollback":
                return rollbackReward(sender, args);
            case "restore":
                return restoreReward(sender, args);
            case "promocode":
                return promoCodeHandler.handlePromoCode(sender, args);
            case "doctor":
                return adminHandler.runDoctor(sender);
            default:
                messages.send(sender, "messages.command-unknown", "Неизвестная подкоманда.");
                return true;
        }
    }

    private void showHelp(CommandSender sender, String label, boolean admin) {
        messages.sendList(sender, "messages.help", Collections.emptyList(), "%label%", label);
        messages.send(sender, "messages.help-history", "#ffd166/%label% history [player|limit] #a8dadc- история открытий", "%label%", label);
        messages.send(sender, "messages.help-redeem", "#ffd166/%label% redeem <code> #a8dadc- активировать промокод", "%label%", label);
        if (!admin) {
            return;
        }
        messages.send(sender, "messages.help-top", "#ffd166/%label% top <opens|rare|guaranteed> [profile] [limit] #a8dadc- показать таблицы лидеров", "%label%", label);
        messages.send(sender, "messages.help-editor", "#ffd166/%label% edit <profile> #a8dadc- открыть редактор наград", "%label%", label);
        messages.send(sender, "messages.help-preset", "#ffd166/%label% preset <list|export|import> ... #a8dadc- работа с пресетами", "%label%", label);
        messages.send(sender, "messages.help-testanim", "#ffd166/%label% testanim <animation> [profile] [instance] #a8dadc- тест анимации", "%label%", label);
        messages.send(sender, "messages.help-simulate", "#ffd166/%label% simulate <profile> <opens> #a8dadc- симулятор дропа", "%label%", label);
        messages.send(sender, "messages.help-audit", "#ffd166/%label% audit [player] [limit] #a8dadc- сырой журнал наград", "%label%", label);
        messages.send(sender, "messages.help-rollback", "#ffd166/%label% rollback <tx|opening> #a8dadc- откатить выдачу", "%label%", label);
        messages.send(sender, "messages.help-restore", "#ffd166/%label% restore <tx|opening> #a8dadc- восстановить откат", "%label%", label);
        messages.send(sender, "messages.help-promocode", "#ffd166/%label% promocode <list|create|delete> ... #a8dadc- управление промокодами", "%label%", label);
        messages.send(sender, "messages.help-doctor", "#ffd166/%label% doctor #a8dadc- диагностика конфигурации и интеграций", "%label%", label);
    }

    void showProfiles(CommandSender sender) {
        List<String> profiles = plugin.getCaseService().getProfileIds();
        if (profiles.isEmpty()) {
            messages.send(sender, "messages.case-list-empty", "Профили кейсов не настроены.");
            return;
        }

        messages.send(sender, "messages.case-list-format", "Профили кейсов: %cases%", "%cases%", String.join(", ", profiles));
    }

    void showInstances(CommandSender sender) {
        List<String> instances = plugin.getCaseService().getRuntimeIds();
        if (instances.isEmpty()) {
            messages.send(sender, "messages.instance-list-empty", "Физические кейсы не настроены.");
            return;
        }

        messages.send(sender, "messages.instance-list-format", "Физические кейсы: %instances%", "%instances%", String.join(", ", instances));
    }

    boolean showBalance(CommandSender sender, String[] args) {
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

    boolean changeKeys(CommandSender sender, String[] args, ChangeMode mode) {
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
        messages.send(sender, mode.messagePath, mode.messageFallback, "%player%", playerName(target), "%amount%", String.valueOf(applied), "%case%", profileId);
        messages.send(sender, "messages.case-balance-after", "Текущий баланс %player%: %amount% ключ(ей) для %case%.", "%player%", playerName(target), "%amount%", String.valueOf(plugin.getStorage().getCaseAmount(target, profileId)), "%case%", profileId);
        logModeration(sender.getName(), subcommandLabel(mode), "player=" + playerName(target) + " profile=" + profileId + " amount=" + applied);
        return true;
    }

    boolean saveInstanceLocation(CommandSender sender, String[] args) {
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
        plugin.saveConfigUtf8();
        plugin.reloadPluginState();
        messages.send(sender, "messages.case-position-saved", "Позиция экземпляра кейса %instance% обновлена.", "%instance%", instanceId);
        return true;
    }

    boolean showTop(CommandSender sender, String[] args) {
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

        messages.send(sender, "messages.top-header", "#ffd166Топ %type% %scope%", "%type%", type.getId(), "%scope%", profileId == null ? "(глобально)" : "(" + profileId + ")");
        int position = 1;
        for (LeaderboardEntry entry : entries) {
            messages.send(sender, "messages.top-line", "#a8dadc#%position% #ffffff%player% #ffd166- %value%", "%position%", String.valueOf(position++), "%player%", entry.getPlayerName(), "%value%", String.valueOf(entry.getValue()));
        }
        return true;
    }

    boolean createProfile(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "messages.command-unknown", "Использование: /cases createprofile <id>");
            return true;
        }
        String profileId = args[1].toLowerCase(Locale.ROOT);
        if (!plugin.getCaseService().createProfile(profileId)) {
            messages.send(sender, "messages.command-unknown", "Не удалось создать профиль. Возможно, он уже существует.");
            return true;
        }
        messages.send(sender, "messages.editor-profile-created", "#80ed99Профиль кейса #ffffff%profile% #80ed99создан.", "%profile%", profileId);
        return true;
    }

    boolean deleteProfile(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "messages.command-unknown", "Использование: /cases deleteprofile <id>");
            return true;
        }
        String profileId = args[1].toLowerCase(Locale.ROOT);
        if (!plugin.getCaseService().deleteProfile(profileId)) {
            messages.send(sender, "messages.case-not-found", "Профиль кейса '%case%' не найден.", "%case%", profileId);
            return true;
        }
        messages.send(sender, "messages.editor-profile-deleted", "#ffd166Профиль кейса #ffffff%profile% #ffd166удален.", "%profile%", profileId);
        return true;
    }

    boolean createInstance(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "messages.player-only", "Эта команда доступна только игрокам.");
            return true;
        }
        if (args.length < 2) {
            messages.send(sender, "messages.command-unknown", "Использование: /cases createinstance <id>");
            return true;
        }
        String instanceId = args[1].toLowerCase(Locale.ROOT);
        if (!plugin.getCaseService().createInstance(instanceId, player.getLocation().getBlock().getLocation())) {
            messages.send(sender, "messages.command-unknown", "Не удалось создать инстанс. Возможно, он уже существует.");
            return true;
        }
        messages.send(sender, "messages.editor-instance-created", "#80ed99Инстанс кейса #ffffff%instance% #80ed99создан.", "%instance%", instanceId);
        return true;
    }

    boolean deleteInstance(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "messages.command-unknown", "Использование: /cases deleteinstance <id>");
            return true;
        }
        String instanceId = args[1].toLowerCase(Locale.ROOT);
        if (!plugin.getCaseService().deleteInstance(instanceId)) {
            messages.send(sender, "messages.instance-not-found", "Физический кейс '%instance%' не найден.", "%instance%", instanceId);
            return true;
        }
        messages.send(sender, "messages.editor-instance-deleted", "#ffd166Инстанс кейса #ffffff%instance% #ffd166удален.", "%instance%", instanceId);
        return true;
    }

    boolean setProfileAnimation(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "messages.command-unknown", "Использование: /cases setprofileanimation <profile> <animation>");
            return true;
        }
        String profileId = args[1].toLowerCase(Locale.ROOT);
        String animationId = args[2].toLowerCase(Locale.ROOT);
        if (!plugin.getCaseService().setProfileAnimation(profileId, animationId)) {
            messages.send(sender, "messages.command-unknown", "Не удалось установить анимацию для профиля.");
            return true;
        }
        messages.send(sender, "messages.editor-animation-set", "#80ed99Анимация #ffffff%animation% #80ed99установлена для #ffffff%target%#80ed99.", "%animation%", animationId, "%target%", profileId);
        return true;
    }

    boolean setInstanceAnimation(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "messages.command-unknown", "Использование: /cases setinstanceanimation <instance> <animation|clear>");
            return true;
        }
        String instanceId = args[1].toLowerCase(Locale.ROOT);
        String animationId = "clear".equalsIgnoreCase(args[2]) ? "" : args[2].toLowerCase(Locale.ROOT);
        if (!plugin.getCaseService().setInstanceAnimation(instanceId, animationId)) {
            messages.send(sender, "messages.command-unknown", "Не удалось установить анимацию для инстанса.");
            return true;
        }
        messages.send(sender, "messages.editor-animation-set", "#80ed99Анимация #ffffff%animation% #80ed99установлена для #ffffff%target%#80ed99.", "%animation%", animationId.isEmpty() ? "profile-default" : animationId, "%target%", instanceId);
        return true;
    }

    boolean openEditor(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "messages.player-only", "Эта команда доступна только игрокам.");
            return true;
        }
        if (args.length < 2) {
            messages.send(sender, "messages.command-unknown", "Использование: /cases edit <profile>");
            return true;
        }
        editorGUI.open(player, args[1].toLowerCase(Locale.ROOT));
        return true;
    }

    boolean handlePreset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "messages.usage-preset", "/cases preset <list|export|import> ...");
            return true;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list":
                List<String> presets = plugin.getCaseService().getPresetIds();
                if (presets.isEmpty()) {
                    messages.send(sender, "messages.preset-list-empty", "#ff6b6bПресеты не найдены.");
                    return true;
                }
                messages.send(sender, "messages.preset-list", "#ffd166Пресеты: #ffffff%presets%", "%presets%", String.join(", ", presets));
                return true;
            case "export":
                if (args.length < 3) {
                    messages.send(sender, "messages.usage-preset-export", "/cases preset export <profile> [preset]");
                    return true;
                }
                String exportProfileId = args[2].toLowerCase(Locale.ROOT);
                String exportPresetId = args.length >= 4 ? args[3].toLowerCase(Locale.ROOT) : exportProfileId;
                if (!plugin.getCaseService().exportPreset(exportProfileId, exportPresetId)) {
                    messages.send(sender, "messages.preset-export-failed", "#ff6b6bНе удалось экспортировать пресет.");
                    return true;
                }
                messages.send(sender, "messages.preset-exported", "#80ed99Пресет #ffffff%preset% #80ed99экспортирован из профиля #ffffff%profile%#80ed99.", "%preset%", exportPresetId, "%profile%", exportProfileId);
                return true;
            case "import":
                if (args.length < 3) {
                    messages.send(sender, "messages.usage-preset-import", "/cases preset import <preset> [profile]");
                    return true;
                }
                String importPresetId = args[2].toLowerCase(Locale.ROOT);
                String targetProfileId = args.length >= 4 ? args[3].toLowerCase(Locale.ROOT) : "";
                if (!plugin.getCaseService().importPreset(importPresetId, targetProfileId)) {
                    messages.send(sender, "messages.preset-import-failed", "#ff6b6bНе удалось импортировать пресет. Проверьте имя и отсутствие конфликта по id.");
                    return true;
                }
                messages.send(sender, "messages.preset-imported", "#80ed99Пресет #ffffff%preset% #80ed99импортирован как профиль #ffffff%profile%#80ed99.",
                        "%preset%", importPresetId,
                        "%profile%", targetProfileId.isEmpty() ? importPresetId : targetProfileId);
                return true;
            default:
                messages.send(sender, "messages.usage-preset", "/cases preset <list|export|import> ...");
                return true;
        }
    }

    boolean runAnimationTest(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "messages.player-only", "This command is only available to players.");
            return true;
        }
        if (args.length < 2) {
            messages.send(sender, "messages.usage-testanim", "/cases testanim <animation> [profile] [instance]");
            return true;
        }

        String animationId = args[1].toLowerCase(Locale.ROOT);
        if (!plugin.getAnimations().isRegistered(animationId)) {
            messages.send(sender, "messages.command-unknown", "Unknown animation.");
            return true;
        }

        String profileId = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : firstProfileId();
        if (profileId.isEmpty() || !plugin.getCaseService().hasProfile(profileId)) {
            messages.send(sender, "messages.case-not-found", "Case profile '%case%' was not found.", "%case%", profileId.isEmpty() ? "<none>" : profileId);
            return true;
        }

        CaseRuntime runtime = args.length >= 4
                ? plugin.getCaseService().getRuntime(args[3].toLowerCase(Locale.ROOT))
                : findNearestRuntime(player);
        if (runtime == null) {
            messages.send(sender, "messages.instance-not-found", "Case instance '%instance%' was not found.", "%instance%", args.length >= 4 ? args[3] : "nearest");
            return true;
        }

        return plugin.getCaseService().beginTestOpening(player, runtime, profileId, animationId);
    }

    boolean simulateDrops(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "messages.usage-simulate", "/cases simulate <profile> <opens>");
            return true;
        }

        String profileId = args[1].toLowerCase(Locale.ROOT);
        CaseProfile profile = plugin.getCaseService().getProfile(profileId);
        if (profile == null) {
            messages.send(sender, "messages.case-not-found", "Case profile '%case%' was not found.", "%case%", profileId);
            return true;
        }

        int opens;
        try {
            opens = Math.max(1, Math.min(100000, Integer.parseInt(args[2])));
        } catch (NumberFormatException exception) {
            messages.send(sender, "messages.amount-invalid", "Amount must be a number.");
            return true;
        }

        Random random = new Random();
        Map<String, Integer> rewardCounts = new LinkedHashMap<>();
        int rareWins = 0;
        int guaranteedHits = 0;
        int pity = 0;
        for (int i = 0; i < opens; i++) {
            boolean guaranteed = profile.getPitySettings().isHardGuaranteeReached(pity) && profile.hasRareRewards();
            CaseItem reward = profile.pickReward(random, guaranteed, pity, candidate -> true);
            if (reward == null && guaranteed) {
                guaranteed = false;
                reward = profile.pickReward(random, false, pity, candidate -> true);
            }
            if (reward == null) {
                continue;
            }

            rewardCounts.merge(reward.getName(), 1, Integer::sum);
            if (reward.isRare()) {
                rareWins++;
                pity = 0;
            } else {
                pity++;
            }
            if (guaranteed) {
                guaranteedHits++;
            }
        }

        messages.send(sender, "messages.simulate-header", "#74c0fcSimulation for #ffffff%profile% #74c0fc(%opens% opens)", "%profile%", profileId, "%opens%", String.valueOf(opens));
        messages.send(sender, "messages.simulate-summary", "#a8dadcRare wins: #ffffff%rare% #a8dadcGuaranteed hits: #ffffff%guaranteed%", "%rare%", String.valueOf(rareWins), "%guaranteed%", String.valueOf(guaranteedHits));
        rewardCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(5)
                .forEach(entry -> messages.send(sender, "messages.simulate-line", "#ffffff%reward% #ffd166- %count%", "%reward%", entry.getKey(), "%count%", String.valueOf(entry.getValue())));
        return true;
    }

    boolean showHistory(CommandSender sender, String[] args, boolean adminMode) {
        OfflinePlayer target = null;
        int limit = 10;

        if (adminMode) {
            if (args.length >= 2 && !isInteger(args[1])) {
                target = findPlayer(args[1]);
                if (target == null) {
                    messages.send(sender, "messages.player-not-found", "Игрок не найден.");
                    return true;
                }
            } else if (args.length >= 2) {
                limit = parsePositiveInt(args[1], 10);
            }
            if (args.length >= 3) {
                limit = parsePositiveInt(args[2], limit);
            }
        } else {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "messages.player-only", "Эта команда доступна только игрокам.");
                return true;
            }
            target = player;
            if (args.length >= 2) {
                limit = parsePositiveInt(args[1], 10);
            }
        }

        if (plugin != null) {
            final OfflinePlayer historyTarget = target;
            final int historyLimit = limit;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                List<RewardAuditService.AuditEntry> entries = plugin.getRewardAudit().getRecentEntries(historyTarget == null ? null : historyTarget.getUniqueId(), historyLimit);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (entries.isEmpty()) {
                        messages.send(sender, "messages.history-empty", "#ff6b6bИстория открытий пока пуста.");
                        return;
                    }

                    messages.send(sender, "messages.history-header", "#74c0fcИстория открытий (%count%)", "%count%", String.valueOf(entries.size()));
                    for (RewardAuditService.AuditEntry entry : entries) {
                        String state = entry.isRolledBack() ? "rolled-back" : entry.isRestored() ? "restored" : "active";
                        messages.send(sender,
                                "messages.history-line",
                                "#a8dadc[%time%] #ffffff%player% #ffd166-> %reward% #a8dadccase=%case% state=%state% tx=%tx%",
                                "%time%", AUDIT_TIME_FORMAT.format(Instant.ofEpochMilli(entry.getCreatedAt())),
                                "%player%", entry.getPlayerName(),
                                "%reward%", entry.getRewardName(),
                                "%case%", entry.getCaseProfile(),
                                "%state%", state,
                                "%tx%", shortTransaction(entry.getTransactionId()));
                    }
                });
            });
            return true;
        }

        List<RewardAuditService.AuditEntry> entries = plugin.getRewardAudit().getRecentEntries(target == null ? null : target.getUniqueId(), limit);
        if (entries.isEmpty()) {
            messages.send(sender, "messages.history-empty", "#ff6b6bИстория открытий пока пуста.");
            return true;
        }

        messages.send(sender, "messages.history-header", "#74c0fcИстория открытий (%count%)", "%count%", String.valueOf(entries.size()));
        for (RewardAuditService.AuditEntry entry : entries) {
            String state = entry.isRolledBack() ? "rolled-back" : entry.isRestored() ? "restored" : "active";
            messages.send(sender,
                    "messages.history-line",
                    "#a8dadc[%time%] #ffffff%player% #ffd166-> %reward% #a8dadccase=%case% state=%state% tx=%tx%",
                    "%time%", AUDIT_TIME_FORMAT.format(Instant.ofEpochMilli(entry.getCreatedAt())),
                    "%player%", entry.getPlayerName(),
                    "%reward%", entry.getRewardName(),
                    "%case%", entry.getCaseProfile(),
                    "%state%", state,
                    "%tx%", shortTransaction(entry.getTransactionId()));
        }
        return true;
    }

    boolean showAudit(CommandSender sender, String[] args) {
        OfflinePlayer target = null;
        int limit = 10;

        if (args.length >= 2 && !isInteger(args[1])) {
            target = findPlayer(args[1]);
            if (target == null) {
                messages.send(sender, "messages.player-not-found", "Player not found.");
                return true;
            }
        } else if (args.length >= 2) {
            limit = parsePositiveInt(args[1], 10);
        }

        if (args.length >= 3) {
            limit = parsePositiveInt(args[2], limit);
        }

        if (plugin != null) {
            final OfflinePlayer auditTarget = target;
            final int auditLimit = limit;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                List<RewardAuditService.AuditEntry> entries = plugin.getRewardAudit().getRecentEntries(auditTarget == null ? null : auditTarget.getUniqueId(), auditLimit);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (entries.isEmpty()) {
                        messages.send(sender, "messages.top-empty", "No audit data yet.");
                        return;
                    }

                    messages.send(sender, "messages.audit-header", "#74c0fcReward audit (%count%)", "%count%", String.valueOf(entries.size()));
                    for (RewardAuditService.AuditEntry entry : entries) {
                        messages.send(sender,
                                "messages.audit-line",
                                "#a8dadc[%time%] #ffffff%player% #ffd166-> %reward% #a8dadccase=%case% pity=%pity% tx=%tx% server=%server%",
                                "%time%", AUDIT_TIME_FORMAT.format(Instant.ofEpochMilli(entry.getCreatedAt())),
                                "%player%", entry.getPlayerName(),
                                "%reward%", entry.getRewardName(),
                                "%case%", entry.getCaseProfile(),
                                "%pity%", String.valueOf(entry.getPityBefore()),
                                "%tx%", shortTransaction(entry.getTransactionId()),
                                "%server%", entry.getServerId().isEmpty() ? "default" : entry.getServerId()
                        );
                    }
                });
            });
            return true;
        }

        List<RewardAuditService.AuditEntry> entries = plugin.getRewardAudit().getRecentEntries(target == null ? null : target.getUniqueId(), limit);
        if (entries.isEmpty()) {
            messages.send(sender, "messages.top-empty", "No audit data yet.");
            return true;
        }

        messages.send(sender, "messages.audit-header", "#74c0fcReward audit (%count%)", "%count%", String.valueOf(entries.size()));
        for (RewardAuditService.AuditEntry entry : entries) {
            messages.send(sender,
                    "messages.audit-line",
                    "#a8dadc[%time%] #ffffff%player% #ffd166-> %reward% #a8dadccase=%case% pity=%pity% tx=%tx% server=%server%",
                    "%time%", AUDIT_TIME_FORMAT.format(Instant.ofEpochMilli(entry.getCreatedAt())),
                    "%player%", entry.getPlayerName(),
                    "%reward%", entry.getRewardName(),
                    "%case%", entry.getCaseProfile(),
                    "%pity%", String.valueOf(entry.getPityBefore()),
                    "%tx%", shortTransaction(entry.getTransactionId()),
                    "%server%", entry.getServerId().isEmpty() ? "default" : entry.getServerId()
            );
        }
        return true;
    }

    private boolean rollbackReward(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "messages.usage-rollback", "/cases rollback <tx|opening>");
            return true;
        }

        RewardAuditService.MutationResult result = plugin.getRewardAudit().rollbackEntry(args[1], sender.getName());
        messages.send(sender, result.isSuccess() ? "messages.rollback-success" : "messages.rollback-failed", result.getMessage());
        if (result.isSuccess() && result.getEntry() != null) {
            logModeration(sender.getName(), "rollback", "tx=" + shortTransaction(result.getEntry().getTransactionId()) + " player=" + result.getEntry().getPlayerName());
        }
        return true;
    }

    private boolean restoreReward(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "messages.usage-restore", "/cases restore <tx|opening>");
            return true;
        }

        RewardAuditService.MutationResult result = plugin.getRewardAudit().restoreEntry(args[1], sender.getName());
        messages.send(sender, result.isSuccess() ? "messages.restore-success" : "messages.restore-failed", result.getMessage());
        if (result.isSuccess() && result.getEntry() != null) {
            logModeration(sender.getName(), "restore", "tx=" + shortTransaction(result.getEntry().getTransactionId()) + " player=" + result.getEntry().getPlayerName());
        }
        return true;
    }

    boolean handlePromoCode(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "messages.usage-promocode", "/cases promocode <list|create|delete> ...");
            return true;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list":
                if (plugin != null) {
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        List<PromoCodeService.PromoCodeEntry> entries = plugin.getPromoCodes().listCodes(20);
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (entries.isEmpty()) {
                                messages.send(sender, "messages.promocode-empty", "#ff6b6bПромокоды не найдены.");
                                return;
                            }
                            messages.send(sender, "messages.promocode-header", "#74c0fcПромокоды (%count%)", "%count%", String.valueOf(entries.size()));
                            for (PromoCodeService.PromoCodeEntry entry : entries) {
                                messages.send(sender,
                                        "messages.promocode-line",
                                        "#a8dadc%code% #ffffffprofile=%profile% amount=%amount% uses=%used%/%max%",
                                        "%code%", entry.code(),
                                        "%profile%", entry.profileId(),
                                        "%amount%", String.valueOf(entry.amount()),
                                        "%used%", String.valueOf(entry.usedCount()),
                                        "%max%", String.valueOf(entry.maxUses()));
                            }
                        });
                    });
                    return true;
                }

                List<PromoCodeService.PromoCodeEntry> entries = plugin.getPromoCodes().listCodes(20);
                if (entries.isEmpty()) {
                    messages.send(sender, "messages.promocode-empty", "#ff6b6bПромокоды не найдены.");
                    return true;
                }
                messages.send(sender, "messages.promocode-header", "#74c0fcПромокоды (%count%)", "%count%", String.valueOf(entries.size()));
                for (PromoCodeService.PromoCodeEntry entry : entries) {
                    messages.send(sender,
                            "messages.promocode-line",
                            "#a8dadc%code% #ffffffprofile=%profile% amount=%amount% uses=%used%/%max%",
                            "%code%", entry.code(),
                            "%profile%", entry.profileId(),
                            "%amount%", String.valueOf(entry.amount()),
                            "%used%", String.valueOf(entry.usedCount()),
                            "%max%", String.valueOf(entry.maxUses()));
                }
                return true;
            case "create":
                if (args.length < 5) {
                    messages.send(sender, "messages.usage-promocode-create", "/cases promocode create <code> <profile> <amount> [maxUses]");
                    return true;
                }
                String profileId = args[3].toLowerCase(Locale.ROOT);
                if (!plugin.getCaseService().hasProfile(profileId)) {
                    messages.send(sender, "messages.case-not-found", "Профиль кейса '%case%' не найден.", "%case%", profileId);
                    return true;
                }
                int amount = parsePositiveInt(args[4], 1);
                int maxUses = args.length >= 6 ? parsePositiveInt(args[5], 1) : 1;
                if (!plugin.getPromoCodes().createCode(args[2], profileId, amount, maxUses, sender.getName())) {
                    messages.send(sender, "messages.promocode-create-failed", "#ff6b6bНе удалось создать промокод.");
                    return true;
                }
                messages.send(sender, "messages.promocode-created", "#80ed99Промокод #ffffff%code% #80ed99создан.", "%code%", args[2].toLowerCase(Locale.ROOT));
                logModeration(sender.getName(), "promocode-create", "code=" + args[2].toLowerCase(Locale.ROOT) + " profile=" + profileId + " amount=" + amount + " maxUses=" + maxUses);
                return true;
            case "delete":
                if (args.length < 3) {
                    messages.send(sender, "messages.usage-promocode-delete", "/cases promocode delete <code>");
                    return true;
                }
                if (!plugin.getPromoCodes().deleteCode(args[2])) {
                    messages.send(sender, "messages.promocode-delete-failed", "#ff6b6bПромокод не найден.");
                    return true;
                }
                messages.send(sender, "messages.promocode-deleted", "#ffd166Промокод #ffffff%code% #ffd166удален.", "%code%", args[2].toLowerCase(Locale.ROOT));
                logModeration(sender.getName(), "promocode-delete", "code=" + args[2].toLowerCase(Locale.ROOT));
                return true;
            default:
                messages.send(sender, "messages.usage-promocode", "/cases promocode <list|create|delete> ...");
                return true;
        }
    }

    boolean redeemCode(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "messages.player-only", "Эта команда доступна только игрокам.");
            return true;
        }
        if (args.length < 2) {
            messages.send(sender, "messages.usage-redeem", "/cases redeem <code>");
            return true;
        }

        PromoCodeService.RedemptionResult result = plugin.getPromoCodes().redeem(player, args[1]);
        if (!result.success()) {
            messages.send(sender, "messages.redeem-failed", result.message());
            return true;
        }

        messages.send(sender,
                "messages.redeem-success",
                "#80ed99Промокод активирован: #ffffff%amount% #80ed99ключ(ей) для #ffffff%case%",
                "%amount%", String.valueOf(result.amount()),
                "%case%", result.profileId());
        return true;
    }

    boolean runDoctor(CommandSender sender) {
        List<String> warnings = plugin.getConfigs().collectValidationWarnings();
        messages.send(sender, "messages.doctor-header", "#74c0fcDoctor report: %count% issue(s)", "%count%", String.valueOf(warnings.size()));

        if (warnings.isEmpty()) {
            messages.send(sender, "messages.doctor-ok", "#80ed99Configuration check passed. No issues found.");
        } else {
            warnings.stream()
                    .limit(20)
                    .forEach(warning -> messages.send(sender, "messages.doctor-warning-line", "#ffb703- %warning%", "%warning%", warning));
            if (warnings.size() > 20) {
                messages.send(sender, "messages.doctor-warning-more", "#a8dadc... and %count% more issue(s).", "%count%", String.valueOf(warnings.size() - 20));
            }
        }

        messages.send(sender, "messages.doctor-status-storage", "#a8dadcStorage: #ffffff%type%", "%type%", plugin.getConfig().getString("settings.storage.type", "sqlite"));
        messages.send(sender, "messages.doctor-status-redis", "#a8dadcRedis sync: #ffffff%state%", "%state%",
                plugin.getConfig().getBoolean("settings.redis.enabled", false) ? "enabled" : "disabled");
        messages.send(sender, "messages.doctor-status-network", "#a8dadcNetwork sync: #ffffff%state%", "%state%",
                plugin.getConfig().getBoolean("settings.network-sync.enabled", false) ? "enabled" : "disabled");
        messages.send(sender, "messages.doctor-status-discordbot", "#a8dadcDiscord bot: #ffffff%state%", "%state%",
                plugin.getConfig().getBoolean("settings.integrations.discord-bot.enabled", false) ? "enabled" : "disabled");
        messages.send(sender, "messages.doctor-status-webhook", "#a8dadcDiscord webhook: #ffffff%state%", "%state%",
                plugin.getConfig().getBoolean("settings.webhooks.discord.enabled", false) ? "enabled" : "disabled");
        return true;
    }

    CaseRuntime findNearestRuntime(Player player) {
        if (player == null || player.getWorld() == null) {
            return null;
        }

        return plugin.getCaseService().getRuntimes().stream()
                .filter(runtime -> runtime.getLocation().getWorld() != null && runtime.getLocation().getWorld().equals(player.getWorld()))
                .min(Comparator.comparingDouble(runtime -> runtime.getLocation().distanceSquared(player.getLocation())))
                .orElse(null);
    }

    String firstProfileId() {
        List<String> profiles = plugin.getCaseService().getProfileIds();
        return profiles.isEmpty() ? "" : profiles.get(0);
    }

    private String shortTransaction(UUID transactionId) {
        String value = transactionId == null ? "" : transactionId.toString();
        return value.length() <= 8 ? value : value.substring(0, 8);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        boolean admin = sender.hasPermission("recases.admin");
        if (!admin) {
            if (args.length == 1) {
                return complete(Arrays.asList("help", "history", "redeem"), args[0]);
            }
            if ("history".equalsIgnoreCase(args[0]) && args.length == 2) {
                return complete(Arrays.asList("5", "10", "20"), args[1]);
            }
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return complete(SUBCOMMANDS, args[0]);
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        List<String> suggestions = profileInstanceTabCompleter.complete(subcommand, args);
        if (suggestions != null) {
            return suggestions;
        }
        suggestions = keysTabCompleter.complete(subcommand, args);
        if (suggestions != null) {
            return suggestions;
        }
        suggestions = openingToolsTabCompleter.complete(subcommand, args);
        if (suggestions != null) {
            return suggestions;
        }
        suggestions = auditTabCompleter.complete(subcommand, args);
        if (suggestions != null) {
            return suggestions;
        }
        suggestions = promoCodeTabCompleter.complete(subcommand, args);
        if (suggestions != null) {
            return suggestions;
        }
        if ("top".equals(subcommand)) {
            if (args.length == 2) {
                return complete(TOP_TYPES, args[1]);
            }
            if (args.length == 3) {
                List<String> values = new ArrayList<>(plugin.getCaseService().getProfileIds());
                values.addAll(Arrays.asList("5", "10", "15"));
                return complete(values, args[2]);
            }
            if (args.length == 4) {
                return complete(Arrays.asList("5", "10", "15", "20"), args[3]);
            }
        }
        return Collections.emptyList();
    }

    List<String> complete(List<String> values, String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        return values.stream()
                .distinct()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted()
                .collect(Collectors.toList());
    }

    List<String> knownOfflinePlayerNames() {
        return Arrays.stream(Bukkit.getOfflinePlayers())
                .map(OfflinePlayer::getName)
                .filter(name -> name != null && !name.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    OfflinePlayer findPlayer(String name) {
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

    String playerName(OfflinePlayer player) {
        return player.getName() != null ? player.getName() : player.getUniqueId().toString();
    }

    boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    int parsePositiveInt(String value, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private void logModeration(String actor, String action, String details) {
        plugin.getDiscordBot().sendModerationLog("minecraft", actor, action, details);
    }

    private String subcommandLabel(ChangeMode mode) {
        return switch (mode) {
            case GIVE -> "give";
            case TAKE -> "take";
            case SET -> "setamount";
        };
    }

    PluginContext plugin() {
        return plugin;
    }

    MessageService messages() {
        return messages;
    }

    enum ChangeMode {
        GIVE("messages.usage-give", "/cases give <player> <profile> <amount>", "messages.case-given", "Выдано %amount% ключ(ей) %case% игроку %player%."),
        TAKE("messages.usage-take", "/cases take <player> <profile> <amount>", "messages.case-taken", "Удалено %amount% ключ(ей) %case% у игрока %player%."),
        SET("messages.usage-setamount", "/cases setamount <player> <profile> <amount>", "messages.case-set", "Баланс %player% для %case% установлен на %amount%.");

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
