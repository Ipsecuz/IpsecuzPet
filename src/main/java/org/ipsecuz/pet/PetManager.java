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
    public final Set<UUID> dyingPets = ConcurrentHashMap.newKeySet();

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

        if (pet instanceof LivingEntity living) {
            living.setRemoveWhenFarAway(false);
            living.setCanPickupItems(false);
            living.setCollidable(false);

            // Scale kích cỡ pet thực tế (0.55 cho bé con, 1.0 cho trưởng thành)
            applyScale(living, isBaby ? 0.55 : 1.0);

            // Gắn Model thông qua ModelProviderManager với cơ chế Authoritative Resolver
            modelHandler.spawnModel(player, pet, petId, isBaby);

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
        PetRarity rarity = PetRarity.fromPetId(plugin, petId);
        try {
            pet.getWorld().spawnParticle(rarity.getRevealParticle(), pet.getLocation().add(0, 0.8, 0), 25, 0.35, 0.35, 0.35, 0.05);
        } catch (Exception ignored) {}

        String msg = plugin.getLanguage().getMessage("pet.spawn");
        if (msg != null) player.sendMessage(msg.replace("%pet_name%", displayName));
        player.playSound(player.getLocation(), rarity.getRevealSound(), 1f, 1.1f);
    }

    public boolean isDying(UUID ownerId) {
        return ownerId != null && dyingPets.contains(ownerId);
    }

    public void startDyingSequence(UUID ownerId, Entity pet, String petId, long durationTicks) {
        if (ownerId == null || pet == null) return;
        // Atomic guard: không cho phép lên lịch tử trận hai lần cùng lúc
        if (!dyingPets.add(ownerId)) {
            return;
        }

        if (modelHandler != null) {
            modelHandler.playTransientAnimation(pet, PetAnimationState.DEATH, durationTicks, PetAnimationState.DEATH);
        }

        // Thực thi dọn dẹp trên Pet Entity Region Scheduler (chuẩn Folia), TUYỆT ĐỐI không dùng GlobalRegionScheduler!
        SchedulerUtils.runEntityTaskLater(plugin, pet, () -> {
            try {
                if (modelHandler != null) {
                    modelHandler.removeModel(pet.getUniqueId());
                }
                if (pet.isValid()) {
                    pet.remove();
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("Lỗi dọn dẹp thực thể pet trong tiến trình tử trận: " + t.getMessage());
            } finally {
                dyingPets.remove(ownerId);
                activePets.remove(ownerId);
                activePetIds.remove(ownerId);
                if (plugin.getSkillManager() != null) {
                    plugin.getSkillManager().clearCooldown(ownerId);
                }
            }
        }, durationTicks);
    }

    public void removePet(UUID ownerId) {
        dyingPets.remove(ownerId);
        if (activePets.containsKey(ownerId)) {
            Entity e = activePets.remove(ownerId);
            activePetIds.remove(ownerId);
            if (plugin.getSkillManager() != null) {
                plugin.getSkillManager().clearCooldown(ownerId);
            }
            if (e != null) {
                // Item 25: Dọn dẹp model tracker vô điều kiện trước
                modelHandler.removeModel(e.getUniqueId());

                SchedulerUtils.runEntityTask(plugin, e, () -> {
                    // Item 24: Dọn dẹp yên cưỡi ArmorStand an toàn
                    for (Entity passenger : new ArrayList<>(e.getPassengers())) {
                        if (passenger != null) {
                            passenger.eject();
                            if (passenger.getPersistentDataContainer().has(new NamespacedKey(plugin, "pet_seat"), PersistentDataType.STRING)) {
                                passenger.remove();
                            }
                        }
                    }
                    if (e.isValid()) {
                        try {
                            e.getWorld().spawnParticle(Particle.CLOUD, e.getLocation().add(0, 0.5, 0), 15, 0.3, 0.3, 0.3, 0.05);
                            e.getWorld().playSound(e.getLocation(), Sound.ENTITY_CHICKEN_EGG, 1f, 1.4f);
                        } catch (Exception ignored) {}
                        e.remove();
                    }
                });
            }
        }
    }

    public void removeAllPets() {
        dyingPets.clear();
        for (UUID uuid : new ArrayList<>(activePets.keySet())) {
            removePet(uuid);
        }
        activePets.clear();
        activePetIds.clear();
        modelHandler.removeAll();
        if (plugin.getSkillManager() != null) {
            plugin.getSkillManager().clearAllCooldowns();
        }
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

        // Sử dụng PetStatEngine chuẩn xác duy nhất cho toàn plugin
        double maxHp = PetStatEngine.calculateEffectiveStat(plugin, ownerId, petId, level, "health");
        double damage = PetStatEngine.calculateEffectiveStat(plugin, ownerId, petId, level, "damage");
        double defense = PetStatEngine.calculateEffectiveStat(plugin, ownerId, petId, level, "defense");
        double speed = PetStatEngine.calculateEffectiveStat(plugin, ownerId, petId, level, "speed");

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

    public static class PetFollowSnapshot {
        private final UUID ownerId;
        private final boolean online;
        private final Location location;
        private final org.bukkit.World world;
        private final org.bukkit.util.Vector direction;

        public PetFollowSnapshot(UUID ownerId, boolean online, Location location, org.bukkit.World world, org.bukkit.util.Vector direction) {
            this.ownerId = ownerId;
            this.online = online;
            this.location = (location != null) ? location.clone() : null;
            this.world = world;
            this.direction = (direction != null) ? direction.clone() : new org.bukkit.util.Vector(0, 0, 1);
        }

        public UUID getOwnerId() { return ownerId; }
        public boolean isOnline() { return online; }
        public Location getLocation() { return location != null ? location.clone() : null; }
        public org.bukkit.World getWorld() { return world; }
        public org.bukkit.util.Vector getDirection() { return direction != null ? direction.clone() : new org.bukkit.util.Vector(0, 0, 1); }
    }

    private long tickCounter = 0;

    private void runPetLogic() {
        tickCounter++;
        final long currentTick = tickCounter;

        // BỐI CẢNH TOÀN CỤC (GLOBAL SCHEDULER CONTEXT):
        // Chỉ duyệt qua tập khóa UUID nhẹ, TUYỆT ĐỐI không gọi owner.isOnline() hay pet.isValid() từ luồng này!
        for (UUID ownerId : activePets.keySet()) {
            if (dyingPets.contains(ownerId)) {
                // Đang trong hoạt ảnh cái chết an toàn, bỏ qua không dọn dẹp
                continue;
            }

            Player owner = Bukkit.getPlayer(ownerId);
            if (owner == null) {
                Entity pet = activePets.get(ownerId);
                if (pet != null) {
                    SchedulerUtils.runEntityTask(plugin, pet, () -> removePet(ownerId));
                } else {
                    activePets.remove(ownerId);
                    activePetIds.remove(ownerId);
                }
                continue;
            }

            // BỐI CẢNH VÙNG NGƯỜI CHƠI (PLAYER REGION): Chụp snapshot trạng thái chủ nhân bất biến
            SchedulerUtils.runEntityTask(plugin, owner, () -> {
                if (!owner.isOnline()) {
                    Entity pEnt = activePets.get(ownerId);
                    if (pEnt != null) {
                        SchedulerUtils.runEntityTask(plugin, pEnt, () -> removePet(ownerId));
                    }
                    return;
                }

                PetFollowSnapshot snapshot = new PetFollowSnapshot(
                        ownerId,
                        true,
                        owner.getLocation(),
                        owner.getWorld(),
                        owner.getLocation().getDirection()
                );

                Entity pet = activePets.get(ownerId);
                if (pet == null) return;

                // BỐI CẢNH VÙNG PET (PET REGION): Chỉ dùng snapshot + trạng thái cục bộ của Pet
                SchedulerUtils.runEntityTask(plugin, pet, () -> {
                    if (!pet.isValid() || !snapshot.isOnline()) {
                        removePet(ownerId);
                        return;
                    }

                    // Khác thế giới: dịch chuyển tức thời bất đồng bộ sang vị trí chủ nhân
                    if (!pet.getWorld().equals(snapshot.getWorld())) {
                        SchedulerUtils.teleportAsync(pet, snapshot.getLocation());
                        return;
                    }

                    String petId = activePetIds.get(ownerId);
                    Location petLoc = pet.getLocation();
                    Location ownerLoc = snapshot.getLocation();
                    org.bukkit.util.Vector ownerDir = snapshot.getDirection();

                    boolean isFlying = isFlyingType(pet.getType());
                    double distSq = petLoc.distanceSquared(ownerLoc);

                    if (distSq > 400) {
                        SchedulerUtils.teleportAsync(pet, ownerLoc);
                    } else if (isFlying) {
                        double bob = Math.sin((System.currentTimeMillis() / 60.0) * 0.15) * 0.15;
                        Location targetHover = ownerLoc.clone().add(0, 1.3 + bob, 0);
                        org.bukkit.util.Vector dir = targetHover.toVector().subtract(petLoc.toVector());
                        double dist = dir.length();
                        if (dist > 1.8) {
                            dir.normalize().multiply(Math.min(0.40, dist * 0.1));
                            pet.setVelocity(dir);
                            modelHandler.playAnimation(pet, dist > 3.0 ? PetAnimationState.FLY : PetAnimationState.FLY_IDLE);
                        } else {
                            pet.setVelocity(new org.bukkit.util.Vector(0, Math.cos((System.currentTimeMillis() / 60.0) * 0.15) * 0.02, 0));
                            modelHandler.playAnimation(pet, PetAnimationState.FLY_IDLE);
                        }
                    } else {
                        // Đội hình flank formation cho Pet mặt đất
                        org.bukkit.util.Vector facing = ownerDir.clone().setY(0);
                        if (facing.lengthSquared() < 0.001) facing = new org.bukkit.util.Vector(0, 0, 1);
                        facing.normalize();
                        org.bukkit.util.Vector right = new org.bukkit.util.Vector(-facing.getZ(), 0, facing.getX());
                        Location flankTarget = ownerLoc.clone().subtract(facing.clone().multiply(1.5)).add(right.clone().multiply(1.2));

                        double flankDistSq = petLoc.distanceSquared(flankTarget);
                        if (flankDistSq > 3.0) {
                            if (pet instanceof Mob mob) {
                                mob.getPathfinder().moveTo(flankTarget, 1.25);
                            }
                        }

                        double speed = pet.getVelocity().setY(0).length();
                        PetAnimationState state;
                        if (speed > 0.35) {
                            state = PetAnimationState.RUN;
                        } else if (speed > 0.08 || flankDistSq > 4.0) {
                            state = PetAnimationState.WALK;
                        } else {
                            state = PetAnimationState.IDLE;
                        }
                        modelHandler.playAnimation(pet, state);
                    }

                    modelHandler.updatePosition(pet);

                    // Phân chia nhịp độ hiệu năng (Cadence Splitting):
                    // 1. Tầm nhìn hiển thị: mỗi 15 ticks (3 chu kỳ)
                    if (currentTick % 3 == 0) {
                        modelHandler.updateMultiplayerVisibility(pet);
                    }
                    // 2. Hạt hiệu ứng: mỗi 20 ticks (4 chu kỳ)
                    if (currentTick % 4 == 0) {
                        playParticles(pet, petId);
                    }

                    plugin.getSkillManager().handlePetTick(owner, pet, petId);

                    // 3. Hiệu ứng bùa lợi định kỳ: mỗi 40 ticks (8 chu kỳ)
                    if (currentTick % 8 == 0) {
                        SchedulerUtils.runEntityTask(plugin, owner, () -> applyBuffs(owner, petId, ownerId));
                    }
                });
            });
        }
    }

    private void applyBuffs(Player p, String petId, UUID uuid) {
        int level = plugin.getConfigManager().getData().getInt(uuid + ".pets." + petId + ".level", 1);
        double intel = PetStatEngine.calculateEffectiveStat(plugin, uuid, petId, level, "intelligence");
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
    public Map<UUID, Entity> getActivePets() { return Collections.unmodifiableMap(activePets); }
    public Map<UUID, String> getActivePetIds() { return Collections.unmodifiableMap(activePetIds); }

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

        double dmg = PetStatEngine.calculateEffectiveStat(plugin, player.getUniqueId(), petId, lvl, "damage");
        double hp = PetStatEngine.calculateEffectiveStat(plugin, player.getUniqueId(), petId, lvl, "health");
        double def = PetStatEngine.calculateEffectiveStat(plugin, player.getUniqueId(), petId, lvl, "defense");
        double spd = PetStatEngine.calculateEffectiveStat(plugin, player.getUniqueId(), petId, lvl, "speed");

        LanguageManager lang = plugin.getLanguage();
        player.sendMessage(lang.getMessage("pet.stats_header"));
        player.sendMessage(lang.getMessage("pet.stats_title", "%pet_name%", name));
        player.sendMessage("§7Độ hiếm: " + rarity.getLocalizedName(plugin) + " §7| Sao: " + starDisplay);
        player.sendMessage("§7Đặc chất (Trait): " + trait.getLocalizedName(plugin));
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

    // --- QUẢN LÝ VÒNG ĐỜI THÁCH ĐẤU (DUEL LIFECYCLE - ITEM 27) ---
    public static class DuelInvite {
        private final UUID sender;
        private final UUID target;
        private final long expireAt;

        public DuelInvite(UUID sender, UUID target, long timeoutMs) {
            this.sender = sender;
            this.target = target;
            this.expireAt = System.currentTimeMillis() + timeoutMs;
        }

        public UUID getSender() { return sender; }
        public UUID getTarget() { return target; }
        public boolean isExpired() { return System.currentTimeMillis() > expireAt; }
    }

    private final Map<UUID, DuelInvite> incomingDuelRequests = new ConcurrentHashMap<>();
    private final Map<UUID, Long> outgoingDuelTimestamps = new ConcurrentHashMap<>();

    public boolean sendDuelInvite(Player sender, Player target) {
        if (sender == null || target == null || sender.equals(target)) return false;
        if (activeDuels.containsKey(sender.getUniqueId()) || activeDuels.containsKey(target.getUniqueId())) {
            return false;
        }
        DuelInvite existing = incomingDuelRequests.get(target.getUniqueId());
        if (existing != null && !existing.isExpired() && existing.getSender().equals(sender.getUniqueId())) {
            return false;
        }
        incomingDuelRequests.put(target.getUniqueId(), new DuelInvite(sender.getUniqueId(), target.getUniqueId(), 60000L));
        outgoingDuelTimestamps.put(sender.getUniqueId(), System.currentTimeMillis());
        return true;
    }

    public UUID acceptDuelInvite(Player target) {
        if (target == null) return null;
        DuelInvite invite = incomingDuelRequests.remove(target.getUniqueId());
        if (invite == null || invite.isExpired()) {
            return null;
        }
        UUID senderId = invite.getSender();
        Player sender = Bukkit.getPlayer(senderId);
        if (sender == null || !sender.isOnline()) {
            return null;
        }
        activeDuels.put(senderId, target.getUniqueId());
        activeDuels.put(target.getUniqueId(), senderId);
        outgoingDuelTimestamps.remove(senderId);
        return senderId;
    }

    public UUID denyDuelInvite(Player target) {
        if (target == null) return null;
        DuelInvite invite = incomingDuelRequests.remove(target.getUniqueId());
        if (invite == null || invite.isExpired()) {
            return null;
        }
        outgoingDuelTimestamps.remove(invite.getSender());
        return invite.getSender();
    }

    public boolean cancelOutgoingDuel(Player sender) {
        if (sender == null) return false;
        boolean removed = false;
        for (Map.Entry<UUID, DuelInvite> entry : new ArrayList<>(incomingDuelRequests.entrySet())) {
            if (entry.getValue().getSender().equals(sender.getUniqueId())) {
                incomingDuelRequests.remove(entry.getKey());
                removed = true;
            }
        }
        outgoingDuelTimestamps.remove(sender.getUniqueId());
        return removed;
    }

    public void clearPlayerDuels(UUID uuid) {
        if (uuid == null) return;
        incomingDuelRequests.remove(uuid);
        outgoingDuelTimestamps.remove(uuid);
        for (Map.Entry<UUID, DuelInvite> entry : new ArrayList<>(incomingDuelRequests.entrySet())) {
            if (entry.getValue().getSender().equals(uuid)) {
                incomingDuelRequests.remove(entry.getKey());
            }
        }
        UUID opponent = activeDuels.remove(uuid);
        if (opponent != null) {
            activeDuels.remove(opponent);
        }
    }

    public void givePetExp(Player p, int amount) {
        if (!hasPet(p.getUniqueId()) || amount <= 0) return;
        String petId = activePetIds.get(p.getUniqueId());
        giveSpecificPetExp(p, petId, amount);
    }

    public void giveSpecificPetExp(Player p, String petId, int amount) {
        if (p == null || petId == null || amount <= 0) return;
        addPetExpAuthoritative(p.getUniqueId(), petId, amount, true);
    }

    public int addPetExpAuthoritative(UUID ownerId, String petId, int amount, boolean allowLevelUp) {
        if (ownerId == null || petId == null || amount <= 0) return 0;
        if (!plugin.getConfigManager().getData().contains(ownerId + ".pets." + petId)) return 0;

        int currentExp = plugin.getConfigManager().getData().getInt(ownerId + ".pets." + petId + ".exp", 0);
        int currentLvl = plugin.getConfigManager().getData().getInt(ownerId + ".pets." + petId + ".level", 1);
        int maxLvl = plugin.getConfig().getInt("rpg_system.max_level", 100);

        if (currentLvl >= maxLvl) {
            return currentLvl;
        }

        Player onlinePlayer = Bukkit.getPlayer(ownerId);
        double multiplier = 1.0;

        if (onlinePlayer != null && onlinePlayer.isOnline()) {
            // 1. Quét quyền hạn của người chơi trực tiếp (ví dụ: ipsecuzpet.multiplier.1.5)
            try {
                for (org.bukkit.permissions.PermissionAttachmentInfo info : onlinePlayer.getEffectivePermissions()) {
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
                        if (onlinePlayer.hasPermission(key) || onlinePlayer.hasPermission("ipsecuzpet.multiplier." + key)) {
                            if (permMultiplier > multiplier) {
                                multiplier = permMultiplier;
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        // 3. Thưởng / Phạt theo độ vui vẻ (Happiness)
        if (plugin.getFeedingManager() != null) {
            int happy = plugin.getFeedingManager().getHappiness(ownerId, petId);
            if (plugin.getHappinessModifierEngine() != null) {
                multiplier *= plugin.getHappinessModifierEngine().getExpMultiplier(happy);
            } else {
                multiplier *= plugin.getFeedingManager().getExpMultiplier(happy);
            }
        }

        // 4. Thưởng EXP theo Đặc chất (Trait)
        String traitName = plugin.getConfigManager().getData().getString(ownerId + ".pets." + petId + ".trait", "NONE");
        PetTrait trait = PetTrait.fromString(traitName);
        if (trait != null) {
            multiplier *= trait.getExpMultiplier();
        }

        int finalAmount = Math.max(1, (int) (amount * multiplier));

        // --- VÒNG LẶP NÂNG CẤP ĐA TẦNG (MULTI-LEVEL PROGRESSION LOOP) ---
        long accumulatedExp = (long) currentExp + finalAmount;
        int baseExpRequirement = plugin.getConfig().getInt("rpg_system.base_exp_requirement", 50);
        int levelsGained = 0;

        if (allowLevelUp) {
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
        }

        int finalExp = (currentLvl >= maxLvl) ? 0 : (int) Math.min(Integer.MAX_VALUE, accumulatedExp);

        plugin.getConfigManager().getData().set(ownerId + ".pets." + petId + ".level", currentLvl);
        plugin.getConfigManager().getData().set(ownerId + ".pets." + petId + ".exp", finalExp);
        plugin.getConfigManager().saveData();

        if (onlinePlayer != null && onlinePlayer.isOnline() && levelsGained > 0) {
            // Âm thanh vinh quang
            onlinePlayer.playSound(onlinePlayer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            onlinePlayer.playSound(onlinePlayer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);

            // Action Bar phản hồi tức thì
            LanguageManager lang = plugin.getLanguage();
            if (levelsGained == 1) {
                String ab = (lang != null) ? lang.getMessage("pet.actionbar_level_up", "%level%", String.valueOf(currentLvl)) : "&a&l★ LÊN CẤP! &eĐạt Lv." + currentLvl;
                onlinePlayer.sendActionBar(GuiText.component(ab));
                String msg = (lang != null) ? lang.getMessage("pet.level_up") : null;
                if (msg != null) onlinePlayer.sendMessage(GuiText.component(msg.replace("%level%", String.valueOf(currentLvl))));
            } else {
                String ab = (lang != null) ? lang.getMessage("pet.actionbar_multi_level_up", "%levels%", String.valueOf(levelsGained), "%current%", String.valueOf(currentLvl)) : "&6&l★ +" + levelsGained + " CẤP ĐỘ! &eLv." + (currentLvl - levelsGained) + " ➔ Lv." + currentLvl;
                onlinePlayer.sendActionBar(GuiText.component(ab));
                String msg = (lang != null) ? lang.getMessage("pet.multi_level_up", "%levels%", String.valueOf(levelsGained), "%current%", String.valueOf(currentLvl)) : "&a&l★ TIẾN HÓA CẤP ĐỘ! &fThú cưng đã tăng vọt &e+" + levelsGained + " Cấp &f(Đạt cấp: &6Lv." + currentLvl + "&f)!";
                onlinePlayer.sendMessage(GuiText.component(msg));
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
                        onlinePlayer.addPotionEffect(new PotionEffect(pet, ticks, amp));
                    }
                } catch (Exception ignored) {}
            }

            // Cập nhật thực thể đang triệu hồi nếu là pet hiện tại
            boolean isActive = petId.equals(activePetIds.get(ownerId));
            Entity petEntity = isActive ? activePets.get(ownerId) : null;
            if (petEntity != null && petEntity.isValid()) {
                updatePetStats(petEntity, petId, currentLvl, ownerId);

                String defaultName = plugin.getConfig().getString("pets." + petId + ".name", "Pet");
                String customName = plugin.getConfigManager().getCustomName(ownerId, petId);
                String name = (customName != null) ? customName : defaultName;
                String format = plugin.getLanguage().getMessage("pet.display_format");
                if (format == null) format = "&7Lv.%level% &e%name% &7(%player%)";

                String displayName = format.replace("%name%", name)
                        .replace("%level%", String.valueOf(currentLvl))
                        .replace("%player%", onlinePlayer.getName())
                        .replace("%owner%", onlinePlayer.getName());

                petEntity.setCustomName(ChatColor.translateAlternateColorCodes('&', displayName));
                petEntity.setCustomNameVisible(true);

                boolean isFlying = isFlyingType(petEntity.getType());
                modelHandler.playTransientAnimation(petEntity, PetAnimationState.CELEBRATE, 45L, isFlying ? PetAnimationState.FLY_IDLE : PetAnimationState.IDLE);
                try {
                    petEntity.getWorld().spawnParticle(Particle.TOTEM, petEntity.getLocation().add(0, 1.0, 0), 25, 0.4, 0.4, 0.4, 0.08);
                    petEntity.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, petEntity.getLocation().add(0, 0.8, 0), 12, 0.3, 0.3, 0.3, 0.05);
                } catch (Exception ignored) {}
            }
        }
        return currentLvl;
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