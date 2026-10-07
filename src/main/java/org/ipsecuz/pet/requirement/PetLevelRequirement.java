package org.ipsecuz.pet.requirement;

/**
 * Validates that the active or target pet has reached a minimum level.
 * Non-consumable.
 */
public class PetLevelRequirement implements Requirement {
    private final int requiredLevel;

    public PetLevelRequirement(int requiredLevel) {
        this.requiredLevel = Math.max(1, requiredLevel);
    }

    public int getRequiredLevel() {
        return requiredLevel;
    }

    @Override
    public RequirementType getType() {
        return RequirementType.PET_LEVEL;
    }

    @Override
    public boolean isConsumable() {
        return false;
    }

    @Override
    public String getDisplay(RequirementContext ctx) {
        int cur = ctx != null ? ctx.getPetLevel() : 1;
        return "Cấp độ thú cưng: Lv." + cur + "/" + requiredLevel;
    }

    @Override
    public RequirementCheckResult evaluate(RequirementContext ctx) {
        int current = ctx != null ? ctx.getPetLevel() : 1;
        boolean satisfied = current >= requiredLevel;
        String msg = satisfied ? "" : "Cần cấp độ Lv." + requiredLevel + " (Hiện tại: Lv." + current + ")";
        return RequirementCheckResult.single(satisfied, getType(), getDisplay(ctx), requiredLevel, current, msg);
    }

    @Override
    public void planTransaction(RequirementContext ctx, RequirementTransaction tx) {
        // Non-consumable, no transaction steps
    }
}
