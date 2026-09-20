package com.maou.apptemplateapi.module.literature.service.retrieval;

import com.maou.apptemplateapi.module.literature.entity.LiteratureChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BM25 稀疏检索单元测试：验证精确匹配优先、稀有词权重更高、无关分块被过滤。
 */
class SparseRetrieverTest {

    private final SparseRetriever retriever = new SparseRetriever();

    @Test
    void ranksExactTermMatchFirst() {
        List<LiteratureChunk> corpus = List.of(
                chunk(1L, "本文研究高校数字图书馆的混合推荐方法，结合关键词与用户行为。"),
                chunk(2L, "本文讨论图书馆的借阅流程管理与读者服务。"),
                chunk(3L, "混合检索通过 BM25 与向量召回融合，避免纯向量召回在专有名词上的失手。"));

        List<SparseRetriever.ScoredChunk> ranked = retriever.rank("BM25 向量召回 混合检索", corpus, 3, 1.2, 0.75);

        assertTrue(!ranked.isEmpty(), "应当检索到分块");
        assertEquals(3L, ranked.get(0).chunkId(), "含 BM25 与向量召回的分块应排第一");
        assertEquals(1, ranked.get(0).rank());
    }

    @Test
    void filtersChunksWithoutMatchingTerms() {
        List<LiteratureChunk> corpus = List.of(
                chunk(10L, "图书馆借阅流程与读者服务说明。"),
                chunk(11L, "知识图谱在文献推荐中的应用。"));

        List<SparseRetriever.ScoredChunk> ranked = retriever.rank("知识图谱", corpus, 5, 1.2, 0.75);

        assertEquals(1, ranked.size(), "只应命中包含目标词的分块");
        assertEquals(11L, ranked.get(0).chunkId());
    }

    @Test
    void weightsRareTermsHigherThanCommonOnes() {
        List<LiteratureChunk> corpus = List.of(
                chunk(20L, "文献检索系统设计。" ),
                chunk(21L, "文献检索系统设计与 RRF 融合排序。"));

        List<SparseRetriever.ScoredChunk> ranked = retriever.rank("文献 检索 RRF", corpus, 5, 1.2, 0.75);

        assertEquals(2, ranked.size());
        assertEquals(21L, ranked.get(0).chunkId(), "包含稀有词 RRF 的分块得分应更高");
    }

    @Test
    void returnsEmptyWhenQueryHasNoMatches() {
        List<LiteratureChunk> corpus = List.of(chunk(30L, "图书馆服务说明。"));

        assertTrue(retriever.rank("量子计算", corpus, 5, 1.2, 0.75).isEmpty());
        assertTrue(retriever.rank("   ", corpus, 5, 1.2, 0.75).isEmpty());
    }

    private LiteratureChunk chunk(Long id, String text) {
        LiteratureChunk chunk = new LiteratureChunk();
        chunk.setId(id);
        chunk.setLiteratureId(1000L);
        chunk.setChunkIndex(0);
        chunk.setChunkText(text);
        return chunk;
    }
}
