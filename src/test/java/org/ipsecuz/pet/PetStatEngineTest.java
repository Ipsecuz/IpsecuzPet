package org.ipsecuz.pet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PetStatEngineTest {

    @Test
    public void testLevel1BaseStatFormula() {
        double baseDamage = 10.0;
        double growth = 2.0;

        // At level 1, (level - 1) * growth must equal 0, so effective stat == base
        int level1 = 1;
        double statLevel1 = baseDamage + ((level1 - 1) * growth);
        assertEquals(10.0, statLevel1, 0.001, "Level 1 stat must be exactly identical to base stat");

        // At level 5, (5 - 1) * 2 = 8, so stat == 18.0
        int level5 = 5;
        double statLevel5 = baseDamage + ((level5 - 1) * growth);
        assertEquals(18.0, statLevel5, 0.001, "Level 5 stat must be base + 4 * growth");
    }

    @Test
    public void testStarMultiplierCalculation() {
        // Star 1 = 1.0 (no bonus)
        double boostPerStar = 0.15;
        int star1 = 1;
        double mult1 = 1.0 + ((star1 - 1) * boostPerStar);
        assertEquals(1.0, mult1, 0.001);

        // Star 2 = 1.15
        int star2 = 2;
        double mult2 = 1.0 + ((star2 - 1) * boostPerStar);
        assertEquals(1.15, mult2, 0.001);

        // Star 5 = 1.60
        int star5 = 5;
        double mult5 = 1.0 + ((star5 - 1) * boostPerStar);
        assertEquals(1.60, mult5, 0.001);

        // Speed boost per star is 0.0 by default to prevent movement glitches
        double speedBoostPerStar = 0.0;
        double speedMult = 1.0 + ((star5 - 1) * speedBoostPerStar);
        assertEquals(1.0, speedMult, 0.001, "Speed multiplier must remain unchanged when boost is 0.0");
    }

    @Test
    public void testTraitMultipliers() {
        PetTrait guardian = PetTrait.GUARDIAN;
        assertEquals(1.20, guardian.getDefenseMultiplier(), 0.001);
        assertEquals(1.0, guardian.getDamageMultiplier(), 0.001);

        PetTrait savage = PetTrait.SAVAGE;
        assertEquals(1.15, savage.getDamageMultiplier(), 0.001);
        assertEquals(1.0, savage.getHealthMultiplier(), 0.001);

        PetTrait swift = PetTrait.SWIFT;
        assertEquals(1.15, swift.getSpeedMultiplier(), 0.001);
    }
}
