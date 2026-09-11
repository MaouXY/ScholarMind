package com.maou.apptemplateapi.module.literature.dto;

import java.util.List;

public record LiteratureDetailResponse(
        LiteratureResponse literature,
        boolean favorited,
        ReadingHistoryResponse readingHistory,
        List<ReadingNoteResponse> recentNotes
) {
}
