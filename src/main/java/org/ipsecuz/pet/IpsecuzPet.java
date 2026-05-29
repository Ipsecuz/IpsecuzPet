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

    @Override
    public void onEnable() {
        instance = this;

        // Load Language trước
        this.languageManager = new LanguageManager(this);

        this.currencyManager = new CurrencyManager(this);
        this.configManager = new ConfigManager(this);

        // Load ModelHandler (Check BetterModel)
        this.modelHandler = new ModelHandler(this);

        this.petManager = new PetManager(this);
        this.captureManager = new CaptureManager(this);


        if (getCommand("ipsecuzpet") != null) {
            getCommand("ipsecuzpet").setExecutor(new PetCommand(this));
            getCommand("ipsecuzpet").setTabCompleter(new PetTabCompleter(this));
        }

        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);

        // Chạy AI (Đã support Folia)
        this.petManager.startPetTask();

        String platform = SchedulerUtils.isFolia() ? "Folia" : "Paper/Spigot";
        getLogger().info("IpsecuzPets V1.4.0 (BetterModel Support) running on: " + platform);

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
}