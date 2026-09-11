package com.maou.apptemplateapi.module.admin.dto;

import java.util.List;

/**
 * AI 输出的运营分析报告结构（用于结构化输出校验）。
 */
public record StructuredOperationReport(
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
