package org.ipsecuz.pet;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PetCommand implements CommandExecutor {
    private final IpsecuzPet plugin;

    public PetCommand(IpsecuzPet plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(plugin.getLanguage().getMessage("general.player_only"));
            return true;
        }

        LanguageManager lang = plugin.getLanguage();

        if (args.length == 0) {
            GuiListener.openPetMenu(p);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help":
                p.sendMessage(lang.getMessage("help.header"));
                p.sendMessage(lang.getMessage("help.title"));
                p.sendMessage(lang.getMessage("help.cmd_gui"));
                p.sendMessage(lang.getMessage("help.cmd_shop"));
                p.sendMessage(lang.getMessage("help.cmd_despawn"));
                p.sendMessage(lang.getMessage("help.cmd_duel"));
                p.sendMessage(lang.getMessage("help.cmd_accept"));
                p.sendMessage("§e/pet stats §7- Xem chỉ số Pet");
                p.sendMessage("§e/pet withdraw <id> §7- Đổi Pet thành vật phẩm");
                p.sendMessage("§e/pet rename <tên> §7- Đổi tên Pet");
                if (p.hasPermission("ipsecuzpet.admin")) {
                    p.sendMessage("§c/pet give <player> <pet_id> §7- Admin Give Pet");
                    p.sendMessage("§c/pet giveball <player> <ball_id> <amount> §7- Give Ball");
                    p.sendMessage("§c/pet reload §7- Admin Reload");
                }
                p.sendMessage(lang.getMessage("help.footer"));
                break;

            case "shop":
                GuiListener.openShopMenu(p);
                break;

            case "despawn":
                if (plugin.getPetManager().hasPet(p.getUniqueId())) {
                    plugin.getPetManager().removePet(p.getUniqueId());
                    p.sendMessage(lang.getMessage("pet.despawn"));
                } else {
                    p.sendMessage(lang.getMessage("pet.no_pet"));
                }
                break;

            case "stats":
            case "info":
                if (!plugin.getPetManager().hasPet(p.getUniqueId())) {
                    p.sendMessage(lang.getMessage("pet.no_pet"));
                    return true;
                }
                Entity pet = plugin.getPetManager().getPet(p.getUniqueId());
                if (pet != null) {
                    plugin.getPetManager().showPetStats(p, pet);
                }
                break;

            case "rename":
                if (!p.hasPermission("ipsecuzpet.rename")) {
                    p.sendMessage(lang.getMessage("general.no_permission"));
                    return true;
                }
                if (!plugin.getPetManager().hasPet(p.getUniqueId())) {
                    p.sendMessage(lang.getMessage("pet.no_pet"));
                    return true;
                }
                if (args.length < 2) {
                    p.sendMessage("§cCú pháp: /pet rename <tên_mới>");
                    return true;
                }
                String petId = plugin.getPetManager().getActivePetId(p.getUniqueId());
                String newName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

                plugin.getConfigManager().setCustomName(p.getUniqueId(), petId, newName);

                Entity petEntity = plugin.getPetManager().getPet(p.getUniqueId());
                if (petEntity != null) {
                    int lvl = plugin.getConfigManager().getData().getInt(p.getUniqueId() + ".pets." + petId + ".level", 1);
                    String format = plugin.getLanguage().getMessage("pet.display_format");

                    String displayName = format.replace("%name%", newName)
                            .replace("%level%", String.valueOf(lvl))
                            .replace("%player%", p.getName())
                            .replace("%owner%", p.getName());

                    petEntity.setCustomName(ChatColor.translateAlternateColorCodes('&', displayName));
                    petEntity.setCustomNameVisible(true);
                }

                p.sendMessage(lang.getMessage("pet.rename_success", "%new_name%", newName));
                break;

            case "withdraw":
                if (args.length < 2) {
                    p.sendMessage(lang.getMessage("pet.withdraw_usage"));
                    return true;
                }
                String wdId = args[1];
                ConfigManager conf = plugin.getConfigManager();
                if (!conf.getData().contains(p.getUniqueId() + ".pets." + wdId)) {
                    p.sendMessage(lang.getMessage("pet.not_owned", "%pet_id%", wdId));
                    return true;
                }
                if (conf.isPetDead(p.getUniqueId(), wdId)) {
                    p.sendMessage(lang.getMessage("pet.cannot_withdraw_dead"));
                    return true;
                }

                if (plugin.getPetManager().hasPet(p.getUniqueId()) &&
                        plugin.getPetManager().getActivePetId(p.getUniqueId()).equals(wdId)) {
                    p.sendMessage(lang.getMessage("pet.cannot_withdraw_active"));
                    return true;
                }

                int wdLvl = conf.getData().getInt(p.getUniqueId() + ".pets." + wdId + ".level");
                int wdExp = conf.getData().getInt(p.getUniqueId() + ".pets." + wdId + ".exp");
                String wdName = plugin.getConfig().getString("pets." + wdId + ".name");

                ItemStack item = new ItemStack(Material.DRAGON_EGG);
                ItemMeta meta = item.getItemMeta();
                meta.displayName(Component.text("§6📦 " + wdName.replace("&", "§") + " §e(Lv." + wdLvl + ")"));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("§7--------------------"));
                lore.add(Component.text("§7Loại: §f" + wdId));
                lore.add(Component.text("§7Cấp độ: §a" + wdLvl));
                lore.add(Component.text("§7EXP: §b" + wdExp));
                lore.add(Component.text("§7--------------------"));
                lore.add(Component.text("§e[Chuột phải để Nhận Pet]"));
                meta.lore(lore);
                meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "pet_item_id"), PersistentDataType.STRING, wdId);
                meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "pet_item_lvl"), PersistentDataType.INTEGER, wdLvl);
                meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "pet_item_exp"), PersistentDataType.INTEGER, wdExp);
                item.setItemMeta(meta);

                if (p.getInventory().firstEmpty() == -1) {
                    p.sendMessage(lang.getMessage("general.inventory_full"));
                    return true;
                }
                p.getInventory().addItem(item);
                conf.deletePetData(p.getUniqueId(), wdId);
                p.sendMessage(lang.getMessage("pet.withdraw_success", "%pet_name%", wdName));
                break;

            case "duel":
                if (args.length < 2) {
                    p.sendMessage(lang.getMessage("duel.usage"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null || target.equals(p)) {
                    p.sendMessage(lang.getMessage("duel.invalid_target"));
                    return true;
                }
                if (plugin.getPetManager().activeDuels.containsKey(p.getUniqueId()) || plugin.getPetManager().activeDuels.containsValue(p.getUniqueId())) {
                    p.sendMessage(lang.getMessage("duel.already_dueling"));
                    return true;
                }
                plugin.getPetManager().duelRequests.put(p.getUniqueId(), target.getUniqueId());
                p.sendMessage(lang.getMessage("duel.invite_sent", "%target%", target.getName()));
                target.sendMessage(lang.getMessage("duel.invite_received", "%player%", p.getName()));
                break;

            case "accept":
                if (plugin.getPetManager().duelRequests.containsValue(p.getUniqueId())) {
                    plugin.getPetManager().duelRequests.forEach((k, v) -> {
                        if (v.equals(p.getUniqueId())) {
                            plugin.getPetManager().activeDuels.put(k, v);
                            plugin.getPetManager().activeDuels.put(v, k);
                            p.sendMessage(lang.getMessage("duel.accepted"));
                            Player opp = Bukkit.getPlayer(k);
                            if (opp != null) opp.sendMessage(lang.getMessage("duel.opponent_accepted"));
                        }
                    });
                    plugin.getPetManager().duelRequests.values().remove(p.getUniqueId());
                } else {
                    p.sendMessage(lang.getMessage("duel.no_invite"));
                }
                break;

            case "give":
                if (!p.hasPermission("ipsecuzpet.admin")) {
                    p.sendMessage(lang.getMessage("general.no_permission"));
                    return true;
                }
                if (args.length < 3) {
                    p.sendMessage("§cCú pháp: /pet give <player> <pet_id>");
                    return true;
                }
                Player receiver = Bukkit.getPlayer(args[1]);
                if (receiver == null) {
                    p.sendMessage(lang.getMessage("duel.invalid_target"));
                    return true;
                }
                String petIdToGive = args[2];
                if (!plugin.getConfig().contains("pets." + petIdToGive)) {
                    p.sendMessage(lang.getMessage("admin.invalid_id"));
                    return true;
                }
                if (plugin.getConfigManager().getData().contains(receiver.getUniqueId() + ".pets." + petIdToGive)) {
                    p.sendMessage(lang.getMessage("admin.already_owned"));
                    return true;
                }
                plugin.getConfigManager().createPetDataIfMissing(receiver.getUniqueId(), petIdToGive);
                p.sendMessage(lang.getMessage("admin.give_success", "%pet_id%", petIdToGive, "%player%", receiver.getName()));
                receiver.sendMessage(lang.getMessage("admin.give_received", "%pet_id%", petIdToGive));
                break;

            case "giveball":
                if (!p.hasPermission("ipsecuzpet.admin")) {
                    p.sendMessage(lang.getMessage("general.no_permission"));
                    return true;
                }
                if (args.length < 3) {
                    p.sendMessage("§cCú pháp: /pet giveball <player> <ball_id> [amount]");
                    return true;
                }
                Player targetP = Bukkit.getPlayer(args[1]);
                if (targetP == null) {
                    p.sendMessage(lang.getMessage("duel.invalid_target"));
                    return true;
                }
                String ballId = args[2];
                int amount = 1;
                if (args.length >= 4) {
                    try { amount = Integer.parseInt(args[3]); } catch (NumberFormatException e) { amount = 1; }
                }

                ItemStack ballItem = plugin.getCaptureManager().getBallItem(ballId, amount);
                if (ballItem == null) {
                    p.sendMessage(lang.getMessage("admin.invalid_ball_id"));
                    return true;
                }

                targetP.getInventory().addItem(ballItem);
                p.sendMessage(lang.getMessage("admin.giveball_success", "%amount%", String.valueOf(amount), "%ball_id%", ballId, "%player%", targetP.getName()));
                targetP.sendMessage(lang.getMessage("admin.giveball_received"));
                break;

            case "reload":
                if(p.hasPermission("ipsecuzpet.admin")) {
                    plugin.reloadConfig();
                    plugin.getLanguage().loadMessages();
                    plugin.getConfigManager().loadDataFile();
                    plugin.getCaptureManager().loadBalls();
                    p.sendMessage(lang.getMessage("general.config_reloaded"));
                } else {
                    p.sendMessage(lang.getMessage("general.no_permission"));
                }
                break;
        }
        return true;
    }
}