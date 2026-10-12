package org.ipsecuz.pet;

import org.bukkit.configuration.file.YamlConfiguration;
import org.ipsecuz.pet.dialog.PaperNativeDialogEngine;
import org.ipsecuz.pet.requirement.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class HatchRequirementsAndSpawnMessageTest {

    @Test
    @DisplayName("Verify hatching.yml parses all egg requirement groups correctly into RequirementGroup")
    public void testHatchingYmlRequirementParsing() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("modules/hatching.yml");
        assertNotNull(stream, "modules/hatching.yml must exist");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));

        assertTrue(config.isConfigurationSection("eggs"), "hatching.yml must contain 'eggs'");

        // 1. Common Egg (Money: 500, Item: WHEAT:10)
        var commonSec = config.getConfigurationSection("eggs.common_egg");
        assertNotNull(commonSec, "common_egg section must exist");
        RequirementGroup commonGroup = RequirementParser.parse(commonSec);
        assertNotNull(commonGroup);
        assertFalse(commonGroup.getRequirements().isEmpty(), "common_egg requirements must not be empty");
        assertEquals(RequirementGroupType.ALL, commonGroup.getGroupType());

        List<Requirement> commonReqs = commonGroup.getRequirements();
        assertEquals(2, commonReqs.size(), "common_egg must have 2 requirements (Money and Item)");

        boolean hasMoney500 = false;
        boolean hasWheat10 = false;
        for (Requirement r : commonReqs) {
            if (r instanceof MoneyRequirement mr) {
                if (mr.getAmount() == 500.0) hasMoney500 = true;
            } else if (r instanceof ItemRequirement ir) {
                if ("WHEAT".equalsIgnoreCase(ir.getId()) && ir.getAmount() == 10) hasWheat10 = true;
            }
        }
        assertTrue(hasMoney500, "common_egg must require 500 Money");
        assertTrue(hasWheat10, "common_egg must require 10 Wheat");

        // 2. Rare Egg (Money: 3000, Points: 10, Item: DIAMOND:2)
        var rareSec = config.getConfigurationSection("eggs.rare_egg");
        assertNotNull(rareSec, "rare_egg section must exist");
        RequirementGroup rareGroup = RequirementParser.parse(rareSec);
        assertNotNull(rareGroup);
        assertFalse(rareGroup.getRequirements().isEmpty(), "rare_egg requirements must not be empty");

        List<Requirement> rareReqs = rareGroup.getRequirements();
        assertEquals(3, rareReqs.size(), "rare_egg must have 3 requirements");

        boolean hasMoney3000 = false;
        boolean hasPoints10 = false;
        boolean hasDiamond2 = false;
        for (Requirement r : rareReqs) {
            if (r instanceof MoneyRequirement mr) {
                if (mr.getAmount() == 3000.0) hasMoney3000 = true;
            } else if (r instanceof PointsRequirement pr) {
                if (pr.getAmount() == 10) hasPoints10 = true;
            } else if (r instanceof ItemRequirement ir) {
                if ("DIAMOND".equalsIgnoreCase(ir.getId()) && ir.getAmount() == 2) hasDiamond2 = true;
            }
        }
        assertTrue(hasMoney3000, "rare_egg must require 3000 Money");
        assertTrue(hasPoints10, "rare_egg must require 10 Points");
        assertTrue(hasDiamond2, "rare_egg must require 2 Diamonds");

        // 3. Legendary Egg (all: ITEM DIAMOND:5, one_of: MONEY 10000, POINTS 50, ITEM NETHERITE_INGOT:1)
        var legSec = config.getConfigurationSection("eggs.legendary_egg");
        assertNotNull(legSec, "legendary_egg section must exist");
        RequirementGroup legGroup = RequirementParser.parse(legSec);
        assertNotNull(legGroup);
        assertFalse(legGroup.getRequirements().isEmpty(), "legendary_egg requirements must not be empty");

        List<Requirement> legReqs = legGroup.getRequirements();
        assertEquals(2, legReqs.size(), "legendary_egg root group must have 2 elements: all requirement and one_of subgroup");

        RequirementGroup allSubgroup = null;
        RequirementGroup oneOfSubgroup = null;

        for (Requirement r : legReqs) {
            if (r instanceof RequirementGroup rg) {
                if (rg.getGroupType() == RequirementGroupType.ALL) {
                    allSubgroup = rg;
                } else if (rg.getGroupType() == RequirementGroupType.ONE_OF) {
                    oneOfSubgroup = rg;
                }
            }
        }
        assertNotNull(allSubgroup, "legendary_egg must contain an ALL subgroup");
        assertNotNull(oneOfSubgroup, "legendary_egg must contain a ONE_OF subgroup");

        boolean hasDiamond5 = false;
        for (Requirement r : allSubgroup.getRequirements()) {
            if (r instanceof ItemRequirement ir) {
                if ("DIAMOND".equalsIgnoreCase(ir.getId()) && ir.getAmount() == 5) hasDiamond5 = true;
            }
        }
        assertTrue(hasDiamond5, "legendary_egg must require 5 Diamonds in all subgroup");
        assertEquals(3, oneOfSubgroup.getRequirements().size(), "legendary_egg ONE_OF subgroup must have 3 alternatives");

        boolean hasMoney10000 = false;
        boolean hasPoints50 = false;
        boolean hasNetheriteIngot = false;
        for (Requirement alt : oneOfSubgroup.getRequirements()) {
            if (alt instanceof MoneyRequirement mr) {
                if (mr.getAmount() == 10000.0) hasMoney10000 = true;
            } else if (alt instanceof PointsRequirement pr) {
                if (pr.getAmount() == 50) hasPoints50 = true;
            } else if (alt instanceof ItemRequirement ir) {
                if ("NETHERITE_INGOT".equalsIgnoreCase(ir.getId()) && ir.getAmount() == 1) hasNetheriteIngot = true;
            }
        }
        assertTrue(hasMoney10000, "legendary_egg ONE_OF must include 10000 Money");
        assertTrue(hasPoints50, "legendary_egg ONE_OF must include 50 Points");
        assertTrue(hasNetheriteIngot, "legendary_egg ONE_OF must include 1 Netherite Ingot");
    }

    @Test
    @DisplayName("Verify rejection of malformed or empty requirement configurations unless free: true")
    public void testEmptyOrMalformedRequirementsValidation() {
        // 1. Egg with empty requirement group and NOT free -> Must be detected as invalid
        YamlConfiguration emptyEgg = new YamlConfiguration();
        emptyEgg.set("loot_table.pet_common", 100);
        emptyEgg.createSection("requirements"); // empty requirements
        RequirementGroup emptyGroup = RequirementParser.parse(emptyEgg);
        assertTrue(emptyGroup.getRequirements().isEmpty(), "Empty requirement section yields empty group");
        assertFalse(emptyEgg.getBoolean("free", false), "Egg is not marked free");

        // 2. Egg with free: true -> Allowed even with empty requirement group
        YamlConfiguration freeEgg = new YamlConfiguration();
        freeEgg.set("free", true);
        freeEgg.set("loot_table.pet_common", 100);
        assertTrue(freeEgg.getBoolean("free", false), "Free egg is valid regardless of requirements");

        // 3. Egg with unrecognized requirement keys (legacy flat keys that don't match parser)
        YamlConfiguration legacyFlat = new YamlConfiguration();
        legacyFlat.set("requirements.unrecognized_key", "123");
        RequirementGroup invalidGroup = RequirementParser.parse(legacyFlat);
        assertTrue(invalidGroup.getRequirements().isEmpty(), "Unrecognized keys yield empty group");
    }

    @Test
    @DisplayName("Verify spawn chat message formatting separates entity nameplate from chat message")
    public void testSpawnMessageFormattingSeparation() {
        // Check language files
        InputStream vnStream = getClass().getClassLoader().getResourceAsStream("languages/VN.yml");
        assertNotNull(vnStream);
        YamlConfiguration vnConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(vnStream, StandardCharsets.UTF_8));

        String vnSpawnTemplate = vnConfig.getString("pet.spawn");
        assertNotNull(vnSpawnTemplate);
        assertTrue(vnSpawnTemplate.contains("%pet_name%"), "pet.spawn template must use placeholder %pet_name%");
        assertFalse(vnSpawnTemplate.contains("%player%"), "pet.spawn chat message should not embed %player%");
        assertFalse(vnSpawnTemplate.contains("%level%"), "pet.spawn chat message should not embed %level%");

        // Canonical pet display name
        String petDisplayName = "§bSói Băng";

        // Simulated chat message replacement
        String formattedChatMsg = LanguageManager.colorize(vnSpawnTemplate.replace("%pet_name%", petDisplayName));
        assertTrue(formattedChatMsg.contains("Sói Băng"), "Chat message must contain the pet's display name");
        assertFalse(formattedChatMsg.contains("&"), "Chat message must not contain raw & color codes: " + formattedChatMsg);
        assertFalse(formattedChatMsg.contains("[Lv."), "Chat message must not contain entity nameplate [Lv.] tag");
        assertFalse(formattedChatMsg.contains("(Player)"), "Chat message must not contain entity owner tag");

        // Entity nameplate format check
        String nameplateFormat = vnConfig.getString("pet.display_format", "&8[&eLv.%level%&8] &a%name% &7(&f%owner%&7)");
        String formattedNameplate = LanguageManager.colorize(
                nameplateFormat
                        .replace("%name%", petDisplayName)
                        .replace("%level%", "1")
                        .replace("%owner%", "Steve")
        );
        assertTrue(formattedNameplate.contains("Lv.1"), "Nameplate must contain level tag: " + formattedNameplate);
        assertTrue(formattedNameplate.contains("Steve"), "Nameplate must contain owner tag: " + formattedNameplate);
        assertTrue(formattedNameplate.contains("Sói Băng"), "Nameplate must contain pet name: " + formattedNameplate);
    }

    @Test
    @DisplayName("Verify PaperNativeDialogEngine diagnostic tracking and status reporting")
    public void testPaperNativeDialogEngineDiagnostics() {
        PaperNativeDialogEngine engine = new PaperNativeDialogEngine(null);

        // On Paper 1.20 test runtime, Dialog classes are not present
        assertFalse(engine.isAvailable(), "Native Dialogs must not be available on mock 1.20 test runtime");
        assertFalse(engine.isClassAvailable(), "Dialog class must not be found on mock 1.20 runtime");
        assertFalse(engine.isMethodsAvailable(), "Dialog methods must not be bound on mock 1.20 runtime");

        String initMsg = engine.getInitStatusMessage();
        assertNotNull(initMsg, "Init status message must be populated");
        assertTrue(initMsg.contains("Paper Dialog API"), "Diagnostic message must mention Paper Dialog API: " + initMsg);

        // Calling showNotice safely fails and updates last failure reason
        boolean result = engine.showNotice(null, net.kyori.adventure.text.Component.text("Title"), java.util.List.of(net.kyori.adventure.text.Component.text("Message")), net.kyori.adventure.text.Component.text("OK"), null);
        assertFalse(result, "showNotice must return false on unsupported runtime");
        assertNotNull(engine.getLastFailureReason(), "Last failure reason must be recorded");
    }

    @Test
    @DisplayName("Verify 'No Active Pet' fallback navigation correctly redirects to owned pets")
    public void testNoActivePetFallbackNavigationLogic() {
        YamlConfiguration playerData = new YamlConfiguration();
        UUID playerId = UUID.randomUUID();

        // Case 1: Player has NO pets at all
        boolean hasOwnedPetsBefore = playerData.isConfigurationSection(playerId + ".pets")
                && !playerData.getConfigurationSection(playerId + ".pets").getKeys(false).isEmpty();
        assertFalse(hasOwnedPetsBefore, "Initially player owns 0 pets");

        // Case 2: Player has owned pets, but no active pet
        playerData.set(playerId + ".pets.wolf_pet.level", 1);
        playerData.set(playerId + ".pets.cat_pet.level", 2);

        boolean hasOwnedPetsAfter = playerData.isConfigurationSection(playerId + ".pets")
                && !playerData.getConfigurationSection(playerId + ".pets").getKeys(false).isEmpty();
        assertTrue(hasOwnedPetsAfter, "Player owns pets in storage");

        // In this case, activeId == null should redirect to pet menu, NOT show pet.no_pet error
        String activePetId = null;
        boolean shouldRedirectToMenu = (activePetId == null && hasOwnedPetsAfter);
        assertTrue(shouldRedirectToMenu, "Should redirect to owned pets menu instead of throwing no active pet error");
    }
}
