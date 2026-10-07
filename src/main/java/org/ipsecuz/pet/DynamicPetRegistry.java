package org.ipsecuz.pet;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

import java.io.File;
import java.util.*;

/**
 * Tự động phát hiện phiên bản server (1.20 -> 1.21.x -> các phiên bản mới)
 * và tự động bổ sung các Mob mới xuất hiện ở phiên bản đó vào file riêng biệt discovered_entities.yml.
 * Tuyệt đối không thay đổi hay làm hỏng cấu trúc chú thích của config.yml.
 */
public class DynamicPetRegistry {
    private final IpsecuzPet plugin;
    private final String serverVersion;
    private final File discoveredFile;
    private YamlConfiguration discoveredConfig;

    public DynamicPetRegistry(IpsecuzPet plugin) {
        this.plugin = plugin;
        this.serverVersion = Bukkit.getBukkitVersion();
        this.discoveredFile = new File(plugin.getDataFolder(), "discovered_entities.yml");
        loadDiscoveredFile();
    }

    public void loadDiscoveredFile() {
        if (!discoveredFile.exists()) {
            try {
                if (discoveredFile.getParentFile() != null) {
                    discoveredFile.getParentFile().mkdirs();
                }
                discoveredFile.createNewFile();
            } catch (Exception ignored) {}
        }
        this.discoveredConfig = YamlConfiguration.loadConfiguration(discoveredFile);
    }

    public FileConfiguration getDiscoveredConfig() {
        return discoveredConfig;
    }

    public void detectAndRegisterNewMobs() {
        loadDiscoveredFile();
        FileConfiguration mainConfig = plugin.getConfig();
        Set<String> registeredTypes = new HashSet<>();

        if (mainConfig.isConfigurationSection("pets")) {
            for (String key : mainConfig.getConfigurationSection("pets").getKeys(false)) {
                String typeStr = mainConfig.getString("pets." + key + ".type");
                if (typeStr != null) {
                    registeredTypes.add(typeStr.toUpperCase());
                }
            }
        }
        if (discoveredConfig.isConfigurationSection("discovered_entities")) {
            for (String key : discoveredConfig.getConfigurationSection("discovered_entities").getKeys(false)) {
                String typeStr = discoveredConfig.getString("discovered_entities." + key + ".type");
                if (typeStr != null) {
                    registeredTypes.add(typeStr.toUpperCase());
                }
            }
        }

        int addedCount = 0;
        for (EntityType type : EntityType.values()) {
            if (!type.isAlive() || type.getEntityClass() == null) continue;
            if (!LivingEntity.class.isAssignableFrom(type.getEntityClass())) continue;

            String typeName = type.name();
            // Bỏ qua các entity kỹ thuật / boss phức tạp không phù hợp làm pet mặc định
            if (typeName.equals("PLAYER") || typeName.equals("ARMOR_STAND") || typeName.equals("GIANT")) continue;

            if (!registeredTypes.contains(typeName)) {
                String entityId = typeName.toLowerCase() + "_pet";
                if (!discoveredConfig.contains("discovered_entities." + entityId) && !mainConfig.contains("pets." + entityId)) {
                    registerDiscoveredEntity(discoveredConfig, entityId, type);
                    addedCount++;
                }
            }
        }

        if (addedCount > 0) {
            try {
                discoveredConfig.save(discoveredFile);
                plugin.getLogger().info("§a[DynamicPetRegistry] Đã tự động phát hiện và ghi nhận " + addedCount + " loài thực thể mới vào discovered_entities.yml (" + serverVersion + ")!");
            } catch (Exception e) {
                plugin.getLogger().severe("§c[DynamicPetRegistry] Không thể lưu discovered_entities.yml: " + e.getMessage());
            }
        }
    }

    private void registerDiscoveredEntity(FileConfiguration config, String entityId, EntityType type) {
        String path = "discovered_entities." + entityId;
        String formattedName = formatName(type.name());

        config.set(path + ".enabled", false);
        config.set(path + ".visible_in_shop", false);
        config.set(path + ".catchable", false);
        config.set(path + ".type", type.name());
        config.set(path + ".name", "&e" + formattedName + " Pet");
        config.set(path + ".icon", guessIcon(type).name());
        config.set(path + ".price", 50);
        config.set(path + ".currency", "MONEY");
        config.set(path + ".rarity", "COMMON");
        config.set(path + ".particle", "VILLAGER_HAPPY");
        config.set(path + ".effects", Collections.emptyList());
        config.set(path + ".skills", Collections.emptyMap());
        config.set(path + ".stats.damage", 5.0);
        config.set(path + ".stats.health", 25.0);
        config.set(path + ".stats.defense", 2.0);
        config.set(path + ".stats.speed", 0.28);
        config.set(path + ".stats.intelligence", 5.0);
    }

    private String formatName(String name) {
        String[] words = name.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private Material guessIcon(EntityType type) {
        try {
            Material eggMat = Material.getMaterial(type.name() + "_SPAWN_EGG");
            if (eggMat != null) return eggMat;
        } catch (Exception ignored) {}
        return Material.NAME_TAG;
    }
}
