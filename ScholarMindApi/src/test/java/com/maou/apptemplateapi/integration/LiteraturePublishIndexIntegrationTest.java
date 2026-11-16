package com.maou.apptemplateapi.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.maou.apptemplateapi.common.enums.UserRole;
import com.maou.apptemplateapi.common.security.CurrentUser;
import com.maou.apptemplateapi.common.security.LoginType;
import com.maou.apptemplateapi.module.ai.service.AiClient;
import com.maou.apptemplateapi.module.literature.dto.LiteratureRequest;
import com.maou.apptemplateapi.module.literature.dto.LiteratureResponse;
import com.maou.apptemplateapi.module.literature.entity.Literature;
import com.maou.apptemplateapi.module.literature.entity.LiteratureCategory;
import com.maou.apptemplateapi.module.literature.entity.LiteratureChunk;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureCategoryMapper;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureChunkMapper;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureMapper;
import com.maou.apptemplateapi.module.literature.service.LiteratureService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P3-1 自动重建索引的集成测试（客户需求「重建索引改为自动：上传/审核通过后自动重建」）。
 *
 * <p>为什么单独一个类、且不加 {@code @Transactional}：自动重建挂在
 * {@code @TransactionalEventListener(AFTER_COMMIT)} 上，用例若包在测试事务里，
 * 监听器要等测试事务提交才触发，而测试事务永远回滚——那样只会测到「事件没触发」。
 * 所以这里让业务事务真正提交，用 {@link JdbcTemplate} 在 {@link AfterEach} 里硬删数据。
 *
 * <p>ID 全部交给 MyBatis-Plus 生成，不用固定 ID，保证可重复执行。
 */
@SpringBootTest
@TestPropertySource(properties = {
        "app.ai.retrieval.rerank-enabled=false",
        "app.ai.retrieval.dense-top-k=10",
        "app.ai.retrieval.sparse-top-k=10"
})
class LiteraturePublishIndexIntegrationTest {

    @Autowired
    private UserAccountMapper userAccountMapper;
    @Autowired
    private LiteratureCategoryMapper categoryMapper;
    @Autowired
    private LiteratureMapper literatureMapper;
    @Autowired
    private LiteratureChunkMapper chunkMapper;
    @Autowired
    private LiteratureService literatureService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private AiClient aiClient;
    @MockBean
    private MilvusLiteratureVectorService milvusLiteratureVectorService;

    private Long adminId;
    private Long uploaderId;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        adminId = insertUser("it_publish_admin", UserRole.ADMIN);
        uploaderId = insertUser("it_publish_uploader", UserRole.STUDENT);

        LiteratureCategory category = new LiteratureCategory();
        category.setName("自动重建索引测试分类");
        category.setSort(1);
        category.setStatus(1);
        category.setDeleted(0);
        categoryMapper.insert(category);
        categoryId = category.getId();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        // 业务事务已提交，测试自己清理，避免污染演示库
        if (categoryId != null) {
            jdbcTemplate.update("DELETE FROM literature_chunk WHERE literature_id IN (SELECT id FROM literature WHERE category_id = ?)", categoryId);
            jdbcTemplate.update("DELETE FROM literature WHERE category_id = ?", categoryId);
            jdbcTemplate.update("DELETE FROM literature_category WHERE id = ?", categoryId);
        }
        if (adminId != null) {
            jdbcTemplate.update("DELETE FROM user_account WHERE id = ?", adminId);
        }
        if (uploaderId != null) {
            jdbcTemplate.update("DELETE FROM user_account WHERE id = ?", uploaderId);
        }
    }

    @Test
    @DisplayName("管理员上传后自动重建索引并顺带生成摘要：无需前端点击，分块与摘要直接落库")
    void adminUpload_triggersIndexRebuildAutomatically() {
        authenticate(adminId, UserRole.ADMIN);
        LiteratureResponse created = literatureService.create(new LiteratureRequest(
                "管理员上传后自动重建索引", "集成测试作者", "集成测试来源", "学术期刊",
                categoryId, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 1), "zh-CN", null,
                "该文献用于验证管理员上传后自动重建索引，正文来自题录兜底，包含混合检索与 RRF 融合等关键词。",
                "自动重建索引,RRF,集成测试", null, 0));
        assertEquals(adminId, created.uploaderId(), "上传者应记为当前管理员");

        // AFTER_COMMIT 监听器在 create 返回前同步执行完毕，无需等待
        List<LiteratureChunk> chunks = chunksOf(created.id());
        assertFalse(chunks.isEmpty(), "上传后应自动生成分块");
        Literature stored = literatureMapper.selectById(created.id());
        assertEquals("INDEXED", stored.getParseStatus());
        assertNotNull(stored.getParseError(), "题录兜底时应写入兜底原因，便于排查");
        assertTrue(chunks.stream().allMatch(chunk -> "DB_ONLY".equals(chunk.getVectorStoreStatus())),
                "Milvus 未启用时向量状态应为 DB_ONLY");

        // 客户 2026-11-12 拍板：文献入库时顺带生成 AI 摘要。AiClient 是 mock（返回 null），
        // 两次尝试都失败后走兜底摘要，因此这里是 FALLBACK 而不是 SUCCESS。
        assertNotNull(stored.getAiSummary(), "上传后应自动写入 AI 摘要");
        assertTrue(stored.getAiSummary().contains("background"), "兜底摘要也应是结构化 JSON");
        assertNotNull(stored.getSummaryUpdatedAt(), "摘要生成时间不应为空");
        assertEquals("FALLBACK", stored.getSummaryStatus(), "AI 输出不可用时应标记 FALLBACK");
    }

    // ---------------------------------------------------------------- 工具方法

    private Long insertUser(String username, UserRole role) {
        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setPasswordHash("$2b$10$integrationtestintegrationtestintegrationtestintegrationte");
        account.setNickname(username);
        account.setEmail(username + "@it.local");
        account.setRole(role.name());
        account.setStatus(1);
        account.setDeleted(0);
        userAccountMapper.insert(account);
        return account.getId();
    }

    private List<LiteratureChunk> chunksOf(Long literatureId) {
        return chunkMapper.selectList(new LambdaQueryWrapper<LiteratureChunk>()
                .eq(LiteratureChunk::getLiteratureId, literatureId)
                .eq(LiteratureChunk::getDeleted, 0));
    }

    private void authenticate(long userId, UserRole role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                CurrentUser.builder()
                        .principalId(userId)
                        .subject("it-publish-" + userId)
                        .loginType(role == UserRole.ADMIN ? LoginType.ADMIN : LoginType.USER)
                        .roleCode(role.name())
                        .build(),
                null, List.of()));
    }
}
