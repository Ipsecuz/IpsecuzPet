package org.ipsecuz.pet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class HatchingSessionSecurityTest {

    @Test
    @DisplayName("Hatching session cannot be committed more than once (anti double reward)")
    public void testSessionSingleCommit() {
        UUID playerId = UUID.randomUUID();
        HatchingManager.PendingHatchSession session = new HatchingManager.PendingHatchSession(playerId, "legendary_egg", "dragon_pet");

        assertEquals(HatchingManager.HatchState.PENDING, session.getState());

        // First commit succeeds
        boolean firstCommit = session.markCommitted();
        assertTrue(firstCommit, "First commit attempt must succeed");
        assertEquals(HatchingManager.HatchState.COMPLETED, session.getState());

        // Second commit MUST fail
        boolean secondCommit = session.markCommitted();
        assertFalse(secondCommit, "Second commit attempt must fail to eliminate double rewards");
        assertEquals(HatchingManager.HatchState.COMPLETED, session.getState());
    }
}
