package org.ipsecuz.pet;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GameListener implements Listener {
    private final IpsecuzPet plugin;

    public GameListener(IpsecuzPet plugin) {
        this.plugin = plugin;
    }

    private boolean isPet(Entity e) { return e.getPersistentDataContainer().has(plugin.getPetManager().petKey, PersistentDataType.STRING); }
    private UUID getOwnerId(Entity e) {
        String s = e.getPersistentDataContainer().get(plugin.getPetManager().petKey, PersistentDataType.STRING);
        return (s == null) ? null : UUID.fromString(s);
    }
    private int getMaxSlots(Player p) {
        int max = plugin.getConfig().getInt("max_pets", 2);
        for (PermissionAttachmentInfo info : p.getEffectivePermissions()) {
            String perm = info.getPermission();
            if (perm.startsWith("ipsecuzpet.maxslots.")) {
                try {
                    int amount = Integer.parseInt(perm.substring("ipsecuzpet.maxslots.".length()));
                    if (amount > max) max = amount;
                } catch (NumberFormatException ignored) {}
            }
        }
        return max;
    }

    @EventHandler
    public void onRedeemPet(PlayerInteractEvent e) {
        if (!e.hasItem()) return;
        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta()) return;

        NamespacedKey keyId = new NamespacedKey(plugin, "pet_item_id");
        if (item.getItemMeta().getPersistentDataContainer().has(keyId, PersistentDataType.STRING)) {
            e.setCancelled(true);
            if (e.getAction().toString().contains("RIGHT")) {
                Player p = e.getPlayer();
                LanguageManager lang = plugin.getLanguage();
                String petId = item.getItemMeta().getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
                int lvl = item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "pet_item_lvl"), PersistentDataType.INTEGER);
                int exp = item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "pet_item_exp"), PersistentDataType.INTEGER);

                if (plugin.getConfigManager().getData().contains(p.getUniqueId() + ".pets." + petId)) {
                    p.sendMessage(lang.getMessage("pet.already_owned"));
                    return;
                }

                List<String> owned = new ArrayList<>();
                if (plugin.getConfigManager().getData().getConfigurationSection(p.getUniqueId() + ".pets") != null) {
                    owned.addAll(plugin.getConfigManager().getData().getConfigurationSection(p.getUniqueId() + ".pets").getKeys(false));
                }

                int limit = getMaxSlots(p);
                if (owned.size() >= limit) {
                    p.sendMessage(lang.getMessage("pet.limit_reached", "%current%", String.valueOf(owned.size()), "%max%", String.valueOf(limit)));
                    return;
                }

                plugin.getConfigManager().createPetDataIfMissing(p.getUniqueId(), petId);
                plugin.getConfigManager().getData().set(p.getUniqueId() + ".pets." + petId + ".level", lvl);
                plugin.getConfigManager().getData().set(p.getUniqueId() + ".pets." + petId + ".exp", exp);
                plugin.getConfigManager().saveData();
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

        if (e.getRightClicked() instanceof Entity pet && p.isSneaking() && isPet(pet) && getOwnerId(pet).equals(p.getUniqueId())) {
            e.setCancelled(true);
            plugin.getPetManager().showPetStats(p, pet);
            return;
        }

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

                List<String> owned = new ArrayList<>();
                if (plugin.getConfigManager().getData().getConfigurationSection(p.getUniqueId() + ".pets") != null) {
                    owned.addAll(plugin.getConfigManager().getData().getConfigurationSection(p.getUniqueId() + ".pets").getKeys(false));
                }
                int limit = getMaxSlots(p);
                if (owned.size() >= limit) {
                    p.sendMessage(lang.getMessage("pet.limit_reached", "%current%", String.valueOf(owned.size()), "%max%", String.valueOf(limit)));
                    return;
                }

                hand.setAmount(hand.getAmount() - 1);
                p.sendMessage(lang.getMessage("pet.catch_start"));
                p.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, 1f, 1f);

                String finalPetId = foundPetId;
                SchedulerUtils.runEntityTaskLater(plugin, p, () -> {
                    if (!target.isValid()) return;
                    CaptureManager.CaptureResult result = plugin.getCaptureManager().calculateCapture(p, type, ballId);
                    if (result == CaptureManager.CaptureResult.TYPE_NOT_ALLOWED) {
                        p.sendMessage(lang.getMessage("pet.catch_fail_type"));
                        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 1f, 1f);
                    } else if (result == CaptureManager.CaptureResult.SUCCESS) {
                        p.sendMessage(lang.getMessage("pet.caught", "%pet_type%", plugin.getConfig().getString("pets." + finalPetId + ".name")));
                        target.remove();
                        plugin.getConfigManager().createPetDataIfMissing(p.getUniqueId(), finalPetId);
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                        p.spawnParticle(org.bukkit.Particle.VILLAGER_HAPPY, target.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5);
                    } else {
                        p.sendMessage(lang.getMessage("pet.catch_fail"));
                        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    }
                }, 40L);
                return;
            }
        }

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
            return;
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent e) {
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
                if (e.getEntity() instanceof Warden warden) warden.clearAnger(owner);
            }
        }
        if (e.getDamager() instanceof Player p && plugin.getPetManager().hasPet(p.getUniqueId())) {
            Entity pet = plugin.getPetManager().getPet(p.getUniqueId());
            if (pet instanceof Mob mob && !e.getEntity().equals(pet) && e.getEntity() instanceof LivingEntity target) {
                mob.setTarget(target);
            }
        }
        if (e.getEntity() instanceof Player owner && plugin.getPetManager().hasPet(owner.getUniqueId())) {
            Entity pet = plugin.getPetManager().getPet(owner.getUniqueId());
            if (!e.getDamager().equals(pet)) {
                if (pet instanceof Mob mob && e.getDamager() instanceof LivingEntity attacker) {
                    mob.setTarget(attacker);
                }
            }
        }
        if (isPet(e.getDamager()) && isPet(e.getEntity())) {
            UUID p1 = getOwnerId(e.getDamager());
            UUID p2 = getOwnerId(e.getEntity());
            if (plugin.getPetManager().activeDuels.containsKey(p1) && plugin.getPetManager().activeDuels.get(p1).equals(p2)) {
                e.setCancelled(false);
                ConfigManager cm = plugin.getConfigManager();
                String petId1 = plugin.getPetManager().getActivePetId(p1);
                int lvl1 = cm.getData().getInt(p1 + ".pets." + petId1 + ".level");
                double damage = cm.getPetStat(petId1, lvl1, "damage");
                String petId2 = plugin.getPetManager().getActivePetId(p2);
                int lvl2 = cm.getData().getInt(p2 + ".pets." + petId2 + ".level");
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
            if(org.bukkit.Bukkit.getPlayer(ownerId) != null)
                org.bukkit.Bukkit.getPlayer(ownerId).sendMessage(plugin.getLanguage().getMessage("pet.death"));
        }
    }

    @EventHandler
    public void onMobKill(EntityDeathEvent e) {
        if (e.getEntity().getKiller() != null && e.getEntity() instanceof Monster) {
            Player p = e.getEntity().getKiller();
            if (plugin.getPetManager().hasPet(p.getUniqueId())) {
                int exp = plugin.getConfig().getInt("rpg_system.exp_per_kill", 10);
                plugin.getPetManager().givePetExp(p, exp);
            }
        }
    }

    @EventHandler
    public void onMineDiamond(BlockBreakEvent e) {
        Material mat = e.getBlock().getType();
        if (mat == Material.DIAMOND_ORE || mat == Material.DEEPSLATE_DIAMOND_ORE) {
            Player p = e.getPlayer();
            ConfigManager cm = plugin.getConfigManager();
            List<String> pets = new ArrayList<>();
            if (cm.getData().getConfigurationSection(p.getUniqueId() + ".pets") != null) {
                pets.addAll(cm.getData().getConfigurationSection(p.getUniqueId() + ".pets").getKeys(false));
            }
            boolean hasDead = false;
            for(String pid : pets) if(cm.isPetDead(p.getUniqueId(), pid)) hasDead = true;
            if (hasDead) {
                cm.addReviveProgress(p.getUniqueId(), 1);
                int cur = cm.getReviveProgress(p.getUniqueId());
                int max = plugin.getConfig().getInt("rpg_system.revive_cost_diamonds", 50);
                p.sendActionBar(net.kyori.adventure.text.Component.text(
                        plugin.getLanguage().getMessage("pet.revive_progress", "%current%", String.valueOf(cur), "%max%", String.valueOf(max))
                ));
                if (cur >= max) {
                    for(String pid : pets) if(cm.isPetDead(p.getUniqueId(), pid)) cm.setPetStatus(p.getUniqueId(), pid, "ALIVE");
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