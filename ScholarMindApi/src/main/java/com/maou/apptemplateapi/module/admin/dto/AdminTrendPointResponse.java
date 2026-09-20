package com.maou.apptemplateapi.module.admin.dto;

/**
 * 趋势点位统计。计数使用 Integer：全局 Jackson 会把 Long 序列化为字符串，前端按数字使用。
 */
public record AdminTrendPointResponse(
        String date,
        Integer newUsers,
        Integer newLiteratures,
        Integer readingEvents,
        Integer qaEvents,
        Integer aiTasks
) {
}
