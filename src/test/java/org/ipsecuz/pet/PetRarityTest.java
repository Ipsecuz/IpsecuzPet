package org.ipsecuz.pet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PetRarityTest {

    @Test
    @DisplayName("Verify all rarities exist with valid values")
    public void testRarities() {
        assertEquals(8, PetRarity.values().length);

        assertTrue(PetRarity.COMMON.getShardValue() < PetRarity.UNCOMMON.getShardValue());
        assertTrue(PetRarity.UNCOMMON.getShardValue() < PetRarity.RARE.getShardValue());
        assertTrue(PetRarity.RARE.getShardValue() < PetRarity.EPIC.getShardValue());
        assertTrue(PetRarity.EPIC.getShardValue() < PetRarity.LEGENDARY.getShardValue());
        assertTrue(PetRarity.LEGENDARY.getShardValue() < PetRarity.MYTHIC.getShardValue());
        assertTrue(PetRarity.MYTHIC.getShardValue() < PetRarity.SECRET.getShardValue());
        assertTrue(PetRarity.SECRET.getShardValue() < PetRarity.ETERNAL.getShardValue());
    }

    @Test
    @DisplayName("Verify fromString fallback and casing")
    public void testFromString() {
        assertEquals(PetRarity.COMMON, PetRarity.fromString(null));
        assertEquals(PetRarity.COMMON, PetRarity.fromString(""));
        assertEquals(PetRarity.COMMON, PetRarity.fromString("INVALID_FOO_BAR"));

        assertEquals(PetRarity.LEGENDARY, PetRarity.fromString("legendary"));
        assertEquals(PetRarity.LEGENDARY, PetRarity.fromString("LEGENDARY"));
        assertEquals(PetRarity.MYTHIC, PetRarity.fromString("mythic"));
        assertEquals(PetRarity.ETERNAL, PetRarity.fromString("Eternal"));
    }
}

