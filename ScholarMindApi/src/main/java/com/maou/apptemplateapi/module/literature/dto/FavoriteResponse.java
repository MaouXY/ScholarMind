package com.maou.apptemplateapi.module.literature.dto;

import java.time.LocalDateTime;

public record FavoriteResponse(
        Long id,
        Long literatureId,
        LocalDateTime createdAt
) {
}
