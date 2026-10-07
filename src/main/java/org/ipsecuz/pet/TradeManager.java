package org.ipsecuz.pet;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

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

    private final Map<UUID, TradeRequest> pendingTrades = new ConcurrentHashMap<>();
    private final Set<TradeSession> activeSessions = ConcurrentHashMap.newKeySet();

    public TradeManager(IpsecuzPet plugin) {
        this.plugin = plugin;
    }

    public void sendTradeRequest(Player sender, Player target) {
        if (!plugin.getModuleManager().isTradeEnabled()) {
            sender.sendMessage("§cTính năng Giao Dịch Pet hiện đang bị tắt bởi máy chủ!");
            return;
        }

        if (!sender.hasPermission("ipsecuzpet.trade")) {
            sender.sendMessage(plugin.getLanguage().getMessage("general.no_permission"));
            return;
        }

        if (target == null || !target.isOnline() || target.equals(sender)) {
            sender.sendMessage("§cNgười chơi không hợp lệ hoặc đang offline!");
            return;
        }

        if (isInTrade(sender) || isInTrade(target)) {
            sender.sendMessage("§cMột trong hai người chơi hiện đang trong phiên giao dịch khác!");
            return;
        }

        long timeoutMs = plugin.getModuleManager().getTradeConfig().getInt("trade_request_timeout_seconds", 60) * 1000L;
        pendingTrades.put(target.getUniqueId(), new TradeRequest(sender.getUniqueId(), System.currentTimeMillis()));
        sender.sendMessage("§aĐã gửi lời mời giao dịch Pet tới §e" + target.getName() + "§a (Hết hạn sau 60s).");
        target.sendMessage("§e" + sender.getName() + " §7muốn giao dịch Pet với bạn! Nhập §b/pet trade accept §7để chấp nhận.");
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
    }

    public void acceptTrade(Player accepter) {
        if (!plugin.getModuleManager().isTradeEnabled()) {
            accepter.sendMessage("§cTính năng Giao Dịch Pet hiện đang bị tắt bởi máy chủ!");
            return;
        }

        if (!accepter.hasPermission("ipsecuzpet.trade")) {
            accepter.sendMessage(plugin.getLanguage().getMessage("general.no_permission"));
            return;
        }

        TradeRequest req = pendingTrades.remove(accepter.getUniqueId());
        long timeoutMs = plugin.getModuleManager().getTradeConfig().getInt("trade_request_timeout_seconds", 60) * 1000L;
        if (req == null || req.isExpired(timeoutMs)) {
            accepter.sendMessage("§cBạn không có lời mời giao dịch Pet nào (hoặc lời mời đã hết hạn).");
            return;
        }

        Player sender = Bukkit.getPlayer(req.getSenderId());
        if (sender == null || !sender.isOnline()) {
            accepter.sendMessage("§cNgười gửi lời mời hiện đã offline!");
            return;
        }

        if (isInTrade(sender) || isInTrade(accepter)) {
            accepter.sendMessage("§cMột trong hai người chơi hiện đang trong phiên giao dịch khác!");
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
