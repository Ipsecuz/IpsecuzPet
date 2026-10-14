package org.ipsecuz.pet;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LanguageManager {

    private final IpsecuzPet plugin;
    private FileConfiguration primaryConfig;
    private FileConfiguration fallbackConfig;
    private String selectedLanguage = "VN";

    public LanguageManager(IpsecuzPet plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    public LanguageManager(FileConfiguration primaryConfig, FileConfiguration fallbackConfig, String selectedLanguage) {
        this.plugin = null;
        this.primaryConfig = primaryConfig;
        this.fallbackConfig = fallbackConfig != null ? fallbackConfig : primaryConfig;
        this.selectedLanguage = selectedLanguage != null ? selectedLanguage : "VN";
    }

    public void loadMessages() {
        // 1. Lưu các file ngôn ngữ mặc định nếu chưa tồn tại
        saveDefaultLanguageFile("VN.yml");
        saveDefaultLanguageFile("EN.yml");

        // 2. Xác định ngôn ngữ từ config.yml
        String configuredLang = plugin.getConfig().getString("language", "VN");
        if (configuredLang == null) configuredLang = "VN";
        configuredLang = configuredLang.trim().toUpperCase();

        if (!"VN".equals(configuredLang) && !"EN".equals(configuredLang)) {
            plugin.getLogger().warning("§c[LanguageManager] Ngôn ngữ '" + configuredLang + "' không hợp lệ! Đang tự động chuyển về 'VN'.");
            configuredLang = "VN";
        }
        this.selectedLanguage = configuredLang;

        // 3. Tải cấu hình ngôn ngữ chính
        File langFile = new File(plugin.getDataFolder(), "languages/" + selectedLanguage + ".yml");
        if (!langFile.exists()) {
            // Tương thích ngược: nếu có messages.yml cũ ở thư mục gốc
            File legacyFile = new File(plugin.getDataFolder(), "messages.yml");
            if (legacyFile.exists() && "VN".equals(selectedLanguage)) {
                langFile = legacyFile;
            }
        }
        primaryConfig = YamlConfiguration.loadConfiguration(langFile);

        // 4. Tải cấu hình dự phòng (VN.yml)
        File vnFallbackFile = new File(plugin.getDataFolder(), "languages/VN.yml");
        if (vnFallbackFile.exists()) {
            fallbackConfig = YamlConfiguration.loadConfiguration(vnFallbackFile);
        } else {
            fallbackConfig = primaryConfig;
        }

        // Tích hợp resource mặc định từ JAR vào fallback
        try (InputStream stream = plugin.getResource("languages/VN.yml")) {
            if (stream != null) {
                YamlConfiguration internal = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
                if (fallbackConfig instanceof YamlConfiguration yamlFallback) {
                    yamlFallback.setDefaults(internal);
                }
            }
        } catch (Throwable ignored) {}
    }

    private void saveDefaultLanguageFile(String fileName) {
        File folder = new File(plugin.getDataFolder(), "languages");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        File target = new File(folder, fileName);
        if (!target.exists()) {
            try {
                plugin.saveResource("languages/" + fileName, false);
            } catch (Throwable t) {
                // Thử tạo file rỗng nếu không tìm thấy resource
                try {
                    target.createNewFile();
                } catch (Throwable ignored) {}
            }
        }
    }

    public String getSelectedLanguage() {
        return selectedLanguage;
    }

    public String getMessage(String path) {
        return getMessage(path, new String[0]);
    }

    public boolean contains(String path) {
        if (path == null) return false;
        return (primaryConfig != null && primaryConfig.contains(path)) ||
               (fallbackConfig != null && fallbackConfig.contains(path));
    }

    public boolean isDisplayOnlyKey(String path) {
        if (path == null) return false;
        return path.startsWith("trait.") ||
               path.startsWith("rarity.") ||
               path.startsWith("card.") ||
               path.startsWith("gui.") ||
               path.startsWith("dialog.") ||
               path.startsWith("shop.") ||
               path.startsWith("materials.") ||
               path.startsWith("requirement.") ||
               path.startsWith("help.") ||
               path.startsWith("model.") ||
               path.startsWith("stats.") ||
               path.startsWith("title.") ||
               path.startsWith("actionbar.") ||
               path.startsWith("pets.") ||
               path.startsWith("eggs.") ||
               path.startsWith("skills.") ||
               path.startsWith("happiness.") ||
               path.startsWith("hatching.title_") ||
               path.startsWith("hatching.subtitle_") ||
               path.startsWith("trade.initiator_offer") ||
               path.startsWith("trade.partner_offer") ||
               path.startsWith("trade.divider") ||
               path.startsWith("trade.status_") ||
               path.startsWith("trade.toggle_lock") ||
               path.startsWith("trade.countdown") ||
               path.startsWith("trade.ready") ||
               path.startsWith("trade.waiting_lock") ||
               path.startsWith("trade.cancel_") ||
               path.startsWith("trade.confirm_") ||
               path.startsWith("trade.accept") ||
               path.startsWith("trade.decline") ||
               path.equals("trade.title") ||
               path.equals("pet.display_format") ||
               path.equals("prefix");
    }

    public String getPetName(String petId) {
        if (petId == null) return "Pet";
        String key = "pets." + petId + ".name";
        String val = getRawMessage(key);
        if (val != null && !val.startsWith("§cMissing")) {
            return val;
        }
        if (plugin != null && plugin.getConfig() != null) {
            return plugin.getConfig().getString("pets." + petId + ".name", petId);
        }
        return petId;
    }

    public String getPetDisplayName(java.util.UUID ownerId, String petId) {
        if (petId == null) return "Pet";
        if (ownerId != null && plugin != null && plugin.getConfigManager() != null) {
            String customName = plugin.getConfigManager().getCustomName(ownerId, petId);
            if (customName != null && !customName.trim().isEmpty()) {
                return customName;
            }
        }
        return getPetName(petId);
    }

    public String applyPlaceholders(String text, String... placeholders) {
        if (text == null || placeholders == null || placeholders.length == 0) {
            return text;
        }
        String result = text;
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length && placeholders[i] != null && placeholders[i + 1] != null) {
                String key = placeholders[i];
                String val = placeholders[i + 1];
                result = result.replace(key, val);
                if ("%pet%".equals(key)) {
                    result = result.replace("%pet_name%", val);
                } else if ("%pet_name%".equals(key)) {
                    result = result.replace("%pet%", val);
                }
            }
        }
        return result;
    }

    private void validatePlaceholders(String path, String rendered) {
        if (plugin != null && plugin.getConfig() != null && plugin.getConfig().getBoolean("debug", false) && rendered != null) {
            Pattern unresolvedPattern = Pattern.compile("%([a-zA-Z0-9_]+)%");
            Matcher m = unresolvedPattern.matcher(rendered);
            while (m.find()) {
                if (plugin.getLogger() != null) {
                    plugin.getLogger().warning("[LanguageManager] Potential unresolved placeholder '%" + m.group(1) + "%' in key '" + path + "': " + rendered);
                }
            }
        }
    }

    public String getRawMessageOrDefault(String path, String defaultValue, String... placeholders) {
        String msg = null;
        if (primaryConfig != null) {
            msg = primaryConfig.getString(path);
        }
        if (msg == null && fallbackConfig != null) {
            msg = fallbackConfig.getString(path);
        }
        if (msg == null) {
            msg = defaultValue;
        }
        if (msg == null) {
            return "§c" + path;
        }

        msg = applyPlaceholders(msg, placeholders);
        validatePlaceholders(path, msg);
        return colorize(msg);
    }

    public String getMessageOrDefault(String path, String defaultValue, String... placeholders) {
        String msg = null;
        if (primaryConfig != null) {
            msg = primaryConfig.getString(path);
        }
        if (msg == null && fallbackConfig != null) {
            msg = fallbackConfig.getString(path);
        }
        if (msg == null) {
            msg = defaultValue;
        }
        if (msg == null) {
            return "§c" + path;
        }

        if (!isDisplayOnlyKey(path)) {
            String prefix = primaryConfig != null ? primaryConfig.getString("prefix", "") : "";
            if (prefix.isEmpty() && fallbackConfig != null) {
                prefix = fallbackConfig.getString("prefix", "");
            }
            msg = prefix + msg;
        }

        msg = applyPlaceholders(msg, placeholders);
        validatePlaceholders(path, msg);
        return colorize(msg);
    }

    public String getRawMessage(String path, String... placeholders) {
        String msg = null;
        if (primaryConfig != null) {
            msg = primaryConfig.getString(path);
        }
        if (msg == null && fallbackConfig != null) {
            msg = fallbackConfig.getString(path);
        }
        if (msg == null) {
            return "§cMissing language key: " + path;
        }

        msg = applyPlaceholders(msg, placeholders);
        validatePlaceholders(path, msg);
        return colorize(msg);
    }

    public String getMessage(String path, String... placeholders) {
        String msg = null;
        if (primaryConfig != null) {
            msg = primaryConfig.getString(path);
        }
        if (msg == null && fallbackConfig != null) {
            msg = fallbackConfig.getString(path);
        }
        if (msg == null) {
            return "§cMissing language key: " + path;
        }

        // Thêm prefix nếu không phải là display only key
        if (!isDisplayOnlyKey(path)) {
            String prefix = primaryConfig != null ? primaryConfig.getString("prefix", "") : "";
            if (prefix.isEmpty() && fallbackConfig != null) {
                prefix = fallbackConfig.getString("prefix", "");
            }
            msg = prefix + msg;
        }

        msg = applyPlaceholders(msg, placeholders);
        validatePlaceholders(path, msg);
        return colorize(msg);
    }

    public List<String> getMessageList(String path, String... placeholders) {
        List<String> list = null;
        if (primaryConfig != null) {
            list = primaryConfig.getStringList(path);
        }
        if ((list == null || list.isEmpty()) && fallbackConfig != null) {
            list = fallbackConfig.getStringList(path);
        }
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>(list.size());
        for (String line : list) {
            if (line == null) continue;
            String processed = applyPlaceholders(line, placeholders);
            result.add(colorize(processed));
        }
        return result;
    }

    public String getRaw(String path) {
        if (primaryConfig != null && primaryConfig.contains(path)) {
            return primaryConfig.getString(path);
        }
        if (fallbackConfig != null && fallbackConfig.contains(path)) {
            return fallbackConfig.getString(path);
        }
        return "";
    }

    public static String colorize(String message) {
        if (message == null) return "";

        // 1. Xử lý Hex Color dạng &#RRGGBB
        Pattern pattern = Pattern.compile("&#([a-fA-F0-9]{6})");
        Matcher matcher = pattern.matcher(message);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            matcher.appendReplacement(sb, ChatColor.of("#" + hex).toString());
        }
        matcher.appendTail(sb);
        String hexProcessed = sb.toString();

        // 2. Xử lý legacy color codes (&a -> §a)
        return ChatColor.translateAlternateColorCodes('&', hexProcessed);
    }
}