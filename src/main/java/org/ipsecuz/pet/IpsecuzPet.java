package org.ipsecuz.pet;

import org.bukkit.plugin.java.JavaPlugin;

public class IpsecuzPet extends JavaPlugin {

    private static final int RESOURCE_ID = 130551;
    private static IpsecuzPet instance;
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

    @Override
    public void onEnable() {
        instance = this;

        // 1. Nạp Language trước
        this.languageManager = new LanguageManager(this);

        // 2. Nạp Currency & Config
        this.currencyManager = new CurrencyManager(this);
        this.configManager = new ConfigManager(this);

        // 2.5 Nạp ItemHookManager (Hỗ trợ ItemsAdder, Oraxen, Nexo)
        this.itemHookManager = new ItemHookManager(this);

        // 3. Nạp ModuleManager (quản lý folder modules/*.yml)
        this.moduleManager = new ModuleManager(this);

        // 4. Nạp ModelHandler (BetterModel Support)
        this.modelHandler = new ModelHandler(this);

        // 5. Nạp các hệ thống mở rộng
        this.hatchingManager = new HatchingManager(this);
        this.skillManager = new SkillManager(this);
        this.feedingManager = new FeedingManager(this);
        this.evolutionManager = new EvolutionManager(this);
        this.tradeManager = new TradeManager(this);

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

        String platform = SchedulerUtils.isFolia() ? "Folia" : "Paper/Spigot";
        getLogger().info("§a[IpsecuzPet V2.0] Đã khởi chạy thành công trên nền tảng: §e" + platform);

        new UpdateChecker(this, RESOURCE_ID).getVersion(version -> {
            if (this.getDescription().getVersion().equals(version)) {
                getLogger().info("Plugin have a new version");
            } else {
                getLogger().warning("Download here: https://www.spigotmc.org/resources/" + RESOURCE_ID);
            }
        });
    }

    @Override
    public void onDisable() {
        if (petManager != null) petManager.removeAllPets();
    }

    public static IpsecuzPet getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public PetManager getPetManager() { return petManager; }
    public CurrencyManager getCurrencyManager() { return currencyManager; }
    public LanguageManager getLanguage() { return languageManager; }
    public CaptureManager getCaptureManager() { return captureManager; }
    public ModelHandler getModelHandler() { return modelHandler; }
    public ModuleManager getModuleManager() { return moduleManager; }
    public HatchingManager getHatchingManager() { return hatchingManager; }
    public SkillManager getSkillManager() { return skillManager; }
    public FeedingManager getFeedingManager() { return feedingManager; }
    public EvolutionManager getEvolutionManager() { return evolutionManager; }
    public TradeManager getTradeManager() { return tradeManager; }
    public DynamicPetRegistry getDynamicPetRegistry() { return dynamicPetRegistry; }
    public ItemHookManager getItemHookManager() { return itemHookManager; }
}