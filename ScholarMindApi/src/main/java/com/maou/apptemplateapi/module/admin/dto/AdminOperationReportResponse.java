package com.maou.apptemplateapi.module.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 运营分析报告响应：AI 生成失败时由本地统计兜底，通过 fallbackUsed 标识。
 */
public record AdminOperationReportResponse(
        LocalDateTime generatedAt,
        Integer days,
        Boolean fallbackUsed,
        String summary,
        List<String> highlights,
        List<String> risks,
        List<String> suggestions,
        List<Section> sections
) {

    public record Section(
            String title,
            String content
    ) {
    }
}
