package org.ipsecuz.pet;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LanguageManager {
    private final IpsecuzPet plugin;
    private File file;
    private FileConfiguration config;

    public LanguageManager(IpsecuzPet plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    public void loadMessages() {
        file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public String getMessage(String path) {
        String msg = config.getString(path);
        if (msg == null) return "§cMissing message: " + path;

        // Tự động thêm prefix nếu không phải là gui hay help
        if (!path.startsWith("gui") && !path.startsWith("help")&& !path.equals("pet.display_format")) {
            String prefix = config.getString("prefix", "");
            return colorize(prefix + msg);
        }
        return colorize(msg);
    }

    // Hỗ trợ replace biến (Ví dụ: %player% -> Tên)
    public String getMessage(String path, String... placeholders) {
        String msg = config.getString(path);
        if (msg == null) return "§cMissing message: " + path;

        // Thêm prefix trước khi replace
        if (!path.startsWith("gui") && !path.startsWith("help")) {
            String prefix = config.getString("prefix", "");
            msg = prefix + msg;
        }

        // Thay thế biến %...%
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                msg = msg.replace(placeholders[i], placeholders[i + 1]);
            }
        }

        return colorize(msg);
    }

    /**
     * Hàm xử lý màu sắc chuẩn cho Paper/Folia
     * Hỗ trợ:
     * 1. Hex Color: &#RRGGBB (Ví dụ: &#FF0000)
     * 2. Legacy Color: &a, &e, &l...
     */
    private String colorize(String message) {
        if (message == null) return "";

        // 1. Xử lý Hex Color dạng &#RRGGBB
        // Regex tìm chuỗi có dạng &# theo sau là 6 ký tự hex
        Pattern pattern = Pattern.compile("&#[a-fA-F0-9]{6}");
        Matcher matcher = pattern.matcher(message);

        while (matcher.find()) {
            String hexCode = message.substring(matcher.start(), matcher.end());
            // hexCode ví dụ: &#FF0000
            // replace thành §x§F§F§0§0§0§0 (Định dạng màu của Minecraft)
            String replaceSharp = hexCode.replace("&#", "x");
            char[] chars = replaceSharp.toCharArray();
            StringBuilder builder = new StringBuilder();
            for (char c : chars) {
                builder.append("§").append(c);
            }
            message = message.replace(hexCode, builder.toString());
            matcher = pattern.matcher(message);
        }

        // 2. Xử lý màu thường (&e -> §e)
        // Dùng LegacyComponentSerializer của Paper để chuyển đổi chuẩn nhất
        return LegacyComponentSerializer.legacyAmpersand().deserialize(message)
                .content(LegacyComponentSerializer.legacySection().serialize(
                        LegacyComponentSerializer.legacyAmpersand().deserialize(message)
                )).content();

        // Cách đơn giản hơn nếu cách trên phức tạp:
        // return net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', message);
    }
}