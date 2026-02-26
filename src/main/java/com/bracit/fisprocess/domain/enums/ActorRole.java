package com.bracit.fisprocess.domain.enums;

import org.jspecify.annotations.Nullable;

/**
 * Caller role used for period enforcement decisions.
 */
public enum ActorRole {
    FIS_ADMIN,
    FIS_ACCOUNTANT,
    FIS_READER;

    public static ActorRole fromHeader(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return FIS_ACCOUNTANT;
        }
        try {
            return ActorRole.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return FIS_ACCOUNTANT;
        }
    }
}
