package org.ipsecuz.pet;

import org.ipsecuz.pet.requirement.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class UniversalRequirementEngineTest {

    // 1. ALL requirements
    @Test
    @DisplayName("1. ALL requirements: All requirements must pass for group to be satisfied")
    public void testAllRequirements() {
        RequirementGroup allGroup = new RequirementGroup(RequirementGroupType.ALL);
        allGroup.addRequirement(new PetLevelRequirement(20));
        allGroup.addRequirement(new PetStarRequirement(2));

        RequirementContext passCtx = new RequirementContext(null, "dragon", 25, 3, null);
        RequirementCheckResult passResult = allGroup.evaluate(passCtx);
        assertTrue(passResult.isSatisfied());
        assertEquals(2, passResult.getSubResults().size());

        RequirementContext failCtx = new RequirementContext(null, "dragon", 15, 3, null);
        RequirementCheckResult failResult = allGroup.evaluate(failCtx);
        assertFalse(failResult.isSatisfied());
    }

    // 2. ONE_OF requirements
    @Test
    @DisplayName("2. ONE_OF requirements: At least one requirement must pass")
    public void testOneOfRequirements() {
        RequirementGroup oneOfGroup = new RequirementGroup(RequirementGroupType.ONE_OF);
        oneOfGroup.addRequirement(new PetLevelRequirement(50));
        oneOfGroup.addRequirement(new PetStarRequirement(2));

        // Pet is level 10 (fails lvl 50) but star 3 (passes star 2)
        RequirementContext ctx = new RequirementContext(null, "wolf", 10, 3, null);
        RequirementCheckResult res = oneOfGroup.evaluate(ctx);
        assertTrue(res.isSatisfied());
        assertNotNull(res.getSelectedAlternative());
        assertEquals(RequirementType.PET_STAR, res.getSelectedAlternative().getType());
    }

    // 3. Nested groups
    @Test
    @DisplayName("3. Nested groups: (Level >= 20) AND (Star >= 3 OR Star >= 1)")
    public void testNestedGroups() {
        RequirementGroup root = new RequirementGroup(RequirementGroupType.ALL);
        root.addRequirement(new PetLevelRequirement(20));

        RequirementGroup subOneOf = new RequirementGroup(RequirementGroupType.ONE_OF);
        subOneOf.addRequirement(new PetStarRequirement(5));
        subOneOf.addRequirement(new PetStarRequirement(2));
        root.addRequirement(subOneOf);

        RequirementContext passCtx = new RequirementContext(null, "allay", 20, 2, null);
        assertTrue(root.evaluate(passCtx).isSatisfied());

        RequirementContext failCtx = new RequirementContext(null, "allay", 19, 5, null);
        assertFalse(root.evaluate(failCtx).isSatisfied());
    }

    // 4. Money success
    @Test
    @DisplayName("4. Money requirement creation and amount validation")
    public void testMoneySuccess() {
        MoneyRequirement req = new MoneyRequirement(10000.0);
        assertEquals(10000.0, req.getAmount());
        assertEquals(RequirementType.MONEY, req.getType());
        assertTrue(req.isConsumable());
        assertTrue(req.getDisplay(null).contains("10000"));
    }

    // 5. Points success
    @Test
    @DisplayName("5. Points requirement creation and amount validation")
    public void testPointsSuccess() {
        PointsRequirement req = new PointsRequirement(150);
        assertEquals(150, req.getAmount());
        assertEquals(RequirementType.POINTS, req.getType());
        assertTrue(req.isConsumable());
        assertTrue(req.getDisplay(null).contains("150"));
    }

    // 6. Item success (Vanilla)
    @Test
    @DisplayName("6. Item requirement creation for Vanilla material")
    public void testItemSuccess() {
        ItemRequirement req = new ItemRequirement("DIAMOND", 16);
        assertEquals("DIAMOND", req.getId());
        assertEquals(16, req.getAmount());
        assertEquals(RequirementType.ITEM, req.getType());
        assertTrue(req.isConsumable());
        assertTrue(req.getDisplay(null).contains("16"));
    }

    // 7. Custom item success with display name
    @Test
    @DisplayName("7. Custom item with custom display string")
    public void testCustomItemSuccess() {
        ItemRequirement req = new ItemRequirement("itemsadder:ruby", 5, "§cRuby Quý", null, null);
        assertEquals("itemsadder:ruby", req.getId());
        assertEquals(5, req.getAmount());
        assertEquals("§cRuby Quý", req.getCustomDisplay());
        assertTrue(req.getDisplay(null).contains("Ruby Quý"));
    }

    // 8. ItemsAdder item identifier parsing
    @Test
    @DisplayName("8. ItemsAdder item identifier validation")
    public void testItemsAdderItem() {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "ITEM");
        map.put("id", "itemsadder:celestial_sword");
        map.put("amount", 2);

        Requirement req = RequirementParser.parseEntry(map);
        assertNotNull(req);
        assertTrue(req instanceof ItemRequirement);
        assertEquals("itemsadder:celestial_sword", ((ItemRequirement) req).getId());
    }

    // 9. Oraxen item identifier parsing
    @Test
    @DisplayName("9. Oraxen item identifier validation")
    public void testOraxenItem() {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "ITEM");
        map.put("id", "oraxen:ancient_soul");
        map.put("amount", 4);

        Requirement req = RequirementParser.parseEntry(map);
        assertNotNull(req);
        assertTrue(req instanceof ItemRequirement);
        assertEquals("oraxen:ancient_soul", ((ItemRequirement) req).getId());
    }

    // 10. Nexo item identifier parsing
    @Test
    @DisplayName("10. Nexo item identifier validation")
    public void testNexoItem() {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "ITEM");
        map.put("id", "nexo:celestial_core");
        map.put("amount", 1);

        Requirement req = RequirementParser.parseEntry(map);
        assertNotNull(req);
        assertTrue(req instanceof ItemRequirement);
        assertEquals("nexo:celestial_core", ((ItemRequirement) req).getId());
    }

    // 11. Multiple items all required
    @Test
    @DisplayName("11. Multiple required items in ALL group")
    public void testMultipleItems() {
        RequirementGroup group = new RequirementGroup(RequirementGroupType.ALL);
        group.addRequirement(new ItemRequirement("DIAMOND", 32));
        group.addRequirement(new ItemRequirement("NETHERITE_INGOT", 4));
        group.addRequirement(new ItemRequirement("itemsadder:ruby", 8));
        group.addRequirement(new ItemRequirement("oraxen:ancient_soul", 2));

        assertEquals(4, group.getRequirements().size());
        assertTrue(group.isConsumable());
    }

    // 12. Missing item failure
    @Test
    @DisplayName("12. Missing item generates proper unsatisfied check result")
    public void testMissingItem() {
        ItemRequirement req = new ItemRequirement("NETHERITE_INGOT", 5);
        RequirementCheckResult res = req.evaluate(null);
        assertFalse(res.isSatisfied());
        assertEquals(5.0, res.getRequiredAmount());
        assertEquals(0.0, res.getCurrentAmount());
        assertFalse(res.getMissingMessage().isEmpty());
    }

    // 13. Insufficient money failure
    @Test
    @DisplayName("13. Insufficient money generates proper unsatisfied check result")
    public void testInsufficientMoney() {
        MoneyRequirement req = new MoneyRequirement(50000.0);
        RequirementCheckResult res = req.evaluate(null);
        assertFalse(res.isSatisfied());
        assertEquals(50000.0, res.getRequiredAmount());
        assertFalse(res.getMissingMessage().isEmpty());
    }

    // 14. Insufficient points failure
    @Test
    @DisplayName("14. Insufficient points generates proper unsatisfied check result")
    public void testInsufficientPoints() {
        PointsRequirement req = new PointsRequirement(200);
        RequirementCheckResult res = req.evaluate(null);
        assertFalse(res.isSatisfied());
        assertEquals(200.0, res.getRequiredAmount());
        assertFalse(res.getMissingMessage().isEmpty());
    }

    // 15. Missing one ALL requirement fails entire group
    @Test
    @DisplayName("15. Missing one requirement in ALL group fails entire group")
    public void testMissingOneAllRequirement() {
        RequirementGroup group = new RequirementGroup(RequirementGroupType.ALL);
        group.addRequirement(new PetLevelRequirement(10));
        group.addRequirement(new PetStarRequirement(5)); // fails

        RequirementContext ctx = new RequirementContext(null, "wolf", 20, 2, null);
        RequirementCheckResult res = group.evaluate(ctx);
        assertFalse(res.isSatisfied());
    }

    // 16. Valid alternative in ONE_OF selects first satisfied alternative
    @Test
    @DisplayName("16. Valid alternative in ONE_OF selects the first matching option in config order")
    public void testValidAlternativeInOneOf() {
        RequirementGroup group = new RequirementGroup(RequirementGroupType.ONE_OF);
        group.addRequirement(new PetLevelRequirement(50)); // fails (lvl 20)
        group.addRequirement(new PetLevelRequirement(15)); // passes 1st
        group.addRequirement(new PetStarRequirement(1));   // passes 2nd

        RequirementContext ctx = new RequirementContext(null, "wolf", 20, 2, null);
        RequirementCheckResult res = group.evaluate(ctx);
        assertTrue(res.isSatisfied());
        assertNotNull(res.getSelectedAlternative());
        assertEquals(15.0, res.getSelectedAlternative().getRequiredAmount());
    }

    // 17. No valid alternative in ONE_OF fails entire group
    @Test
    @DisplayName("17. No valid alternative in ONE_OF fails entire group")
    public void testNoValidAlternative() {
        RequirementGroup group = new RequirementGroup(RequirementGroupType.ONE_OF);
        group.addRequirement(new PetLevelRequirement(50));
        group.addRequirement(new PetStarRequirement(5));

        RequirementContext ctx = new RequirementContext(null, "wolf", 20, 2, null);
        RequirementCheckResult res = group.evaluate(ctx);
        assertFalse(res.isSatisfied());
        assertNull(res.getSelectedAlternative());
    }

    // 18. Transaction consumes ONLY selected alternative in ONE_OF
    @Test
    @DisplayName("18. Transaction planning consumes ONLY the selected alternative")
    public void testTransactionConsumesOnlySelectedAlternative() {
        RequirementGroup group = new RequirementGroup(RequirementGroupType.ONE_OF);
        group.addRequirement(new PetLevelRequirement(50)); // fails (non-consumable)

        final AtomicBoolean step1Planned = new AtomicBoolean(false);
        final AtomicBoolean step2Planned = new AtomicBoolean(false);

        group.addRequirement(new Requirement() {
            @Override public RequirementType getType() { return RequirementType.MONEY; }
            @Override public boolean isConsumable() { return true; }
            @Override public String getDisplay(RequirementContext ctx) { return "Alternative 1"; }
            @Override public RequirementCheckResult evaluate(RequirementContext ctx) {
                return RequirementCheckResult.single(true, getType(), "Alternative 1", 10, 10, "");
            }
            @Override public void planTransaction(RequirementContext ctx, RequirementTransaction tx) {
                step1Planned.set(true);
                tx.addStep(new RequirementTransaction.TransactionStep() {
                    @Override public boolean execute(org.bukkit.entity.Player player) { return true; }
                    @Override public void rollback(org.bukkit.entity.Player player) {}
                    @Override public String getDescription() { return "Step 1"; }
                });
            }
        });

        group.addRequirement(new Requirement() {
            @Override public RequirementType getType() { return RequirementType.POINTS; }
            @Override public boolean isConsumable() { return true; }
            @Override public String getDisplay(RequirementContext ctx) { return "Alternative 2"; }
            @Override public RequirementCheckResult evaluate(RequirementContext ctx) {
                return RequirementCheckResult.single(true, getType(), "Alternative 2", 20, 20, "");
            }
            @Override public void planTransaction(RequirementContext ctx, RequirementTransaction tx) {
                step2Planned.set(true);
            }
        });

        RequirementContext ctx = new RequirementContext(null, "wolf", 20, 1, null);
        RequirementTransaction tx = new RequirementTransaction();
        group.planTransaction(ctx, tx);

        assertTrue(step1Planned.get(), "First satisfied alternative must be planned");
        assertFalse(step2Planned.get(), "Subsequent alternative must NOT be planned");
        assertEquals(1, tx.size());
    }

    // 19. Transaction rollback executes in reverse order upon failure
    @Test
    @DisplayName("19. Transaction failure triggers reverse rollback of all committed steps")
    public void testTransactionRollback() {
        RequirementTransaction tx = new RequirementTransaction();
        List<String> events = new ArrayList<>();

        tx.addStep(new RequirementTransaction.TransactionStep() {
            @Override public boolean execute(org.bukkit.entity.Player player) {
                events.add("EXEC_1");
                return true;
            }
            @Override public void rollback(org.bukkit.entity.Player player) {
                events.add("ROLLBACK_1");
            }
            @Override public String getDescription() { return "Step 1"; }
        });

        tx.addStep(new RequirementTransaction.TransactionStep() {
            @Override public boolean execute(org.bukkit.entity.Player player) {
                events.add("EXEC_2");
                return true;
            }
            @Override public void rollback(org.bukkit.entity.Player player) {
                events.add("ROLLBACK_2");
            }
            @Override public String getDescription() { return "Step 2"; }
        });

        tx.addStep(new RequirementTransaction.TransactionStep() {
            @Override public boolean execute(org.bukkit.entity.Player player) {
                events.add("EXEC_3_FAIL");
                return false; // Step 3 fails!
            }
            @Override public void rollback(org.bukkit.entity.Player player) {
                events.add("ROLLBACK_3");
            }
            @Override public String getDescription() { return "Step 3"; }
        });

        boolean success = tx.execute(null);
        assertFalse(success, "Transaction must fail when step 3 returns false");

        // Verify execution and reverse rollback
        assertEquals(Arrays.asList("EXEC_1", "EXEC_2", "EXEC_3_FAIL", "ROLLBACK_2", "ROLLBACK_1"), events);
    }

    // 20. Evolution using new requirement engine
    @Test
    @DisplayName("20. Evolution configuration parses correctly with all and one_of")
    public void testEvolutionConfigParsing() {
        Map<String, Object> map = new HashMap<>();
        List<Map<String, Object>> allList = new ArrayList<>();
        Map<String, Object> lvlReq = new HashMap<>();
        lvlReq.put("type", "PET_LEVEL");
        lvlReq.put("value", 20);
        allList.add(lvlReq);

        Map<String, Object> diaReq = new HashMap<>();
        diaReq.put("type", "ITEM");
        diaReq.put("id", "DIAMOND");
        diaReq.put("amount", 16);
        allList.add(diaReq);
        map.put("all", allList);

        List<Map<String, Object>> oneOfList = new ArrayList<>();
        Map<String, Object> moneyReq = new HashMap<>();
        moneyReq.put("type", "MONEY");
        moneyReq.put("amount", 10000);
        oneOfList.add(moneyReq);

        Map<String, Object> pointsReq = new HashMap<>();
        pointsReq.put("type", "POINTS");
        pointsReq.put("amount", 100);
        oneOfList.add(pointsReq);
        map.put("one_of", oneOfList);

        RequirementGroup root = RequirementParser.parse(map);
        assertEquals(RequirementType.GROUP, root.getType());
        assertEquals(RequirementGroupType.ALL, root.getGroupType());
        assertEquals(2, root.getRequirements().size()); // all subgroup and one_of subgroup
    }

    // 21. Skills using new requirement engine
    @Test
    @DisplayName("21. Skill configuration with requirements block parses correctly")
    public void testSkillConfigParsing() {
        Map<String, Object> sec = new HashMap<>();
        Map<String, Object> reqs = new HashMap<>();
        List<Map<String, Object>> allList = new ArrayList<>();
        Map<String, Object> lvlReq = new HashMap<>();
        lvlReq.put("type", "PET_LEVEL");
        lvlReq.put("value", 25);
        allList.add(lvlReq);
        reqs.put("all", allList);
        sec.put("requirements", reqs);

        RequirementGroup group = RequirementParser.parse(sec);
        assertEquals(1, group.getRequirements().size());
        assertEquals(RequirementType.PET_LEVEL, group.getRequirements().get(0).getType());
    }

    // 22. Hatching using new requirement engine
    @Test
    @DisplayName("22. Hatching configuration parses correctly")
    public void testHatchingConfigParsing() {
        Map<String, Object> sec = new HashMap<>();
        Map<String, Object> reqs = new HashMap<>();
        List<Map<String, Object>> allList = new ArrayList<>();
        Map<String, Object> itm = new HashMap<>();
        itm.put("type", "ITEM");
        itm.put("id", "DIAMOND");
        itm.put("amount", 5);
        allList.add(itm);
        reqs.put("all", allList);
        sec.put("requirements", reqs);

        RequirementGroup group = RequirementParser.parse(sec);
        assertEquals(1, group.getRequirements().size());
        assertEquals(RequirementType.ITEM, group.getRequirements().get(0).getType());
    }

    // 23. Legacy config compatibility
    @Test
    @DisplayName("23. Legacy config fields (cost_money, cost_diamonds, min_level) parse seamlessly")
    public void testLegacyConfigCompatibility() {
        Map<String, Object> legacy = new HashMap<>();
        legacy.put("min_level", 40);
        legacy.put("cost_money", 30000);
        legacy.put("cost_diamonds", 32);
        legacy.put("cost_points", 50);
        legacy.put("cost_netherite", 2);
        legacy.put("cost_items", Collections.singletonList("EMERALD:10"));

        RequirementGroup group = RequirementParser.parse(legacy);
        assertEquals(RequirementGroupType.ALL, group.getGroupType());
        assertEquals(6, group.getRequirements().size());

        Set<RequirementType> types = new HashSet<>();
        for (Requirement r : group.getRequirements()) {
            types.add(r.getType());
        }
        assertTrue(types.contains(RequirementType.PET_LEVEL));
        assertTrue(types.contains(RequirementType.MONEY));
        assertTrue(types.contains(RequirementType.POINTS));
        assertTrue(types.contains(RequirementType.ITEM));
    }

    // 24. Invalid configuration rejection
    @Test
    @DisplayName("24. Invalid configuration (unknown type or missing ID) is skipped safely")
    public void testInvalidConfiguration() {
        Map<String, Object> unknown = new HashMap<>();
        unknown.put("type", "UNKNOWN_TYPE_123");
        unknown.put("amount", 100);
        assertNull(RequirementParser.parseEntry(unknown));

        Map<String, Object> missingId = new HashMap<>();
        missingId.put("type", "ITEM");
        missingId.put("amount", 5);
        assertNull(RequirementParser.parseEntry(missingId));
    }

    // 25. Negative and zero amounts rejected
    @Test
    @DisplayName("25. Negative and zero amounts are rejected by constructors and parser")
    public void testNegativeAndZeroAmounts() {
        assertThrows(IllegalArgumentException.class, () -> new MoneyRequirement(-10));
        assertThrows(IllegalArgumentException.class, () -> new MoneyRequirement(0));
        assertThrows(IllegalArgumentException.class, () -> new PointsRequirement(-5));
        assertThrows(IllegalArgumentException.class, () -> new PointsRequirement(0));
        assertThrows(IllegalArgumentException.class, () -> new ItemRequirement("DIAMOND", -1));
        assertThrows(IllegalArgumentException.class, () -> new ItemRequirement("DIAMOND", 0));

        Map<String, Object> negMoney = new HashMap<>();
        negMoney.put("type", "MONEY");
        negMoney.put("amount", -500);
        assertNull(RequirementParser.parseEntry(negMoney));
    }

    // 26. GUI status calculation and renderer
    @Test
    @DisplayName("26. GUI status calculation renders checkmarks, selected options and overall state")
    public void testGuiStatusCalculation() {
        RequirementGroup root = new RequirementGroup(RequirementGroupType.ALL);
        root.addRequirement(new PetLevelRequirement(20));

        RequirementGroup oneOf = new RequirementGroup(RequirementGroupType.ONE_OF);
        oneOf.addRequirement(new PetStarRequirement(5)); // fails
        oneOf.addRequirement(new PetStarRequirement(1)); // passes
        root.addRequirement(oneOf);

        RequirementContext ctx = new RequirementContext(null, "dragon", 25, 2, null);
        RequirementCheckResult res = root.evaluate(ctx);
        assertTrue(res.isSatisfied());

        List<String> lines = RequirementGuiRenderer.renderToStrings(res);
        assertNotNull(lines);
        assertFalse(lines.isEmpty());

        boolean hasPass = false;
        boolean hasSelected = false;
        boolean hasReady = false;

        for (String line : lines) {
            if (line.contains("[✔]")) hasPass = true;
            if (line.contains("(Đã chọn)")) hasSelected = true;
            if (line.contains("ĐỦ ĐIỀU KIỆN")) hasReady = true;
        }

        assertTrue(hasPass, "GUI must contain checkmark for passed condition");
        assertTrue(hasSelected, "GUI must indicate the selected alternative");
        assertTrue(hasReady, "GUI must display READY status when satisfied");
    }
}
