package org.ipsecuz.pet;

import java.util.*;

public class PetCodexManager {
    private final IpsecuzPet plugin;

    public PetCodexManager(IpsecuzPet plugin) {
        this.plugin = plugin;
    }

    public boolean isDiscovered(UUID uuid, String petId) {
        if (uuid == null || petId == null) return false;
        var data = plugin.getConfigManager().getData();
        if (data.getBoolean(uuid + ".codex." + petId, false)) return true;
        // Nếu đã từng sở hữu trong data.pets thì xem như đã khám phá
        return data.contains(uuid + ".pets." + petId);
    }

    public boolean hasDiscovered(UUID uuid, String petId) {
        return isDiscovered(uuid, petId);
    }

    public void discover(UUID uuid, String petId) {
        if (uuid == null || petId == null) return;
        if (!isDiscovered(uuid, petId)) {
            plugin.getConfigManager().getData().set(uuid + ".codex." + petId, true);
            plugin.getConfigManager().saveData();
        }
    }

    public int getDiscoveredCount(UUID uuid) {
        if (uuid == null) return 0;
        Set<String> discovered = new HashSet<>();
        var petsSec = plugin.getConfig().getConfigurationSection("pets");
        if (petsSec != null) {
            for (String key : petsSec.getKeys(false)) {
                if (isDiscovered(uuid, key)) {
                    discovered.add(key);
                }
            }
        }
        return discovered.size();
    }

    public int getTotalPetCount() {
        var petsSec = plugin.getConfig().getConfigurationSection("pets");
        return petsSec != null ? petsSec.getKeys(false).size() : 0;
    }
}

