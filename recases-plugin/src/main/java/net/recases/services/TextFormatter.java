package net.recases.services;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextFormatter {

    private static final Pattern HEX_PATTERN = Pattern.compile("#[a-fA-F0-9]{6}");
    private static final LegacyComponentSerializer AMPERSAND_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    public String colorize(String message) {
        if (message == null) {
            return "";
        }

        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(toLegacyHex(matcher.group())));
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    public Component asComponent(String message) {
        return AMPERSAND_SERIALIZER.deserialize(normalize(message));
    }

    public List<Component> asComponents(List<String> lines) {
        List<Component> result = new ArrayList<>();
        if (lines == null) {
            return result;
        }
        for (String line : lines) {
            result.add(asComponent(line));
        }
        return result;
    }

    private String toLegacyHex(String hexCode) {
        StringBuilder builder = new StringBuilder(14);
        builder.append("&x");
        for (int i = 1; i < hexCode.length(); i++) {
            builder.append('&').append(hexCode.charAt(i));
        }
        return builder.toString();
    }

    private String normalize(String message) {
        if (message == null) {
            return "";
        }

        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(toLegacyHex(matcher.group())));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}


