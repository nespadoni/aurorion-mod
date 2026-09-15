package com.aurorion.areas.rules;

public enum Decision {
    INHERIT, ALLOW, DENY;

    public static Decision parse(String value) {
        return switch (value) {
            case "herdar" -> INHERIT;
            case "permitir" -> ALLOW;
            case "negar" -> DENY;
            default -> throw new IllegalArgumentException("Use permitir, negar ou herdar.");
        };
    }
}
