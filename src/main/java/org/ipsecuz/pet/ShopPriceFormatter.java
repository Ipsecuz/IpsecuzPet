package org.ipsecuz.pet;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.ipsecuz.pet.requirement.RequirementCheckResult;
import org.ipsecuz.pet.requirement.RequirementContext;
import org.ipsecuz.pet.requirement.RequirementGroup;
import org.ipsecuz.pet.requirement.RequirementGuiRenderer;
import org.ipsecuz.pet.requirement.RequirementManager;

import java.util.*;

/**
 * Shared service for authoritative shop price formatting and validation.
 * Used by NativeDialogProvider, ChestGuiFallback, and GuiListener to guarantee
 * consistent cost representations across all user interfaces and payment backends.
 */
public class ShopPriceFormatter {

    public enum PriceType {
        MONEY,
        POINTS,
        ITEM,
        STRUCTURED_REQUIREMENTS,
        FREE,
        INVALID
    }

    public static class PriceResult {
        private final PriceType type;
        private final double amount;
        private final String rawIdentifier;
        private final String itemDisplayName;
        private final boolean available;
        private final String unavailableReason;
        private final String formattedPrice;
        private final String browseButtonLabel;
        private final List<String> detailLines;
        private final RequirementGroup requirementGroup;
        private final RequirementCheckResult requirementCheckResult;

        public PriceResult(PriceType type, double amount, String rawIdentifier, String itemDisplayName,
                           boolean available, String unavailableReason, String formattedPrice,
                           String browseButtonLabel, List<String> detailLines,
                           RequirementGroup requirementGroup, RequirementCheckResult requirementCheckResult) {
            this.type = type;
            this.amount = amount;
            this.rawIdentifier = rawIdentifier;
            this.itemDisplayName = itemDisplayName;
            this.available = available;
            this.unavailableReason = unavailableReason;
            this.formattedPrice = formattedPrice;
            this.browseButtonLabel = browseButtonLabel;
            this.detailLines = detailLines != null ? Collections.unmodifiableList(detailLines) : Collections.emptyList();
            this.requirementGroup = requirementGroup;
            this.requirementCheckResult = requirementCheckResult;
        }

        public PriceType getType() { return type; }
        public double getAmount() { return amount; }
        public String getRawIdentifier() { return rawIdentifier; }
        public String getItemDisplayName() { return itemDisplayName; }
        public boolean isAvailable() { return available; }
        public String getUnavailableReason() { return unavailableReason; }
        public String getFormattedPrice() { return formattedPrice; }
        public String getBrowseButtonLabel() { return browseButtonLabel; }
        public List<String> getDetailLines() { return detailLines; }
        public RequirementGroup getRequirementGroup() { return requirementGroup; }
        public RequirementCheckResult getRequirementCheckResult() { return requirementCheckResult; }
    }

    private final IpsecuzPet plugin;
    private LanguageManager languageManager;
    private CurrencyManager currencyManager;
    private ItemHookManager itemHookManager;
    private RequirementManager requirementManager;
    private final Map<String, String> invalidShopEntries = new LinkedHashMap<>();

    public ShopPriceFormatter(IpsecuzPet plugin) {
        this.plugin = plugin;
    }

    public ShopPriceFormatter(LanguageManager lang, CurrencyManager curr, ItemHookManager itemHooks, RequirementManager reqs) {
        this.plugin = null;
        this.languageManager = lang;
        this.currencyManager = curr;
        this.itemHookManager = itemHooks;
        this.requirementManager = reqs;
    }

    public LanguageManager getLanguageManager() {
        return languageManager != null ? languageManager : (plugin != null ? plugin.getLanguage() : null);
    }

    public CurrencyManager getCurrencyManager() {
        return currencyManager != null ? currencyManager : (plugin != null ? plugin.getCurrencyManager() : null);
    }

    public ItemHookManager getItemHookManager() {
        return itemHookManager != null ? itemHookManager : (plugin != null ? plugin.getItemHookManager() : null);
    }

    public RequirementManager getRequirementManager() {
        return requirementManager != null ? requirementManager : (plugin != null ? plugin.getRequirementManager() : null);
    }

    /**
     * Validates all pets in the given configuration section.
     */
    public void validateConfiguration(ConfigurationSection petsSec) {
        invalidShopEntries.clear();
        if (petsSec == null) return;
        for (String petId : petsSec.getKeys(false)) {
            ConfigurationSection sec = petsSec.getConfigurationSection(petId);
            if (sec == null) continue;
            PriceResult res = format(null, sec, petId);
            if (res.getType() == PriceType.INVALID) {
                invalidShopEntries.put(petId, res.getUnavailableReason());
                if (plugin != null && plugin.getLogger() != null) {
                    plugin.getLogger().warning("[ShopPriceFormatter] Invalid shop configuration for pet '" + petId + "': " + res.getUnavailableReason());
                }
            } else if (!res.isAvailable()) {
                if (plugin != null && plugin.getLogger() != null) {
                    plugin.getLogger().info("[ShopPriceFormatter] Pet '" + petId + "' is currently unavailable: " + res.getUnavailableReason());
                }
            }
        }
    }

    /**
     * Scans and validates all pets defined under pets.* in config.yml.
     * Records invalid shop entries for administrative diagnostics.
     */
    public void validateAllPets() {
        if (plugin == null || plugin.getConfig() == null) return;
        ConfigurationSection petsSec = plugin.getConfig().getConfigurationSection("pets");
        if (petsSec == null) return;
        validateConfiguration(petsSec);
    }

    public Map<String, String> getInvalidShopEntries() {
        return Collections.unmodifiableMap(invalidShopEntries);
    }

    public int getInvalidShopEntriesCount() {
        return invalidShopEntries.size();
    }

    public PriceResult format(String petId) {
        return format(null, petId);
    }

    public PriceResult format(Player player, String petId) {
        if (petId == null) {
            return makeInvalid(null, "Pet ID is null");
        }
        if (plugin == null || plugin.getConfig() == null) {
            return makeInvalid(petId, "Plugin configuration is not loaded");
        }
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("pets." + petId);
        if (sec == null) {
            return makeInvalid(petId, "Pet '" + petId + "' not found in configuration");
        }
        return format(player, sec, petId);
    }

    public PriceResult format(Player player, ConfigurationSection petSec, String petId) {
        LanguageManager lang = getLanguageManager();

        // 1. Structured requirements check (ALL / ONE_OF)
        if (petSec.contains("requirements") || petSec.contains("all") || petSec.contains("one_of")) {
            RequirementGroup group = null;
            try {
                RequirementManager rm = getRequirementManager();
                if (rm != null) {
                    group = rm.parse(petSec);
                }
            } catch (Exception ex) {
                return makeInvalid(petId, "Failed to parse requirements: " + ex.getMessage());
            }
            if (group == null || group.getRequirements().isEmpty()) {
                return makeInvalid(petId, "Structured requirements section ('requirements', 'all', or 'one_of') is defined but contains no valid executable requirements.");
            }
            RequirementContext ctx = new RequirementContext(player, petId, 1, 1, plugin);
            RequirementCheckResult evalResult = group.evaluate(ctx);
            List<String> rendered = RequirementGuiRenderer.renderToStrings(evalResult);

            String formatted = lang != null ? lang.getMessageOrDefault("dialog.currency_requirements", "Yêu cầu nhiệm vụ") : "Yêu cầu nhiệm vụ";
            String browseLabel = lang != null ? lang.getMessageOrDefault("dialog.shop_status_requirements", "&6[Điều kiện]") : "&6[Điều kiện]";

            return new PriceResult(PriceType.STRUCTURED_REQUIREMENTS, 0, null, null,
                    true, null, formatted, browseLabel, rendered, group, evalResult);
        }

        // 2. Explicit FREE check
        String rawCurrency = petSec.getString("currency", "").trim();
        boolean explicitFree = petSec.getBoolean("free", false) || "FREE".equalsIgnoreCase(rawCurrency);
        if (explicitFree) {
            String freeLabel = lang != null ? lang.getMessageOrDefault("dialog.currency_free", "Miễn phí") : "Miễn phí";
            String browseLabel = lang != null ? lang.getMessageOrDefault("dialog.shop_status_free", "&a[Miễn phí]") : "&a[Miễn phí]";
            List<String> details = List.of(lang != null ? lang.getMessageOrDefault("dialog.shop_price_free", "&aMiễn phí") : "&aMiễn phí");
            return new PriceResult(PriceType.FREE, 0, null, null, true, null, freeLabel, browseLabel, details, null, null);
        }

        // 3. Price validation
        if (!petSec.contains("price")) {
            String reason = lang != null ? lang.getMessageOrDefault("shop.invalid_price", "&cThiếu cấu hình giá bán (price)!") : "Missing price configuration";
            return makeInvalid(petId, reason);
        }
        double price = petSec.getDouble("price", -1.0);
        if (Double.isNaN(price) || Double.isInfinite(price)) {
            return makeInvalid(petId, "Price must be a finite number: " + price);
        }
        if (price < 0) {
            String reason = lang != null ? lang.getMessageOrDefault("shop.invalid_price", "&cGiá bán không được là số âm: %price%", "%price%", String.valueOf(price)) : "Negative price: " + price;
            return makeInvalid(petId, reason);
        }
        if (price == 0) {
            // Price is 0 without explicit free flag or FREE currency
            return makeInvalid(petId, "Price is 0 but pet is not explicitly configured as FREE");
        }

        // 4. Currency type normalization
        if (rawCurrency.isEmpty()) {
            return makeInvalid(petId, "Missing currency configuration");
        }
        String normalizedCurrency = normalizeCurrency(rawCurrency);
        if (normalizedCurrency == null) {
            String reason = lang != null ? lang.getMessageOrDefault("shop.invalid_currency", "&cLoại tiền tệ không được hỗ trợ: %currency%", "%currency%", rawCurrency) : "Unsupported currency: " + rawCurrency;
            return makeInvalid(petId, reason);
        }

        // 5. MONEY (Vault)
        if ("MONEY".equals(normalizedCurrency)) {
            if (price < 0 || Double.isNaN(price) || Double.isInfinite(price)) {
                return makeInvalid(petId, "Money price must be a finite non-negative number: " + price);
            }
            CurrencyManager cm = getCurrencyManager();
            boolean hasEcon = cm != null && cm.hasEconomy();
            Economy econ = cm != null ? cm.getEconomy() : null;
            String formattedMoney = formatMoney(econ, price);
            String unavailText = lang != null ? lang.getMessageOrDefault("dialog.shop_status_unavailable", "&c[Chưa khả dụng]") : "&c[Chưa khả dụng]";
            String browseLabel = hasEcon ? "&e[" + formattedMoney + "]" : unavailText;
            String unavailReason = hasEcon ? null : (lang != null ? lang.getMessageOrDefault("shop.economy_unavailable", "&cKinh tế Vault chưa được kích hoạt!") : "Economy provider (Vault) unavailable");
            List<String> details = List.of(lang != null ? lang.getMessageOrDefault("dialog.shop_price_money", "&7Giá: &a%price%", "%price%", formattedMoney) : "Giá: " + formattedMoney);
            return new PriceResult(PriceType.MONEY, price, null, null, hasEcon, unavailReason, formattedMoney, browseLabel, details, null, null);
        }

        // 6. POINTS (PlayerPoints)
        if ("POINTS".equals(normalizedCurrency)) {
            if (price != Math.floor(price) || price <= 0 || price > Integer.MAX_VALUE) {
                return makeInvalid(petId, "Points price must be a positive whole integer, got: " + price);
            }
            CurrencyManager cm = getCurrencyManager();
            boolean hasPoints = cm != null && cm.getPointsAPI() != null;
            int pointsCost = (int) price;
            String pointsSuffix = lang != null ? lang.getMessageOrDefault("dialog.currency_points", "Points") : "Points";
            String formattedPoints = pointsCost + " " + pointsSuffix;
            String unavailText = lang != null ? lang.getMessageOrDefault("dialog.shop_status_unavailable", "&c[Chưa khả dụng]") : "&c[Chưa khả dụng]";
            String browseLabel = hasPoints ? "&b[" + formattedPoints + "]" : unavailText;
            String unavailReason = hasPoints ? null : (lang != null ? lang.getMessageOrDefault("shop.points_unavailable", "&cPlayerPoints chưa được kích hoạt!") : "Points provider (PlayerPoints) unavailable");
            List<String> details = List.of(lang != null ? lang.getMessageOrDefault("dialog.shop_price_points", "&7Giá: &b%price% Points", "%price%", String.valueOf(pointsCost)) : "Giá: " + formattedPoints);
            return new PriceResult(PriceType.POINTS, pointsCost, null, null, hasPoints, unavailReason, formattedPoints, browseLabel, details, null, null);
        }

        // 7. ITEM (Vanilla / ItemsAdder / Oraxen / Nexo)
        if ("ITEM".equals(normalizedCurrency)) {
            if (price != Math.floor(price) || price <= 0 || price > Integer.MAX_VALUE) {
                return makeInvalid(petId, "Item price must be a positive whole integer, got: " + price);
            }
            String materialId = petSec.getString("material");
            if (materialId == null || materialId.trim().isEmpty()) {
                String reason = lang != null ? lang.getMessageOrDefault("shop.missing_material", "&cThiếu vật phẩm yêu cầu trong cấu hình!") : "Missing material configuration";
                return makeInvalid(petId, reason);
            }
            materialId = materialId.trim();
            int amount = (int) price;

            ItemHookManager ihm = getItemHookManager();
            boolean providerAvailable = ihm != null && ihm.isProviderAvailableForItem(materialId);
            String itemDisplayName = resolveItemDisplayName(petSec, materialId);
            String formattedItem = amount + " × " + itemDisplayName;
            String unavailText = lang != null ? lang.getMessageOrDefault("dialog.shop_status_unavailable", "&c[Chưa khả dụng]") : "&c[Chưa khả dụng]";
            String browseLabel = providerAvailable ? "&a[" + formattedItem + "]" : unavailText;
            String unavailReason = providerAvailable ? null : (lang != null ? lang.getMessageOrDefault("shop.custom_item_unavailable", "&cPlugin cung cấp vật phẩm (%provider%) chưa được kích hoạt!", "%provider%", extractProviderName(materialId)) : "Item provider unavailable");
            List<String> details = List.of(lang != null ? lang.getMessageOrDefault("dialog.shop_price_item", "&7Giá: &e%amount% × %item%", "%amount%", String.valueOf(amount), "%item%", itemDisplayName) : "Giá: " + formattedItem);
            return new PriceResult(PriceType.ITEM, amount, materialId, itemDisplayName, providerAvailable, unavailReason, formattedItem, browseLabel, details, null, null);
        }

        return makeInvalid(petId, "Unhandled currency type: " + normalizedCurrency);
    }

    private PriceResult makeInvalid(String petId, String reason) {
        LanguageManager lang = getLanguageManager();
        String formatted = lang != null ? lang.getMessageOrDefault("dialog.shop_status_invalid", "&c[Lỗi cấu hình]") : "&c[Lỗi cấu hình]";
        List<String> details = List.of("&c" + reason);
        return new PriceResult(PriceType.INVALID, 0, null, null, false, reason, formatted, formatted, details, null, null);
    }

    public static String normalizeCurrency(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toUpperCase();
        if ("MONEY".equals(s) || "VAULT".equals(s) || "ECO".equals(s) || "ECONOMY".equals(s)) {
            return "MONEY";
        }
        if ("POINTS".equals(s) || "POINT".equals(s) || "PLAYERPOINTS".equals(s)) {
            return "POINTS";
        }
        if ("ITEM".equals(s) || "ITEMS".equals(s) || "MATERIAL".equals(s)) {
            return "ITEM";
        }
        if ("FREE".equals(s)) {
            return "FREE";
        }
        return null;
    }

    public String formatMoney(Economy econ, double amount) {
        if (econ != null) {
            try {
                String formatted = econ.format(amount);
                if (formatted != null && !formatted.trim().isEmpty()) {
                    return formatted;
                }
            } catch (Throwable ignored) {}
        }
        if (amount == (long) amount) {
            return "$" + String.format(Locale.US, "%,d", (long) amount);
        } else {
            return "$" + String.format(Locale.US, "%,.2f", amount);
        }
    }

    public String resolveItemDisplayName(ConfigurationSection petSec, String materialId) {
        if (petSec != null) {
            String explicitName = petSec.getString("material_display", petSec.getString("material_name", null));
            if (explicitName != null && !explicitName.trim().isEmpty()) {
                return explicitName.trim();
            }
        }
        ItemHookManager ihm = getItemHookManager();
        if (ihm != null) {
            return ihm.getItemDisplayName(materialId);
        }
        return materialId;
    }

    private String extractProviderName(String id) {
        if (id == null) return "Unknown";
        String lower = id.toLowerCase();
        if (lower.startsWith("itemsadder:") || lower.startsWith("ia:")) return "ItemsAdder";
        if (lower.startsWith("oraxen:")) return "Oraxen";
        if (lower.startsWith("nexo:")) return "Nexo";
        return id;
    }
}
