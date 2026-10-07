package org.ipsecuz.pet;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConfigManager {
    public static final int CURRENT_SCHEMA_VERSION = 2;

    private final IpsecuzPet plugin;
    private File dataFile;
    private FileConfiguration dataConfig;
    private final Object saveLock = new Object();
    private final AtomicBoolean isDirty = new AtomicBoolean(false);
    private boolean saveScheduled = false;

    public ConfigManager(IpsecuzPet plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        loadDataFile();
        runDataMigration();
    }

    public void loadDataFile() {
        synchronized (saveLock) {
            dataFile = new File(plugin.getDataFolder(), "data.yml");
            if (!dataFile.exists()) {
                try {
                    dataFile.getParentFile().mkdirs();
                    dataFile.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().severe("Không thể tạo file data.yml: " + e.getMessage());
                }
            }
            dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        }
    }

    private void runDataMigration() {
        synchronized (saveLock) {
            int version = dataConfig.getInt("metadata.schema_version", 1);
            if (version < CURRENT_SCHEMA_VERSION) {
                plugin.getLogger().info("§e[IpsecuzPet] Đang tự động nâng cấp dữ liệu data.yml từ phiên bản v" + version + " lên v" + CURRENT_SCHEMA_VERSION + "...");
                dataConfig.set("metadata.schema_version", CURRENT_SCHEMA_VERSION);
                forceSave();
                plugin.getLogger().info("§a[IpsecuzPet] Nâng cấp dữ liệu hoàn tất thành công!");
            }
        }
    }

    /**
     * Lưu dữ liệu thông minh với cơ chế Debounce (tránh nghẽn I/O khi gọi liên tục)
     */
    public void saveData() {
        isDirty.set(true);
        synchronized (saveLock) {
            if (saveScheduled) return;
            saveScheduled = true;
        }

        // Lên lịch lưu sau 60 ticks (3 giây)
        SchedulerUtils.runAsync(plugin, () -> {
            try {
                Thread.sleep(3000L);
            } catch (InterruptedException ignored) {}

            synchronized (saveLock) {
                saveScheduled = false;
                if (isDirty.getAndSet(false)) {
                    performDiskWrite();
                }
            }
        });
    }

    /**
     * Buộc lưu ngay lập tức xuống đĩa cứng (dùng cho các giao dịch quan trọng và khi tắt server)
     */
    public void forceSave() {
        synchronized (saveLock) {
            isDirty.set(false);
            performDiskWrite();
        }
    }

    private void performDiskWrite() {
        if (dataConfig == null || dataFile == null) return;
        try {
            // Tạo bản sao lưu an toàn trước khi ghi
            if (dataFile.exists() && dataFile.length() > 0) {
                File backupFile = new File(dataFile.getParentFile(), "data.yml.bak");
                Files.copy(dataFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Lỗi nghiêm trọng khi ghi file data.yml: " + e.getMessage());
        }
    }

    public FileConfiguration getData() { return dataConfig; }

    public void createPetDataIfMissing(UUID uuid, String petId) {
        if (uuid == null || petId == null) return;
        synchronized (saveLock) {
            String path = uuid + ".pets." + petId;
            if (!dataConfig.contains(path)) {
                dataConfig.set(path + ".level", 1);
                dataConfig.set(path + ".exp", 0);
                dataConfig.set(path + ".stars", 1);
                dataConfig.set(path + ".status", "ALIVE");
                dataConfig.set(path + ".happiness", 100);
                dataConfig.set(path + ".is_baby", false);
                saveData();
            }
        }
    }

    public void deletePetData(UUID uuid, String petId) {
        if (uuid == null || petId == null) return;
        synchronized (saveLock) {
            if (dataConfig.contains(uuid + ".pets." + petId)) {
                dataConfig.set(uuid + ".pets." + petId, null);
                forceSave(); // Thao tác xóa pet cần lưu ngay lập tức
            }
        }
    }

    public boolean isPetDead(UUID uuid, String petId) {
        if (uuid == null || petId == null) return false;
        return "DEAD".equalsIgnoreCase(dataConfig.getString(uuid + ".pets." + petId + ".status", "ALIVE"));
    }

    public void setPetStatus(UUID uuid, String petId, String status) {
        if (uuid == null || petId == null) return;
        dataConfig.set(uuid + ".pets." + petId + ".status", status);
        saveData();
    }

    public String getCustomName(UUID uuid, String petId) {
        if (uuid == null || petId == null) return null;
        return dataConfig.getString(uuid + ".pets." + petId + ".customName", null);
    }

    public void setCustomName(UUID uuid, String petId, String customName) {
        if (uuid == null || petId == null) return;
        dataConfig.set(uuid + ".pets." + petId + ".customName", customName);
        saveData();
    }

    public boolean isPetBaby(UUID uuid, String petId) {
        if (uuid == null || petId == null) return false;
        return dataConfig.getBoolean(uuid + ".pets." + petId + ".is_baby", false);
    }

    public void setPetBaby(UUID uuid, String petId, boolean isBaby) {
        if (uuid == null || petId == null) return;
        dataConfig.set(uuid + ".pets." + petId + ".is_baby", isBaby);
        saveData();
    }

    public double getPetStat(String petId, int level, String statName) {
        if (petId == null || statName == null) return 0.0;
        double base = plugin.getConfig().getDouble("pets." + petId + ".stats." + statName, 0.0);
        double growth = plugin.getConfig().getDouble("rpg_system.default_growth." + statName, 0.0);
        return base + (Math.max(1, level) * growth);
    }

    public int getReviveProgress(UUID uuid) {
        if (uuid == null) return 0;
        return dataConfig.getInt(uuid + ".revive_progress", 0);
    }

    public void addReviveProgress(UUID uuid, int amount) {
        if (uuid == null || amount <= 0) return;
        int current = getReviveProgress(uuid);
        dataConfig.set(uuid + ".revive_progress", current + amount);
        saveData();
    }

    public void resetReviveProgress(UUID uuid) {
        if (uuid == null) return;
        dataConfig.set(uuid + ".revive_progress", 0);
        saveData();
    }
}