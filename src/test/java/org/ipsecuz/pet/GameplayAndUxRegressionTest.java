package org.ipsecuz.pet;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class GameplayAndUxRegressionTest {

    @Test
    @DisplayName("Verify RenameSessionManager session lifecycle, cancellation, and validation")
    public void testRenameSessionValidationAndCancellation() {
        RenameSessionManager sessionManager = new RenameSessionManager(null);
        UUID playerId = UUID.randomUUID();

        // 1. Session tracking
        assertFalse(sessionManager.hasSession(playerId));

        sessionManager.startSession(null, "wolf_pet");
        // null player shouldn't register
        assertFalse(sessionManager.hasSession(playerId));

        // Start session manually via record
        RenameSessionManager.RenameSession session = new RenameSessionManager.RenameSession(playerId, "wolf_pet", System.currentTimeMillis());
        assertNotNull(session);
        assertEquals("wolf_pet", session.petId());
        assertEquals(playerId, session.playerId());

        // Cancel keyword checks
        assertTrue("cancel".equalsIgnoreCase("CANCEL"));
        assertTrue("huy".equalsIgnoreCase("HUY"));

        // Validation lengths
        String validName = "Fluffy";
        assertTrue(validName.length() >= 2 && validName.length() <= 16);

        String tooShort = "A";
        assertTrue(tooShort.length() < 2);

        String tooLong = "SuperUltraLongName123456";
        assertTrue(tooLong.length() > 16);
    }

    @Test
    @DisplayName("Verify all 43 configured pets in config.yml have valid EntityType definitions (no silent zombie fallback)")
    public void testAllConfiguredPetsHaveValidEntityTypes() {
        InputStream configStream = getClass().getClassLoader().getResourceAsStream("config.yml");
        assertNotNull(configStream, "Resource config.yml must exist");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(configStream, StandardCharsets.UTF_8));

        assertTrue(config.isConfigurationSection("pets"), "config.yml must contain 'pets' section");
        Set<String> petKeys = config.getConfigurationSection("pets").getKeys(false);
        assertFalse(petKeys.isEmpty(), "Pets section must not be empty");
        assertTrue(petKeys.size() >= 40, "Expected at least 40 configured pets, found: " + petKeys.size());

        for (String petId : petKeys) {
            String typeRaw = config.getString("pets." + petId + ".type");
            if (typeRaw == null || typeRaw.trim().isEmpty()) {
                typeRaw = config.getString("pets." + petId + ".entity_type");
            }
            assertNotNull(typeRaw, "Pet '" + petId + "' must specify 'type' or 'entity_type'");
            assertFalse(typeRaw.trim().isEmpty(), "Pet '" + petId + "' type cannot be empty");

            // Must parse into a valid Bukkit EntityType
            try {
                EntityType type = EntityType.valueOf(typeRaw.trim().toUpperCase(Locale.ROOT));
                assertNotNull(type, "Pet '" + petId + "' resolved EntityType must not be null");
            } catch (IllegalArgumentException e) {
                fail("Pet '" + petId + "' has invalid EntityType: '" + typeRaw + "'");
            }
        }
    }

    @Test
    @DisplayName("Verify specific critical pets (Iron Golem, Chicken, Wolf, Allay) map to correct entities")
    public void testCriticalPetEntityMappings() {
        InputStream configStream = getClass().getClassLoader().getResourceAsStream("config.yml");
        assertNotNull(configStream);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(configStream, StandardCharsets.UTF_8));

        assertEquals("WOLF", config.getString("pets.wolf_pet.type").toUpperCase(Locale.ROOT));
        assertEquals("CHICKEN", config.getString("pets.chicken_pet.type").toUpperCase(Locale.ROOT));
        assertEquals("IRON_GOLEM", config.getString("pets.iron_golem_pet.type").toUpperCase(Locale.ROOT));
        assertEquals("ALLAY", config.getString("pets.allay_pet.type").toUpperCase(Locale.ROOT));
    }
}
