package com.maou.apptemplateapi.integration;

import com.maou.apptemplateapi.common.enums.UserRole;
import com.maou.apptemplateapi.common.exception.BusinessException;
import com.maou.apptemplateapi.common.exception.ErrorCode;
import com.maou.apptemplateapi.common.security.CurrentUser;
import com.maou.apptemplateapi.common.security.LoginType;
import com.maou.apptemplateapi.module.admin.service.AdminLiteratureVectorService;
import com.maou.apptemplateapi.module.admin.service.AdminStatisticsService;
import com.maou.apptemplateapi.module.ai.entity.AiTaskRecord;
import com.maou.apptemplateapi.module.ai.mapper.AiTaskRecordMapper;
import com.maou.apptemplateapi.module.ai.service.AiClient;
import com.maou.apptemplateapi.module.citation.dto.CitationRecordResponse;
import com.maou.apptemplateapi.module.citation.dto.CitationSaveRequest;
import com.maou.apptemplateapi.module.citation.dto.CitationStyleResponse;
import com.maou.apptemplateapi.module.citation.service.CitationService;
import com.maou.apptemplateapi.module.literature.dto.FavoriteResponse;
import com.maou.apptemplateapi.module.literature.dto.LiteratureIndexResponse;
import com.maou.apptemplateapi.module.literature.dto.LiteratureQuestionRequest;
import com.maou.apptemplateapi.module.literature.dto.LiteratureQuestionResponse;
import com.maou.apptemplateapi.module.literature.dto.LiteratureReferenceChunkResponse;
import com.maou.apptemplateapi.module.literature.dto.LiteratureRequest;
import com.maou.apptemplateapi.module.literature.dto.LiteratureResponse;
import com.maou.apptemplateapi.module.literature.dto.LiteratureReviewRequest;
import com.maou.apptemplateapi.module.literature.dto.LiteratureSearchRequest;
import com.maou.apptemplateapi.module.literature.dto.LiteratureSearchResponse;
import com.maou.apptemplateapi.module.literature.dto.ReadingHistoryRequest;
import com.maou.apptemplateapi.module.literature.dto.ReadingNoteRequest;
import com.maou.apptemplateapi.module.literature.dto.ReadingNoteResponse;
import com.maou.apptemplateapi.module.literature.dto.RetrievalCompareResponse;
import com.maou.apptemplateapi.module.literature.entity.Literature;
import com.maou.apptemplateapi.module.literature.entity.LiteratureCategory;
import com.maou.apptemplateapi.module.literature.entity.LiteratureChunk;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureCategoryMapper;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureChunkMapper;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureMapper;
import com.maou.apptemplateapi.module.literature.service.LiteratureAiService;
import com.maou.apptemplateapi.module.literature.service.LiteratureFavoriteService;
import com.maou.apptemplateapi.module.literature.service.LiteratureIndexService;
import com.maou.apptemplateapi.module.literature.service.LiteratureService;
import com.maou.apptemplateapi.module.literature.service.ReadingHistoryService;
import com.maou.apptemplateapi.module.literature.service.ReadingNoteService;
import com.maou.apptemplateapi.module.rag.service.MilvusLiteratureVectorService;
import com.maou.apptemplateapi.module.user.entity.UserAccount;
import com.maou.apptemplateapi.module.user.mapper.UserAccountMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 主链路集成测试：真实 MySQL + 真实 Service/Controller 层 Bean，
 * 仅把「外部依赖」（大模型、Milvus）替换为 Mock，保证测试离线、可重复。
 *
 * 覆盖：文献上传→提交→审核→发布、可读权限、索引重建、阅读进度、笔记、收藏、
 * 引用管理、混合检索与 RRF 融合、AI 问答留痕、管理端权限校验。
 *
 * 说明：类上标注 @Transactional，每个用例结束后自动回滚，不会污染演示数据。
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "app.ai.retrieval.rerank-enabled=false",
        "app.ai.retrieval.dense-top-k=10",
        "app.ai.retrieval.sparse-top-k=10"
})
class LiteratureMainFlowIntegrationTest {

    private static final long ADMIN_ID = 7700000000000000001L;
    private static final long TEACHER_ID = 7700000000000000002L;
    private static final long STUDENT_A_ID = 7700000000000000003L;
    private static final long STUDENT_B_ID = 7700000000000000004L;
    private static final long CATEGORY_ID = 7700000000000000010L;

    @Autowired
    private UserAccountMapper userAccountMapper;
    @Autowired
    private LiteratureCategoryMapper categoryMapper;
    @Autowired
    private LiteratureMapper literatureMapper;
    @Autowired
    private LiteratureChunkMapper chunkMapper;
    @Autowired
    private AiTaskRecordMapper aiTaskRecordMapper;
    @Autowired
    private LiteratureService literatureService;
    @Autowired
    private LiteratureIndexService literatureIndexService;
    @Autowired
    private LiteratureFavoriteService favoriteService;
    @Autowired
    private ReadingHistoryService readingHistoryService;
    @Autowired
    private ReadingNoteService readingNoteService;
    @Autowired
    private CitationService citationService;
    @Autowired
    private LiteratureAiService literatureAiService;
    @Autowired
    private AdminStatisticsService adminStatisticsService;
    @Autowired
    private AdminLiteratureVectorService adminLiteratureVectorService;

    @MockBean
    private AiClient aiClient;
    @MockBean
    private MilvusLiteratureVectorService milvusLiteratureVectorService;

    @BeforeEach
    void setUp() {
        insertUser(ADMIN_ID, "it_admin", UserRole.ADMIN);
        insertUser(TEACHER_ID, "it_teacher", UserRole.TEACHER);
        insertUser(STUDENT_A_ID, "it_student_a", UserRole.STUDENT);
        insertUser(STUDENT_B_ID, "it_student_b", UserRole.STUDENT);

        LiteratureCategory category = new LiteratureCategory();
        category.setId(CATEGORY_ID);
        category.setName("集成测试分类");
        category.setSort(1);
        category.setStatus(1);
        category.setDeleted(0);
        categoryMapper.insert(category);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------- 主链路

    @Test
    @DisplayName("主链路：新建公开文献（自动进入待审核）→ 管理员通过后公开可见")
    void literatureLifecycle_publishAfterReview() {
        authenticate(TEACHER_ID, UserRole.TEACHER);
        LiteratureResponse created = literatureService.create(literatureRequest("集成测试文献：混合检索方法", "PUBLIC"));
        assertEquals("PENDING_REVIEW", created.status(), "公开文献上传后自动进入待审核");
        assertEquals(TEACHER_ID, created.uploaderId());

        authenticate(ADMIN_ID, UserRole.ADMIN);
        LiteratureResponse published = literatureService.approve(created.id());
        assertEquals("PUBLISHED", published.status());
        assertNotNull(literatureMapper.selectById(created.id()).getReviewedAt());

        // 私有文献则先落草稿，需显式提交（此处验证草稿态不被自动发布）
        authenticate(TEACHER_ID, UserRole.TEACHER);
        LiteratureResponse privateDraft = literatureService.create(literatureRequest("私有草稿文献", "PRIVATE"));
        assertEquals("DRAFT", privateDraft.status());
    }

    @Test
    @DisplayName("异常分支：私有文献不许提交审核、已发布不许重复提交、草稿不许直接审核")
    void literatureLifecycle_rejectsIllegalStateTransition() {
        authenticate(TEACHER_ID, UserRole.TEACHER);
        LiteratureResponse privateOne = literatureService.create(literatureRequest("私有文献提交测试", "PRIVATE"));
        assertEquals("DRAFT", privateOne.status());
        BusinessException privateSubmit = assertThrows(BusinessException.class, () -> literatureService.submit(privateOne.id()));
        assertEquals(ErrorCode.LITERATURE_STATUS_INVALID, privateSubmit.getErrorCode());

        LiteratureResponse publicOne = literatureService.create(literatureRequest("已发布文献重复提交测试", "PUBLIC"));
        authenticate(ADMIN_ID, UserRole.ADMIN);
        literatureService.approve(publicOne.id());

        authenticate(TEACHER_ID, UserRole.TEACHER);
        BusinessException resubmit = assertThrows(BusinessException.class, () -> literatureService.submit(publicOne.id()));
        assertEquals(ErrorCode.LITERATURE_STATUS_INVALID, resubmit.getErrorCode());

        LiteratureResponse draft = literatureService.create(literatureRequest("草稿直接审核测试", "PRIVATE"));
        authenticate(ADMIN_ID, UserRole.ADMIN);
        BusinessException approveDraft = assertThrows(BusinessException.class, () -> literatureService.approve(draft.id()));
        assertEquals(ErrorCode.LITERATURE_STATUS_INVALID, approveDraft.getErrorCode());
    }

    @Test
    @DisplayName("异常分支：审核退回需记录原因，退回后为 REJECTED")
    void literatureLifecycle_rejectWithReason() {
        authenticate(TEACHER_ID, UserRole.TEACHER);
        LiteratureResponse created = literatureService.create(literatureRequest("待退回文献", "PUBLIC"));

        authenticate(ADMIN_ID, UserRole.ADMIN);
        LiteratureResponse rejected = literatureService.reject(created.id(), new LiteratureReviewRequest("摘要信息不足，请补充"));
        assertEquals("REJECTED", rejected.status());
        assertEquals("摘要信息不足，请补充", literatureMapper.selectById(created.id()).getRejectReason());
    }

    @Test
    @DisplayName("权限：私有文献仅上传者可读，他人访问返回 14102")
    void privateLiterature_isReadableOnlyByUploader() {
        authenticate(STUDENT_A_ID, UserRole.STUDENT);
        LiteratureResponse created = literatureService.create(literatureRequest("私有读书笔记", "PRIVATE"));

        CurrentUser studentA = currentUser(STUDENT_A_ID, UserRole.STUDENT);
        assertEquals(created.id(), literatureService.requireReadableLiterature(created.id(), studentA, "it").getId());

        CurrentUser studentB = currentUser(STUDENT_B_ID, UserRole.STUDENT);
        BusinessException denied = assertThrows(BusinessException.class,
                () -> literatureService.requireReadableLiterature(created.id(), studentB, "it"));
        assertEquals(ErrorCode.LITERATURE_ACCESS_DENIED, denied.getErrorCode());
    }

    // ---------------------------------------------------------------- 索引与阅读

    @Test
    @DisplayName("索引重建：无原文文件时回退题录摘要，分块落库且向量状态为 DB_ONLY")
    void indexRebuild_fallsBackToMetadataAndWritesChunks() {
        when(milvusLiteratureVectorService.enabled()).thenReturn(true);
        when(milvusLiteratureVectorService.rebuildLiterature(any(), any(), anyString())).thenReturn(false);

        authenticate(TEACHER_ID, UserRole.TEACHER);
        LiteratureResponse created = literatureService.create(literatureRequest("索引重建测试文献", "PRIVATE"));

        LiteratureIndexResponse index = literatureIndexService.rebuild(created.id());
        assertTrue(index.chunkCount() > 0, "应生成正文分块");
        assertEquals("INDEXED", index.parseStatus());

        List<LiteratureChunk> chunks = chunkMapper.selectList(null).stream()
                .filter(chunk -> created.id().equals(chunk.getLiteratureId()))
                .toList();
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().allMatch(chunk -> "DB_ONLY".equals(chunk.getVectorStoreStatus())),
                "向量库不可用时状态应为 DB_ONLY");
    }

    @Test
    @DisplayName("阅读进度：正常保存可回读，进度越界返回 14202")
    void readingHistory_upsertAndValidateProgress() {
        authenticate(STUDENT_A_ID, UserRole.STUDENT);
        LiteratureResponse created = literatureService.create(literatureRequest("阅读进度测试文献", "PRIVATE"));

        readingHistoryService.upsert(created.id(), new ReadingHistoryRequest(2, 60, 300));
        assertEquals(60, readingHistoryService.getByLiterature(created.id()).progress());

        BusinessException invalid = assertThrows(BusinessException.class,
                () -> readingHistoryService.upsert(created.id(), new ReadingHistoryRequest(1, 150, 10)));
        assertEquals(ErrorCode.READING_PROGRESS_INVALID, invalid.getErrorCode());
    }

    @Test
    @DisplayName("阅读笔记：本人可增删改查，他人删除返回 14302")
    void readingNote_crudAndCrossUserDenied() {
        authenticate(STUDENT_A_ID, UserRole.STUDENT);
        LiteratureResponse created = literatureService.create(literatureRequest("笔记测试文献", "PRIVATE"));
        ReadingNoteResponse note = readingNoteService.create(
                new ReadingNoteRequest(created.id(), "研究问题", "本文关注混合检索的召回质量。", 0));
        assertNotNull(note.id());

        ReadingNoteResponse updated = readingNoteService.update(note.id(),
                new ReadingNoteRequest(created.id(), "研究问题（修订）", "补充：RRF 融合能缓解单路召回偏置。", 0));
        assertEquals("研究问题（修订）", updated.title());

        authenticate(STUDENT_B_ID, UserRole.STUDENT);
        BusinessException denied = assertThrows(BusinessException.class, () -> readingNoteService.delete(note.id()));
        assertEquals(ErrorCode.READING_NOTE_ACCESS_DENIED, denied.getErrorCode());

        authenticate(STUDENT_A_ID, UserRole.STUDENT);
        readingNoteService.delete(note.id());
        assertTrue(readingNoteService.list(created.id(), 1, 10).getItems().isEmpty());
        assertTrue(readingNoteService.exportMarkdown(created.id()).contains("阅读笔记导出"));
    }

    @Test
    @DisplayName("收藏：重复收藏返回 14105，取消后可再次收藏")
    void favorite_duplicateGuardAndUnfavorite() {
        authenticate(STUDENT_A_ID, UserRole.STUDENT);
        LiteratureResponse created = literatureService.create(literatureRequest("收藏测试文献", "PRIVATE"));

        FavoriteResponse favorite = favoriteService.favorite(created.id());
        assertNotNull(favorite.id());
        assertTrue(favoriteService.isFavorited(created.id(), STUDENT_A_ID));

        BusinessException duplicated = assertThrows(BusinessException.class, () -> favoriteService.favorite(created.id()));
        assertEquals(ErrorCode.LITERATURE_FAVORITE_DUPLICATED, duplicated.getErrorCode());

        favoriteService.unfavorite(created.id());
        assertFalse(favoriteService.isFavorited(created.id(), STUDENT_A_ID));
        assertThrows(BusinessException.class, () -> favoriteService.unfavorite(created.id()));
    }

    @Test
    @DisplayName("回归：反复收藏/取消收藏多轮不得抛唯一索引异常（客户反馈的 500）")
    void favorite_repeatedToggleIsStable() {
        authenticate(STUDENT_A_ID, UserRole.STUDENT);
        LiteratureResponse created = literatureService.create(literatureRequest("收藏反复切换文献", "PRIVATE"));

        // 逻辑删除会把 deleted 置 1，第二次取消时与历史行冲突 -> DuplicateKeyException -> HTTP 500。
        // 修复后取消收藏为物理删除，任意轮次都应稳定。
        for (int round = 1; round <= 3; round++) {
            favoriteService.favorite(created.id());
            assertTrue(favoriteService.isFavorited(created.id(), STUDENT_A_ID), "第 " + round + " 轮收藏后应为已收藏");
            favoriteService.unfavorite(created.id());
            assertFalse(favoriteService.isFavorited(created.id(), STUDENT_A_ID), "第 " + round + " 轮取消后应为未收藏");
        }
        // 最后一轮再收藏，确认仍可正常写入
        favoriteService.favorite(created.id());
        assertTrue(favoriteService.isFavorited(created.id(), STUDENT_A_ID));
    }

    // ---------------------------------------------------------------- 引用管理

    @Test
    @DisplayName("引用管理：生成三种格式、收藏去重、导出参考文献、取消收藏")
    void citation_generateSaveExportAndDelete() {
        authenticate(STUDENT_A_ID, UserRole.STUDENT);
        LiteratureResponse created = literatureService.create(literatureRequest("引用管理测试文献", "PRIVATE"));

        List<CitationStyleResponse> styles = citationService.stylesForLiterature(created.id());
        assertEquals(3, styles.size());
        assertTrue(styles.stream().allMatch(style -> style.text() != null && !style.text().isBlank()));
        assertTrue(styles.stream().anyMatch(style -> style.text().contains("[J]")));
        assertTrue(styles.stream().anyMatch(style -> style.text().contains("(2024)")));

        CitationRecordResponse saved = citationService.save(new CitationSaveRequest(created.id(), "GB_T_7714", "第 3 条"));
        assertNotNull(saved.id());
        assertEquals("GB/T 7714（中国国家标准）", saved.styleLabel());

        BusinessException duplicated = assertThrows(BusinessException.class,
                () -> citationService.save(new CitationSaveRequest(created.id(), "GB_T_7714", null)));
        assertEquals(ErrorCode.CITATION_DUPLICATED, duplicated.getErrorCode());

        String exported = citationService.exportText("GB_T_7714");
        assertTrue(exported.contains("[1]"));
        assertTrue(exported.contains("引用管理测试文献"));

        citationService.delete(saved.id());
        assertTrue(citationService.list(1, 10, created.id()).getItems().isEmpty());
        assertThrows(BusinessException.class, () -> citationService.delete(saved.id()));
    }

    @Test
    @DisplayName("异常分支：非法引用格式返回 14403")
    void citation_rejectsUnknownStyle() {
        authenticate(STUDENT_A_ID, UserRole.STUDENT);
        LiteratureResponse created = literatureService.create(literatureRequest("格式校验文献", "PRIVATE"));

        BusinessException invalid = assertThrows(BusinessException.class,
                () -> citationService.save(new CitationSaveRequest(created.id(), "CHICAGO", null)));
        assertEquals(ErrorCode.CITATION_STYLE_INVALID, invalid.getErrorCode());
    }

    // ---------------------------------------------------------------- 混合检索

    @Test
    @DisplayName("混合检索：两路召回经 RRF 融合，两路都命中的分块排最前")
    void hybridSearch_fusesSparseAndDenseRanks() {
        Literature literature = insertLiterature(STUDENT_A_ID, "PUBLISHED", "PUBLIC", "混合检索融合测试文献");
        LiteratureChunk sparseOnly = insertChunk(literature.getId(), 0, "混合检索 通过 BM25 关键词召回与向量召回融合，RRF 排序。");
        LiteratureChunk denseOnly = insertChunk(literature.getId(), 1, "本段文字不含查询词，仅用于验证向量召回结果被保留。");

        when(milvusLiteratureVectorService.enabled()).thenReturn(true);
        when(milvusLiteratureVectorService.search(anyString(), anyInt(), anyString()))
                .thenReturn(List.of(
                        new MilvusLiteratureVectorService.VectorHit(denseOnly.getId(), literature.getId(), 0.91),
                        new MilvusLiteratureVectorService.VectorHit(sparseOnly.getId(), literature.getId(), 0.80)));

        authenticate(STUDENT_A_ID, UserRole.STUDENT);
        LiteratureSearchResponse response = literatureAiService.semanticSearch(
                new LiteratureSearchRequest("混合检索 RRF", CATEGORY_ID, 2));

        assertEquals(2, response.references().size());
        LiteratureReferenceChunkResponse first = response.references().get(0);
        LiteratureReferenceChunkResponse second = response.references().get(1);

        assertEquals("HYBRID", first.retriever());
        assertEquals(sparseOnly.getId(), first.id(), "两路都命中的分块 RRF 得分最高，应排第一");
        assertEquals(1, first.sparseRank());
        assertEquals(2, first.denseRank());
        assertNotNull(first.rrfScore());

        assertEquals(denseOnly.getId(), second.id(), "仅向量命中的分块应被保留");
        assertNull(second.sparseRank());
        assertEquals(1, second.denseRank());
    }

    @Test
    @DisplayName("混合检索：向量库不可用时退化为纯稀疏检索")
    void hybridSearch_degradesToSparseWhenDenseUnavailable() {
        Literature literature = insertLiterature(STUDENT_A_ID, "PUBLISHED", "PUBLIC", "纯稀疏退化测试文献");
        insertChunk(literature.getId(), 0, "混合检索在向量库不可用时应退化为纯 BM25 稀疏检索。");

        when(milvusLiteratureVectorService.enabled()).thenReturn(false);

        authenticate(STUDENT_A_ID, UserRole.STUDENT);
        LiteratureSearchResponse response = literatureAiService.semanticSearch(
                new LiteratureSearchRequest("混合检索", CATEGORY_ID, 3));

        assertFalse(response.references().isEmpty());
        assertTrue(response.references().stream().allMatch(item -> "SPARSE".equals(item.retriever())));
        assertTrue(response.references().stream().allMatch(item -> item.denseRank() == null));
    }

    @Test
    @DisplayName("检索消融对比：同时给出稀疏、稠密与融合三路排序")
    void retrievalCompare_returnsThreeRankedLists() {
        Literature literature = insertLiterature(STUDENT_A_ID, "PUBLISHED", "PUBLIC", "检索对比测试文献");
        LiteratureChunk sparseOnly = insertChunk(literature.getId(), 0, "关键词召回 精确匹配 专有名词 BM25 得分更高。");
        LiteratureChunk denseOnly = insertChunk(literature.getId(), 1, "语义相近但字面不同的段落，用于验证向量召回。");

        when(milvusLiteratureVectorService.enabled()).thenReturn(true);
        when(milvusLiteratureVectorService.search(anyString(), anyInt(), anyString()))
                .thenReturn(List.of(new MilvusLiteratureVectorService.VectorHit(denseOnly.getId(), literature.getId(), 0.88)));

        authenticate(STUDENT_A_ID, UserRole.STUDENT);
        RetrievalCompareResponse compare = literatureAiService.compareRetrieval(
                new LiteratureSearchRequest("BM25 关键词", CATEGORY_ID, 3));

        assertTrue(compare.denseAvailable());
        assertEquals(sparseOnly.getId(), compare.sparse().get(0).chunkId());
        assertEquals(denseOnly.getId(), compare.dense().get(0).chunkId());
        assertFalse(compare.hybrid().isEmpty(), "融合列表应返回结果");
        assertEquals(1, compare.hybrid().get(0).rank());
        assertTrue(compare.sparseCandidateCount() >= 1, "稀疏召回至少应有一条候选");
    }

    // ---------------------------------------------------------------- AI 与管理端权限

    @Test
    @DisplayName("AI 问答：答案与引用片段落库，AI 任务记录为 SUCCESS")
    void literatureQuestion_persistsAnswerAndAiTask() {
        when(aiClient.generate(anyString(), anyString())).thenReturn("本文关注混合检索与 RRF 融合排序。");

        Literature literature = insertLiterature(STUDENT_A_ID, "PUBLISHED", "PUBLIC", "问答留痕测试文献");
        insertChunk(literature.getId(), 0, "本文关注混合检索与 RRF 融合排序，避免纯向量召回偏置。");

        authenticate(STUDENT_A_ID, UserRole.STUDENT);
        LiteratureQuestionResponse answer = literatureAiService.answer(literature.getId(),
                new LiteratureQuestionRequest("本文关注什么？", 3));

        assertNotNull(answer.recordId());
        assertTrue(answer.answer().contains("RRF"));
        assertFalse(answer.references().isEmpty());

        List<AiTaskRecord> tasks = aiTaskRecordMapper.selectList(null).stream()
                .filter(task -> "LITERATURE_QUESTION".equals(task.getTaskType()))
                .toList();
        assertFalse(tasks.isEmpty());
        assertTrue(tasks.stream().anyMatch(task -> "SUCCESS".equals(task.getStatus())));
    }

    @Test
    @DisplayName("管理端权限：学生调用统计与向量重建均返回 403")
    void adminEndpoints_rejectNonAdmin() {
        authenticate(STUDENT_A_ID, UserRole.STUDENT);

        BusinessException statistics = assertThrows(BusinessException.class,
                () -> adminStatisticsService.dashboard(7, 5));
        assertEquals(ErrorCode.FORBIDDEN, statistics.getErrorCode());

        BusinessException vector = assertThrows(BusinessException.class,
                () -> adminLiteratureVectorService.rebuildAll());
        assertEquals(ErrorCode.FORBIDDEN, vector.getErrorCode());

        authenticate(ADMIN_ID, UserRole.ADMIN);
        assertNotNull(adminStatisticsService.dashboard(7, 5).overview());
    }

    // ---------------------------------------------------------------- 工具方法

    private void insertUser(long id, String username, UserRole role) {
        UserAccount account = new UserAccount();
        account.setId(id);
        account.setUsername(username);
        account.setPasswordHash("$2b$10$integrationtestintegrationtestintegrationtestintegrationte");
        account.setNickname(username);
        account.setEmail(username + "@it.local");
        account.setRole(role.name());
        account.setStatus(1);
        account.setDeleted(0);
        userAccountMapper.insert(account);
    }

    private LiteratureRequest literatureRequest(String title, String visibility) {
        return new LiteratureRequest(title, "集成测试作者", "集成测试来源", 2024, "zh-CN",
                CATEGORY_ID, visibility, null,
                "本文用于集成测试，关注混合检索与 RRF 融合排序在学术文献检索中的效果。",
                "混合检索,RRF,集成测试");
    }

    private Literature insertLiterature(long uploaderId, String status, String visibility, String title) {
        Literature literature = new Literature();
        literature.setTitle(title);
        literature.setAuthor("集成测试作者");
        literature.setSource("集成测试来源");
        literature.setPublishYear(2024);
        literature.setLanguage("zh-CN");
        literature.setCategoryId(CATEGORY_ID);
        literature.setUploaderId(uploaderId);
        literature.setVisibility(visibility);
        literature.setStatus(status);
        literature.setAbstractText("集成测试用文献摘要，包含混合检索与 RRF 融合等关键词。");
        literature.setKeywords("混合检索,RRF,测试");
        literature.setReadCount(0);
        literature.setDownloadCount(0);
        literature.setDeleted(0);
        literatureMapper.insert(literature);
        return literature;
    }

    private LiteratureChunk insertChunk(Long literatureId, int chunkIndex, String text) {
        LiteratureChunk chunk = new LiteratureChunk();
        chunk.setLiteratureId(literatureId);
        chunk.setChunkIndex(chunkIndex);
        chunk.setChunkText(text);
        chunk.setTokenCount(Math.max(1, text.length() / 2));
        chunk.setVectorStoreStatus("DB_ONLY");
        chunk.setEnabled(1);
        chunk.setDeleted(0);
        chunkMapper.insert(chunk);
        return chunk;
    }

    private void authenticate(long userId, UserRole role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUser(userId, role), null, List.of()));
    }

    private CurrentUser currentUser(long userId, UserRole role) {
        return CurrentUser.builder()
                .principalId(userId)
                .subject("it-user-" + userId)
                .loginType(role == UserRole.ADMIN ? LoginType.ADMIN : LoginType.USER)
                .roleCode(role.name())
                .build();
    }
}
