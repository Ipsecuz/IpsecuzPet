package org.ipsecuz.pet;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class EvolutionManager {
    private final IpsecuzPet plugin;

    public EvolutionManager(IpsecuzPet plugin) {
        this.plugin = plugin;
    }

    public int getStar(UUID uuid, String petId) {
        return plugin.getConfigManager().getData().getInt(uuid + ".pets." + petId + ".stars", 1);
    }

    public void setStar(UUID uuid, String petId, int star) {
        plugin.getConfigManager().getData().set(uuid + ".pets." + petId + ".stars", star);
        plugin.getConfigManager().saveData();
    }

    public String getStarDisplay(int star) {
        int max = plugin.getModuleManager().getEvolutionConfig().getInt("max_stars", 5);
        if (star >= max) {
            return "§6§l★★★★★ MAX";
        }
        StringBuilder sb = new StringBuilder("§e");
        for (int i = 0; i < star; i++) {
            sb.append("★");
        }
        sb.append("§7");
        for (int i = star; i < max; i++) {
            sb.append("☆");
        }
        return sb.toString();
    }

    public double getStarMultiplier(UUID uuid, String petId) {
        int stars = getStar(uuid, petId);
        double boostPerStar = plugin.getModuleManager().getEvolutionConfig().getDouble("stat_boost_per_star", 0.15);
        return 1.0 + ((stars - 1) * boostPerStar);
    }

    public boolean upgradeStar(Player player, String petId) {
        if (!plugin.getModuleManager().isEvolutionEnabled()) {
            player.sendMessage("§cTính năng Tiến Hóa & Tăng Sao hiện đang bị tắt bởi máy chủ!");
            return false;
        }

        if (!plugin.getConfigManager().getData().contains(player.getUniqueId() + ".pets." + petId)) {
            player.sendMessage("§cBạn không sở hữu thú cưng này!");
            return false;
        }

        int curStar = getStar(player.getUniqueId(), petId);
        int maxStar = plugin.getModuleManager().getEvolutionConfig().getInt("max_stars", 5);

        if (curStar >= maxStar) {
            player.sendMessage("§cThú cưng này đã đạt cấp sao tối đa (" + getStarDisplay(curStar) + "§c)!");
            return false;
        }

        int nextStar = curStar + 1;
        FileConfiguration config = plugin.getModuleManager().getEvolutionConfig();
        ConfigurationSection reqSec = config.getConfigurationSection("requirements.star_" + nextStar);

        if (reqSec != null) {
            int minLvl = reqSec.getInt("min_level", 20);
            int petLvl = plugin.getConfigManager().getData().getInt(player.getUniqueId() + ".pets." + petId + ".level", 1);
            if (petLvl < minLvl) {
                player.sendMessage("§cThú cưng cần đạt cấp độ tối thiểu §eLv." + minLvl + " §cđể tăng lên " + nextStar + "⭐! (Hiện tại: Lv." + petLvl + ")");
                return false;
            }

            int costMoney = reqSec.getInt("cost_money", 0);
            if (costMoney > 0 && !plugin.getCurrencyManager().hasMoney(player, costMoney)) {
                player.sendMessage("§cBạn không đủ tiền! Cần: §e$" + costMoney);
                return false;
            }

            int costPoints = reqSec.getInt("cost_points", 0);
            if (costPoints > 0 && !plugin.getCurrencyManager().hasPoints(player, costPoints)) {
                player.sendMessage("§cBạn không đủ Points! Cần: §b" + costPoints + " Points");
                return false;
            }

            int costDiamonds = reqSec.getInt("cost_diamonds", 0);
            if (costDiamonds > 0 && !plugin.getItemHookManager().hasItem(player, "DIAMOND", costDiamonds)) {
                player.sendMessage("§cBạn cần có ít nhất §b" + costDiamonds + " Kim Cương §cđể tiến hóa!");
                return false;
            }

            int costNetherite = reqSec.getInt("cost_netherite", 0);
            if (costNetherite > 0 && !plugin.getItemHookManager().hasItem(player, "NETHERITE_INGOT", costNetherite)) {
                player.sendMessage("§cBạn cần có ít nhất §8" + costNetherite + " Phôi Netherite §cđể tiến hóa!");
                return false;
            }

            List<String> costItems = reqSec.getStringList("cost_items");
            for (String itemStr : costItems) {
                String[] parts = itemStr.split(":");
                String id = parts[0];
                int amt = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                if (!plugin.getItemHookManager().hasItem(player, id, amt)) {
                    player.sendMessage("§cBạn thiếu vật phẩm: §e" + amt + "x " + plugin.getItemHookManager().getItemDisplayName(id));
                    return false;
                }
            }

            // Giao dịch nguyên tử (Atomic transaction): Trừ toàn bộ chi phí sau khi đã kiểm tra
            if (costMoney > 0) plugin.getCurrencyManager().withdrawMoney(player, costMoney);
            if (costPoints > 0) plugin.getCurrencyManager().withdrawPoints(player, costPoints);
            if (costDiamonds > 0) plugin.getItemHookManager().takeItem(player, "DIAMOND", costDiamonds);
            if (costNetherite > 0) plugin.getItemHookManager().takeItem(player, "NETHERITE_INGOT", costNetherite);
            for (String itemStr : costItems) {
                String[] parts = itemStr.split(":");
                String id = parts[0];
                int amt = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                plugin.getItemHookManager().takeItem(player, id, amt);
            }
        }

        setStar(player.getUniqueId(), petId, nextStar);
        player.sendMessage("§6§lTIẾN HÓA THÀNH CÔNG! §ePet của bạn đã đạt cấp " + getStarDisplay(nextStar) + "§e!");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        try {
            player.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, player.getLocation().add(0, 1, 0), 25, 0.4, 0.5, 0.4, 0.08);
        } catch (Exception ignored) {}

        // Cập nhật lại chỉ số pet đang kích hoạt nếu đúng là pet này
        if (plugin.getPetManager().hasPet(player.getUniqueId()) &&
                petId.equals(plugin.getPetManager().getActivePetId(player.getUniqueId()))) {
            plugin.getPetManager().refreshPetStats(player);
        }
        return true;
    }
}
