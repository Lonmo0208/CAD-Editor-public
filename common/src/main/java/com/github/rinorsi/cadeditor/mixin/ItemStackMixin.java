package com.github.rinorsi.cadeditor.mixin;

import com.github.rinorsi.cadeditor.client.RainbowNameHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Client-side: intercepts item name and tooltip rendering, adds dynamic rainbow effect for the OP golden sword.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Inject(method = "getHoverName", at = @At("HEAD"), cancellable = true)
    private void cadeditor$getHoverName(CallbackInfoReturnable<Component> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (RainbowNameHandler.isOpSword(stack)) {
            Component originalName = stack.get(DataComponents.CUSTOM_NAME);
            if (originalName == null) {
                originalName = Component.literal("寰宇陨神剑");
            }

            // Build rainbow name
            String text = originalName.getString();

            cir.setReturnValue(RainbowNameHandler.getRainbowName(text));
            cir.cancel();
        }
    }

    @Inject(method = "getTooltipLines", at = @At("RETURN"), cancellable = true)
    private void cadeditor$getTooltipLines(Item.TooltipContext context, @Nullable Player player, TooltipFlag flag,
                                           CallbackInfoReturnable<List<Component>> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (!RainbowNameHandler.isOpSword(stack)) {
            return;
        }

        List<Component> originalLines = cir.getReturnValue();
        List<Component> newLines = new ArrayList<>();
        List<String> loreTexts = RainbowNameHandler.getOpSwordLoreLines(stack);

        for (Component line : originalLines) {
            String lineStr = line.getString();

            // Lore lines: replace with rainbow italic (35% brightness)
            boolean replaced = false;
            for (String loreText : loreTexts) {
                if (lineStr.equals(loreText)) {
                    newLines.add(RainbowNameHandler.getRainbowLoreLine(loreText));
                    replaced = true;
                    break;
                }
            }
            if (replaced) {
                continue;
            }

            // Replace the ∞ of attack damage and attack speed with rainbow Infinite
            if (lineStr.contains("∞") && (lineStr.contains("攻击伤害") || lineStr.contains("Attack Damage")
                    || lineStr.contains("攻击速度") || lineStr.contains("Attack Speed"))) {

                if (lineStr.contains("攻击伤害") || lineStr.contains("Attack Damage")) {
                    newLines.add(RainbowNameHandler.getRainbowInfiniteLine("攻击伤害"));
                } else {
                    newLines.add(RainbowNameHandler.getRainbowInfiniteLine("攻击速度"));
                }
            } else {
                newLines.add(line);
            }
        }

        cir.setReturnValue(newLines);
    }
}
