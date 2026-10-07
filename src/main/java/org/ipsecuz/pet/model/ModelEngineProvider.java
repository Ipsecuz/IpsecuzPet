package org.ipsecuz.pet.model;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.animation.handler.AnimationHandler;
import com.ticxo.modelengine.api.generator.blueprint.ModelBlueprint;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.ipsecuz.pet.IpsecuzPet;
import org.ipsecuz.pet.PetAnimationState;
import org.ipsecuz.pet.SchedulerUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ModelEngineProvider implements ModelProvider {

    private final IpsecuzPet plugin;
    private final Map<UUID, ModeledEntity> activeEntities = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveModel> activeModels = new ConcurrentHashMap<>();
    private final Map<UUID, PetAnimationState> currentStates = new ConcurrentHashMap<>();
    private final Map<UUID, Long> stateExpirationMs = new ConcurrentHashMap<>();
    private final Map<String, Long> lastErrorLogTime = new ConcurrentHashMap<>();
    private Boolean availableCache = null;

    public ModelEngineProvider(IpsecuzPet plugin) {
        this.plugin = plugin;
    }

    @Override
    public ModelType getType() {
        return ModelType.MODELENGINE;
    }

    @Override
    public boolean isAvailable() {
        if (availableCache == null) {
            try {
                availableCache = Bukkit.getPluginManager().isPluginEnabled("ModelEngine")
                        && Class.forName("com.ticxo.modelengine.api.ModelEngineAPI") != null;
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
            ModelBlueprint blueprint = ModelEngineAPI.getBlueprint(modelId);
            if (blueprint == null) {
                plugin.getLogger().warning("§c[ModelEngine] Không tìm thấy Blueprint Model ID: " + modelId);
                return false;
            }

            if (pet instanceof LivingEntity living) {
                living.setInvisible(true);
            }

            ModeledEntity modeledEntity = ModelEngineAPI.getOrCreateModeledEntity(pet);
            if (modeledEntity == null) {
                return false;
            }
            modeledEntity.setBaseEntityVisible(false);

            ActiveModel activeModel = ModelEngineAPI.createActiveModel(modelId);
            if (activeModel == null) {
                return false;
            }

            modeledEntity.addModel(activeModel, false);

            activeEntities.put(pet.getUniqueId(), modeledEntity);
            activeModels.put(pet.getUniqueId(), activeModel);

            playTransientAnimation(pet, PetAnimationState.SPAWN, 25L, PetAnimationState.IDLE);
            return true;
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
        activeModels.remove(entityUuid);
        activeEntities.remove(entityUuid);

        if (isAvailable()) {
            try {
                ModelEngineAPI.removeModeledEntity(entityUuid);
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void removeAll() {
        currentStates.clear();
        stateExpirationMs.clear();
        for (UUID uuid : activeEntities.keySet()) {
            if (isAvailable()) {
                try {
                    ModelEngineAPI.removeModeledEntity(uuid);
                } catch (Throwable ignored) {}
            }
        }
        activeModels.clear();
        activeEntities.clear();
    }

    @Override
    public void show(Entity pet, Player viewer) {
        // ModelEngine handles multiplayer visibility automatically through its internal packet renderer
    }

    @Override
    public void hide(Entity pet, Player viewer) {
        // ModelEngine handles multiplayer visibility automatically
    }

    @Override
    public void updatePosition(Entity pet) {
        // ModelEngine updates position automatically each tick via BaseEntity wrapper
    }

    @Override
    public void updateMultiplayerVisibility(Entity pet) {
        // ModelEngine handles client packet tracking internally
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
        executeAnimation(uuid, state, false);
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
        executeAnimation(uuid, state, true);

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
        ActiveModel model = activeModels.get(pet.getUniqueId());
        if (model != null && !model.isDestroyed()) {
            try {
                AnimationHandler handler = model.getAnimationHandler();
                if (handler != null) {
                    handler.forceStopAllAnimations();
                }
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void handlePlayerQuit(Player player) {
        // ModelEngine cleans up viewer trackers on player disconnect automatically
    }

    private void executeAnimation(UUID uuid, PetAnimationState state, boolean force) {
        ActiveModel model = activeModels.get(uuid);
        if (model == null || model.isDestroyed()) return;

        try {
            AnimationHandler handler = model.getAnimationHandler();
            if (handler == null) return;

            String animName = resolveModelEngineAnimationName(state);
            handler.playAnimation(animName, 0.25, 0.25, 1.0, force);
        } catch (Throwable t) {
            logThrottledError("executeAnimation", t);
        }
    }

    private String resolveModelEngineAnimationName(PetAnimationState state) {
        String configured = plugin.getConfig().getString("animations." + state.name().toLowerCase() + ".modelengine");
        if (configured != null && !configured.trim().isEmpty()) {
            return configured.trim();
        }

        return switch (state) {
            case IDLE -> "idle";
            case WALK -> "walk";
            case RUN -> "run";
            case FLY_IDLE -> "fly_idle";
            case FLY -> "fly";
            case ATTACK -> "attack";
            case HURT -> "hurt";
            case SPAWN -> "spawn";
            case FEED -> "eat";
            case HAPPY -> "happy";
            case SKILL_CHARGE -> "charge";
            case SKILL_CAST -> "cast";
            case EVOLVE -> "evolution";
            case CELEBRATE -> "celebrate";
            case LEVEL_UP -> "celebrate";
            case SAD -> "sad";
            case DEATH -> "death";
            default -> state.getPrimaryName();
        };
    }

    private void logThrottledError(String action, Throwable t) {
        long now = System.currentTimeMillis();
        Long last = lastErrorLogTime.get(action);
        if (last == null || (now - last) > 10000L) {
            lastErrorLogTime.put(action, now);
            plugin.getLogger().log(Level.WARNING, "§c[ModelEngineProvider] Lỗi trong (" + action + "): " + t.getMessage(), t);
        }
    }
}
