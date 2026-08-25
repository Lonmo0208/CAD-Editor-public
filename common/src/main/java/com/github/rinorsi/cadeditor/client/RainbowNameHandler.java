package com.github.rinorsi.cadeditor.client;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the rainbow scrolling effect for the OP golden sword name.
 * Uses the HSL color model to generate a standard rainbow gradient matching the color wheel:
 * red -> orange -> yellow -> green -> cyan -> blue -> purple -> magenta -> red, smoothly transitioning.
 * Each character uses true RGB color (TextColor), bright and evenly distributed.
 *
 * All text (name, attributes, lore) is localized based on the client's current language setting.
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
    private static final String LORE_EN_KEY = "cadeditor.lore_en";

    // Scroll speed: advance the hue step every 0.26 tick
    // Use an accumulator for sub-tick precision: add 50 sub-steps per tick, advance the hue offset every 13 sub-steps (13/50 = 0.26 tick/step)
    private static final int ACCUM_NUMERATOR = 50;
    private static final int ACCUM_DENOMINATOR = 13;
    private static int tickAccumulator = 0;
    // Global hue offset (in 0.1-degree units to avoid float accumulation errors)
    private static int globalHueOffset = 0;
    // Hue interval between characters (degrees * 10)
    private static final int HUE_STEP_PER_CHAR = 60;   // 6.0 degrees, moderate difference between characters

    // ─── Localized text maps ───

    private static final Map<String, String> SWORD_NAMES = new HashMap<>();
    private static final Map<String, List<String>> LORE_LINES = new HashMap<>();
    private static final Map<String, String> ATK_DAMAGE_NAMES = new HashMap<>();
    private static final Map<String, String> ATK_SPEED_NAMES = new HashMap<>();

    static {
        SWORD_NAMES.put("zh_cn", "寰宇陨神剑");
        SWORD_NAMES.put("en_us", "Sword of Cosmic Godfall");

        LORE_LINES.put("zh_cn", List.of(
                "此乃殒神剑",
                "",
                "可斩万维之生，亦斩万界之神",
                "",
                "唯掌权柄者，方可见其锋",
                "",
                "伪神虽可握，却不及其力",
                "",
                "此剑已无他长，仅掌杀伐之道"
        ));
        LORE_LINES.put("en_us", List.of(
                "Sword of Godfall — this it is,",
                "",
                "Which cutteth life in every world, and gods in every sphere",
                "",
                "Only the wielder of the power may behold its edge",
                "",
                "Though false gods grasp it, yet they reach not its strength",
                "",
                "This sword hath no other virtue, but it ruleth war alone"
        ));

        ATK_DAMAGE_NAMES.put("zh_cn", "攻击伤害");
        ATK_DAMAGE_NAMES.put("en_us", "Attack Damage");

        ATK_SPEED_NAMES.put("zh_cn", "攻击速度");
        ATK_SPEED_NAMES.put("en_us", "Attack Speed");
    }

    private RainbowNameHandler() {
    }

    /**
     * Gets the current language code from Minecraft's language system.
     * Normalizes to lowercase for consistent map lookups.
     * Returns "en_us" as fallback if detection fails.
     */
    public static String getCurrentLanguage() {
        try {
            String code = Language.getInstance().getOrDefault("language.code");
            if (code != null && !code.isEmpty()) {
                String normalized = code.toLowerCase().replace('-', '_');
                // If it's any Chinese variant, normalize to zh_cn
                if (normalized.startsWith("zh")) {
                    return "zh_cn";
                }
                return normalized;
            }
        } catch (Exception ignored) { /* fallback */ }
        return "en_us";
    }

    /**
     * Gets the localized sword name based on current language.
     */
    public static String getLocalizedSwordName() {
        String lang = getCurrentLanguage();
        return SWORD_NAMES.getOrDefault(lang, SWORD_NAMES.get("en_us"));
    }

    /**
     * Gets the localized lore lines based on current language.
     */
    public static List<String> getLocalizedLoreLines() {
        String lang = getCurrentLanguage();
        return LORE_LINES.getOrDefault(lang, LORE_LINES.get("en_us"));
    }

    /**
     * Gets the localized attribute display name.
     *
     * @param isDamage true for attack damage, false for attack speed
     */
    public static String getLocalizedAttribute(boolean isDamage) {
        String lang = getCurrentLanguage();
        Map<String, String> map = isDamage ? ATK_DAMAGE_NAMES : ATK_SPEED_NAMES;
        return map.getOrDefault(lang, map.get("en_us"));
    }

    /**
     * Checks whether a vanilla tooltip line matches the attack damage or attack speed attribute.
     * Matches both Chinese and English versions.
     */
    public static boolean isInfiniteAttributeLine(String lineStr) {
        if (lineStr == null || !lineStr.contains("∞")) {
            return false;
        }
        // Chinese: "攻击伤害" or "攻击速度"
        if (lineStr.contains("攻击") && (lineStr.contains("伤害") || lineStr.contains("速度"))) {
            return true;
        }
        // English: "Attack Damage" or "Attack Speed"
        String lower = lineStr.toLowerCase();
        return lower.contains("attack damage") || lower.contains("attack speed");
    }

    /**
     * Checks whether the line is an attack damage line (vs attack speed).
     */
    public static boolean isAttackDamageLine(String lineStr) {
        if (lineStr.contains("攻击伤害")) return true;
        String lower = lineStr.toLowerCase();
        return lower.contains("attack damage");
    }

    // ─── HSL to RGB conversion ───

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

    private static int getRgbColor(int charIndex) {
        return getRgbColor(charIndex, LIGHTNESS);
    }

    private static int getRgbColor(int charIndex, float lightness) {
        return getRgbColor(charIndex, lightness, SATURATION);
    }

    private static int getRgbColor(int charIndex, float lightness, float saturation) {
        int hueTenths = ((globalHueOffset + charIndex * HUE_STEP_PER_CHAR) % 3600 + 3600) % 3600;
        float hue = hueTenths / 10.0f;
        return hslToRgb(hue, saturation, lightness);
    }

    // ─── Tick update ───

    /**
     * Called each client tick to update the global hue offset.
     * Speed: advances 1.5 degrees of hue every 0.26 tick.
     */
    public static void onClientTick() {
        tickAccumulator += ACCUM_NUMERATOR;
        while (tickAccumulator >= ACCUM_DENOMINATOR) {
            tickAccumulator -= ACCUM_DENOMINATOR;
            globalHueOffset = ((globalHueOffset - 15) % 3600 + 3600) % 3600;
        }
    }

    // ─── Sword detection ───

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

    // ─── Rainbow text generation ───

    /**
     * Generates the rainbow "Infinite" attribute line with localized attribute name.
     */
    public static MutableComponent getRainbowInfiniteLine(boolean isDamage) {
        String attributeName = getLocalizedAttribute(isDamage);
        return getRainbowInfiniteLineRaw(attributeName);
    }

    /**
     * Generates the rainbow "Infinite" attribute line with the given attribute name.
     * Each character uses an independent true RGB color, fading clockwise around the color wheel.
     *
     * @param attributeName localized attribute name, e.g. "Attack Damage" or "攻击伤害"
     * @return attribute line with rainbow colors
     */
    public static MutableComponent getRainbowInfiniteLineRaw(String attributeName) {
        MutableComponent line = Component.literal(" ").withStyle(Style.EMPTY.withItalic(false));
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
     * Generates a rainbow name from the given text (used for arbitrary text coloring).
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
     * Generates the localized rainbow sword name.
     */
    public static MutableComponent getLocalizedRainbowName() {
        return getRainbowName(getLocalizedSwordName());
    }

    /**
     * Reads the raw lore lines of the OP golden sword from the item's custom_data.
     * Returns Chinese lore by default, falling back to localized version if not found.
     */
    public static List<String> getOpSwordLoreLines(ItemStack stack) {
        List<String> lines = new ArrayList<>();
        if (stack == null || stack.isEmpty()) {
            return lines;
        }
        var customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return getLocalizedLoreLines();
        }
        var tag = customData.copyTag();
        // Try localized (English) lore first
        if (tag.contains(LORE_EN_KEY, net.minecraft.nbt.Tag.TAG_LIST)) {
            var list = tag.getList(LORE_EN_KEY, net.minecraft.nbt.Tag.TAG_STRING);
            if (!list.isEmpty()) {
                for (int i = 0; i < list.size(); i++) {
                    lines.add(list.getString(i));
                }
                return lines;
            }
        }
        // Fall back to Chinese lore from NBT
        if (tag.contains(LORE_KEY, net.minecraft.nbt.Tag.TAG_LIST)) {
            var list = tag.getList(LORE_KEY, net.minecraft.nbt.Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                lines.add(list.getString(i));
            }
            return lines;
        }
        // Final fallback: use localized lore maps
        return getLocalizedLoreLines();
    }

    /**
     * Reads the CHINESE lore lines from NBT for text-matching purposes.
     * The tooltip renders Chinese lore (from DataComponents.LORE on the server),
     * so we need Chinese text to match and replace those lines.
     */
    public static List<String> getOpSwordLoreLinesForMatch(ItemStack stack) {
        List<String> lines = new ArrayList<>();
        if (stack == null || stack.isEmpty()) {
            return lines;
        }
        var customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return lines;
        }
        var tag = customData.copyTag();
        if (tag.contains(LORE_KEY, net.minecraft.nbt.Tag.TAG_LIST)) {
            var list = tag.getList(LORE_KEY, net.minecraft.nbt.Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                lines.add(list.getString(i));
            }
        }
        return lines;
    }

    /**
     * Generates rainbow italic text for a single lore line.
     * Lightness 40%, saturation 85%, italic.
     * Hue direction is reversed relative to the main name.
     */
    public static MutableComponent getRainbowLoreLine(String text) {
        String cleanText = text.replaceAll("§.", "");
        MutableComponent line = Component.literal("").withStyle(Style.EMPTY.withItalic(true));
        for (int i = 0; i < cleanText.length(); i++) {
            int rgb = getRgbColor(-i, LORE_LIGHTNESS, LORE_SATURATION);
            line.append(Component.literal(String.valueOf(cleanText.charAt(i)))
                    .withStyle(Style.EMPTY.withColor(rgb).withItalic(true)));
        }
        return line;
    }
}