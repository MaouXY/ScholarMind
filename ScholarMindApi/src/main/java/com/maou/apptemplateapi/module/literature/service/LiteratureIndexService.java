package com.maou.apptemplateapi.module.literature.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.maou.apptemplateapi.common.config.ai.AgentToolProperties;
import com.maou.apptemplateapi.common.exception.BusinessException;
import com.maou.apptemplateapi.common.exception.ErrorCode;
import com.maou.apptemplateapi.common.security.CurrentUser;
import com.maou.apptemplateapi.common.security.CurrentUserProvider;
import com.maou.apptemplateapi.module.file.entity.FileMetadata;
import com.maou.apptemplateapi.module.file.mapper.FileMetadataMapper;
import com.maou.apptemplateapi.module.literature.dto.LiteratureChunkResponse;
import com.maou.apptemplateapi.module.literature.dto.LiteratureIndexResponse;
import com.maou.apptemplateapi.module.literature.entity.Literature;
import com.maou.apptemplateapi.module.literature.entity.LiteratureChunk;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureChunkMapper;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureMapper;
import com.maou.apptemplateapi.module.literature.service.parse.LiteratureTextExtractor;
import com.maou.apptemplateapi.module.rag.service.MilvusLiteratureVectorService;
import com.maou.apptemplateapi.module.rag.service.RagEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiteratureIndexService {

    private static final String STATUS_INDEXED = "INDEXED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String VECTOR_STATUS_PENDING = "PENDING";
    private static final String VECTOR_STATUS_MILVUS = "MILVUS";
    private static final String VECTOR_STATUS_DB_ONLY = "DB_ONLY";

    /** 索引语料来源：正文抽取成功 / 题录与摘要兜底 */
    private static final String INDEX_SOURCE_FILE_TEXT = "FILE_TEXT";
    private static final String INDEX_SOURCE_METADATA_FALLBACK = "METADATA_FALLBACK";
    /** 正文抽取的最小可用长度：低于该长度视为「没解析出正文」，直接走题录兜底 */
    private static final int MIN_FILE_TEXT_CHARS = 200;
    private static final String REBUILD_SCENARIO = "literature-index-rebuild";
    private static final String DEFAULT_FALLBACK_REASON = "未解析到正文（类型不支持/解析为空/S3 读取失败），已使用题录与摘要兜底";
    private static final int MAX_PARSE_ERROR_CHARS = 500;

    private final LiteratureService literatureService;
    private final LiteratureMapper literatureMapper;
    private final LiteratureChunkMapper chunkMapper;
    private final FileMetadataMapper fileMetadataMapper;
    private final AgentToolProperties agentToolProperties;
    private final RagEmbeddingService ragEmbeddingService;
    private final MilvusLiteratureVectorService milvusLiteratureVectorService;
    private final S3Client rustfsS3Client;
    private final LiteratureTextExtractor literatureTextExtractor;

    @Transactional
    public LiteratureIndexResponse rebuild(Long literatureId) {
        CurrentUser currentUser = CurrentUserProvider.require();
        Literature literature = literatureService.requireReadableLiterature(literatureId, currentUser, "literature-index-rebuild");
        try {
            IndexContent content = resolveIndexContent(literature);
            if (!StringUtils.hasText(content.text())) {
                log.warn("literature index rejected, scenario=literature-index-rebuild, reason=empty-content, literatureId={}, userId={}",
                        literatureId, currentUser.getId());
                throw new BusinessException(ErrorCode.LITERATURE_STATUS_INVALID, "literature content is empty");
            }
            int deletedChunks = chunkMapper.physicalDeleteByLiteratureId(literatureId);
            List<String> texts = splitText(content.text());
            List<LiteratureChunk> chunks = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                LiteratureChunk chunk = new LiteratureChunk();
                chunk.setLiteratureId(literatureId);
                chunk.setChunkIndex(i);
                chunk.setChunkText(texts.get(i));
                chunk.setTokenCount(Math.max(1, texts.get(i).length() / 2));
                chunk.setEmbeddingProvider("%s:%s".formatted(ragEmbeddingService.provider(), ragEmbeddingService.modelName()));
                chunk.setVectorStoreStatus(VECTOR_STATUS_PENDING);
                chunk.setEnabled(1);
                chunkMapper.insert(chunk);
                chunks.add(chunk);
            }
            // 写入 Milvus 向量库：成功标记 MILVUS，失败保留 DB_ONLY（检索会自动退化为纯稀疏 BM25）
            boolean vectorIndexed = milvusLiteratureVectorService.rebuildLiterature(literatureId, chunks, "literature-index-rebuild");
            String vectorStatus = vectorIndexed ? VECTOR_STATUS_MILVUS : VECTOR_STATUS_DB_ONLY;
            for (LiteratureChunk chunk : chunks) {
                chunk.setVectorStoreStatus(vectorStatus);
                chunkMapper.updateById(chunk);
            }
            literature.setParseStatus(STATUS_INDEXED);
            // 索引成功（INDEXED 语义不变）：走正文时清空 parse_error，走题录兜底时保留兜底原因便于排查
            literature.setParseError(content.parseError());
            literatureMapper.updateById(literature);
            log.info("literature index rebuilt, scenario=literature-index-rebuild, literatureId={}, userId={}, deletedChunkCount={}, insertedChunkCount={}, embeddingProvider={}, vectorStoreStatus={}",
                    literatureId, currentUser.getId(), deletedChunks, texts.size(), ragEmbeddingService.provider(), vectorStatus);
            return new LiteratureIndexResponse(literatureId, texts.size(), literature.getParseStatus());
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            literature.setParseStatus(STATUS_FAILED);
            literature.setParseError(exception.getMessage());
            literatureMapper.updateById(literature);
            log.error("literature index failed, scenario=literature-index-rebuild, literatureId={}, userId={}",
                    literatureId, currentUser.getId(), exception);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "literature index failed");
        }
    }

    /**
     * 在线阅读使用的正文分块列表：先做可读权限校验，再按 chunkIndex 顺序返回已启用分块。
     */
    public List<LiteratureChunkResponse> listChunks(Long literatureId) {
        CurrentUser currentUser = CurrentUserProvider.require();
        literatureService.requireReadableLiterature(literatureId, currentUser, "literature-chunk-list");
        List<LiteratureChunkResponse> chunks = listEnabledChunks(literatureId).stream()
                .map(chunk -> new LiteratureChunkResponse(
                        chunk.getId(),
                        chunk.getLiteratureId(),
                        chunk.getChunkIndex(),
                        chunk.getPageNo(),
                        chunk.getChunkText(),
                        chunk.getTokenCount(),
                        chunk.getVectorStoreStatus()))
                .toList();
        log.info("literature chunks listed, scenario=literature-chunk-list, literatureId={}, userId={}, chunkCount={}",
                literatureId, currentUser.getId(), chunks.size());
        return chunks;
    }

    public List<LiteratureChunk> listEnabledChunks(Long literatureId) {
        return chunkMapper.selectList(new LambdaQueryWrapper<LiteratureChunk>()
                .eq(LiteratureChunk::getLiteratureId, literatureId)
                .eq(LiteratureChunk::getEnabled, 1)
                .eq(LiteratureChunk::getDeleted, 0)
                .orderByAsc(LiteratureChunk::getChunkIndex)
                .orderByAsc(LiteratureChunk::getId));
    }

    String buildIndexContent(Literature literature) {
        return """
                Title: %s
                Author: %s
                Source: %s
                Year: %s
                Keywords: %s
                Abstract: %s
                AI Summary: %s
                """.formatted(
                value(literature.getTitle()),
                value(literature.getAuthor()),
                value(literature.getSource()),
                literature.getPublishYear() == null ? "" : literature.getPublishYear(),
                value(literature.getKeywords()),
                value(literature.getAbstractText()),
                value(literature.getAiSummary()));
    }

    /**
     * 索引语料解析：优先用绑定文件（RustFS）解析出的真实正文，抽不到正文就回退题录与摘要。
     *
     * <p>整条链路（查 file_metadata → S3 取对象 → PDF/Word 解析）都用 try/catch 兜住，
     * 任何远程 I/O 或解析异常只降级为兜底语料，绝不让重建事务整体失败。
     */
    private IndexContent resolveIndexContent(Literature literature) {
        LiteratureTextExtractor.ExtractionResult extraction = null;
        if (literature.getFileId() != null) {
            extraction = extractFileText(literature);
            if (StringUtils.hasText(extraction.text()) && extraction.text().length() >= MIN_FILE_TEXT_CHARS) {
                log.info("literature index content resolved, scenario=literature-index-rebuild, literatureId={}, source={}, textChars={}, fallbackReason={}",
                        literature.getId(), INDEX_SOURCE_FILE_TEXT, extraction.text().length(), null);
                return new IndexContent(extraction.text(), INDEX_SOURCE_FILE_TEXT, null);
            }
        }
        String fallbackReason = buildFallbackReason(extraction);
        String metadataContent = buildIndexContent(literature);
        log.info("literature index content resolved, scenario=literature-index-rebuild, literatureId={}, source={}, textChars={}, fallbackReason={}",
                literature.getId(), INDEX_SOURCE_METADATA_FALLBACK, metadataContent.length(), fallbackReason);
        return new IndexContent(metadataContent, INDEX_SOURCE_METADATA_FALLBACK, fallbackReason);
    }

    /**
     * 从 RustFS 取回绑定文件字节并抽取正文；任何失败都转化为「解析失败但类型支持」的结果，由调用方决定兜底。
     */
    private LiteratureTextExtractor.ExtractionResult extractFileText(Literature literature) {
        Long fileId = literature.getFileId();
        try {
            FileMetadata metadata = fileMetadataMapper.selectOne(new LambdaQueryWrapper<FileMetadata>()
                    .eq(FileMetadata::getId, fileId)
                    .eq(FileMetadata::getDeleted, 0));
            if (metadata == null || !StringUtils.hasText(metadata.getObjectKey())) {
                log.warn("literature file text skipped, scenario=literature-index-rebuild, reason=file-metadata-missing, literatureId={}, fileId={}",
                        literature.getId(), fileId);
                return LiteratureTextExtractor.ExtractionResult.failure("绑定文件元数据缺失");
            }
            String bucket = StringUtils.hasText(metadata.getBucketName()) ? metadata.getBucketName() : null;
            ResponseBytes<GetObjectResponse> objectBytes;
            try {
                GetObjectRequest request = GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(metadata.getObjectKey())
                        .build();
                objectBytes = rustfsS3Client.getObjectAsBytes(request);
            } catch (RuntimeException exception) {
                // 远程 I/O 失败不抛给事务：仅记录并降级为兜底语料
                log.error("literature file text fetch failed, scenario=literature-index-rebuild, literatureId={}, fileId={}, bucket={}, objectKey={}",
                        literature.getId(), fileId, bucket, metadata.getObjectKey(), exception);
                return LiteratureTextExtractor.ExtractionResult.failure("对象存储读取失败");
            }
            return literatureTextExtractor.extract(metadata.getContentType(), metadata.getOriginalName(),
                    objectBytes.asByteArray(), REBUILD_SCENARIO);
        } catch (RuntimeException exception) {
            log.error("literature file text extract aborted, scenario=literature-index-rebuild, literatureId={}, fileId={}, fileType={}",
                    literature.getId(), fileId, literature.getFileType(), exception);
            return LiteratureTextExtractor.ExtractionResult.failure("正文抽取异常");
        }
    }

    /** 组装写回 literature.parse_error 的兜底原因（≤500 字符），保证字段不会因超长写入失败。 */
    private String buildFallbackReason(LiteratureTextExtractor.ExtractionResult extraction) {
        if (extraction == null) {
            return DEFAULT_FALLBACK_REASON;
        }
        return truncate(LiteratureTextExtractor.fallbackReason(extraction));
    }

    private String truncate(String text) {
        return text.length() <= MAX_PARSE_ERROR_CHARS ? text : text.substring(0, MAX_PARSE_ERROR_CHARS);
    }

    /** 索引语料 + 来源 + 兜底原因 */
    private record IndexContent(String text, String source, String parseError) {
    }

    private List<String> splitText(String text) {
        int chunkSize = Math.max(agentToolProperties.getRag().getMilvus().getChunkSize() == null ? 800 : agentToolProperties.getRag().getMilvus().getChunkSize(), 200);
        int overlap = Math.min(Math.max(agentToolProperties.getRag().getMilvus().getChunkOverlap() == null ? 120 : agentToolProperties.getRag().getMilvus().getChunkOverlap(), 0), chunkSize / 2);
        if (text.length() <= chunkSize) {
            return List.of(text);
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            if (end == text.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return chunks;
    }

    private String value(String text) {
        return text == null ? "" : text;
    }
}
