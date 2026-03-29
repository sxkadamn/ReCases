package net.recases.animations.opening;

import net.recases.app.PluginContext;
import net.recases.management.CaseItem;
import net.recases.management.OpeningSession;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

final class OpeningStyle {

    private final String color;
    private final String modifier;
    private final Material previewMaterial;

    private OpeningStyle(String color, String modifier, Material previewMaterial) {
        this.color = color;
        this.modifier = modifier;
        this.previewMaterial = previewMaterial;
    }

    static OpeningStyle of(OpeningSession session) {
        if (session == null || session.getFinalReward() == null) {
            return new OpeningStyle("#a8dadc", "Таинственный", Material.AMETHYST_SHARD);
        }

        CaseItem reward = session.getFinalReward();
        Material type = reward.getIcon().getType();
        String materialName = type.name().toLowerCase(Locale.ROOT);

        if (materialName.contains("sword") || materialName.contains("axe") || materialName.contains("bow")
                || materialName.contains("crossbow") || materialName.contains("trident") || materialName.contains("mace")) {
            return new OpeningStyle("#ff6b6b", "Боевой", Material.NETHERITE_SWORD);
        }
        if (materialName.contains("totem") || materialName.contains("beacon") || materialName.contains("star")) {
            return new OpeningStyle("#95d5b2", "Священный", Material.TOTEM_OF_UNDYING);
        }
        if (materialName.contains("diamond") || materialName.contains("emerald") || materialName.contains("gold")
                || materialName.contains("coin") || materialName.contains("ingot")) {
            return new OpeningStyle("#ffd166", "Королевский", Material.GOLD_BLOCK);
        }
        if (materialName.contains("ender") || materialName.contains("chorus") || materialName.contains("obsidian")
                || materialName.contains("echo") || materialName.contains("dragon")) {
            return new OpeningStyle("#9775fa", "Бездонный", Material.ENDER_EYE);
        }
        if (materialName.contains("fire") || materialName.contains("blaze") || materialName.contains("lava")
                || materialName.contains("magma")) {
            return new OpeningStyle("#ff922b", "Пламенный", Material.BLAZE_ROD);
        }
        if (materialName.contains("book") || materialName.contains("potion") || materialName.contains("crystal")
                || materialName.contains("amethyst")) {
            return new OpeningStyle("#74c0fc", "Арканный", Material.END_CRYSTAL);
        }
        if (type == Material.PLAYER_HEAD) {
            return new OpeningStyle("#f4a261", "Легендарный", Material.PLAYER_HEAD);
        }
        return new OpeningStyle(reward.isRare() || session.isGuaranteedReward() ? "#f4a261" : "#a8dadc", reward.isRare() || session.isGuaranteedReward() ? "Элитный" : "Таинственный", Material.AMETHYST_SHARD);
    }

    String color() {
        return color;
    }

    String roleLabel(String role, int index) {
        return color + modifier + " " + role + " #" + index;
    }

    ItemStack preview(PluginContext plugin, OpeningSession session, String fallbackName) {
        if (session != null && session.getFinalReward() != null && (session.getFinalReward().isRare() || session.isGuaranteedReward())) {
            return session.getFinalReward().getIcon();
        }
        return plugin.getItemFactory().create("ITEM;" + previewMaterial.name(), color + fallbackName);
    }
}
