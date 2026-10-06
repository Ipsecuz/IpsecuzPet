package org.ipsecuz.pet;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SkillManager {
    private final IpsecuzPet plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public SkillManager(IpsecuzPet plugin) {
        this.plugin = plugin;
    }

    public boolean isSkillUnlocked(UUID uuid, String petId, String skillType) {
        List<String> unlocked = plugin.getConfigManager().getData().getStringList(uuid + ".pets." + petId + ".unlocked_skills");
        if (unlocked.contains(skillType.toLowerCase())) return true;

        FileConfiguration config = plugin.getModuleManager().getSkillsConfig();
        ConfigurationSection sec = config.getConfigurationSection("skills." + petId + "." + skillType);
        if (sec == null) return false;

        // Nếu không yêu cầu level hoặc chi phí thì mặc định mở khóa
        int reqLvl = sec.getInt("req_level", 0);
        int costMoney = sec.getInt("cost_money", 0);
        int costPoints = sec.getInt("cost_points", 0);
        String costItem = sec.getString("cost_items", null);
        return (reqLvl <= 1 && costMoney <= 0 && costPoints <= 0 && costItem == null);
    }

    public boolean unlockSkill(Player player, String petId, String skillType) {
        if (isSkillUnlocked(player.getUniqueId(), petId, skillType)) {
            player.sendMessage("§aThú cưng đã học kỹ năng này rồi!");
            return false;
        }

        FileConfiguration config = plugin.getModuleManager().getSkillsConfig();
        ConfigurationSection sec = config.getConfigurationSection("skills." + petId + "." + skillType);
        if (sec == null) {
            player.sendMessage("§cKhông tìm thấy thông tin kỹ năng này!");
            return false;
        }

        int petLvl = plugin.getConfigManager().getData().getInt(player.getUniqueId() + ".pets." + petId + ".level", 1);
        int reqLvl = sec.getInt("req_level", 1);
        if (petLvl < reqLvl) {
            player.sendMessage("§cThú cưng cần đạt cấp độ §eLv." + reqLvl + " §cđể học kỹ năng này! (Hiện tại: Lv." + petLvl + ")");
            return false;
        }

        int costMoney = sec.getInt("cost_money", 0);
        if (costMoney > 0) {
            if (!plugin.getCurrencyManager().hasMoney(player, costMoney)) {
                player.sendMessage("§cBạn không đủ tiền! Cần: §e$" + costMoney);
                return false;
            }
        }

        int costPoints = sec.getInt("cost_points", 0);
        if (costPoints > 0) {
            if (!plugin.getCurrencyManager().hasPoints(player, costPoints)) {
                player.sendMessage("§cBạn không đủ Points! Cần: §b" + costPoints + " Points");
                return false;
            }
        }

        List<String> costItems = new ArrayList<>();
        if (sec.contains("cost_items")) {
            if (sec.isList("cost_items")) {
                costItems.addAll(sec.getStringList("cost_items"));
            } else {
                costItems.add(sec.getString("cost_items"));
            }
        }

        for (String itemStr : costItems) {
            String[] parts = itemStr.split(":");
            String id = parts[0];
            int amt = (parts.length > 1) ? Integer.parseInt(parts[1]) : 1;
            if (!plugin.getItemHookManager().hasItem(player, id, amt)) {
                player.sendMessage("§cBạn thiếu vật phẩm: §e" + amt + "x " + plugin.getItemHookManager().getItemDisplayName(id));
                return false;
            }
        }

        // Trừ chi phí
        if (costMoney > 0) plugin.getCurrencyManager().withdrawMoney(player, costMoney);
        if (costPoints > 0) plugin.getCurrencyManager().withdrawPoints(player, costPoints);
        for (String itemStr : costItems) {
            String[] parts = itemStr.split(":");
            String id = parts[0];
            int amt = (parts.length > 1) ? Integer.parseInt(parts[1]) : 1;
            plugin.getItemHookManager().takeItem(player, id, amt);
        }

        List<String> unlocked = plugin.getConfigManager().getData().getStringList(player.getUniqueId() + ".pets." + petId + ".unlocked_skills");
        unlocked.add(skillType.toLowerCase());
        plugin.getConfigManager().getData().set(player.getUniqueId() + ".pets." + petId + ".unlocked_skills", unlocked);
        plugin.getConfigManager().saveData();

        String skillName = sec.getString("name", skillType);
        player.sendMessage("§a§lTHÀNH CÔNG! §fThú cưng đã học được kỹ năng: " + ChatColor.translateAlternateColorCodes('&', skillName));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        return true;
    }

    public boolean triggerUltimate(Player player) {
        if (!plugin.getPetManager().hasPet(player.getUniqueId())) {
            player.sendMessage(plugin.getLanguage().getMessage("pet.no_pet"));
            return false;
        }

        String petId = plugin.getPetManager().getActivePetId(player.getUniqueId());
        Entity pet = plugin.getPetManager().getPet(player.getUniqueId());
        if (pet == null || !pet.isValid()) return false;

        FileConfiguration config = plugin.getModuleManager().getSkillsConfig();
        ConfigurationSection skillSec = config.getConfigurationSection("skills." + petId + ".ultimate");
        if (skillSec == null) {
            player.sendMessage("§cPet này hiện chưa có Tuyệt Chiêu Kích Hoạt!");
            return false;
        }

        if (!isSkillUnlocked(player.getUniqueId(), petId, "ultimate")) {
            player.sendMessage("§cThú cưng chưa học Tuyệt Chiêu này! Hãy mở Bảng Điều Khiển để học.");
            return false;
        }

        // Kiểm tra cooldown
        long now = System.currentTimeMillis();
        long lastUse = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        int cdSec = config.getInt("default_ultimate_cooldown", 45);
        long remaining = (lastUse + (cdSec * 1000L) - now) / 1000L;

        if (remaining > 0) {
            player.sendMessage("§cTuyệt chiêu đang hồi lại! Vui lòng chờ §e" + remaining + "s§c.");
            return false;
        }

        cooldowns.put(player.getUniqueId(), now);

        String skillName = skillSec.getString("name", "Tuyệt Chiêu");
        double radius = skillSec.getDouble("radius", 6.0);
        double damage = skillSec.getDouble("damage", 25.0);
        String pName = skillSec.getString("particle", "EXPLOSION_NORMAL");
        String sName = skillSec.getString("sound", "ENTITY_GENERIC_EXPLODE");

        player.sendMessage("§6§lPET SKILL! §e" + ChatColor.translateAlternateColorCodes('&', skillName) + " §ađã được kích hoạt!");

        SchedulerUtils.runEntityTask(plugin, pet, () -> {
            Location loc = pet.getLocation();
            try {
                pet.getWorld().playSound(loc, Sound.valueOf(sName), 1.5f, 1f);
            } catch (Exception ignored) {}

            try {
                pet.getWorld().spawnParticle(Particle.valueOf(pName), loc.add(0, 1, 0), 40, 1.0, 1.0, 1.0, 0.1);
            } catch (Exception ignored) {}

            for (Entity nearby : pet.getNearbyEntities(radius, radius, radius)) {
                if (nearby instanceof Monster target) {
                    SchedulerUtils.runEntityTask(plugin, target, () -> {
                        if (target.isValid()) {
                            target.damage(damage, player);
                        }
                    });
                }
            }

            if (petId.equals("allay_pet")) {
                SchedulerUtils.runEntityTask(plugin, player, () -> {
                    for (PotionEffect effect : player.getActivePotionEffects()) {
                        player.removePotionEffect(effect.getType());
                    }
                    player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 1));
                });
            } else if (petId.equals("wolf_pet")) {
                SchedulerUtils.runEntityTask(plugin, player, () -> {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 300, 1));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 1));
                });
            }
        });

        return true;
    }
}
