package org.ipsecuz.pet.dialog;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.ipsecuz.pet.GuiText;
import org.ipsecuz.pet.IpsecuzPet;
import org.ipsecuz.pet.LanguageManager;
import org.ipsecuz.pet.PetRarity;
import org.ipsecuz.pet.PetStatEngine;

import java.util.*;
import java.util.function.Consumer;

/**
 * Universal Chest GUI fallback provider.
 * Guarantees 100% feature availability and parity on any Minecraft server/client version.
 */
public class ChestGuiFallback implements DialogProvider {

    private final IpsecuzPet plugin;

    // --- Custom InventoryHolders ---

    public static class PetHubHolder implements InventoryHolder {
        private Inventory inventory;
        private final DialogSession session;
        public PetHubHolder(DialogSession session) { this.session = session; }
        @Override public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        public DialogSession getSession() { return session; }
    }

    public static class PetGuideHolder implements InventoryHolder {
        private Inventory inventory;
        private final DialogSession session;
        private final int chapterIndex;
        public PetGuideHolder(DialogSession session, int chapterIndex) {
            this.session = session;
            this.chapterIndex = chapterIndex;
        }
        @Override public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        public DialogSession getSession() { return session; }
        public int getChapterIndex() { return chapterIndex; }
    }

    public static class PetSettingsHolder implements InventoryHolder {
        private Inventory inventory;
        private final DialogSession session;
        public PetSettingsHolder(DialogSession session) { this.session = session; }
        @Override public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        public DialogSession getSession() { return session; }
    }

    public static class PetEvolutionConfirmHolder implements InventoryHolder {
        private Inventory inventory;
        private final DialogSession session;
        private final String petId;
        public PetEvolutionConfirmHolder(DialogSession session, String petId) {
            this.session = session;
            this.petId = petId;
        }
        @Override public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        public DialogSession getSession() { return session; }
        public String getPetId() { return petId; }
    }

    public static class PetShopConfirmHolder implements InventoryHolder {
        private Inventory inventory;
        private final DialogSession session;
        private final String petId;
        public PetShopConfirmHolder(DialogSession session, String petId) {
            this.session = session;
            this.petId = petId;
        }
        @Override public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        public DialogSession getSession() { return session; }
        public String getPetId() { return petId; }
    }

    public static class PetHatchConfirmHolder implements InventoryHolder {
        private Inventory inventory;
        private final DialogSession session;
        private final String eggId;
        public PetHatchConfirmHolder(DialogSession session, String eggId) {
            this.session = session;
            this.eggId = eggId;
        }
        @Override public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        public DialogSession getSession() { return session; }
        public String getEggId() { return eggId; }
    }

    public static class PetTradeInviteHolder implements InventoryHolder {
        private Inventory inventory;
        private final DialogSession session;
        private final UUID senderId;
        public PetTradeInviteHolder(DialogSession session, UUID senderId) {
            this.session = session;
            this.senderId = senderId;
        }
        @Override public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        public DialogSession getSession() { return session; }
        public UUID getSenderId() { return senderId; }
    }

    public static class PetTradeConfirmHolder implements InventoryHolder {
        private Inventory inventory;
        private final DialogSession session;
        private final UUID tradeId;
        private final Consumer<Boolean> callback;
        public PetTradeConfirmHolder(DialogSession session, UUID tradeId, Consumer<Boolean> callback) {
            this.session = session;
            this.tradeId = tradeId;
            this.callback = callback;
        }
        @Override public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        public DialogSession getSession() { return session; }
        public UUID getTradeId() { return tradeId; }
        public Consumer<Boolean> getCallback() { return callback; }
    }

    public static class PetNoticeHolder implements InventoryHolder {
        private Inventory inventory;
        private final Runnable onClose;
        public PetNoticeHolder(Runnable onClose) { this.onClose = onClose; }
        @Override public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        public Runnable getOnClose() { return onClose; }
    }

    public static class PetConfirmHolder implements InventoryHolder {
        private Inventory inventory;
        private final Consumer<Boolean> callback;
        public PetConfirmHolder(Consumer<Boolean> callback) { this.callback = callback; }
        @Override public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        public Consumer<Boolean> getCallback() { return callback; }
    }

    public ChestGuiFallback(IpsecuzPet plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "CHEST";
    }

    @Override
    public boolean isSupported(Player player) {
        return true;
    }

    @Override
    public void openNotice(Player player, String title, String message, Runnable onClose) {
        PetNoticeHolder holder = new PetNoticeHolder(onClose);
        Component titleComp = LegacyComponentSerializer.legacyAmpersand().deserialize(title);
        Inventory inv = Bukkit.createInventory(holder, 27, titleComp);
        holder.setInventory(inv);

        fillBorders(inv, Material.GRAY_STAINED_GLASS_PANE);

        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(titleComp);
            List<Component> lore = new ArrayList<>();
            for (String line : message.split("\n")) {
                lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&7" + line));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        inv.setItem(13, item);

        ItemStack ok = createBtn(Material.LIME_DYE, "&a✓ OK", List.of("&7Nhấp để đóng thông báo"));
        inv.setItem(22, ok);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
    }

    @Override
    public void openConfirmation(Player player, String title, String message, String confirmText, String cancelText, Consumer<Boolean> callback) {
        PetConfirmHolder holder = new PetConfirmHolder(callback);
        Component titleComp = LegacyComponentSerializer.legacyAmpersand().deserialize(title);
        Inventory inv = Bukkit.createInventory(holder, 27, titleComp);
        holder.setInventory(inv);

        fillBorders(inv, Material.GRAY_STAINED_GLASS_PANE);

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta meta = info.getItemMeta();
        if (meta != null) {
            meta.displayName(titleComp);
            List<Component> lore = new ArrayList<>();
            for (String line : message.split("\n")) {
                lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&7" + line));
            }
            meta.lore(lore);
            info.setItemMeta(meta);
        }
        inv.setItem(13, info);

        ItemStack btnConfirm = createBtn(Material.EMERALD_BLOCK, confirmText != null ? confirmText : "&a✓ Xác nhận", List.of("&7Nhấp để đồng ý"));
        ItemStack btnCancel = createBtn(Material.REDSTONE_BLOCK, cancelText != null ? cancelText : "&c✘ Hủy bỏ", List.of("&7Nhấp để từ chối"));

        inv.setItem(11, btnConfirm);
        inv.setItem(15, btnCancel);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
    }

    @Override
    public void openPetHub(Player player, DialogSession session) {
        LanguageManager lang = plugin.getLanguage();
        PetHubHolder holder = new PetHubHolder(session);
        String rawTitle = lang.getMessage("dialog.hub_title");
        if (rawTitle == null || rawTitle.isEmpty()) rawTitle = "&8&lIPSECUZPET &7- &6Pet Hub";
        Component titleComp = LegacyComponentSerializer.legacyAmpersand().deserialize(rawTitle);
        Inventory inv = Bukkit.createInventory(holder, 45, titleComp);
        holder.setInventory(inv);

        fillBorders(inv, Material.BLACK_STAINED_GLASS_PANE);

        // Center: Active Pet or Placeholder
        String activePetId = plugin.getPetManager().getActivePetId(player.getUniqueId());
        int maxPets = plugin.getConfig().getInt("max_pets", 2);
        List<String> ownedList = plugin.getOwnershipManager().getOwnedPetIds(player.getUniqueId());
        int ownedCount = ownedList.size();

        if (activePetId != null) {
            String petName = plugin.getConfig().getString("pets." + activePetId + ".name", activePetId);
            String customName = plugin.getConfigManager().getCustomName(player.getUniqueId(), activePetId);
            if (customName != null && !customName.isEmpty()) petName = customName;
            int level = plugin.getConfigManager().getData().getInt(player.getUniqueId() + ".pets." + activePetId + ".level", 1);
            int stars = plugin.getConfigManager().getData().getInt(player.getUniqueId() + ".pets." + activePetId + ".stars", 1);
            int happiness = plugin.getConfigManager().getData().getInt(player.getUniqueId() + ".pets." + activePetId + ".happiness", 100);
            String rarityStr = plugin.getConfig().getString("pets." + activePetId + ".rarity", "COMMON");
            PetRarity rarity;
            try {
                rarity = PetRarity.valueOf(rarityStr.toUpperCase());
            } catch (Exception e) {
                rarity = PetRarity.COMMON;
            }

            ItemStack petItem = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta pMeta = petItem.getItemMeta();
            if (pMeta != null) {
                pMeta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize("&a&l" + petName));
                List<Component> lore = new ArrayList<>();
                lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&7" + lang.getMessage("dialog.hub_active_pet")));
                lore.add(Component.empty());
                lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&e" + lang.getMessage("gui.lore_level") + ": &f" + level));
                lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&6" + lang.getMessage("gui.lore_stars") + ": &f" + stars + " ⭐"));
                lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&d" + lang.getMessage("gui.lore_rarity") + ": " + rarity.getChatColor() + rarity.getLocalizedName(plugin)));
                lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&c" + lang.getMessage("gui.lore_happiness") + ": &f" + happiness + "%"));
                lore.add(Component.empty());
                lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&7" + lang.getMessage("dialog.hub_owned_pets", "%count%", String.valueOf(ownedCount), "%max%", String.valueOf(maxPets))));
                pMeta.lore(lore);
                petItem.setItemMeta(pMeta);
            }
            inv.setItem(22, petItem);
        } else {
            ItemStack noPet = createBtn(Material.LEAD, "&7" + lang.getMessage("dialog.hub_no_active_pet"),
                    List.of("&7" + lang.getMessage("dialog.hub_owned_pets", "%count%", String.valueOf(ownedCount), "%max%", String.valueOf(maxPets)),
                            "",
                            "&eNhấp [Bộ Sưu Tập] để triệu hồi một bạn thú cưng!"));
            inv.setItem(22, noPet);
        }

        // Action Buttons
        inv.setItem(10, createBtn(Material.SADDLE, lang.getMessage("dialog.btn_my_pets"), List.of("&7Xem danh sách và triệu hồi pet đang sở hữu")));
        inv.setItem(12, createBtn(Material.BOOK, lang.getMessage("dialog.btn_details"), List.of("&7Xem chỉ số chi tiết, kỹ năng và tiến hóa")));
        inv.setItem(14, createBtn(Material.EMERALD, lang.getMessage("dialog.btn_shop"), List.of("&7Mua thú cưng mới bằng Coin hoặc Points")));
        inv.setItem(16, createBtn(Material.DRAGON_EGG, lang.getMessage("dialog.btn_hatch"), List.of("&7Mở Lò Ấp Trứng và quay Gacha Roulette")));

        inv.setItem(28, createBtn(Material.ENCHANTED_BOOK, lang.getMessage("dialog.btn_codex"), List.of("&7Bách khoa toàn thư khám phá thế giới pet")));
        inv.setItem(30, createBtn(Material.WRITTEN_BOOK, lang.getMessage("dialog.btn_guide"), List.of("&7Cẩm nang A-Z toàn bộ tính năng IpsecuzPet")));
        inv.setItem(32, createBtn(Material.REPEATER, lang.getMessage("dialog.btn_settings"), List.of("&7Tùy chỉnh hiệu ứng, âm thanh và giao diện")));
        inv.setItem(34, createBtn(Material.AMETHYST_SHARD, lang.getMessage("gui.btn_shards"), List.of("&7Xem và ghép các mảnh thú cưng")));

        inv.setItem(40, createBtn(Material.BARRIER, "&c✘ Đóng", List.of("&7Nhấp để đóng menu")));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
    }

    @Override
    public void openPetDetails(Player player, DialogSession session, String petId) {
        if (petId == null) {
            petId = plugin.getPetManager().getActivePetId(player.getUniqueId());
        }
        if (petId == null) {
            player.sendMessage(plugin.getLanguage().getMessage("pet.no_pet"));
            return;
        }
        org.ipsecuz.pet.GuiListener.openPetDetailMenu(player, petId);
    }

    @Override
    public void openRenameDialog(Player player, DialogSession session, String petId) {
        // Fallback: starts rename session with chat capture
        plugin.getRenameSessionManager().startSession(player, petId);
    }

    @Override
    public void openEvolutionDialog(Player player, DialogSession session, String petId) {
        LanguageManager lang = plugin.getLanguage();
        PetEvolutionConfirmHolder holder = new PetEvolutionConfirmHolder(session, petId);
        String rawTitle = lang.getMessage("dialog.evolution_title");
        if (rawTitle == null) rawTitle = "&8&lTIẾN HÓA &7- &6" + petId;
        Component titleComp = LegacyComponentSerializer.legacyAmpersand().deserialize(rawTitle);
        Inventory inv = Bukkit.createInventory(holder, 27, titleComp);
        holder.setInventory(inv);

        fillBorders(inv, Material.ORANGE_STAINED_GLASS_PANE);

        int currentStars = plugin.getConfigManager().getData().getInt(player.getUniqueId() + ".pets." + petId + ".stars", 1);
        int nextStars = Math.min(5, currentStars + 1);

        double curDmg = PetStatEngine.calculateStatPreview(plugin, petId, 1, currentStars, "damage");
        double nextDmg = PetStatEngine.calculateStatPreview(plugin, petId, 1, nextStars, "damage");
        double curHp = PetStatEngine.calculateStatPreview(plugin, petId, 1, currentStars, "health");
        double nextHp = PetStatEngine.calculateStatPreview(plugin, petId, 1, nextStars, "health");

        ItemStack center = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = center.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize("&6&l★ TIẾN HÓA SAO: " + petId));
            List<Component> lore = new ArrayList<>();
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&7" + lang.getMessage("dialog.evo_current_stars") + ": &e" + currentStars + " ⭐ &7➔ &6" + nextStars + " ⭐"));
            lore.add(Component.empty());
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&c⚔ Sát thương: &f" + String.format("%.1f", curDmg) + " &7➔ &a" + String.format("%.1f", nextDmg)));
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&a❤ Máu tối đa: &f" + String.format("%.1f", curHp) + " &7➔ &a" + String.format("%.1f", nextHp)));
            lore.add(Component.empty());

            // Requirements from RequirementManager
            FileConfiguration evoCfg = plugin.getModuleManager().getEvolutionConfig();
            ConfigurationSection reqSec = evoCfg != null ? evoCfg.getConfigurationSection("requirements.star_" + nextStars) : null;
            if (reqSec != null) {
                var group = plugin.getRequirementManager().parse(reqSec);
                var ctx = new org.ipsecuz.pet.requirement.RequirementContext(player, petId, 1, currentStars, plugin);
                var eval = plugin.getRequirementManager().evaluate(group, ctx);
                lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&6✦ Yêu cầu nâng cấp:"));
                lore.addAll(org.ipsecuz.pet.requirement.RequirementGuiRenderer.renderToComponents(eval));
            } else {
                lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&a✓ " + lang.getMessage("gui.evo_free")));
            }
            meta.lore(lore);
            center.setItemMeta(meta);
        }
        inv.setItem(13, center);

        inv.setItem(11, createBtn(Material.EMERALD_BLOCK, lang.getMessage("dialog.evo_confirm"), List.of("&7Nhấp để xác nhận tiến hóa")));
        inv.setItem(15, createBtn(Material.REDSTONE_BLOCK, lang.getMessage("dialog.evo_cancel"), List.of("&7Nhấp để hủy bỏ")));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
    }

    @Override
    public void openShopConfirmDialog(Player player, DialogSession session, String petId) {
        LanguageManager lang = plugin.getLanguage();
        PetShopConfirmHolder holder = new PetShopConfirmHolder(session, petId);
        String rawTitle = lang.getMessage("dialog.shop_confirm_title");
        if (rawTitle == null) rawTitle = "&8&lMUA PET &7- &6" + petId;
        Component titleComp = LegacyComponentSerializer.legacyAmpersand().deserialize(rawTitle);
        Inventory inv = Bukkit.createInventory(holder, 27, titleComp);
        holder.setInventory(inv);

        fillBorders(inv, Material.LIME_STAINED_GLASS_PANE);

        double cost = plugin.getConfig().getDouble("pets." + petId + ".cost", 0.0);
        String curr = plugin.getConfig().getString("pets." + petId + ".currency", "VAULT");

        ItemStack preview = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = preview.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize("&e&lMUA: " + petId));
            List<Component> lore = new ArrayList<>();
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&7" + lang.getMessage("dialog.shop_confirm_desc")));
            lore.add(Component.empty());
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&6Giá bán: &f" + cost + " &e(" + curr + ")"));
            meta.lore(lore);
            preview.setItemMeta(meta);
        }
        inv.setItem(13, preview);

        inv.setItem(11, createBtn(Material.EMERALD_BLOCK, lang.getMessage("dialog.shop_confirm_buy"), List.of("&7Nhấp để xác nhận mua")));
        inv.setItem(15, createBtn(Material.REDSTONE_BLOCK, lang.getMessage("dialog.shop_confirm_cancel"), List.of("&7Nhấp để hủy")));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
    }

    @Override
    public void openHatchConfirmDialog(Player player, DialogSession session, String eggId) {
        LanguageManager lang = plugin.getLanguage();
        PetHatchConfirmHolder holder = new PetHatchConfirmHolder(session, eggId);
        String rawTitle = lang.getMessage("dialog.hatch_title");
        if (rawTitle == null) rawTitle = "&8&lẤP TRỨNG &7- &6" + eggId;
        Component titleComp = LegacyComponentSerializer.legacyAmpersand().deserialize(rawTitle);
        Inventory inv = Bukkit.createInventory(holder, 27, titleComp);
        holder.setInventory(inv);

        fillBorders(inv, Material.YELLOW_STAINED_GLASS_PANE);

        ItemStack eggItem = new ItemStack(Material.EGG);
        ItemMeta meta = eggItem.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize("&e&l" + eggId));
            List<Component> lore = new ArrayList<>();
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&7" + lang.getMessage("dialog.hatch_select_egg")));
            lore.add(Component.empty());
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&a▶ Nhấp Xác nhận để bắt đầu vòng quay Roulette!"));
            meta.lore(lore);
            eggItem.setItemMeta(meta);
        }
        inv.setItem(13, eggItem);

        inv.setItem(11, createBtn(Material.EMERALD_BLOCK, lang.getMessage("dialog.hatch_confirm"), List.of("&7Nhấp để bắt đầu ấp trứng")));
        inv.setItem(15, createBtn(Material.REDSTONE_BLOCK, lang.getMessage("dialog.hatch_cancel"), List.of("&7Nhấp để hủy")));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
    }

    @Override
    public void openPetGuide(Player player, DialogSession session, int chapterIndex) {
        LanguageManager lang = plugin.getLanguage();
        PetGuideHolder holder = new PetGuideHolder(session, chapterIndex);
        String rawTitle = lang.getMessage("dialog.guide_title");
        if (rawTitle == null) rawTitle = "&8&lHƯỚNG DẪN PET &7- &6A đến Z";
        Component titleComp = LegacyComponentSerializer.legacyAmpersand().deserialize(rawTitle);
        Inventory inv = Bukkit.createInventory(holder, 54, titleComp);
        holder.setInventory(inv);

        fillBorders(inv, Material.BROWN_STAINED_GLASS_PANE);

        if (chapterIndex < 0 || chapterIndex >= 18) {
            // Table of contents view
            int[] slots = {
                    10, 11, 12, 13, 14, 15, 16,
                    19, 20, 21, 22, 23, 24, 25,
                    28, 29, 30, 31
            };
            for (int i = 0; i < 18; i++) {
                int chNum = i + 1;
                String tKey = "dialog.guide.ch" + chNum + "_title";
                String chTitle = lang.getMessage(tKey);
                if (chTitle == null) chTitle = "Chương " + chNum;
                ItemStack book = createBtn(Material.BOOK, "&e&l" + chNum + ". " + chTitle,
                        List.of("&7Nhấp để đọc nội dung chi tiết chương này"));
                inv.setItem(slots[i], book);
            }
            inv.setItem(49, createBtn(Material.BARRIER, "&c✘ Đóng Hướng Dẫn", List.of("&7Nhấp để quay lại")));
        } else {
            // Detailed chapter view
            int chNum = chapterIndex + 1;
            String tKey = "dialog.guide.ch" + chNum + "_title";
            String bKey = "dialog.guide.ch" + chNum + "_body";
            String chTitle = lang.getMessage(tKey);
            String chBody = lang.getMessage(bKey);
            if (chTitle == null) chTitle = "Chương " + chNum;
            if (chBody == null) chBody = "";

            ItemStack centerBook = new ItemStack(Material.WRITTEN_BOOK);
            ItemMeta bMeta = centerBook.getItemMeta();
            if (bMeta != null) {
                bMeta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize("&6&lCHƯƠNG " + chNum + ": &e" + chTitle));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                for (String line : chBody.split("\n")) {
                    lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&f" + line));
                }
                bMeta.lore(lore);
                centerBook.setItemMeta(bMeta);
            }
            inv.setItem(22, centerBook);

            if (chapterIndex > 0) {
                inv.setItem(45, createBtn(Material.ARROW, lang.getMessage("dialog.guide_btn_prev"), List.of("&7Về chương trước")));
            }
            inv.setItem(49, createBtn(Material.BOOK, lang.getMessage("dialog.guide_btn_hub"), List.of("&7Về danh mục tất cả các chương")));
            if (chapterIndex < 17) {
                inv.setItem(53, createBtn(Material.ARROW, lang.getMessage("dialog.guide_btn_next"), List.of("&7Sang chương tiếp theo")));
            }
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
    }

    @Override
    public void openSettingsDialog(Player player, DialogSession session) {
        LanguageManager lang = plugin.getLanguage();
        PetSettingsHolder holder = new PetSettingsHolder(session);
        String rawTitle = lang.getMessage("dialog.settings_title");
        if (rawTitle == null) rawTitle = "&8&lCÀI ĐẶT THÚ CƯNG &7- &6Settings";
        Component titleComp = LegacyComponentSerializer.legacyAmpersand().deserialize(rawTitle);
        Inventory inv = Bukkit.createInventory(holder, 27, titleComp);
        holder.setInventory(inv);

        fillBorders(inv, Material.CYAN_STAINED_GLASS_PANE);

        boolean particles = plugin.getPlayerSettingsManager().isParticlesEnabled(player.getUniqueId());
        boolean sounds = plugin.getPlayerSettingsManager().isSoundsEnabled(player.getUniqueId());
        String followMode = plugin.getPlayerSettingsManager().getFollowMode(player.getUniqueId());

        ItemStack itemParticles = createBtn(
                particles ? Material.BLAZE_POWDER : Material.GUNPOWDER,
                "&e" + lang.getMessage("dialog.setting_particles"),
                List.of("&7Trạng thái: " + (particles ? "&a✔ BẬT (ON)" : "&c✘ TẮT (OFF)"),
                        "",
                        "&6Nhấp để Bật/Tắt hiệu ứng hạt"));
        inv.setItem(11, itemParticles);

        ItemStack itemSounds = createBtn(
                sounds ? Material.NOTE_BLOCK : Material.JUKEBOX,
                "&e" + lang.getMessage("dialog.setting_sounds"),
                List.of("&7Trạng thái: " + (sounds ? "&a✔ BẬT (ON)" : "&c✘ TẮT (OFF)"),
                        "",
                        "&6Nhấp để Bật/Tắt hiệu ứng âm thanh"));
        inv.setItem(13, itemSounds);

        ItemStack itemFollow = createBtn(
                Material.LEAD,
                "&e" + lang.getMessage("dialog.setting_follow_dist"),
                List.of("&7Khoảng cách: &f" + ("CLOSE".equalsIgnoreCase(followMode) ? "&bGẦN (8 blocks)" : "&aBÌNH THƯỜNG (16 blocks)"),
                        "",
                        "&6Nhấp để chuyển đổi khoảng cách theo sau"));
        inv.setItem(15, itemFollow);

        inv.setItem(22, createBtn(Material.BARRIER, "&c✘ Quay lại Pet Hub", List.of("&7Nhấp để đóng cài đặt")));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_COMPARATOR_CLICK, 1f, 1f);
    }

    @Override
    public void openTradeInviteDialog(Player player, DialogSession session, Player sender) {
        LanguageManager lang = plugin.getLanguage();
        PetTradeInviteHolder holder = new PetTradeInviteHolder(session, sender.getUniqueId());
        String rawTitle = lang.getMessage("dialog.trade_invite_title");
        if (rawTitle == null) rawTitle = "&8&lLỜI MỜI GIAO DỊCH";
        Component titleComp = LegacyComponentSerializer.legacyAmpersand().deserialize(rawTitle);
        Inventory inv = Bukkit.createInventory(holder, 27, titleComp);
        holder.setInventory(inv);

        fillBorders(inv, Material.PURPLE_STAINED_GLASS_PANE);

        ItemStack info = createBtn(Material.PLAYER_HEAD, "&eLời mời từ: &f" + sender.getName(),
                List.of("&7Người chơi muốn giao dịch thẻ Pet và vật phẩm với bạn.", "", "&eHãy chọn một hành động bên dưới:"));
        inv.setItem(13, info);

        inv.setItem(11, createBtn(Material.EMERALD_BLOCK, lang.getMessage("dialog.trade_accept"), List.of("&7Nhấp để mở sàn giao dịch hai chiều")));
        inv.setItem(15, createBtn(Material.REDSTONE_BLOCK, lang.getMessage("dialog.trade_decline"), List.of("&7Nhấp để từ chối lời mời")));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f);
    }

    @Override
    public void openTradeConfirmDialog(Player player, DialogSession session, UUID tradeId, List<ItemStack> myItems, List<ItemStack> theirItems, Consumer<Boolean> callback) {
        LanguageManager lang = plugin.getLanguage();
        PetTradeConfirmHolder holder = new PetTradeConfirmHolder(session, tradeId, callback);
        String rawTitle = lang.getMessage("dialog.trade_confirm_title");
        if (rawTitle == null) rawTitle = "&8&lXÁC NHẬN GIAO DỊCH CUỐI";
        Component titleComp = LegacyComponentSerializer.legacyAmpersand().deserialize(rawTitle);
        Inventory inv = Bukkit.createInventory(holder, 27, titleComp);
        holder.setInventory(inv);

        fillBorders(inv, Material.MAGENTA_STAINED_GLASS_PANE);

        ItemStack summary = createBtn(Material.WRITABLE_BOOK, "&6&lTỔNG KẾT VẬT PHẨM GIAO DỊCH",
                List.of("&eVật phẩm bạn đưa: &f" + (myItems != null ? myItems.size() : 0) + " món",
                        "&aVật phẩm bạn nhận: &f" + (theirItems != null ? theirItems.size() : 0) + " món",
                        "",
                        "&c⚠ Cảnh báo: Sau khi nhấn xác nhận, giao dịch",
                        "&csẽ được bàn giao vĩnh viễn không thể hoàn tác!"));
        inv.setItem(13, summary);

        inv.setItem(11, createBtn(Material.EMERALD_BLOCK, lang.getMessage("dialog.trade_confirm_commit"), List.of("&aNhấp để cam kết bàn giao")));
        inv.setItem(15, createBtn(Material.REDSTONE_BLOCK, lang.getMessage("dialog.trade_confirm_cancel"), List.of("&cNhấp để hủy và lấy lại đồ")));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.5f);
    }

    // --- Helpers ---

    private void fillBorders(Inventory inv, Material mat) {
        ItemStack pane = new ItemStack(mat);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            pane.setItemMeta(meta);
        }
        int size = inv.getSize();
        for (int i = 0; i < size; i++) {
            if (i < 9 || i >= size - 9 || i % 9 == 0 || (i + 1) % 9 == 0) {
                inv.setItem(i, pane);
            }
        }
    }

    private ItemStack createBtn(Material mat, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(name != null ? name : ""));
            if (loreLines != null && !loreLines.isEmpty()) {
                List<Component> lore = new ArrayList<>();
                for (String line : loreLines) {
                    lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(line));
                }
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
