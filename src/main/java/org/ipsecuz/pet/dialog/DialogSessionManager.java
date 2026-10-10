package org.ipsecuz.pet.dialog;

import org.bukkit.entity.Player;
import org.ipsecuz.pet.IpsecuzPet;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active player Dialog sessions, enforces single-use tokens, and handles timeouts.
 */
public class DialogSessionManager {
    public static final long DEFAULT_TTL_MS = 120_000L; // 2 minutes

    private final IpsecuzPet plugin;
    private final Map<UUID, DialogSession> activeSessions = new ConcurrentHashMap<>();

    public DialogSessionManager(IpsecuzPet plugin) {
        this.plugin = plugin;
    }

    public DialogSession createSession(Player player, DialogType type, String petId, Map<String, Object> data) {
        if (player == null) return null;
        DialogSession session = new DialogSession(player.getUniqueId(), type, petId, data);
        activeSessions.put(player.getUniqueId(), session);
        return session;
    }

    public DialogSession getSession(Player player) {
        if (player == null) return null;
        DialogSession session = activeSessions.get(player.getUniqueId());
        if (session != null && session.isExpired(DEFAULT_TTL_MS)) {
            activeSessions.remove(player.getUniqueId());
            return null;
        }
        return session;
    }

    public DialogSession validateAndConsume(Player player, String nonce, DialogType expectedType) {
        if (player == null || nonce == null) return null;
        DialogSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return null;

        if (session.isExpired(DEFAULT_TTL_MS)) {
            activeSessions.remove(player.getUniqueId());
            return null;
        }

        if (!nonce.equals(session.getNonce())) {
            return null;
        }

        if (expectedType != null && session.getType() != expectedType) {
            return null;
        }

        if (!session.markSubmitted()) {
            // Already submitted (double-click/replay prevention)
            return null;
        }

        // Successfully consumed
        activeSessions.remove(player.getUniqueId());
        return session;
    }

    public void invalidate(UUID playerId) {
        if (playerId != null) {
            activeSessions.remove(playerId);
        }
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        activeSessions.entrySet().removeIf(e -> e.getValue().isExpired(DEFAULT_TTL_MS));
    }

    public void shutdown() {
        activeSessions.clear();
    }
}

