package org.ipsecuz.pet.dialog;

public enum DialogMode {
    AUTO,
    NATIVE,
    CHEST;

    public static DialogMode fromString(String str) {
        if (str == null) return AUTO;
        try {
            return valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            return AUTO;
        }
    }
}

