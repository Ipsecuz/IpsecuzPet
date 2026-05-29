package org.ipsecuz.pet;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ModelHandler {

    private final IpsecuzPet plugin;
    private final Map<UUID, Object> activeModels = new ConcurrentHashMap<>();
    private static Object cachedModelManager = null;

    public ModelHandler(IpsecuzPet plugin) {
        this.plugin = plugin;
    }

    public void spawnModel(Player owner, Entity baseEntity, String modelId) {
        try {
            Object modelManager = getModelManagerSmart();
            if (modelManager == null) {
                // Giữ lại: Lỗi nghiêm trọng, cần biết
                plugin.getLogger().warning(plugin.getLanguage().getMessage("model_handler.manager_not_found"));
                return;
            }

            Object model = null;
            try {
                Method modelMethod = modelManager.getClass().getMethod("model", String.class);
                model = modelMethod.invoke(modelManager, modelId);
            } catch (Exception e) {
                // Giữ lại: Lỗi nghiêm trọng, cần biết
                String errorMsg = plugin.getLanguage().getMessage("model_handler.error_calling_model_method", "%model_id%", modelId);
                plugin.getLogger().log(Level.SEVERE, errorMsg, e);
                return;
            }

            if (model == null) {
                // Giữ lại: Cảnh báo quan trọng, cần biết để sửa config
                plugin.getLogger().warning(plugin.getLanguage().getMessage("model_handler.model_id_not_found", "%model_id%", modelId));
                try {
                    Method keysMethod = modelManager.getClass().getMethod("modelKeys");
                    Object keys = keysMethod.invoke(modelManager);
                    plugin.getLogger().warning(plugin.getLanguage().getMessage("model_handler.available_model_ids", "%keys%", String.valueOf(keys)));
                } catch(Exception ex) {
                    String errorMsg = plugin.getLanguage().getMessage("model_handler.error_getting_model_keys");
                    plugin.getLogger().log(Level.WARNING, errorMsg, ex);
                }
                return;
            }

            // --- LOGIC MỚI: TẠO VÀ SPAWN MODEL ---
            Object modelInstance = null;
            try {
                Method createMethod = model.getClass().getMethod("create", Location.class);
                modelInstance = createMethod.invoke(model, baseEntity.getLocation());

                if (modelInstance != null) {
                    activeModels.put(baseEntity.getUniqueId(), modelInstance);
                    // Ẩn đi: Thông báo thành công, không cần thiết khi đã hoạt động
                    // plugin.getLogger().info(plugin.getLanguage().getMessage("model_handler.model_spawned_unbound", "%model_id%", modelId));

                    // --- QUAN TRỌNG: SPAWN MODEL CHO CHỦ NHÂN ---
                    try {
                        Method spawnMethod = modelInstance.getClass().getMethod("spawn", Player.class);
                        spawnMethod.invoke(modelInstance, owner);
                        // Ẩn đi: Thông báo thành công, không cần thiết khi đã hoạt động
                        // plugin.getLogger().info("Đã spawn thành công model '" + modelId + "' cho " + owner.getName());
                    } catch (NoSuchMethodException e) {
                        // Ẩn đi: Fallback không cần thiết vì spawn() đã thành công
                        // plugin.getLogger().warning("Không tìm thấy phương thức spawn(Player). Thử phương thức show(Player)...");
                        try {
                            Method showMethod = modelInstance.getClass().getMethod("show", Player.class);
                            showMethod.invoke(modelInstance, owner);
                            plugin.getLogger().info("Đã hiển thị thành công model '" + modelId + "' cho " + owner.getName() + " bằng show().");
                        } catch (Exception ex) {
                            plugin.getLogger().log(Level.SEVERE, "Lỗi khi gọi phương thức show(Player)", ex);
                        }
                    } catch (Exception e) {
                        // Giữ lại: Lỗi nghiêm trọng, cần biết
                        plugin.getLogger().log(Level.SEVERE, "Lỗi khi gọi spawn(Player) cho model: " + modelId, e);
                    }
                    // ---------------------------------------------------
                } else {
                    // Giữ lại: Cảnh báo quan trọng
                    plugin.getLogger().warning(plugin.getLanguage().getMessage("model_handler.model_returned_null", "%model_id%", modelId));
                }
            } catch (Exception e2) {
                // Giữ lại: Lỗi nghiêm trọng, cần biết
                String errorMsg = plugin.getLanguage().getMessage("model_handler.error_calling_create_loc");
                plugin.getLogger().log(Level.SEVERE, errorMsg, e2);
            }
            // --- KẾT THÚC LOGIC MỚI ---
        } catch (Exception e) {
            // Giữ lại: Lỗi nghiêm trọng, cần biết
            String errorMsg = plugin.getLanguage().getMessage("model_handler.error_spawning_model", "%model_id%", modelId);
            plugin.getLogger().log(Level.SEVERE, errorMsg, e);
        }
    }

    public void updatePosition(Entity pet) {
        if (!activeModels.containsKey(pet.getUniqueId())) return;
        Object modelInstance = activeModels.get(pet.getUniqueId());
        if (modelInstance == null) return;

        try {
            Location modelLoc = pet.getLocation().add(0, pet.getHeight() / 2, 0);
            Method locationMethod = modelInstance.getClass().getMethod("location", Location.class);
            locationMethod.invoke(modelInstance, modelLoc);
        } catch (Exception e) {
            // Giữ lại: Cảnh báo quan trọng, có thể là lỗi hiệu năng
            plugin.getLogger().log(Level.WARNING, "Lỗi khi cập nhật vị trí model cho pet: " + pet.getUniqueId(), e);
        }
    }

    public void updateAnimation(Entity pet) {
        if (!(pet instanceof LivingEntity)) return;

        if (!activeModels.containsKey(pet.getUniqueId())) return;
        Object modelInstance = activeModels.get(pet.getUniqueId());
        if (modelInstance == null) return;

        boolean isMoving = pet.getVelocity().length() > 0.1;
        String animName = isMoving ? "walk" : "idle";

        try {
            Method animateMethod = modelInstance.getClass().getMethod("animate", String.class);
            animateMethod.invoke(modelInstance, animName);
        } catch (NoSuchMethodException e1) {
            try {
                Method playMethod = modelInstance.getClass().getMethod("playAnimation", String.class);
                playMethod.invoke(modelInstance, animName);
            } catch (NoSuchMethodException e2) {
                try {
                    Method playMethod = modelInstance.getClass().getMethod("play", String.class);
                    playMethod.invoke(modelInstance, animName);
                } catch (Exception ignored) {}
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    public void removeModel(UUID baseEntityUuid) {
        if (activeModels.containsKey(baseEntityUuid)) {
            Object modelInstance = activeModels.get(baseEntityUuid);
            if (modelInstance != null) {
                try {
                    Method despawnMethod = modelInstance.getClass().getMethod("despawn");
                    despawnMethod.invoke(modelInstance);
                    // Ẩn đi: Thông báo thành công, không cần thiết khi đã hoạt động
                    // plugin.getLogger().info("Đã despawn model thành công.");
                } catch (NoSuchMethodException e) {
                    try {
                        Method closeMethod = modelInstance.getClass().getMethod("close");
                        closeMethod.invoke(modelInstance);
                        // Ẩn đi: Thông báo thành công, không cần thiết khi đã hoạt động
                        // plugin.getLogger().info("Đã xóa model thành công bằng close().");
                    } catch (Exception ex) {
                        // Giữ lại: Cảnh báo quan trọng
                        String errorMsg = plugin.getLanguage().getMessage("model_handler.error_calling_remove_method", "%method_name%", "close()");
                        plugin.getLogger().log(Level.WARNING, errorMsg, ex);
                    }
                } catch (Exception e) {
                    // Giữ lại: Cảnh báo quan trọng
                    String errorMsg = plugin.getLanguage().getMessage("model_handler.error_calling_remove_method", "%method_name%", "despawn()");
                    plugin.getLogger().log(Level.WARNING, errorMsg, e);
                }
            }
            activeModels.remove(baseEntityUuid);
        }
    }

    public void removeAll() {
        for (UUID uuid : new ArrayList<>(activeModels.keySet())) {
            removeModel(uuid);
        }
        activeModels.clear();
        // Ẩn đi: Thông báo thành công, không cần thiết khi đã hoạt động
        // plugin.getLogger().info(plugin.getLanguage().getMessage("model_handler.all_models_removed"));
    }

    private Object getModelManagerSmart() {
        if (cachedModelManager != null) return cachedModelManager;
        Plugin bmPlugin = Bukkit.getPluginManager().getPlugin("BetterModel");
        if (bmPlugin == null) {
            // Giữ lại: Lỗi nghiêm trọng, cần biết
            plugin.getLogger().warning(plugin.getLanguage().getMessage("model_handler.plugin_not_found"));
            return null;
        }

        Object instance = bmPlugin;
        try {
            Method inst = bmPlugin.getClass().getMethod("inst");
            instance = inst.invoke(null);
        } catch (Exception ignored) {
            // Ẩn đi: Thông báo không quan trọng, chỉ là fallback
            // plugin.getLogger().info(plugin.getLanguage().getMessage("model_handler.no_inst_method"));
        }

        for (Method m : instance.getClass().getMethods()) {
            if (m.getName().toLowerCase().contains("manager") && m.getParameterCount() == 0) {
                try {
                    Object res = m.invoke(instance);
                    if (res != null && res.getClass().getName().contains("ModelManager")) {
                        cachedModelManager = res;
                        // Ẩn đi: Thông báo thành công, không cần thiết khi đã hoạt động
                        // plugin.getLogger().info(plugin.getLanguage().getMessage("model_handler.manager_found"));
                        return res;
                    }
                } catch (Exception ignored) {}
            }
        }
        // Giữ lại: Cảnh báo quan trọng
        plugin.getLogger().warning(plugin.getLanguage().getMessage("model_handler.model_manager_not_found"));
        return null;
    }
}