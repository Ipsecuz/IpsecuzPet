package org.ipsecuz.pet;

import org.ipsecuz.pet.model.ModelType;
import org.ipsecuz.pet.model.ResolvedModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ResolvedModelTest {

    @Test
    @DisplayName("Verify valid ResolvedModel attributes")
    public void testValidResolvedModel() {
        ResolvedModel model = new ResolvedModel(ModelType.BETTERMODEL, "dragon_adult", false, false, false);
        assertEquals(ModelType.BETTERMODEL, model.getProvider());
        assertEquals("dragon_adult", model.getModelId());
        assertFalse(model.isFallback());
        assertFalse(model.isLegacy());
        assertFalse(model.isBaby());
        assertTrue(model.isValid());
    }

    @Test
    @DisplayName("Verify fallback and legacy flags")
    public void testFallbackAndLegacyModel() {
        ResolvedModel model = new ResolvedModel(ModelType.MODELENGINE, "legacy_pet", true, true, true);
        assertEquals(ModelType.MODELENGINE, model.getProvider());
        assertEquals("legacy_pet", model.getModelId());
        assertTrue(model.isFallback());
        assertTrue(model.isLegacy());
        assertTrue(model.isBaby());
        assertTrue(model.isValid());
    }

    @Test
    @DisplayName("Verify ResolvedModel.none() factory")
    public void testNoneFactory() {
        ResolvedModel noneAdult = ResolvedModel.none(false);
        assertEquals(ModelType.NONE, noneAdult.getProvider());
        assertNull(noneAdult.getModelId());
        assertFalse(noneAdult.isFallback());
        assertFalse(noneAdult.isLegacy());
        assertFalse(noneAdult.isBaby());
        assertFalse(noneAdult.isValid());

        ResolvedModel noneBaby = ResolvedModel.none(true);
        assertEquals(ModelType.NONE, noneBaby.getProvider());
        assertNull(noneBaby.getModelId());
        assertTrue(noneBaby.isBaby());
        assertFalse(noneBaby.isValid());
    }

    @Test
    @DisplayName("Verify null provider defaults to NONE")
    public void testNullProviderDefaultsToNone() {
        ResolvedModel model = new ResolvedModel(null, "some_id", false, false, false);
        assertEquals(ModelType.NONE, model.getProvider());
        assertFalse(model.isValid());
    }

    @Test
    @DisplayName("Verify blank or null modelId results in invalid")
    public void testBlankModelIdIsInvalid() {
        ResolvedModel nullId = new ResolvedModel(ModelType.BETTERMODEL, null, false, false, false);
        assertFalse(nullId.isValid());

        ResolvedModel emptyId = new ResolvedModel(ModelType.BETTERMODEL, "   ", false, false, false);
        assertFalse(emptyId.isValid());
    }
}
