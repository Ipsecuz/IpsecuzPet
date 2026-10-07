package org.ipsecuz.pet.model;

public enum ModelType {
    NONE,
    BETTERMODEL,
    MODELENGINE,
    AUTO;

    public static ModelType fromString(String name) {
        if (name == null) return AUTO;
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return AUTO;
        }
    }
}
