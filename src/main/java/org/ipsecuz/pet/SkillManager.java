package org.ipsecuz.pet;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SkillManager {
    private final IpsecuzPet plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public SkillManager(IpsecuzPet plugin) {
        this.plugin = plugin;
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

            // Gây sát thương hoặc hiệu ứng AoE lên quái vật xung quanh
            for (Entity nearby : pet.getNearbyEntities(radius, radius, radius)) {
                if (nearby instanceof Monster target) {
                    SchedulerUtils.runEntityTask(plugin, target, () -> {
                        if (target.isValid()) {
                            target.damage(damage, player);
                        }
                    });
                }
            }

            // Hiệu ứng đặc biệt cho một số loài
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

