package org.ipsecuz.pet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class HatchingLootTableTest {

    @Test
    @DisplayName("Loot table algorithm correctly filters non-positive weights and retains positive ones in LinkedHashMap")
    public void testLootTableFiltering() {
        Map<String, Integer> rawWeights = new LinkedHashMap<>();
        rawWeights.put("pet_common", 70);
        rawWeights.put("pet_rare", 20);
        rawWeights.put("pet_negative", -5);
        rawWeights.put("pet_zero", 0);
        rawWeights.put("pet_epic", 10);

        LinkedHashMap<String, Integer> validWeights = new LinkedHashMap<>();
        int totalWeight = 0;

        for (Map.Entry<String, Integer> entry : rawWeights.entrySet()) {
            int w = entry.getValue();
            if (w <= 0) continue;
            validWeights.put(entry.getKey(), w);
            totalWeight += w;
        }

        assertEquals(3, validWeights.size());
        assertEquals(100, totalWeight);
        assertTrue(validWeights.containsKey("pet_common"));
        assertTrue(validWeights.containsKey("pet_rare"));
        assertTrue(validWeights.containsKey("pet_epic"));
        assertFalse(validWeights.containsKey("pet_negative"));
        assertFalse(validWeights.containsKey("pet_zero"));

        // Verify iteration order is preserved (LinkedHashMap)
        List<String> keys = new ArrayList<>(validWeights.keySet());
        assertEquals("pet_common", keys.get(0));
        assertEquals("pet_rare", keys.get(1));
        assertEquals("pet_epic", keys.get(2));
    }

    @Test
    @DisplayName("Roulette cumulative interval logic accurately maps random points")
    public void testRouletteIntervalMapping() {
        LinkedHashMap<String, Integer> weights = new LinkedHashMap<>();
        weights.put("common", 70);
        weights.put("rare", 25);
        weights.put("legendary", 5);

        // Test roll within [0, 69] -> common
        assertEquals("common", pickWinner(weights, 0));
        assertEquals("common", pickWinner(weights, 69));

        // Test roll within [70, 94] -> rare
        assertEquals("rare", pickWinner(weights, 70));
        assertEquals("rare", pickWinner(weights, 94));

        // Test roll within [95, 99] -> legendary
        assertEquals("legendary", pickWinner(weights, 95));
        assertEquals("legendary", pickWinner(weights, 99));
    }

    private String pickWinner(LinkedHashMap<String, Integer> weights, int randomWeight) {
        int count = 0;
        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
            count += entry.getValue();
            if (randomWeight < count) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Test
    @DisplayName("PendingHatchSession lifecycle and state transitions")
    public void testPendingHatchSessionLifecycle() {
        UUID playerId = UUID.randomUUID();
        HatchingManager.PendingHatchSession session = new HatchingManager.PendingHatchSession(
                playerId, "starter_egg", "cat_pet", HatchingManager.HatchState.PENDING, System.currentTimeMillis()
        );

        assertEquals(HatchingManager.HatchState.PENDING, session.getState());
        assertEquals("starter_egg", session.getEggId());
        assertEquals("cat_pet", session.getWinningPetId());
        assertEquals(playerId, session.getPlayerUuid());

        // State update to PROCESSING (payment successful, gacha spinning)
        session.setState(HatchingManager.HatchState.PROCESSING);
        assertEquals(HatchingManager.HatchState.PROCESSING, session.getState());

        // Atomic commit (reward delivered)
        boolean committed = session.markCommitted();
        assertTrue(committed);
        assertEquals(HatchingManager.HatchState.COMPLETED, session.getState());

        // Second commit must fail
        boolean secondCommit = session.markCommitted();
        assertFalse(secondCommit);
        assertEquals(HatchingManager.HatchState.COMPLETED, session.getState());
    }
}
