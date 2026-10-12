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
import java.util.Map;
import java.util.UUID;

public class PetCommand implements CommandExecutor {
    private final IpsecuzPet plugin;

    public PetCommand(IpsecuzPet plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        LanguageManager lang = plugin.getLanguage();

        // 1. Điều hướng trực tiếp bí danh /pethatch hoặc /givepet
        if (label.equalsIgnoreCase("pethatch") || label.toLowerCase().endsWith(":pethatch")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(lang.getMessage("general.player_only"));
                return true;
            }
            if (!p.hasPermission("ipsecuzpet.hatch")) {
                p.sendMessage(lang.getMessage("general.no_permission"));
                return true;
            }
            plugin.getDialogService().openHatchSelect(p);
            return true;
        }

        if (label.equalsIgnoreCase("givepet") || label.toLowerCase().endsWith(":givepet")) {
            if (!sender.hasPermission("ipsecuzpet.admin")) {
                sender.sendMessage(lang.getMessage("general.no_permission"));
                return true;
            }
            String[] giveArgs = new String[args.length + 1];
            giveArgs[0] = "give";
            System.arraycopy(args, 0, giveArgs, 1, args.length);
            return handleGive(sender, giveArgs, lang);
        }

        // 2. Hỗ trợ thực thi lệnh Quản trị viên từ máy chủ (Console)
        if (args.length > 0) {
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "reload":
                    return handleReload(sender, lang);
                case "give":
                case "givepet":
                    return handleGive(sender, args, lang);
                case "giveball":
                    return handleGiveBall(sender, args, lang);
                case "giveegg":
                    return handleGiveEgg(sender, args, lang);
                case "model":
                    if (args.length > 1 && args[1].equalsIgnoreCase("status")) {
                        return handleModelStatus(sender, args, lang);
                    }
                    sender.sendMessage("§cUsage: /pet model status [pet_id]");
                    return true;
                case "admin":
                    return handleAdmin(sender, args, lang);
                case "hatch":
                    if (args.length > 1 && (args[1].equalsIgnoreCase("list") || args[1].equalsIgnoreCase("recover"))) {
                        return handleDirectHatchAdmin(sender, args, lang);
                    }
                    break;
            }
        }

        // 3. Các lệnh người chơi yêu cầu Player
        if (!(sender instanceof Player p)) {
            sender.sendMessage(lang.getMessage("general.player_only"));
            return true;
        }

        if (args.length == 0) {
            plugin.getDialogService().openPetHub(p);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "guide":
                int chapterIdx = -1;
                if (args.length > 1) {
                    try {
                        chapterIdx = Integer.parseInt(args[1]) - 1;
                        if (chapterIdx < 0 || chapterIdx >= 18) chapterIdx = -1;
                    } catch (NumberFormatException ignored) {}
                }
                plugin.getDialogService().openPetGuide(p, chapterIdx);
                return true;

            case "settings":
                plugin.getDialogService().openSettingsDialog(p);
                return true;

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
                    p.sendMessage(lang.getMessage("help.cmd_ui_status"));
                    p.sendMessage(lang.getMessage("help.cmd_give"));
                    p.sendMessage(lang.getMessage("help.cmd_giveball"));
                    p.sendMessage(lang.getMessage("help.cmd_giveegg"));
                    p.sendMessage(lang.getMessage("help.cmd_reload"));
                }
                p.sendMessage(lang.getMessage("help.footer"));
                break;

            case "ui":
                if (args.length > 1 && args[1].equalsIgnoreCase("status")) {
                    return handleUiStatus(p, lang);
                }
                p.sendMessage("§cUsage: /pet ui status");
                break;

            case "model":
                if (args.length > 1 && args[1].equalsIgnoreCase("status")) {
                    return handleModelStatus(p, args, lang);
                }
                p.sendMessage("§cUsage: /pet model status [pet_id]");
                break;

            case "shop":
                if (!p.hasPermission("ipsecuzpet.shop")) {
                    p.sendMessage(lang.getMessage("general.no_permission"));
                    return true;
                }
                plugin.getDialogService().openShopBrowse(p, 1);
                break;

            case "codex":
                if (!p.hasPermission("ipsecuzpet.codex")) {
                    p.sendMessage(lang.getMessage("general.no_permission"));
                    return true;
                }
                plugin.getDialogService().openCodexBrowse(p, 1);
                break;

            case "shards":
                if (!p.hasPermission("ipsecuzpet.shards")) {
                    p.sendMessage(lang.getMessage("general.no_permission"));
                    return true;
                }
                plugin.getDialogService().openShardsOverview(p);
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
                    plugin.getDialogService().openRenameDialog(p, plugin.getPetManager().getActivePetId(p.getUniqueId()));
                    return true;
                }
                String petId = plugin.getPetManager().getActivePetId(p.getUniqueId());
                String newName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

                String cleanRaw = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', newName));
                if (cleanRaw.length() < 2 || cleanRaw.length() > 16) {
                    p.sendMessage(lang.getMessage("pet.rename_length_error"));
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return true;
                }

                plugin.getConfigManager().setCustomName(p.getUniqueId(), petId, newName);

                Entity petEntity = plugin.getPetManager().getPet(p.getUniqueId());
                if (petEntity != null) {
                    plugin.getPetManager().updatePetNameplate(petEntity, p.getUniqueId(), petId);
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
            case "givepet":
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
                p.sendMessage(lang.getMessage(!isBaby ? "pet.size_changed_baby" : "pet.size_changed_adult"));
                plugin.getPetManager().spawnPet(p, curActivePet);
                break;

            case "admin":
                return handleAdmin(p, args, lang);

            case "hatch":
            case "incubator":
                if (args.length > 1 && (args[1].equalsIgnoreCase("list") || args[1].equalsIgnoreCase("recover"))) {
                    return handleDirectHatchAdmin(p, args, lang);
                }
                if (!p.hasPermission("ipsecuzpet.hatch")) {
                    p.sendMessage(lang.getMessage("general.no_permission"));
                    return true;
                }
                plugin.getDialogService().openHatchSelect(p);
                break;

            case "feed":
                if (!p.hasPermission("ipsecuzpet.feed")) {
                    p.sendMessage(lang.getMessage("general.no_permission"));
                    return true;
                }
                ItemStack handItem = p.getInventory().getItemInMainHand();
                plugin.getFeedingManager().feedPet(p, handItem);
                break;

            case "skill":
            case "ultimate":
                if (!p.hasPermission("ipsecuzpet.skill")) {
                    p.sendMessage(lang.getMessage("general.no_permission"));
                    return true;
                }
                plugin.getSkillManager().triggerUltimate(p);
                break;

            case "star":
            case "evolve":
                if (!p.hasPermission("ipsecuzpet.evolution")) {
                    p.sendMessage(lang.getMessage("general.no_permission"));
                    return true;
                }
                if (!plugin.getPetManager().hasPet(p.getUniqueId())) {
                    p.sendMessage(lang.getMessage("pet.no_pet"));
                    return true;
                }
                String starPetId = plugin.getPetManager().getActivePetId(p.getUniqueId());
                plugin.getDialogService().openEvolutionDialog(p, starPetId);
                break;

            case "trade":
                if (!p.hasPermission("ipsecuzpet.trade")) {
                    p.sendMessage(lang.getMessage("general.no_permission"));
                    return true;
                }
                if (args.length < 2) {
                    p.sendMessage(lang.getMessage("pet.trade_usage"));
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

    private boolean handleModelStatus(CommandSender sender, String[] args, LanguageManager lang) {
        if (!sender.hasPermission("ipsecuzpet.admin") && !sender.hasPermission("ipsecuzpet.model.status")) {
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
        String summary = (mm != null) ? mm.getAvailableRenderersSummary() : "None";
        int activePetCount = (plugin.getPetManager() != null && plugin.getPetManager().getActivePets() != null) ? plugin.getPetManager().getActivePets().size() : 0;

        sender.sendMessage(lang.getMessage("model.status_header"));
        sender.sendMessage(lang.getMessage("model.status_title"));
        sender.sendMessage(lang.getMessage("model.bettermodel_label", "%status%", bmStatus));
        sender.sendMessage(lang.getMessage("model.modelengine_label", "%status%", meStatus));
        sender.sendMessage(lang.getMessage("model.global_provider", "%provider%", globalProvider));
        sender.sendMessage(lang.getMessage("model.active_provider", "%provider%", activeProvider));
        sender.sendMessage(lang.getMessage("model.available_renderers", "%summary%", summary));
        sender.sendMessage(lang.getMessage("model.active_pets_count", "%count%", String.valueOf(activePetCount)));

        if (args != null && args.length > 2 && mm != null) {
            String petId = args[2];
            org.ipsecuz.pet.model.ResolvedModel adultModel = mm.resolveModel(petId, false);
            org.ipsecuz.pet.model.ResolvedModel babyModel = mm.resolveModel(petId, true);
            sender.sendMessage("§b✦ Phân tích Pet: §e" + petId);
            sender.sendMessage("  §7- Cấu hình chỉ định: §f" + plugin.getConfig().getString("pets." + petId + ".model.provider", "Mặc định (Global)"));
            sender.sendMessage("  §7- Dạng Trưởng thành: §a" + adultModel.getProvider() + " §7(ID: §f" + adultModel.getModelId() + "§7, Fallback: " + adultModel.isFallback() + ")");
            sender.sendMessage("  §7- Dạng Bé con: §a" + babyModel.getProvider() + " §7(ID: §f" + babyModel.getModelId() + "§7, Fallback: " + babyModel.isFallback() + ")");
        } else {
            sender.sendMessage("§7Mẹo: Sử dụng §e/pet model status <pet_id> §7để kiểm tra chi tiết cấu hình của Pet.");
        }

        sender.sendMessage(lang.getMessage("model.status_footer"));
        return true;
    }

    private boolean handleReload(CommandSender sender, LanguageManager lang) {
        if (!sender.hasPermission("ipsecuzpet.admin")) {
            sender.sendMessage(lang.getMessage("general.no_permission"));
            return true;
        }
        plugin.getConfigManager().flushPendingData();
        plugin.getConfigManager().reloadPluginConfig();
        plugin.getLanguage().loadMessages();
        // Không reload data.yml để bảo vệ dữ liệu người chơi đang hoạt động
        plugin.getCaptureManager().loadBalls();
        if (plugin.getModelHandler() != null && plugin.getModelHandler().getManager() != null) {
            plugin.getModelHandler().getManager().reload();
        }
        if (plugin.getModuleManager() != null) {
            plugin.getModuleManager().reloadAllModules();
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

    private boolean handleUiStatus(CommandSender sender, LanguageManager lang) {
        if (!sender.hasPermission("ipsecuzpet.admin")) {
            sender.sendMessage(lang.getMessage("general.no_permission"));
            return true;
        }
        Player p = (sender instanceof Player player) ? player : null;
        Map<String, String> diag = plugin.getDialogService().getDiagnosticStatus(p);

        sender.sendMessage("§8§m--------------------------------------------------");
        sender.sendMessage("§b✦ §lIPSECUZPET UI & DIALOG DIAGNOSTIC STATUS §b✦");
        sender.sendMessage("§7Configured Mode: §e" + diag.get("configured_mode") + " §7| Effective: §e" + diag.get("effective_mode"));
        sender.sendMessage("§7Server Platform: §f" + diag.get("server_version"));
        sender.sendMessage("§7Bukkit Version: §f" + diag.get("bukkit_version"));
        sender.sendMessage("§7Server API Support: " + (Boolean.parseBoolean(diag.get("server_api_supported")) ? "§aYES (Paper 1.21.7+ Dialog API)" : "§cNO (Legacy API)"));
        sender.sendMessage("§7Client Protocol: §f" + diag.get("client_protocol") + " §7(Compatible: " + (Boolean.parseBoolean(diag.get("client_protocol_supported")) ? "§aYES" : "§cNO") + "§7)");
        sender.sendMessage("§7Native Reflection Bound: " + (Boolean.parseBoolean(diag.get("native_api_available")) ? "§aYES" : "§cNO"));
        sender.sendMessage("§7Reflection Classes: " + (Boolean.parseBoolean(diag.get("native_class_available")) ? "§aPresent" : "§cMissing") + " §7| Methods: " + (Boolean.parseBoolean(diag.get("native_methods_available")) ? "§aPresent" : "§cMissing"));
        sender.sendMessage("§7Audited Capabilities: §e" + diag.get("capabilities_summary"));
        sender.sendMessage("§7DialogWindow Bridge: " + (Boolean.parseBoolean(diag.get("dialogwindow_bridge_available")) ? "§aDetected" : "§7Not Installed"));
        sender.sendMessage("§7Selected UI Backend: §b" + diag.get("selected_backend"));
        sender.sendMessage("§7Selection Rationale: §f" + diag.get("selection_rationale"));
        sender.sendMessage("§7Engine Status: §6" + diag.get("init_status"));
        if (!"None".equals(diag.get("last_failure"))) {
            sender.sendMessage("§cLast Failure: §c" + diag.get("last_failure"));
        }
        sender.sendMessage("§8§m--------------------------------------------------");
        return true;
    }

    private boolean handleAdmin(CommandSender sender, String[] args, LanguageManager lang) {
        if (!sender.hasPermission("ipsecuzpet.admin")) {
            sender.sendMessage(lang.getMessage("general.no_permission"));
            return true;
        }
        if (args.length > 1 && args[1].equalsIgnoreCase("hatch")) {
            if (args.length > 2 && args[2].equalsIgnoreCase("list")) {
                return handleAdminHatchList(sender, lang);
            }
            if (args.length > 4 && args[2].equalsIgnoreCase("recover")) {
                return handleAdminHatchRecover(sender, args[3], args[4], lang);
            }
            sender.sendMessage(lang.getMessageOrDefault("admin.hatch_usage", "§cUsage: /pet admin hatch <list|recover <tx_id> <grant|refund|discard>>"));
            return true;
        }
        sender.sendMessage(lang.getMessageOrDefault("admin.hatch_usage", "§cUsage: /pet admin hatch <list|recover <tx_id> <grant|refund|discard>>"));
        return true;
    }

    private boolean handleDirectHatchAdmin(CommandSender sender, String[] args, LanguageManager lang) {
        if (!sender.hasPermission("ipsecuzpet.admin")) {
            sender.sendMessage(lang.getMessage("general.no_permission"));
            return true;
        }
        if (args.length > 1 && args[1].equalsIgnoreCase("list")) {
            return handleAdminHatchList(sender, lang);
        }
        if (args.length > 3 && args[1].equalsIgnoreCase("recover")) {
            return handleAdminHatchRecover(sender, args[2], args[3], lang);
        }
        sender.sendMessage(lang.getMessageOrDefault("admin.hatch_usage", "§cUsage: /pet admin hatch <list|recover <tx_id> <grant|refund|discard>>"));
        return true;
    }

    private boolean handleAdminHatchList(CommandSender sender, LanguageManager lang) {
        List<HatchingManager.PendingHatchSession> sessions = plugin.getHatchingManager().getAllPendingSessions();
        if (sessions.isEmpty()) {
            sender.sendMessage(lang.getMessageOrDefault("admin.hatch_list_empty", "§7Không có giao dịch ấp trứng nào đang chờ xử lý."));
            return true;
        }
        sender.sendMessage(lang.getMessageOrDefault("admin.hatch_list_header", "§6§l[IPSECUZ PET] §eDanh sách giao dịch ấp trứng đang chờ (%count%):", "%count%", String.valueOf(sessions.size())));
        for (HatchingManager.PendingHatchSession s : sessions) {
            String pName = "Unknown";
            Player target = Bukkit.getPlayer(s.getPlayerUuid());
            if (target != null) {
                pName = target.getName();
            } else {
                var op = Bukkit.getOfflinePlayer(s.getPlayerUuid());
                if (op.getName() != null) pName = op.getName();
            }
            String pUuidStr = s.getPlayerUuid().toString();
            String shortUuid = pUuidStr.length() >= 8 ? pUuidStr.substring(0, 8) + "..." : pUuidStr;
            String entry = lang.getMessageOrDefault("admin.hatch_list_entry",
                    "§7- TX: §f%tx_id% §7| Player: §e%player% §7| Egg: §b%egg_id% §7| Winner: §a%win_pet% §7| State: §6%state% §7| Paid: §f%paid% §7| Recov: §e%recoverable%",
                    "%tx_id%", s.getTransactionId(),
                    "%player%", pName + " (" + shortUuid + ")",
                    "%egg_id%", s.getEggId(),
                    "%win_pet%", s.getWinningPetId(),
                    "%state%", s.getState().name(),
                    "%paid%", String.valueOf(s.isPaymentCommitted()),
                    "%recoverable%", String.valueOf(s.isRecoverable()),
                    "%timestamp%", String.valueOf(s.getTimestamp())
            );
            sender.sendMessage(entry);
        }
        return true;
    }

    private boolean handleAdminHatchRecover(CommandSender sender, String txId, String action, LanguageManager lang) {
        if (txId == null || action == null || (!action.equalsIgnoreCase("grant") && !action.equalsIgnoreCase("refund") && !action.equalsIgnoreCase("discard"))) {
            sender.sendMessage(lang.getMessageOrDefault("admin.hatch_usage", "§cUsage: /pet admin hatch <list|recover <tx_id> <grant|refund|discard>>"));
            return true;
        }
        boolean ok = plugin.getHatchingManager().resolvePendingTransaction(txId, action, sender.getName());
        if (ok) {
            sender.sendMessage(lang.getMessageOrDefault("admin.hatch_recover_success",
                    "§aĐã xử lý giao dịch %tx_id% với hành động %action% thành công!",
                    "%tx_id%", txId, "%action%", action.toLowerCase()));
        } else {
            sender.sendMessage(lang.getMessageOrDefault("admin.hatch_not_found",
                    "§cKhông tìm thấy giao dịch hoặc trạng thái không hợp lệ: %tx_id%",
                    "%tx_id%", txId));
        }
        return true;
    }
}