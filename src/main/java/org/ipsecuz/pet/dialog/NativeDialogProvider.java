package org.ipsecuz.pet.dialog;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.ipsecuz.pet.*;
import org.ipsecuz.pet.dialog.PaperNativeDialogEngine.NativeButton;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;

/**
 * Isolated Native Dialog Provider using PaperNativeDialogEngine to interface with Paper 1.21.7+ Dialog API.
 * Ensures zero class-loading errors on older Paper/Folia versions while delivering genuine, fully functional
 * Minecraft Dialogs for Pet Hub, Details, Rename input, Evolution, Shop, Hatch, Guide, and Settings.
 */
public class NativeDialogProvider implements DialogProvider {

    private final IpsecuzPet plugin;
    private final PaperNativeDialogEngine engine;

    public NativeDialogProvider(IpsecuzPet plugin) {
        this.plugin = plugin;
        this.engine = new PaperNativeDialogEngine(plugin);
    }

    public PaperNativeDialogEngine getEngine() {
        return engine;
    }

    public boolean isServerSupported() {
        return engine.isAvailable();
    }

    @Override
    public String getName() {
        return "NATIVE";
    }

    public int getClientProtocol(Player player) {
        if (player == null || !player.isOnline()) return -1;
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("ViaVersion")) {
                Class<?> viaClass = Class.forName("com.viaversion.viaversion.api.Via");
                Object api = viaClass.getMethod("getAPI").invoke(null);
                Method getVer = api.getClass().getMethod("getPlayerVersion", UUID.class);
                return (int) getVer.invoke(api, player.getUniqueId());
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    public boolean isClientSupported(Player player) {
        if (player == null || !player.isOnline()) return false;
        int clientProtocol = getClientProtocol(player);
        if (clientProtocol != -1) {
            // Minecraft 1.21.6+ protocol: 771 (1.21.6), 772 (1.21.7+), or snapshot bit (1 << 30)
            return clientProtocol >= 771 || (clientProtocol & (1 << 30)) != 0;
        }

        // Fallback when ViaVersion is not present: check server version string
        String sVer = Bukkit.getBukkitVersion();
        return sVer.contains("1.21.6") || sVer.contains("1.21.7") || sVer.contains("1.21.8")
                || sVer.contains("1.21.9") || sVer.contains("1.21.10") || sVer.contains("1.21.11")
                || sVer.contains("1.22") || sVer.contains("1.23") || sVer.contains("1.24");
    }

    @Override
    public boolean isSupported(Player player) {
        return isServerSupported() && isClientSupported(player);
    }

    private final java.util.concurrent.ConcurrentHashMap<String, Long> fallbackLogRateLimitMap = new java.util.concurrent.ConcurrentHashMap<>();

    private void fallback(Player player, Consumer<Player> fallbackAction) {
        if (isSupported(player)) {
            String reason = engine.getLastFailureReason();
            String stage = engine.getLastFailureStage() != null ? engine.getLastFailureStage().name() : "UNKNOWN";
            String rootEx = engine.getLastRootExceptionType();
            String pName = player != null ? player.getName() : "unknown";
            String key = pName + "_" + stage;
            long now = System.currentTimeMillis();
            Long lastLog = fallbackLogRateLimitMap.get(key);
            if (lastLog == null || (now - lastLog) > 5000) {
                fallbackLogRateLimitMap.put(key, now);
                if (plugin != null) {
                    plugin.getLogger().warning("[NativeDialogProvider] Failed to open native dialog for " + pName
                            + ", falling back to Chest GUI. Stage: " + stage
                            + ", Reason: " + (reason != null ? reason : "Unknown")
                            + (rootEx != null && !"None".equals(rootEx) ? ", Root: " + rootEx + ": " + engine.getLastRootExceptionMessage() : ""));
                }
            }
        }
        SchedulerUtils.runEntityTask(plugin, player, () -> fallbackAction.accept(player));
    }

    // --- Public DialogProvider API ---

    @Override
    public void openNotice(Player player, String title, String message, Runnable onClose) {
        if (!isSupported(player)) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openNotice(p, title, message, onClose));
            return;
        }
        try {
            boolean displayed = showDynamicNoticeDialog(player, title, message, onClose);
            if (!displayed) {
                fallback(player, p -> plugin.getDialogService().getFallbackProvider().openNotice(p, title, message, onClose));
            }
        } catch (Throwable t) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openNotice(p, title, message, onClose));
        }
    }

    @Override
    public void openConfirmation(Player player, String title, String message, String confirmText, String cancelText, Consumer<Boolean> callback) {
        if (!isSupported(player)) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openConfirmation(p, title, message, confirmText, cancelText, callback));
            return;
        }
        try {
            boolean displayed = showDynamicConfirmDialog(player, title, message, confirmText, cancelText, callback);
            if (!displayed) {
                fallback(player, p -> plugin.getDialogService().getFallbackProvider().openConfirmation(p, title, message, confirmText, cancelText, callback));
            }
        } catch (Throwable t) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openConfirmation(p, title, message, confirmText, cancelText, callback));
        }
    }

    @Override
    public void openPetHub(Player player, DialogSession session) {
        if (!isSupported(player)) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openPetHub(p, session));
            return;
        }
        try {
            boolean displayed = showDynamicHubDialog(player, session);
            if (!displayed) {
                fallback(player, p -> plugin.getDialogService().getFallbackProvider().openPetHub(p, session));
            }
        } catch (Throwable t) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openPetHub(p, session));
        }
    }

    @Override
    public void openPetDetails(Player player, DialogSession session, String petId) {
        if (!isSupported(player)) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openPetDetails(p, session, petId));
            return;
        }
        try {
            boolean displayed = showDynamicDetailDialog(player, session, petId);
            if (!displayed) {
                fallback(player, p -> plugin.getDialogService().getFallbackProvider().openPetDetails(p, session, petId));
            }
        } catch (Throwable t) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openPetDetails(p, session, petId));
        }
    }

    @Override
    public void openRenameDialog(Player player, DialogSession session, String petId) {
        if (!isSupported(player)) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openRenameDialog(p, session, petId));
            return;
        }
        try {
            boolean displayed = showDynamicRenameDialog(player, session, petId);
            if (!displayed) {
                fallback(player, p -> plugin.getDialogService().getFallbackProvider().openRenameDialog(p, session, petId));
            }
        } catch (Throwable t) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openRenameDialog(p, session, petId));
        }
    }

    @Override
    public void openEvolutionDialog(Player player, DialogSession session, String petId) {
        if (!isSupported(player)) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openEvolutionDialog(p, session, petId));
            return;
        }
        try {
            boolean displayed = showDynamicEvolutionDialog(player, session, petId);
            if (!displayed) {
                fallback(player, p -> plugin.getDialogService().getFallbackProvider().openEvolutionDialog(p, session, petId));
            }
        } catch (Throwable t) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openEvolutionDialog(p, session, petId));
        }
    }

    @Override
    public void openShopConfirmDialog(Player player, DialogSession session, String petId) {
        if (!isSupported(player)) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openShopConfirmDialog(p, session, petId));
            return;
        }
        try {
            boolean displayed = showDynamicShopConfirmDialog(player, session, petId);
            if (!displayed) {
                fallback(player, p -> plugin.getDialogService().getFallbackProvider().openShopConfirmDialog(p, session, petId));
            }
        } catch (Throwable t) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openShopConfirmDialog(p, session, petId));
        }
    }

    @Override
    public void openHatchConfirmDialog(Player player, DialogSession session, String eggId) {
        if (!isSupported(player)) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openHatchConfirmDialog(p, session, eggId));
            return;
        }
        try {
            boolean displayed = showDynamicHatchConfirmDialog(player, session, eggId);
            if (!displayed) {
                fallback(player, p -> plugin.getDialogService().getFallbackProvider().openHatchConfirmDialog(p, session, eggId));
            }
        } catch (Throwable t) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openHatchConfirmDialog(p, session, eggId));
        }
    }

    @Override
    public void openPetGuide(Player player, DialogSession session, int chapterIndex) {
        if (!isSupported(player)) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openPetGuide(p, session, chapterIndex));
            return;
        }
        try {
            boolean displayed = showDynamicGuideDialog(player, session, chapterIndex);
            if (!displayed) {
                fallback(player, p -> plugin.getDialogService().getFallbackProvider().openPetGuide(p, session, chapterIndex));
            }
        } catch (Throwable t) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openPetGuide(p, session, chapterIndex));
        }
    }

    @Override
    public void openSettingsDialog(Player player, DialogSession session) {
        if (!isSupported(player)) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openSettingsDialog(p, session));
            return;
        }
        try {
            boolean displayed = showDynamicSettingsDialog(player, session);
            if (!displayed) {
                fallback(player, p -> plugin.getDialogService().getFallbackProvider().openSettingsDialog(p, session));
            }
        } catch (Throwable t) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openSettingsDialog(p, session));
        }
    }

    @Override
    public void openTradeInviteDialog(Player player, DialogSession session, Player sender) {
        if (!isSupported(player)) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openTradeInviteDialog(p, session, sender));
            return;
        }
        try {
            boolean displayed = showDynamicTradeInviteDialog(player, session, sender);
            if (!displayed) {
                fallback(player, p -> plugin.getDialogService().getFallbackProvider().openTradeInviteDialog(p, session, sender));
            }
        } catch (Throwable t) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openTradeInviteDialog(p, session, sender));
        }
    }

    @Override
    public void openTradeConfirmDialog(Player player, DialogSession session, UUID tradeId, List<ItemStack> myItems, List<ItemStack> theirItems, Consumer<Boolean> callback) {
        if (!isSupported(player)) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openTradeConfirmDialog(p, session, tradeId, myItems, theirItems, callback));
            return;
        }
        try {
            boolean displayed = showDynamicTradeConfirmDialog(player, session, tradeId, myItems, theirItems, callback);
            if (!displayed) {
                fallback(player, p -> plugin.getDialogService().getFallbackProvider().openTradeConfirmDialog(p, session, tradeId, myItems, theirItems, callback));
            }
        } catch (Throwable t) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openTradeConfirmDialog(p, session, tradeId, myItems, theirItems, callback));
        }
    }

    @Override
    public void openMyPets(Player player, DialogSession session, int page) {
        if (!isSupported(player)) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openMyPets(p, session, page));
            return;
        }
        try {
            boolean displayed = showDynamicMyPets(player, session, page);
            if (!displayed) {
                fallback(player, p -> plugin.getDialogService().getFallbackProvider().openMyPets(p, session, page));
            }
        } catch (Throwable t) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openMyPets(p, session, page));
        }
    }

    @Override
    public void openShopBrowse(Player player, DialogSession session, int page) {
        if (!isSupported(player)) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openShopBrowse(p, session, page));
            return;
        }
        try {
            boolean displayed = showDynamicShopBrowse(player, session, page);
            if (!displayed) {
                fallback(player, p -> plugin.getDialogService().getFallbackProvider().openShopBrowse(p, session, page));
            }
        } catch (Throwable t) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openShopBrowse(p, session, page));
        }
    }

    @Override
    public void openHatchSelect(Player player, DialogSession session) {
        if (!isSupported(player)) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openHatchSelect(p, session));
            return;
        }
        try {
            boolean displayed = showDynamicHatchSelect(player, session);
            if (!displayed) {
                fallback(player, p -> plugin.getDialogService().getFallbackProvider().openHatchSelect(p, session));
            }
        } catch (Throwable t) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openHatchSelect(p, session));
        }
    }

    @Override
    public void openCodexBrowse(Player player, DialogSession session, int page) {
        if (!isSupported(player)) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openCodexBrowse(p, session, page));
            return;
        }
        try {
            boolean displayed = showDynamicCodexBrowse(player, session, page);
            if (!displayed) {
                fallback(player, p -> plugin.getDialogService().getFallbackProvider().openCodexBrowse(p, session, page));
            }
        } catch (Throwable t) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openCodexBrowse(p, session, page));
        }
    }

    @Override
    public void openShardsOverview(Player player, DialogSession session) {
        if (!isSupported(player)) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openShardsOverview(p, session));
            return;
        }
        try {
            boolean displayed = showDynamicShardsOverview(player, session);
            if (!displayed) {
                fallback(player, p -> plugin.getDialogService().getFallbackProvider().openShardsOverview(p, session));
            }
        } catch (Throwable t) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openShardsOverview(p, session));
        }
    }

    // --- Dynamic Native Dialog Renderers ---

    public boolean showDynamicNoticeDialog(Player player, String title, String message, Runnable onClose) {
        List<Component> body = new ArrayList<>();
        if (message != null) {
            for (String line : message.split("\n")) {
                body.add(GuiText.component("&7" + line));
            }
        }
        return engine.showNotice(player, GuiText.title(title), body, GuiText.component(plugin.getLanguage().getMessageOrDefault("dialog.btn_ok", "&a✓ OK")), onClose);
    }

    public boolean showDynamicConfirmDialog(Player player, String title, String message, String confirmText, String cancelText, Consumer<Boolean> callback) {
        List<Component> body = new ArrayList<>();
        if (message != null) {
            for (String line : message.split("\n")) {
                body.add(GuiText.component("&7" + line));
            }
        }
        String okLabel = confirmText != null ? confirmText : plugin.getLanguage().getMessageOrDefault("dialog.btn_confirm", "&a✓ Xác nhận");
        String cancelLabel = cancelText != null ? cancelText : plugin.getLanguage().getMessageOrDefault("dialog.btn_cancel", "&c✘ Hủy bỏ");
        return engine.showConfirmation(player, GuiText.title(title), body, GuiText.component(okLabel), GuiText.component(cancelLabel), callback);
    }

    public boolean showDynamicHubDialog(Player player, DialogSession session) {
        LanguageManager lang = plugin.getLanguage();
        String activePetId = plugin.getPetManager().getActivePetId(player.getUniqueId());
        int maxPets = plugin.getConfig().getInt("max_pets", 2);
        List<String> owned = plugin.getOwnershipManager().getOwnedPetIds(player.getUniqueId());

        List<Component> body = new ArrayList<>();
        body.add(GuiText.component("&e" + lang.getMessage("dialog.hub_welcome")));
        body.add(Component.empty());

        if (activePetId != null) {
            String pName = plugin.getConfigManager().getCustomName(player.getUniqueId(), activePetId);
            if (pName == null || pName.isEmpty()) pName = lang.getPetName(activePetId);
            int lvl = plugin.getConfigManager().getData().getInt(player.getUniqueId() + ".pets." + activePetId + ".level", 1);
            int stars = plugin.getConfigManager().getData().getInt(player.getUniqueId() + ".pets." + activePetId + ".stars", 1);
            int happy = plugin.getConfigManager().getData().getInt(player.getUniqueId() + ".pets." + activePetId + ".happiness", 100);
            body.add(GuiText.component("&a" + lang.getMessage("dialog.hub_active_pet") + " &f" + pName));
            body.add(GuiText.component("&7- " + lang.getMessage("gui.lore_level") + ": &fLv." + lvl + " &7| " + lang.getMessage("gui.lore_stars") + ": &6" + stars + " ⭐"));
            body.add(GuiText.component("&7- " + lang.getMessage("gui.lore_happiness") + ": &a" + happy + "%"));
        } else {
            body.add(GuiText.component("&7" + lang.getMessage("dialog.hub_no_active_pet")));
        }
        body.add(GuiText.component("&8" + lang.getMessage("dialog.hub_owned_pets", "%count%", String.valueOf(owned.size()), "%max%", String.valueOf(maxPets))));

        List<NativeButton> buttons = new ArrayList<>();
        buttons.add(new NativeButton(GuiText.component(lang.getMessage("dialog.btn_my_pets")), null, 140, (p, resp) -> openMyPets(p, session, 1)));
        buttons.add(new NativeButton(GuiText.component(lang.getMessage("dialog.btn_details")), null, 140, (p, resp) -> {
            if (activePetId != null) {
                openPetDetails(p, session, activePetId);
            } else {
                openMyPets(p, session, 1);
            }
        }));
        buttons.add(new NativeButton(GuiText.component(lang.getMessage("dialog.btn_shop")), null, 140, (p, resp) -> openShopBrowse(p, session, 1)));
        buttons.add(new NativeButton(GuiText.component(lang.getMessage("dialog.btn_hatch")), null, 140, (p, resp) -> openHatchSelect(p, session)));
        buttons.add(new NativeButton(GuiText.component(lang.getMessage("dialog.btn_codex")), null, 140, (p, resp) -> openCodexBrowse(p, session, 1)));
        buttons.add(new NativeButton(GuiText.component(lang.getMessage("gui.btn_shards")), null, 140, (p, resp) -> openShardsOverview(p, session)));
        buttons.add(new NativeButton(GuiText.component(lang.getMessage("dialog.btn_guide")), null, 140, (p, resp) -> openPetGuide(p, session, -1)));
        buttons.add(new NativeButton(GuiText.component(lang.getMessage("dialog.btn_settings")), null, 140, (p, resp) -> openSettingsDialog(p, session)));

        if (activePetId != null) {
            buttons.add(new NativeButton(GuiText.component(lang.getMessageOrDefault("dialog.btn_despawn", "&c✘ Cất Pet")), null, 140, (p, resp) -> {
                plugin.getPetManager().removePet(p.getUniqueId());
                openPetHub(p, session);
            }));
        }

        NativeButton exitBtn = new NativeButton(GuiText.component(lang.getMessageOrDefault("dialog.btn_close", "&c✘ Đóng")), null, 100, (p, resp) -> {});
        return engine.showMultiAction(player, GuiText.title(lang.getMessage("dialog.hub_title")), body, null, buttons, exitBtn, 2);
    }

    public boolean showDynamicMyPets(Player player, DialogSession session, int page) {
        LanguageManager lang = plugin.getLanguage();
        List<String> owned = plugin.getOwnershipManager().getOwnedPetIds(player.getUniqueId());
        int pageSize = 6;
        int totalPages = Math.max(1, (int) Math.ceil((double) owned.size() / pageSize));
        int curPage = Math.max(1, Math.min(page, totalPages));

        List<Component> body = new ArrayList<>();
        body.add(GuiText.component("&7" + lang.getMessageOrDefault("dialog.my_pets_desc", "Danh sách các thú cưng bạn đã thuần hóa và sở hữu.")));
        body.add(GuiText.component(lang.getMessageOrDefault("dialog.page_info", "&7Trang: &e%page%&7/&6%total% &8(Tổng: %count%)", "%page%", String.valueOf(curPage), "%total%", String.valueOf(totalPages), "%count%", String.valueOf(owned.size()))));

        List<NativeButton> buttons = new ArrayList<>();
        int start = (curPage - 1) * pageSize;
        int end = Math.min(start + pageSize, owned.size());

        for (int i = start; i < end; i++) {
            String petId = owned.get(i);
            String custom = plugin.getConfigManager().getCustomName(player.getUniqueId(), petId);
            String name = (custom != null && !custom.isEmpty()) ? custom : lang.getPetName(petId);
            int lvl = plugin.getConfigManager().getData().getInt(player.getUniqueId() + ".pets." + petId + ".level", 1);
            int stars = plugin.getConfigManager().getData().getInt(player.getUniqueId() + ".pets." + petId + ".stars", 1);
            String btnText = "⭐" + stars + " " + name + " (Lv." + lvl + ")";
            buttons.add(new NativeButton(GuiText.component("&a" + btnText), null, 180, (p, resp) -> openPetDetails(p, session, petId)));
        }

        if (curPage > 1) {
            buttons.add(new NativeButton(GuiText.component(lang.getMessageOrDefault("dialog.btn_prev_page", "&c◀ Trang trước")), null, 120, (p, resp) -> openMyPets(p, session, curPage - 1)));
        }
        if (curPage < totalPages) {
            buttons.add(new NativeButton(GuiText.component(lang.getMessageOrDefault("dialog.btn_next_page", "&aTrang sau ▶")), null, 120, (p, resp) -> openMyPets(p, session, curPage + 1)));
        }

        NativeButton exitBtn = new NativeButton(GuiText.component(lang.getMessageOrDefault("dialog.btn_hub", "&6🏠 Pet Hub")), null, 120, (p, resp) -> openPetHub(p, session));
        return engine.showMultiAction(player, GuiText.title(lang.getMessageOrDefault("dialog.my_pets_title", "&8&lKHO THÚ CƯNG &7(My Pets)")), body, null, buttons, exitBtn, 2);
    }

    public boolean showDynamicDetailDialog(Player player, DialogSession session, String petId) {
        if (petId == null) return false;
        LanguageManager lang = plugin.getLanguage();

        String custom = plugin.getConfigManager().getCustomName(player.getUniqueId(), petId);
        String name = (custom != null && !custom.isEmpty()) ? custom : lang.getPetName(petId);
        int lvl = plugin.getConfigManager().getData().getInt(player.getUniqueId() + ".pets." + petId + ".level", 1);
        int exp = plugin.getConfigManager().getData().getInt(player.getUniqueId() + ".pets." + petId + ".exp", 0);
        int stars = plugin.getConfigManager().getData().getInt(player.getUniqueId() + ".pets." + petId + ".stars", 1);
        int happy = plugin.getConfigManager().getData().getInt(player.getUniqueId() + ".pets." + petId + ".happiness", 100);
        String trait = plugin.getConfigManager().getData().getString(player.getUniqueId() + ".pets." + petId + ".trait", "NONE");
        String traitLocalized = PetTrait.fromString(trait).getLocalizedName(plugin);

        double dmg = PetStatEngine.calculateStatPreview(plugin, petId, lvl, stars, "damage");
        double hp = PetStatEngine.calculateStatPreview(plugin, petId, lvl, stars, "health");
        double def = PetStatEngine.calculateStatPreview(plugin, petId, lvl, stars, "defense");
        double spd = PetStatEngine.calculateStatPreview(plugin, petId, lvl, stars, "speed");

        boolean isActive = petId.equals(plugin.getPetManager().getActivePetId(player.getUniqueId()));
        boolean isBaby = plugin.getConfigManager().isPetBaby(player.getUniqueId(), petId);

        List<Component> body = new ArrayList<>();
        body.add(GuiText.component("&a&l" + name + " &8[" + petId + "]"));
        body.add(GuiText.component("&e" + lang.getMessage("gui.lore_level") + ": &fLv." + lvl + " &8(EXP: " + exp + ") &7| &6" + lang.getMessage("gui.lore_stars") + ": &f" + stars + " ⭐"));
        body.add(GuiText.component("&c" + lang.getMessage("gui.lore_happiness") + ": &f" + happy + "% &7| &b" + lang.getMessage("gui.lore_trait") + ": &f" + traitLocalized));
        body.add(GuiText.component("&c⚔ " + lang.getMessageOrDefault("gui.lore_damage", "Sát thương") + ": &f" + String.format("%.1f", dmg) + " &7| &a❤ " + lang.getMessageOrDefault("gui.lore_health", "Máu") + ": &f" + String.format("%.1f", hp)));
        body.add(GuiText.component("&9🛡 " + lang.getMessageOrDefault("gui.lore_defense", "Giáp") + ": &f" + String.format("%.1f", def) + " &7| &e⚡ " + lang.getMessageOrDefault("gui.lore_speed", "Tốc độ") + ": &f" + String.format("%.2f", spd)));

        List<NativeButton> buttons = new ArrayList<>();
        if (isActive) {
            buttons.add(new NativeButton(GuiText.component(lang.getMessageOrDefault("dialog.btn_despawn", "&c✘ Cất Thú Cưng")), null, 140, (p, resp) -> {
                plugin.getPetManager().removePet(p.getUniqueId());
                openPetDetails(p, session, petId);
            }));
        } else {
            buttons.add(new NativeButton(GuiText.component(lang.getMessageOrDefault("dialog.btn_summon", "&a✔ Triệu Hồi Đồng Hành")), null, 140, (p, resp) -> {
                plugin.getPetManager().spawnPet(p, petId);
                openPetDetails(p, session, petId);
            }));
        }

        buttons.add(new NativeButton(GuiText.component(lang.getMessageOrDefault("dialog.btn_rename", "&6✏ Đổi Tên")), null, 140, (p, resp) -> openRenameDialog(p, session, petId)));
        buttons.add(new NativeButton(GuiText.component(lang.getMessageOrDefault(isBaby ? "dialog.btn_mode_adult" : "dialog.btn_mode_baby", isBaby ? "&d✦ Đổi: Trưởng Thành" : "&b✦ Đổi: Bé Con")), null, 140, (p, resp) -> {
            plugin.getConfigManager().setPetBaby(p.getUniqueId(), petId, !isBaby);
            if (isActive) plugin.getPetManager().spawnPet(p, petId);
            openPetDetails(p, session, petId);
        }));
        buttons.add(new NativeButton(GuiText.component(lang.getMessageOrDefault("dialog.btn_evolution", "&6⭐ Tiến Hóa Sao")), null, 140, (p, resp) -> openEvolutionDialog(p, session, petId)));
        buttons.add(new NativeButton(GuiText.component(lang.getMessageOrDefault("dialog.btn_feed", "&e🍖 Cho Ăn")), null, 140, (p, resp) -> {
            ItemStack hand = p.getInventory().getItemInMainHand();
            plugin.getFeedingManager().feedPet(p, petId, hand);
            openPetDetails(p, session, petId);
        }));

        NativeButton exitBtn = new NativeButton(GuiText.component(lang.getMessageOrDefault("dialog.btn_back", "&7◀ Quay lại")), null, 100, (p, resp) -> openMyPets(p, session, 1));
        return engine.showMultiAction(player, GuiText.title(lang.getMessageOrDefault("dialog.detail_title", "&8&lCHI TIẾT: &6%name%", "%name%", name, "%pet_name%", name)), body, null, buttons, exitBtn, 2);
    }

    public boolean showDynamicRenameDialog(Player player, DialogSession session, String petId) {
        if (petId == null) return false;
        LanguageManager lang = plugin.getLanguage();

        List<Component> body = new ArrayList<>();
        body.add(GuiText.component("&e" + lang.getMessageOrDefault("dialog.rename_desc_1", "Nhập tên mới cho thú cưng vào ô bên dưới.")));
        body.add(GuiText.component("&7" + lang.getMessageOrDefault("dialog.rename_desc_2", "Độ dài từ 2 đến 16 ký tự. Hỗ trợ mã màu &a&b&c...")));

        List<Object> inputs = new ArrayList<>();
        try {
            String curName = plugin.getConfigManager().getCustomName(player.getUniqueId(), petId);
            if (curName == null) curName = "";
            Object textInput = engine.createTextInput("name_input", GuiText.component(lang.getMessageOrDefault("dialog.rename_input_label", "&eTên Mới:")), 200, 16, curName);
            if (textInput != null) inputs.add(textInput);
        } catch (Throwable t) {
            return false;
        }

        List<NativeButton> buttons = new ArrayList<>();
        buttons.add(new NativeButton(GuiText.component(lang.getMessageOrDefault("dialog.btn_save_name", "&a✓ Lưu Tên")), null, 120, (p, resp) -> {
            if (resp == null) return;
            String entered = resp.getText("name_input");
            if (session != null && !session.isValid()) {
                p.sendMessage(lang.getMessage("general.no_permission"));
                return;
            }
            if (plugin.getDialogService().processRename(p, petId, entered)) {
                openPetDetails(p, session, petId);
            }
        }));

        NativeButton exitBtn = new NativeButton(GuiText.component(lang.getMessageOrDefault("dialog.btn_cancel", "&c✘ Hủy Bỏ")), null, 100, (p, resp) -> openPetDetails(p, session, petId));
        return engine.showMultiAction(player, GuiText.title(lang.getMessage("dialog.rename_title")), body, inputs, buttons, exitBtn, 1);
    }

    public boolean showDynamicEvolutionDialog(Player player, DialogSession session, String petId) {
        if (petId == null) return false;
        LanguageManager lang = plugin.getLanguage();

        int curLevel = plugin.getConfigManager().getData().getInt(player.getUniqueId() + ".pets." + petId + ".level", 1);
        int curStars = plugin.getConfigManager().getData().getInt(player.getUniqueId() + ".pets." + petId + ".stars", 1);
        int nextStars = Math.min(5, curStars + 1);

        double curDmg = PetStatEngine.calculateStatPreview(plugin, petId, curLevel, curStars, "damage");
        double nextDmg = PetStatEngine.calculateStatPreview(plugin, petId, curLevel, nextStars, "damage");
        double curHp = PetStatEngine.calculateStatPreview(plugin, petId, curLevel, curStars, "health");
        double nextHp = PetStatEngine.calculateStatPreview(plugin, petId, curLevel, nextStars, "health");
        double curDef = PetStatEngine.calculateStatPreview(plugin, petId, curLevel, curStars, "defense");
        double nextDef = PetStatEngine.calculateStatPreview(plugin, petId, curLevel, nextStars, "defense");
        double curSpd = PetStatEngine.calculateStatPreview(plugin, petId, curLevel, curStars, "speed");
        double nextSpd = PetStatEngine.calculateStatPreview(plugin, petId, curLevel, nextStars, "speed");

        List<Component> body = new ArrayList<>();
        body.add(GuiText.component("&7" + lang.getMessageOrDefault("dialog.evo_stars_desc", "Cấp sao: %current% ⭐ ➔ %next% ⭐", "%current%", String.valueOf(curStars), "%next%", String.valueOf(nextStars))));
        body.add(GuiText.component("&c⚔ " + lang.getMessageOrDefault("gui.lore_damage", "Sát thương") + ": &f" + String.format("%.1f", curDmg) + " &7➔ &a" + String.format("%.1f", nextDmg)));
        body.add(GuiText.component("&a❤ " + lang.getMessageOrDefault("gui.lore_health", "Máu") + ": &f" + String.format("%.1f", curHp) + " &7➔ &a" + String.format("%.1f", nextHp)));
        body.add(GuiText.component("&9🛡 " + lang.getMessageOrDefault("gui.lore_defense", "Giáp") + ": &f" + String.format("%.1f", curDef) + " &7➔ &a" + String.format("%.1f", nextDef)));
        body.add(GuiText.component("&e⚡ " + lang.getMessageOrDefault("gui.lore_speed", "Tốc độ") + ": &f" + String.format("%.2f", curSpd) + " &7➔ &a" + String.format("%.2f", nextSpd)));
        body.add(GuiText.component("&e" + lang.getMessageOrDefault("dialog.evo_potential_note", "Tiến hóa sẽ gia tăng vĩnh viễn tiềm năng chiến đấu của Pet.")));

        List<NativeButton> buttons = new ArrayList<>();
        buttons.add(new NativeButton(GuiText.component(lang.getMessage("dialog.evo_confirm")), null, 140, (p, resp) -> {
            if (session != null && !session.isValid()) {
                p.sendMessage(lang.getMessage("general.no_permission"));
                return;
            }
            if (session != null) session.markSubmitted();
            if (plugin.getDialogService().processEvolution(p, petId)) {
                openPetDetails(p, session, petId);
            }
        }));

        NativeButton exitBtn = new NativeButton(GuiText.component(lang.getMessage("dialog.evo_cancel")), null, 100, (p, resp) -> openPetDetails(p, session, petId));
        return engine.showMultiAction(player, GuiText.title(lang.getMessage("dialog.evolution_title")), body, null, buttons, exitBtn, 1);
    }

    public boolean showDynamicShopBrowse(Player player, DialogSession session, int page) {
        LanguageManager lang = plugin.getLanguage();
        List<String> petIds = plugin.getDialogService().getShopEligiblePets(player);

        if (petIds.isEmpty()) {
            List<Component> body = new ArrayList<>();
            body.add(GuiText.component("&e" + lang.getMessageOrDefault("dialog.shop_welcome", "Chào mừng bạn đến với Cửa Hàng Thú Cưng!")));
            body.add(GuiText.component("&7" + lang.getMessageOrDefault("dialog.shop_empty", "Hiện tại không có thú cưng nào được bày bán trong cửa hàng.")));

            NativeButton exitBtn = new NativeButton(GuiText.component(lang.getMessageOrDefault("dialog.btn_hub", "&6🏠 Pet Hub")), null, 120, (p, resp) -> openPetHub(p, session));
            return engine.showMultiAction(player, GuiText.title(lang.getMessageOrDefault("dialog.shop_title", "&8&lCỬA HÀNG THÚ CƯNG")), body, null, List.of(), exitBtn, 1);
        }

        int pageSize = 6;
        int totalPages = Math.max(1, (int) Math.ceil((double) petIds.size() / pageSize));
        int curPage = Math.max(1, Math.min(page, totalPages));

        List<Component> body = new ArrayList<>();
        body.add(GuiText.component("&e" + lang.getMessageOrDefault("dialog.shop_welcome", "Chào mừng bạn đến với Cửa Hàng Thú Cưng!")));
        body.add(GuiText.component(lang.getMessageOrDefault("dialog.shop_select_hint", "&7Chọn pet để xem chi tiết và mua. Trang: &e%page%&7/&6%total%", "%page%", String.valueOf(curPage), "%total%", String.valueOf(totalPages))));

        List<NativeButton> buttons = new ArrayList<>();
        int start = (curPage - 1) * pageSize;
        int end = Math.min(start + pageSize, petIds.size());

        for (int i = start; i < end; i++) {
            String petId = petIds.get(i);
            String name = lang.getPetName(petId);
            double price = plugin.getConfig().getDouble("pets." + petId + ".price", 1000.0);
            boolean owned = plugin.getOwnershipManager().hasPet(player.getUniqueId(), petId);
            String status = owned ? lang.getMessageOrDefault("dialog.shop_status_owned", "&a[Đã sở hữu]") : "&e[" + (long) price + "$]";
            buttons.add(new NativeButton(GuiText.component("&6" + name + " " + status), null, 180, (p, resp) -> {
                if (!owned) {
                    openShopConfirmDialog(p, session, petId);
                } else {
                    p.sendMessage(lang.getMessage("pet.already_owned"));
                }
            }));
        }

        if (curPage > 1) {
            buttons.add(new NativeButton(GuiText.component(lang.getMessageOrDefault("dialog.btn_prev_page", "&c◀ Trang trước")), null, 120, (p, resp) -> openShopBrowse(p, session, curPage - 1)));
        }
        if (curPage < totalPages) {
            buttons.add(new NativeButton(GuiText.component(lang.getMessageOrDefault("dialog.btn_next_page", "&aTrang sau ▶")), null, 120, (p, resp) -> openShopBrowse(p, session, curPage + 1)));
        }

        NativeButton exitBtn = new NativeButton(GuiText.component(lang.getMessageOrDefault("dialog.btn_hub", "&6🏠 Pet Hub")), null, 120, (p, resp) -> openPetHub(p, session));
        return engine.showMultiAction(player, GuiText.title(lang.getMessageOrDefault("dialog.shop_title", "&8&lCỬA HÀNG THÚ CƯNG")), body, null, buttons, exitBtn, 2);
    }

    public boolean showDynamicShopConfirmDialog(Player player, DialogSession session, String petId) {
        if (petId == null) return false;
        LanguageManager lang = plugin.getLanguage();

        String name = lang.getPetName(petId);
        double price = plugin.getConfig().getDouble("pets." + petId + ".price", 1000.0);
        String currency = plugin.getConfig().getString("pets." + petId + ".currency", "VAULT");

        List<Component> body = new ArrayList<>();
        body.add(GuiText.component("&e" + lang.getMessageOrDefault("dialog.shop_confirm_desc", "Bạn có chắc chắn muốn mua thú cưng này?")));
        body.add(GuiText.component(lang.getMessageOrDefault("dialog.shop_pet_label", "&7Thú cưng: &6&l%name%", "%name%", name)));
        body.add(GuiText.component(lang.getMessageOrDefault("dialog.shop_price_label", "&7Giá bán: &a%price% &e(%currency%)", "%price%", String.valueOf((long) price), "%currency%", currency)));

        List<NativeButton> buttons = new ArrayList<>();
        buttons.add(new NativeButton(GuiText.component(lang.getMessageOrDefault("dialog.shop_confirm_buy", "&a✓ MUA NGAY")), null, 140, (p, resp) -> {
            if (session != null && !session.isValid()) {
                p.sendMessage(lang.getMessage("general.no_permission"));
                return;
            }
            if (session != null) session.markSubmitted();
            if (plugin.getDialogService().processShopPurchase(p, petId)) {
                openShopBrowse(p, session, 1);
            }
        }));

        NativeButton exitBtn = new NativeButton(GuiText.component(lang.getMessageOrDefault("dialog.shop_confirm_cancel", "&c✘ HỦY BỎ")), null, 100, (p, resp) -> openShopBrowse(p, session, 1));
        return engine.showMultiAction(player, GuiText.title(lang.getMessageOrDefault("dialog.shop_confirm_title", "&8&lXÁC NHẬN MUA PET")), body, null, buttons, exitBtn, 1);
    }

    public boolean showDynamicHatchSelect(Player player, DialogSession session) {
        LanguageManager lang = plugin.getLanguage();
        List<Component> body = new ArrayList<>();
        body.add(GuiText.component("&e" + lang.getMessageOrDefault("dialog.hatch_select_desc", "Chọn loại Trứng để tiến hành ấp và quay Roulette!")));
        body.add(GuiText.component("&7" + lang.getMessageOrDefault("dialog.hatch_select_sub", "Mỗi loại trứng chứa những linh hồn thú cưng độc nhất.")));

        List<NativeButton> buttons = new ArrayList<>();
        if (plugin.getModuleManager() != null && plugin.getModuleManager().getHatchingConfig() != null) {
            var eggs = plugin.getModuleManager().getHatchingConfig().getConfigurationSection("eggs");
            if (eggs != null) {
                for (String eggId : eggs.getKeys(false)) {
                    ConfigurationSection eggSec = eggs.getConfigurationSection(eggId);
                    String eggName = eggSec != null ? eggSec.getString("name", eggId) : eggId;
                    String eggKeyName = "eggs." + eggId + ".name";
                    if (lang != null && !lang.getRawMessage(eggKeyName).startsWith("§cMissing")) {
                        eggName = lang.getRawMessage(eggKeyName);
                    }
                    buttons.add(new NativeButton(GuiText.component("&e" + eggName), null, 180, (p, resp) -> openHatchConfirmDialog(p, session, eggId)));
                }
            }
        }

        NativeButton exitBtn = new NativeButton(GuiText.component(lang.getMessageOrDefault("dialog.btn_hub", "&6🏠 Pet Hub")), null, 120, (p, resp) -> openPetHub(p, session));
        return engine.showMultiAction(player, GuiText.title(lang.getMessageOrDefault("dialog.hatch_select_title", "&8&lLÒ ẤP TRỨNG")), body, null, buttons, exitBtn, 2);
    }

    public boolean showDynamicHatchConfirmDialog(Player player, DialogSession session, String eggId) {
        if (eggId == null) return false;
        LanguageManager lang = plugin.getLanguage();

        ConfigurationSection eggSec = (plugin.getModuleManager() != null && plugin.getModuleManager().getHatchingConfig() != null)
                ? plugin.getModuleManager().getHatchingConfig().getConfigurationSection("eggs." + eggId)
                : null;
        if (eggSec == null) return false;

        String eggName = eggSec.getString("name", eggId);
        String eggKeyName = "eggs." + eggId + ".name";
        if (lang != null && !lang.getRawMessage(eggKeyName).startsWith("§cMissing")) {
            eggName = lang.getRawMessage(eggKeyName);
        }

        List<Component> body = new ArrayList<>();
        body.add(GuiText.component(lang.getMessageOrDefault("dialog.hatch_confirm_target", "&7Trứng chuẩn bị ấp: &6&l%name%", "%name%", eggName)));
        body.add(Component.empty());

        // Egg lore / description
        List<String> transLore = (lang != null) ? lang.getMessageList("eggs." + eggId + ".lore") : null;
        List<String> rawLore = (transLore != null && !transLore.isEmpty()) ? transLore : eggSec.getStringList("lore");
        for (String l : rawLore) {
            body.add(GuiText.component(l));
        }

        // Requirements & Status
        boolean isFree = eggSec.getBoolean("free", false);
        org.ipsecuz.pet.requirement.RequirementGroup group = plugin.getRequirementManager().parse(eggSec);
        if (isFree) {
            body.add(Component.empty());
            body.add(GuiText.component(lang.getMessageOrDefault("dialog.hatch_cost_free", "&a✦ Chi phí: Miễn phí hoàn toàn!")));
        } else if (!group.getRequirements().isEmpty()) {
            body.add(Component.empty());
            body.add(GuiText.component(lang.getMessageOrDefault("dialog.hatch_cost_header", "&6✦ Chi phí để quay:")));
            org.ipsecuz.pet.requirement.RequirementContext ctx = new org.ipsecuz.pet.requirement.RequirementContext(player, plugin);
            org.ipsecuz.pet.requirement.RequirementCheckResult result = plugin.getRequirementManager().evaluate(group, ctx);
            for (String line : org.ipsecuz.pet.requirement.RequirementGuiRenderer.renderToStrings(result)) {
                if (!line.startsWith("§7----")) {
                    body.add(GuiText.component(line));
                }
            }
        }

        List<NativeButton> buttons = new ArrayList<>();
        buttons.add(new NativeButton(GuiText.component(lang.getMessageOrDefault("dialog.hatch_confirm", "&a▶ QUAY NGAY")), null, 140, (p, resp) -> {
            if (session != null && !session.isValid()) {
                p.sendMessage(lang.getMessage("general.no_permission"));
                return;
            }
            if (session != null) session.markSubmitted();
            plugin.getDialogService().processHatchConfirm(p, eggId);
        }));

        NativeButton exitBtn = new NativeButton(GuiText.component(lang.getMessageOrDefault("dialog.hatch_cancel", "&c✘ HỦY BỎ")), null, 100, (p, resp) -> openHatchSelect(p, session));
        return engine.showMultiAction(player, GuiText.title(lang.getMessageOrDefault("dialog.hatch_title", "&8&lLÒ ẤP TRỨNG")), body, null, buttons, exitBtn, 1);
    }

    public boolean showDynamicGuideDialog(Player player, DialogSession session, int chapterIndex) {
        LanguageManager lang = plugin.getLanguage();

        if (chapterIndex < 0) {
            List<Component> body = new ArrayList<>();
            body.add(GuiText.component("&e" + lang.getMessageOrDefault("dialog.guide_catalog_desc", "Danh mục 18 chương cẩm nang toàn diện hệ thống IpsecuzPet.")));

            List<NativeButton> buttons = new ArrayList<>();
            for (int i = 0; i < 18; i++) {
                final int idx = i;
                String chapterTitleKey = "dialog.guide." + "ch" + (i + 1) + "_title";
                String title = lang.getMessage(chapterTitleKey);
                if (title == null || title.startsWith("dialog.")) title = lang.getMessageOrDefault("dialog.guide_chapter_prefix", "Chương %num%", "%num%", String.valueOf(i + 1));
                buttons.add(new NativeButton(GuiText.component("&e" + (i + 1) + ". " + title), null, 200, (p, resp) -> openPetGuide(p, session, idx)));
            }

            NativeButton exitBtn = new NativeButton(GuiText.component(lang.getMessageOrDefault("dialog.btn_hub", "&6🏠 Pet Hub")), null, 120, (p, resp) -> openPetHub(p, session));
            return engine.showMultiAction(player, GuiText.title(lang.getMessage("dialog.guide_title")), body, null, buttons, exitBtn, 2);
        } else {
            String chapterTitleKey = "dialog.guide." + "ch" + (chapterIndex + 1) + "_title";
            String chapterBodyKey = "dialog.guide." + "ch" + (chapterIndex + 1) + "_body";
            String chTitle = lang.getMessage(chapterTitleKey);
            String chBody = lang.getMessage(chapterBodyKey);

            List<Component> body = new ArrayList<>();
            body.add(GuiText.component("&6&l" + lang.getMessageOrDefault("dialog.guide_chapter_prefix", "Chương %num%", "%num%", String.valueOf(chapterIndex + 1)) + ": " + (chTitle != null ? chTitle : "")));
            body.add(Component.empty());
            if (chBody != null) {
                for (String line : chBody.split("\n")) {
                    body.add(GuiText.component("&7" + line));
                }
            }

            List<NativeButton> buttons = new ArrayList<>();
            if (chapterIndex > 0) {
                buttons.add(new NativeButton(GuiText.component(lang.getMessage("dialog.guide_btn_prev")), null, 120, (p, resp) -> openPetGuide(p, session, chapterIndex - 1)));
            }
            buttons.add(new NativeButton(GuiText.component(lang.getMessage("dialog.guide_btn_hub")), null, 120, (p, resp) -> openPetGuide(p, session, -1)));
            if (chapterIndex < 17) {
                buttons.add(new NativeButton(GuiText.component(lang.getMessage("dialog.guide_btn_next")), null, 120, (p, resp) -> openPetGuide(p, session, chapterIndex + 1)));
            }

            NativeButton exitBtn = new NativeButton(GuiText.component(lang.getMessageOrDefault("dialog.btn_close", "&c✘ Đóng")), null, 100, (p, resp) -> {});
            return engine.showMultiAction(player, GuiText.title(lang.getMessage("dialog.guide_title")), body, null, buttons, exitBtn, 2);
        }
    }

    public boolean showDynamicSettingsDialog(Player player, DialogSession session) {
        LanguageManager lang = plugin.getLanguage();

        boolean curParticles = plugin.getPlayerSettingsManager().isParticlesEnabled(player.getUniqueId());
        boolean curSounds = plugin.getPlayerSettingsManager().isSoundsEnabled(player.getUniqueId());

        List<Component> body = new ArrayList<>();
        body.add(GuiText.component("&e" + lang.getMessageOrDefault("dialog.settings_desc", "Tùy chỉnh các hiệu ứng và sở thích trải nghiệm hệ thống Thú cưng.")));

        List<Object> inputs = new ArrayList<>();
        try {
            Object pInput = engine.createBoolInput("particles", GuiText.component("&b" + lang.getMessage("dialog.setting_particles")), curParticles, lang.getMessageOrDefault("dialog.toggle_on", "Bật"), lang.getMessageOrDefault("dialog.toggle_off", "Tắt"));
            Object sInput = engine.createBoolInput("sounds", GuiText.component("&a" + lang.getMessage("dialog.setting_sounds")), curSounds, lang.getMessageOrDefault("dialog.toggle_on", "Bật"), lang.getMessageOrDefault("dialog.toggle_off", "Tắt"));
            if (pInput != null) inputs.add(pInput);
            if (sInput != null) inputs.add(sInput);
        } catch (Throwable ignored) {}

        List<NativeButton> buttons = new ArrayList<>();
        buttons.add(new NativeButton(GuiText.component(lang.getMessageOrDefault("dialog.btn_save_settings", "&a✓ Lưu Cài Đặt")), null, 140, (p, resp) -> {
            if (resp != null) {
                Boolean pVal = resp.getBoolean("particles");
                Boolean sVal = resp.getBoolean("sounds");
                if (pVal != null) plugin.getPlayerSettingsManager().setParticlesEnabled(p.getUniqueId(), pVal);
                if (sVal != null) plugin.getPlayerSettingsManager().setSoundsEnabled(p.getUniqueId(), sVal);
                p.sendMessage(lang.getMessageOrDefault("dialog.settings_saved", "&aĐã lưu thiết lập cá nhân thành công!"));
            }
            openPetHub(p, session);
        }));

        NativeButton exitBtn = new NativeButton(GuiText.component(lang.getMessageOrDefault("dialog.btn_hub", "&6🏠 Pet Hub")), null, 120, (p, resp) -> openPetHub(p, session));
        return engine.showMultiAction(player, GuiText.title(lang.getMessage("dialog.settings_title")), body, inputs, buttons, exitBtn, 1);
    }

    public boolean showDynamicTradeInviteDialog(Player player, DialogSession session, Player sender) {
        LanguageManager lang = plugin.getLanguage();
        List<Component> body = new ArrayList<>();
        body.add(GuiText.component(lang.getMessageOrDefault("dialog.trade_invite_desc", "&eNgười chơi &f%player% &eđã gửi lời mời giao dịch Thú cưng cho bạn!", "%player%", sender.getName())));
        body.add(GuiText.component(lang.getMessageOrDefault("dialog.trade_secure_note", "&7Giao dịch diễn ra an toàn, có xác nhận và ghi nhận nhật ký 2 bước.")));

        List<NativeButton> buttons = new ArrayList<>();
        buttons.add(new NativeButton(GuiText.component(lang.getMessage("dialog.trade_accept")), null, 140, (p, resp) -> {
            plugin.getTradeManager().acceptTrade(p);
        }));

        NativeButton exitBtn = new NativeButton(GuiText.component(lang.getMessage("dialog.trade_decline")), null, 140, (p, resp) -> {
            p.sendMessage(lang.getMessage("trade.cancelled", "%reason%", lang.getMessageOrDefault("dialog.trade_decline_reason", "Từ chối lời mời")));
            sender.sendMessage(lang.getMessage("trade.cancelled", "%reason%", lang.getMessageOrDefault("dialog.trade_partner_declined", "Đối phương đã từ chối")));
        });

        return engine.showMultiAction(player, GuiText.title(lang.getMessage("dialog.trade_invite_title")), body, null, buttons, exitBtn, 2);
    }

    public boolean showDynamicTradeConfirmDialog(Player player, DialogSession session, UUID tradeId, List<ItemStack> myItems, List<ItemStack> theirItems, Consumer<Boolean> callback) {
        LanguageManager lang = plugin.getLanguage();
        List<Component> body = new ArrayList<>();
        body.add(GuiText.component(lang.getMessageOrDefault("dialog.trade_confirm_warning", "&eKiểm tra kỹ trước khi xác nhận bàn giao!")));
        body.add(GuiText.component(lang.getMessageOrDefault("dialog.trade_items_giving", "&aVật phẩm bạn giao: &f%count% món", "%count%", String.valueOf(myItems != null ? myItems.size() : 0))));
        body.add(GuiText.component(lang.getMessageOrDefault("dialog.trade_items_receiving", "&bVật phẩm nhận về: &f%count% món", "%count%", String.valueOf(theirItems != null ? theirItems.size() : 0))));

        List<NativeButton> buttons = new ArrayList<>();
        buttons.add(new NativeButton(GuiText.component(lang.getMessage("dialog.trade_confirm_commit")), null, 150, (p, resp) -> {
            if (callback != null) callback.accept(true);
        }));

        NativeButton exitBtn = new NativeButton(GuiText.component(lang.getMessage("dialog.trade_confirm_cancel")), null, 150, (p, resp) -> {
            if (callback != null) callback.accept(false);
        });

        return engine.showMultiAction(player, GuiText.title(lang.getMessage("dialog.trade_confirm_title")), body, null, buttons, exitBtn, 2);
    }

    public boolean showDynamicCodexBrowse(Player player, DialogSession session, int page) {
        LanguageManager lang = plugin.getLanguage();
        List<String> allPetKeys = new ArrayList<>();
        var petsSec = plugin.getConfig().getConfigurationSection("pets");
        if (petsSec != null) {
            allPetKeys.addAll(petsSec.getKeys(false));
        }

        int pageSize = 6;
        int totalPages = Math.max(1, (int) Math.ceil((double) allPetKeys.size() / pageSize));
        int curPage = Math.max(1, Math.min(page, totalPages));

        int discovered = plugin.getCodexManager().getDiscoveredCount(player.getUniqueId());
        int total = allPetKeys.size();
        int percent = total > 0 ? (discovered * 100 / total) : 0;

        List<Component> body = new ArrayList<>();
        body.add(GuiText.component("&d" + lang.getMessageOrDefault("dialog.codex_desc", "Bách Khoa Toàn Thư Codex Thú Cưng")));
        body.add(GuiText.component(lang.getMessageOrDefault("dialog.codex_progress", "&7Tiến độ khám phá: &a%discovered%&7/&6%total% &e(%percent%%)", "%discovered%", String.valueOf(discovered), "%total%", String.valueOf(total), "%percent%", String.valueOf(percent))));
        body.add(GuiText.component(lang.getMessageOrDefault("dialog.page_info_simple", "&7Trang: &e%page%&7/&6%total%", "%page%", String.valueOf(curPage), "%total%", String.valueOf(totalPages))));
        body.add(Component.empty());

        List<NativeButton> buttons = new ArrayList<>();
        int start = (curPage - 1) * pageSize;
        int end = Math.min(start + pageSize, allPetKeys.size());

        for (int i = start; i < end; i++) {
            String petId = allPetKeys.get(i);
            boolean isDiscovered = plugin.getCodexManager().isDiscovered(player.getUniqueId(), petId);
            String name = lang.getPetName(petId);
            String rarityStr = plugin.getConfig().getString("pets." + petId + ".rarity", "COMMON");
            PetRarity rarity = PetRarity.fromString(rarityStr);

            if (isDiscovered) {
                buttons.add(new NativeButton(GuiText.component(rarity.getChatColor() + "✓ " + name), null, 180, (p, resp) -> {
                    String desc = plugin.getConfig().getString("pets." + petId + ".description", lang.getMessageOrDefault("dialog.codex_no_desc", "Chưa có mô tả."));
                    String unlockedMsg = lang.getMessageOrDefault("dialog.codex_unlocked_desc", "§7Độ hiếm: %rarity%\n§7Mô tả: %description%\n§aĐã mở khóa vào bách khoa thư!", "%rarity%", rarity.getLocalizedName(plugin), "%description%", desc);
                    openNotice(p, name, unlockedMsg, () -> openCodexBrowse(p, session, curPage));
                }));
            } else {
                buttons.add(new NativeButton(GuiText.component("&8? ??? (" + rarity.getLocalizedName(plugin) + "&8)"), null, 180, (p, resp) -> {
                    String lockedTitle = lang.getMessageOrDefault("dialog.codex_locked_title", "Chưa Mở Khóa");
                    String lockedMsg = lang.getMessageOrDefault("dialog.codex_locked_desc", "§7Thú cưng này chưa được mở khóa!\n§7Hãy tìm bắt hoặc mở trứng để bổ sung vào Codex.");
                    openNotice(p, lockedTitle, lockedMsg, () -> openCodexBrowse(p, session, curPage));
                }));
            }
        }

        if (curPage > 1) {
            buttons.add(new NativeButton(GuiText.component(lang.getMessageOrDefault("dialog.btn_prev_page", "&c◀ Trang trước")), null, 120, (p, resp) -> openCodexBrowse(p, session, curPage - 1)));
        }
        if (curPage < totalPages) {
            buttons.add(new NativeButton(GuiText.component(lang.getMessageOrDefault("dialog.btn_next_page", "&aTrang sau ▶")), null, 120, (p, resp) -> openCodexBrowse(p, session, curPage + 1)));
        }

        NativeButton exitBtn = new NativeButton(GuiText.component(lang.getMessageOrDefault("dialog.btn_hub", "&6🏠 Pet Hub")), null, 120, (p, resp) -> openPetHub(p, session));
        return engine.showMultiAction(player, GuiText.title(lang.getMessageOrDefault("dialog.codex_title", "&8&lBÁCH KHOA CODEX")), body, null, buttons, exitBtn, 2);
    }

    public boolean showDynamicShardsOverview(Player player, DialogSession session) {
        LanguageManager lang = plugin.getLanguage();
        Map<String, Integer> shards = plugin.getShardManager().getAllShards(player.getUniqueId());

        List<Component> body = new ArrayList<>();
        body.add(GuiText.component("&d" + lang.getMessageOrDefault("dialog.shards_desc", "Quản lý mảnh ghép và tinh hoa Thú Cưng")));
        body.add(Component.empty());

        if (shards.isEmpty()) {
            body.add(GuiText.component("&7" + lang.getMessageOrDefault("dialog.shards_empty", "Hiện bạn chưa có mảnh thú cưng nào.")));
            body.add(GuiText.component("&7" + lang.getMessageOrDefault("dialog.shards_empty_hint", "Mở trứng trùng lặp hoặc hoàn thành nhiệm vụ để thu thập!")));
        } else {
            body.add(GuiText.component("&7" + lang.getMessageOrDefault("dialog.shards_list_header", "Danh sách các mảnh hiện có (Nhấp để ghép pet):")));
        }

        List<NativeButton> buttons = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : shards.entrySet()) {
            String petId = entry.getKey();
            int amount = entry.getValue();
            if (amount <= 0) continue;

            String name = lang.getPetName(petId);
            int req = plugin.getShardManager().getRequiredShardsToCraft(petId);
            String status = (amount >= req) ? lang.getMessageOrDefault("dialog.shards_ready_badge", "&a[Đủ Ghép]") : "&e[" + amount + "/" + req + "]";

            buttons.add(new NativeButton(GuiText.component("&6" + name + " " + status), null, 180, (p, resp) -> {
                if (amount >= req) {
                    if (session != null && !session.isValid()) {
                        p.sendMessage(lang.getMessage("general.no_permission"));
                        return;
                    }
                    if (!plugin.getOwnershipManager().canAcquirePet(p)) {
                        p.sendMessage(lang.getMessage("pet.limit_reached", "%max_pets%", String.valueOf(plugin.getOwnershipManager().getMaxOwnedPets(p))));
                        return;
                    }
                    if (plugin.getShardManager().takeShards(p.getUniqueId(), petId, req)) {
                        boolean acquired = plugin.getOwnershipManager().acquirePet(p, petId, 1, 0, 1, PetTrait.rollRandomTrait(), null);
                        if (!acquired) {
                            plugin.getShardManager().addShards(p.getUniqueId(), petId, req);
                            p.sendMessage(lang.getMessageOrDefault("shards.craft_failed_refunded", "&cGhép thú cưng thất bại! Đã hoàn trả lại số mảnh."));
                            return;
                        }
                        if (session != null) session.markSubmitted();
                        p.sendMessage(lang.getMessage("shards.craft_success", "%pet_name%", name));
                        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                        openShardsOverview(p, session);
                    } else {
                        p.sendMessage(lang.getMessage("shards.not_enough_shards", "%current%", String.valueOf(amount), "%required%", String.valueOf(req)));
                    }
                } else {
                    p.sendMessage(lang.getMessage("shards.not_enough_shards", "%current%", String.valueOf(amount), "%required%", String.valueOf(req)));
                }
            }));
        }

        NativeButton exitBtn = new NativeButton(GuiText.component(lang.getMessageOrDefault("dialog.btn_hub", "&6🏠 Pet Hub")), null, 120, (p, resp) -> openPetHub(p, session));
        return engine.showMultiAction(player, GuiText.title(lang.getMessageOrDefault("dialog.shards_title", "&8&lMẢNH THÚ CƯNG")), body, null, buttons, exitBtn, 2);
    }
}
