package com.maou.apptemplateapi.module.admin.dto;

import java.util.List;

/**
 * AI 调用概要。数值字段统一使用 Integer：全局 Jackson 会把 Long 序列化为字符串，
 * 而统计口径的数值前端需要按数字使用。
 */
public record AdminAiTaskSummaryResponse(
        Integer total,
        Integer success,
        Integer failed,
        Integer timeout,
        Integer running,
        Double successRate,
        Integer avgDurationMs,
        List<AdminAiTaskHealthResponse> items
) {
}
