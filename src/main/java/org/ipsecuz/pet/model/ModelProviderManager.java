package org.ipsecuz.pet.model;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.ipsecuz.pet.IpsecuzPet;
import org.ipsecuz.pet.PetAnimationState;
import org.ipsecuz.pet.SchedulerUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ModelProviderManager {

    private final IpsecuzPet plugin;
    private final BetterModelProvider betterModelProvider;
    private final ModelEngineProvider modelEngineProvider;
    private final NoneModelProvider noneModelProvider;
    private final PetAnimationController animationController;

    private final Map<UUID, ModelProvider> activeEntityProviders = new ConcurrentHashMap<>();
    private final Set<String> failedModelCache = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<String, Long> debugLogThrottle = new ConcurrentHashMap<>();

    public ModelProviderManager(IpsecuzPet plugin) {
        this.plugin = plugin;
        this.betterModelProvider = new BetterModelProvider(plugin);
        this.modelEngineProvider = new ModelEngineProvider(plugin);
        this.noneModelProvider = new NoneModelProvider();
        this.animationController = new PetAnimationController(plugin, this);
    }

    public PetAnimationController getAnimationController() {
        return animationController;
    }

    public ModelProvider getActiveProvider(UUID entityUuid) {
        return activeEntityProviders.get(entityUuid);
    }

    public BetterModelProvider getBetterModelProvider() {
        return betterModelProvider;
    }

    public ModelEngineProvider getModelEngineProvider() {
        return modelEngineProvider;
    }

    public NoneModelProvider getNoneModelProvider() {
        return noneModelProvider;
    }

    public ModelProvider getProvider(ModelType type) {
        return switch (type) {
            case BETTERMODEL -> betterModelProvider;
            case MODELENGINE -> modelEngineProvider;
            case NONE, AUTO -> noneModelProvider;
        };
    }

    public void reload() {
        betterModelProvider.invalidateAvailabilityCache();
        modelEngineProvider.invalidateAvailabilityCache();
        failedModelCache.clear();
    }

    public boolean isDebugEnabled() {
        return plugin.getConfig().getBoolean("model.debug", false);
    }

    private void logDebug(String message) {
        if (!isDebugEnabled()) return;
        long now = System.currentTimeMillis();
        Long last = debugLogThrottle.get(message);
        if (last == null || (now - last) > 5000L) {
            debugLogThrottle.put(message, now);
            plugin.getLogger().info("§8[§bModelDebug§8] §7" + message);
        }
    }

    /**
     * Xác định Provider chính thức áp dụng toàn cục nếu cấu hình là AUTO.
     */
    public ModelProvider resolveAutoProvider() {
        List<String> priority = plugin.getConfig().getStringList("model.auto_priority");
        if (priority.isEmpty()) {
            priority = List.of("BETTERMODEL", "MODELENGINE");
        }

        for (String p : priority) {
            ModelType type = ModelType.fromString(p);
            if (type == ModelType.BETTERMODEL && betterModelProvider.isAvailable()) {
                return betterModelProvider;
            } else if (type == ModelType.MODELENGINE && modelEngineProvider.isAvailable()) {
                return modelEngineProvider;
            }
        }
        return noneModelProvider;
    }

    /**
     * Xác định Provider cho một Pet cụ thể. Per-pet setting ghi đè Global setting.
     */
    public ModelProvider resolveProviderForPet(String petId) {
        FileConfiguration config = plugin.getConfig();
        String petProviderStr = config.getString("pets." + petId + ".model.provider");
        ModelType requestedType;

        if (petProviderStr != null && !petProviderStr.trim().isEmpty()) {
            requestedType = ModelType.fromString(petProviderStr);
        } else {
            String globalProviderStr = config.getString("model.provider", "AUTO");
            requestedType = ModelType.fromString(globalProviderStr);
        }

        if (requestedType == ModelType.AUTO) {
            return resolveAutoProvider();
        } else if (requestedType == ModelType.BETTERMODEL) {
            if (betterModelProvider.isAvailable()) return betterModelProvider;
        } else if (requestedType == ModelType.MODELENGINE) {
            if (modelEngineProvider.isAvailable()) return modelEngineProvider;
        } else if (requestedType == ModelType.NONE) {
            return noneModelProvider;
        }

        // Nếu provider yêu cầu không khả dụng, kiểm tra fallback
        if (config.getBoolean("model.fallback.enabled", true)) {
            String fallbackStr = config.getString("model.fallback.provider", "BETTERMODEL");
            ModelType fallbackType = ModelType.fromString(fallbackStr);
            if (fallbackType == ModelType.BETTERMODEL && betterModelProvider.isAvailable()) {
                return betterModelProvider;
            } else if (fallbackType == ModelType.MODELENGINE && modelEngineProvider.isAvailable()) {
                return modelEngineProvider;
            }
        }

        return noneModelProvider;
    }

    /**
     * Authoritative Model ID Resolver.
     * Priority:
     * 1. Pet-specific provider model configuration (adult / baby)
     * 2. Provider-specific general model id
     * 3. Legacy backward compatibility (model_id / model_id_baby)
     * 4. No model
     */
    public String resolveModelId(String petId, ModelType providerType, boolean isBaby) {
        if (petId == null) return null;
        FileConfiguration config = plugin.getConfig();
        String formKey = isBaby ? "baby" : "adult";

        if (providerType == ModelType.MODELENGINE) {
            // 1. pets.<id>.model.modelengine.baby / adult
            String meForm = config.getString("pets." + petId + ".model.modelengine." + formKey);
            if (meForm != null && !meForm.trim().isEmpty()) return meForm.trim();

            // 2. pets.<id>.model.modelengine.id
            String meId = config.getString("pets." + petId + ".model.modelengine.id");
            if (meId != null && !meId.trim().isEmpty()) return meId.trim();

            // 3. Fallback to general modelengine string if defined as key
            String meDirect = config.getString("pets." + petId + ".model.modelengine");
            if (meDirect != null && !meDirect.trim().isEmpty() && !config.isConfigurationSection("pets." + petId + ".model.modelengine")) {
                return meDirect.trim();
            }
        } else if (providerType == ModelType.BETTERMODEL) {
            // 1. pets.<id>.model.bettermodel.baby / adult
            String bmForm = config.getString("pets." + petId + ".model.bettermodel." + formKey);
            if (bmForm != null && !bmForm.trim().isEmpty()) return bmForm.trim();

            // 2. pets.<id>.model.bettermodel.id
            String bmId = config.getString("pets." + petId + ".model.bettermodel.id");
            if (bmId != null && !bmId.trim().isEmpty()) return bmId.trim();

            // 3. Fallback to general bettermodel string if defined as key
            String bmDirect = config.getString("pets." + petId + ".model.bettermodel");
            if (bmDirect != null && !bmDirect.trim().isEmpty() && !config.isConfigurationSection("pets." + petId + ".model.bettermodel")) {
                return bmDirect.trim();
            }
        }

        // 3. Backward compatibility: legacy model_id / model_id_baby
        // Only if provider is BetterModel or if no provider-specific config hijacked it
        if (providerType == ModelType.BETTERMODEL || (providerType != ModelType.MODELENGINE && !config.contains("pets." + petId + ".model.modelengine"))) {
            if (isBaby && config.contains("pets." + petId + ".model_id_baby")) {
                String babyLegacy = config.getString("pets." + petId + ".model_id_baby");
                if (babyLegacy != null && !babyLegacy.trim().isEmpty()) return babyLegacy.trim();
            }
            String legacyId = config.getString("pets." + petId + ".model_id");
            if (legacyId != null && !legacyId.trim().isEmpty()) {
                return legacyId.trim();
            }
        }

        return null;
    }

    public String resolveModelIdForProvider(String petId, ModelType providerType) {
        return resolveModelId(petId, providerType, false);
    }

    /**
     * Authoritative Animation Name Resolver.
     * Priority:
     * 1. Pet-specific mapping: pets.<id>.animations.<state>.<provider>
     * 2. Global provider mapping: animations.<state>.<provider>
     * 3. Fallback chain for the state
     * 4. Default animation name
     */
    public String resolveAnimationName(String petId, ModelType providerType, PetAnimationState state) {
        if (state == null) return "idle";
        FileConfiguration config = plugin.getConfig();
        String stateName = state.name().toLowerCase();
        String providerKey = (providerType == ModelType.MODELENGINE) ? "modelengine" : "bettermodel";

        // 1. Pet-specific mapping: pets.<petId>.animations.<state>.<provider>
        if (petId != null) {
            String petSpecific = config.getString("pets." + petId + ".animations." + stateName + "." + providerKey);
            if (petSpecific != null && !petSpecific.trim().isEmpty()) {
                return petSpecific.trim();
            }
        }

        // 2. Global provider-specific mapping: animations.<state>.<provider>
        String globalConfigured = config.getString("animations." + stateName + "." + providerKey);
        if (globalConfigured != null && !globalConfigured.trim().isEmpty()) {
            return globalConfigured.trim();
        }

        // 3. Fallback chain: test fallbacks
        for (String fb : state.getFallbacks()) {
            if (petId != null) {
                String fbPet = config.getString("pets." + petId + ".animations." + fb + "." + providerKey);
                if (fbPet != null && !fbPet.trim().isEmpty()) return fbPet.trim();
            }
            String fbGlobal = config.getString("animations." + fb + "." + providerKey);
            if (fbGlobal != null && !fbGlobal.trim().isEmpty()) return fbGlobal.trim();
        }

        // 4. Default provider animation names
        if (providerType == ModelType.MODELENGINE) {
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
                case CELEBRATE, LEVEL_UP -> "celebrate";
                case SAD -> "sad";
                case DEATH -> "death";
                default -> state.getPrimaryName();
            };
        }

        return state.getPrimaryName();
    }

    /**
     * Khởi tạo và gắn Model cho thú cưng với kiến trúc Đa Engine và Fallback an toàn.
     */
    public boolean spawnModel(Player owner, Entity pet, String petId) {
        return spawnModel(owner, pet, petId, null, false);
    }

    public boolean spawnModel(Player owner, Entity pet, String petId, boolean isBaby) {
        return spawnModel(owner, pet, petId, null, isBaby);
    }

    public boolean spawnModel(Player owner, Entity pet, String petId, String directModelId) {
        return spawnModel(owner, pet, petId, directModelId, false);
    }

    public boolean spawnModel(Player owner, Entity pet, String petId, String directModelId, boolean isBaby) {
        if (pet == null) return false;
        UUID uuid = pet.getUniqueId();

        // Xóa model cũ nếu đang tồn tại
        removeModel(uuid);

        ModelProvider primary = resolveProviderForPet(petId);
        String modelId = directModelId != null ? directModelId : resolveModelId(petId, primary.getType(), isBaby);

        logDebug("Spawning pet [" + petId + "] with provider [" + primary.getType() + "] and model [" + modelId + "] (baby=" + isBaby + ")");

        if (primary == noneModelProvider || modelId == null || modelId.trim().isEmpty()) {
            noneModelProvider.spawn(owner, pet, null, petId);
            activeEntityProviders.put(uuid, noneModelProvider);
            animationController.registerPet(uuid, petId);
            return true;
        }

        String cacheKey = primary.getType() + ":" + modelId;
        if (failedModelCache.contains(cacheKey)) {
            logDebug("Model [" + cacheKey + "] is marked as failed, skipping to fallback/none.");
            noneModelProvider.spawn(owner, pet, null, petId);
            activeEntityProviders.put(uuid, noneModelProvider);
            animationController.registerPet(uuid, petId);
            return false;
        }

        boolean success = primary.spawn(owner, pet, modelId, petId);
        if (success) {
            activeEntityProviders.put(uuid, primary);
            animationController.registerPet(uuid, petId);
            logDebug("Successfully spawned model [" + modelId + "] via [" + primary.getType() + "]");
            return true;
        }

        // Nếu Provider chính thất bại, thử Fallback Provider
        FileConfiguration config = plugin.getConfig();
        if (config.getBoolean("model.fallback.enabled", true)) {
            String fallbackStr = config.getString("model.fallback.provider", "BETTERMODEL");
            ModelType fallbackType = ModelType.fromString(fallbackStr);
            ModelProvider fallbackProvider = (fallbackType == ModelType.BETTERMODEL) ? betterModelProvider : modelEngineProvider;

            if (fallbackProvider != primary && fallbackProvider.isAvailable()) {
                String fallbackModelId = resolveModelId(petId, fallbackProvider.getType(), isBaby);
                if (fallbackModelId != null && !fallbackModelId.trim().isEmpty()) {
                    logDebug("Attempting fallback provider [" + fallbackProvider.getType() + "] with model [" + fallbackModelId + "]");
                    boolean fallbackSuccess = fallbackProvider.spawn(owner, pet, fallbackModelId, petId);
                    if (fallbackSuccess) {
                        activeEntityProviders.put(uuid, fallbackProvider);
                        animationController.registerPet(uuid, petId);
                        return true;
                    }
                }
            }
        }

        // Đánh dấu cache để tránh lặp lại mỗi tick
        failedModelCache.add(cacheKey);
        logDebug("All custom model spawn attempts failed for pet [" + petId + "]. Falling back to vanilla entity.");
        noneModelProvider.spawn(owner, pet, null, petId);
        activeEntityProviders.put(uuid, noneModelProvider);
        animationController.registerPet(uuid, petId);
        return false;
    }

    public void removeModel(Entity pet) {
        if (pet != null) {
            removeModel(pet.getUniqueId());
            if (pet instanceof LivingEntity living && pet.isValid()) {
                living.setInvisible(false);
            }
        }
    }

    public void removeModel(UUID entityUuid) {
        if (entityUuid == null) return;
        animationController.unregisterPet(entityUuid);
        ModelProvider provider = activeEntityProviders.remove(entityUuid);
        if (provider != null) {
            provider.remove(entityUuid);
        } else {
            betterModelProvider.remove(entityUuid);
            modelEngineProvider.remove(entityUuid);
        }
    }

    public void removeAll() {
        animationController.clear();
        activeEntityProviders.clear();
        betterModelProvider.removeAll();
        modelEngineProvider.removeAll();
        noneModelProvider.removeAll();
    }

    public void updatePosition(Entity pet) {
        if (pet == null) return;
        ModelProvider provider = activeEntityProviders.get(pet.getUniqueId());
        if (provider != null) {
            provider.updatePosition(pet);
        }
    }

    public void updateMultiplayerVisibility(Entity pet) {
        if (pet == null) return;
        ModelProvider provider = activeEntityProviders.get(pet.getUniqueId());
        if (provider != null) {
            provider.updateMultiplayerVisibility(pet);
        }
    }

    public void playAnimation(Entity pet, PetAnimationState state) {
        if (pet == null || state == null) return;
        animationController.requestAnimation(pet, state);
    }

    public void playTransientAnimation(Entity pet, PetAnimationState state, long durationTicks, PetAnimationState returnState) {
        if (pet == null || state == null) return;
        animationController.requestTransientAnimation(pet, state, durationTicks, returnState);
    }

    public void stopAnimation(Entity pet) {
        if (pet == null) return;
        animationController.stopAnimation(pet);
    }

    public void handlePlayerQuit(Player player) {
        betterModelProvider.handlePlayerQuit(player);
        modelEngineProvider.handlePlayerQuit(player);
    }

    public ModelProvider getActiveProviderForEntity(UUID entityUuid) {
        return activeEntityProviders.getOrDefault(entityUuid, noneModelProvider);
    }

    public String getActiveProviderName() {
        String global = plugin.getConfig().getString("model.provider", "AUTO");
        if ("AUTO".equalsIgnoreCase(global)) {
            ModelProvider auto = resolveAutoProvider();
            return "AUTO (" + auto.getType().name() + ")";
        }
        return global;
    }

    public String getAvailableRenderersSummary() {
        boolean bm = betterModelProvider.isAvailable();
        boolean me = modelEngineProvider.isAvailable();
        if (bm && me) return "Both";
        if (bm) return "BetterModel";
        if (me) return "ModelEngine";
        return "None";
    }
}

