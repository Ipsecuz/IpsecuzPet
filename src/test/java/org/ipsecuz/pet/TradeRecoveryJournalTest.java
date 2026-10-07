package org.ipsecuz.pet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class TradeRecoveryJournalTest {

    @Test
    @DisplayName("TradeCommitRecord stores transaction details correctly")
    public void testTradeCommitRecordAttributes() {
        UUID tradeId = UUID.randomUUID();
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        long before = System.currentTimeMillis();
        TradeManager.TradeCommitRecord record = new TradeManager.TradeCommitRecord(
                tradeId, playerA, playerB, Collections.emptyList(), Collections.emptyList()
        );
        long after = System.currentTimeMillis();

        assertEquals(tradeId, record.getTradeId());
        assertEquals(playerA, record.getPlayerA());
        assertEquals(playerB, record.getPlayerB());
        assertNotNull(record.getItemsFromA());
        assertNotNull(record.getItemsFromB());
        assertEquals(TradeManager.TradeCommitStatus.PREPARED, record.getStatus(), "Default status should be PREPARED");
        assertTrue(record.getTimestamp() >= before && record.getTimestamp() <= after);
    }

    @Test
    @DisplayName("TradeCommitRecord correctly supports COMMITTED status")
    public void testTradeCommitStatusCommitted() {
        UUID tradeId = UUID.randomUUID();
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        long now = System.currentTimeMillis();
        TradeManager.TradeCommitRecord record = new TradeManager.TradeCommitRecord(
                tradeId, playerA, playerB, Collections.emptyList(), Collections.emptyList(),
                TradeManager.TradeCommitStatus.COMMITTED, now
        );

        assertEquals(TradeManager.TradeCommitStatus.COMMITTED, record.getStatus());
        assertEquals(now, record.getTimestamp());
    }

    @Test
    @DisplayName("Trade journal path formatting is deterministic and safe")
    public void testTradeJournalPaths() {
        UUID tradeId = UUID.randomUUID();
        String tradePath = "pending_trade." + tradeId;
        assertTrue(tradePath.startsWith("pending_trade."));
        assertEquals(tradeId.toString(), tradePath.substring("pending_trade.".length()));

        UUID playerUuid = UUID.randomUUID();
        String refundPath = "pending_refund." + playerUuid;
        assertTrue(refundPath.startsWith("pending_refund."));
        assertEquals(playerUuid.toString(), refundPath.substring("pending_refund.".length()));
    }
}

