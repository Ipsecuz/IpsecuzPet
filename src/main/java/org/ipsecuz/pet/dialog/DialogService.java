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
import org.ipsecuz.pet.ShopPriceFormatter;
import org.ipsecuz.pet.ShopTransactionReceipt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
    private final Set<UUID> inProgressPurchases = ConcurrentHashMap.newKeySet();

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

    public Map<String, String> getDiagnosticStatus(Player player) {
        Map<String, String> status = new java.util.LinkedHashMap<>();
        DialogMode effMode = getEffectiveMode(player);
        status.put("configured_mode", plugin.getConfig().getString("ui.dialog.mode", "AUTO"));
        status.put("effective_mode", effMode.name());
        status.put("server_version", org.bukkit.Bukkit.getName() + " " + org.bukkit.Bukkit.getVersion());
        status.put("bukkit_version", org.bukkit.Bukkit.getBukkitVersion());
        status.put("server_api_supported", String.valueOf(nativeProvider.isServerSupported()));
        int clientProto = nativeProvider.getClientProtocol(player);
        status.put("client_protocol", clientProto != -1 ? String.valueOf(clientProto) : "Unknown (ViaVersion not present)");
        status.put("client_protocol_supported", String.valueOf(nativeProvider.isClientSupported(player)));
        status.put("native_api_available", String.valueOf(nativeProvider.getEngine().isAvailable()));
        status.put("native_class_available", String.valueOf(nativeProvider.getEngine().isClassAvailable()));
        status.put("native_methods_available", String.valueOf(nativeProvider.getEngine().isMethodsAvailable()));
        status.put("callback_options_initialized", String.valueOf(nativeProvider.getEngine().isCallbackOptionsInitialized()));
        status.put("action_button_available", String.valueOf(nativeProvider.getEngine().isActionButtonAvailable()));
        status.put("action_button_tested", String.valueOf(nativeProvider.getEngine().isActionButtonCreationTested()));
        status.put("multiaction_tested", String.valueOf(nativeProvider.getEngine().isMultiActionConstructionTested()));
        status.put("empty_action_guard_tested", String.valueOf(nativeProvider.getEngine().isEmptyActionGuardTested()));

        // Latest operation attempt
        status.put("last_attempt_type", nativeProvider.getEngine().getLastAttemptType() != null ? nativeProvider.getEngine().getLastAttemptType() : "None");
        status.put("last_attempt_player", nativeProvider.getEngine().getLastAttemptPlayer() != null ? nativeProvider.getEngine().getLastAttemptPlayer() : "None");
        status.put("last_attempt_success", nativeProvider.getEngine().getLastAttemptTimestamp() > 0 ? String.valueOf(nativeProvider.getEngine().isLastAttemptSuccess()) : "N/A");
        long attemptAgo = nativeProvider.getEngine().getLastAttemptTimestamp() > 0 ? (System.currentTimeMillis() - nativeProvider.getEngine().getLastAttemptTimestamp()) / 1000 : -1;
        status.put("last_attempt_time_ago", attemptAgo >= 0 ? attemptAgo + "s ago" : "None");

        status.put("capabilities_summary", nativeProvider.getEngine().getCapabilitySummary());
        status.put("dialogwindow_bridge_available", String.valueOf(dialogWindowBridge.isAvailable()));
        status.put("selected_backend", getProviderFor(player).getClass().getSimpleName());

        String rationale;
        if (effMode == DialogMode.CHEST) {
            rationale = "Forced CHEST mode by player/server configuration";
        } else if (!nativeProvider.isServerSupported()) {
            rationale = "Fallback CHEST GUI: Server lacks Paper 1.21.7+ Dialog API reflection bindings";
        } else if (player != null && !nativeProvider.isClientSupported(player)) {
            rationale = "Fallback CHEST GUI: Player client protocol (" + clientProto + ") is older than 771 (MC 1.21.6+)";
        } else {
            rationale = "Native Minecraft Dialog active: Server API bound and player client compatible (protocol >= 771)";
        }
        status.put("selection_rationale", rationale);

        status.put("init_status", nativeProvider.getEngine().getInitStatusMessage());

        // Active and historical failure diagnostics
        status.put("has_active_failure", String.valueOf(nativeProvider.getEngine().hasActiveFailure()));
        if (nativeProvider.getEngine().hasActiveFailure()) {
            var stage = nativeProvider.getEngine().getLastFailureStage();
            status.put("last_failure_stage", stage != null ? stage.name() + " (" + stage.getDescription() + ")" : "None");
            String lastFail = nativeProvider.getEngine().getLastFailureReason();
            status.put("last_failure", lastFail != null ? lastFail : "None");
            String rootExType = nativeProvider.getEngine().getLastRootExceptionType();
            String rootExMsg = nativeProvider.getEngine().getLastRootExceptionMessage();
            status.put("last_root_cause", (rootExType != null && !"None".equals(rootExType)) ? rootExType + ": " + rootExMsg : "None");
            long failAgo = (System.currentTimeMillis() - nativeProvider.getEngine().getLastFailureTimestamp()) / 1000;
            status.put("last_failure_time_ago", failAgo + "s ago");
        } else {
            status.put("last_failure_stage", "None");
            status.put("last_failure", "None");
            status.put("last_root_cause", "None");
            status.put("last_failure_time_ago", "None");
        }

        if (nativeProvider.getEngine().getHistoricalFailureReason() != null) {
            long histAgo = (System.currentTimeMillis() - nativeProvider.getEngine().getHistoricalFailureTimestamp()) / 1000;
            status.put("historical_last_failure", nativeProvider.getEngine().getHistoricalFailureReason() + " (" + histAgo + "s ago)");
        } else {
            status.put("historical_last_failure", "None");
        }
        return status;
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
            plugin.getPetManager().updatePetNameplate(activePet, player.getUniqueId(), petId);
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

    public boolean isPetShopVisible(Player player, String petId) {
        if (petId == null) return false;
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("pets." + petId);
        if (sec == null) return false;
        if (!sec.getBoolean("enabled", true)) return false;
        if (!sec.getBoolean("buyable", true)) return false;
        if (!sec.getBoolean("visible_in_shop", true)) return false;
        if (sec.getBoolean("draft", false) || sec.getBoolean("disabled", false)) return false;
        String perm = sec.getString("permission");
        if (perm != null && !perm.isEmpty() && player != null) {
            if (!player.hasPermission(perm)) return false;
        }
        return true;
    }

    public List<String> getShopEligiblePets(Player player) {
        List<String> list = new ArrayList<>();
        ConfigurationSection petsSec = plugin.getConfig().getConfigurationSection("pets");
        if (petsSec == null) return list;
        for (String petId : petsSec.getKeys(false)) {
            if (isPetShopVisible(player, petId)) {
                list.add(petId);
            }
        }
        return list;
    }

    public boolean processShopPurchase(Player player, String petId) {
        if (player == null || petId == null) return false;
        LanguageManager lang = plugin.getLanguage();

        if (!player.hasPermission("ipsecuzpet.shop")) {
            player.sendMessage(lang.getMessage("general.no_permission"));
            return false;
        }

        if (!isPetShopVisible(player, petId)) {
            player.sendMessage(lang.getMessageOrDefault("shop.pet_not_buyable", "&cThú cưng này hiện không có sẵn để mua!"));
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

        // Prevent double-click / concurrent purchase
        if (!inProgressPurchases.add(player.getUniqueId())) {
            return false;
        }

        try {
            ShopPriceFormatter.PriceResult priceInfo = null;
            if (plugin.getShopPriceFormatter() != null) {
                priceInfo = plugin.getShopPriceFormatter().format(player, petId);
                if (!priceInfo.isAvailable() || priceInfo.getType() == ShopPriceFormatter.PriceType.INVALID) {
                    player.sendMessage(lang.getMessageOrDefault("shop.provider_unavailable", "&cKhông thể mua: %reason%", "%reason%", priceInfo.getUnavailableReason()));
                    return false;
                }
            }

            // Durable journal: CREATED -> PAYMENT_PROCESSING
            String txId = plugin.getConfigManager() != null ? plugin.getConfigManager().createShopTransaction(player.getUniqueId(), petId, priceInfo) : null;
            if (txId != null && plugin.getConfigManager() != null) {
                plugin.getConfigManager().updateShopTransactionStatus(txId, "PAYMENT_PROCESSING", null);
            }

            ShopTransactionReceipt receipt = plugin.getCurrencyManager().executeShopTransaction(player, petId);
            if (!receipt.isSuccess()) {
                if (txId != null && plugin.getConfigManager() != null) {
                    plugin.getConfigManager().updateShopTransactionStatus(txId, "FAILED", receipt.getErrorMessage());
                }
                return false;
            }

            if (txId != null && plugin.getConfigManager() != null) {
                plugin.getConfigManager().updateShopTransactionStatus(txId, "REWARD_PROCESSING", null);
            }

            boolean acquired = plugin.getOwnershipManager().acquirePet(player, petId, 1, 0, 1, org.ipsecuz.pet.PetTrait.rollRandomTrait(), null);
            if (!acquired) {
                // Explicit safe rollback on acquisition failure
                plugin.getLogger().warning("[DialogService] Failed to acquire pet " + petId + " for " + player.getName() + " after payment deduction. Executing exact rollback.");
                if (txId != null && plugin.getConfigManager() != null) {
                    plugin.getConfigManager().updateShopTransactionStatus(txId, "REFUND_PROCESSING", "Acquisition failed");
                }
                boolean rolledBack = receipt.rollback(player, plugin);
                if (rolledBack) {
                    if (txId != null && plugin.getConfigManager() != null) {
                        plugin.getConfigManager().updateShopTransactionStatus(txId, "REFUNDED", "Rolled back successfully");
                    }
                    player.sendMessage(lang.getMessageOrDefault("shop.purchase_failed_refunded", "&cKhông thể tiếp nhận Thú cưng vào kho! Đã hoàn trả lại chi phí giao dịch."));
                } else {
                    plugin.getLogger().severe("[DialogService] Failed to complete immediate rollback for " + player.getName() + ". Storing recoverable journal record.");
                    if (txId != null && plugin.getConfigManager() != null) {
                        plugin.getConfigManager().updateShopTransactionStatus(txId, "RECOVERABLE", "Provider offline or rollback failed");
                        plugin.getConfigManager().recordPendingRefund(player.getUniqueId(), petId, receipt);
                    }
                    player.sendMessage(lang.getMessageOrDefault("shop.refund_pending", "&cKhông thể tiếp nhận Thú cưng! Hoàn trả tạm thời chưa thể thực hiện do hệ thống tiền tệ/vật phẩm ngoại vi đang bảo trì. Quản trị viên sẽ xử lý lại cho bạn."));
                }
                return false;
            }

            if (txId != null && plugin.getConfigManager() != null) {
                plugin.getConfigManager().updateShopTransactionStatus(txId, "COMPLETED", null);
            }
            plugin.getCodexManager().discover(player.getUniqueId(), petId);
            player.sendMessage(lang.getMessage("pet.buy_success", "%pet_name%", lang.getPetName(petId)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
            return true;
        } finally {
            inProgressPurchases.remove(player.getUniqueId());
        }
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
