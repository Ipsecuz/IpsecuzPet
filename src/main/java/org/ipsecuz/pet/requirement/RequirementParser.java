package org.ipsecuz.pet.requirement;

import org.bukkit.configuration.ConfigurationSection;
import org.ipsecuz.pet.IpsecuzPet;

import java.util.*;
import java.util.logging.Logger;

/**
 * Data-driven parser for requirement configurations.
 * Supports modern structured syntax (all, one_of, nested groups) and
 * transparently adapts legacy configuration formats (cost_money, cost_diamonds, etc.).
 */
public class RequirementParser {

    private static Logger getLogger() {
        IpsecuzPet plugin = IpsecuzPet.getInstance();
        return plugin != null ? plugin.getLogger() : Logger.getLogger("RequirementParser");
    }

    /**
     * Parses a ConfigurationSection into a RequirementGroup.
     */
    public static RequirementGroup parse(ConfigurationSection section) {
        if (section == null) {
            return new RequirementGroup(RequirementGroupType.ALL);
        }

        // Check if there is an inner "requirements" section
        if (section.isConfigurationSection("requirements")) {
            section = section.getConfigurationSection("requirements");
        }

        boolean hasAll = section.contains("all");
        boolean hasOneOf = section.contains("one_of");

        if (hasAll && hasOneOf) {
            RequirementGroup root = new RequirementGroup(RequirementGroupType.ALL);
            RequirementGroup allGroup = parseGroup(section, "all", RequirementGroupType.ALL);
            RequirementGroup oneOfGroup = parseGroup(section, "one_of", RequirementGroupType.ONE_OF);
            root.addRequirement(allGroup);
            root.addRequirement(oneOfGroup);
            return root;
        } else if (hasAll) {
            return parseGroup(section, "all", RequirementGroupType.ALL);
        } else if (hasOneOf) {
            return parseGroup(section, "one_of", RequirementGroupType.ONE_OF);
        }

        // Fallback: Check for legacy configuration keys
        return parseLegacy(section);
    }

    /**
     * Parses a Map into a RequirementGroup.
     */
    @SuppressWarnings("unchecked")
    public static RequirementGroup parse(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return new RequirementGroup(RequirementGroupType.ALL);
        }

        if (map.containsKey("requirements") && map.get("requirements") instanceof Map) {
            map = (Map<String, Object>) map.get("requirements");
        }

        boolean hasAll = map.containsKey("all");
        boolean hasOneOf = map.containsKey("one_of");

        if (hasAll && hasOneOf) {
            RequirementGroup root = new RequirementGroup(RequirementGroupType.ALL);
            RequirementGroup allGroup = parseGroupFromList((List<?>) map.get("all"), RequirementGroupType.ALL);
            RequirementGroup oneOfGroup = parseGroupFromList((List<?>) map.get("one_of"), RequirementGroupType.ONE_OF);
            root.addRequirement(allGroup);
            root.addRequirement(oneOfGroup);
            return root;
        } else if (hasAll) {
            return parseGroupFromList((List<?>) map.get("all"), RequirementGroupType.ALL);
        } else if (hasOneOf) {
            return parseGroupFromList((List<?>) map.get("one_of"), RequirementGroupType.ONE_OF);
        }

        return parseLegacyMap(map);
    }

    private static RequirementGroup parseGroup(ConfigurationSection section, String key, RequirementGroupType groupType) {
        RequirementGroup group = new RequirementGroup(groupType);
        List<?> rawList = section.getList(key);
        if (rawList != null) {
            for (Object obj : rawList) {
                Requirement req = parseEntry(obj);
                if (req != null) {
                    group.addRequirement(req);
                }
            }
        }
        return group;
    }

    private static RequirementGroup parseGroupFromList(List<?> rawList, RequirementGroupType groupType) {
        RequirementGroup group = new RequirementGroup(groupType);
        if (rawList != null) {
            for (Object obj : rawList) {
                Requirement req = parseEntry(obj);
                if (req != null) {
                    group.addRequirement(req);
                }
            }
        }
        return group;
    }

    @SuppressWarnings("unchecked")
    public static Requirement parseEntry(Object obj) {
        if (obj == null) return null;

        Map<String, Object> map = null;
        if (obj instanceof Map) {
            map = (Map<String, Object>) obj;
        } else if (obj instanceof ConfigurationSection sec) {
            map = sec.getValues(false);
        } else {
            getLogger().warning("[RequirementParser] Invalid entry format: " + obj);
            return null;
        }

        // Check for nested groups
        if (map.containsKey("all") && map.get("all") instanceof List) {
            return parseGroupFromList((List<?>) map.get("all"), RequirementGroupType.ALL);
        }
        if (map.containsKey("one_of") && map.get("one_of") instanceof List) {
            return parseGroupFromList((List<?>) map.get("one_of"), RequirementGroupType.ONE_OF);
        }

        String rawType = getString(map, "type");
        if (rawType == null || rawType.trim().isEmpty()) {
            getLogger().warning("[RequirementParser] Requirement entry missing 'type': " + map);
            return null;
        }

        String typeStr = rawType.trim().toUpperCase();
        try {
            switch (typeStr) {
                case "PET_LEVEL":
                case "LEVEL": {
                    int val = getInt(map, "value", getInt(map, "amount", 1));
                    if (val <= 0) {
                        getLogger().warning("[RequirementParser] Invalid PET_LEVEL value <= 0: " + val);
                        return null;
                    }
                    return new PetLevelRequirement(val);
                }
                case "PET_STAR":
                case "STAR":
                case "STARS": {
                    int val = getInt(map, "value", getInt(map, "amount", 1));
                    if (val <= 0) {
                        getLogger().warning("[RequirementParser] Invalid PET_STAR value <= 0: " + val);
                        return null;
                    }
                    return new PetStarRequirement(val);
                }
                case "MONEY": {
                    double amt = getDouble(map, "amount", getDouble(map, "value", 0));
                    if (amt <= 0) {
                        getLogger().warning("[RequirementParser] Invalid MONEY amount <= 0: " + amt);
                        return null;
                    }
                    return new MoneyRequirement(amt);
                }
                case "POINTS": {
                    int amt = getInt(map, "amount", getInt(map, "value", 0));
                    if (amt <= 0) {
                        getLogger().warning("[RequirementParser] Invalid POINTS amount <= 0: " + amt);
                        return null;
                    }
                    return new PointsRequirement(amt);
                }
                case "ITEM": {
                    String id = getString(map, "id", getString(map, "material", null));
                    if (id == null || id.trim().isEmpty()) {
                        getLogger().warning("[RequirementParser] ITEM requirement missing 'id': " + map);
                        return null;
                    }
                    int amt = getInt(map, "amount", 1);
                    if (amt <= 0) {
                        getLogger().warning("[RequirementParser] Invalid ITEM amount <= 0: " + amt);
                        return null;
                    }
                    String display = getString(map, "display", null);
                    Integer cmd = null;
                    Map<String, String> pdc = null;

                    if (map.containsKey("exact") && map.get("exact") instanceof Map exactMap) {
                        if (exactMap.containsKey("custom_model_data")) {
                            cmd = parseInteger(exactMap.get("custom_model_data"));
                        }
                        if (exactMap.containsKey("pdc") && exactMap.get("pdc") instanceof Map pdcMap) {
                            pdc = new HashMap<>();
                            for (Object k : pdcMap.keySet()) {
                                if (k != null && pdcMap.get(k) != null) {
                                    pdc.put(k.toString(), pdcMap.get(k).toString());
                                }
                            }
                        }
                    }
                    return new ItemRequirement(id, amt, display, cmd, pdc);
                }
                case "PERMISSION": {
                    String perm = getString(map, "permission", getString(map, "node", getString(map, "value", null)));
                    if (perm == null || perm.trim().isEmpty()) {
                        getLogger().warning("[RequirementParser] PERMISSION requirement missing permission node: " + map);
                        return null;
                    }
                    return new PermissionRequirement(perm);
                }
                case "PET_OWNED": {
                    String petId = getString(map, "id", getString(map, "pet", getString(map, "value", null)));
                    if (petId == null || petId.trim().isEmpty()) {
                        getLogger().warning("[RequirementParser] PET_OWNED requirement missing pet id: " + map);
                        return null;
                    }
                    return new PetOwnershipRequirement(petId, true);
                }
                case "PET_NOT_OWNED": {
                    String petId = getString(map, "id", getString(map, "pet", getString(map, "value", null)));
                    if (petId == null || petId.trim().isEmpty()) {
                        getLogger().warning("[RequirementParser] PET_NOT_OWNED requirement missing pet id: " + map);
                        return null;
                    }
                    return new PetOwnershipRequirement(petId, false);
                }
                default:
                    getLogger().warning("[RequirementParser] Unknown requirement type: '" + typeStr + "' in entry: " + map);
                    return null;
            }
        } catch (Exception ex) {
            getLogger().warning("[RequirementParser] Failed to parse requirement entry: " + map + " (" + ex.getMessage() + ")");
            return null;
        }
    }

    /**
     * Backward-compatibility adapter for legacy configuration sections.
     */
    public static RequirementGroup parseLegacy(ConfigurationSection sec) {
        RequirementGroup group = new RequirementGroup(RequirementGroupType.ALL);
        if (sec == null) return group;

        // Level
        int minLvl = sec.getInt("min_level", sec.getInt("req_level", sec.getInt("level", sec.getInt("requirements.level", 0))));
        if (minLvl > 0) {
            group.addRequirement(new PetLevelRequirement(minLvl));
        }

        // Money
        double money = sec.getDouble("cost_money", sec.getDouble("requirements.money", 0));
        if (money <= 0 && "MONEY".equalsIgnoreCase(sec.getString("currency"))) {
            money = sec.getDouble("price", 0);
        }
        if (money > 0) {
            group.addRequirement(new MoneyRequirement(money));
        }

        // Points
        int points = sec.getInt("cost_points", sec.getInt("requirements.points", 0));
        if (points <= 0 && "POINTS".equalsIgnoreCase(sec.getString("currency"))) {
            points = sec.getInt("price", 0);
        }
        if (points > 0) {
            group.addRequirement(new PointsRequirement(points));
        }

        // Diamonds
        int diamonds = sec.getInt("cost_diamonds", 0);
        if (diamonds > 0) {
            group.addRequirement(new ItemRequirement("DIAMOND", diamonds));
        }

        // Netherite
        int netherite = sec.getInt("cost_netherite", 0);
        if (netherite > 0) {
            group.addRequirement(new ItemRequirement("NETHERITE_INGOT", netherite));
        }

        // Cost items list
        List<String> items = new ArrayList<>();
        if (sec.contains("cost_items")) {
            if (sec.isList("cost_items")) items.addAll(sec.getStringList("cost_items"));
            else items.add(sec.getString("cost_items"));
        }
        if (sec.contains("requirements.items")) {
            items.addAll(sec.getStringList("requirements.items"));
        }
        if (items.isEmpty() && "ITEM".equalsIgnoreCase(sec.getString("currency")) && sec.contains("material")) {
            String mat = sec.getString("material", "DIAMOND");
            int amt = sec.getInt("price", 1);
            items.add(mat + ":" + amt);
        }

        for (String itemStr : items) {
            if (itemStr == null || itemStr.trim().isEmpty()) continue;
            String[] parts = itemStr.split(":");
            String id = parts[0].trim();
            int amt = 1;
            if (parts.length > 1) {
                try {
                    amt = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException ignored) {}
            }
            if (!id.isEmpty() && amt > 0) {
                group.addRequirement(new ItemRequirement(id, amt));
            }
        }

        return group;
    }

    @SuppressWarnings("unchecked")
    private static RequirementGroup parseLegacyMap(Map<String, Object> map) {
        RequirementGroup group = new RequirementGroup(RequirementGroupType.ALL);
        if (map == null) return group;

        int minLvl = getInt(map, "min_level", getInt(map, "req_level", getInt(map, "level", 0)));
        if (minLvl > 0) group.addRequirement(new PetLevelRequirement(minLvl));

        double money = getDouble(map, "cost_money", 0);
        if (money > 0) group.addRequirement(new MoneyRequirement(money));

        int points = getInt(map, "cost_points", 0);
        if (points > 0) group.addRequirement(new PointsRequirement(points));

        int diamonds = getInt(map, "cost_diamonds", 0);
        if (diamonds > 0) group.addRequirement(new ItemRequirement("DIAMOND", diamonds));

        int netherite = getInt(map, "cost_netherite", 0);
        if (netherite > 0) group.addRequirement(new ItemRequirement("NETHERITE_INGOT", netherite));

        List<String> items = new ArrayList<>();
        if (map.containsKey("cost_items")) {
            Object ci = map.get("cost_items");
            if (ci instanceof List<?> list) {
                for (Object o : list) if (o != null) items.add(o.toString());
            } else if (ci != null) {
                items.add(ci.toString());
            }
        }
        for (String itemStr : items) {
            if (itemStr == null || itemStr.trim().isEmpty()) continue;
            String[] parts = itemStr.split(":");
            String id = parts[0].trim();
            int amt = 1;
            if (parts.length > 1) {
                try {
                    amt = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException ignored) {}
            }
            if (!id.isEmpty() && amt > 0) {
                group.addRequirement(new ItemRequirement(id, amt));
            }
        }

        return group;
    }

    private static String getString(Map<String, Object> map, String key) {
        return getString(map, key, null);
    }

    private static String getString(Map<String, Object> map, String key, String def) {
        Object val = map.get(key);
        return val != null ? val.toString() : def;
    }

    private static int getInt(Map<String, Object> map, String key, int def) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val != null) {
            try { return Integer.parseInt(val.toString()); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    private static double getDouble(Map<String, Object> map, String key, double def) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.doubleValue();
        if (val != null) {
            try { return Double.parseDouble(val.toString()); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    private static Integer parseInteger(Object val) {
        if (val instanceof Number n) return n.intValue();
        if (val != null) {
            try { return Integer.parseInt(val.toString()); } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
