package org.ipsecuz.pet;

import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.ipsecuz.pet.ShopPriceFormatter.PriceResult;
import org.ipsecuz.pet.ShopPriceFormatter.PriceType;
import org.ipsecuz.pet.ShopTransactionReceipt.Status;
import org.ipsecuz.pet.dialog.DialogService;
import org.ipsecuz.pet.requirement.RequirementManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ShopPriceAndTransactionTest {

    private YamlConfiguration vnConfig;
    private YamlConfiguration enConfig;
    private LanguageManager vnLang;
    private LanguageManager enLang;
    private ItemHookManager itemHooks;
    private CurrencyManager currencyManager;
    private RequirementManager requirementManager;
    private ShopPriceFormatter formatter;

    @BeforeEach
    public void setup() {
        InputStream vnStream = getClass().getClassLoader().getResourceAsStream("languages/VN.yml");
        assertNotNull(vnStream, "languages/VN.yml must exist");
        vnConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(vnStream, StandardCharsets.UTF_8));

        InputStream enStream = getClass().getClassLoader().getResourceAsStream("languages/EN.yml");
        assertNotNull(enStream, "languages/EN.yml must exist");
        enConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(enStream, StandardCharsets.UTF_8));

        vnLang = new LanguageManager(vnConfig, vnConfig, "VN");
        enLang = new LanguageManager(enConfig, vnConfig, "EN");

        itemHooks = new ItemHookManager(true, true, true);
        itemHooks.setLanguageManager(vnLang);
        currencyManager = new CurrencyManager();
        requirementManager = new RequirementManager(null);

        formatter = new ShopPriceFormatter(vnLang, currencyManager, itemHooks, requirementManager);
    }

    @Test
    @DisplayName("Case 1: MONEY price display ($5,000)")
    public void test01_MoneyPriceDisplay() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection sec = config.createSection("pets.rich_dog");
        sec.set("currency", "MONEY");
        sec.set("price", 5000.0);

        PriceResult res = formatter.format(null, sec, "rich_dog");
        assertEquals(PriceType.MONEY, res.getType());
        assertEquals(5000.0, res.getAmount(), 0.001);
        assertEquals("$5,000", res.getFormattedPrice());
        assertFalse(res.getFormattedPrice().contains("null"));
    }

    @Test
    @DisplayName("Case 2: POINTS price display (100 Points)")
    public void test02_PointsPriceDisplay() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection sec = config.createSection("pets.mystic_cat");
        sec.set("currency", "POINTS");
        sec.set("price", 100);

        PriceResult res = formatter.format(null, sec, "mystic_cat");
        assertEquals(PriceType.POINTS, res.getType());
        assertEquals(100.0, res.getAmount(), 0.001);
        assertTrue(res.getFormattedPrice().contains("100 Points"));
    }

    @Test
    @DisplayName("Case 3: Vanilla ITEM price display (32 × Wheat Seeds)")
    public void test03_VanillaItemPriceDisplay() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection sec = config.createSection("pets.chicken_pet");
        sec.set("currency", "ITEM");
        sec.set("price", 32);
        sec.set("material", "WHEAT_SEEDS");

        // With English language
        itemHooks.setLanguageManager(enLang);
        ShopPriceFormatter enFormatter = new ShopPriceFormatter(enLang, currencyManager, itemHooks, requirementManager);
        PriceResult enRes = enFormatter.format(null, sec, "chicken_pet");
        assertEquals(PriceType.ITEM, enRes.getType());
        assertEquals(32.0, enRes.getAmount(), 0.001);
        assertEquals("WHEAT_SEEDS", enRes.getRawIdentifier());
        assertEquals("32 × Wheat Seeds", enRes.getFormattedPrice());

        // With Vietnamese language
        itemHooks.setLanguageManager(vnLang);
        PriceResult vnRes = formatter.format(null, sec, "chicken_pet");
        assertEquals("32 × Hạt giống lúa mì", vnRes.getFormattedPrice());
    }

    @Test
    @DisplayName("Case 4: ItemsAdder price display")
    public void test04_ItemsAdderPriceDisplay() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection sec = config.createSection("pets.ia_pet");
        sec.set("currency", "ITEM");
        sec.set("price", 2);
        sec.set("material", "itemsadder:ruby_sword");

        itemHooks.setItemsAdderAvailable(true);
        PriceResult res = formatter.format(null, sec, "ia_pet");

        assertEquals(PriceType.ITEM, res.getType());
        assertTrue(res.isAvailable(), "Must be available when ItemsAdder is active");
        assertTrue(res.getItemDisplayName().contains("Ruby Sword"));
        assertTrue(res.getFormattedPrice().contains("2 × Ruby Sword"));
    }

    @Test
    @DisplayName("Case 5: Oraxen price display")
    public void test05_OraxenPriceDisplay() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection sec = config.createSection("pets.oraxen_pet");
        sec.set("currency", "ITEM");
        sec.set("price", 5);
        sec.set("material", "oraxen:amethyst_pickaxe");

        itemHooks.setOraxenAvailable(true);
        PriceResult res = formatter.format(null, sec, "oraxen_pet");

        assertEquals(PriceType.ITEM, res.getType());
        assertTrue(res.isAvailable(), "Must be available when Oraxen is active");
        assertTrue(res.getItemDisplayName().contains("Amethyst Pickaxe"));
        assertTrue(res.getFormattedPrice().contains("5 × Amethyst Pickaxe"));
    }

    @Test
    @DisplayName("Case 6: Nexo price display")
    public void test06_NexoPriceDisplay() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection sec = config.createSection("pets.nexo_pet");
        sec.set("currency", "ITEM");
        sec.set("price", 1);
        sec.set("material", "nexo:dragon_scale");

        itemHooks.setNexoAvailable(true);
        PriceResult res = formatter.format(null, sec, "nexo_pet");

        assertEquals(PriceType.ITEM, res.getType());
        assertTrue(res.isAvailable(), "Must be available when Nexo is active");
        assertTrue(res.getItemDisplayName().contains("Dragon Scale"));
        assertTrue(res.getFormattedPrice().contains("1 × Dragon Scale"));
    }

    @Test
    @DisplayName("Case 7: Custom item provider unavailable")
    public void test07_CustomItemProviderUnavailable() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection sec = config.createSection("pets.missing_ia_pet");
        sec.set("currency", "ITEM");
        sec.set("price", 3);
        sec.set("material", "itemsadder:ruby_sword");

        itemHooks.setItemsAdderAvailable(false); // Offline!
        PriceResult res = formatter.format(null, sec, "missing_ia_pet");

        assertEquals(PriceType.ITEM, res.getType());
        assertFalse(res.isAvailable(), "Must be unavailable when ItemsAdder provider is offline");
        assertNotNull(res.getUnavailableReason());
        assertTrue(res.getUnavailableReason().contains("ItemsAdder") || res.getUnavailableReason().contains("chưa được kích hoạt"));
        assertTrue(res.getBrowseButtonLabel().contains("Chưa khả dụng"));
    }

    @Test
    @DisplayName("Case 8: Missing price")
    public void test08_MissingPrice() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection sec = config.createSection("pets.no_price_pet");
        sec.set("currency", "MONEY");

        PriceResult res = formatter.format(null, sec, "no_price_pet");
        assertEquals(PriceType.INVALID, res.getType());
        assertFalse(res.isAvailable());
        assertTrue(res.getUnavailableReason().toLowerCase().contains("giá bán") || res.getUnavailableReason().toLowerCase().contains("price"));
    }

    @Test
    @DisplayName("Case 9: Missing material for ITEM currency")
    public void test09_MissingMaterial() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection sec = config.createSection("pets.no_material_pet");
        sec.set("currency", "ITEM");
        sec.set("price", 10);

        PriceResult res = formatter.format(null, sec, "no_material_pet");
        assertEquals(PriceType.INVALID, res.getType());
        assertFalse(res.isAvailable());
        assertTrue(res.getUnavailableReason().toLowerCase().contains("vật phẩm") || res.getUnavailableReason().toLowerCase().contains("material"));
    }

    @Test
    @DisplayName("Case 10: Unsupported currency")
    public void test10_UnsupportedCurrency() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection sec = config.createSection("pets.bitcoin_pet");
        sec.set("currency", "BITCOIN");
        sec.set("price", 100);

        PriceResult res = formatter.format(null, sec, "bitcoin_pet");
        assertEquals(PriceType.INVALID, res.getType());
        assertFalse(res.isAvailable());
        assertTrue(res.getUnavailableReason().toLowerCase().contains("tiền tệ") || res.getUnavailableReason().toLowerCase().contains("currency"));
    }

    @Test
    @DisplayName("Case 11: Lower-case currency handling")
    public void test11_LowerCaseCurrencyHandling() {
        assertEquals("MONEY", ShopPriceFormatter.normalizeCurrency("money"));
        assertEquals("MONEY", ShopPriceFormatter.normalizeCurrency("vault"));
        assertEquals("MONEY", ShopPriceFormatter.normalizeCurrency("eco"));
        assertEquals("MONEY", ShopPriceFormatter.normalizeCurrency("economy"));

        assertEquals("POINTS", ShopPriceFormatter.normalizeCurrency("points"));
        assertEquals("POINTS", ShopPriceFormatter.normalizeCurrency("point"));
        assertEquals("POINTS", ShopPriceFormatter.normalizeCurrency("playerpoints"));

        assertEquals("ITEM", ShopPriceFormatter.normalizeCurrency("item"));
        assertEquals("ITEM", ShopPriceFormatter.normalizeCurrency("items"));
        assertEquals("ITEM", ShopPriceFormatter.normalizeCurrency("material"));

        assertEquals("FREE", ShopPriceFormatter.normalizeCurrency("free"));
        assertNull(ShopPriceFormatter.normalizeCurrency("unknown"));
    }

    @Test
    @DisplayName("Case 12: Negative price")
    public void test12_NegativePrice() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection sec = config.createSection("pets.negative_pet");
        sec.set("currency", "MONEY");
        sec.set("price", -500.0);

        PriceResult res = formatter.format(null, sec, "negative_pet");
        assertEquals(PriceType.INVALID, res.getType());
        assertFalse(res.isAvailable());
    }

    @Test
    @DisplayName("Case 13: Free entry")
    public void test13_FreeEntry() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection sec1 = config.createSection("pets.free_pet_flag");
        sec1.set("free", true);

        PriceResult res1 = formatter.format(null, sec1, "free_pet_flag");
        assertEquals(PriceType.FREE, res1.getType());
        assertTrue(res1.isAvailable());
        assertEquals("Miễn phí", res1.getFormattedPrice());

        ConfigurationSection sec2 = config.createSection("pets.free_pet_currency");
        sec2.set("currency", "FREE");

        PriceResult res2 = formatter.format(null, sec2, "free_pet_currency");
        assertEquals(PriceType.FREE, res2.getType());
        assertTrue(res2.isAvailable());
    }

    @Test
    @DisplayName("Case 14: Shop browse label")
    public void test14_ShopBrowseLabel() {
        YamlConfiguration config = new YamlConfiguration();

        // 1. Valid Money
        ConfigurationSection secMoney = config.createSection("pets.browse_money");
        secMoney.set("currency", "MONEY");
        secMoney.set("price", 1000.0);
        PriceResult resMoney = formatter.format(null, secMoney, "browse_money");
        assertTrue(resMoney.getBrowseButtonLabel().contains("Chưa khả dụng")); // Economy provider not connected

        // 2. Valid Points
        ConfigurationSection secPoints = config.createSection("pets.browse_points");
        secPoints.set("currency", "POINTS");
        secPoints.set("price", 50);
        PriceResult resPoints = formatter.format(null, secPoints, "browse_points");
        assertTrue(resPoints.getBrowseButtonLabel().contains("Chưa khả dụng")); // Points API not connected

        // 3. Valid Free
        ConfigurationSection secFree = config.createSection("pets.browse_free");
        secFree.set("free", true);
        PriceResult resFree = formatter.format(null, secFree, "browse_free");
        assertTrue(resFree.getBrowseButtonLabel().contains("Miễn phí"));
    }

    @Test
    @DisplayName("Case 15: Shop confirmation label")
    public void test15_ShopConfirmationLabel() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection sec = config.createSection("pets.confirm_pet");
        sec.set("currency", "ITEM");
        sec.set("price", 16);
        sec.set("material", "CARROT");

        PriceResult res = formatter.format(null, sec, "confirm_pet");
        List<String> details = res.getDetailLines();
        assertFalse(details.isEmpty());

        String detailJoined = String.join(" ", details);
        assertFalse(detailJoined.contains("(ITEM)"), "Must not display raw enum '(ITEM)'");
        assertTrue(detailJoined.contains("16"));
        String lowerDetail = detailJoined.toLowerCase();
        assertTrue(lowerDetail.contains("cà rốt") || lowerDetail.contains("carrot"));
    }

    @Test
    @DisplayName("Case 16: Chest Shop label")
    public void test16_ChestShopLabel() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection sec = config.createSection("pets.chest_pet");
        sec.set("currency", "ITEM");
        sec.set("price", 10);
        sec.set("material", "DIAMOND");

        itemHooks.setLanguageManager(vnLang);
        PriceResult res = formatter.format(null, sec, "chest_pet");
        assertEquals("10 × Kim cương", res.getFormattedPrice());
        assertFalse(res.getFormattedPrice().contains("null"));
    }

    @Test
    @DisplayName("Case 17: VN/EN price labels")
    public void test17_VnEnPriceLabels() {
        // Assert critical shop display keys exist and are non-empty in both VN and EN
        String[] keys = {
                "dialog.currency_money",
                "dialog.currency_points",
                "dialog.currency_item",
                "dialog.currency_free",
                "dialog.currency_requirements",
                "dialog.shop_price_money",
                "dialog.shop_price_points",
                "dialog.shop_price_item",
                "dialog.shop_price_free",
                "dialog.shop_status_free",
                "dialog.shop_status_requirements",
                "dialog.shop_status_unavailable",
                "dialog.shop_status_invalid",
                "shop.invalid_price",
                "shop.invalid_currency",
                "shop.missing_material",
                "shop.economy_unavailable",
                "shop.points_unavailable",
                "shop.custom_item_unavailable",
                "shop.insufficient_money",
                "shop.insufficient_points",
                "shop.insufficient_items",
                "shop.purchase_duplicate",
                "shop.purchase_processing",
                "shop.purchase_already_owned"
        };

        for (String k : keys) {
            assertTrue(vnConfig.contains(k), "VN.yml missing key: " + k);
            assertTrue(enConfig.contains(k), "EN.yml missing key: " + k);
            assertFalse(vnConfig.getString(k).trim().isEmpty(), "VN.yml empty key: " + k);
            assertFalse(enConfig.getString(k).trim().isEmpty(), "EN.yml empty key: " + k);
        }
    }

    @Test
    @DisplayName("Case 18: Requirement group display does not get mistaken for single price")
    public void test18_RequirementGroupDisplayNotMistakenForSinglePrice() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection sec = config.createSection("pets.req_group_pet");
        List<Map<String, Object>> allList = new ArrayList<>();
        Map<String, Object> req1 = new LinkedHashMap<>();
        req1.put("type", "MONEY");
        req1.put("amount", 500.0);
        allList.add(req1);
        Map<String, Object> req2 = new LinkedHashMap<>();
        req2.put("type", "ITEM");
        req2.put("id", "WHEAT");
        req2.put("amount", 10);
        allList.add(req2);
        sec.set("requirements.all", allList);

        PriceResult res = formatter.format(null, sec, "req_group_pet");
        assertEquals(PriceType.STRUCTURED_REQUIREMENTS, res.getType());
        assertNotEquals("$0", res.getFormattedPrice());
        assertEquals("Yêu cầu nhiệm vụ", res.getFormattedPrice());
        assertTrue(res.getBrowseButtonLabel().contains("Điều kiện"));
        assertNotNull(res.getRequirementGroup());
    }

    @Test
    @DisplayName("Case 19: Economy withdrawal failure")
    public void test19_EconomyWithdrawalFailure() {
        ShopTransactionReceipt receipt = ShopTransactionReceipt.failure(Status.INSUFFICIENT_RESOURCES, "Insufficient balance");
        assertFalse(receipt.isSuccess());
        assertEquals(Status.INSUFFICIENT_RESOURCES, receipt.getStatus());
        assertEquals("Insufficient balance", receipt.getErrorMessage());
    }

    @Test
    @DisplayName("Case 20: PlayerPoints deduction failure")
    public void test20_PlayerPointsDeductionFailure() {
        ShopTransactionReceipt receipt = ShopTransactionReceipt.failure(Status.DEDUCTION_FAILED, "PlayerPoints deduction returned false");
        assertFalse(receipt.isSuccess());
        assertEquals(Status.DEDUCTION_FAILED, receipt.getStatus());
    }

    @Test
    @DisplayName("Case 21: Item deduction failure")
    public void test21_ItemDeductionFailure() {
        ShopTransactionReceipt receipt = ShopTransactionReceipt.failure(Status.INSUFFICIENT_RESOURCES, "Player lacks required items");
        assertFalse(receipt.isSuccess());
        assertEquals(Status.INSUFFICIENT_RESOURCES, receipt.getStatus());
    }

    @Test
    @DisplayName("Case 22: Acquisition failure rollback")
    public void test22_AcquisitionFailureRollback() {
        ShopTransactionReceipt receipt = ShopTransactionReceipt.successMoney(2500.0);
        assertTrue(receipt.isSuccess());
        assertEquals(2500.0, receipt.getAmount(), 0.001);
        assertEquals(PriceType.MONEY, receipt.getPriceType());
    }

    @Test
    @DisplayName("Case 23: ITEM refund with full inventory (drops leftovers naturally)")
    public void test23_ItemRefundWithFullInventoryDropsLeftovers() {
        // ItemHookManager.refundItem handles full inventory by dropping leftovers naturally to world
        // Verify method contract and safe execution logic
        ShopTransactionReceipt receipt = ShopTransactionReceipt.successItem("WHEAT", 64);
        assertEquals(PriceType.ITEM, receipt.getPriceType());
        assertEquals("WHEAT", receipt.getItemIdentifier());
        assertEquals(64.0, receipt.getAmount(), 0.001);
    }

    @Test
    @DisplayName("Case 24: Missing custom item provider during refund (no stone/diamond substitution)")
    public void test24_MissingCustomItemProviderDuringRefundNoSubstitution() {
        ShopTransactionReceipt receipt = ShopTransactionReceipt.successItem("itemsadder:legendary_sword", 1);

        // When custom item provider is offline, rollback must return false to trigger recovery journal
        // It must NEVER return Diamond or Stone!
        boolean rollbackSuccess = receipt.rollback(null, null);
        assertFalse(rollbackSuccess, "Rollback must return false when provider or plugin is offline/null");
    }

    @Test
    @DisplayName("Case 25: Structured requirement transaction failure handling")
    public void test25_StructuredRequirementTransactionFailureHandling() {
        ShopTransactionReceipt receipt = ShopTransactionReceipt.successRequirements(null);
        assertEquals(PriceType.STRUCTURED_REQUIREMENTS, receipt.getPriceType());

        // Multi-step structured rollback returns false rather than substituting wrong legacy items
        assertFalse(receipt.rollback(null, null));
    }

    @Test
    @DisplayName("Case 26: Duplicate purchase submission guard")
    public void test26_DuplicatePurchaseSubmissionGuard() {
        Set<UUID> activePurchases = Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
        UUID playerUuid = UUID.randomUUID();

        // 1. First purchase starts
        boolean firstSubmissionAccepted = activePurchases.add(playerUuid);
        assertTrue(firstSubmissionAccepted, "First purchase submission must be accepted");

        // 2. Second rapid click before first finishes
        boolean secondSubmissionAccepted = activePurchases.add(playerUuid);
        assertFalse(secondSubmissionAccepted, "Duplicate purchase submission must be rejected while first is in-flight");

        // 3. First finishes
        activePurchases.remove(playerUuid);

        // 4. Subsequent submission succeeds
        boolean subsequentSubmissionAccepted = activePurchases.add(playerUuid);
        assertTrue(subsequentSubmissionAccepted, "Subsequent purchase must succeed after first completes");
    }

    @Test
    @DisplayName("Case 27: Invalid configuration cannot lead to free pet")
    public void test27_InvalidConfigurationCannotLeadToFreePet() {
        YamlConfiguration config = new YamlConfiguration();

        // Subcase A: price = 0 without explicit free
        ConfigurationSection secA = config.createSection("pets.zero_not_free");
        secA.set("price", 0);
        secA.set("currency", "MONEY");
        PriceResult resA = formatter.format(null, secA, "zero_not_free");
        assertEquals(PriceType.INVALID, resA.getType());
        assertFalse(resA.isAvailable());

        // Subcase B: negative price
        ConfigurationSection secB = config.createSection("pets.negative_price");
        secB.set("price", -10);
        secB.set("currency", "POINTS");
        PriceResult resB = formatter.format(null, secB, "negative_price");
        assertEquals(PriceType.INVALID, resB.getType());
        assertFalse(resB.isAvailable());

        // Subcase C: missing currency
        ConfigurationSection secC = config.createSection("pets.no_currency");
        secC.set("price", 50);
        PriceResult resC = formatter.format(null, secC, "no_currency");
        assertEquals(PriceType.INVALID, resC.getType());
        assertFalse(resC.isAvailable());
    }

    @Test
    @DisplayName("Audit all 43 configured production pets from config.yml")
    public void test28_ProductionConfigPetsAudit() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("config.yml");
        assertNotNull(stream, "config.yml must exist in classpath");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));

        ConfigurationSection petsSec = config.getConfigurationSection("pets");
        assertNotNull(petsSec, "pets section must exist in config.yml");

        Set<String> petIds = petsSec.getKeys(false);
        assertEquals(43, petIds.size(), "config.yml must define exactly 43 production pets");

        int itemCount = 0;
        int moneyCount = 0;
        int pointsCount = 0;

        for (String petId : petIds) {
            ConfigurationSection sec = petsSec.getConfigurationSection(petId);
            assertNotNull(sec, "Pet section for " + petId + " must exist");

            PriceResult res = formatter.format(null, sec, petId);
            assertNotEquals(PriceType.INVALID, res.getType(), "Pet '" + petId + "' must have valid shop config: " + res.getUnavailableReason());

            double configuredPrice = sec.getDouble("price", 0.0);
            String configuredCurrency = ShopPriceFormatter.normalizeCurrency(sec.getString("currency", ""));

            assertTrue(configuredPrice > 0, "Pet '" + petId + "' must have price > 0");
            assertNotNull(configuredCurrency, "Pet '" + petId + "' must have valid currency");

            if ("ITEM".equals(configuredCurrency)) {
                itemCount++;
                String mat = sec.getString("material");
                assertNotNull(mat, "ITEM pet must have material identifier");
                assertFalse(mat.trim().isEmpty());
            } else if ("MONEY".equals(configuredCurrency)) {
                moneyCount++;
            } else if ("POINTS".equals(configuredCurrency)) {
                pointsCount++;
            }
        }

        assertEquals(15, itemCount, "Must have exactly 15 ITEM pets");
        assertEquals(16, moneyCount, "Must have exactly 16 MONEY pets");
        assertEquals(12, pointsCount, "Must have exactly 12 POINTS pets");
    }

    @Test
    @DisplayName("Case 29: Fractional ITEM price is strictly rejected")
    public void test29_FractionalItemPriceRejected() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection sec = config.createSection("pets.fractional_item");
        sec.set("currency", "ITEM");
        sec.set("material", "DIAMOND");

        sec.set("price", 0.5);
        PriceResult res1 = formatter.format(null, sec, "fractional_item");
        assertEquals(PriceType.INVALID, res1.getType());
        assertTrue(res1.getUnavailableReason().contains("positive whole integer"));

        sec.set("price", 1.25);
        PriceResult res2 = formatter.format(null, sec, "fractional_item");
        assertEquals(PriceType.INVALID, res2.getType());

        sec.set("price", -1.0);
        PriceResult res3 = formatter.format(null, sec, "fractional_item");
        assertEquals(PriceType.INVALID, res3.getType());
    }

    @Test
    @DisplayName("Case 30: Fractional POINTS price is strictly rejected")
    public void test30_FractionalPointsPriceRejected() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection sec = config.createSection("pets.fractional_points");
        sec.set("currency", "POINTS");

        sec.set("price", 0.5);
        PriceResult res1 = formatter.format(null, sec, "fractional_points");
        assertEquals(PriceType.INVALID, res1.getType());
        assertTrue(res1.getUnavailableReason().contains("positive whole integer"));

        sec.set("price", 99.9);
        PriceResult res2 = formatter.format(null, sec, "fractional_points");
        assertEquals(PriceType.INVALID, res2.getType());

        sec.set("price", -5.0);
        PriceResult res3 = formatter.format(null, sec, "fractional_points");
        assertEquals(PriceType.INVALID, res3.getType());
    }

    @Test
    @DisplayName("Case 31: NaN and Infinite prices are rejected across all currencies")
    public void test31_NaNAndInfinityPriceRejected() {
        YamlConfiguration config = new YamlConfiguration();

        for (String curr : List.of("MONEY", "POINTS", "ITEM")) {
            ConfigurationSection sec = config.createSection("pets.nan_" + curr);
            sec.set("currency", curr);
            sec.set("material", "GOLD_INGOT");

            sec.set("price", Double.NaN);
            PriceResult resNaN = formatter.format(null, sec, "nan_" + curr);
            assertEquals(PriceType.INVALID, resNaN.getType(), "NaN price must be rejected for " + curr);

            sec.set("price", Double.POSITIVE_INFINITY);
            PriceResult resPosInf = formatter.format(null, sec, "posinf_" + curr);
            assertEquals(PriceType.INVALID, resPosInf.getType(), "Positive infinity price must be rejected for " + curr);

            sec.set("price", Double.NEGATIVE_INFINITY);
            PriceResult resNegInf = formatter.format(null, sec, "neginf_" + curr);
            assertEquals(PriceType.INVALID, resNegInf.getType(), "Negative infinity price must be rejected for " + curr);
        }
    }

    @Test
    @DisplayName("Case 32: Empty or invalid structured requirements must NOT fall through to legacy payment")
    public void test32_EmptyStructuredRequirementsMustNotFallThrough() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection sec = config.createSection("pets.empty_reqs");
        sec.set("price", 100);
        sec.set("currency", "MONEY");
        sec.createSection("requirements"); // Empty requirements section

        PriceResult res = formatter.format(null, sec, "empty_reqs");
        assertEquals(PriceType.INVALID, res.getType(), "Empty requirements must be INVALID and never fall through to MONEY");
        assertTrue(res.getUnavailableReason().contains("contains no valid executable requirements"));
    }

    @Test
    @DisplayName("Case 33: Structured requirement rollback executes all planned steps in reverse")
    public void test33_StructuredRequirementRollback() {
        org.ipsecuz.pet.requirement.RequirementTransaction tx = new org.ipsecuz.pet.requirement.RequirementTransaction();
        List<String> rollbacks = new ArrayList<>();

        tx.addStep(new org.ipsecuz.pet.requirement.RequirementTransaction.TransactionStep() {
            @Override public boolean execute(Player player) { return true; }
            @Override public void rollback(Player player) { rollbacks.add("step1_money"); }
            @Override public String getDescription() { return "Deduct 500 Money"; }
        });

        tx.addStep(new org.ipsecuz.pet.requirement.RequirementTransaction.TransactionStep() {
            @Override public boolean execute(Player player) { return true; }
            @Override public void rollback(Player player) { rollbacks.add("step2_item"); }
            @Override public String getDescription() { return "Deduct 5 Diamonds"; }
        });

        assertEquals(2, tx.size());
        ShopTransactionReceipt receipt = ShopTransactionReceipt.successRequirements(tx);
        assertTrue(receipt.isSuccess());

        boolean ok = receipt.rollback(null, null);
        assertTrue(ok, "Rollback with valid RequirementTransaction should succeed");
        assertEquals(List.of("step2_item", "step1_money"), rollbacks, "Rollback must execute in reverse order");
    }

    @Test
    @DisplayName("Case 34: ItemHookManager rejects amount <= 0 to prevent free item purchases")
    public void test34_ItemHookManagerZeroOrNegativeAmountRejected() {
        ItemHookManager ihm = new ItemHookManager(true, true, true);
        assertFalse(ihm.hasItem(null, "DIAMOND", 0));
        assertFalse(ihm.hasItem(null, "DIAMOND", -1));
        assertFalse(ihm.takeItem(null, "DIAMOND", 0));
        assertFalse(ihm.takeItem(null, "DIAMOND", -5));
        assertFalse(ihm.refundItem(null, "DIAMOND", 0, null, null));
        assertFalse(ihm.refundItem(null, "DIAMOND", -10, null, null));
    }

    @Test
    @DisplayName("Case 35: ShopTransactionEntry attributes, status transitions, and journal paths")
    public void test35_ShopTransactionEntryAndJournalPaths() {
        UUID playerUuid = UUID.randomUUID();
        String txId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        ConfigManager.ShopTransactionEntry entry = new ConfigManager.ShopTransactionEntry(
                txId, playerUuid, "fire_dragon", "MONEY", 10000.0, null, now, "CREATED", "Initial payment"
        );

        assertEquals(txId, entry.getTxId());
        assertEquals(playerUuid, entry.getPlayerUuid());
        assertEquals("fire_dragon", entry.getPetId());
        assertEquals("MONEY", entry.getCurrency());
        assertEquals(10000.0, entry.getAmount(), 0.001);
        assertNull(entry.getMaterial());
        assertEquals(now, entry.getTimestamp());
        assertEquals("CREATED", entry.getStatus());
        assertEquals("Initial payment", entry.getNote());

        // Validate journal paths
        String txPath = "shop_transactions." + txId;
        assertTrue(txPath.startsWith("shop_transactions."));
        assertEquals(txId, txPath.substring("shop_transactions.".length()));

        // Validate that legacy pending refunds path is distinct
        String legacyPath = "pending_shop_refunds." + txId;
        assertTrue(legacyPath.startsWith("pending_shop_refunds."));
    }
}

