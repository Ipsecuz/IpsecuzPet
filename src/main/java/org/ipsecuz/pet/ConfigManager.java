package org.ipsecuz.pet;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
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

    private final java.util.concurrent.atomic.AtomicLong saveGeneration = new java.util.concurrent.atomic.AtomicLong(0);
    private volatile long lastCommittedGeneration = 0;

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
            long generation = 0;
            synchronized (saveLock) {
                saveScheduled = false;
                if (isDirty.getAndSet(false) && dataConfig != null) {
                    generation = saveGeneration.incrementAndGet();
                    snapshotContent = dataConfig.saveToString();
                }
            }
            if (snapshotContent != null) {
                performDiskWrite(snapshotContent, generation);
            }
        }, 50L);
    }

    /**
     * Buộc lưu ngay lập tức xuống đĩa cứng (dùng cho các giao dịch quan trọng và khi tắt server)
     */
    public void forceSave() {
        String snapshotContent = null;
        long generation = 0;
        synchronized (saveLock) {
            isDirty.set(false);
            if (dataConfig != null) {
                generation = saveGeneration.incrementAndGet();
                snapshotContent = dataConfig.saveToString();
            }
        }
        if (snapshotContent != null) {
            performDiskWrite(snapshotContent, generation);
        }
    }

    /**
     * Đồng bộ ngay lập tức các thay đổi đang chờ lưu (nếu có) trước khi thực hiện reload hoặc thao tác quan trọng.
     */
    public void flushPendingData() {
        if (isDirty.get()) {
            forceSave();
        }
    }

    /**
     * Chỉ nạp lại cấu hình plugin (config.yml, v.v.), TUYỆT ĐỐI không ghi đè dữ liệu người chơi trong bộ nhớ.
     */
    public void reloadPluginConfig() {
        plugin.reloadConfig();
    }

    /**
     * Nạp lại dữ liệu người chơi trực tiếp từ đĩa cứng (chỉ dùng khi khởi động server hoặc yêu cầu rõ ràng từ admin).
     */
    public void loadPlayerDataFromDisk() {
        loadDataFile();
    }

    private void performDiskWrite(String content, long generation) {
        if (content == null || dataFile == null) return;
        diskLock.lock();
        try {
            // Chống đua snapshot (Stale Snapshot Race): Không cho phép snapshot cũ ghi đè lên dữ liệu đã lưu mới hơn!
            if (generation < lastCommittedGeneration) {
                return;
            }
            // Tạo bản sao lưu an toàn trước khi ghi
            if (dataFile.exists() && dataFile.length() > 0) {
                File backupFile = new File(dataFile.getParentFile(), "data.yml.bak");
                Files.copy(dataFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(dataFile.toPath(), content, java.nio.charset.StandardCharsets.UTF_8);
            lastCommittedGeneration = generation;
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

    /**
     * Records an uncompleted refund (e.g. custom item provider offline) into data.yml
     * so administrators and recovery tasks can inspect and grant it safely.
     */
    public void recordPendingRefund(UUID uuid, String petId, ShopTransactionReceipt receipt) {
        if (uuid == null || receipt == null) return;
        synchronized (saveLock) {
            String txId = UUID.randomUUID().toString();
            String path = "pending_shop_refunds." + txId;
            dataConfig.set(path + ".player", uuid.toString());
            dataConfig.set(path + ".petId", petId != null ? petId : "unknown");
            dataConfig.set(path + ".currency", receipt.getPriceType() != null ? receipt.getPriceType().name() : "UNKNOWN");
            dataConfig.set(path + ".amount", receipt.getAmount());
            dataConfig.set(path + ".material", receipt.getItemIdentifier());
            dataConfig.set(path + ".timestamp", System.currentTimeMillis());
            dataConfig.set(path + ".status", "PENDING");
            forceSave();
        }
    }

    public static class ShopTransactionEntry {
        private final String txId;
        private final UUID playerUuid;
        private final String petId;
        private final String currency;
        private final double amount;
        private final String material;
        private final long timestamp;
        private final String status;
        private final String note;

        public ShopTransactionEntry(String txId, UUID playerUuid, String petId, String currency,
                                    double amount, String material, long timestamp, String status, String note) {
            this.txId = txId;
            this.playerUuid = playerUuid;
            this.petId = petId;
            this.currency = currency;
            this.amount = amount;
            this.material = material;
            this.timestamp = timestamp;
            this.status = status;
            this.note = note;
        }

        public String getTxId() { return txId; }
        public UUID getPlayerUuid() { return playerUuid; }
        public String getPetId() { return petId; }
        public String getCurrency() { return currency; }
        public double getAmount() { return amount; }
        public String getMaterial() { return material; }
        public long getTimestamp() { return timestamp; }
        public String getStatus() { return status; }
        public String getNote() { return note; }
    }

    public String createShopTransaction(UUID uuid, String petId, ShopPriceFormatter.PriceResult priceInfo) {
        if (uuid == null || petId == null) return null;
        String txId = UUID.randomUUID().toString();
        synchronized (saveLock) {
            String path = "shop_transactions." + txId;
            dataConfig.set(path + ".player", uuid.toString());
            dataConfig.set(path + ".petId", petId);
            dataConfig.set(path + ".currency", priceInfo != null && priceInfo.getType() != null ? priceInfo.getType().name() : "UNKNOWN");
            dataConfig.set(path + ".amount", priceInfo != null ? priceInfo.getAmount() : 0);
            dataConfig.set(path + ".material", priceInfo != null ? priceInfo.getRawIdentifier() : null);
            dataConfig.set(path + ".timestamp", System.currentTimeMillis());
            dataConfig.set(path + ".status", "CREATED");
            forceSave();
        }
        return txId;
    }

    public void updateShopTransactionStatus(String txId, String status, String note) {
        if (txId == null || txId.isEmpty()) return;
        synchronized (saveLock) {
            if (txId.startsWith("legacy_")) {
                String legacyKey = txId.substring("legacy_".length());
                dataConfig.set("pending_shop_refunds." + legacyKey + ".status", status);
            } else {
                String path = "shop_transactions." + txId;
                dataConfig.set(path + ".status", status);
                dataConfig.set(path + ".last_updated", System.currentTimeMillis());
                if (note != null) {
                    dataConfig.set(path + ".note", note);
                }
            }
            forceSave();
        }
    }

    public List<ShopTransactionEntry> getAllPendingShopTransactions() {
        List<ShopTransactionEntry> list = new ArrayList<>();
        synchronized (saveLock) {
            ConfigurationSection sec = dataConfig.getConfigurationSection("shop_transactions");
            if (sec != null) {
                for (String txId : sec.getKeys(false)) {
                    String status = sec.getString(txId + ".status", "UNKNOWN");
                    if (!"COMPLETED".equalsIgnoreCase(status) && !"REFUNDED".equalsIgnoreCase(status) && !"FAILED".equalsIgnoreCase(status)) {
                        String pStr = sec.getString(txId + ".player");
                        UUID pUuid = null;
                        try {
                            if (pStr != null) pUuid = UUID.fromString(pStr);
                        } catch (Exception ignored) {}
                        list.add(new ShopTransactionEntry(
                                txId,
                                pUuid,
                                sec.getString(txId + ".petId"),
                                sec.getString(txId + ".currency"),
                                sec.getDouble(txId + ".amount", 0.0),
                                sec.getString(txId + ".material"),
                                sec.getLong(txId + ".timestamp", 0L),
                                status,
                                sec.getString(txId + ".note")
                        ));
                    }
                }
            }

            ConfigurationSection legacySec = dataConfig.getConfigurationSection("pending_shop_refunds");
            if (legacySec != null) {
                for (String txId : legacySec.getKeys(false)) {
                    String status = legacySec.getString(txId + ".status", "PENDING");
                    if ("PENDING".equalsIgnoreCase(status)) {
                        String pStr = legacySec.getString(txId + ".player");
                        UUID pUuid = null;
                        try {
                            if (pStr != null) pUuid = UUID.fromString(pStr);
                        } catch (Exception ignored) {}
                        list.add(new ShopTransactionEntry(
                                "legacy_" + txId,
                                pUuid,
                                legacySec.getString(txId + ".petId"),
                                legacySec.getString(txId + ".currency"),
                                legacySec.getDouble(txId + ".amount", 0.0),
                                legacySec.getString(txId + ".material"),
                                legacySec.getLong(txId + ".timestamp", 0L),
                                "RECOVERABLE",
                                "Legacy pending refund"
                        ));
                    }
                }
            }
        }
        return list;
    }

    public boolean refundShopTransaction(ShopTransactionEntry entry, Player player) {
        if (entry == null || player == null || !player.isOnline()) return false;
        String currency = entry.getCurrency();
        double amount = entry.getAmount();
        String material = entry.getMaterial();

        if ("MONEY".equalsIgnoreCase(currency)) {
            CurrencyManager cm = plugin.getCurrencyManager();
            return cm != null && cm.depositMoney(player, amount);
        }
        if ("POINTS".equalsIgnoreCase(currency)) {
            CurrencyManager cm = plugin.getCurrencyManager();
            return cm != null && cm.givePoints(player, (int) amount);
        }
        if ("ITEM".equalsIgnoreCase(currency)) {
            ItemHookManager ihm = plugin.getItemHookManager();
            if (ihm == null || material == null || amount <= 0) return false;
            return ihm.refundItem(player, material, (int) amount, null, null);
        }
        if ("FREE".equalsIgnoreCase(currency)) {
            return true;
        }
        return false;
    }

    public boolean resolvePendingShopTransaction(String txId, String action, String adminName) {
        if (txId == null || action == null) return false;
        ShopTransactionEntry target = null;
        for (ShopTransactionEntry entry : getAllPendingShopTransactions()) {
            if (entry.getTxId().equalsIgnoreCase(txId)) {
                target = entry;
                break;
            }
        }
        if (target == null) return false;

        if ("grant".equalsIgnoreCase(action)) {
            if (target.getPlayerUuid() != null) {
                Player p = Bukkit.getPlayer(target.getPlayerUuid());
                if (p != null && p.isOnline()) {
                    if (plugin.getOwnershipManager() != null) {
                        plugin.getOwnershipManager().acquirePet(p, target.getPetId(), 1, 0, 1, PetTrait.rollRandomTrait(), null);
                    }
                    if (plugin.getCodexManager() != null) {
                        plugin.getCodexManager().discover(target.getPlayerUuid(), target.getPetId());
                    }
                } else {
                    savePetProfile(target.getPlayerUuid(), target.getPetId(), 1, 0, 1, PetTrait.rollRandomTrait().name(), null, new ArrayList<>(), true);
                    if (plugin.getCodexManager() != null) {
                        plugin.getCodexManager().discover(target.getPlayerUuid(), target.getPetId());
                    }
                }
            }
            updateShopTransactionStatus(target.getTxId(), "COMPLETED", "Manually granted by " + adminName);
            return true;
        }

        if ("refund".equalsIgnoreCase(action)) {
            if (target.getPlayerUuid() != null) {
                Player p = Bukkit.getPlayer(target.getPlayerUuid());
                if (p != null && p.isOnline()) {
                    boolean ok = refundShopTransaction(target, p);
                    if (ok) {
                        updateShopTransactionStatus(target.getTxId(), "REFUNDED", "Refunded by " + adminName);
                        return true;
                    }
                    return false;
                } else {
                    if ("MONEY".equalsIgnoreCase(target.getCurrency())) {
                        if (plugin.getCurrencyManager() != null && plugin.getCurrencyManager().hasEconomy()) {
                            var op = Bukkit.getOfflinePlayer(target.getPlayerUuid());
                            plugin.getCurrencyManager().getEconomy().depositPlayer(op, target.getAmount());
                            updateShopTransactionStatus(target.getTxId(), "REFUNDED", "Offline Vault refund by " + adminName);
                            return true;
                        }
                    }
                    return false;
                }
            }
            return false;
        }

        if ("discard".equalsIgnoreCase(action)) {
            updateShopTransactionStatus(target.getTxId(), "FAILED", "Discarded by " + adminName);
            return true;
        }

        return false;
    }

    public int resolvePendingShopRefunds(Player player) {
        if (player == null || !player.isOnline()) return 0;
        int recovered = 0;
        UUID uuid = player.getUniqueId();
        List<ShopTransactionEntry> toProcess = new ArrayList<>();

        synchronized (saveLock) {
            ConfigurationSection sec = dataConfig.getConfigurationSection("shop_transactions");
            if (sec != null) {
                for (String txId : sec.getKeys(false)) {
                    String pStr = sec.getString(txId + ".player");
                    if (pStr != null && pStr.equals(uuid.toString())) {
                        String status = sec.getString(txId + ".status", "UNKNOWN");
                        if ("RECOVERABLE".equalsIgnoreCase(status) || "REFUND_PROCESSING".equalsIgnoreCase(status)
                                || "PAID".equalsIgnoreCase(status) || "REWARD_PROCESSING".equalsIgnoreCase(status)) {
                            toProcess.add(new ShopTransactionEntry(
                                    txId,
                                    uuid,
                                    sec.getString(txId + ".petId"),
                                    sec.getString(txId + ".currency"),
                                    sec.getDouble(txId + ".amount", 0.0),
                                    sec.getString(txId + ".material"),
                                    sec.getLong(txId + ".timestamp", 0L),
                                    status,
                                    sec.getString(txId + ".note")
                            ));
                        }
                    }
                }
            }

            ConfigurationSection legacySec = dataConfig.getConfigurationSection("pending_shop_refunds");
            if (legacySec != null) {
                for (String legacyId : legacySec.getKeys(false)) {
                    String pStr = legacySec.getString(legacyId + ".player");
                    if (pStr != null && pStr.equals(uuid.toString())) {
                        String status = legacySec.getString(legacyId + ".status", "PENDING");
                        if ("PENDING".equalsIgnoreCase(status)) {
                            toProcess.add(new ShopTransactionEntry(
                                    "legacy_" + legacyId,
                                    uuid,
                                    legacySec.getString(legacyId + ".petId"),
                                    legacySec.getString(legacyId + ".currency"),
                                    legacySec.getDouble(legacyId + ".amount", 0.0),
                                    legacySec.getString(legacyId + ".material"),
                                    legacySec.getLong(legacyId + ".timestamp", 0L),
                                    "RECOVERABLE",
                                    "Legacy pending refund"
                            ));
                        }
                    }
                }
            }
        }

        for (ShopTransactionEntry entry : toProcess) {
            String status = entry.getStatus();
            if ("PAID".equalsIgnoreCase(status) || "REWARD_PROCESSING".equalsIgnoreCase(status)) {
                if (plugin.getOwnershipManager() != null && plugin.getOwnershipManager().hasPet(uuid, entry.getPetId())) {
                    updateShopTransactionStatus(entry.getTxId(), "COMPLETED", "Confirmed owned on join");
                    recovered++;
                } else if (plugin.getOwnershipManager() != null && plugin.getOwnershipManager().canAcquirePet(player)) {
                    boolean ok = plugin.getOwnershipManager().acquirePet(player, entry.getPetId(), 1, 0, 1, PetTrait.rollRandomTrait(), null);
                    if (ok) {
                        if (plugin.getCodexManager() != null) {
                            plugin.getCodexManager().discover(uuid, entry.getPetId());
                        }
                        updateShopTransactionStatus(entry.getTxId(), "COMPLETED", "Granted on join");
                        String petName = plugin.getLanguage() != null ? plugin.getLanguage().getPetName(entry.getPetId()) : entry.getPetId();
                        player.sendMessage(plugin.getLanguage() != null
                                ? plugin.getLanguage().getMessageOrDefault("shop.recovered_grant", "§a[IpsecuzPet] Giao dịch mua thú cưng %pet% trước đó đã được hoàn tất thành công!", "%pet%", petName)
                                : "§a[IpsecuzPet] Thú cưng " + petName + " đã được trao vào tài khoản!");
                        recovered++;
                    }
                } else {
                    boolean refunded = refundShopTransaction(entry, player);
                    if (refunded) {
                        updateShopTransactionStatus(entry.getTxId(), "REFUNDED", "Refunded on join (capacity full)");
                        player.sendMessage(plugin.getLanguage() != null
                                ? plugin.getLanguage().getMessageOrDefault("shop.recovered_refund", "§a[IpsecuzPet] Giao dịch trước đó đã được hoàn trả thành công!")
                                : "§a[IpsecuzPet] Giao dịch trước đó đã được hoàn trả thành công!");
                        recovered++;
                    }
                }
            } else if ("RECOVERABLE".equalsIgnoreCase(status) || "REFUND_PROCESSING".equalsIgnoreCase(status)) {
                boolean refunded = refundShopTransaction(entry, player);
                if (refunded) {
                    updateShopTransactionStatus(entry.getTxId(), "REFUNDED", "Recovered on join");
                    player.sendMessage(plugin.getLanguage() != null
                            ? plugin.getLanguage().getMessageOrDefault("shop.recovered_refund", "§a[IpsecuzPet] Giao dịch trước đó đã được hoàn trả thành công!")
                            : "§a[IpsecuzPet] Giao dịch trước đó đã được hoàn trả thành công!");
                    recovered++;
                }
            }
        }

        return recovered;
    }
}