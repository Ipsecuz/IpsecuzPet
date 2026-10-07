package org.ipsecuz.pet.model;

import org.bukkit.entity.Entity;
import org.ipsecuz.pet.IpsecuzPet;
import org.ipsecuz.pet.PetAnimationState;
import org.ipsecuz.pet.SchedulerUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Authoritative Centralized Pet Animation Controller.
 * Manages animation state, priority, transient durations, and transition token/generation safety.
 * Prevents race conditions and stale callbacks from overriding newer high-priority animations.
 */
public class PetAnimationController {

    private final IpsecuzPet plugin;
    private final ModelProviderManager providerManager;

    private static class EntityAnimationRecord {
        PetAnimationState state;
        long generation;
        long expirationMs;
        String petId;

        EntityAnimationRecord(PetAnimationState state, long generation, long expirationMs, String petId) {
            this.state = state;
            this.generation = generation;
            this.expirationMs = expirationMs;
            this.petId = petId;
        }
    }

    private final Map<UUID, EntityAnimationRecord> activeStates = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicLong> generationCounters = new ConcurrentHashMap<>();

    public PetAnimationController(IpsecuzPet plugin, ModelProviderManager providerManager) {
        this.plugin = plugin;
        this.providerManager = providerManager;
    }

    public synchronized void registerPet(UUID entityUuid, String petId) {
        if (entityUuid == null) return;
        activeStates.put(entityUuid, new EntityAnimationRecord(PetAnimationState.IDLE, 0L, 0L, petId));
        generationCounters.put(entityUuid, new AtomicLong(0L));
    }

    public synchronized void unregisterPet(UUID entityUuid) {
        if (entityUuid == null) return;
        activeStates.remove(entityUuid);
        generationCounters.remove(entityUuid);
    }

    public synchronized void updatePetId(UUID entityUuid, String petId) {
        if (entityUuid == null) return;
        EntityAnimationRecord record = activeStates.get(entityUuid);
        if (record != null) {
            record.petId = petId;
        } else {
            activeStates.put(entityUuid, new EntityAnimationRecord(PetAnimationState.IDLE, 0L, 0L, petId));
        }
    }

    public synchronized boolean requestAnimation(Entity pet, PetAnimationState newState) {
        if (pet == null || newState == null) return false;
        UUID uuid = pet.getUniqueId();
        EntityAnimationRecord current = activeStates.get(uuid);
        if (current == null) {
            current = new EntityAnimationRecord(PetAnimationState.IDLE, 0L, 0L, null);
            activeStates.put(uuid, current);
        }

        long now = System.currentTimeMillis();
        // Priority check: If active state has not expired and has strictly higher priority, reject transition!
        if (now < current.expirationMs && current.state != null && current.state.getPriority() > newState.getPriority()) {
            return false;
        }

        // Avoid re-triggering non-transient animations that are already playing
        if (current.state == newState && !newState.isTransient()) {
            return false;
        }

        // Advance generation token to invalidate any previous scheduled transient callbacks
        long token = generationCounters.computeIfAbsent(uuid, k -> new AtomicLong(0L)).incrementAndGet();
        current.state = newState;
        current.generation = token;
        current.expirationMs = 0L;

        // Render animation through active provider
        ModelProvider provider = providerManager.getActiveProvider(uuid);
        if (provider != null) {
            String animationName = providerManager.resolveAnimationName(current.petId, provider.getType(), newState);
            provider.renderRawAnimation(pet, animationName, newState);
        }
        return true;
    }

    public synchronized boolean requestTransientAnimation(Entity pet, PetAnimationState newState, long durationTicks, PetAnimationState returnState) {
        if (pet == null || newState == null) return false;
        UUID uuid = pet.getUniqueId();
        EntityAnimationRecord current = activeStates.get(uuid);
        if (current == null) {
            current = new EntityAnimationRecord(PetAnimationState.IDLE, 0L, 0L, null);
            activeStates.put(uuid, current);
        }

        long now = System.currentTimeMillis();
        if (now < current.expirationMs && current.state != null && current.state.getPriority() > newState.getPriority()) {
            return false;
        }

        long token = generationCounters.computeIfAbsent(uuid, k -> new AtomicLong(0L)).incrementAndGet();
        current.state = newState;
        current.generation = token;
        current.expirationMs = now + (durationTicks * 50L);

        ModelProvider provider = providerManager.getActiveProvider(uuid);
        if (provider != null) {
            String animationName = providerManager.resolveAnimationName(current.petId, provider.getType(), newState);
            provider.renderRawAnimation(pet, animationName, newState);
        }

        final PetAnimationState targetReturn = returnState != null ? returnState : PetAnimationState.IDLE;
        SchedulerUtils.runEntityTaskLater(plugin, pet, () -> {
            synchronized (PetAnimationController.this) {
                if (!pet.isValid()) return;
                EntityAnimationRecord st = activeStates.get(uuid);
                // Stale callback protection: Only apply return state if generation still matches this token!
                if (st != null && st.generation == token) {
                    st.expirationMs = 0L;
                    requestAnimation(pet, targetReturn);
                }
            }
        }, durationTicks);

        return true;
    }

    public synchronized PetAnimationState getCurrentState(UUID uuid) {
        EntityAnimationRecord st = activeStates.get(uuid);
        return st != null ? st.state : PetAnimationState.IDLE;
    }

    public synchronized void stopAnimation(Entity pet) {
        if (pet == null) return;
        UUID uuid = pet.getUniqueId();
        EntityAnimationRecord st = activeStates.get(uuid);
        if (st != null) {
            generationCounters.computeIfAbsent(uuid, k -> new AtomicLong(0L)).incrementAndGet();
            st.state = PetAnimationState.IDLE;
            st.expirationMs = 0L;
        }
        ModelProvider provider = providerManager.getActiveProvider(uuid);
        if (provider != null) {
            provider.stopAnimation(pet);
        }
    }

    public synchronized void clear() {
        activeStates.clear();
        generationCounters.clear();
    }
}
