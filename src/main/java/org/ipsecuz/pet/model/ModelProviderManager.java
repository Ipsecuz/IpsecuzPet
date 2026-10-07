package org.ipsecuz.pet.model;

import org.bukkit.Bukkit;
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

    public static class FailedModelEntry {
        private final ModelType provider;
        private final String modelId;
        private final long timestamp;
        private final String reason;

        public FailedModelEntry(ModelType provider, String modelId, String reason) {
            this.provider = provider;
            this.modelId = modelId;
            this.timestamp = System.currentTimeMillis();
            this.reason = reason;
        }

        public boolean isExpired(long ttlMillis) {
            return (System.currentTimeMillis() - timestamp) > ttlMillis;
        }

        public ModelType getProvider() { return provider; }
        public String getModelId() { return modelId; }
        public long getTimestamp() { return timestamp; }
        public String getReason() { return reason; }
    }

    private static final long FAILED_CACHE_TTL_MS = 60_000L;
    private static final int MAX_FAILED_CACHE_SIZE = 500;

    private final IpsecuzPet plugin;
    private ModelProvider betterModelProvider;
    private ModelProvider modelEngineProvider;
    private final NoneModelProvider noneModelProvider;
    private final PetAnimationController animationController;

    private final Map<UUID, ModelProvider> activeEntityProviders = new ConcurrentHashMap<>();
    private final Map<String, FailedModelEntry> failedModelCache = new ConcurrentHashMap<>();
    private final Map<String, Long> debugLogThrottle = new ConcurrentHashMap<>();

    public ModelProviderManager(IpsecuzPet plugin) {
        this.plugin = plugin;
        this.betterModelProvider = initBetterModelProvider(plugin);
        this.modelEngineProvider = initModelEngineProvider(plugin);
        this.noneModelProvider = new NoneModelProvider();
        this.animationController = new PetAnimationController(plugin, this);
    }

    private ModelProvider initBetterModelProvider(IpsecuzPet plugin) {
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("BetterModel") &&
                    Class.forName("kr.toxicity.model.api.BetterModel") != null) {
                Class<?> clazz = Class.forName("org.ipsecuz.pet.model.BetterModelProvider");
                return (ModelProvider) clazz.getConstructor(IpsecuzPet.class).newInstance(plugin);
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "BetterModel API unavailable: " + t.getMessage());
        }
        return new UnavailableModelProvider(ModelType.BETTERMODEL);
    }

    private ModelProvider initModelEngineProvider(IpsecuzPet plugin) {
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("ModelEngine") &&
                    Class.forName("com.ticxo.modelengine.api.ModelEngineAPI") != null) {
                Class<?> clazz = Class.forName("org.ipsecuz.pet.model.ModelEngineProvider");
                return (ModelProvider) clazz.getConstructor(IpsecuzPet.class).newInstance(plugin);
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "ModelEngine API unavailable: " + t.getMessage());
        }
        return new UnavailableModelProvider(ModelType.MODELENGINE);
    }

    public PetAnimationController getAnimationController() {
        return animationController;
    }

    public ModelProvider getActiveProvider(UUID entityUuid) {
        return activeEntityProviders.get(entityUuid);
    }

    public ModelProvider getBetterModelProvider() {
        return betterModelProvider;
    }

    public ModelProvider getModelEngineProvider() {
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
        if (this.betterModelProvider == null || this.betterModelProvider instanceof UnavailableModelProvider) {
            this.betterModelProvider = initBetterModelProvider(plugin);
        }
        if (this.modelEngineProvider == null || this.modelEngineProvider instanceof UnavailableModelProvider) {
            this.modelEngineProvider = initModelEngineProvider(plugin);
        }
        this.failedModelCache.clear();
        this.debugLogThrottle.clear();
        rebindActivePets();
    }

    /**
     * Tái liên kết toàn bộ model cho các pet đang hoạt động sau khi cấu hình được tải lại (Rebind on Reload).
     * Đảm bảo không để lại model rác hay renderer cũ bị bỏ rơi.
     */
    public void rebindActivePets() {
        if (plugin.getPetManager() == null) return;
        Map<UUID, Entity> activePets = plugin.getPetManager().getActivePets();
        Map<UUID, String> activePetIds = plugin.getPetManager().getActivePetIds();
        if (activePets == null || activePets.isEmpty()) return;

        for (Map.Entry<UUID, Entity> entry : activePets.entrySet()) {
            UUID ownerId = entry.getKey();
            Entity pet = entry.getValue();
            String petId = (activePetIds != null) ? activePetIds.get(ownerId) : null;
            if (pet == null || petId == null) continue;

            SchedulerUtils.runEntityTask(plugin, pet, () -> {
                if (!pet.isValid()) return;
                Player owner = Bukkit.getPlayer(ownerId);
                boolean isBaby = plugin.getConfigManager().isPetBaby(ownerId, petId);

                // 1. Dọn dẹp model cũ khỏi provider trước đó
                removeModel(pet);

                // 2. Tái phân giải và gắn model mới theo cấu hình cập nhật
                spawnModel(owner, pet, petId, isBaby);

                // 3. Phục hồi hoạt ảnh và tầm nhìn
                playAnimation(pet, PetAnimationState.IDLE);
                updateMultiplayerVisibility(pet);
            });
        }
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

    public boolean isModelFailed(ModelType provider, String modelId) {
        if (provider == null || modelId == null) return false;
        String key = provider.name() + ":" + modelId;
        FailedModelEntry entry = failedModelCache.get(key);
        if (entry == null) return false;
        if (entry.isExpired(FAILED_CACHE_TTL_MS)) {
            failedModelCache.remove(key);
            return false;
        }
        return true;
    }

    public void markModelFailed(ModelType provider, String modelId, String reason) {
        if (provider == null || modelId == null) return;
        if (failedModelCache.size() > MAX_FAILED_CACHE_SIZE) {
            long now = System.currentTimeMillis();
            failedModelCache.entrySet().removeIf(e -> (now - e.getValue().getTimestamp()) > FAILED_CACHE_TTL_MS);
        }
        String key = provider.name() + ":" + modelId;
        failedModelCache.put(key, new FailedModelEntry(provider, modelId, reason));
    }

    /**
     * Determines default provider for AUTO mode globally.
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
     * Resolves requested provider for a pet.
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
     * Authoritative single decision step: Resolves Provider and Model ID together.
     */
    public ResolvedModel resolveModel(String petId, boolean isBaby) {
        if (petId == null || petId.trim().isEmpty()) {
            return ResolvedModel.none(isBaby);
        }

        FileConfiguration config = plugin.getConfig();
        String petProviderStr = config.getString("pets." + petId + ".model.provider");
        ModelType requestedType;

        if (petProviderStr != null && !petProviderStr.trim().isEmpty()) {
            requestedType = ModelType.fromString(petProviderStr);
        } else {
            String globalProviderStr = config.getString("model.provider", "AUTO");
            requestedType = ModelType.fromString(globalProviderStr);
        }

        if (requestedType == ModelType.NONE) {
            return ResolvedModel.none(isBaby);
        }

        // Explicit provider requested (BETTERMODEL or MODELENGINE)
        if (requestedType != ModelType.AUTO) {
            ModelProvider targetProvider = getProvider(requestedType);
            if (targetProvider.isAvailable()) {
                String mId = resolveConfiguredModelId(petId, requestedType, isBaby);
                if (mId != null && !mId.isEmpty() && !isModelFailed(requestedType, mId)) {
                    boolean isLegacy = isLegacyId(petId, requestedType, mId, isBaby);
                    return new ResolvedModel(requestedType, mId, false, isLegacy, isBaby);
                }
            }

            // Explicit provider unavailable or missing model -> Fallback
            ResolvedModel fallback = resolveFallbackModel(petId, requestedType, isBaby);
            if (fallback.isValid()) {
                return fallback;
            }
            return ResolvedModel.none(isBaby);
        }

        // AUTO resolution: Check candidates in priority order
        List<String> priority = config.getStringList("model.auto_priority");
        if (priority.isEmpty()) {
            priority = List.of("BETTERMODEL", "MODELENGINE");
        }

        for (String p : priority) {
            ModelType candidateType = ModelType.fromString(p);
            if (candidateType == ModelType.NONE || candidateType == ModelType.AUTO) continue;
            ModelProvider candProvider = getProvider(candidateType);
            if (candProvider.isAvailable()) {
                String candModelId = resolveConfiguredModelId(petId, candidateType, isBaby);
                if (candModelId != null && !candModelId.isEmpty() && !isModelFailed(candidateType, candModelId)) {
                    boolean isLegacy = isLegacyId(petId, candidateType, candModelId, isBaby);
                    return new ResolvedModel(candidateType, candModelId, false, isLegacy, isBaby);
                }
            }
        }

        // AUTO candidates exhausted -> Fallback
        ResolvedModel fallback = resolveFallbackModel(petId, null, isBaby);
        if (fallback.isValid()) {
            return fallback;
        }

        return ResolvedModel.none(isBaby);
    }

    private ResolvedModel resolveFallbackModel(String petId, ModelType primaryFailed, boolean isBaby) {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("model.fallback.enabled", true)) {
            return ResolvedModel.none(isBaby);
        }

        String fallbackStr = config.getString("model.fallback.provider", "BETTERMODEL");
        ModelType fallbackType = ModelType.fromString(fallbackStr);
        if (fallbackType == primaryFailed || fallbackType == ModelType.NONE || fallbackType == ModelType.AUTO) {
            return ResolvedModel.none(isBaby);
        }

        ModelProvider fbProvider = getProvider(fallbackType);
        if (fbProvider.isAvailable()) {
            String fbModelId = resolveConfiguredModelId(petId, fallbackType, isBaby);
            if (fbModelId != null && !fbModelId.isEmpty() && !isModelFailed(fallbackType, fbModelId)) {
                boolean isLegacy = isLegacyId(petId, fallbackType, fbModelId, isBaby);
                return new ResolvedModel(fallbackType, fbModelId, true, isLegacy, isBaby);
            }
        }
        return ResolvedModel.none(isBaby);
    }

    /**
     * Resolves configured model ID for a specific provider.
     */
    public String resolveConfiguredModelId(String petId, ModelType providerType, boolean isBaby) {
        if (petId == null || providerType == null || providerType == ModelType.NONE) return null;
        FileConfiguration config = plugin.getConfig();
        String formKey = isBaby ? "baby" : "adult";

        if (providerType == ModelType.MODELENGINE) {
            // 1. pets.<id>.model.modelengine.baby / adult
            String meForm = config.getString("pets." + petId + ".model.modelengine." + formKey);
            if (meForm != null && !meForm.trim().isEmpty()) return meForm.trim();

            // 2. pets.<id>.model.modelengine.id
            String meId = config.getString("pets." + petId + ".model.modelengine.id");
            if (meId != null && !meId.trim().isEmpty()) return meId.trim();

            // 3. pets.<id>.model.modelengine
            if (!config.isConfigurationSection("pets." + petId + ".model.modelengine")) {
                String meDirect = config.getString("pets." + petId + ".model.modelengine");
                if (meDirect != null && !meDirect.trim().isEmpty()) return meDirect.trim();
            }
            return null;
        } else if (providerType == ModelType.BETTERMODEL) {
            // 1. pets.<id>.model.bettermodel.baby / adult
            String bmForm = config.getString("pets." + petId + ".model.bettermodel." + formKey);
            if (bmForm != null && !bmForm.trim().isEmpty()) return bmForm.trim();

            // 2. pets.<id>.model.bettermodel.id
            String bmId = config.getString("pets." + petId + ".model.bettermodel.id");
            if (bmId != null && !bmId.trim().isEmpty()) return bmId.trim();

            // 3. pets.<id>.model.bettermodel
            if (!config.isConfigurationSection("pets." + petId + ".model.bettermodel")) {
                String bmDirect = config.getString("pets." + petId + ".model.bettermodel");
                if (bmDirect != null && !bmDirect.trim().isEmpty()) return bmDirect.trim();
            }

            // 4. Legacy backward compatibility for BetterModel only
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

    private boolean isLegacyId(String petId, ModelType providerType, String modelId, boolean isBaby) {
        if (providerType != ModelType.BETTERMODEL || modelId == null) return false;
        FileConfiguration config = plugin.getConfig();
        String legacyBaby = config.getString("pets." + petId + ".model_id_baby");
        String legacyAdult = config.getString("pets." + petId + ".model_id");
        return (isBaby && modelId.equals(legacyBaby)) || modelId.equals(legacyAdult);
    }

    public String resolveModelId(String petId, ModelType providerType, boolean isBaby) {
        return resolveConfiguredModelId(petId, providerType, isBaby);
    }

    public String resolveModelIdForProvider(String petId, ModelType providerType) {
        return resolveConfiguredModelId(petId, providerType, false);
    }

    /**
     * Authoritative Animation Name Resolver.
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

    public boolean spawnModel(Player owner, Entity pet, String petId) {
        return spawnModel(owner, pet, petId, null, false);
    }

    public boolean spawnModel(Player owner, Entity pet, String petId, boolean isBaby) {
        return spawnModel(owner, pet, petId, null, isBaby);
    }

    public boolean spawnModel(Player owner, Entity pet, String petId, String directModelId) {
        return spawnModel(owner, pet, petId, directModelId, false);
    }

    /**
     * Spawns and attaches custom model with strict lifecycle ordering:
     * remove old model -> resolve provider/model -> attach model -> register active provider -> register animation -> play SPAWN animation.
     */
    public boolean spawnModel(Player owner, Entity pet, String petId, String directModelId, boolean isBaby) {
        if (pet == null) return false;
        UUID uuid = pet.getUniqueId();

        // 1. Remove old model
        removeModel(uuid);

        // 2. Resolve provider and model together
        ResolvedModel resolved;
        if (directModelId != null && !directModelId.trim().isEmpty()) {
            ModelProvider prov = resolveProviderForPet(petId);
            resolved = new ResolvedModel(prov.getType(), directModelId.trim(), false, false, isBaby);
        } else {
            resolved = resolveModel(petId, isBaby);
        }

        logDebug("Spawn decision for pet [" + petId + "]: " + resolved);

        if (!resolved.isValid() || resolved.getProvider() == ModelType.NONE) {
            noneModelProvider.spawn(owner, pet, null, petId);
            activeEntityProviders.put(uuid, noneModelProvider);
            animationController.registerPet(uuid, petId);
            if (pet instanceof LivingEntity living && pet.isValid()) {
                living.setInvisible(false);
            }
            return true;
        }

        ModelProvider primaryProvider = getProvider(resolved.getProvider());
        boolean success = primaryProvider.spawn(owner, pet, resolved.getModelId(), petId);

        if (success) {
            // 3. Register active provider & animation controller
            activeEntityProviders.put(uuid, primaryProvider);
            animationController.registerPet(uuid, petId);
            // 4. Play SPAWN animation AFTER full registration
            animationController.requestTransientAnimation(pet, PetAnimationState.SPAWN, 25L, PetAnimationState.IDLE);
            logDebug("Spawned model [" + resolved.getModelId() + "] via [" + resolved.getProvider() + "]");
            return true;
        }

        // Primary spawn failed -> Record failure with TTL
        markModelFailed(resolved.getProvider(), resolved.getModelId(), "Spawn failed or threw exception");

        // 5. Fallback retry if primary was not already fallback
        if (!resolved.isFallback()) {
            ResolvedModel fallback = resolveFallbackModel(petId, resolved.getProvider(), isBaby);
            if (fallback.isValid()) {
                ModelProvider fbProvider = getProvider(fallback.getProvider());
                logDebug("Attempting fallback provider [" + fallback.getProvider() + "] with model [" + fallback.getModelId() + "]");
                boolean fbSuccess = fbProvider.spawn(owner, pet, fallback.getModelId(), petId);
                if (fbSuccess) {
                    activeEntityProviders.put(uuid, fbProvider);
                    animationController.registerPet(uuid, petId);
                    animationController.requestTransientAnimation(pet, PetAnimationState.SPAWN, 25L, PetAnimationState.IDLE);
                    return true;
                } else {
                    markModelFailed(fallback.getProvider(), fallback.getModelId(), "Fallback spawn failed");
                }
            }
        }

        // All custom renders failed -> Fallback to vanilla entity
        logDebug("All custom models failed for pet [" + petId + "]. Using vanilla entity.");
        noneModelProvider.spawn(owner, pet, null, petId);
        activeEntityProviders.put(uuid, noneModelProvider);
        animationController.registerPet(uuid, petId);
        if (pet instanceof LivingEntity living && pet.isValid()) {
            living.setInvisible(false);
        }
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
