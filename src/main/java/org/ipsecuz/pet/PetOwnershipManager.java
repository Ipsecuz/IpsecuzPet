package org.ipsecuz.pet;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.*;

public class PetOwnershipManager {
    private final IpsecuzPet plugin;

    public PetOwnershipManager(IpsecuzPet plugin) {
        this.plugin = plugin;
    }

    public int getMaxOwnedPets(Player player) {
        if (player == null) return plugin.getConfig().getInt("max_pets", 2);
        int max = plugin.getConfig().getInt("max_pets", 2);
        try {
            for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
                if (!info.getValue()) continue;
                String perm = info.getPermission();
                if (perm != null && perm.startsWith("ipsecuzpet.maxslots.")) {
                    try {
                        int amount = Integer.parseInt(perm.substring("ipsecuzpet.maxslots.".length()));
                        if (amount > max) max = amount;
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return max;
    }

    public int getOwnedPetCount(UUID uuid) {
        if (uuid == null) return 0;
        var sec = plugin.getConfigManager().getData().getConfigurationSection(uuid + ".pets");
        return sec != null ? sec.getKeys(false).size() : 0;
    }

    public List<String> getOwnedPetIds(UUID uuid) {
        if (uuid == null) return Collections.emptyList();
        var sec = plugin.getConfigManager().getData().getConfigurationSection(uuid + ".pets");
        if (sec == null) return Collections.emptyList();
        return new ArrayList<>(sec.getKeys(false));
    }

    public boolean hasPet(UUID uuid, String petId) {
        if (uuid == null || petId == null) return false;
        return plugin.getConfigManager().getData().contains(uuid + ".pets." + petId);
    }

    public boolean canAcquirePet(Player player) {
        if (player == null) return false;
        int current = getOwnedPetCount(player.getUniqueId());
        int limit = getMaxOwnedPets(player);
        return current < limit;
    }

    public boolean canAcquirePet(UUID uuid) {
        if (uuid == null) return false;
        Player online = org.bukkit.Bukkit.getPlayer(uuid);
        if (online != null && online.isOnline()) {
            return canAcquirePet(online);
        }
        int current = getOwnedPetCount(uuid);
        int limit = plugin.getConfig().getInt("max_pets", 2);
        return current < limit;
    }

    public boolean acquirePet(Player player, String petId, int level, int exp, int stars, PetTrait trait, String customName) {
        if (player == null || petId == null) return false;
        UUID uuid = player.getUniqueId();

        if (hasPet(uuid, petId)) {
            return false;
        }

        if (!canAcquirePet(player)) {
            return false;
        }

        String path = uuid + ".pets." + petId;
        var data = plugin.getConfigManager().getData();
        data.set(path + ".level", Math.max(1, level));
        data.set(path + ".exp", Math.max(0, exp));
        data.set(path + ".stars", Math.max(1, stars));
        data.set(path + ".status", "ALIVE");
        data.set(path + ".happiness", 100);
        data.set(path + ".is_baby", false);
        if (trait != null && trait != PetTrait.NONE) {
            data.set(path + ".trait", trait.name());
        }
        if (customName != null && !customName.trim().isEmpty()) {
            data.set(path + ".customName", customName);
        }

        // Đánh dấu phát hiện trong Bộ Sưu Tập (Codex)
        data.set(uuid + ".codex." + petId, true);

        plugin.getConfigManager().saveData();
        return true;
    }

    public boolean removePet(UUID uuid, String petId) {
        if (uuid == null || petId == null) return false;
        if (!hasPet(uuid, petId)) return false;

        // Nếu pet đang được triệu hồi, cất đi trước
        if (plugin.getPetManager() != null && plugin.getPetManager().hasPet(uuid)) {
            String active = plugin.getPetManager().getActivePetId(uuid);
            if (petId.equals(active)) {
                plugin.getPetManager().removePet(uuid);
            }
        }

        plugin.getConfigManager().deletePetData(uuid, petId);
        return true;
    }
}

