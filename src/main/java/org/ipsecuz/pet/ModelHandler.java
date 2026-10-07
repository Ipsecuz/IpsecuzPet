package org.ipsecuz.pet;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.data.renderer.ModelRenderer;
import kr.toxicity.model.api.tracker.EntityTracker;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ModelHandler {

    private final IpsecuzPet plugin;
    private final Map<UUID, EntityTracker> activeTrackers = new ConcurrentHashMap<>();
    private final Map<UUID, PetAnimationState> currentStates = new ConcurrentHashMap<>();
    private final Map<UUID, Long> stateExpirationMs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastVisibilityCheck = new ConcurrentHashMap<>();
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
                    // Fallback to idle
                }
                playTransientAnimation(baseEntity, PetAnimationState.SPAWN, 25L, PetAnimationState.IDLE);
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

    private final Map<String, Long> lastErrorLogTime = new ConcurrentHashMap<>();

    private void logThrottledError(String action, Throwable t) {
        long now = System.currentTimeMillis();
        Long last = lastErrorLogTime.get(action);
        if (last == null || (now - last) > 10000L) {
            lastErrorLogTime.put(action, now);
            plugin.getLogger().log(Level.WARNING, "§c[IpsecuzPet] Lỗi trong ModelHandler (" + action + "): " + t.getMessage(), t);
        }
    }

    /**
     * Theo dõi tầm nhìn đa người chơi (Multiplayer Visibility Tracking):
     * Tự động hiển thị model cho người chơi trong phạm vi 48 blocks và ẩn khi ra xa.
     * Throttled: kiểm tra tối đa 2 lần/giây cho mỗi thú cưng để tối ưu hiệu năng.
     */
    public void updateMultiplayerVisibility(Entity pet) {
        if (!isBetterModelInstalled() || pet == null) return;
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
            org.bukkit.Location petLoc = pet.getLocation();
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

    public void handlePlayerQuit(Player player) {
        if (!isBetterModelInstalled() || player == null) return;
        for (EntityTracker tracker : activeTrackers.values()) {
            if (tracker != null && !tracker.isClosed()) {
                try {
                    tracker.remove(player);
                } catch (Throwable ignored) {}
            }
        }
    }

    /**
     * Chuyển trạng thái hoạt ảnh bền vững (Locomotion, Idle, v.v.).
     * Không khởi động lại hoạt ảnh nếu trạng thái không đổi, và không ghi đè hoạt ảnh tạm thời (transient) có độ ưu tiên cao hơn.
     */
    public void playAnimation(Entity pet, PetAnimationState state) {
        if (!isBetterModelInstalled() || pet == null || state == null) return;
        UUID uuid = pet.getUniqueId();

        PetAnimationState current = currentStates.get(uuid);
        Long expire = stateExpirationMs.get(uuid);
        if (expire != null && System.currentTimeMillis() < expire) {
            if (current != null && current.getPriority() > state.getPriority()) {
                return; // Đang chạy animation tạm thời ưu tiên cao hơn (evolve, skill, hurt,...)
            }
        }

        if (current == state) {
            return; // Tránh restart animation liên tục mỗi tick!
        }

        currentStates.put(uuid, state);
        executeAnimation(uuid, state);
    }

    /**
     * Kích hoạt hoạt ảnh tạm thời với thời lượng xác định (Ticks), sau đó tự động chuyển về returnState.
     * Tôn trọng thứ tự ưu tiên (Priority).
     */
    public void playTransientAnimation(Entity pet, PetAnimationState state, long durationTicks, PetAnimationState returnState) {
        if (!isBetterModelInstalled() || pet == null || state == null) return;
        UUID uuid = pet.getUniqueId();

        PetAnimationState current = currentStates.get(uuid);
        Long expire = stateExpirationMs.get(uuid);
        if (expire != null && System.currentTimeMillis() < expire) {
            if (current != null && current.getPriority() > state.getPriority()) {
                return; // Không ghi đè hoạt ảnh có mức ưu tiên cao hơn
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

    public PetAnimationState getCurrentState(UUID uuid) {
        return currentStates.getOrDefault(uuid, PetAnimationState.IDLE);
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

    public void updateAnimation(Entity pet) {
        if (pet == null) return;
        boolean isMoving = pet.getVelocity().length() > 0.08;
        playAnimation(pet, isMoving ? PetAnimationState.WALK : PetAnimationState.IDLE);
    }

    public void removeModel(UUID baseEntityUuid) {
        if (!isBetterModelInstalled()) return;
        currentStates.remove(baseEntityUuid);
        stateExpirationMs.remove(baseEntityUuid);
        lastVisibilityCheck.remove(baseEntityUuid);
        EntityTracker tracker = activeTrackers.remove(baseEntityUuid);
        if (tracker != null && !tracker.isClosed()) {
            try {
                tracker.close();
            } catch (Throwable ignored) {}
        }
    }

    public void removeAll() {
        if (!isBetterModelInstalled()) return;
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
}