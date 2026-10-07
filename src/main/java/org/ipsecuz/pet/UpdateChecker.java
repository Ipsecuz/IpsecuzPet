package org.ipsecuz.pet;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Scanner;
import java.util.function.Consumer;

public class UpdateChecker {

    private final JavaPlugin plugin;
    private final int resourceId;

    public UpdateChecker(JavaPlugin plugin, int resourceId) {
        this.plugin = plugin;
        this.resourceId = resourceId;
    }

    public void getVersion(final Consumer<String> consumer) {
        // Sử dụng SchedulerUtils.runAsync để tương thích với Folia
        SchedulerUtils.runAsync(plugin, () -> {
            try (InputStream inputStream = new URL("https://api.spigotmc.org/legacy/update.php?resource=" + this.resourceId).openStream();
                 Scanner scanner = new Scanner(inputStream)) {
                if (scanner.hasNext()) {
                    consumer.accept(scanner.next());
                }
            } catch (IOException exception) {
                plugin.getLogger().warning("Không thể kiểm tra cập nhật: " + exception.getMessage());
            }
        });
    }

    public static boolean isNewerVersion(String remote, String current) {
        if (remote == null || current == null) return false;
        String cleanRemote = remote.trim().replaceAll("^[vV]", "");
        String cleanCurrent = current.trim().replaceAll("^[vV]", "");
        String[] rParts = cleanRemote.split("[-_]");
        String[] cParts = cleanCurrent.split("[-_]");
        String[] rNums = rParts[0].split("\\.");
        String[] cNums = cParts[0].split("\\.");

        int max = Math.max(rNums.length, cNums.length);
        for (int i = 0; i < max; i++) {
            int r = 0;
            int c = 0;
            if (i < rNums.length) {
                try { r = Integer.parseInt(rNums[i].replaceAll("[^0-9]", "")); } catch (Exception ignored) {}
            }
            if (i < cNums.length) {
                try { c = Integer.parseInt(cNums[i].replaceAll("[^0-9]", "")); } catch (Exception ignored) {}
            }
            if (r > c) return true;
            if (r < c) return false;
        }
        return false;
    }
}