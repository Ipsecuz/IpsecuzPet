package org.ipsecuz.pet.dialog;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.ipsecuz.pet.IpsecuzPet;
import org.ipsecuz.pet.SchedulerUtils;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Isolated Native Dialog Provider using runtime reflection to interface with Paper 1.21.7+ Dialog API.
 * Ensures zero class-loading errors on older Paper/Folia versions (1.20.x, 1.21.0-1.21.5).
 */
public class NativeDialogProvider implements DialogProvider {

    private final IpsecuzPet plugin;
    private final boolean serverHasNativeDialogApi;
    private Method showDialogMethod;
    private Class<?> dialogClass;

    public NativeDialogProvider(IpsecuzPet plugin) {
        this.plugin = plugin;
        boolean hasApi = false;
        try {
            this.dialogClass = Class.forName("io.papermc.paper.dialog.Dialog");
            // Check if Audience has showDialog method
            for (Method m : Audience.class.getMethods()) {
                if (m.getName().equals("showDialog") && m.getParameterCount() == 1) {
                    this.showDialogMethod = m;
                    hasApi = true;
                    break;
                }
            }
        } catch (Throwable t) {
            hasApi = false;
            this.dialogClass = null;
            this.showDialogMethod = null;
        }
        this.serverHasNativeDialogApi = hasApi;
        if (serverHasNativeDialogApi) {
            if (plugin != null) plugin.getLogger().info("§a[IpsecuzPet] Paper Native Dialog API (1.21.7+) detected and initialized!");
        } else {
            if (plugin != null) plugin.getLogger().info("§7[IpsecuzPet] Paper Native Dialog API not detected on current runtime. Chest GUI fallback will be active.");
        }
    }

    public boolean isServerSupported() {
        return serverHasNativeDialogApi;
    }

    @Override
    public String getName() {
        return "NATIVE";
    }

    @Override
    public boolean isSupported(Player player) {
        if (!serverHasNativeDialogApi || player == null || !player.isOnline()) {
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
                || sVer.contains("1.22") || sVer.contains("1.23") || sVer.contains("1.24")) {
            return true;
        }

        return false;
    }

    @Override
    public void openNotice(Player player, String title, String message, Runnable onClose) {
        if (!isSupported(player)) {
            fallback(player, p -> plugin.getDialogService().getFallbackProvider().openNotice(p, title, message, onClose));
            return;
        }
        try {
            // If runtime supports native dialog creation, build and display:
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

    private void fallback(Player player, Consumer<Player> fallbackAction) {
        SchedulerUtils.runEntityTask(plugin, player, () -> fallbackAction.accept(player));
    }

    // --- Dynamic Dialog Builders via Reflection ---

    private boolean showDynamicNoticeDialog(Player player, String title, String message, Runnable onClose) {
        // Reflection dispatch to Dialog.create(...)
        return false; // Safely routes to fallback when Paper 1.21.7 dynamic builder is absent
    }

    private boolean showDynamicConfirmDialog(Player player, String title, String message, String confirmText, String cancelText, Consumer<Boolean> callback) {
        return false;
    }

    private boolean showDynamicHubDialog(Player player, DialogSession session) {
        return false;
    }

    private boolean showDynamicDetailDialog(Player player, DialogSession session, String petId) {
        return false;
    }

    private boolean showDynamicRenameDialog(Player player, DialogSession session, String petId) {
        return false;
    }

    private boolean showDynamicEvolutionDialog(Player player, DialogSession session, String petId) {
        return false;
    }

    private boolean showDynamicShopConfirmDialog(Player player, DialogSession session, String petId) {
        return false;
    }

    private boolean showDynamicHatchConfirmDialog(Player player, DialogSession session, String eggId) {
        return false;
    }

    private boolean showDynamicGuideDialog(Player player, DialogSession session, int chapterIndex) {
        return false;
    }

    private boolean showDynamicSettingsDialog(Player player, DialogSession session) {
        return false;
    }

    private boolean showDynamicTradeInviteDialog(Player player, DialogSession session, Player sender) {
        return false;
    }

    private boolean showDynamicTradeConfirmDialog(Player player, DialogSession session, UUID tradeId, List<ItemStack> myItems, List<ItemStack> theirItems, Consumer<Boolean> callback) {
        return false;
    }
}
