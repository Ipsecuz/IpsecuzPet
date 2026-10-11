package org.ipsecuz.pet;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
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
 * Automatically suppresses unwanted italic formatting for GUI items and lore.
 */
public final class GuiText {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern PREFIX_PATTERN = Pattern.compile("(?i)^(?:\\s*(?:§[0-9a-fk-orx]|&[0-9a-fk-orx]|&#[A-Fa-f0-9]{6}))*\\[(?:\\s*(?:§[0-9a-fk-orx]|&[0-9a-fk-orx]|&#[A-Fa-f0-9]{6}))*IpsecuzPets?(?:\\s*(?:§[0-9a-fk-orx]|&[0-9a-fk-orx]|&#[A-Fa-f0-9]{6}))*\\](?:\\s*(?:§r|&r))*\\s*");

    private static final LegacyComponentSerializer SECTION_SERIALIZER =
            LegacyComponentSerializer.builder()
                    .character('§')
                    .hexColors()
                    .useUnusualXRepeatedCharacterHexFormat()
                    .build();

    private GuiText() {}

    /**
     * Strips any unintentional plugin prefix (e.g., "[IpsecuzPets]") and surrounding color codes
     * from GUI titles, item names, and lore lines.
     */
    public static String stripPrefix(String text) {
        if (text == null || text.isEmpty()) return "";
        Matcher matcher = PREFIX_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.replaceFirst("");
        }
        return text;
    }

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
     * Converts formatted text to an Adventure Component with italics disabled by default.
     * Automatically purges any unwanted prefix tags.
     */
    public static Component component(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty().decoration(TextDecoration.ITALIC, false);
        }
        String clean = stripPrefix(text);
        String formatted = colorize(clean);
        Component deserialized = SECTION_SERIALIZER.deserialize(formatted);
        return stripItalics(deserialized);
    }

    /**
     * Converts formatted text to an Adventure Component specifically for inventory titles.
     * Automatically purges any unwanted prefix tags and suppresses italic.
     */
    public static Component title(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        String clean = stripPrefix(text);
        String formatted = colorize(clean);
        return stripItalics(SECTION_SERIALIZER.deserialize(formatted));
    }

    /**
     * Ensures that the root component has TextDecoration.ITALIC set to false if unset.
     */
    public static Component stripItalics(Component comp) {
        if (comp == null) return Component.empty();
        if (comp.decoration(TextDecoration.ITALIC) == TextDecoration.State.NOT_SET) {
            return comp.decoration(TextDecoration.ITALIC, false);
        }
        return comp;
    }

    /**
     * Converts a list of formatted strings to a list of non-italic Adventure Components.
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

    /**
     * Builds a standardized progress bar string.
     * Example: [§a████§7░░░░§b] §e50%
     */
    public static String progressBar(int current, int max, int totalBars) {
        if (max <= 0) max = 1;
        double ratio = Math.max(0.0, Math.min(1.0, (double) current / max));
        int filled = (int) Math.round(ratio * totalBars);
        StringBuilder sb = new StringBuilder("§b[§a");
        for (int i = 0; i < filled; i++) sb.append("█");
        sb.append("§7");
        for (int i = filled; i < totalBars; i++) sb.append("░");
        sb.append("§b] §e").append((int) (ratio * 100)).append("%");
        return sb.toString();
    }
}
