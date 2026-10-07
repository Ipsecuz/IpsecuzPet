package org.ipsecuz.pet;

import org.bukkit.ChatColor;

import java.util.concurrent.ThreadLocalRandom;

public enum PetTrait {
    NONE("Normal", "&7Normal", 1.0, 1.0, 1.0, 1.0, 1.0),
    SAVAGE("Savage", "&c⚔ Savage", 1.0, 1.15, 1.0, 1.0, 1.0),
    GUARDIAN("Guardian", "&9🛡 Guardian", 1.0, 1.0, 1.20, 1.0, 1.0),
    SWIFT("Swift", "&b⚡ Swift", 1.0, 1.0, 1.0, 1.15, 1.0),
    VITAL("Vital", "&a❤ Vital", 1.20, 1.0, 1.0, 1.0, 1.0),
    TITAN("Titan", "&6★ Titan", 1.15, 1.10, 1.15, 0.95, 1.0),
    SCHOLAR("Scholar", "&d✦ Scholar", 1.0, 1.0, 1.0, 1.0, 1.25);

    private final String rawName;
    private final String formattedName;
    private final double healthMultiplier;
    private final double damageMultiplier;
    private final double defenseMultiplier;
    private final double speedMultiplier;
    private final double expMultiplier;

    PetTrait(String rawName, String formattedName, double healthMultiplier, double damageMultiplier, double defenseMultiplier, double speedMultiplier, double expMultiplier) {
        this.rawName = rawName;
        this.formattedName = formattedName;
        this.healthMultiplier = healthMultiplier;
        this.damageMultiplier = damageMultiplier;
        this.defenseMultiplier = defenseMultiplier;
        this.speedMultiplier = speedMultiplier;
        this.expMultiplier = expMultiplier;
    }

    public String getRawName() { return rawName; }
    public String getFormattedName() { return ChatColor.translateAlternateColorCodes('&', formattedName); }
    public double getHealthMultiplier() { return healthMultiplier; }
    public double getDamageMultiplier() { return damageMultiplier; }
    public double getDefenseMultiplier() { return defenseMultiplier; }
    public double getSpeedMultiplier() { return speedMultiplier; }
    public double getExpMultiplier() { return expMultiplier; }

    public String getLocalizedName(IpsecuzPet plugin) {
        if (plugin != null && plugin.getLanguage() != null) {
            String val = plugin.getLanguage().getMessage("trait." + name().toLowerCase());
            if (val != null && !val.startsWith("§cMissing")) {
                return val;
            }
        }
        return getFormattedName();
    }

    public static PetTrait fromString(String name) {
        if (name == null || name.trim().isEmpty()) return NONE;
        String clean = name.trim();
        for (PetTrait t : values()) {
            if (t.name().equalsIgnoreCase(clean) || t.rawName.equalsIgnoreCase(clean)) {
                return t;
            }
        }
        if (clean.equalsIgnoreCase("Hung Tợn") || clean.equalsIgnoreCase("Hung Bạo")) return SAVAGE;
        if (clean.equalsIgnoreCase("Hộ Vệ")) return GUARDIAN;
        if (clean.equalsIgnoreCase("Nhanh Nhẹn")) return SWIFT;
        if (clean.equalsIgnoreCase("Sức Sống")) return VITAL;
        if (clean.equalsIgnoreCase("Khổng Lồ") || clean.equalsIgnoreCase("Thần Lực")) return TITAN;
        if (clean.equalsIgnoreCase("Thông Thái")) return SCHOLAR;
        if (clean.equalsIgnoreCase("Bình Thường")) return NONE;

        return NONE;
    }

    public static PetTrait rollRandomTrait() {
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < 45) return NONE;
        if (roll < 60) return SAVAGE;
        if (roll < 75) return GUARDIAN;
        if (roll < 88) return SWIFT;
        if (roll < 95) return VITAL;
        if (roll < 98) return SCHOLAR;
        return TITAN;
    }
}

