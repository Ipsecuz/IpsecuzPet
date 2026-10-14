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
    private LanguageManager languageManager;

    public ItemHookManager(IpsecuzPet plugin) {
        this.plugin = plugin;
        checkHooks();
    }

    public ItemHookManager() {
        this.plugin = null;
    }

    public ItemHookManager(boolean itemsAdder, boolean oraxen, boolean nexo) {
        this.plugin = null;
        this.itemsAdderAvailable = itemsAdder;
        this.oraxenAvailable = oraxen;
        this.nexoAvailable = nexo;
    }

    public void setLanguageManager(LanguageManager languageManager) {
        this.languageManager = languageManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager != null ? languageManager : (plugin != null ? plugin.getLanguage() : null);
    }

    public void checkHooks() {
        try {
            if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null) return;
            Plugin ia = Bukkit.getPluginManager().getPlugin("ItemsAdder");
            itemsAdderAvailable = (ia != null && ia.isEnabled());

            Plugin oraxen = Bukkit.getPluginManager().getPlugin("Oraxen");
            oraxenAvailable = (oraxen != null && oraxen.isEnabled());

            Plugin nexo = Bukkit.getPluginManager().getPlugin("Nexo");
            nexoAvailable = (nexo != null && nexo.isEnabled());

            if (plugin != null && plugin.getLogger() != null) {
                if (itemsAdderAvailable) plugin.getLogger().info("§a[ItemHookManager] Đã kết nối thành công với ItemsAdder!");
                if (oraxenAvailable) plugin.getLogger().info("§a[ItemHookManager] Đã kết nối thành công với Oraxen!");
                if (nexoAvailable) plugin.getLogger().info("§a[ItemHookManager] Đã kết nối thành công với Nexo!");
            }
        } catch (Throwable ignored) {}
    }

    public boolean isItemsAdderAvailable() { return itemsAdderAvailable; }
    public boolean isOraxenAvailable() { return oraxenAvailable; }
    public boolean isNexoAvailable() { return nexoAvailable; }

    public void setItemsAdderAvailable(boolean available) { this.itemsAdderAvailable = available; }
    public void setOraxenAvailable(boolean available) { this.oraxenAvailable = available; }
    public void setNexoAvailable(boolean available) { this.nexoAvailable = available; }

    public boolean isProviderAvailableForItem(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) return false;
        String lower = identifier.toLowerCase().trim();
        if (lower.startsWith("itemsadder:") || lower.startsWith("ia:")) {
            return itemsAdderAvailable;
        }
        if (lower.startsWith("oraxen:")) {
            return oraxenAvailable;
        }
        if (lower.startsWith("nexo:")) {
            return nexoAvailable;
        }
        Material mat = Material.getMaterial(identifier.toUpperCase().trim());
        return mat != null;
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

        // Custom item prefix without active provider must NOT convert to vanilla material!
        if (lower.startsWith("itemsadder:") || lower.startsWith("ia:") || lower.startsWith("oraxen:") || lower.startsWith("nexo:")) {
            return fallback != null ? new ItemStack(fallback) : null;
        }

        // 4. Vanilla Material fallback
        try {
            Material mat = Material.getMaterial(identifier.toUpperCase().trim());
            if (mat != null) return new ItemStack(mat);
        } catch (Exception ignored) {}

        return fallback != null ? new ItemStack(fallback) : null;
    }

    public boolean matchesItem(ItemStack item, String identifier, Integer customModelData, java.util.Map<String, String> pdcStrings) {
        if (item == null || item.getType() == Material.AIR || identifier == null) return false;
        String lower = identifier.toLowerCase().trim();
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
        if (!match) return false;

        if (customModelData != null) {
            if (!item.hasItemMeta() || !item.getItemMeta().hasCustomModelData() || item.getItemMeta().getCustomModelData() != customModelData) {
                return false;
            }
        }

        if (pdcStrings != null && !pdcStrings.isEmpty()) {
            if (!item.hasItemMeta()) return false;
            org.bukkit.persistence.PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            for (java.util.Map.Entry<String, String> entry : pdcStrings.entrySet()) {
                org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(entry.getKey(), plugin);
                if (key == null) {
                    String[] parts = entry.getKey().split(":");
                    key = parts.length > 1 ? new org.bukkit.NamespacedKey(parts[0], parts[1]) : new org.bukkit.NamespacedKey(plugin, parts[0]);
                }
                if (!pdc.has(key, org.bukkit.persistence.PersistentDataType.STRING)) return false;
                String val = pdc.get(key, org.bukkit.persistence.PersistentDataType.STRING);
                if (!entry.getValue().equals(val)) return false;
            }
        }

        return true;
    }

    /**
     * Kiểm tra người chơi có đủ số lượng vật phẩm (Vanilla / ItemsAdder / Oraxen / Nexo) không
     */
    public boolean hasItem(Player player, String identifier, int amount) {
        return hasItem(player, identifier, amount, null, null);
    }

    public boolean hasItem(Player player, String identifier, int amount, Integer customModelData, java.util.Map<String, String> pdcStrings) {
        if (player == null || identifier == null || amount <= 0) return true;
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (matchesItem(item, identifier, customModelData, pdcStrings)) {
                count += item.getAmount();
            }
            if (count >= amount) return true;
        }
        return count >= amount;
    }

    /**
     * Trừ vật phẩm của người chơi (Vanilla / ItemsAdder / Oraxen / Nexo)
     */
    public boolean takeItem(Player player, String identifier, int amount) {
        return takeItem(player, identifier, amount, null, null);
    }

    public boolean takeItem(Player player, String identifier, int amount, Integer customModelData, java.util.Map<String, String> pdcStrings) {
        if (!hasItem(player, identifier, amount, customModelData, pdcStrings)) return false;
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (matchesItem(item, identifier, customModelData, pdcStrings)) {
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

    public void giveItem(Player player, String identifier, int amount, Integer customModelData, java.util.Map<String, String> pdcStrings) {
        if (player == null || identifier == null || amount <= 0) return;
        ItemStack item = getItem(identifier, Material.STONE);
        if (item.hasItemMeta()) {
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (customModelData != null) {
                meta.setCustomModelData(customModelData);
            }
            if (pdcStrings != null && !pdcStrings.isEmpty()) {
                for (java.util.Map.Entry<String, String> entry : pdcStrings.entrySet()) {
                    org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(entry.getKey(), plugin);
                    if (key == null) {
                        String[] parts = entry.getKey().split(":");
                        key = parts.length > 1 ? new org.bukkit.NamespacedKey(parts[0], parts[1]) : new org.bukkit.NamespacedKey(plugin, parts[0]);
                    }
                    meta.getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.STRING, entry.getValue());
                }
            }
            item.setItemMeta(meta);
        }
        item.setAmount(amount);
        java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty() && player.getLocation() != null && player.getWorld() != null) {
            for (ItemStack rem : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rem);
            }
        }
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
        return getItemDisplayName(identifier, getLanguageManager());
    }

    public String getItemDisplayName(String identifier, LanguageManager lang) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return lang != null
                    ? lang.getMessageOrDefault("dialog.currency_item", "Vật phẩm")
                    : "Vật phẩm";
        }
        String lower = identifier.toLowerCase().trim();
        if (lower.startsWith("itemsadder:") || lower.startsWith("ia:")) {
            if (itemsAdderAvailable) {
                ItemStack item = getItem(identifier, null);
                if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                    return item.getItemMeta().getDisplayName();
                }
            }
            String id = identifier.substring(identifier.indexOf(":") + 1);
            return formatReadableIdentifier(id);
        }
        if (lower.startsWith("oraxen:")) {
            if (oraxenAvailable) {
                ItemStack item = getItem(identifier, null);
                if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                    return item.getItemMeta().getDisplayName();
                }
            }
            String id = identifier.substring(identifier.indexOf(":") + 1);
            return formatReadableIdentifier(id);
        }
        if (lower.startsWith("nexo:")) {
            if (nexoAvailable) {
                ItemStack item = getItem(identifier, null);
                if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                    return item.getItemMeta().getDisplayName();
                }
            }
            String id = identifier.substring(identifier.indexOf(":") + 1);
            return formatReadableIdentifier(id);
        }

        // Vanilla Material
        Material mat = Material.getMaterial(identifier.toUpperCase().trim());
        if (mat != null) {
            if (lang != null) {
                String key = "materials." + mat.name();
                String raw = lang.getRawMessage(key);
                if (raw != null && !raw.startsWith("§cMissing")) {
                    return raw;
                }
            }
            return formatReadableIdentifier(mat.name());
        }

        return formatReadableIdentifier(identifier);
    }

    public static String formatReadableIdentifier(String id) {
        if (id == null || id.isEmpty()) return "";
        String[] parts = id.replace('_', ' ').replace('-', ' ').trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty()) continue;
            if (i > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) {
                sb.append(p.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    /**
     * Refunds item safely without converting missing custom items into Diamond/Stone.
     * Drops leftovers on the ground if player inventory is full.
     */
    public boolean refundItem(Player player, String identifier, int amount, Integer customModelData, java.util.Map<String, String> pdcStrings) {
        if (player == null || identifier == null || amount <= 0) return false;
        if (!isProviderAvailableForItem(identifier)) {
            // NEVER substitute custom item with Stone or Diamond!
            return false;
        }
        ItemStack item = getItem(identifier, null);
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        if (customModelData != null && item.hasItemMeta()) {
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            meta.setCustomModelData(customModelData);
            item.setItemMeta(meta);
        }
        if (pdcStrings != null && !pdcStrings.isEmpty() && item.hasItemMeta()) {
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            for (java.util.Map.Entry<String, String> entry : pdcStrings.entrySet()) {
                org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(entry.getKey(), plugin);
                if (key == null) {
                    String[] parts = entry.getKey().split(":");
                    key = parts.length > 1 ? new org.bukkit.NamespacedKey(parts[0], parts[1]) : new org.bukkit.NamespacedKey(plugin, parts[0]);
                }
                meta.getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.STRING, entry.getValue());
            }
            item.setItemMeta(meta);
        }
        item.setAmount(amount);
        java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty() && player.getLocation() != null && player.getWorld() != null) {
            for (ItemStack rem : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rem);
            }
        }
        return true;
    }
}
