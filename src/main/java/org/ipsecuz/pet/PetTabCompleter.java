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
            commands.add("shop");
            commands.add("help");
            commands.add("despawn");
            commands.add("duel");
            commands.add("accept");
            commands.add("stats");
            commands.add("withdraw");
            commands.add("rename"); // <--- THÊM DÒNG NÀY

            if (sender.hasPermission("ipsecuzpet.admin")) {
                commands.add("give");
                commands.add("giveball");
                commands.add("reload");
            }

            for (String cmd : commands) {
                if (cmd.toLowerCase().startsWith(args[0].toLowerCase())) {
                    results.add(cmd);
                }
            }
            return results;
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("withdraw") && sender instanceof Player p) {
                if (plugin.getConfigManager().getData().getConfigurationSection(p.getUniqueId() + ".pets") != null) {
                    results.addAll(plugin.getConfigManager().getData().getConfigurationSection(p.getUniqueId() + ".pets").getKeys(false));
                }
                return results;
            }
            // Không gợi ý cho duel/give để game tự gợi ý tên player
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
            // Gợi ý ID bóng
            if (args[0].equalsIgnoreCase("giveball") && sender.hasPermission("ipsecuzpet.admin")) {
                return new ArrayList<>(plugin.getCaptureManager().getBallIds());
            }
        }

        return results;
    }
}