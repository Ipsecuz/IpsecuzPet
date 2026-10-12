package org.ipsecuz.pet;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class HatchingManager {
    private final IpsecuzPet plugin;
    public final NamespacedKey eggKey;

    public static class HatchMenuHolder implements InventoryHolder {
        private Inventory inventory;
        @Override
        public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
    }

    public static class RouletteHolder implements InventoryHolder {
        private Inventory inventory;
        private final String eggId;
        private final String winningPetId;
        private boolean finished = false;

        public RouletteHolder(String eggId, String winningPetId) {
            this.eggId = eggId;
            this.winningPetId = winningPetId;
        }

        @Override
        public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        public String getEggId() { return eggId; }
        public String getWinningPetId() { return winningPetId; }
        public boolean isFinished() { return finished; }
        public void setFinished(boolean finished) { this.finished = finished; }
    }

    public enum HatchState {
        CREATED,
        PAYMENT_PROCESSING,
        PAID,
        PROCESSING,
        COMPLETED,
        FAILED,
        RECOVERABLE
    }

    public static class PendingHatchSession {
        public static final int CURRENT_SCHEMA = 2;
        private final UUID playerUuid;
        private final String transactionId;
        private final String eggId;
        private final String winningPetId;
        private volatile HatchState state;
        private final long timestamp;
        private final int schemaVersion;
        private volatile boolean paymentCommitted;
        private volatile boolean rewardGranted;

        public PendingHatchSession(UUID playerUuid, String eggId, String winningPetId) {
            this(playerUuid, UUID.randomUUID().toString(), eggId, winningPetId, HatchState.CREATED, System.currentTimeMillis(), CURRENT_SCHEMA, false, false);
        }

        public PendingHatchSession(UUID playerUuid, String eggId, String winningPetId, HatchState state, long timestamp) {
            this(playerUuid, UUID.randomUUID().toString(), eggId, winningPetId, state, timestamp, CURRENT_SCHEMA, state == HatchState.PAID || state == HatchState.PROCESSING || state == HatchState.COMPLETED, state == HatchState.COMPLETED);
        }

        public PendingHatchSession(UUID playerUuid, String eggId, String winningPetId, HatchState state, long timestamp, int schemaVersion, boolean paymentCommitted) {
            this(playerUuid, UUID.randomUUID().toString(), eggId, winningPetId, state, timestamp, schemaVersion, paymentCommitted, state == HatchState.COMPLETED);
        }

        public PendingHatchSession(UUID playerUuid, String transactionId, String eggId, String winningPetId, HatchState state, long timestamp, int schemaVersion, boolean paymentCommitted, boolean rewardGranted) {
            this.playerUuid = playerUuid;
            this.transactionId = transactionId != null ? transactionId : UUID.randomUUID().toString();
            this.eggId = eggId;
            this.winningPetId = winningPetId;
            this.state = state;
            this.timestamp = timestamp;
            this.schemaVersion = schemaVersion;
            this.paymentCommitted = paymentCommitted;
            this.rewardGranted = rewardGranted;
        }

        public UUID getPlayerUuid() { return playerUuid; }
        public String getTransactionId() { return transactionId; }
        public String getEggId() { return eggId; }
        public String getWinningPetId() { return winningPetId; }
        public HatchState getState() { return state; }
        public void setState(HatchState state) { this.state = state; }
        public long getTimestamp() { return timestamp; }
        public int getSchemaVersion() { return schemaVersion; }
        public boolean isPaymentCommitted() { return paymentCommitted; }
        public void setPaymentCommitted(boolean paymentCommitted) { this.paymentCommitted = paymentCommitted; }
        public boolean isRewardGranted() { return rewardGranted; }
        public void setRewardGranted(boolean rewardGranted) { this.rewardGranted = rewardGranted; }

        public synchronized boolean markCommitted() {
            if (this.state == HatchState.COMPLETED || this.rewardGranted) {
                return false;
            }
            this.state = HatchState.COMPLETED;
            this.rewardGranted = true;
            return true;
        }
    }

    private final Map<UUID, PendingHatchSession> pendingHatchSessions = new java.util.concurrent.ConcurrentHashMap<>();

    public HatchingManager(IpsecuzPet plugin) {
        this.plugin = plugin;
        this.eggKey = new NamespacedKey(plugin, "pet_egg_id");
        validateAllEggs();
        loadPendingSessions();
    }

    public void validateAllEggs() {
        FileConfiguration config = plugin.getModuleManager() != null ? plugin.getModuleManager().getHatchingConfig() : null;
        if (config == null) return;
        ConfigurationSection eggsSec = config.getConfigurationSection("eggs");
        if (eggsSec == null) return;
        for (String eggId : eggsSec.getKeys(false)) {
            ConfigurationSection sec = eggsSec.getConfigurationSection(eggId);
            if (!validateEgg(eggId, sec)) {
                plugin.getLogger().warning("[Hatching] Egg configuration validation failed for: " + eggId);
            }
        }
    }

    public boolean validateEgg(String eggId, ConfigurationSection eggSec) {
        if (eggSec == null) {
            plugin.getLogger().warning("[Hatching] Egg '" + eggId + "' configuration section is null!");
            return false;
        }

        ConfigurationSection lootTable = eggSec.getConfigurationSection("loot_table");
        if (lootTable == null || lootTable.getKeys(false).isEmpty()) {
            plugin.getLogger().warning("[Hatching] Egg '" + eggId + "' has missing or empty loot_table!");
            return false;
        }

        if (eggSec.getBoolean("free", false)) {
            return true;
        }

        boolean hasReqs = eggSec.contains("requirements") || eggSec.contains("all") || eggSec.contains("one_of");
        if (!hasReqs) {
            plugin.getLogger().warning("[Hatching] Egg '" + eggId + "' has no requirement block and is not marked 'free: true'!");
            return false;
        }

        org.ipsecuz.pet.requirement.RequirementGroup group = plugin.getRequirementManager().parse(eggSec);
        if (group == null || group.getRequirements().isEmpty()) {
            plugin.getLogger().warning("[Hatching] Egg '" + eggId + "' has unrecognized or empty requirement structure! Refusing to allow free hatch without 'free: true'.");
            return false;
        }

        return true;
    }

    public void loadPendingSessions() {
        var sec = plugin.getConfigManager().getData().getConfigurationSection("pending_hatch");
        if (sec != null) {
            for (String key : new ArrayList<>(sec.getKeys(false))) {
                try {
                    UUID u = UUID.fromString(key);
                    String txId = sec.getString(key + ".transaction_id");
                    String eggId = sec.getString(key + ".egg_id");
                    String winPet = sec.getString(key + ".winning_pet_id");
                    String stStr = sec.getString(key + ".state", "CREATED");
                    long time = sec.getLong(key + ".timestamp", System.currentTimeMillis());
                    int schema = sec.getInt(key + ".schema_version", 1);
                    boolean paid = sec.getBoolean(key + ".payment_committed", false);
                    boolean rewarded = sec.getBoolean(key + ".reward_granted", false);

                    if (txId == null || txId.isEmpty()) {
                        txId = UUID.randomUUID().toString();
                    }

                    if (schema <= 0 || schema > PendingHatchSession.CURRENT_SCHEMA) {
                        plugin.getLogger().warning("[Hatching] Pending transaction for " + key + " has incompatible schema: " + schema + ". Preserving record without deleting.");
                        continue;
                    }

                    if (eggId == null || !plugin.getModuleManager().getHatchingConfig().contains("eggs." + eggId)) {
                        plugin.getLogger().warning("[Hatching] Pending transaction for " + key + " has invalid/missing eggId: '" + eggId + "'. Preserving record without deleting.");
                        continue;
                    }

                    if (winPet == null || !plugin.getConfig().contains("pets." + winPet)) {
                        plugin.getLogger().warning("[Hatching] Pending transaction for " + key + " has invalid/missing petId: '" + winPet + "'. Preserving record without deleting.");
                        continue;
                    }

                    if (time <= 0 || time > System.currentTimeMillis() + 60_000L) {
                        plugin.getLogger().warning("[Hatching] Pending transaction for " + key + " has invalid timestamp: " + time + ". Preserving record without deleting.");
                        continue;
                    }

                    HatchState st;
                    try {
                        st = HatchState.valueOf(stStr);
                    } catch (IllegalArgumentException ex) {
                        plugin.getLogger().warning("[Hatching] Pending transaction for " + key + " has unknown state: '" + stStr + "'. Preserving record without deleting.");
                        continue;
                    }

                    // Ambiguous external-provider payment status:
                    // If crashed during PAYMENT_PROCESSING, payment may or may not have succeeded.
                    if (st == HatchState.PAYMENT_PROCESSING) {
                        st = HatchState.RECOVERABLE;
                        plugin.getLogger().warning("[Hatching] Ambiguous transaction for " + key + " (state: PAYMENT_PROCESSING, tx: " + txId + "). Server may have stopped during payment. Preserving journal for recovery; will not auto-grant or auto-refund.");
                    }

                    PendingHatchSession session = new PendingHatchSession(u, txId, eggId, winPet, st, time, schema, paid, rewarded);
                    pendingHatchSessions.put(u, session);
                } catch (Exception ex) {
                    plugin.getLogger().warning("[Hatching] Error loading pending transaction for key " + key + ": " + ex.getMessage());
                }
            }
        }
    }

    public void persistPendingSession(PendingHatchSession session) {
        if (session == null) return;
        String path = "pending_hatch." + session.getPlayerUuid();
        plugin.getConfigManager().getData().set(path + ".transaction_id", session.getTransactionId());
        plugin.getConfigManager().getData().set(path + ".egg_id", session.getEggId());
        plugin.getConfigManager().getData().set(path + ".winning_pet_id", session.getWinningPetId());
        plugin.getConfigManager().getData().set(path + ".state", session.getState().name());
        plugin.getConfigManager().getData().set(path + ".timestamp", session.getTimestamp());
        plugin.getConfigManager().getData().set(path + ".schema_version", session.getSchemaVersion());
        plugin.getConfigManager().getData().set(path + ".payment_committed", session.isPaymentCommitted());
        plugin.getConfigManager().getData().set(path + ".reward_granted", session.isRewardGranted());
        plugin.getConfigManager().forceSave();
    }

    public void removePersistedPendingSession(UUID uuid) {
        if (uuid == null) return;
        plugin.getConfigManager().getData().set("pending_hatch." + uuid, null);
        plugin.getConfigManager().forceSave();
    }

    public void recordPendingHatch(UUID uuid, String eggId, String winningPetId, HatchState state) {
        boolean paid = (state == HatchState.PAID || state == HatchState.PROCESSING || state == HatchState.COMPLETED);
        PendingHatchSession session = new PendingHatchSession(uuid, UUID.randomUUID().toString(), eggId, winningPetId, state, System.currentTimeMillis(), PendingHatchSession.CURRENT_SCHEMA, paid, state == HatchState.COMPLETED);
        pendingHatchSessions.put(uuid, session);
        persistPendingSession(session);
    }

    public void recordPendingHatch(UUID uuid, String eggId, String winningPetId) {
        recordPendingHatch(uuid, eggId, winningPetId, HatchState.CREATED);
    }

    public void resolvePendingHatchOnJoin(Player player) {
        if (player == null) return;
        PendingHatchSession session = pendingHatchSessions.get(player.getUniqueId());
        if (session != null) {
            LanguageManager lang = plugin.getLanguage();
            // Case 1: Reward was already granted before crash, but pending state was not yet cleared
            if (session.isRewardGranted() || session.getState() == HatchState.COMPLETED) {
                pendingHatchSessions.remove(player.getUniqueId());
                removePersistedPendingSession(player.getUniqueId());
                return;
            }

            // Case 2: Definitely paid (payment committed) -> grant reward idempotently
            if (session.isPaymentCommitted() && (session.getState() == HatchState.PAID || session.getState() == HatchState.PROCESSING)) {
                plugin.getLogger().info("[Hatching] Auto-delivering paid hatch reward for: " + player.getName() + " (tx: " + session.getTransactionId() + ")");
                completeHatchReward(player.getUniqueId(), session.getWinningPetId(), true);
                return;
            }

            // Case 3: Ambiguous payment status (PAYMENT_PROCESSING or RECOVERABLE with uncommitted payment)
            if (session.getState() == HatchState.PAYMENT_PROCESSING || session.getState() == HatchState.RECOVERABLE) {
                plugin.getLogger().warning("[Hatching] Player " + player.getName() + " has an ambiguous hatch transaction (" + session.getTransactionId() + "). Payment status uncertain; preserving journal without auto-grant or auto-refund.");
                if (lang != null) {
                    String msg = lang.getMessage("hatching.ambiguous_join", "%tx_id%", session.getTransactionId());
                    player.sendMessage(msg);
                }
                return;
            }

            // Case 4: Definitely unpaid (CREATED and not paymentCommitted) or FAILED
            if (session.getState() == HatchState.CREATED || session.getState() == HatchState.FAILED) {
                pendingHatchSessions.remove(player.getUniqueId());
                removePersistedPendingSession(player.getUniqueId());
            }
        }
    }

    public void handlePlayerQuit(Player player) {
        // Giữ nguyên phiên giao dịch chưa hoàn tất để bảo toàn phần thưởng an toàn
    }

    public void saveAllPendingTransactions() {
        for (PendingHatchSession s : pendingHatchSessions.values()) {
            persistPendingSession(s);
        }
        plugin.getConfigManager().forceSave();
    }

    public ItemStack createEggItem(String eggId, int amount) {
        FileConfiguration config = plugin.getModuleManager().getHatchingConfig();
        ConfigurationSection sec = config.getConfigurationSection("eggs." + eggId);
        if (sec == null) return null;

        String matStr = sec.getString("material", "EGG");
        ItemStack item = plugin.getItemHookManager().getItem(matStr, Material.EGG);
        item.setAmount(amount);

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        LanguageManager lang = plugin.getLanguage();
        String name = sec.getString("name", "&eTrứng Pet");
        String eggKeyName = "eggs." + eggId + ".name";
        if (lang != null && !lang.getRawMessage(eggKeyName).startsWith("§cMissing")) {
            name = lang.getRawMessage(eggKeyName);
        }
        meta.displayName(GuiText.component(name));

        List<Component> lore = new ArrayList<>();
        List<String> transLore = (lang != null) ? lang.getMessageList("eggs." + eggId + ".lore") : null;
        List<String> rawLore = (transLore != null && !transLore.isEmpty()) ? transLore : sec.getStringList("lore");
        for (String line : rawLore) {
            lore.add(GuiText.component(line));
        }

        if (sec.contains("requirements") || sec.contains("all") || sec.contains("one_of") || sec.contains("price") || sec.contains("cost_money")) {
            org.ipsecuz.pet.requirement.RequirementGroup group = plugin.getRequirementManager().parse(sec);
            if (!group.getRequirements().isEmpty()) {
                lore.add(GuiText.component(" "));
                String costHeader = (lang != null) ? lang.getRawMessage("gui.hatch_cost_header") : "&6✦ Chi phí để quay:";
                lore.add(GuiText.component(costHeader));
                org.ipsecuz.pet.requirement.RequirementContext ctx = new org.ipsecuz.pet.requirement.RequirementContext(null, plugin);
                org.ipsecuz.pet.requirement.RequirementCheckResult result = plugin.getRequirementManager().evaluate(group, ctx);
                for (String line : org.ipsecuz.pet.requirement.RequirementGuiRenderer.renderToStrings(result)) {
                    if (!line.contains("ĐỦ ĐIỀU KIỆN") && !line.contains("CHƯA ĐỦ ĐIỀU KIỆN") && !line.startsWith("§7----")) {
                        lore.add(GuiText.component(line));
                    }
                }
            }
        }

        meta.lore(lore);
        meta.getPersistentDataContainer().set(eggKey, PersistentDataType.STRING, eggId);

        item.setItemMeta(meta);
        return item;
    }

    public String getEggId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(eggKey, PersistentDataType.STRING);
    }

    public void openHatchingGui(Player player) {
        LanguageManager lang = plugin.getLanguage();
        if (!plugin.getModuleManager().isHatchingEnabled()) {
            player.sendMessage(lang != null ? lang.getMessage("hatching.disabled") : "§cTính năng Ấp Trứng & Quay Pet hiện đang bị tắt bởi máy chủ!");
            return;
        }

        FileConfiguration config = plugin.getModuleManager().getHatchingConfig();
        String title = (lang != null && !lang.getRawMessage("gui.hatch_title").startsWith("§cMissing"))
                ? lang.getRawMessage("gui.hatch_title")
                : config.getString("gui.title", "&1✦ Lò Ấp Trứng Thú Cưng ✦");
        int size = config.getInt("gui.size", 45);

        HatchMenuHolder holder = new HatchMenuHolder();
        Inventory inv = Bukkit.createInventory(holder, size, GuiText.title(title));
        holder.setInventory(inv);

        ItemStack glass = new ItemStack(Material.CYAN_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.displayName(GuiText.component(" "));
            glass.setItemMeta(glassMeta);
        }
        for (int i = 0; i < size; i++) {
            if (i < 9 || i >= size - 9 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, glass);
            }
        }

        ConfigurationSection eggsSec = config.getConfigurationSection("eggs");
        if (eggsSec != null) {
            int slot = 20;
            for (String eggId : eggsSec.getKeys(false)) {
                ItemStack eggItem = createEggItem(eggId, 1);
                if (eggItem != null && slot < size - 9) {
                    ItemMeta meta = eggItem.getItemMeta();
                    if (meta != null) {
                        List<Component> lore = meta.lore();
                        if (lore == null) lore = new ArrayList<>();
                        lore.add(GuiText.component(" "));
                        String spinHint = (lang != null) ? lang.getRawMessage("gui.hatch_click_spin") : "&a▶ Nhấp để Quay Gacha & Ấp Trứng!";
                        lore.add(GuiText.component(spinHint));
                        meta.lore(lore);
                        eggItem.setItemMeta(meta);
                    }
                    inv.setItem(slot, eggItem);
                    slot += 2;
                }
            }
        }

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.displayName(GuiText.component((lang != null) ? lang.getRawMessage("gui.hatch_guide_title") : "&e&lHƯỚNG DẪN ẤP TRỨNG & QUAY PET"));
            List<Component> infoLore = new ArrayList<>();
            infoLore.add(GuiText.component((lang != null) ? lang.getRawMessage("gui.hatch_guide_line1") : "&7- Nhấp chuột phải vào quả trứng trên tay"));
            infoLore.add(GuiText.component((lang != null) ? lang.getRawMessage("gui.hatch_guide_line2") : "&7  hoặc nhấp trực tiếp vào biểu tượng trên GUI."));
            infoLore.add(GuiText.component((lang != null) ? lang.getRawMessage("gui.hatch_guide_line3") : "&7- Vòng quay Roulette sống động với hiệu ứng âm thanh!"));
            infoLore.add(GuiText.component((lang != null) ? lang.getRawMessage("gui.hatch_guide_line4") : "&7- Pet dừng lại ở ô giữa sẽ thuộc về bạn!"));
            infoLore.add(GuiText.component((lang != null) ? lang.getRawMessage("gui.hatch_guide_line5") : "&7- Nếu quay trùng pet: nhận Mảnh Pet & EXP thưởng!"));
            infoMeta.lore(infoLore);
            info.setItemMeta(infoMeta);
        }
        inv.setItem(size - 5, info);

        player.openInventory(inv);
    }

    public boolean checkAndDeductRequirements(Player player, ConfigurationSection eggSec, ItemStack consumedItem) {
        if (eggSec == null) return false;

        if (eggSec.getBoolean("free", false)) {
            if (consumedItem != null) {
                consumedItem.setAmount(consumedItem.getAmount() - 1);
            }
            return true;
        }

        org.ipsecuz.pet.requirement.RequirementGroup group = plugin.getRequirementManager().parse(eggSec);
        if (group == null || group.getRequirements().isEmpty()) {
            plugin.getLogger().warning("[Hatching] Refusing to deduct requirements: egg requirement group is empty and egg is not marked 'free: true'.");
            return false;
        }

        org.ipsecuz.pet.requirement.RequirementContext ctx = new org.ipsecuz.pet.requirement.RequirementContext(player, plugin);

        if (!plugin.getRequirementManager().executeTransaction(group, ctx)) {
            return false;
        }

        if (consumedItem != null) {
            consumedItem.setAmount(consumedItem.getAmount() - 1);
        }

        return true;
    }

    public void processHatch(Player player, String eggId, ItemStack consumedItem) {
        LanguageManager lang = plugin.getLanguage();
        if (!plugin.getModuleManager().isHatchingEnabled()) {
            String msg = lang != null ? lang.getMessage("hatching.disabled") : null;
            player.sendMessage(msg != null ? msg : "§cTính năng Ấp Trứng & Quay Pet hiện đang bị tắt bởi máy chủ!");
            return;
        }

        FileConfiguration config = plugin.getModuleManager().getHatchingConfig();
        ConfigurationSection eggSec = config.getConfigurationSection("eggs." + eggId);
        if (eggSec == null) {
            String msg = lang != null ? lang.getMessage("hatching.invalid_egg") : null;
            player.sendMessage(msg != null ? msg : "§cKhông tìm thấy dữ liệu cho loại trứng này!");
            return;
        }

        if (!validateEgg(eggId, eggSec)) {
            String msg = lang != null ? lang.getMessage("hatching.invalid_requirements") : null;
            player.sendMessage(msg != null ? msg : "§cĐiều kiện hoặc chi phí của trứng này không hợp lệ!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        ConfigurationSection lootTable = eggSec.getConfigurationSection("loot_table");
        if (lootTable == null || lootTable.getKeys(false).isEmpty()) {
            String msg = lang != null ? lang.getMessage("hatching.empty_loot") : null;
            player.sendMessage(msg != null ? msg : "§cTrứng này chưa được thiết lập danh sách pet có thể nở!");
            return;
        }

        // 1. Thu thập tỷ lệ loot table với LinkedHashMap và kiểm tra Pet ID hợp lệ
        int totalWeight = 0;
        LinkedHashMap<String, Integer> weights = new LinkedHashMap<>();
        List<String> candidatePetIds = new ArrayList<>();
        for (String pId : lootTable.getKeys(false)) {
            // Xác thực pet ID tồn tại trong hệ thống và không bị tắt
            if (!plugin.getConfig().contains("pets." + pId)) continue;
            if (!plugin.getConfig().getBoolean("pets." + pId + ".enabled", true)) continue;

            int w = lootTable.getInt(pId, 0);
            if (w <= 0) continue; // Bỏ qua tỉ lệ không dương

            weights.put(pId, w);
            totalWeight += w;
            candidatePetIds.add(pId);
        }

        if (totalWeight <= 0 || candidatePetIds.isEmpty()) {
            String msg = lang != null ? lang.getMessage("hatching.invalid_rates") : null;
            player.sendMessage(msg != null ? msg : "§cTrứng này chưa có tỉ lệ rớt hợp lệ hoặc các Pet cấu hình đều không khả dụng!");
            return;
        }

        int randomWeight = ThreadLocalRandom.current().nextInt(totalWeight);
        String selectedWinner = null;
        int count = 0;
        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
            count += entry.getValue();
            if (randomWeight < count) {
                selectedWinner = entry.getKey();
                break;
            }
        }
        if (selectedWinner == null) selectedWinner = candidatePetIds.get(0);
        final String winningPetId = selectedWinner;

        // 2. Kiểm tra Duplicate và Max Pet Limit trước khi trừ tiền
        boolean isDuplicate = plugin.getConfigManager().getData().contains(player.getUniqueId() + ".pets." + winningPetId);
        if (!isDuplicate && !plugin.getOwnershipManager().canAcquirePet(player)) {
            player.sendMessage("§cBạn đã đạt giới hạn tối đa số pet có thể sở hữu! Nâng cấp VIP hoặc chuyển pet thành thẻ để tiếp tục.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // 3. Ghi nhận giao dịch CREATED bền vững
        String txId = UUID.randomUUID().toString();
        PendingHatchSession session = new PendingHatchSession(player.getUniqueId(), txId, eggId, winningPetId, HatchState.CREATED, System.currentTimeMillis(), PendingHatchSession.CURRENT_SCHEMA, false, false);
        pendingHatchSessions.put(player.getUniqueId(), session);
        persistPendingSession(session);

        // 4. Khấu trừ chi phí giao dịch với trạng thái PAYMENT_PROCESSING
        session.setState(HatchState.PAYMENT_PROCESSING);
        persistPendingSession(session);

        if (!checkAndDeductRequirements(player, eggSec, consumedItem)) {
            session.setState(HatchState.FAILED);
            pendingHatchSessions.remove(player.getUniqueId());
            removePersistedPendingSession(player.getUniqueId());
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // 5. Nâng cấp trạng thái lên PAID (đã thanh toán bền vững) rồi PROCESSING
        session.setState(HatchState.PAID);
        session.setPaymentCommitted(true);
        persistPendingSession(session);

        session.setState(HatchState.PROCESSING);
        persistPendingSession(session);

        // 6. Bắt đầu vòng quay Roulette với phiên theo dõi an toàn
        startGachaRoulette(player, eggId, eggSec, candidatePetIds, winningPetId);
    }

    private void startGachaRoulette(Player player, String eggId, ConfigurationSection eggSec, List<String> candidatePetIds, String winningPetId) {

        int totalSteps = 36;
        int winningIndex = totalSteps + 4;
        List<ItemStack> rollingItems = new ArrayList<>();
        for (int i = 0; i < winningIndex + 10; i++) {
            if (i == winningIndex) {
                rollingItems.add(createPetDisplayIcon(winningPetId, true));
            } else {
                String randomPet = candidatePetIds.get(ThreadLocalRandom.current().nextInt(candidatePetIds.size()));
                rollingItems.add(createPetDisplayIcon(randomPet, false));
            }
        }

        RouletteHolder holder = new RouletteHolder(eggId, winningPetId);
        String eggName = eggSec.getString("name", "Trứng Pet");
        Inventory inv = Bukkit.createInventory(holder, 27, LegacyComponentSerializer.legacySection().deserialize(
                ChatColor.translateAlternateColorCodes('&', "&0✦ Quay: " + eggName)));
        holder.setInventory(inv);

        updateRouletteBorders(inv, 0);

        for (int i = 0; i < 9; i++) {
            inv.setItem(9 + i, rollingItems.get(i));
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f);

        scheduleRouletteStep(player, inv, holder, rollingItems, 0, totalSteps, winningPetId);
    }

    public ItemStack createPetDisplayIcon(String petId, boolean isWinner) {
        String iconMat = plugin.getConfig().getString("pets." + petId + ".icon");
        ItemStack item = null;
        if (iconMat != null && !iconMat.isEmpty() && !"STONE".equalsIgnoreCase(iconMat)) {
            item = plugin.getItemHookManager().getItem(iconMat, null);
        }
        if (item == null) {
            String matStr = plugin.getConfig().getString("pets." + petId + ".material");
            if (matStr != null && !matStr.isEmpty() && !"STONE".equalsIgnoreCase(matStr)) {
                item = plugin.getItemHookManager().getItem(matStr, null);
            }
        }
        if (item == null) {
            EntityType entityType = (plugin.getPetManager() != null) ? plugin.getPetManager().resolveEntityType(petId) : null;
            if (entityType != null) {
                item = resolveThematicMobIcon(entityType);
            }
        }
        if (item == null) {
            item = new ItemStack(Material.NAME_TAG);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            LanguageManager lang = plugin.getLanguage();
            String name = (lang != null) ? lang.getPetName(petId) : plugin.getConfig().getString("pets." + petId + ".name", petId);
            PetRarity rarity = PetRarity.fromPetId(plugin, petId);
            meta.displayName(GuiText.component((isWinner ? "&6&l★ " : "&f") + name));
            GuiLoreBuilder lb = GuiLoreBuilder.create()
                    .stat((lang != null) ? lang.getRawMessage("gui.stat_pet_id") : "Mã Pet", petId)
                    .stat((lang != null) ? lang.getRawMessage("gui.stat_rarity") : "Độ hiếm", rarity.getLocalizedName(plugin));
            if (isWinner) {
                lb.success((lang != null) ? lang.getRawMessage("gui.hatch_winner_badge") : "PHẦN THƯỞNG CỦA BẠN!");
            }
            meta.lore(lb.build());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack resolveThematicMobIcon(EntityType type) {
        try {
            Material spawnEgg = Material.getMaterial(type.name() + "_SPAWN_EGG");
            if (spawnEgg != null) {
                return new ItemStack(spawnEgg);
            }
        } catch (Throwable ignored) {}

        Material fallbackMat = switch (type) {
            case WOLF -> Material.BONE;
            case CAT, OCELOT -> Material.COD;
            case CHICKEN -> Material.FEATHER;
            case COW -> Material.LEATHER;
            case PIG -> Material.CARROT;
            case SHEEP -> Material.WHITE_WOOL;
            case IRON_GOLEM -> Material.IRON_INGOT;
            case SNOWMAN -> Material.SNOWBALL;
            case BEE -> Material.HONEYCOMB;
            case CREEPER -> Material.GUNPOWDER;
            case SKELETON, WITHER_SKELETON -> Material.BOW;
            case SPIDER, CAVE_SPIDER -> Material.SPIDER_EYE;
            case ZOMBIE, ZOMBIE_VILLAGER, DROWNED, HUSK -> Material.ROTTEN_FLESH;
            case BLAZE -> Material.BLAZE_ROD;
            case ENDERMAN -> Material.ENDER_PEARL;
            case SLIME, MAGMA_CUBE -> Material.SLIME_BALL;
            case ALLAY -> Material.AMETHYST_SHARD;
            case AXOLOTL -> Material.WATER_BUCKET;
            case FROG -> Material.SLIME_BALL;
            case WARDEN -> Material.ECHO_SHARD;
            case FOX -> Material.SWEET_BERRIES;
            case RABBIT -> Material.RABBIT_FOOT;
            case PANDA -> Material.BAMBOO;
            case HORSE, DONKEY, MULE -> Material.SADDLE;
            case BAT -> Material.PHANTOM_MEMBRANE;
            case PARROT -> Material.WHEAT_SEEDS;
            case TURTLE -> Material.SCUTE;
            case DOLPHIN -> Material.TROPICAL_FISH;
            default -> Material.NAME_TAG;
        };
        return new ItemStack(fallbackMat);
    }

    private void updateRouletteBorders(Inventory inv, int step) {
        Material glassMat = (step % 2 == 0) ? Material.YELLOW_STAINED_GLASS_PANE : Material.ORANGE_STAINED_GLASS_PANE;
        ItemStack glass = new ItemStack(glassMat);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.displayName(GuiText.component(" "));
            glass.setItemMeta(meta);
        }

        LanguageManager lang = plugin.getLanguage();
        String pointerDownName = (lang != null && !lang.getRawMessage("gui.hatch_pointer_down").startsWith("§cMissing"))
                ? lang.getRawMessage("gui.hatch_pointer_down") : "&e▼ &6&lÔ NHẬN THƯỞNG &e▼";
        String pointerUpName = (lang != null && !lang.getRawMessage("gui.hatch_pointer_up").startsWith("§cMissing"))
                ? lang.getRawMessage("gui.hatch_pointer_up") : "&e▲ &6&lÔ NHẬN THƯỞNG &e▲";

        for (int i = 0; i < 9; i++) {
            if (i == 4) {
                ItemStack pointer = new ItemStack(Material.HOPPER);
                ItemMeta pMeta = pointer.getItemMeta();
                if (pMeta != null) {
                    pMeta.displayName(GuiText.component(pointerDownName));
                    pointer.setItemMeta(pMeta);
                }
                inv.setItem(4, pointer);
            } else {
                inv.setItem(i, glass);
            }
        }

        for (int i = 18; i < 27; i++) {
            if (i == 22) {
                ItemStack pointer = new ItemStack(Material.HOPPER);
                ItemMeta pMeta = pointer.getItemMeta();
                if (pMeta != null) {
                    pMeta.displayName(GuiText.component(pointerUpName));
                    pointer.setItemMeta(pMeta);
                }
                inv.setItem(22, pointer);
            } else {
                inv.setItem(i, glass);
            }
        }
    }

    private void scheduleRouletteStep(Player player, Inventory inv, RouletteHolder holder, List<ItemStack> items, int currentStep, int totalSteps, String winningPetId) {
        // 5 Giai đoạn giảm tốc độ tự nhiên (Natural Deceleration Curve)
        long delayTicks;
        Sound tickSound = Sound.BLOCK_NOTE_BLOCK_PLING;
        float pitch;

        if (currentStep < 16) {
            // Pha 1: Cuộn cực nhanh, quay số hồi hộp
            delayTicks = 2L;
            pitch = 0.9f;
        } else if (currentStep < 24) {
            // Pha 2: Chậm dần đều
            delayTicks = 3L;
            pitch = 1.1f;
        } else if (currentStep < 29) {
            // Pha 3: Bắt đầu lộ diện các ô kề bên
            delayTicks = 5L;
            pitch = 1.25f;
        } else if (currentStep < 33) {
            // Pha 4: Chậm từng nhịp rõ rệt
            delayTicks = 8L;
            pitch = 1.4f;
        } else {
            // Pha 5: Đỉnh điểm nghẹt thở ngay trước ô chiến thắng
            delayTicks = 12L;
            tickSound = Sound.BLOCK_NOTE_BLOCK_BELL;
            pitch = 1.6f;
        }

        final Sound finalTickSound = tickSound;
        final float finalPitch = pitch;

        SchedulerUtils.runEntityTaskLater(plugin, player, () -> {
            if (!player.isOnline()) {
                if (!holder.isFinished()) {
                    holder.setFinished(true);
                    completeHatchReward(player, winningPetId, false);
                }
                return;
            }

            int step = currentStep + 1;

            boolean isGuiOpen = player.getOpenInventory().getTopInventory().getHolder() instanceof RouletteHolder;
            if (isGuiOpen) {
                for (int i = 0; i < 9; i++) {
                    inv.setItem(9 + i, items.get(step + i));
                }
                updateRouletteBorders(inv, step);
                player.playSound(player.getLocation(), finalTickSound, 0.7f, finalPitch);
            }

            if (step < totalSteps) {
                scheduleRouletteStep(player, inv, holder, items, step, totalSteps, winningPetId);
            } else {
                holder.setFinished(true);
                if (isGuiOpen) {
                    finishRoulette(player, inv, winningPetId);
                } else {
                    completeHatchReward(player, winningPetId, true);
                }
            }
        }, delayTicks);
    }

    private void finishRoulette(Player player, Inventory inv, String winningPetId) {
        ItemStack winGlass = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta wgMeta = winGlass.getItemMeta();
        if (wgMeta != null) { wgMeta.displayName(Component.text(" ")); winGlass.setItemMeta(wgMeta); }
        inv.setItem(12, winGlass);
        inv.setItem(14, winGlass);

        completeHatchReward(player, winningPetId, true);

        SchedulerUtils.runEntityTaskLater(plugin, player, () -> {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof RouletteHolder) {
                player.closeInventory();
            }
        }, 50L);
    }

    public void completeHatchReward(Player player, String winningPetId, boolean showTitleAndEffects) {
        if (player == null) return;
        completeHatchReward(player.getUniqueId(), winningPetId, showTitleAndEffects);
    }

    public void completeHatchReward(UUID playerUuid, String winningPetId, boolean showTitleAndEffects) {
        PendingHatchSession session = pendingHatchSessions.get(playerUuid);
        if (session == null) {
            String path = "pending_hatch." + playerUuid;
            if (plugin.getConfigManager().getData().contains(path)) {
                String txId = plugin.getConfigManager().getData().getString(path + ".transaction_id");
                String eggId = plugin.getConfigManager().getData().getString(path + ".egg_id");
                String winPet = plugin.getConfigManager().getData().getString(path + ".winning_pet_id");
                String stStr = plugin.getConfigManager().getData().getString(path + ".state");
                int schema = plugin.getConfigManager().getData().getInt(path + ".schema_version", 1);
                boolean paid = plugin.getConfigManager().getData().getBoolean(path + ".payment_committed", false);
                boolean rewarded = plugin.getConfigManager().getData().getBoolean(path + ".reward_granted", false);
                long time = plugin.getConfigManager().getData().getLong(path + ".timestamp", 0L);

                // Strict validation: Reject malformed or incomplete records from reconstructing
                if (eggId != null && plugin.getModuleManager().getHatchingConfig().contains("eggs." + eggId)
                        && winPet != null && plugin.getConfig().contains("pets." + winPet)
                        && schema > 0 && schema <= PendingHatchSession.CURRENT_SCHEMA
                        && time > 0 && time <= System.currentTimeMillis() + 60_000L
                        && paid) {
                    HatchState st = HatchState.PAID;
                    try {
                        if (stStr != null) st = HatchState.valueOf(stStr);
                    } catch (Exception ignored) {}
                    session = new PendingHatchSession(playerUuid, txId, eggId, winPet, st, time, schema, paid, rewarded);
                    pendingHatchSessions.put(playerUuid, session);
                } else {
                    plugin.getLogger().warning("[Hatching] Incomplete or unverified pending transaction for " + playerUuid + ". Refusing to grant unverified reward.");
                    return;
                }
            }
        }

        if (session == null) {
            return;
        }

        // Must have confirmed payment
        if (!session.isPaymentCommitted() && session.getState() != HatchState.PAID && session.getState() != HatchState.PROCESSING) {
            plugin.getLogger().warning("[Hatching] Refusing to grant reward without confirmed payment: " + playerUuid);
            return;
        }

        // Idempotency: Reject repeated callback / second reward
        if (session.isRewardGranted() || session.getState() == HatchState.COMPLETED) {
            return;
        }

        // Transition: PAID -> PROCESSING
        session.setState(HatchState.PROCESSING);
        persistPendingSession(session);

        LanguageManager lang = plugin.getLanguage();
        Player player = Bukkit.getPlayer(playerUuid);
        String petDisplayName = plugin.getConfig().getString("pets." + winningPetId + ".name", winningPetId);
        PetRarity rarity = PetRarity.fromPetId(plugin, winningPetId);
        PetTrait rolledTrait = null;
        boolean isDuplicateReward = false;
        boolean isStorageFullReward = false;

        // 1. TẠO DỮ LIỆU PHẦN THƯỞNG (Pet mới hoặc quy đổi Shards)
        if (plugin.getConfigManager().getData().contains(playerUuid + ".pets." + winningPetId)) {
            // ĐÃ SỞ HỮU TRƯỚC ĐÓ -> CHUYỂN ĐỔI THÀNH MẢNH SHARDS & EXP
            isDuplicateReward = true;
            plugin.getShardManager().processDuplicateReward(playerUuid, winningPetId);
        } else if (!plugin.getOwnershipManager().canAcquirePet(playerUuid)) {
            // ĐÃ ĐẦY KHO PET TẠI THỜI ĐIỂM COMMIT -> CHUYỂN ĐỔI AN TOÀN SANG MẢNH SHARDS & EXP
            isStorageFullReward = true;
            plugin.getShardManager().processDuplicateReward(playerUuid, winningPetId);
        } else {
            // PET MỚI -> TẠO DỮ LIỆU, ROLL TRAIT VÀ LƯU CODEX
            plugin.getConfigManager().createPetDataIfMissing(playerUuid, winningPetId);
            rolledTrait = PetTrait.rollRandomTrait();
            plugin.getConfigManager().getData().set(playerUuid + ".pets." + winningPetId + ".trait", rolledTrait.name());
            plugin.getCodexManager().discover(playerUuid, winningPetId);
        }

        // 2. MARK COMMITTED VÀ PERSIST COMPLETED TRANSACTION STATE TRƯỚC KHI XÓA
        session.markCommitted();
        persistPendingSession(session);
        plugin.getConfigManager().forceSave();

        // 3. XÓA PENDING STATE SAU KHI PHẦN THƯỞNG ĐÃ BỀN VỮNG VÀ GIAO DỊCH ĐÃ COMPLETED
        pendingHatchSessions.remove(playerUuid);
        removePersistedPendingSession(playerUuid);
        plugin.getConfigManager().forceSave();

        // 4. GỬI THÔNG BÁO VÀ THÔNG ĐIỆP SAU KHI PERSISTENCE ĐÃ ĐẢM BẢO 100%
        if (isDuplicateReward) {
            if (player != null && player.isOnline()) {
                String dupMsg = lang != null ? lang.getMessage("hatching.duplicate_reward") : null;
                player.sendMessage(dupMsg != null ? dupMsg : "§e[IpsecuzPet] Bạn đã sở hữu Pet này! Đã tự động quy đổi thành Mảnh Pet và Kinh Nghiệm.");
            }
        } else if (isStorageFullReward) {
            if (player != null && player.isOnline()) {
                String fullMsg = lang != null ? lang.getMessage("hatching.full_storage_reward") : null;
                player.sendMessage(fullMsg != null ? fullMsg : "§e[Kho Thú Cưng Đã Đầy] Bạn đã đạt giới hạn tối đa số Pet, phần thưởng được chuyển thành Mảnh Pet!");
            }
        } else {
            if (player != null && player.isOnline()) {
                String traitName = (rolledTrait != null) ? rolledTrait.getLocalizedName(plugin) : "NONE";
                String winMsg = lang != null ? lang.getMessage("hatching.success", "%pet%", petDisplayName, "%rarity%", rarity.getLocalizedName(plugin), "%trait%", traitName) : null;
                player.sendMessage(winMsg != null ? winMsg : ChatColor.translateAlternateColorCodes('&',
                        "§a§lCHÚC MỪNG! §fBạn vừa ấp nở thành công Pet: " + petDisplayName +
                                " §7(Độ hiếm: " + rarity.getLocalizedName(plugin) + "§7, Đặc chất: " + traitName + "§7)"));
            }

            // Thông báo toàn server nếu mở được Pet cấp cao
            if (rarity == PetRarity.LEGENDARY || rarity == PetRarity.MYTHIC || rarity == PetRarity.SECRET || rarity == PetRarity.ETERNAL) {
                String pName = (player != null && player.isOnline()) ? player.getName() : Bukkit.getOfflinePlayer(playerUuid).getName();
                if (pName == null) pName = "Người chơi";
                String cleanPetName = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', petDisplayName));
                String bcastMsg = lang != null ? lang.getMessage("hatching.broadcast", "%player%", pName, "%rarity%", rarity.getLocalizedName(plugin), "%pet%", cleanPetName) : null;
                if (bcastMsg != null) {
                    Bukkit.broadcast(LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&', bcastMsg)));
                } else {
                    Bukkit.broadcast(LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&',
                            "&6&l[IPSECUZ PET] &eNgười chơi &f" + pName + " &evừa ấp nở thành công Pet " +
                                    rarity.getLocalizedName(plugin) + " &e" + cleanPetName + "&e!")));
                }
            }
        }

        if (player != null && player.isOnline() && showTitleAndEffects) {
            String titleText;
            String subtitleText = "&eNhận được: " + petDisplayName;

            if (rarity == PetRarity.SECRET) {
                titleText = (lang != null && !lang.getMessage("hatching.title_secret").startsWith("§cMissing"))
                        ? lang.getMessage("hatching.title_secret") : "&5&l??? HUYỀN BÍ ???";
                subtitleText = (lang != null && !lang.getMessage("hatching.subtitle_secret").startsWith("§cMissing"))
                        ? lang.getMessage("hatching.subtitle_secret", "%pet%", petDisplayName) : "&d✦ KHO BÁU BÍ MẬT: " + petDisplayName;
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.2f, 0.8f);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 1.1f);
                try {
                    player.getWorld().spawnParticle(Particle.SQUID_INK, player.getLocation().add(0, 1.2, 0), 50, 0.6, 0.6, 0.6, 0.1);
                    player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1.2, 0), 40, 0.8, 0.8, 0.8, 0.15);
                } catch (Exception ignored) {}

            } else if (rarity == PetRarity.ETERNAL) {
                titleText = (lang != null && !lang.getMessage("hatching.title_eternal").startsWith("§cMissing"))
                        ? lang.getMessage("hatching.title_eternal") : "&4&l⚔ THẦN THOẠI BẤT TỬ ⚔";
                subtitleText = (lang != null && !lang.getMessage("hatching.subtitle_eternal").startsWith("§cMissing"))
                        ? lang.getMessage("hatching.subtitle_eternal", "%pet%", petDisplayName) : "&c&l" + petDisplayName;
                player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.5f, 1f);
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1.2f);
                try {
                    player.getWorld().spawnParticle(Particle.TOTEM, player.getLocation().add(0, 1.5, 0), 60, 0.8, 0.8, 0.8, 0.2);
                    player.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, player.getLocation().add(0, 1.5, 0), 40, 0.6, 0.6, 0.6, 0.1);
                } catch (Exception ignored) {}

            } else if (rarity == PetRarity.MYTHIC) {
                titleText = (lang != null && !lang.getMessage("hatching.title_mythic").startsWith("§cMissing"))
                        ? lang.getMessage("hatching.title_mythic") : "&d&l✦ THẦN THOẠI XUẤT THẾ ✦";
                subtitleText = (lang != null && !lang.getMessage("hatching.subtitle_mythic").startsWith("§cMissing"))
                        ? lang.getMessage("hatching.subtitle_mythic", "%pet%", petDisplayName) : "&d&l" + petDisplayName;
                player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.3f, 1.1f);
                try {
                    player.getWorld().spawnParticle(Particle.TOTEM, player.getLocation().add(0, 1.5, 0), 50, 0.6, 0.6, 0.6, 0.15);
                    player.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, player.getLocation().add(0, 1.5, 0), 35, 0.5, 0.5, 0.5, 0.1);
                } catch (Exception ignored) {}

            } else if (rarity == PetRarity.LEGENDARY) {
                titleText = (lang != null && !lang.getMessage("hatching.title_legendary").startsWith("§cMissing"))
                        ? lang.getMessage("hatching.title_legendary") : "&6&l★ SIÊU PHẨM XUẤT HIỆN! ★";
                subtitleText = (lang != null && !lang.getMessage("hatching.subtitle_default").startsWith("§cMissing"))
                        ? lang.getMessage("hatching.subtitle_default", "%pet%", petDisplayName) : "&eNhận được: " + petDisplayName;
                player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.2f, 1f);
                try {
                    player.getWorld().spawnParticle(Particle.TOTEM, player.getLocation().add(0, 1.5, 0), 40, 0.5, 0.5, 0.5, 0.1);
                    player.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, player.getLocation().add(0, 1.5, 0), 30, 0.5, 0.5, 0.5, 0.1);
                } catch (Exception ignored) {}

            } else if (rarity == PetRarity.EPIC || rarity == PetRarity.RARE) {
                titleText = (lang != null && !lang.getMessage("hatching.title_rare").startsWith("§cMissing"))
                        ? lang.getMessage("hatching.title_rare") : "&d&l✦ ẤP TRỨNG THÀNH CÔNG! ✦";
                subtitleText = (lang != null && !lang.getMessage("hatching.subtitle_default").startsWith("§cMissing"))
                        ? lang.getMessage("hatching.subtitle_default", "%pet%", petDisplayName) : "&eNhận được: " + petDisplayName;
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 1f);
                try {
                    player.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, player.getLocation().add(0, 1.5, 0), 30, 0.4, 0.4, 0.4, 0.08);
                } catch (Exception ignored) {}

            } else {
                titleText = (lang != null && !lang.getMessage("hatching.title_default").startsWith("§cMissing"))
                        ? lang.getMessage("hatching.title_default") : "&a&lẤP TRỨNG THÀNH CÔNG!";
                subtitleText = (lang != null && !lang.getMessage("hatching.subtitle_default").startsWith("§cMissing"))
                        ? lang.getMessage("hatching.subtitle_default", "%pet%", petDisplayName) : "&eNhận được: " + petDisplayName;
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.2f, 1f);
                try {
                    player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, player.getLocation().add(0, 1.2, 0), 20, 0.4, 0.4, 0.4, 0.05);
                } catch (Exception ignored) {}
            }

            Component titleComp = GuiText.component(titleText);
            Component subtitleComp = GuiText.component(subtitleText);
            Title title = Title.title(
                titleComp,
                subtitleComp,
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(3000), Duration.ofMillis(600))
            );
            player.showTitle(title);
        }
    }
}
