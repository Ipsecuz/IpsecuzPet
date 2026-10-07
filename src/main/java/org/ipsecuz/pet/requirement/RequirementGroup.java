package org.ipsecuz.pet.requirement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Composite requirement holding child requirements, evaluated using
 * ALL (conjunction / AND) or ONE_OF (disjunction / OR) semantics.
 */
public class RequirementGroup implements Requirement {
    private final RequirementGroupType groupType;
    private final List<Requirement> requirements = new ArrayList<>();

    public RequirementGroup(RequirementGroupType groupType) {
        this.groupType = groupType != null ? groupType : RequirementGroupType.ALL;
    }

    public RequirementGroup(RequirementGroupType groupType, List<Requirement> reqs) {
        this(groupType);
        if (reqs != null) {
            this.requirements.addAll(reqs);
        }
    }

    public void addRequirement(Requirement requirement) {
        if (requirement != null) {
            requirements.add(requirement);
        }
    }

    public RequirementGroupType getGroupType() {
        return groupType;
    }

    public List<Requirement> getRequirements() {
        return Collections.unmodifiableList(requirements);
    }

    @Override
    public RequirementType getType() {
        return RequirementType.GROUP;
    }

    @Override
    public boolean isConsumable() {
        for (Requirement r : requirements) {
            if (r.isConsumable()) return true;
        }
        return false;
    }

    @Override
    public String getDisplay(RequirementContext ctx) {
        return groupType == RequirementGroupType.ALL ? "BẮT BUỘC (ALL)" : "CHỌN 1 (ONE OF)";
    }

    @Override
    public RequirementCheckResult evaluate(RequirementContext ctx) {
        List<RequirementCheckResult> subResults = new ArrayList<>();

        if (groupType == RequirementGroupType.ALL) {
            boolean allMet = true;
            for (Requirement r : requirements) {
                RequirementCheckResult res = r.evaluate(ctx);
                subResults.add(res);
                if (!res.isSatisfied()) {
                    allMet = false;
                }
            }
            return RequirementCheckResult.group(allMet, subResults, null, getDisplay(ctx));
        } else {
            // ONE_OF semantics: First satisfied option in configuration order is selected
            RequirementCheckResult selected = null;
            for (Requirement r : requirements) {
                RequirementCheckResult res = r.evaluate(ctx);
                subResults.add(res);
                if (res.isSatisfied() && selected == null) {
                    selected = res;
                }
            }
            boolean satisfied = (selected != null || requirements.isEmpty());
            return RequirementCheckResult.group(satisfied, subResults, selected, getDisplay(ctx));
        }
    }

    @Override
    public void planTransaction(RequirementContext ctx, RequirementTransaction tx) {
        if (groupType == RequirementGroupType.ALL) {
            for (Requirement r : requirements) {
                if (r.isConsumable()) {
                    r.planTransaction(ctx, tx);
                }
            }
        } else {
            // ONE_OF semantics: only plan consumption for the first satisfied alternative
            for (Requirement r : requirements) {
                if (r.evaluate(ctx).isSatisfied()) {
                    if (r.isConsumable()) {
                        r.planTransaction(ctx, tx);
                    }
                    break;
                }
            }
        }
    }
}
