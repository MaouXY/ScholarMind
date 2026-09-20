package com.maou.apptemplateapi.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 检索配置：稀疏（BM25）与稠密（向量）召回参数，以及 RRF 融合参数。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.ai.retrieval")
public class RetrievalProperties {

    /** 是否启用混合检索；关闭后退化为纯稀疏（BM25）召回。 */
    private boolean enabled = true;

    /** RRF 融合常数 k：得分 = Σ 1/(k + rank)，业界常用 60。 */
    private int rrfK = 60;

    /** 稀疏召回候选数量。 */
    private int sparseTopK = 30;

    /** 稠密召回候选数量。 */
    private int denseTopK = 30;

    /** BM25 词频饱和参数 k1。 */
    private double bm25K1 = 1.2;

    /** BM25 文档长度归一化参数 b。 */
    private double bm25B = 0.75;

    /** 融合后是否再用外部重排模型做最终排序。 */
    private boolean rerankEnabled = true;

    /** 单次检索返回给上游的最大分块数下限（不足时按该值放宽，保证有足够候选）。 */
    private int candidateFloor = 12;
}
