package com.maou.apptemplateapi.module.literature.service.retrieval;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RRF（Reciprocal Rank Fusion）融合排序。
 *
 * 融合公式：score(d) = Σ_i 1 / (k + rank_i(d))，其中 rank_i 为文档 d 在第 i 路召回中的名次（从 1 开始）。
 *
 * 选择 RRF 而不是「加权求和」的原因：
 * 1. 不同召回器的分数量纲不可比（BM25 是无上界正数、余弦相似度在 [-1,1]），归一化后仍受分布影响；
 * 2. RRF 只依赖名次，天然对量纲与异常值鲁棒，且单路召回完全没命中时另一路仍能主导排序；
 * 3. k 取 60 可削弱头部名次的过度影响，避免某一路召回的高位结果统治最终排序。
 */
@Component
public class RrfFusion {

    public List<FusedChunk> fuse(List<Long> sparseRanked,
                                 List<Long> denseRanked,
                                 int k,
                                 int topK) {
        int effectiveK = Math.max(k, 1);
        Map<Long, Integer> sparseRanks = ranks(sparseRanked);
        Map<Long, Integer> denseRanks = ranks(denseRanked);

        Map<Long, FusedChunk> fused = new LinkedHashMap<>();
        sparseRanks.forEach((chunkId, rank) -> fused.put(chunkId,
                new FusedChunk(chunkId, 1.0 / (effectiveK + rank), rank, null)));
        denseRanks.forEach((chunkId, rank) -> {
            FusedChunk existing = fused.get(chunkId);
            double score = 1.0 / (effectiveK + rank);
            if (existing == null) {
                fused.put(chunkId, new FusedChunk(chunkId, score, null, rank));
            } else {
                fused.put(chunkId, new FusedChunk(chunkId, existing.rrfScore() + score, existing.sparseRank(), rank));
            }
        });

        return fused.values().stream()
                .sorted(Comparator.comparingDouble(FusedChunk::rrfScore).reversed()
                        .thenComparing(item -> bestRank(item))
                        .thenComparing(FusedChunk::chunkId))
                .limit(Math.max(topK, 1))
                .toList();
    }

    private int bestRank(FusedChunk item) {
        int sparse = item.sparseRank() == null ? Integer.MAX_VALUE : item.sparseRank();
        int dense = item.denseRank() == null ? Integer.MAX_VALUE : item.denseRank();
        return Math.min(sparse, dense);
    }

    private Map<Long, Integer> ranks(List<Long> ranked) {
        Map<Long, Integer> ranks = new HashMap<>();
        if (ranked == null) {
            return ranks;
        }
        int rank = 0;
        for (Long chunkId : ranked) {
            if (chunkId == null || ranks.containsKey(chunkId)) {
                continue;
            }
            rank++;
            ranks.put(chunkId, rank);
        }
        return ranks;
    }

    /**
     * @param chunkId    分块 ID
     * @param rrfScore   RRF 融合得分（越大越靠前）
     * @param sparseRank 稀疏召回名次，未命中为 null
     * @param denseRank  稠密召回名次，未命中为 null
     */
    public record FusedChunk(Long chunkId, double rrfScore, Integer sparseRank, Integer denseRank) {
    }
}
