package org.ipsecuz.pet.requirement;

import org.bukkit.entity.Player;
import org.ipsecuz.pet.CurrencyManager;

/**
 * Validates and deducts PlayerPoints.
 * Consumable, atomic, reversible.
 */
public class PointsRequirement implements Requirement {
    private final int amount;

    public PointsRequirement(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Points amount must be greater than 0");
        }
        this.amount = amount;
    }

    public int getAmount() {
        return amount;
    }

    @Override
    public RequirementType getType() {
        return RequirementType.POINTS;
    }

    @Override
    public boolean isConsumable() {
        return true;
    }

    @Override
    public String getDisplay(RequirementContext ctx) {
        return "Points: " + amount + " P";
    }

    @Override
    public RequirementCheckResult evaluate(RequirementContext ctx) {
        int current = 0;
        if (ctx != null && ctx.getPlayer() != null && ctx.getPlugin() != null) {
            CurrencyManager cm = ctx.getPlugin().getCurrencyManager();
            if (cm != null && cm.getPointsAPI() != null) {
                current = cm.getPointsAPI().look(ctx.getPlayerUUID());
            }
        }
        boolean satisfied = current >= amount;
        String display = "Points: " + current + " / " + amount + " P";
        String msg = satisfied ? "" : "Thiếu " + amount + " Points (Hiện có: " + current + " P)";
        return RequirementCheckResult.single(satisfied, getType(), display, amount, current, msg);
    }

    @Override
    public void planTransaction(RequirementContext ctx, RequirementTransaction tx) {
        if (ctx == null || ctx.getPlayer() == null || ctx.getPlugin() == null) return;
        final CurrencyManager cm = ctx.getPlugin().getCurrencyManager();
        if (cm == null) return;

        tx.addStep(new RequirementTransaction.TransactionStep() {
            @Override
            public boolean execute(Player player) {
                return cm.withdrawPoints(player, amount);
            }

            @Override
            public void rollback(Player player) {
                cm.givePoints(player, amount);
            }

            @Override
            public String getDescription() {
                return "Withdraw " + amount + " Points from player " + (ctx.getPlayer() != null ? ctx.getPlayer().getName() : "null");
            }
        });
    }
}
