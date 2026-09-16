package com.maou.apptemplateapi.common.config.ai;

import com.maou.apptemplateapi.module.ai.service.AiClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 AiConfig 中「模型调用重试」的行为：
 * 1）上游 5xx 抖动时重试，成功即返回；
 * 2）上游持续 5xx 时最多尝试 3 次后抛出，不会无限重试；
 * 3）模型/路径配置错误（404 ModelNotFound）不可重试，只调用一次立即抛出；
 * 4）地址解析失败（UnresolvedAddressException -> UnresolvedModelServerException）属于可重试异常，
 *    会按退避策略重试后再抛出。
 */
class AiClientRetryTest {

    private static final int MAX_ATTEMPTS = 3;

    private HttpServer server;
    private final AtomicInteger requestCount = new AtomicInteger();
    private volatile int failuresBeforeSuccess;
    private volatile int failureStatus;
    private volatile String errorBody;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void retriesUpstreamFailureThenSucceeds() {
        failureStatus = 500;
        failuresBeforeSuccess = 2;

        String output = buildClient().generateJson("system", "user");

        assertTrue(output.contains("retry-ok"), "重试后应返回模型输出，实际: " + output);
        assertEquals(3, requestCount.get(), "两次 500 后成功，应共发起 3 次请求");
    }

    @Test
    void givesUpAfterMaxAttemptsAndThrows() {
        failureStatus = 500;
        failuresBeforeSuccess = Integer.MAX_VALUE;

        RuntimeException failure = assertThrows(RuntimeException.class, () -> buildClient().generateJson("system", "user"));

        assertEquals("dev.langchain4j.exception.InternalServerException", failure.getClass().getName());
        assertEquals(MAX_ATTEMPTS, requestCount.get(), "持续 5xx 时应尝试 " + MAX_ATTEMPTS + " 次后放弃");
    }

    @Test
    void doesNotRetryModelConfigurationError() {
        errorBody = "{\"error\":{\"message\":\"The model `nope` does not exist\",\"type\":\"invalid_request_error\"}}";

        RuntimeException failure = assertThrows(RuntimeException.class, () -> buildClient().generateJson("system", "user"));

        assertEquals("dev.langchain4j.exception.ModelNotFoundException", failure.getClass().getName());
        assertEquals(1, requestCount.get(), "配置类错误不应重试");
    }

    @Test
    void retriesUnresolvedAddressFailure() {
        AiClient client = buildClientWithBaseUrl("http://scholarmind-unresolvable-host-xyz.invalid/api/v3");

        long started = System.currentTimeMillis();
        RuntimeException failure = assertThrows(RuntimeException.class, () -> client.generateJson("system", "user"));
        long elapsed = System.currentTimeMillis() - started;

        assertEquals("dev.langchain4j.exception.UnresolvedModelServerException", failure.getClass().getName());
        // 两次退避分别为 800ms 与 1600ms，耗时下界可证明确实重试过
        assertTrue(elapsed >= 1800, "地址解析失败应触发退避重试，实际耗时 " + elapsed + "ms");
    }

    private AiClient buildClient() {
        return buildClientWithBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private AiClient buildClientWithBaseUrl(String baseUrl) {
        AiProperties properties = new AiProperties(
                true,
                true,
                baseUrl,
                "test-key",
                "test-model",
                30,
                0.7,
                300L,
                12000,
                3,
                5242880L,
                10485760L
        );
        return new AiConfig().aiClient(properties);
    }

    private void handle(HttpExchange exchange) throws IOException {
        int attempt = requestCount.incrementAndGet();
        exchange.getRequestBody().readAllBytes();

        if (errorBody != null) {
            respond(exchange, 404, errorBody);
            return;
        }
        if (attempt <= failuresBeforeSuccess) {
            respond(exchange, failureStatus, "{\"error\":{\"message\":\"upstream unavailable\"}}");
            return;
        }
        respond(exchange, 200, """
                {
                  "id": "chatcmpl-test",
                  "object": "chat.completion",
                  "created": 1,
                  "model": "test-model",
                  "choices": [
                    { "index": 0, "finish_reason": "stop", "message": { "role": "assistant", "content": "{\\"retry-ok\\":true}" } }
                  ],
                  "usage": { "prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2 }
                }
                """);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }
}
