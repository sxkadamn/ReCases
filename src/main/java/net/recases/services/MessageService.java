package net.recases.services;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MessageService {

    private final JavaPlugin plugin;
    private final TextFormatter textFormatter;

    public MessageService(JavaPlugin plugin, TextFormatter textFormatter) {
        this.plugin = plugin;
        this.textFormatter = textFormatter;
    }

    public String get(String path, String fallback) {
        return textFormatter.colorize(plugin.getConfig().getString(path, fallback));
    }

    public String get(String path, String fallback, String... replacements) {
        return get(path, fallback, toReplacementMap(replacements));
    }

    public String get(String path, String fallback, Map<String, String> replacements) {
        String text = get(path, fallback);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue());
        }
        return text;
    }

    public List<String> getList(String path, List<String> fallback) {
        List<String> lines = plugin.getConfig().getStringList(path);
        List<String> source = lines.isEmpty() ? fallback : lines;
        return source.stream()
                .map(textFormatter::colorize)
                .collect(Collectors.toList());
    }

    public List<String> getList(String path, List<String> fallback, String... replacements) {
        return getList(path, fallback, toReplacementMap(replacements));
    }

    public List<String> getList(String path, List<String> fallback, Map<String, String> replacements) {
        List<String> lines = getList(path, fallback);
        return lines.stream()
                .map(line -> applyReplacements(line, replacements))
                .collect(Collectors.toList());
    }

    public Component getComponent(String path, String fallback) {
        return textFormatter.asComponent(get(path, fallback));
    }

    public Component getComponent(String path, String fallback, String... replacements) {
        return textFormatter.asComponent(get(path, fallback, replacements));
    }

    public Component getComponent(String path, String fallback, Map<String, String> replacements) {
        return textFormatter.asComponent(get(path, fallback, replacements));
    }

    public List<Component> getComponentList(String path, List<String> fallback) {
        return textFormatter.asComponents(getList(path, fallback));
    }

    public List<Component> getComponentList(String path, List<String> fallback, String... replacements) {
        return textFormatter.asComponents(getList(path, fallback, replacements));
    }

    public void send(CommandSender sender, String path, String fallback) {
        sender.sendMessage(getComponent(path, fallback));
    }

    public void send(CommandSender sender, String path, String fallback, String... replacements) {
        sender.sendMessage(getComponent(path, fallback, replacements));
    }

    public void sendList(CommandSender sender, String path, List<String> fallback, String... replacements) {
        getComponentList(path, fallback, replacements).forEach(sender::sendMessage);
    }

    public void title(Player player, String titlePath, String titleFallback, String subtitlePath, String subtitleFallback,
                      int fadeInTicks, int stayTicks, int fadeOutTicks, String... replacements) {
        player.showTitle(Title.title(
                getComponent(titlePath, titleFallback, replacements),
                getComponent(subtitlePath, subtitleFallback, replacements),
                Title.Times.times(
                        Duration.ofMillis(fadeInTicks * 50L),
                        Duration.ofMillis(stayTicks * 50L),
                        Duration.ofMillis(fadeOutTicks * 50L)
                )
        ));
    }

    private Map<String, String> toReplacementMap(String... replacements) {
        return IntStream.range(0, replacements.length / 2)
                .boxed()
                .collect(Collectors.toMap(
                        index -> replacements[index * 2],
                        index -> replacements[index * 2 + 1],
                        (left, right) -> right,
                        LinkedHashMap::new
                ));
    }

    private String applyReplacements(String text, Map<String, String> replacements) {
        String updated = text;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            updated = updated.replace(entry.getKey(), entry.getValue());
        }
        return updated;
    }
}

