package com.maou.apptemplateapi.module.user.dto;

public record AuthResponse(
        String token,
        long expiresIn,
        UserResponse user
) {
}
