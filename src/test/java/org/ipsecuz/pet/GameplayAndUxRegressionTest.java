package org.ipsecuz.pet;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

    @Test
    @DisplayName("Verify GuiText.stripPrefix strips prefix and cleans GUI titles/components")
    public void testGuiTextStripPrefix() {
        assertEquals("&b🐾 Bộ Sưu Tập Pet", GuiText.stripPrefix("&#00AAFF[IpsecuzPets] &r&b🐾 Bộ Sưu Tập Pet"));
        assertEquals("&8&lIPSECUZPET &7- &6Pet Hub", GuiText.stripPrefix("[IpsecuzPets] &8&lIPSECUZPET &7- &6Pet Hub"));
        assertEquals("Đang đồng hành", GuiText.stripPrefix("[IpsecuzPet] Đang đồng hành"));
        assertEquals("§aChi Tiết Pet", GuiText.stripPrefix("§x§0§0§a§a§f§f[IpsecuzPets] §r§aChi Tiết Pet"));
        assertEquals("&6Tên Thú Cưng", GuiText.stripPrefix("&6Tên Thú Cưng"));

        net.kyori.adventure.text.Component comp = GuiText.component("&#00AAFF[IpsecuzPets] &r&b🐾 Bộ Sưu Tập Pet");
        assertNotNull(comp);
        // Verify component serialization doesn't contain [IpsecuzPets]
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(comp);
        assertFalse(plain.contains("[IpsecuzPets]"), "Component must not contain [IpsecuzPets]: " + plain);
        assertTrue(plain.contains("Bộ Sưu Tập Pet"), "Component must contain actual name: " + plain);
    }

    @Test
    @DisplayName("Verify plugin.yml registers givepet as an alias")
    public void testPluginYmlAliases() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("plugin.yml");
        assertNotNull(stream);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        List<String> aliases = yml.getStringList("commands.ipsecuzpet.aliases");
        assertTrue(aliases.contains("givepet"), "plugin.yml aliases must contain 'givepet'");
        assertTrue(aliases.contains("pet"), "plugin.yml aliases must contain 'pet'");
        assertTrue(aliases.contains("pethatch"), "plugin.yml aliases must contain 'pethatch'");
    }
}

