package org.ipsecuz.pet;

import org.bukkit.configuration.file.YamlConfiguration;
import org.ipsecuz.pet.model.ModelProvider;
import org.ipsecuz.pet.model.ModelType;
import org.ipsecuz.pet.model.NoneModelProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ModelArchitectureTest {

    @Test
    @DisplayName("Verify ModelType enum parsing and fallback")
    public void testModelTypeParsing() {
        assertEquals(ModelType.NONE, ModelType.fromString("NONE"));
        assertEquals(ModelType.NONE, ModelType.fromString("none"));
        assertEquals(ModelType.BETTERMODEL, ModelType.fromString("BETTERMODEL"));
        assertEquals(ModelType.BETTERMODEL, ModelType.fromString("bettermodel"));
        assertEquals(ModelType.MODELENGINE, ModelType.fromString("MODELENGINE"));
        assertEquals(ModelType.MODELENGINE, ModelType.fromString("modelengine"));
        assertEquals(ModelType.AUTO, ModelType.fromString("AUTO"));
        assertEquals(ModelType.AUTO, ModelType.fromString("auto"));

        // Fallbacks for null or invalid inputs
        assertEquals(ModelType.AUTO, ModelType.fromString(null));
        assertEquals(ModelType.AUTO, ModelType.fromString(""));
        assertEquals(ModelType.AUTO, ModelType.fromString("INVALID_ENGINE"));
    }

    @Test
    @DisplayName("Verify NoneModelProvider adheres to contract safely")
    public void testNoneModelProviderContract() {
        ModelProvider noneProvider = new NoneModelProvider();
        assertEquals(ModelType.NONE, noneProvider.getType());
        assertTrue(noneProvider.isAvailable());

        // Calling animations or positions on NoneModelProvider must execute safely without exceptions
        assertDoesNotThrow(() -> noneProvider.playAnimation(null, PetAnimationState.IDLE));
        assertDoesNotThrow(() -> noneProvider.playTransientAnimation(null, PetAnimationState.FEED, 20L, PetAnimationState.IDLE));
        assertDoesNotThrow(() -> noneProvider.stopAnimation(null));
        assertDoesNotThrow(() -> noneProvider.remove((org.bukkit.entity.Entity) null));
        assertDoesNotThrow(() -> noneProvider.removeAll());
        assertDoesNotThrow(() -> noneProvider.show(null, null));
        assertDoesNotThrow(() -> noneProvider.hide(null, null));
        assertDoesNotThrow(() -> noneProvider.updatePosition(null));
        assertDoesNotThrow(() -> noneProvider.handlePlayerQuit(null));
    }

    @Test
    @DisplayName("Verify config.yml contains default model configurations")
    public void testConfigModelDefaults() {
        InputStream configStream = getClass().getClassLoader().getResourceAsStream("config.yml");
        assertNotNull(configStream, "Resource config.yml must exist");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(configStream, StandardCharsets.UTF_8));

        assertTrue(config.contains("model.provider"), "config.yml should have model.provider");
        assertEquals("AUTO", config.getString("model.provider"));

        assertTrue(config.contains("model.auto_priority"), "config.yml should have model.auto_priority");
        List<String> priority = config.getStringList("model.auto_priority");
        assertFalse(priority.isEmpty());
        assertTrue(priority.contains("BETTERMODEL"));
        assertTrue(priority.contains("MODELENGINE"));

        assertTrue(config.contains("model.fallback.enabled"));
        assertTrue(config.getBoolean("model.fallback.enabled"));

        assertTrue(config.contains("model.fallback.provider"));
        assertEquals("BETTERMODEL", config.getString("model.fallback.provider"));

        assertTrue(config.contains("language"));
        assertEquals("VN", config.getString("language"));

        assertTrue(config.contains("metric"));
        assertTrue(config.getBoolean("metric"));
    }
}
