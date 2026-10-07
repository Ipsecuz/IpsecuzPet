package org.ipsecuz.pet.model;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.ipsecuz.pet.PetAnimationState;

import java.util.UUID;

/**
 * Placeholder model provider used when a requested provider is not installed or available on the server.
 * Ensures IpsecuzPet starts and operates safely without crashing on missing optional dependencies.
 */
public class UnavailableModelProvider implements ModelProvider {

    private final ModelType type;

    public UnavailableModelProvider(ModelType type) {
        this.type = type != null ? type : ModelType.NONE;
    }

    @Override
    public ModelType getType() {
        return type;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public boolean spawn(Player owner, Entity pet, String modelId, String petId) {
        return false;
    }

    @Override
    public void remove(Entity pet) {}

    @Override
    public void remove(UUID entityUuid) {}

    @Override
    public void removeAll() {}

    @Override
    public void show(Entity pet, Player viewer) {}

    @Override
    public void hide(Entity pet, Player viewer) {}

    @Override
    public void updatePosition(Entity pet) {}

    @Override
    public void updateMultiplayerVisibility(Entity pet) {}

    @Override
    public void playAnimation(Entity pet, PetAnimationState state) {}

    @Override
    public void playTransientAnimation(Entity pet, PetAnimationState state, long durationTicks, PetAnimationState returnState) {}

    @Override
    public void renderRawAnimation(Entity pet, String animationName, PetAnimationState state) {}

    @Override
    public void stopAnimation(Entity pet) {}

    @Override
    public void handlePlayerQuit(Player player) {}
}
