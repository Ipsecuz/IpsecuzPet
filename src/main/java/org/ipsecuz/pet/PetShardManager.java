package org.ipsecuz.pet;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PetShardManager {
    private final IpsecuzPet plugin;

    public PetShardManager(IpsecuzPet plugin) {
        this.plugin = plugin;
    }

    public int getShards(UUID uuid, String petId) {
        if (uuid == null || petId == null) return 0;
        return plugin.getConfigManager().getData().getInt(uuid + ".shards." + petId, 0);
    }

    public void addShards(UUID uuid, String petId, int amount) {
        if (uuid == null || petId == null || amount <= 0) return;
        int current = getShards(uuid, petId);
        plugin.getConfigManager().getData().set(uuid + ".shards." + petId, current + amount);
        plugin.getConfigManager().saveData();
    }

    public boolean takeShards(UUID uuid, String petId, int amount) {
        if (uuid == null || petId == null || amount <= 0) return false;
        int current = getShards(uuid, petId);
        if (current < amount) return false;
        plugin.getConfigManager().getData().set(uuid + ".shards." + petId, current - amount);
        plugin.getConfigManager().saveData();
        return true;
    }

    public Map<String, Integer> getAllShards(UUID uuid) {
        Map<String, Integer> result = new HashMap<>();
        if (uuid == null) return result;
        var sec = plugin.getConfigManager().getData().getConfigurationSection(uuid + ".shards");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                result.put(key, sec.getInt(key, 0));
            }
        }
        return result;
    }

    public int getRequiredShardsToCraft(String petId) {
        return plugin.getConfig().getInt("shards.required_shards", 50);
    }

    public void convertDuplicateToShards(Player player, String petId) {
        processDuplicateReward(player, petId);
    }

    public void processDuplicateReward(UUID uuid, String petId) {
        if (uuid == null || petId == null) return;
        Player online = Bukkit.getPlayer(uuid);
        if (online != null && online.isOnline()) {
            processDuplicateReward(online, petId);
            return;
        }
        PetRarity rarity = PetRarity.getPetRarity(plugin, petId);
        int shardYield = rarity.getShardValue();
        int bonusExp = shardYield * 25;
        addShards(uuid, petId, shardYield);
        int curExp = plugin.getConfigManager().getData().getInt(uuid + ".pets." + petId + ".exp", 0);
        plugin.getConfigManager().getData().set(uuid + ".pets." + petId + ".exp", curExp + bonusExp);
        plugin.getConfigManager().saveData();
    }

    public void processDuplicateReward(Player player, String petId) {
        if (player == null || petId == null) return;
        PetRarity rarity = PetRarity.getPetRarity(plugin, petId);
        int shardYield = rarity.getShardValue();
        int bonusExp = shardYield * 25;

        addShards(player.getUniqueId(), petId, shardYield);

        String petName = plugin.getConfig().getString("pets." + petId + ".name", petId);
        player.sendMessage(plugin.getLanguage().getMessage("shards.duplicate_reward",
                "%pet_name%", petName,
                "%amount%", String.valueOf(shardYield),
                "%rarity%", rarity.getLocalizedName(plugin),
                "%exp%", String.valueOf(bonusExp)));

        plugin.getPetManager().giveSpecificPetExp(player, petId, bonusExp);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
    }
}

