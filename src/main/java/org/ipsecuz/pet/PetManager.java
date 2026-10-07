package org.ipsecuz.pet;

import org.bukkit.*;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PetManager {
    private final IpsecuzPet plugin;

    private final Map<UUID, Entity> activePets = new ConcurrentHashMap<>();
    private final Map<UUID, String> activePetIds = new ConcurrentHashMap<>();

    public final Map<UUID, UUID> duelRequests = new ConcurrentHashMap<>();
    public final Map<UUID, UUID> activeDuels = new ConcurrentHashMap<>();

    public final NamespacedKey petKey;
    public final NamespacedKey petIdKey;

    private final ModelHandler modelHandler;

    public PetManager(IpsecuzPet plugin) {
        this.plugin = plugin;
        this.petKey = new NamespacedKey(plugin, "ipsecuz_pet_owner");
        this.petIdKey = new NamespacedKey(plugin, "pet_id");
        this.modelHandler = plugin.getModelHandler();
    }

    public ModelHandler getModelHandler() {
        return modelHandler;
    }

    public void spawnPet(Player player, String petId) {
        if (plugin.getConfigManager().isPetDead(player.getUniqueId(), petId)) {
            player.sendMessage(plugin.getLanguage().getMessage("pet.death"));
            return;
        }
        removePet(player.getUniqueId());

        String typeStr = plugin.getConfig().getString("pets." + petId + ".type", "ZOMBIE");
        EntityType type;
        try {
            type = EntityType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            type = EntityType.ZOMBIE;
        }

        Entity pet = player.getWorld().spawnEntity(player.getLocation(), type);

        // --- CODE HIỂN THỊ TÊN (SỬ DỤNG TÊN TÙY CHỈNH) ---
        String defaultName = plugin.getConfig().getString("pets." + petId + ".name", "Pet");
        String customName = plugin.getConfigManager().getCustomName(player.getUniqueId(), petId);
        String name = (customName != null) ? customName : defaultName;

        int lvl = plugin.getConfigManager().getData().getInt(player.getUniqueId() + ".pets." + petId + ".level", 1);
        String format = plugin.getLanguage().getMessage("pet.display_format");
        if (format == null) format = "&7Lv.%level% &e%name% &7(%player%)";

        String displayName = format
                .replace("%name%", name)
                .replace("%level%", String.valueOf(lvl))
                .replace("%player%", player.getName())
                .replace("%owner%", player.getName());

        pet.setCustomName(ChatColor.translateAlternateColorCodes('&', displayName));
        pet.setCustomNameVisible(true);
        // ------------------------------------------------------------

        pet.getPersistentDataContainer().set(petKey, PersistentDataType.STRING, player.getUniqueId().toString());
        pet.getPersistentDataContainer().set(petIdKey, PersistentDataType.STRING, petId);
        pet.setMetadata("pet_owner", new FixedMetadataValue(plugin, player.getUniqueId().toString()));
        pet.setMetadata("IPSECUZ_PET", new FixedMetadataValue(plugin, player.getUniqueId().toString()));

        // Tùy chọn Kích thước: Bé con (Baby) hoặc Trưởng thành (Adult)
        boolean isBaby = plugin.getConfigManager().isPetBaby(player.getUniqueId(), petId);

        // Hook Model: Hỗ trợ model_id_baby riêng biệt cho dạng con
        String modelId = null;
        if (isBaby && plugin.getConfig().contains("pets." + petId + ".model_id_baby")) {
            modelId = plugin.getConfig().getString("pets." + petId + ".model_id_baby");
        } else {
            modelId = plugin.getConfig().getString("pets." + petId + ".model_id", null);
        }

        if (pet instanceof LivingEntity living) {
            living.setRemoveWhenFarAway(false);
            living.setCanPickupItems(false);
            living.setCollidable(false);

            // Scale kích cỡ pet thực tế (0.55 cho bé con, 1.0 cho trưởng thành)
            applyScale(living, isBaby ? 0.55 : 1.0);

            if (modelId != null && !modelId.trim().isEmpty()) {
                living.setInvisible(true);
                modelHandler.spawnModel(player, pet, modelId);
            } else {
                living.setInvisible(false);
            }

            if (plugin.getConfig().getBoolean("pets." + petId + ".silent", true)) {
                living.setSilent(true);
            }
        }

        if (pet instanceof Ageable ageable) {
            if (isBaby) {
                ageable.setBaby();
                ageable.setAgeLock(true); // Khóa tuổi không cho tự lớn lên
            } else {
                ageable.setAdult();
            }
        } else if (pet instanceof Zombie zombie) {
            zombie.setBaby(isBaby);
        } else if (pet instanceof Piglin piglin) {
            piglin.setBaby(isBaby);
        }

        if (pet instanceof Tameable tameable) {
            tameable.setOwner(player);
            tameable.setTamed(true);
        }

        updatePetStats(pet, petId, lvl, player.getUniqueId());

        activePets.put(player.getUniqueId(), pet);
        activePetIds.put(player.getUniqueId(), petId);

        String msg = plugin.getLanguage().getMessage("pet.spawn");
        if (msg != null) player.sendMessage(msg.replace("%pet_name%", displayName));
        player.playSound(player.getLocation(), Sound.ENTITY_CHICKEN_EGG, 1f, 1f);
    }

    public void removePet(UUID ownerId) {
        if (activePets.containsKey(ownerId)) {
            Entity e = activePets.get(ownerId);
            if (e != null && e.isValid()) {
                SchedulerUtils.runEntityTask(plugin, e, () -> {
                    if (e.isValid()) {
                        modelHandler.removeModel(e.getUniqueId());
                        e.remove();
                    }
                });
            }
            activePets.remove(ownerId);
            activePetIds.remove(ownerId);
        }
    }

    public void removeAllPets() {
        for (UUID uuid : new ArrayList<>(activePets.keySet())) {
            removePet(uuid);
        }
        modelHandler.removeAll();
    }

    public void refreshPetStats(Player player) {
        if (!hasPet(player.getUniqueId())) return;
        Entity pet = getPet(player.getUniqueId());
        String petId = getActivePetId(player.getUniqueId());
        int lvl = plugin.getConfigManager().getData().getInt(player.getUniqueId() + ".pets." + petId + ".level", 1);
        if (pet != null && pet.isValid()) {
            updatePetStats(pet, petId, lvl, player.getUniqueId());
        }
    }

    public void updatePetStats(Entity entity, String petId, int level, UUID ownerId) {
        if (!(entity instanceof Attributable attrEntity)) return;

        double baseHp = plugin.getConfig().getDouble("pets." + petId + ".stats.health", 20.0);
        double baseDmg = plugin.getConfig().getDouble("pets." + petId + ".stats.damage", 5.0);
        double baseDef = plugin.getConfig().getDouble("pets." + petId + ".stats.defense", 0.0);
        double baseSpd = plugin.getConfig().getDouble("pets." + petId + ".stats.speed", 0.25);

        double growthHp = plugin.getConfig().getDouble("pets." + petId + ".growth.health",
                plugin.getConfig().getDouble("rpg_system.default_growth.health", 2.0));
        double growthDmg = plugin.getConfig().getDouble("pets." + petId + ".growth.damage",
                plugin.getConfig().getDouble("rpg_system.default_growth.damage", 0.5));
        double growthDef = plugin.getConfig().getDouble("pets." + petId + ".growth.defense",
                plugin.getConfig().getDouble("rpg_system.default_growth.defense", 0.2));
        double growthSpd = plugin.getConfig().getDouble("pets." + petId + ".growth.speed",
                plugin.getConfig().getDouble("rpg_system.default_growth.speed", 0.001));

        int effectiveLevel = Math.max(1, level);
        double maxHp = baseHp + ((effectiveLevel - 1) * growthHp);
        double damage = baseDmg + ((effectiveLevel - 1) * growthDmg);
        double defense = baseDef + ((effectiveLevel - 1) * growthDef);
        double speed = baseSpd + ((effectiveLevel - 1) * growthSpd);

        // 1. Hệ số Sao (Evolution)
        double starMultiplier = (plugin.getEvolutionManager() != null && ownerId != null)
                ? plugin.getEvolutionManager().getStarMultiplier(ownerId, petId) : 1.0;
        maxHp *= starMultiplier;
        damage *= starMultiplier;
        defense *= starMultiplier;

        // 2. Hệ số Trait
        if (ownerId != null) {
            String traitName = plugin.getConfigManager().getData().getString(ownerId + ".pets." + petId + ".trait", "NONE");
            PetTrait trait = PetTrait.fromString(traitName);
            maxHp *= trait.getHealthMultiplier();
            damage *= trait.getDamageMultiplier();
            defense *= trait.getDefenseMultiplier();
            speed *= trait.getSpeedMultiplier();
        }

        // 3. Hệ số Thân thiết (Happiness)
        if (plugin.getFeedingManager() != null && ownerId != null) {
            int happy = plugin.getFeedingManager().getHappiness(ownerId, petId);
            if (happy >= 80) {
                speed *= 1.15;
            } else if (happy < 20) {
                maxHp *= 0.85;
                damage *= 0.85;
                speed *= 0.85;
                defense *= 0.85;
            }
        }

        if (maxHp <= 0) maxHp = 20.0;

        if (attrEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            attrEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(maxHp);
        }

        if (entity instanceof LivingEntity living) {
            if (living.getHealth() > maxHp) {
                living.setHealth(maxHp);
            }
        }

        if (attrEntity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
            attrEntity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(speed);
        }

        if (attrEntity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
            attrEntity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(damage);
        }

        if (attrEntity.getAttribute(Attribute.GENERIC_ARMOR) != null) {
            attrEntity.getAttribute(Attribute.GENERIC_ARMOR).setBaseValue(defense);
        }

        entity.setMetadata("pet_damage", new FixedMetadataValue(plugin, damage));
        entity.setMetadata("pet_defense", new FixedMetadataValue(plugin, defense));
    }

    public void startPetTask() {
        SchedulerUtils.runGlobalTimer(plugin, this::runPetLogic, 1L, 5L);
    }

    private boolean isFlyingType(EntityType type) {
        String name = type.name();
        return name.contains("ALLAY") || name.contains("BAT") || name.contains("BEE")
                || name.contains("PHANTOM") || name.contains("PARROT") || name.contains("GHAST")
                || name.contains("ENDER_DRAGON") || name.contains("WITHER") || name.contains("VEX");
    }

    private void runPetLogic() {
        for (Map.Entry<UUID, Entity> entry : activePets.entrySet()) {
            UUID ownerId = entry.getKey();
            Entity pet = entry.getValue();
            Player owner = Bukkit.getPlayer(ownerId);

            if (owner == null || !owner.isOnline()) {
                removePet(ownerId);
                continue;
            }

            if (pet == null || !pet.isValid()) {
                removePet(ownerId);
                continue;
            }

            SchedulerUtils.runEntityTask(plugin, pet, () -> {
                if (!pet.isValid() || !owner.isOnline()) return;

                String petId = activePetIds.get(ownerId);
                Location petLoc = pet.getLocation();
                Location ownerLoc = owner.getLocation();

                boolean isFlying = isFlyingType(pet.getType());
                if (petLoc.distanceSquared(ownerLoc) > 400) {
                    SchedulerUtils.teleportAsync(pet, ownerLoc);
                } else if (isFlying) {
                    Location targetHover = ownerLoc.clone().add(0, 1.2, 0);
                    org.bukkit.util.Vector dir = targetHover.toVector().subtract(petLoc.toVector());
                    double dist = dir.length();
                    if (dist > 2.0) {
                        dir.normalize().multiply(Math.min(0.35, dist * 0.08));
                        pet.setVelocity(dir);
                    }
                } else if (petLoc.distanceSquared(ownerLoc) > 9) {
                    if (pet instanceof Mob mob) mob.getPathfinder().moveTo(owner);
                }

                modelHandler.updatePosition(pet);
                modelHandler.updateAnimation(pet);

                playParticles(pet, petId);

                if (System.currentTimeMillis() % 2000 < 250) {
                    applyBuffs(owner, petId, ownerId);
                }
            });
        }
    }

    private void applyBuffs(Player p, String petId, UUID uuid) {
        int level = plugin.getConfigManager().getData().getInt(uuid + ".pets." + petId + ".level", 1);
        double intel = plugin.getConfigManager().getPetStat(petId, level, "intelligence");
        int duration = 60 + ((int) intel * 2);

        List<String> effects = plugin.getConfig().getStringList("pets." + petId + ".effects");
        for (String s : effects) {
            try {
                String[] parts = s.split(":");
                PotionEffectType type = PotionEffectType.getByName(parts[0]);
                if (type != null) {
                    p.addPotionEffect(new PotionEffect(type, duration, Integer.parseInt(parts[1]), false, false, true));
                }
            } catch (Exception ignored) {}
        }
    }

    private void playParticles(Entity e, String petId) {
        String pName = plugin.getConfig().getString("pets." + petId + ".particle");
        if (pName != null && !pName.isEmpty()) {
            try {
                e.getWorld().spawnParticle(Particle.valueOf(pName), e.getLocation().add(0, 0.5, 0), 1, 0.2, 0.2, 0.2, 0.0);
            } catch (Exception ignored) {}
        }
    }

    public boolean hasPet(UUID uuid) { return activePets.containsKey(uuid); }
    public Entity getPet(UUID uuid) { return activePets.get(uuid); }
    public String getActivePetId(UUID uuid) { return activePetIds.get(uuid); }

    public void showPetStats(Player player, Entity pet) {
        String petId = pet.getPersistentDataContainer().get(petIdKey, PersistentDataType.STRING);
        if (petId == null) return;

        String defaultName = plugin.getConfig().getString("pets." + petId + ".name", "Pet");
        String customName = plugin.getConfigManager().getCustomName(player.getUniqueId(), petId);
        String name = (customName != null) ? customName : defaultName;

        ConfigManager cm = plugin.getConfigManager();
        int lvl = cm.getData().getInt(player.getUniqueId() + ".pets." + petId + ".level", 1);
        int exp = cm.getData().getInt(player.getUniqueId() + ".pets." + petId + ".exp", 0);
        int maxLvl = plugin.getConfig().getInt("rpg_system.max_level", 100);
        int req = lvl * plugin.getConfig().getInt("rpg_system.base_exp_requirement", 50);

        int stars = (plugin.getEvolutionManager() != null) ? plugin.getEvolutionManager().getStar(player.getUniqueId(), petId) : 1;
        String starDisplay = (plugin.getEvolutionManager() != null) ? plugin.getEvolutionManager().getStarDisplay(stars) : (stars + "⭐");
        String traitName = cm.getData().getString(player.getUniqueId() + ".pets." + petId + ".trait", "NONE");
        PetTrait trait = PetTrait.fromString(traitName);
        PetRarity rarity = PetRarity.fromPetId(plugin, petId);

        double dmg = cm.getPetStat(petId, lvl, "damage");
        double hp = cm.getPetStat(petId, lvl, "health");
        double def = cm.getPetStat(petId, lvl, "defense");
        double spd = cm.getPetStat(petId, lvl, "speed");

        LanguageManager lang = plugin.getLanguage();
        player.sendMessage(lang.getMessage("pet.stats_header"));
        player.sendMessage(lang.getMessage("pet.stats_title", "%pet_name%", name));
        player.sendMessage("§7Độ hiếm: " + rarity.getFormattedName() + " §7| Sao: " + starDisplay);
        player.sendMessage("§7Đặc chất (Trait): " + trait.getFormattedName());
        if (lvl >= maxLvl) {
            player.sendMessage("§7Cấp độ: §6Lv." + lvl + " §e[TỐI ĐA (MAX)]");
        } else {
            player.sendMessage(lang.getMessage("pet.stats_level", "%level%", String.valueOf(lvl)));
            player.sendMessage(lang.getMessage("pet.stats_exp", "%exp%", String.valueOf(exp), "%req%", String.valueOf(req)));
        }
        player.sendMessage(lang.getMessage("pet.stats_damage", "%val%", String.format("%.1f", dmg)));
        player.sendMessage(lang.getMessage("pet.stats_health", "%val%", String.format("%.1f", hp)));
        player.sendMessage(lang.getMessage("pet.stats_defense", "%val%", String.format("%.1f", def)));
        player.sendMessage(lang.getMessage("pet.stats_speed", "%val%", String.format("%.3f", spd)));
        player.sendMessage(lang.getMessage("pet.stats_header"));
    }

    public void givePetExp(Player p, int amount) {
        if (!hasPet(p.getUniqueId()) || amount <= 0) return;
        String petId = activePetIds.get(p.getUniqueId());
        int currentExp = plugin.getConfigManager().getData().getInt(p.getUniqueId() + ".pets." + petId + ".exp", 0);
        int currentLvl = plugin.getConfigManager().getData().getInt(p.getUniqueId() + ".pets." + petId + ".level", 1);
        int maxLvl = plugin.getConfig().getInt("rpg_system.max_level", 100);

        if (currentLvl >= maxLvl) {
            return;
        }

        // --- TÍNH HỆ SỐ NHÂN KINH NGHIỆM AN TOÀN ---
        double multiplier = 1.0;

        // 1. Quét quyền hạn của người chơi trực tiếp (ví dụ: ipsecuzpet.multiplier.1.5)
        try {
            for (org.bukkit.permissions.PermissionAttachmentInfo info : p.getEffectivePermissions()) {
                if (info == null || !info.getValue()) continue;
                String perm = info.getPermission();
                if (perm != null && perm.startsWith("ipsecuzpet.multiplier.")) {
                    try {
                        double permVal = Double.parseDouble(perm.substring("ipsecuzpet.multiplier.".length()));
                        if (permVal > multiplier) {
                            multiplier = permVal;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (Exception ignored) {}

        // 2. Quét cấu hình từ config.yml
        ConfigurationSection multSec = plugin.getConfig().getConfigurationSection("rpg_system.xp_multiplier_permissions");
        if (multSec != null) {
            for (Map.Entry<String, Object> entry : multSec.getValues(true).entrySet()) {
                if (entry.getValue() instanceof ConfigurationSection) continue;
                try {
                    double permMultiplier = Double.parseDouble(entry.getValue().toString());
                    String key = entry.getKey();
                    if (p.hasPermission(key) || p.hasPermission("ipsecuzpet.multiplier." + key)) {
                        if (permMultiplier > multiplier) {
                            multiplier = permMultiplier;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        // 3. Thưởng / Phạt theo độ vui vẻ (Happiness)
        if (plugin.getFeedingManager() != null) {
            int happy = plugin.getFeedingManager().getHappiness(p.getUniqueId(), petId);
            if (happy >= 80) {
                multiplier *= 1.25;
            } else if (happy < 20) {
                multiplier *= 0.75;
            }
        }

        // 4. Trait SCHOLAR thưởng thêm 25% EXP
        String traitName = plugin.getConfigManager().getData().getString(p.getUniqueId() + ".pets." + petId + ".trait", "NONE");
        if ("SCHOLAR".equalsIgnoreCase(traitName)) {
            multiplier *= 1.25;
        }

        int finalAmount = Math.max(1, (int) (amount * multiplier));

        // --- VÒNG LẶP NÂNG CẤP ĐA TẦNG (MULTI-LEVEL PROGRESSION LOOP) ---
        long accumulatedExp = (long) currentExp + finalAmount;
        int baseExpRequirement = plugin.getConfig().getInt("rpg_system.base_exp_requirement", 50);
        int levelsGained = 0;

        while (currentLvl < maxLvl) {
            int nextLvlExp = currentLvl * baseExpRequirement;
            if (accumulatedExp >= nextLvlExp) {
                accumulatedExp -= nextLvlExp;
                currentLvl++;
                levelsGained++;
            } else {
                break;
            }
        }

        int finalExp = (currentLvl >= maxLvl) ? 0 : (int) Math.min(Integer.MAX_VALUE, accumulatedExp);

        plugin.getConfigManager().getData().set(p.getUniqueId() + ".pets." + petId + ".level", currentLvl);
        plugin.getConfigManager().getData().set(p.getUniqueId() + ".pets." + petId + ".exp", finalExp);
        plugin.getConfigManager().saveData();

        if (levelsGained > 0) {
            // Âm thanh vinh quang
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);

            // Thông báo lên cấp (Đơn cấp hoặc Đa cấp)
            if (levelsGained == 1) {
                String msg = plugin.getLanguage().getMessage("pet.levelup");
                if (msg != null) p.sendMessage(msg.replace("%level%", String.valueOf(currentLvl)));
            } else {
                p.sendMessage("§a§l★ TIẾN HÓA CẤP ĐỘ! §fThú cưng đã tăng vọt §e+" + levelsGained + " Cấp §f(Đạt cấp: §6Lv." + currentLvl + "§f)!");
            }

            // Phần thưởng bùa lợi (Level up rewards buff)
            List<String> rewards = plugin.getConfig().getStringList("rpg_system.level_up_rewards.effects");
            int rewardDurationSec = plugin.getConfig().getInt("rpg_system.level_up_rewards.duration", 120);
            int ticks = rewardDurationSec * 20;
            for (String rw : rewards) {
                try {
                    String[] parts = rw.split(":");
                    PotionEffectType pet = PotionEffectType.getByName(parts[0]);
                    int amp = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                    if (pet != null) {
                        p.addPotionEffect(new PotionEffect(pet, ticks, amp));
                    }
                } catch (Exception ignored) {}
            }

            // Cập nhật thực thể đang triệu hồi
            Entity petEntity = activePets.get(p.getUniqueId());
            if (petEntity != null && petEntity.isValid()) {
                updatePetStats(petEntity, petId, currentLvl, p.getUniqueId());

                String defaultName = plugin.getConfig().getString("pets." + petId + ".name", "Pet");
                String customName = plugin.getConfigManager().getCustomName(p.getUniqueId(), petId);
                String name = (customName != null) ? customName : defaultName;
                String format = plugin.getLanguage().getMessage("pet.display_format");
                if (format == null) format = "&7Lv.%level% &e%name% &7(%player%)";

                String displayName = format.replace("%name%", name)
                        .replace("%level%", String.valueOf(currentLvl))
                .replace("%player%", p.getName())
                        .replace("%owner%", p.getName());

                petEntity.setCustomName(ChatColor.translateAlternateColorCodes('&', displayName));
                petEntity.setCustomNameVisible(true);
            }
        }
    }

    public static void applyScale(LivingEntity entity, double scale) {
        if (entity == null || !entity.isValid()) return;
        try {
            Attribute scaleAttr = Attribute.valueOf("GENERIC_SCALE");
            if (scaleAttr != null && entity.getAttribute(scaleAttr) != null) {
                entity.getAttribute(scaleAttr).setBaseValue(scale);
                return;
            }
        } catch (Exception ignored) {}
        if (entity instanceof Slime slime) {
            slime.setSize(scale < 0.8 ? 1 : 2);
        }
    }
}