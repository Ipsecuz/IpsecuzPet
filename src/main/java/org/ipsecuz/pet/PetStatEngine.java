package org.ipsecuz.pet;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.UUID;

/**
 * Authoritative single source of truth for all Pet Stat calculations.
 * Ensures strict consistency between:
 * - Entity living attributes
 * - GUI status displays & tooltips
 * - Evolution preview & star delta
 * - Skill calculations & combat engine
 */
public final class PetStatEngine {

    private PetStatEngine() {}

    /**
     * Calculates base stat at a given level without star, trait, or happiness modifiers.
     * Level 1 returns exactly base stat (Level 1 => (1 - 1) * growth = 0).
     */
    public static double getBaseStatAtLevel(IpsecuzPet plugin, String petId, int level, String statName) {
        if (plugin == null || petId == null || statName == null) return 0.0;
        double base = plugin.getConfig().getDouble("pets." + petId + ".stats." + statName, 0.0);
        double growth = plugin.getConfig().getDouble("rpg_system.default_growth." + statName, 0.0);
        int effectiveLevel = Math.max(1, level);
        return base + ((effectiveLevel - 1) * growth);
    }

    /**
     * Calculates the star multiplier based on evolution configuration.
     * Applies to health, damage, and defense.
     */
    public static double getStarMultiplierForStat(IpsecuzPet plugin, int stars, String statName) {
        if (plugin == null) return 1.0;
        int safeStars = Math.max(1, stars);

        if ("speed".equalsIgnoreCase(statName)) {
            // Speed boost per star is configurable, defaulting to 0.0 to prevent locomotion glitches
            double speedBoost = plugin.getModuleManager().getEvolutionConfig().getDouble("speed_boost_per_star", 0.0);
            return 1.0 + ((safeStars - 1) * speedBoost);
        }

        double boostPerStar = plugin.getModuleManager().getEvolutionConfig().getDouble("stat_boost_per_star", 0.15);
        return 1.0 + ((safeStars - 1) * boostPerStar);
    }

    /**
     * Calculates the preview stat for Evolution GUI (includes star multiplier, but no runtime happiness).
     */
    public static double calculateStatPreview(IpsecuzPet plugin, String petId, int level, int stars, String statName) {
        double baseLvl = getBaseStatAtLevel(plugin, petId, level, statName);
        double starMult = getStarMultiplierForStat(plugin, stars, statName);
        return baseLvl * starMult;
    }

    public static double calculateStatPreview(IpsecuzPet plugin, String petId, int level, int stars, PetTrait trait, int happiness, String statName) {
        double stat = calculateStatPreview(plugin, petId, level, stars, statName);
        if (trait != null) {
            switch (statName.toLowerCase()) {
                case "health":
                case "max_health":
                    stat *= trait.getHealthMultiplier();
                    break;
                case "damage":
                    stat *= trait.getDamageMultiplier();
                    break;
                case "defense":
                    stat *= trait.getDefenseMultiplier();
                    break;
                case "speed":
                    stat *= trait.getSpeedMultiplier();
                    break;
                default:
                    break;
            }
        }
        if (happiness >= 80) {
            if ("speed".equalsIgnoreCase(statName)) {
                stat *= 1.15;
            }
        } else if (happiness < 20) {
            stat *= 0.85;
        }
        return Math.max(0.0, stat);
    }

    /**
     * Calculates the full effective stat for an active entity or player view:
     * Base(Lvl) * StarMultiplier * TraitMultiplier * HappinessMultiplier.
     */
    public static double calculateEffectiveStat(IpsecuzPet plugin, UUID ownerId, String petId, int level, String statName) {
        if (plugin == null || petId == null || statName == null) return 0.0;

        int stars = (plugin.getEvolutionManager() != null && ownerId != null)
                ? plugin.getEvolutionManager().getStar(ownerId, petId) : 1;

        double stat = getBaseStatAtLevel(plugin, petId, level, statName);
        stat *= getStarMultiplierForStat(plugin, stars, statName);

        // Apply Trait multiplier
        if (ownerId != null) {
            String traitName = plugin.getConfigManager().getData().getString(ownerId + ".pets." + petId + ".trait", "NONE");
            PetTrait trait = PetTrait.fromString(traitName);
            switch (statName.toLowerCase()) {
                case "health":
                case "max_health":
                    stat *= trait.getHealthMultiplier();
                    break;
                case "damage":
                    stat *= trait.getDamageMultiplier();
                    break;
                case "defense":
                    stat *= trait.getDefenseMultiplier();
                    break;
                case "speed":
                    stat *= trait.getSpeedMultiplier();
                    break;
                default:
                    break;
            }
        }

        // Apply Happiness multiplier
        if (plugin.getFeedingManager() != null && ownerId != null) {
            int happy = plugin.getFeedingManager().getHappiness(ownerId, petId);
            if (happy >= 80) {
                if ("speed".equalsIgnoreCase(statName)) {
                    stat *= 1.15;
                }
            } else if (happy < 20) {
                stat *= 0.85;
            }
        }

        return Math.max(0.0, stat);
    }
}
