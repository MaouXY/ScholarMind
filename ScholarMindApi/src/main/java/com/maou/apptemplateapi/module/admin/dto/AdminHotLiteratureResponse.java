package com.maou.apptemplateapi.module.admin.dto;

public record AdminHotLiteratureResponse(
        Long literatureId,
        String title,
        String author,
        String categoryName,
        String status,
        String visibility,
        Integer readCount,
        Integer downloadCount,
        Long favoriteCount,
        Long noteCount
) {
}
