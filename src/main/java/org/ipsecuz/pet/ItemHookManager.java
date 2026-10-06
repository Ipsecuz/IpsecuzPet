package org.ipsecuz.pet;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

public class ItemHookManager {
    private final IpsecuzPet plugin;
    private boolean itemsAdderAvailable = false;
    private boolean oraxenAvailable = false;
    private boolean nexoAvailable = false;

    public ItemHookManager(IpsecuzPet plugin) {
        this.plugin = plugin;
        checkHooks();
    }

    public void checkHooks() {
        Plugin ia = Bukkit.getPluginManager().getPlugin("ItemsAdder");
        itemsAdderAvailable = (ia != null && ia.isEnabled());

        Plugin oraxen = Bukkit.getPluginManager().getPlugin("Oraxen");
        oraxenAvailable = (oraxen != null && oraxen.isEnabled());

        Plugin nexo = Bukkit.getPluginManager().getPlugin("Nexo");
        nexoAvailable = (nexo != null && nexo.isEnabled());

        if (itemsAdderAvailable) plugin.getLogger().info("§a[ItemHookManager] Đã kết nối thành công với ItemsAdder!");
        if (oraxenAvailable) plugin.getLogger().info("§a[ItemHookManager] Đã kết nối thành công với Oraxen!");
        if (nexoAvailable) plugin.getLogger().info("§a[ItemHookManager] Đã kết nối thành công với Nexo!");
    }

    /**
     * Lấy ItemStack hỗ trợ đa nền tảng:
     * - "itemsadder:ruby_sword" hoặc "ia:ruby_sword"
     * - "oraxen:custom_egg"
     * - "nexo:custom_pet_collar"
     * - "DIAMOND" (Vanilla)
     */
    public ItemStack getItem(String identifier, Material fallback) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return new ItemStack(fallback != null ? fallback : Material.STONE);
        }

        String lower = identifier.toLowerCase().trim();

        // 1. ItemsAdder Support
        if ((lower.startsWith("itemsadder:") || lower.startsWith("ia:")) && itemsAdderAvailable) {
            String id = identifier.substring(identifier.indexOf(":") + 1);
            try {
                Class<?> csClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
                Method getInst = csClass.getMethod("getInstance", String.class);
                Object cs = getInst.invoke(null, id);
                if (cs != null) {
                    Method getItemStack = csClass.getMethod("getItemStack");
                    ItemStack stack = (ItemStack) getItemStack.invoke(cs);
                    if (stack != null) return stack.clone();
                }
            } catch (Exception ignored) {}
        }

        // 2. Oraxen Support
        if (lower.startsWith("oraxen:") && oraxenAvailable) {
            String id = identifier.substring(identifier.indexOf(":") + 1);
            try {
                Class<?> oiClass = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
                Method getItem = oiClass.getMethod("getItemById", String.class);
                Object itemModifier = getItem.invoke(null, id);
                if (itemModifier != null) {
                    Method build = itemModifier.getClass().getMethod("build");
                    ItemStack stack = (ItemStack) build.invoke(itemModifier);
                    if (stack != null) return stack.clone();
                }
            } catch (Exception ignored) {}
        }

        // 3. Nexo Support
        if (lower.startsWith("nexo:") && nexoAvailable) {
            String id = identifier.substring(identifier.indexOf(":") + 1);
            try {
                Class<?> niClass = Class.forName("com.nexomc.nexo.api.NexoItems");
                Method itemMethod = niClass.getMethod("item", String.class);
                Object itemBuilder = itemMethod.invoke(null, id);
                if (itemBuilder != null) {
                    Method build = itemBuilder.getClass().getMethod("build");
                    ItemStack stack = (ItemStack) build.invoke(itemBuilder);
                    if (stack != null) return stack.clone();
                }
            } catch (Exception ignored) {}
        }

        // 4. Vanilla Material fallback
        try {
            Material mat = Material.getMaterial(identifier.toUpperCase().trim());
            if (mat != null) return new ItemStack(mat);
        } catch (Exception ignored) {}

        return new ItemStack(fallback != null ? fallback : Material.STONE);
    }

    /**
     * Kiểm tra người chơi có đủ số lượng vật phẩm (Vanilla / ItemsAdder / Oraxen / Nexo) không
     */
    public boolean hasItem(Player player, String identifier, int amount) {
        if (player == null || identifier == null || amount <= 0) return true;
        int count = 0;
        String lower = identifier.toLowerCase().trim();

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;

            if (lower.startsWith("itemsadder:") || lower.startsWith("ia:")) {
                String targetId = identifier.substring(identifier.indexOf(":") + 1);
                if (isItemsAdderItem(item, targetId)) {
                    count += item.getAmount();
                }
            } else if (lower.startsWith("oraxen:")) {
                String targetId = identifier.substring(identifier.indexOf(":") + 1);
                if (isOraxenItem(item, targetId)) {
                    count += item.getAmount();
                }
            } else if (lower.startsWith("nexo:")) {
                String targetId = identifier.substring(identifier.indexOf(":") + 1);
                if (isNexoItem(item, targetId)) {
                    count += item.getAmount();
                }
            } else {
                Material mat = Material.getMaterial(identifier.toUpperCase().trim());
                if (mat != null && item.getType() == mat) {
                    count += item.getAmount();
                }
            }
            if (count >= amount) return true;
        }
        return count >= amount;
    }

    /**
     * Trừ vật phẩm của người chơi (Vanilla / ItemsAdder / Oraxen / Nexo)
     */
    public boolean takeItem(Player player, String identifier, int amount) {
        if (!hasItem(player, identifier, amount)) return false;
        int remaining = amount;
        String lower = identifier.toLowerCase().trim();

        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() == Material.AIR) continue;

            boolean match = false;
            if (lower.startsWith("itemsadder:") || lower.startsWith("ia:")) {
                String targetId = identifier.substring(identifier.indexOf(":") + 1);
                match = isItemsAdderItem(item, targetId);
            } else if (lower.startsWith("oraxen:")) {
                String targetId = identifier.substring(identifier.indexOf(":") + 1);
                match = isOraxenItem(item, targetId);
            } else if (lower.startsWith("nexo:")) {
                String targetId = identifier.substring(identifier.indexOf(":") + 1);
                match = isNexoItem(item, targetId);
            } else {
                Material mat = Material.getMaterial(identifier.toUpperCase().trim());
                match = (mat != null && item.getType() == mat);
            }

            if (match) {
                int stackAmount = item.getAmount();
                if (stackAmount <= remaining) {
                    remaining -= stackAmount;
                    contents[i] = null;
                } else {
                    item.setAmount(stackAmount - remaining);
                    remaining = 0;
                }
            }
            if (remaining <= 0) break;
        }
        player.getInventory().setContents(contents);
        player.updateInventory();
        return true;
    }

    public boolean isItemsAdderItem(ItemStack item, String targetId) {
        if (!itemsAdderAvailable || item == null) return false;
        try {
            Class<?> csClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Method byItemStack = csClass.getMethod("byItemStack", ItemStack.class);
            Object cs = byItemStack.invoke(null, item);
            if (cs != null) {
                Method getId = csClass.getMethod("getId");
                String id = (String) getId.invoke(cs);
                return targetId.equalsIgnoreCase(id) || (id != null && id.endsWith(":" + targetId));
            }
        } catch (Exception ignored) {}
        return false;
    }

    public boolean isOraxenItem(ItemStack item, String targetId) {
        if (!oraxenAvailable || item == null) return false;
        try {
            Class<?> oiClass = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
            Method getId = oiClass.getMethod("getIdByItem", ItemStack.class);
            String id = (String) getId.invoke(null, item);
            return targetId.equalsIgnoreCase(id);
        } catch (Exception ignored) {}
        return false;
    }

    public boolean isNexoItem(ItemStack item, String targetId) {
        if (!nexoAvailable || item == null) return false;
        try {
            Class<?> niClass = Class.forName("com.nexomc.nexo.api.NexoItems");
            Method idFromItem = niClass.getMethod("idFromItem", ItemStack.class);
            String id = (String) idFromItem.invoke(null, item);
            return targetId.equalsIgnoreCase(id);
        } catch (Exception ignored) {}
        return false;
    }

    public String getItemDisplayName(String identifier) {
        if (identifier == null) return "Vật phẩm";
        ItemStack item = getItem(identifier, Material.STONE);
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return identifier;
    }
}
