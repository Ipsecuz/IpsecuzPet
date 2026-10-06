package org.ipsecuz.pet;

import net.kyori.adventure.text.Component;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class HatchingManager {
    private final IpsecuzPet plugin;
    public final NamespacedKey eggKey;

    public HatchingManager(IpsecuzPet plugin) {
        this.plugin = plugin;
        this.eggKey = new NamespacedKey(plugin, "pet_egg_id");
    }

    public ItemStack createEggItem(String eggId, int amount) {
        FileConfiguration config = plugin.getModuleManager().getHatchingConfig();
        ConfigurationSection sec = config.getConfigurationSection("eggs." + eggId);
        if (sec == null) return null;

        Material mat = Material.getMaterial(sec.getString("material", "EGG"));
        if (mat == null) mat = Material.EGG;

        ItemStack item = new ItemStack(mat, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String name = sec.getString("name", "&eTrứng Pet");
        meta.displayName(Component.text(ChatColor.translateAlternateColorCodes('&', name)));

        List<Component> lore = new ArrayList<>();
        for (String line : sec.getStringList("lore")) {
            lore.add(Component.text(ChatColor.translateAlternateColorCodes('&', line)));
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
        FileConfiguration config = plugin.getModuleManager().getHatchingConfig();
        String title = config.getString("gui.title", "&1Lò Ấp Trứng Pet");
        int size = config.getInt("gui.size", 45);

        Inventory inv = Bukkit.createInventory(null, size, Component.text(ChatColor.translateAlternateColorCodes('&', title)));

        // Trang trí khung viền
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

        // Đặt các loại trứng vào GUI
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
                        lore.add(Component.text("§a▶ Nhấp để Ấp Trứng ngay!"));
                        meta.lore(lore);
                        eggItem.setItemMeta(meta);
                    }
                    inv.setItem(slot, eggItem);
                    slot += 2;
                }
            }
        }

        // Hướng dẫn ở giữa đáy
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.displayName(Component.text("§e§lHƯỚNG DẪN ẤP TRỨNG"));
            List<Component> infoLore = new ArrayList<>();
            infoLore.add(Component.text("§7- Nhấp chuột phải vào quả trứng trên tay"));
            infoLore.add(Component.text("§7  hoặc nhấp trực tiếp vào biểu tượng trên GUI."));
            infoLore.add(Component.text("§7- Trứng sẽ nứt và nở ra Pet ngẫu nhiên!"));
            infoMeta.lore(infoLore);
            info.setItemMeta(infoMeta);
        }
        inv.setItem(size - 5, info);

        player.openInventory(inv);
    }

    public void processHatch(Player player, String eggId, ItemStack consumedItem) {
        FileConfiguration config = plugin.getModuleManager().getHatchingConfig();
        ConfigurationSection eggSec = config.getConfigurationSection("eggs." + eggId);
        if (eggSec == null) {
            player.sendMessage("§cKhông tìm thấy dữ liệu cho loại trứng này!");
            return;
        }

        // Kiểm tra giới hạn slot pet
        int limit = plugin.getConfig().getInt("max_pets", 2);
        List<String> owned = new ArrayList<>();
        if (plugin.getConfigManager().getData().getConfigurationSection(player.getUniqueId() + ".pets") != null) {
            owned.addAll(plugin.getConfigManager().getData().getConfigurationSection(player.getUniqueId() + ".pets").getKeys(false));
        }
        if (owned.size() >= limit) {
            player.sendMessage(plugin.getLanguage().getMessage("pet.limit_reached", "%current%", String.valueOf(owned.size()), "%max%", String.valueOf(limit)));
            return;
        }

        // Trừ trứng
        if (consumedItem != null) {
            consumedItem.setAmount(consumedItem.getAmount() - 1);
        }

        // Chọn pet ngẫu nhiên theo trọng số
        ConfigurationSection lootTable = eggSec.getConfigurationSection("loot_table");
        if (lootTable == null) return;

        int totalWeight = 0;
        Map<String, Integer> weights = new HashMap<>();
        for (String pId : lootTable.getKeys(false)) {
            int w = lootTable.getInt(pId, 1);
            weights.put(pId, w);
            totalWeight += w;
        }

        if (totalWeight <= 0) return;
        int randomWeight = ThreadLocalRandom.current().nextInt(totalWeight);
        String selectedPetId = null;
        int count = 0;
        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
            count += entry.getValue();
            if (randomWeight < count) {
                selectedPetId = entry.getKey();
                break;
            }
        }

        if (selectedPetId == null) return;
        final String finalPetId = selectedPetId;
        String petDisplayName = plugin.getConfig().getString("pets." + finalPetId + ".name", finalPetId);

        // Hiệu ứng nứt trứng & animation
        SchedulerUtils.runEntityTask(plugin, player, () -> {
            player.playSound(player.getLocation(), Sound.ENTITY_TURTLE_EGG_CRACK, 1f, 1f);
            player.spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5);
        });

        SchedulerUtils.runEntityTaskLater(plugin, player, () -> {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            player.spawnParticle(Particle.TOTEM, player.getLocation().add(0, 1, 0), 50, 0.8, 0.8, 0.8, 0.2);

            if (plugin.getConfigManager().getData().contains(player.getUniqueId() + ".pets." + finalPetId)) {
                // Đã sở hữu -> Thưởng EXP cho pet đó
                int expBonus = 150;
                player.sendMessage("§eBạn đã sở hữu §f" + petDisplayName + "§e! Trứng chuyển hóa thành §b+" + expBonus + " EXP §echo Pet.");
                plugin.getPetManager().givePetExp(player, expBonus);
            } else {
                // Thêm pet mới
                plugin.getConfigManager().createPetDataIfMissing(player.getUniqueId(), finalPetId);
                Title title = Title.title(
                        Component.text("§6§lẤP TRỨNG THÀNH CÔNG!"),
                        Component.text("§aBạn nhận được: " + petDisplayName),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(500))
                );
                player.showTitle(title);
                player.sendMessage("§a§lChúc mừng! §fBạn vừa ấp nở thành công Pet: " + petDisplayName);
            }
        }, 20L);
    }
}

