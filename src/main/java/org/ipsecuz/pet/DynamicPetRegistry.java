package org.ipsecuz.pet;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

import java.util.*;

/**
 * Tự động phát hiện phiên bản server (1.20 -> 1.21.x -> 26.x)
 * và tự động bổ sung các Mob mới xuất hiện ở phiên bản đó vào danh sách Pet.
 */
public class DynamicPetRegistry {
    private final IpsecuzPet plugin;
    private final String serverVersion;

    public DynamicPetRegistry(IpsecuzPet plugin) {
        this.plugin = plugin;
        this.serverVersion = Bukkit.getBukkitVersion();
    }

    public void detectAndRegisterNewMobs() {
        FileConfiguration config = plugin.getConfig();
        Set<String> registeredTypes = new HashSet<>();

        if (config.isConfigurationSection("pets")) {
            for (String key : config.getConfigurationSection("pets").getKeys(false)) {
                String typeStr = config.getString("pets." + key + ".type");
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
                // Tạo ID cho pet mới
                String petId = typeName.toLowerCase() + "_pet";
                if (!config.contains("pets." + petId)) {
                    registerDefaultPet(config, petId, type);
                    addedCount++;
                }
            }
        }

        if (addedCount > 0) {
            plugin.saveConfig();
            plugin.getLogger().info("§a[DynamicPetRegistry] Đã tự động phát hiện và đăng ký thêm " + addedCount + " loài Pet mới tương thích phiên bản server (" + serverVersion + ")!");
        }
    }

    private void registerDefaultPet(FileConfiguration config, String petId, EntityType type) {
        String path = "pets." + petId;
        String formattedName = formatName(type.name());

        config.set(path + ".type", type.name());
        config.set(path + ".name", "&e" + formattedName + " Pet");
        config.set(path + ".icon", guessIcon(type).name());
        config.set(path + ".price", 50);
        config.set(path + ".currency", "MONEY");
        config.set(path + ".catchable", true);
        config.set(path + ".particle", "VILLAGER_HAPPY");
        config.set(path + ".effects", Collections.singletonList("SPEED:0"));
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

