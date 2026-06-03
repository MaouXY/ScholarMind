package com.maou.apptemplateapi.module.rag.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.maou.apptemplateapi.common.config.ai.AgentToolProperties;
import com.maou.apptemplateapi.module.rag.dto.RagChunkResponse;
import com.maou.apptemplateapi.module.rag.entity.RagKnowledgeChunk;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class MilvusRagVectorService {

    private static final String ID_FIELD = "id";
    private static final String DOCUMENT_ID_FIELD = "document_id";
    private static final String CHUNK_INDEX_FIELD = "chunk_index";
    private static final String CONTENT_FIELD = "content";

    private final AgentToolProperties agentToolProperties;
    private final RagEmbeddingService embeddingService;

    private volatile MilvusClientV2 client;
    private volatile boolean collectionReady;

    public boolean enabled() {
        return agentToolProperties.getRag().getMilvus().isEnabled();
    }

    public boolean rebuildDocument(Long documentId, List<RagKnowledgeChunk> chunks, String scenario) {
        if (!enabled()) {
            return false;
        }
        AgentToolProperties.Milvus milvus = agentToolProperties.getRag().getMilvus();
        try {
            ensureCollectionReady(scenario);
            MilvusClientV2 milvusClient = client();
            milvusClient.delete(DeleteReq.builder()
                    .collectionName(milvus.getCollectionName())
                    .filter("%s == %d".formatted(DOCUMENT_ID_FIELD, documentId))
                    .build());
            List<JsonObject> rows = new ArrayList<>();
            for (RagKnowledgeChunk chunk : chunks) {
                rows.add(toRow(documentId, chunk, scenario));
            }
            if (!rows.isEmpty()) {
                milvusClient.insert(InsertReq.builder()
                        .collectionName(milvus.getCollectionName())
                        .data(rows)
                        .build());
            }
            log.info("rag milvus indexed, scenario={}, documentId={}, chunkCount={}, collectionName={}, uri={}, dimension={}, embeddingProvider={}, embeddingModel={}",
                    scenario, documentId, rows.size(), milvus.getCollectionName(), milvus.getUri(),
                    embeddingService.dimension(), embeddingService.provider(), embeddingService.modelName());
            return true;
        } catch (RuntimeException exception) {
            log.error("rag milvus index failed, scenario={}, documentId={}, collectionName={}, uri={}, chunkCount={}",
                    scenario, documentId, milvus.getCollectionName(), milvus.getUri(), chunks == null ? 0 : chunks.size(), exception);
            return false;
        }
    }

    public List<RagChunkResponse> search(String query, int limit, String scenario) {
        if (!enabled() || !StringUtils.hasText(query)) {
            return List.of();
        }
        AgentToolProperties.Milvus milvus = agentToolProperties.getRag().getMilvus();
        try {
            ensureCollectionReady(scenario);
            SearchResp response = client().search(SearchReq.builder()
                    .collectionName(milvus.getCollectionName())
                    .annsField(milvus.getVectorField())
                    .metricType(IndexParam.MetricType.COSINE)
                    .topK(Math.max(limit, 1))
                    .outputFields(List.of(DOCUMENT_ID_FIELD, CHUNK_INDEX_FIELD, CONTENT_FIELD))
                    .data(List.of(new FloatVec(embeddingService.embed(query, scenario).vector())))
                    .build());
            if (response.getSearchResults().isEmpty()) {
                return List.of();
            }
            return response.getSearchResults().get(0).stream()
                    .map(this::toResponse)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (RuntimeException exception) {
            log.error("rag milvus search failed, scenario={}, collectionName={}, uri={}, query={}, limit={}",
                    scenario, milvus.getCollectionName(), milvus.getUri(), query, limit, exception);
            return List.of();
        }
    }

    private JsonObject toRow(Long documentId, RagKnowledgeChunk chunk, String scenario) {
        JsonObject row = new JsonObject();
        row.addProperty(ID_FIELD, chunk.getId());
        row.addProperty(DOCUMENT_ID_FIELD, documentId);
        row.addProperty(CHUNK_INDEX_FIELD, chunk.getChunkIndex());
        row.addProperty(CONTENT_FIELD, chunk.getContent());
        JsonArray vector = new JsonArray();
        for (float value : embeddingService.embed(chunk.getContent(), scenario).vector()) {
            vector.add(value);
        }
        row.add(agentToolProperties.getRag().getMilvus().getVectorField(), vector);
        return row;
    }

    private RagChunkResponse toResponse(SearchResp.SearchResult result) {
        Map<String, Object> entity = result.getEntity();
        Long chunkId = longValue(result.getId());
        Long documentId = longValue(entity.get(DOCUMENT_ID_FIELD));
        String content = entity.get(CONTENT_FIELD) == null ? "" : String.valueOf(entity.get(CONTENT_FIELD));
        double score = result.getScore() == null ? 0.0 : result.getScore();
        return new RagChunkResponse(chunkId, documentId, "Milvus", content, score);
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
                    .collectionName(milvus.getCollectionName())
                    .build());
            if (!Boolean.TRUE.equals(exists)) {
                CreateCollectionReq.CollectionSchema schema = MilvusClientV2.CreateSchema();
                schema.addField(AddFieldReq.builder()
                        .fieldName(ID_FIELD)
                        .dataType(DataType.Int64)
                        .isPrimaryKey(true)
                        .autoID(false)
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName(DOCUMENT_ID_FIELD)
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
                        .collectionName(milvus.getCollectionName())
                        .collectionSchema(schema)
                        .indexParam(IndexParam.builder()
                                .fieldName(milvus.getVectorField())
                                .indexType(IndexParam.IndexType.FLAT)
                                .metricType(IndexParam.MetricType.COSINE)
                                .extraParams(Map.of())
                                .build())
                        .build());
                log.info("rag milvus collection created, scenario={}, collectionName={}, uri={}, dimension={}, vectorField={}",
                        scenario, milvus.getCollectionName(), milvus.getUri(), embeddingService.dimension(), milvus.getVectorField());
            }
            milvusClient.loadCollection(LoadCollectionReq.builder()
                    .collectionName(milvus.getCollectionName())
                    .sync(true)
                    .timeout((long) Math.max(milvus.getTimeoutSeconds() == null ? 20 : milvus.getTimeoutSeconds(), 1))
                    .build());
            collectionReady = true;
            log.info("rag milvus collection ready, scenario={}, collectionName={}, uri={}, dimension={}, vectorField={}",
                    scenario, milvus.getCollectionName(), milvus.getUri(), embeddingService.dimension(), milvus.getVectorField());
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
                    log.info("rag milvus client initialized, scenario=rag-milvus-init, uri={}, collectionName={}, timeoutMs={}",
                            milvus.getUri(), milvus.getCollectionName(), timeoutMs);
                }
            }
        }
        return client;
    }
}
