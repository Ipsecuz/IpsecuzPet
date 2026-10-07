package org.ipsecuz.pet.requirement;

import org.bukkit.entity.Player;
import org.ipsecuz.pet.CurrencyManager;

/**
 * Validates and deducts Vault currency.
 * Consumable, atomic, reversible.
 */
public class MoneyRequirement implements Requirement {
    private final double amount;

    public MoneyRequirement(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Money amount must be greater than 0");
        }
        this.amount = amount;
    }

    public double getAmount() {
        return amount;
    }

    @Override
    public RequirementType getType() {
        return RequirementType.MONEY;
    }

    @Override
    public boolean isConsumable() {
        return true;
    }

    @Override
    public String getDisplay(RequirementContext ctx) {
        return "Tiền xu: $" + (amount == (long) amount ? String.valueOf((long) amount) : String.format("%.2f", amount));
    }

    @Override
    public RequirementCheckResult evaluate(RequirementContext ctx) {
        double current = 0;
        if (ctx != null && ctx.getPlayer() != null && ctx.getPlugin() != null) {
            CurrencyManager cm = ctx.getPlugin().getCurrencyManager();
            if (cm != null && cm.getEconomy() != null) {
                current = cm.getEconomy().getBalance(ctx.getPlayer());
            }
        }
        boolean satisfied = current >= amount;
        String curStr = current == (long) current ? String.valueOf((long) current) : String.format("%.1f", current);
        String reqStr = amount == (long) amount ? String.valueOf((long) amount) : String.format("%.1f", amount);
        String display = "Tiền xu: $" + curStr + " / $" + reqStr;
        String msg = satisfied ? "" : "Thiếu $" + reqStr + " (Hiện có: $" + curStr + ")";
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
                return cm.withdrawMoney(player, amount);
            }

            @Override
            public void rollback(Player player) {
                cm.depositMoney(player, amount);
            }

            @Override
            public String getDescription() {
                return "Withdraw $" + amount + " from player " + (ctx.getPlayer() != null ? ctx.getPlayer().getName() : "null");
            }
        });
    }
}
