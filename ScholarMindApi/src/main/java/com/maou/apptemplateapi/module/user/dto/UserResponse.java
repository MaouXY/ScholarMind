package com.maou.apptemplateapi.module.user.dto;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String username,
        String nickname,
        String email,
        String avatar,
        String role,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
