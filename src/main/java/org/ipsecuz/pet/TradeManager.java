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
    private final Map<UUID, UUID> pendingTrades = new ConcurrentHashMap<>();
    private final Set<TradeSession> activeSessions = ConcurrentHashMap.newKeySet();

    public TradeManager(IpsecuzPet plugin) {
        this.plugin = plugin;
    }

    public void sendTradeRequest(Player sender, Player target) {
        if (!plugin.getModuleManager().isTradeEnabled()) {
            sender.sendMessage("§cTính năng Giao Dịch Pet hiện đang bị tắt bởi máy chủ!");
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

        pendingTrades.put(target.getUniqueId(), sender.getUniqueId());
        sender.sendMessage("§aĐã gửi lời mời giao dịch Pet tới §e" + target.getName() + "§a.");
        target.sendMessage("§e" + sender.getName() + " §7muốn giao dịch Pet với bạn! Nhập §b/pet trade accept §7để chấp nhận.");
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
    }

    public void acceptTrade(Player accepter) {
        if (!plugin.getModuleManager().isTradeEnabled()) {
            accepter.sendMessage("§cTính năng Giao Dịch Pet hiện đang bị tắt bởi máy chủ!");
            return;
        }

        UUID senderId = pendingTrades.remove(accepter.getUniqueId());
        if (senderId == null) {
            accepter.sendMessage("§cBạn không có lời mời giao dịch Pet nào.");
            return;
        }

        Player sender = Bukkit.getPlayer(senderId);
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
