package com.maou.apptemplateapi.module.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.maou.apptemplateapi.common.enums.UserRole;
import com.maou.apptemplateapi.common.exception.BusinessException;
import com.maou.apptemplateapi.common.exception.ErrorCode;
import com.maou.apptemplateapi.common.security.CurrentUser;
import com.maou.apptemplateapi.common.security.CurrentUserProvider;
import com.maou.apptemplateapi.module.admin.dto.LiteratureVectorRebuildResponse;
import com.maou.apptemplateapi.module.literature.entity.Literature;
import com.maou.apptemplateapi.module.literature.entity.LiteratureChunk;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureChunkMapper;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureMapper;
import com.maou.apptemplateapi.module.rag.service.MilvusLiteratureVectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理端文献向量维护：把已发布文献的正文分块批量写入 Milvus，
 * 使混合检索的稠密召回（向量路）可用；写入失败时保留 DB_ONLY，检索自动退化为纯稀疏。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminLiteratureVectorService {

    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String VECTOR_STATUS_MILVUS = "MILVUS";

    private final LiteratureMapper literatureMapper;
    private final LiteratureChunkMapper chunkMapper;
    private final MilvusLiteratureVectorService milvusLiteratureVectorService;

    public LiteratureVectorRebuildResponse rebuildAll() {
        CurrentUser admin = requireAdmin("admin-literature-vector-rebuild");
        if (!milvusLiteratureVectorService.enabled()) {
            log.warn("literature vector rebuild rejected, scenario=admin-literature-vector-rebuild, reason=milvus-disabled, adminId={}", admin.getId());
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE, "milvus is disabled");
        }
        List<Literature> literatures = literatureMapper.selectList(new LambdaQueryWrapper<Literature>()
                .eq(Literature::getDeleted, 0)
                .eq(Literature::getStatus, STATUS_PUBLISHED)
                .orderByAsc(Literature::getId));

        int literatureCount = 0;
        int chunkCount = 0;
        int successCount = 0;
        for (Literature literature : literatures) {
            List<LiteratureChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<LiteratureChunk>()
                    .eq(LiteratureChunk::getLiteratureId, literature.getId())
                    .eq(LiteratureChunk::getEnabled, 1)
                    .eq(LiteratureChunk::getDeleted, 0)
                    .orderByAsc(LiteratureChunk::getChunkIndex));
            if (chunks.isEmpty()) {
                continue;
            }
            literatureCount++;
            chunkCount += chunks.size();
            if (milvusLiteratureVectorService.rebuildLiterature(literature.getId(), chunks, "admin-literature-vector-rebuild")) {
                successCount++;
                for (LiteratureChunk chunk : chunks) {
                    chunk.setVectorStoreStatus(VECTOR_STATUS_MILVUS);
                    chunkMapper.updateById(chunk);
                }
            }
        }
        log.info("literature vector rebuild finished, scenario=admin-literature-vector-rebuild, adminId={}, collectionName={}, literatureTotal={}, literatureIndexed={}, chunkCount={}, successLiterature={}",
                admin.getId(), milvusLiteratureVectorService.collectionName(), literatures.size(), literatureCount, chunkCount, successCount);
        return new LiteratureVectorRebuildResponse(milvusLiteratureVectorService.collectionName(),
                literatureCount, chunkCount, successCount);
    }

    private CurrentUser requireAdmin(String scenario) {
        CurrentUser currentUser = CurrentUserProvider.require();
        if (currentUser.getRole() != UserRole.ADMIN) {
            log.warn("user role denied, scenario={}, userId={}, roleCode={}", scenario, currentUser.getId(), currentUser.getRoleCode());
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return currentUser;
    }
}
