package org.ipsecuz.pet.model;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.ipsecuz.pet.PetAnimationState;

import java.util.UUID;

public class NoneModelProvider implements ModelProvider {

    @Override
    public ModelType getType() {
        return ModelType.NONE;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean spawn(Player owner, Entity pet, String modelId, String petId) {
        if (pet instanceof LivingEntity living) {
            living.setInvisible(false);
        }
        return true;
    }

    @Override
    public void remove(Entity pet) {
        if (pet instanceof LivingEntity living && pet.isValid()) {
            living.setInvisible(false);
        }
    }

    @Override
    public void remove(UUID entityUuid) {
        // Vanilla entity cleanup is handled by Bukkit entity removal
    }

    @Override
    public void removeAll() {
        // No-op for vanilla
    }

    @Override
    public void show(Entity pet, Player viewer) {
        // No-op for vanilla
    }

    @Override
    public void hide(Entity pet, Player viewer) {
        // No-op for vanilla
    }

    @Override
    public void updatePosition(Entity pet) {
        // No-op for vanilla
    }

    @Override
    public void updateMultiplayerVisibility(Entity pet) {
        // No-op for vanilla
    }

    @Override
    public void playAnimation(Entity pet, PetAnimationState state) {
        // No-op for vanilla
    }

    @Override
    public void playTransientAnimation(Entity pet, PetAnimationState state, long durationTicks, PetAnimationState returnState) {
        // No-op for vanilla
    }

    @Override
    public void renderRawAnimation(Entity pet, String animationName, PetAnimationState state) {
        // No-op for vanilla
    }

    @Override
    public void stopAnimation(Entity pet) {
        // No-op for vanilla
    }

    @Override
    public void handlePlayerQuit(Player player) {
        // No-op for vanilla
    }
}

