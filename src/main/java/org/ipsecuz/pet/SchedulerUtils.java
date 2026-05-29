package org.ipsecuz.pet;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public class SchedulerUtils {
    private static boolean isFolia = false;

    static {
        try {
            // Check kỹ class của Folia
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
        } catch (ClassNotFoundException e) {
            isFolia = false;
        }
    }

    public static boolean isFolia() { return isFolia; }

    // 1. Chạy Task lặp lại (Global) - Dùng cho PetManager
    public static void runGlobalTimer(Plugin plugin, Runnable task, long delay, long period) {
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, (st) -> task.run(), delay, period);
        } else {
            new BukkitRunnable() {
                @Override public void run() { task.run(); }
            }.runTaskTimer(plugin, delay, period);
        }
    }

    // 2. Chạy Async (Bất đồng bộ) - Dùng cho UpdateChecker
    public static void runAsync(Plugin plugin, Runnable task) {
        if (isFolia) {
            Bukkit.getAsyncScheduler().runNow(plugin, (st) -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    // 3. Chạy Task trên Entity
    public static void runEntityTask(Plugin plugin, Entity entity, Runnable task) {
        if (entity == null || !entity.isValid()) return;
        if (isFolia) {
            entity.getScheduler().run(plugin, (st) -> task.run(), null);
        } else {
            task.run();
        }
    }

    // 4. Chạy Task trên Entity với độ trễ
    public static void runEntityTaskLater(Plugin plugin, Entity entity, Runnable task, long delay) {
        if (entity == null || !entity.isValid()) return;
        if (isFolia) {
            entity.getScheduler().runDelayed(plugin, (st) -> task.run(), null, delay);
        } else {
            new BukkitRunnable() {
                @Override public void run() { task.run(); }
            }.runTaskLater(plugin, delay);
        }
    }

    // 5. Teleport an toàn
    public static void teleportAsync(Entity entity, Location location) {
        if (entity == null || !entity.isValid()) return;
        entity.teleportAsync(location);
    }
}