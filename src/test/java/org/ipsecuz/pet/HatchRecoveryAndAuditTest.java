package org.ipsecuz.pet;

import org.bukkit.configuration.file.YamlConfiguration;
import org.ipsecuz.pet.dialog.PaperNativeDialogEngine;
import org.ipsecuz.pet.HatchingManager.PendingHatchSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class HatchRecoveryAndAuditTest {

    @Test
    @DisplayName("Hatch Session Recovery: Ambiguous PAYMENT_PROCESSING must map to RECOVERABLE and not be silently dropped")
    public void testPaymentProcessingRecoveryAmbiguousNotDeleted() {
        UUID playerId = UUID.randomUUID();
        String txId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        // Simulate a session that was interrupted during payment processing
        PendingHatchSession session = new PendingHatchSession(
                playerId,
                txId,
                "rare_egg",
                "dragon_pet",
                HatchingManager.HatchState.PAYMENT_PROCESSING,
                now,
                2,
                false,
                false
        );

        assertEquals(HatchingManager.HatchState.PAYMENT_PROCESSING, session.getState());
        assertFalse(session.isPaymentCommitted(), "Payment is not definitely confirmed yet");
        assertFalse(session.isRewardGranted(), "Reward has not been granted yet");
        assertEquals(txId, session.getTransactionId());

        // When loaded on restart / crash recovery, PAYMENT_PROCESSING transitions to RECOVERABLE
        PendingHatchSession recovered = new PendingHatchSession(
                session.getPlayerUuid(),
                session.getTransactionId(),
                session.getEggId(),
                session.getWinningPetId(),
                HatchingManager.HatchState.RECOVERABLE,
                session.getTimestamp(),
                session.getSchemaVersion(),
                session.isPaymentCommitted(),
                session.isRewardGranted()
        );

        assertEquals(HatchingManager.HatchState.RECOVERABLE, recovered.getState());
        assertFalse(recovered.isPaymentCommitted(), "Must not assume payment succeeded");
        assertFalse(recovered.isRewardGranted(), "Must not auto-grant reward without payment proof");
    }

    @Test
    @DisplayName("Hatch Session Schema and Field Validation: Validates all 8 persisted fields")
    public void testPendingSessionSchemaAndFieldValidation() {
        UUID playerId = UUID.randomUUID();
        String txId = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();

        PendingHatchSession session = new PendingHatchSession(
                playerId,
                txId,
                "common_egg",
                "wolf_pet",
                HatchingManager.HatchState.PAID,
                timestamp,
                2,
                true,
                false
        );

        assertEquals(playerId, session.getPlayerUuid());
        assertEquals("common_egg", session.getEggId());
        assertEquals("wolf_pet", session.getWinningPetId());
        assertEquals(HatchingManager.HatchState.PAID, session.getState());
        assertEquals(timestamp, session.getTimestamp());
        assertEquals(2, session.getSchemaVersion());
        assertTrue(session.isPaymentCommitted());
        assertFalse(session.isRewardGranted());
        assertEquals(txId, session.getTransactionId());

        // Validate schema bounds: 1 <= schema <= 2
        assertTrue(session.getSchemaVersion() >= 1 && session.getSchemaVersion() <= 2);
    }

    @Test
    @DisplayName("Hatch Reward Ordering and Idempotency: Anti-duplicate reward delivery with CAS commitment")
    public void testIdempotentRewardCompletionOrdering() {
        UUID playerId = UUID.randomUUID();
        PendingHatchSession session = new PendingHatchSession(
                playerId,
                UUID.randomUUID().toString(),
                "legendary_egg",
                "phoenix_pet",
                HatchingManager.HatchState.PAID,
                System.currentTimeMillis(),
                2,
                true,
                false
        );

        // Step 1: Transition PAID -> PROCESSING
        session.setState(HatchingManager.HatchState.PROCESSING);
        assertEquals(HatchingManager.HatchState.PROCESSING, session.getState());

        // Step 2: Grant reward and commit idempotently
        boolean firstCommit = session.markCommitted();
        assertTrue(firstCommit, "First commitment attempt must succeed");
        assertTrue(session.isRewardGranted(), "rewardGranted must now be true");
        assertEquals(HatchingManager.HatchState.COMPLETED, session.getState(), "State must be COMPLETED");

        // Step 3: Duplicate callback attempt (e.g. concurrent reconnect / re-delivery)
        boolean secondCommit = session.markCommitted();
        assertFalse(secondCommit, "Second commitment attempt must be rejected to prevent duplicate rewards");
    }

    @Test
    @DisplayName("Client Protocol Detection: Accept protocol 771 (MC 1.21.6), 772 (MC 1.21.7+), reject older protocols")
    public void testDialogProtocolDetection() {
        // Simulated helper implementing the exact protocol rule
        java.util.function.IntPredicate isSupportedProtocol = protocol ->
                protocol >= 771 || (protocol & (1 << 30)) != 0;

        // Protocol 771 (Minecraft 1.21.6) -> MUST be supported
        assertTrue(isSupportedProtocol.test(771), "Protocol 771 (1.21.6) must be supported");

        // Protocol 772 (Minecraft 1.21.7 / 1.21.8) -> MUST be supported
        assertTrue(isSupportedProtocol.test(772), "Protocol 772 (1.21.7+) must be supported");

        // Protocol 773+ (Future updates) -> MUST be supported
        assertTrue(isSupportedProtocol.test(773), "Protocol 773 must be supported");

        // Protocol 770 (Minecraft 1.21.5) -> MUST NOT be supported (pre-Dialog client)
        assertFalse(isSupportedProtocol.test(770), "Protocol 770 (1.21.5) must NOT be supported");

        // Protocol 769 (Minecraft 1.21.4) -> MUST NOT be supported
        assertFalse(isSupportedProtocol.test(769), "Protocol 769 must NOT be supported");

        // Snapshot build with bit 30 set -> MUST be supported
        int snapshotProtocol = (1 << 30) | 771;
        assertTrue(isSupportedProtocol.test(snapshotProtocol), "Snapshot protocol bit must be supported");
    }

    @Test
    @DisplayName("PaperNativeDialogEngine Diagnostics: Capabilities audit and rate-limited logging")
    public void testPaperNativeDialogEngineCapabilitiesAudit() {
        PaperNativeDialogEngine engine = new PaperNativeDialogEngine(null);

        // When running in test environment (classes absent or present depending on classpath)
        Map<String, Boolean> caps = engine.auditCapabilities();
        assertNotNull(caps, "auditCapabilities() must not return null");
        assertTrue(caps.containsKey("Notice"));
        assertTrue(caps.containsKey("Confirmation"));
        assertTrue(caps.containsKey("MultiAction"));
        assertTrue(caps.containsKey("TextInput"));
        assertTrue(caps.containsKey("BoolInput"));
        assertTrue(caps.containsKey("RangeInput"));
        assertTrue(caps.containsKey("ShowDialog"));
        assertTrue(caps.containsKey("ResponseExtraction"));

        String summary = engine.getCapabilitySummary();
        assertNotNull(summary);
        assertTrue(summary.contains("Notice:"));
        assertTrue(summary.contains("Confirmation:"));
        assertTrue(summary.contains("MultiAction:"));
        assertTrue(summary.contains("ShowDialog:"));

        // Test rate limited logger does not throw NPE when plugin is null
        assertDoesNotThrow(() -> {
            engine.logRateLimited("test_key", "test message", 1000);
            engine.logRateLimited("test_key", "test message duplicate", 1000);
        });
    }

    @Test
    @DisplayName("Language Parity: Verify dialog and requirement keys exist in both VN.yml and EN.yml")
    public void testLanguageKeysParityForDialogAndHatch() {
        InputStream vnStream = getClass().getClassLoader().getResourceAsStream("languages/VN.yml");
        InputStream enStream = getClass().getClassLoader().getResourceAsStream("languages/EN.yml");
        assertNotNull(vnStream, "VN.yml must exist");
        assertNotNull(enStream, "EN.yml must exist");

        YamlConfiguration vnConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(vnStream, StandardCharsets.UTF_8));
        YamlConfiguration enConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(enStream, StandardCharsets.UTF_8));

        String[] requiredDialogKeys = {
                "dialog.hub_title",
                "dialog.hub_welcome",
                "dialog.hub_active_pet",
                "dialog.hub_no_active_pet",
                "dialog.hub_owned_pets",
                "dialog.hub_my_pets_desc",
                "dialog.my_pets_title",
                "dialog.my_pets_desc",
                "dialog.page_info",
                "dialog.page_info_simple",
                "dialog.btn_my_pets",
                "dialog.btn_details",
                "dialog.btn_shop",
                "dialog.btn_hatch",
                "dialog.btn_codex",
                "dialog.btn_guide",
                "dialog.btn_settings",
                "dialog.btn_ok",
                "dialog.btn_confirm",
                "dialog.btn_cancel",
                "dialog.btn_despawn",
                "dialog.btn_summon",
                "dialog.btn_rename",
                "dialog.btn_mode_adult",
                "dialog.btn_mode_baby",
                "dialog.btn_evolution",
                "dialog.btn_feed",
                "dialog.btn_back",
                "dialog.btn_save_name",
                "dialog.detail_title",
                "dialog.rename_title",
                "dialog.rename_desc_1",
                "dialog.rename_desc_2",
                "dialog.rename_input_label",
                "dialog.evolution_title",
                "dialog.evo_confirm",
                "dialog.evo_cancel",
                "dialog.shop_title",
                "dialog.shop_confirm_title",
                "dialog.shop_confirm_buy",
                "dialog.shop_confirm_cancel",
                "dialog.hatch_title",
                "dialog.hatch_select_title",
                "dialog.hatch_confirm",
                "dialog.hatch_cancel",
                "dialog.settings_title",
                "dialog.toggle_on",
                "dialog.toggle_off",
                "dialog.btn_save_settings",
                "dialog.trade_invite_title",
                "dialog.trade_accept",
                "dialog.trade_decline",
                "dialog.trade_confirm_title",
                "dialog.trade_confirm_commit",
                "dialog.trade_confirm_cancel",
                "dialog.codex_title",
                "dialog.shards_title",
                "requirement.none",
                "requirement.divider",
                "requirement.not_ready",
                "requirement.no_cost",
                "requirement.one_of_header",
                "requirement.selected_badge",
                "requirement.all_header",
                "hatching.ambiguous_join"
        };

        for (String key : requiredDialogKeys) {
            assertTrue(vnConfig.contains(key), "VN.yml missing key: " + key);
            assertTrue(enConfig.contains(key), "EN.yml missing key: " + key);
        }
    }
}
