package org.ipsecuz.pet;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages chat-based pet renaming sessions.
 * Intercepts player chat input, validates nickname constraints, and persists custom names.
 */
public class RenameSessionManager {

    public record RenameSession(UUID playerId, String petId, long startTime) {}

    private final IpsecuzPet plugin;
    private final Map<UUID, RenameSession> sessions = new ConcurrentHashMap<>();
    private static final long SESSION_TIMEOUT_MS = 60_000L; // 60 seconds

    public RenameSessionManager(IpsecuzPet plugin) {
        this.plugin = plugin;
    }

    public void startSession(Player player, String petId) {
        if (player == null || petId == null) return;
        sessions.put(player.getUniqueId(), new RenameSession(player.getUniqueId(), petId, System.currentTimeMillis()));
        player.sendMessage(plugin.getLanguage().getMessage("pet.rename_prompt"));
    }

    public boolean hasSession(UUID playerId) {
        RenameSession session = sessions.get(playerId);
        if (session == null) return false;
        if (System.currentTimeMillis() - session.startTime() > SESSION_TIMEOUT_MS) {
            sessions.remove(playerId);
            Player p = plugin.getServer().getPlayer(playerId);
            if (p != null && p.isOnline()) {
                p.sendMessage(plugin.getLanguage().getMessage("pet.rename_timeout"));
            }
            return false;
        }
        return true;
    }

    public RenameSession getSession(UUID playerId) {
        return sessions.get(playerId);
    }

    public void cancelSession(UUID playerId) {
        sessions.remove(playerId);
    }

    public boolean handleChatInput(Player player, String input) {
        if (player == null || input == null) return false;
        RenameSession session = sessions.remove(player.getUniqueId());
        if (session == null) return false;

        if (System.currentTimeMillis() - session.startTime() > SESSION_TIMEOUT_MS) {
            player.sendMessage(plugin.getLanguage().getMessage("pet.rename_timeout"));
            return true;
        }

        String raw = input.trim();
        if (raw.equalsIgnoreCase("cancel") || raw.equalsIgnoreCase("huy")) {
            player.sendMessage(plugin.getLanguage().getMessage("pet.rename_cancelled"));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return true;
        }

        String clean = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', raw));
        if (clean.length() < 2 || clean.length() > 16) {
            player.sendMessage(plugin.getLanguage().getMessage("pet.rename_length_error"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            // Re-store session so user can retry without reopening GUI
            sessions.put(player.getUniqueId(), new RenameSession(player.getUniqueId(), session.petId(), System.currentTimeMillis()));
            return true;
        }

        // Persist new custom name
        plugin.getConfigManager().setCustomName(player.getUniqueId(), session.petId(), raw);

        // Update currently active pet if spawned
        Entity activePet = plugin.getPetManager().getPet(player.getUniqueId());
        if (activePet != null && session.petId().equals(plugin.getPetManager().getActivePetId(player.getUniqueId()))) {
            plugin.getPetManager().updatePetNameplate(activePet, player.getUniqueId(), session.petId());
        }

        player.sendMessage(plugin.getLanguage().getMessage("pet.rename_success", "%new_name%", raw));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        return true;
    }
}

