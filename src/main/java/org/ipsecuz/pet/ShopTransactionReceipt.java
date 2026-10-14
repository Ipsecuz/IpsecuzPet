package org.ipsecuz.pet;

import org.bukkit.entity.Player;
import org.ipsecuz.pet.requirement.RequirementTransaction;

/**
 * Authoritative receipt of a pet shop transaction.
 * Tracks deducted resources and executes strict, safe rollbacks without resource substitutions.
 */
public class ShopTransactionReceipt {

    public enum Status {
        SUCCESS,
        INSUFFICIENT_RESOURCES,
        PROVIDER_UNAVAILABLE,
        INVALID_CONFIG,
        DEDUCTION_FAILED
    }

    private final Status status;
    private final ShopPriceFormatter.PriceType priceType;
    private final double amount;
    private final String itemIdentifier;
    private final RequirementTransaction requirementTransaction;
    private final String errorMessage;

    public ShopTransactionReceipt(Status status, ShopPriceFormatter.PriceType priceType, double amount,
                                  String itemIdentifier, RequirementTransaction requirementTransaction, String errorMessage) {
        this.status = status;
        this.priceType = priceType;
        this.amount = amount;
        this.itemIdentifier = itemIdentifier;
        this.requirementTransaction = requirementTransaction;
        this.errorMessage = errorMessage;
    }

    public static ShopTransactionReceipt successMoney(double amount) {
        return new ShopTransactionReceipt(Status.SUCCESS, ShopPriceFormatter.PriceType.MONEY, amount, null, null, null);
    }

    public static ShopTransactionReceipt successPoints(int amount) {
        return new ShopTransactionReceipt(Status.SUCCESS, ShopPriceFormatter.PriceType.POINTS, amount, null, null, null);
    }

    public static ShopTransactionReceipt successItem(String itemIdentifier, int amount) {
        return new ShopTransactionReceipt(Status.SUCCESS, ShopPriceFormatter.PriceType.ITEM, amount, itemIdentifier, null, null);
    }

    public static ShopTransactionReceipt successRequirements(RequirementTransaction tx) {
        return new ShopTransactionReceipt(Status.SUCCESS, ShopPriceFormatter.PriceType.STRUCTURED_REQUIREMENTS, 0, null, tx, null);
    }

    public static ShopTransactionReceipt successFree() {
        return new ShopTransactionReceipt(Status.SUCCESS, ShopPriceFormatter.PriceType.FREE, 0, null, null, null);
    }

    public static ShopTransactionReceipt failure(Status status, String errorMessage) {
        return new ShopTransactionReceipt(status, ShopPriceFormatter.PriceType.INVALID, 0, null, null, errorMessage);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public Status getStatus() {
        return status;
    }

    public ShopPriceFormatter.PriceType getPriceType() {
        return priceType;
    }

    public double getAmount() {
        return amount;
    }

    public String getItemIdentifier() {
        return itemIdentifier;
    }

    public RequirementTransaction getRequirementTransaction() {
        return requirementTransaction;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Executes safe, atomic rollback of the exact resources deducted.
     * Guarantees:
     * - MONEY: deposits back through Vault Economy.
     * - POINTS: gives back through PlayerPoints API.
     * - ITEM: returns exact material / custom item; if custom item provider is unavailable,
     *   NEVER substitutes with Diamond or Stone; returns false so pending refund is persisted.
     * - STRUCTURED_REQUIREMENTS: never fabricates wrong legacy refund.
     *
     * @param player Target player
     * @param plugin Plugin instance
     * @return true if rollback succeeded, false if external provider failed or was unavailable
     */
    public boolean rollback(Player player, IpsecuzPet plugin) {
        if (player == null || plugin == null) return false;
        if (!isSuccess()) return false;
        if (priceType == ShopPriceFormatter.PriceType.FREE) return true;

        if (priceType == ShopPriceFormatter.PriceType.MONEY) {
            return plugin.getCurrencyManager().depositMoney(player, amount);
        }

        if (priceType == ShopPriceFormatter.PriceType.POINTS) {
            return plugin.getCurrencyManager().givePoints(player, (int) amount);
        }

        if (priceType == ShopPriceFormatter.PriceType.ITEM) {
            if (itemIdentifier == null || amount <= 0) return false;
            ItemHookManager ihm = plugin.getItemHookManager();
            if (ihm == null) return false;
            return ihm.refundItem(player, itemIdentifier, (int) amount, null, null);
        }

        if (priceType == ShopPriceFormatter.PriceType.STRUCTURED_REQUIREMENTS) {
            // Cannot safely guess multi-step rollback if external providers are uncertain.
            // Return false to preserve recoverable journal record.
            return false;
        }

        return false;
    }

    @Override
    public String toString() {
        return "ShopTransactionReceipt{" +
                "status=" + status +
                ", priceType=" + priceType +
                ", amount=" + amount +
                ", itemIdentifier='" + itemIdentifier + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}

