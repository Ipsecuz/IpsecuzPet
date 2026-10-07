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

    public boolean depositMoney(Player p, double amount) {
        if (econ != null && amount > 0) {
            econ.depositPlayer(p, amount);
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

    public boolean givePoints(Player p, int amount) {
        if (pointsAPI != null && amount > 0) {
            pointsAPI.give(p.getUniqueId(), amount);
            return true;
        }
        return false;
    }

    public void setEconomy(Economy econ) {
        this.econ = econ;
    }

    public void setPointsAPI(PlayerPointsAPI pointsAPI) {
        this.pointsAPI = pointsAPI;
    }

    public boolean processTransaction(Player p, String petId) {
        org.bukkit.configuration.ConfigurationSection sec = plugin.getConfig().getConfigurationSection("pets." + petId);
        if (sec != null && (sec.contains("requirements") || sec.contains("all") || sec.contains("one_of"))) {
            org.ipsecuz.pet.requirement.RequirementGroup group = plugin.getRequirementManager().parse(sec);
            org.ipsecuz.pet.requirement.RequirementContext ctx = new org.ipsecuz.pet.requirement.RequirementContext(p, petId, 1, 1, plugin);
            return plugin.getRequirementManager().executeTransaction(group, ctx);
        }

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
        org.bukkit.configuration.ConfigurationSection sec = plugin.getConfig().getConfigurationSection("pets." + petId);
        if (sec != null && (sec.contains("requirements") || sec.contains("all") || sec.contains("one_of"))) {
            org.ipsecuz.pet.requirement.RequirementGroup group = plugin.getRequirementManager().parse(sec);
            org.ipsecuz.pet.requirement.RequirementContext ctx = new org.ipsecuz.pet.requirement.RequirementContext(null, petId, 1, 1, plugin);
            org.ipsecuz.pet.requirement.RequirementCheckResult res = plugin.getRequirementManager().evaluate(group, ctx);
            java.util.List<String> rendered = org.ipsecuz.pet.requirement.RequirementGuiRenderer.renderToStrings(res);
            for (String line : rendered) {
                if (!line.contains("ĐỦ ĐIỀU KIỆN") && !line.contains("CHƯA ĐỦ ĐIỀU KIỆN") && !line.startsWith("§7----") && !line.contains("BẮT BUỘC") && !line.contains("CHỌN 1")) {
                    return line.replace("§a[✔] ", "").replace("§c[✖] ", "").trim();
                }
            }
        }
        String type = plugin.getConfig().getString("pets." + petId + ".currency", "ITEM");
        double cost = plugin.getConfig().getDouble("pets." + petId + ".price", 0);
        if (type.equals("MONEY")) return "$" + (long)cost;
        if (type.equals("POINTS")) return (int)cost + " Points";
        return (int)cost + " " + plugin.getConfig().getString("pets." + petId + ".material", "DIAMOND");
    }
}