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
        return GuiText.progressBar(current, max, totalBars);
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
        Inventory inv = Bukkit.createInventory(holder, 54, GuiText.title(rawTitle));
        holder.setInventory(inv);

        int startIndex = (curPage - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, ownedPets.size());

        for (int i = startIndex; i < endIndex; i++) {
            String petId = ownedPets.get(i);
            String matStr = plugin.getConfig().getString("pets." + petId + ".icon", "STONE");
            String displayName = lang.getPetDisplayName(p.getUniqueId(), petId);

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

            FeedingManager.HappinessState happyState = (plugin.getFeedingManager() != null)
                    ? plugin.getFeedingManager().getHappinessState(happy) : FeedingManager.HappinessState.CONTENT;

            String active = plugin.getPetManager().getActivePetId(p.getUniqueId());
            boolean isActive = active != null && active.equals(petId);

            GuiLoreBuilder lore = GuiLoreBuilder.create()
                    .stat(lang.getMessage("gui.lore_rarity"), rarity.getLocalizedName(plugin))
                    .stat(lang.getMessage("gui.lore_level"), "Lv." + lvl + (lvl >= maxLvl ? " " + lang.getMessage("gui.lore_max") : (" §8(" + exp + "/" + reqExp + ")")));

            if (lvl < maxLvl) {
                lore.raw("§7" + lang.getMessage("gui.lore_exp") + ": " + GuiText.progressBar(exp, reqExp, 10));
            }

            lore.stat(lang.getMessage("gui.lore_stars"), ((plugin.getEvolutionManager() != null) ? plugin.getEvolutionManager().getStarDisplay(star) : (star + "⭐")))
                .stat(lang.getMessage("gui.lore_trait"), trait.getLocalizedName(plugin))
                .stat(lang.getMessage("gui.lore_form"), isBaby ? lang.getMessage("gui.form_baby") : lang.getMessage("gui.form_adult"))
                .stat(lang.getMessage("gui.lore_happiness"), "§d♡ " + happy + "% §8(" + happyState.getLocalizedDisplay(plugin) + "§8)")
                .separator();

            if (isActive) {
                lore.status(true, lang.getMessage("gui.status_active"));
            } else {
                lore.status(false, lang.getMessage("gui.status_resting"));
                lore.action(lang.getMessage("gui.action_summon"));
            }
            lore.action(lang.getMessage("gui.action_detail"));

            ItemStack item = GuiItemBuilder.from(matStr, Material.STONE)
                    .name(displayName)
                    .lore(lore)
                    .pdc("pet_id", petId)
                    .build();

            inv.setItem(i - startIndex, item);
        }

        // Thanh điều hướng đáy (slots 45-53)
        ItemStack navGlass = GuiItemBuilder.from(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int s = 45; s < 54; s++) inv.setItem(s, navGlass);

        if (curPage > 1) {
            ItemStack prev = GuiItemBuilder.from(Material.ARROW)
                    .name(lang.getMessage("gui.btn_prev_page_num", "%page%", String.valueOf(curPage - 1), "%total%", String.valueOf(totalPages)))
                    .pdc("nav_page", curPage - 1)
                    .build();
            inv.setItem(45, prev);
        }

        // Nút Codex (slot 46)
        ItemStack codexBtn = GuiItemBuilder.from(Material.KNOWLEDGE_BOOK)
                .name(lang.getMessage("gui.btn_codex"))
                .lore(GuiLoreBuilder.create()
                        .description(lang.getMessage("gui.btn_codex_desc"))
                        .action(lang.getMessage("gui.btn_codex_action")))
                .pdc("btn_action", "open_codex")
                .build();
            inv.setItem(46, codexBtn);

        // Nút Shards (slot 47)
        ItemStack shardsBtn = GuiItemBuilder.from(Material.PRISMARINE_SHARD)
                .name(lang.getMessage("gui.btn_shards"))
                .lore(GuiLoreBuilder.create()
                        .description(lang.getMessage("gui.btn_shards_desc"))
                        .action(lang.getMessage("gui.btn_shards_action")))
                .pdc("btn_action", "open_shards")
                .build();
        inv.setItem(47, shardsBtn);

        // Nút Cửa Hàng (slot 49)
        ItemStack shopBtn = GuiItemBuilder.from(Material.EMERALD)
                .name(lang.getMessage("gui.btn_shop"))
                .lore(GuiLoreBuilder.create()
                        .description(lang.getMessage("gui.btn_shop_desc"))
                        .action(lang.getMessage("gui.btn_shop_action")))
                .pdc("btn_action", "open_shop")
                .build();
        inv.setItem(49, shopBtn);

        // Nút Lò Ấp Trứng (slot 51)
        ItemStack hatchBtn = GuiItemBuilder.from(Material.DRAGON_EGG)
                .name(lang.getMessage("gui.btn_hatch"))
                .lore(GuiLoreBuilder.create()
                        .description(lang.getMessage("gui.btn_hatch_desc"))
                        .action(lang.getMessage("gui.btn_hatch_action")))
                .pdc("btn_action", "open_hatch")
                .build();
        inv.setItem(51, hatchBtn);

        if (curPage < totalPages) {
            ItemStack next = GuiItemBuilder.from(Material.ARROW)
                    .name(lang.getMessage("gui.btn_next_page_num", "%page%", String.valueOf(curPage + 1), "%total%", String.valueOf(totalPages)))
                    .pdc("nav_page", curPage + 1)
                    .build();
            inv.setItem(53, next);
        }

        p.openInventory(inv);
    }

    public static void openPetDetailMenu(Player p, String petId) {
        IpsecuzPet plugin = IpsecuzPet.getInstance();
        ConfigManager cm = plugin.getConfigManager();
        LanguageManager lang = plugin.getLanguage();

        PetDetailHolder holder = new PetDetailHolder(petId);
        String name = lang.getPetDisplayName(p.getUniqueId(), petId);

        String rawTitle = lang.getMessage("gui.detail_title", "%pet_name%", name);
        Inventory inv = Bukkit.createInventory(holder, 45, GuiText.title(rawTitle));
        holder.setInventory(inv);

        ItemStack border = GuiItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 45; i++) inv.setItem(i, border);

        // Icon chính (slot 13)
        String matStr = plugin.getConfig().getString("pets." + petId + ".icon", "STONE");
        int lvl = cm.getData().getInt(p.getUniqueId() + ".pets." + petId + ".level", 1);
        int exp = cm.getData().getInt(p.getUniqueId() + ".pets." + petId + ".exp", 0);
        int reqExp = lvl * plugin.getConfig().getInt("rpg_system.base_exp_requirement", 50);
        int star = (plugin.getEvolutionManager() != null) ? plugin.getEvolutionManager().getStar(p.getUniqueId(), petId) : 1;
        boolean isBaby = cm.isPetBaby(p.getUniqueId(), petId);
        int happy = (plugin.getFeedingManager() != null) ? plugin.getFeedingManager().getHappiness(p.getUniqueId(), petId) : 100;
        String traitName = cm.getData().getString(p.getUniqueId() + ".pets." + petId + ".trait", "NONE");
        PetTrait trait = PetTrait.fromString(traitName);
        PetRarity rarity = PetRarity.fromPetId(plugin, petId);

        FeedingManager.HappinessState happyState = (plugin.getFeedingManager() != null)
                ? plugin.getFeedingManager().getHappinessState(happy) : FeedingManager.HappinessState.CONTENT;

        GuiLoreBuilder petLore = GuiLoreBuilder.create()
                .stat(lang.getMessage("gui.lore_id"), petId)
                .stat(lang.getMessage("gui.lore_rarity"), rarity.getLocalizedName(plugin));

        if (plugin.getModelProviderManager() != null) {
            var provider = plugin.getModelProviderManager().resolveProviderForPet(petId);
            if (provider != null && provider.getType() != org.ipsecuz.pet.model.ModelType.NONE) {
                petLore.raw(lang.getMessage("model.model_3d_label", "%provider%", provider.getType().name()));
            }
        }

        petLore.stat(lang.getMessage("gui.lore_stars"), ((plugin.getEvolutionManager() != null) ? plugin.getEvolutionManager().getStarDisplay(star) : (star + "⭐")))
                .stat(lang.getMessage("gui.lore_trait"), trait.getLocalizedName(plugin))
                .stat(lang.getMessage("gui.lore_level"), "Lv." + lvl + " §7(" + lang.getMessage("gui.lore_exp") + ": §b" + exp + "/" + reqExp + "§7)")
                .stat(lang.getMessage("gui.lore_happiness"), happy + "% §8(" + happyState.getLocalizedDisplay(plugin) + "§8)")
                .separator()
                .heading(lang.getMessage("gui.heading_stats"));

        double dmg = PetStatEngine.calculateEffectiveStat(plugin, p.getUniqueId(), petId, lvl, "damage");
        double hp = PetStatEngine.calculateEffectiveStat(plugin, p.getUniqueId(), petId, lvl, "health");
        double def = PetStatEngine.calculateEffectiveStat(plugin, p.getUniqueId(), petId, lvl, "defense");
        double spd = PetStatEngine.calculateEffectiveStat(plugin, p.getUniqueId(), petId, lvl, "speed");

        petLore.stat(lang.getMessage("stats.health"), String.format("%.1f", hp) + " ❤")
                .stat(lang.getMessage("stats.damage"), String.format("%.1f", dmg) + " ⚔")
                .stat(lang.getMessage("stats.defense"), String.format("%.1f", def) + " 🛡")
                .stat(lang.getMessage("stats.speed"), String.format("%.3f", spd));

        ItemStack petIcon = GuiItemBuilder.from(matStr, Material.STONE)
                .name("&6★ " + name)
                .lore(petLore)
                .build();
        inv.setItem(13, petIcon);

        // Nút Triệu Hồi / Cất đi (slot 20)
        String active = plugin.getPetManager().getActivePetId(p.getUniqueId());
        boolean isCurrent = active != null && active.equals(petId);
        ItemStack summonBtn = GuiItemBuilder.from(isCurrent ? Material.BARRIER : Material.LEAD)
                .name(isCurrent ? lang.getMessage("gui.btn_despawn_title") : lang.getMessage("gui.btn_summon_title"))
                .lore(GuiLoreBuilder.create()
                        .action(isCurrent ? lang.getMessage("gui.btn_despawn_action") : lang.getMessage("gui.btn_summon_action")))
                .pdc("detail_action", "toggle_summon:" + petId)
                .build();
        inv.setItem(20, summonBtn);

        // Nút Đổi dạng Kích thước Bé / Lớn (slot 22)
        ItemStack sizeBtn = GuiItemBuilder.from(isBaby ? Material.EGG : Material.TURTLE_HELMET)
                .name(isBaby ? lang.getMessage("gui.btn_size_baby_title") : lang.getMessage("gui.btn_size_adult_title"))
                .lore(GuiLoreBuilder.create()
                        .description(lang.getMessage("gui.btn_size_desc"))
                        .action(isBaby ? lang.getMessage("gui.btn_size_toggle_to_adult") : lang.getMessage("gui.btn_size_toggle_to_baby")))
                .pdc("detail_action", "toggle_baby:" + petId)
                .build();
        inv.setItem(22, sizeBtn);

        // Nút Cho ăn (slot 24)
        ItemStack feedBtn = GuiItemBuilder.from(Material.COOKED_BEEF)
                .name(lang.getMessage("gui.btn_feed_title"))
                .lore(GuiLoreBuilder.create()
                        .description(lang.getMessage("gui.btn_feed_desc_1"))
                        .description(lang.getMessage("gui.btn_feed_desc_2"))
                        .action(lang.getMessage("gui.btn_feed_action")))
                .pdc("detail_action", "feed:" + petId)
                .build();
        inv.setItem(24, feedBtn);

        // Nút Kỹ Năng Ultimate (slot 29)
        boolean skillUnlocked = plugin.getSkillManager().isSkillUnlocked(p.getUniqueId(), petId, "ultimate");
        FileConfiguration skillsCfg = plugin.getModuleManager().getSkillsConfig();
        ConfigurationSection ultSec = skillsCfg.getConfigurationSection("skills." + petId + ".ultimate");

        String defaultSkName = (ultSec != null) ? ultSec.getString("name", "Tuyệt Chiêu") : "Tuyệt Chiêu";
        String skName = (lang != null && lang.contains("skills." + petId + ".name"))
                ? lang.getMessage("skills." + petId + ".name") : defaultSkName;

        GuiLoreBuilder skLore = GuiLoreBuilder.create();
        if (ultSec != null && ultSec.contains("desc")) {
            skLore.description(ultSec.getString("desc", ""));
        }

        ItemStack skillBtn;
        if (skillUnlocked) {
            skLore.status(true, lang.getMessage("gui.skill_unlocked"))
                  .action(lang.getMessage("gui.btn_skill_active_action"));
            skillBtn = GuiItemBuilder.from(Material.BLAZE_POWDER)
                    .name(lang.getMessage("gui.btn_skill_active_title", "%skill%", skName))
                    .lore(skLore)
                    .pdc("detail_action", "ultimate:" + petId)
                    .build();
        } else {
            skLore.status(false, lang.getMessage("gui.skill_locked"))
                  .separator()
                  .heading(lang.getMessage("gui.heading_requirements"));
            if (ultSec != null) {
                org.ipsecuz.pet.requirement.RequirementGroup group = plugin.getRequirementManager().parse(ultSec);
                org.ipsecuz.pet.requirement.RequirementContext ctx = new org.ipsecuz.pet.requirement.RequirementContext(p, petId, lvl, star, plugin);
                org.ipsecuz.pet.requirement.RequirementCheckResult result = plugin.getRequirementManager().evaluate(group, ctx);
                skLore.components(org.ipsecuz.pet.requirement.RequirementGuiRenderer.renderToComponents(result));
            }
            skLore.action(lang.getMessage("gui.btn_skill_learn_action"));
            skillBtn = GuiItemBuilder.from(Material.NETHER_BRICK)
                    .name(lang.getMessage("gui.btn_skill_learn_title", "%skill%", skName))
                    .lore(skLore)
                    .pdc("detail_action", "learn_skill:ultimate:" + petId)
                    .build();
        }
        inv.setItem(29, skillBtn);

        // Nút Nâng Sao & Tiến Hóa (slot 31)
        GuiLoreBuilder evoBtnLore = GuiLoreBuilder.create()
                .stat(lang.getMessage("gui.lore_stars"), ((plugin.getEvolutionManager() != null) ? plugin.getEvolutionManager().getStarDisplay(star) : (star + "⭐")))
                .description(lang.getMessage("gui.btn_evolution_desc"));
        if (star < 5) {
            evoBtnLore.action(lang.getMessage("gui.btn_evolution_action"));
        } else {
            evoBtnLore.status(true, lang.getMessage("gui.star_max_reached"));
        }
        ItemStack starBtn = GuiItemBuilder.from(Material.NETHER_STAR)
                .name(lang.getMessage("gui.btn_evolution_title"))
                .lore(evoBtnLore)
                .pdc("detail_action", "open_evolution:" + petId)
                .build();
        inv.setItem(31, starBtn);

        // Nút Rút Thẻ Pet (slot 33)
        ItemStack withdrawBtn = GuiItemBuilder.from(Material.CHEST_MINECART)
                .name(lang.getMessage("gui.btn_withdraw_title"))
                .lore(GuiLoreBuilder.create()
                        .description(lang.getMessage("gui.btn_withdraw_desc"))
                        .action(lang.getMessage("gui.btn_withdraw_action")))
                .pdc("detail_action", "confirm_withdraw_prompt:" + petId)
                .build();
        inv.setItem(33, withdrawBtn);

        // Nút Đổi tên Thú Cưng (slot 35)
        ItemStack renameBtn = GuiItemBuilder.from(Material.NAME_TAG)
                .name(lang.getMessage("gui.btn_rename_title"))
                .lore(GuiLoreBuilder.create()
                        .description(lang.getMessage("gui.btn_rename_desc"))
                        .action(lang.getMessage("gui.btn_rename_hint"))
                        .description(lang.getMessage("gui.btn_rename_limit")))
                .pdc("detail_action", "rename_info:" + petId)
                .build();
        inv.setItem(35, renameBtn);

        // Nút Quay lại (slot 40)
        ItemStack backBtn = GuiItemBuilder.from(Material.BARRIER)
                .name(lang.getMessage("gui.btn_back_to_list"))
                .pdc("detail_action", "back")
                .build();
        inv.setItem(40, backBtn);

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.1f);
    }

    public static void openEvolutionMenu(Player p, String petId) {
        IpsecuzPet plugin = IpsecuzPet.getInstance();
        ConfigManager cm = plugin.getConfigManager();
        EvolutionManager evo = plugin.getEvolutionManager();
        LanguageManager lang = plugin.getLanguage();

        int curStar = evo.getStar(p.getUniqueId(), petId);
        int maxStar = plugin.getModuleManager().getEvolutionConfig().getInt("max_stars", 5);
        int nextStar = Math.min(maxStar, curStar + 1);

        EvolutionHolder holder = new EvolutionHolder(petId);
        String rawTitle = lang.getMessage("gui.evolution_title", "%cur%", curStar + "⭐", "%next%", nextStar + "⭐");
        Inventory inv = Bukkit.createInventory(holder, 45, GuiText.title(rawTitle));
        holder.setInventory(inv);

        ItemStack border = GuiItemBuilder.from(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
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

        GuiLoreBuilder prevLore = GuiLoreBuilder.create()
                .stat(lang.getMessage("gui.lore_stars"), evo.getStarDisplay(curStar) + " §e➔ " + evo.getStarDisplay(nextStar))
                .separator();

        if (curStar < maxStar) {
            double hpDiff = ((nextHp / curHp) - 1.0) * 100.0;
            double dmgDiff = ((nextDmg / curDmg) - 1.0) * 100.0;
            double defDiff = ((nextDef / Math.max(0.1, curDef)) - 1.0) * 100.0;

            String hpExtra = hpDiff > 0 ? " §a(+" + Math.round(hpDiff) + "%)" : "";
            String dmgExtra = dmgDiff > 0 ? " §a(+" + Math.round(dmgDiff) + "%)" : "";
            String defExtra = defDiff > 0 ? " §a(+" + Math.round(defDiff) + "%)" : "";

            prevLore.statCompare(lang.getMessage("stats.health"), String.format("%.1f", curHp) + " ❤", String.format("%.1f", nextHp) + " ❤" + hpExtra)
                    .statCompare(lang.getMessage("stats.damage"), String.format("%.1f", curDmg) + " ⚔", String.format("%.1f", nextDmg) + " ⚔" + dmgExtra)
                    .statCompare(lang.getMessage("stats.defense"), String.format("%.1f", curDef) + " 🛡", String.format("%.1f", nextDef) + " 🛡" + defExtra);

            if (nextSpd > curSpd) {
                prevLore.statCompare(lang.getMessage("stats.speed"), String.format("%.3f", curSpd), String.format("%.3f", nextSpd));
            } else {
                prevLore.stat(lang.getMessage("stats.speed"), String.format("%.3f", curSpd) + " §7(" + lang.getMessage("gui.stat_unchanged") + ")");
            }
        } else {
            prevLore.status(true, lang.getMessage("gui.star_max_reached"));
        }

        ItemStack preview = GuiItemBuilder.from(Material.NETHER_STAR)
                .name(lang.getMessage("gui.evo_preview_title"))
                .lore(prevLore)
                .build();
        inv.setItem(13, preview);

        // Slot 29: Danh sách kiểm tra điều kiện (Requirements Checklist)
        boolean allMet = true;
        GuiLoreBuilder chLore = GuiLoreBuilder.create();

        if (curStar >= maxStar) {
            chLore.status(true, lang.getMessage("gui.star_max_reached"));
            allMet = false;
        } else {
            FileConfiguration config = plugin.getModuleManager().getEvolutionConfig();
            ConfigurationSection reqSec = config.getConfigurationSection("requirements.star_" + nextStar);

            if (reqSec != null) {
                org.ipsecuz.pet.requirement.RequirementGroup group = plugin.getRequirementManager().parse(reqSec);
                org.ipsecuz.pet.requirement.RequirementContext ctx = new org.ipsecuz.pet.requirement.RequirementContext(p, petId, lvl, curStar, plugin);
                org.ipsecuz.pet.requirement.RequirementCheckResult result = plugin.getRequirementManager().evaluate(group, ctx);
                allMet = result.isSatisfied();
                chLore.components(org.ipsecuz.pet.requirement.RequirementGuiRenderer.renderToComponents(result));
            } else {
                chLore.status(true, lang.getMessage("gui.evo_free"));
            }
        }

        ItemStack checkItem = GuiItemBuilder.from(Material.WRITABLE_BOOK)
                .name(lang.getMessage("gui.heading_evo_requirements"))
                .lore(chLore)
                .build();
        inv.setItem(29, checkItem);

        // Slot 33: Nút Tiến Hóa (Action Button)
        if (curStar < maxStar) {
            GuiLoreBuilder aLore = GuiLoreBuilder.create();
            if (allMet) {
                aLore.action(lang.getMessage("gui.btn_evolve_action"));
            } else {
                aLore.description(lang.getMessage("gui.btn_evolve_not_ready_desc"));
            }
            ItemStack actionBtn = GuiItemBuilder.from(allMet ? Material.LIME_CONCRETE : Material.RED_CONCRETE)
                    .name(allMet ? lang.getMessage("gui.btn_evolve_ready") : lang.getMessage("gui.btn_evolve_not_ready"))
                    .lore(aLore)
                    .pdc("evo_action", allMet ? ("do_evolve:" + petId) : "not_ready")
                    .build();
            inv.setItem(33, actionBtn);
        }

        // Slot 40: Nút Quay lại
        ItemStack backBtn = GuiItemBuilder.from(Material.BARRIER)
                .name(lang.getMessage("gui.btn_back_to_detail"))
                .pdc("evo_action", "back:" + petId)
                .build();
        inv.setItem(40, backBtn);

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1f);
    }

    public static void openConfirmDialog(Player p, String title, ItemStack displayItem, String confirmActionPayload) {
        IpsecuzPet plugin = IpsecuzPet.getInstance();
        LanguageManager lang = plugin.getLanguage();
        ConfirmHolder holder = new ConfirmHolder(confirmActionPayload);
        Inventory inv = Bukkit.createInventory(holder, 27, GuiText.title(title));
        holder.setInventory(inv);

        ItemStack border = GuiItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 27; i++) inv.setItem(i, border);

        // Slot 11: Nút Xác Nhận (Lime Concrete)
        ItemStack confirmBtn = GuiItemBuilder.from(Material.LIME_CONCRETE)
                .name(lang.getMessage("gui.btn_confirm"))
                .lore(GuiLoreBuilder.create().action(lang.getMessage("gui.btn_confirm_desc")))
                .pdc("confirm_dialog", "accept")
                .build();
        inv.setItem(11, confirmBtn);

        // Slot 13: Thẻ Thông Tin
        if (displayItem != null) {
            inv.setItem(13, displayItem);
        }

        // Slot 15: Nút Hủy Bỏ (Red Concrete)
        ItemStack cancelBtn = GuiItemBuilder.from(Material.RED_CONCRETE)
                .name(lang.getMessage("gui.btn_cancel"))
                .lore(GuiLoreBuilder.create().action(lang.getMessage("gui.btn_cancel_desc")))
                .pdc("confirm_dialog", "cancel")
                .build();
        inv.setItem(15, cancelBtn);

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1f);
    }

    public static void openCodexMenu(Player p) {
        openCodexMenu(p, 1);
    }

    public static void openCodexMenu(Player p, int page) {
        IpsecuzPet plugin = IpsecuzPet.getInstance();
        LanguageManager lang = plugin.getLanguage();
        ConfigurationSection petsSec = plugin.getConfig().getConfigurationSection("pets");
        List<String> allPetKeys = petsSec != null ? new ArrayList<>(petsSec.getKeys(false)) : Collections.emptyList();

        int pageSize = 45;
        int totalPages = Math.max(1, (int) Math.ceil((double) allPetKeys.size() / pageSize));
        int curPage = Math.max(1, Math.min(page, totalPages));

        CodexHolder holder = new CodexHolder(curPage);
        int discoveredCount = plugin.getCodexManager().getDiscoveredCount(p.getUniqueId());
        String titleStr = lang.getMessage("gui.codex_title_page", "%discovered%", String.valueOf(discoveredCount), "%total%", String.valueOf(allPetKeys.size()), "%page%", String.valueOf(curPage), "%max_page%", String.valueOf(totalPages));
        Inventory inv = Bukkit.createInventory(holder, 54, GuiText.title(titleStr));
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
                String petName = lang.getPetName(petId);
                double hp = plugin.getConfig().getDouble("pets." + petId + ".stats.health", 20.0);
                double dmg = plugin.getConfig().getDouble("pets." + petId + ".stats.damage", 5.0);

                item = GuiItemBuilder.from(matStr, Material.STONE)
                        .name("&a✔ " + petName)
                        .lore(GuiLoreBuilder.create()
                                .stat(lang.getMessage("gui.lore_rarity"), rarity.getLocalizedName(plugin))
                                .status(true, lang.getMessage("gui.codex_registered"))
                                .stat(lang.getMessage("stats.health"), String.format("%.1f", hp) + " ❤")
                                .stat(lang.getMessage("stats.damage"), String.format("%.1f", dmg) + " ⚔"))
                        .build();
            } else {
                item = GuiItemBuilder.from(Material.GRAY_DYE)
                        .name(lang.getMessage("gui.codex_unknown_title"))
                        .lore(GuiLoreBuilder.create()
                                .stat(lang.getMessage("gui.lore_rarity"), rarity.getLocalizedName(plugin))
                                .description(lang.getMessage("gui.codex_unknown_desc")))
                        .build();
            }
            inv.setItem(i - start, item);
        }

        ItemStack navGlass = GuiItemBuilder.from(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int s = 45; s < 54; s++) inv.setItem(s, navGlass);

        if (curPage > 1) {
            ItemStack prev = GuiItemBuilder.from(Material.ARROW)
                    .name(lang.getMessage("gui.btn_prev_page_num", "%page%", String.valueOf(curPage - 1), "%total%", String.valueOf(totalPages)))
                    .pdc("nav_codex_page", curPage - 1)
                    .build();
            inv.setItem(45, prev);
        }

        ItemStack back = GuiItemBuilder.from(Material.LEAD)
                .name(lang.getMessage("gui.btn_back_to_menu"))
                .pdc("btn_action", "open_pet_menu")
                .build();
        inv.setItem(49, back);

        if (curPage < totalPages) {
            ItemStack next = GuiItemBuilder.from(Material.ARROW)
                    .name(lang.getMessage("gui.btn_next_page_num", "%page%", String.valueOf(curPage + 1), "%total%", String.valueOf(totalPages)))
                    .pdc("nav_codex_page", curPage + 1)
                    .build();
            inv.setItem(53, next);
        }

        p.openInventory(inv);
    }

    public static void openShardsMenu(Player p) {
        IpsecuzPet plugin = IpsecuzPet.getInstance();
        LanguageManager lang = plugin.getLanguage();
        ShardsHolder holder = new ShardsHolder();
        Inventory inv = Bukkit.createInventory(holder, 45, GuiText.title(lang.getMessage("gui.shards_title")));
        holder.setInventory(inv);

        ItemStack border = GuiItemBuilder.from(Material.CYAN_STAINED_GLASS_PANE).name(" ").build();
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
                String petName = lang.getPetName(petId);
                PetRarity rarity = PetRarity.fromPetId(plugin, petId);
                int req = plugin.getShardManager().getRequiredShardsToCraft(petId);

                GuiLoreBuilder sLore = GuiLoreBuilder.create()
                        .stat(lang.getMessage("gui.lore_rarity"), rarity.getLocalizedName(plugin))
                        .stat(lang.getMessage("gui.lore_shards_count"), count + "/" + req);

                if (count >= req) {
                    sLore.status(true, lang.getMessage("gui.shards_craft_ready"));
                } else {
                    sLore.status(false, lang.getMessage("gui.shards_craft_needed", "%needed%", String.valueOf(req - count)));
                }

                ItemStack item = GuiItemBuilder.from(matStr, Material.PRISMARINE_SHARD)
                        .name("&b" + lang.getMessage("gui.shard_name_prefix") + " " + petName)
                        .lore(sLore)
                        .pdc("craft_shard_id", petId)
                        .build();

                inv.setItem(slot, item);
                slot++;
                if (slot % 9 == 8) slot += 2;
                if (slot >= 35) break;
            }
        }

        if (slot == 10) {
            ItemStack empty = GuiItemBuilder.from(Material.BARRIER)
                    .name(lang.getMessage("gui.shards_empty_title"))
                    .lore(GuiLoreBuilder.create()
                            .description(lang.getMessage("gui.shards_empty_desc_1"))
                            .description(lang.getMessage("gui.shards_empty_desc_2")))
                    .build();
            inv.setItem(22, empty);
        }

        ItemStack back = GuiItemBuilder.from(Material.ARROW)
                .name(lang.getMessage("gui.btn_back_to_menu"))
                .pdc("btn_action", "open_pet_menu")
                .build();
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
        Inventory inv = Bukkit.createInventory(holder, 54, GuiText.title(rawTitle));
        holder.setInventory(inv);

        int startIndex = (curPage - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, allKeys.size());

        for (int i = startIndex; i < endIndex; i++) {
            String key = allKeys.get(i);
            String matStr = plugin.getConfig().getString("pets." + key + ".icon", "STONE");
            String petName = lang.getPetName(key);
            String price = plugin.getCurrencyManager().getPriceDisplay(key);
            PetRarity rarity = PetRarity.fromPetId(plugin, key);

            double hp = PetStatEngine.getBaseStatAtLevel(plugin, key, 1, "health");
            double dmg = PetStatEngine.getBaseStatAtLevel(plugin, key, 1, "damage");
            double def = PetStatEngine.getBaseStatAtLevel(plugin, key, 1, "defense");
            double spd = PetStatEngine.getBaseStatAtLevel(plugin, key, 1, "speed");

            GuiLoreBuilder lore = GuiLoreBuilder.create()
                    .stat(lang.getMessage("gui.lore_rarity"), rarity.getLocalizedName(plugin))
                    .separator()
                    .heading(lang.getMessage("gui.heading_base_stats"))
                    .stat(lang.getMessage("stats.health"), String.format("%.1f", hp) + " ❤")
                    .stat(lang.getMessage("stats.damage"), String.format("%.1f", dmg) + " ⚔")
                    .stat(lang.getMessage("stats.defense"), String.format("%.1f", def) + " 🛡")
                    .stat(lang.getMessage("stats.speed"), String.format("%.3f", spd))
                    .separator();

            if (cm.getData().contains(p.getUniqueId() + ".pets." + key)) {
                lore.status(true, lang.getMessage("gui.shop_already_owned"));
            } else {
                org.bukkit.configuration.ConfigurationSection petSec = plugin.getConfig().getConfigurationSection("pets." + key);
                if (petSec != null && (petSec.contains("requirements") || petSec.contains("all") || petSec.contains("one_of"))) {
                    org.ipsecuz.pet.requirement.RequirementGroup group = plugin.getRequirementManager().parse(petSec);
                    org.ipsecuz.pet.requirement.RequirementContext ctx = new org.ipsecuz.pet.requirement.RequirementContext(p, key, 1, 1, plugin);
                    org.ipsecuz.pet.requirement.RequirementCheckResult res = plugin.getRequirementManager().evaluate(group, ctx);
                    lore.components(org.ipsecuz.pet.requirement.RequirementGuiRenderer.renderToComponents(res));
                } else {
                    lore.price(lang.getMessage("gui.lore_price", "%cost%", price));
                }
                lore.action(lang.getMessage("gui.action_buy_now"));
            }

            ItemStack item = GuiItemBuilder.from(matStr, Material.STONE)
                    .name(petName)
                    .lore(lore)
                    .pdc("shop_id", key)
                    .build();

            inv.setItem(i - startIndex, item);
        }

        if (allKeys.isEmpty()) {
            ItemStack emptyItem = GuiItemBuilder.from(Material.BARRIER)
                    .name(lang.getMessage("gui.shop_empty_title"))
                    .lore(GuiLoreBuilder.create().desc(lang.getMessage("gui.shop_empty_desc")))
                    .build();
            inv.setItem(22, emptyItem);
        }

        ItemStack navGlass = GuiItemBuilder.from(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int s = 45; s < 54; s++) inv.setItem(s, navGlass);

        if (curPage > 1) {
            ItemStack prev = GuiItemBuilder.from(Material.ARROW)
                    .name(lang.getMessage("gui.btn_prev_page_num", "%page%", String.valueOf(curPage - 1), "%total%", String.valueOf(totalPages)))
                    .pdc("nav_shop_page", curPage - 1)
                    .build();
            inv.setItem(45, prev);
        }

        ItemStack hatchBtn = GuiItemBuilder.from(Material.DRAGON_EGG)
                .name(lang.getMessage("gui.btn_hatch"))
                .lore(GuiLoreBuilder.create().action(lang.getMessage("gui.btn_hatch_action")))
                .pdc("btn_action", "open_hatch")
                .build();
        inv.setItem(47, hatchBtn);

        ItemStack info = GuiItemBuilder.from(Material.BOOK)
                .name(lang.getMessage("gui.shop_info_title"))
                .lore(GuiLoreBuilder.create()
                        .stat(lang.getMessage("gui.shop_total_pets"), String.valueOf(allKeys.size()))
                        .stat(lang.getMessage("gui.shop_cur_page"), curPage + "/" + totalPages))
                .build();
        inv.setItem(49, info);

        ItemStack menuBtn = GuiItemBuilder.from(Material.LEAD)
                .name(lang.getMessage("gui.btn_back_to_menu"))
                .pdc("btn_action", "open_pet_menu")
                .build();
        inv.setItem(51, menuBtn);

        if (curPage < totalPages) {
            ItemStack next = GuiItemBuilder.from(Material.ARROW)
                    .name(lang.getMessage("gui.btn_next_page_num", "%page%", String.valueOf(curPage + 1), "%total%", String.valueOf(totalPages)))
                    .pdc("nav_shop_page", curPage + 1)
                    .build();
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
                        p.sendMessage(plugin.getLanguage().getMessage("trade.only_valid_card"));
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
                session.cancel(plugin.getLanguage().getMessage("trade.cancel_quit"));
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
                        p.sendMessage(plugin.getLanguage().getMessage("trade.only_valid_card"));
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
                            p.sendMessage(plugin.getLanguage().getMessage("trade.only_valid_card"));
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
                            p.sendMessage(plugin.getLanguage().getMessage("trade.offer_full"));
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
                        String petDisplayName = plugin.getLanguage().getPetDisplayName(p.getUniqueId(), petId);
                        openConfirmDialog(p, plugin.getLanguage().getMessage("gui.confirm_evolve_title", "%pet_name%", petDisplayName, "%pet%", petDisplayName), confirmIcon.clone(), "confirm_evolve:" + petId);
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

                            p.sendMessage(plugin.getLanguage().getMessage("pet.buy_success", "%pet_name%", plugin.getLanguage().getPetName(shopId)));
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
                    p.sendMessage(plugin.getLanguage().getMessage("shards.not_enough_shards", "%required%", String.valueOf(reqShards), "%current%", String.valueOf(shards)));
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

                String petDisplayName = plugin.getLanguage().getPetName(petId);
                p.sendMessage(plugin.getLanguage().getMessage("shards.craft_success", "%pet_name%", petDisplayName));
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
                String formStr = !curBaby ? plugin.getLanguage().getMessage("gui.form_baby") : plugin.getLanguage().getMessage("gui.form_adult");
                p.sendMessage(plugin.getLanguage().getMessage("pet.baby_toggle", "%form%", formStr));
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
                    p.sendMessage(plugin.getLanguage().getMessage("skills.need_summon_first"));
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
                String petDisplayName = plugin.getLanguage().getPetDisplayName(p.getUniqueId(), petId);
                openConfirmDialog(p, plugin.getLanguage().getMessage("gui.confirm_withdraw_title", "%pet_name%", petDisplayName, "%pet%", petDisplayName), wdIcon, "confirm_withdraw:" + petId);
            } else if ("rename_info".equals(action) && parts.length > 1) {
                p.closeInventory();
                plugin.getRenameSessionManager().startSession(p, parts[1]);
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
                    p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.8f);
                } else {
                    plugin.getPetManager().spawnPet(p, petId);
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                }
                openPetMenu(p, 1);
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
                String name = plugin.getLanguage().getPetName(shopId);
                openConfirmDialog(p, plugin.getLanguage().getMessage("gui.confirm_buy_title", "%pet_name%", name), buyCard, "confirm_buy:" + shopId);
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