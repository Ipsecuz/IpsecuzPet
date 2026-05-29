package org.ipsecuz.pet;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import java.util.ArrayList;
import java.util.List;

public class GuiListener implements Listener {
    private final IpsecuzPet plugin;
    public GuiListener(IpsecuzPet plugin) { this.plugin = plugin; }

    public static void openPetMenu(Player p) {
        IpsecuzPet plugin = IpsecuzPet.getInstance();
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(plugin.getLanguage().getMessage("gui.menu_title").replace("&", "§")));
        ConfigManager cm = plugin.getConfigManager();
        LanguageManager lang = plugin.getLanguage();

        if (cm.getData().getConfigurationSection(p.getUniqueId() + ".pets") != null) {
            for (String petId : cm.getData().getConfigurationSection(p.getUniqueId() + ".pets").getKeys(false)) {
                String mat = plugin.getConfig().getString("pets." + petId + ".icon", "STONE");
                ItemStack item = new ItemStack(Material.valueOf(mat));
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(plugin.getConfig().getString("pets." + petId + ".name").replace("&", "§"));

                List<String> lore = new ArrayList<>();
                int lvl = cm.getData().getInt(p.getUniqueId() + ".pets." + petId + ".level");
                lore.add(lang.getMessage("gui.lore_level", "%level%", String.valueOf(lvl)));

                if (cm.isPetDead(p.getUniqueId(), petId)) {
                    lore.add(lang.getMessage("gui.lore_dead"));
                } else {
                    lore.add(lang.getMessage("gui.lore_click_summon"));
                    lore.add(lang.getMessage("gui.lore_click_despawn"));
                }

                meta.setLore(lore);
                meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "pet_id"), PersistentDataType.STRING, petId);
                item.setItemMeta(meta);
                inv.addItem(item);
            }
        }
        p.openInventory(inv);
    }

    public static void openShopMenu(Player p) {
        IpsecuzPet plugin = IpsecuzPet.getInstance();
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(plugin.getLanguage().getMessage("gui.shop_title").replace("&", "§")));
        LanguageManager lang = plugin.getLanguage();
        ConfigManager cm = plugin.getConfigManager(); // Lấy ConfigManager để check sở hữu

        for (String key : plugin.getConfig().getConfigurationSection("pets").getKeys(false)) {
            String mat = plugin.getConfig().getString("pets." + key + ".icon", "STONE");
            ItemStack item = new ItemStack(Material.valueOf(mat));
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(plugin.getConfig().getString("pets." + key + ".name").replace("&", "§"));

            List<String> lore = new ArrayList<>();
            String price = plugin.getCurrencyManager().getPriceDisplay(key);

            // --- KIỂM TRA ĐÃ SỞ HỮU CHƯA ĐỂ HIỆN LORE ---
            if (cm.getData().contains(p.getUniqueId() + ".pets." + key)) {
                lore.add("§a✔ ĐÃ SỞ HỮU");
            } else {
                lore.add(lang.getMessage("gui.lore_price", "%cost%", price));
            }
            // ---------------------------------------------

            meta.setLore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "shop_id"), PersistentDataType.STRING, key);
            item.setItemMeta(meta);
            inv.addItem(item);
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        // Lấy tiêu đề GUI (cách so sánh title đơn giản hóa, nên dùng holder nếu có thể nhưng vầy cho nhanh)
        // Lưu ý: So sánh title bằng String đôi khi bị lỗi color code, nên mình dùng check item container là chính

        if (e.getCurrentItem() == null || e.getCurrentItem().getItemMeta() == null) return;

        // --- XỬ LÝ MENU HỒ SƠ PET ---
        String petId = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "pet_id"), PersistentDataType.STRING);
        if (petId != null) {
            e.setCancelled(true);
            // Check nếu là menu hồ sơ (logic cũ)
            // Hoặc đơn giản là nếu item có tag pet_id thì thực hiện summon/despawn

            String activePet = plugin.getPetManager().getActivePetId(p.getUniqueId());
            if (activePet != null && activePet.equals(petId)) {
                plugin.getPetManager().removePet(p.getUniqueId());
                p.sendMessage(plugin.getLanguage().getMessage("pet.despawn"));
            } else {
                plugin.getPetManager().spawnPet(p, petId);
            }
            p.closeInventory();
            return;
        }

        // --- XỬ LÝ MENU SHOP ---
        String shopId = e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "shop_id"), PersistentDataType.STRING);
        if (shopId != null) {
            e.setCancelled(true);

            if (plugin.getConfigManager().getData().contains(p.getUniqueId() + ".pets." + shopId)) {
                p.sendMessage(plugin.getLanguage().getMessage("pet.already_owned"));
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }

            if (plugin.getCurrencyManager().processTransaction(p, shopId)) {
                plugin.getConfigManager().createPetDataIfMissing(p.getUniqueId(), shopId);
                p.sendMessage(plugin.getLanguage().getMessage("pet.buy_success", "%pet_name%", plugin.getConfig().getString("pets." + shopId + ".name")));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                p.closeInventory();
            }
        }
    }
}