package org.ipsecuz.pet.requirement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result object produced by evaluating a requirement or requirement group.
 * Provides rich metadata for GUI rendering, transaction planning, and feedback messaging.
 */
public class RequirementCheckResult {
    private final boolean satisfied;
    private final RequirementType type;
    private final String display;
    private final double requiredAmount;
    private final double currentAmount;
    private final String missingMessage;
    private final RequirementCheckResult selectedAlternative;
    private final List<RequirementCheckResult> subResults;

    public RequirementCheckResult(boolean satisfied,
                                  RequirementType type,
                                  String display,
                                  double requiredAmount,
                                  double currentAmount,
                                  String missingMessage,
                                  RequirementCheckResult selectedAlternative,
                                  List<RequirementCheckResult> subResults) {
        this.satisfied = satisfied;
        this.type = type;
        this.display = display != null ? display : "";
        this.requiredAmount = requiredAmount;
        this.currentAmount = currentAmount;
        this.missingMessage = missingMessage != null ? missingMessage : "";
        this.selectedAlternative = selectedAlternative;
        this.subResults = subResults != null ? Collections.unmodifiableList(new ArrayList<>(subResults)) : Collections.emptyList();
    }

    public static RequirementCheckResult single(boolean satisfied, RequirementType type, String display, double requiredAmount, double currentAmount, String missingMessage) {
        return new RequirementCheckResult(satisfied, type, display, requiredAmount, currentAmount, missingMessage, null, Collections.emptyList());
    }

    public static RequirementCheckResult group(boolean satisfied, List<RequirementCheckResult> subResults, RequirementCheckResult selectedAlternative, String display) {
        return new RequirementCheckResult(satisfied, RequirementType.GROUP, display, 0, 0, "", selectedAlternative, subResults);
    }

    public boolean isSatisfied() {
        return satisfied;
    }

    public RequirementType getType() {
        return type;
    }

    public String getDisplay() {
        return display;
    }

    public double getRequiredAmount() {
        return requiredAmount;
    }

    public double getCurrentAmount() {
        return currentAmount;
    }

    public String getMissingMessage() {
        return missingMessage;
    }

    public RequirementCheckResult getSelectedAlternative() {
        return selectedAlternative;
    }

    public List<RequirementCheckResult> getSubResults() {
        return subResults;
    }
}
