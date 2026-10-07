package org.ipsecuz.pet.model;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.data.renderer.ModelRenderer;
import kr.toxicity.model.api.tracker.EntityTracker;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.ipsecuz.pet.IpsecuzPet;
import org.ipsecuz.pet.PetAnimationState;
import org.ipsecuz.pet.SchedulerUtils;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class BetterModelProvider implements ModelProvider {

    private final IpsecuzPet plugin;
    private final Map<UUID, EntityTracker> activeTrackers = new ConcurrentHashMap<>();
    private final Map<UUID, PetAnimationState> currentStates = new ConcurrentHashMap<>();
    private final Map<UUID, Long> stateExpirationMs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastVisibilityCheck = new ConcurrentHashMap<>();
    private final Map<String, Long> lastErrorLogTime = new ConcurrentHashMap<>();
    private Boolean availableCache = null;

    public BetterModelProvider(IpsecuzPet plugin) {
        this.plugin = plugin;
    }

    @Override
    public ModelType getType() {
        return ModelType.BETTERMODEL;
    }

    @Override
    public boolean isAvailable() {
        if (availableCache == null) {
            try {
                availableCache = Bukkit.getPluginManager().isPluginEnabled("BetterModel")
                        && Class.forName("kr.toxicity.model.api.BetterModel") != null;
            } catch (Throwable t) {
                availableCache = false;
            }
        }
        return availableCache;
    }

    public void invalidateAvailabilityCache() {
        availableCache = null;
    }

    @Override
    public boolean spawn(Player owner, Entity pet, String modelId, String petId) {
        if (!isAvailable() || pet == null || modelId == null || modelId.trim().isEmpty()) {
            return false;
        }

        try {
            ModelRenderer renderer = BetterModel.modelOrNull(modelId);
            if (renderer == null) {
                plugin.getLogger().warning("§c[BetterModel] Không tìm thấy Model ID: " + modelId +
                        ". Các Model hiện có: " + BetterModel.modelKeys());
                return false;
            }

            if (pet instanceof LivingEntity living) {
                living.setInvisible(true);
            }

            EntityTracker tracker = renderer.create(pet);
            if (tracker != null) {
                activeTrackers.put(pet.getUniqueId(), tracker);
                if (owner != null && owner.isOnline()) {
                    try {
                        tracker.show(owner);
                    } catch (Throwable ignored) {}
                }
                playTransientAnimation(pet, PetAnimationState.SPAWN, 25L, PetAnimationState.IDLE);
                return true;
            }
        } catch (Throwable t) {
            logThrottledError("spawn", t);
        }
        return false;
    }

    @Override
    public void remove(Entity pet) {
        if (pet != null) {
            remove(pet.getUniqueId());
            if (pet instanceof LivingEntity living && pet.isValid()) {
                living.setInvisible(false);
            }
        }
    }

    @Override
    public void remove(UUID entityUuid) {
        if (entityUuid == null) return;
        currentStates.remove(entityUuid);
        stateExpirationMs.remove(entityUuid);
        lastVisibilityCheck.remove(entityUuid);
        EntityTracker tracker = activeTrackers.remove(entityUuid);
        if (tracker != null && !tracker.isClosed()) {
            try {
                tracker.close();
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void removeAll() {
        currentStates.clear();
        stateExpirationMs.clear();
        lastVisibilityCheck.clear();
        for (EntityTracker tracker : activeTrackers.values()) {
            if (tracker != null && !tracker.isClosed()) {
                try {
                    tracker.close();
                } catch (Throwable ignored) {}
            }
        }
        activeTrackers.clear();
    }

    @Override
    public void show(Entity pet, Player viewer) {
        if (pet == null || viewer == null) return;
        EntityTracker tracker = activeTrackers.get(pet.getUniqueId());
        if (tracker != null && !tracker.isClosed()) {
            try {
                tracker.show(viewer);
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void hide(Entity pet, Player viewer) {
        if (pet == null || viewer == null) return;
        EntityTracker tracker = activeTrackers.get(pet.getUniqueId());
        if (tracker != null && !tracker.isClosed()) {
            try {
                tracker.hide(viewer);
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void updatePosition(Entity pet) {
        // BetterModel EntityTracker updates position automatically via NMS packet listeners
    }

    @Override
    public void updateMultiplayerVisibility(Entity pet) {
        if (!isAvailable() || pet == null) return;
        UUID uuid = pet.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastCheck = lastVisibilityCheck.get(uuid);
        if (lastCheck != null && (now - lastCheck) < 500L) {
            return;
        }
        lastVisibilityCheck.put(uuid, now);

        EntityTracker tracker = activeTrackers.get(uuid);
        if (tracker == null || tracker.isClosed()) return;

        try {
            Location petLoc = pet.getLocation();
            double maxDistSq = 48.0 * 48.0;

            for (Player p : pet.getWorld().getNearbyPlayers(petLoc, 48.0)) {
                if (!p.isOnline()) continue;
                double distSq = p.getLocation().distanceSquared(petLoc);
                if (distSq <= maxDistSq) {
                    if (tracker.isHide(p)) {
                        tracker.show(p);
                    }
                } else {
                    if (!tracker.isHide(p)) {
                        tracker.hide(p);
                    }
                }
            }
        } catch (Throwable t) {
            logThrottledError("updateMultiplayerVisibility", t);
        }
    }

    @Override
    public void playAnimation(Entity pet, PetAnimationState state) {
        if (!isAvailable() || pet == null || state == null) return;
        UUID uuid = pet.getUniqueId();

        PetAnimationState current = currentStates.get(uuid);
        Long expire = stateExpirationMs.get(uuid);
        if (expire != null && System.currentTimeMillis() < expire) {
            if (current != null && current.getPriority() > state.getPriority()) {
                return;
            }
        }

        if (current == state) {
            return;
        }

        currentStates.put(uuid, state);
        executeAnimation(uuid, state);
    }

    @Override
    public void playTransientAnimation(Entity pet, PetAnimationState state, long durationTicks, PetAnimationState returnState) {
        if (!isAvailable() || pet == null || state == null) return;
        UUID uuid = pet.getUniqueId();

        PetAnimationState current = currentStates.get(uuid);
        Long expire = stateExpirationMs.get(uuid);
        if (expire != null && System.currentTimeMillis() < expire) {
            if (current != null && current.getPriority() > state.getPriority()) {
                return;
            }
        }

        currentStates.put(uuid, state);
        long targetExpiry = System.currentTimeMillis() + (durationTicks * 50L);
        stateExpirationMs.put(uuid, targetExpiry);
        executeAnimation(uuid, state);

        SchedulerUtils.runEntityTaskLater(plugin, pet, () -> {
            if (!pet.isValid()) return;
            if (currentStates.get(uuid) == state && System.currentTimeMillis() >= stateExpirationMs.getOrDefault(uuid, 0L) - 50L) {
                stateExpirationMs.remove(uuid);
                playAnimation(pet, returnState != null ? returnState : PetAnimationState.IDLE);
            }
        }, durationTicks);
    }

    @Override
    public void stopAnimation(Entity pet) {
        if (pet == null) return;
        EntityTracker tracker = activeTrackers.get(pet.getUniqueId());
        if (tracker != null && !tracker.isClosed()) {
            try {
                tracker.animate("idle");
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void handlePlayerQuit(Player player) {
        if (!isAvailable() || player == null) return;
        for (EntityTracker tracker : activeTrackers.values()) {
            if (tracker != null && !tracker.isClosed()) {
                try {
                    tracker.remove(player);
                } catch (Throwable ignored) {}
            }
        }
    }

    private void executeAnimation(UUID uuid, PetAnimationState state) {
        EntityTracker tracker = activeTrackers.get(uuid);
        if (tracker == null || tracker.isClosed()) return;

        try {
            ModelRenderer renderer = tracker.renderer();
            Set<String> available = (renderer != null && renderer.animations() != null)
                    ? renderer.animations().keySet() : Collections.emptySet();

            String targetAnim = null;
            if (!available.isEmpty()) {
                if (available.contains(state.getPrimaryName())) {
                    targetAnim = state.getPrimaryName();
                } else {
                    for (String fallback : state.getFallbacks()) {
                        if (available.contains(fallback)) {
                            targetAnim = fallback;
                            break;
                        }
                    }
                    if (targetAnim == null && available.contains("idle")) {
                        targetAnim = "idle";
                    }
                }
            } else {
                targetAnim = state.getPrimaryName();
            }

            if (targetAnim != null) {
                boolean started = tracker.animate(targetAnim);
                if (!started) {
                    for (String fallback : state.getFallbacks()) {
                        if (tracker.animate(fallback)) break;
                    }
                }
            }
        } catch (Throwable t) {
            logThrottledError("executeAnimation", t);
        }
    }

    private void logThrottledError(String action, Throwable t) {
        long now = System.currentTimeMillis();
        Long last = lastErrorLogTime.get(action);
        if (last == null || (now - last) > 10000L) {
            lastErrorLogTime.put(action, now);
            plugin.getLogger().log(Level.WARNING, "§c[BetterModelProvider] Lỗi trong (" + action + "): " + t.getMessage(), t);
        }
    }
}
