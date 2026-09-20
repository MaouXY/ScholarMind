package com.maou.apptemplateapi.integration;

import com.maou.apptemplateapi.common.enums.UserRole;
import com.maou.apptemplateapi.common.security.CurrentUser;
import com.maou.apptemplateapi.common.security.JwtTokenService;
import com.maou.apptemplateapi.common.security.LoginType;
import com.maou.apptemplateapi.module.user.entity.UserAccount;
import com.maou.apptemplateapi.module.user.mapper.UserAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接口层鉴权测试：使用真实 JWT（由 {@link JwtTokenService} 签发）走完整 Spring Security 过滤链，
 * 验证「未登录 401、角色不足 403、正常放行 200、业务越权返回业务错误码」四类边界。
 *
 * 与主链路测试相同，类上标注 @Transactional，用例结束后回滚。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.ai.retrieval.rerank-enabled=false")
class ApiSecurityIntegrationTest {

    private static final long ADMIN_ID = 7710000000000000001L;
    private static final long STUDENT_A_ID = 7710000000000000002L;
    private static final long STUDENT_B_ID = 7710000000000000003L;
    private static final long PRIVATE_LITERATURE_ID = 7710000000000000010L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenService jwtTokenService;
    @Autowired
    private UserAccountMapper userAccountMapper;
    @Autowired
    private com.maou.apptemplateapi.module.literature.mapper.LiteratureMapper literatureMapper;

    @BeforeEach
    void setUp() {
        insertUser(ADMIN_ID, "it_api_admin", UserRole.ADMIN);
        insertUser(STUDENT_A_ID, "it_api_student_a", UserRole.STUDENT);
        insertUser(STUDENT_B_ID, "it_api_student_b", UserRole.STUDENT);

        com.maou.apptemplateapi.module.literature.entity.Literature literature =
                new com.maou.apptemplateapi.module.literature.entity.Literature();
        literature.setId(PRIVATE_LITERATURE_ID);
        literature.setTitle("接口鉴权测试：学生 A 的私有文献");
        literature.setAuthor("集成测试作者");
        literature.setUploaderId(STUDENT_A_ID);
        literature.setVisibility("PRIVATE");
        literature.setStatus("DRAFT");
        literature.setAbstractText("用于验证越权访问私有文献返回业务错误码。");
        literature.setKeywords("鉴权,私有文献");
        literature.setReadCount(0);
        literature.setDownloadCount(0);
        literature.setDeleted(0);
        literatureMapper.insert(literature);
    }

    @Test
    @DisplayName("未携带 token 访问受保护接口返回 401")
    void unauthenticated_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/literatures"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("非法 token 返回 401")
    void invalidToken_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/literatures").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("学生访问管理端接口返回 403，管理员可正常访问")
    void adminEndpoint_requiresAdminRole() throws Exception {
        // URL 层已按角色拦截（RestAccessDeniedHandler），因此是真正的 HTTP 403
        mockMvc.perform(get("/api/v1/admin/statistics/dashboard")
                        .header("Authorization", bearer(STUDENT_A_ID, UserRole.STUDENT)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/ai-tasks?page=1&size=5")
                        .header("Authorization", bearer(STUDENT_A_ID, UserRole.STUDENT)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/statistics/dashboard")
                        .header("Authorization", bearer(ADMIN_ID, UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.overview").exists());
    }

    @Test
    @DisplayName("登录用户可访问公开文献列表与热门关键词以外的公共接口")
    void authenticatedUser_canAccessPublicEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/literatures?page=1&size=5")
                        .header("Authorization", bearer(STUDENT_A_ID, UserRole.STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v1/favorites?page=1&size=5")
                        .header("Authorization", bearer(STUDENT_B_ID, UserRole.STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("越权读取他人私有文献返回业务错误码 14102")
    void foreignPrivateLiterature_returnsAccessDeniedCode() throws Exception {
        mockMvc.perform(get("/api/v1/literatures/" + PRIVATE_LITERATURE_ID + "/view")
                        .header("Authorization", bearer(STUDENT_B_ID, UserRole.STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(14102));

        mockMvc.perform(get("/api/v1/literatures/" + PRIVATE_LITERATURE_ID + "/view")
                        .header("Authorization", bearer(STUDENT_A_ID, UserRole.STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("不存在的文献返回业务错误码 14101")
    void unknownLiterature_returnsNotFoundCode() throws Exception {
        mockMvc.perform(get("/api/v1/literatures/7799999999999999999/view")
                        .header("Authorization", bearer(ADMIN_ID, UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(14101));
    }

    private String bearer(long userId, UserRole role) {
        CurrentUser currentUser = CurrentUser.builder()
                .principalId(userId)
                .subject("it-api-user-" + userId)
                .loginType(role == UserRole.ADMIN ? LoginType.ADMIN : LoginType.USER)
                .roleCode(role.name())
                .build();
        return "Bearer " + jwtTokenService.generateToken(currentUser);
    }

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
}
