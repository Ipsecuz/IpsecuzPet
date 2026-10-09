package org.ipsecuz.pet;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Standardized, fluent item builder for IpsecuzPet GUI items.
 * Ensures consistent display name formatting, lore injection, and PDC tagging.
 */
public class GuiItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    public GuiItemBuilder(Material material) {
        this(new ItemStack(material != null ? material : Material.STONE, 1));
    }

    public GuiItemBuilder(Material material, int amount) {
        this(new ItemStack(material != null ? material : Material.STONE, Math.max(1, amount)));
    }

    public GuiItemBuilder(ItemStack item) {
        this.item = (item != null) ? item.clone() : new ItemStack(Material.STONE);
        this.meta = this.item.getItemMeta();
    }

    public static GuiItemBuilder from(Material material) {
        return new GuiItemBuilder(material);
    }

    public static GuiItemBuilder from(Material material, int amount) {
        return new GuiItemBuilder(material, amount);
    }

    public static GuiItemBuilder from(ItemStack item) {
        return new GuiItemBuilder(item);
    }

    public static GuiItemBuilder from(String materialOrHook, Material fallback) {
        IpsecuzPet plugin = IpsecuzPet.getInstance();
        if (plugin != null && plugin.getItemHookManager() != null) {
            ItemStack item = plugin.getItemHookManager().getItem(materialOrHook, fallback);
            return new GuiItemBuilder(item);
        }
        Material mat = (materialOrHook != null) ? Material.matchMaterial(materialOrHook) : null;
        return new GuiItemBuilder(mat != null ? mat : fallback);
    }

    public GuiItemBuilder name(String name) {
        if (meta != null && name != null) {
            meta.displayName(GuiText.component(name));
        }
        return this;
    }

    public GuiItemBuilder name(Component component) {
        if (meta != null && component != null) {
            meta.displayName(GuiText.stripItalics(component));
        }
        return this;
    }

    public GuiItemBuilder lore(GuiLoreBuilder builder) {
        if (meta != null && builder != null) {
            meta.lore(builder.build());
        }
        return this;
    }

    public GuiItemBuilder lore(List<Component> lore) {
        if (meta != null && lore != null) {
            meta.lore(lore);
        }
        return this;
    }

    public <T, Z> GuiItemBuilder pdc(NamespacedKey key, PersistentDataType<T, Z> type, Z value) {
        if (meta != null && key != null && value != null) {
            meta.getPersistentDataContainer().set(key, type, value);
        }
        return this;
    }

    public GuiItemBuilder pdc(String key, String value) {
        IpsecuzPet plugin = IpsecuzPet.getInstance();
        if (plugin != null && key != null && value != null) {
            return pdc(new NamespacedKey(plugin, key), PersistentDataType.STRING, value);
        }
        return this;
    }

    public GuiItemBuilder pdc(String key, int value) {
        IpsecuzPet plugin = IpsecuzPet.getInstance();
        if (plugin != null && key != null) {
            return pdc(new NamespacedKey(plugin, key), PersistentDataType.INTEGER, value);
        }
        return this;
    }

    public GuiItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, amount));
        return this;
    }

    public ItemStack build() {
        if (meta != null) {
            item.setItemMeta(meta);
        }
        return item;
    }
}
