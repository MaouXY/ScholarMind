package com.maou.apptemplateapi.module.literature.dto;

/**
 * 检索消融对比中的单条命中（用于展示「纯关键词 / 纯向量 / RRF 融合」三者的排序差异）。
 */
public record RetrievalHit(
        Long chunkId,
        Long literatureId,
        String literatureTitle,
        Integer chunkIndex,
        String content,
        double score,
        int rank,
        Integer sparseRank,
        Integer denseRank,
        Double rrfScore
) {
}
