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
import java.util.concurrent.locks.ReentrantLock;

public class ConfigManager {
    public static final int CURRENT_SCHEMA_VERSION = 2;

    private final IpsecuzPet plugin;
    private File dataFile;
    private FileConfiguration dataConfig;
    private final Object saveLock = new Object();
    private final ReentrantLock diskLock = new ReentrantLock();
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
     * Lưu dữ liệu thông minh với cơ chế Debounce và Snapshot an toàn luồng (Folia-safe thread snapshot)
     */
    public void saveData() {
        isDirty.set(true);
        synchronized (saveLock) {
            if (saveScheduled) return;
            saveScheduled = true;
        }

        // Lên lịch lưu sau 2.5 giây không gây block worker thread
        SchedulerUtils.runAsyncLater(plugin, () -> {
            String snapshotContent = null;
            synchronized (saveLock) {
                saveScheduled = false;
                if (isDirty.getAndSet(false) && dataConfig != null) {
                    snapshotContent = dataConfig.saveToString();
                }
            }
            if (snapshotContent != null) {
                performDiskWrite(snapshotContent);
            }
        }, 50L);
    }

    /**
     * Buộc lưu ngay lập tức xuống đĩa cứng (dùng cho các giao dịch quan trọng và khi tắt server)
     */
    public void forceSave() {
        String snapshotContent = null;
        synchronized (saveLock) {
            isDirty.set(false);
            if (dataConfig != null) {
                snapshotContent = dataConfig.saveToString();
            }
        }
        if (snapshotContent != null) {
            performDiskWrite(snapshotContent);
        }
    }

    private void performDiskWrite(String content) {
        if (content == null || dataFile == null) return;
        diskLock.lock();
        try {
            // Tạo bản sao lưu an toàn trước khi ghi
            if (dataFile.exists() && dataFile.length() > 0) {
                File backupFile = new File(dataFile.getParentFile(), "data.yml.bak");
                Files.copy(dataFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(dataFile.toPath(), content, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().severe("Lỗi nghiêm trọng khi ghi file data.yml: " + e.getMessage());
        } finally {
            diskLock.unlock();
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
        synchronized (saveLock) {
            return "DEAD".equalsIgnoreCase(dataConfig.getString(uuid + ".pets." + petId + ".status", "ALIVE"));
        }
    }

    public void setPetStatus(UUID uuid, String petId, String status) {
        if (uuid == null || petId == null) return;
        synchronized (saveLock) {
            dataConfig.set(uuid + ".pets." + petId + ".status", status);
            if ("DEAD".equalsIgnoreCase(status)) {
                forceSave(); // Trạng thái chết phải lưu đĩa ngay lập tức
            } else {
                saveData();
            }
        }
    }

    public void savePetProfile(UUID uuid, String petId, int level, int exp, int stars, String trait, String customName, java.util.List<String> unlockedSkills, boolean immediate) {
        if (uuid == null || petId == null) return;
        synchronized (saveLock) {
            String path = uuid + ".pets." + petId;
            createPetDataIfMissing(uuid, petId);
            dataConfig.set(path + ".level", level);
            dataConfig.set(path + ".exp", exp);
            dataConfig.set(path + ".stars", stars);
            dataConfig.set(path + ".trait", trait);
            if (customName != null && !customName.isEmpty()) {
                dataConfig.set(path + ".custom_name", customName);
            }
            if (unlockedSkills != null && !unlockedSkills.isEmpty()) {
                dataConfig.set(path + ".unlocked_skills", unlockedSkills);
            }
            if (immediate) {
                forceSave();
            } else {
                saveData();
            }
        }
    }

    public void setPetLevelAndExp(UUID uuid, String petId, int level, int exp) {
        if (uuid == null || petId == null) return;
        synchronized (saveLock) {
            String path = uuid + ".pets." + petId;
            dataConfig.set(path + ".level", level);
            dataConfig.set(path + ".exp", exp);
            saveData();
        }
    }

    public void setPetStars(UUID uuid, String petId, int stars) {
        if (uuid == null || petId == null) return;
        synchronized (saveLock) {
            dataConfig.set(uuid + ".pets." + petId + ".stars", stars);
            saveData();
        }
    }

    public void setPetTrait(UUID uuid, String petId, String trait) {
        if (uuid == null || petId == null) return;
        synchronized (saveLock) {
            dataConfig.set(uuid + ".pets." + petId + ".trait", trait);
            saveData();
        }
    }

    public void setPetUnlockedSkills(UUID uuid, String petId, java.util.List<String> skills) {
        if (uuid == null || petId == null) return;
        synchronized (saveLock) {
            dataConfig.set(uuid + ".pets." + petId + ".unlocked_skills", skills);
            saveData();
        }
    }

    public void setShards(UUID uuid, String petId, int amount) {
        if (uuid == null || petId == null) return;
        synchronized (saveLock) {
            dataConfig.set(uuid + ".shards." + petId, amount);
            saveData();
        }
    }

    public void setCodexDiscovered(UUID uuid, String petId) {
        if (uuid == null || petId == null) return;
        synchronized (saveLock) {
            dataConfig.set(uuid + ".codex." + petId, true);
            saveData();
        }
    }

    public String getCustomName(UUID uuid, String petId) {
        if (uuid == null || petId == null) return null;
        synchronized (saveLock) {
            String path = uuid + ".pets." + petId;
            if (dataConfig.contains(path + ".custom_name")) {
                return dataConfig.getString(path + ".custom_name");
            }
            // Hỗ trợ migration từ key cũ customName
            return dataConfig.getString(path + ".customName", null);
        }
    }

    public void setCustomName(UUID uuid, String petId, String customName) {
        if (uuid == null || petId == null) return;
        synchronized (saveLock) {
            String path = uuid + ".pets." + petId;
            dataConfig.set(path + ".custom_name", customName);
            dataConfig.set(path + ".customName", null);
            saveData();
        }
    }

    public boolean isPetBaby(UUID uuid, String petId) {
        if (uuid == null || petId == null) return false;
        synchronized (saveLock) {
            return dataConfig.getBoolean(uuid + ".pets." + petId + ".is_baby", false);
        }
    }

    public void setPetBaby(UUID uuid, String petId, boolean isBaby) {
        if (uuid == null || petId == null) return;
        synchronized (saveLock) {
            dataConfig.set(uuid + ".pets." + petId + ".is_baby", isBaby);
            saveData();
        }
    }

    public double getPetStat(String petId, int level, String statName) {
        return PetStatEngine.getBaseStatAtLevel(plugin, petId, level, statName);
    }

    public double getEffectivePetStat(UUID uuid, String petId, int level, String statName) {
        return PetStatEngine.calculateEffectiveStat(plugin, uuid, petId, level, statName);
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