package org.ipsecuz.pet.dialog;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.ipsecuz.pet.IpsecuzPet;

/**
 * Optional interoperability bridge for the external DialogWindow plugin.
 * Completely optional; does NOT hard-depend on or shade any external DialogWindow classes.
 */
public class OptionalDialogWindowBridge {

    private final IpsecuzPet plugin;
    private final boolean available;

    public OptionalDialogWindowBridge(IpsecuzPet plugin) {
        this.plugin = plugin;
        boolean found = false;
        try {
            boolean enabledInConfig = plugin != null && plugin.getConfig().getBoolean("ui.dialog.dialogwindow_bridge", false);
            Plugin dw = Bukkit.getPluginManager() != null ? Bukkit.getPluginManager().getPlugin("DialogWindow") : null;
            if (enabledInConfig && dw != null && dw.isEnabled()) {
                found = true;
                if (plugin != null) plugin.getLogger().info("§a[IpsecuzPet] Optional DialogWindow integration detected and active via /dwopen.");
            }
        } catch (Throwable t) {
            found = false;
        }
        this.available = found;
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Executes a dialog through DialogWindow's registered command interface (/dwopen) if available.
     * All security and permission checks are maintained authoritatively by IpsecuzPet.
     */
    public boolean openExternalDialog(Player player, String dialogIdentifier) {
        if (!available || player == null || dialogIdentifier == null) {
            return false;
        }
        try {
            return Bukkit.dispatchCommand(player, "dwopen " + dialogIdentifier);
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.getLogger().warning("§c[IpsecuzPet] Failed to dispatch DialogWindow /dwopen command: " + t.getMessage());
            }
            return false;
        }
    }
}

