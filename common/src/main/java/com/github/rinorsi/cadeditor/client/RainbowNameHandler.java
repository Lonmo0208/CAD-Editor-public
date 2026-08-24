package com.github.rinorsi.cadeditor.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the rainbow scrolling effect for the OP golden sword name.
 * Uses the HSL color model to generate a standard rainbow gradient matching the color wheel:
 * red -> orange -> yellow -> green -> cyan -> blue -> purple -> magenta -> red, smoothly transitioning.
 * Each character uses true RGB color (TextColor), bright and evenly distributed.
 */
public final class RainbowNameHandler {
    // Hue span: covers the entire color wheel 0-360°
    private static final float HUE_START = 0.0f;       // red
    private static final float HUE_END = 360.0f;       // back to red
    private static final float SATURATION = 1.0f;      // saturation 100% (vivid)
    private static final float LIGHTNESS = 0.55f;      // name lightness 55% (balanced)
    private static final float LORE_LIGHTNESS = 0.40f; // lore lightness 40% (not too dark)
    private static final float LORE_SATURATION = 0.85f; // lore saturation 85% (slightly gray, not garish)

    // Storage key for lore text in custom_data
    private static final String LORE_KEY = "cadeditor.lore";

    // Scroll speed: advance the hue step every 0.26 tick
    // Use an accumulator for sub-tick precision: add 50 sub-steps per tick, advance the hue offset every 13 sub-steps (13/50 = 0.26 tick/step)
    private static final int ACCUM_NUMERATOR = 50;
    private static final int ACCUM_DENOMINATOR = 13;
    private static int tickAccumulator = 0;
    // Global hue offset (in 0.1-degree units to avoid float accumulation errors)
    private static int globalHueOffset = 0;
    // Hue interval between characters (degrees * 10)
    private static final int HUE_STEP_PER_CHAR = 60;   // 6.0 degrees, moderate difference between characters

    private RainbowNameHandler() {
    }

    /**
     * HSL to RGB conversion algorithm.
     *
     * @param hue        hue 0-360
     * @param saturation saturation 0-1
     * @param lightness  lightness 0-1
     * @return RGB integer (0xRRGGBB)
     */
    private static int hslToRgb(float hue, float saturation, float lightness) {
        float c = (1.0f - Math.abs(2.0f * lightness - 1.0f)) * saturation;
        float x = c * (1.0f - Math.abs(((hue / 60.0f) % 2.0f) - 1.0f));
        float m = lightness - c / 2.0f;
        float r, g, b;

        if (hue < 60) {
            r = c; g = x; b = 0;
        } else if (hue < 120) {
            r = x; g = c; b = 0;
        } else if (hue < 180) {
            r = 0; g = c; b = x;
        } else if (hue < 240) {
            r = 0; g = x; b = c;
        } else if (hue < 300) {
            r = x; g = 0; b = c;
        } else {
            r = c; g = 0; b = x;
        }

        int ri = Math.round((r + m) * 255);
        int gi = Math.round((g + m) * 255);
        int bi = Math.round((b + m) * 255);
        return (ri << 16) | (gi << 8) | bi;
    }

    /**
     * Gets the RGB color value for the name lightness based on the global offset and character index.
     * Hue flows clockwise around the color wheel: red -> orange -> yellow -> green -> cyan -> blue -> purple -> magenta -> red.
     *
     * @param charIndex position of the character in the name
     * @return RGB color integer (0xRRGGBB)
     */
    private static int getRgbColor(int charIndex) {
        return getRgbColor(charIndex, LIGHTNESS);
    }

    /**
     * Gets the RGB color value based on the global offset, character index and specified lightness.
     * Hue flows clockwise around the color wheel: red -> orange -> yellow -> green -> cyan -> blue -> purple -> magenta -> red.
     *
     * @param charIndex position of the character in the name
     * @return RGB color integer (0xRRGGBB)
     */
    private static int getRgbColor(int charIndex, float lightness) {
        return getRgbColor(charIndex, lightness, SATURATION);
    }

    /**
     * Gets the RGB color value based on the global offset, character index and specified lightness/saturation.
     * Used so lore (lightness 40%, full saturation) and name (lightness 55%) share the same scrolling hue.
     */
    private static int getRgbColor(int charIndex, float lightness, float saturation) {
        // Compute in 0.1-degree units to avoid float error; charIndex can be negative (reversed lore), normalize to [0,3600)
        int hueTenths = ((globalHueOffset + charIndex * HUE_STEP_PER_CHAR) % 3600 + 3600) % 3600;
        float hue = hueTenths / 10.0f;
        return hslToRgb(hue, saturation, lightness);
    }

    /**
     * Called each client tick to update the global hue offset.
     * The color band flows left-to-right along the name.
     * Speed: advances 1.5 degrees of hue every 0.26 tick (decrementing the hue offset
     * so right-side characters inherit the left neighbor's previous frame color, making the band flow left-to-right)
     */
    public static void onClientTick() {
        tickAccumulator += ACCUM_NUMERATOR;
        while (tickAccumulator >= ACCUM_DENOMINATOR) {
            tickAccumulator -= ACCUM_DENOMINATOR;
            // Decrement 1.5 degrees (stored as 15 in 0.1-degree units).
            // Negative modulo wraps back to the 0-3600 range so the global offset never shrinks indefinitely
            globalHueOffset = ((globalHueOffset - 15) % 3600 + 3600) % 3600;
        }
    }

    /**
     * Checks whether the item has the OP golden sword attribute (via custom_data).
     */
    public static boolean isOpSword(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        var customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return false;
        }
        return customData.copyTag().getBoolean(com.github.rinorsi.cadeditor.common.logic.InstantKillLogic.INSTANT_KILL_KEY);
    }

    /**
     * Generates the rainbow "Infinite" attribute line for the OP golden sword.
     * Each character uses an independent true RGB color, fading clockwise around the color wheel.
     *
     * @param attributeName original attribute name, e.g. "Attack Damage"
     * @return attribute line with rainbow colors
     */
    public static MutableComponent getRainbowInfiniteLine(String attributeName) {
        MutableComponent line = Component.literal("").withStyle(Style.EMPTY.withItalic(false));
        String infinite = "Infinite";
        int index = 0;
        for (int i = 0; i < infinite.length(); i++) {
            int rgb = getRgbColor(index++);
            line.append(Component.literal(String.valueOf(infinite.charAt(i)))
                    .withStyle(Style.EMPTY.withColor(rgb).withItalic(false)));
        }
        line.append(Component.literal(" ").withStyle(Style.EMPTY.withItalic(false)));
        for (int i = 0; i < attributeName.length(); i++) {
            int rgb = getRgbColor(index++);
            line.append(Component.literal(String.valueOf(attributeName.charAt(i)))
                    .withStyle(Style.EMPTY.withColor(rgb).withItalic(false)));
        }
        return line;
    }

    /**
     * Strips formatting codes from the item name and generates a rainbow name.
     * Each character uses an independent true RGB color, fading to match the color wheel image.
     */
    public static MutableComponent getRainbowName(String text) {
        String cleanText = text.replaceAll("§.", "");
        MutableComponent rainbowName = Component.literal("").withStyle(Style.EMPTY.withItalic(false));
        for (int i = 0; i < cleanText.length(); i++) {
            int rgb = getRgbColor(i);
            rainbowName.append(Component.literal(String.valueOf(cleanText.charAt(i)))
                    .withStyle(Style.EMPTY.withColor(rgb).withItalic(false)));
        }
        return rainbowName;
    }

    /**
     * Reads the raw lore lines of the OP golden sword from the item's custom_data (for identification and replacement during rendering).
     */
    public static List<String> getOpSwordLoreLines(ItemStack stack) {
        List<String> lines = new ArrayList<>();
        if (stack == null || stack.isEmpty()) {
            return lines;
        }
        var customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return lines;
        }
        var tag = customData.copyTag();
        if (!tag.contains(LORE_KEY, net.minecraft.nbt.Tag.TAG_LIST)) {
            return lines;
        }
        var list = tag.getList(LORE_KEY, net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            lines.add(list.getString(i));
        }
        return lines;
    }

    /**
     * Generates rainbow italic text for the OP golden sword's lore.
     * Lightness 40% (not too dark), saturation 85% (slightly gray), italic (vanilla lore style).
     * Hue direction is reversed relative to the main name (negative index): when the name flows left-to-right,
     * the lore flows right-to-left, creating a complementary contrast.
     *
     * @param text single raw lore line
     * @return italic component with rainbow colors
     */
    public static MutableComponent getRainbowLoreLine(String text) {
        String cleanText = text.replaceAll("§.", "");
        MutableComponent line = Component.literal("").withStyle(Style.EMPTY.withItalic(true));
        for (int i = 0; i < cleanText.length(); i++) {
            // Use a negative index: hue advances in reverse per character, opposite to the main name's rainbow order
            int rgb = getRgbColor(-i, LORE_LIGHTNESS, LORE_SATURATION);
            line.append(Component.literal(String.valueOf(cleanText.charAt(i)))
                    .withStyle(Style.EMPTY.withColor(rgb).withItalic(true)));
        }
        return line;
    }
}
