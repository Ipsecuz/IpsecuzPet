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

    public boolean processTransaction(Player p, String petId) {
        String type = plugin.getConfig().getString("pets." + petId + ".currency", "ITEM");
        double cost = plugin.getConfig().getDouble("pets." + petId + ".price", 0);
        LanguageManager lang = plugin.getLanguage();

        switch (type.toUpperCase()) {
            case "MONEY":
                if (econ != null && econ.getBalance(p) >= cost) {
                    econ.withdrawPlayer(p, cost);
                    return true;
                }
                p.sendMessage(lang.getMessage("pet.buy_fail_money", "%cost%", String.valueOf(cost)));
                return false;
            case "POINTS":
                if (pointsAPI != null && pointsAPI.look(p.getUniqueId()) >= cost) {
                    pointsAPI.take(p.getUniqueId(), (int) cost);
                    return true;
                }
                p.sendMessage(lang.getMessage("pet.buy_fail_points", "%cost%", String.valueOf((int)cost)));
                return false;
            default:
                String matName = plugin.getConfig().getString("pets." + petId + ".material", "DIAMOND");
                Material mat = Material.getMaterial(matName);
                if (mat == null) mat = Material.DIAMOND;
                if (p.getInventory().contains(mat, (int) cost)) {
                    p.getInventory().removeItem(new ItemStack(mat, (int) cost));
                    return true;
                }
                p.sendMessage(lang.getMessage("pet.buy_fail_items", "%amount%", String.valueOf((int)cost), "%material%", mat.name()));
                return false;
        }
    }

    public String getPriceDisplay(String petId) {
        String type = plugin.getConfig().getString("pets." + petId + ".currency", "ITEM");
        double cost = plugin.getConfig().getDouble("pets." + petId + ".price", 0);
        if (type.equals("MONEY")) return "$" + cost;
        if (type.equals("POINTS")) return (int)cost + " Points";
        return (int)cost + " " + plugin.getConfig().getString("pets." + petId + ".material");
    }
}