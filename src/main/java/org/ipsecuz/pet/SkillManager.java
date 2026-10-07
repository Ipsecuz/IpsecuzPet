package org.ipsecuz.pet;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SkillManager {
    private final IpsecuzPet plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    private static final Set<String> HARMFUL_POTION_EFFECTS = new HashSet<>(Arrays.asList(
            "POISON", "WITHER", "BLINDNESS", "SLOWNESS", "SLOW", "CONFUSION", "WEAKNESS",
            "DARKNESS", "LEVITATION", "UNLUCK", "SLOW_DIGGING", "MINING_FATIGUE", "BAD_OMEN", "HUNGER"
    ));

    public SkillManager(IpsecuzPet plugin) {
        this.plugin = plugin;
    }

    public boolean isSkillUnlocked(UUID uuid, String petId, String skillType) {
        List<String> unlocked = plugin.getConfigManager().getData().getStringList(uuid + ".pets." + petId + ".unlocked_skills");
        if (unlocked.contains(skillType.toLowerCase())) return true;

        FileConfiguration config = plugin.getModuleManager().getSkillsConfig();
        ConfigurationSection sec = config.getConfigurationSection("skills." + petId + "." + skillType);
        if (sec == null) return false;

        int reqLvl = sec.getInt("req_level", 0);
        int costMoney = sec.getInt("cost_money", 0);
        int costPoints = sec.getInt("cost_points", 0);
        List<String> costItems = sec.getStringList("cost_items");
        return (reqLvl <= 1 && costMoney <= 0 && costPoints <= 0 && costItems.isEmpty());
    }

    public boolean unlockSkill(Player player, String petId, String skillType) {
        if (!plugin.getModuleManager().isSkillsEnabled()) {
            player.sendMessage("§cTính năng Kỹ Năng Pet hiện đang bị tắt bởi máy chủ!");
            return false;
        }

        if (!plugin.getConfigManager().getData().contains(player.getUniqueId() + ".pets." + petId)) {
            player.sendMessage("§cBạn không sở hữu thú cưng này!");
            return false;
        }

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
        if (costMoney > 0 && !plugin.getCurrencyManager().hasMoney(player, costMoney)) {
            player.sendMessage("§cBạn không đủ tiền! Cần: §e$" + costMoney);
            return false;
        }

        int costPoints = sec.getInt("cost_points", 0);
        if (costPoints > 0 && !plugin.getCurrencyManager().hasPoints(player, costPoints)) {
            player.sendMessage("§cBạn không đủ Points! Cần: §b" + costPoints + " Points");
            return false;
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

        // Trừ chi phí giao dịch nguyên tử
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
        if (!plugin.getModuleManager().isSkillsEnabled()) {
            player.sendMessage("§cTính năng Kỹ Năng Pet hiện đang bị tắt bởi máy chủ!");
            return false;
        }

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
                pet.getWorld().spawnParticle(Particle.valueOf(pName), loc.clone().add(0, 1, 0), 40, 1.0, 1.0, 1.0, 0.1);
            } catch (Exception ignored) {}

            // Xử lý hiệu ứng đặc trưng cho từng loại Pet
            if ("allay_pet".equals(petId)) {
                // Khúc Hát Thanh Lọc: CHỈ XÓA HIỆU ỨNG XẤU, GIỮ NGUYÊN BUFF CÓ LỢI!
                SchedulerUtils.runEntityTask(plugin, player, () -> {
                    int cleansed = 0;
                    for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
                        String name = effect.getType().getName().toUpperCase();
                        if (HARMFUL_POTION_EFFECTS.contains(name)) {
                            player.removePotionEffect(effect.getType());
                            cleansed++;
                        }
                    }
                    player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 1));
                    try {
                        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.5f, 1.2f);
                    } catch (Exception ignored) {}
                    if (cleansed > 0) {
                        player.sendMessage("§aKhúc Hát Thanh Lọc đã loại bỏ §e" + cleansed + " §ahiệu ứng tiêu cực!");
                    }
                });
            } else if ("warden_pet".equals(petId)) {
                // Sóng Âm Diệt Vực: Đẩy lùi mạnh và làm choáng quái vật trong 3 giây
                for (Entity nearby : pet.getNearbyEntities(radius, radius, radius)) {
                    if (nearby instanceof Monster target) {
                        SchedulerUtils.runEntityTask(plugin, target, () -> {
                            if (target.isValid()) {
                                Vector push = target.getLocation().toVector().subtract(loc.toVector()).normalize().multiply(1.8).setY(0.4);
                                target.setVelocity(push);
                                try {
                                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 255));
                                    target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 1));
                                } catch (Exception ignored) {}
                                target.damage(damage, player);
                            }
                        });
                    }
                }
            } else if ("iron_golem_pet".equals(petId)) {
                // Địa Chấn Dập Nát: Hất tung toàn bộ quái vật xung quanh lên không trung
                for (Entity nearby : pet.getNearbyEntities(radius, radius, radius)) {
                    if (nearby instanceof Monster target) {
                        SchedulerUtils.runEntityTask(plugin, target, () -> {
                            if (target.isValid()) {
                                target.setVelocity(new Vector(0, 1.1, 0));
                                target.damage(damage, player);
                            }
                        });
                    }
                }
            } else if ("ender_dragon_pet".equals(petId)) {
                // Cầu Lửa Hư Không: Phun sóng xung kích hư không diện rộng
                for (Entity nearby : pet.getNearbyEntities(radius, radius, radius)) {
                    if (nearby instanceof Monster target) {
                        SchedulerUtils.runEntityTask(plugin, target, () -> {
                            if (target.isValid()) {
                                Vector push = target.getLocation().toVector().subtract(loc.toVector()).normalize().multiply(1.2).setY(0.3);
                                target.setVelocity(push);
                                target.damage(damage, player);
                            }
                        });
                    }
                }
            } else if ("wolf_pet".equals(petId)) {
                // Tiếng Hú Đầu Đàn: Ban Sức Mạnh II và Tốc Độ II trong 15s cho chủ
                SchedulerUtils.runEntityTask(plugin, player, () -> {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 300, 1));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 1));
                });
            } else {
                // Mặc định: Gây sát thương các quái vật xung quanh
                for (Entity nearby : pet.getNearbyEntities(radius, radius, radius)) {
                    if (nearby instanceof Monster target) {
                        SchedulerUtils.runEntityTask(plugin, target, () -> {
                            if (target.isValid()) {
                                target.damage(damage, player);
                            }
                        });
                    }
                }
            }
        });

        return true;
    }

    // --- HỆ THỐNG NỘI TẠI (PASSIVES) ---
    public double getDamageReductionPercent(Player player) {
        if (!plugin.getModuleManager().isSkillsEnabled()) return 0.0;
        if (!plugin.getPetManager().hasPet(player.getUniqueId())) return 0.0;
        String petId = plugin.getPetManager().getActivePetId(player.getUniqueId());
        if ("ender_dragon_pet".equals(petId)) {
            return plugin.getModuleManager().getSkillsConfig().getDouble("skills.ender_dragon_pet.passive.damage_reduction_percent", 25.0);
        } else if ("iron_golem_pet".equals(petId)) {
            return 15.0; // Khiên thép khổng lồ
        }
        return 0.0;
    }

    public boolean hasFireImmunity(Player player) {
        if (!plugin.getModuleManager().isSkillsEnabled()) return false;
        if (!plugin.getPetManager().hasPet(player.getUniqueId())) return false;
        String petId = plugin.getPetManager().getActivePetId(player.getUniqueId());
        return "ender_dragon_pet".equals(petId);
    }

    public double getCritChancePercent(Player player) {
        if (!plugin.getModuleManager().isSkillsEnabled()) return 0.0;
        if (!plugin.getPetManager().hasPet(player.getUniqueId())) return 0.0;
        String petId = plugin.getPetManager().getActivePetId(player.getUniqueId());
        if ("wolf_pet".equals(petId)) {
            return plugin.getModuleManager().getSkillsConfig().getDouble("skills.wolf_pet.passive.crit_chance_percent", 20.0);
        }
        return 0.0;
    }

    public double getBonusDamagePercent(Player player) {
        if (!plugin.getModuleManager().isSkillsEnabled()) return 0.0;
        if (!plugin.getPetManager().hasPet(player.getUniqueId())) return 0.0;
        String petId = plugin.getPetManager().getActivePetId(player.getUniqueId());
        if ("warden_pet".equals(petId)) {
            return 10.0; // Âm ba cảm biến: +10% sát thương
        }
        return 0.0;
    }
}
