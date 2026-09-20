package com.maou.apptemplateapi.module.literature.dto;

/**
 * 检索命中的文献分块。
 *
 * 在混合检索下额外携带两路召回的名次与 RRF 融合得分，便于前端展示「为什么这个分块排在前面」：
 * - sparseRank：BM25 稀疏（关键词）召回名次，未命中为 null
 * - denseRank：Milvus 向量稠密召回名次，未命中为 null
 * - rrfScore：RRF 融合得分（Σ 1/(k+rank)，越大越靠前）
 * - retriever：本次结果来源（HYBRID / SPARSE / DENSE）
 */
public record LiteratureReferenceChunkResponse(
        Long id,
        Long literatureId,
        Integer chunkIndex,
        String content,
        double score,
        Integer sparseRank,
        Integer denseRank,
        Double rrfScore,
        String retriever
) {

    public LiteratureReferenceChunkResponse(Long id, Long literatureId, Integer chunkIndex, String content, double score) {
        this(id, literatureId, chunkIndex, content, score, null, null, null, null);
    }
}
