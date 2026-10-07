package org.ipsecuz.pet;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class LanguageFileParityTest {

    @Test
    @DisplayName("Verify VN.yml and EN.yml have exact key parity and exist in resources")
    public void testLanguageFileKeyParity() {
        InputStream vnStream = getClass().getClassLoader().getResourceAsStream("languages/VN.yml");
        assertNotNull(vnStream, "Resource languages/VN.yml must exist");
        YamlConfiguration vnConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(vnStream, StandardCharsets.UTF_8));

        InputStream enStream = getClass().getClassLoader().getResourceAsStream("languages/EN.yml");
        assertNotNull(enStream, "Resource languages/EN.yml must exist");
        YamlConfiguration enConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(enStream, StandardCharsets.UTF_8));

        Set<String> vnKeys = vnConfig.getKeys(true);
        Set<String> enKeys = enConfig.getKeys(true);

        assertFalse(vnKeys.isEmpty(), "VN.yml keys must not be empty");
        assertFalse(enKeys.isEmpty(), "EN.yml keys must not be empty");

        // Find keys in VN that are missing in EN
        Set<String> missingInEn = new HashSet<>(vnKeys);
        missingInEn.removeAll(enKeys);

        // Find keys in EN that are missing in VN
        Set<String> missingInVn = new HashSet<>(enKeys);
        missingInVn.removeAll(vnKeys);

        assertTrue(missingInEn.isEmpty(), "EN.yml is missing keys present in VN.yml: " + missingInEn);
        assertTrue(missingInVn.isEmpty(), "VN.yml is missing keys present in EN.yml: " + missingInVn);
    }

    @Test
    @DisplayName("Verify all PetRarity constants have localized names in both language files")
    public void testRarityKeysPresent() {
        InputStream vnStream = getClass().getClassLoader().getResourceAsStream("languages/VN.yml");
        assertNotNull(vnStream);
        YamlConfiguration vnConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(vnStream, StandardCharsets.UTF_8));

        InputStream enStream = getClass().getClassLoader().getResourceAsStream("languages/EN.yml");
        assertNotNull(enStream);
        YamlConfiguration enConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(enStream, StandardCharsets.UTF_8));

        for (PetRarity rarity : PetRarity.values()) {
            String path = "rarity." + rarity.name().toLowerCase();
            assertTrue(vnConfig.contains(path), "VN.yml missing rarity key: " + path);
            assertTrue(enConfig.contains(path), "EN.yml missing rarity key: " + path);
            assertNotNull(vnConfig.getString(path));
            assertNotNull(enConfig.getString(path));
        }
    }

    @Test
    @DisplayName("Verify Model diagnostics status keys are present in both language files")
    public void testModelStatusKeysPresent() {
        InputStream vnStream = getClass().getClassLoader().getResourceAsStream("languages/VN.yml");
        assertNotNull(vnStream);
        YamlConfiguration vnConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(vnStream, StandardCharsets.UTF_8));

        InputStream enStream = getClass().getClassLoader().getResourceAsStream("languages/EN.yml");
        assertNotNull(enStream);
        YamlConfiguration enConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(enStream, StandardCharsets.UTF_8));

        String[] requiredModelKeys = {
                "model.status_header",
                "model.status_title",
                "model.bettermodel_label",
                "model.modelengine_label",
                "model.global_provider",
                "model.active_provider",
                "model.installed",
                "model.not_installed",
                "model.status_footer",
                "model.model_3d_label"
        };

        for (String key : requiredModelKeys) {
            assertTrue(vnConfig.contains(key), "VN.yml missing key: " + key);
            assertTrue(enConfig.contains(key), "EN.yml missing key: " + key);
        }
    }
}
