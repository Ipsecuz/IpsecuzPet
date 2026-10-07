package org.ipsecuz.pet;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
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
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class PetCommand implements CommandExecutor {
    private final IpsecuzPet plugin;

    public PetCommand(IpsecuzPet plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        LanguageManager lang = plugin.getLanguage();

        // 1. Điều hướng trực tiếp bí danh /pethatch
        if (label.equalsIgnoreCase("pethatch")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(lang.getMessage("general.player_only"));
                return true;
            }
            if (!p.hasPermission("ipsecuzpet.hatch")) {
                p.sendMessage(lang.getMessage("general.no_permission"));
                return true;
            }
            plugin.getHatchingManager().openHatchingGui(p);
            return true;
        }

        // 2. Hỗ trợ thực thi lệnh Quản trị viên từ máy chủ (Console)
        if (args.length > 0) {
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "reload":
                    return handleReload(sender, lang);
                case "give":
                    return handleGive(sender, args, lang);
                case "giveball":
                    return handleGiveBall(sender, args, lang);
                case "giveegg":
                    return handleGiveEgg(sender, args, lang);
                case "model":
                    if (args.length > 1 && args[1].equalsIgnoreCase("status")) {
                        return handleModelStatus(sender, lang);
                    }
                    sender.sendMessage("§cUsage: /pet model status");
                    return true;
            }
        }

        // 3. Các lệnh người chơi yêu cầu Player
        if (!(sender instanceof Player p)) {
            sender.sendMessage(lang.getMessage("general.player_only"));
            return true;
        }

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
                p.sendMessage(lang.getMessage("help.cmd_hatch"));
                p.sendMessage(lang.getMessage("help.cmd_codex"));
                p.sendMessage(lang.getMessage("help.cmd_shards"));
                p.sendMessage(lang.getMessage("help.cmd_baby"));
                p.sendMessage(lang.getMessage("help.cmd_feed"));
                p.sendMessage(lang.getMessage("help.cmd_skill"));
                p.sendMessage(lang.getMessage("help.cmd_star"));
                p.sendMessage(lang.getMessage("help.cmd_trade"));
                p.sendMessage(lang.getMessage("help.cmd_duel"));
                p.sendMessage(lang.getMessage("help.cmd_accept"));
                if (p.hasPermission("ipsecuzpet.admin")) {
                    p.sendMessage(lang.getMessage("help.cmd_model_status"));
                    p.sendMessage(lang.getMessage("help.cmd_give"));
                    p.sendMessage(lang.getMessage("help.cmd_giveball"));
                    p.sendMessage(lang.getMessage("help.cmd_giveegg"));
                    p.sendMessage(lang.getMessage("help.cmd_reload"));
                }
                p.sendMessage(lang.getMessage("help.footer"));
                break;

            case "model":
                if (args.length > 1 && args[1].equalsIgnoreCase("status")) {
                    return handleModelStatus(p, lang);
                }
                p.sendMessage("§cUsage: /pet model status");
                break;

            case "shop":
                GuiListener.openShopMenu(p);
                break;

            case "codex":
                GuiListener.openCodexMenu(p);
                break;

            case "shards":
                GuiListener.openShardsMenu(p);
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

                String cleanRaw = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', newName));
                if (cleanRaw.length() < 2 || cleanRaw.length() > 24) {
                    p.sendMessage("§cTên thú cưng phải có độ dài từ 2 đến 24 ký tự!");
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return true;
                }

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
                if (!p.hasPermission("ipsecuzpet.withdraw")) {
                    p.sendMessage(lang.getMessage("general.no_permission"));
                    return true;
                }
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

                ItemStack[] snapshot = p.getInventory().getStorageContents().clone();
                int wdLvl = conf.getData().getInt(p.getUniqueId() + ".pets." + wdId + ".level", 1);
                int wdExp = conf.getData().getInt(p.getUniqueId() + ".pets." + wdId + ".exp", 0);
                int stars = (plugin.getEvolutionManager() != null) ? plugin.getEvolutionManager().getStar(p.getUniqueId(), wdId) : 1;
                String trait = conf.getData().getString(p.getUniqueId() + ".pets." + wdId + ".trait", "NONE");
                String customName = conf.getCustomName(p.getUniqueId(), wdId);
                String wdName = plugin.getConfig().getString("pets." + wdId + ".name", wdId);
                List<String> unlockedSkills = conf.getData().getStringList(p.getUniqueId() + ".pets." + wdId + ".unlocked_skills");

                ItemStack cardItem = PetCardSecurity.createPetCard(plugin, wdId, wdLvl, wdExp, stars, trait, customName, unlockedSkills);
                HashMap<Integer, ItemStack> overflow = p.getInventory().addItem(cardItem);
                if (!overflow.isEmpty()) {
                    p.getInventory().setStorageContents(snapshot);
                    p.sendMessage(lang.getMessage("general.inventory_full"));
                    return true;
                }
                conf.deletePetData(p.getUniqueId(), wdId);
                conf.forceSave();
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
                if (!plugin.getPetManager().sendDuelInvite(p, target)) {
                    p.sendMessage("§cKhông thể gửi lời mời thách đấu (đã có lời mời đang chờ hoặc đối thủ đang bận)!");
                    return true;
                }
                p.sendMessage(lang.getMessage("duel.invite_sent", "%target%", target.getName()));
                target.sendMessage(lang.getMessage("duel.invite_received", "%player%", p.getName()));
                target.sendMessage("§7Dùng §a/pet accept §7để chấp nhận hoặc §c/pet deny §7để từ chối (Thời hạn 60s).");
                break;

            case "accept":
                UUID duelPartner = plugin.getPetManager().acceptDuelInvite(p);
                if (duelPartner != null) {
                    p.sendMessage(lang.getMessage("duel.accepted"));
                    Player opp = Bukkit.getPlayer(duelPartner);
                    if (opp != null && opp.isOnline()) {
                        opp.sendMessage(lang.getMessage("duel.opponent_accepted"));
                    }
                } else {
                    p.sendMessage(lang.getMessage("duel.no_invite"));
                }
                break;

            case "deny":
                UUID deniedSender = plugin.getPetManager().denyDuelInvite(p);
                if (deniedSender != null) {
                    p.sendMessage("§eBạn đã từ chối lời mời thách đấu!");
                    Player opp = Bukkit.getPlayer(deniedSender);
                    if (opp != null && opp.isOnline()) {
                        opp.sendMessage("§c" + p.getName() + " đã từ chối lời mời thách đấu của bạn.");
                    }
                } else {
                    p.sendMessage("§cBạn không có lời mời thách đấu nào đang chờ!");
                }
                break;

            case "cancelduel":
            case "duelcancel":
                if (plugin.getPetManager().cancelOutgoingDuel(p)) {
                    p.sendMessage("§eĐã hủy lời mời thách đấu đang chờ!");
                } else {
                    p.sendMessage("§cBạn không có lời mời thách đấu nào đang chờ phản hồi.");
                }
                break;

            case "give":
                return handleGive(p, args, lang);

            case "giveball":
                return handleGiveBall(p, args, lang);

            case "giveegg":
                return handleGiveEgg(p, args, lang);

            case "baby":
            case "form":
                if (!plugin.getPetManager().hasPet(p.getUniqueId())) {
                    p.sendMessage(lang.getMessage("pet.no_pet"));
                    return true;
                }
                String curActivePet = plugin.getPetManager().getActivePetId(p.getUniqueId());
                boolean isBaby = plugin.getConfigManager().isPetBaby(p.getUniqueId(), curActivePet);
                plugin.getConfigManager().setPetBaby(p.getUniqueId(), curActivePet, !isBaby);
                p.sendMessage("§aĐã đổi dạng kích thước của Pet thành: " + (!isBaby ? "§b👶 Bé con (Baby)" : "§6🦁 Trưởng thành (Adult)"));
                plugin.getPetManager().spawnPet(p, curActivePet);
                break;

            case "hatch":
            case "incubator":
                if (!p.hasPermission("ipsecuzpet.hatch")) {
                    p.sendMessage(lang.getMessage("general.no_permission"));
                    return true;
                }
                plugin.getHatchingManager().openHatchingGui(p);
                break;

            case "feed":
                ItemStack handItem = p.getInventory().getItemInMainHand();
                plugin.getFeedingManager().feedPet(p, handItem);
                break;

            case "skill":
            case "ultimate":
                plugin.getSkillManager().triggerUltimate(p);
                break;

            case "star":
            case "evolve":
                if (!plugin.getPetManager().hasPet(p.getUniqueId())) {
                    p.sendMessage(lang.getMessage("pet.no_pet"));
                    return true;
                }
                String starPetId = plugin.getPetManager().getActivePetId(p.getUniqueId());
                plugin.getEvolutionManager().upgradeStar(p, starPetId);
                break;

            case "trade":
                if (!p.hasPermission("ipsecuzpet.trade")) {
                    p.sendMessage(lang.getMessage("general.no_permission"));
                    return true;
                }
                if (args.length < 2) {
                    p.sendMessage("§cCú pháp: /pet trade <player> hoặc /pet trade accept");
                    return true;
                }
                if (args[1].equalsIgnoreCase("accept")) {
                    plugin.getTradeManager().acceptTrade(p);
                } else {
                    Player tradeTarget = Bukkit.getPlayer(args[1]);
                    plugin.getTradeManager().sendTradeRequest(p, tradeTarget);
                }
                break;

            case "reload":
                return handleReload(p, lang);
        }
        return true;
    }

    private boolean handleModelStatus(CommandSender sender, LanguageManager lang) {
        if (!sender.hasPermission("ipsecuzpet.admin")) {
            sender.sendMessage(lang.getMessage("general.no_permission"));
            return true;
        }

        var mm = plugin.getModelProviderManager();
        boolean bm = mm != null && mm.getBetterModelProvider().isAvailable();
        boolean me = mm != null && mm.getModelEngineProvider().isAvailable();
        String bmStatus = bm ? lang.getMessage("model.installed") : lang.getMessage("model.not_installed");
        String meStatus = me ? lang.getMessage("model.installed") : lang.getMessage("model.not_installed");
        String globalProvider = plugin.getConfig().getString("model.provider", "AUTO");
        String activeProvider = (mm != null) ? mm.getActiveProviderName() : "None";

        sender.sendMessage(lang.getMessage("model.status_header"));
        sender.sendMessage(lang.getMessage("model.status_title"));
        sender.sendMessage(lang.getMessage("model.bettermodel_label", "%status%", bmStatus));
        sender.sendMessage(lang.getMessage("model.modelengine_label", "%status%", meStatus));
        sender.sendMessage(lang.getMessage("model.global_provider", "%provider%", globalProvider));
        sender.sendMessage(lang.getMessage("model.active_provider", "%provider%", activeProvider));
        sender.sendMessage(lang.getMessage("model.status_footer"));
        return true;
    }

    private boolean handleReload(CommandSender sender, LanguageManager lang) {
        if (!sender.hasPermission("ipsecuzpet.admin")) {
            sender.sendMessage(lang.getMessage("general.no_permission"));
            return true;
        }
        plugin.reloadConfig();
        plugin.getLanguage().loadMessages();
        plugin.getConfigManager().loadDataFile();
        plugin.getCaptureManager().loadBalls();
        if (plugin.getModelHandler() != null && plugin.getModelHandler().getManager() != null) {
            plugin.getModelHandler().getManager().reload();
        }
        if (plugin.getModuleManager() != null) {
            plugin.getModuleManager().reloadAllModules();
        }
        if (plugin.getDynamicPetRegistry() != null) {
            plugin.getDynamicPetRegistry().detectAndRegisterNewMobs();
        }
        sender.sendMessage(plugin.getLanguage().getMessage("general.config_reloaded"));
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args, LanguageManager lang) {
        if (!sender.hasPermission("ipsecuzpet.admin")) {
            sender.sendMessage(lang.getMessage("general.no_permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(lang.getMessage("admin.give_usage"));
            return true;
        }
        Player receiver = Bukkit.getPlayer(args[1]);
        if (receiver == null) {
            sender.sendMessage(lang.getMessage("duel.invalid_target"));
            return true;
        }
        String petIdToGive = args[2];
        if (!plugin.getConfig().contains("pets." + petIdToGive)) {
            sender.sendMessage(lang.getMessage("admin.invalid_id"));
            return true;
        }
        if (plugin.getConfigManager().getData().contains(receiver.getUniqueId() + ".pets." + petIdToGive)) {
            sender.sendMessage(lang.getMessage("admin.already_owned"));
            return true;
        }
        if (!plugin.getOwnershipManager().canAcquirePet(receiver)) {
            sender.sendMessage(lang.getMessage("pet.limit_reached", "%max_pets%", String.valueOf(plugin.getConfig().getInt("max_pets", 2))));
            return true;
        }
        plugin.getConfigManager().createPetDataIfMissing(receiver.getUniqueId(), petIdToGive);
        PetTrait giveTrait = PetTrait.rollRandomTrait();
        plugin.getConfigManager().getData().set(receiver.getUniqueId() + ".pets." + petIdToGive + ".trait", giveTrait.name());
        plugin.getConfigManager().saveData();
        plugin.getCodexManager().discover(receiver.getUniqueId(), petIdToGive);
        sender.sendMessage(lang.getMessage("admin.give_success", "%pet_id%", petIdToGive, "%player%", receiver.getName()));
        receiver.sendMessage(lang.getMessage("admin.give_received", "%pet_id%", petIdToGive));
        return true;
    }

    private boolean handleGiveBall(CommandSender sender, String[] args, LanguageManager lang) {
        if (!sender.hasPermission("ipsecuzpet.admin")) {
            sender.sendMessage(lang.getMessage("general.no_permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(lang.getMessage("admin.giveball_usage"));
            return true;
        }
        Player targetP = Bukkit.getPlayer(args[1]);
        if (targetP == null) {
            sender.sendMessage(lang.getMessage("duel.invalid_target"));
            return true;
        }
        String ballId = args[2];
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(lang.getMessage("general.invalid_amount"));
                return true;
            }
        }
        if (amount <= 0 || amount > 2304) {
            sender.sendMessage(lang.getMessage("general.invalid_amount"));
            return true;
        }

        if (!plugin.getCaptureManager().getBallIds().contains(ballId)) {
            sender.sendMessage(lang.getMessage("admin.invalid_ball_id"));
            return true;
        }

        int remainingBalls = amount;
        while (remainingBalls > 0) {
            int batch = Math.min(remainingBalls, 64);
            ItemStack ballItem = plugin.getCaptureManager().getBallItem(ballId, batch);
            if (ballItem != null) {
                HashMap<Integer, ItemStack> overflow = targetP.getInventory().addItem(ballItem);
                for (ItemStack left : overflow.values()) {
                    targetP.getWorld().dropItemNaturally(targetP.getLocation(), left);
                }
            }
            remainingBalls -= batch;
        }

        sender.sendMessage(lang.getMessage("admin.giveball_success", "%amount%", String.valueOf(amount), "%ball_id%", ballId, "%player%", targetP.getName()));
        targetP.sendMessage(lang.getMessage("admin.giveball_received"));
        return true;
    }

    private boolean handleGiveEgg(CommandSender sender, String[] args, LanguageManager lang) {
        if (!sender.hasPermission("ipsecuzpet.admin")) {
            sender.sendMessage(lang.getMessage("general.no_permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(lang.getMessage("admin.giveegg_usage"));
            return true;
        }
        Player targetEggP = Bukkit.getPlayer(args[1]);
        if (targetEggP == null) {
            sender.sendMessage(lang.getMessage("duel.invalid_target"));
            return true;
        }
        String eggId = args[2];
        int eggAmount = 1;
        if (args.length >= 4) {
            try {
                eggAmount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(lang.getMessage("general.invalid_amount"));
                return true;
            }
        }
        if (eggAmount <= 0 || eggAmount > 2304) {
            sender.sendMessage(lang.getMessage("general.invalid_amount"));
            return true;
        }

        if (plugin.getModuleManager() == null || !plugin.getModuleManager().getHatchingConfig().contains("eggs." + eggId)) {
            sender.sendMessage(lang.getMessage("admin.invalid_egg_id"));
            return true;
        }

        int remainingEggs = eggAmount;
        while (remainingEggs > 0) {
            int batch = Math.min(remainingEggs, 64);
            ItemStack eggItem = plugin.getHatchingManager().createEggItem(eggId, batch);
            if (eggItem != null) {
                HashMap<Integer, ItemStack> overflow = targetEggP.getInventory().addItem(eggItem);
                for (ItemStack left : overflow.values()) {
                    targetEggP.getWorld().dropItemNaturally(targetEggP.getLocation(), left);
                }
            }
            remainingEggs -= batch;
        }

        sender.sendMessage(lang.getMessage("admin.giveegg_success", "%amount%", String.valueOf(eggAmount), "%egg_id%", eggId, "%player%", targetEggP.getName()));
        targetEggP.sendMessage(lang.getMessage("admin.giveegg_received"));
        return true;
    }
}