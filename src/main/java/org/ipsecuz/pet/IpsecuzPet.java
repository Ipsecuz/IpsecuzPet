package org.ipsecuz.pet;

import org.bukkit.plugin.java.JavaPlugin;

public class IpsecuzPet extends JavaPlugin {

    public static final int RESOURCE_ID = 130551;
    private static IpsecuzPet instance;
    private static boolean metricsInitialized = false;
    private ConfigManager configManager;
    private PetManager petManager;
    private CurrencyManager currencyManager;
    private LanguageManager languageManager;
    private CaptureManager captureManager;
    private ModelHandler modelHandler;
    private ModuleManager moduleManager;
    private HatchingManager hatchingManager;
    private SkillManager skillManager;
    private FeedingManager feedingManager;
    private EvolutionManager evolutionManager;
    private TradeManager tradeManager;
    private DynamicPetRegistry dynamicPetRegistry;
    private ItemHookManager itemHookManager;
    private PetOwnershipManager ownershipManager;
    private PetShardManager shardManager;
    private PetCodexManager codexManager;
    private HappinessModifierEngine happinessModifierEngine;
    private org.ipsecuz.pet.requirement.RequirementManager requirementManager;

    @Override
    public void onEnable() {
        instance = this;

        // 1. Nạp Language trước
        this.languageManager = new LanguageManager(this);

        // 2. Nạp Currency & Config
        this.currencyManager = new CurrencyManager(this);
        this.configManager = new ConfigManager(this);

        // 2.2 Nạp hệ thống quản lý quyền sở hữu, mảnh pet và codex
        this.ownershipManager = new PetOwnershipManager(this);
        this.shardManager = new PetShardManager(this);
        this.codexManager = new PetCodexManager(this);

        // 2.5 Nạp ItemHookManager (Hỗ trợ ItemsAdder, Oraxen, Nexo)
        this.itemHookManager = new ItemHookManager(this);

        // 2.8 Nạp Universal Requirement Engine
        this.requirementManager = new org.ipsecuz.pet.requirement.RequirementManager(this);

        // 3. Nạp ModuleManager (quản lý folder modules/*.yml)
        this.moduleManager = new ModuleManager(this);

        // 4. Nạp ModelHandler (BetterModel Support)
        this.modelHandler = new ModelHandler(this);

        // 5. Nạp các hệ thống mở rộng
        this.hatchingManager = new HatchingManager(this);
        this.skillManager = new SkillManager(this);
        this.feedingManager = new FeedingManager(this);
        this.happinessModifierEngine = new HappinessModifierEngine(this);
        this.evolutionManager = new EvolutionManager(this);
        this.tradeManager = new TradeManager(this);
        this.tradeManager.recoverPendingTrades();

        // 6. Nạp PetManager & CaptureManager
        this.petManager = new PetManager(this);
        this.captureManager = new CaptureManager(this);

        // 7. Tự động phát hiện phiên bản và đăng ký mob mới (1.20 -> 1.21.x -> 26.x)
        this.dynamicPetRegistry = new DynamicPetRegistry(this);
        this.dynamicPetRegistry.detectAndRegisterNewMobs();

        // 8. Đăng ký Commands & TabCompleter
        if (getCommand("ipsecuzpet") != null) {
            getCommand("ipsecuzpet").setExecutor(new PetCommand(this));
            getCommand("ipsecuzpet").setTabCompleter(new PetTabCompleter(this));
        }

        // 9. Đăng ký tất cả Event Listeners (Đầy đủ GameListener, GuiListener, PetListener)
        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new PetListener(this), this);

        // 10. Chạy AI Pet Timer (Folia Safe)
        this.petManager.startPetTask();

        // 11. Chạy Feeding Decay Timer (nếu module feeding bật)
        if (this.moduleManager.isFeedingEnabled()) {
            this.feedingManager.startDecayTask();
        }

        String platform = SchedulerUtils.isFolia() ? "Folia" : "Paper/Spigot";
        getLogger().info("§a[IpsecuzPet V2.1] Đã khởi chạy thành công trên nền tảng: §e" + platform);

        new UpdateChecker(this, RESOURCE_ID).getVersion(version -> {
            if (UpdateChecker.isNewerVersion(version, this.getDescription().getVersion())) {
                getLogger().info("§e[IpsecuzPet] Đã có phiên bản mới: " + version + "! Tải ngay tại: https://www.spigotmc.org/resources/" + RESOURCE_ID);
            } else {
                getLogger().info("§a[IpsecuzPet] Bạn đang sử dụng phiên bản mới nhất (v" + this.getDescription().getVersion() + ").");
            }
        });
        // 12. Tích hợp bStats Metrics (Plugin ID: 34551) với singleton guard
        if (getConfig().getBoolean("metric", true) && !metricsInitialized) {
            try {
                org.bstats.bukkit.Metrics metrics = new org.bstats.bukkit.Metrics(this, 34551);
                metrics.addCustomChart(new org.bstats.charts.SimplePie("language", () ->
                        languageManager != null ? languageManager.getSelectedLanguage().toUpperCase() : "VN"));
                metrics.addCustomChart(new org.bstats.charts.SimplePie("model_provider", () ->
                        modelHandler != null && modelHandler.getManager() != null ? modelHandler.getManager().getActiveProviderName() : "None"));
                metrics.addCustomChart(new org.bstats.charts.SimplePie("renderer_availability", () ->
                        modelHandler != null && modelHandler.getManager() != null ? modelHandler.getManager().getAvailableRenderersSummary() : "None"));
                metricsInitialized = true;
            } catch (Throwable t) {
                getLogger().warning("§c[bStats] Không thể khởi tạo dịch vụ thống kê: " + t.getMessage());
            }
        }
    }

    @Override
    public void onDisable() {
        if (tradeManager != null) {
            tradeManager.cancelAllActiveTrades();
        }
        if (petManager != null) {
            petManager.removeAllPets();
        }
        if (skillManager != null) {
            skillManager.clearAllCooldowns();
        }
        if (captureManager != null) {
            captureManager.refundAllPending();
        }
        if (hatchingManager != null) {
            hatchingManager.saveAllPendingTransactions();
        }
        if (configManager != null) {
            configManager.forceSave();
        }
    }

    public static IpsecuzPet getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public PetManager getPetManager() { return petManager; }
    public CurrencyManager getCurrencyManager() { return currencyManager; }
    public LanguageManager getLanguage() { return languageManager; }
    public CaptureManager getCaptureManager() { return captureManager; }
    public ModelHandler getModelHandler() { return modelHandler; }
    public org.ipsecuz.pet.model.ModelProviderManager getModelProviderManager() { return modelHandler != null ? modelHandler.getManager() : null; }
    public ModuleManager getModuleManager() { return moduleManager; }
    public HatchingManager getHatchingManager() { return hatchingManager; }
    public SkillManager getSkillManager() { return skillManager; }
    public FeedingManager getFeedingManager() { return feedingManager; }
    public HappinessModifierEngine getHappinessModifierEngine() { return happinessModifierEngine; }
    public EvolutionManager getEvolutionManager() { return evolutionManager; }
    public TradeManager getTradeManager() { return tradeManager; }
    public DynamicPetRegistry getDynamicPetRegistry() { return dynamicPetRegistry; }
    public ItemHookManager getItemHookManager() { return itemHookManager; }
    public PetOwnershipManager getOwnershipManager() { return ownershipManager; }
    public PetShardManager getShardManager() { return shardManager; }
    public PetCodexManager getCodexManager() { return codexManager; }
    public org.ipsecuz.pet.requirement.RequirementManager getRequirementManager() { return requirementManager; }
}