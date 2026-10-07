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
    private final Map<UUID, String> activeModelIds = new ConcurrentHashMap<>();
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

            ModeledEntity modeledEntity = ModelEngineAPI.getOrCreateModeledEntity(pet);
            if (modeledEntity == null) {
                return false;
            }

            ActiveModel activeModel = ModelEngineAPI.createActiveModel(modelId);
            if (activeModel == null) {
                return false;
            }

            modeledEntity.addModel(activeModel, false);

            activeEntities.put(pet.getUniqueId(), modeledEntity);
            activeModels.put(pet.getUniqueId(), activeModel);
            activeModelIds.put(pet.getUniqueId(), modelId);

            // Only hide base entity after model is confirmed attached
            modeledEntity.setBaseEntityVisible(false);
            if (pet instanceof LivingEntity living) {
                living.setInvisible(true);
            }
            return true;
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
        activeModels.remove(entityUuid);
        String modelId = activeModelIds.remove(entityUuid);
        ModeledEntity modeledEntity = activeEntities.remove(entityUuid);

        if (isAvailable() && modeledEntity != null) {
            try {
                if (modelId != null) {
                    modeledEntity.removeModel(modelId);
                }
                modeledEntity.setBaseEntityVisible(true);
                if (modeledEntity.getModels().isEmpty()) {
                    ModelEngineAPI.removeModeledEntity(entityUuid);
                }
            } catch (Throwable t) {
                logThrottledError("remove", t);
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
        for (Map.Entry<UUID, ModeledEntity> entry : activeEntities.entrySet()) {
            UUID uuid = entry.getKey();
            ModeledEntity me = entry.getValue();
            String mId = activeModelIds.get(uuid);
            if (isAvailable() && me != null) {
                try {
                    if (mId != null) {
                        me.removeModel(mId);
                    }
                    if (me.getModels().isEmpty()) {
                        ModelEngineAPI.removeModeledEntity(uuid);
                    }
                } catch (Throwable t) {
                    logThrottledError("removeAll", t);
                }
            }
        }
        activeModelIds.clear();
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
    public void renderRawAnimation(Entity pet, String animationName, PetAnimationState state) {
        if (!isAvailable() || pet == null || animationName == null) return;
        ActiveModel model = activeModels.get(pet.getUniqueId());
        if (model == null || model.isDestroyed()) return;

        try {
            AnimationHandler handler = model.getAnimationHandler();
            if (handler != null) {
                handler.playAnimation(animationName, 0.25, 0.25, 1.0, true);
            }
        } catch (Throwable t) {
            logThrottledError("renderRawAnimation", t);
        }
    }

    @Override
    public void playAnimation(Entity pet, PetAnimationState state) {
        if (pet == null || state == null) return;
        if (plugin.getModelProviderManager() != null && plugin.getModelProviderManager().getAnimationController() != null) {
            plugin.getModelProviderManager().getAnimationController().requestAnimation(pet, state);
        } else {
            String animName = (plugin.getModelProviderManager() != null) ?
                    plugin.getModelProviderManager().resolveAnimationName(null, ModelType.MODELENGINE, state) :
                    state.getPrimaryName();
            renderRawAnimation(pet, animName, state);
        }
    }

    @Override
    public void playTransientAnimation(Entity pet, PetAnimationState state, long durationTicks, PetAnimationState returnState) {
        if (pet == null || state == null) return;
        if (plugin.getModelProviderManager() != null && plugin.getModelProviderManager().getAnimationController() != null) {
            plugin.getModelProviderManager().getAnimationController().requestTransientAnimation(pet, state, durationTicks, returnState);
        } else {
            String animName = (plugin.getModelProviderManager() != null) ?
                    plugin.getModelProviderManager().resolveAnimationName(null, ModelType.MODELENGINE, state) :
                    state.getPrimaryName();
            renderRawAnimation(pet, animName, state);
        }
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
            } catch (Throwable t) {
                logThrottledError("stopAnimation", t);
            }
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

            String animName = (plugin.getModelProviderManager() != null) ?
                    plugin.getModelProviderManager().resolveAnimationName(null, ModelType.MODELENGINE, state) :
                    state.getPrimaryName();
            handler.playAnimation(animName, 0.25, 0.25, 1.0, force);
        } catch (Throwable t) {
            logThrottledError("executeAnimation", t);
        }
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
