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

import org.bukkit.persistence.PersistentDataType;

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

    public boolean isValidEnemyTarget(Player owner, Entity entity) {
        if (entity == null || !entity.isValid() || entity.isDead()) return false;
        if (owner != null && entity.equals(owner)) return false;

        // Ignore active pets
        if (entity.getPersistentDataContainer().has(plugin.getPetManager().petKey, PersistentDataType.STRING)) {
            return false;
        }

        // Ignore NPCs and ArmorStands
        if (entity.hasMetadata("NPC") || entity instanceof org.bukkit.entity.ArmorStand) {
            return false;
        }

        // Check PvP target setting
        if (entity instanceof Player targetPlayer) {
            if (!plugin.getConfig().getBoolean("combat.target_players", false)) {
                return false;
            }
            if (targetPlayer.getGameMode() == org.bukkit.GameMode.SPECTATOR || targetPlayer.getGameMode() == org.bukkit.GameMode.CREATIVE) {
                return false;
            }
            return true;
        }

        // Monsters are always valid
        if (entity instanceof Monster) {
            return true;
        }

        // Other hostile mobs / Slimes / Ghasts / Bosses
        if (entity instanceof org.bukkit.entity.Mob mob) {
            if (mob instanceof org.bukkit.entity.Tameable tameable && tameable.isTamed()) {
                if (owner != null && owner.getUniqueId().equals(tameable.getOwnerUniqueId())) {
                    return false;
                }
            }
            return (entity instanceof org.bukkit.entity.Enemy);
        }

        return false;
    }

    public boolean isSkillUnlocked(UUID uuid, String petId, String skillType) {
        List<String> unlocked = plugin.getConfigManager().getData().getStringList(uuid + ".pets." + petId + ".unlocked_skills");
        if (unlocked.contains(skillType.toLowerCase())) return true;

        FileConfiguration config = plugin.getModuleManager().getSkillsConfig();
        ConfigurationSection sec = config.getConfigurationSection("skills." + petId + "." + skillType);
        if (sec == null) return false;

        org.ipsecuz.pet.requirement.RequirementGroup group = plugin.getRequirementManager().parse(sec);
        return group.getRequirements().isEmpty();
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
        int star = (plugin.getEvolutionManager() != null) ? plugin.getEvolutionManager().getStar(player.getUniqueId(), petId) : 1;

        org.ipsecuz.pet.requirement.RequirementGroup group = plugin.getRequirementManager().parse(sec);
        org.ipsecuz.pet.requirement.RequirementContext ctx = new org.ipsecuz.pet.requirement.RequirementContext(player, petId, petLvl, star, plugin);
        if (!plugin.getRequirementManager().executeTransaction(group, ctx)) {
            return false;
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
            Location chargeLoc = pet.getLocation();
            // Giai đoạn 1: SKILL_CHARGE (20 ticks / 1.0 giây chuẩn bị)
            plugin.getModelHandler().playTransientAnimation(pet, PetAnimationState.SKILL_CHARGE, 20L, PetAnimationState.SKILL_CAST);
            try {
                pet.getWorld().playSound(chargeLoc, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.6f);
                pet.getWorld().spawnParticle(Particle.PORTAL, chargeLoc.clone().add(0, 0.8, 0), 20, 0.4, 0.4, 0.4, 0.08);
            } catch (Exception ignored) {}

            // Giai đoạn 2: SKILL_CAST và bùng nổ hiệu ứng sau khi nạp đủ năng lượng
            SchedulerUtils.runEntityTaskLater(plugin, pet, () -> {
                if (!pet.isValid() || !player.isOnline()) return;
                Location loc = pet.getLocation();
                plugin.getModelHandler().playTransientAnimation(pet, PetAnimationState.SKILL_CAST, 30L, PetAnimationState.IDLE);

                // Xử lý hiệu ứng đặc trưng theo bản sắc từng loại Pet (Themed Visual Identity)
                if ("allay_pet".equals(petId)) {
                    // Khúc Hát Thanh Lọc: Chuỗi chuông thạch anh + Vòng sáng thánh tẩy
                    try {
                        pet.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.8f, 1.2f);
                        pet.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_CLUSTER_STEP, 1.5f, 1.5f);
                        pet.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 35, 0.8, 0.8, 0.8, 0.05);
                        pet.getWorld().spawnParticle(Particle.GLOW, loc.clone().add(0, 1, 0), 25, 0.6, 0.6, 0.6, 0.05);
                    } catch (Exception ignored) {}

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
                        if (cleansed > 0) {
                            player.sendMessage("§aKhúc Hát Thanh Lọc đã loại bỏ §e" + cleansed + " §ahiệu ứng tiêu cực!");
                        }
                    });

                } else if ("warden_pet".equals(petId)) {
                    // Sóng Âm Diệt Vực: Nhịp tim tử thần -> Sóng âm rền vang
                    try {
                        pet.getWorld().playSound(loc, Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 0.8f);
                        pet.getWorld().playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.8f, 1f);
                        pet.getWorld().spawnParticle(Particle.SONIC_BOOM, loc.clone().add(0, 1.2, 0), 3, 0.2, 0.2, 0.2, 0.0);
                        pet.getWorld().spawnParticle(Particle.SCULK_SOUL, loc.clone().add(0, 1, 0), 40, 1.2, 0.6, 1.2, 0.08);
                    } catch (Exception ignored) {}

                    for (Entity nearby : pet.getNearbyEntities(radius, radius, radius)) {
                        if (nearby instanceof org.bukkit.entity.LivingEntity target && isValidEnemyTarget(player, target)) {
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
                    // Địa Chấn Dập Nát: Tiếng đe rèn đập nát + Vụ nổ chấn động hất tung
                    try {
                        pet.getWorld().playSound(loc, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.6f, 0.8f);
                        pet.getWorld().playSound(loc, Sound.BLOCK_ANVIL_LAND, 1.4f, 0.9f);
                        pet.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, loc, 3, 0.5, 0.1, 0.5, 0.0);
                        pet.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc.clone().add(0, 0.2, 0), 30, 1.5, 0.2, 1.5, 0.05);
                    } catch (Exception ignored) {}

                    for (Entity nearby : pet.getNearbyEntities(radius, radius, radius)) {
                        if (nearby instanceof org.bukkit.entity.LivingEntity target && isValidEnemyTarget(player, target)) {
                            SchedulerUtils.runEntityTask(plugin, target, () -> {
                                if (target.isValid()) {
                                    target.setVelocity(new Vector(0, 1.25, 0));
                                    target.damage(damage, player);
                                }
                            });
                        }
                    }

                } else if ("ender_dragon_pet".equals(petId)) {
                    // Cầu Lửa Hư Không: Tiếng rống rồng + Làn khói rồng tím hư không
                    try {
                        pet.getWorld().playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.8f, 1f);
                        pet.getWorld().playSound(loc, Sound.ENTITY_ENDER_DRAGON_SHOOT, 1.5f, 0.8f);
                        pet.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc.clone().add(0, 1, 0), 50, 1.2, 0.5, 1.2, 0.1);
                        pet.getWorld().spawnParticle(Particle.PORTAL, loc.clone().add(0, 1, 0), 40, 1.0, 0.8, 1.0, 0.2);
                    } catch (Exception ignored) {}

                    for (Entity nearby : pet.getNearbyEntities(radius, radius, radius)) {
                        if (nearby instanceof org.bukkit.entity.LivingEntity target && isValidEnemyTarget(player, target)) {
                            SchedulerUtils.runEntityTask(plugin, target, () -> {
                                if (target.isValid()) {
                                    Vector push = target.getLocation().toVector().subtract(loc.toVector()).normalize().multiply(1.3).setY(0.35);
                                    target.setVelocity(push);
                                    target.damage(damage, player);
                                }
                            });
                        }
                    }

                } else if ("wolf_pet".equals(petId)) {
                    // Tiếng Hú Đầu Đàn: Tiếng hú lãnh địa + Bão lửa linh hồn
                    try {
                        pet.getWorld().playSound(loc, Sound.ENTITY_WOLF_HOWL, 1.8f, 1f);
                        pet.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 1, 0), 30, 0.8, 0.5, 0.8, 0.05);
                    } catch (Exception ignored) {}

                    SchedulerUtils.runEntityTask(plugin, player, () -> {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 300, 1));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 1));
                        try {
                            player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.05);
                        } catch (Exception ignored) {}
                    });

                } else {
                    // Mặc định: Gây sát thương các quái vật xung quanh kèm hiệu ứng hạt độ hiếm
                    PetRarity rarity = PetRarity.fromPetId(plugin, petId);
                    try {
                        pet.getWorld().playSound(loc, Sound.valueOf(sName), 1.5f, 1f);
                        pet.getWorld().spawnParticle(rarity.getRevealParticle(), loc.clone().add(0, 1, 0), 35, 1.0, 1.0, 1.0, 0.1);
                    } catch (Exception ignored) {}

                    for (Entity nearby : pet.getNearbyEntities(radius, radius, radius)) {
                        if (nearby instanceof org.bukkit.entity.LivingEntity target && isValidEnemyTarget(player, target)) {
                            SchedulerUtils.runEntityTask(plugin, target, () -> {
                                if (target.isValid()) {
                                    target.damage(damage, player);
                                }
                            });
                        }
                    }
                }
            }, 20L);
        });

        return true;
    }

    // --- HỆ THỐNG NỘI TẠI (PASSIVES) ---
    private final Map<String, Long> lastPassiveTick = new ConcurrentHashMap<>();

    public void handlePetTick(Player owner, Entity pet, String petId) {
        if (!plugin.getModuleManager().isSkillsEnabled() || owner == null || pet == null || petId == null) return;
        long now = System.currentTimeMillis();

        if ("allay_pet".equals(petId)) {
            FileConfiguration config = plugin.getModuleManager().getSkillsConfig();
            int intervalSec = config.getInt("skills.allay_pet.passive.regen_interval_seconds", 5);
            double healAmt = config.getDouble("skills.allay_pet.passive.heal_amount", 2.0);
            String passiveKey = owner.getUniqueId() + ":" + petId + ":regen";
            long last = lastPassiveTick.getOrDefault(passiveKey, 0L);
            if (now - last >= intervalSec * 1000L) {
                lastPassiveTick.put(passiveKey, now);
                SchedulerUtils.runEntityTask(plugin, owner, () -> {
                    if (owner.isOnline()) {
                        double maxHp = 20.0;
                        org.bukkit.attribute.AttributeInstance attr = owner.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
                        if (attr != null) maxHp = attr.getValue();
                        if (owner.getHealth() < maxHp) {
                            owner.setHealth(Math.min(maxHp, owner.getHealth() + healAmt));
                            try {
                                owner.getWorld().spawnParticle(Particle.HEART, owner.getLocation().add(0, 1.2, 0), 2, 0.3, 0.3, 0.3, 0.0);
                                owner.getWorld().playSound(owner.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.5f);
                            } catch (Exception ignored) {}
                        }
                    }
                });
            }
        } else if ("warden_pet".equals(petId)) {
            FileConfiguration config = plugin.getModuleManager().getSkillsConfig();
            int intervalSec = config.getInt("skills.warden_pet.passive.detect_interval_seconds", 3);
            double radius = config.getDouble("skills.warden_pet.passive.detect_invisible_radius", 10.0);
            String passiveKey = owner.getUniqueId() + ":" + petId + ":detect";
            long last = lastPassiveTick.getOrDefault(passiveKey, 0L);
            if (now - last >= intervalSec * 1000L) {
                lastPassiveTick.put(passiveKey, now);
                SchedulerUtils.runEntityTask(plugin, pet, () -> {
                    if (!pet.isValid()) return;
                    boolean foundInvisible = false;
                    for (Entity nearby : pet.getNearbyEntities(radius, radius, radius)) {
                        if (nearby instanceof org.bukkit.entity.LivingEntity living && isValidEnemyTarget(owner, living)) {
                            if (living.isInvisible() || living.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                                living.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 80, 0, false, false, true));
                                foundInvisible = true;
                            }
                        }
                    }
                    if (foundInvisible) {
                        try {
                            pet.getWorld().spawnParticle(Particle.SCULK_SOUL, pet.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.05);
                            pet.getWorld().playSound(pet.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f, 1.2f);
                        } catch (Exception ignored) {}
                    }
                });
            }
        }
    }

    public double getDamageReductionPercent(Player player) {
        if (!plugin.getModuleManager().isSkillsEnabled()) return 0.0;
        if (!plugin.getPetManager().hasPet(player.getUniqueId())) return 0.0;
        String petId = plugin.getPetManager().getActivePetId(player.getUniqueId());
        if (petId == null) return 0.0;
        FileConfiguration config = plugin.getModuleManager().getSkillsConfig();
        if ("ender_dragon_pet".equals(petId)) {
            return config.getDouble("skills.ender_dragon_pet.passive.damage_reduction_percent", 25.0);
        } else if ("iron_golem_pet".equals(petId)) {
            return config.getDouble("skills.iron_golem_pet.passive.damage_reduction_percent", 15.0);
        }
        return config.getDouble("skills." + petId + ".passive.damage_reduction_percent", 0.0);
    }

    public boolean hasFireImmunity(Player player) {
        if (!plugin.getModuleManager().isSkillsEnabled()) return false;
        if (!plugin.getPetManager().hasPet(player.getUniqueId())) return false;
        String petId = plugin.getPetManager().getActivePetId(player.getUniqueId());
        if (petId == null) return false;
        FileConfiguration config = plugin.getModuleManager().getSkillsConfig();
        return config.getBoolean("skills." + petId + ".passive.fire_resistance", "ender_dragon_pet".equals(petId));
    }

    public double getCritChancePercent(Player player) {
        if (!plugin.getModuleManager().isSkillsEnabled()) return 0.0;
        if (!plugin.getPetManager().hasPet(player.getUniqueId())) return 0.0;
        String petId = plugin.getPetManager().getActivePetId(player.getUniqueId());
        if (petId == null) return 0.0;
        FileConfiguration config = plugin.getModuleManager().getSkillsConfig();
        if ("wolf_pet".equals(petId)) {
            return config.getDouble("skills.wolf_pet.passive.crit_chance_percent", 20.0);
        }
        return config.getDouble("skills." + petId + ".passive.crit_chance_percent", 0.0);
    }

    public double getBonusDamagePercent(Player player) {
        if (!plugin.getModuleManager().isSkillsEnabled()) return 0.0;
        if (!plugin.getPetManager().hasPet(player.getUniqueId())) return 0.0;
        String petId = plugin.getPetManager().getActivePetId(player.getUniqueId());
        if (petId == null) return 0.0;
        FileConfiguration config = plugin.getModuleManager().getSkillsConfig();
        if ("warden_pet".equals(petId)) {
            return config.getDouble("skills.warden_pet.passive.bonus_damage_percent", 10.0);
        }
        return config.getDouble("skills." + petId + ".passive.bonus_damage_percent", 0.0);
    }

    public void clearPassiveState(UUID ownerId) {
        if (ownerId == null) return;
        String prefix = ownerId.toString() + ":";
        lastPassiveTick.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public void clearCooldown(UUID uuid) {
        cooldowns.remove(uuid);
        clearPassiveState(uuid);
    }

    public void clearAllCooldowns() {
        cooldowns.clear();
        lastPassiveTick.clear();
    }
}
