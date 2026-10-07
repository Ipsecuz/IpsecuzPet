package org.ipsecuz.pet.requirement;

import org.bukkit.entity.Player;

/**
 * Validates that the player possesses a specific Bukkit permission node.
 * Non-consumable.
 */
public class PermissionRequirement implements Requirement {
    private final String permission;

    public PermissionRequirement(String permission) {
        if (permission == null || permission.trim().isEmpty()) {
            throw new IllegalArgumentException("Permission node cannot be null or empty");
        }
        this.permission = permission.trim();
    }

    public String getPermission() {
        return permission;
    }

    @Override
    public RequirementType getType() {
        return RequirementType.PERMISSION;
    }

    @Override
    public boolean isConsumable() {
        return false;
    }

    @Override
    public String getDisplay(RequirementContext ctx) {
        return "Quyền hạn: " + permission;
    }

    @Override
    public RequirementCheckResult evaluate(RequirementContext ctx) {
        Player player = ctx != null ? ctx.getPlayer() : null;
        boolean satisfied = player != null && player.hasPermission(permission);
        String msg = satisfied ? "" : "Cần quyền hạn: " + permission;
        return RequirementCheckResult.single(satisfied, getType(), getDisplay(ctx), 1, satisfied ? 1 : 0, msg);
    }

    @Override
    public void planTransaction(RequirementContext ctx, RequirementTransaction tx) {
        // Non-consumable
    }
}
