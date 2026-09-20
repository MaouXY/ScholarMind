package com.maou.apptemplateapi.module.literature.dto;

import java.util.List;

/**
 * 混合检索对比结果：同时返回稀疏召回、稠密召回与 RRF 融合排序，
 * 便于演示与论文中说明「为什么需要融合，而不是纯向量检索」。
 */
public record RetrievalCompareResponse(
        String query,
        boolean denseAvailable,
        int limit,
        int sparseCandidateCount,
        int denseCandidateCount,
        List<RetrievalHit> sparse,
        List<RetrievalHit> dense,
        List<RetrievalHit> hybrid
) {
}
