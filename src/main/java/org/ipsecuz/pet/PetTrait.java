package org.ipsecuz.pet;

import org.bukkit.ChatColor;

import java.util.concurrent.ThreadLocalRandom;

public enum PetTrait {
    NONE("Bình Thường", "&7Bình Thường", 1.0, 1.0, 1.0, 1.0, 1.0),
    SAVAGE("Hung Tợn", "&c⚔ Hung Tợn", 1.0, 1.15, 1.0, 1.0, 1.0),
    GUARDIAN("Hộ Vệ", "&9🛡 Hộ Vệ", 1.0, 1.0, 1.20, 1.0, 1.0),
    SWIFT("Nhanh Nhẹn", "&b⚡ Nhanh Nhẹn", 1.0, 1.0, 1.0, 1.15, 1.0),
    VITAL("Sức Sống", "&a❤ Sức Sống", 1.20, 1.0, 1.0, 1.0, 1.0),
    TITAN("Khổng Lồ", "&6★ Thần Lực", 1.15, 1.10, 1.15, 0.95, 1.0),
    SCHOLAR("Thông Thái", "&d✦ Thông Thái", 1.0, 1.0, 1.0, 1.0, 1.25);

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

    public static PetTrait fromString(String name) {
        if (name == null || name.trim().isEmpty()) return NONE;
        for (PetTrait t : values()) {
            if (t.name().equalsIgnoreCase(name) || t.rawName.equalsIgnoreCase(name)) {
                return t;
            }
        }
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
