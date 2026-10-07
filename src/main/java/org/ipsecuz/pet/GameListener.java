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
        return (s == null) ? null : UUID.fromString(s);
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
        NamespacedKey keyId = new NamespacedKey(plugin, "pet_item_id");
        if (item.getItemMeta().getPersistentDataContainer().has(keyId, PersistentDataType.STRING)) {
            e.setCancelled(true);
            if (e.getAction().toString().contains("RIGHT")) {
                Player p = e.getPlayer();
                LanguageManager lang = plugin.getLanguage();
                String petId = item.getItemMeta().getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
                int lvl = item.getItemMeta().getPersistentDataContainer().getOrDefault(new NamespacedKey(plugin, "pet_item_lvl"), PersistentDataType.INTEGER, 1);
                int exp = item.getItemMeta().getPersistentDataContainer().getOrDefault(new NamespacedKey(plugin, "pet_item_exp"), PersistentDataType.INTEGER, 0);
                int stars = item.getItemMeta().getPersistentDataContainer().getOrDefault(new NamespacedKey(plugin, "pet_item_stars"), PersistentDataType.INTEGER, 1);
                String trait = item.getItemMeta().getPersistentDataContainer().getOrDefault(new NamespacedKey(plugin, "pet_item_trait"), PersistentDataType.STRING, "NONE");
                String customName = item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "pet_item_name"), PersistentDataType.STRING);

                if (plugin.getConfigManager().getData().contains(p.getUniqueId() + ".pets." + petId)) {
                    p.sendMessage(lang.getMessage("pet.already_owned"));
                    return;
                }

                if (!plugin.getOwnershipManager().canAcquirePet(p)) {
                    return;
                }

                plugin.getConfigManager().createPetDataIfMissing(p.getUniqueId(), petId);
                plugin.getConfigManager().getData().set(p.getUniqueId() + ".pets." + petId + ".level", lvl);
                plugin.getConfigManager().getData().set(p.getUniqueId() + ".pets." + petId + ".exp", exp);
                plugin.getConfigManager().getData().set(p.getUniqueId() + ".pets." + petId + ".stars", stars);
                plugin.getConfigManager().getData().set(p.getUniqueId() + ".pets." + petId + ".trait", trait);
                if (customName != null && !customName.isEmpty()) {
                    plugin.getConfigManager().getData().set(p.getUniqueId() + ".pets." + petId + ".custom_name", customName);
                }
                plugin.getConfigManager().saveData();

                plugin.getCodexManager().discover(p.getUniqueId(), petId);

                item.setAmount(item.getAmount() - 1);
                p.sendMessage(lang.getMessage("pet.redeem_success", "%pet_id%", petId));
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
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
        if (e.getTarget() != null && e.getTarget().getUniqueId().equals(ownerId)) {
            e.setCancelled(true);
            e.setTarget(null);
            if (e.getEntity() instanceof Warden warden && e.getTarget() instanceof LivingEntity) {
                warden.clearAnger((LivingEntity) e.getTarget());
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
                mob.setTarget(target);
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

            if (e.getDamager() instanceof Monster) {
                double reduction = plugin.getSkillManager().getDamageReductionPercent(owner);
                if (reduction > 0) {
                    e.setDamage(e.getDamage() * (1.0 - (reduction / 100.0)));
                }
            }
        }

        // Quyết Đấu Pet (Pet Duel)
        if (isPet(e.getDamager()) && isPet(e.getEntity())) {
            UUID p1 = getOwnerId(e.getDamager());
            UUID p2 = getOwnerId(e.getEntity());
            if (plugin.getPetManager().activeDuels.containsKey(p1) && plugin.getPetManager().activeDuels.get(p1).equals(p2)) {
                e.setCancelled(false);
                ConfigManager cm = plugin.getConfigManager();
                String petId1 = plugin.getPetManager().getActivePetId(p1);
                int lvl1 = cm.getData().getInt(p1 + ".pets." + petId1 + ".level", 1);
                double damage = cm.getPetStat(petId1, lvl1, "damage");
                String petId2 = plugin.getPetManager().getActivePetId(p2);
                int lvl2 = cm.getData().getInt(p2 + ".pets." + petId2 + ".level", 1);
                double defense = cm.getPetStat(petId2, lvl2, "defense");
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
        UUID ownerId = getOwnerId(e.getEntity());
        String petId = plugin.getPetManager().getActivePetId(ownerId);
        plugin.getPetManager().removePet(ownerId);
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
                    for (String pid : pets) if (cm.isPetDead(p.getUniqueId(), pid)) cm.setPetStatus(p.getUniqueId(), pid, "ALIVE");
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
        if (p.hasPermission("ipsecuzpet.admin")) {
            new UpdateChecker(plugin, 130551).getVersion(version -> {
                if (!plugin.getDescription().getVersion().equals(version)) {
                    p.sendMessage("§8[§6IpsecuzPet§8] §aNew Version: §e" + version);
                    p.sendMessage("§8[§6IpsecuzPet§8] §7Download Now: §fhttps://www.spigotmc.org/resources/130551");
                }
            });
        }
    }
}