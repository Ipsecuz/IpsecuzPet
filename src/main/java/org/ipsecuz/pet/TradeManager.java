package org.ipsecuz.pet;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TradeManager {
    private final IpsecuzPet plugin;
    private final Map<UUID, UUID> pendingTrades = new ConcurrentHashMap<>();

    public TradeManager(IpsecuzPet plugin) {
        this.plugin = plugin;
    }

    public void sendTradeRequest(Player sender, Player target) {
        if (target == null || !target.isOnline() || target.equals(sender)) {
            sender.sendMessage("§cNgười chơi không hợp lệ hoặc đang offline!");
            return;
        }

        pendingTrades.put(target.getUniqueId(), sender.getUniqueId());
        sender.sendMessage("§aĐã gửi lời mời giao dịch Pet tới §e" + target.getName() + "§a.");
        target.sendMessage("§e" + sender.getName() + " §7muốn giao dịch Pet với bạn! Nhập §b/pet trade accept §7để chấp nhận.");
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
    }

    public void acceptTrade(Player accepter) {
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

        accepter.sendMessage("§aĐã chấp nhận giao dịch Pet với §e" + sender.getName() + "§a!");
        sender.sendMessage("§e" + accepter.getName() + " §ađã chấp nhận giao dịch Pet!");
        // Cả 2 người chơi có thể dùng /pet withdraw để đổi thành thẻ pet và giao dịch an toàn
        accepter.sendMessage("§7[Mẹo] Bạn có thể dùng §e/pet withdraw <pet_id> §7để rút pet thành thẻ an toàn rồi trao đổi.");
        sender.sendMessage("§7[Mẹo] Bạn có thể dùng §e/pet withdraw <pet_id> §7để rút pet thành thẻ an toàn rồi trao đổi.");
    }
}

