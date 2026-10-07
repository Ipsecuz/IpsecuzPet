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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class BetterModelProvider implements ModelProvider {

    private final IpsecuzPet plugin;
    private final Map<UUID, EntityTracker> activeTrackers = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> entityViewers = new ConcurrentHashMap<>();
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

            EntityTracker tracker = renderer.create(pet);
            if (tracker != null) {
                activeTrackers.put(pet.getUniqueId(), tracker);
                if (owner != null && owner.isOnline()) {
                    try {
                        tracker.show(owner);
                    } catch (Throwable ignored) {}
                }
                // Only hide base entity after tracker is confirmed attached
                if (pet instanceof LivingEntity living) {
                    living.setInvisible(true);
                }
                return true;
            }
        } catch (Throwable t) {
            if (pet instanceof LivingEntity living && pet.isValid()) {
                living.setInvisible(false);
            }
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
        lastVisibilityCheck.remove(entityUuid);
        entityViewers.remove(entityUuid);
        EntityTracker tracker = activeTrackers.remove(entityUuid);
        if (tracker != null && !tracker.isClosed()) {
            try {
                tracker.close();
            } catch (Throwable t) {
                logThrottledError("tracker.close", t);
            }
        }
        try {
            Entity ent = Bukkit.getEntity(entityUuid);
            if (ent instanceof LivingEntity living && ent.isValid()) {
                living.setInvisible(false);
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void removeAll() {
        lastVisibilityCheck.clear();
        entityViewers.clear();
        for (EntityTracker tracker : activeTrackers.values()) {
            if (tracker != null && !tracker.isClosed()) {
                try {
                    tracker.close();
                } catch (Throwable t) {
                    logThrottledError("tracker.closeAll", t);
                }
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

            Set<UUID> currentNearby = new HashSet<>();
            for (Player p : pet.getWorld().getNearbyPlayers(petLoc, 48.0)) {
                if (p.isOnline() && p.getLocation().distanceSquared(petLoc) <= maxDistSq) {
                    currentNearby.add(p.getUniqueId());
                }
            }

            Set<UUID> previous = entityViewers.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());

            // Show new viewers: currentNearby - previous
            for (UUID pId : currentNearby) {
                if (!previous.contains(pId)) {
                    Player p = Bukkit.getPlayer(pId);
                    if (p != null && p.isOnline()) {
                        tracker.show(p);
                    }
                }
            }

            // Hide removed viewers: previous - currentNearby
            for (UUID oldId : previous) {
                if (!currentNearby.contains(oldId)) {
                    Player p = Bukkit.getPlayer(oldId);
                    if (p != null && p.isOnline()) {
                        tracker.hide(p);
                    }
                }
            }

            previous.clear();
            previous.addAll(currentNearby);
        } catch (Throwable t) {
            logThrottledError("updateMultiplayerVisibility", t);
        }
    }

    @Override
    public void renderRawAnimation(Entity pet, String animationName, PetAnimationState state) {
        if (!isAvailable() || pet == null || animationName == null) return;
        EntityTracker tracker = activeTrackers.get(pet.getUniqueId());
        if (tracker != null && !tracker.isClosed()) {
            try {
                tracker.animate(animationName);
            } catch (Throwable t) {
                logThrottledError("renderRawAnimation", t);
            }
        }
    }

    @Override
    public void playAnimation(Entity pet, PetAnimationState state) {
        if (pet == null || state == null) return;
        if (plugin.getModelProviderManager() != null && plugin.getModelProviderManager().getAnimationController() != null) {
            plugin.getModelProviderManager().getAnimationController().requestAnimation(pet, state);
        } else {
            renderRawAnimation(pet, state.getPrimaryName(), state);
        }
    }

    @Override
    public void playTransientAnimation(Entity pet, PetAnimationState state, long durationTicks, PetAnimationState returnState) {
        if (pet == null || state == null) return;
        if (plugin.getModelProviderManager() != null && plugin.getModelProviderManager().getAnimationController() != null) {
            plugin.getModelProviderManager().getAnimationController().requestTransientAnimation(pet, state, durationTicks, returnState);
        } else {
            renderRawAnimation(pet, state.getPrimaryName(), state);
        }
    }

    @Override
    public void stopAnimation(Entity pet) {
        if (pet == null) return;
        EntityTracker tracker = activeTrackers.get(pet.getUniqueId());
        if (tracker != null && !tracker.isClosed()) {
            try {
                String petId = null;
                try {
                    org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "pet_pet_id");
                    petId = pet.getPersistentDataContainer().get(key, org.bukkit.persistence.PersistentDataType.STRING);
                } catch (Throwable ignored) {}

                String idleAnim = (plugin.getModelProviderManager() != null)
                        ? plugin.getModelProviderManager().resolveAnimationName(petId, ModelType.BETTERMODEL, PetAnimationState.IDLE)
                        : "idle";
                tracker.animate(idleAnim != null ? idleAnim : "idle");
            } catch (Throwable t) {
                logThrottledError("stopAnimation", t);
            }
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

