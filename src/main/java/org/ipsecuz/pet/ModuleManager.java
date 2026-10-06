package org.ipsecuz.pet;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class ModuleManager {
    private final IpsecuzPet plugin;
    private final File modulesFolder;
    private final Map<String, FileConfiguration> configs = new HashMap<>();

    public ModuleManager(IpsecuzPet plugin) {
        this.plugin = plugin;
        this.modulesFolder = new File(plugin.getDataFolder(), "modules");
        if (!modulesFolder.exists()) {
            modulesFolder.mkdirs();
        }
        loadAllModules();
    }

    public void loadAllModules() {
        configs.clear();
        loadModule("hatching");
        loadModule("skills");
        loadModule("feeding");
        loadModule("evolution");
        loadModule("trade");
    }

    private void loadModule(String name) {
        File file = new File(modulesFolder, name + ".yml");
        if (!file.exists()) {
            String resourcePath = "modules/" + name + ".yml";
            try (InputStream in = plugin.getResource(resourcePath)) {
                if (in != null) {
                    Files.copy(in, file.toPath());
                } else {
                    file.createNewFile();
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Không thể tạo module file: " + name + ".yml");
            }
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        configs.put(name, config);
    }

    public FileConfiguration getModuleConfig(String name) {
        return configs.getOrDefault(name, new YamlConfiguration());
    }

    public boolean isModuleEnabled(String name) {
        FileConfiguration cfg = configs.get(name);
        return cfg != null && cfg.getBoolean("enabled", true);
    }

    public FileConfiguration getHatchingConfig() { return getModuleConfig("hatching"); }
    public FileConfiguration getSkillsConfig() { return getModuleConfig("skills"); }
    public FileConfiguration getFeedingConfig() { return getModuleConfig("feeding"); }
    public FileConfiguration getEvolutionConfig() { return getModuleConfig("evolution"); }
    public FileConfiguration getTradeConfig() { return getModuleConfig("trade"); }
}

