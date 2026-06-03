package com.maou.apptemplateapi.module.literature.dto;

import java.time.LocalDateTime;

public record LiteratureCategoryResponse(
        Long id,
        String name,
        Long parentId,
        Integer sort,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
