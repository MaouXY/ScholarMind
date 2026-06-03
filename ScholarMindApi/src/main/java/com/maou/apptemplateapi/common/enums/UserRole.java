package com.maou.apptemplateapi.common.enums;

import java.util.Arrays;

public enum UserRole {
    ADMIN;

    public static UserRole fromCode(String code) {
        return Arrays.stream(values())
                .filter(role -> role.name().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown role code: " + code));
    }
}

