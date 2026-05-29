package org.ipsecuz.pet;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class CaptureManager {
    private final IpsecuzPet plugin;
    private final Map<String, BallData> balls = new HashMap<>();
    private final NamespacedKey ballKey;

    public CaptureManager(IpsecuzPet plugin) {
        this.plugin = plugin;
        this.ballKey = new NamespacedKey(plugin, "capture_ball_id");
        loadBalls();
    }

    public void loadBalls() {
        balls.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("capture_system.items");
        if (sec == null) return;

        for (String key : sec.getKeys(false)) {
            Material mat = Material.getMaterial(sec.getString(key + ".material", "EGG"));
            if (mat == null) mat = Material.EGG;

            String name = sec.getString(key + ".name", "Ball");
            List<String> lore = sec.getStringList(key + ".lore");
            double chance = sec.getDouble(key + ".base_chance", 0.0);

            
            String modeStr = sec.getString(key + ".mode", "BLACKLIST").toUpperCase();
            List<String> types = sec.getStringList(key + ".types");

            balls.put(key, new BallData(mat, name, lore, chance, modeStr, types));
        }
    }

    public Set<String> getBallIds() {
        return balls.keySet();
    }

    public ItemStack getBallItem(String ballId, int amount) {
        if (!balls.containsKey(ballId)) return null;
        BallData data = balls.get(ballId);

        ItemStack item = new ItemStack(data.material, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(data.name.replace("&", "§")));

        List<Component> loreComp = new ArrayList<>();
        for (String l : data.lore) loreComp.add(Component.text(l.replace("&", "§")));
        meta.lore(loreComp);

        meta.getPersistentDataContainer().set(ballKey, PersistentDataType.STRING, ballId);
        item.setItemMeta(meta);
        return item;
    }

    public String getBallIdFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(ballKey, PersistentDataType.STRING);
    }

    public CaptureResult calculateCapture(Player p, EntityType targetType, String ballId) {
        if (!balls.containsKey(ballId)) return CaptureResult.INVALID_ITEM;
        BallData data = balls.get(ballId);

        boolean isAllowed = true;
        String typeName = targetType.toString();

        if (data.mode.equals("WHITELIST")) {
            // Chế độ Whitelist: Nếu KHÔNG có trong list -> CHẶN
            if (!data.types.contains(typeName)) {
                isAllowed = false;
            }
        } else {
            // Chế độ Blacklist (Mặc định): Nếu CÓ trong list -> CHẶN
            if (data.types.contains(typeName)) {
                isAllowed = false;
            }
        }

        if (!isAllowed) {
            return CaptureResult.TYPE_NOT_ALLOWED;
        }
        // ---------------------------

        // Tính tỉ lệ
        double chance = data.baseChance;
        ConfigurationSection permSec = plugin.getConfig().getConfigurationSection("capture_system.permission_bonus");
        if (permSec != null) {
            for (String key : permSec.getKeys(false)) {
                if (p.hasPermission("ipsecuzpet.catch." + key)) {
                    chance += permSec.getDouble(key);
                }
            }
        }

        double random = ThreadLocalRandom.current().nextDouble(100.0);
        return (random <= chance) ? CaptureResult.SUCCESS : CaptureResult.FAILED;
    }

    public enum CaptureResult {
        SUCCESS, FAILED, TYPE_NOT_ALLOWED, INVALID_ITEM
    }

    private static class BallData {
        Material material;
        String name;
        List<String> lore;
        double baseChance;
        String mode;      // WHITELIST hoặc BLACKLIST
        List<String> types; // Danh sách mob

        public BallData(Material m, String n, List<String> l, double c, String mode, List<String> types) {
            this.material = m;
            this.name = n;
            this.lore = l;
            this.baseChance = c;
            this.mode = mode;
            this.types = types;
        }
    }
}