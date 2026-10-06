package org.ipsecuz.pet;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class GuiListener implements Listener {
    private final IpsecuzPet plugin;

    // --- Custom InventoryHolders để bảo mật 100% chống trộm vật phẩm ---
    public static class PetMenuHolder implements InventoryHolder {
        private Inventory inventory;
        private final int page;
        public PetMenuHolder(int page) { this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        public int getPage() { return page; }
    }

    public static class ShopMenuHolder implements InventoryHolder {
        private Inventory inventory;
        private final int page;
        public ShopMenuHolder(int page) { this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        public int getPage() { return page; }
    }

    public static class PetDetailHolder implements InventoryHolder {
        private Inventory inventory;
        private final String petId;
        public PetDetailHolder(String petId) { this.petId = petId; }
        @Override public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        public String getPetId() { return petId; }
    }

    public GuiListener(IpsecuzPet plugin) {
        this.plugin = plugin;
    }

    public static boolean isPluginMenu(Inventory inv) {
        if (inv == null) return false;
        InventoryHolder holder = inv.getHolder();
        return holder instanceof PetMenuHolder ||
                holder instanceof ShopMenuHolder ||
                holder instanceof PetDetailHolder ||
                holder instanceof HatchingManager.HatchMenuHolder ||
                holder instanceof HatchingManager.RouletteHolder;
    }

    public static void openPetMenu(Player p) {
        openPetMenu(p, 1);
    }

    public static void openPetMenu(Player p, int page) {
        IpsecuzPet plugin = IpsecuzPet.getInstance();
        ConfigManager cm = plugin.getConfigManager();
        LanguageManager lang = plugin.getLanguage();

        List<String> ownedPets = new ArrayList<>();
        if (cm.getData().getConfigurationSection(p.getUniqueId() + ".pets") != null) {
            ownedPets.addAll(cm.getData().getConfigurationSection(p.getUniqueId() + ".pets").getKeys(false));
        }

        int pageSize = 45;
        int totalPages = Math.max(1, (int) Math.ceil((double) ownedPets.size() / pageSize));
        int curPage = Math.max(1, Math.min(page, totalPages));

        PetMenuHolder holder = new PetMenuHolder(curPage);
        String rawTitle = lang.getMessage("gui.menu_title") + " &8[" + curPage + "/" + totalPages + "]";
        Inventory inv = Bukkit.createInventory(holder, 54, LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&', rawTitle)));
        holder.setInventory(inv);

        int startIndex = (curPage - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, ownedPets.size());

        for (int i = startIndex; i < endIndex; i++) {
            String petId = ownedPets.get(i);
            String matStr = plugin.getConfig().getString("pets." + petId + ".icon", "STONE");
            ItemStack item = plugin.getItemHookManager().getItem(matStr, Material.STONE);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            String defaultName = plugin.getConfig().getString("pets." + petId + ".name", "Pet");
            String customName = cm.getCustomName(p.getUniqueId(), petId);
            String displayName = (customName != null) ? customName : defaultName;
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&', displayName)));

            List<Component> lore = new ArrayList<>();
            int lvl = cm.getData().getInt(p.getUniqueId() + ".pets." + petId + ".level", 1);
            int star = (plugin.getEvolutionManager() != null) ? plugin.getEvolutionManager().getStar(p.getUniqueId(), petId) : 1;
            boolean isBaby = cm.isPetBaby(p.getUniqueId(), petId);
            int happy = (plugin.getFeedingManager() != null) ? plugin.getFeedingManager().getHappiness(p.getUniqueId(), petId) : 100;

            lore.add(LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&',
                    lang.getMessage("gui.lore_level", "%level%", String.valueOf(lvl)) + " &e(" + star + "⭐)")));
            lore.add(Component.text("§7Dạng kích thước: " + (isBaby ? "§b👶 Bé con" : "§6🦁 Trưởng thành")));
            lore.add(Component.text("§7Độ vui vẻ: §a" + happy + "%"));
            lore.add(Component.text("§7--------------------"));

            if (cm.isPetDead(p.getUniqueId(), petId)) {
                lore.add(LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&', lang.getMessage("gui.lore_dead"))));
            } else {
                String activePet = plugin.getPetManager().getActivePetId(p.getUniqueId());
                if (activePet != null && activePet.equals(petId)) {
                    lore.add(Component.text("§c▶ [Chuột Trái] Cất thú cưng"));
                } else {
                    lore.add(Component.text("§a▶ [Chuột Trái] Triệu hồi"));
                }
                lore.add(Component.text("§e▶ [Chuột Phải] Bảng Điều Khiển Chi Tiết"));
            }

            meta.lore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "pet_id"), PersistentDataType.STRING, petId);
            item.setItemMeta(meta);
            inv.setItem(i - startIndex, item);
        }

        // Dòng thanh công cụ đáy (slots 45 - 53)
        ItemStack navGlass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta ngMeta = navGlass.getItemMeta();
        if (ngMeta != null) { ngMeta.displayName(Component.text(" ")); navGlass.setItemMeta(ngMeta); }
        for (int s = 45; s < 54; s++) inv.setItem(s, navGlass);

        if (curPage > 1) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta pMeta = prev.getItemMeta();
            if (pMeta != null) {
                pMeta.displayName(Component.text("§e◀ Trang " + (curPage - 1)));
                pMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "nav_page"), PersistentDataType.INTEGER, curPage - 1);
                prev.setItemMeta(pMeta);
            }
            inv.setItem(45, prev);
        }

        ItemStack shopBtn = new ItemStack(Material.EMERALD);
        ItemMeta sMeta = shopBtn.getItemMeta();
        if (sMeta != null) {
            sMeta.displayName(Component.text("§a§lCỬA HÀNG PET"));
            sMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "btn_action"), PersistentDataType.STRING, "open_shop");
            shopBtn.setItemMeta(sMeta);
        }
        inv.setItem(47, shopBtn);

        ItemStack infoItem = new ItemStack(Material.NETHER_STAR);
        ItemMeta iMeta = infoItem.getItemMeta();
        if (iMeta != null) {
            iMeta.displayName(Component.text("§6§lHỒ SƠ THÚ CƯNG"));
            List<Component> iLore = new ArrayList<>();
            iLore.add(Component.text("§7Tổng số sở hữu: §e" + ownedPets.size() + "/" + plugin.getConfig().getInt("max_pets", 2)));
            iLore.add(Component.text("§7Trang hiện tại: §f" + curPage + "/" + totalPages));
            iMeta.lore(iLore);
            infoItem.setItemMeta(iMeta);
        }
        inv.setItem(49, infoItem);

        ItemStack hatchBtn = new ItemStack(Material.DRAGON_EGG);
        ItemMeta hMeta = hatchBtn.getItemMeta();
        if (hMeta != null) {
            hMeta.displayName(Component.text("§d§lLÒ ẤP TRỨNG"));
            hMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "btn_action"), PersistentDataType.STRING, "open_hatch");
            hatchBtn.setItemMeta(hMeta);
        }
        inv.setItem(51, hatchBtn);

        if (curPage < totalPages) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nMeta = next.getItemMeta();
            if (nMeta != null) {
                nMeta.displayName(Component.text("§eTrang " + (curPage + 1) + " ▶"));
                nMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "nav_page"), PersistentDataType.INTEGER, curPage + 1);
                next.setItemMeta(nMeta);
            }
            inv.setItem(53, next);
        }

        p.openInventory(inv);
    }

    public static void openPetDetailMenu(Player p, String petId) {
        IpsecuzPet plugin = IpsecuzPet.getInstance();
        ConfigManager cm = plugin.getConfigManager();

        String petName = plugin.getConfig().getString("pets." + petId + ".name", petId);
        PetDetailHolder holder = new PetDetailHolder(petId);
        Inventory inv = Bukkit.createInventory(holder, 45, LegacyComponentSerializer.legacySection().deserialize(
                ChatColor.translateAlternateColorCodes('&', "&8Điều Khiển: " + petName)));
        holder.setInventory(inv);

        // Nền trang trí
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        if (bgMeta != null) { bgMeta.displayName(Component.text(" ")); bg.setItemMeta(bgMeta); }
        for (int i = 0; i < 45; i++) inv.setItem(i, bg);

        int lvl = cm.getData().getInt(p.getUniqueId() + ".pets." + petId + ".level", 1);
        int star = (plugin.getEvolutionManager() != null) ? plugin.getEvolutionManager().getStar(p.getUniqueId(), petId) : 1;
        boolean isBaby = cm.isPetBaby(p.getUniqueId(), petId);
        int happy = (plugin.getFeedingManager() != null) ? plugin.getFeedingManager().getHappiness(p.getUniqueId(), petId) : 100;

        // Biểu tượng Pet ở giữa (slot 13)
        String matStr = plugin.getConfig().getString("pets." + petId + ".icon", "STONE");
        ItemStack petIcon = plugin.getItemHookManager().getItem(matStr, Material.STONE);
        ItemMeta pMeta = petIcon.getItemMeta();
        if (pMeta != null) {
            pMeta.displayName(LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&', "&e&l" + petName)));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§7Cấp độ: §6Lv." + lvl));
            lore.add(Component.text("§7Cấp Sao: §e" + star + " ⭐"));
            lore.add(Component.text("§7Độ Vui Vẻ: §a" + happy + "%"));
            lore.add(Component.text("§7Kích Thước: §b" + (isBaby ? "Bé con (Baby)" : "Trưởng thành (Adult)")));
            pMeta.lore(lore);
            petIcon.setItemMeta(pMeta);
        }
        inv.setItem(13, petIcon);

        // Nút Triệu Hồi / Cất đi (slot 20)
        String active = plugin.getPetManager().getActivePetId(p.getUniqueId());
        boolean isCurrent = active != null && active.equals(petId);
        ItemStack summonBtn = new ItemStack(isCurrent ? Material.BARRIER : Material.LEAD);
        ItemMeta sMeta = summonBtn.getItemMeta();
        if (sMeta != null) {
            sMeta.displayName(Component.text(isCurrent ? "§c§l[CẤT THÚ CƯNG]" : "§a§l[TRIỆU HỒI PET]"));
            sMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "detail_action"), PersistentDataType.STRING, "toggle_summon:" + petId);
            summonBtn.setItemMeta(sMeta);
        }
        inv.setItem(20, summonBtn);

        // Nút Đổi dạng Kích thước Bé / Lớn (slot 22)
        ItemStack sizeBtn = new ItemStack(isBaby ? Material.EGG : Material.TURTLE_HELMET);
        ItemMeta sizeMeta = sizeBtn.getItemMeta();
        if (sizeMeta != null) {
            sizeMeta.displayName(Component.text(isBaby ? "§b§lKÍCH THƯỚC: [BÉ CON 👶]" : "§6§lKÍCH THƯỚC: [TRƯỞNG THÀNH 🦁]"));
            List<Component> sizeLore = new ArrayList<>();
            sizeLore.add(Component.text("§7Nhấp để chuyển đổi dạng kích thước"));
            sizeLore.add(Component.text("§e▶ Đổi sang: " + (isBaby ? "Trưởng thành 🦁" : "Bé con 👶")));
            sizeMeta.lore(sizeLore);
            sizeMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "detail_action"), PersistentDataType.STRING, "toggle_baby:" + petId);
            sizeBtn.setItemMeta(sizeMeta);
        }
        inv.setItem(22, sizeBtn);

        // Nút Cho ăn (slot 24)
        ItemStack feedBtn = new ItemStack(Material.COOKED_BEEF);
        ItemMeta fMeta = feedBtn.getItemMeta();
        if (fMeta != null) {
            fMeta.displayName(Component.text("§6§l[CHO ĂN 🍖]"));
            List<Component> fLore = new ArrayList<>();
            fLore.add(Component.text("§7Cầm thức ăn trên tay và nhấp vào đây"));
            fLore.add(Component.text("§7hoặc dùng lệnh: §e/pet feed"));
            fMeta.lore(fLore);
            fMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "detail_action"), PersistentDataType.STRING, "feed:" + petId);
            feedBtn.setItemMeta(fMeta);
        }
        inv.setItem(24, feedBtn);

        // Nút Kỹ Năng Ultimate (slot 29): Hỗ trợ điều kiện học kỹ năng
        boolean skillUnlocked = plugin.getSkillManager().isSkillUnlocked(p.getUniqueId(), petId, "ultimate");
        FileConfiguration skillsCfg = plugin.getModuleManager().getSkillsConfig();
        ConfigurationSection ultSec = skillsCfg.getConfigurationSection("skills." + petId + ".ultimate");

        ItemStack skillBtn = new ItemStack(skillUnlocked ? Material.BLAZE_POWDER : Material.NETHER_BRICK);
        ItemMeta skMeta = skillBtn.getItemMeta();
        if (skMeta != null) {
            String skName = (ultSec != null) ? ultSec.getString("name", "Tuyệt Chiêu") : "Tuyệt Chiêu";
            List<Component> skLore = new ArrayList<>();

            if (skillUnlocked) {
                skMeta.displayName(LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&', "&a&l[KÍCH HOẠT: " + skName + " ⚡]")));
                if (ultSec != null && ultSec.contains("desc")) {
                    skLore.add(LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&', ultSec.getString("desc", ""))));
                }
                skLore.add(Component.text("§a✔ Đã học kỹ năng"));
                skLore.add(Component.text("§e▶ Nhấp để kích hoạt ngay!"));
                skMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "detail_action"), PersistentDataType.STRING, "ultimate:" + petId);
            } else {
                skMeta.displayName(LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&', "&6&l[HỌC KỸ NĂNG: " + skName + " ⚡]")));
                if (ultSec != null && ultSec.contains("desc")) {
                    skLore.add(LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&', ultSec.getString("desc", ""))));
                }
                skLore.add(Component.text("§c✖ Chưa học kỹ năng này!"));
                skLore.add(Component.text("§7--- Yêu cầu để học ---"));
                int reqLvl = (ultSec != null) ? ultSec.getInt("req_level", 1) : 1;
                int costMoney = (ultSec != null) ? ultSec.getInt("cost_money", 0) : 0;
                int costPoints = (ultSec != null) ? ultSec.getInt("cost_points", 0) : 0;
                skLore.add(Component.text("§7- Cấp Pet: " + (lvl >= reqLvl ? "§aLv." : "§cLv.") + reqLvl + " (Hiện: Lv." + lvl + ")"));
                if (costMoney > 0) skLore.add(Component.text("§7- Tiền: §e$" + costMoney));
                if (costPoints > 0) skLore.add(Component.text("§7- Points: §b" + costPoints + " P"));
                if (ultSec != null && ultSec.contains("cost_items")) {
                    skLore.add(Component.text("§7- Vật phẩm: §f" + ultSec.getString("cost_items")));
                }
                skLore.add(Component.text("§a▶ Nhấp để Học Kỹ Năng ngay!"));
                skMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "detail_action"), PersistentDataType.STRING, "learn_skill:ultimate:" + petId);
            }

            skMeta.lore(skLore);
            skillBtn.setItemMeta(skMeta);
        }
        inv.setItem(29, skillBtn);

        // Nút Nâng Sao (slot 31)
        ItemStack starBtn = new ItemStack(Material.NETHER_STAR);
        ItemMeta starMeta = starBtn.getItemMeta();
        if (starMeta != null) {
            starMeta.displayName(Component.text("§e§l[TIẾN HÓA & TĂNG SAO ⭐]"));
            List<Component> stLore = new ArrayList<>();
            stLore.add(Component.text("§7Cấp hiện tại: §b" + star + "⭐"));
            stLore.add(Component.text("§7Mỗi sao tăng +15% Máu, Sát thương"));
            stLore.add(Component.text("§a▶ Nhấp để nâng cấp lên " + (star + 1) + "⭐"));
            starMeta.lore(stLore);
            starMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "detail_action"), PersistentDataType.STRING, "upgrade_star:" + petId);
            starBtn.setItemMeta(starMeta);
        }
        inv.setItem(31, starBtn);

        // Nút Rút Thẻ Pet (slot 33)
        ItemStack withdrawBtn = new ItemStack(Material.CHEST_MINECART);
        ItemMeta wMeta = withdrawBtn.getItemMeta();
        if (wMeta != null) {
            wMeta.displayName(Component.text("§d§l[RÚT THÀNH THẺ VẬT PHẨM 📦]"));
            List<Component> wLore = new ArrayList<>();
            wLore.add(Component.text("§7Rút thú cưng ra vật phẩm trong rương đồ"));
            wMeta.lore(wLore);
            wMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "detail_action"), PersistentDataType.STRING, "withdraw:" + petId);
            withdrawBtn.setItemMeta(wMeta);
        }
        inv.setItem(33, withdrawBtn);

        // Nút Quay lại (slot 40)
        ItemStack backBtn = new ItemStack(Material.ARROW);
        ItemMeta bMeta = backBtn.getItemMeta();
        if (bMeta != null) {
            bMeta.displayName(Component.text("§7◀ Quay lại Danh Sách Pet"));
            bMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "detail_action"), PersistentDataType.STRING, "back");
            backBtn.setItemMeta(bMeta);
        }
        inv.setItem(40, backBtn);

        p.openInventory(inv);
    }

    public static void openShopMenu(Player p) {
        openShopMenu(p, 1);
    }

    public static void openShopMenu(Player p, int page) {
        IpsecuzPet plugin = IpsecuzPet.getInstance();
        LanguageManager lang = plugin.getLanguage();
        ConfigManager cm = plugin.getConfigManager();

        ConfigurationSection petsSec = plugin.getConfig().getConfigurationSection("pets");
        List<String> allKeys = (petsSec != null) ? new ArrayList<>(petsSec.getKeys(false)) : new ArrayList<>();

        int pageSize = 45;
        int totalPages = Math.max(1, (int) Math.ceil((double) allKeys.size() / pageSize));
        int curPage = Math.max(1, Math.min(page, totalPages));

        ShopMenuHolder holder = new ShopMenuHolder(curPage);
        String rawTitle = lang.getMessage("gui.shop_title") + " &8[Trang " + curPage + "/" + totalPages + "]";
        Inventory inv = Bukkit.createInventory(holder, 54, LegacyComponentSerializer.legacySection().deserialize(
                ChatColor.translateAlternateColorCodes('&', rawTitle)));
        holder.setInventory(inv);

        int startIndex = (curPage - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, allKeys.size());

        for (int i = startIndex; i < endIndex; i++) {
            String key = allKeys.get(i);
            String mat = plugin.getConfig().getString("pets." + key + ".icon", "STONE");
            ItemStack item = plugin.getItemHookManager().getItem(mat, Material.STONE);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            String petName = plugin.getConfig().getString("pets." + key + ".name", key);
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&', petName)));

            List<Component> lore = new ArrayList<>();
            String price = plugin.getCurrencyManager().getPriceDisplay(key);

            // Thêm tóm tắt chỉ số cơ bản
            double hp = plugin.getConfig().getDouble("pets." + key + ".stats.health", 20.0);
            double dmg = plugin.getConfig().getDouble("pets." + key + ".stats.damage", 5.0);
            double def = plugin.getConfig().getDouble("pets." + key + ".stats.defense", 0.0);
            lore.add(Component.text("§7Máu: §a" + hp + " ❤ §7| Sát thương: §c" + dmg + " ⚔"));
            lore.add(Component.text("§7Giáp: §9" + def + " 🛡"));
            lore.add(Component.text("§7--------------------"));

            if (cm.getData().contains(p.getUniqueId() + ".pets." + key)) {
                lore.add(Component.text("§a✔ ĐÃ SỞ HỮU"));
            } else {
                lore.add(LegacyComponentSerializer.legacySection().deserialize(
                        ChatColor.translateAlternateColorCodes('&', lang.getMessage("gui.lore_price", "%cost%", price))));
                lore.add(Component.text("§e▶ Nhấp để Mua ngay!"));
            }

            meta.lore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "shop_id"), PersistentDataType.STRING, key);
            item.setItemMeta(meta);
            inv.setItem(i - startIndex, item);
        }

        // Thanh điều hướng đáy (slots 45-53)
        ItemStack navGlass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta ngMeta = navGlass.getItemMeta();
        if (ngMeta != null) { ngMeta.displayName(Component.text(" ")); navGlass.setItemMeta(ngMeta); }
        for (int s = 45; s < 54; s++) inv.setItem(s, navGlass);

        if (curPage > 1) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta pMeta = prev.getItemMeta();
            if (pMeta != null) {
                pMeta.displayName(Component.text("§e◀ Trang " + (curPage - 1)));
                pMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "nav_shop_page"), PersistentDataType.INTEGER, curPage - 1);
                prev.setItemMeta(pMeta);
            }
            inv.setItem(45, prev);
        }

        ItemStack hatchBtn = new ItemStack(Material.DRAGON_EGG);
        ItemMeta hMeta = hatchBtn.getItemMeta();
        if (hMeta != null) {
            hMeta.displayName(Component.text("§d§lLÒ ẤP TRỨNG"));
            hMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "btn_action"), PersistentDataType.STRING, "open_hatch");
            hatchBtn.setItemMeta(hMeta);
        }
        inv.setItem(47, hatchBtn);

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.displayName(Component.text("§6§lCỬA HÀNG THÚ CƯNG"));
            List<Component> iLore = new ArrayList<>();
            iLore.add(Component.text("§7Tổng số Pet bán: §e" + allKeys.size()));
            iLore.add(Component.text("§7Trang hiện tại: §f" + curPage + "/" + totalPages));
            infoMeta.lore(iLore);
            info.setItemMeta(infoMeta);
        }
        inv.setItem(49, info);

        ItemStack menuBtn = new ItemStack(Material.LEAD);
        ItemMeta mMeta = menuBtn.getItemMeta();
        if (mMeta != null) {
            mMeta.displayName(Component.text("§b§lMENU PET CỦA TÔI"));
            mMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "btn_action"), PersistentDataType.STRING, "open_pet_menu");
            menuBtn.setItemMeta(mMeta);
        }
        inv.setItem(51, menuBtn);

        if (curPage < totalPages) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nMeta = next.getItemMeta();
            if (nMeta != null) {
                nMeta.displayName(Component.text("§eTrang " + (curPage + 1) + " ▶"));
                nMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "nav_shop_page"), PersistentDataType.INTEGER, curPage + 1);
                next.setItemMeta(nMeta);
            }
            inv.setItem(53, next);
        }

        p.openInventory(inv);
    }

    // --- CHẶN KÉO THẢ ITEM (INVENTORY DRAG) HOÀN TOÀN TRÊN TẤT CẢ MENU PLUGIN ---
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (isPluginMenu(e.getView().getTopInventory())) {
            e.setCancelled(true);
        }
    }

    // --- AN TOÀN KHI ĐÓNG GACHA: KHÔNG MẤT PHẦN THƯỞNG ---
    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof HatchingManager.RouletteHolder holder) {
            if (!holder.isFinished() && e.getPlayer() instanceof Player player) {
                holder.setFinished(true);
                plugin.getHatchingManager().completeHatchReward(player, holder.getWinningPetId(), false);
            }
        }
    }

    // --- CHẶN VÀ XỬ LÝ CLICK (INVENTORY CLICK) AN TOÀN TUYỆT ĐỐI ---
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        Inventory topInv = e.getView().getTopInventory();
        if (!isPluginMenu(topInv)) return;

        // BẢO VỆ TUYỆT ĐỐI: CHẶN TẤT CẢ CLICK VÀ SHIFT-CLICK
        e.setCancelled(true);

        // Nếu click ngoài hoặc click vào túi đồ cá nhân, đã bị chặn không cho nhặt/đẩy item
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(topInv)) {
            return;
        }

        ItemStack clickedItem = e.getCurrentItem();
        if (clickedItem == null || clickedItem.getItemMeta() == null) return;
        ItemMeta meta = clickedItem.getItemMeta();

        // 1. Nút chuyển trang Menu Pet (nav_page)
        NamespacedKey navKey = new NamespacedKey(plugin, "nav_page");
        if (meta.getPersistentDataContainer().has(navKey, PersistentDataType.INTEGER)) {
            Integer targetPage = meta.getPersistentDataContainer().get(navKey, PersistentDataType.INTEGER);
            if (targetPage != null) openPetMenu(p, targetPage);
            return;
        }

        // 2. Nút chuyển trang Shop (nav_shop_page)
        NamespacedKey navShopKey = new NamespacedKey(plugin, "nav_shop_page");
        if (meta.getPersistentDataContainer().has(navShopKey, PersistentDataType.INTEGER)) {
            Integer targetPage = meta.getPersistentDataContainer().get(navShopKey, PersistentDataType.INTEGER);
            if (targetPage != null) openShopMenu(p, targetPage);
            return;
        }

        // 3. Nút công cụ chuyển menu (btn_action)
        NamespacedKey btnKey = new NamespacedKey(plugin, "btn_action");
        if (meta.getPersistentDataContainer().has(btnKey, PersistentDataType.STRING)) {
            String action = meta.getPersistentDataContainer().get(btnKey, PersistentDataType.STRING);
            if ("open_shop".equals(action)) {
                openShopMenu(p, 1);
            } else if ("open_hatch".equals(action)) {
                plugin.getHatchingManager().openHatchingGui(p);
            } else if ("open_pet_menu".equals(action)) {
                openPetMenu(p, 1);
            }
            return;
        }

        // 4. Menu Quản lý Chi tiết Pet (detail_action)
        NamespacedKey detailKey = new NamespacedKey(plugin, "detail_action");
        if (meta.getPersistentDataContainer().has(detailKey, PersistentDataType.STRING)) {
            String actionData = meta.getPersistentDataContainer().get(detailKey, PersistentDataType.STRING);
            if (actionData == null) return;

            if (actionData.equals("back")) {
                openPetMenu(p, 1);
                return;
            }

            String[] parts = actionData.split(":", 3);
            String action = parts[0];

            if ("toggle_summon".equals(action) && parts.length > 1) {
                String petId = parts[1];
                String active = plugin.getPetManager().getActivePetId(p.getUniqueId());
                if (active != null && active.equals(petId)) {
                    plugin.getPetManager().removePet(p.getUniqueId());
                    p.sendMessage(plugin.getLanguage().getMessage("pet.despawn"));
                } else {
                    plugin.getPetManager().spawnPet(p, petId);
                }
                openPetDetailMenu(p, petId);
            } else if ("toggle_baby".equals(action) && parts.length > 1) {
                String petId = parts[1];
                boolean curBaby = plugin.getConfigManager().isPetBaby(p.getUniqueId(), petId);
                plugin.getConfigManager().setPetBaby(p.getUniqueId(), petId, !curBaby);
                p.sendMessage("§aĐã đổi dạng thú cưng sang: " + (!curBaby ? "§b👶 Bé con" : "§6🦁 Trưởng thành"));
                // Respawn nếu đang hoạt động
                String active = plugin.getPetManager().getActivePetId(p.getUniqueId());
                if (active != null && active.equals(petId)) {
                    plugin.getPetManager().spawnPet(p, petId);
                }
                openPetDetailMenu(p, petId);
            } else if ("feed".equals(action) && parts.length > 1) {
                String petId = parts[1];
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (plugin.getFeedingManager().feedPet(p, hand)) {
                    openPetDetailMenu(p, petId);
                }
            } else if ("ultimate".equals(action)) {
                plugin.getSkillManager().triggerUltimate(p);
                p.closeInventory();
            } else if ("learn_skill".equals(action) && parts.length > 2) {
                String skillType = parts[1];
                String petId = parts[2];
                if (plugin.getSkillManager().unlockSkill(p, petId, skillType)) {
                    openPetDetailMenu(p, petId);
                }
            } else if ("upgrade_star".equals(action) && parts.length > 1) {
                String petId = parts[1];
                if (plugin.getEvolutionManager().upgradeStar(p, petId)) {
                    openPetDetailMenu(p, petId);
                }
            } else if ("withdraw".equals(action) && parts.length > 1) {
                String petId = parts[1];
                p.closeInventory();
                p.performCommand("pet withdraw " + petId);
            }
            return;
        }

        // 5. Bắt click trong Menu Hồ Sơ Pet (pet_id)
        NamespacedKey petKey = new NamespacedKey(plugin, "pet_id");
        if (meta.getPersistentDataContainer().has(petKey, PersistentDataType.STRING)) {
            String petId = meta.getPersistentDataContainer().get(petKey, PersistentDataType.STRING);
            if (petId == null) return;

            if (e.getClick() == ClickType.RIGHT) {
                // Nhấp chuột phải -> Mở bảng điều khiển chi tiết
                openPetDetailMenu(p, petId);
            } else {
                // Nhấp chuột trái -> Triệu hồi hoặc cất
                String activePet = plugin.getPetManager().getActivePetId(p.getUniqueId());
                if (activePet != null && activePet.equals(petId)) {
                    plugin.getPetManager().removePet(p.getUniqueId());
                    p.sendMessage(plugin.getLanguage().getMessage("pet.despawn"));
                } else {
                    plugin.getPetManager().spawnPet(p, petId);
                }
                p.closeInventory();
            }
            return;
        }

        // 6. Bắt click trong Menu Shop (shop_id)
        NamespacedKey shopKey = new NamespacedKey(plugin, "shop_id");
        if (meta.getPersistentDataContainer().has(shopKey, PersistentDataType.STRING)) {
            String shopId = meta.getPersistentDataContainer().get(shopKey, PersistentDataType.STRING);
            if (shopId != null) {
                if (plugin.getConfigManager().getData().contains(p.getUniqueId() + ".pets." + shopId)) {
                    p.sendMessage(plugin.getLanguage().getMessage("pet.already_owned"));
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }

                if (plugin.getCurrencyManager().processTransaction(p, shopId)) {
                    plugin.getConfigManager().createPetDataIfMissing(p.getUniqueId(), shopId);
                    p.sendMessage(plugin.getLanguage().getMessage("pet.buy_success", "%pet_name%", plugin.getConfig().getString("pets." + shopId + ".name", shopId)));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                    // Làm mới lại trang shop hiện tại
                    int curPage = 1;
                    if (topInv.getHolder() instanceof ShopMenuHolder smh) {
                        curPage = smh.getPage();
                    }
                    openShopMenu(p, curPage);
                }
            }
            return;
        }

        // 7. Bắt click trong Menu Lò Ấp Trứng (pet_egg_id)
        NamespacedKey eggKey = plugin.getHatchingManager().eggKey;
        if (meta.getPersistentDataContainer().has(eggKey, PersistentDataType.STRING)) {
            String eggId = meta.getPersistentDataContainer().get(eggKey, PersistentDataType.STRING);
            if (eggId != null) {
                p.closeInventory();
                plugin.getHatchingManager().processHatch(p, eggId, null);
            }
        }
    }
}