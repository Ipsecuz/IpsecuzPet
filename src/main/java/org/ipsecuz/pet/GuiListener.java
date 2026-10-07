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

import java.util.*;

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

    public static class CodexHolder implements InventoryHolder {
        private Inventory inventory;
        private final int page;
        public CodexHolder(int page) { this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        public int getPage() { return page; }
    }

    public static class ShardsHolder implements InventoryHolder {
        private Inventory inventory;
        public ShardsHolder() {}
        @Override public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
    }

    public static class EvolutionHolder implements InventoryHolder {
        private Inventory inventory;
        private final String petId;
        public EvolutionHolder(String petId) { this.petId = petId; }
        @Override public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        public String getPetId() { return petId; }
    }

    public static class ConfirmHolder implements InventoryHolder {
        private Inventory inventory;
        private final String actionPayload;
        public ConfirmHolder(String actionPayload) { this.actionPayload = actionPayload; }
        @Override public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        public String getActionPayload() { return actionPayload; }
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
                holder instanceof CodexHolder ||
                holder instanceof ShardsHolder ||
                holder instanceof EvolutionHolder ||
                holder instanceof ConfirmHolder ||
                holder instanceof HatchingManager.HatchMenuHolder ||
                holder instanceof HatchingManager.RouletteHolder ||
                holder instanceof TradeSession.TradeHolder;
    }

    public static String buildProgressBar(int current, int max, int totalBars) {
        if (max <= 0) return "§a[██████████] 100%";
        double percent = Math.min(1.0, Math.max(0.0, (double) current / max));
        int filled = (int) Math.round(percent * totalBars);
        StringBuilder sb = new StringBuilder("§a[");
        for (int i = 0; i < filled; i++) sb.append("█");
        sb.append("§7");
        for (int i = filled; i < totalBars; i++) sb.append("░");
        sb.append("§a] §e").append((int) (percent * 100)).append("%");
        return sb.toString();
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
            int exp = cm.getData().getInt(p.getUniqueId() + ".pets." + petId + ".exp", 0);
            int maxLvl = plugin.getConfig().getInt("rpg_system.max_level", 100);
            int reqExp = lvl * plugin.getConfig().getInt("rpg_system.base_exp_requirement", 50);
            int star = (plugin.getEvolutionManager() != null) ? plugin.getEvolutionManager().getStar(p.getUniqueId(), petId) : 1;
            boolean isBaby = cm.isPetBaby(p.getUniqueId(), petId);
            int happy = (plugin.getFeedingManager() != null) ? plugin.getFeedingManager().getHappiness(p.getUniqueId(), petId) : 100;
            String traitName = cm.getData().getString(p.getUniqueId() + ".pets." + petId + ".trait", "NONE");
            PetTrait trait = PetTrait.fromString(traitName);
            PetRarity rarity = PetRarity.fromPetId(plugin, petId);

            lore.add(Component.text("§7Độ hiếm: " + rarity.getFormattedName()));
            lore.add(Component.text("§7Cấp độ: §6Lv." + lvl + (lvl >= maxLvl ? " §e[TỐI ĐA]" : (" §8(" + exp + "/" + reqExp + ")"))));
            if (lvl < maxLvl) {
                lore.add(Component.text("§7Kinh nghiệm: " + buildProgressBar(exp, reqExp, 10)));
            }
            lore.add(Component.text("§7Cấp sao: " + ((plugin.getEvolutionManager() != null) ? plugin.getEvolutionManager().getStarDisplay(star) : (star + "⭐"))));
            lore.add(Component.text("§7Đặc chất: " + trait.getFormattedName()));
            lore.add(Component.text("§7Dạng kích thước: " + (isBaby ? "§b👶 Bé con" : "§6🦁 Trưởng thành")));

            FeedingManager.HappinessState happyState = (plugin.getFeedingManager() != null)
                    ? plugin.getFeedingManager().getHappinessState(happy) : FeedingManager.HappinessState.CONTENT;
            lore.add(Component.text("§7Thân thiết: §d♡ " + happy + "% §8(" + happyState.getDisplay() + "§8)"));

            lore.add(Component.text("§8§m------------------------"));
            String active = plugin.getPetManager().getActivePetId(p.getUniqueId());
            if (active != null && active.equals(petId)) {
                lore.add(Component.text("§a● ĐANG ĐƯỢC TRIỆU HỒI [KÍCH HOẠT]"));
            } else {
                lore.add(Component.text("§7○ ĐANG NGHỈ NGƠI"));
                lore.add(Component.text("§e▶ Chuột trái: Triệu hồi"));
            }
            lore.add(Component.text("§b▶ Chuột phải: Mở Quản Lý Chi Tiết"));

            meta.lore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "pet_id"), PersistentDataType.STRING, petId);
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
                pMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "nav_page"), PersistentDataType.INTEGER, curPage - 1);
                prev.setItemMeta(pMeta);
            }
            inv.setItem(45, prev);
        }

        // Nút Codex (slot 46)
        ItemStack codexBtn = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta cMeta = codexBtn.getItemMeta();
        if (cMeta != null) {
            cMeta.displayName(Component.text("§6§l✦ BÁCH KHOA PET (CODEX) ✦"));
            List<Component> cLore = new ArrayList<>();
            cLore.add(Component.text("§7Xem toàn bộ danh sách Pet và tiến trình"));
            cLore.add(Component.text("§e▶ Nhấp để mở Bách Khoa!"));
            cMeta.lore(cLore);
            cMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "btn_action"), PersistentDataType.STRING, "open_codex");
            codexBtn.setItemMeta(cMeta);
        }
        inv.setItem(46, codexBtn);

        // Nút Shards (slot 47)
        ItemStack shardsBtn = new ItemStack(Material.PRISMARINE_SHARD);
        ItemMeta shMeta = shardsBtn.getItemMeta();
        if (shMeta != null) {
            shMeta.displayName(Component.text("§b§l✦ KHO MẢNH PET (SHARDS) ✦"));
            List<Component> shLore = new ArrayList<>();
            shLore.add(Component.text("§7Ghép 50 mảnh để mở khóa Pet mới!"));
            shLore.add(Component.text("§e▶ Nhấp để kiểm tra mảnh ghép"));
            shMeta.lore(shLore);
            shMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "btn_action"), PersistentDataType.STRING, "open_shards");
            shardsBtn.setItemMeta(shMeta);
        }
        inv.setItem(47, shardsBtn);

        // Nút Cửa Hàng (slot 49)
        ItemStack shopBtn = new ItemStack(Material.EMERALD);
        ItemMeta sMeta = shopBtn.getItemMeta();
        if (sMeta != null) {
            sMeta.displayName(Component.text("§a§lCỬA HÀNG THÚ CƯNG"));
            sMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "btn_action"), PersistentDataType.STRING, "open_shop");
            shopBtn.setItemMeta(sMeta);
        }
        inv.setItem(49, shopBtn);

        // Nút Lò Ấp Trứng (slot 51)
        ItemStack hatchBtn = new ItemStack(Material.DRAGON_EGG);
        ItemMeta hMeta = hatchBtn.getItemMeta();
        if (hMeta != null) {
            hMeta.displayName(Component.text("§d§lLÒ ẤP TRỨNG PET"));
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

        PetDetailHolder holder = new PetDetailHolder(petId);
        String defaultName = plugin.getConfig().getString("pets." + petId + ".name", "Pet");
        String customName = cm.getCustomName(p.getUniqueId(), petId);
        String name = (customName != null) ? customName : defaultName;

        Inventory inv = Bukkit.createInventory(holder, 45, LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&', "&8✦ Quản Lý: " + name)));
        holder.setInventory(inv);

        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bMeta = border.getItemMeta();
        if (bMeta != null) { bMeta.displayName(Component.text(" ")); border.setItemMeta(bMeta); }
        for (int i = 0; i < 45; i++) inv.setItem(i, border);

        // Icon chính (slot 13)
        String matStr = plugin.getConfig().getString("pets." + petId + ".icon", "STONE");
        ItemStack petIcon = plugin.getItemHookManager().getItem(matStr, Material.STONE);
        ItemMeta pMeta = petIcon.getItemMeta();
        int lvl = cm.getData().getInt(p.getUniqueId() + ".pets." + petId + ".level", 1);
        int exp = cm.getData().getInt(p.getUniqueId() + ".pets." + petId + ".exp", 0);
        int reqExp = lvl * plugin.getConfig().getInt("rpg_system.base_exp_requirement", 50);
        int star = (plugin.getEvolutionManager() != null) ? plugin.getEvolutionManager().getStar(p.getUniqueId(), petId) : 1;
        boolean isBaby = cm.isPetBaby(p.getUniqueId(), petId);
        int happy = (plugin.getFeedingManager() != null) ? plugin.getFeedingManager().getHappiness(p.getUniqueId(), petId) : 100;
        String traitName = cm.getData().getString(p.getUniqueId() + ".pets." + petId + ".trait", "NONE");
        PetTrait trait = PetTrait.fromString(traitName);
        PetRarity rarity = PetRarity.fromPetId(plugin, petId);

        if (pMeta != null) {
            pMeta.displayName(LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&', "&6★ " + name)));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§7Mã định danh: §e" + petId));
            lore.add(Component.text("§7Độ hiếm: " + rarity.getFormattedName()));
            lore.add(Component.text("§7Cấp sao: " + ((plugin.getEvolutionManager() != null) ? plugin.getEvolutionManager().getStarDisplay(star) : (star + "⭐"))));
            lore.add(Component.text("§7Đặc chất: " + trait.getFormattedName()));
            lore.add(Component.text("§7Cấp độ: §aLv." + lvl + " §7(EXP: §b" + exp + "/" + reqExp + "§7)"));
            lore.add(Component.text("§7Độ vui vẻ: §e" + happy + "%"));
            lore.add(Component.text("§7--------------------"));
            double dmg = PetStatEngine.calculateEffectiveStat(plugin, p.getUniqueId(), petId, lvl, "damage");
            double hp = PetStatEngine.calculateEffectiveStat(plugin, p.getUniqueId(), petId, lvl, "health");
            double def = PetStatEngine.calculateEffectiveStat(plugin, p.getUniqueId(), petId, lvl, "defense");
            double spd = PetStatEngine.calculateEffectiveStat(plugin, p.getUniqueId(), petId, lvl, "speed");
            lore.add(Component.text("§7Máu tối đa: §a" + String.format("%.1f", hp) + " ❤"));
            lore.add(Component.text("§7Sát thương: §c" + String.format("%.1f", dmg) + " ⚔"));
            lore.add(Component.text("§7Giáp phòng thủ: §9" + String.format("%.1f", def) + " 🛡"));
            lore.add(Component.text("§7Tốc độ di chuyển: §f" + String.format("%.3f", spd)));
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

        // Nút Kỹ Năng Ultimate (slot 29)
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
                if (ultSec != null) {
                    org.ipsecuz.pet.requirement.RequirementGroup group = plugin.getRequirementManager().parse(ultSec);
                    org.ipsecuz.pet.requirement.RequirementContext ctx = new org.ipsecuz.pet.requirement.RequirementContext(p, petId, lvl, star, plugin);
                    org.ipsecuz.pet.requirement.RequirementCheckResult result = plugin.getRequirementManager().evaluate(group, ctx);
                    skLore.addAll(org.ipsecuz.pet.requirement.RequirementGuiRenderer.renderToComponents(result));
                }
                skLore.add(Component.text("§a▶ Nhấp để Học Kỹ Năng ngay!"));
                skMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "detail_action"), PersistentDataType.STRING, "learn_skill:ultimate:" + petId);
            }

            skMeta.lore(skLore);
            skillBtn.setItemMeta(skMeta);
        }
        inv.setItem(29, skillBtn);

        // Nút Nâng Sao & Tiến Hóa (slot 31)
        ItemStack starBtn = new ItemStack(Material.NETHER_STAR);
        ItemMeta starMeta = starBtn.getItemMeta();
        if (starMeta != null) {
            starMeta.displayName(Component.text("§e§l[TIẾN HÓA & TĂNG SAO ⭐]"));
            List<Component> stLore = new ArrayList<>();
            stLore.add(Component.text("§7Cấp sao hiện tại: " + ((plugin.getEvolutionManager() != null) ? plugin.getEvolutionManager().getStarDisplay(star) : (star + "⭐"))));
            stLore.add(Component.text("§7Gia tăng +15% Máu, Sát thương & Giáp mỗi sao"));
            if (star < 5) {
                stLore.add(Component.text("§a▶ Nhấp để mở Giao Diện Tiến Hóa Chuyên Sâu ⭐"));
            } else {
                stLore.add(Component.text("§6§l✔ ĐÃ ĐẠT CẤP SAO TỐI ĐA!"));
            }
            starMeta.lore(stLore);
            starMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "detail_action"), PersistentDataType.STRING, "open_evolution:" + petId);
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
            wLore.add(Component.text("§e▶ Nhấp để mở hộp thoại xác nhận rút"));
            wMeta.lore(wLore);
            wMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "detail_action"), PersistentDataType.STRING, "confirm_withdraw_prompt:" + petId);
            withdrawBtn.setItemMeta(wMeta);
        }
        inv.setItem(33, withdrawBtn);

        // Nút Đổi tên Thú Cưng (slot 35)
        ItemStack renameBtn = new ItemStack(Material.NAME_TAG);
        ItemMeta rnMeta = renameBtn.getItemMeta();
        if (rnMeta != null) {
            rnMeta.displayName(Component.text("§e§l[ĐỔI TÊN THÚ CƯNG 🏷]"));
            List<Component> rnLore = new ArrayList<>();
            rnLore.add(Component.text("§7Tùy biến biệt danh cho pet của bạn"));
            rnLore.add(Component.text("§e▶ Dùng lệnh: §6/pet rename <tên_mới>"));
            rnLore.add(Component.text("§7(Độ dài hợp lệ từ 2 đến 24 ký tự)"));
            rnMeta.lore(rnLore);
            rnMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "detail_action"), PersistentDataType.STRING, "rename_info:" + petId);
            renameBtn.setItemMeta(rnMeta);
        }
        inv.setItem(35, renameBtn);

        // Nút Quay lại (slot 40)
        ItemStack backBtn = new ItemStack(Material.BARRIER);
        ItemMeta bkMeta = backBtn.getItemMeta();
        if (bkMeta != null) {
            bkMeta.displayName(Component.text("§c§l◀ QUAY LẠI DANH SÁCH"));
            bkMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "detail_action"), PersistentDataType.STRING, "back");
            backBtn.setItemMeta(bkMeta);
        }
        inv.setItem(40, backBtn);

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.1f);
    }

    public static void openEvolutionMenu(Player p, String petId) {
        IpsecuzPet plugin = IpsecuzPet.getInstance();
        ConfigManager cm = plugin.getConfigManager();
        EvolutionManager evo = plugin.getEvolutionManager();

        int curStar = evo.getStar(p.getUniqueId(), petId);
        int maxStar = plugin.getModuleManager().getEvolutionConfig().getInt("max_stars", 5);
        int nextStar = Math.min(maxStar, curStar + 1);

        EvolutionHolder holder = new EvolutionHolder(petId);
        Inventory inv = Bukkit.createInventory(holder, 45, LegacyComponentSerializer.legacySection().deserialize(
                ChatColor.translateAlternateColorCodes('&', "&8✦ Tiến Hóa: " + curStar + "⭐ ➔ " + nextStar + "⭐")
        ));
        holder.setInventory(inv);

        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta bMeta = border.getItemMeta();
        if (bMeta != null) { bMeta.displayName(Component.text(" ")); border.setItemMeta(bMeta); }
        for (int i = 0; i < 45; i++) inv.setItem(i, border);

        // Slot 13: Thẻ So Sánh Chỉ Số (Current vs Next Star Preview)
        int lvl = cm.getData().getInt(p.getUniqueId() + ".pets." + petId + ".level", 1);
        int happy = cm.getData().getInt(p.getUniqueId() + ".pets." + petId + ".happiness", 100);
        String traitStr = cm.getData().getString(p.getUniqueId() + ".pets." + petId + ".trait");
        PetTrait trait = PetTrait.fromString(traitStr);

        double curHp = PetStatEngine.calculateStatPreview(plugin, petId, lvl, curStar, trait, happy, "health");
        double curDmg = PetStatEngine.calculateStatPreview(plugin, petId, lvl, curStar, trait, happy, "damage");
        double curDef = PetStatEngine.calculateStatPreview(plugin, petId, lvl, curStar, trait, happy, "defense");
        double curSpd = PetStatEngine.calculateStatPreview(plugin, petId, lvl, curStar, trait, happy, "speed");

        double nextHp = PetStatEngine.calculateStatPreview(plugin, petId, lvl, nextStar, trait, happy, "health");
        double nextDmg = PetStatEngine.calculateStatPreview(plugin, petId, lvl, nextStar, trait, happy, "damage");
        double nextDef = PetStatEngine.calculateStatPreview(plugin, petId, lvl, nextStar, trait, happy, "defense");
        double nextSpd = PetStatEngine.calculateStatPreview(plugin, petId, lvl, nextStar, trait, happy, "speed");

        ItemStack preview = new ItemStack(Material.NETHER_STAR);
        ItemMeta prevMeta = preview.getItemMeta();
        if (prevMeta != null) {
            prevMeta.displayName(Component.text("§e§lSO SÁNH TIẾN HÓA ⭐"));
            List<Component> pLore = new ArrayList<>();
            pLore.add(Component.text("§7Cấp sao: " + evo.getStarDisplay(curStar) + " §e➔ " + evo.getStarDisplay(nextStar)));
            pLore.add(Component.text("§8§m------------------------"));
            if (curStar < maxStar) {
                double hpDiff = ((nextHp / curHp) - 1.0) * 100.0;
                double dmgDiff = ((nextDmg / curDmg) - 1.0) * 100.0;
                double defDiff = ((nextDef / Math.max(0.1, curDef)) - 1.0) * 100.0;
                pLore.add(Component.text("§7Máu: §a" + String.format("%.1f", curHp) + " ❤ §e➔ §a" + String.format("%.1f", nextHp) + " ❤ " + (hpDiff > 0 ? "§a(+" + Math.round(hpDiff) + "%)" : "")));
                pLore.add(Component.text("§7Sát thương: §c" + String.format("%.1f", curDmg) + " ⚔ §e➔ §c" + String.format("%.1f", nextDmg) + " ⚔ " + (dmgDiff > 0 ? "§a(+" + Math.round(dmgDiff) + "%)" : "")));
                pLore.add(Component.text("§7Giáp: §9" + String.format("%.1f", curDef) + " 🛡 §e➔ §9" + String.format("%.1f", nextDef) + " 🛡 " + (defDiff > 0 ? "§a(+" + Math.round(defDiff) + "%)" : "")));
                if (nextSpd > curSpd) {
                    pLore.add(Component.text("§7Tốc độ: §f" + String.format("%.3f", curSpd) + " §e➔ §f" + String.format("%.3f", nextSpd)));
                } else {
                    pLore.add(Component.text("§7Tốc độ: §f" + String.format("%.3f", curSpd) + " §7(Không đổi)"));
                }
            } else {
                pLore.add(Component.text("§6§l✔ ĐÃ ĐẠT CẤP SAO TỐI ĐA!"));
            }
            prevMeta.lore(pLore);
            preview.setItemMeta(prevMeta);
        }
        inv.setItem(13, preview);

        // Slot 29: Danh sách kiểm tra điều kiện (Requirements Checklist)
        ItemStack checkItem = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta chMeta = checkItem.getItemMeta();
        boolean allMet = true;

        if (chMeta != null) {
            chMeta.displayName(Component.text("§6§lDANH SÁCH ĐIỀU KIỆN TIẾN HÓA"));
            List<Component> chLore = new ArrayList<>();

            if (curStar >= maxStar) {
                chLore.add(Component.text("§a✔ Đã đạt cấp sao tối đa!"));
                allMet = false;
            } else {
                FileConfiguration config = plugin.getModuleManager().getEvolutionConfig();
                ConfigurationSection reqSec = config.getConfigurationSection("requirements.star_" + nextStar);

                if (reqSec != null) {
                    org.ipsecuz.pet.requirement.RequirementGroup group = plugin.getRequirementManager().parse(reqSec);
                    org.ipsecuz.pet.requirement.RequirementContext ctx = new org.ipsecuz.pet.requirement.RequirementContext(p, petId, lvl, curStar, plugin);
                    org.ipsecuz.pet.requirement.RequirementCheckResult result = plugin.getRequirementManager().evaluate(group, ctx);
                    allMet = result.isSatisfied();
                    chLore.addAll(org.ipsecuz.pet.requirement.RequirementGuiRenderer.renderToComponents(result));
                } else {
                    chLore.add(Component.text("§a[✔] Miễn phí tiến hóa!"));
                }
            }
            chMeta.lore(chLore);
            checkItem.setItemMeta(chMeta);
        }
        inv.setItem(29, checkItem);

        // Slot 33: Nút Tiến Hóa (Action Button)
        if (curStar < maxStar) {
            ItemStack actionBtn = new ItemStack(allMet ? Material.LIME_CONCRETE : Material.RED_CONCRETE);
            ItemMeta aMeta = actionBtn.getItemMeta();
            if (aMeta != null) {
                aMeta.displayName(Component.text(allMet ? "§a§l[TIẾN HÓA NGAY! ⭐]" : "§c§l[CHƯA ĐỦ ĐIỀU KIỆN ✖]"));
                List<Component> aLore = new ArrayList<>();
                if (allMet) {
                    aLore.add(Component.text("§e▶ Nhấp để bắt đầu nghi thức tiến hóa MMORPG!"));
                } else {
                    aLore.add(Component.text("§7Vui lòng đáp ứng đầy đủ điều kiện ở danh sách bên trái."));
                }
                aMeta.lore(aLore);
                aMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "evo_action"), PersistentDataType.STRING, allMet ? ("do_evolve:" + petId) : "not_ready");
                actionBtn.setItemMeta(aMeta);
            }
            inv.setItem(33, actionBtn);
        }

        // Slot 40: Nút Quay lại
        ItemStack backBtn = new ItemStack(Material.BARRIER);
        ItemMeta bkMeta = backBtn.getItemMeta();
        if (bkMeta != null) {
            bkMeta.displayName(Component.text("§c§l◀ QUAY LẠI QUẢN LÝ"));
            bkMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "evo_action"), PersistentDataType.STRING, "back:" + petId);
            backBtn.setItemMeta(bkMeta);
        }
        inv.setItem(40, backBtn);

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1f);
    }

    public static void openConfirmDialog(Player p, String title, ItemStack displayItem, String confirmActionPayload) {
        IpsecuzPet plugin = IpsecuzPet.getInstance();
        ConfirmHolder holder = new ConfirmHolder(confirmActionPayload);
        Inventory inv = Bukkit.createInventory(holder, 27, LegacyComponentSerializer.legacySection().deserialize(
                ChatColor.translateAlternateColorCodes('&', title)
        ));
        holder.setInventory(inv);

        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bMeta = border.getItemMeta();
        if (bMeta != null) { bMeta.displayName(Component.text(" ")); border.setItemMeta(bMeta); }
        for (int i = 0; i < 27; i++) inv.setItem(i, border);

        // Slot 11: Nút Xác Nhận (Lime Concrete)
        ItemStack confirmBtn = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta cMeta = confirmBtn.getItemMeta();
        if (cMeta != null) {
            cMeta.displayName(Component.text("§a§l✔ XÁC NHẬN"));
            List<Component> cLore = new ArrayList<>();
            cLore.add(Component.text("§7Nhấp để hoàn tất thao tác."));
            cMeta.lore(cLore);
            cMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "confirm_dialog"), PersistentDataType.STRING, "accept");
            confirmBtn.setItemMeta(cMeta);
        }
        inv.setItem(11, confirmBtn);

        // Slot 13: Thẻ Thông Tin
        if (displayItem != null) {
            inv.setItem(13, displayItem);
        }

        // Slot 15: Nút Hủy Bỏ (Red Concrete)
        ItemStack cancelBtn = new ItemStack(Material.RED_CONCRETE);
        ItemMeta canMeta = cancelBtn.getItemMeta();
        if (canMeta != null) {
            canMeta.displayName(Component.text("§c§l✖ HỦY BỎ"));
            List<Component> canLore = new ArrayList<>();
            canLore.add(Component.text("§7Nhấp để hủy thao tác và quay lại."));
            canMeta.lore(canLore);
            canMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "confirm_dialog"), PersistentDataType.STRING, "cancel");
            cancelBtn.setItemMeta(canMeta);
        }
        inv.setItem(15, cancelBtn);

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1f);
    }

    public static void openCodexMenu(Player p) {
        openCodexMenu(p, 1);
    }

    public static void openCodexMenu(Player p, int page) {
        IpsecuzPet plugin = IpsecuzPet.getInstance();
        ConfigurationSection petsSec = plugin.getConfig().getConfigurationSection("pets");
        List<String> allPetKeys = petsSec != null ? new ArrayList<>(petsSec.getKeys(false)) : Collections.emptyList();

        int pageSize = 45;
        int totalPages = Math.max(1, (int) Math.ceil((double) allPetKeys.size() / pageSize));
        int curPage = Math.max(1, Math.min(page, totalPages));

        CodexHolder holder = new CodexHolder(curPage);
        int discoveredCount = plugin.getCodexManager().getDiscoveredCount(p.getUniqueId());
        String titleStr = "&1✦ Bách Khoa Pet: &e" + discoveredCount + "/" + allPetKeys.size() + " &8[" + curPage + "/" + totalPages + "]";
        Inventory inv = Bukkit.createInventory(holder, 54, LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&', titleStr)));
        holder.setInventory(inv);

        int start = (curPage - 1) * pageSize;
        int end = Math.min(start + pageSize, allPetKeys.size());

        for (int i = start; i < end; i++) {
            String petId = allPetKeys.get(i);
            boolean discovered = plugin.getCodexManager().hasDiscovered(p.getUniqueId(), petId);
            PetRarity rarity = PetRarity.fromPetId(plugin, petId);

            ItemStack item;
            if (discovered) {
                String matStr = plugin.getConfig().getString("pets." + petId + ".icon", "STONE");
                item = plugin.getItemHookManager().getItem(matStr, Material.STONE);
            } else {
                item = new ItemStack(Material.GRAY_DYE);
            }

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String name = plugin.getConfig().getString("pets." + petId + ".name", petId);
                if (discovered) {
                    meta.displayName(LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&', "&a✔ " + name)));
                } else {
                    meta.displayName(Component.text("§8??? [Chưa khám phá]"));
                }

                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("§7Độ hiếm: " + rarity.getFormattedName()));
                if (discovered) {
                    lore.add(Component.text("§aĐã ghi danh vào Bách Khoa Toàn Thư!"));
                    double hp = plugin.getConfig().getDouble("pets." + petId + ".stats.health", 20.0);
                    double dmg = plugin.getConfig().getDouble("pets." + petId + ".stats.damage", 5.0);
                    lore.add(Component.text("§7Máu gốc: §a" + hp + " ❤ §7| Dame gốc: §c" + dmg + " ⚔"));
                } else {
                    lore.add(Component.text("§7Mở khóa qua: Ấp Trứng, Cửa Hàng hoặc Bắt thú hoang."));
                }
                meta.lore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(i - start, item);
        }

        ItemStack navGlass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta ngMeta = navGlass.getItemMeta();
        if (ngMeta != null) { ngMeta.displayName(Component.text(" ")); navGlass.setItemMeta(ngMeta); }
        for (int s = 45; s < 54; s++) inv.setItem(s, navGlass);

        if (curPage > 1) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta pMeta = prev.getItemMeta();
            if (pMeta != null) {
                pMeta.displayName(Component.text("§e◀ Trang " + (curPage - 1)));
                pMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "nav_codex_page"), PersistentDataType.INTEGER, curPage - 1);
                prev.setItemMeta(pMeta);
            }
            inv.setItem(45, prev);
        }

        ItemStack back = new ItemStack(Material.LEAD);
        ItemMeta bMeta = back.getItemMeta();
        if (bMeta != null) {
            bMeta.displayName(Component.text("§b◀ Menu Pet Của Tôi"));
            bMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "btn_action"), PersistentDataType.STRING, "open_pet_menu");
            back.setItemMeta(bMeta);
        }
        inv.setItem(49, back);

        if (curPage < totalPages) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nMeta = next.getItemMeta();
            if (nMeta != null) {
                nMeta.displayName(Component.text("§eTrang " + (curPage + 1) + " ▶"));
                nMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "nav_codex_page"), PersistentDataType.INTEGER, curPage + 1);
                next.setItemMeta(nMeta);
            }
            inv.setItem(53, next);
        }

        p.openInventory(inv);
    }

    public static void openShardsMenu(Player p) {
        IpsecuzPet plugin = IpsecuzPet.getInstance();
        ShardsHolder holder = new ShardsHolder();
        Inventory inv = Bukkit.createInventory(holder, 45, Component.text("§1✦ Kho Mảnh Thú Cưng (Shards) ✦"));
        holder.setInventory(inv);

        ItemStack border = new ItemStack(Material.CYAN_STAINED_GLASS_PANE);
        ItemMeta bMeta = border.getItemMeta();
        if (bMeta != null) { bMeta.displayName(Component.text(" ")); border.setItemMeta(bMeta); }
        for (int i = 0; i < 45; i++) {
            if (i < 9 || i >= 36 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, border);
            }
        }

        ConfigurationSection shardsSec = plugin.getConfigManager().getData().getConfigurationSection(p.getUniqueId() + ".shards");
        int slot = 10;
        if (shardsSec != null) {
            for (String petId : shardsSec.getKeys(false)) {
                int count = shardsSec.getInt(petId, 0);
                if (count <= 0) continue;

                String matStr = plugin.getConfig().getString("pets." + petId + ".icon", "PRISMARINE_SHARD");
                ItemStack item = plugin.getItemHookManager().getItem(matStr, Material.PRISMARINE_SHARD);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    String name = plugin.getConfig().getString("pets." + petId + ".name", petId);
                    PetRarity rarity = PetRarity.fromPetId(plugin, petId);
                    meta.displayName(LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&', "&bMảnh: " + name)));
                    List<Component> lore = new ArrayList<>();
                    lore.add(Component.text("§7Độ hiếm: " + rarity.getFormattedName()));
                    lore.add(Component.text("§7Số mảnh hiện có: §b" + count + "/50"));
                    if (count >= 50) {
                        lore.add(Component.text("§a§l✔ ĐỦ MẢNH! Nhấp để ghép thành Pet!"));
                    } else {
                        lore.add(Component.text("§c✖ Cần thêm " + (50 - count) + " mảnh để ghép."));
                    }
                    meta.lore(lore);
                    meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "craft_shard_id"), PersistentDataType.STRING, petId);
                    item.setItemMeta(meta);
                }
                inv.setItem(slot, item);
                slot++;
                if (slot % 9 == 8) slot += 2;
                if (slot >= 35) break;
            }
        }

        if (slot == 10) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta eMeta = empty.getItemMeta();
            if (eMeta != null) {
                eMeta.displayName(Component.text("§cChưa Có Mảnh Nào"));
                List<Component> eLore = new ArrayList<>();
                eLore.add(Component.text("§7Khi quay trúng Pet đã sở hữu trong Lò Ấp,"));
                eLore.add(Component.text("§7bạn sẽ nhận được Mảnh Pet tương ứng!"));
                eMeta.lore(eLore);
                empty.setItemMeta(eMeta);
            }
            inv.setItem(22, empty);
        }

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bkMeta = back.getItemMeta();
        if (bkMeta != null) {
            bkMeta.displayName(Component.text("§b◀ Menu Pet Của Tôi"));
            bkMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "btn_action"), PersistentDataType.STRING, "open_pet_menu");
            back.setItemMeta(bkMeta);
        }
        inv.setItem(40, back);

        p.openInventory(inv);
    }

    public static void openShopMenu(Player p) {
        openShopMenu(p, 1);
    }

    public static void openShopMenu(Player p, int page) {
        IpsecuzPet plugin = IpsecuzPet.getInstance();
        ConfigManager cm = plugin.getConfigManager();
        LanguageManager lang = plugin.getLanguage();

        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("pets");
        if (sec == null) return;

        List<String> allKeys = new ArrayList<>();
        for (String k : sec.getKeys(false)) {
            boolean enabled = plugin.getConfig().getBoolean("pets." + k + ".enabled", true);
            boolean visibleInShop = plugin.getConfig().getBoolean("pets." + k + ".visible_in_shop", true);
            if (enabled && visibleInShop) {
                allKeys.add(k);
            }
        }
        int pageSize = 45;
        int totalPages = Math.max(1, (int) Math.ceil((double) allKeys.size() / pageSize));
        int curPage = Math.max(1, Math.min(page, totalPages));

        ShopMenuHolder holder = new ShopMenuHolder(curPage);
        String rawTitle = lang.getMessage("gui.shop_title") + " &8[" + curPage + "/" + totalPages + "]";
        Inventory inv = Bukkit.createInventory(holder, 54, LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&', rawTitle)));
        holder.setInventory(inv);

        int startIndex = (curPage - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, allKeys.size());

        for (int i = startIndex; i < endIndex; i++) {
            String key = allKeys.get(i);
            String matStr = plugin.getConfig().getString("pets." + key + ".icon", "STONE");
            ItemStack item = plugin.getItemHookManager().getItem(matStr, Material.STONE);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            String name = plugin.getConfig().getString("pets." + key + ".name", key);
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&', name)));

            List<Component> lore = new ArrayList<>();
            String price = plugin.getCurrencyManager().getPriceDisplay(key);
            PetRarity rarity = PetRarity.fromPetId(plugin, key);

            lore.add(Component.text("§7Độ hiếm: " + rarity.getFormattedName()));
            double hp = PetStatEngine.getBaseStatAtLevel(plugin, key, 1, "health");
            double dmg = PetStatEngine.getBaseStatAtLevel(plugin, key, 1, "damage");
            double def = PetStatEngine.getBaseStatAtLevel(plugin, key, 1, "defense");
            double spd = PetStatEngine.getBaseStatAtLevel(plugin, key, 1, "speed");
            lore.add(Component.text("§7Chỉ số cơ bản (Lv.1):"));
            lore.add(Component.text("§7Máu: §a" + String.format("%.1f", hp) + " ❤ §7| Sát thương: §c" + String.format("%.1f", dmg) + " ⚔"));
            lore.add(Component.text("§7Giáp: §9" + String.format("%.1f", def) + " 🛡 §7| Tốc độ: §f" + String.format("%.3f", spd)));
            lore.add(Component.text("§7--------------------"));

            if (cm.getData().contains(p.getUniqueId() + ".pets." + key)) {
                lore.add(Component.text("§a✔ ĐÃ SỞ HỮU"));
            } else {
                org.bukkit.configuration.ConfigurationSection petSec = plugin.getConfig().getConfigurationSection("pets." + key);
                if (petSec != null && (petSec.contains("requirements") || petSec.contains("all") || petSec.contains("one_of"))) {
                    org.ipsecuz.pet.requirement.RequirementGroup group = plugin.getRequirementManager().parse(petSec);
                    org.ipsecuz.pet.requirement.RequirementContext ctx = new org.ipsecuz.pet.requirement.RequirementContext(p, key, 1, 1, plugin);
                    org.ipsecuz.pet.requirement.RequirementCheckResult res = plugin.getRequirementManager().evaluate(group, ctx);
                    lore.addAll(org.ipsecuz.pet.requirement.RequirementGuiRenderer.renderToComponents(res));
                } else {
                    lore.add(LegacyComponentSerializer.legacySection().deserialize(
                            ChatColor.translateAlternateColorCodes('&', lang.getMessage("gui.lore_price", "%cost%", price))));
                }
                lore.add(Component.text("§e▶ Nhấp để Mua ngay!"));
            }

            meta.lore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "shop_id"), PersistentDataType.STRING, key);
            item.setItemMeta(meta);
            inv.setItem(i - startIndex, item);
        }

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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (isPluginMenu(e.getView().getTopInventory())) {
            if (e.getView().getTopInventory().getHolder() instanceof TradeSession.TradeHolder holder) {
                if (!(e.getWhoClicked() instanceof Player p)) {
                    e.setCancelled(true);
                    return;
                }
                TradeSession session = holder.getSession();
                boolean affectsTop = false;
                for (int slot : e.getRawSlots()) {
                    if (slot < 54) {
                        affectsTop = true;
                        boolean allowed = p.equals(session.getPlayerA()) ? TradeSession.SLOTS_A.contains(slot) :
                                          p.equals(session.getPlayerB()) && TradeSession.SLOTS_B.contains(slot);
                        if (!allowed) {
                            e.setCancelled(true);
                            return;
                        }
                    }
                }
                if (affectsTop) {
                    ItemStack dragged = e.getOldCursor();
                    PetCardSecurity.CardValidationResult res = PetCardSecurity.validateAndExtractCard(plugin, dragged);
                    if (!res.isValid() || (res.getCardData().getCardUniqueId() != null && PetCardSecurity.isCardConsumed(plugin, res.getCardData().getCardUniqueId()))) {
                        e.setCancelled(true);
                        p.sendMessage("§cChỉ có thể đặt Thẻ Pet hợp lệ (chưa bị tiêu hao) vào khung giao dịch!");
                        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                        return;
                    }
                    session.resetLocks();
                }
            } else {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof HatchingManager.RouletteHolder holder) {
            // FIX: Do not instantly grant reward when player closes GUI!
            // Background scheduler will finish naturally and safely deliver reward.
        } else if (e.getInventory().getHolder() instanceof TradeSession.TradeHolder holder) {
            TradeSession session = holder.getSession();
            if (!session.isFinished()) {
                session.cancel(e.getPlayer().getName() + " đã đóng giao diện giao dịch.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        Inventory topInv = e.getView().getTopInventory();
        if (!isPluginMenu(topInv)) return;

        // XỬ LÝ RIÊNG BIỆT CHO GIAO DỊCH HAI CHIỀU (TRADE GUI)
        if (topInv.getHolder() instanceof TradeSession.TradeHolder holder) {
            TradeSession session = holder.getSession();
            int rawSlot = e.getRawSlot();

            // Chặn các click đặc biệt có thể bypass (Number key swap, Swap hand, Double click)
            if (e.getClick() == ClickType.NUMBER_KEY || e.getClick() == ClickType.SWAP_OFFHAND || e.getClick() == ClickType.DOUBLE_CLICK) {
                e.setCancelled(true);
                return;
            }

            if (rawSlot >= 0 && rawSlot < 54) {
                // Click trong khung giao dịch phía trên
                if (rawSlot == 38 && p.equals(session.getPlayerA())) {
                    e.setCancelled(true);
                    session.toggleLock(p);
                    return;
                } else if (rawSlot == 42 && p.equals(session.getPlayerB())) {
                    e.setCancelled(true);
                    session.toggleLock(p);
                    return;
                }

                boolean isPlayerASlot = TradeSession.SLOTS_A.contains(rawSlot);
                boolean isPlayerBSlot = TradeSession.SLOTS_B.contains(rawSlot);

                boolean canTouch = (p.equals(session.getPlayerA()) && isPlayerASlot) ||
                                   (p.equals(session.getPlayerB()) && isPlayerBSlot);

                if (!canTouch) {
                    e.setCancelled(true);
                    return;
                }

                // Nếu đang đặt item vào slot của mình (cursor không rỗng)
                ItemStack cursor = e.getCursor();
                if (cursor != null && cursor.getType() != Material.AIR) {
                    PetCardSecurity.CardValidationResult res = PetCardSecurity.validateAndExtractCard(plugin, cursor);
                    if (!res.isValid() || (res.getCardData().getCardUniqueId() != null && PetCardSecurity.isCardConsumed(plugin, res.getCardData().getCardUniqueId()))) {
                        e.setCancelled(true);
                        p.sendMessage("§cChỉ có thể đặt Thẻ Pet hợp lệ (chưa bị tiêu hao) vào khung giao dịch!");
                        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                        return;
                    }
                }

                session.resetLocks();
                return; // Cho phép tương tác bình thường trong slot của chính mình
            } else {
                // Click trong rương cá nhân của người chơi khi mở Trade GUI (rawSlot >= 54)
                if (e.isShiftClick()) {
                    e.setCancelled(true); // Chặn shift-click mặc định để tránh chèn nhầm ô hệ thống
                    ItemStack clicked = e.getCurrentItem();
                    if (clicked != null && clicked.getType() != Material.AIR) {
                        PetCardSecurity.CardValidationResult res = PetCardSecurity.validateAndExtractCard(plugin, clicked);
                        if (!res.isValid() || (res.getCardData().getCardUniqueId() != null && PetCardSecurity.isCardConsumed(plugin, res.getCardData().getCardUniqueId()))) {
                            p.sendMessage("§cChỉ có thể đưa Thẻ Pet hợp lệ (chưa bị tiêu hao) vào khung giao dịch!");
                            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                            return;
                        }

                        // Tìm ô trống hợp lệ trong khu vực của người chơi
                        Set<Integer> targetSlots = p.equals(session.getPlayerA()) ? TradeSession.SLOTS_A : TradeSession.SLOTS_B;
                        Integer emptySlot = null;
                        for (int slot : targetSlots) {
                            ItemStack existing = topInv.getItem(slot);
                            if (existing == null || existing.getType() == Material.AIR) {
                                emptySlot = slot;
                                break;
                            }
                        }

                        if (emptySlot != null) {
                            topInv.setItem(emptySlot, clicked.clone());
                            e.setCurrentItem(null);
                            session.resetLocks();
                            p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1f, 1.2f);
                        } else {
                            p.sendMessage("§cKhung đề nghị giao dịch của bạn đã đầy!");
                        }
                    }
                    return;
                }
                // Click bình thường trong rương cá nhân: cho phép sắp xếp đồ
                return;
            }
        }

        // BẢO VỆ TUYỆT ĐỐI CHO CÁC MENU KHÁC
        e.setCancelled(true);

        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(topInv)) {
            return;
        }

        ItemStack clickedItem = e.getCurrentItem();
        if (clickedItem == null || clickedItem.getItemMeta() == null) return;
        ItemMeta meta = clickedItem.getItemMeta();

        // 0.1 Xử lý giao diện Tiến Hóa Chuyên Sâu (EvolutionHolder)
        if (topInv.getHolder() instanceof EvolutionHolder) {
            NamespacedKey evoKey = new NamespacedKey(plugin, "evo_action");
            if (meta.getPersistentDataContainer().has(evoKey, PersistentDataType.STRING)) {
                String action = meta.getPersistentDataContainer().get(evoKey, PersistentDataType.STRING);
                if (action != null) {
                    if (action.startsWith("do_evolve:")) {
                        String petId = action.substring("do_evolve:".length());
                        ItemStack confirmIcon = topInv.getItem(13);
                        if (confirmIcon == null) confirmIcon = new ItemStack(Material.NETHER_STAR);
                        openConfirmDialog(p, "&0Xác Nhận Tiến Hóa Pet", confirmIcon.clone(), "confirm_evolve:" + petId);
                    } else if (action.startsWith("back:")) {
                        String petId = action.substring("back:".length());
                        openPetDetailMenu(p, petId);
                    } else if ("not_ready".equals(action)) {
                        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    }
                }
            }
            return;
        }

        // 0.2 Xử lý hộp thoại Xác Nhận (ConfirmHolder)
        if (topInv.getHolder() instanceof ConfirmHolder confirmHolder) {
            NamespacedKey confKey = new NamespacedKey(plugin, "confirm_dialog");
            if (meta.getPersistentDataContainer().has(confKey, PersistentDataType.STRING)) {
                String choice = meta.getPersistentDataContainer().get(confKey, PersistentDataType.STRING);
                if ("cancel".equals(choice)) {
                    p.closeInventory();
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 0.8f);
                } else if ("accept".equals(choice)) {
                    String payload = confirmHolder.getActionPayload();
                    if (payload != null && payload.startsWith("confirm_buy:")) {
                        String shopId = payload.substring("confirm_buy:".length());
                        if (plugin.getConfigManager().getData().contains(p.getUniqueId() + ".pets." + shopId)) {
                            p.sendMessage(plugin.getLanguage().getMessage("pet.already_owned"));
                            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                            p.closeInventory();
                            return;
                        }
                        if (!plugin.getOwnershipManager().canAcquirePet(p)) {
                            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                            p.closeInventory();
                            return;
                        }
                        if (plugin.getCurrencyManager().processTransaction(p, shopId)) {
                            plugin.getConfigManager().createPetDataIfMissing(p.getUniqueId(), shopId);
                            PetTrait trait = PetTrait.rollRandomTrait();
                            plugin.getConfigManager().getData().set(p.getUniqueId() + ".pets." + shopId + ".trait", trait.name());
                            plugin.getConfigManager().saveData();
                            plugin.getCodexManager().discover(p.getUniqueId(), shopId);

                            p.sendMessage(plugin.getLanguage().getMessage("pet.buy_success", "%pet_name%", plugin.getConfig().getString("pets." + shopId + ".name", shopId)));
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                            openShopMenu(p, 1);
                        } else {
                            p.closeInventory();
                        }
                    } else if (payload != null && payload.startsWith("confirm_withdraw:")) {
                        String petId = payload.substring("confirm_withdraw:".length());
                        p.closeInventory();
                        if (!p.hasPermission("ipsecuzpet.withdraw")) {
                            p.sendMessage(plugin.getLanguage().getMessage("general.no_permission"));
                            return;
                        }
                        p.performCommand("pet withdraw " + petId);
                    } else if (payload != null && payload.startsWith("confirm_evolve:")) {
                        String petId = payload.substring("confirm_evolve:".length());
                        if (plugin.getEvolutionManager().upgradeStar(p, petId)) {
                            openEvolutionMenu(p, petId);
                        } else {
                            p.closeInventory();
                        }
                    }
                }
            }
            return;
        }

        // 1. Chuyển trang Menu Pet
        NamespacedKey navKey = new NamespacedKey(plugin, "nav_page");
        if (meta.getPersistentDataContainer().has(navKey, PersistentDataType.INTEGER)) {
            Integer targetPage = meta.getPersistentDataContainer().get(navKey, PersistentDataType.INTEGER);
            if (targetPage != null) openPetMenu(p, targetPage);
            return;
        }

        // 2. Chuyển trang Shop
        NamespacedKey navShopKey = new NamespacedKey(plugin, "nav_shop_page");
        if (meta.getPersistentDataContainer().has(navShopKey, PersistentDataType.INTEGER)) {
            Integer targetPage = meta.getPersistentDataContainer().get(navShopKey, PersistentDataType.INTEGER);
            if (targetPage != null) openShopMenu(p, targetPage);
            return;
        }

        // 2.5 Chuyển trang Codex
        NamespacedKey navCodexKey = new NamespacedKey(plugin, "nav_codex_page");
        if (meta.getPersistentDataContainer().has(navCodexKey, PersistentDataType.INTEGER)) {
            Integer targetPage = meta.getPersistentDataContainer().get(navCodexKey, PersistentDataType.INTEGER);
            if (targetPage != null) openCodexMenu(p, targetPage);
            return;
        }

        // 3. Nút công cụ chuyển menu (btn_action)
        NamespacedKey btnKey = new NamespacedKey(plugin, "btn_action");
        if (meta.getPersistentDataContainer().has(btnKey, PersistentDataType.STRING)) {
            String action = meta.getPersistentDataContainer().get(btnKey, PersistentDataType.STRING);
            if ("open_shop".equals(action)) {
                if (!p.hasPermission("ipsecuzpet.shop")) {
                    p.sendMessage(plugin.getLanguage().getMessage("general.no_permission"));
                    return;
                }
                openShopMenu(p, 1);
            } else if ("open_hatch".equals(action)) {
                if (!p.hasPermission("ipsecuzpet.hatch")) {
                    p.sendMessage(plugin.getLanguage().getMessage("general.no_permission"));
                    return;
                }
                plugin.getHatchingManager().openHatchingGui(p);
            } else if ("open_pet_menu".equals(action)) {
                openPetMenu(p, 1);
            } else if ("open_codex".equals(action)) {
                if (!p.hasPermission("ipsecuzpet.codex")) {
                    p.sendMessage(plugin.getLanguage().getMessage("general.no_permission"));
                    return;
                }
                openCodexMenu(p, 1);
            } else if ("open_shards".equals(action)) {
                if (!p.hasPermission("ipsecuzpet.shards")) {
                    p.sendMessage(plugin.getLanguage().getMessage("general.no_permission"));
                    return;
                }
                openShardsMenu(p);
            }
            return;
        }

        // 3.5 Ghép Mảnh Pet (craft_shard_id)
        NamespacedKey craftShardKey = new NamespacedKey(plugin, "craft_shard_id");
        if (meta.getPersistentDataContainer().has(craftShardKey, PersistentDataType.STRING)) {
            if (!p.hasPermission("ipsecuzpet.shards")) {
                p.sendMessage(plugin.getLanguage().getMessage("general.no_permission"));
                return;
            }
            String petId = meta.getPersistentDataContainer().get(craftShardKey, PersistentDataType.STRING);
            if (petId != null) {
                int shards = plugin.getShardManager().getShards(p.getUniqueId(), petId);
                int reqShards = plugin.getShardManager().getRequiredShardsToCraft(petId);
                if (shards < reqShards) {
                    p.sendMessage("§cBạn chưa đủ " + reqShards + " mảnh! Hiện có: §e" + shards + "/" + reqShards);
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }
                if (!plugin.getOwnershipManager().canAcquirePet(p)) {
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }
                plugin.getShardManager().takeShards(p.getUniqueId(), petId, reqShards);
                plugin.getConfigManager().createPetDataIfMissing(p.getUniqueId(), petId);
                PetTrait trait = PetTrait.rollRandomTrait();
                plugin.getConfigManager().getData().set(p.getUniqueId() + ".pets." + petId + ".trait", trait.name());
                plugin.getConfigManager().saveData();
                plugin.getCodexManager().discover(p.getUniqueId(), petId);

                p.sendMessage("§a§lTHÀNH CÔNG! §fĐã ghép thành công " + reqShards + " mảnh thành Pet mới!");
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                openShardsMenu(p);
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
                String active = plugin.getPetManager().getActivePetId(p.getUniqueId());
                if (active != null && active.equals(petId)) {
                    plugin.getPetManager().spawnPet(p, petId);
                }
                openPetDetailMenu(p, petId);
            } else if ("feed".equals(action) && parts.length > 1) {
                String petId = parts[1];
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (plugin.getFeedingManager().feedPet(p, petId, hand)) {
                    openPetDetailMenu(p, petId);
                }
            } else if ("ultimate".equals(action)) {
                if (!p.hasPermission("ipsecuzpet.skill")) {
                    p.sendMessage(plugin.getLanguage().getMessage("general.no_permission"));
                    return;
                }
                String petId = (parts.length > 1) ? parts[1] : null;
                String active = plugin.getPetManager().getActivePetId(p.getUniqueId());
                if (petId != null && (active == null || !active.equals(petId))) {
                    p.sendMessage("§cBạn cần triệu hồi pet này trước để kích hoạt tuyệt chiêu chiến đấu!");
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }
                plugin.getSkillManager().triggerUltimate(p);
                p.closeInventory();
            } else if ("learn_skill".equals(action) && parts.length > 2) {
                if (!p.hasPermission("ipsecuzpet.skill")) {
                    p.sendMessage(plugin.getLanguage().getMessage("general.no_permission"));
                    return;
                }
                String skillType = parts[1];
                String petId = parts[2];
                if (plugin.getSkillManager().unlockSkill(p, petId, skillType)) {
                    openPetDetailMenu(p, petId);
                }
            } else if ("open_evolution".equals(action) && parts.length > 1) {
                if (!p.hasPermission("ipsecuzpet.evolution")) {
                    p.sendMessage(plugin.getLanguage().getMessage("general.no_permission"));
                    return;
                }
                String petId = parts[1];
                openEvolutionMenu(p, petId);
            } else if ("confirm_withdraw_prompt".equals(action) && parts.length > 1) {
                if (!p.hasPermission("ipsecuzpet.withdraw")) {
                    p.sendMessage(plugin.getLanguage().getMessage("general.no_permission"));
                    return;
                }
                String petId = parts[1];
                ItemStack wdIcon = clickedItem.clone();
                openConfirmDialog(p, "&0Xác Nhận Rút Thẻ Pet", wdIcon, "confirm_withdraw:" + petId);
            } else if ("rename_info".equals(action) && parts.length > 1) {
                p.closeInventory();
                p.sendMessage("§e[IpsecuzPet] Hãy dùng lệnh: §6/pet rename <tên_mới> §eđể đổi tên cho thú cưng này!");
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
            }
            return;
        }

        // 5. Click Pet trong Menu Chính
        NamespacedKey petKey = new NamespacedKey(plugin, "pet_id");
        if (meta.getPersistentDataContainer().has(petKey, PersistentDataType.STRING)) {
            String petId = meta.getPersistentDataContainer().get(petKey, PersistentDataType.STRING);
            if (petId == null) return;

            if (e.getClick() == ClickType.RIGHT) {
                openPetDetailMenu(p, petId);
            } else {
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

        // 6. Mua Pet trong Menu Shop
        NamespacedKey shopKey = new NamespacedKey(plugin, "shop_id");
        if (meta.getPersistentDataContainer().has(shopKey, PersistentDataType.STRING)) {
            String shopId = meta.getPersistentDataContainer().get(shopKey, PersistentDataType.STRING);
            if (shopId != null) {
                if (plugin.getConfigManager().getData().contains(p.getUniqueId() + ".pets." + shopId)) {
                    p.sendMessage(plugin.getLanguage().getMessage("pet.already_owned"));
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }

                if (!plugin.getOwnershipManager().canAcquirePet(p)) {
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }

                ItemStack buyCard = clickedItem.clone();
                String name = plugin.getConfig().getString("pets." + shopId + ".name", shopId);
                openConfirmDialog(p, "&0Xác Nhận Mua: " + name, buyCard, "confirm_buy:" + shopId);
            }
            return;
        }

        // 7. Click Trứng trong Menu Lò Ấp
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