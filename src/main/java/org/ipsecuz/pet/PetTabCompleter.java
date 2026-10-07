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
            if (args[0].equalsIgnoreCase("model") && (sender.hasPermission("ipsecuzpet.admin") || sender.hasPermission("ipsecuzpet.model.status"))) {
                if ("status".startsWith(args[1].toLowerCase())) {
                    results.add("status");
                }
                return results;
            }
            if (args[0].equalsIgnoreCase("withdraw") && sender instanceof Player p) {
                if (plugin.getConfigManager().getData().getConfigurationSection(p.getUniqueId() + ".pets") != null) {
                    results.addAll(plugin.getConfigManager().getData().getConfigurationSection(p.getUniqueId() + ".pets").getKeys(false));
                }
                return results;
            }
            if (args[0].equalsIgnoreCase("trade")) {
                results.add("accept");
                for (Player online : plugin.getServer().getOnlinePlayers()) {
                    if (!online.getName().equalsIgnoreCase(sender.getName())) {
                        results.add(online.getName());
                    }
                }
                return results;
            }
            if (args[0].equalsIgnoreCase("duel") ||
                    (args[0].equalsIgnoreCase("give") && sender.hasPermission("ipsecuzpet.admin")) ||
                    (args[0].equalsIgnoreCase("giveball") && sender.hasPermission("ipsecuzpet.admin"))) {
                return null;
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("give") && sender.hasPermission("ipsecuzpet.admin")) {
                if (plugin.getConfig().getConfigurationSection("pets") != null) {
                    for (String key : plugin.getConfig().getConfigurationSection("pets").getKeys(false)) {
                        if (key.toLowerCase().startsWith(args[2].toLowerCase())) {
                            results.add(key);
                        }
                    }
                }
                return results;
            }
            if (args[0].equalsIgnoreCase("giveball") && sender.hasPermission("ipsecuzpet.admin")) {
                return new ArrayList<>(plugin.getCaptureManager().getBallIds());
            }
            if (args[0].equalsIgnoreCase("giveegg") && sender.hasPermission("ipsecuzpet.admin")) {
                if (plugin.getModuleManager() != null && plugin.getModuleManager().getHatchingConfig().isConfigurationSection("eggs")) {
                    return new ArrayList<>(plugin.getModuleManager().getHatchingConfig().getConfigurationSection("eggs").getKeys(false));
                }
            }
            if (args[0].equalsIgnoreCase("model") && args[1].equalsIgnoreCase("status") && (sender.hasPermission("ipsecuzpet.admin") || sender.hasPermission("ipsecuzpet.model.status"))) {
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

        return results;
    }
}