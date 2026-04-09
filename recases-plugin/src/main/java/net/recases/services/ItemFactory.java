package net.recases.services;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ItemFactory {

    private static final Pattern HEX_PATTERN = Pattern.compile("#[a-fA-F0-9]{6}");
    private static final Pattern TEXTURE_URL_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private final JavaPlugin plugin;

    public ItemFactory(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack create(String materialDefinition, String displayName) {
        return create(materialDefinition, displayName, null);
    }

    public ItemStack create(String materialDefinition, String displayName, List<String> lore) {
        ItemStack item = createBaseItem(materialDefinition);
        applyMeta(item, displayName, lore);
        return item;
    }

    public String serialize(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "ITEM;STONE";
        }

        String itemsAdderId = serializeItemsAdder(item);
        if (!itemsAdderId.isEmpty()) {
            return "ITEMSADDER;" + itemsAdderId;
        }

        String oraxenId = serializeOraxen(item);
        if (!oraxenId.isEmpty()) {
            return "ORAXEN;" + oraxenId;
        }

        String nexoId = serializeNexo(item);
        if (!nexoId.isEmpty()) {
            return "NEXO;" + nexoId;
        }

        String mmoItemsId = serializeMmoItems(item);
        if (!mmoItemsId.isEmpty()) {
            return mmoItemsId;
        }

        return "ITEM;" + item.getType().name();
    }

    public String serializeDisplayName(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "";
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return "";
        }

        Component displayName = meta.displayName();
        return displayName == null ? "" : LEGACY_SERIALIZER.serialize(displayName);
    }

    public ItemStack createActionItem(String definition, String displayName) {
        if (definition == null || definition.trim().isEmpty()) {
            return create("ITEM;STONE", displayName);
        }

        String normalized = definition.trim();
        if (normalized.regionMatches(true, 0, "item:", 0, 5)) {
            return create("ITEM;" + normalized.substring(5), displayName);
        }
        if (normalized.regionMatches(true, 0, "head:", 0, 5)) {
            return create("HEAD;" + normalized.substring(5), displayName);
        }
        if (normalized.regionMatches(true, 0, "itemsadder:", 0, 11)) {
            return create("ITEMSADDER;" + normalized.substring(11), displayName);
        }
        if (normalized.regionMatches(true, 0, "oraxen:", 0, 7)) {
            return create("ORAXEN;" + normalized.substring(7), displayName);
        }
        if (normalized.regionMatches(true, 0, "nexo:", 0, 5)) {
            return create("NEXO;" + normalized.substring(5), displayName);
        }
        if (normalized.regionMatches(true, 0, "mmoitems:", 0, 9)) {
            String[] parts = normalized.substring(9).split(":", 2);
            if (parts.length == 2) {
                return create("MMOITEMS;" + parts[0] + ";" + parts[1], displayName);
            }
        }
        if (normalized.contains(";")) {
            return create(normalized, displayName);
        }
        return create("ITEM;" + normalized, displayName);
    }

    private ItemStack createBaseItem(String materialDefinition) {
        if (materialDefinition == null || materialDefinition.trim().isEmpty()) {
            return new ItemStack(Material.STONE);
        }

        String normalized = materialDefinition.trim();
        if (!normalized.contains(";")) {
            return createVanillaItem(normalized);
        }

        String[] parts = normalized.split(";");
        String type = parts[0].trim().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "HEAD" -> parts.length < 2 ? new ItemStack(Material.PLAYER_HEAD) : createCustomHead(parts[1]);
            case "ITEM" -> createVanillaItem(parts.length > 1 ? parts[1] : "STONE");
            case "ITEMSADDER" -> createItemsAdderItem(parts.length > 1 ? parts[1] : "");
            case "ORAXEN" -> createOraxenItem(parts.length > 1 ? parts[1] : "");
            case "NEXO" -> createNexoItem(parts.length > 1 ? parts[1] : "");
            case "MMOITEMS" -> createMmoItemsItem(parts);
            default -> createVanillaItem(parts.length > 1 ? parts[1] : parts[0]);
        };
    }

    private ItemStack createVanillaItem(String materialName) {
        Material material = Material.matchMaterial(materialName == null ? "" : materialName.trim());
        return new ItemStack(material == null ? Material.STONE : material);
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

    private void applyMeta(ItemStack item, String displayName, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        if (displayName != null && !displayName.isEmpty()) {
            meta.displayName(LEGACY_SERIALIZER.deserialize(normalize(displayName)));
        }
        if (lore != null && !lore.isEmpty()) {
            meta.lore(lore.stream().map(line -> LEGACY_SERIALIZER.deserialize(normalize(line))).toList());
        }
        item.setItemMeta(meta);
    }

    private ItemStack createItemsAdderItem(String itemId) {
        if (!isIntegrationEnabled("settings.integrations.custom-items.itemsadder.enabled", "ItemsAdder")) {
            return new ItemStack(Material.STONE);
        }

        try {
            Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object customStack = customStackClass.getMethod("getInstance", String.class).invoke(null, itemId);
            Object itemStack = customStack == null ? null : customStackClass.getMethod("getItemStack").invoke(customStack);
            return itemStack instanceof ItemStack stack ? stack.clone() : new ItemStack(Material.STONE);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return new ItemStack(Material.STONE);
        }
    }

    private ItemStack createOraxenItem(String itemId) {
        if (!isIntegrationEnabled("settings.integrations.custom-items.oraxen.enabled", "Oraxen")) {
            return new ItemStack(Material.STONE);
        }

        try {
            Class<?> oraxenItemsClass = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
            Object builder = oraxenItemsClass.getMethod("getItemById", String.class).invoke(null, itemId);
            if (builder == null) {
                return new ItemStack(Material.STONE);
            }
            Object built = invokeFirst(builder, "build", "buildExact", "getItemStack");
            return built instanceof ItemStack stack ? stack.clone() : new ItemStack(Material.STONE);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return new ItemStack(Material.STONE);
        }
    }

    private ItemStack createNexoItem(String itemId) {
        if (!isIntegrationEnabled("settings.integrations.custom-items.nexo.enabled", "Nexo")) {
            return new ItemStack(Material.STONE);
        }

        try {
            Class<?> nexoItemsClass = Class.forName("com.nexomc.nexo.api.NexoItems");
            Object builder = nexoItemsClass.getMethod("itemFromId", String.class).invoke(null, itemId);
            if (builder == null) {
                return new ItemStack(Material.STONE);
            }
            Object built = invokeFirst(builder, "build", "buildExact", "getItemStack");
            return built instanceof ItemStack stack ? stack.clone() : new ItemStack(Material.STONE);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return new ItemStack(Material.STONE);
        }
    }

    private ItemStack createMmoItemsItem(String[] parts) {
        if (!isIntegrationEnabled("settings.integrations.custom-items.mmoitems.enabled", "MMOItems") || parts.length < 3) {
            return new ItemStack(Material.STONE);
        }

        try {
            Class<?> typeClass = Class.forName("net.Indyuce.mmoitems.api.Type");
            Object type = typeClass.getMethod("get", String.class).invoke(null, parts[1]);
            if (type == null) {
                return new ItemStack(Material.STONE);
            }

            Class<?> mmoItemsClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
            Object pluginInstance = mmoItemsClass.getField("plugin").get(null);
            Object stack = pluginInstance.getClass().getMethod("getItem", typeClass, String.class).invoke(pluginInstance, type, parts[2]);
            return stack instanceof ItemStack itemStack ? itemStack.clone() : new ItemStack(Material.STONE);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return new ItemStack(Material.STONE);
        }
    }

    private String serializeItemsAdder(ItemStack item) {
        if (!isIntegrationEnabled("settings.integrations.custom-items.itemsadder.enabled", "ItemsAdder")) {
            return "";
        }

        try {
            Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object customStack = customStackClass.getMethod("byItemStack", ItemStack.class).invoke(null, item);
            Object id = customStack == null ? null : customStackClass.getMethod("getNamespacedID").invoke(customStack);
            return id instanceof String value ? value : "";
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return "";
        }
    }

    private String serializeOraxen(ItemStack item) {
        if (!isIntegrationEnabled("settings.integrations.custom-items.oraxen.enabled", "Oraxen")) {
            return "";
        }

        try {
            Class<?> oraxenItemsClass = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
            Object id = invokeStaticFirst(oraxenItemsClass, item, "getIdByItem", "getIdByItemStack");
            return id instanceof String value ? value : "";
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return "";
        }
    }

    private String serializeNexo(ItemStack item) {
        if (!isIntegrationEnabled("settings.integrations.custom-items.nexo.enabled", "Nexo")) {
            return "";
        }

        try {
            Class<?> nexoItemsClass = Class.forName("com.nexomc.nexo.api.NexoItems");
            Object id = invokeStaticFirst(nexoItemsClass, item, "idFromItem", "getIdByItem");
            return id instanceof String value ? value : "";
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return "";
        }
    }

    private String serializeMmoItems(ItemStack item) {
        if (!isIntegrationEnabled("settings.integrations.custom-items.mmoitems.enabled", "MMOItems")) {
            return "";
        }

        try {
            Class<?> typeClass = Class.forName("net.Indyuce.mmoitems.api.Type");
            Object type = typeClass.getMethod("get", ItemStack.class).invoke(null, item);
            if (type == null) {
                return "";
            }

            Class<?> nbtItemClass = Class.forName("io.lumine.mythic.lib.api.item.NBTItem");
            Object nbtItem = nbtItemClass.getMethod("get", ItemStack.class).invoke(null, item);
            Object id = nbtItemClass.getMethod("getString", String.class).invoke(nbtItem, "MMOITEMS_ITEM_ID");
            if (!(id instanceof String itemId) || itemId.isEmpty()) {
                return "";
            }

            Object typeId = invokeFirst(type, "getId", "name");
            return typeId instanceof String value ? "MMOITEMS;" + value + ";" + itemId : "";
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return "";
        }
    }

    private Object invokeFirst(Object instance, String... methodNames) throws ReflectiveOperationException {
        for (String methodName : methodNames) {
            try {
                Method method = instance.getClass().getMethod(methodName);
                return method.invoke(instance);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private Object invokeStaticFirst(Class<?> type, ItemStack item, String... methodNames) throws ReflectiveOperationException {
        for (String methodName : methodNames) {
            try {
                Method method = type.getMethod(methodName, ItemStack.class);
                return method.invoke(null, item);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private boolean isIntegrationEnabled(String configPath, String pluginName) {
        return plugin.getConfig().getBoolean(configPath, true) && plugin.getServer().getPluginManager().getPlugin(pluginName) != null;
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

    private String toLegacyHex(String hexCode) {
        StringBuilder builder = new StringBuilder(14);
        builder.append("&x");
        for (int i = 1; i < hexCode.length(); i++) {
            builder.append('&').append(hexCode.charAt(i));
        }
        return builder.toString();
    }
}
