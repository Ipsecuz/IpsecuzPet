package org.ipsecuz.pet;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Authoritative engine for happiness-based stat and progression multipliers.
 * Centralizes calculations for EXP, speed, health, damage, and defense.
 */
public class HappinessModifierEngine {

    private final IpsecuzPet plugin;

    public HappinessModifierEngine(IpsecuzPet plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration getFeedingConfig() {
        if (plugin != null && plugin.getModuleManager() != null) {
            return plugin.getModuleManager().getFeedingConfig();
        }
        return null;
    }

    public boolean isEnabled() {
        return plugin != null && plugin.getModuleManager() != null && plugin.getModuleManager().isFeedingEnabled();
    }

    public int getBonusThreshold() {
        FileConfiguration cfg = getFeedingConfig();
        return cfg != null ? cfg.getInt("bonus_threshold", 80) : 80;
    }

    public int getPenaltyThreshold() {
        FileConfiguration cfg = getFeedingConfig();
        return cfg != null ? cfg.getInt("penalty_threshold", 20) : 20;
    }

    public double getExpMultiplier(int happiness) {
        if (!isEnabled()) return 1.0;
        FileConfiguration cfg = getFeedingConfig();
        if (cfg == null) return 1.0;

        int bonusThreshold = getBonusThreshold();
        int penaltyThreshold = getPenaltyThreshold();

        if (happiness >= bonusThreshold) {
            return cfg.getDouble("bonus_exp_multiplier", 1.25);
        } else if (happiness < penaltyThreshold) {
            return cfg.getDouble("penalty_exp_multiplier", 0.75);
        }
        return 1.0;
    }

    public double getStatMultiplier(int happiness, String statName) {
        if (!isEnabled() || statName == null) return 1.0;
        FileConfiguration cfg = getFeedingConfig();
        if (cfg == null) return 1.0;

        int bonusThreshold = getBonusThreshold();
        int penaltyThreshold = getPenaltyThreshold();

        String normalized = statName.toLowerCase();
        if (happiness >= bonusThreshold) {
            switch (normalized) {
                case "speed":
                    return cfg.getDouble("bonus_speed_multiplier", 1.15);
                case "health":
                case "max_health":
                    return cfg.getDouble("bonus_health_multiplier", cfg.getDouble("bonus_stat_multiplier", 1.10));
                case "damage":
                    return cfg.getDouble("bonus_damage_multiplier", cfg.getDouble("bonus_stat_multiplier", 1.10));
                case "defense":
                    return cfg.getDouble("bonus_defense_multiplier", cfg.getDouble("bonus_stat_multiplier", 1.10));
                default:
                    return cfg.getDouble("bonus_stat_multiplier", 1.10);
            }
        } else if (happiness < penaltyThreshold) {
            switch (normalized) {
                case "speed":
                    return cfg.getDouble("penalty_speed_multiplier", 0.85);
                case "health":
                case "max_health":
                    return cfg.getDouble("penalty_health_multiplier", cfg.getDouble("penalty_stat_multiplier", 0.90));
                case "damage":
                    return cfg.getDouble("penalty_damage_multiplier", cfg.getDouble("penalty_stat_multiplier", 0.90));
                case "defense":
                    return cfg.getDouble("penalty_defense_multiplier", cfg.getDouble("penalty_stat_multiplier", 0.90));
                default:
                    return cfg.getDouble("penalty_stat_multiplier", 0.90);
            }
        }
        return 1.0;
    }

    public double getHealthMultiplier(int happiness) {
        return getStatMultiplier(happiness, "health");
    }

    public double getDamageMultiplier(int happiness) {
        return getStatMultiplier(happiness, "damage");
    }

    public double getDefenseMultiplier(int happiness) {
        return getStatMultiplier(happiness, "defense");
    }

    public double getSpeedMultiplier(int happiness) {
        return getStatMultiplier(happiness, "speed");
    }
}

