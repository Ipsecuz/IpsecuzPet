package org.ipsecuz.pet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PetProgressionTest {

    @Test
    @DisplayName("Verify multi-level progression calculation")
    public void testMultiLevelLoop() {
        int currentLvl = 1;
        int currentExp = 0;
        int baseExpRequirement = 50;
        int maxLvl = 100;

        // Give 150 EXP:
        // Lvl 1 requires 1 * 50 = 50 EXP -> reaches Lvl 2, remaining 100 EXP
        // Lvl 2 requires 2 * 50 = 100 EXP -> reaches Lvl 3, remaining 0 EXP
        long accumulatedExp = (long) currentExp + 150;
        int levelsGained = 0;

        while (currentLvl < maxLvl) {
            int nextLvlExp = currentLvl * baseExpRequirement;
            if (accumulatedExp >= nextLvlExp) {
                accumulatedExp -= nextLvlExp;
                currentLvl++;
                levelsGained++;
            } else {
                break;
            }
        }

        assertEquals(3, currentLvl);
        assertEquals(2, levelsGained);
        assertEquals(0, accumulatedExp);
    }

    @Test
    @DisplayName("Verify max level cap enforcement and XP overflow prevention")
    public void testMaxLevelCap() {
        int currentLvl = 99;
        int currentExp = 0;
        int baseExpRequirement = 50;
        int maxLvl = 100;

        // Give massive EXP: 1,000,000 EXP
        long accumulatedExp = (long) currentExp + 1_000_000;
        int levelsGained = 0;

        while (currentLvl < maxLvl) {
            int nextLvlExp = currentLvl * baseExpRequirement;
            if (accumulatedExp >= nextLvlExp) {
                accumulatedExp -= nextLvlExp;
                currentLvl++;
                levelsGained++;
            } else {
                break;
            }
        }

        int finalExp = (currentLvl >= maxLvl) ? 0 : (int) Math.min(Integer.MAX_VALUE, accumulatedExp);

        assertEquals(100, currentLvl);
        assertEquals(1, levelsGained);
        assertEquals(0, finalExp);
    }

    @Test
    @DisplayName("Verify evolution star stat multipliers")
    public void testEvolutionStarFormula() {
        double boostPerStar = 0.15;

        // 1 Star: 1.0
        double multStar1 = 1.0 + ((1 - 1) * boostPerStar);
        assertEquals(1.0, multStar1, 0.001);

        // 2 Stars: 1.15
        double multStar2 = 1.0 + ((2 - 1) * boostPerStar);
        assertEquals(1.15, multStar2, 0.001);

        // 5 Stars: 1.60
        double multStar5 = 1.0 + ((5 - 1) * boostPerStar);
        assertEquals(1.60, multStar5, 0.001);
    }
}
