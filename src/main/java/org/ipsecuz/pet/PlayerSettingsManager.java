package org.ipsecuz.pet;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.UUID;

/**
 * Manages per-player gameplay preferences (particle effects, sound effects, UI mode, follow distance).
 * Persisted durably to data.yml.
 */
public class PlayerSettingsManager {

    private final IpsecuzPet plugin;

    public PlayerSettingsManager(IpsecuzPet plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration getData() {
        return plugin.getConfigManager().getData();
    }

    public boolean isParticlesEnabled(UUID playerId) {
        if (playerId == null) return true;
        String path = playerId + ".settings.particles";
        if (getData().contains(path)) {
            return getData().getBoolean(path);
        }
        return plugin.getConfig().getBoolean("settings.particles_enabled", true);
    }

    public void setParticlesEnabled(UUID playerId, boolean enabled) {
        if (playerId == null) return;
        getData().set(playerId + ".settings.particles", enabled);
        plugin.getConfigManager().forceSave();
    }

    public boolean isSoundsEnabled(UUID playerId) {
        if (playerId == null) return true;
        String path = playerId + ".settings.sounds";
        if (getData().contains(path)) {
            return getData().getBoolean(path);
        }
        return plugin.getConfig().getBoolean("settings.sounds_enabled", true);
    }

    public void setSoundsEnabled(UUID playerId, boolean enabled) {
        if (playerId == null) return;
        getData().set(playerId + ".settings.sounds", enabled);
        plugin.getConfigManager().forceSave();
    }

    public String getDialogModePreference(UUID playerId) {
        if (playerId == null) return null;
        return getData().getString(playerId + ".settings.ui_mode", null);
    }

    public void setDialogModePreference(UUID playerId, String mode) {
        if (playerId == null) return;
        getData().set(playerId + ".settings.ui_mode", mode);
        plugin.getConfigManager().forceSave();
    }

    public String getFollowMode(UUID playerId) {
        if (playerId == null) return "NORMAL";
        return getData().getString(playerId + ".settings.follow_mode", "NORMAL");
    }

    public void setFollowMode(UUID playerId, String mode) {
        if (playerId == null) return;
        getData().set(playerId + ".settings.follow_mode", mode != null ? mode.toUpperCase() : "NORMAL");
        plugin.getConfigManager().forceSave();
    }

    public int getFollowDistance(UUID playerId) {
        String mode = getFollowMode(playerId);
        if ("CLOSE".equalsIgnoreCase(mode)) {
            return 8;
        }
        return plugin.getConfig().getInt("settings.default_follow_distance", 16);
    }
}

