package com.github.rinorsi.cadeditor.common.logic;

import com.github.rinorsi.cadeditor.common.EditorType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.Unbreakable;

import java.util.List;

public final class OpSwordLogic {
    private static final String DISPLAY_ATTACK_DAMAGE_KEY = "cadeditor.display.attack_damage";
    private static final String DISPLAY_ATTACK_SPEED_KEY = "cadeditor.display.attack_speed";
    private static final String LORE_KEY = "cadeditor.lore";
    private static final String LORE_EN_KEY = "cadeditor.lore_en";

    /** Chinese lore text for the OP golden sword */
    private static final List<String> LORE_LINES = List.of(
            "此乃殒神剑",
            "",
            "可斩万维之生，亦斩万界之神",
            "",
            "唯掌权柄者，方可见其锋",
            "",
            "伪神虽可握，却不及其力",
            "",
            "此剑已无他长，仅掌杀伐之道"
    );

    /** English lore text for the OP golden sword */
    private static final List<String> LORE_LINES_EN = List.of(
            "Sword of Godfall — this it is,",
            "",
            "Which cutteth life in every world, and gods in every sphere",
            "",
            "Only the wielder of the power may behold its edge",
            "",
            "Though false gods grasp it, yet they reach not its strength",
            "",
            "This sword hath no other virtue, but it ruleth war alone"
    );

    private OpSwordLogic() {
    }

    /**
     * Builds the OP golden sword:
     * - Instant-kill flag (cadeditor.instant_kill: 1b)
     * - Enchantment glint (triggered via ENCHANTMENT_GLINT_OVERRIDE, no enchantments added)
     * - Unbreakable (unbreakable: 1b)
     * - Custom attributes: infinite attack damage and attack speed (positive infinity, rendered as ∞)
     */
    public static ItemStack createOpSword(ServerPlayer player) {
        ItemStack stack = new ItemStack(Items.GOLDEN_SWORD);

        // Instant-kill custom_data + display tags + lore text (bilingual)
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(InstantKillLogic.INSTANT_KILL_KEY, true);
        tag.putString(DISPLAY_ATTACK_DAMAGE_KEY, "Infinite Attack");
        tag.putString(DISPLAY_ATTACK_SPEED_KEY, "Infinite Attack Speed");

        // Chinese lore (backward compatible)
        ListTag loreTag = new ListTag();
        for (String line : LORE_LINES) {
            loreTag.add(StringTag.valueOf(line));
        }
        tag.put(LORE_KEY, loreTag);

        // English lore (for international clients)
        ListTag loreEnTag = new ListTag();
        for (String line : LORE_LINES_EN) {
            loreEnTag.add(StringTag.valueOf(line));
        }
        tag.put(LORE_EN_KEY, loreEnTag);

        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        // Lore: default dark-gray italic on the server (shown by lite/modless clients),
        // replaced with rainbow italic by ItemStackMixin on the full client
        List<Component> loreComponents = new java.util.ArrayList<>();
        for (String line : LORE_LINES) {
            loreComponents.add(Component.literal(line).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        stack.set(DataComponents.LORE, new ItemLore(loreComponents));

        // Unbreakable (showInTooltip=false: hide the "Unbreakable" line from the tooltip)
        stack.set(DataComponents.UNBREAKABLE, new Unbreakable(false));

        // Enchantment glint: force show glow without adding enchantments (no durability shown)
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);

        // Custom attribute display (∞ damage and ∞ attack speed)
        Holder<Attribute> damageHolder = Attributes.ATTACK_DAMAGE;
        Holder<Attribute> speedHolder = Attributes.ATTACK_SPEED;

        ItemAttributeModifiers modifiers = ItemAttributeModifiers.EMPTY;
        modifiers = modifiers.withModifierAdded(
                damageHolder,
                new AttributeModifier(
                        ResourceLocation.fromNamespaceAndPath("cadeditor", "op_sword_damage"),
                        Double.POSITIVE_INFINITY,
                        AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND);

        modifiers = modifiers.withModifierAdded(
                speedHolder,
                new AttributeModifier(
                        ResourceLocation.fromNamespaceAndPath("cadeditor", "op_sword_speed"),
                        Double.POSITIVE_INFINITY,
                        AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND);

        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers);

        // Custom name
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("§4寰§6宇§e陨§2神§b剑"));

        return stack;
    }

    /**
     * Issues an OP golden sword to the given player.
     */
    public static int issueOpSword(ServerPlayer player) {
        ItemStack sword = createOpSword(player);
        if (!player.getInventory().add(sword)) {
            player.drop(sword, false);
        }
        player.getCooldowns().addCooldown(sword.getItem(), 0);
        SecurityAuditLog.logOpSwordIssued(player);
        SecurityAuditLog.logSuccessfulEdit(player, "op_sword", "issued op sword", EditorType.STANDARD);
        return 1;
    }
}
