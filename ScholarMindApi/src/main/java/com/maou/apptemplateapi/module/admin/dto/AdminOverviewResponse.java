package com.maou.apptemplateapi.module.admin.dto;

/**
 * 管理端总览指标。计数、耗时等统计数值统一使用 Integer：
 * 全局 Jackson 会把 Long 序列化为字符串（保护雪花 ID 精度），而统计口径的数值前端需要按数字使用。
 * ID 类字段仍保持 Long，不受此约定影响。
 */
public record AdminOverviewResponse(
        Integer userTotal,
        Integer studentTotal,
        Integer teacherTotal,
        Integer activeUserTotal,
        Integer literatureTotal,
        Integer publishedLiteratureTotal,
        Integer pendingReviewTotal,
        Integer privateLiteratureTotal,
        Integer fileTotal,
        // 文件总字节可能超过 int 范围：全局 Long→String 会影响数值字段，Double 可精确表示到 2^53
        Double fileSizeBytes,
        Integer favoriteTotal,
        Integer readingHistoryTotal,
        Integer readingNoteTotal,
        Integer qaTotal,
        Integer ragDocumentTotal,
        Integer aiTaskTotal,
        Integer aiTaskSuccessTotal,
        Integer aiTaskFailedTotal,
        Double aiSuccessRate,
        Integer avgAiDurationMs
) {
}
