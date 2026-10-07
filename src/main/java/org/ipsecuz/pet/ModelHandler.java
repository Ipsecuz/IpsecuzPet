package org.ipsecuz.pet;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.data.renderer.ModelRenderer;
import kr.toxicity.model.api.tracker.EntityTracker;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ModelHandler {

    private final IpsecuzPet plugin;
    private final Map<UUID, EntityTracker> activeTrackers = new ConcurrentHashMap<>();
    private Boolean betterModelAvailable = null;

    public ModelHandler(IpsecuzPet plugin) {
        this.plugin = plugin;
    }

    public boolean isBetterModelInstalled() {
        if (betterModelAvailable == null) {
            betterModelAvailable = Bukkit.getPluginManager().isPluginEnabled("BetterModel");
        }
        return betterModelAvailable;
    }

    public void spawnModel(Player owner, Entity baseEntity, String modelId) {
        if (modelId == null || modelId.trim().isEmpty()) {
            if (baseEntity instanceof LivingEntity living) {
                living.setInvisible(false);
            }
            return;
        }

        if (!isBetterModelInstalled()) {
            if (baseEntity instanceof LivingEntity living) {
                living.setInvisible(false);
            }
            return;
        }

        try {
            ModelRenderer renderer = BetterModel.modelOrNull(modelId);
            if (renderer == null) {
                if (baseEntity instanceof LivingEntity living) {
                    living.setInvisible(false);
                }
                plugin.getLogger().warning("§c[IpsecuzPet] Không tìm thấy Model ID: " + modelId +
                        ". Các Model hiện có: " + BetterModel.modelKeys());
                return;
            }

            EntityTracker tracker = renderer.create(baseEntity);
            if (tracker != null) {
                activeTrackers.put(baseEntity.getUniqueId(), tracker);
                try {
                    tracker.show(owner);
                } catch (Exception ex) {
                    try {
                        tracker.animate("idle");
                    } catch (Exception ignored) {}
                }
            } else {
                if (baseEntity instanceof LivingEntity living) {
                    living.setInvisible(false);
                }
            }
        } catch (Throwable t) {
            if (baseEntity instanceof LivingEntity living) {
                living.setInvisible(false);
            }
            plugin.getLogger().log(Level.WARNING, "§c[IpsecuzPet] Không thể spawn BetterModel cho pet: " + modelId, t);
        }
    }

    public void updatePosition(Entity pet) {
        // EntityTracker được gắn trực tiếp vào Bukkit Entity nên tự động theo dõi vị trí qua NMS packets.
    }

    public void updateAnimation(Entity pet) {
        if (!isBetterModelInstalled()) return;
        EntityTracker tracker = activeTrackers.get(pet.getUniqueId());
        if (tracker == null || tracker.isClosed()) return;

        try {
            boolean isMoving = pet.getVelocity().length() > 0.08;
            String animName = isMoving ? "walk" : "idle";
            tracker.animate(animName);
        } catch (Throwable ignored) {}
    }

    public void removeModel(UUID baseEntityUuid) {
        if (!isBetterModelInstalled()) return;
        EntityTracker tracker = activeTrackers.remove(baseEntityUuid);
        if (tracker != null && !tracker.isClosed()) {
            try {
                tracker.close();
            } catch (Throwable ignored) {}
        }
    }

    public void removeAll() {
        if (!isBetterModelInstalled()) return;
        for (EntityTracker tracker : activeTrackers.values()) {
            if (tracker != null && !tracker.isClosed()) {
                try {
                    tracker.close();
                } catch (Throwable ignored) {}
            }
        }
        activeTrackers.clear();
    }
}