package com.maou.apptemplateapi.module.admin.dto;

public record AdminActiveUserResponse(
        Long userId,
        String username,
        String nickname,
        String role,
        Long uploadedLiteratureCount,
        Long readingHistoryCount,
        Long favoriteCount,
        Long noteCount,
        Long qaCount,
        Long activityScore
) {
}
