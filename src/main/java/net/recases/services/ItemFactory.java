package net.recases.services;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ItemFactory {

    private static final Pattern TEXTURE_URL_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    public ItemStack create(String materialDefinition, String displayName) {
        return create(materialDefinition, displayName, null);
    }

    public ItemStack create(String materialDefinition, String displayName, List<String> lore) {
        if (materialDefinition == null || materialDefinition.trim().isEmpty()) {
            return new ItemStack(Material.STONE);
        }

        String[] parts = materialDefinition.split(";");
        String type = parts[0].toLowerCase();
        ItemStack item;
        if ("head".equals(type)) {
            item = parts.length < 2 ? new ItemStack(Material.PLAYER_HEAD) : createCustomHead(parts[1]);
        } else {
            Material material = parts.length > 1 ? Material.matchMaterial(parts[1]) : null;
            item = new ItemStack(material == null ? Material.STONE : material);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(LEGACY_SERIALIZER.deserialize(displayName == null ? "" : displayName));
            if (lore != null) {
                meta.lore(lore.stream().map(line -> LEGACY_SERIALIZER.deserialize(line == null ? "" : line)).toList());
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createCustomHead(String value) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) {
            return head;
        }

        URL textureUrl = decodeTextureUrl(value);
        if (textureUrl == null) {
            return head;
        }

        PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID(), "");
        PlayerTextures textures = profile.getTextures();
        textures.setSkin(textureUrl);
        profile.setTextures(textures);
        meta.setOwnerProfile(profile);
        head.setItemMeta(meta);
        return head;
    }

    private URL decodeTextureUrl(String value) {
        try {
            String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            Matcher matcher = TEXTURE_URL_PATTERN.matcher(decoded);
            if (!matcher.find()) {
                return null;
            }
            return new URL(matcher.group(1));
        } catch (IllegalArgumentException | MalformedURLException exception) {
            return null;
        }
    }
}
