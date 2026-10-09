package org.ipsecuz.pet;

import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Standardized, fluent lore builder for IpsecuzPet GUI items.
 * Enforces a consistent visual hierarchy, color palette, semantic statuses, and italic suppression.
 */
public class GuiLoreBuilder {

    private final List<Component> lines = new ArrayList<>();

    public GuiLoreBuilder() {}

    public static GuiLoreBuilder create() {
        return new GuiLoreBuilder();
    }

    /**
     * Important section heading: Gold bold with star adornments (✦ HEADING ✦).
     */
    public GuiLoreBuilder heading(String heading) {
        if (heading != null && !heading.isEmpty()) {
            lines.add(GuiText.component("§6§l✦ " + heading.toUpperCase() + " ✦"));
        }
        return this;
    }

    /**
     * Secondary sub-heading: Yellow bold.
     */
    public GuiLoreBuilder subHeading(String text) {
        if (text != null && !text.isEmpty()) {
            lines.add(GuiText.component("§e§l" + text));
        }
        return this;
    }

    /**
     * Standard explanatory line: Gray.
     */
    public GuiLoreBuilder desc(String text) {
        if (text != null && !text.isEmpty()) {
            lines.add(GuiText.component("§7" + text));
        }
        return this;
    }

    public GuiLoreBuilder description(String text) {
        return desc(text);
    }

    public GuiLoreBuilder raw(String text) {
        return line(text);
    }

    public GuiLoreBuilder components(List<Component> components) {
        return lines(components);
    }

    /**
     * Subtle secondary hint or note: Dark gray.
     */
    public GuiLoreBuilder hint(String text) {
        if (text != null && !text.isEmpty()) {
            lines.add(GuiText.component("§8" + text));
        }
        return this;
    }

    /**
     * Standard statistic row: Gray label, White numeric value.
     */
    public GuiLoreBuilder stat(String label, Object value) {
        lines.add(GuiText.component("§7" + label + ": §f" + value));
        return this;
    }

    /**
     * Statistic row with unit suffix (e.g. ❤, ⚔, 🛡).
     */
    public GuiLoreBuilder stat(String label, Object value, String suffix) {
        String s = (suffix != null && !suffix.isEmpty()) ? " " + suffix : "";
        lines.add(GuiText.component("§7" + label + ": §f" + value + s));
        return this;
    }

    /**
     * Statistic row with custom value color code.
     */
    public GuiLoreBuilder statColored(String label, Object value, String valueColorCode) {
        lines.add(GuiText.component("§7" + label + ": " + valueColorCode + value));
        return this;
    }

    /**
     * Statistic comparison row: Gray label, Current white, Next green, Optional diff.
     */
    public GuiLoreBuilder statCompare(String label, Object cur, Object next, String diff) {
        String diffStr = (diff != null && !diff.isEmpty()) ? " " + diff : "";
        lines.add(GuiText.component("§7" + label + ": §f" + cur + " §8➔ §a" + next + diffStr));
        return this;
    }

    public GuiLoreBuilder statCompare(String label, Object cur, Object next) {
        return statCompare(label, cur, next, "");
    }

    /**
     * Informational value row: Gray label, Aqua value.
     */
    public GuiLoreBuilder info(String label, Object value) {
        lines.add(GuiText.component("§7" + label + ": §b" + value));
        return this;
    }

    /**
     * Price/reward row: Gray label, Gold price.
     */
    public GuiLoreBuilder price(String label, Object cost) {
        lines.add(GuiText.component("§7" + label + ": §6" + cost));
        return this;
    }

    public GuiLoreBuilder price(String text) {
        if (text != null && !text.isEmpty()) {
            lines.add(GuiText.component(text.startsWith("§") ? text : "§6" + text));
        }
        return this;
    }

    /**
     * Positive/success status row: Green with checkmark.
     */
    public GuiLoreBuilder success(String text) {
        lines.add(GuiText.component("§a✔ " + text));
        return this;
    }

    /**
     * Error/unavailable status row: Red with cross.
     */
    public GuiLoreBuilder failure(String text) {
        lines.add(GuiText.component("§c✖ " + text));
        return this;
    }

    /**
     * Warning status row: Yellow with warning symbol.
     */
    public GuiLoreBuilder warning(String text) {
        lines.add(GuiText.component("§e⚠ " + text));
        return this;
    }

    /**
     * Binary status row (Satisfied/Unsatisfied).
     */
    public GuiLoreBuilder status(boolean ok, String okText, String failText) {
        if (ok) {
            lines.add(GuiText.component("§a✔ " + okText));
        } else {
            lines.add(GuiText.component("§c✖ " + failText));
        }
        return this;
    }

    public GuiLoreBuilder status(boolean ok, String text) {
        return ok ? success(text) : failure(text);
    }

    /**
     * Standard action instruction: Aqua ▸ prefix followed by instruction.
     */
    public GuiLoreBuilder action(String hint) {
        lines.add(GuiText.component("§b▸ §f" + hint));
        return this;
    }

    /**
     * Action instruction with click-type label (e.g. Left-click / Right-click).
     */
    public GuiLoreBuilder action(String clickLabel, String hint) {
        lines.add(GuiText.component("§b▸ §7" + clickLabel + ": §f" + hint));
        return this;
    }

    /**
     * Standardized progress bar row.
     */
    public GuiLoreBuilder progressBar(String label, int current, int max, int totalBars) {
        lines.add(GuiText.component("§7" + label + ": " + GuiText.progressBar(current, max, totalBars)));
        return this;
    }

    /**
     * Adds an arbitrary formatted line.
     */
    public GuiLoreBuilder line(String rawLine) {
        if (rawLine != null) {
            lines.add(GuiText.component(rawLine));
        }
        return this;
    }

    /**
     * Adds a pre-built Adventure Component (suppressing unwanted italics).
     */
    public GuiLoreBuilder line(Component component) {
        if (component != null) {
            lines.add(GuiText.stripItalics(component));
        }
        return this;
    }

    /**
     * Appends multiple pre-built Adventure Components.
     */
    public GuiLoreBuilder lines(List<Component> components) {
        if (components != null) {
            for (Component c : components) {
                line(c);
            }
        }
        return this;
    }

    /**
     * Subtle dark-gray strikethrough section divider.
     */
    public GuiLoreBuilder separator() {
        lines.add(GuiText.component("§8§m------------------------"));
        return this;
    }

    /**
     * Blank spacing line.
     */
    public GuiLoreBuilder blank() {
        lines.add(GuiText.component(" "));
        return this;
    }

    /**
     * Builds and returns an unmodifiable list of non-italic Adventure Components.
     */
    public List<Component> build() {
        return Collections.unmodifiableList(new ArrayList<>(lines));
    }
}
