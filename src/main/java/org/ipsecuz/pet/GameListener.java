package org.ipsecuz.pet;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class GameListener implements Listener {
    private final IpsecuzPet plugin;

    public GameListener(IpsecuzPet plugin) {
        this.plugin = plugin;
    }

    private boolean isPet(Entity e) {
        return e.getPersistentDataContainer().has(plugin.getPetManager().petKey, PersistentDataType.STRING);
    }

    private UUID getOwnerId(Entity e) {
        String s = e.getPersistentDataContainer().get(plugin.getPetManager().petKey, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @EventHandler
    public void onRedeemPet(PlayerInteractEvent e) {
        if (!e.hasItem()) return;
        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta()) return;

        // 1. Kiểm tra nếu là Trứng Pet (Pet Egg Hatching)
        if (plugin.getHatchingManager() != null) {
            String eggId = plugin.getHatchingManager().getEggId(item);
            if (eggId != null) {
                e.setCancelled(true);
                if (e.getAction().toString().contains("RIGHT")) {
                    plugin.getHatchingManager().processHatch(e.getPlayer(), eggId, item);
                }
                return;
            }
        }

        // 2. Kiểm tra nếu là Thẻ Pet rút từ /pet withdraw
        if (PetCardSecurity.isPetCard(item)) {
            e.setCancelled(true);
            if (e.getAction().toString().contains("RIGHT")) {
                Player p = e.getPlayer();
                LanguageManager lang = plugin.getLanguage();

                PetCardSecurity.CardValidationResult result = PetCardSecurity.validateAndExtractCard(plugin, item);
                if (!result.isValid()) {
                    p.sendMessage("§c§l[LỖI THẺ PET] §c" + result.getErrorMessage());
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }

                PetCardSecurity.PetCardData cardData = result.getCardData();
                String petId = cardData.getPetId();

                if (plugin.getConfigManager().getData().contains(p.getUniqueId() + ".pets." + petId)) {
                    p.sendMessage(lang.getMessage("pet.already_owned"));
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }

                if (!plugin.getOwnershipManager().canAcquirePet(p)) {
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }

                String cardUuid = cardData.getCardUniqueId();
                if (cardUuid != null && PetCardSecurity.isCardConsumed(plugin, cardUuid)) {
                    p.sendMessage("§c§l[CẢNH BÁO] §cThẻ Thú Cưng này đã được kích hoạt trước đó (Chống nhân bản)!");
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }

                // Giao dịch kích hoạt thẻ an toàn (Transactional Redeem):
                // Chỉ trừ thẻ khi chắc chắn lưu trữ hồ sơ Pet thành công; nếu lỗi sẽ rollback và bảo toàn thẻ
                try {
                    plugin.getConfigManager().savePetProfile(
                            p.getUniqueId(), petId,
                            cardData.getLevel(), cardData.getExp(), cardData.getStars(),
                            cardData.getTrait(), cardData.getCustomName(), cardData.getUnlockedSkills(),
                            true // Ghi đĩa bền vững ngay lập tức (Durable write)
                    );

                    if (cardUuid != null) {
                        PetCardSecurity.markCardConsumed(plugin, cardUuid); // Ghi nhận thẻ đã dùng bền vững
                    }

                    // Khấu trừ vật phẩm thẻ từ tay người chơi sau khi dữ liệu đã được xác nhận an toàn
                    item.setAmount(item.getAmount() - 1);

                    plugin.getCodexManager().discover(p.getUniqueId(), petId);
                    p.sendMessage(lang.getMessage("pet.redeem_success", "%pet_id%", petId));
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                } catch (Exception ex) {
                    // Rollback hồ sơ Pet nếu có lỗi xảy ra
                    plugin.getConfigManager().getData().set(p.getUniqueId() + ".pets." + petId, null);
                    plugin.getConfigManager().saveData();
                    plugin.getLogger().log(Level.SEVERE, "Lỗi xảy ra trong quá trình kích hoạt thẻ Pet của " + p.getName(), ex);
                    p.sendMessage("§c§l[LỖI GIAO DỊCH] §cKhông thể kích hoạt thẻ Pet do lỗi lưu trữ! Thẻ vẫn được giữ nguyên trong túi.");
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                }
            }
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        LanguageManager lang = plugin.getLanguage();

        // Sneak + Chuột phải vào Pet của mình: Mở bảng quản trị chi tiết
        if (e.getRightClicked() instanceof Entity pet && p.isSneaking() && isPet(pet) && getOwnerId(pet).equals(p.getUniqueId())) {
            e.setCancelled(true);
            String petId = plugin.getPetManager().getActivePetId(p.getUniqueId());
            if (petId != null) {
                GuiListener.openPetDetailMenu(p, petId);
            } else {
                plugin.getPetManager().showPetStats(p, pet);
            }
            return;
        }

        // Bắt thú hoang bằng Bóng Bắt Thú (Capture System)
        if (!isPet(e.getRightClicked()) && e.getRightClicked() instanceof LivingEntity target) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            String ballId = plugin.getCaptureManager().getBallIdFromItem(hand);
            if (ballId != null) {
                e.setCancelled(true);
                EntityType type = target.getType();
                String foundPetId = null;

                for (String key : plugin.getConfig().getConfigurationSection("pets").getKeys(false)) {
                    if (plugin.getConfig().getString("pets." + key + ".type").equals(type.toString())) {
                        if (plugin.getConfig().getBoolean("pets." + key + ".catchable", false)) {
                            foundPetId = key;
                            break;
                        }
                    }
                }

                if (foundPetId == null) {
                    p.sendMessage(lang.getMessage("pet.catch_impossible"));
                    return;
                }

                if (plugin.getConfigManager().getData().contains(p.getUniqueId() + ".pets." + foundPetId)) {
                    p.sendMessage(lang.getMessage("pet.already_owned"));
                    p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 1f, 1f);
                    return;
                }

                if (!plugin.getOwnershipManager().canAcquirePet(p)) {
                    return;
                }

                hand.setAmount(hand.getAmount() - 1);
                p.sendMessage(lang.getMessage("pet.catch_start"));
                p.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, 1f, 1f);

                String finalPetId = foundPetId;
                SchedulerUtils.runEntityTaskLater(plugin, p, () -> {
                    // HOÀN TRẢ BÓNG NẾU MỤC TIÊU ĐÃ CHẾT HOẶC BIẾN MẤT TRONG KHI BẮT
                    if (!target.isValid() || target.isDead()) {
                        ItemStack refundBall = plugin.getCaptureManager().createBallItem(ballId, 1);
                        if (refundBall != null) {
                            HashMap<Integer, ItemStack> overflow = p.getInventory().addItem(refundBall);
                            for (ItemStack leftover : overflow.values()) {
                                p.getWorld().dropItemNaturally(p.getLocation(), leftover);
                            }
                            p.sendMessage("§eMục tiêu đã biến mất hoặc bị hạ gục! Bóng bắt thú đã được hoàn trả.");
                        }
                        return;
                    }

                    CaptureManager.CaptureResult result = plugin.getCaptureManager().calculateCapture(p, type, ballId);
                    if (result == CaptureManager.CaptureResult.TYPE_NOT_ALLOWED) {
                        p.sendMessage(lang.getMessage("pet.catch_fail_type"));
                        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 1f, 1f);
                    } else if (result == CaptureManager.CaptureResult.SUCCESS) {
                        p.sendMessage(lang.getMessage("pet.caught", "%pet_type%", plugin.getConfig().getString("pets." + finalPetId + ".name")));
                        target.remove();
                        plugin.getConfigManager().createPetDataIfMissing(p.getUniqueId(), finalPetId);
                        PetTrait trait = PetTrait.rollRandomTrait();
                        plugin.getConfigManager().getData().set(p.getUniqueId() + ".pets." + finalPetId + ".trait", trait.name());
                        plugin.getConfigManager().saveData();
                        plugin.getCodexManager().discover(p.getUniqueId(), finalPetId);

                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                        p.spawnParticle(Particle.VILLAGER_HAPPY, target.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5);
                    } else {
                        p.sendMessage(lang.getMessage("pet.catch_fail"));
                        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    }
                }, 40L);
                return;
            }
        }

        // Cưỡi Pet (Mount)
        if (isPet(e.getRightClicked()) && !p.isSneaking()) {
            UUID ownerId = getOwnerId(e.getRightClicked());
            if (ownerId != null && ownerId.equals(p.getUniqueId())) {
                e.setCancelled(true);
                if (!p.hasPermission("ipsecuzpet.ride")) {
                    p.sendMessage(lang.getMessage("general.no_permission"));
                    return;
                }
                Entity pet = e.getRightClicked();
                if (pet.getPassengers().contains(p)) return;
                ArmorStand seat = (ArmorStand) pet.getWorld().spawnEntity(pet.getLocation(), EntityType.ARMOR_STAND);
                seat.setVisible(false);
                seat.setSmall(true);
                seat.setGravity(false);
                seat.setMarker(true);
                seat.setBasePlate(false);
                seat.getPersistentDataContainer().set(new NamespacedKey(plugin, "pet_seat"), PersistentDataType.STRING, "true");
                pet.addPassenger(seat);
                seat.addPassenger(p);
                p.sendMessage(lang.getMessage("pet.mount"));
            }
        }
    }

    @EventHandler
    public void onDismount(org.bukkit.event.entity.EntityDismountEvent e) {
        if (e.getDismounted().getPersistentDataContainer().has(new NamespacedKey(plugin, "pet_seat"), PersistentDataType.STRING)) {
            e.getDismounted().remove();
        }
    }

    @EventHandler
    public void onEntityTarget(EntityTargetEvent e) {
        if (!isPet(e.getEntity())) return;
        UUID ownerId = getOwnerId(e.getEntity());
        Entity target = e.getTarget();

        // 1. Chặn nhắm vào chủ nhân & dọn dẹp Warden anger đúng cách
        if (target != null && target.getUniqueId().equals(ownerId)) {
            e.setCancelled(true);
            e.setTarget(null);
            if (e.getEntity() instanceof Warden warden && target instanceof LivingEntity livingTarget) {
                warden.clearAnger(livingTarget);
            }
            return;
        }

        // 2. Chặn tự động tấn công người chơi khác trừ khi trong quyết đấu hoặc bật cấu hình target_players
        if (target instanceof Player playerTarget && ownerId != null) {
            boolean allowPlayerTarget = plugin.getConfig().getBoolean("combat.target_players", false);
            boolean isDuel = plugin.getPetManager().activeDuels.containsKey(ownerId)
                    && playerTarget.getUniqueId().equals(plugin.getPetManager().activeDuels.get(ownerId));
            if (!allowPlayerTarget && !isDuel) {
                e.setCancelled(true);
                e.setTarget(null);
                if (e.getEntity() instanceof Warden warden) {
                    warden.clearAnger(playerTarget);
                }
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        // Chặn sát thương giữa chủ và pet
        if (isPet(e.getDamager()) && e.getEntity() instanceof Player victim) {
            UUID petOwner = getOwnerId(e.getDamager());
            if (petOwner != null && petOwner.equals(victim.getUniqueId())) {
                e.setCancelled(true);
                if (e.getDamager() instanceof Warden warden) warden.clearAnger(victim);
                return;
            }
        }
        if (e.getDamager() instanceof Player owner && isPet(e.getEntity())) {
            UUID petOwner = getOwnerId(e.getEntity());
            if (petOwner != null && petOwner.equals(owner.getUniqueId())) {
                e.setCancelled(true);
                if (e.getEntity() instanceof Warden warden) warden.clearAnger(owner);
                return;
            }
        }

        // Pet hỗ trợ tấn công mục tiêu của chủ
        if (e.getDamager() instanceof Player p && plugin.getPetManager().hasPet(p.getUniqueId())) {
            Entity pet = plugin.getPetManager().getPet(p.getUniqueId());
            if (pet instanceof Mob mob && !e.getEntity().equals(pet) && e.getEntity() instanceof LivingEntity target) {
                if (target instanceof Player victimPlayer) {
                    boolean allowPlayerTarget = plugin.getConfig().getBoolean("combat.target_players", false);
                    boolean isDuel = plugin.getPetManager().activeDuels.containsKey(p.getUniqueId())
                            && victimPlayer.getUniqueId().equals(plugin.getPetManager().activeDuels.get(p.getUniqueId()));
                    if (allowPlayerTarget || isDuel) {
                        mob.setTarget(target);
                    }
                } else {
                    mob.setTarget(target);
                }
            }

            // Kỹ năng nội tại: Sát thương cộng thêm (Warden) & Chí mạng (Wolf)
            double bonusPercent = plugin.getSkillManager().getBonusDamagePercent(p);
            if (bonusPercent > 0) {
                e.setDamage(e.getDamage() * (1.0 + (bonusPercent / 100.0)));
            }

            double critChance = plugin.getSkillManager().getCritChancePercent(p);
            if (critChance > 0 && ThreadLocalRandom.current().nextDouble(100.0) < critChance) {
                e.setDamage(e.getDamage() * 1.5);
                p.getWorld().spawnParticle(Particle.CRIT, e.getEntity().getLocation().add(0, 1, 0), 20, 0.4, 0.4, 0.4, 0.1);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1f);
            }
        }

        // Kỹ năng nội tại: Giảm sát thương nhận vào từ quái vật (Ender Dragon & Iron Golem)
        if (e.getEntity() instanceof Player owner && plugin.getPetManager().hasPet(owner.getUniqueId())) {
            Entity pet = plugin.getPetManager().getPet(owner.getUniqueId());
            if (!e.getDamager().equals(pet)) {
                if (pet instanceof Mob mob && e.getDamager() instanceof LivingEntity attacker) {
                    mob.setTarget(attacker);
                }
            }

            boolean isMonsterAttack = e.getDamager() instanceof Monster
                    || (e.getDamager() instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Monster);

            if (isMonsterAttack) {
                double reduction = plugin.getSkillManager().getDamageReductionPercent(owner);
                if (reduction > 0) {
                    e.setDamage(e.getDamage() * (1.0 - (reduction / 100.0)));
                }
            }
        }

        // Quyết Đấu Pet (Pet Duel) với chỉ số thực tế (Effective Stats)
        if (isPet(e.getDamager()) && isPet(e.getEntity())) {
            UUID p1 = getOwnerId(e.getDamager());
            UUID p2 = getOwnerId(e.getEntity());
            if (p1 != null && p2 != null && plugin.getPetManager().activeDuels.containsKey(p1) && p2.equals(plugin.getPetManager().activeDuels.get(p1))) {
                e.setCancelled(false);
                ConfigManager cm = plugin.getConfigManager();
                String petId1 = plugin.getPetManager().getActivePetId(p1);
                int lvl1 = cm.getData().getInt(p1 + ".pets." + petId1 + ".level", 1);
                double damage = PetStatEngine.calculateEffectiveStat(plugin, p1, petId1, lvl1, "damage");

                String petId2 = plugin.getPetManager().getActivePetId(p2);
                int lvl2 = cm.getData().getInt(p2 + ".pets." + petId2 + ".level", 1);
                double defense = PetStatEngine.calculateEffectiveStat(plugin, p2, petId2, lvl2, "defense");

                double finalDamage = damage - (defense * 0.5);
                if (finalDamage < 1.0) finalDamage = 1.0;
                e.setDamage(finalDamage);
                return;
            }
        }

        if (isPet(e.getEntity()) && e.getEntity().isInvulnerable()) e.setCancelled(true);
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent e) {
        // Nội tại Rồng Ender: Kháng lửa hoàn toàn
        if (e.getEntity() instanceof Player p && plugin.getPetManager().hasPet(p.getUniqueId())) {
            EntityDamageEvent.DamageCause cause = e.getCause();
            if (cause == EntityDamageEvent.DamageCause.FIRE ||
                cause == EntityDamageEvent.DamageCause.FIRE_TICK ||
                cause == EntityDamageEvent.DamageCause.LAVA ||
                cause == EntityDamageEvent.DamageCause.HOT_FLOOR) {
                if (plugin.getSkillManager().hasFireImmunity(p)) {
                    e.setCancelled(true);
                    return;
                }
            }

            // Phản ứng lo lắng/đau đớn của Pet khi chủ nhân bị trọng thương (< 30% HP)
            if (!e.isCancelled() && (p.getHealth() - e.getFinalDamage() <= 6.0)) {
                Entity pet = plugin.getPetManager().getPet(p.getUniqueId());
                if (pet != null && pet.isValid()) {
                    plugin.getModelHandler().playTransientAnimation(pet, PetAnimationState.HURT, 25L, PetAnimationState.IDLE);
                    try {
                        pet.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, pet.getLocation().add(0, 0.8, 0), 4, 0.2, 0.2, 0.2, 0.05);
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    @EventHandler
    public void onPetDeath(EntityDeathEvent e) {
        if (!isPet(e.getEntity())) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        Entity pet = e.getEntity();
        UUID ownerId = getOwnerId(pet);
        if (ownerId == null) return;
        String petId = plugin.getPetManager().getActivePetId(ownerId);

        // Phát hoạt ảnh và hiệu ứng DEATH cinematic
        try {
            pet.getWorld().spawnParticle(Particle.SMOKE_LARGE, pet.getLocation().add(0, 0.6, 0), 20, 0.4, 0.4, 0.4, 0.05);
            pet.getWorld().playSound(pet.getLocation(), Sound.ENTITY_ALLAY_DEATH, 1.2f, 0.8f);
        } catch (Exception ignored) {}

        if (plugin.getPetManager().activeDuels.containsKey(ownerId)) {
            UUID winnerId = plugin.getPetManager().activeDuels.get(ownerId);
            plugin.getPetManager().activeDuels.remove(ownerId);
            plugin.getPetManager().activeDuels.remove(winnerId);
            Player winner = Bukkit.getPlayer(winnerId);
            if (winner != null) {
                winner.sendMessage(plugin.getLanguage().getMessage("duel.win"));
            }
            Player loser = Bukkit.getPlayer(ownerId);
            if (loser != null) {
                loser.sendMessage(plugin.getLanguage().getMessage("duel.lose"));
            }
        } else {
            plugin.getConfigManager().setPetStatus(ownerId, petId, "DEAD");
            if (Bukkit.getPlayer(ownerId) != null)
                Bukkit.getPlayer(ownerId).sendMessage(plugin.getLanguage().getMessage("pet.death"));
        }

        // Bắt đầu chuỗi hấp hối (Dying Sequence - 30 ticks) an toàn, chống bị dọn dẹp sớm
        plugin.getPetManager().startDyingSequence(ownerId, pet, petId, 30L);
    }

    @EventHandler
    public void onMobKill(EntityDeathEvent e) {
        if (e.getEntity().getKiller() != null && e.getEntity() instanceof Monster) {
            Player p = e.getEntity().getKiller();
            if (plugin.getPetManager().hasPet(p.getUniqueId())) {
                int exp = plugin.getConfig().getInt("rpg_system.exp_per_kill", 10);
                plugin.getPetManager().givePetExp(p, exp);

                // Phản ứng chúc mừng của Pet khi chủ tiêu diệt quái vật
                Entity pet = plugin.getPetManager().getPet(p.getUniqueId());
                if (pet != null && pet.isValid()) {
                    plugin.getModelHandler().playTransientAnimation(pet, PetAnimationState.CELEBRATE, 35L, PetAnimationState.IDLE);
                    try {
                        pet.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, pet.getLocation().add(0, 0.8, 0), 8, 0.3, 0.3, 0.3, 0.05);
                    } catch (Exception ignored) {}
                }
            }

            // TỈ LỆ RƠI TRỨNG TỪ QUÁI VẬT & BOSS (HATCHING DROPS)
            if (plugin.getModuleManager().isHatchingEnabled()) {
                FileConfiguration cfg = plugin.getModuleManager().getHatchingConfig();
                if (cfg.getBoolean("drops.mobs.enabled", true)) {
                    boolean isBoss = e.getEntity() instanceof Boss || e.getEntity() instanceof Warden || e.getEntity() instanceof ElderGuardian;
                    if (isBoss) {
                        double bossChance = cfg.getDouble("drops.mobs.boss_chance_percent", 25.0);
                        if (ThreadLocalRandom.current().nextDouble(100.0) < bossChance) {
                            String eggId = cfg.getString("drops.mobs.boss_egg", "legendary_egg");
                            ItemStack eggItem = plugin.getHatchingManager().createEggItem(eggId, 1);
                            if (eggItem != null) {
                                e.getEntity().getWorld().dropItemNaturally(e.getEntity().getLocation(), eggItem);
                                p.sendMessage("§6§l✦ RƠI TRỨNG BOSS! §eBạn vừa nhận được §f" + eggItem.getItemMeta().getDisplayName() + "§e!");
                                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                            }
                        }
                    } else {
                        double mobChance = cfg.getDouble("drops.mobs.monster_chance_percent", 0.5);
                        if (ThreadLocalRandom.current().nextDouble(100.0) < mobChance) {
                            String eggId = cfg.getString("drops.mobs.monster_egg", "common_egg");
                            ItemStack eggItem = plugin.getHatchingManager().createEggItem(eggId, 1);
                            if (eggItem != null) {
                                e.getEntity().getWorld().dropItemNaturally(e.getEntity().getLocation(), eggItem);
                                p.sendMessage("§a§l✦ RƠI TRỨNG! §eQuái vật đã làm rơi §f" + eggItem.getItemMeta().getDisplayName() + "§e!");
                                p.playSound(p.getLocation(), Sound.ENTITY_CHICKEN_EGG, 1f, 1.2f);
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onMineDiamond(BlockBreakEvent e) {
        Material mat = e.getBlock().getType();

        // 1. TỈ LỆ RƠI TRỨNG KHI ĐÀO KHOÁNG (HATCHING MINING DROPS)
        if (plugin.getModuleManager().isHatchingEnabled()) {
            FileConfiguration cfg = plugin.getModuleManager().getHatchingConfig();
            if (cfg.getBoolean("drops.mining.enabled", true)) {
                double chance = cfg.getDouble("drops.mining.chance_percent", 1.5);
                if (ThreadLocalRandom.current().nextDouble(100.0) < chance) {
                    String eggId = null;
                    if (mat == Material.DIAMOND_ORE || mat == Material.DEEPSLATE_DIAMOND_ORE) {
                        eggId = cfg.getString("drops.mining.diamond_ore_egg", "rare_egg");
                    } else if (mat == Material.ANCIENT_DEBRIS) {
                        eggId = cfg.getString("drops.mining.ancient_debris_egg", "legendary_egg");
                    }

                    if (eggId != null) {
                        ItemStack eggItem = plugin.getHatchingManager().createEggItem(eggId, 1);
                        if (eggItem != null) {
                            e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), eggItem);
                            e.getPlayer().sendMessage("§6§l✦ CƠ DUYÊN ĐÀO KHOÁNG! §eBạn vừa đào được một quả " + eggItem.getItemMeta().getDisplayName() + "§e!");
                            e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
                        }
                    }
                }
            }
        }

        // 2. HỆ THỐNG HỒI SINH PET BẰNG QUẶNG KIM CƯƠNG
        if (mat == Material.DIAMOND_ORE || mat == Material.DEEPSLATE_DIAMOND_ORE) {
            Player p = e.getPlayer();
            ConfigManager cm = plugin.getConfigManager();
            List<String> pets = new ArrayList<>();
            if (cm.getData().getConfigurationSection(p.getUniqueId() + ".pets") != null) {
                pets.addAll(cm.getData().getConfigurationSection(p.getUniqueId() + ".pets").getKeys(false));
            }
            boolean hasDead = false;
            for (String pid : pets) if (cm.isPetDead(p.getUniqueId(), pid)) hasDead = true;
            if (hasDead) {
                cm.addReviveProgress(p.getUniqueId(), 1);
                int cur = cm.getReviveProgress(p.getUniqueId());
                int max = plugin.getConfig().getInt("rpg_system.revive_cost_diamonds", 32);
                p.sendActionBar(net.kyori.adventure.text.Component.text(
                        plugin.getLanguage().getMessage("pet.revive_progress", "%current%", String.valueOf(cur), "%max%", String.valueOf(max))
                ));
                if (cur >= max) {
                    String reviveMode = plugin.getConfig().getString("rpg_system.revive_mode", "SINGLE");
                    if ("SINGLE".equalsIgnoreCase(reviveMode)) {
                        for (String pid : pets) {
                            if (cm.isPetDead(p.getUniqueId(), pid)) {
                                cm.setPetStatus(p.getUniqueId(), pid, "ALIVE");
                                break;
                            }
                        }
                    } else {
                        for (String pid : pets) {
                            if (cm.isPetDead(p.getUniqueId(), pid)) {
                                cm.setPetStatus(p.getUniqueId(), pid, "ALIVE");
                            }
                        }
                    }
                    cm.resetReviveProgress(p.getUniqueId());
                    p.sendMessage(plugin.getLanguage().getMessage("pet.revive_complete"));
                    p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (plugin.getHatchingManager() != null) {
            plugin.getHatchingManager().resolvePendingHatchOnJoin(p);
        }
        if (plugin.getTradeManager() != null) {
            plugin.getTradeManager().deliverOfflineRefundOnJoin(p);
        }
        if (p.hasPermission("ipsecuzpet.admin")) {
            new UpdateChecker(plugin, IpsecuzPet.RESOURCE_ID).getVersion(version -> {
                if (UpdateChecker.isNewerVersion(version, plugin.getDescription().getVersion())) {
                    SchedulerUtils.runEntityTask(plugin, p, () -> {
                        if (p.isOnline()) {
                            p.sendMessage("§8[§6IpsecuzPet§8] §aPhát hiện phiên bản mới: §e" + version);
                            p.sendMessage("§8[§6IpsecuzPet§8] §7Tải ngay tại: §fhttps://www.spigotmc.org/resources/" + IpsecuzPet.RESOURCE_ID);
                        }
                    });
                }
            });
        }
    }

    @EventHandler
    public void onPetExplosion(org.bukkit.event.entity.EntityExplodeEvent e) {
        Entity entity = e.getEntity();
        if (entity != null) {
            if (isPet(entity)) {
                e.blockList().clear();
                e.setYield(0f);
                e.setCancelled(true);
                return;
            }
            if (entity instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Entity shooter && isPet(shooter)) {
                e.blockList().clear();
                e.setYield(0f);
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPetChangeBlock(org.bukkit.event.entity.EntityChangeBlockEvent e) {
        if (e.getEntity() != null && isPet(e.getEntity())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerKick(org.bukkit.event.player.PlayerKickEvent e) {
        handleDisconnect(e.getPlayer());
    }

    @EventHandler
    public void onPetExplosionPrime(org.bukkit.event.entity.ExplosionPrimeEvent e) {
        if (isPet(e.getEntity())) {
            e.setCancelled(true);
            e.setRadius(0f);
        }
    }

    @EventHandler
    public void onPetExplode(org.bukkit.event.entity.EntityExplodeEvent e) {
        if (isPet(e.getEntity())) {
            e.setCancelled(true);
            e.blockList().clear();
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        handleDisconnect(e.getPlayer());
    }

    private void handleDisconnect(Player p) {
        if (p == null) return;
        java.util.UUID uuid = p.getUniqueId();

        if (plugin.getTradeManager() != null) {
            plugin.getTradeManager().handlePlayerQuit(p);
        }
        if (plugin.getHatchingManager() != null) {
            plugin.getHatchingManager().handlePlayerQuit(p);
        }
        if (plugin.getPetManager() != null) {
            plugin.getPetManager().removePet(uuid);
            plugin.getPetManager().clearPlayerDuels(uuid);
        }
        if (plugin.getModelHandler() != null) {
            plugin.getModelHandler().handlePlayerQuit(p);
        }
        if (plugin.getCaptureManager() != null) {
            plugin.getCaptureManager().handlePlayerQuit(uuid);
        }
        if (plugin.getSkillManager() != null) {
            plugin.getSkillManager().clearCooldown(uuid);
        }
    }
}