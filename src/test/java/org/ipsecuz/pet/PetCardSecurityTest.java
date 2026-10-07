package org.ipsecuz.pet;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.*;

public class PetCardSecurityTest {

    private String computeTestSignature(String uniqueId, String petId, int level, int exp, int stars, String trait, String salt) {
        String payload = uniqueId + ":" + petId + ":" + level + ":" + exp + ":" + stars + ":" + trait + ":" + salt;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(payload.hashCode());
        }
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

        String validSig = computeTestSignature(cardUuid, petId, level, exp, stars, trait, salt);
        assertNotNull(validSig);
        assertEquals(32, validSig.length());

        // Identical parameters produce identical signature
        String matchSig = computeTestSignature(cardUuid, petId, level, exp, stars, trait, salt);
        assertEquals(validSig, matchSig);

        // Tampering level
        String tamperedLevel = computeTestSignature(cardUuid, petId, 99, exp, stars, trait, salt);
        assertNotEquals(validSig, tamperedLevel, "Tampering with level must invalidate signature");

        // Tampering stars
        String tamperedStars = computeTestSignature(cardUuid, petId, level, exp, 5, trait, salt);
        assertNotEquals(validSig, tamperedStars, "Tampering with stars must invalidate signature");

        // Tampering trait
        String tamperedTrait = computeTestSignature(cardUuid, petId, level, exp, stars, "TITAN", salt);
        assertNotEquals(validSig, tamperedTrait, "Tampering with trait must invalidate signature");

        // Tampering salt
        String wrongSalt = computeTestSignature(cardUuid, petId, level, exp, stars, trait, "foreign-server-salt");
        assertNotEquals(validSig, wrongSalt, "Cards forged on a different server must fail signature check");
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
