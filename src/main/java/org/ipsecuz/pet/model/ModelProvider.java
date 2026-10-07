package org.ipsecuz.pet.model;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.ipsecuz.pet.PetAnimationState;

import java.util.UUID;

public interface ModelProvider {

    ModelType getType();

    boolean isAvailable();

    boolean spawn(Player owner, Entity pet, String modelId, String petId);

    void remove(Entity pet);

    void remove(UUID entityUuid);

    void removeAll();

    void show(Entity pet, Player viewer);

    void hide(Entity pet, Player viewer);

    void updatePosition(Entity pet);

    void updateMultiplayerVisibility(Entity pet);

    void playAnimation(Entity pet, PetAnimationState state);

    void playTransientAnimation(Entity pet, PetAnimationState state, long durationTicks, PetAnimationState returnState);

    void renderRawAnimation(Entity pet, String animationName, PetAnimationState state);

    void stopAnimation(Entity pet);

    void handlePlayerQuit(Player player);
}

