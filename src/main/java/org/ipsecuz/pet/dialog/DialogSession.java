package org.ipsecuz.pet.dialog;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Immutable/tamper-resistant representation of an active Dialog session.
 * Protects against replay attacks, stale selections, and double-submissions.
 */
public class DialogSession {
    private final String nonce;
    private final UUID playerId;
    private final DialogType type;
    private final String petId;
    private final long createdAt;
    private final AtomicBoolean submitted = new AtomicBoolean(false);
    private final Map<String, Object> data;

    public DialogSession(UUID playerId, DialogType type, String petId, Map<String, Object> data) {
        this.nonce = UUID.randomUUID().toString();
        this.playerId = playerId;
        this.type = type;
        this.petId = petId;
        this.createdAt = System.currentTimeMillis();
        this.data = data != null ? new HashMap<>(data) : new HashMap<>();
    }

    public String getNonce() { return nonce; }
    public UUID getPlayerId() { return playerId; }
    public DialogType getType() { return type; }
    public String getPetId() { return petId; }
    public long getCreatedAt() { return createdAt; }

    public boolean isExpired(long ttlMs) {
        return (System.currentTimeMillis() - createdAt) > ttlMs;
    }

    public boolean markSubmitted() {
        return submitted.compareAndSet(false, true);
    }

    public boolean isSubmitted() {
        return submitted.get();
    }

    public boolean isValid() {
        return !isSubmitted() && !isExpired(300_000L);
    }

    public Map<String, Object> getData() {
        return Collections.unmodifiableMap(data);
    }

    @SuppressWarnings("unchecked")
    public <T> T getData(String key, T defaultValue) {
        Object val = data.get(key);
        if (val == null) return defaultValue;
        try {
            return (T) val;
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }
}

