package org.ipsecuz.pet;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class TradeSessionTest {

    @Test
    public void testTradeSlotsDisjointAndValid() {
        Set<Integer> slotsA = TradeSession.SLOTS_A;
        Set<Integer> slotsB = TradeSession.SLOTS_B;
        Set<Integer> dividers = TradeSession.DIVIDER_SLOTS;

        // Ensure 9 offer slots per player (3x3 grid)
        assertEquals(9, slotsA.size(), "Player A must have exactly 9 trade slots");
        assertEquals(9, slotsB.size(), "Player B must have exactly 9 trade slots");

        // Ensure sets are strictly disjoint
        for (int slot : slotsA) {
            assertFalse(slotsB.contains(slot), "Slot " + slot + " must not exist in both player A and B offerings");
            assertFalse(dividers.contains(slot), "Slot " + slot + " must not overlap divider slots");
            assertTrue(slot >= 0 && slot < 54, "Slot " + slot + " must be within 54-slot chest bounds");
        }

        for (int slot : slotsB) {
            assertFalse(dividers.contains(slot), "Slot " + slot + " must not overlap divider slots");
            assertTrue(slot >= 0 && slot < 54, "Slot " + slot + " must be within 54-slot chest bounds");
        }

        // Lock button and countdown slots must not overlap offer slots
        int lockBtnA = 38;
        int lockBtnB = 42;
        int countdownSlot = 49;

        assertFalse(slotsA.contains(lockBtnA));
        assertFalse(slotsB.contains(lockBtnA));
        assertFalse(slotsA.contains(lockBtnB));
        assertFalse(slotsB.contains(lockBtnB));
        assertFalse(slotsA.contains(countdownSlot));
        assertFalse(slotsB.contains(countdownSlot));
    }
}
