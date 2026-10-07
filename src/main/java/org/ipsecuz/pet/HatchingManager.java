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
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }

    public static class PendingHatchSession {
        private final UUID playerUuid;
        private final String eggId;
        private final String winningPetId;
        private volatile HatchState state = HatchState.PENDING;
        private final long timestamp;

        public PendingHatchSession(UUID playerUuid, String eggId, String winningPetId) {
            this(playerUuid, eggId, winningPetId, HatchState.PENDING, System.currentTimeMillis());
        }

        public PendingHatchSession(UUID playerUuid, String eggId, String winningPetId, HatchState state, long timestamp) {
            this.playerUuid = playerUuid;
            this.eggId = eggId;
            this.winningPetId = winningPetId;
            this.state = state;
            this.timestamp = timestamp;
        }

        public UUID getPlayerUuid() { return playerUuid; }
        public String getEggId() { return eggId; }
        public String getWinningPetId() { return winningPetId; }
        public HatchState getState() { return state; }
        public void setState(HatchState state) { this.state = state; }
        public long getTimestamp() { return timestamp; }

        public synchronized boolean markCommitted() {
            if (this.state == HatchState.COMPLETED) {
                return false;
            }
            this.state = HatchState.COMPLETED;
            return true;
        }
    }

    private final Map<UUID, PendingHatchSession> pendingHatchSessions = new java.util.concurrent.ConcurrentHashMap<>();

    public HatchingManager(IpsecuzPet plugin) {
        this.plugin = plugin;
        this.eggKey = new NamespacedKey(plugin, "pet_egg_id");
        loadPendingSessions();
    }

    public void loadPendingSessions() {
        var sec = plugin.getConfigManager().getData().getConfigurationSection("pending_hatch");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                try {
                    UUID u = UUID.fromString(key);
                    String eggId = sec.getString(key + ".egg_id");
                    String winPet = sec.getString(key + ".winning_pet_id");
                    String stStr = sec.getString(key + ".state", "PENDING");
                    long time = sec.getLong(key + ".timestamp", System.currentTimeMillis());
                    HatchState st = HatchState.valueOf(stStr);
                    pendingHatchSessions.put(u, new PendingHatchSession(u, eggId, winPet, st, time));
                } catch (Exception ignored) {}
            }
        }
    }

    public void recordPendingHatch(UUID uuid, String eggId, String winningPetId) {
        PendingHatchSession session = new PendingHatchSession(uuid, eggId, winningPetId);
        pendingHatchSessions.put(uuid, session);
        String path = "pending_hatch." + uuid;
        plugin.getConfigManager().getData().set(path + ".egg_id", eggId);
        plugin.getConfigManager().getData().set(path + ".winning_pet_id", winningPetId);
        plugin.getConfigManager().getData().set(path + ".state", HatchState.PENDING.name());
        plugin.getConfigManager().getData().set(path + ".timestamp", System.currentTimeMillis());
        plugin.getConfigManager().saveData();
    }

    public void resolvePendingHatchOnJoin(Player player) {
        if (player == null) return;
        PendingHatchSession session = pendingHatchSessions.get(player.getUniqueId());
        if (session != null && (session.getState() == HatchState.PENDING || session.getState() == HatchState.FAILED)) {
            plugin.getLogger().info("§e[IpsecuzPet] Đang tự động trao thưởng ấp trứng chưa nhận cho: " + player.getName());
            completeHatchReward(player.getUniqueId(), session.getWinningPetId(), true);
        }
    }

    public void handlePlayerQuit(Player player) {
        // Giữ nguyên phiên giao dịch chưa hoàn tất để bảo toàn phần thưởng an toàn
    }

    public void saveAllPendingTransactions() {
        for (PendingHatchSession s : pendingHatchSessions.values()) {
            String path = "pending_hatch." + s.getPlayerUuid();
            plugin.getConfigManager().getData().set(path + ".egg_id", s.getEggId());
            plugin.getConfigManager().getData().set(path + ".winning_pet_id", s.getWinningPetId());
            plugin.getConfigManager().getData().set(path + ".state", s.getState().name());
            plugin.getConfigManager().getData().set(path + ".timestamp", s.getTimestamp());
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

        String name = sec.getString("name", "&eTrứng Pet");
        meta.displayName(LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&', name)));

        List<Component> lore = new ArrayList<>();
        for (String line : sec.getStringList("lore")) {
            lore.add(LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&', line)));
        }

        if (sec.contains("requirements") || sec.contains("all") || sec.contains("one_of") || sec.contains("price") || sec.contains("cost_money")) {
            org.ipsecuz.pet.requirement.RequirementGroup group = plugin.getRequirementManager().parse(sec);
            if (!group.getRequirements().isEmpty()) {
                lore.add(Component.text(" "));
                lore.add(LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&', "&6✦ Chi phí để quay:")));
                org.ipsecuz.pet.requirement.RequirementContext ctx = new org.ipsecuz.pet.requirement.RequirementContext(null, plugin);
                org.ipsecuz.pet.requirement.RequirementCheckResult result = plugin.getRequirementManager().evaluate(group, ctx);
                for (String line : org.ipsecuz.pet.requirement.RequirementGuiRenderer.renderToStrings(result)) {
                    if (!line.contains("ĐỦ ĐIỀU KIỆN") && !line.contains("CHƯA ĐỦ ĐIỀU KIỆN") && !line.startsWith("§7----")) {
                        lore.add(Component.text(line));
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
        if (!plugin.getModuleManager().isHatchingEnabled()) {
            player.sendMessage("§cTính năng Ấp Trứng & Quay Pet hiện đang bị tắt bởi máy chủ!");
            return;
        }

        FileConfiguration config = plugin.getModuleManager().getHatchingConfig();
        String title = config.getString("gui.title", "&1✦ Lò Ấp Trứng Thú Cưng ✦");
        int size = config.getInt("gui.size", 45);

        HatchMenuHolder holder = new HatchMenuHolder();
        Inventory inv = Bukkit.createInventory(holder, size, LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&', title)));
        holder.setInventory(inv);

        ItemStack glass = new ItemStack(Material.CYAN_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.displayName(Component.text(" "));
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
                        lore.add(Component.text(" "));
                        lore.add(LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&', "&a▶ Nhấp để Quay Gacha & Ấp Trứng!")));
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
            infoMeta.displayName(LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&', "&e&lHƯỚNG DẪN ẤP TRỨNG & QUAY PET")));
            List<Component> infoLore = new ArrayList<>();
            infoLore.add(Component.text("§7- Nhấp chuột phải vào quả trứng trên tay"));
            infoLore.add(Component.text("§7  hoặc nhấp trực tiếp vào biểu tượng trên GUI."));
            infoLore.add(Component.text("§7- Vòng quay Roulette sống động với hiệu ứng âm thanh!"));
            infoLore.add(Component.text("§7- Pet dừng lại ở ô giữa sẽ thuộc về bạn!"));
            infoLore.add(Component.text("§7- Nếu quay trùng pet: nhận Mảnh Pet & EXP thưởng!"));
            infoMeta.lore(infoLore);
            info.setItemMeta(infoMeta);
        }
        inv.setItem(size - 5, info);

        player.openInventory(inv);
    }

    public boolean checkAndDeductRequirements(Player player, ConfigurationSection eggSec, ItemStack consumedItem) {
        org.ipsecuz.pet.requirement.RequirementGroup group = plugin.getRequirementManager().parse(eggSec);
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
        if (!plugin.getModuleManager().isHatchingEnabled()) {
            player.sendMessage("§cTính năng Ấp Trứng & Quay Pet hiện đang bị tắt bởi máy chủ!");
            return;
        }

        FileConfiguration config = plugin.getModuleManager().getHatchingConfig();
        ConfigurationSection eggSec = config.getConfigurationSection("eggs." + eggId);
        if (eggSec == null) {
            player.sendMessage("§cKhông tìm thấy dữ liệu cho loại trứng này!");
            return;
        }

        ConfigurationSection lootTable = eggSec.getConfigurationSection("loot_table");
        if (lootTable == null || lootTable.getKeys(false).isEmpty()) {
            player.sendMessage("§cTrứng này chưa được thiết lập danh sách pet có thể nở!");
            return;
        }

        // 1. Thu thập tỷ lệ loot table
        int totalWeight = 0;
        Map<String, Integer> weights = new HashMap<>();
        List<String> candidatePetIds = new ArrayList<>();
        for (String pId : lootTable.getKeys(false)) {
            int w = lootTable.getInt(pId, 1);
            weights.put(pId, w);
            totalWeight += w;
            candidatePetIds.add(pId);
        }

        if (totalWeight <= 0 || candidatePetIds.isEmpty()) {
            player.sendMessage("§cTrứng này chưa có tỉ lệ rớt hợp lệ!");
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

        // 3. Khấu trừ chi phí giao dịch sau khi mọi điều kiện hợp lệ
        if (!checkAndDeductRequirements(player, eggSec, consumedItem)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // 4. Bắt đầu vòng quay Roulette với phiên theo dõi an toàn
        recordPendingHatch(player.getUniqueId(), eggId, winningPetId);
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
        String iconMat = plugin.getConfig().getString("pets." + petId + ".icon", "STONE");
        ItemStack item = plugin.getItemHookManager().getItem(iconMat, Material.STONE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = plugin.getConfig().getString("pets." + petId + ".name", petId);
            PetRarity rarity = PetRarity.fromPetId(plugin, petId);
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize(
                    ChatColor.translateAlternateColorCodes('&', (isWinner ? "&6&l★ " : "&f") + name)));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§7Mã Pet: §e" + petId));
            lore.add(Component.text("§7Độ hiếm: " + rarity.getFormattedName()));
            if (isWinner) {
                lore.add(Component.text("§a§l✔ PHẦN THƯỞNG CỦA BẠN!"));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void updateRouletteBorders(Inventory inv, int step) {
        Material glassMat = (step % 2 == 0) ? Material.YELLOW_STAINED_GLASS_PANE : Material.ORANGE_STAINED_GLASS_PANE;
        ItemStack glass = new ItemStack(glassMat);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            glass.setItemMeta(meta);
        }

        for (int i = 0; i < 9; i++) {
            if (i == 4) {
                ItemStack pointer = new ItemStack(Material.HOPPER);
                ItemMeta pMeta = pointer.getItemMeta();
                if (pMeta != null) {
                    pMeta.displayName(LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&', "&e▼ &6&lÔ NHẬN THƯỞNG &e▼")));
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
                    pMeta.displayName(LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&', "&e▲ &6&lÔ NHẬN THƯỞNG &e▲")));
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
        if (session != null) {
            if (!session.markCommitted()) return; // Đã commit trước đó, chặn duplicate reward tuyệt đối!
            pendingHatchSessions.remove(playerUuid);
        }
        plugin.getConfigManager().getData().set("pending_hatch." + playerUuid, null);
        plugin.getConfigManager().forceSave();

        Player player = Bukkit.getPlayer(playerUuid);
        String petDisplayName = plugin.getConfig().getString("pets." + winningPetId + ".name", winningPetId);
        PetRarity rarity = PetRarity.fromPetId(plugin, winningPetId);

        if (plugin.getConfigManager().getData().contains(playerUuid + ".pets." + winningPetId)) {
            // ĐÃ SỞ HỮU TRƯỚC ĐÓ -> CHUYỂN ĐỔI THÀNH MẢNH SHARDS & EXP
            plugin.getShardManager().processDuplicateReward(playerUuid, winningPetId);
            if (player != null && player.isOnline()) {
                player.sendMessage("§e[IpsecuzPet] Bạn đã sở hữu Pet này! Đã tự động quy đổi thành Mảnh Pet và Kinh Nghiệm.");
            }
        } else if (!plugin.getOwnershipManager().canAcquirePet(playerUuid)) {
            // ĐÃ ĐẦY KHO PET TẠI THỜI ĐIỂM COMMIT -> CHUYỂN ĐỔI AN TOÀN SANG MẢNH SHARDS & EXP
            plugin.getShardManager().processDuplicateReward(playerUuid, winningPetId);
            if (player != null && player.isOnline()) {
                player.sendMessage("§e[Kho Thú Cưng Đã Đầy] Bạn đã đạt giới hạn tối đa số Pet, phần thưởng được chuyển thành Mảnh Pet!");
            }
        } else {
            // PET MỚI -> TẠO DỮ LIỆU, ROLL TRAIT VÀ LƯU CODEX
            plugin.getConfigManager().createPetDataIfMissing(playerUuid, winningPetId);
            PetTrait trait = PetTrait.rollRandomTrait();
            plugin.getConfigManager().getData().set(playerUuid + ".pets." + winningPetId + ".trait", trait.name());
            plugin.getConfigManager().forceSave();

            plugin.getCodexManager().discover(playerUuid, winningPetId);

            if (player != null && player.isOnline()) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "§a§lCHÚC MỪNG! §fBạn vừa ấp nở thành công Pet: " + petDisplayName +
                                " §7(Độ hiếm: " + rarity.getFormattedName() + "§7, Đặc chất: " + trait.getFormattedName() + "§7)"));
            }

            // Thông báo toàn server nếu mở được Pet cấp cao
            if (rarity == PetRarity.LEGENDARY || rarity == PetRarity.MYTHIC || rarity == PetRarity.SECRET || rarity == PetRarity.ETERNAL) {
                String pName = (player != null && player.isOnline()) ? player.getName() : Bukkit.getOfflinePlayer(playerUuid).getName();
                if (pName == null) pName = "Người chơi";
                String cleanPetName = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', petDisplayName));
                Bukkit.broadcast(LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&',
                        "&6&l[IPSECUZ PET] &eNgười chơi &f" + pName + " &evừa ấp nở thành công Pet " +
                                rarity.getFormattedName() + " &e" + cleanPetName + "&e!")));
            }
        }

        if (player != null && player.isOnline() && showTitleAndEffects) {
            String titleText;
            String subtitleText = "&eNhận được: " + petDisplayName;

            if (rarity == PetRarity.SECRET) {
                titleText = "&5&l??? HUYỀN BÍ ???";
                subtitleText = "&d✦ KHO BÁU BÍ MẬT: " + petDisplayName;
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.2f, 0.8f);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 1.1f);
                try {
                    player.getWorld().spawnParticle(Particle.SQUID_INK, player.getLocation().add(0, 1.2, 0), 50, 0.6, 0.6, 0.6, 0.1);
                    player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1.2, 0), 40, 0.8, 0.8, 0.8, 0.15);
                } catch (Exception ignored) {}

            } else if (rarity == PetRarity.ETERNAL) {
                titleText = "&4&l⚔ THẦN THOẠI BẤT TỬ ⚔";
                subtitleText = "&c&l" + petDisplayName;
                player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.5f, 1f);
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1.2f);
                try {
                    player.getWorld().spawnParticle(Particle.TOTEM, player.getLocation().add(0, 1.5, 0), 60, 0.8, 0.8, 0.8, 0.2);
                    player.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, player.getLocation().add(0, 1.5, 0), 40, 0.6, 0.6, 0.6, 0.1);
                } catch (Exception ignored) {}

            } else if (rarity == PetRarity.MYTHIC || rarity == PetRarity.LEGENDARY) {
                titleText = "&6&l★ SIÊU PHẨM XUẤT HIỆN! ★";
                player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.2f, 1f);
                try {
                    player.getWorld().spawnParticle(Particle.TOTEM, player.getLocation().add(0, 1.5, 0), 40, 0.5, 0.5, 0.5, 0.1);
                    player.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, player.getLocation().add(0, 1.5, 0), 30, 0.5, 0.5, 0.5, 0.1);
                } catch (Exception ignored) {}

            } else if (rarity == PetRarity.EPIC || rarity == PetRarity.RARE) {
                titleText = "&d&l✦ ẤP TRỨNG THÀNH CÔNG! ✦";
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 1f);
                try {
                    player.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, player.getLocation().add(0, 1.5, 0), 30, 0.4, 0.4, 0.4, 0.08);
                } catch (Exception ignored) {}

            } else {
                titleText = "&a&lẤP TRỨNG THÀNH CÔNG!";
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.2f, 1f);
                try {
                    player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, player.getLocation().add(0, 1.2, 0), 20, 0.4, 0.4, 0.4, 0.05);
                } catch (Exception ignored) {}
            }

            Component titleComp = LegacyComponentSerializer.legacySection().deserialize(
                    ChatColor.translateAlternateColorCodes('&', titleText)
            );
            Component subtitleComp = LegacyComponentSerializer.legacySection().deserialize(
                    ChatColor.translateAlternateColorCodes('&', subtitleText)
            );
            Title title = Title.title(
                titleComp,
                subtitleComp,
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(3000), Duration.ofMillis(600))
            );
            player.showTitle(title);
        }
    }
}
