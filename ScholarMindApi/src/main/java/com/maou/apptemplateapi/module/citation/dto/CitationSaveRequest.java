package com.maou.apptemplateapi.module.citation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 收藏引用请求。
 */
public record CitationSaveRequest(
        @NotNull Long literatureId,
        String style,
        @Size(max = 255) String note
) {
}
