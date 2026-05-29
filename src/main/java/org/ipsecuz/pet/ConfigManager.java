package org.ipsecuz.pet;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class ConfigManager {
    private final IpsecuzPet plugin;
    private File dataFile;
    private FileConfiguration dataConfig;

    public ConfigManager(IpsecuzPet plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        loadDataFile();
    }

    public void loadDataFile() {
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void saveData() {
        try { dataConfig.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
    }

    public FileConfiguration getData() { return dataConfig; }

    public void createPetDataIfMissing(UUID uuid, String petId) {
        if (!dataConfig.contains(uuid + ".pets." + petId)) {
            String path = uuid + ".pets." + petId;
            dataConfig.set(path + ".level", 1);
            dataConfig.set(path + ".exp", 0);
            dataConfig.set(path + ".status", "ALIVE");
            saveData();
        }
    }

    public void deletePetData(UUID uuid, String petId) {
        if (dataConfig.contains(uuid + ".pets." + petId)) {
            dataConfig.set(uuid + ".pets." + petId, null);
            saveData();
        }
    }

    public boolean isPetDead(UUID uuid, String petId) {
        return "DEAD".equals(dataConfig.getString(uuid + ".pets." + petId + ".status", "ALIVE"));
    }

    public void setPetStatus(UUID uuid, String petId, String status) {
        dataConfig.set(uuid + ".pets." + petId + ".status", status);
        saveData();
    }

    
    public String getCustomName(UUID uuid, String petId) {
        return dataConfig.getString(uuid + ".pets." + petId + ".customName", null);
    }

    public void setCustomName(UUID uuid, String petId, String customName) {
        dataConfig.set(uuid + ".pets." + petId + ".customName", customName);
        saveData();
    }
    

    public double getPetStat(String petId, int level, String statName) {
        double base = plugin.getConfig().getDouble("pets." + petId + ".stats." + statName, 0.0);
        double growth = plugin.getConfig().getDouble("rpg_system.default_growth." + statName, 0.0);
        return base + (level * growth);
    }

    public int getReviveProgress(UUID uuid) {
        return dataConfig.getInt(uuid + ".revive_progress", 0);
    }

    public void addReviveProgress(UUID uuid, int amount) {
        int current = getReviveProgress(uuid);
        dataConfig.set(uuid + ".revive_progress", current + amount);
        saveData();
    }

    public void resetReviveProgress(UUID uuid) {
        dataConfig.set(uuid + ".revive_progress", 0);
        saveData();
    }
}