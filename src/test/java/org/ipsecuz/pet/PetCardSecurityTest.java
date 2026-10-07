package org.ipsecuz.pet;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.*;

public class PetCardSecurityTest {

    @Test
    public void testBuildCanonicalPayloadDeterminismAndSkillSorting() {
        int schema = PetCardSecurity.CURRENT_SCHEMA;
        String cardUuid = "uuid-abc-123";
        String petId = "ender_dragon_pet";
        int level = 50;
        int exp = 1200;
        int stars = 3;
        String trait = "SAVAGE";
        String customName = "DragonLord";

        // Skills in different order must yield identical canonical payload due to alphabetical sorting
        List<String> skills1 = List.of("roar", "fireball", "dash");
        List<String> skills2 = List.of("fireball", "dash", "roar");

        String payload1 = PetCardSecurity.buildCanonicalPayload(schema, cardUuid, petId, level, exp, stars, trait, customName, skills1);
        String payload2 = PetCardSecurity.buildCanonicalPayload(schema, cardUuid, petId, level, exp, stars, trait, customName, skills2);

        assertNotNull(payload1);
        assertEquals(payload1, payload2, "Canonical payload must sort skills alphabetically for deterministic signing");
        assertEquals("2:uuid-abc-123:ender_dragon_pet:50:1200:3:SAVAGE:DragonLord:dash,fireball,roar", payload1);
    }

    @Test
    public void testBuildCanonicalPayloadNullSafety() {
        int schema = PetCardSecurity.CURRENT_SCHEMA;
        String cardUuid = "uuid-xyz-789";
        String petId = "cat_pet";

        String payload = PetCardSecurity.buildCanonicalPayload(schema, cardUuid, petId, 1, 0, 1, "NONE", null, null);
        assertNotNull(payload);
        assertEquals("2:uuid-xyz-789:cat_pet:1:0:1:NONE::", payload, "Null custom name and null skills should resolve to empty strings");
    }

    @Test
    public void testCardSignatureIntegrityAndTamperDetection() {
        String salt = "test-salt-secret-123456";
        String cardUuid = "uuid-abc-123";
        String petId = "ender_dragon_pet";
        int level = 50;
        int exp = 1200;
        int stars = 3;
        String trait = "SAVAGE";
        String customName = "Shadow";
        List<String> skills = List.of("dash", "fireball");

        String basePayload = PetCardSecurity.buildCanonicalPayload(2, cardUuid, petId, level, exp, stars, trait, customName, skills);
        String validSig = PetCardSecurity.computeHmacSha256(salt, basePayload);
        assertNotNull(validSig);
        assertEquals(64, validSig.length());

        // Identical parameters produce identical signature
        String matchPayload = PetCardSecurity.buildCanonicalPayload(2, cardUuid, petId, level, exp, stars, trait, customName, skills);
        assertEquals(validSig, PetCardSecurity.computeHmacSha256(salt, matchPayload));

        // Tampering level
        String tamperedLevel = PetCardSecurity.buildCanonicalPayload(2, cardUuid, petId, 99, exp, stars, trait, customName, skills);
        assertNotEquals(validSig, PetCardSecurity.computeHmacSha256(salt, tamperedLevel), "Tampering with level must invalidate signature");

        // Tampering stars
        String tamperedStars = PetCardSecurity.buildCanonicalPayload(2, cardUuid, petId, level, exp, 5, trait, customName, skills);
        assertNotEquals(validSig, PetCardSecurity.computeHmacSha256(salt, tamperedStars), "Tampering with stars must invalidate signature");

        // Tampering trait
        String tamperedTrait = PetCardSecurity.buildCanonicalPayload(2, cardUuid, petId, level, exp, stars, "TITAN", customName, skills);
        assertNotEquals(validSig, PetCardSecurity.computeHmacSha256(salt, tamperedTrait), "Tampering with trait must invalidate signature");

        // Tampering salt
        assertNotEquals(validSig, PetCardSecurity.computeHmacSha256("foreign-server-salt", basePayload), "Cards forged on a different server must fail signature check");
    }

    @Test
    public void testFailClosedNullInputThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> PetCardSecurity.computeHmacSha256(null, "some-data"));
        assertThrows(IllegalArgumentException.class, () -> PetCardSecurity.computeHmacSha256("some-key", null));
    }

    @Test
    public void testComputeHmacSha256DeterminismAndLength() {
        String key = "secure-server-salt-key-999";
        String data = "2:uuid-1234:dragon:50:500:3:TITAN:Shadow:skill1,skill2";

        String sig1 = PetCardSecurity.computeHmacSha256(key, data);
        String sig2 = PetCardSecurity.computeHmacSha256(key, data);

        assertNotNull(sig1);
        assertEquals(64, sig1.length(), "HMAC signature should be 64 hex characters");
        assertEquals(sig1, sig2, "Identical key and data must yield identical HMAC signature");
    }

    @Test
    public void testComputeHmacSha256DifferentKeysProduceDistinctOutputs() {
        String data = "2:uuid-1234:dragon:50:500:3:TITAN:Shadow:skill1,skill2";
        String keyA = "server-salt-alpha";
        String keyB = "server-salt-beta";

        String sigA = PetCardSecurity.computeHmacSha256(keyA, data);
        String sigB = PetCardSecurity.computeHmacSha256(keyB, data);

        assertNotEquals(sigA, sigB, "Different server salt keys must produce different signatures");
    }

    @Test
    public void testCanonicalPayloadTamperSensitivity() {
        String key = "secure-salt";
        String base = "2:uuid-card-1:fire_dragon:10:200:1:NONE:Sparky:slash";
        String sigBase = PetCardSecurity.computeHmacSha256(key, base);

        // Schema version tamper
        String tamperedSchema = PetCardSecurity.computeHmacSha256(key, "1:uuid-card-1:fire_dragon:10:200:1:NONE:Sparky:slash");
        assertNotEquals(sigBase, tamperedSchema);

        // Custom name tamper
        String tamperedName = PetCardSecurity.computeHmacSha256(key, "2:uuid-card-1:fire_dragon:10:200:1:NONE:HackedName:slash");
        assertNotEquals(sigBase, tamperedName);

        // Skill list tamper
        String tamperedSkills = PetCardSecurity.computeHmacSha256(key, "2:uuid-card-1:fire_dragon:10:200:1:NONE:Sparky:slash,god_mode");
        assertNotEquals(sigBase, tamperedSkills);
    }
}
