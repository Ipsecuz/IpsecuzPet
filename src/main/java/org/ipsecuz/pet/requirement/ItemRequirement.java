package org.ipsecuz.pet.requirement;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.ipsecuz.pet.ItemHookManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Universal item requirement supporting Vanilla materials and custom plugin items
 * (ItemsAdder, Oraxen, Nexo) with optional exact CustomModelData and PDC matching.
 * Consumable, atomic, reversible.
 */
public class ItemRequirement implements Requirement {
    private final String id;
    private final int amount;
    private final String customDisplay;
    private final Integer customModelData;
    private final Map<String, String> pdcStrings;

    public ItemRequirement(String id, int amount, String customDisplay, Integer customModelData, Map<String, String> pdcStrings) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Item id cannot be null or empty");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Item amount must be greater than 0");
        }
        this.id = id.trim();
        this.amount = amount;
        this.customDisplay = customDisplay;
        this.customModelData = customModelData;
        this.pdcStrings = pdcStrings != null ? Collections.unmodifiableMap(new HashMap<>(pdcStrings)) : Collections.emptyMap();
    }

    public ItemRequirement(String id, int amount) {
        this(id, amount, null, null, null);
    }

    public String getId() {
        return id;
    }

    public int getAmount() {
        return amount;
    }

    public String getCustomDisplay() {
        return customDisplay;
    }

    public Integer getCustomModelData() {
        return customModelData;
    }

    public Map<String, String> getPdcStrings() {
        return pdcStrings;
    }

    @Override
    public RequirementType getType() {
        return RequirementType.ITEM;
    }

    @Override
    public boolean isConsumable() {
        return true;
    }

    public String getItemName(RequirementContext ctx) {
        if (customDisplay != null && !customDisplay.isEmpty()) {
            return customDisplay;
        }
        if (ctx != null && ctx.getPlugin() != null && ctx.getPlugin().getItemHookManager() != null) {
            return ctx.getPlugin().getItemHookManager().getItemDisplayName(id);
        }
        return id;
    }

    @Override
    public String getDisplay(RequirementContext ctx) {
        return getItemName(ctx) + " x" + amount;
    }

    @Override
    public RequirementCheckResult evaluate(RequirementContext ctx) {
        int current = 0;
        if (ctx != null && ctx.getPlayer() != null && ctx.getPlugin() != null) {
            ItemHookManager ihm = ctx.getPlugin().getItemHookManager();
            if (ihm != null) {
                for (ItemStack item : ctx.getPlayer().getInventory().getContents()) {
                    if (ihm.matchesItem(item, id, customModelData, pdcStrings)) {
                        current += item.getAmount();
                    }
                }
            }
        }
        boolean satisfied = current >= amount;
        String display = getItemName(ctx) + " x" + amount + " (" + current + "/" + amount + ")";
        String msg = satisfied ? "" : "Thiếu " + (amount - current) + "x " + getItemName(ctx);
        return RequirementCheckResult.single(satisfied, getType(), display, amount, current, msg);
    }

    @Override
    public void planTransaction(RequirementContext ctx, RequirementTransaction tx) {
        if (ctx == null || ctx.getPlayer() == null || ctx.getPlugin() == null) return;
        final ItemHookManager ihm = ctx.getPlugin().getItemHookManager();
        if (ihm == null) return;

        tx.addStep(new RequirementTransaction.TransactionStep() {
            @Override
            public boolean execute(Player player) {
                return ihm.takeItem(player, id, amount, customModelData, pdcStrings);
            }

            @Override
            public void rollback(Player player) {
                ihm.giveItem(player, id, amount, customModelData, pdcStrings);
            }

            @Override
            public String getDescription() {
                return "Take " + amount + "x " + id + " from player " + (ctx.getPlayer() != null ? ctx.getPlayer().getName() : "null");
            }
        });
    }
}
