package org.ipsecuz.pet;

import org.bukkit.*;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
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
        this.modelHandler = new ModelHandler(plugin);
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

        if (pet instanceof LivingEntity living) {
            living.setRemoveWhenFarAway(false);
            living.setCanPickupItems(false);
            living.setCollidable(false);
            living.setInvisible(true); // Ẩn mob gốc
            if (plugin.getConfig().getBoolean("pets." + petId + ".silent", true)) {
                living.setSilent(true);
            }
        }

        if (pet instanceof Ageable ageable) ageable.setAdult();
        if (pet instanceof Tameable tameable) {
            tameable.setOwner(player);
            tameable.setTamed(true);
        }

        updatePetStats(pet, petId, lvl);

        // Hook Model
        String modelId = plugin.getConfig().getString("pets." + petId + ".model_id", petId);
        // --- THAY ĐỔI QUAN TRỌNG: TRUYỀN THÊM ĐỐI TƯỢNG PLAYER ---
        modelHandler.spawnModel(player, pet, modelId);
        // -----------------------------------------------------------

        activePets.put(player.getUniqueId(), pet);
        activePetIds.put(player.getUniqueId(), petId);

        String msg = plugin.getLanguage().getMessage("pet.spawn");
        if(msg != null) player.sendMessage(msg.replace("%pet_name%", displayName));
        player.playSound(player.getLocation(), Sound.ENTITY_CHICKEN_EGG, 1f, 1f);
    }

    public void removePet(UUID ownerId) {
        if (activePets.containsKey(ownerId)) {
            Entity e = activePets.get(ownerId);
            if (e != null && e.isValid()) {
                // --- SỬA LỖI FOLIA: CHẠY TRÊN LUỒNG CỦA ENTITY ---
                SchedulerUtils.runEntityTask(plugin, e, () -> {
                    if (e.isValid()) {
                        modelHandler.removeModel(e.getUniqueId());
                        e.remove();
                    }
                });
                // ----------------------------------------------------
            }
            activePets.remove(ownerId);
            activePetIds.remove(ownerId);
        }
    }

    public void removeAllPets() {
        // Tạo một bản sao của keySet để tránh ConcurrentModificationException
        for (UUID uuid : new ArrayList<>(activePets.keySet())) {
            removePet(uuid);
        }
        modelHandler.removeAll();
    }

    private void updatePetStats(Entity entity, String petId, int level) {
        if (!(entity instanceof Attributable attrEntity)) return;

        double maxHp = plugin.getConfigManager().getPetStat(petId, level, "health");
        double speed = plugin.getConfigManager().getPetStat(petId, level, "speed");
        double damage = plugin.getConfigManager().getPetStat(petId, level, "damage");

        if (maxHp <= 0) maxHp = 20;

        if (attrEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null)
            attrEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(maxHp);

        if (entity instanceof LivingEntity living) living.setHealth(maxHp);

        if (attrEntity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null)
            attrEntity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(speed);

        if (attrEntity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null)
            attrEntity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(damage);

        entity.setMetadata("pet_damage", new FixedMetadataValue(plugin, damage));
    }

    public void startPetTask() {
        SchedulerUtils.runGlobalTimer(plugin, this::runPetLogic, 1L, 5L);
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

            // Chạy Logic trên từng Entity (Folia Safe)
            SchedulerUtils.runEntityTask(plugin, pet, () -> {
                if (!pet.isValid() || !owner.isOnline()) return;

                String petId = activePetIds.get(ownerId);
                Location petLoc = pet.getLocation();
                Location ownerLoc = owner.getLocation();

                if (petLoc.distanceSquared(ownerLoc) > 400) {
                    SchedulerUtils.teleportAsync(pet, ownerLoc);
                }
                else if (petLoc.distanceSquared(ownerLoc) > 9) {
                    if (pet instanceof Mob mob) mob.getPathfinder().moveTo(owner);
                }

                // --- THAY ĐỔI QUAN TRỌNG: CẬP NHẬT VỊ TRÍ VÀ HOẠT ẢNH ---
                modelHandler.updatePosition(pet);
                modelHandler.updateAnimation(pet);
                // ------------------------------------------------------------

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
        int duration = 60 + ((int)intel * 2);

        List<String> effects = plugin.getConfig().getStringList("pets." + petId + ".effects");
        for(String s : effects) {
            try {
                String[] parts = s.split(":");
                PotionEffectType type = PotionEffectType.getByName(parts[0]);
                if (type != null) {
                    p.addPotionEffect(new PotionEffect(type, duration, Integer.parseInt(parts[1]), false, false, true));
                }
            } catch(Exception e){}
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

    // --- HÀM MỚI: HIỂN THỊ CHỈ SỐ PET ---
    public void showPetStats(Player player, Entity pet) {
        String petId = pet.getPersistentDataContainer().get(petIdKey, PersistentDataType.STRING);
        if (petId == null) return;

        String defaultName = plugin.getConfig().getString("pets." + petId + ".name", "Pet");
        String customName = plugin.getConfigManager().getCustomName(player.getUniqueId(), petId);
        String name = (customName != null) ? customName : defaultName;

        ConfigManager cm = plugin.getConfigManager();
        int lvl = cm.getData().getInt(player.getUniqueId() + ".pets." + petId + ".level");
        int exp = cm.getData().getInt(player.getUniqueId() + ".pets." + petId + ".exp");
        int req = lvl * plugin.getConfig().getInt("rpg_system.base_exp_requirement", 50);

        double dmg = cm.getPetStat(petId, lvl, "damage");
        double hp = cm.getPetStat(petId, lvl, "health");
        double def = cm.getPetStat(petId, lvl, "defense");
        double spd = cm.getPetStat(petId, lvl, "speed");

        LanguageManager lang = plugin.getLanguage();
        player.sendMessage(lang.getMessage("pet.stats_header"));
        player.sendMessage(lang.getMessage("pet.stats_title", "%pet_name%", name));
        player.sendMessage(lang.getMessage("pet.stats_level", "%level%", String.valueOf(lvl)));
        player.sendMessage(lang.getMessage("pet.stats_exp", "%exp%", String.valueOf(exp), "%req%", String.valueOf(req)));
        player.sendMessage(lang.getMessage("pet.stats_damage", "%val%", String.format("%.1f", dmg)));
        player.sendMessage(lang.getMessage("pet.stats_health", "%val%", String.format("%.1f", hp)));
        player.sendMessage(lang.getMessage("pet.stats_defense", "%val%", String.format("%.1f", def)));
        player.sendMessage(lang.getMessage("pet.stats_speed", "%val%", String.format("%.3f", spd)));
        player.sendMessage(lang.getMessage("pet.stats_header"));
    }
    // -----------------------------------------

    public void givePetExp(Player p, int amount) {
        if (!hasPet(p.getUniqueId())) return;
        String petId = activePetIds.get(p.getUniqueId());
        int currentExp = plugin.getConfigManager().getData().getInt(p.getUniqueId() + ".pets." + petId + ".exp");
        int currentLvl = plugin.getConfigManager().getData().getInt(p.getUniqueId() + ".pets." + petId + ".level");

        int baseExpRequirement = plugin.getConfig().getInt("rpg_system.base_exp_requirement", 50);
        int nextLvlExp = currentLvl * baseExpRequirement;

        // --- LOGIC MỚI: TÍNH HỆ SỐ NHÂN KINH NGHIỆM ---
        double multiplier = 1.0;
        if (plugin.getConfig().contains("rpg_system.xp_multiplier_permissions")) {
            for (Map.Entry<String, Object> entry : plugin.getConfig().getConfigurationSection("rpg_system.xp_multiplier_permissions").getValues(false).entrySet()) {
                if (p.hasPermission(entry.getKey())) {
                    double permMultiplier = Double.parseDouble(entry.getValue().toString());
                    if (permMultiplier > multiplier) {
                        multiplier = permMultiplier;
                    }
                }
            }
        }
        int finalAmount = (int) (amount * multiplier);
        // ----------------------------------------------------

        currentExp += finalAmount;
        if (currentExp >= nextLvlExp) {
            currentExp -= nextLvlExp;
            currentLvl++;
            plugin.getConfigManager().getData().set(p.getUniqueId() + ".pets." + petId + ".level", currentLvl);

            String msg = plugin.getLanguage().getMessage("pet.levelup");
            if(msg != null) p.sendMessage(msg.replace("%level%", String.valueOf(currentLvl)));

            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);

            Entity pet = activePets.get(p.getUniqueId());
            if (pet != null) {
                updatePetStats(pet, petId, currentLvl);

                // Cập nhật tên mới (nếu có tên tùy chỉnh)
                String defaultName = plugin.getConfig().getString("pets." + petId + ".name", "Pet");
                String customName = plugin.getConfigManager().getCustomName(p.getUniqueId(), petId);
                String name = (customName != null) ? customName : defaultName;
                String format = plugin.getLanguage().getMessage("pet.display_format");
                if (format == null) format = "&7Lv.%level% &e%name% &7(%player%)";

                String displayName = format.replace("%name%", name)
                        .replace("%level%", String.valueOf(currentLvl))
                        .replace("%player%", p.getName())
                        .replace("%owner%", p.getName());

                pet.setCustomName(ChatColor.translateAlternateColorCodes('&', displayName));
                pet.setCustomNameVisible(true);
            }
        }
        plugin.getConfigManager().getData().set(p.getUniqueId() + ".pets." + petId + ".exp", currentExp);
        plugin.getConfigManager().saveData();
    }
}