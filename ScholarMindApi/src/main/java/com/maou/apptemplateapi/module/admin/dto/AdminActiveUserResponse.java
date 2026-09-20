package com.maou.apptemplateapi.module.admin.dto;

/**
 * 活跃用户榜单项。计数与活跃分使用 Integer：全局 Jackson 会把 Long 序列化为字符串，前端按数字使用；
 * userId 是主键 ID，必须保持 Long 以走全局的 ID 转字符串保护。
 */
public record AdminActiveUserResponse(
        Long userId,
        String username,
        String nickname,
        String role,
        Integer uploadedLiteratureCount,
        Integer readingHistoryCount,
        Integer favoriteCount,
        Integer noteCount,
        Integer qaCount,
        Integer activityScore
) {
}
