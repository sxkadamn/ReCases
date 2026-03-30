package net.recases.protocollib.hologram;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HologramLine {

    public static final String HOLOGRAM_METADATA = "recases_hologram_line";
    private static final Pattern HEX_PATTERN = Pattern.compile("#[a-fA-F0-9]{6}");
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private final Plugin plugin;
    private String text;
    private Location location;
    private ArmorStand stand;

    public HologramLine(Plugin plugin, String text, Location location) {
        this.plugin = plugin;
        this.text = text;
        this.location = location.clone();
        spawn();
    }

    public String getText() {
        return text;
    }

    public Location getLocation() {
        return location.clone();
    }

    public ArmorStand getStand() {
        return stand;
    }

    public void setText(String text) {
        this.text = text;
        if (stand != null) {
            stand.customName(colorize(text));
        }
    }

    public void teleport(Location location) {
        this.location = location.clone();
        if (stand != null) {
            stand.teleport(this.location);
        }
    }

    public void remove() {
        if (stand != null && !stand.isDead()) {
            stand.remove();
        }
        stand = null;
    }

    public void removeLine() {
        remove();
    }

    private void spawn() {
        if (location.getWorld() == null) {
            return;
        }

        stand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setMarker(true);
        stand.setGravity(false);
        stand.setSmall(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.customName(colorize(text));
        stand.setCustomNameVisible(true);
        stand.setMetadata(HOLOGRAM_METADATA, new FixedMetadataValue(plugin, true));
    }

    private Component colorize(String line) {
        return LEGACY_SERIALIZER.deserialize(normalize(line));
    }

    private String normalize(String line) {
        if (line == null) {
            return "";
        }

        Matcher matcher = HEX_PATTERN.matcher(line);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(toLegacyHex(matcher.group())));
        }
        matcher.appendTail(buffer);
        return buffer.toString().replace('§', '&');
    }

    private String toLegacyHex(String hexCode) {
        StringBuilder builder = new StringBuilder(14);
        builder.append('&').append('x');
        for (int i = 1; i < hexCode.length(); i++) {
            builder.append('&').append(hexCode.charAt(i));
        }
        return builder.toString();
    }
}

