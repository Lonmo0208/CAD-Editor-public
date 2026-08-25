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

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Inject(method = "getHoverName", at = @At("HEAD"), cancellable = true)
    private void cadeditor$getHoverName(CallbackInfoReturnable<Component> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (RainbowNameHandler.isOpSword(stack)) {
            cir.setReturnValue(RainbowNameHandler.getLocalizedRainbowName());
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
        List<String> localizedLore = RainbowNameHandler.getLocalizedLoreLines();
        List<String> nbtLore = RainbowNameHandler.getOpSwordLoreLinesForMatch(stack);

        List<Component> result = new ArrayList<>();
        int lastAttrIndex = -1;
        int loreInsertAt = -1;

        for (int i = 0; i < originalLines.size(); i++) {
            String lineStr = originalLines.get(i).getString();
            String trimmed = lineStr.trim();

            // Replace attribute lines with rainbow Infinite
            if (RainbowNameHandler.isInfiniteAttributeLine(lineStr)) {
                boolean isDamage = RainbowNameHandler.isAttackDamageLine(lineStr);
                result.add(RainbowNameHandler.getRainbowInfiniteLine(isDamage));
                lastAttrIndex = result.size() - 1;
                continue;
            }

            // Detect and skip lore lines (we insert localized version)
            // Skip blank lines between lore entries too
            if (isLoreLine(trimmed, localizedLore, nbtLore)) {
                // Remember where to insert lore: right after the name section, before attributes
                if (loreInsertAt == -1 && lastAttrIndex == -1) {
                    // First lore line encountered — lore should go in the blank line before this
                    loreInsertAt = result.size();
                }
                continue;
            }

            // Blank lines after name section and before first attribute are lore spacers — skip them
            if (trimmed.isEmpty() && lastAttrIndex == -1 && loreInsertAt != -1) {
                continue;
            }

            result.add(originalLines.get(i));
        }

        // Insert localized rainbow lore at the right position
        if (loreInsertAt != -1) {
            List<Component> finalResult = new ArrayList<>();
            for (int i = 0; i < result.size(); i++) {
                if (i == loreInsertAt) {
                    for (String loreLine : localizedLore) {
                        finalResult.add(RainbowNameHandler.getRainbowLoreLine(loreLine));
                    }
                }
                finalResult.add(result.get(i));
            }
            cir.setReturnValue(finalResult);
        } else {
            cir.setReturnValue(result);
        }
    }

    /**
     * Checks if a line is a lore line that should be replaced.
     * Matches against both localized (client-side) and NBT-stored (server-side) lore text.
     */
    private static boolean isLoreLine(String trimmed, List<String> localizedLore, List<String> nbtLore) {
        if (trimmed.isEmpty()) return false;
        for (String lore : localizedLore) {
            if (!lore.isEmpty() && trimmed.equals(lore.trim())) return true;
        }
        for (String lore : nbtLore) {
            if (!lore.isEmpty() && trimmed.equals(lore.trim())) return true;
        }
        return false;
    }
}