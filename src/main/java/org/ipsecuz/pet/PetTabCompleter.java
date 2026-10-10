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

        if (args.length == 1) {
            List<String> commands = new ArrayList<>();
            commands.add("help");
            commands.add("despawn");
            commands.add("baby");
            commands.add("duel");
            commands.add("accept");
            commands.add("stats");

            if (sender.hasPermission("ipsecuzpet.shop")) commands.add("shop");
            if (sender.hasPermission("ipsecuzpet.hatch")) commands.add("hatch");
            if (sender.hasPermission("ipsecuzpet.codex")) commands.add("codex");
            if (sender.hasPermission("ipsecuzpet.shards")) commands.add("shards");
            if (sender.hasPermission("ipsecuzpet.feed")) commands.add("feed");
            if (sender.hasPermission("ipsecuzpet.skill")) commands.add("skill");
            if (sender.hasPermission("ipsecuzpet.evolution")) commands.add("star");
            if (sender.hasPermission("ipsecuzpet.trade")) commands.add("trade");
            if (sender.hasPermission("ipsecuzpet.withdraw")) commands.add("withdraw");
            if (sender.hasPermission("ipsecuzpet.rename")) commands.add("rename");

            if (sender.hasPermission("ipsecuzpet.admin")) {
                commands.add("give");
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

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("model") && (sender.hasPermission("ipsecuzpet.admin") || sender.hasPermission("ipsecuzpet.model.status"))) {
                if ("status".startsWith(args[1].toLowerCase())) {
                    results.add("status");
                }
                return results;
            }
            if ((sub.equals("withdraw") || sub.equals("baby") || sub.equals("stats") || sub.equals("star") || sub.equals("evolve")) && sender instanceof Player p) {
                if (plugin.getConfigManager().getData().getConfigurationSection(p.getUniqueId() + ".pets") != null) {
                    for (String key : plugin.getConfigManager().getData().getConfigurationSection(p.getUniqueId() + ".pets").getKeys(false)) {
                        if (key.toLowerCase().startsWith(args[1].toLowerCase())) {
                            results.add(key);
                        }
                    }
                }
                return results;
            }
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
            if (sub.equals("duel") ||
                    (sub.equals("give") && sender.hasPermission("ipsecuzpet.admin")) ||
                    (sub.equals("giveball") && sender.hasPermission("ipsecuzpet.admin")) ||
                    (sub.equals("giveegg") && sender.hasPermission("ipsecuzpet.admin"))) {
                return null; // Return null to delegate player name completion to Bukkit
            }
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("give") && sender.hasPermission("ipsecuzpet.admin")) {
                if (plugin.getConfig().getConfigurationSection("pets") != null) {
                    for (String key : plugin.getConfig().getConfigurationSection("pets").getKeys(false)) {
                        if (key.toLowerCase().startsWith(args[2].toLowerCase())) {
                            results.add(key);
                        }
                    }
                }
                return results;
            }
            if (sub.equals("giveball") && sender.hasPermission("ipsecuzpet.admin")) {
                for (String ball : plugin.getCaptureManager().getBallIds()) {
                    if (ball.toLowerCase().startsWith(args[2].toLowerCase())) {
                        results.add(ball);
                    }
                }
                return results;
            }
            if (sub.equals("giveegg") && sender.hasPermission("ipsecuzpet.admin")) {
                if (plugin.getModuleManager() != null && plugin.getModuleManager().getHatchingConfig().isConfigurationSection("eggs")) {
                    for (String egg : plugin.getModuleManager().getHatchingConfig().getConfigurationSection("eggs").getKeys(false)) {
                        if (egg.toLowerCase().startsWith(args[2].toLowerCase())) {
                            results.add(egg);
                        }
                    }
                }
                return results;
            }
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