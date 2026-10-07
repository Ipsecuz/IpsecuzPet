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

    public HatchingManager(IpsecuzPet plugin) {
        this.plugin = plugin;
        this.eggKey = new NamespacedKey(plugin, "pet_egg_id");
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

        double reqMoney = sec.getDouble("requirements.money", sec.getString("currency", "").equalsIgnoreCase("MONEY") ? sec.getDouble("price", 0) : 0);
        int reqPoints = sec.getInt("requirements.points", sec.getString("currency", "").equalsIgnoreCase("POINTS") ? sec.getInt("price", 0) : 0);
        List<String> reqItems = sec.getStringList("requirements.items");

        if (reqMoney > 0 || reqPoints > 0 || !reqItems.isEmpty()) {
            lore.add(Component.text(" "));
            lore.add(LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&', "&6✦ Chi phí để quay:")));
            if (reqMoney > 0) lore.add(LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&', " &7- Tiền: &a$" + (long)reqMoney)));
            if (reqPoints > 0) lore.add(LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&', " &7- Points: &b" + reqPoints + " P")));
            for (String reqItm : reqItems) {
                lore.add(LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&', " &7- Vật phẩm: &e" + reqItm)));
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
        double reqMoney = 0;
        if (eggSec.contains("requirements.money")) {
            reqMoney = eggSec.getDouble("requirements.money");
        } else if ("MONEY".equalsIgnoreCase(eggSec.getString("currency")) && eggSec.contains("price")) {
            reqMoney = eggSec.getDouble("price");
        }

        if (reqMoney > 0 && !plugin.getCurrencyManager().hasMoney(player, reqMoney)) {
            player.sendMessage("§cBạn không đủ tiền để ấp trứng! Cần: §e$" + (long)reqMoney);
            return false;
        }

        int reqPoints = 0;
        if (eggSec.contains("requirements.points")) {
            reqPoints = eggSec.getInt("requirements.points");
        } else if ("POINTS".equalsIgnoreCase(eggSec.getString("currency")) && eggSec.contains("price")) {
            reqPoints = eggSec.getInt("price");
        }

        if (reqPoints > 0 && !plugin.getCurrencyManager().hasPoints(player, reqPoints)) {
            player.sendMessage("§cBạn không đủ Points để ấp trứng! Cần: §b" + reqPoints + " Points");
            return false;
        }

        List<String> reqItems = eggSec.getStringList("requirements.items");
        if (reqItems.isEmpty() && "ITEM".equalsIgnoreCase(eggSec.getString("currency")) && eggSec.contains("material")) {
            String mat = eggSec.getString("material", "DIAMOND");
            int amt = eggSec.getInt("price", 1);
            reqItems = Collections.singletonList(mat + ":" + amt);
        }

        for (String itemStr : reqItems) {
            String[] parts = itemStr.split(":");
            String id = parts[0];
            int amt = 1;
            if (parts.length > 1) {
                try { amt = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) {}
            }
            if (!plugin.getItemHookManager().hasItem(player, id, amt)) {
                player.sendMessage("§cBạn không đủ vật phẩm để ấp trứng! Cần: §e" + amt + "x " + plugin.getItemHookManager().getItemDisplayName(id));
                return false;
            }
        }

        if (reqMoney > 0) plugin.getCurrencyManager().withdrawMoney(player, reqMoney);
        if (reqPoints > 0) plugin.getCurrencyManager().withdrawPoints(player, reqPoints);
        for (String itemStr : reqItems) {
            String[] parts = itemStr.split(":");
            String id = parts[0];
            int amt = 1;
            if (parts.length > 1) {
                try { amt = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) {}
            }
            plugin.getItemHookManager().takeItem(player, id, amt);
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

        if (!plugin.getOwnershipManager().canAcquirePet(player)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        if (!checkAndDeductRequirements(player, eggSec, consumedItem)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        startGachaRoulette(player, eggId, eggSec);
    }

    private void startGachaRoulette(Player player, String eggId, ConfigurationSection eggSec) {
        ConfigurationSection lootTable = eggSec.getConfigurationSection("loot_table");
        if (lootTable == null || lootTable.getKeys(false).isEmpty()) {
            player.sendMessage("§cTrứng này chưa được thiết lập danh sách pet có thể nở!");
            return;
        }

        int totalWeight = 0;
        Map<String, Integer> weights = new HashMap<>();
        List<String> candidatePetIds = new ArrayList<>();
        for (String pId : lootTable.getKeys(false)) {
            int w = lootTable.getInt(pId, 1);
            weights.put(pId, w);
            totalWeight += w;
            candidatePetIds.add(pId);
        }

        if (totalWeight <= 0) return;
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

        int totalSteps = 32;
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
        long delayTicks;
        if (currentStep < 20) {
            delayTicks = 2L;
        } else if (currentStep < 26) {
            delayTicks = 3L;
        } else if (currentStep < 30) {
            delayTicks = 5L;
        } else {
            delayTicks = 7L;
        }

        SchedulerUtils.runEntityTaskLater(plugin, player, () -> {
            if (!player.isOnline()) {
                if (!holder.isFinished()) {
                    holder.setFinished(true);
                    completeHatchReward(player, winningPetId, false);
                }
                return;
            }

            int step = currentStep + 1;

            for (int i = 0; i < 9; i++) {
                inv.setItem(9 + i, items.get(step + i));
            }
            updateRouletteBorders(inv, step);

            float pitch = 0.8f + ((float) step / totalSteps) * 0.8f;
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, pitch);

            if (step < totalSteps) {
                scheduleRouletteStep(player, inv, holder, items, step, totalSteps, winningPetId);
            } else {
                holder.setFinished(true);
                finishRoulette(player, inv, winningPetId);
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
        String petDisplayName = plugin.getConfig().getString("pets." + winningPetId + ".name", winningPetId);
        PetRarity rarity = PetRarity.fromPetId(plugin, winningPetId);

        if (plugin.getConfigManager().getData().contains(player.getUniqueId() + ".pets." + winningPetId)) {
            // ĐÃ SỞ HỮU TRƯỚC ĐÓ -> CHUYỂN ĐỔI THÀNH MẢNH SHARDS & EXP
            plugin.getShardManager().convertDuplicateToShards(player, winningPetId);
        } else {
            // PET MỚI -> TẠO DỮ LIỆU, ROLL TRAIT VÀ LƯU CODEX
            plugin.getConfigManager().createPetDataIfMissing(player.getUniqueId(), winningPetId);
            PetTrait trait = PetTrait.rollRandomTrait();
            plugin.getConfigManager().getData().set(player.getUniqueId() + ".pets." + winningPetId + ".trait", trait.name());
            plugin.getConfigManager().saveData();

            plugin.getCodexManager().discover(player.getUniqueId(), winningPetId);

            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "§a§lCHÚC MỪNG! §fBạn vừa ấp nở thành công Pet: " + petDisplayName +
                            " §7(Độ hiếm: " + rarity.getFormattedName() + "§7, Đặc chất: " + trait.getFormattedName() + "§7)"));

            // Thông báo toàn server nếu mở được Pet cấp cao
            if (rarity == PetRarity.LEGENDARY || rarity == PetRarity.MYTHIC || rarity == PetRarity.SECRET || rarity == PetRarity.ETERNAL) {
                String cleanPetName = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', petDisplayName));
                Bukkit.broadcast(LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&',
                        "&6&l[IPSECUZ PET] &eNgười chơi &f" + player.getName() + " &evừa ấp nở thành công Pet " +
                                rarity.getFormattedName() + " &e" + cleanPetName + "&e!")));
            }
        }

        if (showTitleAndEffects) {
            player.playSound(player.getLocation(), rarity.getRevealSound(), 1.5f, 1f);
            try {
                player.getWorld().spawnParticle(rarity.getRevealParticle(), player.getLocation().add(0, 1.5, 0), 40, 0.5, 0.5, 0.5, 0.1);
            } catch (Exception ignored) {}

            Component titleComp = LegacyComponentSerializer.legacySection().deserialize(
                    ChatColor.translateAlternateColorCodes('&', "&6&lẤP TRỨNG THÀNH CÔNG!")
            );
            Component subtitleComp = LegacyComponentSerializer.legacySection().deserialize(
                    ChatColor.translateAlternateColorCodes('&', "&eBạn nhận được: " + petDisplayName)
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
