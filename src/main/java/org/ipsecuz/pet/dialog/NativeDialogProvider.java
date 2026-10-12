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

    @Override
    public boolean isSupported(Player player) {
        if (!engine.isAvailable() || player == null || !player.isOnline()) {
            return false;
        }

        // Check client version via ViaVersion if installed
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("ViaVersion")) {
                Class<?> viaClass = Class.forName("com.viaversion.viaversion.api.Via");
                Object api = viaClass.getMethod("getAPI").invoke(null);
                Method getVer = api.getClass().getMethod("getPlayerVersion", UUID.class);
                int clientProtocol = (int) getVer.invoke(api, player.getUniqueId());
                // Minecraft 1.21.6+ protocol: 772 or snapshot bit (1 << 30)
                if (clientProtocol >= 772 || (clientProtocol & (1 << 30)) != 0) {
                    return true;
                }
                return false;
            }
        } catch (Throwable ignored) {}

        // Fallback: check server version string
        String sVer = Bukkit.getBukkitVersion();
        if (sVer.contains("1.21.6") || sVer.contains("1.21.7") || sVer.contains("1.21.8")
                || sVer.contains("1.21.9") || sVer.contains("1.21.10") || sVer.contains("1.21.11")
                || sVer.contains("1.22") || sVer.contains("1.23") || sVer.contains("1.24")) {
            return true;
        }

        return false;
    }

    private void fallback(Player player, Consumer<Player> fallbackAction) {
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
        return engine.showNotice(player, GuiText.title(title), body, GuiText.component("&a✓ OK"), onClose);
    }

    public boolean showDynamicConfirmDialog(Player player, String title, String message, String confirmText, String cancelText, Consumer<Boolean> callback) {
        List<Component> body = new ArrayList<>();
        if (message != null) {
            for (String line : message.split("\n")) {
                body.add(GuiText.component("&7" + line));
            }
        }
        String okLabel = confirmText != null ? confirmText : "&a✓ Xác nhận";
        String cancelLabel = cancelText != null ? cancelText : "&c✘ Hủy bỏ";
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
            if (pName == null || pName.isEmpty()) pName = plugin.getConfig().getString("pets." + activePetId + ".name", activePetId);
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
            buttons.add(new NativeButton(GuiText.component("&c✘ Cất Pet"), null, 140, (p, resp) -> {
                plugin.getPetManager().removePet(p.getUniqueId());
                openPetHub(p, session);
            }));
        }

        NativeButton exitBtn = new NativeButton(GuiText.component("&c✘ Đóng"), null, 100, (p, resp) -> {});
        return engine.showMultiAction(player, GuiText.title(lang.getMessage("dialog.hub_title")), body, null, buttons, exitBtn, 2);
    }

    public boolean showDynamicMyPets(Player player, DialogSession session, int page) {
        LanguageManager lang = plugin.getLanguage();
        List<String> owned = plugin.getOwnershipManager().getOwnedPetIds(player.getUniqueId());
        int pageSize = 6;
        int totalPages = Math.max(1, (int) Math.ceil((double) owned.size() / pageSize));
        int curPage = Math.max(1, Math.min(page, totalPages));

        List<Component> body = new ArrayList<>();
        body.add(GuiText.component("&7Danh sách các thú cưng bạn đã thuần hóa và sở hữu."));
        body.add(GuiText.component("&7Trang: &e" + curPage + "&7/&6" + totalPages + " &8(Tổng: " + owned.size() + ")"));

        List<NativeButton> buttons = new ArrayList<>();
        int start = (curPage - 1) * pageSize;
        int end = Math.min(start + pageSize, owned.size());

        for (int i = start; i < end; i++) {
            String petId = owned.get(i);
            String custom = plugin.getConfigManager().getCustomName(player.getUniqueId(), petId);
            String name = (custom != null && !custom.isEmpty()) ? custom : plugin.getConfig().getString("pets." + petId + ".name", petId);
            int lvl = plugin.getConfigManager().getData().getInt(player.getUniqueId() + ".pets." + petId + ".level", 1);
            int stars = plugin.getConfigManager().getData().getInt(player.getUniqueId() + ".pets." + petId + ".stars", 1);
            String btnText = "⭐" + stars + " " + name + " (Lv." + lvl + ")";
            buttons.add(new NativeButton(GuiText.component("&a" + btnText), null, 180, (p, resp) -> openPetDetails(p, session, petId)));
        }

        if (curPage > 1) {
            buttons.add(new NativeButton(GuiText.component("&c◀ Trang trước"), null, 120, (p, resp) -> openMyPets(p, session, curPage - 1)));
        }
        if (curPage < totalPages) {
            buttons.add(new NativeButton(GuiText.component("&aTrang sau ▶"), null, 120, (p, resp) -> openMyPets(p, session, curPage + 1)));
        }

        NativeButton exitBtn = new NativeButton(GuiText.component("&6🏠 Pet Hub"), null, 120, (p, resp) -> openPetHub(p, session));
        return engine.showMultiAction(player, GuiText.title("&8&lKHO THÚ CƯNG &7(My Pets)"), body, null, buttons, exitBtn, 2);
    }

    public boolean showDynamicDetailDialog(Player player, DialogSession session, String petId) {
        if (petId == null) return false;
        LanguageManager lang = plugin.getLanguage();

        String custom = plugin.getConfigManager().getCustomName(player.getUniqueId(), petId);
        String name = (custom != null && !custom.isEmpty()) ? custom : plugin.getConfig().getString("pets." + petId + ".name", petId);
        int lvl = plugin.getConfigManager().getData().getInt(player.getUniqueId() + ".pets." + petId + ".level", 1);
        int exp = plugin.getConfigManager().getData().getInt(player.getUniqueId() + ".pets." + petId + ".exp", 0);
        int stars = plugin.getConfigManager().getData().getInt(player.getUniqueId() + ".pets." + petId + ".stars", 1);
        int happy = plugin.getConfigManager().getData().getInt(player.getUniqueId() + ".pets." + petId + ".happiness", 100);
        String trait = plugin.getConfigManager().getData().getString(player.getUniqueId() + ".pets." + petId + ".trait", "NORMAL");

        double dmg = PetStatEngine.calculateStatPreview(plugin, petId, lvl, stars, "damage");
        double hp = PetStatEngine.calculateStatPreview(plugin, petId, lvl, stars, "health");
        double def = PetStatEngine.calculateStatPreview(plugin, petId, lvl, stars, "defense");
        double spd = PetStatEngine.calculateStatPreview(plugin, petId, lvl, stars, "speed");

        boolean isActive = petId.equals(plugin.getPetManager().getActivePetId(player.getUniqueId()));
        boolean isBaby = plugin.getConfigManager().isPetBaby(player.getUniqueId(), petId);

        List<Component> body = new ArrayList<>();
        body.add(GuiText.component("&a&l" + name + " &8[" + petId + "]"));
        body.add(GuiText.component("&e" + lang.getMessage("gui.lore_level") + ": &fLv." + lvl + " &8(EXP: " + exp + ") &7| &6" + lang.getMessage("gui.lore_stars") + ": &f" + stars + " ⭐"));
        body.add(GuiText.component("&c" + lang.getMessage("gui.lore_happiness") + ": &f" + happy + "% &7| &b" + lang.getMessage("gui.lore_trait") + ": &f" + trait));
        body.add(GuiText.component("&c⚔ Sát thương: &f" + String.format("%.1f", dmg) + " &7| &a❤ Máu: &f" + String.format("%.1f", hp)));
        body.add(GuiText.component("&9🛡 Giáp: &f" + String.format("%.1f", def) + " &7| &e⚡ Tốc độ: &f" + String.format("%.2f", spd)));

        List<NativeButton> buttons = new ArrayList<>();
        if (isActive) {
            buttons.add(new NativeButton(GuiText.component("&c✘ Cất Thú Cưng"), null, 140, (p, resp) -> {
                plugin.getPetManager().removePet(p.getUniqueId());
                openPetDetails(p, session, petId);
            }));
        } else {
            buttons.add(new NativeButton(GuiText.component("&a✔ Triệu Hồi Đồng Hành"), null, 140, (p, resp) -> {
                plugin.getPetManager().spawnPet(p, petId);
                openPetDetails(p, session, petId);
            }));
        }

        buttons.add(new NativeButton(GuiText.component("&6✏ Đổi Tên (Rename)"), null, 140, (p, resp) -> openRenameDialog(p, session, petId)));
        buttons.add(new NativeButton(GuiText.component(isBaby ? "&d✦ Đổi: Trưởng Thành" : "&b✦ Đổi: Bé Con"), null, 140, (p, resp) -> {
            plugin.getConfigManager().setPetBaby(p.getUniqueId(), petId, !isBaby);
            if (isActive) plugin.getPetManager().spawnPet(p, petId);
            openPetDetails(p, session, petId);
        }));
        buttons.add(new NativeButton(GuiText.component("&6⭐ Tiến Hóa Sao"), null, 140, (p, resp) -> openEvolutionDialog(p, session, petId)));
        buttons.add(new NativeButton(GuiText.component("&e🍖 Cho Ăn"), null, 140, (p, resp) -> {
            ItemStack hand = p.getInventory().getItemInMainHand();
            plugin.getFeedingManager().feedPet(p, hand);
            openPetDetails(p, session, petId);
        }));

        NativeButton exitBtn = new NativeButton(GuiText.component("&7◀ Quay lại"), null, 100, (p, resp) -> openMyPets(p, session, 1));
        return engine.showMultiAction(player, GuiText.title("&8&lCHI TIẾT: &6" + name), body, null, buttons, exitBtn, 2);
    }

    public boolean showDynamicRenameDialog(Player player, DialogSession session, String petId) {
        if (petId == null) return false;
        LanguageManager lang = plugin.getLanguage();

        List<Component> body = new ArrayList<>();
        body.add(GuiText.component("&eNhập tên mới cho thú cưng vào ô bên dưới."));
        body.add(GuiText.component("&7Độ dài từ 2 đến 16 ký tự. Hỗ trợ mã màu &a&b&c..."));

        List<Object> inputs = new ArrayList<>();
        try {
            String curName = plugin.getConfigManager().getCustomName(player.getUniqueId(), petId);
            if (curName == null) curName = "";
            Object textInput = engine.createTextInput("name_input", GuiText.component("&eTên Mới:"), 200, 16, curName);
            if (textInput != null) inputs.add(textInput);
        } catch (Throwable t) {
            return false;
        }

        List<NativeButton> buttons = new ArrayList<>();
        buttons.add(new NativeButton(GuiText.component("&a✓ Lưu Tên"), null, 120, (p, resp) -> {
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

        NativeButton exitBtn = new NativeButton(GuiText.component("&c✘ Hủy Bỏ"), null, 100, (p, resp) -> openPetDetails(p, session, petId));
        return engine.showMultiAction(player, GuiText.title(lang.getMessage("dialog.rename_title")), body, inputs, buttons, exitBtn, 1);
    }

    public boolean showDynamicEvolutionDialog(Player player, DialogSession session, String petId) {
        if (petId == null) return false;
        LanguageManager lang = plugin.getLanguage();

        int curStars = plugin.getConfigManager().getData().getInt(player.getUniqueId() + ".pets." + petId + ".stars", 1);
        int nextStars = Math.min(5, curStars + 1);

        double curDmg = PetStatEngine.calculateStatPreview(plugin, petId, 1, curStars, "damage");
        double nextDmg = PetStatEngine.calculateStatPreview(plugin, petId, 1, nextStars, "damage");

        List<Component> body = new ArrayList<>();
        body.add(GuiText.component("&7Cấp sao: &e" + curStars + " ⭐ &7➔ &6" + nextStars + " ⭐"));
        body.add(GuiText.component("&7Sát thương cơ bản: &f" + String.format("%.1f", curDmg) + " ➔ &a" + String.format("%.1f", nextDmg)));
        body.add(GuiText.component("&eTiến hóa sẽ gia tăng vĩnh viễn tiềm năng chiến đấu của Pet."));

        List<NativeButton> buttons = new ArrayList<>();
        buttons.add(new NativeButton(GuiText.component(lang.getMessage("dialog.evo_confirm")), null, 140, (p, resp) -> {
            if (plugin.getDialogService().processEvolution(p, petId)) {
                openPetDetails(p, session, petId);
            }
        }));

        NativeButton exitBtn = new NativeButton(GuiText.component(lang.getMessage("dialog.evo_cancel")), null, 100, (p, resp) -> openPetDetails(p, session, petId));
        return engine.showMultiAction(player, GuiText.title(lang.getMessage("dialog.evolution_title")), body, null, buttons, exitBtn, 1);
    }

    public boolean showDynamicShopBrowse(Player player, DialogSession session, int page) {
        LanguageManager lang = plugin.getLanguage();
        var sec = plugin.getConfig().getConfigurationSection("pets");
        List<String> petIds = new ArrayList<>();
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                if (sec.getBoolean(key + ".buyable", true)) {
                    petIds.add(key);
                }
            }
        }

        int pageSize = 6;
        int totalPages = Math.max(1, (int) Math.ceil((double) petIds.size() / pageSize));
        int curPage = Math.max(1, Math.min(page, totalPages));

        List<Component> body = new ArrayList<>();
        body.add(GuiText.component("&eChào mừng bạn đến với Cửa Hàng Thú Cưng!"));
        body.add(GuiText.component("&7Chọn pet để xem chi tiết và mua. Trang: &e" + curPage + "&7/&6" + totalPages));

        List<NativeButton> buttons = new ArrayList<>();
        int start = (curPage - 1) * pageSize;
        int end = Math.min(start + pageSize, petIds.size());

        for (int i = start; i < end; i++) {
            String petId = petIds.get(i);
            String name = plugin.getConfig().getString("pets." + petId + ".name", petId);
            double price = plugin.getConfig().getDouble("pets." + petId + ".price", 1000.0);
            boolean owned = plugin.getOwnershipManager().hasPet(player.getUniqueId(), petId);
            String status = owned ? "&a[Đã sở hữu]" : "&e[" + (long) price + "$]";
            buttons.add(new NativeButton(GuiText.component("&6" + name + " " + status), null, 180, (p, resp) -> {
                if (!owned) {
                    openShopConfirmDialog(p, session, petId);
                } else {
                    p.sendMessage(lang.getMessage("pet.already_owned"));
                }
            }));
        }

        if (curPage > 1) {
            buttons.add(new NativeButton(GuiText.component("&c◀ Trang trước"), null, 120, (p, resp) -> openShopBrowse(p, session, curPage - 1)));
        }
        if (curPage < totalPages) {
            buttons.add(new NativeButton(GuiText.component("&aTrang sau ▶"), null, 120, (p, resp) -> openShopBrowse(p, session, curPage + 1)));
        }

        NativeButton exitBtn = new NativeButton(GuiText.component("&6🏠 Pet Hub"), null, 120, (p, resp) -> openPetHub(p, session));
        return engine.showMultiAction(player, GuiText.title("&8&lCỬA HÀNG THÚ CƯNG"), body, null, buttons, exitBtn, 2);
    }

    public boolean showDynamicShopConfirmDialog(Player player, DialogSession session, String petId) {
        if (petId == null) return false;
        LanguageManager lang = plugin.getLanguage();

        String name = plugin.getConfig().getString("pets." + petId + ".name", petId);
        double price = plugin.getConfig().getDouble("pets." + petId + ".price", 1000.0);
        String currency = plugin.getConfig().getString("pets." + petId + ".currency", "VAULT");

        List<Component> body = new ArrayList<>();
        body.add(GuiText.component("&e" + lang.getMessage("dialog.shop_confirm_desc")));
        body.add(GuiText.component("&7Thú cưng: &6&l" + name));
        body.add(GuiText.component("&7Giá bán: &a" + (long) price + " &e(" + currency + ")"));

        List<NativeButton> buttons = new ArrayList<>();
        buttons.add(new NativeButton(GuiText.component(lang.getMessage("dialog.shop_confirm_buy")), null, 140, (p, resp) -> {
            if (plugin.getDialogService().processShopPurchase(p, petId)) {
                openShopBrowse(p, session, 1);
            }
        }));

        NativeButton exitBtn = new NativeButton(GuiText.component(lang.getMessage("dialog.shop_confirm_cancel")), null, 100, (p, resp) -> openShopBrowse(p, session, 1));
        return engine.showMultiAction(player, GuiText.title(lang.getMessage("dialog.shop_confirm_title")), body, null, buttons, exitBtn, 1);
    }

    public boolean showDynamicHatchSelect(Player player, DialogSession session) {
        LanguageManager lang = plugin.getLanguage();
        List<Component> body = new ArrayList<>();
        body.add(GuiText.component("&eChọn loại Trứng để tiến hành ấp và quay Roulette!"));
        body.add(GuiText.component("&7Mỗi loại trứng chứa những linh hồn thú cưng độc nhất."));

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

        NativeButton exitBtn = new NativeButton(GuiText.component("&6🏠 Pet Hub"), null, 120, (p, resp) -> openPetHub(p, session));
        return engine.showMultiAction(player, GuiText.title("&8&lLÒ ẤP TRỨNG"), body, null, buttons, exitBtn, 2);
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
        body.add(GuiText.component("&7Trứng chuẩn bị ấp: &6&l" + eggName));
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
            body.add(GuiText.component("&a✦ Chi phí: Miễn phí hoàn toàn!"));
        } else if (!group.getRequirements().isEmpty()) {
            body.add(Component.empty());
            body.add(GuiText.component("&6✦ Chi phí để quay:"));
            org.ipsecuz.pet.requirement.RequirementContext ctx = new org.ipsecuz.pet.requirement.RequirementContext(player, plugin);
            org.ipsecuz.pet.requirement.RequirementCheckResult result = plugin.getRequirementManager().evaluate(group, ctx);
            for (String line : org.ipsecuz.pet.requirement.RequirementGuiRenderer.renderToStrings(result)) {
                if (!line.startsWith("§7----")) {
                    body.add(GuiText.component(line));
                }
            }
        }

        List<NativeButton> buttons = new ArrayList<>();
        buttons.add(new NativeButton(GuiText.component(lang.getMessage("dialog.hatch_confirm")), null, 140, (p, resp) -> {
            plugin.getDialogService().processHatchConfirm(p, eggId);
        }));

        NativeButton exitBtn = new NativeButton(GuiText.component(lang.getMessage("dialog.hatch_cancel")), null, 100, (p, resp) -> openHatchSelect(p, session));
        return engine.showMultiAction(player, GuiText.title(lang.getMessage("dialog.hatch_title")), body, null, buttons, exitBtn, 1);
    }

    public boolean showDynamicGuideDialog(Player player, DialogSession session, int chapterIndex) {
        LanguageManager lang = plugin.getLanguage();

        if (chapterIndex < 0) {
            List<Component> body = new ArrayList<>();
            body.add(GuiText.component("&eDanh mục 18 chương cẩm nang toàn diện hệ thống IpsecuzPet."));

            List<NativeButton> buttons = new ArrayList<>();
            for (int i = 0; i < 18; i++) {
                final int idx = i;
                String chapterTitleKey = "dialog.guide." + "ch" + (i + 1) + "_title";
                String title = lang.getMessage(chapterTitleKey);
                if (title == null || title.startsWith("dialog.")) title = "Chương " + (i + 1);
                buttons.add(new NativeButton(GuiText.component("&e" + (i + 1) + ". " + title), null, 200, (p, resp) -> openPetGuide(p, session, idx)));
            }

            NativeButton exitBtn = new NativeButton(GuiText.component("&6🏠 Pet Hub"), null, 120, (p, resp) -> openPetHub(p, session));
            return engine.showMultiAction(player, GuiText.title(lang.getMessage("dialog.guide_title")), body, null, buttons, exitBtn, 2);
        } else {
            String chapterTitleKey = "dialog.guide." + "ch" + (chapterIndex + 1) + "_title";
            String chapterBodyKey = "dialog.guide." + "ch" + (chapterIndex + 1) + "_body";
            String chTitle = lang.getMessage(chapterTitleKey);
            String chBody = lang.getMessage(chapterBodyKey);

            List<Component> body = new ArrayList<>();
            body.add(GuiText.component("&6&lChương " + (chapterIndex + 1) + ": " + (chTitle != null ? chTitle : "")));
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

            NativeButton exitBtn = new NativeButton(GuiText.component("&c✘ Đóng"), null, 100, (p, resp) -> {});
            return engine.showMultiAction(player, GuiText.title(lang.getMessage("dialog.guide_title")), body, null, buttons, exitBtn, 2);
        }
    }

    public boolean showDynamicSettingsDialog(Player player, DialogSession session) {
        LanguageManager lang = plugin.getLanguage();

        boolean curParticles = plugin.getPlayerSettingsManager().isParticlesEnabled(player.getUniqueId());
        boolean curSounds = plugin.getPlayerSettingsManager().isSoundsEnabled(player.getUniqueId());

        List<Component> body = new ArrayList<>();
        body.add(GuiText.component("&eTùy chỉnh các hiệu ứng và sở thích trải nghiệm hệ thống Thú cưng."));

        List<Object> inputs = new ArrayList<>();
        try {
            Object pInput = engine.createBoolInput("particles", GuiText.component("&b" + lang.getMessage("dialog.setting_particles")), curParticles, "Bật", "Tắt");
            Object sInput = engine.createBoolInput("sounds", GuiText.component("&a" + lang.getMessage("dialog.setting_sounds")), curSounds, "Bật", "Tắt");
            if (pInput != null) inputs.add(pInput);
            if (sInput != null) inputs.add(sInput);
        } catch (Throwable ignored) {}

        List<NativeButton> buttons = new ArrayList<>();
        buttons.add(new NativeButton(GuiText.component("&a✓ Lưu Cài Đặt"), null, 140, (p, resp) -> {
            if (resp != null) {
                Boolean pVal = resp.getBoolean("particles");
                Boolean sVal = resp.getBoolean("sounds");
                if (pVal != null) plugin.getPlayerSettingsManager().setParticlesEnabled(p.getUniqueId(), pVal);
                if (sVal != null) plugin.getPlayerSettingsManager().setSoundsEnabled(p.getUniqueId(), sVal);
                p.sendMessage(lang.getMessage("general.config_reloaded"));
            }
            openPetHub(p, session);
        }));

        NativeButton exitBtn = new NativeButton(GuiText.component("&6🏠 Pet Hub"), null, 120, (p, resp) -> openPetHub(p, session));
        return engine.showMultiAction(player, GuiText.title(lang.getMessage("dialog.settings_title")), body, inputs, buttons, exitBtn, 1);
    }

    public boolean showDynamicTradeInviteDialog(Player player, DialogSession session, Player sender) {
        LanguageManager lang = plugin.getLanguage();
        List<Component> body = new ArrayList<>();
        body.add(GuiText.component("&eNgười chơi &f" + sender.getName() + " &eđã gửi lời mời giao dịch Thú cưng cho bạn!"));
        body.add(GuiText.component("&7Giao dịch diễn ra an toàn, có xác nhận và ghi nhận nhật ký 2 bước."));

        List<NativeButton> buttons = new ArrayList<>();
        buttons.add(new NativeButton(GuiText.component(lang.getMessage("dialog.trade_accept")), null, 140, (p, resp) -> {
            plugin.getTradeManager().acceptTrade(p);
        }));

        NativeButton exitBtn = new NativeButton(GuiText.component(lang.getMessage("dialog.trade_decline")), null, 140, (p, resp) -> {
            p.sendMessage(lang.getMessage("trade.cancelled", "%reason%", "Từ chối lời mời"));
            sender.sendMessage(lang.getMessage("trade.cancelled", "%reason%", "Đối phương đã từ chối"));
        });

        return engine.showMultiAction(player, GuiText.title(lang.getMessage("dialog.trade_invite_title")), body, null, buttons, exitBtn, 2);
    }

    public boolean showDynamicTradeConfirmDialog(Player player, DialogSession session, UUID tradeId, List<ItemStack> myItems, List<ItemStack> theirItems, Consumer<Boolean> callback) {
        LanguageManager lang = plugin.getLanguage();
        List<Component> body = new ArrayList<>();
        body.add(GuiText.component("&eKiểm tra kỹ trước khi xác nhận bàn giao!"));
        body.add(GuiText.component("&aVật phẩm bạn giao: &f" + (myItems != null ? myItems.size() : 0) + " món"));
        body.add(GuiText.component("&bVật phẩm nhận về: &f" + (theirItems != null ? theirItems.size() : 0) + " món"));

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
        body.add(GuiText.component("&dBách Khoa Toàn Thư Codex Thú Cưng"));
        body.add(GuiText.component("&7Tiến độ khám phá: &a" + discovered + "&7/&6" + total + " &e(" + percent + "%)"));
        body.add(GuiText.component("&7Trang: &e" + curPage + "&7/&6" + totalPages));
        body.add(Component.empty());

        List<NativeButton> buttons = new ArrayList<>();
        int start = (curPage - 1) * pageSize;
        int end = Math.min(start + pageSize, allPetKeys.size());

        for (int i = start; i < end; i++) {
            String petId = allPetKeys.get(i);
            boolean isDiscovered = plugin.getCodexManager().isDiscovered(player.getUniqueId(), petId);
            String name = plugin.getConfig().getString("pets." + petId + ".name", petId);
            String rarityStr = plugin.getConfig().getString("pets." + petId + ".rarity", "COMMON");
            PetRarity rarity = PetRarity.fromString(rarityStr);

            if (isDiscovered) {
                buttons.add(new NativeButton(GuiText.component(rarity.getChatColor() + "✓ " + name), null, 180, (p, resp) -> {
                    String desc = plugin.getConfig().getString("pets." + petId + ".description", "Chưa có mô tả.");
                    openNotice(p, name, "§7Độ hiếm: " + rarity.getLocalizedName(plugin) + "\n§7Mô tả: " + desc + "\n§aĐã mở khóa vào bách khoa thư!", () -> openCodexBrowse(p, session, curPage));
                }));
            } else {
                buttons.add(new NativeButton(GuiText.component("&8? ??? (" + rarity.getLocalizedName(plugin) + "&8)"), null, 180, (p, resp) -> {
                    openNotice(p, "Chưa Mở Khóa", "§7Thú cưng này chưa được mở khóa!\n§7Hãy tìm bắt hoặc mở trứng để bổ sung vào Codex.", () -> openCodexBrowse(p, session, curPage));
                }));
            }
        }

        if (curPage > 1) {
            buttons.add(new NativeButton(GuiText.component("&c◀ Trang trước"), null, 120, (p, resp) -> openCodexBrowse(p, session, curPage - 1)));
        }
        if (curPage < totalPages) {
            buttons.add(new NativeButton(GuiText.component("&aTrang sau ▶"), null, 120, (p, resp) -> openCodexBrowse(p, session, curPage + 1)));
        }

        NativeButton exitBtn = new NativeButton(GuiText.component("&6🏠 Pet Hub"), null, 120, (p, resp) -> openPetHub(p, session));
        return engine.showMultiAction(player, GuiText.title("&8&lBÁCH KHOA CODEX"), body, null, buttons, exitBtn, 2);
    }

    public boolean showDynamicShardsOverview(Player player, DialogSession session) {
        LanguageManager lang = plugin.getLanguage();
        Map<String, Integer> shards = plugin.getShardManager().getAllShards(player.getUniqueId());

        List<Component> body = new ArrayList<>();
        body.add(GuiText.component("&dQuản lý mảnh ghép và tinh hoa Thú Cưng"));
        body.add(Component.empty());

        if (shards.isEmpty()) {
            body.add(GuiText.component("&7Hiện bạn chưa có mảnh thú cưng nào."));
            body.add(GuiText.component("&7Mở trứng trùng lặp hoặc hoàn thành nhiệm vụ để thu thập!"));
        } else {
            body.add(GuiText.component("&7Danh sách các mảnh hiện có (Nhấp để ghép pet):"));
        }

        List<NativeButton> buttons = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : shards.entrySet()) {
            String petId = entry.getKey();
            int amount = entry.getValue();
            if (amount <= 0) continue;

            String name = plugin.getConfig().getString("pets." + petId + ".name", petId);
            int req = plugin.getShardManager().getRequiredShardsToCraft(petId);
            String status = (amount >= req) ? "&a[Đủ Ghép]" : "&e[" + amount + "/" + req + "]";

            buttons.add(new NativeButton(GuiText.component("&6" + name + " " + status), null, 180, (p, resp) -> {
                if (amount >= req) {
                    if (plugin.getOwnershipManager().canAcquirePet(p)) {
                        plugin.getShardManager().takeShards(p.getUniqueId(), petId, req);
                        plugin.getOwnershipManager().acquirePet(p, petId, 1, 0, 1, PetTrait.rollRandomTrait(), null);
                        p.sendMessage(lang.getMessage("shards.craft_success", "%pet_name%", name));
                        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                        openShardsOverview(p, session);
                    } else {
                        p.sendMessage(lang.getMessage("pet.limit_reached", "%max_pets%", String.valueOf(plugin.getOwnershipManager().getMaxOwnedPets(p))));
                    }
                } else {
                    p.sendMessage(lang.getMessage("shards.not_enough_shards", "%current%", String.valueOf(amount), "%required%", String.valueOf(req)));
                }
            }));
        }

        NativeButton exitBtn = new NativeButton(GuiText.component("&6🏠 Pet Hub"), null, 120, (p, resp) -> openPetHub(p, session));
        return engine.showMultiAction(player, GuiText.title("&8&lMẢNH THÚ CƯNG"), body, null, buttons, exitBtn, 2);
    }
}
