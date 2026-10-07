package org.ipsecuz.pet;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class HappinessModifierEngineTest {

    @Test
    @DisplayName("Default null plugin returns safe 1.0 multipliers")
    public void testNullPluginSafeFallbacks() {
        HappinessModifierEngine engine = new HappinessModifierEngine(null);
        assertEquals(1.0, engine.getExpMultiplier(100), 0.001);
        assertEquals(1.0, engine.getExpMultiplier(0), 0.001);
        assertEquals(1.0, engine.getStatMultiplier(100, "speed"), 0.001);
        assertEquals(1.0, engine.getStatMultiplier(0, "damage"), 0.001);
    }

    @Test
    @DisplayName("Verify happiness thresholds logic")
    public void testThresholdMultipliers() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("bonus_threshold", 80);
        config.set("penalty_threshold", 20);
        config.set("bonus_exp_multiplier", 1.25);
        config.set("penalty_exp_multiplier", 0.75);
        config.set("bonus_speed_multiplier", 1.15);
        config.set("penalty_speed_multiplier", 0.85);
        config.set("bonus_stat_multiplier", 1.10);
        config.set("penalty_stat_multiplier", 0.90);

        // Happiness 100 (Ecstatic)
        int happy = 100;
        int bonusThreshold = config.getInt("bonus_threshold", 80);
        int penaltyThreshold = config.getInt("penalty_threshold", 20);

        assertTrue(happy >= bonusThreshold);
        assertEquals(1.25, config.getDouble("bonus_exp_multiplier"), 0.001);
        assertEquals(1.15, config.getDouble("bonus_speed_multiplier"), 0.001);
        assertEquals(1.10, config.getDouble("bonus_stat_multiplier"), 0.001);

        // Happiness 50 (Content)
        int content = 50;
        assertFalse(content >= bonusThreshold);
        assertFalse(content < penaltyThreshold);

        // Happiness 10 (Starving)
        int starving = 10;
        assertTrue(starving < penaltyThreshold);
        assertEquals(0.75, config.getDouble("penalty_exp_multiplier"), 0.001);
        assertEquals(0.85, config.getDouble("penalty_speed_multiplier"), 0.001);
        assertEquals(0.90, config.getDouble("penalty_stat_multiplier"), 0.001);
    }
}
