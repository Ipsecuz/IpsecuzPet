package org.ipsecuz.pet.requirement;

import org.ipsecuz.pet.ConfigManager;
import org.ipsecuz.pet.PetOwnershipManager;

import java.util.UUID;

/**
 * Validates whether the player already owns or does not yet own a specific pet.
 * Non-consumable.
 */
public class PetOwnershipRequirement implements Requirement {
    private final String targetPetId;
    private final boolean mustBeOwned;

    public PetOwnershipRequirement(String targetPetId, boolean mustBeOwned) {
        if (targetPetId == null || targetPetId.trim().isEmpty()) {
            throw new IllegalArgumentException("Target pet id cannot be null or empty");
        }
        this.targetPetId = targetPetId.trim();
        this.mustBeOwned = mustBeOwned;
    }

    public String getTargetPetId() {
        return targetPetId;
    }

    public boolean isMustBeOwned() {
        return mustBeOwned;
    }

    @Override
    public RequirementType getType() {
        return mustBeOwned ? RequirementType.PET_OWNED : RequirementType.PET_NOT_OWNED;
    }

    @Override
    public boolean isConsumable() {
        return false;
    }

    @Override
    public String getDisplay(RequirementContext ctx) {
        String petName = targetPetId;
        if (ctx != null && ctx.getPlugin() != null) {
            petName = ctx.getPlugin().getConfig().getString("pets." + targetPetId + ".name", targetPetId);
        }
        return (mustBeOwned ? "Cần sở hữu pet: " : "Chưa sở hữu pet: ") + petName;
    }

    @Override
    public RequirementCheckResult evaluate(RequirementContext ctx) {
        boolean owned = false;
        if (ctx != null && ctx.getPlayerUUID() != null && ctx.getPlugin() != null) {
            UUID uuid = ctx.getPlayerUUID();
            PetOwnershipManager om = ctx.getPlugin().getOwnershipManager();
            if (om != null && om.hasPet(uuid, targetPetId)) {
                owned = true;
            } else {
                ConfigManager cm = ctx.getPlugin().getConfigManager();
                if (cm != null && cm.getData().contains(uuid + ".pets." + targetPetId)) {
                    owned = true;
                }
            }
        }

        boolean satisfied = (mustBeOwned == owned);
        String msg = satisfied ? "" : (mustBeOwned ? "Bạn chưa sở hữu thú cưng: " + targetPetId : "Bạn đã sở hữu thú cưng này rồi: " + targetPetId);
        return RequirementCheckResult.single(satisfied, getType(), getDisplay(ctx), 1, satisfied ? 1 : 0, msg);
    }

    @Override
    public void planTransaction(RequirementContext ctx, RequirementTransaction tx) {
        // Non-consumable
    }
}
