package org.ipsecuz.pet;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TradeManager {
    private final IpsecuzPet plugin;
    public static class TradeRequest {
        private final UUID senderId;
        private final long timestamp;

        public TradeRequest(UUID senderId, long timestamp) {
            this.senderId = senderId;
            this.timestamp = timestamp;
        }

        public UUID getSenderId() { return senderId; }
        public boolean isExpired(long timeoutMs) {
            return (System.currentTimeMillis() - timestamp) > timeoutMs;
        }
    }

    public enum TradeCommitStatus {
        PREPARED,
        COMMITTED
    }

    public static class TradeCommitRecord {
        private final UUID tradeId;
        private final UUID playerA;
        private final UUID playerB;
        private final java.util.List<org.bukkit.inventory.ItemStack> itemsFromA;
        private final java.util.List<org.bukkit.inventory.ItemStack> itemsFromB;
        private final TradeCommitStatus status;
        private final long timestamp;

        public TradeCommitRecord(UUID tradeId, UUID playerA, UUID playerB, java.util.List<org.bukkit.inventory.ItemStack> itemsFromA, java.util.List<org.bukkit.inventory.ItemStack> itemsFromB) {
            this(tradeId, playerA, playerB, itemsFromA, itemsFromB, TradeCommitStatus.PREPARED, System.currentTimeMillis());
        }

        public TradeCommitRecord(UUID tradeId, UUID playerA, UUID playerB, java.util.List<org.bukkit.inventory.ItemStack> itemsFromA, java.util.List<org.bukkit.inventory.ItemStack> itemsFromB, TradeCommitStatus status, long timestamp) {
            this.tradeId = tradeId;
            this.playerA = playerA;
            this.playerB = playerB;
            this.itemsFromA = itemsFromA;
            this.itemsFromB = itemsFromB;
            this.status = status;
            this.timestamp = timestamp;
        }

        public UUID getTradeId() { return tradeId; }
        public UUID getPlayerA() { return playerA; }
        public UUID getPlayerB() { return playerB; }
        public java.util.List<org.bukkit.inventory.ItemStack> getItemsFromA() { return itemsFromA; }
        public java.util.List<org.bukkit.inventory.ItemStack> getItemsFromB() { return itemsFromB; }
        public TradeCommitStatus getStatus() { return status; }
        public long getTimestamp() { return timestamp; }
    }

    private final Map<UUID, TradeRequest> pendingTrades = new ConcurrentHashMap<>();
    private final Map<UUID, TradeCommitRecord> committingTrades = new ConcurrentHashMap<>();
    private final Set<TradeSession> activeSessions = ConcurrentHashMap.newKeySet();

    public void recordCommittingTrade(UUID tradeId, UUID a, UUID b, java.util.List<org.bukkit.inventory.ItemStack> fromA, java.util.List<org.bukkit.inventory.ItemStack> fromB) {
        committingTrades.put(tradeId, new TradeCommitRecord(tradeId, a, b, fromA, fromB, TradeCommitStatus.PREPARED, System.currentTimeMillis()));
        String path = "pending_trade." + tradeId;
        plugin.getConfigManager().getData().set(path + ".player_a", a.toString());
        plugin.getConfigManager().getData().set(path + ".player_b", b.toString());
        plugin.getConfigManager().getData().set(path + ".items_a", fromA);
        plugin.getConfigManager().getData().set(path + ".items_b", fromB);
        plugin.getConfigManager().getData().set(path + ".status", TradeCommitStatus.PREPARED.name());
        plugin.getConfigManager().getData().set(path + ".timestamp", System.currentTimeMillis());
        plugin.getConfigManager().forceSave();
    }

    public void markTradeCommitted(UUID tradeId) {
        TradeCommitRecord current = committingTrades.get(tradeId);
        if (current != null) {
            committingTrades.put(tradeId, new TradeCommitRecord(
                    current.getTradeId(),
                    current.getPlayerA(),
                    current.getPlayerB(),
                    current.getItemsFromA(),
                    current.getItemsFromB(),
                    TradeCommitStatus.COMMITTED,
                    current.getTimestamp()
            ));
        }
        String path = "pending_trade." + tradeId;
        if (plugin.getConfigManager().getData().contains(path)) {
            plugin.getConfigManager().getData().set(path + ".status", TradeCommitStatus.COMMITTED.name());
            plugin.getConfigManager().forceSave();
        }
    }

    public void removeCommittingTrade(UUID tradeId) {
        committingTrades.remove(tradeId);
        plugin.getConfigManager().getData().set("pending_trade." + tradeId, null);
        plugin.getConfigManager().forceSave();
    }

    public Map<UUID, TradeCommitRecord> getCommittingTrades() {
        return committingTrades;
    }

    public TradeManager(IpsecuzPet plugin) {
        this.plugin = plugin;
    }

    public void recoverPendingTrades() {
        var sec = plugin.getConfigManager().getData().getConfigurationSection("pending_trade");
        if (sec == null) return;
        for (String key : new ArrayList<>(sec.getKeys(false))) {
            try {
                UUID tradeId = UUID.fromString(key);
                String statusStr = sec.getString(key + ".status", TradeCommitStatus.PREPARED.name());
                TradeCommitStatus status;
                try {
                    status = TradeCommitStatus.valueOf(statusStr);
                } catch (IllegalArgumentException e) {
                    status = TradeCommitStatus.PREPARED;
                }

                // Nếu đã COMMITTED trước khi tắt server, việc chuyển đồ đã thành công hoàn tất -> TUYỆT ĐỐI không hoàn trả tránh nhân bản đồ!
                if (status == TradeCommitStatus.COMMITTED) {
                    plugin.getLogger().info("§a[IpsecuzPet] Giao dịch " + tradeId + " đã hoàn tất bàn giao trước khi tắt server. Dọn dẹp nhật ký.");
                    sec.set(key, null);
                    continue;
                }

                String aStr = sec.getString(key + ".player_a");
                String bStr = sec.getString(key + ".player_b");
                if (aStr == null || bStr == null) {
                    sec.set(key, null);
                    continue;
                }
                UUID playerA = UUID.fromString(aStr);
                UUID playerB = UUID.fromString(bStr);

                @SuppressWarnings("unchecked")
                java.util.List<org.bukkit.inventory.ItemStack> itemsA = (java.util.List<org.bukkit.inventory.ItemStack>) sec.getList(key + ".items_a");
                @SuppressWarnings("unchecked")
                java.util.List<org.bukkit.inventory.ItemStack> itemsB = (java.util.List<org.bukkit.inventory.ItemStack>) sec.getList(key + ".items_b");

                plugin.getLogger().warning("§e[IpsecuzPet] Phục hồi giao dịch PREPARED dở dang sau khởi động (Crash Recovery): " + tradeId);

                if (itemsA != null && !itemsA.isEmpty()) {
                    Player pA = Bukkit.getPlayer(playerA);
                    if (pA != null && pA.isOnline()) {
                        for (org.bukkit.inventory.ItemStack item : itemsA) {
                            if (item != null) pA.getInventory().addItem(item).values().forEach(drop -> pA.getWorld().dropItemNaturally(pA.getLocation(), drop));
                        }
                    } else {
                        storeOfflineRefund(playerA, itemsA);
                    }
                }

                if (itemsB != null && !itemsB.isEmpty()) {
                    Player pB = Bukkit.getPlayer(playerB);
                    if (pB != null && pB.isOnline()) {
                        for (org.bukkit.inventory.ItemStack item : itemsB) {
                            if (item != null) pB.getInventory().addItem(item).values().forEach(drop -> pB.getWorld().dropItemNaturally(pB.getLocation(), drop));
                        }
                    } else {
                        storeOfflineRefund(playerB, itemsB);
                    }
                }

                sec.set(key, null);
            } catch (Exception ex) {
                plugin.getLogger().severe("Lỗi khi phục hồi giao dịch dở dang " + key + ": " + ex.getMessage());
            }
        }
        plugin.getConfigManager().forceSave();
    }

    private void storeOfflineRefund(UUID playerUuid, java.util.List<org.bukkit.inventory.ItemStack> items) {
        String path = "pending_refund." + playerUuid;
        @SuppressWarnings("unchecked")
        java.util.List<org.bukkit.inventory.ItemStack> existing = (java.util.List<org.bukkit.inventory.ItemStack>) plugin.getConfigManager().getData().getList(path);
        if (existing == null) existing = new java.util.ArrayList<>();
        existing.addAll(items);
        plugin.getConfigManager().getData().set(path, existing);
    }

    public void deliverOfflineRefundOnJoin(Player player) {
        if (player == null) return;
        String path = "pending_refund." + player.getUniqueId();
        if (plugin.getConfigManager().getData().contains(path)) {
            @SuppressWarnings("unchecked")
            java.util.List<org.bukkit.inventory.ItemStack> items = (java.util.List<org.bukkit.inventory.ItemStack>) plugin.getConfigManager().getData().getList(path);
            if (items != null && !items.isEmpty()) {
                plugin.getLogger().info("§a[IpsecuzPet] Hoàn trả " + items.size() + " vật phẩm giao dịch dở dang cho: " + player.getName());
                for (org.bukkit.inventory.ItemStack item : items) {
                    if (item != null) {
                        player.getInventory().addItem(item).values().forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
                    }
                }
                player.sendMessage("§a[IpsecuzPet] Bạn đã nhận lại các vật phẩm từ phiên giao dịch bị gián đoạn trước đó!");
            }
            plugin.getConfigManager().getData().set(path, null);
            plugin.getConfigManager().forceSave();
        }
    }

    public void sendTradeRequest(Player sender, Player target) {
        if (!plugin.getModuleManager().isTradeEnabled()) {
            sender.sendMessage(plugin.getLanguage().getMessage("trade.disabled"));
            return;
        }

        if (!sender.hasPermission("ipsecuzpet.trade")) {
            sender.sendMessage(plugin.getLanguage().getMessage("general.no_permission"));
            return;
        }

        if (target == null || !target.isOnline() || target.equals(sender)) {
            sender.sendMessage(plugin.getLanguage().getMessage("trade.invalid_target"));
            return;
        }

        if (isInTrade(sender) || isInTrade(target)) {
            sender.sendMessage(plugin.getLanguage().getMessage("trade.already_trading"));
            return;
        }

        long timeoutMs = plugin.getModuleManager().getTradeConfig().getInt("trade_request_timeout_seconds", 60) * 1000L;
        pendingTrades.put(target.getUniqueId(), new TradeRequest(sender.getUniqueId(), System.currentTimeMillis()));
        sender.sendMessage(plugin.getLanguage().getMessage("trade.invite_sent", "%player%", target.getName()));
        target.sendMessage(plugin.getLanguage().getMessage("trade.invite_received", "%player%", sender.getName()));
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
    }

    public void acceptTrade(Player accepter) {
        if (!plugin.getModuleManager().isTradeEnabled()) {
            accepter.sendMessage(plugin.getLanguage().getMessage("trade.disabled"));
            return;
        }

        if (!accepter.hasPermission("ipsecuzpet.trade")) {
            accepter.sendMessage(plugin.getLanguage().getMessage("general.no_permission"));
            return;
        }

        TradeRequest req = pendingTrades.remove(accepter.getUniqueId());
        long timeoutMs = plugin.getModuleManager().getTradeConfig().getInt("trade_request_timeout_seconds", 60) * 1000L;
        if (req == null || req.isExpired(timeoutMs)) {
            accepter.sendMessage(plugin.getLanguage().getMessage("trade.no_invite"));
            return;
        }

        Player sender = Bukkit.getPlayer(req.getSenderId());
        if (sender == null || !sender.isOnline()) {
            accepter.sendMessage(plugin.getLanguage().getMessage("trade.sender_offline"));
            return;
        }

        if (isInTrade(sender) || isInTrade(accepter)) {
            accepter.sendMessage(plugin.getLanguage().getMessage("trade.already_trading"));
            return;
        }

        TradeSession session = new TradeSession(plugin, sender, accepter);
        activeSessions.add(session);
        session.open();
    }

    public void handlePlayerQuit(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        TradeSession session = getSession(player);
        if (session != null) {
            session.cancel(player.getName() + " đã thoát game.");
        }
        pendingTrades.remove(uuid);
        pendingTrades.entrySet().removeIf(entry -> entry.getValue().getSenderId().equals(uuid));
    }

    public boolean isInTrade(Player player) {
        return getSession(player) != null;
    }

    public TradeSession getSession(Player player) {
        for (TradeSession session : activeSessions) {
            if (player.equals(session.getPlayerA()) || player.equals(session.getPlayerB())) {
                return session;
            }
        }
        return null;
    }

    public void removeActiveSession(TradeSession session) {
        activeSessions.remove(session);
    }

    public void cancelAllActiveTrades() {
        for (TradeSession session : activeSessions) {
            session.cancel("Máy chủ khởi động lại hoặc tắt tính năng giao dịch.");
        }
        activeSessions.clear();
        pendingTrades.clear();
    }
}
