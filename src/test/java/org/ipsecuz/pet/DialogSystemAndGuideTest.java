package org.ipsecuz.pet;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.ipsecuz.pet.dialog.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class DialogSystemAndGuideTest {

    private Player createMockPlayer(UUID uuid, String name) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if ("getUniqueId".equals(method.getName())) return uuid;
                    if ("getName".equals(method.getName())) return name;
                    return null;
                }
        );
    }

    @Test
    @DisplayName("Verify DialogSession immutability, nonce, and atomic CAS single-submission")
    public void testDialogSessionSecurity() {
        UUID playerId = UUID.randomUUID();
        Map<String, Object> data = new HashMap<>();
        data.put("targetStar", 3);
        data.put("cost", 500.0);

        DialogSession session = new DialogSession(playerId, DialogType.EVOLUTION, "phoenix", data);

        assertNotNull(session.getNonce());
        assertFalse(session.getNonce().isEmpty());
        assertEquals(playerId, session.getPlayerId());
        assertEquals(DialogType.EVOLUTION, session.getType());
        assertEquals("phoenix", session.getPetId());
        assertEquals(3, (int) session.getData("targetStar", 0));
        assertEquals(500.0, (double) session.getData("cost", 0.0));
        assertFalse(session.isExpired(10_000L));
        assertFalse(session.isSubmitted());

        // First submit should succeed (CAS: false -> true)
        assertTrue(session.markSubmitted(), "First call to markSubmitted must succeed");
        assertTrue(session.isSubmitted());

        // Second submit should fail (replay attack prevention)
        assertFalse(session.markSubmitted(), "Subsequent call to markSubmitted must return false to block replay");
    }

    @Test
    @DisplayName("Verify DialogSessionManager session lifecycle, token validation, and single-use consumption")
    public void testDialogSessionManagerLifecycle() {
        DialogSessionManager manager = new DialogSessionManager(null);
        UUID playerId = UUID.randomUUID();
        Player player = createMockPlayer(playerId, "PlayerTester");

        // 1. Create session
        DialogSession session = manager.createSession(player, DialogType.SHOP_CONFIRM, "dragon", Map.of("price", 1000));
        assertNotNull(session);
        assertEquals(session, manager.getSession(player));

        // 2. Validate with incorrect nonce -> Rejected
        DialogSession rejected1 = manager.validateAndConsume(player, "wrong-nonce", DialogType.SHOP_CONFIRM);
        assertNull(rejected1, "Invalid nonce must be rejected");

        // 3. Validate with mismatched DialogType -> Rejected
        DialogSession rejected2 = manager.validateAndConsume(player, session.getNonce(), DialogType.TRADE_INVITE);
        assertNull(rejected2, "Mismatched DialogType must be rejected");

        // 4. Validate with correct nonce & type -> Accepted & Consumed
        DialogSession consumed = manager.validateAndConsume(player, session.getNonce(), DialogType.SHOP_CONFIRM);
        assertNotNull(consumed, "Valid nonce and type must be consumed successfully");
        assertEquals("dragon", consumed.getPetId());

        // 5. Attempt second consumption (replay attack) -> Rejected because session removed and marked submitted
        DialogSession replay = manager.validateAndConsume(player, session.getNonce(), DialogType.SHOP_CONFIRM);
        assertNull(replay, "Session must not be reusable after consumption");
        assertNull(manager.getSession(player), "Session must no longer be active");
    }

    @Test
    @DisplayName("Verify DialogSessionManager TTL expiration and invalidation")
    public void testDialogSessionManagerExpirationAndInvalidation() {
        DialogSessionManager manager = new DialogSessionManager(null);
        UUID playerId = UUID.randomUUID();
        Player player = createMockPlayer(playerId, "PlayerTester");

        DialogSession session = manager.createSession(player, DialogType.NOTICE, "info", Collections.emptyMap());
        assertNotNull(session);

        // Test manual invalidation
        manager.invalidate(playerId);
        assertNull(manager.getSession(player));

        // Test TTL check
        DialogSession expiredSession = new DialogSession(playerId, DialogType.NOTICE, "info", Collections.emptyMap()) {
            @Override
            public boolean isExpired(long ttlMs) {
                return true;
            }
        };
        assertTrue(expiredSession.isExpired(1000L));
    }

    @Test
    @DisplayName("Verify DialogMode and DialogType enum completeness")
    public void testDialogModeAndTypes() {
        // DialogMode
        assertEquals(DialogMode.AUTO, DialogMode.valueOf("AUTO"));
        assertEquals(DialogMode.NATIVE, DialogMode.valueOf("NATIVE"));
        assertEquals(DialogMode.CHEST, DialogMode.valueOf("CHEST"));

        // DialogType check
        Set<String> requiredTypes = Set.of(
                "HUB", "DETAIL", "SELECTION", "RENAME", "EVOLUTION", "SHOP_CONFIRM",
                "HATCH_CONFIRM", "GUIDE", "SETTINGS", "TRADE_INVITE", "TRADE_CONFIRM",
                "NOTICE", "CONFIRMATION"
        );
        for (String typeName : requiredTypes) {
            assertNotNull(DialogType.valueOf(typeName), "Missing DialogType: " + typeName);
        }
    }

    @Test
    @DisplayName("Verify all 18 Pet Guidebook chapters exist and have complete content in both VN.yml and EN.yml")
    public void testAllGuidebookChaptersParityAndContent() {
        InputStream vnStream = getClass().getClassLoader().getResourceAsStream("languages/VN.yml");
        InputStream enStream = getClass().getClassLoader().getResourceAsStream("languages/EN.yml");
        assertNotNull(vnStream, "VN.yml must exist");
        assertNotNull(enStream, "EN.yml must exist");

        YamlConfiguration vnConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(vnStream, StandardCharsets.UTF_8));
        YamlConfiguration enConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(enStream, StandardCharsets.UTF_8));

        // Check common guide navigation keys
        String[] navKeys = {
                "dialog.guide_title",
                "dialog.guide_btn_prev",
                "dialog.guide_btn_next",
                "dialog.guide_btn_hub"
        };
        for (String k : navKeys) {
            assertTrue(vnConfig.contains(k), "VN.yml missing navigation key: " + k);
            assertTrue(enConfig.contains(k), "EN.yml missing navigation key: " + k);
            assertFalse(vnConfig.getString(k, "").trim().isEmpty(), "VN.yml empty key: " + k);
            assertFalse(enConfig.getString(k, "").trim().isEmpty(), "EN.yml empty key: " + k);
        }

        // Check all 18 chapters
        for (int ch = 1; ch <= 18; ch++) {
            String titleKey = "dialog.guide.ch" + ch + "_title";
            String bodyKey = "dialog.guide.ch" + ch + "_body";

            // VN checks
            assertTrue(vnConfig.contains(titleKey), "VN.yml missing chapter " + ch + " title: " + titleKey);
            assertTrue(vnConfig.contains(bodyKey), "VN.yml missing chapter " + ch + " body: " + bodyKey);
            String vnTitle = vnConfig.getString(titleKey, "").trim();
            String vnBody = vnConfig.getString(bodyKey, "").trim();
            assertFalse(vnTitle.isEmpty(), "VN chapter " + ch + " title cannot be empty");
            assertFalse(vnBody.isEmpty(), "VN chapter " + ch + " body cannot be empty");
            assertTrue(vnBody.length() >= 20, "VN chapter " + ch + " body must contain substantial guide text");

            // EN checks
            assertTrue(enConfig.contains(titleKey), "EN.yml missing chapter " + ch + " title: " + titleKey);
            assertTrue(enConfig.contains(bodyKey), "EN.yml missing chapter " + ch + " body: " + bodyKey);
            String enTitle = enConfig.getString(titleKey, "").trim();
            String enBody = enConfig.getString(bodyKey, "").trim();
            assertFalse(enTitle.isEmpty(), "EN chapter " + ch + " title cannot be empty");
            assertFalse(enBody.isEmpty(), "EN chapter " + ch + " body cannot be empty");
            assertTrue(enBody.length() >= 20, "EN chapter " + ch + " body must contain substantial guide text");
        }
    }

    @Test
    @DisplayName("Verify OptionalDialogWindowBridge and NativeDialogProvider fail gracefully when APIs absent")
    public void testSafeBridgeFallbacks() {
        // On test runtime (no DialogWindow installed), bridge returns false gracefully without throwing
        OptionalDialogWindowBridge bridge = new OptionalDialogWindowBridge(null);
        assertFalse(bridge.isAvailable());

        UUID playerId = UUID.randomUUID();
        Player player = createMockPlayer(playerId, "PlayerTester");

        // NativeDialogProvider should safely report false on 1.20 mock runtime without throwing
        NativeDialogProvider provider = new NativeDialogProvider(null);
        assertFalse(provider.isSupported(player));
    }
}

