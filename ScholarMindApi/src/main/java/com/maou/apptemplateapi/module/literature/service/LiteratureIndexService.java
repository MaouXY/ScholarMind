package com.maou.apptemplateapi.module.literature.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.maou.apptemplateapi.common.config.ai.AgentToolProperties;
import com.maou.apptemplateapi.common.exception.BusinessException;
import com.maou.apptemplateapi.common.exception.ErrorCode;
import com.maou.apptemplateapi.common.security.CurrentUser;
import com.maou.apptemplateapi.common.security.CurrentUserProvider;
import com.maou.apptemplateapi.module.literature.dto.LiteratureIndexResponse;
import com.maou.apptemplateapi.module.literature.entity.Literature;
import com.maou.apptemplateapi.module.literature.entity.LiteratureChunk;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureChunkMapper;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureMapper;
import com.maou.apptemplateapi.module.rag.service.RagEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiteratureIndexService {

    private static final String STATUS_INDEXED = "INDEXED";
    private static final String STATUS_FAILED = "FAILED";

    private final LiteratureService literatureService;
    private final LiteratureMapper literatureMapper;
    private final LiteratureChunkMapper chunkMapper;
    private final AgentToolProperties agentToolProperties;
    private final RagEmbeddingService ragEmbeddingService;

    @Transactional
    public LiteratureIndexResponse rebuild(Long literatureId) {
        CurrentUser currentUser = CurrentUserProvider.require();
        Literature literature = literatureService.requireReadableLiterature(literatureId, currentUser, "literature-index-rebuild");
        try {
            String content = buildIndexContent(literature);
            if (!StringUtils.hasText(content)) {
                log.warn("literature index rejected, scenario=literature-index-rebuild, reason=empty-content, literatureId={}, userId={}",
                        literatureId, currentUser.getId());
                throw new BusinessException(ErrorCode.LITERATURE_STATUS_INVALID, "literature content is empty");
            }
            int deletedChunks = chunkMapper.physicalDeleteByLiteratureId(literatureId);
            List<String> texts = splitText(content);
            for (int i = 0; i < texts.size(); i++) {
                LiteratureChunk chunk = new LiteratureChunk();
                chunk.setLiteratureId(literatureId);
                chunk.setChunkIndex(i);
                chunk.setChunkText(texts.get(i));
                chunk.setTokenCount(Math.max(1, texts.get(i).length() / 2));
                chunk.setEmbeddingProvider("%s:%s".formatted(ragEmbeddingService.provider(), ragEmbeddingService.modelName()));
                chunk.setVectorStoreStatus("DB_ONLY");
                chunk.setEnabled(1);
                chunkMapper.insert(chunk);
            }
            literature.setParseStatus(STATUS_INDEXED);
            literature.setParseError(null);
            literatureMapper.updateById(literature);
            log.info("literature index rebuilt, scenario=literature-index-rebuild, literatureId={}, userId={}, deletedChunkCount={}, insertedChunkCount={}, embeddingProvider={}",
                    literatureId, currentUser.getId(), deletedChunks, texts.size(), ragEmbeddingService.provider());
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
