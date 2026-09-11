package com.maou.apptemplateapi.module.admin.dto;

public record AdminOverviewResponse(
        Long userTotal,
        Long studentTotal,
        Long teacherTotal,
        Long activeUserTotal,
        Long literatureTotal,
        Long publishedLiteratureTotal,
        Long pendingReviewTotal,
        Long privateLiteratureTotal,
        Long fileTotal,
        Long fileSizeBytes,
        Long favoriteTotal,
        Long readingHistoryTotal,
        Long readingNoteTotal,
        Long qaTotal,
        Long ragDocumentTotal,
        Long aiTaskTotal,
        Long aiTaskSuccessTotal,
        Long aiTaskFailedTotal,
        Double aiSuccessRate,
        Long avgAiDurationMs
) {
}
