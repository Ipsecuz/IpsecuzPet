package org.ipsecuz.pet.requirement;

/**
 * Universal interface for all individual requirements and requirement groups.
 */
public interface Requirement {
    /**
     * @return The requirement type
     */
    RequirementType getType();

    /**
     * @return true if this requirement consumes/deducts resources upon commit
     */
    boolean isConsumable();

    /**
     * @param ctx The requirement evaluation context
     * @return Formatted human-readable display string
     */
    String getDisplay(RequirementContext ctx);

    /**
     * Evaluates whether the requirement is satisfied without consuming resources.
     *
     * @param ctx The requirement evaluation context
     * @return Detailed check result
     */
    RequirementCheckResult evaluate(RequirementContext ctx);

    /**
     * Contributes consumption steps to the two-phase transaction plan.
     * Called only after all preconditions are verified satisfied.
     *
     * @param ctx The requirement evaluation context
     * @param tx The transaction builder
     */
    void planTransaction(RequirementContext ctx, RequirementTransaction tx);
}
