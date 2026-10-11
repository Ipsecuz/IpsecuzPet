package org.ipsecuz.pet.dialog;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.ipsecuz.pet.IpsecuzPet;
import org.ipsecuz.pet.LanguageManager;
import org.ipsecuz.pet.SchedulerUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Central Dialog Service orchestrating Native Dialog, Chest GUI Fallback, and Optional DialogWindow Bridge.
 * Enforces server-side authority, Folia/Paper execution safety, and anti-replay session security.
 */
public class DialogService {

    private final IpsecuzPet plugin;
    private final NativeDialogProvider nativeProvider;
    private final ChestGuiFallback fallbackProvider;
    private final OptionalDialogWindowBridge dialogWindowBridge;
    private final DialogSessionManager sessionManager;

    public DialogService(IpsecuzPet plugin) {
        this.plugin = plugin;
        this.nativeProvider = new NativeDialogProvider(plugin);
        this.fallbackProvider = new ChestGuiFallback(plugin);
        this.dialogWindowBridge = new OptionalDialogWindowBridge(plugin);
        this.sessionManager = new DialogSessionManager(plugin);
    }

    public NativeDialogProvider getNativeProvider() { return nativeProvider; }
    public ChestGuiFallback getFallbackProvider() { return fallbackProvider; }
    public OptionalDialogWindowBridge getDialogWindowBridge() { return dialogWindowBridge; }
    public DialogSessionManager getSessionManager() { return sessionManager; }

    public DialogMode getEffectiveMode(Player player) {
        if (player != null && plugin.getPlayerSettingsManager() != null) {
            String pPref = plugin.getPlayerSettingsManager().getDialogModePreference(player.getUniqueId());
            if (pPref != null) {
                return DialogMode.fromString(pPref);
            }
        }
        String cfgMode = plugin.getConfig().getString("ui.dialog.mode", "AUTO");
        return DialogMode.fromString(cfgMode);
    }

    public boolean isNativeSupported(Player player) {
        DialogMode mode = getEffectiveMode(player);
        if (mode == DialogMode.CHEST) return false;
        return nativeProvider.isSupported(player);
    }

    public DialogProvider getProviderFor(Player player) {
        if (isNativeSupported(player)) {
            return nativeProvider;
        }
        return fallbackProvider;
    }

    // --- Public API UI Navigation ---

    public void openPetHub(Player player) {
        if (player == null || !player.isOnline()) return;
        DialogSession session = sessionManager.createSession(player, DialogType.HUB, null, null);
        getProviderFor(player).openPetHub(player, session);
    }

    public void openPetDetails(Player player, String petId) {
        if (player == null || !player.isOnline()) return;
        DialogSession session = sessionManager.createSession(player, DialogType.DETAIL, petId, null);
        getProviderFor(player).openPetDetails(player, session, petId);
    }

    public void openRenameDialog(Player player, String petId) {
        if (player == null || !player.isOnline()) return;
        if (!player.hasPermission("ipsecuzpet.rename")) {
            player.sendMessage(plugin.getLanguage().getMessage("general.no_permission"));
            return;
        }
        DialogSession session = sessionManager.createSession(player, DialogType.RENAME, petId, null);
        getProviderFor(player).openRenameDialog(player, session, petId);
    }

    public void openEvolutionDialog(Player player, String petId) {
        if (player == null || !player.isOnline()) return;
        if (!player.hasPermission("ipsecuzpet.evolution")) {
            player.sendMessage(plugin.getLanguage().getMessage("general.no_permission"));
            return;
        }
        DialogSession session = sessionManager.createSession(player, DialogType.EVOLUTION, petId, null);
        getProviderFor(player).openEvolutionDialog(player, session, petId);
    }

    public void openShopConfirmDialog(Player player, String petId) {
        if (player == null || !player.isOnline()) return;
        if (!player.hasPermission("ipsecuzpet.shop")) {
            player.sendMessage(plugin.getLanguage().getMessage("general.no_permission"));
            return;
        }
        DialogSession session = sessionManager.createSession(player, DialogType.SHOP_CONFIRM, petId, null);
        getProviderFor(player).openShopConfirmDialog(player, session, petId);
    }

    public void openHatchConfirmDialog(Player player, String eggId) {
        if (player == null || !player.isOnline()) return;
        if (!player.hasPermission("ipsecuzpet.hatch")) {
            player.sendMessage(plugin.getLanguage().getMessage("general.no_permission"));
            return;
        }
        DialogSession session = sessionManager.createSession(player, DialogType.HATCH_CONFIRM, null, Map.of("egg_id", eggId));
        getProviderFor(player).openHatchConfirmDialog(player, session, eggId);
    }

    public void openPetGuide(Player player, int chapterIndex) {
        if (player == null || !player.isOnline()) return;
        DialogSession session = sessionManager.createSession(player, DialogType.GUIDE, null, Map.of("chapter", chapterIndex));
        getProviderFor(player).openPetGuide(player, session, chapterIndex);
    }

    public void openSettingsDialog(Player player) {
        if (player == null || !player.isOnline()) return;
        DialogSession session = sessionManager.createSession(player, DialogType.SETTINGS, null, null);
        getProviderFor(player).openSettingsDialog(player, session);
    }

    public void openTradeInviteDialog(Player player, Player sender) {
        if (player == null || !player.isOnline() || sender == null) return;
        DialogSession session = sessionManager.createSession(player, DialogType.TRADE_INVITE, null, Map.of("sender", sender.getUniqueId().toString()));
        getProviderFor(player).openTradeInviteDialog(player, session, sender);
    }

    public void openTradeConfirmDialog(Player player, UUID tradeId, List<ItemStack> myItems, List<ItemStack> theirItems, Consumer<Boolean> callback) {
        if (player == null || !player.isOnline()) return;
        DialogSession session = sessionManager.createSession(player, DialogType.TRADE_CONFIRM, null, Map.of("trade_id", tradeId.toString()));
        getProviderFor(player).openTradeConfirmDialog(player, session, tradeId, myItems, theirItems, callback);
    }

    public void openNotice(Player player, String title, String message, Runnable onClose) {
        if (player == null || !player.isOnline()) return;
        getProviderFor(player).openNotice(player, title, message, onClose);
    }

    public void openConfirmation(Player player, String title, String message, String confirmText, String cancelText, Consumer<Boolean> callback) {
        if (player == null || !player.isOnline()) return;
        getProviderFor(player).openConfirmation(player, title, message, confirmText, cancelText, callback);
    }

    public void openMyPets(Player player, int page) {
        if (player == null || !player.isOnline()) return;
        DialogSession session = sessionManager.createSession(player, DialogType.SELECTION, null, Map.of("page", page));
        getProviderFor(player).openMyPets(player, session, page);
    }

    public void openShopBrowse(Player player, int page) {
        if (player == null || !player.isOnline()) return;
        if (!player.hasPermission("ipsecuzpet.shop")) {
            player.sendMessage(plugin.getLanguage().getMessage("general.no_permission"));
            return;
        }
        DialogSession session = sessionManager.createSession(player, DialogType.SHOP_BROWSE, null, Map.of("page", page));
        getProviderFor(player).openShopBrowse(player, session, page);
    }

    public void openHatchSelect(Player player) {
        if (player == null || !player.isOnline()) return;
        if (!player.hasPermission("ipsecuzpet.hatch")) {
            player.sendMessage(plugin.getLanguage().getMessage("general.no_permission"));
            return;
        }
        DialogSession session = sessionManager.createSession(player, DialogType.HATCH_SELECT, null, null);
        getProviderFor(player).openHatchSelect(player, session);
    }

    public void openCodexBrowse(Player player, int page) {
        if (player == null || !player.isOnline()) return;
        if (!player.hasPermission("ipsecuzpet.codex")) {
            player.sendMessage(plugin.getLanguage().getMessage("general.no_permission"));
            return;
        }
        DialogSession session = sessionManager.createSession(player, DialogType.CODEX_BROWSE, null, Map.of("page", page));
        getProviderFor(player).openCodexBrowse(player, session, page);
    }

    public void openShardsOverview(Player player) {
        if (player == null || !player.isOnline()) return;
        if (!player.hasPermission("ipsecuzpet.shards")) {
            player.sendMessage(plugin.getLanguage().getMessage("general.no_permission"));
            return;
        }
        DialogSession session = sessionManager.createSession(player, DialogType.SHARDS_OVERVIEW, null, null);
        getProviderFor(player).openShardsOverview(player, session);
    }

    // --- Authoritative Gameplay Processors ---

    public boolean processRename(Player player, String petId, String newName) {
        if (player == null || petId == null || newName == null) return false;
        LanguageManager lang = plugin.getLanguage();

        if (!player.hasPermission("ipsecuzpet.rename")) {
            player.sendMessage(lang.getMessage("general.no_permission"));
            return false;
        }

        if (!plugin.getOwnershipManager().hasPet(player.getUniqueId(), petId)) {
            player.sendMessage(lang.getMessage("pet.not_owned", "%pet_id%", petId));
            return false;
        }

        String raw = newName.trim();
        if (raw.equalsIgnoreCase("cancel") || raw.equalsIgnoreCase("huy")) {
            player.sendMessage(lang.getMessage("pet.rename_cancelled"));
            return true;
        }

        String clean = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', raw));
        if (clean.length() < 2 || clean.length() > 16) {
            player.sendMessage(lang.getMessage("pet.rename_length_error"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }

        // Persist new custom name
        plugin.getConfigManager().setCustomName(player.getUniqueId(), petId, raw);

        // Update active entity if spawned
        Entity activePet = plugin.getPetManager().getPet(player.getUniqueId());
        if (activePet != null && petId.equals(plugin.getPetManager().getActivePetId(player.getUniqueId()))) {
            int lvl = plugin.getConfigManager().getData().getInt(player.getUniqueId() + ".pets." + petId + ".level", 1);
            String format = lang.getMessage("pet.display_format");
            if (format == null) format = "&8[&eLv.%level%&8] &a%name% &7(&f%owner%&7)";
            String displayName = format
                    .replace("%name%", raw)
                    .replace("%level%", String.valueOf(lvl))
                    .replace("%player%", player.getName())
                    .replace("%owner%", player.getName());
            activePet.setCustomName(ChatColor.translateAlternateColorCodes('&', displayName));
            activePet.setCustomNameVisible(true);
        }

        player.sendMessage(lang.getMessage("pet.rename_success", "%new_name%", raw));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        return true;
    }

    public boolean processEvolution(Player player, String petId) {
        if (player == null || petId == null) return false;
        if (!player.hasPermission("ipsecuzpet.evolution")) {
            player.sendMessage(plugin.getLanguage().getMessage("general.no_permission"));
            return false;
        }
        return plugin.getEvolutionManager().upgradeStar(player, petId);
    }

    public boolean processShopPurchase(Player player, String petId) {
        if (player == null || petId == null) return false;
        LanguageManager lang = plugin.getLanguage();

        if (!player.hasPermission("ipsecuzpet.shop")) {
            player.sendMessage(lang.getMessage("general.no_permission"));
            return false;
        }

        if (plugin.getOwnershipManager().hasPet(player.getUniqueId(), petId)) {
            player.sendMessage(lang.getMessage("pet.already_owned"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }

        if (!plugin.getOwnershipManager().canAcquirePet(player)) {
            player.sendMessage(lang.getMessage("pet.limit_reached", "%max_pets%", String.valueOf(plugin.getOwnershipManager().getMaxOwnedPets(player))));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }

        if (plugin.getCurrencyManager().processTransaction(player, petId)) {
            plugin.getOwnershipManager().acquirePet(player, petId, 1, 0, 1, org.ipsecuz.pet.PetTrait.rollRandomTrait(), null);
            plugin.getCodexManager().discover(player.getUniqueId(), petId);
            player.sendMessage(lang.getMessage("pet.buy_success", "%pet_name%", lang.getPetName(petId)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
            return true;
        }
        return false;
    }

    public boolean processHatchConfirm(Player player, String eggId) {
        if (player == null || eggId == null) return false;
        LanguageManager lang = plugin.getLanguage();

        if (!player.hasPermission("ipsecuzpet.hatch")) {
            player.sendMessage(lang.getMessage("general.no_permission"));
            return false;
        }

        plugin.getHatchingManager().processHatch(player, eggId, null);
        return true;
    }

    public void cleanup(UUID playerId) {
        if (playerId != null) {
            sessionManager.invalidate(playerId);
        }
    }

    public void shutdown() {
        sessionManager.shutdown();
    }
}
