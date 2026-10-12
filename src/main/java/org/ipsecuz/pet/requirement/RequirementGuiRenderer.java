package org.ipsecuz.pet.requirement;

import net.kyori.adventure.text.Component;
import org.ipsecuz.pet.GuiText;
import org.ipsecuz.pet.IpsecuzPet;
import org.ipsecuz.pet.LanguageManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders structured Adventure components and legacy text lore for requirement checklists,
 * clearly distinguishing mandatory ALL conditions from alternative ONE_OF payment choices.
 */
public class RequirementGuiRenderer {

    private static String getMessage(String key, String def) {
        try {
            IpsecuzPet plugin = IpsecuzPet.getInstance();
            if (plugin != null && plugin.getLanguage() != null) {
                String val = plugin.getLanguage().getRawMessage(key);
                if (val != null && !val.startsWith("§cMissing")) {
                    return val;
                }
            }
        } catch (Throwable ignored) {}
        return def;
    }

    /**
     * Renders a RequirementCheckResult into Adventure Component lore lines.
     * Uses centralized GuiText serializer to properly parse colors and strip unwanted formatting.
     */
    public static List<Component> renderToComponents(RequirementCheckResult rootResult) {
        List<String> lines = renderToStrings(rootResult);
        List<Component> components = new ArrayList<>(lines.size());
        for (String line : lines) {
            components.add(GuiText.component(line));
        }
        return components;
    }

    /**
     * Renders a RequirementCheckResult into formatted color-coded string lore lines.
     */
    public static List<String> renderToStrings(RequirementCheckResult rootResult) {
        List<String> lines = new ArrayList<>();
        if (rootResult == null) {
            lines.add(getMessage("requirement.none", "§a[✔] Không có yêu cầu!"));
            return lines;
        }

        if (rootResult.getType() != RequirementType.GROUP) {
            renderLeaf(rootResult, lines, "  ");
        } else {
            renderGroup(rootResult, lines, "");
        }

        lines.add(getMessage("requirement.divider", "§7--------------------"));
        if (rootResult.isSatisfied()) {
            lines.add(getMessage("requirement.ready", "§a§l✔ ĐỦ ĐIỀU KIỆN (SẴN SÀNG)"));
        } else {
            lines.add(getMessage("requirement.not_ready", "§c§l✖ CHƯA ĐỦ ĐIỀU KIỆN"));
        }

        return lines;
    }

    private static void renderGroup(RequirementCheckResult groupResult, List<String> lines, String indent) {
        List<RequirementCheckResult> sub = groupResult.getSubResults();
        if (sub.isEmpty()) {
            lines.add(indent + getMessage("requirement.no_cost", "§a[✔] Không có yêu cầu chi phí!"));
            return;
        }

        boolean hasNestedGroups = false;
        for (RequirementCheckResult child : sub) {
            if (child.getType() == RequirementType.GROUP) {
                hasNestedGroups = true;
                break;
            }
        }

        if (hasNestedGroups) {
            for (RequirementCheckResult child : sub) {
                if (child.getType() == RequirementType.GROUP) {
                    renderSpecificGroup(child, lines, indent);
                } else {
                    renderLeaf(child, lines, indent);
                }
            }
        } else {
            renderSpecificGroup(groupResult, lines, indent);
        }
    }

    private static void renderSpecificGroup(RequirementCheckResult groupResult, List<String> lines, String indent) {
        boolean isOneOf = groupResult.getSelectedAlternative() != null || "CHỌN 1 (ONE OF)".equals(groupResult.getDisplay());

        if (isOneOf) {
            lines.add(indent + getMessage("requirement.one_of_header", "§e§lCHỌN 1 HÌNH THỨC (CHOOSE ONE):"));
            RequirementCheckResult selected = groupResult.getSelectedAlternative();
            String selectedBadge = getMessage("requirement.selected_badge", "§a(Đã chọn) §f");
            for (RequirementCheckResult item : groupResult.getSubResults()) {
                if (item.getType() == RequirementType.GROUP) {
                    renderGroup(item, lines, indent + "  ");
                    continue;
                }
                if (item == selected) {
                    lines.add(indent + "  §a[✔] " + selectedBadge + item.getDisplay());
                } else if (item.isSatisfied()) {
                    lines.add(indent + "  §a[✔] §7" + item.getDisplay());
                } else {
                    lines.add(indent + "  §c[✖] §7" + item.getDisplay());
                }
            }
        } else {
            lines.add(indent + getMessage("requirement.all_header", "§6§lĐIỀU KIỆN BẮT BUỘC (ALL):"));
            for (RequirementCheckResult item : groupResult.getSubResults()) {
                if (item.getType() == RequirementType.GROUP) {
                    renderGroup(item, lines, indent + "  ");
                } else {
                    renderLeaf(item, lines, indent + "  ");
                }
            }
        }
    }

    private static void renderLeaf(RequirementCheckResult leaf, List<String> lines, String indent) {
        String icon = leaf.isSatisfied() ? "§a[✔] " : "§c[✖] ";
        String color = leaf.isSatisfied() ? "§f" : "§7";
        lines.add(indent + icon + color + leaf.getDisplay());
    }
}
