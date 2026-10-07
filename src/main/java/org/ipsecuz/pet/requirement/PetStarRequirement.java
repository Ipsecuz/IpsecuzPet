package org.ipsecuz.pet.requirement;

/**
 * Validates that the pet has reached a minimum star tier.
 * Non-consumable.
 */
public class PetStarRequirement implements Requirement {
    private final int requiredStars;

    public PetStarRequirement(int requiredStars) {
        this.requiredStars = Math.max(1, requiredStars);
    }

    public int getRequiredStars() {
        return requiredStars;
    }

    @Override
    public RequirementType getType() {
        return RequirementType.PET_STAR;
    }

    @Override
    public boolean isConsumable() {
        return false;
    }

    @Override
    public String getDisplay(RequirementContext ctx) {
        int cur = ctx != null ? ctx.getPetStars() : 1;
        return "Cấp sao: " + cur + "/" + requiredStars + "⭐";
    }

    @Override
    public RequirementCheckResult evaluate(RequirementContext ctx) {
        int current = ctx != null ? ctx.getPetStars() : 1;
        boolean satisfied = current >= requiredStars;
        String msg = satisfied ? "" : "Cần cấp sao " + requiredStars + "⭐ (Hiện tại: " + current + "⭐)";
        return RequirementCheckResult.single(satisfied, getType(), getDisplay(ctx), requiredStars, current, msg);
    }

    @Override
    public void planTransaction(RequirementContext ctx, RequirementTransaction tx) {
        // Non-consumable
    }
}
