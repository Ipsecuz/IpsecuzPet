package org.ipsecuz.pet;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class FeedingManager {
    private final IpsecuzPet plugin;

    public FeedingManager(IpsecuzPet plugin) {
        this.plugin = plugin;
    }

    public int getHappiness(UUID uuid, String petId) {
        return plugin.getConfigManager().getData().getInt(uuid + ".pets." + petId + ".happiness", 100);
    }

    public void setHappiness(UUID uuid, String petId, int val) {
        int max = plugin.getModuleManager().getFeedingConfig().getInt("max_happiness", 100);
        val = Math.max(0, Math.min(max, val));
        plugin.getConfigManager().getData().set(uuid + ".pets." + petId + ".happiness", val);
        plugin.getConfigManager().saveData();
    }

    public boolean feedPet(Player player, ItemStack foodItem) {
        if (!plugin.getPetManager().hasPet(player.getUniqueId())) {
            player.sendMessage(plugin.getLanguage().getMessage("pet.no_pet"));
            return false;
        }

        if (foodItem == null || foodItem.getType() == Material.AIR) {
            player.sendMessage("§cVui lòng cầm thức ăn trên tay!");
            return false;
        }

        FileConfiguration config = plugin.getModuleManager().getFeedingConfig();
        String matName = foodItem.getType().name();
        ConfigurationSection foodSec = config.getConfigurationSection("foods." + matName);

        if (foodSec == null) {
            player.sendMessage("§cThú cưng không thể ăn vật phẩm này!");
            return false;
        }

        String petId = plugin.getPetManager().getActivePetId(player.getUniqueId());
        Entity pet = plugin.getPetManager().getPet(player.getUniqueId());

        int curHappy = getHappiness(player.getUniqueId(), petId);
        int maxHappy = config.getInt("max_happiness", 100);
        if (curHappy >= maxHappy) {
            player.sendMessage("§aThú cưng đã no và vô cùng hạnh phúc (100%)!");
            return false;
        }

        int addHappy = foodSec.getInt("happiness_add", 15);
        int addExp = foodSec.getInt("exp_add", 20);
        String soundName = foodSec.getString("sound", "ENTITY_GENERIC_EAT");

        foodItem.setAmount(foodItem.getAmount() - 1);
        setHappiness(player.getUniqueId(), petId, curHappy + addHappy);
        plugin.getPetManager().givePetExp(player, addExp);

        int newHappy = getHappiness(player.getUniqueId(), petId);
        player.sendMessage("§aĐã cho thú cưng ăn §e" + matName + "§a! Độ vui vẻ: §e" + newHappy + "/" + maxHappy + " §a(+§b" + addExp + " EXP§a)");

        if (pet != null && pet.isValid()) {
            SchedulerUtils.runEntityTask(plugin, pet, () -> {
                try {
                    pet.getWorld().playSound(pet.getLocation(), Sound.valueOf(soundName), 1f, 1f);
                } catch (Exception ignored) {}
                pet.getWorld().spawnParticle(Particle.HEART, pet.getLocation().add(0, pet.getHeight() + 0.3, 0), 5, 0.3, 0.3, 0.3);
            });
        }
        return true;
    }
}

