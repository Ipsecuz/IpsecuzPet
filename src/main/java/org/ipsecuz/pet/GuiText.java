package org.ipsecuz.pet;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adventure GUI color and component serialization utility.
 * Preserves standard Minecraft formatting codes as well as modern RGB hex codes (&#RRGGBB).
 */
public final class GuiText {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private static final LegacyComponentSerializer SECTION_SERIALIZER =
            LegacyComponentSerializer.builder()
                    .character('§')
                    .hexColors()
                    .useUnusualXRepeatedCharacterHexFormat()
                    .build();

    private GuiText() {}

    /**
     * Translates alternate color codes ('&') and hex format (&#RRGGBB) to section sign formatted text.
     */
    public static String colorize(String text) {
        if (text == null) return "";
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append('§').append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    /**
     * Converts formatted text to an Adventure Component.
     */
    public static Component component(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        String formatted = colorize(text);
        return SECTION_SERIALIZER.deserialize(formatted);
    }

    /**
     * Converts a list of formatted strings to a list of Adventure Components.
     */
    public static List<Component> components(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return Collections.emptyList();
        }
        List<Component> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(component(line));
        }
        return result;
    }

    /**
     * Colorizes a list of strings.
     */
    public static List<String> colorize(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(colorize(line));
        }
        return result;
    }
}
