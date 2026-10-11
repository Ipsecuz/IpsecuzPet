package org.ipsecuz.pet.dialog;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Common interface for dialog UI rendering providers (Native Dialog, Chest GUI Fallback).
 */
public interface DialogProvider {

    String getName();

    boolean isSupported(Player player);

    void openNotice(Player player, String title, String message, Runnable onClose);

    void openConfirmation(Player player, String title, String message, String confirmText, String cancelText, Consumer<Boolean> callback);

    void openPetHub(Player player, DialogSession session);

    void openPetDetails(Player player, DialogSession session, String petId);

    void openRenameDialog(Player player, DialogSession session, String petId);

    void openEvolutionDialog(Player player, DialogSession session, String petId);

    void openShopConfirmDialog(Player player, DialogSession session, String petId);

    void openHatchConfirmDialog(Player player, DialogSession session, String eggId);

    void openPetGuide(Player player, DialogSession session, int chapterIndex);

    void openSettingsDialog(Player player, DialogSession session);

    void openTradeInviteDialog(Player player, DialogSession session, Player sender);

    void openTradeConfirmDialog(Player player, DialogSession session, UUID tradeId, List<ItemStack> myItems, List<ItemStack> theirItems, Consumer<Boolean> callback);

    void openMyPets(Player player, DialogSession session, int page);

    void openShopBrowse(Player player, DialogSession session, int page);

    void openHatchSelect(Player player, DialogSession session);

    void openCodexBrowse(Player player, DialogSession session, int page);

    void openShardsOverview(Player player, DialogSession session);
}

