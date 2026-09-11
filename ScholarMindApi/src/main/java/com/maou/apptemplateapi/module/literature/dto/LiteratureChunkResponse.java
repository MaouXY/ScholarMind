package com.maou.apptemplateapi.module.literature.dto;

/**
 * 文献正文分块：用于在线阅读页渲染真实正文与解析状态。
 */
public record LiteratureChunkResponse(
        Long id,
        Long literatureId,
        Integer chunkIndex,
        Integer pageNo,
        String content,
        Integer tokenCount,
        String vectorStoreStatus
) {
}
