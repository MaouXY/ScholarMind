package com.maou.apptemplateapi.module.literature.dto;

import java.time.LocalDateTime;

/**
 * 收藏夹响应。{@code favoriteCount} 为该夹下的收藏数量，供侧栏直接展示。
 */
public record FavoriteFolderResponse(
        Long id,
        String name,
        String description,
        Integer sort,
        Integer favoriteCount,
        LocalDateTime createdAt
) {
}
