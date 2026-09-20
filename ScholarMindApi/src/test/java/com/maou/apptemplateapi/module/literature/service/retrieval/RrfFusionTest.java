package com.maou.apptemplateapi.module.literature.service.retrieval;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RRF 融合单元测试：验证融合公式、单路独有结果的保留，以及 k 值对头部名次的影响。
 */
class RrfFusionTest {

    private final RrfFusion fusion = new RrfFusion();

    @Test
    void fusesRanksWithReciprocalFormula() {
        List<RrfFusion.FusedChunk> fused = fusion.fuse(List.of(1L, 2L), List.of(2L, 3L), 60, 10);

        // 2 号同时出现在两路（第 2 名与第 1 名），应为最高分
        assertEquals(2L, fused.get(0).chunkId());
        double expected = 1.0 / (60 + 2) + 1.0 / (60 + 1);
        assertEquals(expected, fused.get(0).rrfScore(), 1e-9);
        assertEquals(2, fused.get(0).sparseRank());
        assertEquals(1, fused.get(0).denseRank());
        assertEquals(3, fused.size(), "三路候选取并集，应为 3 条");
    }

    @Test
    void keepsSingleRetrieverResultsWithNullRankOnOtherSide() {
        List<RrfFusion.FusedChunk> fused = fusion.fuse(List.of(10L), List.of(20L), 60, 10);

        assertEquals(2, fused.size());
        RrfFusion.FusedChunk fromSparse = fused.stream().filter(item -> item.chunkId().equals(10L)).findFirst().orElseThrow();
        RrfFusion.FusedChunk fromDense = fused.stream().filter(item -> item.chunkId().equals(20L)).findFirst().orElseThrow();
        assertEquals(1, fromSparse.sparseRank());
        assertNull(fromSparse.denseRank());
        assertEquals(1, fromDense.denseRank());
        assertNull(fromDense.sparseRank());
    }

    @Test
    void smallerKFavoursTopRanksMore() {
        List<Long> sparse = List.of(1L, 2L);
        List<Long> dense = List.of(2L, 1L);

        // 两路名次互换时，融合得分相同（RRF 对称性）
        List<RrfFusion.FusedChunk> fused = fusion.fuse(sparse, dense, 60, 10);
        assertEquals(fused.get(0).rrfScore(), fused.get(1).rrfScore(), 1e-9);

        // k 越小，头部名次权重越大
        double smallK = fusion.fuse(sparse, dense, 1, 10).get(0).rrfScore();
        double largeK = fusion.fuse(sparse, dense, 1000, 10).get(0).rrfScore();
        assertTrue(smallK > largeK, "k 越小头部名次得分越高");
    }

    @Test
    void ignoresDuplicateAndNullEntriesAndRespectsTopK() {
        List<RrfFusion.FusedChunk> fused = fusion.fuse(Arrays.asList(1L, 1L, null, 2L), List.of(), 60, 1);

        assertEquals(1, fused.size(), "topK 生效");
        assertEquals(1L, fused.get(0).chunkId());
        assertEquals(1, fused.get(0).sparseRank(), "重复项只取首次出现的名次");
    }

    @Test
    void handlesEmptyInputs() {
        assertTrue(fusion.fuse(List.of(), List.of(), 60, 10).isEmpty());
        assertTrue(fusion.fuse(null, null, 60, 10).isEmpty());
    }
}
