package org.ipsecuz.pet.requirement;

import org.bukkit.entity.Player;
import org.ipsecuz.pet.IpsecuzPet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * Two-phase atomic transaction executor.
 * Collects planned execution steps, verifies preconditions, commits them atomically,
 * and performs strict reverse rollback in case of partial failure.
 */
public class RequirementTransaction {

    public interface TransactionStep {
        boolean execute(Player player) throws Exception;
        void rollback(Player player);
        String getDescription();
    }

    private final List<TransactionStep> steps = new ArrayList<>();

    public void addStep(TransactionStep step) {
        if (step != null) {
            steps.add(step);
        }
    }

    public List<TransactionStep> getSteps() {
        return Collections.unmodifiableList(steps);
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }

    public int size() {
        return steps.size();
    }

    /**
     * Executes all transaction steps atomically.
     * If any step fails or throws an exception, all executed steps are rolled back in reverse order.
     *
     * @param player Target player
     * @return true if all steps committed successfully, false if aborted and rolled back
     */
    public boolean execute(Player player) {
        List<TransactionStep> executedSteps = new ArrayList<>();
        IpsecuzPet plugin = IpsecuzPet.getInstance();

        for (TransactionStep step : steps) {
            try {
                boolean ok = step.execute(player);
                if (!ok) {
                    if (plugin != null) {
                        plugin.getLogger().warning("[RequirementEngine] Transaction step rejected: " + step.getDescription() + " for " + (player != null ? player.getName() : "null"));
                    }
                    rollback(player, executedSteps);
                    return false;
                }
                executedSteps.add(step);
            } catch (Exception ex) {
                if (plugin != null) {
                    plugin.getLogger().log(Level.SEVERE, "[RequirementEngine] Exception during step execution: " + step.getDescription(), ex);
                }
                rollback(player, executedSteps);
                return false;
            }
        }

        return true;
    }

    private void rollback(Player player, List<TransactionStep> executedSteps) {
        IpsecuzPet plugin = IpsecuzPet.getInstance();
        if (plugin != null) {
            plugin.getLogger().warning("[RequirementEngine] Initiating atomic transaction rollback (" + executedSteps.size() + " steps)...");
        }
        for (int i = executedSteps.size() - 1; i >= 0; i--) {
            TransactionStep step = executedSteps.get(i);
            try {
                step.rollback(player);
                if (plugin != null) {
                    plugin.getLogger().info("[RequirementEngine] Rolled back: " + step.getDescription());
                }
            } catch (Exception ex) {
                if (plugin != null) {
                    plugin.getLogger().log(Level.SEVERE, "[RequirementEngine] Critical error during rollback of: " + step.getDescription(), ex);
                }
            }
        }
    }
}
