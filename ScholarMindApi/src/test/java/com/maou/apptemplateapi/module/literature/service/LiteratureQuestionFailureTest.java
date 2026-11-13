package com.maou.apptemplateapi.module.literature.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maou.apptemplateapi.common.exception.BusinessException;
import com.maou.apptemplateapi.common.exception.ErrorCode;
import com.maou.apptemplateapi.common.security.CurrentUser;
import com.maou.apptemplateapi.module.ai.entity.AiTaskRecord;
import com.maou.apptemplateapi.module.ai.service.AiClient;
import com.maou.apptemplateapi.module.ai.service.AiTaskService;
import com.maou.apptemplateapi.module.literature.dto.LiteratureQuestionRequest;
import com.maou.apptemplateapi.module.literature.entity.Literature;
import com.maou.apptemplateapi.module.literature.entity.LiteratureChunk;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureChunkMapper;
import com.maou.apptemplateapi.module.literature.mapper.LiteratureQaRecordMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.net.ssl.SSLHandshakeException;
import java.net.SocketTimeoutException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiteratureQuestionFailureTest {

    private static final Long LITERATURE_ID = 2121010405823217666L;
    private static final Long USER_ID = 42L;
    private static final Long TASK_ID = 123L;

    @Mock private AiClient aiClient;
    @Mock private AiTaskService aiTaskService;
    @Mock private LiteratureChunkMapper chunkMapper;
    @Mock private LiteratureQaRecordMapper qaRecordMapper;
    @Mock private LiteratureService literatureService;
    @Mock private LiteratureIndexService literatureIndexService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private LiteratureAiService service;

    private final Logger logger = (Logger) LoggerFactory.getLogger(LiteratureAiService.class);
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        CurrentUser user = CurrentUser.builder().principalId(USER_ID).roleCode("STUDENT").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
        Literature literature = new Literature();
        literature.setId(LITERATURE_ID);
        literature.setTitle("AndroidStudio");
        when(literatureService.requireReadableLiterature(eq(LITERATURE_ID), any(), eq("literature-question")))
                .thenReturn(literature);
        when(chunkMapper.selectList(any())).thenReturn(List.of());
        LiteratureChunk chunk = new LiteratureChunk();
        chunk.setId(1L);
        chunk.setLiteratureId(LITERATURE_ID);
        chunk.setChunkIndex(0);
        chunk.setChunkText("SurfaceView 界面代码");
        when(literatureIndexService.listEnabledChunks(LITERATURE_ID)).thenReturn(List.of(chunk));
        when(aiTaskService.maxPromptChars()).thenReturn(12000);
        AiTaskRecord task = new AiTaskRecord();
        task.setId(TASK_ID);
        task.setModelName("test-model");
        when(aiTaskService.createPending(any(), eq("LITERATURE_QUESTION"), eq("LITERATURE"),
                eq(LITERATURE_ID), anyString())).thenReturn(task);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        SecurityContextHolder.clearContext();
    }

    @Test
    void reportsConnectionFailureWithTaskIdAndLogsOriginalStack() {
        RuntimeException failure = new RuntimeException(
                new SSLHandshakeException("Remote host terminated the handshake"));
        when(aiClient.generate(anyString(), anyString())).thenThrow(failure);

        BusinessException error = assertThrows(BusinessException.class, this::ask);

        assertEquals(ErrorCode.AI_SERVICE_UNAVAILABLE, error.getErrorCode());
        assertTrue(error.getMessage().contains("安全连接失败"));
        assertTrue(error.getMessage().contains("任务编号：123"));
        verify(aiTaskService).markFailed(eq(TASK_ID), eq(failure.getMessage()), anyLong());
        verify(aiTaskService, never()).markSuccess(anyLong(), anyString(), anyLong());
        assertFailureLogged();
    }

    @Test
    void reportsTimeoutWithoutExposingProviderException() {
        when(aiClient.generate(anyString(), anyString()))
                .thenThrow(new RuntimeException(new SocketTimeoutException("provider internal details")));

        BusinessException error = assertThrows(BusinessException.class, this::ask);

        assertTrue(error.getMessage().contains("响应超时"));
        assertTrue(!error.getMessage().contains("provider internal details"));
        assertFailureLogged();
    }

    @Test
    void doesNotTreatBlankModelOutputAsSuccess() {
        when(aiClient.generate(anyString(), anyString())).thenReturn("  ");

        assertThrows(BusinessException.class, this::ask);

        verify(aiTaskService).markFailed(eq(TASK_ID), eq("AI question output is blank"), anyLong());
        verify(aiTaskService, never()).markSuccess(anyLong(), anyString(), anyLong());
        assertFailureLogged();
    }

    @Test
    void logsOriginalFailureBeforeAuditWriteCanFail() {
        when(aiClient.generate(anyString(), anyString()))
                .thenThrow(new RuntimeException(new SSLHandshakeException("Remote host terminated the handshake")));
        doThrow(new IllegalStateException("audit storage unavailable"))
                .when(aiTaskService).markFailed(eq(TASK_ID), anyString(), anyLong());

        assertThrows(IllegalStateException.class, this::ask);

        assertFailureLogged();
    }

    private void ask() {
        service.answer(LITERATURE_ID, new LiteratureQuestionRequest("这篇文献的研究问题是什么？", 4));
    }

    private void assertFailureLogged() {
        ILoggingEvent event = appender.list.stream()
                .filter(item -> item.getFormattedMessage().startsWith("literature question failed,"))
                .findFirst().orElseThrow();
        assertTrue(event.getFormattedMessage().contains("scenario=literature-question"));
        assertTrue(event.getFormattedMessage().contains("literatureId=" + LITERATURE_ID));
        assertTrue(event.getFormattedMessage().contains("taskId=" + TASK_ID));
        assertNotNull(event.getThrowableProxy());
    }
}
