package org.ipsecuz.pet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UpdateCheckerTest {

    @Test
    public void testIsNewerVersion() {
        assertTrue(UpdateChecker.isNewerVersion("2.1", "2.0"));
        assertTrue(UpdateChecker.isNewerVersion("2.1.1", "2.1.0"));
        assertTrue(UpdateChecker.isNewerVersion("v2.2-SNAPSHOT", "2.1"));
        assertTrue(UpdateChecker.isNewerVersion("3.0", "2.9.9"));

        assertFalse(UpdateChecker.isNewerVersion("2.0", "2.1"));
        assertFalse(UpdateChecker.isNewerVersion("2.1", "2.1"));
        assertFalse(UpdateChecker.isNewerVersion("2.0.9", "2.1.0"));
        assertFalse(UpdateChecker.isNewerVersion(null, "2.1"));
        assertFalse(UpdateChecker.isNewerVersion("2.1", null));
    }
}
