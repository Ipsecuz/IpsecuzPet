package org.ipsecuz.pet;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PetTabCompleter implements TabCompleter {
    private final IpsecuzPet plugin;

    public PetTabCompleter(IpsecuzPet plugin) {
        this.plugin = plugin;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> results = new ArrayList<>();

        // 1. Bí danh trực tiếp: /givepet <player> <pet_id>
        if (label.equalsIgnoreCase("givepet")) {
            if (!sender.hasPermission("ipsecuzpet.admin")) return results;
            if (args.length == 1) {
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                        results.add(p.getName());
                    }
                }
                return results;
            }
            if (args.length == 2) {
                if (plugin.getConfig().getConfigurationSection("pets") != null) {
                    for (String key : plugin.getConfig().getConfigurationSection("pets").getKeys(false)) {
                        if (key.toLowerCase().startsWith(args[1].toLowerCase())) {
                            results.add(key);
                        }
                    }
                }
                return results;
            }
            return results;
        }

        // 2. Bí danh trực tiếp: /pethatch (không có đối số con)
        if (label.equalsIgnoreCase("pethatch")) {
            return results;
        }

        // 3. /pet hoặc /ipsecuzpet - Đối số 1: Tên lệnh con
        if (args.length == 1) {
            List<String> commands = new ArrayList<>();
            commands.add("help");
            commands.add("guide");
            commands.add("settings");
            commands.add("despawn");
            commands.add("stats");
            commands.add("info");
            commands.add("baby");
            commands.add("form");
            commands.add("duel");
            commands.add("accept");
            commands.add("deny");
            commands.add("cancelduel");

            if (sender.hasPermission("ipsecuzpet.shop")) commands.add("shop");
            if (sender.hasPermission("ipsecuzpet.hatch")) {
                commands.add("hatch");
                commands.add("incubator");
            }
            if (sender.hasPermission("ipsecuzpet.codex")) commands.add("codex");
            if (sender.hasPermission("ipsecuzpet.shards")) commands.add("shards");
            if (sender.hasPermission("ipsecuzpet.feed")) commands.add("feed");
            if (sender.hasPermission("ipsecuzpet.skill")) {
                commands.add("skill");
                commands.add("ultimate");
            }
            if (sender.hasPermission("ipsecuzpet.evolution")) {
                commands.add("star");
                commands.add("evolve");
            }
            if (sender.hasPermission("ipsecuzpet.trade")) commands.add("trade");
            if (sender.hasPermission("ipsecuzpet.withdraw")) commands.add("withdraw");
            if (sender.hasPermission("ipsecuzpet.rename")) commands.add("rename");

            if (sender.hasPermission("ipsecuzpet.admin")) {
                commands.add("give");
                commands.add("givepet");
                commands.add("giveball");
                commands.add("giveegg");
                commands.add("reload");
            }
            if (sender.hasPermission("ipsecuzpet.admin") || sender.hasPermission("ipsecuzpet.model.status")) {
                commands.add("model");
            }

            for (String cmd : commands) {
                if (cmd.toLowerCase().startsWith(args[0].toLowerCase())) {
                    results.add(cmd);
                }
            }
            return results;
        }

        // 4. Đối số 2
        if (args.length == 2) {
            String sub = args[0].toLowerCase();

            // /pet guide [1-18]
            if (sub.equals("guide")) {
                for (int i = 1; i <= 18; i++) {
                    String ch = String.valueOf(i);
                    if (ch.startsWith(args[1])) {
                        results.add(ch);
                    }
                }
                return results;
            }

            // /pet model status
            if (sub.equals("model") && (sender.hasPermission("ipsecuzpet.admin") || sender.hasPermission("ipsecuzpet.model.status"))) {
                if ("status".startsWith(args[1].toLowerCase())) {
                    results.add("status");
                }
                return results;
            }

            // /pet trade accept hoặc /pet trade <player>
            if (sub.equals("trade")) {
                if ("accept".startsWith(args[1].toLowerCase())) {
                    results.add("accept");
                }
                for (Player online : plugin.getServer().getOnlinePlayers()) {
                    if (!online.getName().equalsIgnoreCase(sender.getName()) && online.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        results.add(online.getName());
                    }
                }
                return results;
            }

            // /pet duel <player>
            if (sub.equals("duel")) {
                for (Player online : plugin.getServer().getOnlinePlayers()) {
                    if (!online.getName().equalsIgnoreCase(sender.getName()) && online.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        results.add(online.getName());
                    }
                }
                return results;
            }

            // Admin: /pet give <player>, /pet givepet <player>, /pet giveball <player>, /pet giveegg <player>
            if ((sub.equals("give") || sub.equals("givepet") || sub.equals("giveball") || sub.equals("giveegg")) && sender.hasPermission("ipsecuzpet.admin")) {
                for (Player online : plugin.getServer().getOnlinePlayers()) {
                    if (online.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        results.add(online.getName());
                    }
                }
                return results;
            }

            // /pet withdraw <pet_id>, /pet baby <pet_id>, /pet stats <pet_id>, /pet star <pet_id>
            if (sub.equals("withdraw") || sub.equals("baby") || sub.equals("form") || sub.equals("stats") || sub.equals("info") || sub.equals("star") || sub.equals("evolve")) {
                if (sender instanceof Player p && plugin.getConfigManager().getData().getConfigurationSection(p.getUniqueId() + ".pets") != null) {
                    for (String key : plugin.getConfigManager().getData().getConfigurationSection(p.getUniqueId() + ".pets").getKeys(false)) {
                        if (key.toLowerCase().startsWith(args[1].toLowerCase())) {
                            results.add(key);
                        }
                    }
                } else if (sender.hasPermission("ipsecuzpet.admin") && plugin.getConfig().getConfigurationSection("pets") != null) {
                    for (String key : plugin.getConfig().getConfigurationSection("pets").getKeys(false)) {
                        if (key.toLowerCase().startsWith(args[1].toLowerCase())) {
                            results.add(key);
                        }
                    }
                }
                return results;
            }
        }

        // 5. Đối số 3
        if (args.length == 3) {
            String sub = args[0].toLowerCase();

            // /pet give <player> <pet_id> hoặc /pet givepet <player> <pet_id>
            if ((sub.equals("give") || sub.equals("givepet")) && sender.hasPermission("ipsecuzpet.admin")) {
                if (plugin.getConfig().getConfigurationSection("pets") != null) {
                    for (String key : plugin.getConfig().getConfigurationSection("pets").getKeys(false)) {
                        if (key.toLowerCase().startsWith(args[2].toLowerCase())) {
                            results.add(key);
                        }
                    }
                }
                return results;
            }

            // /pet giveball <player> <ball_id>
            if (sub.equals("giveball") && sender.hasPermission("ipsecuzpet.admin")) {
                if (plugin.getCaptureManager() != null) {
                    for (String ball : plugin.getCaptureManager().getBallIds()) {
                        if (ball.toLowerCase().startsWith(args[2].toLowerCase())) {
                            results.add(ball);
                        }
                    }
                }
                return results;
            }

            // /pet giveegg <player> <egg_id>
            if (sub.equals("giveegg") && sender.hasPermission("ipsecuzpet.admin")) {
                if (plugin.getModuleManager() != null && plugin.getModuleManager().getHatchingConfig() != null &&
                        plugin.getModuleManager().getHatchingConfig().isConfigurationSection("eggs")) {
                    for (String egg : plugin.getModuleManager().getHatchingConfig().getConfigurationSection("eggs").getKeys(false)) {
                        if (egg.toLowerCase().startsWith(args[2].toLowerCase())) {
                            results.add(egg);
                        }
                    }
                }
                return results;
            }

            // /pet model status <pet_id>
            if (sub.equals("model") && args[1].equalsIgnoreCase("status") && (sender.hasPermission("ipsecuzpet.admin") || sender.hasPermission("ipsecuzpet.model.status"))) {
                if (plugin.getConfig().getConfigurationSection("pets") != null) {
                    for (String key : plugin.getConfig().getConfigurationSection("pets").getKeys(false)) {
                        if (key.toLowerCase().startsWith(args[2].toLowerCase())) {
                            results.add(key);
                        }
                    }
                }
                return results;
            }
        }

        // 6. Đối số 4
        if (args.length == 4) {
            String sub = args[0].toLowerCase();
            if ((sub.equals("giveball") || sub.equals("giveegg")) && sender.hasPermission("ipsecuzpet.admin")) {
                List<String> amounts = List.of("1", "5", "10", "16", "32", "64");
                for (String amt : amounts) {
                    if (amt.startsWith(args[3])) {
                        results.add(amt);
                    }
                }
                return results;
            }
        }

        return results;
    }
}