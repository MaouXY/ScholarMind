package com.maou.apptemplateapi.module.admin.dto;

/**
 * AI 任务类型健康度。计数与平均耗时使用 Integer：
 * 全局 Jackson 会把 Long 序列化为字符串，而统计口径的数值前端需要按数字使用。
 */
public record AdminAiTaskHealthResponse(
        String taskType,
        Integer total,
        Integer success,
        Integer failed,
        Integer timeout,
        Double successRate,
        Integer avgDurationMs
) {
}
