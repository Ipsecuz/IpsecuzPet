package org.ipsecuz.pet.requirement;

import org.bukkit.entity.Player;
import org.ipsecuz.pet.IpsecuzPet;

import java.util.UUID;

/**
 * Encapsulates the execution context of a requirement evaluation,
 * containing player, pet state, and plugin instance.
 */
public class RequirementContext {
    private final Player player;
    private final String petId;
    private final int petLevel;
    private final int petStars;
    private final IpsecuzPet plugin;

    public RequirementContext(Player player, String petId, int petLevel, int petStars, IpsecuzPet plugin) {
        this.player = player;
        this.petId = petId;
        this.petLevel = petLevel;
        this.petStars = petStars;
        this.plugin = plugin != null ? plugin : IpsecuzPet.getInstance();
    }

    public RequirementContext(Player player, IpsecuzPet plugin) {
        this(player, null, 1, 1, plugin);
    }

    public Player getPlayer() {
        return player;
    }

    public UUID getPlayerUUID() {
        return player != null ? player.getUniqueId() : null;
    }

    public String getPetId() {
        return petId;
    }

    public int getPetLevel() {
        return petLevel;
    }

    public int getPetStars() {
        return petStars;
    }

    public IpsecuzPet getPlugin() {
        return plugin;
    }
}
