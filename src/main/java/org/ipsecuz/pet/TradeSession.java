package org.ipsecuz.pet;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class TradeSession {
    private final IpsecuzPet plugin;
    private final Player playerA;
    private final Player playerB;
    private final Inventory inventory;

    public enum TradeState {
        OPEN,
        LOCKED,
        CONFIRMED,
        COMMITTING,
        COMPLETED,
        CANCELLED
    }

    private boolean lockedA = false;
    private boolean lockedB = false;
    private volatile TradeState state = TradeState.OPEN;
    private int countdown = -1;

    // Các slot đề nghị trao đổi (3x3 cho mỗi bên)
    public static final Set<Integer> SLOTS_A = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            10, 11, 12,
            19, 20, 21,
            28, 29, 30
    )));

    public static final Set<Integer> SLOTS_B = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            14, 15, 16,
            23, 24, 25,
            32, 33, 34
    )));

    public static final Set<Integer> DIVIDER_SLOTS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            4, 13, 22, 31, 40
    )));

    public static class TradeHolder implements InventoryHolder {
        private final TradeSession session;
        public TradeHolder(TradeSession session) { this.session = session; }
        @Override public Inventory getInventory() { return session.inventory; }
        public TradeSession getSession() { return session; }
    }

    public TradeSession(IpsecuzPet plugin, Player playerA, Player playerB) {
        this.plugin = plugin;
        this.playerA = playerA;
        this.playerB = playerB;

        String rawTitle = plugin.getModuleManager().getTradeConfig().getString(
                "gui_title", "&8Giao Dịch: %player1% &8⇄ %player2%")
                .replace("%player1%", playerA.getName())
                .replace("%player2%", playerB.getName());

        Component title = LegacyComponentSerializer.legacySection().deserialize(ChatColor.translateAlternateColorCodes('&', rawTitle));
        this.inventory = Bukkit.createInventory(new TradeHolder(this), 54, title);
    }

    public void open() {
        renderBaseFrame();
        updateStatusButtons();
        playerA.openInventory(inventory);
        playerB.openInventory(inventory);
    }

    private void renderBaseFrame() {
        ItemStack blackGlass = createGlass(Material.BLACK_STAINED_GLASS_PANE, " ");
        ItemStack cyanGlass = createGlass(Material.CYAN_STAINED_GLASS_PANE, "§b✦ Bên đề nghị: " + playerA.getName());
        ItemStack orangeGlass = createGlass(Material.ORANGE_STAINED_GLASS_PANE, "§6✦ Bên đối tác: " + playerB.getName());
        ItemStack whiteGlass = createGlass(Material.GRAY_STAINED_GLASS_PANE, "§8⇄ Vách Ngăn");

        for (int i = 0; i < 54; i++) {
            if (SLOTS_A.contains(i) || SLOTS_B.contains(i) || i == 38 || i == 42 || i == 49) {
                continue;
            }
            if (DIVIDER_SLOTS.contains(i)) {
                inventory.setItem(i, whiteGlass);
            } else if (i < 9) {
                inventory.setItem(i, (i < 4) ? cyanGlass : (i > 4 ? orangeGlass : whiteGlass));
            } else {
                inventory.setItem(i, blackGlass);
            }
        }
    }

    public void updateStatusButtons() {
        // Nút khóa bên A (slot 38)
        ItemStack btnA = createGlass(
                lockedA ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                lockedA ? "§a§l✔ " + playerA.getName() + " ĐÃ KHÓA" : "§c§l✖ " + playerA.getName() + " CHƯA KHÓA",
                "§7Nhấp để chuyển trạng thái Khóa / Mở"
        );
        inventory.setItem(38, btnA);

        // Nút khóa bên B (slot 42)
        ItemStack btnB = createGlass(
                lockedB ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                lockedB ? "§a§l✔ " + playerB.getName() + " ĐÃ KHÓA" : "§c§l✖ " + playerB.getName() + " CHƯA KHÓA",
                "§7Nhấp để chuyển trạng thái Khóa / Mở"
        );
        inventory.setItem(42, btnB);

        // Slot trung tâm (slot 49): Trạng thái đếm ngược
        ItemStack centerSlot;
        if (countdown > 0) {
            centerSlot = createGlass(Material.YELLOW_STAINED_GLASS_PANE,
                    "§e§lĐANG ĐẾM NGƯỢC: §6" + countdown + "s...",
                    "§7Giao dịch sẽ hoàn tất sau giây lát!");
        } else if (lockedA && lockedB) {
            centerSlot = createGlass(Material.LIME_STAINED_GLASS_PANE,
                    "§a§lCẢ HAI ĐÃ SẴN SÀNG!",
                    "§7Đang chuẩn bị xác nhận...");
        } else {
            centerSlot = createGlass(Material.BARRIER,
                    "§c§lCHỜ KHÓA GIAO DỊCH",
                    "§7Cả 2 người chơi phải bấm nút Khóa để tiếp tục.");
        }
        inventory.setItem(49, centerSlot);
    }

    public void resetLocks() {
        if (isFinished()) return;
        if (lockedA || lockedB || countdown > 0) {
            lockedA = false;
            lockedB = false;
            countdown = -1;
            state = TradeState.OPEN;
            updateStatusButtons();
            playerA.playSound(playerA.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
            playerB.playSound(playerB.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
            playerA.sendMessage("§e[Giao Dịch] Ưu đãi đã thay đổi. Trạng thái khóa đã được hủy bỏ!");
            playerB.sendMessage("§e[Giao Dịch] Ưu đãi đã thay đổi. Trạng thái khóa đã được hủy bỏ!");
        }
    }

    public void toggleLock(Player player) {
        if (isFinished()) return;

        if (player.equals(playerA)) {
            lockedA = !lockedA;
        } else if (player.equals(playerB)) {
            lockedB = !lockedB;
        }

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
        updateStatusButtons();

        if (lockedA && lockedB) {
            state = TradeState.LOCKED;
            startCountdown();
        } else {
            state = TradeState.OPEN;
            countdown = -1;
            updateStatusButtons();
        }
    }

    private void startCountdown() {
        int initialCountdown = plugin.getModuleManager().getTradeConfig().getInt("confirm_countdown_seconds", 3);
        countdown = initialCountdown;
        updateStatusButtons();

        runCountdownStep();
    }

    private void runCountdownStep() {
        if (isFinished() || state != TradeState.LOCKED || !lockedA || !lockedB) return;

        if (countdown <= 0) {
            state = TradeState.CONFIRMED;
            completeTrade();
            return;
        }

        playerA.playSound(playerA.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f + (float)(3 - countdown) * 0.2f);
        playerB.playSound(playerB.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f + (float)(3 - countdown) * 0.2f);
        updateStatusButtons();

        SchedulerUtils.runGlobalTaskLater(plugin, () -> {
            if (isFinished() || state != TradeState.LOCKED || !lockedA || !lockedB) return;
            countdown--;
            if (countdown <= 0) {
                state = TradeState.CONFIRMED;
                completeTrade();
            } else {
                updateStatusButtons();
                runCountdownStep();
            }
        }, 20L);
    }

    private synchronized void completeTrade() {
        if (state != TradeState.CONFIRMED) return;
        state = TradeState.COMMITTING;

        if (!playerA.isOnline() || !playerB.isOnline()) {
            cancel("Một trong hai người chơi đã thoát game trước khi hoàn tất giao dịch.");
            return;
        }

        // Pha 1: Chụp ảnh snapshot toàn bộ ưu đãi và kiểm tra tính hợp lệ
        List<ItemStack> itemsFromA = new ArrayList<>();
        for (int slot : SLOTS_A) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                if (!PetCardSecurity.isPetCard(item)) {
                    cancel("Phát hiện vật phẩm không phải Thẻ Pet hợp lệ trong khung giao dịch!");
                    return;
                }
                itemsFromA.add(item.clone());
            }
        }

        List<ItemStack> itemsFromB = new ArrayList<>();
        for (int slot : SLOTS_B) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                if (!PetCardSecurity.isPetCard(item)) {
                    cancel("Phát hiện vật phẩm không phải Thẻ Pet hợp lệ trong khung giao dịch!");
                    return;
                }
                itemsFromB.add(item.clone());
            }
        }

        // Pha 2: Kiểm tra dung lượng kho đồ (Inventory Capacity Pre-Validation)
        int freeSlotsA = getFreeStorageSlots(playerA);
        int freeSlotsB = getFreeStorageSlots(playerB);

        if (freeSlotsA < itemsFromB.size()) {
            cancel("Người chơi " + playerA.getName() + " không đủ ô trống trong kho đồ (cần " + itemsFromB.size() + " ô, hiện có " + freeSlotsA + " ô)!");
            return;
        }

        if (freeSlotsB < itemsFromA.size()) {
            cancel("Người chơi " + playerB.getName() + " không đủ ô trống trong kho đồ (cần " + itemsFromA.size() + " ô, hiện có " + freeSlotsB + " ô)!");
            return;
        }

        // Dọn sạch các slot giao dịch để tránh duplicate
        for (int slot : SLOTS_A) inventory.setItem(slot, null);
        for (int slot : SLOTS_B) inventory.setItem(slot, null);

        // Pha 3: Giao dịch nguyên tử (Atomic Commit & Rollback)
        try {
            // Chuyển đồ từ A sang B
            for (ItemStack item : itemsFromA) {
                playerB.getInventory().addItem(item);
            }

            // Chuyển đồ từ B sang A
            for (ItemStack item : itemsFromB) {
                playerA.getInventory().addItem(item);
            }
        } catch (Exception ex) {
            plugin.getLogger().severe("Lỗi nghiêm trọng trong quá trình chuyển giao dịch Pet: " + ex.getMessage());
            // Rollback lập tức về chủ sở hữu ban đầu
            for (ItemStack item : itemsFromA) giveItemSafely(playerA, item);
            for (ItemStack item : itemsFromB) giveItemSafely(playerB, item);
            cancel("Giao dịch gặp lỗi kỹ thuật ngoại lệ và đã hoàn trả đồ về chủ cũ an toàn.");
            return;
        }

        state = TradeState.COMPLETED;

        playerA.playSound(playerA.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        playerB.playSound(playerB.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);

        playerA.sendMessage("§a§lGIAO DỊCH THÀNH CÔNG! §fBạn đã nhận được Thẻ Pet từ §e" + playerB.getName() + "§f.");
        playerB.sendMessage("§a§lGIAO DỊCH THÀNH CÔNG! §fBạn đã nhận được Thẻ Pet từ §e" + playerA.getName() + "§f.");

        playerA.closeInventory();
        playerB.closeInventory();

        plugin.getTradeManager().removeActiveSession(this);
    }

    public synchronized void cancel(String reason) {
        if (state == TradeState.COMPLETED || state == TradeState.CANCELLED) return;
        state = TradeState.CANCELLED;

        // Hoàn trả toàn bộ đồ về cho người đề nghị ban đầu
        for (int slot : SLOTS_A) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                giveItemSafely(playerA, item);
                inventory.setItem(slot, null);
            }
        }

        for (int slot : SLOTS_B) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                giveItemSafely(playerB, item);
                inventory.setItem(slot, null);
            }
        }

        if (reason != null) {
            if (playerA.isOnline()) playerA.sendMessage("§c§l[GIAO DỊCH ĐÃ HỦY] §7" + reason);
            if (playerB.isOnline()) playerB.sendMessage("§c§l[GIAO DỊCH ĐÃ HỦY] §7" + reason);
        }

        if (playerA.isOnline() && playerA.getOpenInventory().getTopInventory().equals(inventory)) {
            playerA.closeInventory();
        }
        if (playerB.isOnline() && playerB.getOpenInventory().getTopInventory().equals(inventory)) {
            playerB.closeInventory();
        }

        plugin.getTradeManager().removeActiveSession(this);
    }

    public static int getFreeStorageSlots(Player player) {
        if (player == null) return 0;
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                count++;
            }
        }
        return count;
    }

    private void giveItemSafely(Player player, ItemStack item) {
        if (player == null || item == null) return;
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private ItemStack createGlass(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            if (lore.length > 0) {
                List<Component> list = new ArrayList<>();
                for (String l : lore) list.add(Component.text(l));
                meta.lore(list);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public Player getPlayerA() { return playerA; }
    public Player getPlayerB() { return playerB; }
    public boolean isFinished() { return state == TradeState.COMPLETED || state == TradeState.CANCELLED; }
    public TradeState getState() { return state; }
}

