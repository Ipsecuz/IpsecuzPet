package org.ipsecuz.pet.requirement;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.ipsecuz.pet.IpsecuzPet;

import java.util.Map;

/**
 * Central service orchestrating universal requirement parsing, validation,
 * atomic transactions, and UI rendering across all plugin systems.
 */
public class RequirementManager {
    private final IpsecuzPet plugin;

    public RequirementManager(IpsecuzPet plugin) {
        this.plugin = plugin;
    }

    public RequirementGroup parse(ConfigurationSection section) {
        return RequirementParser.parse(section);
    }

    public RequirementGroup parse(Map<String, Object> map) {
        return RequirementParser.parse(map);
    }

    public RequirementCheckResult evaluate(RequirementGroup group, RequirementContext ctx) {
        if (group == null) {
            return RequirementCheckResult.group(true, java.util.Collections.emptyList(), null, "Miễn phí");
        }
        return group.evaluate(ctx);
    }

    /**
     * Executes the requirement transaction atomically.
     * Evaluates preconditions, compiles transaction plan, commits or rolls back,
     * and sends localized error feedback to the player if rejected.
     */
    public boolean executeTransaction(RequirementGroup group, RequirementContext ctx) {
        if (group == null) return true;

        RequirementCheckResult result = group.evaluate(ctx);
        if (!result.isSatisfied()) {
            sendFailureFeedback(ctx, result);
            return false;
        }

        RequirementTransaction tx = new RequirementTransaction();
        group.planTransaction(ctx, tx);

        Player player = ctx != null ? ctx.getPlayer() : null;
        boolean ok = tx.execute(player);
        if (!ok && player != null) {
            String failMsg = plugin.getLanguage() != null
                    ? plugin.getLanguage().getMessage("requirement.transaction_failed")
                    : "§cThực hiện giao dịch chi phí thất bại! Đã hoàn trả tài nguyên.";
            player.sendMessage(failMsg);
        }

        return ok;
    }

    /**
     * Helper to parse, validate and deduct requirements from a ConfigurationSection.
     */
    public boolean checkAndDeduct(Player player, ConfigurationSection section, String petId, int petLevel, int petStars) {
        RequirementGroup group = parse(section);
        RequirementContext ctx = new RequirementContext(player, petId, petLevel, petStars, plugin);
        return executeTransaction(group, ctx);
    }

    private void sendFailureFeedback(RequirementContext ctx, RequirementCheckResult result) {
        if (ctx == null || ctx.getPlayer() == null) return;
        Player player = ctx.getPlayer();

        String missing = findFirstMissingMessage(result);
        if (missing == null || missing.isEmpty()) {
            missing = "Không đủ điều kiện yêu cầu!";
        }

        String template = plugin.getLanguage() != null
                ? plugin.getLanguage().getMessage("requirement.not_met", "%missing%", missing)
                : "§cBạn chưa đáp ứng đủ điều kiện: §e" + missing;
        player.sendMessage(template);
    }

    private String findFirstMissingMessage(RequirementCheckResult res) {
        if (res == null) return null;
        if (!res.isSatisfied()) {
            if (!res.getMissingMessage().isEmpty()) {
                return res.getMissingMessage();
            }
            for (RequirementCheckResult sub : res.getSubResults()) {
                String subMsg = findFirstMissingMessage(sub);
                if (subMsg != null && !subMsg.isEmpty()) {
                    return subMsg;
                }
            }
            if (res.getSelectedAlternative() == null && "CHỌN 1 (ONE OF)".equals(res.getDisplay())) {
                return "Cần thỏa mãn ít nhất 1 hình thức chi phí trong nhóm tùy chọn!";
            }
        }
        return null;
    }
}
