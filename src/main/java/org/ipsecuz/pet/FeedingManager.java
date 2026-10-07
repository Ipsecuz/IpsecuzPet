package org.ipsecuz.pet;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class FeedingManager {
    private final IpsecuzPet plugin;

    public enum HappinessState {
        ECSTATIC("§aHạnh Phúc (1.25x EXP, +15% Tốc)", 1.25, 1.15),
        CONTENT("§eBình Thường", 1.0, 1.0),
        SAD("§6Hơi Buồn (-10% Chỉ số)", 0.9, 0.95),
        STARVING("§cĐói Lả (-15% Chỉ số)", 0.75, 0.85);

        private final String display;
        private final double expMult;
        private final double speedMult;

        HappinessState(String display, double expMult, double speedMult) {
            this.display = display;
            this.expMult = expMult;
            this.speedMult = speedMult;
        }

        public String getDisplay() { return display; }
        public double getExpMultiplier() { return expMult; }
        public double getSpeedMultiplier() { return speedMult; }
    }

    public FeedingManager(IpsecuzPet plugin) {
        this.plugin = plugin;
    }

    public void startDecayTask() {
        FileConfiguration cfg = plugin.getModuleManager().getFeedingConfig();
        int intervalMinutes = cfg.getInt("decay_interval_minutes", 10);
        long intervalTicks = Math.max(1, intervalMinutes) * 60L * 20L;

        SchedulerUtils.runGlobalTimer(plugin, this::decayHappiness, intervalTicks, intervalTicks);
    }

    private void decayHappiness() {
        if (!plugin.getModuleManager().isFeedingEnabled()) return;

        FileConfiguration cfg = plugin.getModuleManager().getFeedingConfig();
        int decayAmount = cfg.getInt("decay_amount", 5);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.getPetManager().hasPet(player.getUniqueId())) continue;
            String petId = plugin.getPetManager().getActivePetId(player.getUniqueId());
            if (petId == null) continue;

            int curHappy = getHappiness(player.getUniqueId(), petId);
            if (curHappy <= 0) continue;

            int newHappy = Math.max(0, curHappy - decayAmount);
            setHappiness(player.getUniqueId(), petId, newHappy);

            if (curHappy >= 20 && newHappy < 20) {
                player.sendMessage("§c§l[CẢNH BÁO PET] §eThú cưng của bạn đang cảm thấy đói và mệt mỏi! Hãy cho thú cưng ăn để hồi phục năng lượng (§6/pet feed§e)!");
                player.playSound(player.getLocation(), Sound.ENTITY_WOLF_WHINE, 1f, 1f);
                plugin.getPetManager().refreshPetStats(player);
            }
        }
    }

    public HappinessState getHappinessState(int happiness) {
        if (happiness >= 80) return HappinessState.ECSTATIC;
        if (happiness >= 50) return HappinessState.CONTENT;
        if (happiness >= 20) return HappinessState.SAD;
        return HappinessState.STARVING;
    }

    public int getHappiness(UUID uuid, String petId) {
        return plugin.getConfigManager().getData().getInt(uuid + ".pets." + petId + ".happiness", 100);
    }

    public void setHappiness(UUID uuid, String petId, int val) {
        int max = plugin.getModuleManager().getFeedingConfig().getInt("max_happiness", 100);
        val = Math.max(0, Math.min(max, val));
        plugin.getConfigManager().getData().set(uuid + ".pets." + petId + ".happiness", val);
        plugin.getConfigManager().saveData();
    }

    public boolean feedPet(Player player, ItemStack foodItem) {
        if (!plugin.getPetManager().hasPet(player.getUniqueId())) {
            player.sendMessage(plugin.getLanguage().getMessage("pet.no_pet"));
            return false;
        }
        String petId = plugin.getPetManager().getActivePetId(player.getUniqueId());
        return feedPet(player, petId, foodItem);
    }

    public boolean feedPet(Player player, String petId, ItemStack foodItem) {
        if (!plugin.getModuleManager().isFeedingEnabled()) {
            player.sendMessage("§cTính năng Cho Ăn & Thân Thiết hiện đang bị tắt bởi máy chủ!");
            return false;
        }

        if (player == null || petId == null) return false;

        if (!plugin.getConfigManager().getData().contains(player.getUniqueId() + ".pets." + petId)) {
            player.sendMessage(plugin.getLanguage().getMessage("pet.not_owned", "%pet_id%", petId));
            return false;
        }

        if (foodItem == null || foodItem.getType() == Material.AIR) {
            player.sendMessage("§cVui lòng cầm thức ăn trên tay!");
            return false;
        }

        FileConfiguration config = plugin.getModuleManager().getFeedingConfig();
        String matName = foodItem.getType().name();
        ConfigurationSection foodSec = config.getConfigurationSection("foods." + matName);

        if (foodSec == null) {
            player.sendMessage("§cThú cưng không thể ăn vật phẩm §e" + matName + "§c!");
            return false;
        }

        int curHappy = getHappiness(player.getUniqueId(), petId);
        int maxHappy = config.getInt("max_happiness", 100);
        if (curHappy >= maxHappy) {
            player.sendMessage("§aThú cưng đã no căng bụng và vô cùng hạnh phúc (" + maxHappy + "/" + maxHappy + ")!");
            return false;
        }

        int addHappy = foodSec.getInt("happiness_add", 15);
        int addExp = foodSec.getInt("exp_add", 20);
        String soundName = foodSec.getString("sound", "ENTITY_GENERIC_EAT");

        foodItem.setAmount(foodItem.getAmount() - 1);
        int newHappy = Math.min(maxHappy, curHappy + addHappy);
        setHappiness(player.getUniqueId(), petId, newHappy);

        // Cộng kinh nghiệm cụ thể cho pet này
        plugin.getPetManager().giveSpecificPetExp(player, petId, addExp);

        HappinessState state = getHappinessState(newHappy);
        player.sendActionBar(net.kyori.adventure.text.Component.text(
                "§d♡ Thân Thiết +" + addHappy + " §8(" + newHappy + "/" + maxHappy + ") §7| §b✦ EXP +" + addExp
        ));
        player.sendMessage("§aĐã cho thú cưng ăn §e" + matName + "§a! Độ vui vẻ: §e" + newHappy + "/" + maxHappy +
                " §7(" + state.getDisplay() + "§7) §a(+§b" + addExp + " EXP§a)");

        String activeId = plugin.getPetManager().getActivePetId(player.getUniqueId());
        boolean isActive = petId.equals(activeId);
        Entity pet = isActive ? plugin.getPetManager().getPet(player.getUniqueId()) : null;

        if (isActive) {
            plugin.getPetManager().refreshPetStats(player);
        }

        if (pet != null && pet.isValid()) {
            SchedulerUtils.runEntityTask(plugin, pet, () -> {
                try {
                    org.bukkit.util.Vector faceDir = player.getLocation().toVector().subtract(pet.getLocation().toVector());
                    if (faceDir.lengthSquared() > 0.001) {
                        org.bukkit.Location lookLoc = pet.getLocation().clone();
                        lookLoc.setDirection(faceDir);
                        pet.teleport(lookLoc);
                    }
                } catch (Exception ignored) {}

                plugin.getModelHandler().playTransientAnimation(pet, PetAnimationState.FEED, 30L, PetAnimationState.IDLE);

                try {
                    pet.getWorld().playSound(pet.getLocation(), Sound.valueOf(soundName), 1.2f, 1f);
                    if (newHappy >= maxHappy) {
                        pet.getWorld().playSound(pet.getLocation(), Sound.ENTITY_PLAYER_BURP, 1f, 1.1f);
                    }
                } catch (Exception ignored) {}

                try {
                    pet.getWorld().spawnParticle(Particle.HEART, pet.getLocation().add(0, pet.getHeight() + 0.3, 0), 8, 0.35, 0.35, 0.35, 0.05);
                } catch (Exception ignored) {}
            });
        } else {
            try {
                player.getWorld().playSound(player.getLocation(), Sound.valueOf(soundName), 1.2f, 1f);
                player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1.2, 0), 6, 0.3, 0.3, 0.3, 0.05);
            } catch (Exception ignored) {}
        }
        return true;
    }
}
