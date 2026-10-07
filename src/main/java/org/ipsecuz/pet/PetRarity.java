package org.ipsecuz.pet;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;

public enum PetRarity {
    COMMON("Common", "&aCommon", NamedTextColor.GREEN, ChatColor.GREEN, 1, 100, Sound.BLOCK_NOTE_BLOCK_BELL, Particle.VILLAGER_HAPPY),
    UNCOMMON("Uncommon", "&bUncommon", NamedTextColor.AQUA, ChatColor.AQUA, 2, 70, Sound.BLOCK_NOTE_BLOCK_CHIME, Particle.CRIT),
    RARE("Rare", "&9Rare", NamedTextColor.BLUE, ChatColor.BLUE, 5, 40, Sound.BLOCK_AMETHYST_BLOCK_CHIME, Particle.ENCHANTMENT_TABLE),
    EPIC("Epic", "&d§lEpic", NamedTextColor.LIGHT_PURPLE, ChatColor.LIGHT_PURPLE, 10, 20, Sound.ENTITY_PLAYER_LEVELUP, Particle.SPELL_WITCH),
    LEGENDARY("Legendary", "&6§lLegendary", NamedTextColor.GOLD, ChatColor.GOLD, 25, 10, Sound.UI_TOAST_CHALLENGE_COMPLETE, Particle.TOTEM),
    MYTHIC("Mythic", "&c§lMythic", NamedTextColor.RED, ChatColor.RED, 50, 4, Sound.ENTITY_ENDER_DRAGON_GROWL, Particle.DRAGON_BREATH),
    SECRET("Secret", "&8§l✦ SECRET ✦", NamedTextColor.DARK_GRAY, ChatColor.DARK_GRAY, 100, 2, Sound.ENTITY_WITHER_SPAWN, Particle.PORTAL),
    ETERNAL("Eternal", "&e§l★ ETERNAL ★", TextColor.color(0xFFD700), ChatColor.YELLOW, 200, 1, Sound.UI_TOAST_CHALLENGE_COMPLETE, Particle.FLASH);

    private final String rawName;
    private final String formattedName;
    private final TextColor textColor;
    private final ChatColor chatColor;
    private final int shardValue;
    private final int defaultWeight;
    private final Sound revealSound;
    private final Particle revealParticle;

    PetRarity(String rawName, String formattedName, TextColor textColor, ChatColor chatColor, int shardValue, int defaultWeight, Sound revealSound, Particle revealParticle) {
        this.rawName = rawName;
        this.formattedName = formattedName;
        this.textColor = textColor;
        this.chatColor = chatColor;
        this.shardValue = shardValue;
        this.defaultWeight = defaultWeight;
        this.revealSound = revealSound;
        this.revealParticle = revealParticle;
    }

    public String getRawName() { return rawName; }
    public String getFormattedName() { return formattedName; }
    public TextColor getTextColor() { return textColor; }
    public ChatColor getChatColor() { return chatColor; }
    public int getShardValue() { return shardValue; }
    public int getDefaultWeight() { return defaultWeight; }
    public Sound getRevealSound() { return revealSound; }
    public Particle getRevealParticle() { return revealParticle; }

    public Component getDisplayNameComponent() {
        return Component.text(ChatColor.translateAlternateColorCodes('&', formattedName));
    }

    public String getLocalizedName(IpsecuzPet plugin) {
        if (plugin != null && plugin.getLanguage() != null) {
            String key = "rarity." + name().toLowerCase();
            String localized = plugin.getLanguage().getMessage(key);
            if (localized != null && !localized.startsWith("§cMissing")) {
                boolean bold = (this == EPIC || this == LEGENDARY || this == MYTHIC || this == SECRET || this == ETERNAL);
                return (bold ? chatColor + "§l" : chatColor.toString()) + localized;
            }
        }
        return getFormattedName();
    }

    public static PetRarity fromString(String name) {
        if (name == null || name.trim().isEmpty()) return COMMON;
        String clean = name.trim().toUpperCase();
        for (PetRarity r : values()) {
            if (r.name().equalsIgnoreCase(clean) || r.rawName.equalsIgnoreCase(clean)) {
                return r;
            }
        }
        return COMMON;
    }

    public static PetRarity fromPetId(IpsecuzPet plugin, String petId) {
        return getPetRarity(plugin, petId);
    }

    public static PetRarity getPetRarity(IpsecuzPet plugin, String petId) {
        if (plugin == null || petId == null) return COMMON;
        String configured = plugin.getConfig().getString("pets." + petId + ".rarity");
        if (configured != null && !configured.trim().isEmpty()) {
            return fromString(configured);
        }

        // Tự động suy luận theo loại tiền tệ và giá trị nếu chưa cấu hình
        String currency = plugin.getConfig().getString("pets." + petId + ".currency", "ITEM");
        double price = plugin.getConfig().getDouble("pets." + petId + ".price", 0);

        if ("POINTS".equalsIgnoreCase(currency)) {
            if (price >= 150) return LEGENDARY;
            if (price >= 70) return EPIC;
            return RARE;
        } else if ("MONEY".equalsIgnoreCase(currency)) {
            if (price >= 10000) return EPIC;
            if (price >= 4000) return RARE;
            return UNCOMMON;
        } else {
            if (price >= 64) return UNCOMMON;
            return COMMON;
        }
    }
}

