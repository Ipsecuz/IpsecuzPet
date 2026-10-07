package org.ipsecuz.pet;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.ipsecuz.pet.model.ModelProvider;
import org.ipsecuz.pet.model.ModelProviderManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ModelHandler {

    private final IpsecuzPet plugin;
    private final ModelProviderManager manager;
    private final Map<UUID, PetAnimationState> currentStates = new ConcurrentHashMap<>();

    public ModelHandler(IpsecuzPet plugin) {
        this.plugin = plugin;
        this.manager = new ModelProviderManager(plugin);
    }

    public ModelProviderManager getManager() {
        return manager;
    }

    public boolean isBetterModelInstalled() {
        return manager.getBetterModelProvider().isAvailable();
    }

    public boolean isModelEngineInstalled() {
        return manager.getModelEngineProvider().isAvailable();
    }

    public void spawnModel(Player owner, Entity baseEntity, String petId) {
        manager.spawnModel(owner, baseEntity, petId);
    }

    public void spawnModel(Player owner, Entity baseEntity, String petId, String directModelId) {
        manager.spawnModel(owner, baseEntity, petId, directModelId);
    }

    public void updatePosition(Entity pet) {
        manager.updatePosition(pet);
    }

    public void updateMultiplayerVisibility(Entity pet) {
        manager.updateMultiplayerVisibility(pet);
    }

    public void handlePlayerQuit(Player player) {
        manager.handlePlayerQuit(player);
    }

    public void playAnimation(Entity pet, PetAnimationState state) {
        if (pet == null || state == null) return;
        currentStates.put(pet.getUniqueId(), state);
        manager.playAnimation(pet, state);
    }

    public void playTransientAnimation(Entity pet, PetAnimationState state, long durationTicks, PetAnimationState returnState) {
        if (pet == null || state == null) return;
        currentStates.put(pet.getUniqueId(), state);
        manager.playTransientAnimation(pet, state, durationTicks, returnState);
    }

    public PetAnimationState getCurrentState(UUID uuid) {
        return currentStates.getOrDefault(uuid, PetAnimationState.IDLE);
    }

    public void updateAnimation(Entity pet) {
        if (pet == null) return;
        boolean isMoving = pet.getVelocity().length() > 0.08;
        playAnimation(pet, isMoving ? PetAnimationState.WALK : PetAnimationState.IDLE);
    }

    public void removeModel(UUID baseEntityUuid) {
        currentStates.remove(baseEntityUuid);
        manager.removeModel(baseEntityUuid);
    }

    public void removeAll() {
        currentStates.clear();
        manager.removeAll();
    }

    public ModelProvider getActiveProviderForEntity(UUID entityUuid) {
        return manager.getActiveProviderForEntity(entityUuid);
    }
}