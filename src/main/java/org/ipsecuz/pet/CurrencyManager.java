package org.ipsecuz.pet;

import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;

public class CurrencyManager {
    private final IpsecuzPet plugin;
    private Economy econ = null;
    private PlayerPointsAPI pointsAPI = null;

    public CurrencyManager(IpsecuzPet plugin) {
        this.plugin = plugin;
        setupEconomy();
        setupPoints();
    }

    public CurrencyManager() {
        this.plugin = null;
    }

    public CurrencyManager(Economy econ, PlayerPointsAPI pointsAPI) {
        this.plugin = null;
        this.econ = econ;
        this.pointsAPI = pointsAPI;
    }

    private void setupEconomy() {
        try {
            if (plugin == null || plugin.getServer() == null || plugin.getServer().getPluginManager() == null) return;
            if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) return;
            RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) econ = rsp.getProvider();
        } catch (Throwable ignored) {}
    }
    private void setupPoints() {
        try {
            if (plugin == null || plugin.getServer() == null || plugin.getServer().getPluginManager() == null) return;
            if (plugin.getServer().getPluginManager().isPluginEnabled("PlayerPoints")) {
                this.pointsAPI = PlayerPoints.getInstance().getAPI();
            }
        } catch (Throwable ignored) {}
    }

    public boolean hasEconomy() { return econ != null; }
    public Economy getEconomy() { return econ; }
    public PlayerPointsAPI getPointsAPI() { return pointsAPI; }

    public boolean hasMoney(Player p, double amount) {
        return econ != null && p != null && amount >= 0 && econ.getBalance(p) >= amount;
    }

    public boolean withdrawMoney(Player p, double amount) {
        if (econ == null || p == null || amount <= 0) return false;
        if (econ.getBalance(p) < amount) return false;
        net.milkbowl.vault.economy.EconomyResponse resp = econ.withdrawPlayer(p, amount);
        return resp != null && resp.transactionSuccess();
    }

    public boolean depositMoney(Player p, double amount) {
        if (econ == null || p == null || amount <= 0) return false;
        net.milkbowl.vault.economy.EconomyResponse resp = econ.depositPlayer(p, amount);
        return resp != null && resp.transactionSuccess();
    }

    public boolean hasPoints(Player p, int amount) {
        return pointsAPI != null && p != null && amount >= 0 && pointsAPI.look(p.getUniqueId()) >= amount;
    }

    public boolean withdrawPoints(Player p, int amount) {
        if (pointsAPI == null || p == null || amount <= 0) return false;
        if (pointsAPI.look(p.getUniqueId()) < amount) return false;
        return pointsAPI.take(p.getUniqueId(), amount);
    }

    public boolean givePoints(Player p, int amount) {
        if (pointsAPI == null || p == null || amount <= 0) return false;
        return pointsAPI.give(p.getUniqueId(), amount);
    }

    public void setEconomy(Economy econ) {
        this.econ = econ;
    }

    public void setPointsAPI(PlayerPointsAPI pointsAPI) {
        this.pointsAPI = pointsAPI;
    }

    /**
     * Executes authoritative shop transaction returning structured receipt.
     */
    public ShopTransactionReceipt executeShopTransaction(Player p, String petId) {
        if (p == null || petId == null) {
            return ShopTransactionReceipt.failure(ShopTransactionReceipt.Status.INVALID_CONFIG, "Player or petId is null");
        }

        if (plugin.getShopPriceFormatter() == null) {
            return ShopTransactionReceipt.failure(ShopTransactionReceipt.Status.PROVIDER_UNAVAILABLE, "ShopPriceFormatter not initialized");
        }

        ShopPriceFormatter.PriceResult priceInfo = plugin.getShopPriceFormatter().format(p, petId);
        LanguageManager lang = plugin.getLanguage();

        if (!priceInfo.isAvailable() || priceInfo.getType() == ShopPriceFormatter.PriceType.INVALID) {
            String reason = priceInfo.getUnavailableReason() != null ? priceInfo.getUnavailableReason() : "Configuration invalid or provider unavailable";
            if (lang != null) {
                p.sendMessage(lang.getMessageOrDefault("shop.provider_unavailable", "&cKhông thể mua: %reason%", "%reason%", reason));
            }
            return ShopTransactionReceipt.failure(ShopTransactionReceipt.Status.PROVIDER_UNAVAILABLE, reason);
        }

        if (priceInfo.getType() == ShopPriceFormatter.PriceType.FREE) {
            return ShopTransactionReceipt.successFree();
        }

        if (priceInfo.getType() == ShopPriceFormatter.PriceType.STRUCTURED_REQUIREMENTS) {
            org.bukkit.configuration.ConfigurationSection sec = plugin.getConfig().getConfigurationSection("pets." + petId);
            org.ipsecuz.pet.requirement.RequirementGroup group = plugin.getRequirementManager().parse(sec);
            org.ipsecuz.pet.requirement.RequirementContext ctx = new org.ipsecuz.pet.requirement.RequirementContext(p, petId, 1, 1, plugin);

            org.ipsecuz.pet.requirement.RequirementCheckResult checkRes = group.evaluate(ctx);
            if (!checkRes.isSatisfied()) {
                plugin.getRequirementManager().executeTransaction(group, ctx);
                return ShopTransactionReceipt.failure(ShopTransactionReceipt.Status.INSUFFICIENT_RESOURCES, "Requirements not met");
            }

            org.ipsecuz.pet.requirement.RequirementTransaction tx = new org.ipsecuz.pet.requirement.RequirementTransaction();
            group.planTransaction(ctx, tx);
            boolean executed = tx.execute(p);
            if (!executed) {
                if (lang != null) {
                    p.sendMessage(lang.getMessageOrDefault("requirement.transaction_failed", "§cThực hiện giao dịch chi phí thất bại! Đã hoàn trả tài nguyên."));
                }
                return ShopTransactionReceipt.failure(ShopTransactionReceipt.Status.DEDUCTION_FAILED, "Requirement deduction failed");
            }
            return ShopTransactionReceipt.successRequirements(tx);
        }

        if (priceInfo.getType() == ShopPriceFormatter.PriceType.MONEY) {
            double cost = priceInfo.getAmount();
            if (!hasMoney(p, cost)) {
                if (lang != null) {
                    p.sendMessage(lang.getMessage("pet.buy_fail_money", "%cost%", priceInfo.getFormattedPrice()));
                }
                return ShopTransactionReceipt.failure(ShopTransactionReceipt.Status.INSUFFICIENT_RESOURCES, "Insufficient money");
            }
            if (!withdrawMoney(p, cost)) {
                if (lang != null) {
                    p.sendMessage(lang.getMessage("pet.buy_fail_money", "%cost%", priceInfo.getFormattedPrice()));
                }
                return ShopTransactionReceipt.failure(ShopTransactionReceipt.Status.DEDUCTION_FAILED, "Withdrawal failed");
            }
            return ShopTransactionReceipt.successMoney(cost);
        }

        if (priceInfo.getType() == ShopPriceFormatter.PriceType.POINTS) {
            int cost = (int) priceInfo.getAmount();
            if (!hasPoints(p, cost)) {
                if (lang != null) {
                    p.sendMessage(lang.getMessage("pet.buy_fail_points", "%cost%", String.valueOf(cost)));
                }
                return ShopTransactionReceipt.failure(ShopTransactionReceipt.Status.INSUFFICIENT_RESOURCES, "Insufficient points");
            }
            if (!withdrawPoints(p, cost)) {
                if (lang != null) {
                    p.sendMessage(lang.getMessage("pet.buy_fail_points", "%cost%", String.valueOf(cost)));
                }
                return ShopTransactionReceipt.failure(ShopTransactionReceipt.Status.DEDUCTION_FAILED, "Points deduction failed");
            }
            return ShopTransactionReceipt.successPoints(cost);
        }

        if (priceInfo.getType() == ShopPriceFormatter.PriceType.ITEM) {
            int cost = (int) priceInfo.getAmount();
            String matName = priceInfo.getRawIdentifier();
            if (plugin.getItemHookManager() == null || !plugin.getItemHookManager().hasItem(p, matName, cost)) {
                if (lang != null) {
                    p.sendMessage(lang.getMessage("pet.buy_fail_items", "%amount%", String.valueOf(cost), "%material%", priceInfo.getItemDisplayName()));
                }
                return ShopTransactionReceipt.failure(ShopTransactionReceipt.Status.INSUFFICIENT_RESOURCES, "Insufficient items");
            }
            if (!plugin.getItemHookManager().takeItem(p, matName, cost)) {
                if (lang != null) {
                    p.sendMessage(lang.getMessage("pet.buy_fail_items", "%amount%", String.valueOf(cost), "%material%", priceInfo.getItemDisplayName()));
                }
                return ShopTransactionReceipt.failure(ShopTransactionReceipt.Status.DEDUCTION_FAILED, "Item deduction failed");
            }
            return ShopTransactionReceipt.successItem(matName, cost);
        }

        return ShopTransactionReceipt.failure(ShopTransactionReceipt.Status.INVALID_CONFIG, "Unknown price type");
    }

    public boolean processTransaction(Player p, String petId) {
        ShopTransactionReceipt receipt = executeShopTransaction(p, petId);
        return receipt.isSuccess();
    }

    public String getPriceDisplay(String petId) {
        if (plugin.getShopPriceFormatter() != null) {
            ShopPriceFormatter.PriceResult res = plugin.getShopPriceFormatter().format(petId);
            return res.getFormattedPrice();
        }
        return "N/A";
    }
}