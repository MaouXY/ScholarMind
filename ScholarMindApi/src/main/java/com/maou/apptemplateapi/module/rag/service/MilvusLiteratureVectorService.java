package com.maou.apptemplateapi.module.rag.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.maou.apptemplateapi.common.config.ai.AgentToolProperties;
import com.maou.apptemplateapi.module.literature.entity.LiteratureChunk;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 文献分块的向量召回（Milvus 稠密检索）。
 *
 * 与 RAG 知识库共用同一套 Milvus 连接与嵌入模型，但使用独立 collection
 * （默认 `${app.agent.rag.milvus.collection-name}_literature`），
 * 避免知识库文档与文献分块互相污染召回结果。
 *
 * 所有方法在 Milvus 不可用时都不抛异常：返回空结果或 false，
 * 由上游退化为「纯稀疏（BM25）检索」，保证检索功能始终可用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MilvusLiteratureVectorService {

    private static final String CHUNK_ID_FIELD = "chunk_id";
    private static final String LITERATURE_ID_FIELD = "literature_id";
    private static final String CHUNK_INDEX_FIELD = "chunk_index";
    private static final String CONTENT_FIELD = "content";

    private final AgentToolProperties agentToolProperties;
    private final RagEmbeddingService embeddingService;

    private volatile MilvusClientV2 client;
    private volatile boolean collectionReady;

    public boolean enabled() {
        return agentToolProperties.getRag().getMilvus().isEnabled();
    }

    public String collectionName() {
        return agentToolProperties.getRag().getMilvus().getCollectionName() + "_literature";
    }

    /** 重建某篇文献的向量：先按文献删除旧向量，再写入当前分块。返回是否成功。 */
    public boolean rebuildLiterature(Long literatureId, List<LiteratureChunk> chunks, String scenario) {
        if (!enabled()) {
            return false;
        }
        AgentToolProperties.Milvus milvus = agentToolProperties.getRag().getMilvus();
        try {
            ensureCollectionReady(scenario);
            MilvusClientV2 milvusClient = client();
            milvusClient.delete(DeleteReq.builder()
                    .collectionName(collectionName())
                    .filter("%s == %d".formatted(LITERATURE_ID_FIELD, literatureId))
                    .build());
            if (chunks == null || chunks.isEmpty()) {
                log.info("literature vector rebuilt with empty chunks, scenario={}, literatureId={}, collectionName={}",
                        scenario, literatureId, collectionName());
                return true;
            }
            List<JsonObject> rows = new ArrayList<>();
            for (LiteratureChunk chunk : chunks) {
                rows.add(toRow(chunk, scenario));
            }
            insertWithDimensionGuard(literatureId, rows, scenario);
            log.info("literature vector indexed, scenario={}, literatureId={}, chunkCount={}, collectionName={}, dimension={}, embeddingProvider={}, embeddingModel={}",
                    scenario, literatureId, rows.size(), collectionName(), embeddingService.dimension(),
                    embeddingService.provider(), embeddingService.modelName());
            return true;
        } catch (RuntimeException exception) {
            log.error("literature vector index failed, scenario={}, literatureId={}, chunkCount={}, collectionName={}, uri={}, dimension={}",
                    scenario, literatureId, chunks == null ? 0 : chunks.size(), collectionName(),
                    milvus.getUri(), embeddingService.dimension(), exception);
            return false;
        }
    }

    public boolean deleteLiterature(Long literatureId, String scenario) {
        if (!enabled()) {
            return false;
        }
        try {
            ensureCollectionReady(scenario);
            client().delete(DeleteReq.builder()
                    .collectionName(collectionName())
                    .filter("%s == %d".formatted(LITERATURE_ID_FIELD, literatureId))
                    .build());
            log.info("literature vector deleted, scenario={}, literatureId={}, collectionName={}",
                    scenario, literatureId, collectionName());
            return true;
        } catch (RuntimeException exception) {
            log.error("literature vector delete failed, scenario={}, literatureId={}, collectionName={}",
                    scenario, literatureId, collectionName(), exception);
            return false;
        }
    }

    /** 向量召回：返回按相似度降序的分块命中（未做权限与分类过滤，由上游处理）。 */
    public List<VectorHit> search(String query, int topK, String scenario) {
        if (!enabled() || !StringUtils.hasText(query) || topK <= 0) {
            return List.of();
        }
        try {
            ensureCollectionReady(scenario);
            SearchResp response = client().search(SearchReq.builder()
                    .collectionName(collectionName())
                    .annsField(agentToolProperties.getRag().getMilvus().getVectorField())
                    .metricType(IndexParam.MetricType.COSINE)
                    .topK(topK)
                    .outputFields(List.of(LITERATURE_ID_FIELD, CHUNK_INDEX_FIELD))
                    .data(List.of(new FloatVec(embeddingService.embed(query, scenario).vector())))
                    .build());
            if (response.getSearchResults().isEmpty()) {
                return List.of();
            }
            return response.getSearchResults().get(0).stream()
                    .map(this::toHit)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (RuntimeException exception) {
            log.error("literature vector search failed, scenario={}, collectionName={}, uri={}, topK={}, query={}",
                    scenario, collectionName(), agentToolProperties.getRag().getMilvus().getUri(), topK, query, exception);
            return List.of();
        }
    }

    /**
     * 写入向量；若因维度不匹配失败（例如配置的 dimension 与 embedding 模型实际输出不一致），
     * 自动删除旧 collection 并按当前维度重建后重试一次。
     * 这样更换 embedding 模型时无需手工清理 Milvus，向量可由 DB 分块随时重建。
     */
    private void insertWithDimensionGuard(Long literatureId, List<JsonObject> rows, String scenario) {
        MilvusClientV2 milvusClient = client();
        try {
            milvusClient.insert(InsertReq.builder()
                    .collectionName(collectionName())
                    .data(rows)
                    .build());
        } catch (RuntimeException exception) {
            if (!isDimensionMismatch(exception)) {
                throw exception;
            }
            log.warn("literature vector dimension mismatch, recreating collection, scenario={}, literatureId={}, collectionName={}, configuredDimension={}, reason={}",
                    scenario, literatureId, collectionName(), embeddingService.dimension(), exception.getMessage());
            try {
                milvusClient.dropCollection(DropCollectionReq.builder()
                        .collectionName(collectionName())
                        .build());
            } catch (RuntimeException dropException) {
                log.error("literature vector collection drop failed, scenario={}, collectionName={}",
                        scenario, collectionName(), dropException);
            }
            collectionReady = false;
            ensureCollectionReady(scenario);
            client().insert(InsertReq.builder()
                    .collectionName(collectionName())
                    .data(rows)
                    .build());
            log.info("literature vector collection recreated with new dimension, scenario={}, literatureId={}, collectionName={}, dimension={}",
                    scenario, literatureId, collectionName(), embeddingService.dimension());
        }
    }

    private boolean isDimensionMismatch(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("dimension") || normalized.contains("dim");
    }

    private JsonObject toRow(LiteratureChunk chunk, String scenario) {        JsonObject row = new JsonObject();
        row.addProperty(CHUNK_ID_FIELD, chunk.getId());
        row.addProperty(LITERATURE_ID_FIELD, chunk.getLiteratureId());
        row.addProperty(CHUNK_INDEX_FIELD, chunk.getChunkIndex());
        row.addProperty(CONTENT_FIELD, chunk.getChunkText() == null ? "" : chunk.getChunkText());
        JsonArray vector = new JsonArray();
        for (float value : embeddingService.embed(chunk.getChunkText(), scenario).vector()) {
            vector.add(value);
        }
        row.add(agentToolProperties.getRag().getMilvus().getVectorField(), vector);
        return row;
    }

    private VectorHit toHit(SearchResp.SearchResult result) {
        Long chunkId = longValue(result.getId());
        if (chunkId == null) {
            return null;
        }
        Map<String, Object> entity = result.getEntity();
        Long literatureId = entity == null ? null : longValue(entity.get(LITERATURE_ID_FIELD));
        double score = result.getScore() == null ? 0.0 : result.getScore();
        return new VectorHit(chunkId, literatureId, score);
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void ensureCollectionReady(String scenario) {
        if (collectionReady) {
            return;
        }
        synchronized (this) {
            if (collectionReady) {
                return;
            }
            AgentToolProperties.Milvus milvus = agentToolProperties.getRag().getMilvus();
            MilvusClientV2 milvusClient = client();
            Boolean exists = milvusClient.hasCollection(HasCollectionReq.builder()
                    .collectionName(collectionName())
                    .build());
            if (!Boolean.TRUE.equals(exists)) {
                CreateCollectionReq.CollectionSchema schema = MilvusClientV2.CreateSchema();
                schema.addField(AddFieldReq.builder()
                        .fieldName(CHUNK_ID_FIELD)
                        .dataType(DataType.Int64)
                        .isPrimaryKey(true)
                        .autoID(false)
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName(LITERATURE_ID_FIELD)
                        .dataType(DataType.Int64)
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName(CHUNK_INDEX_FIELD)
                        .dataType(DataType.Int32)
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName(CONTENT_FIELD)
                        .dataType(DataType.VarChar)
                        .maxLength(4096)
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName(milvus.getVectorField())
                        .dataType(DataType.FloatVector)
                        .dimension(embeddingService.dimension())
                        .build());
                milvusClient.createCollection(CreateCollectionReq.builder()
                        .collectionName(collectionName())
                        .collectionSchema(schema)
                        .indexParam(IndexParam.builder()
                                .fieldName(milvus.getVectorField())
                                .indexType(IndexParam.IndexType.FLAT)
                                .metricType(IndexParam.MetricType.COSINE)
                                .extraParams(Map.of())
                                .build())
                        .build());
                log.info("literature vector collection created, scenario={}, collectionName={}, uri={}, dimension={}, vectorField={}",
                        scenario, collectionName(), milvus.getUri(), embeddingService.dimension(), milvus.getVectorField());
            }
            milvusClient.loadCollection(LoadCollectionReq.builder()
                    .collectionName(collectionName())
                    .sync(true)
                    .timeout((long) Math.max(milvus.getTimeoutSeconds() == null ? 20 : milvus.getTimeoutSeconds(), 1))
                    .build());
            collectionReady = true;
            log.info("literature vector collection ready, scenario={}, collectionName={}, uri={}, dimension={}, vectorField={}",
                    scenario, collectionName(), milvus.getUri(), embeddingService.dimension(), milvus.getVectorField());
        }
    }

    private MilvusClientV2 client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    AgentToolProperties.Milvus milvus = agentToolProperties.getRag().getMilvus();
                    long timeoutMs = TimeUnit.SECONDS.toMillis(Math.max(milvus.getTimeoutSeconds() == null ? 20 : milvus.getTimeoutSeconds(), 1));
                    ConnectConfig.ConnectConfigBuilder builder = ConnectConfig.builder()
                            .uri(milvus.getUri())
                            .connectTimeoutMs(timeoutMs)
                            .rpcDeadlineMs(timeoutMs);
                    if (StringUtils.hasText(milvus.getToken())) {
                        builder.token(milvus.getToken());
                    }
                    client = new MilvusClientV2(builder.build());
                    log.info("literature vector milvus client initialized, scenario=literature-vector-init, uri={}, collectionName={}, timeoutMs={}",
                            milvus.getUri(), collectionName(), timeoutMs);
                }
            }
        }
        return client;
    }

    /**
     * @param chunkId      分块 ID
     * @param literatureId 所属文献 ID
     * @param score        余弦相似度
     */
    public record VectorHit(Long chunkId, Long literatureId, double score) {
    }
}
