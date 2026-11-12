package com.maou.apptemplateapi.module.literature.service;

import com.maou.apptemplateapi.module.citation.mapper.CitationRecordMapper;
import com.maou.apptemplateapi.module.file.entity.FileMetadata;
import com.maou.apptemplateapi.module.file.mapper.FileMetadataMapper;
import com.maou.apptemplateapi.module.file.service.FileService;
import com.maou.apptemplateapi.module.literature.entity.Literature;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureChunkMapper;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureFavoriteMapper;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureQaRecordMapper;
import com.maou.apptemplateapi.module.literature.mapper.ReadingHistoryMapper;
import com.maou.apptemplateapi.module.literature.mapper.ReadingNoteMapper;
import com.maou.apptemplateapi.module.rag.service.MilvusLiteratureVectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 文献级联清理：删除文献时同步清掉分散在 8 张表与 2 个外部存储里的关联数据。
 *
 * 为什么需要它：库上没有任何外键约束（见 doc 05 的 ER 说明），文献一删，
 * 分块、收藏、阅读历史、笔记、引用、问答、附件元数据、Milvus 向量都会变成孤儿；
 * 这些孤儿会让「热门文献」「我的书房」等统计偏高，也会让向量检索召回打不开的条目。
 *
 * 调用方负责权限校验与文献主表删除，本服务只做关联数据清理，
 * 因此在同一事务内被 {@link LiteratureService#delete} 调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiteraturePurgeService {

    private final LiteratureChunkMapper chunkMapper;
    private final LiteratureFavoriteMapper favoriteMapper;
    private final ReadingHistoryMapper readingHistoryMapper;
    private final ReadingNoteMapper readingNoteMapper;
    private final CitationRecordMapper citationRecordMapper;
    private final LiteratureQaRecordMapper qaRecordMapper;
    private final FileMetadataMapper fileMetadataMapper;
    private final FileService fileService;
    private final MilvusLiteratureVectorService milvusLiteratureVectorService;

    /**
     * 清理一篇文献的全部关联数据（不含文献主表）。
     *
     * 顺序说明：先清数据库子表，再删对象存储与向量库——外部存储失败只影响「有无残留」，
     * 不会让已经决定要删的文献留在库里。
     */
    public PurgeResult purge(Literature literature, String scenario) {
        Long literatureId = literature.getId();

        int chunkCount = chunkMapper.physicalDeleteByLiteratureId(literatureId);
        int favoriteCount = favoriteMapper.hardDeleteByLiteratureId(literatureId);
        int readingHistoryCount = readingHistoryMapper.physicalDeleteByLiteratureId(literatureId);
        int readingNoteCount = readingNoteMapper.physicalDeleteByLiteratureId(literatureId);
        int citationCount = citationRecordMapper.physicalDeleteByLiteratureId(literatureId);
        int qaRecordCount = qaRecordMapper.physicalDeleteByLiteratureId(literatureId);

        boolean fileDeleted = purgeFile(literature, scenario);
        boolean vectorDeleted = milvusLiteratureVectorService.deleteLiterature(literatureId, scenario);

        PurgeResult result = new PurgeResult(chunkCount, favoriteCount, readingHistoryCount, readingNoteCount,
                citationCount, qaRecordCount, fileDeleted, vectorDeleted);
        log.info("literature cascade purged, scenario={}, literatureId={}, chunkCount={}, favoriteCount={}, "
                        + "readingHistoryCount={}, readingNoteCount={}, citationCount={}, qaRecordCount={}, "
                        + "fileDeleted={}, vectorDeleted={}",
                scenario, literatureId, chunkCount, favoriteCount, readingHistoryCount, readingNoteCount,
                citationCount, qaRecordCount, fileDeleted, vectorDeleted);
        return result;
    }

    private boolean purgeFile(Literature literature, String scenario) {
        Long fileId = literature.getFileId();
        if (fileId == null) {
            return false;
        }
        FileMetadata file = fileMetadataMapper.selectById(fileId);
        if (file == null) {
            log.warn("literature purge skipped missing file, scenario={}, literatureId={}, fileId={}",
                    scenario, literature.getId(), fileId);
            return false;
        }
        boolean objectDeleted = fileService.deleteObject(file.getBucketName(), file.getObjectKey());
        // 元数据走 MyBatis-Plus 的逻辑删除（deleted=1），文件统计等查询均带 deleted=0，不会继续计入
        fileMetadataMapper.deleteById(fileId);
        return objectDeleted;
    }

    /** 级联清理结果的计数快照，便于日志与测试断言。 */
    public record PurgeResult(int chunkCount, int favoriteCount, int readingHistoryCount, int readingNoteCount,
                              int citationCount, int qaRecordCount, boolean fileDeleted, boolean vectorDeleted) {
    }
}
