package org.ipsecuz.pet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PetTraitTest {

    @Test
    @DisplayName("Verify trait multipliers")
    public void testTraitMultipliers() {
        assertEquals(1.15, PetTrait.SAVAGE.getDamageMultiplier(), 0.001);
        assertEquals(1.20, PetTrait.GUARDIAN.getDefenseMultiplier(), 0.001);
        assertEquals(1.15, PetTrait.SWIFT.getSpeedMultiplier(), 0.001);
        assertEquals(1.20, PetTrait.VITAL.getHealthMultiplier(), 0.001);
        assertEquals(1.15, PetTrait.TITAN.getHealthMultiplier(), 0.001);
        assertEquals(1.10, PetTrait.TITAN.getDamageMultiplier(), 0.001);
        assertEquals(1.15, PetTrait.TITAN.getDefenseMultiplier(), 0.001);
        assertEquals(1.25, PetTrait.SCHOLAR.getExpMultiplier(), 0.001);

        assertEquals(1.0, PetTrait.NONE.getHealthMultiplier(), 0.001);
        assertEquals(1.0, PetTrait.NONE.getDamageMultiplier(), 0.001);
        assertEquals(1.0, PetTrait.NONE.getDefenseMultiplier(), 0.001);
        assertEquals(1.0, PetTrait.NONE.getSpeedMultiplier(), 0.001);
    }

    @Test
    @DisplayName("Verify rollRandomTrait returns valid trait")
    public void testRandomTrait() {
        boolean rolledSpecial = false;
        for (int i = 0; i < 100; i++) {
            PetTrait trait = PetTrait.rollRandomTrait();
            assertNotNull(trait);
            if (trait != PetTrait.NONE) {
                rolledSpecial = true;
            }
        }
        assertTrue(rolledSpecial, "Expected at least one special trait in 100 rolls");
    }

    @Test
    @DisplayName("Verify fromString parsing")
    public void testFromString() {
        assertEquals(PetTrait.NONE, PetTrait.fromString(null));
        assertEquals(PetTrait.NONE, PetTrait.fromString("INVALID"));
        assertEquals(PetTrait.SAVAGE, PetTrait.fromString("savage"));
        assertEquals(PetTrait.SCHOLAR, PetTrait.fromString("SCHOLAR"));
    }
}

