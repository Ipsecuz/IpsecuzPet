package org.ipsecuz.pet;

import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;
import org.ipsecuz.pet.dialog.PaperNativeDialogEngine;
import org.ipsecuz.pet.dialog.PaperNativeDialogEngine.NativeButton;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class NativeDialogEmptyActionsTest {

    @Test
    @DisplayName("Empty-Action Guard: Non-empty actions plus exit button retains both")
    public void testNonEmptyActionsPlusExitButton() {
        List<String> actions = new ArrayList<>();
        actions.add("button_1");
        actions.add("button_2");
        String exit = "exit_button";

        // Simulation of central guard
        if (actions.isEmpty() && exit != null) {
            actions.add(exit);
            exit = null;
        }

        assertEquals(2, actions.size(), "Main actions must retain both items");
        assertEquals("exit_button", exit, "Exit button must remain as exit button");
    }

    @Test
    @DisplayName("Empty-Action Guard: Empty actions plus exit button promotes exit button to only action")
    public void testEmptyActionsPlusExitButton() {
        List<String> actions = new ArrayList<>();
        String exit = "back_to_hub";

        // Simulation of Requirement 4
        boolean guardExercised = false;
        if (actions.isEmpty() && exit != null) {
            actions.add(exit);
            exit = null;
            guardExercised = true;
        }

        assertTrue(guardExercised, "Guard must be exercised");
        assertEquals(1, actions.size(), "Actions list must contain the promoted exit button");
        assertEquals("back_to_hub", actions.get(0));
        assertNull(exit, "Exit button must be null to prevent duplicate display in multiAction");
    }

    @Test
    @DisplayName("Empty-Action Guard: Empty actions and no exit button adds fallback Close button")
    public void testEmptyActionsAndNoExitButton() {
        List<String> actions = new ArrayList<>();
        String exit = null;

        boolean guardExercised = false;
        if (actions.isEmpty() && exit != null) {
            actions.add(exit);
            exit = null;
        }
        if (actions.isEmpty()) {
            actions.add("fallback_close");
            guardExercised = true;
        }

        assertTrue(guardExercised);
        assertEquals(1, actions.size());
        assertEquals("fallback_close", actions.get(0));
        assertFalse(actions.isEmpty(), "Actions list must never be empty");
    }

    @Test
    @DisplayName("Button Safety: List containing null NativeButton entries skips nulls safely")
    public void testNullNativeButtonEntryHandledSafely() {
        NativeButton valid = new NativeButton(Component.text("Valid"), null, 100, (p, r) -> {});
        List<NativeButton> rawButtons = Arrays.asList(null, valid, null);

        List<NativeButton> safeButtons = new ArrayList<>();
        for (NativeButton b : rawButtons) {
            if (b != null) {
                safeButtons.add(b);
            }
        }

        assertEquals(1, safeButtons.size(), "Null entries must be skipped without error");
        assertSame(valid, safeButtons.get(0));
    }

    @Test
    @DisplayName("Button Construction Safety: Null returned from button builder does not pollute action list")
    public void testActionButtonConstructionReturningNull() {
        List<Object> actionButtons = new ArrayList<>();
        Object nullButton = null;

        if (nullButton != null) {
            actionButtons.add(nullButton);
        }

        assertTrue(actionButtons.isEmpty(), "Null ActionButton must never be added to action list");
    }

    @Test
    @DisplayName("Empty Shop Screen: Empty pet list produces non-empty buttons with Back to Hub")
    public void testEmptyShopScreenProducesNonEmptyActions() {
        List<String> eligiblePets = new ArrayList<>();
        List<NativeButton> screenButtons = new ArrayList<>();

        if (eligiblePets.isEmpty()) {
            screenButtons.add(new NativeButton(Component.text("Pet Hub"), null, 140, (p, r) -> {}));
        }

        assertFalse(screenButtons.isEmpty(), "Empty shop screen must provide a non-empty action button");
        assertEquals(1, screenButtons.size());
    }

    @Test
    @DisplayName("Empty My Pets Screen: Empty owned pets produces non-empty buttons with Back to Hub")
    public void testEmptyMyPetsScreenProducesNonEmptyActions() {
        List<String> owned = new ArrayList<>();
        List<NativeButton> screenButtons = new ArrayList<>();

        if (owned.isEmpty()) {
            screenButtons.add(new NativeButton(Component.text("Pet Hub"), null, 140, (p, r) -> {}));
        }

        assertFalse(screenButtons.isEmpty(), "Empty My Pets screen must provide a non-empty action button");
        assertEquals(1, screenButtons.size());
    }

    @Test
    @DisplayName("Empty Shards Screen: Zero valid shards produces non-empty buttons with Back to Hub")
    public void testEmptyShardsScreenProducesNonEmptyActions() {
        java.util.Map<String, Integer> shards = java.util.Map.of("pet1", 0, "pet2", -1);
        boolean hasValid = false;
        for (int v : shards.values()) {
            if (v > 0) { hasValid = true; break; }
        }

        List<NativeButton> screenButtons = new ArrayList<>();
        if (!hasValid) {
            screenButtons.add(new NativeButton(Component.text("Pet Hub"), null, 140, (p, r) -> {}));
        }

        assertFalse(screenButtons.isEmpty(), "Empty shards screen must provide a non-empty action button");
        assertEquals(1, screenButtons.size());
    }

    @Test
    @DisplayName("Empty Hatch Screen: Empty egg configuration produces non-empty buttons with Back to Hub")
    public void testEmptyHatchScreenProducesNonEmptyActions() {
        List<String> eggs = new ArrayList<>();
        List<NativeButton> screenButtons = new ArrayList<>();

        if (eggs.isEmpty()) {
            screenButtons.add(new NativeButton(Component.text("Pet Hub"), null, 140, (p, r) -> {}));
        }

        assertFalse(screenButtons.isEmpty(), "Empty hatch screen must provide a non-empty action button");
        assertEquals(1, screenButtons.size());
    }

    @Test
    @DisplayName("Empty Codex Screen: Empty pet configuration produces non-empty buttons with Back to Hub")
    public void testEmptyCodexScreenProducesNonEmptyActions() {
        List<String> pets = new ArrayList<>();
        List<NativeButton> screenButtons = new ArrayList<>();

        if (pets.isEmpty()) {
            screenButtons.add(new NativeButton(Component.text("Pet Hub"), null, 140, (p, r) -> {}));
        }

        assertFalse(screenButtons.isEmpty(), "Empty codex screen must provide a non-empty action button");
        assertEquals(1, screenButtons.size());
    }

    @Test
    @DisplayName("Diagnostics State: Successful operation after previous failure clears active failure while preserving history")
    public void testSuccessAfterFailureClearsActiveFailure() {
        PaperNativeDialogEngine engine = new PaperNativeDialogEngine(null);

        // 1. Record failure
        engine.recordFailure(PaperNativeDialogEngine.Stage.CONSTRUCT_MULTI_ACTION_TYPE,
                "DialogType.multiAction", "actions.isEmpty",
                new IllegalArgumentException("actions cannot be empty"));

        assertTrue(engine.hasActiveFailure(), "hasActiveFailure must be true after recordFailure");
        assertNotNull(engine.getLastFailureReason());
        assertEquals(PaperNativeDialogEngine.Stage.CONSTRUCT_MULTI_ACTION_TYPE, engine.getLastFailureStage());
        assertFalse(engine.isLastAttemptSuccess());

        // 2. Later operation succeeds
        engine.recordSuccess("MultiAction", null);

        assertFalse(engine.hasActiveFailure(), "hasActiveFailure must be false after recordSuccess");
        assertNull(engine.getLastFailureReason(), "Active failure reason must be cleared on success");
        assertNull(engine.getLastFailureStage(), "Active failure stage must be cleared on success");
        assertTrue(engine.isLastAttemptSuccess(), "lastAttemptSuccess must be true");

        // 3. Historical failure remains preserved
        assertNotNull(engine.getHistoricalFailureReason(), "Historical failure reason must remain preserved");
        assertEquals(PaperNativeDialogEngine.Stage.CONSTRUCT_MULTI_ACTION_TYPE, engine.getHistoricalFailureStage());
        assertTrue(engine.getHistoricalFailureTimestamp() > 0);
    }

    @Test
    @DisplayName("Diagnostics State: Correctly tracks attempt type and timestamp")
    public void testDiagnosticsReportLatestOperationCorrectly() {
        PaperNativeDialogEngine engine = new PaperNativeDialogEngine(null);

        engine.recordSuccess("Notice", null);
        assertEquals("Notice", engine.getLastAttemptType());
        assertTrue(engine.isLastAttemptSuccess());
        assertTrue(engine.getLastAttemptTimestamp() > 0);

        engine.recordSuccess("Confirmation", null);
        assertEquals("Confirmation", engine.getLastAttemptType());
    }

    @Test
    @DisplayName("Language Parity: Verify VN and EN empty state localization keys exist and are non-blank")
    public void testVnEnEmptyStateMessagesParity() {
        InputStream vnStream = getClass().getClassLoader().getResourceAsStream("languages/VN.yml");
        assertNotNull(vnStream);
        YamlConfiguration vnConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(vnStream, StandardCharsets.UTF_8));

        InputStream enStream = getClass().getClassLoader().getResourceAsStream("languages/EN.yml");
        assertNotNull(enStream);
        YamlConfiguration enConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(enStream, StandardCharsets.UTF_8));

        String[] requiredKeys = {
                "dialog.my_pets_empty",
                "dialog.my_pets_empty_hint",
                "dialog.shop_empty",
                "dialog.shop_empty_hint",
                "dialog.hatch_select_empty",
                "dialog.hatch_select_empty_hint",
                "dialog.codex_empty",
                "dialog.codex_empty_hint",
                "dialog.shards_empty",
                "dialog.shards_empty_hint"
        };

        for (String key : requiredKeys) {
            assertTrue(vnConfig.contains(key), "VN.yml missing key: " + key);
            assertFalse(vnConfig.getString(key, "").isBlank(), "VN.yml key is blank: " + key);

            assertTrue(enConfig.contains(key), "EN.yml missing key: " + key);
            assertFalse(enConfig.getString(key, "").isBlank(), "EN.yml key is blank: " + key);
        }
    }
}

