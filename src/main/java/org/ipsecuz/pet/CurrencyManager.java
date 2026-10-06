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

    private void setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) return;
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) econ = rsp.getProvider();
    }
    private void setupPoints() {
        if (plugin.getServer().getPluginManager().isPluginEnabled("PlayerPoints")) {
            this.pointsAPI = PlayerPoints.getInstance().getAPI();
        }
    }

    public boolean hasEconomy() { return econ != null; }
    public Economy getEconomy() { return econ; }
    public PlayerPointsAPI getPointsAPI() { return pointsAPI; }

    public boolean hasMoney(Player p, double amount) {
        return econ != null && econ.getBalance(p) >= amount;
    }

    public boolean withdrawMoney(Player p, double amount) {
        if (econ != null && econ.getBalance(p) >= amount) {
            econ.withdrawPlayer(p, amount);
            return true;
        }
        return false;
    }

    public boolean hasPoints(Player p, int amount) {
        return pointsAPI != null && pointsAPI.look(p.getUniqueId()) >= amount;
    }

    public boolean withdrawPoints(Player p, int amount) {
        if (pointsAPI != null && pointsAPI.look(p.getUniqueId()) >= amount) {
            pointsAPI.take(p.getUniqueId(), amount);
            return true;
        }
        return false;
    }

    public boolean processTransaction(Player p, String petId) {
        String type = plugin.getConfig().getString("pets." + petId + ".currency", "ITEM");
        double cost = plugin.getConfig().getDouble("pets." + petId + ".price", 0);
        LanguageManager lang = plugin.getLanguage();

        switch (type.toUpperCase()) {
            case "MONEY":
                if (withdrawMoney(p, cost)) {
                    return true;
                }
                p.sendMessage(lang.getMessage("pet.buy_fail_money", "%cost%", String.valueOf(cost)));
                return false;
            case "POINTS":
                if (withdrawPoints(p, (int) cost)) {
                    return true;
                }
                p.sendMessage(lang.getMessage("pet.buy_fail_points", "%cost%", String.valueOf((int)cost)));
                return false;
            default:
                String matName = plugin.getConfig().getString("pets." + petId + ".material", "DIAMOND");
                if (plugin.getItemHookManager() != null && plugin.getItemHookManager().hasItem(p, matName, (int) cost)) {
                    plugin.getItemHookManager().takeItem(p, matName, (int) cost);
                    return true;
                }
                p.sendMessage(lang.getMessage("pet.buy_fail_items", "%amount%", String.valueOf((int)cost), "%material%", matName));
                return false;
        }
    }

    public String getPriceDisplay(String petId) {
        String type = plugin.getConfig().getString("pets." + petId + ".currency", "ITEM");
        double cost = plugin.getConfig().getDouble("pets." + petId + ".price", 0);
        if (type.equals("MONEY")) return "$" + (long)cost;
        if (type.equals("POINTS")) return (int)cost + " Points";
        return (int)cost + " " + plugin.getConfig().getString("pets." + petId + ".material", "DIAMOND");
    }
}