package com.maou.apptemplateapi.module.admin.dto;

/**
 * 文献向量重建结果（管理端一次性为已发布文献重建 Milvus 向量）。
 */
public record LiteratureVectorRebuildResponse(
        String collectionName,
        Integer literatureCount,
        Integer chunkCount,
        Integer successCount
) {
}
