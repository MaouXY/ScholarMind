package com.maou.apptemplateapi.module.literature.dto;

import java.time.LocalDateTime;

/**
 * 我的书房——收藏条目（带文献基本信息，供列表直接展示）。
 * <p>文献上传即发布后不再有可见性与审核状态，条目上的 status / visibility 已移除。</p>
 * <p>{@code folderId} / {@code folderName} 标明该收藏所属收藏夹，为空表示未分类。</p>
 */
public record FavoriteResponse(
        Long id,
        Long literatureId,
        String literatureTitle,
        String author,
        String categoryName,
        Integer readCount,
        Long folderId,
        String folderName,
        LocalDateTime createdAt
) {
}
